-- W36（2026-10-10）回滚：撤销 scores.remark 列。
-- ⚠️ 仅当尚未有新数据写入该列时才是无损回滚；若已有成绩行填了 remark，回滚会丢这些备注。
ALTER TABLE `scores` DROP COLUMN `remark`;
