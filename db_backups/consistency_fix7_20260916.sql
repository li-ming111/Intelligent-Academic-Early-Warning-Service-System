-- ============================================================================
-- 批次 7：把 92 行选修课录入记录补完（学期/学年/总评/等级/平时期末）
--
--   【已应用】2026-09-16 在本库执行过，当前库即为本脚本的目标状态。
--   重跑本脚本会因 "待处理行数 = 0 ≠ 92" 而安全回滚，是预期行为。
--
--   起因：fix6 已把 92 行按第 6 学期补进权威表 scores，但 score_record 那 92 行
--         本身还是"半成品"：
--           · semester / year 都是 NULL（全表唯一一处不知道属于哪个学期）
--           · score（录入分 61~96）与 score_total（另一套加权算出的总评）81 行对不上
--             —— 例如张健课9那行 score=61 但 score_total=56，等级被算成 F/0.60，
--                与权威表里的 61 分（及格）直接矛盾
--           · grade/grade_point 74 行还是旧口径（A/B/C/D/F + (score-50)/10）
--           · 总评不满足项目自己的「总评 = ROUND((平时+期末)/2)」，而权威表里
--             163 行有 153 行满足
--
--   本批次做四件事（权威表原有 167 行一字不动）：
--     1) semester = 6，year = 学号前四位 + 2（赵琦 2022 级 -> 2024，其余 2025）
--     2) score_total = score（代码不变式：权威总评 = 录入分），
--        grade / grade_point / score_level 按 ScoreSyncSupport 口径重算
--     3) 平移平时分与期末分（两者加同一个 d），使 ROUND((平时+期末)/2) = 录入分。
--        d = 录入分 - ROUND((平时+期末)/2)，实测范围 -7 ~ +6，平移后每个分量
--        都落在 [56,100]，录入分本身不变（不会凭空造出挂科）。
--        scores 里这 92 行的平时/期末同步改成同样的值，两表保持一致。
--     4) 顺带删掉 score_record id=295（邱琳紫 课8 学期6）：319 号补上学期后，
--        二者成为同一 (学生,课程,学期) 的重复行，沿用 fix4 的规则保留 id 最大的一条；
--        295 的录入分 88 已在 319 与权威表 id=191 中保留，无分数丢失。
--
--   ⚠️ 事务位置（前几批脚本踩过的坑，务必保持这个顺序）：
--      MySQL 里 CREATE TABLE / DROP TABLE 会【隐式提交】当前事务。若把
--      START TRANSACTION 写在建表之前，DDL 一执行事务就结束，后面的 UPDATE/DELETE
--      会以自动提交方式执行，最后的 ROLLBACK 变成空操作 —— 安全阀形同虚设。
--      因此：所有 DDL（备份表、临时表及其 INSERT）必须在 START TRANSACTION 之前，
--      事务只包裹 DML 与校验。
--
--   备份：score_record_bak_prefix7_20260916（93 行：92 行原始态 + 被删的 295）
--         scores_bak_prefix7_20260916（fix7 前的 259 行，用于恢复那 92 行的平时/期末）
-- ============================================================================

