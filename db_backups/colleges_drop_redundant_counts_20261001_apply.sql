-- ============================================================
-- 2026-10-01 清理：删掉 colleges.student_count / teacher_count 两个冗余计数列
-- ============================================================
-- 为什么删（不是"校验太严所以放宽"，而是"这两列本身没有存在理由"）：
--
--   1) **Java 侧从来没有读者。**
--      `College.studentCount / teacherCount / counselorCount` 三个字段**本来就**
--      标着 `@TableField(exist = false)` —— 只算不存。所有对外展示的"学生数/教师数"
--      都是现算的 `COUNT(*)`：
--        - admin-service `AdminController.getStudentCount(id)` / `getTeacherCount(id)`
--          （`GET /admin/colleges` 用它拼 Map）
--        - admin-service `CollegeServiceImpl.getAllColleges()` 读时现算后 set 回对象
--        - college-service `CollegeController.getAllColleges()` 同上
--      全仓 grep `getStudentCount() / getTeacherCount()` 的**读取点：0 个**。
--
--   2) **唯一的写入方是自助注册路径的 `+1`。**
--      `RegistrationMapper.increaseCollegeStudentCount / increaseCollegeTeacherCount`
--      （`UPDATE colleges SET xxx_count = xxx_count + 1`），只在
--      `UserServiceImpl.createStudentProfile / createTeacherProfile` 里各调一次。
--      管理员建号、删号、批量导入、改学生的学院……**没有任何一条路径会动它们**。
--      净效果：删掉一个学生，这两列就永久偏大 —— 账实立刻不符。
--      （`counselor_count` 更早就是这样：**库里根本没有这一列**，只有实体上那个
--        `exist = false` 的字段。）
--
--   3) **它靠一条不变量守着，而那条不变量是它唯一的存在理由。**
--      `verify_invariants.py` 的 B 组原本有
--        `colleges.student_count == 该学院学生档案数`
--        `colleges.teacher_count == 该学院教师档案数`
--      两项。与其去补全所有写路径来喂饱一条没人读的列，不如把列删掉：
--      "账实相符"由"根本没有冗余列"来保证，比"有人记得每次都维护它"可靠。
--      B 组这两项随之移除（**40 项 → 38 项**）。
--
--   ⚠️ 顺带记一个该警觉的信号：`<>` 判据对 NULL 是**不成立**的
--      （`NULL <> 24` 结果是 NULL，不是 TRUE），所以这两列哪天变成 NULL，
--      B 组那两项会**空转通过**。删列前实测过它们有值且判据命中 0 行，
--      所以当时不是空转；但这类"NULL 静默通过"是写判据时该主动防的。
--
-- 影响面：colleges 4 行（软件学院 24/5、电子工程学院 0/1、商学院 0/0、艺术设计学院 0/0）。
--         **没有任何代码读这两列**，所以删列不影响任何接口的返回。
--
-- 回滚：colleges_drop_redundant_counts_20261001_rollback.sql
--       （回滚脚本用 student_profile / teacher_profile 现算回填，不是写死快照 ——
--         因为删列前 B 组校验通过，现算值与原值逐行相同。）
-- ============================================================

-- 0) 删之前先把现状打出来（留档用；这两列此后不再存在）
SELECT id, name, student_count, teacher_count
  FROM colleges
 ORDER BY id;

-- 1) 幂等删列（MySQL 没有 `DROP COLUMN IF EXISTS`，走 prepared statement）
SET @has_student := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colleges' AND COLUMN_NAME = 'student_count'
);
SET @ddl := IF(@has_student = 1,
  'ALTER TABLE colleges DROP COLUMN student_count',
  'SELECT ''student_count 已不存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_teacher := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colleges' AND COLUMN_NAME = 'teacher_count'
);
SET @ddl := IF(@has_teacher = 1,
  'ALTER TABLE colleges DROP COLUMN teacher_count',
  'SELECT ''teacher_count 已不存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 核对：两列都应已不在 information_schema 里（期望 0 行）
SELECT COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colleges'
   AND COLUMN_NAME IN ('student_count', 'teacher_count');

-- 3) 核对：colleges 表结构里不该再有 *_count 列
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'colleges'
 ORDER BY ORDINAL_POSITION;
