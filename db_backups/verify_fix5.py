# -*- coding: utf-8 -*-
"""第五批（步骤 A）校验：score_record 已对齐到权威表 scores，
且 scores 一行未动、权威挂科基线未变、选修课那 95 行原封不动。"""
import subprocess

DB = 'academic_warning_system'
BAK = 'score_record_bak_align_20260916'

# 与 ScoreSyncSupport 完全一致的口径（Java 阶跃函数，已核验 scores 163/163 吻合）
G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")
L = ("CASE WHEN h.score_total>=90 THEN 1 WHEN h.score_total>=80 THEN 2 "
     "WHEN h.score_total>=70 THEN 3 WHEN h.score_total>=60 THEN 4 ELSE 5 END")

# 可配对的三元组
PAIR = ("score_record sr JOIN scores h ON h.student_id=sr.student_id AND h.course_id=sr.course_id "
        "AND h.semester=CAST(sr.semester AS UNSIGNED) WHERE h.score_total IS NOT NULL")


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


o = []
o.append('========== 第五批校验（score_record 对齐 scores）==========')
o.append('')

o.append('--- (a) 行数不变，备份完整 ---')
o.append(check('live=247 / bak=247', '247\t247',
               'SELECT (SELECT COUNT(*) FROM score_record), (SELECT COUNT(*) FROM %s)' % BAK))
o.append(check('本次一条行都没增删（id 集合与备份完全一致）', '0',
               'SELECT COUNT(*) FROM %s b WHERE NOT EXISTS '
               '(SELECT 1 FROM score_record sr WHERE sr.id=b.id)' % BAK))
o.append(check('反向也没有多出来的行', '0',
               'SELECT COUNT(*) FROM score_record sr WHERE NOT EXISTS '
               '(SELECT 1 FROM %s b WHERE b.id=sr.id)' % BAK))
o.append('')

o.append('--- (b) 152 个可配对三元组：七列全部一致 ---')
o.append(check('可配对行数', '152', 'SELECT COUNT(*) FROM %s' % PAIR))
o.append(check('score 与权威总分不一致的行数 = 0', '0',
               'SELECT COUNT(*) FROM %s AND sr.score <> h.score_total' % PAIR))
o.append(check('score_total / 平时 / 期末 与权威不一致的行数 = 0', '0',
               'SELECT COUNT(*) FROM %s AND (NOT (sr.score_total<=>h.score_total) '
               'OR NOT (sr.regular_score<=>h.regular_score) OR NOT (sr.final_score<=>h.final_score))' % PAIR))
o.append(check('grade 与 scoreToGrade 不一致的行数 = 0', '0',
               'SELECT COUNT(*) FROM %s AND sr.grade <> %s' % (PAIR, G)))
o.append(check('grade_point 与 scoreToGpa 不一致的行数 = 0', '0',
               'SELECT COUNT(*) FROM %s AND sr.grade_point <> %s' % (PAIR, P)))
o.append(check('score_level 与 scoreToLevel 不一致的行数 = 0', '0',
               'SELECT COUNT(*) FROM %s AND sr.score_level <> %s' % (PAIR, L)))
o.append(check('grade 与权威表逐字相同（不是各自算的）', '0',
               'SELECT COUNT(*) FROM %s AND sr.grade <> h.grade' % PAIR))
o.append(check('grade_point 与权威表逐字相同', '0',
               'SELECT COUNT(*) FROM %s AND sr.grade_point <> h.grade_point' % PAIR))
o.append('')

o.append('--- (c) 只改了分数列：其余字段与备份逐字相同 ---')
o.append(check('id/semester/year/teacher_id/姓名/班级/评语 被动过的行数 = 0', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN %s b ON b.id=sr.id '
               'WHERE NOT (sr.student_id<=>b.student_id) OR NOT (sr.course_id<=>b.course_id) '
               'OR NOT (sr.teacher_id<=>b.teacher_id) OR NOT (sr.student_name<=>b.student_name) '
               'OR NOT (sr.class_name<=>b.class_name) OR NOT (sr.semester<=>b.semester) '
               'OR NOT (sr.year<=>b.year) OR NOT (sr.remark<=>b.remark) '
               'OR NOT (sr.create_time<=>b.create_time)' % BAK))
UNPAIR = ('score_record sr WHERE NOT EXISTS (SELECT 1 FROM scores h '
          'WHERE h.student_id=sr.student_id AND h.course_id=sr.course_id '
          'AND h.semester=CAST(sr.semester AS UNSIGNED))')
o.append(check('无法配对的 95 行确实有 95 行（92 选修 NULL 学期 + 3 行学期6）', '95',
               'SELECT COUNT(*) FROM %s' % UNPAIR))
o.append(check('这 95 行的分数列与备份逐字相同（一行未动）', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN %s b ON b.id=sr.id '
               'WHERE NOT EXISTS (SELECT 1 FROM scores h WHERE h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED)) '
               'AND (NOT (sr.score<=>b.score) OR NOT (sr.score_total<=>b.score_total) '
               'OR NOT (sr.grade<=>b.grade) OR NOT (sr.grade_point<=>b.grade_point) '
               'OR NOT (sr.score_level<=>b.score_level) '
               'OR NOT (sr.regular_score<=>b.regular_score) OR NOT (sr.final_score<=>b.final_score))' % BAK))
