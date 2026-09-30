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

-- 3) 【2026-09-30 补】冗余计数列漏同步了。
--    上一步摘掉 2 个班之后，counselor_profiles.class_count 仍写着 8。
--    该列没有代码读取（学院管理员端的 classCount 是现算 classes.size()，
--    CollegeAdminController:80/391），但它正是 fix10(A13/A14)/fix11 一路维护的
--    "账实相符"那一类列：库里写 8、按 classes.counselor_id 数出来是 6，
--    与 fix11 在 A18 里批评的"带班表 1 个班 vs 主表 8 个班"是同一种自相矛盾。
--    同步到 6；student_count 仍为 24，无需改（24 名学生都在这 6 个班里）。
UPDATE counselor_profiles
   SET class_count = 6
 WHERE id = 1
   AND user_id = 53;
