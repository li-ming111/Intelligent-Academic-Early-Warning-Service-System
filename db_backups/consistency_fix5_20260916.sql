-- ============================================================================
-- 批次 5（步骤 A）：把 score_record 的分数对齐到权威表 scores
--
--   背景：按 (学生,课程,学期) 三元组配对，152 行可配对，其中
--         司明 14 行两表分数完全一致；其余 23 名学生的 138 行被加了噪声，
--         且噪声方向恰好把 8 门挂科(韩坤2/李爽3/张健3)漂成了及格(62~71 分)。
--         反方向(用 score_record 覆盖 scores)会让这 8 门挂科全部消失，
--         预期 4 条预警塌成 1 条，所以只能单向对齐 score_record -> scores。
--
--   原则：scores 是权威表。本次只改 score_record，scores 一行不动。
--
--   抄的字段(直接取 authoritative 值，不做任何重新推导)：
--     score         <- scores.score_total     (教师端显示、导出用的是这一列)
--     score_total   <- scores.score_total
--     regular_score <- scores.regular_score
--     final_score   <- scores.final_score
--     grade         <- scores.grade           (已核验 163/163 与 ScoreSyncSupport.scoreToGrade 一致)
--     grade_point   <- scores.grade_point      (已核验 163/163 与 ScoreSyncSupport.scoreToGpa 一致)
--     score_level   <- 由 score_total 按 scoreToLevel(90/80/70/60 -> 1/2/3/4/5) 推出
--                       (scores 没有 score_level 列，只能按同一个函数推)
--
--   不动：id / student_id / course_id / teacher_id / student_name / class_name /
--         semester / year / remark / create_time（update_time 会按 ON UPDATE 自动刷新）
--
--   可回滚：整表已备份到 score_record_bak_align_20260916，
--           回滚脚本 db_backups/rollback_fix5_20260916.sql。
--
--   安全阀：可配对行数不等于 152、或对齐后仍有配对不一致的行、或 score_record 行数变了，
--           一律 ROLLBACK 保持原状。
-- ============================================================================

DROP PROCEDURE IF EXISTS fix5_align;
DELIMITER $$
CREATE PROCEDURE fix5_align()
BEGIN
    DECLARE v_expected_pairs INT DEFAULT 152;
    DECLARE v_rows_before    INT DEFAULT 0;
    DECLARE v_pairs          INT DEFAULT 0;
    DECLARE v_changed        INT DEFAULT 0;
    DECLARE v_left           INT DEFAULT 0;
    DECLARE v_grade_null     INT DEFAULT 0;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_rows_before FROM score_record;

    DROP TABLE IF EXISTS score_record_bak_align_20260916;
    CREATE TABLE score_record_bak_align_20260916 AS SELECT * FROM score_record;

    -- 可配对行数（必须是 152）
    SELECT COUNT(*) INTO v_pairs
      FROM score_record sr
      JOIN scores s ON s.student_id = sr.student_id
                   AND s.course_id  = sr.course_id
                   AND s.semester   = CAST(sr.semester AS UNSIGNED)
     WHERE s.score_total IS NOT NULL;

    -- 权威行的 grade 不能为空，否则抄过去会把教师端弄成空白
    SELECT COUNT(*) INTO v_grade_null
      FROM score_record sr
      JOIN scores s ON s.student_id = sr.student_id
                   AND s.course_id  = sr.course_id
                   AND s.semester   = CAST(sr.semester AS UNSIGNED)
     WHERE s.score_total IS NOT NULL AND s.grade IS NULL;

    -- 对齐
    UPDATE score_record sr
      JOIN scores s ON s.student_id = sr.student_id
                   AND s.course_id  = sr.course_id
                   AND s.semester   = CAST(sr.semester AS UNSIGNED)
       SET sr.score         = s.score_total,
           sr.score_total   = s.score_total,
           sr.regular_score = s.regular_score,
           sr.final_score   = s.final_score,
           sr.grade         = s.grade,
           sr.grade_point   = s.grade_point,
           sr.score_level   = CASE WHEN s.score_total >= 90 THEN 1
                                   WHEN s.score_total >= 80 THEN 2
                                   WHEN s.score_total >= 70 THEN 3
                                   WHEN s.score_total >= 60 THEN 4
                                   ELSE 5 END
     WHERE s.score_total IS NOT NULL;

    SET v_changed = ROW_COUNT();

    -- 复查：配对的行里是否还有任何一列不一致
    SELECT COUNT(*) INTO v_left
      FROM score_record sr
      JOIN scores s ON s.student_id = sr.student_id
                   AND s.course_id  = sr.course_id
                   AND s.semester   = CAST(sr.semester AS UNSIGNED)
     WHERE s.score_total IS NOT NULL
       AND (sr.score         <> s.score_total
         OR NOT (sr.score_total   <=> s.score_total)
         OR NOT (sr.regular_score <=> s.regular_score)
         OR NOT (sr.final_score   <=> s.final_score)
         OR NOT (sr.grade         <=> s.grade)
         OR NOT (sr.grade_point   <=> s.grade_point)
         OR sr.score_level <> CASE WHEN s.score_total >= 90 THEN 1
                                   WHEN s.score_total >= 80 THEN 2
                                   WHEN s.score_total >= 70 THEN 3
                                   WHEN s.score_total >= 60 THEN 4
                                   ELSE 5 END);

    IF v_pairs <> v_expected_pairs
       OR v_left <> 0
       OR v_grade_null <> 0
       OR (SELECT COUNT(*) FROM score_record) <> v_rows_before THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_pairs AS pairs, v_left AS still_mismatched,
               v_grade_null AS authoritative_grade_null, v_rows_before AS `rows`;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_rows_before AS `rows`,
               v_pairs AS pairs, v_changed AS rows_updated, v_left AS still_mismatched;
    END IF;
END$$
DELIMITER ;

CALL fix5_align();
DROP PROCEDURE fix5_align;
