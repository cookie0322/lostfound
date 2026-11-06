-- =====================================================================
-- 04-appeal-20261106.sql  申诉（FR-05）——对已有库执行一次
-- 用法： mysql -u root -p lostfound < db/04-appeal-20261106.sql
--
-- 背景：FR-03 要求"学号已被他人绑定时拒绝并提示可向管理员申诉"，
--       FR-05 要求"用户可就学号被他人绑定向管理员申诉，管理员核实后解除原绑定"。
--       之前只做了管理员强制解绑，缺"用户怎么发起申诉"这一半，本表补上。
-- =====================================================================

SET NAMES utf8mb4;
USE lostfound;

CREATE TABLE IF NOT EXISTS appeal (
    appeal_id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL COMMENT '申诉人（本地账号）',
    student_no_hash  CHAR(64)     NOT NULL COMMENT '申诉声称被占用的学号（哈希）',
    reason           VARCHAR(200) NOT NULL COMMENT '申诉理由',
    state            TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 / 1 已解除 / 2 已驳回',
    handled_by       BIGINT       NULL COMMENT '处理的管理员',
    handled_at       DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (appeal_id),
    KEY idx_appeal_state (state, created_at),
    KEY idx_appeal_user (user_id, student_no_hash),
    CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES account (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '学号占用申诉';

GRANT SELECT, INSERT, UPDATE, DELETE ON lostfound.appeal TO 'lf_app'@'localhost';
FLUSH PRIVILEGES;

SELECT COUNT(*) AS appeals FROM appeal;
