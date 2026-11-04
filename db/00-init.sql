-- =====================================================================
-- 00-init.sql  建库与建应用账号（以 root 执行，只需跑一次）
-- 用法： mysql -u root -p < db/00-init.sql
-- 说明： 这里的口令仅用于本机开发（DC-03 单机部署）；
--        如果要换，同步改 backend/src/main/resources/application.yml 里的
--        DB_USER / DB_PASSWORD，或设置同名环境变量。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS lostfound
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 应用账号：不使用 root 连接数据库（QR-02 的最小权限原则）
CREATE USER IF NOT EXISTS 'lf_app'@'localhost' IDENTIFIED BY 'lf_dev_2026';
