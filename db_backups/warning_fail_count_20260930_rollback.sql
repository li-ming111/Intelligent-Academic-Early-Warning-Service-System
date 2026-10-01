-- ============================================================
-- 回滚：撤销 2026-09-30 给 academic_warning 加的 fail_count 列
-- 对应：warning_fail_count_20260930_apply.sql
-- ============================================================
-- ⚠️ 回滚前必须先回滚**代码**（把 ScoreReadMapper.findExistingWarningKeys
--    与 WarningScheduledTask 的判重口径改回去），否则 Mapper 会 SELECT 一个
--    不存在的列，预警扫描整体报错。
--
-- 加列是纯新增，没有改动任何既有列，所以 DROP COLUMN 不会丢业务数据 ——
-- 丢的只是"判重键的一半"，行为退回"处理掉的预警会被下一轮扫描复活"。
-- ============================================================

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'academic_warning'
     AND COLUMN_NAME  = 'fail_count'
);

SET @ddl := IF(@has_col = 1,
  'ALTER TABLE academic_warning DROP COLUMN fail_count',
  'SELECT ''fail_count 列不存在，无需回滚'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 核对：列已消失
SELECT COUNT(*) AS fail_count_col_remaining
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'academic_warning'
   AND COLUMN_NAME  = 'fail_count';
