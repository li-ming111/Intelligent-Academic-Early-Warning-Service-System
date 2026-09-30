-- ============================================================================
-- 批次 11 的回滚脚本（依赖 fix11 在事务之前留下的 bak_fix11_* 备份表）
-- 只回填被改动的列；teacher_class_application 被删了 8 行，需先按主键补回再逐列还原
-- （不用 REPLACE：REPLACE = DELETE + INSERT，两张申请表都没被外键引用，但逐列还原更小、
--   且能保留 update_time 之外的一切现场）。
-- ============================================================================
SET SESSION group_concat_max_len = 100000000;
START TRANSACTION;

-- A2/A3 账号姓名与联系方式
UPDATE users u JOIN bak_fix11_users b ON b.id = u.id
   SET u.name = b.name, u.email = b.email, u.phone = b.phone;

-- A7 教师班级申请：补回被删的 8 行 + 还原 6 行的理由/状态/拒绝理由
INSERT INTO teacher_class_application
    (id, teacher_id, class_id, reason, status, create_time, update_time,
     class_name, teacher_name, teacher_username, application_type, reject_reason)
SELECT b.id, b.teacher_id, b.class_id, b.reason, b.status, b.create_time, b.update_time,
       b.class_name, b.teacher_name, b.teacher_username, b.application_type, b.reject_reason
  FROM bak_fix11_teacher_class_application b
 WHERE NOT EXISTS (SELECT 1 FROM teacher_class_application t WHERE t.id = b.id);

UPDATE teacher_class_application t JOIN bak_fix11_teacher_class_application b ON b.id = t.id
   SET t.reason = b.reason, t.status = b.status, t.reject_reason = b.reject_reason;

-- A6 辅导员班级申请
UPDATE counselor_class_application a JOIN bak_fix11_counselor_class_application b ON b.id = a.id
   SET a.reason = b.reason, a.status = b.status, a.counselor_name = b.counselor_name;

-- A8 沟通记录
UPDATE communication_record r JOIN bak_fix11_communication_record b ON b.id = r.id
   SET r.student_id = b.student_id, r.content = b.content;

-- A9 学生反馈
UPDATE feedback f JOIN bak_fix11_feedback b ON b.id = f.id
   SET f.content = b.content;

-- A11 教学资源 / 帮扶资源
--    ⚠️ teaching_resources 没有 category 列（support_resource 才有），别照抄。
UPDATE teaching_resources r JOIN bak_fix11_teaching_resources b ON b.id = r.id
   SET r.title = b.title, r.description = b.description, r.url = b.url, r.created_by = b.created_by;

UPDATE support_resource r JOIN bak_fix11_support_resource b ON b.id = r.id
   SET r.title = b.title, r.description = b.description, r.content = b.content,
       r.category = b.category, r.url = b.url;

-- A16 辅导员约谈记录
UPDATE communication_logs l JOIN bak_fix11_communication_logs b ON b.id = l.id
   SET l.content = b.content;

-- A17 辅导员档案的学院名
UPDATE counselor_profiles cp JOIN bak_fix11_counselor_profiles b ON b.id = cp.id
   SET cp.college_name = b.college_name;

-- A18 辅导员带班表：删掉新增的 7 行，再还原原有 1 行
DELETE m FROM counselor_class_management m
 WHERE NOT EXISTS (SELECT 1 FROM bak_fix11_counselor_class_management b WHERE b.id = m.id);

UPDATE counselor_class_management m JOIN bak_fix11_counselor_class_management b ON b.id = m.id
   SET m.counselor_id = b.counselor_id, m.class_id = b.class_id,
       m.class_name = b.class_name, m.status = b.status;

COMMIT;

-- 回滚自检（时间戳列不在指纹内：update_time/updated_at 是 on update CURRENT_TIMESTAMP）
SELECT 'users' AS t, COUNT(*) AS n, MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) AS fp FROM (
  SELECT CONCAT_WS(',', id, username, name, role, IFNULL(email,'~'), IFNULL(phone,'~'), IFNULL(status,'~')) x FROM users) a
UNION ALL SELECT 'teacher_app', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, teacher_id, class_id, IFNULL(reason,'~'), IFNULL(status,'~'), class_name,
         IFNULL(teacher_name,'~'), IFNULL(teacher_username,'~'), IFNULL(application_type,'~'), IFNULL(reject_reason,'~')) x
    FROM teacher_class_application) a
UNION ALL SELECT 'counselor_app', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, counselor_id, class_id, IFNULL(reason,'~'), IFNULL(status,'~'),
         IFNULL(class_name,'~'), IFNULL(counselor_name,'~'), IFNULL(application_type,'~')) x
    FROM counselor_class_application) a
UNION ALL SELECT 'comm_record', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, teacher_id, student_id, IFNULL(content,'~'), IFNULL(type,'~'), IFNULL(student_name,'~')) x
    FROM communication_record) a
UNION ALL SELECT 'feedback', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, teacher_id, IFNULL(category,'~'), IFNULL(content,'~'), IFNULL(reply,'~'), IFNULL(status,'~')) x
    FROM feedback) a
UNION ALL SELECT 'teaching_res', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, course_id, IFNULL(title,'~'), IFNULL(description,'~'), IFNULL(type,'~'),
         IFNULL(url,'~'), IFNULL(created_by,'~'), IFNULL(status,'~')) x FROM teaching_resources) a
UNION ALL SELECT 'support_res', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, IFNULL(title,'~'), IFNULL(description,'~'), IFNULL(type,'~'), IFNULL(category,'~'),
         IFNULL(content,'~'), IFNULL(url,'~'), IFNULL(author,'~'), IFNULL(views,'~'), IFNULL(downloads,'~'), IFNULL(status,'~')) x
    FROM support_resource) a
UNION ALL SELECT 'comm_logs', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, IFNULL(counselor_id,'~'), IFNULL(teacher_id,'~'), IFNULL(student_id,'~'),
         IFNULL(warning_id,'~'), IFNULL(content,'~'), IFNULL(category,'~'), IFNULL(status,'~'), IFNULL(reply,'~')) x
    FROM communication_logs) a
UNION ALL SELECT 'counselor_prof', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, user_id, college_id, IFNULL(counselor_id,'~'), IFNULL(name,'~'), IFNULL(college_name,'~'),
         IFNULL(staff_id,'~'), IFNULL(class_count,'~'), IFNULL(student_count,'~'), IFNULL(status,'~')) x FROM counselor_profiles) a
UNION ALL SELECT 'counselor_ccm', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, counselor_id, class_id, IFNULL(class_name,'~'), IFNULL(status,'~')) x
    FROM counselor_class_management) a
UNION ALL SELECT 'scores', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, student_id, course_id, semester, IFNULL(year,'~'), regular_score,
         final_score, score_total, IFNULL(grade,'~'), IFNULL(grade_point,'~')) x FROM scores) a
UNION ALL SELECT 'score_record', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',', id, student_id, course_id, semester, IFNULL(year,'~'), regular_score,
         final_score, score_total, IFNULL(grade,'~'), IFNULL(grade_point,'~'), IFNULL(teacher_id,'~')) x FROM score_record) a;