o.append(check('实际被改动的行数（= 备份与现状分数列不同的行）', '152',
               'SELECT COUNT(*) FROM score_record sr JOIN %s b ON b.id=sr.id '
               'WHERE NOT (sr.score<=>b.score) OR NOT (sr.score_total<=>b.score_total) '
               'OR NOT (sr.grade<=>b.grade) OR NOT (sr.grade_point<=>b.grade_point) '
               'OR NOT (sr.score_level<=>b.score_level)' % BAK))
o.append('')

o.append('--- (d) 权威表 scores 一行未动 ---')
o.append(check('scores 仍是 167 行', '167', 'SELECT COUNT(*) FROM scores'))
o.append(check('scores 里 23 名同学的挂科行仍是 8 行（反方向对齐会让它们消失）', '8',
               'SELECT COUNT(*) FROM scores WHERE score_total<60 AND student_id<>3'))
o.append(check('司明的挂科行仍是 9 行（含 4 行期末未录）', '9',
               'SELECT COUNT(*) FROM scores WHERE score_total<60 AND student_id=3'))
o.append(check('司明/李爽/张健/韩坤 挂科门数仍是 3,3,3,2', '3,3,3,2',
               'SELECT GROUP_CONCAT(f.c) FROM (SELECT COUNT(*) c FROM ('
               'SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL) t '
               'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id ORDER BY c DESC, t.student_id) f'))
o.append('')

o.append('--- (e) 教师端与学生端现在显示同一个分数 ---')
o.append(check('原先把挂科漂成及格的那 8 行，两表现已相等', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED) '
               'WHERE h.score_total<60 AND sr.score<>h.score_total'))
o.append(check('这 8 行的(学生,课程,权威分,录入分)：韩坤59/54 李爽54/52/47 张健52/48/49',
               '13-4-59.00-59.00,13-6-54.00-54.00,20-1-54.00-54.00,20-3-52.00-52.00,'
               '20-5-47.00-47.00,26-1-52.00-52.00,26-3-48.00-48.00,26-5-49.00-49.00',
               'SELECT GROUP_CONCAT(CONCAT(sr.student_id,"-",sr.course_id,"-",h.score_total,"-",sr.score) '
               'ORDER BY sr.student_id, sr.course_id) FROM score_record sr JOIN scores h '
               'ON h.student_id=sr.student_id AND h.course_id=sr.course_id '
               'AND h.semester=CAST(sr.semester AS UNSIGNED) '
               'WHERE h.score_total<60 AND sr.student_id<>3'))
o.append(check('司明的 14 行也随之对齐（等级由旧口径 B 变成新口径 A-）', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED) '
               'WHERE sr.student_id=3 AND sr.grade<>h.grade'))
o.append('')

o.append('--- (f) 两个口径统一成一条：总评 = (平时+期末)/2（差 ±0.5 视为取整误差）---')
o.append(check('可配对的行里不吻合 50/50 的 = 6 行，全部继承自 scores（不是本次改出来的）', '6',
               'SELECT COUNT(*) FROM %s AND sr.score_total <> ROUND((sr.regular_score+sr.final_score)/2)' % PAIR))
o.append(check('score_record 全表不吻合 50/50 = 59 行：可配对 6（继承 scores）+ 未动的选修课 53', '59\t6\t53',
               'SELECT (SELECT COUNT(*) FROM score_record WHERE regular_score IS NOT NULL AND final_score IS NOT NULL '
               'AND score_total <> ROUND((regular_score+final_score)/2)), '
               '(SELECT COUNT(*) FROM score_record sr WHERE sr.regular_score IS NOT NULL AND sr.final_score IS NOT NULL '
               'AND sr.score_total <> ROUND((sr.regular_score+sr.final_score)/2) AND EXISTS (SELECT 1 FROM scores h '
               'WHERE h.student_id=sr.student_id AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED))), '
               '(SELECT COUNT(*) FROM score_record sr WHERE sr.regular_score IS NOT NULL AND sr.final_score IS NOT NULL '
               'AND sr.score_total <> ROUND((sr.regular_score+sr.final_score)/2) AND NOT EXISTS (SELECT 1 FROM scores h '
               'WHERE h.student_id=sr.student_id AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED)))'))
o.append(check('scores 自身不吻合 50/50 的仍是 10 行，且全是司明（纯取整痕迹，本次未动）', '10\t10',
               'SELECT (SELECT COUNT(*) FROM scores WHERE regular_score IS NOT NULL AND final_score IS NOT NULL '
               'AND score_total <> ROUND((regular_score+final_score)/2)), '
               '(SELECT COUNT(*) FROM scores WHERE student_id=3 AND regular_score IS NOT NULL AND final_score IS NOT NULL '
               'AND score_total <> ROUND((regular_score+final_score)/2))'))
o.append('')

o.append('--- (g) 回滚脚本可行性 ---')
o.append(check('备份 247 行、主键无重复', '247\t247',
               'SELECT COUNT(*), COUNT(DISTINCT id) FROM %s' % BAK))
o.append(check('备份里与现状分数列不同的行数 = 152（回滚要还原的行数）', '152',
               'SELECT COUNT(*) FROM score_record sr JOIN %s b ON b.id=sr.id '
               'WHERE NOT (sr.score<=>b.score) OR NOT (sr.score_total<=>b.score_total) '
               'OR NOT (sr.grade<=>b.grade) OR NOT (sr.grade_point<=>b.grade_point) '
               'OR NOT (sr.score_level<=>b.score_level)' % BAK))

o.append('')
o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))

with open('_verify5.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify5.txt; fails =', len(fails))
