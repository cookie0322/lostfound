package edu.campus.lostfound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.claim.ClaimAppService;
import edu.campus.lostfound.data.entity.ClaimHold;
import edu.campus.lostfound.data.entity.Item;
import edu.campus.lostfound.data.mapper.ClaimHoldMapper;
import edu.campus.lostfound.data.mapper.ClaimMapper;
import edu.campus.lostfound.data.mapper.ItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QR-09 / FR-07 的验收证据：50 个并发申请同一条招领信息，只能成功 1 个。
 *
 * <p>直接调 Service，不经过 HTTP：这样测的是"数据库唯一索引 + 事务"这层，
 * 而不是 Session/序列化。
 * <p>不要给测试方法加 @Transactional——加了之后各线程互相看不见对方的插入，
 * 并发就测不出来了（这是很多人写并发测试时最容易踩的坑）。
 */
@SpringBootTest
class ClaimConcurrencyTest {

    private static final int THREADS = 50;

    @Autowired
    private ClaimAppService claimAppService;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private ClaimMapper claimMapper;
    @Autowired
    private ClaimHoldMapper claimHoldMapper;
    @Autowired
    private edu.campus.lostfound.data.mapper.AccountMapper accountMapper;
    @Autowired
    private edu.campus.lostfound.data.mapper.NoticeMapper noticeMapper;

    private Long itemId;
    private Long applicantId;
    private Long publisherId;

    /**
     * 测试自己造账号与物品，不依赖 db/02-seed.sql 的当前状态。
     * （用种子账号会踩坑：演示过程中 zhao 被解绑过，一跑就 403。）
     */
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        applicantId = newAccount("并行申请人");
        publisherId = newAccount("并行拾得者");
    }

    private Long newAccount(String name) {
        edu.campus.lostfound.data.entity.Account a = new edu.campus.lostfound.data.entity.Account();
        a.setLoginName("t_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        a.setRealName(name);
        a.setAuthState(1);
        a.setRole("USER");
        a.setCreatedAt(LocalDateTime.now());
        accountMapper.insert(a);
        return a.getUserId();
    }

    @AfterEach
    void cleanup() {
        if (itemId != null) {
            List<Long> claimIds = claimMapper.selectList(
                            new LambdaQueryWrapper<edu.campus.lostfound.data.entity.Claim>()
                                    .eq(edu.campus.lostfound.data.entity.Claim::getItemId, itemId))
                    .stream().map(c -> c.getClaimId()).toList();
            claimIds.forEach(id -> claimHoldMapper.delete(
                    new LambdaQueryWrapper<ClaimHold>().eq(ClaimHold::getClaimId, id)));
            claimIds.forEach(claimMapper::deleteById);
            itemMapper.deleteById(itemId);
        }
        // 通知表有外键指向 account，删账号前必须先删通知
        for (Long uid : new Long[]{applicantId, publisherId}) {
            if (uid != null) {
                noticeMapper.delete(new LambdaQueryWrapper<edu.campus.lostfound.data.entity.Notice>()
                        .eq(edu.campus.lostfound.data.entity.Notice::getReceiverId, uid));
            }
        }
        if (applicantId != null) {
            accountMapper.deleteById(applicantId);
        }
        if (publisherId != null) {
            accountMapper.deleteById(publisherId);
        }
    }

    @Test
    @DisplayName("50 并发申请同一信息：仅 1 个成功，其余全部唯一索引冲突")
    void onlyOneWins() throws Exception {
        Item item = new Item();
        item.setTitle("并发测试用物品");
        item.setCategory("钥匙");
        item.setPlace("测试地点");
        item.setFoundAt(LocalDateTime.now());
        item.setStatus(0);
        item.setPublisherId(publisherId);
        itemMapper.insert(item);
        itemId = item.getItemId();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    claimAppService.apply(applicantId,
                            new ClaimAppService.ApplyRequest(itemId, List.of("特征一", "特征二", "特征三")), "USER");
                    ok.incrementAndGet();
                } catch (DuplicateKeyException e) {
                    conflict.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                    System.err.println("非预期异常: " + e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        System.out.println("并发结果: 成功=" + ok.get() + " 冲突=" + conflict.get() + " 其他=" + other.get());

        assertThat(ok.get()).as("只能有 1 个成功").isEqualTo(1);
        assertThat(conflict.get()).as("其余都应是唯一索引冲突").isEqualTo(THREADS - 1);
        assertThat(other.get()).as("不应出现其他异常").isZero();

        Long holds = claimHoldMapper.selectCount(
                new LambdaQueryWrapper<ClaimHold>().eq(ClaimHold::getItemId, itemId));
        assertThat(holds).as("占用表里该信息只能有 1 行").isEqualTo(1L);
    }
}
