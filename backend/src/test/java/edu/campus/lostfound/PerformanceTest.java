package edu.campus.lostfound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.data.entity.ClaimHold;
import edu.campus.lostfound.data.entity.Item;
import edu.campus.lostfound.data.mapper.AccountMapper;
import edu.campus.lostfound.data.mapper.ClaimHoldMapper;
import edu.campus.lostfound.data.mapper.ClaimMapper;
import edu.campus.lostfound.data.mapper.ItemMapper;
import edu.campus.lostfound.data.mapper.NoticeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QR-01 性能验收：真实 HTTP、真实 MySQL、随机端口起服务，不 mock。
 *
 * <p>三个指标来自需求原文：
 * <ul>
 *   <li>票据换取 ≤ 1s（单次绑定耗时）；</li>
 *   <li>20 并发下列表与详情 P95 ≤ 2s、P99 ≤ 3s；</li>
 *   <li>（200ms 反馈属前端交互，不在本测试范围）</li>
 * </ul>
 *
 * <p>用 JDK HttpClient 自己管 Cookie，避免测试框架的会话共享带来的干扰。
 * 阈值取得比较宽松（P95 2s），目的是守住"明显退化"，不是精确压测。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PerformanceTest {

    private static final int THREADS = 20;
    private static final int REQUESTS_PER_THREAD = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private ClaimMapper claimMapper;
    @Autowired
    private ClaimHoldMapper claimHoldMapper;
    @Autowired
    private NoticeMapper noticeMapper;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> items = new ArrayList<>();
    private final List<Long> claims = new ArrayList<>();

    private String base;
    private Long claimId;

    @BeforeEach
    void setUp() {
        base = "http://127.0.0.1:" + port;

        Long applicant = newAccount("性能申请人", 1);
        Long respondent = newAccount("性能拾得者", 1);

        Item item = new Item();
        item.setTitle("性能测试物品");
        item.setCategory("文具");
        item.setPlace("测试地点");
        item.setFoundAt(LocalDateTime.now());
        item.setStatus(0);
        item.setPublisherId(respondent);
        itemMapper.insert(item);
        items.add(item.getItemId());

        Claim claim = new Claim();
        claim.setItemId(item.getItemId());
        claim.setApplicantId(applicant);
        claim.setRespondentId(respondent);
        claim.setState(0);
        claim.setEvidenceJson("[\"甲\",\"乙\",\"丙\"]");
        claim.setExpireAt(LocalDateTime.now().plusHours(24));
        claim.setCreatedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());
        claimMapper.insert(claim);
        claims.add(claim.getClaimId());
        claimId = claim.getClaimId();

        ClaimHold hold = new ClaimHold();
        hold.setClaimId(claim.getClaimId());
        hold.setItemId(item.getItemId());
        hold.setCreatedAt(LocalDateTime.now());
        claimHoldMapper.insert(hold);
    }

    @AfterEach
    void tearDown() {
        for (Long cid : claims) {
            claimHoldMapper.delete(new LambdaQueryWrapper<ClaimHold>().eq(ClaimHold::getClaimId, cid));
            claimMapper.deleteById(cid);
        }
        for (Long iid : items) {
            itemMapper.deleteById(iid);
        }
        for (Long uid : users) {
            noticeMapper.delete(new LambdaQueryWrapper<edu.campus.lostfound.data.entity.Notice>()
                    .eq(edu.campus.lostfound.data.entity.Notice::getReceiverId, uid));
            accountMapper.deleteById(uid);
        }
    }

    private Long newAccount(String name, int authState) {
        Account a = new Account();
        a.setLoginName("p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        a.setRealName(name);
        a.setAuthState(authState);
        a.setRole("USER");
        a.setCreatedAt(LocalDateTime.now());
        accountMapper.insert(a);
        users.add(a.getUserId());
        return a.getUserId();
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpResponse<String> get(HttpClient c, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return c.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(HttpClient c, String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return c.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpClient login(String loginName) throws Exception {
        HttpClient c = newClient();
        HttpResponse<String> r = post(c, "/api/auth/login", "{\"loginName\":\"" + loginName + "\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        return c;
    }

    private static long p95(long[] sorted) {
        return sorted[(int) Math.min(sorted.length - 1, Math.round(sorted.length * 0.95) - 1)];
    }

    private long[] concurrentLatency(String path, HttpClient client) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        long[] latencies = new long[THREADS * REQUESTS_PER_THREAD];
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            final int threadIndex = t;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        long begin = System.nanoTime();
                        HttpResponse<String> r = get(client, path);
                        long cost = (System.nanoTime() - begin) / 1_000_000;
                        assertThat(r.statusCode()).isEqualTo(200);
                        latencies[threadIndex * REQUESTS_PER_THREAD + i] = cost;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
        for (var f : futures) {
            f.get(); // 把线程内抛出的断言异常带回主线程
        }
        Arrays.sort(latencies);
        return latencies;
    }

    @Test
    @DisplayName("QR-01 票据换取：单次绑定 ≤ 1s")
    void bindLatencyUnder1s() throws Exception {
        // 预热，避免把 JIT/连接建立算进去
        for (int i = 0; i < 5; i++) {
            get(newClient(), "/api/items");
        }

        long worst = 0;
        long total = 0;
        int rounds = 10;
        for (int i = 0; i < rounds; i++) {
            Long uid = newAccount("性能绑定" + i, 0);
            Account a = accountMapper.selectById(uid);
            HttpClient c = login(a.getLoginName());

            HttpResponse<String> t = post(c, "/api/auth/mock-sso",
                    "{\"studentNo\":\"2099" + (100000 + i) + "\",\"realName\":\"性能" + i + "\"}");
            assertThat(t.statusCode()).isEqualTo(200);
            String ticket = t.body().replaceAll(".*\"ticket\":\"([^\"]+)\".*", "$1");

            long begin = System.nanoTime();
            HttpResponse<String> bind = post(c, "/api/auth/bind", "{\"ticket\":\"" + ticket + "\"}");
            long cost = (System.nanoTime() - begin) / 1_000_000;

            assertThat(bind.statusCode()).as("绑定应成功: %s", bind.body()).isEqualTo(200);
            worst = Math.max(worst, cost);
            total += cost;
        }
        System.out.printf("票据换取：平均 %d ms，最慢 %d ms%n", total / rounds, worst);
        assertThat(worst).as("票据换取必须 ≤ 1000ms").isLessThanOrEqualTo(1000L);
    }

    @Test
    @DisplayName("QR-01 列表：20 并发 P95 ≤ 2s、P99 ≤ 3s")
    void itemsListMeetsSlo() throws Exception {
        HttpClient c = newClient();
        for (int i = 0; i < 10; i++) {
            get(c, "/api/items");
        }

        long[] sorted = concurrentLatency("/api/items", c);
        long p95 = p95(sorted);
        long p99 = sorted[(int) Math.ceil(sorted.length * 0.99) - 1];
        long max = sorted[sorted.length - 1];
        System.out.printf("/api/items  n=%d  P95=%dms  P99=%dms  max=%dms%n", sorted.length, p95, p99, max);

        assertThat(p95).as("列表 P95 ≤ 2000ms").isLessThanOrEqualTo(2000L);
        assertThat(p99).as("列表 P99 ≤ 3000ms").isLessThanOrEqualTo(3000L);
    }

    @Test
    @DisplayName("QR-01 详情：20 并发 P95 ≤ 2s、P99 ≤ 3s")
    void claimDetailMeetsSlo() throws Exception {
        Account applicant = accountMapper.selectById(users.get(0));
        HttpClient c = login(applicant.getLoginName());
        for (int i = 0; i < 10; i++) {
            get(c, "/api/claims/" + claimId);
        }

        long[] sorted = concurrentLatency("/api/claims/" + claimId, c);
        long p95 = p95(sorted);
        long p99 = sorted[(int) Math.ceil(sorted.length * 0.99) - 1];
        long max = sorted[sorted.length - 1];
        System.out.printf("/api/claims/{id}  n=%d  P95=%dms  P99=%dms  max=%dms%n", sorted.length, p95, p99, max);

        assertThat(p95).as("详情 P95 ≤ 2000ms").isLessThanOrEqualTo(2000L);
        assertThat(p99).as("详情 P99 ≤ 3000ms").isLessThanOrEqualTo(3000L);
    }
}
