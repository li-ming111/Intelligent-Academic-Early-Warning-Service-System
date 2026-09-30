-- ============================================================
-- 2026-09-30 修复：帮扶评价的两个「收集了却没地方放」的字段
-- ============================================================
-- 现象（实测，非代码判读）：PC 端 `views/student/AssistanceFeedback.vue` 的
--   评价弹窗收集三样东西 —— 计划评分、**有效性评分**、反馈内容，
--   提交体是 { planId, studentId, rating, effectiveness, feedback }。
--   但 `POST /students/evaluations/submit` 只读 planId / counselorId / **score** / content：
--     · `rating`         —— key 名字不对，静默丢弃（真名是 `score`）
--     · `feedback`       —— key 名字不对，静默丢弃（真名是 `content`）
--     · `effectiveness`  —— **后端连列都没有**，无处可写
--   实测（用页面原样的提交体打一次）：响应 `{"success":true,"message":"评价提交成功"}`，
--   而库里那一行是 `plan_id=1001, counselor_id=NULL, score=NULL, content=NULL`
--   —— 学生点的 4 分、5 分有效性和整段反馈，一个都没进去，且 HTTP 200 / code 200 全程无错。
--
-- 本脚本只解决其中**这一件**：给有效性评分一个真正的列。
--   `rating`/`feedback` 的 key 改名与 counselor_id 的取值属于代码问题，在同一个 commit 里改代码。
--
-- 为什么另开一列而不是复用 `score`：`score` 是「对帮扶计划本身的评分」，
-- `effectiveness` 是「帮扶对成绩提升是否有效」——页面上是并排的两个 el-rate，
-- 统计卡片也分成「平均评分」与「有效性评分」两张。合成一列等于让两个问题共用一个答案。
--
-- 为什么可空：加列之前提交的评价本来就没有这一项（页面的 key 从来没被读进去过），
--   给历史行编一个 5 分等于伪造学生的评价。NULL = 「当时没收集到」，是事实。
--   前端不把 NULL 当 0 显示（见 evaluationToMap 与 AssistanceFeedback.vue）。
--
-- 类型定为 INT：页面原来给两个 el-rate 都开了 `allow-half`，但 `score` 列本来就是 INT
--   （MySQL 会把 3.5 静默四舍五入成 4 —— 学生选 3.5 分、库里存 4 分、页面显示 4 分）。
--   所以同一个 commit 里把前端两处 `allow-half` 去掉，后端对非整数评分**报错而不是取整**。
--
-- 影响面：assistance_evaluations 当前 **0 行**（历史探针行已按字面 id 删除、
--   AUTO_INCREMENT 已复原），所以加列不触碰任何既有数据。
--
-- 回滚：evaluation_effectiveness_20260930_rollback.sql（两个脚本都实测可重复执行）
-- 快照：无需 —— 纯加一个可空列，不改动任何既有值。
-- ============================================================

-- 0) 幂等：列已存在就不重复加（MySQL 没有条件 DDL，只能走 prepared statement）
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'assistance_evaluations'
     AND COLUMN_NAME  = 'effectiveness'
);

SET @ddl := IF(@has_col = 0,
  'ALTER TABLE assistance_evaluations ADD COLUMN effectiveness INT NULL COMMENT ''帮扶有效性评分 1-5（学生对"这次帮扶是否真的有用"的独立打分）；加列前的历史行为 NULL'' AFTER score',
  'SELECT ''effectiveness 列已存在，跳过 ALTER'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1) 核对：列存在、类型/可空/注释如实
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'assistance_evaluations'
   AND COLUMN_NAME  = 'effectiveness';

-- 2) 核对：现有行的 effectiveness 一律 NULL（没有编造出来的评分）
SELECT id, plan_id, counselor_id, score, effectiveness, LEFT(content, 40) AS content_head
  FROM assistance_evaluations;
