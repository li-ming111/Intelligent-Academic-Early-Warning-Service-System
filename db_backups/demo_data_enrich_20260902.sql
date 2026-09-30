-- ============================================================
-- 演示数据补足脚本 (生成于 2026-09-02)
-- 目标:为演示学生"司明"(student_profile.id=3, users.id=27)
--      补齐 帮扶计划 + 通知中心 数据, 让"预警→帮扶→申诉"闭环完整
-- 所有内容锚定 score_record 中司明真实挂科成绩, 可信可讲。
-- 幂等: 以固定 id 插入并使用 INSERT ... ON DUPLICATE KEY, 可重复执行
-- 撤销: 见脚本末尾 DELETE 语句
-- ============================================================
USE academic_warning_system;

-- 1) 帮扶计划: 辅导员(user 53) 给 司明(student_profile 3) 制定, 状态进行中(1)
INSERT INTO assistance_plans (id, counselor_id, student_id, content, status, created_at, updated_at) VALUES
(1001, 53, 3,
 '针对司明同学的学业帮扶计划：1.重点补习操作系统、计算机基础、大学英语、高等数学四门挂科课程；'
 '2.安排学习小组每周两次辅导答疑；3.每两周检查一次学习进度并反馈辅导员；4.目标：本学期末补考全部通过。',
 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE content = VALUES(content), status = VALUES(status), updated_at = NOW();

-- 2) 通知1: 学业预警(挂科) —— 关联真实预警 301
INSERT INTO notifications (id, user_id, title, content, type, warning_id, push_channel, is_read, is_deleted, created_at, updated_at) VALUES
(1001, 27, '学业预警通知',
 '司明同学，您本学期有5门课程成绩不及格（操作系统40分、计算机基础47分、大学英语51分、高等数学56.5分、计算机基础59分），已触发学业预警。请尽快联系辅导员沟通帮扶安排。',
 'warning', 301, 'app', 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE content = VALUES(content), is_read = VALUES(is_read), updated_at = NOW();

-- 3) 通知2: 系统通知 —— 帮扶计划已制定
INSERT INTO notifications (id, user_id, title, content, type, warning_id, push_channel, is_read, is_deleted, created_at, updated_at) VALUES
(1002, 27, '帮扶计划已制定',
 '辅导员已为您制定学业帮扶计划（进行中）：每周两次学习小组辅导答疑。请积极跟进学习进度，如有困难可随时在系统中反馈。',
 'system', NULL, 'app', 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE content = VALUES(content), is_read = VALUES(is_read), updated_at = NOW();

-- ============ 撤销方法(如需还原, 执行下面注释掉的语句) ============
-- DELETE FROM assistance_plans WHERE id = 1001;
-- DELETE FROM notifications  WHERE id IN (1001, 1002);
