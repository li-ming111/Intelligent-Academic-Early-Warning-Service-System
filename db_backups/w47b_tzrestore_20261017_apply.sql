-- ============================================================
-- W47B 演示数据「时区漂移」还原（2026-10-17）
-- 用法：mysql -uroot -proot academic_warning_system < 本文件
--
-- 目标：把活库里 15 个格子还原到**已入库种子**(academic_warning_system.sql)的基线。
--
-- 背景（本轮的实质发现）：
--   W41 于 10-13 清库并重生成根种子；此后 W43 的 cleanup/fix_tz 两步把若干
--   TIMESTAMP 列写早了 8 小时（先在 +08 会话写 UTC 字面量、又在 +00 会话重写），
--   而 W44/W45/W46 的探针在真实演示行上落下了 updated_at/updated_by 残迹
--   （如 feedback.1、teacher_profile.1 的 updated_at = 2026-10-14 02:54 本地，
--    正是 W44 写探针的运行时刻；探针只复原了业务列，没复原 updated_at）。
--
--   判据钉在**存储 epoch**（与会话时区无关）上，比较 aws_seedcheck(种子按自身
--   头部 +00:00 原样载入) 与活库：全库 1665 个时间格子里恰有 9 格不同 ⇒ 上面两组。
--   交叉验证：users 表里 28 个账号共享同一字面量与 epoch(1789568596)，
--   唯独 id=27(司明) 早 8 小时 ⇒ 活库那格才是离群的，种子是对的。
--
-- 备份：db_backups/academic_warning_system_full_20261017_2203_before_w47b_tzrestore.sql
-- 回滚：db_backups/w47b_tzrestore_20261017_rollback.sql
--
-- 判据（跑完逐条核）：
--   · genfix 类逐格比对：种子 vs 活库 = 0 差异（全 49 表）
--   · verify_invariants.py 41/41
--   · 探针残留文本扫描 0 命中
--   · users.password 仍能用 123456 登录（改的是盐，不是口令）
-- ============================================================
SET @old_tz := @@session.time_zone;
SET time_zone = '+00:00';

-- 探针残迹（updated_by / updated_at 被 W44-W46 探针 touch）
UPDATE `academic_warning`           SET `updated_by`=NULL,        `updated_at`='2026-10-03 17:19:36' WHERE `id`=395;
UPDATE `feedback`                   SET `update_time`='2026-06-25 09:53:16'                          WHERE `id`=1;
UPDATE `teacher_profile`            SET `updated_at`='2026-05-21 09:27:04'                           WHERE `id`=1;

-- W43 时区错写（早 8 小时）+ W42/W43 探针改过业务列
UPDATE `assistance_plans`           SET `updated_at`='2026-10-09 07:12:10'                           WHERE `id`=1001;
UPDATE `notifications`              SET `updated_at`='2026-09-02 06:36:09'                           WHERE `id`=1002;
UPDATE `notifications`              SET `is_read`=0, `is_deleted`=0, `updated_at`='2026-10-02 10:52:28' WHERE `id`=1003;
UPDATE `student_profile`            SET `updated_at`='2026-10-03 14:31:54'                           WHERE `id`=3;
UPDATE `subscription_preferences`   SET `subscribe_medium`=1, `push_email`=1, `updated_at`='2026-10-01 14:52:00' WHERE `id`=1;
UPDATE `users`                      SET `password`='$2a$10$W4zlhXKkn8zf/vnljhX3gegNikhnpqwVxRjUpT2vNwezbJZ8MB/YG',
                                        `updated_at`='2026-09-16 14:23:16' WHERE `id`=27;

SET time_zone = @old_tz;

-- 复核（+00:00 口径）
SELECT 'users27'  AS what, id, LEFT(password,14) AS pwd, updated_at FROM `users` WHERE id=27;
SELECT 'sp3'      AS what, id, updated_at FROM `student_profile` WHERE id=3;
SELECT 'n1003'    AS what, id, is_read, is_deleted, updated_at FROM `notifications` WHERE id=1003;
SELECT 'sub1'     AS what, id, subscribe_medium, push_email, updated_at FROM `subscription_preferences` WHERE id=1;
SELECT 'ap1001'   AS what, id, updated_at FROM `assistance_plans` WHERE id=1001;
SELECT 'aw395'    AS what, id, updated_by, updated_at FROM `academic_warning` WHERE id=395;
SELECT 'tp1'      AS what, id, updated_at FROM `teacher_profile` WHERE id=1;
SELECT 'fb1'      AS what, id, update_time FROM `feedback` WHERE id=1;
