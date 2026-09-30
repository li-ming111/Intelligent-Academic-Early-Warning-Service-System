-- ============================================================================
-- 批次 9 回滚
--
--   scores_bak_sem9_20260916        259 行 = fix9 之前的 scores
--   score_record_bak_sem9_20260916  246 行 = fix9 之前的 score_record
--
--   fix9 共动了三处：
--     1) scores 里司明课7/课8 的 semester 6 -> 5
--     2) score_record 291/294 的 7 列（对齐成权威值）+ 评语
--     3) score_record 154 行的 year（NULL -> 推算值）
--   score_record 用 REPLACE 按主键整体还原最稳（改的列跨两类，逐列写易漏）；
--   scores 只改回那 2 行的 semester —— 不做整表 REPLACE，避免触碰外键引用。
-- ============================================================================

-- 前置检查
SELECT (SELECT COUNT(*) FROM score_record_bak_sem9_20260916) AS sr_backup_rows,
       (SELECT COUNT(*) FROM scores_bak_sem9_20260916)        AS sc_backup_rows,
       (SELECT COUNT(*) FROM score_record)                     AS sr_now,
       (SELECT COUNT(*) FROM scores)                           AS sc_now;

-- 1) score_record 整体还原（291/294 的分数与评语、154 行的 year 一起回去）
REPLACE INTO score_record SELECT * FROM score_record_bak_sem9_20260916;

-- 2) scores 只改那 2 行的学期
UPDATE scores h JOIN scores_bak_sem9_20260916 b ON b.id = h.id
   SET h.semester = b.semester
 WHERE h.id IN (162, 163);

-- 回滚后自检：score_record 应恢复到 154 行 year 为 NULL、291/294 仍是 85/78；
--             scores 里司明课7/课8 应回到第 5 学期
SELECT (SELECT COUNT(*) FROM score_record WHERE year IS NULL) AS null_year_back,
       (SELECT GROUP_CONCAT(CONCAT(id,'-',score) ORDER BY id) FROM score_record WHERE id IN (291,294)) AS sr_291_294,
       (SELECT GROUP_CONCAT(semester ORDER BY course_id) FROM scores WHERE student_id=3 AND course_id IN (7,8)) AS sc_sem;
