# -*- coding: utf-8 -*-
"""第十一批校验：文本/身份/冗余引用类不一致（A2/A3/A6/A7/A8/A9/A11/A16/A17/A18）修完后的终态。

除断言 fix11 的结果外，还回归 fix5~fix10 立起来的全部不变量（两表对齐、挂科基线、
口径换算、结构性引用、指纹），并检查两批回滚素材是否可用。

注意：counselor_class_management 只用语义断言（8 行且与 classes 一致），不用 id 指纹 ——
彩排时先回滚再重跑会把自增值顶到 9~15，id 天然不同，不是数据差异。
"""
import subprocess

DB = 'academic_warning_system'

G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")
L = ("CASE WHEN h.score_total>=90 THEN 1 WHEN h.score_total>=80 THEN 2 "
     "WHEN h.score_total>=70 THEN 3 WHEN h.score_total>=60 THEN 4 ELSE 5 END")
LSR = L.replace('h.', 'sr.')

PAIR = ("score_record sr JOIN scores h ON h.student_id=sr.student_id AND h.course_id=sr.course_id "
        "AND h.semester=CAST(sr.semester AS UNSIGNED)")
CAL = ('SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
       'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL')
# 与 fix10 同一套 10 列指纹（不含 grade/reason_for_change 等 fix10 起才加入的列）
FP10 = ("SET SESSION group_concat_max_len=100000000; SELECT COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM ("
        "SELECT CONCAT_WS(',', id, student_id, course_id, semester, IFNULL(year,'~'), regular_score, "
        "final_score, score_total, IFNULL(grade,'~'), IFNULL(grade_point,'~')) x FROM %s) b")

# 本批涉及的文本列：全库扫描乱码/占位/上一代目录
JUNK = ("SELECT COUNT(*) FROM ("
        "SELECT CONCAT(IFNULL(reason,''),' ',IFNULL(reject_reason,'')) x FROM teacher_class_application "
        "UNION ALL SELECT CONCAT(IFNULL(reason,''),' ',IFNULL(counselor_name,'')) FROM counselor_class_application "
        "UNION ALL SELECT IFNULL(content,'') FROM communication_record "
        "UNION ALL SELECT IFNULL(content,'') FROM feedback "
        "UNION ALL SELECT IFNULL(content,'') FROM communication_logs "
        "UNION ALL SELECT CONCAT(IFNULL(title,''),' ',IFNULL(description,'')) FROM support_resource "
        "UNION ALL SELECT CONCAT(IFNULL(title,''),' ',IFNULL(description,'')) FROM teaching_resources "
        "UNION ALL SELECT IFNULL(name,'') FROM users) z "
        "WHERE z.x LIKE '%范德萨%' OR z.x LIKE '%fds a%' OR z.x LIKE '%fdas%' OR z.x LIKE '%法撒旦%' "
        "OR z.x LIKE '%大师傅%' OR z.x LIKE '%飞洒%' OR z.x LIKE '%安抚的%' OR z.x LIKE '%张浩%' "
        "OR z.x LIKE '%张明%' OR z.x LIKE '%线性代数%' OR z.x LIKE '%计组%' OR z.x LIKE '%Java程序设计%' "
        "OR z.x LIKE '%计算机组成原理%' OR z.x LIKE '%发生的%' OR z.x IN ('发送','1','2','辅导员','张老师')")


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace').replace('\r\n', '\n')
    if r.returncode != 0:
        out += '[SQLERR] ' + r.stderr.decode('utf-8', 'replace')
    return out.strip()


def check(label, expect, sql):
    got = q(sql)
    ok = (got == expect)
    return '%s %s\n    期望: %s\n    实际: %s' % ('[PASS]' if ok else '[FAIL]', label, expect or '(空)', got or '(空)')


o = ['========== 第十一批校验（文本/身份/冗余引用类不一致终态）==========', '']

o.append('--- (a) 回归：fix5~fix10 的不变量必须原封不动 ---')
o.append(check('两表行数仍 259 / 246', '259\t246',
               'SELECT (SELECT COUNT(*) FROM scores), (SELECT COUNT(*) FROM score_record)'))
o.append(check('scores 无 NULL 学期/学年', '0\t0',
               'SELECT SUM(semester IS NULL), SUM(year IS NULL) FROM scores'))
