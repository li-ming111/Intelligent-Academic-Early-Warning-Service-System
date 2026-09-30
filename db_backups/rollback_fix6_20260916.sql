-- ============================================================================
-- 批次 6（步骤 B）回滚：删掉本批次补进 scores 的 92 行选修课
--
-- 本批次是纯新增，所以回滚 = 按 (学生,课程,学期=6,分数来源) 精确删掉新增行。
-- 用 id > 备份表最大 id 判定"新增行"，比按内容匹配安全（不会误删原有行）。
-- 前提：备份表 scores_bak_elective_20260916 存在。
-- ============================================================================

-- 前置检查：备份 167 行，live 应为 259 行
SELECT (SELECT COUNT(*) FROM scores_bak_elective_20260916) AS backup_rows,
       (SELECT COUNT(*) FROM scores) AS rows_now,
       (SELECT IFNULL(MAX(id),0) FROM scores_bak_elective_20260916) AS max_id_before;

-- 回滚
DELETE FROM scores WHERE id > (SELECT IFNULL(MAX(id),0) FROM scores_bak_elective_20260916);

-- 回滚后自检：应回到 167 行
SELECT COUNT(*) AS rows_after_rollback FROM scores;

-- 如果之后又插入了别的行（id 会更大），改用下面这条，只删"批次6 那 92 个三元组"：
-- DELETE h FROM scores h JOIN scores_bak_elective_20260916 b
--    ON b.student_id = h.student_id AND b.course_id = h.course_id
-- WHERE h.semester = 6 AND h.id > b.id
--   AND NOT EXISTS (SELECT 1 FROM score_record sr WHERE sr.student_id = h.student_id
--                     AND sr.course_id = h.course_id AND sr.semester IS NULL
--                     AND NOT (sr.score <=> h.score_total));
