/**
 * 审计模块（QR-08）：状态变更、解绑、裁决统一留痕，记录只增不改。
 *
 * <p>实现方式：认证状态机与认领状态机是唯一的状态写入口，二者都必须经
 * {@code AuditRecorder} 写 audit_log；数据库层再对应用账号只授
 * INSERT/SELECT，形成机械保证（详见 db/schema.sql 末尾的授权说明）。
 */
package edu.campus.lostfound.audit;
