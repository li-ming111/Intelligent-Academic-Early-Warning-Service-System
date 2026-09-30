-- ============================================================================
-- 批次 7 回滚：把 92 行选修课录入记录还原成 fix7 之前的样子，并恢复被删的 295
--
--   score_record_bak_prefix7_20260916  93 行 = 92 行原始态（semester/year 为 NULL、
--                                      总评为旧加权、等级为旧口径）+ 被删的 295
--   scores_bak_prefix7_20260916        259 行 = fix7 之前的 scores
--                                      （新增的 92 行平时/期末是未平移的值）
--
--   已实测：用 REPLACE 按主键整体替换即可，回滚后与改动前逐字节相同。
--   （负向演练见 db_backups/_fix7_negative.sql 与执行记录）
-- ============================================================================

-- 前置检查
SELECT (SELECT COUNT(*) FROM score_record_bak_prefix7_20260916) AS sr_backup_rows,
       (SELECT COUNT(*) FROM scores_bak_prefix7_20260916) AS sc_backup_rows,
       (SELECT COUNT(*) FROM score_record) AS sr_now,
       (SELECT COUNT(*) FROM scores) AS sc_now;

-- 回滚 score_record：92 行按主键覆盖回原值，295 重新插入
REPLACE INTO score_record SELECT * FROM score_record_bak_prefix7_20260916;

-- 回滚 scores：只有新增的 92 行平时/期末被动过
UPDATE scores h JOIN scores_bak_prefix7_20260916 b ON b.id = h.id
   SET h.regular_score = b.regular_score, h.final_score = b.final_score
 WHERE h.id > 167;

-- 回滚后自检：应为 247 行、其中 92 行 semester 为 NULL，且 295 存在
SELECT COUNT(*) AS rows_after_rollback,
       SUM(semester IS NULL) AS null_semester,
       SUM(id = 295) AS has_295
  FROM score_record;
