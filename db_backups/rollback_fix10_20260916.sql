-- ============================================================================
-- 批次 10 的回滚脚本（依赖 fix10 在建事务之前留下的 bak_fix10_* 备份表）
-- 只回填被改动的列，不做整表 REPLACE —— majors 被 student_profile.major_id 外键引用，
-- 整表删再插会撞外键；而 fix10 只删了 3/5、改了 4，REPLACE 回填即可。
-- ============================================================================
SET SESSION group_concat_max_len = 100000000;
START TRANSACTION;

-- A12 专业表：补回被删的 3/5，再把 4 还原。
-- ⚠️ 不能用 REPLACE：REPLACE = DELETE + INSERT，而 student_profile.major_id 有外键，
--    删 majors 里被引用的行会撞 ERROR 1451（彩排时真踩到）。
INSERT INTO majors (id, college_id, name, code, short_name, description, status, created_at, updated_at)
SELECT b.id, b.college_id, b.name, b.code, b.short_name, b.description, b.status, b.created_at, b.updated_at
  FROM bak_fix10_majors b
 WHERE NOT EXISTS (SELECT 1 FROM majors m WHERE m.id = b.id);

UPDATE majors m JOIN bak_fix10_majors b ON b.id = m.id
   SET m.college_id = b.college_id, m.name = b.name, m.code = b.code,
       m.short_name = b.short_name, m.description = b.description, m.status = b.status;

UPDATE classes c JOIN bak_fix10_classes b ON b.id = c.id
   SET c.college_id = b.college_id, c.major_id = b.major_id,
       c.teacher_id = b.teacher_id, c.counselor_id = b.counselor_id;

-- A4 教师
UPDATE courses c JOIN bak_fix10_courses b ON b.id = c.id
   SET c.teacher_id = b.teacher_id, c.module_id = b.module_id;

UPDATE score_record sr JOIN bak_fix10_score_record b ON b.id = sr.id
   SET sr.teacher_id = b.teacher_id;

UPDATE course_plans p JOIN bak_fix10_course_plans b ON b.id = p.id
   SET p.teacher_id = b.teacher_id;

-- A13 / A14 冗余计数
UPDATE colleges c JOIN bak_fix10_colleges b ON b.id = c.id
   SET c.student_count = b.student_count, c.teacher_count = b.teacher_count;

UPDATE counselor_profiles cp JOIN bak_fix10_counselor_profiles b ON b.id = cp.id
   SET cp.class_count = b.class_count, cp.student_count = b.student_count;

-- A1 更正记录（difference 是生成列，不能赋值）
UPDATE score_audit_log l JOIN bak_fix10_score_audit_log b ON b.id = l.id
   SET l.new_score = b.new_score, l.reason = b.reason;

COMMIT;

-- 回滚自检
SELECT 'scores' AS t, COUNT(*) AS n, MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) AS fp FROM (
  SELECT CONCAT_WS(',',id,student_id,course_id,semester,IFNULL(year,'~'),regular_score,final_score,score_total,IFNULL(grade,'~'),IFNULL(grade_point,'~'),IFNULL(reason_for_change,'~'),IFNULL(modified_by,'~')) x FROM scores) a
UNION ALL SELECT 'majors', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',',id,college_id,name,code,IFNULL(short_name,'~'),IFNULL(description,'~'),IFNULL(status,'~')) x FROM majors) a
UNION ALL SELECT 'audit', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',',id,student_id,student_name,course_id,course_name,teacher_id,old_score,new_score,difference,reason,modified_by) x FROM score_audit_log) a
UNION ALL SELECT 'courses_t', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',',id,IFNULL(teacher_id,'~'),IFNULL(module_id,'~')) x FROM courses) a
UNION ALL SELECT 'classes_m', COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
  SELECT CONCAT_WS(',',id,IFNULL(major_id,'~'),college_id) x FROM classes) a;
