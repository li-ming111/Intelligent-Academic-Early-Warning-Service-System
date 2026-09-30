# -*- coding: utf-8 -*-
"""第十批校验：结构性/引用类不一致（A1/A4/A5/A12/A13/A14/A10）修完后的终态。

除断言 fix10 的结果外，还回归 fix5~fix9 立起来的全部不变量（两表对齐、挂科基线、
指纹），并检查回滚素材是否可用。最后用"彩排库执行后的 11 张表指纹"做交叉复现：
线上终态应当与独立彩排的产物逐字节相同（时间戳列已排除在指纹之外）。"""
import subprocess

DB = 'academic_warning_system'
SC_BAK9 = 'scores_bak_sem9_20260916'
SR_BAK9 = 'score_record_bak_sem9_20260916'

G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")
L = ("CASE WHEN h.score_total>=90 THEN 1 WHEN h.score_total>=80 THEN 2 "
     "WHEN h.score_total>=70 THEN 3 WHEN h.score_total>=60 THEN 4 ELSE 5 END")

PAIR = ("score_record sr JOIN scores h ON h.student_id=sr.student_id AND h.course_id=sr.course_id "
        "AND h.semester=CAST(sr.semester AS UNSIGNED)")


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


o = ['========== 第十批校验（结构性/引用类不一致终态）==========', '']

o.append('--- (a) 回归：fix5~fix9 的不变量必须原封不动 ---')
o.append(check('两表行数仍 259 / 246',
               '259\t246',
               'SELECT (SELECT COUNT(*) FROM scores), (SELECT COUNT(*) FROM score_record)'))
o.append(check('scores 无 NULL 学期/学年',
               '0\t0',
               'SELECT SUM(semester IS NULL), SUM(year IS NULL) FROM scores'))
o.append(check('score_record 无 NULL 学期/学年',
               '0\t0',
               'SELECT SUM(semester IS NULL), SUM(year IS NULL) FROM score_record'))
o.append(check('学年公式 (学号前四位+(学期-1)/2) 违例',
               '0',
               'SELECT COUNT(*) FROM score_record sr JOIN student_profile sp ON sp.id=sr.student_id '
               'WHERE sr.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (CAST(sr.semester AS UNSIGNED)-1) DIV 2'))
o.append(check('246 行可配对、0 行无法配对',
               '246\t0',
               'SELECT COUNT(*), (SELECT COUNT(*) FROM score_record sr LEFT JOIN scores h '
               'ON h.student_id=sr.student_id AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED) '
               'WHERE h.id IS NULL) FROM ' + PAIR))
o.append(check('配对后 score/总评/平时/期末 不一致',
               '0',
               'SELECT COUNT(*) FROM ' + PAIR + ' WHERE sr.score <> h.score_total OR sr.score_total <> h.score_total '
               'OR NOT (sr.regular_score <=> h.regular_score) OR NOT (sr.final_score <=> h.final_score)'))
o.append(check('grade/grade_point 与权威表逐字相同（含换算复核）',
               '0\t0',
               'SELECT SUM(NOT (sr.grade <=> h.grade) OR NOT (sr.grade_point <=> h.grade_point)), '
               'SUM(h.grade <> ' + G + ' OR h.grade_point <> ' + P + ') FROM ' + PAIR))
LSR = L.replace('h.', 'sr.')
o.append(check('score_level 与 ScoreSyncSupport 口径一致',
               '0',
               'SELECT COUNT(*) FROM score_record sr WHERE sr.score_level <> ' + LSR))
o.append(check('挂科基线：17 行 / 4 人 / 司明 9 行',
               '17\t4\t9',
               'SELECT COUNT(*), COUNT(DISTINCT student_id), '
               'SUM(student_id=3) FROM scores WHERE score_total < 60'))
CAL = ('SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
       'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL')
