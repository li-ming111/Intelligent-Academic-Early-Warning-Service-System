-- user_settings 部分更新探针的回滚（2026-09-29）。
--
-- 探针做了什么（testsprite/local-check 之外的临时脚本，结论记在下一条注释里）：
--   1. GET  /api/users/1/settings  → 返回 id=null 的**默认值**（说明 user 1 当时没有设置行）
--   2. PUT  /api/users/1/settings  body 只发 {emailReminder:true}
--      → 返回 id=4，且 warningNotification 等四个开关保持原值不变
--      → 结论：**PUT 是部分更新（null 字段不进 SET），不是整表覆盖**
--   3. PUT 把 emailReminder 改回 false（值还原）
--
-- 但第 2 步**插了一行**（id=4），这是"从没有行"变成"有行"的实质变化 ——
-- 值虽然等于默认值，库里却多了一行、多了一对时间戳。按"演示库要经得起追问"的标准，
-- 这一行必须删掉，让状态回到"没有设置行、接口返回默认值"。
--
-- 前置快照：db_backups/user_settings_20260929_after_probe.sql（含那行被污染的数据）
--
-- 用法：mysql -uroot -proot academic_warning_system < user_settings_20260929_probe_rollback.sql

DELETE FROM user_settings WHERE id = 4 AND user_id = 1;

-- 复核：应只剩 user 53（辅导员）与 user 18（教师）两行，且没有 user_id = 1
SELECT id, user_id, email_reminder, created_at FROM user_settings ORDER BY id;

-- NOTE：user_settings 的 AUTO_INCREMENT 会停在 5（探针插过一次、删掉了）。
-- 这是自增计数器，不是数据差异；真要去掉，可以显式
--   ALTER TABLE user_settings AUTO_INCREMENT = 4;
-- 但没必要 —— 全库的计数器本来就各不相同，只要行内容对得上就行。
