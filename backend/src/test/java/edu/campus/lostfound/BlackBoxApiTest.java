package edu.campus.lostfound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.data.entity.ClaimHold;
import edu.campus.lostfound.data.entity.Item;
import edu.campus.lostfound.data.mapper.AccountMapper;
import edu.campus.lostfound.data.mapper.AuditLogMapper;
import edu.campus.lostfound.data.mapper.ClaimHoldMapper;
import edu.campus.lostfound.data.mapper.ClaimMapper;
import edu.campus.lostfound.data.mapper.ItemMapper;
import edu.campus.lostfound.data.mapper.NoticeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 黑盒测试：只通过 HTTP 接口观察行为，不关心内部实现。
 *
 * <p>用例设计方法：
 * <ul>
 *   <li><b>等价类划分</b>：已登录/未登录、管理员/普通用户、已认证/未认证；</li>
 *   <li><b>边界值</b>：特征项数 2（不合法）/ 3（合法下界）；</li>
 *   <li><b>错误推测</b>：重复申请同一信息、越权访问管理端、删除审计日志；</li>
 *   <li><b>安全/隐私检查</b>：响应体中不得出现明文姓名与手机号。</li>
 * </ul>
 * 覆盖 QR-02 / QR-03 / QR-04 / QR-08 与 FR-03、FR-06、FR-07 的错误分支。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BlackBoxApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
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
    @Autowired
    private AuditLogMapper auditLogMapper;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> items = new ArrayList<>();
    private final List<Long> claims = new ArrayList<>();

    private Long publisherId;
    private String applicantLogin;
    private String adminLogin;
    private Long itemId;

    @BeforeEach
    void setUp() {
        publisherId = newAccount("黑盒拾得者", "USER");
        applicantLogin = newAccountWithLogin("黑盒申请人", "USER", 1);
        adminLogin = newAccountWithLogin("黑盒管理员", "ADMIN", 1);

        Item item = new Item();
        item.setTitle("黑盒测试物品");
        item.setCategory("雨伞");
        item.setPlace("测试地点");
        item.setFoundAt(LocalDateTime.now());
        item.setStatus(0);
        item.setPublisherId(publisherId);
        itemMapper.insert(item);
        itemId = item.getItemId();
        items.add(itemId);
    }

    @AfterEach
    void tearDown() {
        // 先按物品把认领清干净，再删物品（claim→item 有外键，顺序错了删不掉）
        for (Long iid : items) {
            List<Long> cids = claimMapper.selectList(
                            new LambdaQueryWrapper<Claim>().eq(Claim::getItemId, iid))
                    .stream().map(Claim::getClaimId).toList();
            for (Long cid : cids) {
                claimHoldMapper.delete(new LambdaQueryWrapper<ClaimHold>().eq(ClaimHold::getClaimId, cid));
                claimMapper.deleteById(cid);
            }
            itemMapper.deleteById(iid);
        }
        for (Long uid : users) {
            noticeMapper.delete(new LambdaQueryWrapper<edu.campus.lostfound.data.entity.Notice>()
                    .eq(edu.campus.lostfound.data.entity.Notice::getReceiverId, uid));
            accountMapper.deleteById(uid);
        }
    }

    private Long newAccount(String name, String role) {
        Account a = new Account();
        a.setLoginName("b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        a.setRealName(name);
        a.setAuthState(1);
        a.setRole(role);
        a.setCreatedAt(LocalDateTime.now());
        accountMapper.insert(a);
        users.add(a.getUserId());
        return a.getUserId();
    }

    private String newAccountWithLogin(String name, String role, int authState) {
        Account a = new Account();
        a.setLoginName("b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        a.setRealName(name);
        a.setAuthState(authState);
        a.setRole(role);
        a.setCreatedAt(LocalDateTime.now());
        accountMapper.insert(a);
        users.add(a.getUserId());
        return a.getLoginName();
    }

    private MockHttpSession login(String loginName) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    @DisplayName("QR-03 等价类：未登录访问受保护接口 → 401")
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/claims/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("QR-03 等价类：普通用户访问管理端 → 403，且写入 ACCESS_DENIED 审计")
    void nonAdminGets403AndAudited() throws Exception {
        MockHttpSession session = login(applicantLogin);

        mvc.perform(get("/api/admin/appeals").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        Long denied = auditLogMapper.selectCount(
                new LambdaQueryWrapper<edu.campus.lostfound.data.entity.AuditLog>()
                        .eq(edu.campus.lostfound.data.entity.AuditLog::getAction, "ACCESS_DENIED"));
        assertThat(denied).as("越权必须留痕（QR-03）").isGreaterThan(0L);
    }

    @Test
    @DisplayName("QR-03 等价类：管理员访问管理端 → 200")
    void adminGets200() throws Exception {
        MockHttpSession session = login(adminLogin);
        mvc.perform(get("/api/admin/appeals").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("FR-06 边界值：特征 2 项 → 400；第 3 项补上 → 200")
    void featureCountBoundary() throws Exception {
        MockHttpSession session = login(applicantLogin);

        mvc.perform(post("/api/claims").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"features\":[\"特征一\",\"特征二\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        MvcResult ok = mvc.perform(post("/api/claims").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"features\":[\"特征一\",\"特征二\",\"特征三\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        Number claimId = com.jayway.jsonpath.JsonPath.read(ok.getResponse().getContentAsString(), "$.data.claimId");
        claims.add(claimId.longValue());
    }

    @Test
    @DisplayName("FR-07 错误推测：同一信息被占用后再申请 → 409")
    void secondApplyConflicts() throws Exception {
        MockHttpSession session = login(applicantLogin);

        MvcResult first = mvc.perform(post("/api/claims").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"features\":[\"甲\",\"乙\",\"丙\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        Number firstId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.data.claimId");
        claims.add(firstId.longValue());

        mvc.perform(post("/api/claims").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"features\":[\"甲\",\"乙\",\"丙\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        // QR-10：失败的那次必须整体回滚，不能在 claim 表留下半完成记录
        Long rows = claimMapper.selectCount(
                new LambdaQueryWrapper<Claim>().eq(Claim::getItemId, itemId));
        assertThat(rows).as("冲突失败后只应剩下成功的那 1 条").isEqualTo(1L);
    }

    @Test
    @DisplayName("QR-02/QR-04 安全：认证响应里没有明文姓名与手机号")
    void responseHasNoPlainNameOrPhone() throws Exception {
        MockHttpSession session = login(applicantLogin);
        String body = mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).as("不得出现明文姓名").doesNotContain("黑盒申请人");
        assertThat(body).as("不得出现手机号").doesNotContainPattern("1[3-9]\\d{9}");
    }

    @Test
    @DisplayName("QR-08 白盒佐证：应用账号无删除审计日志的权限")
    void auditLogIsAppendOnly() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .as("数据库授权层面就不允许删审计（只增不改）")
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
