-- ============================================================================
-- 第三十九轮（W39，2026-10-12）：新建 `academic_warning_course`——预警的「挂科课程明细」
--
-- 为什么：用户报「教师端和管理员端预警信息和课程信息对应不上」。实测（直连服务 + 活库）
-- 根因不在前端，而在**预警表根本没有课程这一维**：
--   · `academic_warning` 只有 student_id（= student_profile.id）。预警是**按学生**聚合生成的
--     （warning_rules 的 4 条规则全是"入学以来累计挂科 N 门"，statistic_cycle=full_program），
--     "挂了哪几门课"只以**散文**形式躺在 description / warning_message 里。
--   · 教师端 GET /teachers/warnings/{teacherId} 按**班级**过滤（classes.teacher_id → 该班学生
--     → 全部预警），而"我的课程"来自 courses.teacher_id —— 两条路不相交。于是于凤海（班主任，
--     uid 29）看到 4 条预警，但正文点名的课大部分**不是他教的**：
--         id=395 司明 操作系统40(刘教授)/计算机基础47(李老师)/高等数学56.5(于凤海)
--         id=396 李爽 计算机网络47(李老师)/计算机基础52(李老师)/高等数学54(于凤海)
--         id=397 张健 计算机基础48(李老师)/计算机网络49(李老师)/高等数学52(于凤海)
--         id=398 韩坤 操作系统54(刘教授)/数据结构59(于凤海)
--   · 管理员端 GET /admin/warnings 回裸实体、没有 className ⇒ AdminWarnings.vue 的班级列恒为 `--`。
--   · PC 学生端 Warnings.vue 是**唯一**显示"关联课程"的地方，而它是用正则从 description 里
--     **抠**出来的（extractCourseName）—— 编造。
--
-- 本表把「预警 ↔ 课程」变成可查、可回读的关系。写入方只有扫描生成预警那一刻
-- （WarningScheduledTask.createWarning → WarningWriter，同一事务）；读取方是
-- com.academic.mapper.AcademicWarningCourseMapper（放 academic-common，12 个服务共用）。
--
-- ⚠️ 本表是**派生数据**，只在生成预警时写一次；成绩后来变了（补考通过/改分）它不跟着改——
--    那种情况由 releaseRecoveredWarnings 把整条预警标成 status=2（已解除），本表随之失去意义。
--
-- ⚠️ 新建表要**两处都改**：这里 + `academic-common/src/main/resources/init_schema.sql`。
--    `db_backups/rebuild_schema.py` 已于 2026-10-12（W39）**整块删除** —— 它从静态 dump 重建
--    init_schema.sql，会把本表连同 admin_tasks / course_recommendation / user_settings 一起抹掉。
--    `init_schema.sql` 现在是唯一权威结构（现场见 FEATURES.md §8.14）。
--
-- ⚠️ 本表**没有**加进 DataInitializer.EXPECTED_COLUMNS，这是**有意的**：common jar 被
--    admin/ai/college/counselor/course 5 个服务加载，若它们的活库还没有这张表，自检会把它
--    报成"(表不存在)"而变红。student_settings 是先例（init_schema.sql 有表、清单里没有）。
--
-- 可重复执行（CREATE TABLE IF NOT EXISTS + upsert 回填）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 建表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `academic_warning_course` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `warning_id` bigint NOT NULL COMMENT '关联 academic_warning.id',
    `course_id` bigint NOT NULL COMMENT '关联 courses.id（不加外键，见说明）',
    `course_name` varchar(100) DEFAULT NULL COMMENT '生成时的课程名快照',
    `score_total` decimal(5,2) DEFAULT NULL COMMENT '生成预警时的总评成绩',
    `semester` int DEFAULT NULL COMMENT '该门挂科成绩所属学期',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_awc_warning_course` (`warning_id`,`course_id`),
    KEY `idx_awc_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '预警关联挂科课程明细表';

-- ---------------------------------------------------------------------------
-- 2) 回填历史预警（让演示数据不再自相矛盾）
--
-- 不回填的话，司明 id=395 的明细为空 ⇒ 教师端"涉及我教的课"筛不到它，
-- 而它的正文白纸黑字写着高等数学 —— 比改动之前更自相矛盾。
--
-- 口径与生成路径**完全一致**：ScoreCaliber.LATEST_VALID_SCORE（final_score IS NOT NULL，
-- 每 (student, course) 取 semester DESC, id DESC 的最新一次）+ score_total < 60。
-- 不硬编码预警 id —— 对**所有**预警重建，将来重扫出来的新行也走同一条 SQL 就是幂等的。
-- ---------------------------------------------------------------------------
INSERT INTO `academic_warning_course` (warning_id, course_id, course_name, score_total, semester)
SELECT aw.id, v.course_id, c.name, v.score_total, v.semester
  FROM `academic_warning` aw
  JOIN (
        SELECT lat.student_id, lat.course_id, lat.score_total, lat.semester
          FROM (SELECT s.id, s.student_id, s.course_id, s.semester, s.score_total,
                       ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id
                                          ORDER BY s.semester DESC, s.id DESC) AS rn
                  FROM `scores` s WHERE s.final_score IS NOT NULL) lat
         WHERE lat.rn = 1
       ) v ON v.student_id = aw.student_id AND v.score_total < 60
  LEFT JOIN `courses` c ON c.id = v.course_id
ON DUPLICATE KEY UPDATE course_name = VALUES(course_name),
                        score_total = VALUES(score_total),
                        semester    = VALUES(semester);

-- ---------------------------------------------------------------------------
-- 3) 核对（判据，也是回归钉子）
-- ---------------------------------------------------------------------------
-- (a) 明细行数应逐条等于该预警的 fail_count —— 因为 fail_count 与上面的
--     LATEST_VALID_SCORE 口径本就同源，两者**应当**相等。不等就是其中一处口径漂了。
--     期望 4 行，且每一行 detail_rows = fail_count：
SELECT aw.id AS warning_id, aw.student_name, aw.fail_count,
       COUNT(awc.id) AS detail_rows,
       CASE WHEN COUNT(awc.id) = aw.fail_count THEN 'OK' ELSE 'MISMATCH' END AS verdict
  FROM `academic_warning` aw
  LEFT JOIN `academic_warning_course` awc ON awc.warning_id = aw.id
 GROUP BY aw.id, aw.student_name, aw.fail_count
 ORDER BY aw.id;
--     期望：4 行，全部 OK；总计 12 行明细（3+3+3+2）。

-- (b) 逐条明细（人工核对是否与 description 正文点名的课一致）：
-- SELECT awc.warning_id, aw.student_name, awc.course_id, awc.course_name,
--        awc.score_total, awc.semester, c.teacher_id AS course_teacher_uid
--   FROM academic_warning_course awc
--   JOIN academic_warning aw ON aw.id = awc.warning_id
--   LEFT JOIN courses c ON c.id = awc.course_id
--  ORDER BY awc.warning_id, awc.score_total;
