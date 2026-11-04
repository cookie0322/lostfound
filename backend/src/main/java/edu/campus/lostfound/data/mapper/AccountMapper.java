package edu.campus.lostfound.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.campus.lostfound.data.entity.Account;

/** 账号表。由 {@code @MapperScan} 统一注册，无需 @Mapper 注解。 */
public interface AccountMapper extends BaseMapper<Account> {
}
