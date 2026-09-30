-- 复核 2026-09-29「教师端成绩页界面驱动写路径验证」的回滚结果（只读，不改任何东西）。
-- 用法：mysql -uroot -proot academic_warning_system < 本文件
--
-- 判读：result 列全为 PASS，且「残留」那几行 n=0。
-- 口径与上一次写路径验证一致：**行内容逐字段等于验证之前，自增计数器除外**。
-- 计数器只有"下一个 id 是多少"这一层含义，不参与任何接口返回、不显示在任何页面上
-- （见本目录 teacher_write_probe_20260929_rollback.sql 第 5 条的说明）。
--
-- 更强的证据不在这份 SQL 里，而在 check.sh：它把三张表重新 dump 一遍、
-- 去掉 AUTO_INCREMENT= 行之后与验证前的快照**逐字节 diff**。
-- 那份 diff 为空就等价于"这三张表回到了验证之前"，比逐字段写断言更难糊弄过去。

SELECT '=== 一、残留检查（都应为 0）===' AS section;

SELECT 'score_record 里的验证残留' AS chk, COUNT(*) AS n
  FROM score_record WHERE remark LIKE 'UI%验证-临时'
UNION ALL
SELECT 'score_audit_log 里的验证残留', COUNT(*)
  FROM score_audit_log WHERE reason LIKE 'UI%验证-临时'
UNION ALL
SELECT 'scores 镜像里的验证残留（司明·高等数学·第8学期）', COUNT(*)
  FROM scores WHERE student_id = 3 AND course_id = 1 AND semester = '8'
UNION ALL
SELECT 'score_record 里司明·高等数学的行数（应 3）', COUNT(*)
  FROM score_record WHERE student_id = 3 AND course_id = 1
UNION ALL
SELECT 'score_record 总行数（应 246）', COUNT(*) FROM score_record
UNION ALL
SELECT 'score_audit_log 总行数（应 1）', COUNT(*) FROM score_audit_log;

SELECT '=== 二、司明三条原记录逐字段核对（result 应全为 PASS）===' AS section;

SELECT 'score_record#277 未变' AS chk,
       IF(score = 87.00 AND score_level = 2 AND score_total = 87.00 AND grade = 'A-'
          AND grade_point = 3.70 AND regular_score = 85.00 AND final_score = 88.00
          AND remark = '第3学期高等数学' AND semester = '3' AND year = 2024
          AND modified_by IS NULL AND update_time = '2026-09-16 11:07:32', 'PASS', 'FAIL') AS result
  FROM score_record WHERE id = 277
UNION ALL
SELECT 'score_record#283 未变',
       IF(score = 71.50 AND score_level = 3 AND grade = 'B' AND grade_point = 2.00
          AND regular_score = 70.00 AND final_score = 72.00
          AND remark = '第4学期高等数学下滑' AND semester = '4' AND year = 2024
          AND modified_by IS NULL AND update_time = '2026-09-16 11:07:32', 'PASS', 'FAIL')
  FROM score_record WHERE id = 283
UNION ALL
SELECT 'score_record#288 未变',
       IF(score = 56.50 AND score_level = 5 AND grade = 'D' AND grade_point = 0.00
          AND regular_score = 58.00 AND final_score = 55.00
          AND remark = '第5学期高等数学持续下滑' AND semester = '5' AND year = 2025
          AND modified_by IS NULL AND update_time = '2026-09-16 11:07:32', 'PASS', 'FAIL')
  FROM score_record WHERE id = 288
UNION ALL
-- scores 镜像表里司明·高等数学的四行（注意 semester=2 那行 id=1，
-- 它在 score_record 里没有对应流水行 —— 这是**验证之前就存在**的历史不一致，
-- 不是本次验证造成的；这里只核对它没被改过）
SELECT 'scores#1 (sem2) 未变',
       IF(score_total = 82.00 AND regular_score = 78.00 AND final_score = 85.00
          AND grade = 'A-' AND grade_point = 3.30 AND updated_at = '2026-09-16 20:36:43', 'PASS', 'FAIL')
  FROM scores WHERE id = 1
UNION ALL
SELECT 'scores#145 (sem3) 未变',
       IF(score_total = 87.00 AND regular_score = 85.00 AND final_score = 88.00
          AND grade = 'A-' AND grade_point = 3.70 AND updated_at = '2026-09-16 20:36:43', 'PASS', 'FAIL')
  FROM scores WHERE id = 145
UNION ALL
SELECT 'scores#151 (sem4) 未变',
       IF(score_total = 71.50 AND regular_score = 70.00 AND final_score = 72.00
          AND grade = 'B' AND grade_point = 2.00 AND updated_at = '2026-09-16 20:36:43', 'PASS', 'FAIL')
  FROM scores WHERE id = 151
UNION ALL
SELECT 'scores#157 (sem5) 未变',
       IF(score_total = 56.50 AND regular_score = 58.00 AND final_score = 55.00
          AND grade = 'D' AND grade_point = 0.00 AND updated_at = '2026-09-16 20:36:43', 'PASS', 'FAIL')
  FROM scores WHERE id = 157
UNION ALL
-- 审计日志只剩种子里那条：55 → 56.5，理由「试卷复核发现平时分漏计，据实更正」
SELECT 'score_audit_log#1 未变',
       IF(student_id = 3 AND student_name = '司明' AND course_id = 1 AND course_name = '高等数学'
          AND teacher_id = 29 AND old_score = 55.00 AND new_score = 56.50 AND difference = 1.50
          AND reason = '试卷复核发现平时分漏计，据实更正' AND modified_by = '于凤海'
          AND modified_at = '2026-06-25 09:38:17', 'PASS', 'FAIL')
  FROM score_audit_log WHERE id = 1;
