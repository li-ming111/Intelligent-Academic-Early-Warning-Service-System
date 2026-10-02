-- ============================================================================
-- 第三十一轮（W1.14，2026-10-02）：回滚 `counselor_class_application.reject_reason`
--
-- 与 `class_application_reject_reason_20261002_apply.sql` 成对。
--
-- 🔴 这一列**删掉就是真的删掉**：如果已经用它写过拒绝理由（管理员拒绝过某条辅导员申请），
--    那些文字会随列一起消失，无法从别处恢复。回滚前先确认：
--      SELECT id, status, reject_reason FROM counselor_class_application WHERE reject_reason IS NOT NULL;
--    有行的话先导出（`mysqldump` 或 `SELECT ... INTO OUTFILE`）再执行本脚本。
--
-- 回滚后要把 `AdminController.rejectClassRequest` 里写这一列的那几行一起退掉，
-- 否则每次拒绝都会 `Unknown column 'reject_reason' in 'field list'`。
--
-- 可重复执行。
-- ============================================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'counselor_class_application'
      AND COLUMN_NAME = 'reject_reason'
);

SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE `counselor_class_application` DROP COLUMN `reject_reason`',
    'SELECT ''counselor_class_application.reject_reason 不存在，跳过'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
