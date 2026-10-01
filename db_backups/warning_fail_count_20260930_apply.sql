-- ============================================================
-- 2026-09-30 修复：已处理的预警会被半小时一次的扫描"复活"
-- ============================================================
-- 现象：把一条预警置成"已处理" → 该生名下不再有 status=0 的预警 →
--       下一轮扫描（每 30 分钟一次，`WarningScheduledTask.incrementalScan`）
--       只要他挂科门数仍 > 0 就**再插一条新的**（新 id、status=0）。
--       页面上表现为：处理完的人半小时后又冒出来，原来那条已处理的还留着，
--       同一个人挂着两条，看起来像重复。
--
-- 根因：判重口径写死在 `ScoreReadMapper.findExistingWarningStudentIds`：
--       `SELECT DISTINCT student_id FROM academic_warning WHERE status = 0`
--       —— 它只认"未处理"，处理完就等于解除了屏蔽。
--       而表里**没有挂科门数这一列**，所以没法判断"这是不是同一个问题"。
--
-- 决定：给 academic_warning 加一列 fail_count，判重键从"学生"改为"学生 + 挂科门数"。
--       语义：同一个问题只报一次；门数变了（问题变严重或变轻）才再生成一条。
--       代码侧同时改 `WarningScheduledTask.runScan` 与 `ScoreReadMapper`。
--
-- 回填：加列之前已存在的行，用**它自己正文里记的门数**回填 ——
--       生成时写进 description 的文案是
--         "%s同学已累计%d门课程不及格（%s），触发预警规则「%s」，系统自动生成预警。"
--       所以 "已累计N门课程不及格" 里的 N 就是**当时那次扫描算出的门数**，
--       这是行内自带的、最忠实的事实来源（而不是拿今天的成绩重算 ——
--       成绩可能已经变了，重算会把"历史记录"改成"今天的猜测"）。
--       回填不到的保持 NULL：代码对 NULL 行按旧口径兜底（仍有未处理的旧行则跳过），
--       所以漏填只会更保守，不会多报。
--
-- 影响面：academic_warning 当前 4 行（id 395-398，均 status=0，一人一条）。
--
-- 回滚：warning_fail_count_20260930_rollback.sql
-- 快照：见本脚本第 2 步打印的"回填前"原值 —— 加列不改动任何既有列。
-- ============================================================

-- 0) 幂等：列已存在就跳过（MySQL 没有条件 DDL，只能走 prepared statement）
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'academic_warning'
     AND COLUMN_NAME  = 'fail_count'
);

SET @ddl := IF(@has_col = 0,
  'ALTER TABLE academic_warning ADD COLUMN fail_count INT NULL COMMENT ''生成该预警时该生的挂科门数（ScoreCaliber 口径）；判重键的一半，加列前的历史行按正文回填'' AFTER rule_id',
  'SELECT ''fail_count 列已存在，跳过 ALTER'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1) 回填前先打印现状（哪些行还是 NULL）
SELECT id, student_id, rule_id, warning_level, status,
       fail_count IS NULL AS fail_count_is_null
  FROM academic_warning
 ORDER BY id;

-- 2) 回填：只填 NULL 行，且正文里确实带着"已累计N门课程不及格"
--    （LIKE 先兜住格式，避免 CAST 到非数字列时静默变 0）
UPDATE academic_warning
   SET fail_count = CAST(
         SUBSTRING_INDEX(
           SUBSTRING_INDEX(description, '累计', -1),
           '门课程不及格', 1) AS UNSIGNED)
 WHERE fail_count IS NULL
   AND description LIKE '%已累计%门课程不及格%';

-- 3) 核对：每一行都该有门数，且回填值能与规则档位对上
--    （规则是 1-2 门→level 1、3-5 门→level 2、>5 门→level 3）
SELECT id, student_id, rule_id, warning_level, fail_count, status,
       CASE
         WHEN fail_count IS NULL THEN 'MISSING'
         WHEN warning_level = 1 AND fail_count BETWEEN 1 AND 2 THEN 'ok'
         WHEN warning_level = 2 AND fail_count BETWEEN 3 AND 5 THEN 'ok'
         WHEN warning_level = 3 AND fail_count > 5 THEN 'ok'
         ELSE 'MISMATCH'
       END AS level_check
  FROM academic_warning
 ORDER BY id;

-- 4) 回填后不该再有整行缺门数（NULL 只允许出现在后续新插入的行上，而新代码一定会写）
SELECT COUNT(*) AS rows_still_null
  FROM academic_warning
 WHERE fail_count IS NULL;
