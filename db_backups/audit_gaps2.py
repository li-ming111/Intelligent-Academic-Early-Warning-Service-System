# -*- coding: utf-8 -*-
"""解决两个悬而未决的问题：

1. score_record.student_id 到底存的是 student_profile.id 还是 users.id？
   （两个 id 空间有重叠的话，JOIN 会把两边的行都算进去，必须先判定清楚）
2. benchmark_analysis 还有没有读取路径？

以及：重算 grade_point 会对 GPA / 班级均分 / 排名造成多大影响。
"""
import io
import subprocess

DB = 'academic_warning_system'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')


out = []

out.append('== 1a. 两个 id 空间的实际取值范围 ==')
out.append(q('SELECT "student_profile.id" AS space_, MIN(id) mn, MAX(id) mx, COUNT(*) n FROM student_profile '
             'UNION ALL SELECT "users.id", MIN(id), MAX(id), COUNT(*) FROM users;'))

out.append('== 1b. score_record.student_id 的取值范围 ==')
out.append(q('SELECT MIN(student_id) mn, MAX(student_id) mx, COUNT(DISTINCT student_id) d FROM score_record;'))

out.append('== 1c. 是否两个空间都命中（重叠判定）==')
out.append(q("SELECT SUM(sp.id IS NOT NULL) AS hit_profile, SUM(u.id IS NOT NULL) AS hit_users, "
             "SUM(sp.id IS NOT NULL AND u.id IS NOT NULL) AS hit_both "
             "FROM score_record sr "
             "LEFT JOIN student_profile sp ON sp.id = sr.student_id "
             "LEFT JOIN users u ON u.id = sr.student_id;"))

out.append('== 1d. 取一个具体学号验证：谁的 profile.id / user.id 是同一个数 ==')
out.append(q("SELECT sp.id AS profile_id, sp.student_id AS 学号, sp.name, sp.user_id, "
             "(SELECT name FROM users WHERE id=sp.id) AS users表里同号的是谁 "
             "FROM student_profile sp WHERE sp.id <= 30 ORDER BY sp.id LIMIT 8;"))

out.append('== 1e. score_record 里出现的 student_id 是否都是 profile.id（用姓名交叉验证）==')
out.append(q("SELECT sr.student_id, COUNT(*) rows_, "
             "(SELECT name FROM student_profile WHERE id=sr.student_id) AS 按profile解释, "
             "(SELECT name FROM users WHERE id=sr.student_id) AS 按user解释 "
             "FROM score_record sr GROUP BY sr.student_id ORDER BY sr.student_id LIMIT 12;"))

out.append('== 2. benchmark_analysis 是否被任何查询引用（表名扫描）==')
for t in ('BenchmarkAnalysisMapper', 'StudentServiceImpl', 'ScoreMapper'):
    pass

out.append(q('SELECT COUNT(*) AS benchmark_rows FROM benchmark_analysis;'))

out.append('== 3. 重算 grade_point 对 GPA 的影响（按 ScoreSyncSupport 公式）==')
GP = ("CASE WHEN s.score_total>=90 THEN 4.0 WHEN s.score_total>=85 THEN 3.7 WHEN s.score_total>=82 THEN 3.3 "
      "WHEN s.score_total>=78 THEN 3.0 WHEN s.score_total>=75 THEN 2.7 WHEN s.score_total>=72 THEN 2.3 "
      "WHEN s.score_total>=68 THEN 2.0 WHEN s.score_total>=64 THEN 1.5 WHEN s.score_total>=60 THEN 1.0 ELSE 0.0 END")
LATEST = ("(SELECT lat.id, lat.student_id, lat.course_id, lat.semester, lat.score_total, lat.grade_point "
          "FROM (SELECT s.id, s.student_id, s.course_id, s.semester, s.score_total, s.grade_point, "
          "ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id ORDER BY s.semester DESC, s.id DESC) rn "
          "FROM scores s WHERE s.final_score IS NOT NULL) lat WHERE lat.rn=1)")
out.append('   学生 | 现在GPA(用库里的grade_point) | 重算后GPA')
out.append(q("SELECT sp.id AS profile_id, sp.name, "
             "ROUND(SUM(v.grade_point*c.credits)/NULLIF(SUM(CASE WHEN v.grade_point IS NOT NULL AND c.credits IS NOT NULL THEN c.credits ELSE 0 END),0),4) AS gpa_now, "
             "ROUND(SUM((" + GP.replace('s.score_total', 'v.score_total') + ")*c.credits)/NULLIF(SUM(c.credits),0),4) AS gpa_after "
             "FROM " + LATEST + " v JOIN student_profile sp ON sp.id=v.student_id "
             "LEFT JOIN courses c ON c.id=v.course_id GROUP BY sp.id, sp.name ORDER BY sp.id;"))

out.append('== 4. 班级均分 GPA 变化（class 1 = 计科2306班）==')
out.append(q("SELECT COUNT(DISTINCT sp.id) AS students, "
             "ROUND(AVG(t.gpa_now),4) AS class_avg_now, ROUND(AVG(t.gpa_after),4) AS class_avg_after FROM ("
             "SELECT sp.id, "
             "SUM(v.grade_point*c.credits)/NULLIF(SUM(CASE WHEN v.grade_point IS NOT NULL AND c.credits IS NOT NULL THEN c.credits ELSE 0 END),0) AS gpa_now, "
             "SUM((" + GP.replace('s.score_total', 'v.score_total') + ")*c.credits)/NULLIF(SUM(c.credits),0) AS gpa_after "
             "FROM " + LATEST + " v JOIN student_profile sp ON sp.id=v.student_id "
             "LEFT JOIN courses c ON c.id=v.course_id WHERE sp.class_id=1 GROUP BY sp.id) t;"))

io.open('D:/ABS/db_backups/_gaps2.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('ok')
