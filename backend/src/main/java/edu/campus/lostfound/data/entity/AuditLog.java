package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志（QR-08）：认证与认领的状态变更、解绑、裁决全部留痕。
 *
 * <p>只增不改：应用侧没有 update / delete 的任何路径；
 * 数据库侧对应用账号只授予 INSERT、SELECT（见 db/schema.sql 末尾）。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;

    /** ACCOUNT / CLAIM */
    private String targetType;

    private Long targetId;

    /** BIND / UNBIND / APPEAL_UNBIND / ACCEPT / REJECT / WITHDRAW / TIMEOUT / CONFIRM / ARBITRATE / VERDICT ... */
    private String action;

    /** 变更前值，例如认证状态或认领状态 */
    private String fromValue;

    /** 变更后值 */
    private String toValue;

    private Long operatorId;

    /** USER / ADMIN / SYSTEM（定时任务） */
    private String operatorRole;

    /** 附加信息（理由、结束原因等），JSON */
    private String detail;

    private LocalDateTime createdAt;
}
