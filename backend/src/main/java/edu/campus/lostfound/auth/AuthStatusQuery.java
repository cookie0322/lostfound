package edu.campus.lostfound.auth;

import edu.campus.lostfound.data.entity.Account;
import edu.campus.lostfound.data.mapper.AccountMapper;
import org.springframework.stereotype.Component;

@Component
public class AuthStatusQuery {
    private final AccountMapper accountMapper;

    public AuthStatusQuery(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public boolean isVerified(long userId) {
        Account account = accountMapper.selectById(userId);
        return account != null && account.getAuthState() != null && account.getAuthState() == AuthState.VERIFIED.code();
    }
}