-- 学院管理员删号分支验证（admin-role5-delete-probe.mjs）的回滚脚本。
--
-- 这次验证只写了两样东西，都在同一个临时账号名下：
--   1. users 一行（username = uiprobeadm5，由接口创建）
--   2. college_admin_profile 一行（user_id 指向上面那行，**由验证脚本用 SQL 手工插入**）
--
-- 为什么要手工插第 2 行：建号接口有意不给 role=5 建档案行（档案表的 college_id 是
-- NOT NULL，建号表单里没有学院字段），所以"建号→删号"这条链根本构造不出
-- "有档案的学院管理员账号"，也就验不到删除分支。必须先造出那一行才行。
--
-- 正常跑完脚本残留为 0（脚本自己会清）。这个文件是给"脚本中途崩了"用的。
--
-- 用法：mysql -uroot -proot academic_warning_system < admin_role5_delete_20260929_rollback.sql

DELETE FROM college_admin_profile
 WHERE user_id IN (SELECT id FROM users WHERE username = 'uiprobeadm5');

-- 兜底：user_id 已经悬空、子查询选不到的情况（脚本崩在删号之后、清档之前）
DELETE FROM college_admin_profile WHERE name = 'tmpadm5';

DELETE FROM users WHERE username = 'uiprobeadm5';

-- 复核：两条都应返回 0
SELECT
  (SELECT COUNT(*) FROM users WHERE username = 'uiprobeadm5') AS users_residue,
  (SELECT COUNT(*) FROM college_admin_profile WHERE name = 'tmpadm5') AS profile_residue;

-- NOTE：users / college_admin_profile 的 AUTO_INCREMENT 会前移（本次两次跑下来
-- users 计数器到 61、college_admin_profile 到 5）。这是自增计数器，不是数据差异 ——
-- 表内容与验证前逐字节相同，检查脚本 admin_role5_delete_20260929_check.sh 会证明这一点。