o.append(check('司明/李爽/张健/韩坤 挂科门数（权威口径）仍 3,3,3,2',
               '3,3,3,2',
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
o.append('')

o.append('--- (b) A1 更正记录与权威成绩对齐 ---')
o.append(check('55 -> 56.5，差值 1.5，理由改成"平时分漏计"',
               '55.00\t56.50\t1.50\t试卷复核发现平时分漏计，据实更正',
               'SELECT old_score, new_score, difference, reason FROM score_audit_log WHERE id=1'))
o.append(check('改后 new_score == 该生该课最新一次权威成绩',
               '56.50',
               'SELECT s.score_total FROM scores s WHERE s.student_id=3 AND s.course_id=1 '
               'ORDER BY s.semester DESC, s.id DESC LIMIT 1'))
o.append(check('difference 是生成列 (new_score-old_score)，自动跟着变',
               '1.50',
               'SELECT new_score - old_score FROM score_audit_log WHERE id=1'))
o.append(check('其余字段未动（司明/高等数学/于凤海/原时间）',
               '司明\t高等数学\t29\t于凤海\t2026-06-24 22:38:17',
               'SELECT student_name, course_name, teacher_id, modified_by, modified_at FROM score_audit_log WHERE id=1'))
o.append(check('这条更正不再否掉预警：司明仍是 3 门挂科（权威口径）',
               '3',
               'SELECT COUNT(*) FROM (' + CAL + ') t WHERE t.rn=1 AND t.student_id=3 AND t.score_total<60'))
o.append(check('反证：若按原来的 65 分算，司明只挂 2 门、预警会从"中级"掉到"低级"',
               '2',
               'SELECT COUNT(*) FROM (' + CAL + ') t WHERE t.rn=1 AND t.student_id=3 '
               'AND IF(t.course_id=1, 65, t.score_total) < 60'))
o.append('')

o.append('--- (c) A4 三张表统一到同一个授课教师 ---')
o.append(check('courses 6/7/8 由不存在的 23 改成 刘教授18/赵振江28/赵振江28',
               '6-18|7-28|8-28',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",teacher_id) ORDER BY id SEPARATOR "|") FROM courses WHERE id IN (6,7,8)'))
o.append(check('全 10 门课的授课教师（1,4,10 于凤海 / 2 王老师 / 3,5 李老师 / 6 刘教授 / 7,8,9 赵振江）',
               '1-29|2-19|3-22|4-29|5-22|6-18|7-28|8-28|9-28|10-29',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",teacher_id) ORDER BY id SEPARATOR "|") FROM courses'))
o.append(check('三张表 0 个悬空教师引用',
               '0\t0\t0',
               'SELECT (SELECT COUNT(*) FROM courses c LEFT JOIN users u ON u.id=c.teacher_id WHERE c.teacher_id IS NOT NULL AND u.id IS NULL), '
               '(SELECT COUNT(*) FROM score_record sr LEFT JOIN users u ON u.id=sr.teacher_id WHERE sr.teacher_id IS NOT NULL AND u.id IS NULL), '
               '(SELECT COUNT(*) FROM course_plans p LEFT JOIN users u ON u.id=p.teacher_id WHERE p.teacher_id IS NOT NULL AND u.id IS NULL)'))
o.append(check('score_record 246 行教师 == 该课教师（原 1~6 课是 22/23/28 混填）',
               '246\t0',
               'SELECT COUNT(*), SUM(NOT (sr.teacher_id <=> c.teacher_id)) FROM score_record sr JOIN courses c ON c.id=sr.course_id'))
o.append(check('course_plans 10 行教师 == 该课教师（原来 10 行全是 29）',
               '10\t0',
               'SELECT COUNT(*), SUM(NOT (p.teacher_id <=> c.teacher_id)) FROM course_plans p JOIN courses c ON c.id=p.course_id'))
o.append(check('授课教师与死表 teacher_course 的独立证据一致（于凤海1,4,10 / 王老师2 / 李老师3,5 / 赵振江9）',
               '0',
               'SELECT COUNT(*) FROM teacher_course tc JOIN teacher_profile tp ON tp.id=tc.teacher_id '
               'JOIN courses c ON c.id=tc.course_id WHERE NOT (c.teacher_id <=> tp.user_id)'))
o.append('')

o.append('--- (d) A5 班级的专业与学院按班名归位 ---')
o.append(check('8 个班的 专业/学院（计科→2、软工→1、人工智能→8 且归电子工程学院）',
               '计科2306班|2|1,软工2306班|1|1,计算机2101班|2|1,计算机2102班|2|1,'
               '软件工程2101班|1|1,软件工程2102班|1|1,人工智能2101班|8|2,人工智能2102班|8|2',
               'SELECT GROUP_CONCAT(CONCAT(name,"|",major_id,"|",college_id) ORDER BY id SEPARATOR ",") FROM classes'))
o.append(check('班名里的专业 == 专业字段（0 个错位，原来 8 个全错）',
               '0',
               'SELECT COUNT(*) FROM classes c JOIN majors m ON m.id=c.major_id '
               'WHERE (c.name LIKE "计科%" AND m.name <> "计算机科学与技术") '
               'OR (c.name LIKE "计算机%" AND m.name <> "计算机科学与技术") '
               'OR (c.name LIKE "软工%" AND m.name <> "软件工程") '
               'OR (c.name LIKE "软件工程%" AND m.name <> "软件工程") '
               'OR (c.name LIKE "人工智能%" AND m.name <> "人工智能")'))
o.append(check('班级所在学院 == 该专业所属学院',
               '0',
               'SELECT COUNT(*) FROM classes c JOIN majors m ON m.id=c.major_id WHERE m.college_id <> c.college_id'))
o.append(check('24 名学生仍全在计科2306班、专业 2、学院 1（学生侧未被本批改动）',
               '24\t24\t24',
               'SELECT COUNT(*), SUM(class_id=1), SUM(major_id=2 AND college_id=1) FROM student_profile'))
o.append('')

o.append('--- (e) A12/A10 专业目录与选修模块 ---')
o.append(check('专业表 14 -> 12 行，重复行 3/5 已删',
               '12\t0',
               'SELECT (SELECT COUNT(*) FROM majors), (SELECT COUNT(*) FROM majors WHERE id IN (3,5))'))
o.append(check('软件学院只剩软件类专业',
               '0',
               'SELECT COUNT(*) FROM majors WHERE college_id=1 AND name NOT IN ("软件工程","计算机科学与技术")'))
o.append(check('同一学院内无同名专业（原来 电子信息工程/视觉传达设计 各重一次）',
               '0',
               'SELECT COUNT(*) FROM majors m WHERE EXISTS (SELECT 1 FROM majors o '
               'WHERE o.name=m.name AND o.college_id=m.college_id AND o.id<>m.id)'))
o.append(check('工商管理归位到商学院(3)，简称与专业名一致',
               '4\t工商管理\t工商\t3',
               'SELECT id, name, short_name, college_id FROM majors WHERE id=4'))
o.append(check('简称不再串门（大数据/智科/VR 已消失）',
               '0',
               'SELECT COUNT(*) FROM majors WHERE short_name IN ("大数据","智科","VR")'))
o.append(check('无任何表挂在已删专业上',
               '0\t0\t0',
               'SELECT (SELECT COUNT(*) FROM classes WHERE major_id NOT IN (SELECT id FROM majors)), '
               '(SELECT COUNT(*) FROM student_profile WHERE major_id NOT IN (SELECT id FROM majors)), '
               '(SELECT COUNT(*) FROM benchmark_analysis WHERE major_id IN (3,5))'))
o.append(check('必修课不再挂选修模块；选修课 4 门保留（7,8,9 理工 SCI，10 社科 SOC）',
               '0\t4\t7-3,8-3,9-3,10-4',
               'SELECT (SELECT COUNT(*) FROM courses WHERE type="必修课" AND module_id IS NOT NULL), '
               '(SELECT COUNT(*) FROM courses WHERE type="选修课" AND module_id IS NOT NULL), '
               '(SELECT GROUP_CONCAT(CONCAT(id,"-",module_id) ORDER BY id SEPARATOR ",") FROM courses '
               'WHERE type="选修课" AND module_id IS NOT NULL)'))
o.append('')

o.append('--- (f) A13/A14 冗余计数列 ---')
o.append(check('学院学生数=实际（软件学院24/电子工程学院0/商学院0/艺术设计0）',
               '1-24|2-0|3-0|4-0',
               'SELECT GROUP_CONCAT(CONCAT(c.id,"-",c.student_count) ORDER BY c.id SEPARATOR "|") FROM colleges c'))
o.append(check('学院教师数=实际（软件学院5/电子工程学院1）',
               '1-5|2-1|3-0|4-0',
               'SELECT GROUP_CONCAT(CONCAT(c.id,"-",c.teacher_count) ORDER BY c.id SEPARATOR "|") FROM colleges c'))
o.append(check('学院计数列与实际档案数完全吻合（0 处不符）',
               '0',
               'SELECT COUNT(*) FROM colleges c WHERE c.student_count <> (SELECT COUNT(*) FROM student_profile sp WHERE sp.college_id=c.id) '
               'OR c.teacher_count <> (SELECT COUNT(*) FROM teacher_profile t WHERE t.college_id=c.id)'))
o.append(check('辅导员带 8 个班 / 24 名学生（原来都是 0）',
               '8\t24',
               'SELECT class_count, student_count FROM counselor_profiles WHERE id=1'))
o.append(check('辅导员计数与 classes/student_profile 实际吻合（0 处不符）',
               '0',
               'SELECT COUNT(*) FROM counselor_profiles cp '
               'WHERE cp.class_count <> (SELECT COUNT(*) FROM classes c WHERE c.counselor_id=cp.user_id) '
               'OR cp.student_count <> (SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id=sp.class_id '
               'WHERE c.counselor_id=cp.user_id)'))
o.append('')

o.append('--- (g) 回滚素材可用 ---')
o.append(check('8 张 bak_fix10_* 备份表都在，行数对得上',
               '1\t8\t10\t4\t1\t10\t14\t246',
               'SELECT (SELECT COUNT(*) FROM bak_fix10_score_audit_log), (SELECT COUNT(*) FROM bak_fix10_classes), '
               '(SELECT COUNT(*) FROM bak_fix10_courses), (SELECT COUNT(*) FROM bak_fix10_colleges), '
               '(SELECT COUNT(*) FROM bak_fix10_counselor_profiles), (SELECT COUNT(*) FROM bak_fix10_course_plans), '
               '(SELECT COUNT(*) FROM bak_fix10_majors), (SELECT COUNT(*) FROM bak_fix10_score_record)'))
o.append(check('备份里 3/5 两行还在、4 还是旧学院、audit 还是 65 分（回滚目标态）',
               '3-电子信息工程-1-大数据|5-视觉传达设计-1-VR\t4-工商管理-1-智科\t65.00',
               'SELECT (SELECT GROUP_CONCAT(CONCAT(id,"-",name,"-",college_id,"-",short_name) ORDER BY id SEPARATOR "|") '
               'FROM bak_fix10_majors WHERE id IN (3,5)), '
               '(SELECT CONCAT(id,"-",name,"-",college_id,"-",short_name) FROM bak_fix10_majors WHERE id=4), '
               '(SELECT new_score FROM bak_fix10_score_audit_log WHERE id=1)'))
o.append(check('备份里 score_record 的教师就是混乱的旧值（含悬空的 23）',
               '1\t64',
               'SELECT (SELECT COUNT(DISTINCT teacher_id) FROM bak_fix10_score_record WHERE teacher_id=23), '
               '(SELECT COUNT(*) FROM bak_fix10_score_record WHERE teacher_id=23)'))
o.append('')

o.append('--- (h) 指纹：与独立彩排的产物交叉复现 ---')
o.append(check('scores 259 行 10 列指纹（本批一个字节没动）',
               '259\t185227906f1a09fcfb029c1458139cfd',
               'SET SESSION group_concat_max_len=100000000; SELECT COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
               'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
               'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM scores) b'))
o.append(check('score_record 246 行 10 列指纹（教师列不在指纹内，故应不变）',
               '246\t6a8238dff1ddd403c18a2cb593d63f5b',
               'SET SESSION group_concat_max_len=100000000; SELECT COUNT(*), MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
               'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
               'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM score_record) b'))

with open('_fp10.sql', encoding='utf-8') as f:
    FP_SQL = f.read()
got_fp = {}
for line in q(FP_SQL).split('\n'):
    parts = line.split('\t')
    if len(parts) == 3:
        got_fp[parts[0]] = (parts[1], parts[2])

POST_FP = [('scores', '259', '86794a160fd0c3081daa2c3953eb55eb'),
           ('score_record', '246', 'f28beec343bded2e617eba03fea68089'),
           ('courses', '10', 'ca38db543af8c47b3200568c0638ba02'),
           ('classes', '8', 'bede1e5c4b2c3881a8a7163913c29bb7'),
           ('majors', '12', 'ee0c2f5b58ccd6107858f939ea1926b0'),
           ('colleges', '4', 'b27a1f437581539511e9800aeb3b69b1'),
           ('counselor_profiles', '1', '589638ec55ed819541157d66d851d3d8'),
           ('course_plans', '10', '54db17b0a75b048cfa3e3200e8c94fc8'),
           ('score_audit_log', '1', 'fd01e79d667c0ccaed92e65fbb310306'),
           ('student_profile', '24', 'a477f9390588223f88d9ce948ef0e267'),
           ('teacher_profile', '6', '98e0029107a6b554e6aa91add49e368c')]
for t, n, fp in POST_FP:
    g = got_fp.get(t, ('?', '?'))
    ok = (g == (n, fp))
    o.append('%s 终态指纹 %s: 期望 %s %s / 实际 %s %s' % ('[PASS]' if ok else '[FAIL]', t, n, fp, g[0], g[1]))
o.append('')

o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))
o.append('')
o.append('注：终态指纹须与彩排库 aws_fix10_test 执行后的产物一致（已排除 update_time/updated_at 等时间戳列）。')

with open('_verify10.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify10.txt; fails =', len(fails))
