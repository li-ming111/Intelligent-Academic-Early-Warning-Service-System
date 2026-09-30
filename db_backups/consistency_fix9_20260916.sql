-- ============================================================================
-- 批次 9：司明的 2 行选修课归位 + 补齐 score_record.year 的 154 个 NULL
--
--   起因：fix6/fix7 之后，两表只剩司明的课7/课8 无法配对——
--     scores        第 5 学期：课7 86(A-，平时88/期末85)、课8 91(A，平时90/期末92)
--     score_record  第 6 学期：课7 录入85/总评86(B/旧口径)、课8 录入78/总评74(C)
--
--   证据（见 db_backups/elective_semester_evidence.md）：course_plans 把全部 10 门课
--   排在 2025-2026-2，按项目自己的换算就是第 6 个全局学期；另外 23 名同学的 4 门
--   选修课也都已落在第 6 学期。25 名学生里只有司明的写在第 5 学期，属生成时的疏漏。
--   因此按既定方向「scores 为准」处理：
--     1) scores 里司明课7/课8 的 semester 5 -> 6（只改学期列，分数不动）
--     2) score_record 的 291/294 对齐成权威值（86/91），并补上评语
--   完成后 score_record 246 行全部可与 scores 配对，两表再无冲突。
--
--   顺带：score_record.year 有 154 行是 NULL（scores 里同批行都有值），
--   按「学号前四位 + (学期-1)/2」补齐，0 行残留。
--
--   可回滚：scores_bak_sem9_20260916 / score_record_bak_sem9_20260916
--   ⚠️ 事务位置：DDL 必须在 START TRANSACTION 之前（DDL 隐式提交，否则 ROLLBACK 空转）
-- ============================================================================

