-- ============================================================================
-- 批次 4：score_record 重复三元组去重
--   背景：137 组重复，每组恰好 2 行，全部落在 semester=2，
--         横跨课程 1~6（必修课）× 23 名学生（司明以外），
--         两行仅 teacher_id 与旧口径派生列不同，score 完全相同。
--   决策：每组保留 id 最大的一条（与全项目「取最新一次成绩」的口径一致），删 137 行。
--   影响：384 -> 247 行。只动 score_record，scores（权威表）不受任何影响。
--   可回滚：整表已备份到 score_record_bak_dup_20260916，
--           回滚脚本 db_backups/rollback_fix4_20260916.sql。
--
--   安全阀：删除行数不等于 137，或删完后仍存在重复三元组，则自动 ROLLBACK 并保持原状。
-- ============================================================================

DROP PROCEDURE IF EXISTS fix4_dedup;
DELIMITER $$
CREATE PROCEDURE fix4_dedup()
BEGIN
    DECLARE v_expected INT DEFAULT 137;
    DECLARE v_deleted  INT DEFAULT 0;
    DECLARE v_groups   INT DEFAULT 0;
    DECLARE v_before   INT DEFAULT 0;

    START TRANSACTION;

    -- 整表备份（DDL 会隐式提交，但此刻事务里还没有待提交的改动）
    DROP TABLE IF EXISTS score_record_bak_dup_20260916;
    CREATE TABLE score_record_bak_dup_20260916 AS SELECT * FROM score_record;
    SELECT COUNT(*) INTO v_before FROM score_record;

    -- 自连接去重：删除所有「同三元组里存在更大 id」的行
    DELETE sr FROM score_record sr
      JOIN score_record keep
        ON keep.student_id = sr.student_id
       AND keep.course_id  = sr.course_id
       AND keep.semester  <=> sr.semester
       AND keep.id > sr.id;

    SET v_deleted = ROW_COUNT();

    SELECT COUNT(*) INTO v_groups FROM (
        SELECT 1 FROM score_record
         GROUP BY student_id, course_id, semester
        HAVING COUNT(*) > 1
    ) x;

    IF v_deleted <> v_expected OR v_groups <> 0 THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_before AS rows_before,
               v_deleted AS rows_deleted, v_groups AS dup_groups_left;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_before AS rows_before,
               v_deleted AS rows_deleted, (SELECT COUNT(*) FROM score_record) AS rows_after,
               v_groups AS dup_groups_left;
    END IF;
END$$
DELIMITER ;

CALL fix4_dedup();
DROP PROCEDURE fix4_dedup;
