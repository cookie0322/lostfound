-- =====================================================================
-- 01-schema.sql  建表 + 授权（以 root 执行，在应用启动前跑一次）
-- 用法： mysql -u root -p lostfound < db/01-schema.sql
--
-- 设计来源：《软件架构设计专项》附件 2 表附 1-1 / 1-3，按 2026-11-03 的
-- 需求变更（SSO 两态认证、认领五态）修正后的最终口径。
-- 与文档原表的 4 处差异及理由见 backend/README.md「数据库口径」一节。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. account  用户账号（表附 1-1）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS account (
    user_id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    -- 既有登录模块（M1 的历史部分）的账号名，本功能只读；
    -- 加这一列是为了让 FR-03「学号已被他人绑定」可被真实触发与验收
    login_name       VARCHAR(32)   NOT NULL COMMENT '本地登录名（既有模块维护，模拟实现）',
    student_no_hash  CHAR(64)      NULL COMMENT '学号 SHA-256；NULL=尚未绑定',
    real_name        VARCHAR(20)   NULL COMMENT '姓名，展示时脱敏为首字+*',
    auth_state       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 未认证 / 1 已认证（FR-04）',
    role             VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    phone_enc        VARBINARY(128) NULL COMMENT '手机号密文（AES-256）',
    contact_visible  TINYINT       NOT NULL DEFAULT 0 COMMENT '0 认领中可见 / 1 始终可见 / 2 不公开',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_account_login_name (login_name),
    -- 一个学号只能绑定一个账号（FR-03）；MySQL 的唯一索引允许多个 NULL，正好表达"未绑定"
    UNIQUE KEY uk_account_student_no (student_no_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户账号';

-- ---------------------------------------------------------------------
-- 2. item  招领信息台账（M2 提供，本功能只读）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS item (
    item_id      BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(100) NOT NULL COMMENT '物品名称',
    category     VARCHAR(20)  NOT NULL COMMENT '钥匙/雨伞/水杯/教材/文具/衣物',
    place        VARCHAR(50)  NULL COMMENT '拾得地点',
    found_at     DATETIME     NULL COMMENT '拾得时间',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 可认领 / 1 已完成 / 2 已下架',
    publisher_id BIGINT       NOT NULL COMMENT '拾得者账号 = 认领流程的 respondent',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id),
    KEY idx_item_status (status, created_at),
    CONSTRAINT fk_item_publisher FOREIGN KEY (publisher_id) REFERENCES account (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '招领信息台账（M2，只读）';

-- ---------------------------------------------------------------------
-- 3. claim  认领申请（表附 1-3）
--    五态：0 待处理 / 1 待交接 / 2 仲裁中 / 3 已完成 / 4 已结束
--    结束原因：1 被拒绝 / 2 超时 / 3 撤回 / 4 裁决驳回
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS claim (
    claim_id             BIGINT       NOT NULL AUTO_INCREMENT,
    item_id              BIGINT       NOT NULL,
    applicant_id         BIGINT       NOT NULL COMMENT '认领人（失主）',
    respondent_id        BIGINT       NOT NULL COMMENT '应答人（拾得者）',
    state                TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 / 1 待交接 / 2 仲裁中 / 3 已完成 / 4 已结束',
    end_reason           TINYINT      NULL COMMENT '1 被拒绝 / 2 超时 / 3 撤回 / 4 裁决驳回',
    evidence_json        JSON         NOT NULL COMMENT '不少于 3 项特征答案（FR-06）',
    voucher_url          VARCHAR(255) NULL COMMENT '凭证文件相对路径',
    voucher_visible_until DATETIME    NULL COMMENT '凭证可见截止（KV-03、QR-04）',
    confirm_a_at         DATETIME     NULL COMMENT '申请人确认时间（FR-11）',
    confirm_r_at         DATETIME     NULL COMMENT '拾得者确认时间（FR-11）',
    expire_at            DATETIME     NOT NULL COMMENT '超时时间：创建+24h，进入待交接后+72h',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (claim_id),
    -- 超时扫描任务：按状态 + 过期时间取候选（FR-10）
    KEY idx_claim_expire (state, expire_at),
    KEY idx_claim_applicant (applicant_id, state),
    KEY idx_claim_respondent (respondent_id, state),
    KEY idx_claim_item (item_id),
    CONSTRAINT fk_claim_item FOREIGN KEY (item_id) REFERENCES item (item_id),
    CONSTRAINT fk_claim_applicant FOREIGN KEY (applicant_id) REFERENCES account (user_id),
    CONSTRAINT fk_claim_respondent FOREIGN KEY (respondent_id) REFERENCES account (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '认领申请';

-- ---------------------------------------------------------------------
-- 4. claim_hold  占用（表附 1-3 + 架构 3.1 决策 12）
--    只存活跃占用：申请时插入、进入终态时删除
--    因此 item_id 上是普通唯一索引，不需要 released_at / active_flag
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS claim_hold (
    hold_id    BIGINT   NOT NULL AUTO_INCREMENT,
    claim_id   BIGINT   NOT NULL,
    item_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hold_id),
    UNIQUE KEY uk_hold_claim (claim_id),
    -- FR-07 / QR-09：同一招领信息在同一时刻最多 1 条进行中的认领
    UNIQUE KEY uk_hold_item (item_id),
    CONSTRAINT fk_hold_claim FOREIGN KEY (claim_id) REFERENCES claim (claim_id) ON DELETE CASCADE,
    CONSTRAINT fk_hold_item FOREIGN KEY (item_id) REFERENCES item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '活跃占用（并发互斥的唯一裁决点）';

-- ---------------------------------------------------------------------
-- 5. claim_transition  状态转移表（QR-06：改状态只改表，不改流程代码）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS claim_transition (
    transition_id BIGINT      NOT NULL AUTO_INCREMENT,
    from_state    TINYINT     NOT NULL,
    event_code    VARCHAR(32) NOT NULL COMMENT 'ACCEPT/REJECT/WITHDRAW/TIMEOUT/CONFIRM/BOTH_CONFIRMED/TO_ARBITRATION/ARBITRATE_RETURN/ARBITRATE_REJECT',
    to_state      TINYINT     NOT NULL,
    guard_name    VARCHAR(40) NULL COMMENT 'ClaimGuard 中的业务条件名',
    enabled       TINYINT     NOT NULL DEFAULT 1,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (transition_id),
    UNIQUE KEY uk_transition (from_state, event_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '认领状态转移表';

-- ---------------------------------------------------------------------
-- 6. audit_log  审计日志（QR-08：只增不改）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id      BIGINT      NOT NULL AUTO_INCREMENT,
    target_type   VARCHAR(20) NOT NULL COMMENT 'ACCOUNT / CLAIM',
    target_id     BIGINT      NOT NULL,
    action        VARCHAR(40) NOT NULL COMMENT 'BIND/UNBIND/APPEAL_UNBIND/ACCEPT/REJECT/...',
    from_value    VARCHAR(64) NULL COMMENT '变更前值',
    to_value      VARCHAR(64) NULL COMMENT '变更后值',
    operator_id   BIGINT      NULL,
    operator_role VARCHAR(16) NULL COMMENT 'USER / ADMIN / SYSTEM',
    detail        JSON        NULL COMMENT '理由、结束原因等',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_audit_target (target_type, target_id, created_at),
    KEY idx_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '审计日志（只增不改）';

-- ---------------------------------------------------------------------
-- 7. notice  站内信（FR-14）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notice (
    notice_id   BIGINT       NOT NULL AUTO_INCREMENT,
    receiver_id BIGINT       NOT NULL,
    claim_id    BIGINT       NULL,
    node        VARCHAR(20)  NOT NULL COMMENT 'APPLIED/VERDICT/CONFIRM/COMPLETED/CLOSED',
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(500) NOT NULL COMMENT '脱敏，不含对方姓名与联系方式',
    read_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notice_id),
    KEY idx_notice_receiver (receiver_id, read_at, created_at),
    CONSTRAINT fk_notice_receiver FOREIGN KEY (receiver_id) REFERENCES account (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '站内信';

-- =====================================================================
-- 授权：应用账号按表最小授权；audit_log 只给 INSERT + SELECT
-- 这样"审计只增不改"不是靠自觉，而是数据库层面改不动（QR-08 机械保证）
-- =====================================================================
GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.account          TO 'lf_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.item             TO 'lf_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.claim            TO 'lf_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.claim_hold       TO 'lf_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.claim_transition TO 'lf_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.notice           TO 'lf_app'@'localhost';
-- 关键一行：没有 UPDATE / DELETE
GRANT SELECT, INSERT                ON lostfound.audit_log          TO 'lf_app'@'localhost';
FLUSH PRIVILEGES;
