-- 清理 messages 表中 2 条乱码测试消息(内容为 ?????? 等, 无法用于演示)
USE academic_warning_system;
-- 两条均为乱码测试消息: id2="??????", id3="法撒旦: 啊发生的"(GBK 解码乱码)
DELETE FROM messages WHERE id IN (2, 3);
-- 验证: SELECT id, content FROM messages;
