-- ============================================================
-- 2026-09-30 修复：学生写的「详细描述」从来没进过库
-- ============================================================
-- 现象：PC 端两个页面都有申诉的"请详细描述申诉理由"输入框，而且**都是必填**：
--         views/Appeals.vue:196   !form.description → 「请完善申诉信息」，拦着不让提交
--         views/Scores.vue:501    同样拦截
--       提交时也确实发了这个字段（Appeals.vue:199 / Scores.vue:508），
--       但 StudentServiceImpl.submitAppeal 只读
--         studentId / scoreId / reason / courseId / attachments
--       —— request 是 Map，多出来的 key 一声不响地被丢掉。学生写了 200 字，
--       库里只留一个 reason 代码（'entry_error' 之类），正文没了，且全程无报错。
--
-- 为什么不塞进 reason：`reason` 存的是**原因代码**，有两处代码依赖它是枚举：
--       ① 前端 reasonLabel(r) 按代码查中文标签（Appeals.vue:123）
--       ② GET /students/appeals/{id}/reason-statistics 直接 GROUP BY reason 做统计
--       把自由文本拼进去会同时弄坏这两处。所以必须另开一列。
--
-- 决定：给 score_appeals 加一列 description（自由文本，可空）。
--       可空是**刻意**的：加列之前已存在的申诉本来就没有这段文字，编一段填进去
--       等于伪造学生的话。前端按 v-if 判空渲染（Appeals.vue 详情弹窗），
--       空就不显示这一块，不会出现"必填项却是空的"这种自相矛盾。
--
-- 影响面：score_appeals 当前只有 1 行（id=6，司明，reason='entry_error'），
--         加列后该行 description 为 NULL —— 这是事实（它提交时根本没有这一列可写）。
--
-- 回滚：appeal_description_20260930_rollback.sql
-- 快照：无需 —— 加一个可空列不改动任何既有值，DROP COLUMN 即可完全还原。
-- ============================================================

-- 0) 幂等：列已存在就不重复加（MySQL 没有条件 DDL，只能走 prepared statement）
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'score_appeals'
     AND COLUMN_NAME  = 'description'
);

SET @ddl := IF(@has_col = 0,
  'ALTER TABLE score_appeals ADD COLUMN description TEXT COLLATE utf8mb4_unicode_ci NULL COMMENT ''学生自己填写的申诉详细说明（自由文本）；加列前的历史行为 NULL'' AFTER reason',
  'SELECT ''description 列已存在，跳过 ALTER'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1) 核对：列存在、且历史行如实为 NULL（不编造学生的话）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'score_appeals'
   AND COLUMN_NAME  = 'description';

SELECT id, student_id, course_id, reason,
       description IS NULL AS desc_is_null
  FROM score_appeals;
