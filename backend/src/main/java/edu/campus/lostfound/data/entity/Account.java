package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号（对应架构 表附 1-1，已按 SSO 口径修正）。
 *
 * <p>与表附 1-1 的 4 处补充及理由：
 * <ul>
 *   <li>{@code student_no_hash} 改为可空：本地账号先存在，通过 SSO 绑定后才写入学号；
 *       若按原表的 NOT NULL，FR-03"学号被他人绑定→申诉"永远不会发生，成为死代码。</li>
 *   <li>{@code login_name}：既有登录模块的账号名，本功能只读，用于模拟该模块。</li>
 *   <li>新增 {@code role}：管理员申诉解绑与仲裁裁决需要角色判定（QR-03 越权 403）。</li>
 *   <li>{@code created_at}：审计与排障需要，原表只给了 updated_at。</li>
 * </ul>
 * <p>按 QR-02 / QR-04，本表不保存任何联系方式（2026-11-05 起删除手机号相关字段）。
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

    /**
     * 学号 SHA-256（不存明文），UNIQUE，NULL 表示尚未绑定。
     * <p>updateStrategy = ALWAYS：MyBatis-Plus 默认会跳过 null 字段，
     * 解绑时想把这一列改回 NULL 就必须显式声明，否则 UPDATE 里根本没有这个字段。
     */
    @TableField(value = "student_no_hash", updateStrategy = FieldStrategy.ALWAYS)
    private String studentNoHash;

    /** 姓名，展示时脱敏为"首字 + *" */
    private String realName;

    /** 0 未认证 / 1 已认证（FR-04） */
    private Integer authState;

    /** USER / ADMIN */
    private String role;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
