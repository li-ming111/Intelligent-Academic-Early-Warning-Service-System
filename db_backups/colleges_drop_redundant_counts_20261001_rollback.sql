-- ============================================================
-- 回滚：把 colleges.student_count / teacher_count 两列加回来
-- ============================================================
-- 对应 colleges_drop_redundant_counts_20261001_apply.sql
--
-- 回填口径：**用 student_profile / teacher_profile 现算**，不是写死快照。
--   依据：删列前 `verify_invariants.py` B 组那两项是通过的
--   （判据命中 0 行），也就是说当时的列值 == 现算值，逐行相同。
--   所以现算回填能把原值一字不差地还原，而且对"删列之后才注册的人"
--   也仍然是对的 —— 写死快照做不到这一点。
--
--   ⚠️ 但要注意：一旦回滚，那两列**又变回无人维护的冗余列**（注册路径的
--      `+1` 已经在 2026-10-01 的代码里删掉了）。如果只是想临时看一眼，
--      看完请再跑一次 apply 脚本。要长期保留，就得把
--      `RegistrationMapper.increaseCollegeStudentCount/TeacherCount` 和
--      `UserServiceImpl` 里那两处调用一起恢复（见 git 历史 4cefce9 之后）。
-- ============================================================

-- 1) 幂等加列
SET @has_student := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colleges' AND COLUMN_NAME = 'student_count'
);
SET @ddl := IF(@has_student = 0,
  'ALTER TABLE colleges ADD COLUMN student_count INT NULL DEFAULT 0 COMMENT ''冗余计数列：已于 2026-10-01 删除，此列由回滚脚本临时恢复'' ',
  'SELECT ''student_count 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_teacher := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colleges' AND COLUMN_NAME = 'teacher_count'
);
SET @ddl := IF(@has_teacher = 0,
  'ALTER TABLE colleges ADD COLUMN teacher_count INT NULL DEFAULT 0 COMMENT ''冗余计数列：已于 2026-10-01 删除，此列由回滚脚本临时恢复'' ',
  'SELECT ''teacher_count 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 现算回填
UPDATE colleges c
   SET c.student_count = (SELECT COUNT(*) FROM student_profile sp WHERE sp.college_id = c.id),
       c.teacher_count = (SELECT COUNT(*) FROM teacher_profile t  WHERE t.college_id = c.id);

-- 3) 核对：应回到删列前那组数（软件学院 24/5、电子工程学院 0/1、商学院 0/0、艺术设计学院 0/0）
SELECT id, name, student_count, teacher_count
  FROM colleges
 ORDER BY id;

-- 4) 核对：与现算值逐行相同（期望 0 行）
SELECT c.id, c.name, c.student_count, c.teacher_count
  FROM colleges c
 WHERE c.student_count <> (SELECT COUNT(*) FROM student_profile sp WHERE sp.college_id = c.id)
    OR c.teacher_count <> (SELECT COUNT(*) FROM teacher_profile t  WHERE t.college_id = c.id);
