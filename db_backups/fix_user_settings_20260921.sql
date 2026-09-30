-- 新增 user_settings：教师/辅导员/管理员的"账号级自助设置"（通知偏好 + 办公信息）
--
-- 为什么不是给 users 加列：academic-common 的 DataInitializer.EXPECTED_COLUMNS
-- 在**每次启动**都拿 information_schema 比对 14 张表的列数，不符就 log.error。
-- users 被钉死为 11 列，加列会让每次启动都报错；加**新表**不在该清单内，不受影响。
--
-- 为什么不是复用现有表：
--   student_settings / subscription_preferences 都 FK 绑定 student_profile.id，是学生专用；
--   teacher_profile 只有 8 列（id/user_id/name/college_id/title/research_areas/时间戳），
--   没有任何 email/phone/office 列；
--   office_location/duty_phone 只存在于 counselor_profiles 且 35 行全为 NULL。
--
-- 键是 users.id（不是 student_profile.id）——与 notifications.receiver_id 同一套 ID 空间。
-- 快照：db_backups/academic_warning_system_pre_user_settings_20260921.sql
-- 回滚：db_backups/rollback_user_settings_20260921.sql

USE academic_warning_system;

CREATE TABLE IF NOT EXISTS `user_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'users.id（不是 student_profile.id）',
  `warning_notification` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收预警通知',
  `system_message`       tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收系统消息',
  `class_notification`   tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收班级通知',
  `student_feedback`     tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收学生反馈',
  `email_reminder`       tinyint(1) NOT NULL DEFAULT '0' COMMENT '邮件提醒',
  `office_location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公地点',
  `office_phone`    varchar(20)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公电话',
  `office_hours`    varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_settings_user_id` (`user_id`),
  CONSTRAINT `fk_user_settings_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 故意不插种子数据：GET /users/{id}/settings 在无行时返回默认值，
-- 首次 PUT 才落行。这样"默认值"这条分支在演示环境里是真实被走到的，
-- 而不是靠一行恰好等于默认值的种子数据假装出来的。
SELECT COUNT(*) AS user_settings_rows FROM user_settings;
