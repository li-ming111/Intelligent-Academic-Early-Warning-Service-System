-- 2026-10-03（第四十二轮 W8.3）：给 score_appeals 的两条申诉补上 score_id。
--
-- 背景：这两行的 score_id 一直是 NULL。score_appeals 里的 (student_id, course_id) 只说明
-- "申诉哪门课"，说不出"申诉哪一次成绩"；一门课有多个学期，这个缺口只能靠旁证补。
-- score_id 全仓读取时**从未被 JOIN**（admin 的 ScoreAppealMapper JOIN 的是 courses 不是 scores），
-- 所以这次写入不会改变任何页面显示 —— 只是让库自身更自洽。
--
-- 依据（两条都有独立旁证，不是猜的）：
--   id=6  高等数学(course 1), entry_error, status=0 待处理, 创建 2026-07-08, description 为空
--         → scores.id=157（学期5, 2024级, 平时58 + 期末55 = 总评56.50, 等级 D）
--         旁证 score_audit_log.id=1：司明/高等数学/于凤海, 55.00 → **56.50**,
--         原因「试卷复核发现平时分漏计，据实更正」, 时间 2026-06-25 —— 正是"录入错误"更正,
--         改完恰为 56.50, 且比申诉早 13 天。四个候选中只有 157 与它吻合,
--         且 157 也是司明唯一一门不及格的高等数学（预警 395 正文点名「高等数学 56.5 分」）。
--   id=8  大学英语(course 2), entry_error, status=1 已通过, 创建 2026-10-02
--         description：「第四学期《大学英语》期末成绩登记有误，本人试卷卷面得分应为 61 分…」
--         → scores.id=152（学期4, 2024级, 平时55 + 期末48 = 总评51, 等级 D）
--         旁证 notifications.id=1003「成绩申诉已通过：大学英语 …第四学期期末卷面 61 分，
--         系登分时漏记 10 分，成绩已更正为 61 分」—— 学期与课程逐字对上。
--
-- 回滚：见 score_appeals_backup_20261003.sql（改前原值：两行 score_id 均为 NULL）。

UPDATE score_appeals SET score_id = 157 WHERE id = 6 AND score_id IS NULL;
UPDATE score_appeals SET score_id = 152 WHERE id = 8 AND score_id IS NULL;
