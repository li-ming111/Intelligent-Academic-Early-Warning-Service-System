-- 2026-10-02 第二十五轮：assistance_plans 增加 progress_note 列
--
-- 背景：PC 学生端「更新帮扶进度」弹窗里有一个**必填**的「完成情况」文本域，
-- 前端校验了非空、却从来不把它发出去（`Assistance.vue` 的 submitProgress 只发 percent）。
-- 于是学生填的内容凭空消失。加这一列让它真的落库。
--
-- ⚠️ 与 `academic-common/src/main/resources/init_schema.sql` 两处必须同步改，
--    且**改完不要跑 `db_backups/rebuild_schema.py`**（它从静态 dump 读，会把这个列抹掉）。
--
-- 回滚见 assistance_plan_progress_note_20261002_rollback.sql

USE academic_warning_system;

ALTER TABLE `assistance_plans`
    ADD COLUMN `progress_note` text NULL COMMENT '学生更新进度时填写的完成情况说明（最近一次）'
    AFTER `progress`;