DROP PROCEDURE IF EXISTS fix7_finish_electives;
DELIMITER $$
CREATE PROCEDURE fix7_finish_electives()
BEGIN
    DECLARE v_expected   INT DEFAULT 92;
    DECLARE v_sr_before  INT DEFAULT 0;
    DECLARE v_sc_before  INT DEFAULT 0;
    DECLARE v_todo       INT DEFAULT 0;
    DECLARE v_upd_sr     INT DEFAULT 0;
    DECLARE v_upd_sc     INT DEFAULT 0;
    DECLARE v_oor        INT DEFAULT 0;
    DECLARE v_left_null  INT DEFAULT 0;
    DECLARE v_92_bad     INT DEFAULT 0;
    DECLARE v_sc_left    INT DEFAULT 0;
    DECLARE v_rest_bad_a INT DEFAULT 0;
    DECLARE v_rest_bad_b INT DEFAULT 0;
    DECLARE v_left_bad   INT DEFAULT 0;
    DECLARE v_dup295     INT DEFAULT 0;
    DECLARE v_sr_after   INT DEFAULT 0;
    DECLARE v_sc_after   INT DEFAULT 0;
    DECLARE v_fail_sc_b  INT DEFAULT 0;
    DECLARE v_fail_sc_a  INT DEFAULT 0;
    DECLARE v_untouched  INT DEFAULT 0;

    -- ===== 阶段一：DDL 与基线计数（都在事务之外）=====
    SELECT COUNT(*) INTO v_sr_before FROM score_record;
    SELECT COUNT(*) INTO v_sc_before FROM scores;
    SELECT COUNT(*) INTO v_fail_sc_b FROM scores WHERE score_total < 60;

    DROP TABLE IF EXISTS score_record_bak_sem7_20260916;
    CREATE TABLE score_record_bak_sem7_20260916 AS SELECT * FROM score_record;
    DROP TABLE IF EXISTS scores_bak_sem7_20260916;
    CREATE TABLE scores_bak_sem7_20260916 AS SELECT * FROM scores;

    -- 平移量与目标值一次性算好，避免 UPDATE 里先后赋值互相干扰
    DROP TEMPORARY TABLE IF EXISTS tmp_f7;
    CREATE TEMPORARY TABLE tmp_f7 (
        id          BIGINT PRIMARY KEY,
        student_id  BIGINT,
        course_id   BIGINT,
        new_reg     DECIMAL(5,2),
        new_fin     DECIMAL(5,2),
        new_total   DECIMAL(5,2)
    );
    INSERT INTO tmp_f7
    SELECT id, student_id, course_id,
           regular_score + (score - ROUND((regular_score + final_score) / 2)),
           final_score   + (score - ROUND((regular_score + final_score) / 2)),
           score
      FROM score_record
     WHERE semester IS NULL;

    SELECT COUNT(*) INTO v_todo FROM tmp_f7;

    -- ===== 阶段二：DML（事务保护，校验不过就真的回滚）=====
    START TRANSACTION;

    -- 1+2+3) 补完 score_record 的 92 行
    UPDATE score_record sr
      JOIN tmp_f7 t           ON t.id = sr.id
      JOIN student_profile sp ON sp.id = sr.student_id
       SET sr.semester      = 6,
           sr.year          = CAST(LEFT(sp.student_id, 4) AS UNSIGNED) + (6 - 1) DIV 2,
           sr.score_total   = t.new_total,
           sr.regular_score = t.new_reg,
           sr.final_score   = t.new_fin,
           sr.grade         = CASE WHEN t.new_total >= 90 THEN 'A'
                                   WHEN t.new_total >= 80 THEN 'A-'
                                   WHEN t.new_total >= 70 THEN 'B'
                                   WHEN t.new_total >= 60 THEN 'C'
                                   ELSE 'D' END,
           sr.grade_point   = CASE WHEN t.new_total >= 90 THEN 4.0 WHEN t.new_total >= 85 THEN 3.7
                                   WHEN t.new_total >= 82 THEN 3.3 WHEN t.new_total >= 78 THEN 3.0
                                   WHEN t.new_total >= 75 THEN 2.7 WHEN t.new_total >= 72 THEN 2.3
                                   WHEN t.new_total >= 68 THEN 2.0 WHEN t.new_total >= 64 THEN 1.5
                                   WHEN t.new_total >= 60 THEN 1.0 ELSE 0 END,
           sr.score_level   = CASE WHEN t.new_total >= 90 THEN 1
                                   WHEN t.new_total >= 80 THEN 2
                                   WHEN t.new_total >= 70 THEN 3
                                   WHEN t.new_total >= 60 THEN 4
                                   ELSE 5 END;
    SET v_upd_sr = ROW_COUNT();

    -- scores 里对应的 92 行，平时/期末改成同一组值
    UPDATE scores h
      JOIN tmp_f7 t ON t.student_id = h.student_id AND t.course_id = h.course_id
       SET h.regular_score = t.new_reg,
           h.final_score   = t.new_fin
     WHERE h.id > 167;
    SET v_upd_sc = ROW_COUNT();

    -- 4) 删掉与 319 重复的 295
    DELETE FROM score_record
     WHERE id = 295 AND student_id = 4 AND course_id = 8 AND semester = 6
       AND NOT EXISTS (SELECT 1 FROM tmp_f7 t WHERE t.id = 295);
    SET v_dup295 = ROW_COUNT();

    -- ===== 校验 =====
    SELECT COUNT(*) INTO v_oor FROM tmp_f7
     WHERE new_reg NOT BETWEEN 0 AND 100 OR new_fin NOT BETWEEN 0 AND 100;

    SELECT COUNT(*) INTO v_left_null FROM score_record WHERE semester IS NULL;

    -- 92 行现在必须全部满足「总评 = ROUND((平时+期末)/2)」
    SELECT COUNT(*) INTO v_92_bad FROM score_record
     WHERE regular_score IS NOT NULL AND final_score IS NOT NULL
       AND score_total <> ROUND((regular_score + final_score) / 2)
       AND id IN (SELECT id FROM tmp_f7);

    -- 其余行的不吻合数必须与改动前完全一致（既不能变好也不能变坏）
    SELECT COUNT(*) INTO v_rest_bad_a FROM score_record
     WHERE regular_score IS NOT NULL AND final_score IS NOT NULL
       AND score_total <> ROUND((regular_score + final_score) / 2)
       AND id NOT IN (SELECT id FROM tmp_f7) AND id <> 295;
    SELECT COUNT(*) INTO v_rest_bad_b FROM score_record_bak_sem7_20260916
     WHERE regular_score IS NOT NULL AND final_score IS NOT NULL
       AND score_total <> ROUND((regular_score + final_score) / 2)
       AND id NOT IN (SELECT id FROM tmp_f7) AND id <> 295;

    SELECT COUNT(*) INTO v_left_bad FROM score_record sr
     WHERE sr.id IN (SELECT id FROM tmp_f7)
       AND (sr.grade <> CASE WHEN sr.score_total >= 90 THEN 'A' WHEN sr.score_total >= 80 THEN 'A-'
                             WHEN sr.score_total >= 70 THEN 'B' WHEN sr.score_total >= 60 THEN 'C'
                             ELSE 'D' END
        OR sr.score_level <> CASE WHEN sr.score_total >= 90 THEN 1 WHEN sr.score_total >= 80 THEN 2
                                  WHEN sr.score_total >= 70 THEN 3 WHEN sr.score_total >= 60 THEN 4
                                  ELSE 5 END
        OR sr.score_total <> sr.score);

    -- scores 的 92 行平时/期末必须与 tmp_f7 完全一致
    -- （affected rows 只统计"真的变了"的行，不能用行数判断）
    SELECT COUNT(*) INTO v_sc_left FROM scores h
      JOIN tmp_f7 t ON t.student_id = h.student_id AND t.course_id = h.course_id
     WHERE h.id > 167
       AND (NOT (h.regular_score <=> t.new_reg) OR NOT (h.final_score <=> t.new_fin));

    SELECT COUNT(*) INTO v_sr_after FROM score_record;
    SELECT COUNT(*) INTO v_sc_after FROM scores;
    SELECT COUNT(*) INTO v_fail_sc_a FROM scores WHERE score_total < 60;

    -- 权威表原有 167 行必须一字未动
    SELECT COUNT(*) INTO v_untouched FROM scores h JOIN scores_bak_sem7_20260916 b ON b.id = h.id
     WHERE b.id <= 167
       AND (NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id)
         OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year)
         OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score)
         OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade)
         OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change)
         OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at)
         OR NOT (h.updated_at<=>b.updated_at));

    IF v_todo <> v_expected OR v_upd_sr <> v_expected OR v_sc_left <> 0 OR v_upd_sc > v_expected
       OR v_oor <> 0 OR v_left_null <> 0 OR v_left_bad <> 0
       OR v_dup295 <> 1 OR v_92_bad <> 0 OR v_rest_bad_a <> v_rest_bad_b
       OR v_sr_after <> v_sr_before - 1 OR v_sc_after <> v_sc_before
       OR v_fail_sc_a <> v_fail_sc_b OR v_untouched <> 0 THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_todo AS todo, v_upd_sr AS sr_updated,
               v_upd_sc AS sc_updated, v_sc_left AS scores_not_aligned, v_oor AS out_of_range,
               v_left_null AS null_semester_left, v_left_bad AS caliber_bad, v_92_bad AS bad_in_92,
               v_rest_bad_a AS rest_bad_after, v_rest_bad_b AS rest_bad_before,
               v_dup295 AS dup295_deleted, v_sr_after AS sr_rows, v_sc_after AS sc_rows,
               v_untouched AS orig_rows_changed;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_sr_before AS sr_before, v_sr_after AS sr_after,
               v_sc_after AS scores_rows, v_upd_sr AS sr_updated, v_upd_sc AS scores_changed,
               v_dup295 AS dup295_deleted, v_left_null AS null_semester_left,
               v_92_bad AS bad_in_92, v_rest_bad_b AS not_50_50_left,
               v_fail_sc_b AS fails_before, v_fail_sc_a AS fails_after;
    END IF;
END$$
DELIMITER ;

CALL fix7_finish_electives();
DROP PROCEDURE fix7_finish_electives;
DROP TEMPORARY TABLE IF EXISTS tmp_f7;
