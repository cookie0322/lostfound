package edu.campus.lostfound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.claim.ClaimState;
import edu.campus.lostfound.claim.ClaimStateMachine;
import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.data.entity.ClaimHold;
import edu.campus.lostfound.data.entity.Item;
import edu.campus.lostfound.data.mapper.AccountMapper;
import edu.campus.lostfound.data.mapper.ClaimHoldMapper;
import edu.campus.lostfound.data.mapper.ClaimMapper;
import edu.campus.lostfound.data.mapper.ItemMapper;
import edu.campus.lostfound.data.mapper.NoticeMapper;
import edu.campus.lostfound.web.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 白盒测试：认领状态机的**路径覆盖**。
 *
 * <p>覆盖目标：
 * <ul>
 *   <li>转移表中 12 条合法迁移，逐条断言目标状态；</li>
 *   <li>终态（3、4）上的一切事件都应被拒绝（等价于"表里没有这一行"）；</li>
 *   <li>结束原因 end_reason 与事件的映射；</li>
 *   <li>进入终态的副作用：释放占用；进入已完成：物品下架。</li>
 * </ul>
 * 对应 FR-09 / FR-10 / FR-12、QR-06。
 */
@SpringBootTest
class ClaimStateMachineCoverageTest {

    @Autowired
    private ClaimStateMachine stateMachine;
    @Autowired
    private ClaimMapper claimMapper;
    @Autowired
    private ClaimHoldMapper claimHoldMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private NoticeMapper noticeMapper;

    private final List<Long> tempUserIds = new ArrayList<>();
    private final List<Long> tempItemIds = new ArrayList<>();
    private final List<Long> tempClaimIds = new ArrayList<>();

    private Long applicantId;
    private Long respondentId;

    @BeforeEach
    void setUp() {
        applicantId = newAccount("白盒申请人");
        respondentId = newAccount("白盒拾得者");
    }

    @AfterEach
    void tearDown() {
        for (Long cid : tempClaimIds) {
            claimHoldMapper.delete(new LambdaQueryWrapper<ClaimHold>().eq(ClaimHold::getClaimId, cid));
            claimMapper.deleteById(cid);
        }
        for (Long iid : tempItemIds) {
            itemMapper.deleteById(iid);
        }
        for (Long uid : tempUserIds) {
            noticeMapper.delete(new LambdaQueryWrapper<edu.campus.lostfound.data.entity.Notice>()
                    .eq(edu.campus.lostfound.data.entity.Notice::getReceiverId, uid));
            accountMapper.deleteById(uid);
        }
    }

    private Long newAccount(String name) {
        Account a = new Account();
        a.setLoginName("w_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        a.setRealName(name);
        a.setAuthState(1);
        a.setRole("USER");
        a.setCreatedAt(LocalDateTime.now());
        accountMapper.insert(a);
        tempUserIds.add(a.getUserId());
        return a.getUserId();
    }

    /** 造一条处于指定状态、且带活跃占用的认领 */
    private Claim newClaimInState(int state) {
        Item item = new Item();
        item.setTitle("白盒测试物品");
        item.setCategory("钥匙");
        item.setPlace("测试地点");
        item.setFoundAt(LocalDateTime.now());
        item.setStatus(0);
        item.setPublisherId(respondentId);
        itemMapper.insert(item);
        tempItemIds.add(item.getItemId());

        Claim claim = new Claim();
        claim.setItemId(item.getItemId());
        claim.setApplicantId(applicantId);
        claim.setRespondentId(respondentId);
        claim.setState(state);
        claim.setEvidenceJson("[\"特征一\",\"特征二\",\"特征三\"]");
        claim.setExpireAt(LocalDateTime.now().plusHours(24));
        claim.setCreatedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());
        claimMapper.insert(claim);
        tempClaimIds.add(claim.getClaimId());

        ClaimHold hold = new ClaimHold();
        hold.setClaimId(claim.getClaimId());
        hold.setItemId(item.getItemId());
        hold.setCreatedAt(LocalDateTime.now());
        claimHoldMapper.insert(hold);

        return claim;
    }

    private void assertTransition(int from, String event, int expectedTo, Integer expectedEndReason) {
        Claim claim = newClaimInState(from);
        stateMachine.transition(claim, event, respondentId, "USER", "白盒用例");

        assertThat(claim.getState())
                .as("%d --%s--> 期望 %d", from, event, expectedTo)
                .isEqualTo(expectedTo);
        assertThat(claim.getEndReason())
                .as("%d --%s--> end_reason", from, event)
                .isEqualTo(expectedEndReason);

        boolean terminal = expectedTo == ClaimState.COMPLETED.code() || expectedTo == ClaimState.ENDED.code();
        Long holds = claimHoldMapper.selectCount(
                new LambdaQueryWrapper<ClaimHold>().eq(ClaimHold::getClaimId, claim.getClaimId()));
        assertThat(holds).as("终态必须释放占用").isEqualTo(terminal ? 0L : 1L);

        if (expectedTo == ClaimState.COMPLETED.code()) {
            Item item = itemMapper.selectById(claim.getItemId());
            assertThat(item.getStatus()).as("已完成必须把物品置为不可认领").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("白盒·路径覆盖：转移表 12 条合法迁移")
    void allLegalTransitions() {
        assertTransition(0, "ACCEPT", 1, null);
        assertTransition(0, "REJECT", 4, 1);
        assertTransition(0, "WITHDRAW", 4, 3);
        assertTransition(0, "TIMEOUT", 4, 2);
        assertTransition(0, "TO_ARBITRATION", 2, null);

        assertTransition(1, "CONFIRM", 1, null);          // 1→1 自环：记录单方确认
        assertTransition(1, "BOTH_CONFIRMED", 3, null);
        assertTransition(1, "WITHDRAW", 4, 3);
        assertTransition(1, "TIMEOUT", 4, 2);
        assertTransition(1, "TO_ARBITRATION", 2, null);

        assertTransition(2, "ARBITRATE_RETURN", 3, null);
        assertTransition(2, "ARBITRATE_REJECT", 4, 4);
    }

    @Test
    @DisplayName("白盒·分支覆盖：终态上的一切事件都被拒绝")
    void terminalStatesRejectEverything() {
        String[] events = {"ACCEPT", "REJECT", "WITHDRAW", "TIMEOUT", "CONFIRM",
                "BOTH_CONFIRMED", "TO_ARBITRATION", "ARBITRATE_RETURN", "ARBITRATE_REJECT"};

        for (int terminal : new int[]{ClaimState.COMPLETED.code(), ClaimState.ENDED.code()}) {
            for (String event : events) {
                Claim claim = newClaimInState(terminal);
                assertThatThrownBy(() -> stateMachine.transition(claim, event, respondentId, "USER", "非法跃迁"))
                        .as("状态 %d 不应接受事件 %s", terminal, event)
                        .isInstanceOf(BizException.class);
                assertThat(claim.getState()).as("被拒绝后状态不得改变").isEqualTo(terminal);
            }
        }
    }

    @Test
    @DisplayName("白盒·分支覆盖：未知事件名一律拒绝（防止前后端事件名不一致）")
    void unknownEventRejected() {
        Claim claim = newClaimInState(0);
        assertThatThrownBy(() -> stateMachine.transition(claim, "ARBITRATE", respondentId, "USER", "拼错的事件名"))
                .isInstanceOf(BizException.class);
    }
}
