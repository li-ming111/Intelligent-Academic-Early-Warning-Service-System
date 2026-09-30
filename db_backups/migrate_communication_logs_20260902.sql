-- ============================================================
-- 迁移: 补建 communication_logs 表 (辅导员沟通记录)
-- 生成于 2026-09-02
-- 背景: counselor-service 的沟通记录功能(CommunicationLog 实体 CRUD)
--       代码已就绪且 init_schema.sql 已含建表, 但线上库缺这张表(功能从未迁移),
--       导致任何命中 /counselors/**/communication-logs 的请求 500。
-- 本脚本: ① 按 init_schema.sql 同款定义建表+索引(幂等, 表已存在则跳过建表)
--         ② 补 3 条辅导员53(司明)×学生3(司明, 计科2306班, 预警301~303)演示记录
-- 撤销:   DROP TABLE communication_logs;  (表为纯新增, 无其它依赖)
-- ============================================================
USE academic_warning_system;
SET NAMES utf8mb4;

-- ---------- ① 建表 (与 init_schema.sql 完全一致) ----------
CREATE TABLE IF NOT EXISTS `communication_logs` (
    `id`           bigint auto_increment primary key,
    `counselor_id` bigint                              null,
    `teacher_id`   bigint                              null,
    `student_id`   bigint                              null,
    `warning_id`   bigint                              null,
    `content`      text                                null,
    `category`     varchar(50)                         null,
    `status`       int       default 0                 null,
    `reply`        text                                null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);
-- 索引: MySQL 不支持 CREATE INDEX IF NOT EXISTS, 此处表为新表, 直接建; 若重复执行报错请忽略
CREATE INDEX `idx_communication_logs_counselor_id` ON `communication_logs` (`counselor_id`);
CREATE INDEX `idx_communication_logs_student_id`   ON `communication_logs` (`student_id`);
CREATE INDEX `idx_communication_logs_warning_id`   ON `communication_logs` (`warning_id`);

-- ---------- ② 演示数据: 辅导员53(司明, 用户1000000001) 与 学生3(司明, 计科2306班) ----------
INSERT INTO communication_logs
  (id, counselor_id, teacher_id, student_id, warning_id, content, category, status, reply, created_at, updated_at)
VALUES
  (2001, 53, NULL, 3, 301,
   '就本学期操作系统(40分)、计算机基础(47分)两门挂科课程约谈司明, 了解其学习困难集中在程序设计基础薄弱与上机练习不足, 已建议参加操作系统学习小组并预约每周两次辅导答疑。',
   '预警约谈', 1,
   '学生表示已加入学习小组, 承诺每周完成课后上机练习并在两周内提交复习计划。',
   '2026-08-29 10:30:00', '2026-08-29 10:30:00'),
  (2002, 53, NULL, 3, 302,
   '跟踪帮扶计划(计划1001)执行情况: 司明已参加操作系统学习小组第2次辅导, 上机作业提交正常; 继续观察大学英语、高等数学复习进度。',
   '帮扶跟进', 0, NULL,
   '2026-09-01 15:00:00', '2026-09-01 15:00:00'),
  (2003, 53, NULL, 3, NULL,
   '期中学习状态沟通: 反馈近期因课程冲突缺席两次小组辅导, 已协调补课时间, 提醒关注补考报名安排。',
   '日常关怀', 1,
   '已确认补课时间并完成补考报名, 学习状态趋于稳定。',
   '2026-09-02 09:00:00', '2026-09-02 09:00:00')
ON DUPLICATE KEY UPDATE
  counselor_id = VALUES(counselor_id), student_id = VALUES(student_id),
  warning_id = VALUES(warning_id),      content = VALUES(content),
  category    = VALUES(category),       status   = VALUES(status),
  reply       = VALUES(reply),          updated_at = NOW();

-- ---------- 验证 ----------
-- SELECT id, counselor_id, student_id, warning_id, category, status, LEFT(content,20) FROM communication_logs ORDER BY id;
