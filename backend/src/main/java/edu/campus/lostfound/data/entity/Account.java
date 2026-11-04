package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号（对应架构 表附 1-1，已按 SSO 口径修正）。
 *
 * <p>与表的差异及理由：
 * <ul>
 *   <li>{@code student_no_hash} 改为可空：本地账号先存在，通过 SSO 绑定后才写入学号；
 *       若按原表的 NOT NULL，FR-03"学号被他人绑定→申诉"永远不会发生，成为死代码。</li>
 *   <li>{@code auth_state} 只有两态（0 未认证 / 1 已认证），按需求分析 FR-04 收敛，
 *       原表的"审核中/已驳回/已冻结"属三元校验时代的残留。</li>
 *   <li>新增 {@code role}：管理员申诉解绑与仲裁裁决需要角色判定（QR-03 越权 403）。</li>
 * </ul>
 */
@Data
@TableName("account")
public class Account {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /**
     * 既有登录模块维护的账号名，本功能只读。
     * <p>为什么需要它：FR-03 要求"学号已被他人绑定时拒绝本次绑定"，
     * 只有当"本地账号"和"学号绑定"是两件事时这个场景才存在；
     * 否则学号只能由 SSO 首次登录创建，FR-03 / FR-05 永远触发不了。
     */
    private String loginName;

    /** 学号 SHA-256（不存明文），UNIQUE，NULL 表示尚未绑定 */
    private String studentNoHash;

    /** 姓名，展示时脱敏为"首字 + *" */
    private String realName;

    /** 0 未认证 / 1 已认证（FR-04） */
    private Integer authState;

    /** USER / ADMIN */
    private String role;

    /** 手机号密文（AES-256，QR-02） */
    private byte[] phoneEnc;

    /** 0 认领中可见 / 1 始终可见 / 2 不公开 */
    private Integer contactVisible;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
