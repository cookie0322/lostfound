-- =====================================================================
-- 02-seed.sql  开发期种子数据（以 root 执行，可重复执行）
-- 用法： mysql -u root -p lostfound < db/02-seed.sql
--
-- 账号说明：
--   login_name 是"既有登录模块"的账号名，本功能只读它，用来模拟
--   《架构设计》表附 1-1 注明的"账号与登录由既有模块维护"。
--   auth_state = 0 表示本地账号已存在但尚未通过 SSO 绑定学号。
-- =====================================================================

SET NAMES utf8mb4;
USE lostfound;

INSERT IGNORE INTO account
    (user_id, login_name, student_no_hash, real_name, auth_state, role)
VALUES
    -- 管理员：申诉解绑、仲裁裁决
    (1, 'admin',  NULL,                              '管理员',  1, 'ADMIN'),
    -- 已认证用户，用于正常认领流程
    (2, 'zhang',  SHA2('2023010101', 256),           '张同学',  1, 'USER'),
    (3, 'li',     SHA2('2023010102', 256),           '李同学',  1, 'USER'),
    -- 未认证用户：从未绑定过学号，用于验收"参与认领必须已认证"（FR-06 / 3.2 认证门槛）
    (4, 'wang',   NULL,                              '王同学',  0, 'USER'),
    -- 学号已被占用的账号，用于验收 FR-03 / FR-05 的申诉链路
    (5, 'zhao',   SHA2('2023010104', 256),           '赵同学',  1, 'USER'),
    -- 第二个本地账号，名下没有学号：尝试绑定 2023010104 时应被拒绝并引导申诉
    (6, 'zhao2',  NULL,                              '赵同学',  0, 'USER');

INSERT IGNORE INTO item
    (item_id, title, category, place, found_at, status, publisher_id)
VALUES
    (1, '黑色长柄雨伞',        '雨伞', '图书馆三楼自习区', '2026-11-01 18:20:00', 0, 4),
    (2, '蓝色保温杯（有贴纸）', '水杯', '一食堂二楼',       '2026-11-02 12:10:00', 0, 3),
    (3, '高等数学教材上册',     '教材', '教三 201',         '2026-11-02 16:40:00', 0, 3),
    (4, '钥匙串（蓝色挂件）',   '钥匙', '操场东侧看台',     '2026-11-03 20:05:00', 0, 4),
    (5, '灰色连帽卫衣',        '衣物', '体育馆更衣室',     '2026-11-03 21:30:00', 0, 3);

-- ---------------------------------------------------------------------
-- 认领状态转移表（12 条，对应 图附 2-2）
-- 说明：提交申请是"无 -> 待处理"，不在这张表里，它由申请接口直接创建。
--       转仲裁按 2026-11-05 的结论：任一方，在待处理或待交接都可转。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO claim_transition (from_state, event_code, to_state, guard_name, enabled)
VALUES
    -- 待处理 0
    (0, 'ACCEPT',           1, 'IS_RESPONDENT_AND_REASON', 1),
    (0, 'REJECT',           4, 'IS_RESPONDENT_AND_REASON', 1),
    (0, 'WITHDRAW',         4, 'IS_PARTY_AND_CONFIRMED',   1),
    (0, 'TIMEOUT',          4, 'EXPIRED_24H',              1),
    (0, 'TO_ARBITRATION',   2, 'IS_PARTY_AND_REASON',      1),
    -- 待交接 1
    (1, 'CONFIRM',          1, 'IS_PARTY_NOT_CONFIRMED',   1),
    (1, 'BOTH_CONFIRMED',   3, 'BOTH_TIMESTAMPS_PRESENT',  1),
    (1, 'WITHDRAW',         4, 'IS_PARTY_AND_CONFIRMED',   1),
    (1, 'TIMEOUT',          4, 'EXPIRED_72H',              1),
    (1, 'TO_ARBITRATION',   2, 'IS_PARTY_AND_REASON',      1),
    -- 仲裁中 2
    (2, 'ARBITRATE_RETURN', 3, 'IS_ADMIN',                 1),
    (2, 'ARBITRATE_REJECT', 4, 'IS_ADMIN',                 1);