o.append(check('score_record 无 NULL 学期/学年', '0\t0',
               'SELECT SUM(semester IS NULL), SUM(year IS NULL) FROM score_record'))
o.append(check('学年公式 (学号前四位+(学期-1)/2) 违例', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN student_profile sp ON sp.id=sr.student_id '
               'WHERE sr.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (CAST(sr.semester AS UNSIGNED)-1) DIV 2'))
o.append(check('246 行可配对、0 行无法配对', '246\t0',
               'SELECT COUNT(*), (SELECT COUNT(*) FROM score_record sr LEFT JOIN scores h '
               'ON h.student_id=sr.student_id AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED) '
               'WHERE h.id IS NULL) FROM ' + PAIR))
o.append(check('配对后 score/总评/平时/期末 不一致', '0',
               'SELECT COUNT(*) FROM ' + PAIR + ' WHERE sr.score <> h.score_total OR sr.score_total <> h.score_total '
               'OR NOT (sr.regular_score <=> h.regular_score) OR NOT (sr.final_score <=> h.final_score)'))
o.append(check('grade/grade_point 与权威表逐字相同（含换算复核）', '0\t0',
               'SELECT SUM(NOT (sr.grade <=> h.grade) OR NOT (sr.grade_point <=> h.grade_point)), '
               'SUM(h.grade <> ' + G + ' OR h.grade_point <> ' + P + ') FROM ' + PAIR))
o.append(check('score_level 与 ScoreSyncSupport 口径一致', '0',
               'SELECT COUNT(*) FROM score_record sr WHERE sr.score_level <> ' + LSR))
o.append(check('挂科基线：17 行 / 4 人 / 司明 9 行', '17\t4\t9',
               'SELECT COUNT(*), COUNT(DISTINCT student_id), SUM(student_id=3) FROM scores WHERE score_total < 60'))
o.append(check('司明/李爽/张健/韩坤 挂科门数（权威口径）仍 3,3,3,2', '3,3,3,2',
               'SELECT GROUP_CONCAT(f.c) FROM (SELECT COUNT(*) c FROM (' + CAL + ') t '
               'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id ORDER BY c DESC, t.student_id) f'))
o.append(check('用户选择保留的非 50/50 残留：scores 10 行 / score_record 7 行（全司明）',
               '10\t1\t7\t1\t278,282,283,284,288,290,291',
               'SELECT (SELECT COUNT(*) FROM scores WHERE final_score IS NOT NULL AND regular_score IS NOT NULL '
               'AND score_total <> ROUND((regular_score+final_score)/2)), '
               '(SELECT COUNT(DISTINCT student_id) FROM scores WHERE final_score IS NOT NULL AND regular_score IS NOT NULL '
               'AND score_total <> ROUND((regular_score+final_score)/2)), '
               '(SELECT COUNT(*) FROM score_record WHERE score_total <> ROUND((regular_score+final_score)/2)), '
               '(SELECT COUNT(DISTINCT student_id) FROM score_record WHERE score_total <> ROUND((regular_score+final_score)/2)), '
               '(SELECT GROUP_CONCAT(id ORDER BY id) FROM score_record WHERE score_total <> ROUND((regular_score+final_score)/2))'))
o.append(check('scores 259 行 10 列指纹（fix10 之后一个字节没动）', '259\t185227906f1a09fcfb029c1458139cfd',
               FP10 % 'scores'))
o.append(check('score_record 246 行 10 列指纹（本批不碰录入表）', '246\t6a8238dff1ddd403c18a2cb593d63f5b',
               FP10 % 'score_record'))
o.append('')

o.append('--- (b) fix10 的结构性修复仍在 ---')
o.append(check('A1 更正记录仍与权威成绩一致（55 -> 56.5，差值 1.5）',
               '55.00\t56.50\t1.50\t试卷复核发现平时分漏计，据实更正',
               'SELECT old_score, new_score, difference, reason FROM score_audit_log WHERE id=1'))
