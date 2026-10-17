# -*- coding: utf-8 -*-
"""全局收尾扫描：幻影课程名、幻影人名、学期自洽、以及各表剩余的可疑值。"""
import io
import subprocess

DB = 'academic_warning_system'
PHANTOM = ['线性代数', '计算机组成原理', 'Java程序设计', '大学物理', '程序设计基础', '算法设计', '数据库系统', '软件工程']


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace')
    if r.returncode != 0:
        out += '[SQLERR] ' + r.stderr.decode('utf-8', 'replace')
    return out.strip()


o = []
o.append('== 系统实际课程名（权威清单）==')
real = q('SELECT GROUP_CONCAT(name ORDER BY id) FROM courses')
o.append('  ' + real)
o.append('')

o.append('== 哪些表的文字里出现了"不是本系统课程"的名称 ==')
cols = q("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
         "WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE IN ('text','mediumtext','longtext','varchar') "
         "AND (CHARACTER_MAXIMUM_LENGTH IS NULL OR CHARACTER_MAXIMUM_LENGTH >= 20) "
         "AND TABLE_NAME NOT LIKE '%_bak_%' ORDER BY TABLE_NAME")
targets = []
for line in cols.split('\n'):
    line = line.strip('\r\n')
    if not line.strip():
        continue
    p = [x.strip('\r') for x in line.split('\t')]
    if len(p) >= 2:
        targets.append((p[0], p[1]))

for name in PHANTOM:
    hits = []
    for t, c in targets:
        n = q("SELECT COUNT(*) FROM `%s` WHERE `%s` LIKE '%%%s%%'" % (t, c, name))
        if n.isdigit() and int(n) > 0:
            ids = q("SELECT GROUP_CONCAT(id) FROM `%s` WHERE `%s` LIKE '%%%s%%'" % (t, c, name))
            hits.append('%s.%s(%s行: %s)' % (t, c, n, ids[:60]))
    if hits:
        o.append('  【%s】出现在: %s' % (name, '; '.join(hits)))
    else:
        o.append('  【%s】未出现 ✓' % name)

o.append('')
o.append('== 幻影人名 张明（通知/沟通记录里点名提到，但 users 表没有此人）==')
o.append('  users 里名字含张的: ' + q("SELECT GROUP_CONCAT(CONCAT(id,':',name) ) FROM users WHERE name LIKE '%张%'"))
for t, c in targets:
    n = q("SELECT COUNT(*) FROM `%s` WHERE `%s` LIKE '%%张明%%'" % (t, c))
    if n.isdigit() and int(n) > 0:
        o.append('  出现在 %s.%s : %s 行 -> %s' % (t, c, n,
                 q("SELECT GROUP_CONCAT(id) FROM `%s` WHERE `%s` LIKE '%%张明%%'" % (t, c))))

o.append('')
o.append('== scores.semester 与 year 是否自洽 ==')
o.append('  不符的行数 [期望 0]: ' + q(
    'SELECT COUNT(*) FROM scores s JOIN student_profile sp ON sp.id=s.student_id '
    'WHERE s.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (s.semester-1) DIV 2'))

o.append('')
o.append('== scores 里 final_score 为空的行（属"待录入"，不应算挂科）==')
o.append(q('SELECT student_id, course_id, semester, regular_score, final_score, score_total, grade FROM scores '
           'WHERE final_score IS NULL ORDER BY student_id, course_id'))

o.append('')
o.append('== scores 的等级/绩点与派生规则是否自洽（唯一权威成绩表）==')
o.append('  行数 / grade / grade_point / score_total / regular_score / final_score / remark 非空:')
o.append('  ' + q('SELECT COUNT(*), SUM(grade IS NOT NULL), SUM(grade_point IS NOT NULL), '
                   'SUM(score_total IS NOT NULL), SUM(regular_score IS NOT NULL), '
                   'SUM(final_score IS NOT NULL), SUM(remark IS NOT NULL) FROM scores'))
o.append('  换成当前口径(ScoreSyncSupport)后不符: grade ' + q(
    "SELECT COUNT(*) FROM scores WHERE score_total IS NOT NULL AND grade <> "
    "CASE WHEN score_total>=90 THEN 'A' WHEN score_total>=80 THEN 'A-' WHEN score_total>=70 THEN 'B' "
    "WHEN score_total>=60 THEN 'C' ELSE 'D' END") + ' 行, grade_point ' + q(
    "SELECT COUNT(*) FROM scores WHERE score_total IS NOT NULL AND grade_point <> "
    "CASE WHEN score_total>=90 THEN 4.0 WHEN score_total>=85 THEN 3.7 WHEN score_total>=82 THEN 3.3 "
    "WHEN score_total>=78 THEN 3.0 WHEN score_total>=75 THEN 2.7 WHEN score_total>=72 THEN 2.3 "
    "WHEN score_total>=68 THEN 2.0 WHEN score_total>=64 THEN 1.5 WHEN score_total>=60 THEN 1.0 "
    "ELSE 0.0 END") + ' 行')

o.append('')
o.append('== 「remark 与 course_id 题材错配」这类检查已失去对象 ==')
o.append('  原来是查 `score_record.remark`（旧课程的评语题材）与 `course_id` 对不上。')
o.append('  `score_record` 已于 2026-10-12 W39 整表删除；权威表 `scores` 虽有 `remark` 列，')
o.append('  但全库 `remark IS NOT NULL` 为 0 行（代码不写该列）⇒ 题材错配随旧表一起消失，无可查之列。')

io.open('D:/ABS/db_backups/_final_sweep.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(o))
print('ok')
