-- 回滚：2026-10-02 第二十五轮 assistance_plans.progress_note
USE academic_warning_system;

ALTER TABLE `assistance_plans` DROP COLUMN `progress_note`;
