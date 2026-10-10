-- W36（2026-10-10）：教师端成绩全量迁到权威表 scores。
-- 第 1 步：给 scores 补「备注」列。
--
-- 背景：教师录入/导入/导出都带「备注」，但此前备注只存在 score_record（教师录入暂存表），
--       scores 没有这一列。教师端改用 scores 后，备注必须落到权威表，否则被静默丢弃。
--
-- 落库前核对：SELECT COUNT(*), SUM(remark IS NOT NULL AND remark<>'') FROM score_record;  -- 246/246 有值
ALTER TABLE `scores` ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL AFTER `grade_point`;