DROP PROCEDURE IF EXISTS fix9_siming_electives;
DELIMITER $$
CREATE PROCEDURE fix9_siming_electives()
BEGIN
    DECLARE v_sc_before    INT DEFAULT 0;
    DECLARE v_sr_before    INT DEFAULT 0;
    DECLARE v_fail_before  INT DEFAULT 0;
    DECLARE v_5050_before  INT DEFAULT 0;
    DECLARE v_year_before  INT DEFAULT 0;
    DECLARE v_moved        INT DEFAULT 0;
    DECLARE v_aligned      INT DEFAULT 0;
    DECLARE v_year         INT DEFAULT 0;
    DECLARE v_sc_after     INT DEFAULT 0;
    DECLARE v_sr_after     INT DEFAULT 0;
    DECLARE v_fail_after   INT DEFAULT 0;
    DECLARE v_5050_after   INT DEFAULT 0;
    DECLARE v_null_year    INT DEFAULT 0;
    DECLARE v_pair         INT DEFAULT 0;
    DECLARE v_unpair       INT DEFAULT 0;
    DECLARE v_pair_bad     INT DEFAULT 0;
    DECLARE v_dupt         INT DEFAULT 0;
    DECLARE v_sc_changed   INT DEFAULT 0;

    -- ===== 阶段一：DDL 与基线（事务之外）=====
    SELECT COUNT(*) INTO v_sc_before FROM scores;
    SELECT COUNT(*) INTO v_sr_before FROM score_record;
    SELECT COUNT(*) INTO v_fail_before FROM scores WHERE score_total < 60;
    SELECT COUNT(*) INTO v_5050_before FROM scores
     WHERE regular_score IS NOT NULL AND final_score IS NOT NULL
       AND score_total <> ROUND((regular_score + final_score) / 2);
    SELECT COUNT(*) INTO v_year_before FROM score_record WHERE year IS NULL;

    DROP TABLE IF EXISTS scores_bak_sem9_20260916;
    CREATE TABLE scores_bak_sem9_20260916 AS SELECT * FROM scores;
    DROP TABLE IF EXISTS score_record_bak_sem9_20260916;
    CREATE TABLE score_record_bak_sem9_20260916 AS SELECT * FROM score_record;

    -- ===== 阶段二：DML =====
    START TRANSACTION;

    -- 1) 司明课7/课8 归位到第 6 学期（year 本就是 2025，无需改）
    UPDATE scores SET semester = 6
     WHERE student_id = 3 AND course_id IN (7,8) AND semester = 5;
    SET v_moved = ROW_COUNT();

    -- 2) 录入表 291/294 对齐到权威表，并补评语
    UPDATE score_record sr
      JOIN scores h ON h.student_id = sr.student_id AND h.course_id = sr.course_id AND h.semester = 6
       SET sr.score         = h.score_total,
           sr.score_total   = h.score_total,
           sr.regular_score = h.regular_score,
           sr.final_score   = h.final_score,
           sr.grade         = h.grade,
           sr.grade_point   = h.grade_point,
           sr.score_level   = CASE WHEN h.score_total >= 90 THEN 1 WHEN h.score_total >= 80 THEN 2
                                   WHEN h.score_total >= 70 THEN 3 WHEN h.score_total >= 60 THEN 4
                                   ELSE 5 END,
           sr.remark        = CASE sr.course_id WHEN 7 THEN '算法流程梳理清晰'
                                                WHEN 8 THEN '模型训练过程理解深' END
     WHERE sr.id IN (291, 294);
    SET v_aligned = ROW_COUNT();

    -- 3) 补齐 year 的 154 个 NULL
    UPDATE score_record sr
      JOIN student_profile sp ON sp.id = sr.student_id
       SET sr.year = CAST(LEFT(sp.student_id, 4) AS UNSIGNED) + (sr.semester - 1) DIV 2
     WHERE sr.year IS NULL;
    SET v_year = ROW_COUNT();

    -- ===== 校验 =====
    SELECT COUNT(*) INTO v_sc_after FROM scores;
    SELECT COUNT(*) INTO v_sr_after FROM score_record;
    SELECT COUNT(*) INTO v_fail_after FROM scores WHERE score_total < 60;
    SELECT COUNT(*) INTO v_5050_after FROM scores
     WHERE regular_score IS NOT NULL AND final_score IS NOT NULL
       AND score_total <> ROUND((regular_score + final_score) / 2);
    SELECT COUNT(*) INTO v_null_year FROM score_record WHERE year IS NULL;

    -- 全部 246 行都应可配对，且 7 列逐字一致
    SELECT COUNT(*) INTO v_pair FROM score_record sr JOIN scores h
      ON h.student_id = sr.student_id AND h.course_id = sr.course_id AND h.semester = CAST(sr.semester AS UNSIGNED);
    SELECT COUNT(*) INTO v_unpair FROM score_record sr WHERE NOT EXISTS (
      SELECT 1 FROM scores h WHERE h.student_id = sr.student_id AND h.course_id = sr.course_id
        AND h.semester = CAST(sr.semester AS UNSIGNED));
    SELECT COUNT(*) INTO v_pair_bad FROM score_record sr JOIN scores h
      ON h.student_id = sr.student_id AND h.course_id = sr.course_id AND h.semester = CAST(sr.semester AS UNSIGNED)
     WHERE sr.score <> h.score_total OR NOT (sr.score_total <=> h.score_total)
        OR NOT (sr.regular_score <=> h.regular_score) OR NOT (sr.final_score <=> h.final_score)
        OR sr.grade <> h.grade OR NOT (sr.grade_point <=> h.grade_point)
        OR sr.score_level <> CASE WHEN h.score_total >= 90 THEN 1 WHEN h.score_total >= 80 THEN 2
                                  WHEN h.score_total >= 70 THEN 3 WHEN h.score_total >= 60 THEN 4
                                  ELSE 5 END;

    SELECT COUNT(*) INTO v_dupt FROM (
      SELECT 1 FROM scores GROUP BY student_id, course_id, semester HAVING COUNT(*) > 1) g;

    -- 权威表除那 2 行的 semester 外，一个字都不许变
    SELECT COUNT(*) INTO v_sc_changed FROM scores h JOIN scores_bak_sem9_20260916 b ON b.id = h.id
     WHERE NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id)
        OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year)
        OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score)
        OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade)
        OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change)
        OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at);

    IF v_moved <> 2 OR v_aligned <> 2 OR v_year <> v_year_before
       OR v_sc_after <> v_sc_before OR v_sr_after <> v_sr_before
       OR v_fail_after <> v_fail_before OR v_5050_after <> v_5050_before
       OR v_null_year <> 0 OR v_unpair <> 0 OR v_pair <> v_sr_after OR v_pair_bad <> 0
       OR v_dupt <> 0 OR v_sc_changed <> 2 THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_moved AS moved, v_aligned AS aligned, v_year AS year_filled,
               v_null_year AS null_year_left, v_pair AS paired, v_unpair AS unpaired,
               v_pair_bad AS pair_mismatch, v_dupt AS dup_triples, v_sc_changed AS scores_rows_changed;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_moved AS moved, v_aligned AS aligned, v_year AS year_filled,
               v_sc_after AS scores_rows, v_sr_after AS score_record_rows,
               v_pair AS paired, v_unpair AS unpaired, v_pair_bad AS pair_mismatch,
               v_null_year AS null_year_left, v_fail_before AS fails_before,
               v_fail_after AS fails_after, v_5050_before AS not_50_50_before,
               v_5050_after AS not_50_50_after, v_sc_changed AS scores_rows_changed;
    END IF;
END$$
DELIMITER ;

CALL fix9_siming_electives();
DROP PROCEDURE fix9_siming_electives;
