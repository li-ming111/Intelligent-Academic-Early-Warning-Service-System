-- ============================================================================
-- 批次 5（步骤 A）回滚：把 score_record 的分数恢复成对齐前的样子
--
-- 本批次是纯字段改写（行的增删一条都没有），所以按 id 逐字段还原即可。
-- 用 REPLACE 是因为改写过的列必须被覆盖回去（INSERT IGNORE 只会补缺失行，不覆盖）。
-- ============================================================================

-- 前置检查：备份表必须存在且为 247 行
SELECT COUNT(*) AS backup_rows FROM score_record_bak_align_20260916;

-- 回滚
REPLACE INTO score_record SELECT * FROM score_record_bak_align_20260916;

-- 回滚后自检
SELECT COUNT(*) AS rows_now FROM score_record;

-- 如需撤销"历史行被改写"这一动作但保留合法的新增行，改用下面这条（按 id 只还原备份里的行）：
-- UPDATE score_record sr JOIN score_record_bak_align_20260916 b ON b.id = sr.id
--    SET sr.score = b.score, sr.score_total = b.score_total,
--        sr.regular_score = b.regular_score, sr.final_score = b.final_score,
--        sr.grade = b.grade, sr.grade_point = b.grade_point, sr.score_level = b.score_level,
--        sr.remark = b.remark, sr.semester = b.semester, sr.year = b.year;
