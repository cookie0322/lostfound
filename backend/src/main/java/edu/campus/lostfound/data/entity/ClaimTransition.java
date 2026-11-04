package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认领状态转移表（QR-06：状态扩展只改转移表，不改流程代码）。
 *
 * <p>启动时加载到内存，并由定时任务每 60 秒刷新一次，
 * 因此"改配置后 5 分钟内生效、无需重新编译"是可验收的。
 * <p>{@code guardName} 对应 {@code ClaimGuard} 中的一个业务条件；
 * 非法跃迁（表里没有的行）一律拒绝并记审计（FR-09）。
 */
@Data
@TableName("claim_transition")
public class ClaimTransition {

    @TableId(value = "transition_id", type = IdType.AUTO)
    private Long transitionId;

    private Integer fromState;

    /** SUBMIT / ACCEPT / REJECT / WITHDRAW / TIMEOUT / CONFIRM / BOTH_CONFIRMED / TO_ARBITRATION / ARBITRATE_RETURN / ARBITRATE_REJECT */
    private String eventCode;

    private Integer toState;

    /** 该跃迁要求的业务条件名，可为空 */
    private String guardName;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private LocalDateTime updatedAt;
}
