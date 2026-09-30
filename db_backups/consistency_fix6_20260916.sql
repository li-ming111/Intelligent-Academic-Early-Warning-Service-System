-- ============================================================================
-- 批次 6（步骤 B）：把 4 门选修课的录入行补进权威表 scores
--
--   背景：选修课在 score_record 有 95 行，在 scores 里只有司明的 2 行。
--         23 名学生的 92 行（23 人 × 4 门，semester 为 NULL）在权威表里完全缺失，
--         所以他们的成绩单没有选修课。
--         连 score_record 里那 3 行显式赋过学期的选修行也都写的是 6，且
--         course_plans 把全部 10 门课都排在 2025-2026-2；按项目自己的换算
--         (StudentServiceImpl.semesterLabel) 2025-2026-2 = 第 6 个全局学期。
--         完整证据见 db_backups/elective_semester_evidence.md。
--
--   本次只新增，不改动 scores 里任何现有行：
--     新增 92 行 = 23 名学生 × 4 门选修课，semester = 6
--     司明的 2 行（他已有课7/课8 的记录，只是写在第 5 学期）本次不动，单独决定。
--
--   字段取值（与 ScoreSyncSupport.applyToAuthoritative 的口径一致）：
--     semester    = 6
--     year        = 学号前四位 + (6-1)/2   ← 必须按各人入学年推算：
--                   22 人是 2023 级 -> 2025，赵琦 2022020137 是 2022 级 -> 2024
--     score_total = score_record.score     ← 代码的不变式：权威总分 = 录入分
--     regular_score / final_score = 录入表的同名值
--     grade / grade_point = score_total 按 ScoreSyncSupport 的阶梯函数折算
--
--   可回滚：本次是纯新增，回滚 = 按 id 删掉新增行（脚本内已记录 id 区间）
--           整表另备份到 scores_bak_elective_20260916。
--
--   安全阀：待插入行数不等于 92、或这些 (学生,课程,学期6) 在 scores 里已存在、
--           或存在 <60 分的行（会凭空造出挂科、改动预警基线），一律 ROLLBACK。
-- ============================================================================

DROP PROCEDURE IF EXISTS fix6_electives;
DELIMITER $$
CREATE PROCEDURE fix6_electives()
BEGIN
    DECLARE v_expected  INT DEFAULT 92;
    DECLARE v_before    INT DEFAULT 0;
    DECLARE v_todo      INT DEFAULT 0;
    DECLARE v_conflict  INT DEFAULT 0;
    DECLARE v_fail      INT DEFAULT 0;
    DECLARE v_inserted  INT DEFAULT 0;
    DECLARE v_after     INT DEFAULT 0;
    DECLARE v_dupgroups INT DEFAULT 0;
    DECLARE v_bad       INT DEFAULT 0;
    DECLARE v_maxid_before BIGINT DEFAULT 0;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_before FROM scores;
    SELECT IFNULL(MAX(id),0) INTO v_maxid_before FROM scores;

    DROP TABLE IF EXISTS scores_bak_elective_20260916;
    CREATE TABLE scores_bak_elective_20260916 AS SELECT * FROM scores;

    SELECT COUNT(*) INTO v_todo FROM score_record WHERE semester IS NULL;

    -- 这些三元组在 scores 里必须还不存在，否则会造出重复
    SELECT COUNT(*) INTO v_conflict
      FROM score_record sr
     WHERE sr.semester IS NULL
       AND EXISTS (SELECT 1 FROM scores h
                    WHERE h.student_id = sr.student_id
                      AND h.course_id  = sr.course_id
                      AND h.semester   = 6);

    -- 选修课不能有不及格：一旦有，补进来就会改挂科数、改预警基线
    SELECT COUNT(*) INTO v_fail FROM score_record WHERE semester IS NULL AND (score IS NULL OR score < 60);

    INSERT INTO scores (student_id, course_id, semester, year,
                        regular_score, final_score, score_total, grade, grade_point)
    SELECT sr.student_id,
           sr.course_id,
           6,
           CAST(LEFT(sp.student_id, 4) AS UNSIGNED) + (6 - 1) DIV 2,
           sr.regular_score,
           sr.final_score,
           sr.score,
           CASE WHEN sr.score >= 90 THEN 'A'
                WHEN sr.score >= 80 THEN 'A-'
                WHEN sr.score >= 70 THEN 'B'
                WHEN sr.score >= 60 THEN 'C'
                ELSE 'D' END,
           CASE WHEN sr.score >= 90 THEN 4.0 WHEN sr.score >= 85 THEN 3.7 WHEN sr.score >= 82 THEN 3.3
                WHEN sr.score >= 78 THEN 3.0 WHEN sr.score >= 75 THEN 2.7 WHEN sr.score >= 72 THEN 2.3
                WHEN sr.score >= 68 THEN 2.0 WHEN sr.score >= 64 THEN 1.5 WHEN sr.score >= 60 THEN 1.0
                ELSE 0 END
      FROM score_record sr
      JOIN student_profile sp ON sp.id = sr.student_id
     WHERE sr.semester IS NULL;

    SET v_inserted = ROW_COUNT();
    SELECT COUNT(*) INTO v_after FROM scores;

    -- 新增行不能出现重复三元组
    SELECT COUNT(*) INTO v_dupgroups
      FROM (SELECT 1 FROM scores GROUP BY student_id, course_id, semester HAVING COUNT(*) > 1) g;

    -- 新增行的等级/绩点必须与口径一致，且 year 必须由学号推出
    SELECT COUNT(*) INTO v_bad
      FROM scores h JOIN student_profile sp ON sp.id = h.student_id
     WHERE h.id > v_maxid_before
       AND (h.grade <> CASE WHEN h.score_total >= 90 THEN 'A'
                            WHEN h.score_total >= 80 THEN 'A-'
                            WHEN h.score_total >= 70 THEN 'B'
                            WHEN h.score_total >= 60 THEN 'C'
                            ELSE 'D' END
         OR h.grade_point <> CASE WHEN h.score_total >= 90 THEN 4.0 WHEN h.score_total >= 85 THEN 3.7
                                  WHEN h.score_total >= 82 THEN 3.3 WHEN h.score_total >= 78 THEN 3.0
                                  WHEN h.score_total >= 75 THEN 2.7 WHEN h.score_total >= 72 THEN 2.3
                                  WHEN h.score_total >= 68 THEN 2.0 WHEN h.score_total >= 64 THEN 1.5
                                  WHEN h.score_total >= 60 THEN 1.0 ELSE 0 END
         OR h.year <> CAST(LEFT(sp.student_id, 4) AS UNSIGNED) + 2
         OR h.semester <> 6);

    IF v_todo <> v_expected OR v_conflict <> 0 OR v_fail <> 0
       OR v_inserted <> v_expected OR v_after <> v_before + v_expected
       OR v_dupgroups <> 0 OR v_bad <> 0 THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_before AS rows_before, v_todo AS todo,
               v_conflict AS conflicts, v_fail AS failing_rows,
               v_inserted AS inserted, v_dupgroups AS dup_groups, v_bad AS bad_rows;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_before AS rows_before, v_after AS rows_after,
               v_inserted AS inserted, v_todo AS todo, v_fail AS failing_rows,
               v_dupgroups AS dup_groups, v_maxid_before AS max_id_before,
               (SELECT MIN(id) FROM scores) AS min_id_now;
    END IF;
END$$
DELIMITER ;

CALL fix6_electives();
DROP PROCEDURE fix6_electives;
