-- ============================================================================
-- 批次 4 回滚：把 score_record 恢复到去重前的 384 行
--
-- 批次 4 是一次纯删除（没有改动任何保留行的字段），所以回滚 = 按 id 把删掉的行插回去。
-- INSERT IGNORE 只补缺失的 id，不会覆盖之后产生的正常修改，对"纯删除"是精确回滚。
-- 如需回到备份的完全一致状态（含字段），改用最后那条 REPLACE 语句。
-- ============================================================================

-- 前置检查：备份表必须存在且为 384 行
SELECT COUNT(*) AS backup_rows FROM score_record_bak_dup_20260916;

-- 精确回滚（推荐）
INSERT IGNORE INTO score_record SELECT * FROM score_record_bak_dup_20260916;

-- 回滚后自检
SELECT COUNT(*) AS rows_now FROM score_record;

-- 备选：强制回到备份的逐字段完全一致状态（会覆盖保留行的后续修改，一般不要用）
-- REPLACE INTO score_record SELECT * FROM score_record_bak_dup_20260916;
