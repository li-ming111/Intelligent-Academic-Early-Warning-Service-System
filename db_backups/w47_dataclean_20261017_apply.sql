-- ============================================================
-- W47 演示数据清理（2026-10-17）
-- 目标：把 W45/W46 探针在演示库里写回的残留行清掉，让活库回到
--       已入库的 W41 基线（= 根种子 academic_warning_system.sql 的 id 集合）。
--
-- 背景：W41（2026-10-13）清过一次；此后 W42→W46 六轮走查又各自留下探针行，
--       而 W41 那版清理脚本只处理「悬空行」，探针行（id 不悬空）全留下了。
--       verify_invariants.py 依旧是 41/41 全绿 —— 它只查「自洽/不悬空」，
--       不查「这行是不是真业务」。⇒ 绿色 ≠ 数据干净，故另起本脚本。
--
-- 备份：db_backups/academic_warning_system_full_20261017_2032_before_w47_dataclean.sql
-- 回滚：db_backups/w47_dataclean_20261017_rollback.sql
--
-- 判据（跑完逐条核）：
--   · audit_logs       行数 == 种子 2
--   · notifications    行数 == 种子 8
--   · score_audit_log  行数 == 种子 1
--   · export_history   行数 == 种子 27
--   · login_logs       行数 == 种子 342
--   · 全库 text 列 grep W42|W43|W44|W45|W46|PROBE|探针 零命中
--   · verify_invariants.py 仍 41/41
-- ============================================================
START TRANSACTION;

-- audit_logs: 删 122 行（id 84..205）
DELETE FROM audit_logs WHERE id BETWEEN 84 AND 205;
-- notifications: 删 1 行（id 90005..90005）
DELETE FROM notifications WHERE id BETWEEN 90005 AND 90005;
-- score_audit_log: 删 3 行（id 16..18）
DELETE FROM score_audit_log WHERE id BETWEEN 16 AND 18;
-- export_history: 删 64 行（id 30..93）
DELETE FROM export_history WHERE id BETWEEN 30 AND 93;
-- login_logs: 删 192 行（id 359..550）
DELETE FROM login_logs WHERE id BETWEEN 359 AND 550;

COMMIT;