o.append(check('A4 全 10 门课的授课教师', '1-29|2-19|3-22|4-29|5-22|6-18|7-28|8-28|9-28|10-29',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",teacher_id) ORDER BY id SEPARATOR "|") FROM courses'))
o.append(check('A4 三张表教师一致、0 悬空', '246\t0\t10\t0',
               'SELECT (SELECT COUNT(*) FROM score_record sr JOIN courses c ON c.id=sr.course_id), '
               '(SELECT COUNT(*) FROM score_record sr JOIN courses c ON c.id=sr.course_id WHERE NOT (sr.teacher_id <=> c.teacher_id)), '
               '(SELECT COUNT(*) FROM course_plans p JOIN courses c ON c.id=p.course_id), '
               '(SELECT COUNT(*) FROM course_plans p JOIN courses c ON c.id=p.course_id WHERE NOT (p.teacher_id <=> c.teacher_id))'))
o.append(check('A5 班级专业/学院归位', '计科2306班|2|1,软工2306班|1|1,计算机2101班|2|1,计算机2102班|2|1,'
               '软件工程2101班|1|1,软件工程2102班|1|1,人工智能2101班|8|2,人工智能2102班|8|2',
               'SELECT GROUP_CONCAT(CONCAT(name,"|",major_id,"|",college_id) ORDER BY id SEPARATOR ",") FROM classes'))
o.append(check('A12 专业 12 行、重复行 3/5 已删、工商管理已归商学院', '12\t0\t4\t工商管理\t工商\t3',
               'SELECT (SELECT COUNT(*) FROM majors), (SELECT COUNT(*) FROM majors WHERE id IN (3,5)), '
               'id, name, short_name, college_id FROM majors WHERE id=4'))
o.append(check('A10 必修课不挂选修模块；选修课 4 门', '0\t4\t7-3,8-3,9-3,10-4',
               'SELECT (SELECT COUNT(*) FROM courses WHERE type="必修课" AND module_id IS NOT NULL), '
               '(SELECT COUNT(*) FROM courses WHERE type="选修课" AND module_id IS NOT NULL), '
               '(SELECT GROUP_CONCAT(CONCAT(id,"-",module_id) ORDER BY id SEPARATOR ",") FROM courses '
               'WHERE type="选修课" AND module_id IS NOT NULL)'))
o.append(check('A13/A14 冗余计数：学院 24/5、辅导员 8 班 24 人', '1-24|2-0|3-0|4-0\t1-5|2-1|3-0|4-0\t8\t24',
               'SELECT (SELECT GROUP_CONCAT(CONCAT(id,"-",student_count) ORDER BY id SEPARATOR "|") FROM colleges), '
               '(SELECT GROUP_CONCAT(CONCAT(id,"-",teacher_count) ORDER BY id SEPARATOR "|") FROM colleges), '
               '(SELECT class_count FROM counselor_profiles WHERE id=1), '
               '(SELECT student_count FROM counselor_profiles WHERE id=1)'))
o.append('')

o.append('--- (c) A2 账号姓名与档案表对齐 ---')
o.append(check('6 个乱码/占位账号的姓名已回填（16 夏志浩 / 18 刘教授 / 28 赵振江 / 55 陈志强 / 56 李明远 / 57 张建国）',
               '16-夏志浩|18-刘教授|28-赵振江|55-陈志强|56-李明远|57-张建国',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",name) ORDER BY id SEPARATOR "|") FROM users WHERE id IN (16,18,28,55,56,57)'))
o.append(check('全库账号姓名与其档案表姓名完全一致（0 处不符）', '0',
               'SELECT COUNT(*) FROM users u '
               'LEFT JOIN student_profile sp ON sp.user_id=u.id '
               'LEFT JOIN teacher_profile tp ON tp.user_id=u.id '
               'LEFT JOIN admin_profile ap ON ap.user_id=u.id '
               'LEFT JOIN college_admin_profile cap ON cap.user_id=u.id '
               'LEFT JOIN counselor_profiles cp ON cp.user_id=u.id '
               'WHERE BINARY u.name <> COALESCE(BINARY sp.name, BINARY tp.name, BINARY ap.name, BINARY cap.name, BINARY cp.name, BINARY u.name)'))
o.append(check('每个角色都有人有档案（学生24/教师6/管理员2/学院管理员2/辅导员1）', '24\t6\t2\t2\t1',
               'SELECT (SELECT COUNT(*) FROM student_profile), (SELECT COUNT(*) FROM teacher_profile), '
               '(SELECT COUNT(*) FROM admin_profile), (SELECT COUNT(*) FROM college_admin_profile), '
               '(SELECT COUNT(*) FROM counselor_profiles)'))
o.append(check('用户名/姓名不再是"发送""发生的官方""1""2"这类脏值', '0',
               'SELECT COUNT(*) FROM users WHERE name IN ("发送","发生的官方","张老师","1","2")'))
o.append('')

o.append('--- (d) A3 邮箱/手机号唯一且合法 ---')
o.append(check('重复邮箱 0 组、重复手机号 0 组', '0\t0',
               'SELECT (SELECT COUNT(*) FROM (SELECT email FROM users WHERE email IS NOT NULL AND email<>"" '
               'GROUP BY email HAVING COUNT(*)>1) a), '
               '(SELECT COUNT(*) FROM (SELECT phone FROM users WHERE phone IS NOT NULL AND phone<>"" '
               'GROUP BY phone HAVING COUNT(*)>1) b)'))
o.append(check('非法手机号 0 个（1[3-9] 号段）', '0',
               'SELECT COUNT(*) FROM users WHERE phone IS NOT NULL AND phone<>"" AND phone NOT REGEXP "^1[3-9][0-9]{9}$"'))
o.append(check('24 名学生邮箱 = 学号@stu.academic.edu', '24\t0',
               'SELECT COUNT(*), SUM(NOT (u.email <=> CONCAT(sp.student_id,"@stu.academic.edu"))) '
               'FROM users u JOIN student_profile sp ON sp.user_id=u.id WHERE u.role=1'))
o.append(check('学生手机号段 152 + 8 位流水（例：首位/末位）', '15200000001\t15200000024',
               'SELECT (SELECT phone FROM users WHERE id=27), (SELECT phone FROM users WHERE role=1 ORDER BY id DESC LIMIT 1)'))
o.append(check('辅导员/教师 53/55/56/57 联系方式取自档案表', '13800000014\tcounselor001@test.com\t13800000013\tteacher006@test.com\t'
               '13900000002\tlimy@academic.edu\t13900000001\tzhangjg@academic.edu',
               'SELECT (SELECT phone FROM users WHERE id=53), (SELECT email FROM users WHERE id=53), '
               '(SELECT phone FROM users WHERE id=55), (SELECT email FROM users WHERE id=55), '
               '(SELECT phone FROM users WHERE id=56), (SELECT email FROM users WHERE id=56), '
               '(SELECT phone FROM users WHERE id=57), (SELECT email FROM users WHERE id=57)'))
o.append(check('users 55 的邮箱不再是用户名 adm001、16 的邮箱不再是乱码', '0',
               'SELECT COUNT(*) FROM users WHERE email IN ("adm001","admin@test.com的发生")'))
o.append('')

o.append('--- (e) A6 辅导员班级申请与班级事实对齐 ---')
o.append(check('3 行状态（30/31 班由 53 带，故两行改判为已通过）', '1-1|2-1|5-1',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",status) ORDER BY id SEPARATOR "|") FROM counselor_class_application'))
o.append(check('第 5 行姓名列不再是"辅导员"占位，理由为实文', '司明\t申请负责计科2306班的日常管理与学业帮扶工作',
               'SELECT counselor_name, reason FROM counselor_class_application WHERE id=5'))
o.append(check('已通过的行必须与 classes.counselor_id 一致（0 处矛盾）', '0',
               'SELECT COUNT(*) FROM counselor_class_application a JOIN classes c ON c.id=a.class_id '
               'WHERE a.status=1 AND NOT (a.counselor_id <=> c.counselor_id)'))
o.append('')

o.append('--- (f) A7 教师班级申请台账可逐行讲通 ---')
o.append(check('14 行 -> 6 行，留下的正是可讲通的那 6 条', '1,2,9,10,11,13', 'SELECT GROUP_CONCAT(id ORDER BY id) FROM teacher_class_application'))
o.append(check('(id,班,状态,类型)：取消->拒绝->通过管理->通过解除(班5)、通过管理(班30)、拒绝(班32)',
               '1-5-3-0|2-5-2-0|9-5-1-0|10-5-1-1|11-30-1-0|13-32-2-0',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",class_id,"-",status,"-",application_type) ORDER BY id SEPARATOR "|") '
               'FROM teacher_class_application'))
o.append(check('每条理由都换成了实文（含拒绝理由）',
               '1=>申请信息填写有误，撤销后重新提交|-||2=>申请管理软工2306班|该班本学期已安排班主任，暂不受理|'
               '|9=>本学期教学任务调整，再次申请管理软工2306班|-||10=>因教学与科研任务繁重，申请解除软工2306班的管理工作|-|'
               '|11=>申请管理计算机2101班|-||13=>申请管理软件工程2101班|该班已由刘教授担任班主任，暂不调整|',
               'SELECT GROUP_CONCAT(CONCAT(id,"=>",reason,"|",IFNULL(reject_reason,"-"),"|") ORDER BY id SEPARATOR "|") '
               'FROM teacher_class_application'))
o.append(check('核心自洽点：每个班"最后一条已通过申请"推出的教师 == classes.teacher_id（0 处矛盾）', '0',
               'SELECT COUNT(*) FROM (SELECT a.class_id, IF(a.application_type=1, NULL, a.teacher_id) AS eff '
               'FROM teacher_class_application a WHERE a.status=1 '
               'AND NOT EXISTS (SELECT 1 FROM teacher_class_application b WHERE b.status=1 AND b.class_id=a.class_id '
               'AND (b.create_time > a.create_time OR (b.create_time = a.create_time AND b.id > a.id)))) e '
               'JOIN classes c ON c.id=e.class_id WHERE NOT (e.eff <=> c.teacher_id)'))
o.append(check('反证：班 5 无班主任(29 被解除)、班 30 是 29、班 32 是 18 刘教授（与台账一致）',
               'NULL	29	18',
               'SELECT (SELECT teacher_id FROM classes WHERE id=5), (SELECT teacher_id FROM classes WHERE id=30), '
               '(SELECT teacher_id FROM classes WHERE id=32)'))
o.append('')

o.append('--- (g) A8 沟通记录学生 ID 统一到 student_profile.id ---')
o.append(check('5 行 student_id（原 39/46/52 是 users.id、0 是无效值）', '13,20,26,13,4',
               'SELECT GROUP_CONCAT(student_id ORDER BY id) FROM communication_record'))
o.append(check('student_id 全部落在 student_profile 且与姓名一致（0 处不符）', '0',
               'SELECT COUNT(*) FROM communication_record r LEFT JOIN student_profile sp ON sp.id=r.student_id '
               'WHERE sp.id IS NULL OR NOT ((BINARY sp.name) <=> (BINARY r.student_name))'))
o.append(check('第 5 行内容换成与邱琳紫实际学业状态相符的沟通文本',
               '1\t向辅导员同步邱琳紫同学的学习状态：本学期各科成绩均在 80 分以上，建议纳入学业优秀名单，请辅导员协同关注其后续发展',
               'SELECT (CHAR_LENGTH(content)>10), content FROM communication_record WHERE id=5'))
o.append(check('邱琳紫(profile 4) 各科最新成绩均 >= 80，故"成绩稳定"这一说法有据', '88.00\t80',
               'SELECT ROUND(AVG(t.score_total),2), MIN(t.score_total) FROM ('
               'SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.course_id ORDER BY s.semester DESC, s.id DESC) rn '
               'FROM scores s WHERE s.final_score IS NOT NULL AND s.student_id=4) t WHERE t.rn=1'))
o.append('')

o.append('--- (h) A9 学生反馈里的学生必须真实存在 ---')
o.append(check('2 条反馈的开头都是真学生（司明 / 韩坤）',
               '司明：高等数学课程进度有点快，希望老师能放慢一些\n韩坤：数据结构的课后作业太难了，能否提供一些参考示例',
               'SELECT content FROM feedback ORDER BY id'))
o.append(check('全库再无"张浩"', '0',
               'SELECT COUNT(*) FROM feedback WHERE content LIKE "%张浩%"'))
o.append(check('韩坤的数据结构确实挂了（59 分），"作业太难"与事实吻合', '59.00',
               'SELECT score_total FROM scores WHERE student_id=13 AND course_id=4 ORDER BY semester DESC, id DESC LIMIT 1'))
o.append('')

o.append('--- (i) A11 教学资源/帮扶资源回到现课程目录 ---')
o.append(check('10 行教学资源的作者 = 该课教师（1/4/10 于凤海、2 王老师、3 李老师、5 李老师、6 刘教授）',
               '1-于凤海|2-王老师|3-李老师|4-于凤海|5-李老师|6-刘教授',
               'SELECT GROUP_CONCAT(DISTINCT CONCAT(course_id,"-",created_by) ORDER BY course_id SEPARATOR "|") FROM teaching_resources'))
o.append(check('5 行旧目录标题已按课程改写（course 3/5/6）',
               '4-计算机基础-核心知识点梳理|6-计算机网络-实验指导书|7-网络编程实战项目源码|8-操作系统-实验教程|10-操作系统期末复习大纲',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",title) ORDER BY id SEPARATOR "|") FROM teaching_resources WHERE id IN (4,6,7,8,10)'))
o.append(check('帮扶资源两行改成现课程（计算机网络 / 计算机基础）',
               '2-计算机网络学习指南-计算机|4-计算机基础精讲-计算机',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",title,"-",category) ORDER BY id SEPARATOR "|") FROM support_resource WHERE id IN (2,4)'))
o.append(check('资源类文本里再无上一代目录（线性代数/Java/计组）', '0',
               'SELECT COUNT(*) FROM (SELECT CONCAT(IFNULL(title,""),IFNULL(description,"")) x FROM teaching_resources '
               'UNION ALL SELECT CONCAT(IFNULL(title,""),IFNULL(description,""),IFNULL(content,"")) FROM support_resource) z '
               'WHERE z.x LIKE "%线性代数%" OR z.x LIKE "%计组%" OR z.x LIKE "%Java程序设计%" OR z.x LIKE "%计算机组成原理%" OR z.x LIKE "%Java编程%"'))
o.append('')

o.append('--- (j) A16/A17/A18 约谈记录、辅导员档案与带班表 ---')
o.append(check('约谈记录补全到三门，且分数是权威值、不再写"两门"',
               '1\t0', 'SELECT (CHAR_LENGTH(content)>60), (content LIKE "%两门%") FROM communication_logs WHERE id=2001'))
o.append(check('约谈记录逐字含三门课程与分数', '1',
               "SELECT (content LIKE '%操作系统(40分)%' AND content LIKE '%计算机基础(47分)%' "
               "AND content LIKE '%高等数学(56.5分)%') FROM communication_logs WHERE id=2001"))
o.append(check('这三门分数与权威口径（最新一次成绩）逐字相同', '40.00\t47.00\t56.50',
               'SELECT (SELECT s.score_total FROM scores s WHERE s.student_id=3 AND s.final_score IS NOT NULL AND s.course_id=6 '
               'ORDER BY s.semester DESC, s.id DESC LIMIT 1), '
               '(SELECT s.score_total FROM scores s WHERE s.student_id=3 AND s.final_score IS NOT NULL AND s.course_id=3 '
               'ORDER BY s.semester DESC, s.id DESC LIMIT 1), '
               '(SELECT s.score_total FROM scores s WHERE s.student_id=3 AND s.final_score IS NOT NULL AND s.course_id=1 '
               'ORDER BY s.semester DESC, s.id DESC LIMIT 1)'))
o.append(check('辅导员学院名按 college_id 归位（计算机学院 -> 软件学院）', '软件学院',
               'SELECT college_name FROM counselor_profiles WHERE id=1'))
o.append(check('D 第 2002 行提到的"计划1001"确实存在（司明，3 门课程）', '1001\t3\t1\t50',
               'SELECT id, student_id, status, progress FROM assistance_plans WHERE id=1001'))
o.append(check('A18 带班表补齐到 8 行，id 连续 1~8', '8\t1,2,3,4,5,6,7,8',
               'SELECT COUNT(*), GROUP_CONCAT(id ORDER BY id) FROM counselor_class_management'))
o.append(check('带班表每行都与 classes 的辅导员归属、班名一致（0 处不符）', '0',
               'SELECT COUNT(*) FROM classes c WHERE c.counselor_id IS NOT NULL '
               'AND NOT EXISTS (SELECT 1 FROM counselor_class_management m WHERE m.class_id=c.id '
               'AND m.counselor_id=c.counselor_id AND (BINARY m.class_name)=(BINARY c.name) AND m.status=1)'))
o.append(check('全库文本列无乱码/占位/上一代目录残留', '0', JUNK))
o.append('')

o.append('--- (k) 两批回滚素材可用 ---')
o.append(check('10 张 bak_fix11_* 备份表都在，行数对得上',
               '35\t14\t3\t5\t2\t10\t4\t3\t1\t1',
               'SELECT (SELECT COUNT(*) FROM bak_fix11_users), (SELECT COUNT(*) FROM bak_fix11_teacher_class_application), '
               '(SELECT COUNT(*) FROM bak_fix11_counselor_class_application), (SELECT COUNT(*) FROM bak_fix11_communication_record), '
               '(SELECT COUNT(*) FROM bak_fix11_feedback), (SELECT COUNT(*) FROM bak_fix11_teaching_resources), '
               '(SELECT COUNT(*) FROM bak_fix11_support_resource), (SELECT COUNT(*) FROM bak_fix11_communication_logs), '
               '(SELECT COUNT(*) FROM bak_fix11_counselor_profiles), (SELECT COUNT(*) FROM bak_fix11_counselor_class_management)'))
o.append(check('备份里保存的正是改动前的脏值（供回滚）',
               '57 2 ~ ~ | 56 1 ~ ~ | 55 发送 adm001 24524224545 | 28 发生的官方 23467675@qq.com 16744343245 | '
               '18 张老师 teacher001@test.com 13800000010',
               'SELECT GROUP_CONCAT(CONCAT(u.id," ",u.name," ",IFNULL(u.email,"~")," ",IFNULL(u.phone,"~")) '
               'ORDER BY u.id DESC SEPARATOR " | ") FROM bak_fix11_users u WHERE u.id IN (18,28,55,56,57)'))
o.append(check('备份里 teacher_class_application 14 行、含被删的 8 行乱码理由',
               '14	2	3,4,5,6,7,8,12,14',
               'SELECT (SELECT COUNT(*) FROM bak_fix11_teacher_class_application), '
               '(SELECT COUNT(*) FROM bak_fix11_teacher_class_application WHERE reason="法撒旦"), '
               '(SELECT GROUP_CONCAT(b.id ORDER BY b.id) FROM bak_fix11_teacher_class_application b '
               'WHERE NOT EXISTS (SELECT 1 FROM teacher_class_application t WHERE t.id=b.id))'))
o.append(check('8 张 bak_fix10_* 备份表仍在（fix10 的回滚素材未被本批覆盖）', '1\t8\t10\t4\t1\t10\t14\t246',
               'SELECT (SELECT COUNT(*) FROM bak_fix10_score_audit_log), (SELECT COUNT(*) FROM bak_fix10_classes), '
               '(SELECT COUNT(*) FROM bak_fix10_courses), (SELECT COUNT(*) FROM bak_fix10_colleges), '
               '(SELECT COUNT(*) FROM bak_fix10_counselor_profiles), (SELECT COUNT(*) FROM bak_fix10_course_plans), '
               '(SELECT COUNT(*) FROM bak_fix10_majors), (SELECT COUNT(*) FROM bak_fix10_score_record)'))
o.append('')

o.append('--- (l) 指纹：与独立彩排的产物交叉复现 ---')
POST = [('users', '35', '38f398a6af472f1e74190ce1cd6b1bbc'),
        ('teacher_app', '6', '3144ff6fe89701515d4edaa68af6b10f'),
        ('counselor_app', '3', 'c666bbb03a368fb8312bceb9f6e35961'),
        ('comm_record', '5', '3cb4d9b9e00181ab20ab83b8e0814444'),
        ('feedback', '2', '7507eccfbc12811aac90a2cd47768bf4'),
        ('teaching_res', '10', '3a3011f92c0d76dc2e9f522ba99ecd13'),
        ('support_res', '4', '8d44f52a8878197df8b9fa7e472330e1'),
        ('comm_logs', '3', 'f97689dccebf0a1d690d98f51b8f4c2b'),
        ('counselor_prof', '1', 'f27dda6dcef8f4a9cb89411de4dfb699'),
        ('scores', '259', '185227906f1a09fcfb029c1458139cfd'),
        ('score_record', '246', '7adb7cc92d9e1a70791895bc8a8bff61')]
got_fp = {}
for line in q('SET SESSION group_concat_max_len=100000000; '
              'SELECT "users" t, COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, username, name, role, IFNULL(email,"~"), IFNULL(phone,"~"), IFNULL(status,"~")) x FROM users) a '
              'UNION ALL SELECT "teacher_app", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, teacher_id, class_id, IFNULL(reason,"~"), IFNULL(status,"~"), class_name, '
              'IFNULL(teacher_name,"~"), IFNULL(teacher_username,"~"), IFNULL(application_type,"~"), IFNULL(reject_reason,"~")) x FROM teacher_class_application) a '
              'UNION ALL SELECT "counselor_app", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, counselor_id, class_id, IFNULL(reason,"~"), IFNULL(status,"~"), '
              'IFNULL(class_name,"~"), IFNULL(counselor_name,"~"), IFNULL(application_type,"~")) x FROM counselor_class_application) a '
              'UNION ALL SELECT "comm_record", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, teacher_id, student_id, IFNULL(content,"~"), IFNULL(type,"~"), IFNULL(student_name,"~")) x FROM communication_record) a '
              'UNION ALL SELECT "feedback", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, teacher_id, IFNULL(category,"~"), IFNULL(content,"~"), IFNULL(reply,"~"), IFNULL(status,"~")) x FROM feedback) a '
              'UNION ALL SELECT "teaching_res", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, course_id, IFNULL(title,"~"), IFNULL(description,"~"), IFNULL(type,"~"), '
              'IFNULL(url,"~"), IFNULL(created_by,"~"), IFNULL(status,"~")) x FROM teaching_resources) a '
              'UNION ALL SELECT "support_res", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, IFNULL(title,"~"), IFNULL(description,"~"), IFNULL(type,"~"), IFNULL(category,"~"), '
              'IFNULL(content,"~"), IFNULL(url,"~"), IFNULL(author,"~"), IFNULL(views,"~"), IFNULL(downloads,"~"), IFNULL(status,"~")) x FROM support_resource) a '
              'UNION ALL SELECT "comm_logs", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, IFNULL(counselor_id,"~"), IFNULL(teacher_id,"~"), IFNULL(student_id,"~"), '
              'IFNULL(warning_id,"~"), IFNULL(content,"~"), IFNULL(category,"~"), IFNULL(status,"~"), IFNULL(reply,"~")) x FROM communication_logs) a '
              'UNION ALL SELECT "counselor_prof", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, user_id, college_id, IFNULL(counselor_id,"~"), IFNULL(name,"~"), IFNULL(college_name,"~"), '
              'IFNULL(staff_id,"~"), IFNULL(class_count,"~"), IFNULL(student_count,"~"), IFNULL(status,"~")) x FROM counselor_profiles) a '
              'UNION ALL SELECT "scores", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
              'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM scores) a '
              'UNION ALL SELECT "score_record", COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
              'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
              'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~"), IFNULL(teacher_id,"~")) x FROM score_record) a').split('\n'):
    parts = line.split('\t')
    if len(parts) == 3:
        got_fp[parts[0]] = (parts[1], parts[2])
for t, n, fp in POST:
    g = got_fp.get(t, ('?', '?'))
    ok = (g == (n, fp))
    o.append('%s 终态指纹 %s: 期望 %s %s / 实际 %s %s' % ('[PASS]' if ok else '[FAIL]', t, n, fp, g[0], g[1]))
o.append('')
o.append('[信息] counselor_class_management 不做 id 指纹：彩排库回滚重跑会把自增值顶到 9~15，')
o.append('       id 天然不同（数据内容一致，见 (j) 的两条语义断言）。')

o.append('')
o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))
o.append('')
o.append('注：终态指纹须与彩排库 aws_fix11_test 执行后的产物一致（已排除 update_time/updated_at 等时间戳列）。')

with open('_verify11.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify11.txt; fails =', len(fails))
