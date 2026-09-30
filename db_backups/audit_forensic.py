# -*- coding: utf-8 -*-
"""取证：数据是"系统流程产生的"还是"脚本批量灌的"。

真实业务数据的 created_at 会散落在很多时刻；脚本灌的数据会挤在极少数几个时刻，
或者整列是 NULL（说明 INSERT 显式写了 NULL，而不是让 DEFAULT CURRENT_TIMESTAMP 生效）。
"""
import io
import subprocess

DB = 'academic_warning_system'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')


out = []

out.append('== A. created_at 的列定义（有默认值却全是 NULL = 被显式写成 NULL）==')
out.append(q("SELECT TABLE_NAME, COLUMN_DEFAULT, IS_NULLABLE, EXTRA "
             "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
             "AND COLUMN_NAME='created_at' AND TABLE_NAME IN "
             "('scores','score_record','student_profile','benchmark_analysis','enrollments','courses');"))

out.append('== B. scores 的 5 个 created_at 时刻，各有多少行 ==')
out.append(q("SELECT created_at, COUNT(*) AS rows_ FROM scores "
             "GROUP BY created_at ORDER BY created_at;"))

out.append('== C. 每批灌入的分数是否"过于整齐"（不同分的个数 / 最小最大 / 平均）==')
out.append(q("SELECT created_at, COUNT(*) n, COUNT(DISTINCT score_total) distinct_scores, "
             "MIN(score_total) mn, MAX(score_total) mx, ROUND(AVG(score_total),2) avg_ "
             "FROM scores GROUP BY created_at ORDER BY created_at;"))

out.append('== D. student_profile 的 2 个时刻 + 是否同一批建的学生 ==')
out.append(q("SELECT created_at, COUNT(*) n, MIN(id) min_id, MAX(id) max_id "
             "FROM student_profile GROUP BY created_at ORDER BY created_at;"))

out.append('== E. benchmark_analysis 写死的常量（跨行完全相同的列）==')
out.append(q("SELECT COUNT(*) n, COUNT(DISTINCT student_gpa) d_gpa, "
             "COUNT(DISTINCT class_avg_gpa) d_classavg, COUNT(DISTINCT major_avg_gpa) d_majoravg, "
             "COUNT(DISTINCT required_credits) d_req, COUNT(DISTINCT class_total) d_classtotal, "
             "COUNT(DISTINCT semester) d_sem FROM benchmark_analysis;"))

out.append('== F. scores.year 与 semester 是否自洽（真实导入应自洽）==')
out.append(q("SELECT semester, year, COUNT(*) n FROM scores GROUP BY semester, year "
             "ORDER BY semester, year;"))

out.append('== G. 4 个挂科学生是谁、是否像真实学号 ==')
out.append(q("SELECT sp.id, sp.student_id AS 学号, sp.name, sp.class_name, sp.enrollment_year, "
             "u.username, u.created_at AS 账号创建时间 FROM student_profile sp "
             "LEFT JOIN users u ON u.id=sp.user_id WHERE sp.id IN (3,13,20,26) ORDER BY sp.id;"))

out.append('== H. enrollments 是否与 scores 对得上（144 行 vs 167 行成绩）==')
out.append(q("SELECT COUNT(*) AS enrollments FROM enrollments;"))
out.append(q("SELECT COUNT(*) AS distinct_pairs FROM (SELECT DISTINCT student_id, course_id FROM scores) x;"))

io.open('D:/ABS/db_backups/_forensic.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('ok')
