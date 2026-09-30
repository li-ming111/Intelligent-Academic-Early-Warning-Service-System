-- ============================================================
-- 回滚：撤销 2026-09-30 辅导员跨学院修复
-- ============================================================
-- 把 classes 34/35 的辅导员、以及 counselor_class_management 的两行还原成
-- 修复前的样子（值取自 full_20260930_before_counselor_fix.sql 快照，
-- 不是凭记忆写的 —— 两行原本就是 id=7/8、counselor_id=53、status=1、
-- created_at/updated_at 均为 2026-09-16 11:23:16）。
-- ============================================================

UPDATE classes
   SET counselor_id = 53
 WHERE id IN (34, 35);

INSERT INTO counselor_class_management
       (id, counselor_id, class_id, class_name, status, created_at, updated_at)
VALUES (7, 53, 34, '人工智能2101班', 1, '2026-09-16 11:23:16', '2026-09-16 11:23:16'),
       (8, 53, 35, '人工智能2102班', 1, '2026-09-16 11:23:16', '2026-09-16 11:23:16');

-- 冗余计数列回到修复前的 8（当时确实是 8 个班；原值取自
-- full_20260930_before_counselor_fix.sql 快照里的 counselor_profiles 行）
UPDATE counselor_profiles
   SET class_count = 8
 WHERE id = 1
   AND user_id = 53;
