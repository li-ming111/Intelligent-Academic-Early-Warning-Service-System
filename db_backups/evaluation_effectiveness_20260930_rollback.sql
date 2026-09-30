-- ============================================================
-- 回滚：evaluation_effectiveness_20260930_apply.sql
-- ============================================================
-- 加列是纯增量、不改动任何既有值，所以回滚就是删列。
--
-- ⚠️ 回滚会丢掉**回滚之前**提交的有效性评分（新代码写进去的 effectiveness）。
--    要留证据就先把第 1 步的输出抄下来。
--
-- ⚠️ 本脚本要能**重复执行**（列已经没了时再跑一次不能报错）——
--    同目录的 appeal_description_20260930_rollback.sql 第一版就栽在这里
--    （开头那句直接 SELECT description，列删掉后报 ERROR 1054），
--    所以这里从头就把两句都挂在 @has_col 判断后面。
-- ============================================================

-- 0) 列在不在？（下面两句都靠它决定跑不跑）
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'assistance_evaluations'
     AND COLUMN_NAME  = 'effectiveness'
);

-- 1) 先把将被丢掉的内容打出来（列不存在时给一句提示，不报错）
SET @q_show := IF(@has_col = 1,
  'SELECT id, plan_id, score, effectiveness FROM assistance_evaluations WHERE effectiveness IS NOT NULL',
  'SELECT ''effectiveness 列本来就不存在，没有内容会被丢掉'' AS note');

PREPARE s1 FROM @q_show;
EXECUTE s1;
DEALLOCATE PREPARE s1;

-- 2) 删列
SET @q_drop := IF(@has_col = 1,
  'ALTER TABLE assistance_evaluations DROP COLUMN effectiveness',
  'SELECT ''effectiveness 列本来就不存在，跳过 DROP'' AS note');

PREPARE s2 FROM @q_drop;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- 3) 核对：列应已消失
SELECT COUNT(*) AS should_be_zero
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'assistance_evaluations'
   AND COLUMN_NAME  = 'effectiveness';
