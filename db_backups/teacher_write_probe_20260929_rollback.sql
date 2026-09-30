-- 回滚 2026-09-29「教师端写路径验证」写入的改动。
--
-- 前置快照（本目录）：teacher_write_probe_20260929_before.sql
--   四张表：score_record / scores / score_audit_log / feedback
-- 若本脚本的复原值有任何不确定，直接从快照恢复那四张表。
--
-- 被验证的两次写入（testsprite/local-check/teacher-write-probe.mjs）：
--   1) PUT  /api/teachers/scores/277        score 87 → 88，remark → '写路径验证-临时'
--      → 连带改了 score_record#277、其镜像行 scores#145，并新增一条 score_audit_log
--   2) POST /api/teachers/feedbacks/1/reply reply → '写路径验证-临时回复'，status → 1
--
-- 复原值取自验证**之前**实测的行内容（不是从记忆里抄的）。
-- 注意 DDL 不能放进事务（会隐式提交），本脚本只有 DML，所以 START TRANSACTION 有效。

START TRANSACTION;

-- 1) score_record#277（司明 · 高等数学 · 第3学期 · 87 分）
UPDATE score_record
   SET score         = 87.00,
       score_level   = 2,
       score_total   = 87.00,
       grade         = 'A-',
       grade_point   = 3.70,
       regular_score = 85.00,
       final_score   = 88.00,
       remark        = '第3学期高等数学',
       semester      = '3',
       year          = 2024,
       modified_by   = NULL,
       update_time   = '2026-09-16 11:07:32'
 WHERE id = 277;

-- 2) scores#145（权威成绩表里的镜像行，syncToScores 写入的那一行）
UPDATE scores
   SET score_total   = 87.00,
       regular_score = 85.00,
       final_score   = 88.00,
       grade         = 'A-',
       grade_point   = 3.70,
       updated_at    = '2026-09-16 20:36:43'
 WHERE id = 145;

-- 3) 删掉验证新增的审计日志。
--    用内容条件定位而非硬编码 id：验证明细是"改 87 → 88 且原因是写路径验证-临时"，
--    这条组合在真实数据里不会出现，不会误删业务数据。
DELETE FROM score_audit_log
 WHERE student_id = 3
   AND course_id  = 1
   AND old_score  = 87.00
   AND new_score  = 88.00
   AND reason     = '写路径验证-临时';

-- 4) feedback#1 复原为"待回复"
UPDATE feedback
   SET reply       = NULL,
       status      = 0,
       update_time = '2026-06-25 09:53:16'
 WHERE id = 1;

COMMIT;

-- 5) 试着把自增计数器拨回快照里记的值（4）。
--    实测结论：**拨不回去**。本表 MAX(id)=1，MySQL 8 无论指定 4 还是先设 100
--    再回设 4，计数器最终都停在 3（不会低于 max(id)+2），先拨高再拨低也不行。
--    这项保持原样：计数器不参与任何接口返回，也不显示在任何页面上，
--    下一次插入拿 id=3 或 4 对业务没有任何区别。
--    所以本脚本的复原口径是「**行内容**逐字段等于验证之前，自增计数器除外」。
--    DDL 会隐式提交，所以放在 COMMIT 之后，不混进上面的事务。
ALTER TABLE score_audit_log AUTO_INCREMENT = 4;

-- 复核（应全为 0 行）
SELECT '残留：审计日志' AS chk, COUNT(*) AS n FROM score_audit_log WHERE reason = '写路径验证-临时'
UNION ALL
SELECT '残留：score_record', COUNT(*) FROM score_record WHERE remark = '写路径验证-临时'
UNION ALL
SELECT '残留：feedback', COUNT(*) FROM feedback WHERE reply = '写路径验证-临时回复';
