-- ============================================================
-- 2026-09-30 修复：辅导员的「管理班级」跨了学院
-- ============================================================
-- 现象：辅导员司明（users.id=53，counselor_profiles.college_id=1 软件学院）
--       名下有 8 个班 —— 软件学院 6 个 + 电子工程学院 2 个（classes 34/35）。
--       辅导员端「管理班级」显示 8，其中 7 个是没有学生的空壳班，
--       而「有学生的班级」是 1。同一个词两个数，且 8 与本人档案里的学院自相矛盾。
--
-- 依据：classes.counselor_id 存的是 users.id（CounselorController:113/801 有明确注释）。
--       唯一被代码读取的关联就是这一列 —— counselor_class_management 全工程零引用，
--       但它是同一事实的第二份拷贝（8 行），一并改掉以免库内自相矛盾。
--
-- 决定：电子工程学院的两个班不挂软件学院的辅导员 → 管理班级 = 6。
--       6 与学院管理员端 classCount=6 同源同值，两端对得上。
--
-- 回滚：counselor_fix_20260930_rollback.sql
-- 快照：full_20260930_before_counselor_fix.sql（整库，80 表）
-- ============================================================

-- 1) 真源：classes.counselor_id
UPDATE classes
   SET counselor_id = NULL,
       updated_at   = NOW()
 WHERE id IN (34, 35)
   AND counselor_id = 53;

-- 2) 影子表：无代码引用，但保持与真源一致（否则库里 8 行 vs 应用 6 个）
DELETE FROM counselor_class_management
 WHERE class_id IN (34, 35)
   AND counselor_id = 53;
