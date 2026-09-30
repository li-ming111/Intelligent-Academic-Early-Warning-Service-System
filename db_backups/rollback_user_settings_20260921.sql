-- 回滚 fix_user_settings_20260921.sql
-- 只删本次新增的表；不动任何既有表（users/teacher_profile/counselor_profiles 均未被改）。
USE academic_warning_system;

DROP TABLE IF EXISTS `user_settings`;

SELECT COUNT(*) AS user_settings_should_be_0 FROM information_schema.tables
 WHERE table_schema = 'academic_warning_system' AND table_name = 'user_settings';
