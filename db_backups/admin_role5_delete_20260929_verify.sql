-- 学院管理员删号分支验证 —— 逐字段断言（只读）。
-- 由 admin_role5_delete_20260929_check.sh 调用。

SELECT '一、临时账号残留（期望 0）' AS 检查项,
       (SELECT COUNT(*) FROM users WHERE username = 'uiprobeadm5') AS 实际,
       IF((SELECT COUNT(*) FROM users WHERE username = 'uiprobeadm5') = 0, 'PASS', 'FAIL') AS 判定;

SELECT '二、临时档案行残留（期望 0）' AS 检查项,
       (SELECT COUNT(*) FROM college_admin_profile WHERE name = 'tmpadm5') AS 实际,
       IF((SELECT COUNT(*) FROM college_admin_profile WHERE name = 'tmpadm5') = 0, 'PASS', 'FAIL') AS 判定;

-- 四张 profile 表都不该有 user_id 指向不存在账号的行。
-- 这一条是这次修复的**不变量**：删号之后不该留下任何孤儿档案行。
SELECT '三、student_profile 孤儿行' AS 检查项,
       (SELECT COUNT(*) FROM student_profile p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) AS 实际,
       IF((SELECT COUNT(*) FROM student_profile p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) = 0, 'PASS', 'FAIL') AS 判定;

SELECT '四、teacher_profile 孤儿行' AS 检查项,
       (SELECT COUNT(*) FROM teacher_profile p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) AS 实际,
       IF((SELECT COUNT(*) FROM teacher_profile p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) = 0, 'PASS', 'FAIL') AS 判定;

SELECT '五、counselor_profiles 孤儿行' AS 检查项,
       (SELECT COUNT(*) FROM counselor_profiles p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) AS 实际,
       IF((SELECT COUNT(*) FROM counselor_profiles p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) = 0, 'PASS', 'FAIL') AS 判定;

SELECT '六、college_admin_profile 孤儿行（本次修复的对象）' AS 检查项,
       (SELECT COUNT(*) FROM college_admin_profile p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) AS 实际,
       IF((SELECT COUNT(*) FROM college_admin_profile p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id)) = 0, 'PASS', 'FAIL') AS 判定;

-- 账号总量：35 个（验证前后都该是 35）
SELECT '七、users 总行数（期望 35）' AS 检查项,
       (SELECT COUNT(*) FROM users) AS 实际,
       IF((SELECT COUNT(*) FROM users) = 35, 'PASS', 'FAIL') AS 判定;

-- 学院管理员档案行：2 行（user 56 / 57，两行都是原有数据）
SELECT '八、college_admin_profile 行数（期望 2）' AS 检查项,
       (SELECT COUNT(*) FROM college_admin_profile) AS 实际,
       IF((SELECT COUNT(*) FROM college_admin_profile) = 2, 'PASS', 'FAIL') AS 判定;
