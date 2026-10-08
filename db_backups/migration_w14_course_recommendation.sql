-- W14 (2026-10-04) 教师「推荐选课」建表。
-- 背景：`POST /teachers/courses/recommend` 此前是纯回声桩（不落库），库里也没有任何推荐表。
-- 用法（**只建表，不删不改任何既有数据**）：
--   MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot academic_warning_system < db_backups/migration_w14_course_recommendation.sql
-- 回滚：
--   DROP TABLE IF EXISTS `course_recommendation`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `course_recommendation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL COMMENT '教师的 users.id（与 courses.teacher_id / classes.teacher_id 同一套）',
    `course_id` bigint NOT NULL,
    `target_class_id` bigint DEFAULT NULL COMMENT 'NULL = 该教师名下所有班级',
    `reason` varchar(500) NOT NULL,
    `status` int NOT NULL DEFAULT '1' COMMENT '1=有效, 0=已撤回',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_rec_teacher` (`teacher_id`),
    KEY `idx_course_rec_course` (`course_id`),
    KEY `idx_course_rec_class` (`target_class_id`),
    CONSTRAINT `fk_course_recommendation_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
