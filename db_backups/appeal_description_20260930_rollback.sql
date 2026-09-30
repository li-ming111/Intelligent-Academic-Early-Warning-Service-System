-- ============================================================
-- 回滚：appeal_description_20260930_apply.sql
-- ============================================================
-- 加列是纯增量、不改动任何既有值，所以回滚就是删列。
--
-- ⚠️ 回滚会丢掉**回滚之前**提交的申诉正文（新代码写进去的 description）。
--    要留证据就先把第 1 步的输出抄下来。
--
-- ⚠️ 本脚本要能**重复执行**（列已经没了时再跑一次不能报错）。
--    第一版不是这样：开头那句 `SELECT ... description ...` 在列已删掉之后
--    会报 `ERROR 1054 Unknown column 'description'`，而脚本注释里却写着"幂等"。
--    现在两句都挂在 @has_col 判断后面，实测连跑两次都干净。
-- ============================================================

-- 0) 列在不在？（下面两句都靠它决定跑不跑）
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'score_appeals'
     AND COLUMN_NAME  = 'description'
);

-- 1) 先把将被丢掉的内容打出来（列不存在时给一句提示，不报错）
SET @q_show := IF(@has_col = 1,
  'SELECT id, student_id, reason, LEFT(description, 200) AS description_head FROM score_appeals WHERE description IS NOT NULL',
  'SELECT ''description 列本来就不存在，没有内容会被丢掉'' AS note');

PREPARE s1 FROM @q_show;
EXECUTE s1;
DEALLOCATE PREPARE s1;

-- 2) 删列
SET @q_drop := IF(@has_col = 1,
  'ALTER TABLE score_appeals DROP COLUMN description',
  'SELECT ''description 列本来就不存在，跳过 DROP'' AS note');

PREPARE s2 FROM @q_drop;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- 3) 核对：列应已消失
SELECT COUNT(*) AS should_be_zero
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'score_appeals'
   AND COLUMN_NAME  = 'description';
