package edu.campus.lostfound.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.data.mapper.AccountMapper;
import edu.campus.lostfound.web.BizException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BindingAppService {

    private final AuthApi authApi;
    private final AuthStateMachine stateMachine;
    private final AccountMapper accountMapper;

    public BindingAppService(AuthApi authApi, AuthStateMachine stateMachine, AccountMapper accountMapper) {
        this.authApi = authApi;
        this.stateMachine = stateMachine;
        this.accountMapper = accountMapper;
    }

    public AccountView login(String loginName, HttpSession session) {
        Account account = accountMapper.selectOne(
                new LambdaQueryWrapper<Account>().eq(Account::getLoginName, loginName)
        );
        if (account == null) {
            throw BizException.notFound("用户不存在");
        }

        session.setAttribute("CURRENT_USER_ID", account.getUserId());
        session.setAttribute("CURRENT_ROLE", account.getRole() != null ? account.getRole() : "USER");

        return AccountView.of(account);
    }

    /**
     * 绑定：改状态 + 写审计必须在同一个事务里（QR-10：不产生半完成记录）。
     * 并发撞车不靠这里判断，靠 uk_account_student_no 唯一索引，冲突由全局异常处理器转 409。
     */
    @Transactional
    public AccountView bind(long userId, String ticket) {
        Account account = accountMapper.selectById(userId);
        if (account == null) {
            throw BizException.notFound("未登录或用户不存在");
        }
        if (account.getAuthState() != null && account.getAuthState() == AuthState.VERIFIED.code()) {
            throw BizException.conflict("当前账号已完成认证");
        }

        SsoIdentity identity = authApi.exchange(ticket);
        String hashedStudentNo = HashUtils.hash256(identity.studentNo());

        // 预检只为给出友好文案；真正的裁决是唯一索引（并发下预检会同时通过）
        Account existing = accountMapper.selectOne(
                new LambdaQueryWrapper<Account>().eq(Account::getStudentNoHash, hashedStudentNo)
        );
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw BizException.conflict("该学号已被绑定，可向管理员申诉");
        }

        stateMachine.bind(account, identity);
        accountMapper.updateById(account);

        return AccountView.of(account);
    }

    @Transactional
    public AccountView unbind(Long userId, boolean confirm) {
        if (!confirm) {
            throw BizException.badRequest("请二次确认解绑");
        }
        Account account = accountMapper.selectById(userId);
        if (account == null) throw BizException.notFound("用户不存在");

        stateMachine.unbind(account, userId, "USER", "用户主动解绑");

        accountMapper.updateById(account);
        return AccountView.of(account);
    }

    @Transactional
    public AccountView adminUnbind(Long adminId, Long userId, boolean confirm) {
        if (!confirm) {
            throw BizException.badRequest("请二次确认解绑");
        }
        Account account = accountMapper.selectById(userId);
        if (account == null) throw BizException.notFound("用户不存在");

        stateMachine.unbind(account, adminId, "ADMIN", "强制解榜解绑");

        accountMapper.updateById(account);
        return AccountView.of(account);

    }

    public AccountView me(long userId) {
        Account account = accountMapper.selectById(userId);
        if (account == null) {
            throw BizException.notFound("未登录或用户不存在");
        }
        return AccountView.of(account);
    }


}
