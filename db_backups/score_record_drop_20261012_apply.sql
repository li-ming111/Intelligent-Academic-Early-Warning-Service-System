-- ============================================================
-- 2026-10-12（W39）清理：删掉死表 score_record（教师录入暂存表）
-- ============================================================
-- 为什么删（W36/W38 把成绩链路全量迁到权威表 scores 之后的收尾）：
--
--   1) **全仓已无任何 SQL 层面的读写方。**
--      判据（三条，都是 grep 实测，不是读代码推的）：
--        a. `grep -rn --include=*.java --include=*.xml
--             -E "(FROM|INTO|UPDATE|JOIN|DELETE FROM)[[:space:]]+score_record"`
--           ⇒ **零命中**（既有注解 @Select，也没有 mapper XML —— 全仓 `*mapper*.xml` 一个都没有）
--        b. `grep -rn "BaseMapper<ScoreRecord>|extends.*BaseMapper.*ScoreRecord"` ⇒ **零命中**
--           —— 没有任何 MyBatis-Plus 泛型用法会按 @TableName 自动生成 score_record 的 SQL。
--           （实体 `ScoreRecord.java` 上仍写着 `@TableName("score_record")`，但它**只被当作
--             只读投影类型**用：teacher-service `ScoreRecordMapper` 的 SQL 全部 `FROM scores`，
--             名字里的 "Record" 是历史残留。）
--        c. 活库 `information_schema` 里**没有任何触发器/视图/存储过程**的正文引用它。
--      ⇒ 表在，但没人看得见它。
--
--   2) **它是"两套成绩口径"这条老账的最后一块**，而这块已经没有内容。
--      W36（2026-10-10）教师端读+写全量迁 `scores`；W38 管理员导入也直写 `scores`
--      （`AdminController.upsertScore` 取代了 `syncToScores(ScoreRecord)`）;
--      辅导员端、学生端、预警扫描（`ScoreCaliber`）、AI 分析读的全是 `scores`。
--      迁移完成后 score_record **只增不改地**留着 246 行历史数据，谁都不读。
--
--   3) **留着它的代价是"下一个人会以为它有用"。**
--      2026-10 这一个月里，至少三份文档（`系统功能模块介绍.md`、两份旧台账）还在写
--      "双写 score_record + scores / syncToScores()" —— 而那段代码 2026-10-10 就删了。
--      文档说谎的根子之一就是库里还摆着这张表。
--
-- 影响面：score_record 246 行、22 列（活库实测）。
--         **没有任何代码读它** ⇒ 删表不影响任何接口的返回。
--         删除前已用 `mysqldump` 原样导出全表（结构 + 数据）到
--         `db_backups/score_record_drop_20261012_rollback.sql`（46476 字节，含 CREATE TABLE + 全部行）。
--
-- 回滚：`mysql -uroot -p academic_warning_system < db_backups/score_record_drop_20261012_rollback.sql`
--       （该文件是 **mysqldump 原样输出**，不是手写重建 —— 结构与 246 行数据逐字节可还原。）
--
-- ⚠️ 同轮必须一起改的**第四处**（漏了就每个服务启动都报漂移）：
--     `academic-common/.../config/DataInitializer.java` 的 `EXPECTED_COLUMNS` 里
--     `EXPECTED_COLUMNS.put("score_record", 22);` 这一行 —— 表没了，基线还在，
--     自检会打 `score_record(表不存在)`。本轮已删该行（14 项 → 13 项）。
-- ============================================================

-- 0) 删之前先留档：行数 + 列数（期望 246 行 / 22 列）
SELECT COUNT(*) AS row_count FROM score_record;
SELECT COUNT(*) AS column_count
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'score_record';

-- 1) 幂等删表（MySQL 原生支持 IF EXISTS，多服务并行/重复执行都安全）
DROP TABLE IF EXISTS `score_record`;

-- 2) 核对：表应已不在 information_schema 里（期望 0 行）
SELECT TABLE_NAME
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'score_record';
