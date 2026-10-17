-- ============================================================
-- 演示数据清理（2026-10-13 W41）
-- 目标：只留下"真实"的演示数据 —— 清掉历轮探针/写路径验证留下的残留行，
--       它们会让管理员端（消息管理 / 操作日志）看到与业务无关的假记录。
--
-- 备份：db_backups/academic_warning_system_full_20261013_1023_before_dataclean.sql
-- 回滚：db_backups/dataclean_20261013_rollback.sql（逐行 INSERT 还原被删行）
--
-- 判据：清理后
--   · login_logs 无悬空 user_id（指向已删账号的行）
--   · audit_logs 只剩 id 41 / 59 两条真实业务审计（成绩申诉复核）
--   · 全库 text 列 grep probe/临时/你好 零命中
-- ============================================================
START TRANSACTION;

-- 1) 操作日志：只留两条真业务审计（41=通过大学英语申诉，59=驳回高等数学申诉）。
--    其余 81 行全是探针（新建/删除 admin_tasks、W32PRB、W33PRB、ZZTEST、uiprobe 等）。
DELETE FROM audit_logs WHERE id NOT IN (41, 59);

-- 2) 消息管理页里的 4 条"写路径验证-临时广播"（sender=1 -> 56/57，2026-09-29 / 10-03）
DELETE FROM messages WHERE id IN (4, 5, 27, 28);

-- 3) 司明通知中心里的垃圾行 title/content 都是「你好」
DELETE FROM notifications WHERE id = 1008;

-- 4) 辅导员对司明的沟通记录里那条 category=normal、内容「你好」的垃圾行
DELETE FROM communication_logs WHERE id = 2004;

-- 5) 登录日志里指向已删探针账号的悬空行（uiprobetmp / probe_w26_* / W33PRB_login）
DELETE l FROM login_logs l LEFT JOIN users u ON u.id = l.user_id
  WHERE l.user_id IS NOT NULL AND u.id IS NULL;

COMMIT;
