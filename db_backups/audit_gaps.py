# -*- coding: utf-8 -*-
"""为"让派生字段等于系统算出的值"收集事实依据。

只读，不改数据。
"""
import io
import subprocess

DB = 'academic_warning_system'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')


out = []

out.append('== 1. scores.grade / grade_point 现状 vs 按 score_total 应得的（SampleCaliber 公式）==')
out.append(q("""
SELECT
  CASE WHEN score_total>=90 THEN 'A' WHEN score_total>=80 THEN 'A-'
       WHEN score_total>=70 THEN 'B' WHEN score_total>=60 THEN 'C' ELSE 'D' END AS should_be_grade,
  grade AS now_grade, COUNT(*) AS n,
  CASE WHEN score_total>=90 THEN 4.0 WHEN score_total>=85 THEN 3.7 WHEN score_total>=82 THEN 3.3
       WHEN score_total>=78 THEN 3.0 WHEN score_total>=75 THEN 2.7 WHEN score_total>=72 THEN 2.3
       WHEN score_total>=68 THEN 2.0 WHEN score_total>=64 THEN 1.5 WHEN score_total>=60 THEN 1.0
       ELSE 0.0 END AS should_be_gp,
  grade_point AS now_gp
FROM scores GROUP BY should_be_grade, grade, should_be_gp, grade_point
ORDER BY should_be_grade, grade;"""))

out.append('== 2. scores.year 现状 vs 应有值 ==')
out.append('   应有 year = 入学年 + (semester-1)/2；入学年取学号前四位')
out.append(q("""
SELECT s.semester,
       s.year AS now_year,
       MIN(CAST(LEFT(sp.student_id,4) AS UNSIGNED)) AS min_entrance,
       MAX(CAST(LEFT(sp.student_id,4) AS UNSIGNED)) AS max_entrance,
       MIN(CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (s.semester-1) DIV 2) AS should_be_year_min,
       COUNT(*) AS n
FROM scores s JOIN student_profile sp ON sp.id = s.student_id
GROUP BY s.semester, s.year ORDER BY s.semester;"""))

out.append('== 3. 全体学生的学号前四位（入学年）分布 ==')
out.append(q("SELECT LEFT(student_id,4) AS entrance_year, COUNT(*) n, "
             "MIN(student_id) eg_min, MAX(student_id) eg_max, "
             "SUM(enrollment_year IS NULL) AS enrollment_year_null "
             "FROM student_profile GROUP BY LEFT(student_id,4) ORDER BY entrance_year;"))

out.append('== 4. score_record.student_id 是哪个 id 空间 ==')
out.append(q("SELECT COUNT(*) AS total, "
             "SUM(sp.id IS NOT NULL) AS is_profile_id, "
             "SUM(u.id IS NOT NULL) AS is_user_id "
             "FROM score_record sr "
             "LEFT JOIN student_profile sp ON sp.id = sr.student_id "
             "LEFT JOIN users u ON u.id = sr.student_id;"))

out.append('== 5. score_record.semester 能否从 scores 回填（按 student_id+course_id 关联）==')
out.append(q("""
SELECT
  COUNT(*) AS total_rows,
  SUM(sr.semester IS NULL) AS null_before,
  SUM(sr.semester IS NULL AND sc.semester IS NOT NULL) AS can_backfill,
  SUM(sr.semester IS NULL AND sc.semester IS NULL) AS no_source,
  SUM(sr.semester IS NOT NULL AND sc.semester IS NOT NULL AND sr.semester <> sc.semester) AS wrong_now
FROM score_record sr
LEFT JOIN (SELECT student_id, course_id, MAX(semester) AS semester
           FROM scores GROUP BY student_id, course_id) sc
  ON sc.student_id = sr.student_id AND sc.course_id = sr.course_id;"""))

out.append('== 6. score_record.semester 现状 vs scores 权威学期（只看能对上的）==')
out.append(q("""
SELECT sr.semester AS now_sem, sc.semester AS authoritative_sem, COUNT(*) n
FROM score_record sr
JOIN (SELECT student_id, course_id, MAX(semester) AS semester
      FROM scores GROUP BY student_id, course_id) sc
  ON sc.student_id = sr.student_id AND sc.course_id = sr.course_id
GROUP BY sr.semester, sc.semester ORDER BY authoritative_sem, now_sem;"""))

out.append('== 7. benchmark_analysis 是否还有代码调用点 ==')
out.append(q("SELECT COUNT(*) AS rows_now FROM benchmark_analysis;"))

out.append('== 8. score_record.score_level 现状 vs scoreToLevel(score) ==')
out.append(q("""
SELECT score_level AS now_level,
  CASE WHEN score>=90 THEN 1 WHEN score>=80 THEN 2 WHEN score>=70 THEN 3
       WHEN score>=60 THEN 4 ELSE 5 END AS should_be,
  COUNT(*) n
FROM score_record GROUP BY score_level, should_be ORDER BY should_be, now_level;"""))

out.append('== 9. scores 里 grade/grade_point 为 NULL 的行（应补上）==')
out.append(q("SELECT COUNT(*) AS grade_null, SUM(grade_point IS NULL) AS gp_null FROM scores;"))

out.append('== 10. 重算后权威口径是否不变（当前值，作为对照基线）==')
LATEST = ("(SELECT lat.* FROM (SELECT s.id, s.student_id, s.course_id, s.semester, s.score_total, "
          "ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id ORDER BY s.semester DESC, s.id DESC) rn "
          "FROM scores s WHERE s.final_score IS NOT NULL) lat WHERE lat.rn=1)")
out.append(q("SELECT v.student_id, sp.name, COUNT(*) failCount FROM " + LATEST +
             " v JOIN student_profile sp ON sp.id=v.student_id WHERE v.score_total<60 "
             "GROUP BY v.student_id, sp.name ORDER BY v.student_id;"))

io.open('D:/ABS/db_backups/_gaps.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('ok')
