-- =====================================================================
-- 03-migrate-20261105.sql  文档变更后的数据库迁移（以 root 执行，只跑一次）
-- 用法： mysql -u root -p lostfound < db/03-migrate-20261105.sql
--
-- 变更来源：2026-11-05 两份文档的修订
--   ① QR-02 / QR-04 改为"不采集任何联系方式" → account 删 phone_enc、contact_visible
--   ② 转仲裁入口放宽：任一方在"待处理"也可转 → 转移表补 1 行
-- =====================================================================

SET NAMES utf8mb4;
USE lostfound;

-- ① 不再保存联系方式（QR-02：库表中无手机号字段）
ALTER TABLE account
    DROP COLUMN phone_enc,
    DROP COLUMN contact_visible;

-- ② 待处理(0) 也可直接转仲裁(2)（FR-12 / 图附 2-2）
INSERT IGNORE INTO claim_transition (from_state, event_code, to_state, guard_name, enabled)
VALUES (0, 'TO_ARBITRATION', 2, 'IS_PARTY_AND_REASON', 1);

-- 校验：预期 0 行
SELECT COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'lostfound' AND TABLE_NAME = 'account'
   AND COLUMN_NAME IN ('phone_enc', 'contact_visible');

-- 校验：预期 12 行
SELECT COUNT(*) AS transitions FROM claim_transition;
