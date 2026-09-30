# -*- coding: utf-8 -*-
"""执行后校验：派生字段是否已与系统公式一致，且权威口径基线未被改动。"""
import io
import subprocess

DB = 'academic_warning_system'

GRADE = ("CASE WHEN score_total>=90 THEN 'A' WHEN score_total>=80 THEN 'A-' "
         "WHEN score_total>=70 THEN 'B' WHEN score_total>=60 THEN 'C' ELSE 'D' END")
GP = ("CASE WHEN score_total>=90 THEN 4.0 WHEN score_total>=85 THEN 3.7 WHEN score_total>=82 THEN 3.3 "
      "WHEN score_total>=78 THEN 3.0 WHEN score_total>=75 THEN 2.7 WHEN score_total>=72 THEN 2.3 "
      "WHEN score_total>=68 THEN 2.0 WHEN score_total>=64 THEN 1.5 WHEN score_total>=60 THEN 1.0 "
      "ELSE 0.0 END")
LEVEL = ("CASE WHEN score>=90 THEN 1 WHEN score>=80 THEN 2 WHEN score>=70 THEN 3 "
         "WHEN score>=60 THEN 4 ELSE 5 END")
LATEST = ("(SELECT lat.* FROM (SELECT s.id, s.student_id, s.course_id, s.semester, s.score_total, "
          "ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id ORDER BY s.semester DESC, s.id DESC) rn "
          "FROM scores s WHERE s.final_score IS NOT NULL) lat WHERE lat.rn=1)")


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')


checks = []
checks.append(('(a) grade/grade_point 仍与公式不符的行  [期望 0]',
               q('SELECT COUNT(*) FROM scores WHERE final_score IS NOT NULL AND '
                 '(grade <> %s OR grade_point <> %s)' % (GRADE, GP)).strip()))
checks.append(('(b) year 仍与 semester/入学年 不符的行  [期望 0]',
               q('SELECT COUNT(*) FROM scores s JOIN student_profile sp ON sp.id=s.student_id '
                 'WHERE s.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (s.semester-1) DIV 2').strip()))
checks.append(('(c) enrollment_year 仍为 NULL 的学生  [期望 0]',
               q('SELECT COUNT(*) FROM student_profile WHERE enrollment_year IS NULL').strip()))
checks.append(('(d) enrollment_year 与学号前四位不符  [期望 0]',
               q('SELECT COUNT(*) FROM student_profile '
                 'WHERE enrollment_year <> CAST(LEFT(student_id,4) AS UNSIGNED)').strip()))
checks.append(('(e) benchmark_analysis 行数  [期望 0]',
               q('SELECT COUNT(*) FROM benchmark_analysis').strip()))
checks.append(('(f) 备份表 benchmark_analysis_bak_20260916 行数  [期望 27]',
               q('SELECT COUNT(*) FROM benchmark_analysis_bak_20260916').strip()))
checks.append(('(g) score_record.score_level 仍不符的行  [期望 0]',
               q('SELECT COUNT(*) FROM score_record WHERE score IS NOT NULL AND '
                 'score_level <> ' + LEVEL).strip()))
checks.append(('(h) grade/grade_point 为 NULL 的行  [期望 4 = 司明学期6 待录入]',
               q('SELECT COUNT(*) FROM scores WHERE grade IS NULL OR grade_point IS NULL').strip()))
checks.append(('(i) grade_point 里是否还有公式算不出的值  [期望 0]',
               q('SELECT COUNT(*) FROM scores WHERE final_score IS NOT NULL AND grade_point NOT IN '
                 '(4.0,3.7,3.3,3.0,2.7,2.3,2.0,1.5,1.0,0.0)').strip()))
checks.append(('(j) 权威口径：挂科门数  [期望 3/3/3/2]',
               q('SELECT GROUP_CONCAT(CONCAT(v.student_id,":",c) ORDER BY v.student_id) FROM '
                 '(SELECT v.student_id, COUNT(*) c FROM ' + LATEST +
                 ' v WHERE v.score_total < 60 GROUP BY v.student_id) v').strip()))
checks.append(('(k) scores 总行数  [期望 167，不应变动]', q('SELECT COUNT(*) FROM scores').strip()))
checks.append(('(l) score_record 总行数  [期望 384，不应变动]', q('SELECT COUNT(*) FROM score_record').strip()))

out = []
for label, val in checks:
    out.append('%-58s => %s' % (label, val))

out.append('')
out.append('== 司明(profile 3) 重算后的成绩单 ==')
out.append(q('SELECT v.course_id, c.name, v.semester, v.score_total, s.grade, s.grade_point '
             'FROM ' + LATEST + ' v LEFT JOIN scores s ON s.id=v.id '
             'LEFT JOIN courses c ON c.id=v.course_id WHERE v.student_id=3 ORDER BY v.semester, v.course_id;'))

out.append('== 司明 GPA 重算后（系统公式）==')
out.append(q('SELECT ROUND(SUM(v.score_total<60),0) AS fails, COUNT(*) AS courses, '
             'ROUND(SUM(s.grade_point*c.credits)/NULLIF(SUM(c.credits),0),4) AS gpa '
             'FROM ' + LATEST + ' v LEFT JOIN scores s ON s.id=v.id '
             'LEFT JOIN courses c ON c.id=v.course_id WHERE v.student_id=3;'))

out.append('== 班级1 平均 GPA 重算后 ==')
out.append(q('SELECT COUNT(DISTINCT t.student_id) AS students, '
             'ROUND(AVG(t.gpa),4) AS class_avg_gpa FROM (SELECT v.student_id, '
             'SUM(s.grade_point*c.credits)/NULLIF(SUM(c.credits),0) AS gpa FROM ' + LATEST +
             ' v JOIN student_profile sp ON sp.id=v.student_id LEFT JOIN scores s ON s.id=v.id '
             'LEFT JOIN courses c ON c.id=v.course_id WHERE sp.class_id=1 GROUP BY v.student_id) t;'))

io.open('D:/ABS/db_backups/_verify.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('\n'.join(out[:12]))
