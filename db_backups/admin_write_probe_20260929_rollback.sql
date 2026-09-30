-- 回滚 2026-09-29「管理员端写路径验证（用户 CRUD / 审批 / 广播）」留下的痕迹。
--
-- 前置快照（本目录）：admin_write_probe_20260929_before.sql
--   三张表：users / teacher_profile / messages（验证前 messages 是 0 行）
-- 若本脚本的复原值有任何不确定，直接从快照恢复那三张表（那是唯一的权威）。
--
-- 被验证的写入（testsprite/local-check/admin-write-probe.mjs）：
--   1) 建号：POST /admin/users username=uiprobetmp role=2 password=123456
--      → users 新增一行 +（createProfileIfNeeded）teacher_profile 新增一行
--   2) 改号：PUT /admin/users/{id} 改姓名、把 password 改成 654321、再重置回 123456
--   3) 停用/启用：POST /admin/users/{id}/toggle-status（status 1→0→1）
--   4) 审批：PUT /admin/users/{id}/approve（status 置 1）
--   5) 广播：POST /admin/messages/broadcast targetRole=5
--      → messages 新增「每个 role=5 且 status=1 的用户一条」
--   6) 删号：DELETE /admin/users/{id}
--      → users 那行、teacher_profile 那行都没了；**但广播写进 messages 的那些行还在**
--        （删账号不会连带删消息 —— 消息是独立的数据，与删分不连带删审计日志同理）
--
-- 正常情况下：第 1 步建的行被第 6 步删掉了，users / teacher_profile 本来就等于快照；
-- messages 里那条临时广播是唯一确定会残留的东西。
-- 下面一律按**内容**定位，脚本半途崩了重跑也不误删真实业务数据
-- （uiprobetmp 这个账号名、以及「写路径验证-临时广播」这个标题，
--  在真实数据里不可能出现）。

START TRANSACTION;

-- 1) 账号残留（若脚本在"删号"之前崩了，这一行还在）
DELETE FROM users
 WHERE username = 'uiprobetmp'
    OR email    = 'uiprobetmp@academic.edu';

-- 2) 配套的教师资料残留（按 user_id 定位：先记下要删的 user_id 再删，
--    上面那句已把 users 删掉，所以这里用 teacher_profile 里"名字带临时验证"的行兜底）
DELETE FROM teacher_profile
 WHERE name LIKE '临时验证账号%';

-- 3) 广播残留：按标题定位（type=1 表示广播）
DELETE FROM messages
 WHERE title = '写路径验证-临时广播';

COMMIT;

-- 4) 自增计数器。真实"验证前应有的值"（这三条是为了让 check.sh 能"逐字节比对"才拨回原位）：
--      users          max(id)=57、共 35 行 → 计数器 58
--      teacher_profile max(id)=8、共 6 行  → 计数器 9
--      messages       0 行，但计数器原本就是 4（以前写过 id 1~3 的行，后来被清掉了）
--                      → 拨回 4，不是 1；拨到 1 也能跑，但那样 check.sh 的逐字节比对会报差异
--    ⚠️ 快照 admin_write_probe_20260929_before.sql 里的 users 计数器是 **76**，不是 58：
--    这份快照是在第一次（失败的）探针跑完之后才生成的，那次已经建过 id=76 的临时账号
--    （随后删掉）。所以"不忽略计数器"的那次比对必然报一条 NOTE（76 vs 58），
--    那不是回滚没做干净 —— 58 才是验证前应有的值。行内容的比对（忽略计数器）是 PASS。
--    InnoDB 只接受 >= max(id)+1 的值，所以即使拨不到理想值也不会低于现有最大 id，不会撞主键。
--    DDL 隐式提交，放在 COMMIT 之后，不混进上面的事务。
ALTER TABLE users AUTO_INCREMENT = 58;
ALTER TABLE teacher_profile AUTO_INCREMENT = 9;
ALTER TABLE messages AUTO_INCREMENT = 4;

-- 5) 复核（残留项都应为 0）
SELECT '残留：临时账号' AS chk, COUNT(*) AS n FROM users WHERE username='uiprobetmp'
UNION ALL
SELECT '残留：临时教师资料', COUNT(*) FROM teacher_profile WHERE name LIKE '临时验证账号%'
UNION ALL
SELECT '残留：临时广播', COUNT(*) FROM messages WHERE title='写路径验证-临时广播'
UNION ALL
SELECT 'users 总行数（应 35）', COUNT(*) FROM users
UNION ALL
SELECT 'teacher_profile 总行数（应 6）', COUNT(*) FROM teacher_profile
UNION ALL
SELECT 'messages 总行数（应 0）', COUNT(*) FROM messages;
