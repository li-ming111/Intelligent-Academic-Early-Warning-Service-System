-- 回滚 2026-09-29「教师端成绩页**界面驱动**写路径验证」留下的痕迹。
--
-- 前置快照（本目录）：teacher_ui_write_probe_20260929_before.sql
--   三张表：score_record / scores / score_audit_log
-- 若本脚本的复原值有任何不确定，直接从快照恢复那三张表（那是唯一的权威）。
--
-- 被验证的三次写入（testsprite/local-check/teacher-write-ui-probe.mjs，
-- 全部在**小程序的成绩页上点**完成，不是直接打接口）：
--   1) 增：录入 sheet 选 司明（2023020616）+ 第8学期，总评 77、评语「UI写路径验证-临时」
--      → score_record 新增一行（司明·高等数学·第8学期）并镜像进 scores
--   2) 改：点那一行 → 总评改 88、评语「UI改分验证-临时」
--      → score_record 与 scores 同步更新，**新增一条 score_audit_log**
--        （old 77 → new 88，difference 11，reason = 评语，modified_by = 于凤海）
--   3) 删：点那一行 → 删除这条成绩记录（二次确认）
--      → score_record 那行没了
--
-- ⚠️ 落库实测结论：**删分不会连带删掉那条审计日志**。
--    审计日志是"成绩改动的留痕"，设计上就该留下来 —— 所以第 3 步删完，
--    score_audit_log 里仍然留着第 2 步那条（id=4）。本脚本要清的就是它。
--    反过来说，这也是一条硬证据：**审计日志确实是改分接口在服务端自己写进去的**，
--    不是页面传的。
--
-- 本脚本不碰任何原有数据：验证时增的那一行由页面自己删掉了，
-- 司明的三条原记录（score_record#277/283/288）自始至终没被写过。
-- 下面的 DELETE 都按**内容**定位（remark / 学期），即使脚本在"增"之后半途崩了，
-- 重跑本脚本也能把残留清掉，且不会误删真实业务数据
-- （「UI写路径验证-临时」这种评语在真实数据里不可能出现；
--  司明·高等数学·第8学期这一组合全库也只有验证脚本会造）。

START TRANSACTION;

-- 1) 审计日志残留：改分那一步写进去的那条
DELETE FROM score_audit_log
 WHERE student_id = 3
   AND course_id  = 1
   AND old_score  = 77.00
   AND new_score  = 88.00
   AND reason     = 'UI改分验证-临时';

-- 2) 兜底：万一脚本在"改"之前就崩了，审计日志里可能是 77 以外的旧值
DELETE FROM score_audit_log
 WHERE reason IN ('UI写路径验证-临时', 'UI改分验证-临时');

-- 3) score_record 残留（正常情况为 0 行：页面自己删掉了）
DELETE FROM score_record
 WHERE remark IN ('UI写路径验证-临时', 'UI改分验证-临时');

-- 4) scores 镜像表残留。
--    这张表**没有 remark 列**，所以只能按 (学生, 课程, 学期) 定位 ——
--    验证脚本固定用「司明 + 高等数学 + 第8学期」这个全库不存在的组合。
DELETE FROM scores
 WHERE student_id = 3
   AND course_id  = 1
   AND semester   = '8';

COMMIT;

-- 5) 自增计数器：验证前 score_audit_log 的计数器是 4（删掉 id=4 之后 max(id)=1）。
--    InnoDB 允许把计数器设成 >= max(id)+1 的值，所以这次能拨回 4，
--    与 teacher_write_probe 那次（max(id)=1 却拨不回 4）情况不同。
--    DDL 会隐式提交，所以放在 COMMIT 之后，不混进上面的事务。
ALTER TABLE score_audit_log AUTO_INCREMENT = 4;

-- 6) 复核（下面每一项都应为 0 行）
SELECT '残留：审计日志' AS chk, COUNT(*) AS n FROM score_audit_log WHERE reason LIKE 'UI%验证-临时'
UNION ALL
SELECT '残留：score_record', COUNT(*) FROM score_record WHERE remark LIKE 'UI%验证-临时'
UNION ALL
SELECT '残留：scores 镜像', COUNT(*) FROM scores WHERE student_id=3 AND course_id=1 AND semester='8'
UNION ALL
SELECT '司明·高等数学 的 score_record 行数（应 3）', COUNT(*) FROM score_record WHERE student_id=3 AND course_id=1
UNION ALL
SELECT 'score_audit_log 总行数（应 1）', COUNT(*) FROM score_audit_log
UNION ALL
SELECT 'score_record 总行数（应 246）', COUNT(*) FROM score_record;
