-- ============================================================================
-- 第三十九轮（W39，2026-10-12）：回滚 `academic_warning_course`
--
-- 撤回 db_backups/academic_warning_course_20261012_apply.sql。
--
-- ⚠️ 这张表是**派生数据**，没有唯一的原件 —— 删掉之后可以用 apply 脚本第 2 段
--    重新从 scores 回填出来。所以这里直接 DROP，不做数据保留。
--    （若想留一手：先 CREATE TABLE bak_w39_academic_warning_course AS SELECT * FROM academic_warning_course;）
--
-- ⚠️ 只回滚**数据层**。代码侧的配套（WarningWriter / AcademicWarningCourseMapper /
--    TeacherController 的 courses 字段）不会因此失效 —— 它们读不到行时表现为
--    "预警没有课程明细"，不报错。
--
-- 可重复执行（DROP TABLE IF EXISTS）。
-- ============================================================================

DROP TABLE IF EXISTS `academic_warning_course`;

-- 执行后应当查不到这张表：
--   SELECT COUNT(*) FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA='academic_warning_system' AND TABLE_NAME='academic_warning_course';
-- 期望 0。
