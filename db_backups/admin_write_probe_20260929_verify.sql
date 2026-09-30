-- 复核 2026-09-29「管理员端写路径验证（用户 CRUD / 审批 / 广播）」的回滚结果（只读）。
-- 用法：mysql -uroot -proot academic_warning_system < 本文件
--
-- 判读：result 列全为 PASS，且第一段残留项全为 0。
-- 口径与之前几次写路径验证一致：**行内容逐字段等于验证之前，自增计数器除外**
-- （更强的证据在 check.sh：重新 dump 三张表，去掉 AUTO_INCREMENT= 行后与快照逐字节 diff）。
--
-- 这里挑几行"验证期间最容易被碰坏"的真实数据来核对 —— 都是脚本**不该**碰到的：
--   · admin（users#1）：全程用它登录，但没有任何一步会写它
--   · 于凤海（users#29）：教师列表里的样板账号，改号/停用都不该落到他身上
--   · 司明（users#27，学生）：与临时账号同为 role 相邻的账号，确认没被误改
--   · teacher_profile#1（于凤海的资料行）：确认 createProfileIfNeeded 没往它上面写

SELECT '=== 一、残留检查（都应为 0）===' AS section;

SELECT '临时账号' AS chk, COUNT(*) AS n FROM users WHERE username='uiprobetmp'
UNION ALL
SELECT '临时账号（按邮箱兜底）', COUNT(*) FROM users WHERE email='uiprobetmp@academic.edu'
UNION ALL
SELECT '临时教师资料', COUNT(*) FROM teacher_profile WHERE name LIKE '临时验证账号%'
UNION ALL
SELECT '临时广播（messages）', COUNT(*) FROM messages WHERE title='写路径验证-临时广播'
UNION ALL
SELECT 'messages 总行数（应 0）', COUNT(*) FROM messages
UNION ALL
SELECT 'users 总行数（应 35）', COUNT(*) FROM users
UNION ALL
SELECT 'teacher_profile 总行数（应 6）', COUNT(*) FROM teacher_profile;

SELECT '=== 二、真实账号逐字段核对（result 应全为 PASS）===' AS section;

-- 口令列比的是"是不是 BCrypt 串"，不打印任何哈希内容
SELECT 'users#1 admin 未变' AS chk,
       IF(username='admin' AND name='系统管理员' AND role=4 AND status=1
          AND password LIKE '$2a$%' AND LENGTH(password)=60, 'PASS', 'FAIL') AS result
  FROM users WHERE id=1
UNION ALL
SELECT 'users#29 于凤海 未变',
       IF(username='10001' AND name='于凤海' AND role=2 AND status=1
          AND password LIKE '$2a$%' AND LENGTH(password)=60, 'PASS', 'FAIL')
  FROM users WHERE id=29
UNION ALL
SELECT 'users#27 学生账号未变',
       IF(role=1 AND password LIKE '$2a$%' AND LENGTH(password)=60 AND status=1, 'PASS', 'FAIL')
  FROM users WHERE id=27
UNION ALL
-- 教师资料：于凤海 #1 的学院与姓名不该被临时账号带偏
SELECT 'teacher_profile#1 未变',
       IF(user_id=29 AND name='于凤海' AND college_id=1, 'PASS', 'FAIL')
  FROM teacher_profile WHERE id=1
UNION ALL
-- 角色分布回到验证前：学生 24 / 教师 6 / 辅导员 1 / 管理员 2 / 学院管理员 2
SELECT 'role=1 行数（应 24）', IF(COUNT(*)=24,'PASS','FAIL') FROM users WHERE role=1
UNION ALL
SELECT 'role=2 行数（应 6）', IF(COUNT(*)=6,'PASS','FAIL') FROM users WHERE role=2
UNION ALL
SELECT 'role=3 行数（应 1）', IF(COUNT(*)=1,'PASS','FAIL') FROM users WHERE role=3
UNION ALL
SELECT 'role=4 行数（应 2）', IF(COUNT(*)=2,'PASS','FAIL') FROM users WHERE role=4
UNION ALL
SELECT 'role=5 行数（应 2）', IF(COUNT(*)=2,'PASS','FAIL') FROM users WHERE role=5;
