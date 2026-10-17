-- ============================================================
-- W47B 回滚（逐格还原到 apply 之前的活库值）
-- 用法：mysql -uroot -proot academic_warning_system < 本文件
-- 与 apply 一一对应；值取自 apply 执行前的活库快照：
--   db_backups/academic_warning_system_full_20261017_2203_before_w47b_tzrestore.sql
-- 注意：下方 `SET time_zone='+00:00'` 与字面量是配套的（快照即 UTC 口径）；
--       不要改成 +08，否则会再引入 8 小时偏移。
-- ============================================================
SET @old_tz := @@session.time_zone;
SET time_zone = '+00:00';

UPDATE `academic_warning`         SET `updated_by`='10001',        `updated_at`='2026-10-14 02:54:04' WHERE `id`=395;
UPDATE `feedback`                 SET `update_time`='2026-10-14 10:54:04'                            WHERE `id`=1;
UPDATE `teacher_profile`          SET `updated_at`='2026-10-14 02:54:03'                             WHERE `id`=1;
UPDATE `assistance_plans`         SET `updated_at`='2026-10-08 23:12:10'                             WHERE `id`=1001;
UPDATE `notifications`            SET `updated_at`='2026-09-01 22:36:09'                             WHERE `id`=1002;
UPDATE `notifications`            SET `is_read`=1, `is_deleted`=1, `updated_at`='2026-10-13 08:23:45' WHERE `id`=1003;
UPDATE `student_profile`          SET `updated_at`='2026-10-03 06:31:54'                             WHERE `id`=3;
UPDATE `subscription_preferences` SET `subscribe_medium`=0, `push_email`=0, `updated_at`='2026-10-13 08:23:46' WHERE `id`=1;
UPDATE `users`                    SET `password`='$2a$10$FhVCKefzdjfznx0nUJGpeOmcFYdw8FKTw5.YAWWfJvOPhCnZd7WdG',
                                      `updated_at`='2026-09-16 06:23:16' WHERE `id`=27;

SET time_zone = @old_tz;
