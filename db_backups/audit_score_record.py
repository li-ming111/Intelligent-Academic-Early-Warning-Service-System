# -*- coding: utf-8 -*-
"""量化 score_record 与 scores 两套等级/绩点口径的分歧。

score_record 的 grade/grade_point/score_total/regular_score/final_score 这几列
没有任何代码写入（insertScore/updateScore 都不写它们），全是遗留种子值。
这里把它们的内部规律和 ScoreSyncSupport 的口径都算出来，看差多少。
"""
import io
import subprocess

DB = 'academic_warning_system'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace')
    if r.returncode != 0:
        out += '[SQLERR] ' + r.stderr.decode('utf-8', 'replace')
    return out.strip()


o = []
o.append('== score_record 关键列非空情况 ==')
o.append(q("SELECT COUNT(*) AS 总行数, "
           "SUM(grade IS NOT NULL) AS grade非空, SUM(grade_point IS NOT NULL) AS gp非空, "
           "SUM(score_total IS NOT NULL) AS total非空, SUM(regular_score IS NOT NULL) AS 平时非空, "
           "SUM(final_score IS NOT NULL) AS 期末非空, SUM(score IS NOT NULL) AS score非空, "
           "SUM(score_level IS NOT NULL) AS level非空 FROM score_record"))

o.append('')
o.append('== 反推 score_record 自己的 grade_point 规律 ==')
o.append('  (score_total-50)/10 截到4.0 是否成立：')
o.append(q("SELECT COUNT(*) AS 不符行数 FROM score_record WHERE grade_point IS NOT NULL "
           "AND ABS(grade_point - LEAST(4.0, (score_total-50)/10)) > 0.001"))
o.append('  grade 是否 = A/B/C/D/F 按 90/80/70/60 切分（基于 score_total）：')
o.append(q("SELECT COUNT(*) AS 不符行数 FROM score_record WHERE grade IS NOT NULL AND grade <> "
           "CASE WHEN score_total>=90 THEN 'A' WHEN score_total>=80 THEN 'B' "
           "WHEN score_total>=70 THEN 'C' WHEN score_total>=60 THEN 'D' ELSE 'F' END"))

o.append('')
o.append('== 换成 ScoreSyncSupport 口径后差多少 ==')
o.append('  grade 不符（ScoreSyncSupport: A/A-/B/C/D，无 F）：')
o.append(q("SELECT COUNT(*) FROM score_record WHERE score_total IS NOT NULL AND grade <> "
           "CASE WHEN score_total>=90 THEN 'A' WHEN score_total>=80 THEN 'A-' "
           "WHEN score_total>=70 THEN 'B' WHEN score_total>=60 THEN 'C' ELSE 'D' END"))
o.append('  grade_point 不符（ScoreSyncSupport 阶梯函数）：')
o.append(q("SELECT COUNT(*) FROM score_record WHERE score_total IS NOT NULL AND grade_point <> "
           "CASE WHEN score_total>=90 THEN 4.0 WHEN score_total>=85 THEN 3.7 WHEN score_total>=82 THEN 3.3 "
           "WHEN score_total>=78 THEN 3.0 WHEN score_total>=75 THEN 2.7 WHEN score_total>=72 THEN 2.3 "
           "WHEN score_total>=68 THEN 2.0 WHEN score_total>=64 THEN 1.5 WHEN score_total>=60 THEN 1.0 "
           "ELSE 0.0 END"))

o.append('')
o.append('== score_record.score 与 score_total 的关系（两者是否同一个数）==')
o.append(q("SELECT COUNT(*) AS 总行, SUM(ABS(score-score_total)<0.001) AS 相同, "
           "SUM(ABS(score-score_total)>=0.001) AS 不同, "
           "ROUND(AVG(score-score_total),2) AS 平均差 FROM score_record "
           "WHERE score IS NOT NULL AND score_total IS NOT NULL"))

o.append('')
o.append('== score_level 该按哪个数列算 ==')
o.append('  与 scoreToLevel(score) 不符的行数 [我本次修的就是这个口径]：')
o.append(q("SELECT COUNT(*) FROM score_record WHERE score IS NOT NULL AND score_level <> "
           "CASE WHEN score>=90 THEN 1 WHEN score>=80 THEN 2 WHEN score>=70 THEN 3 "
           "WHEN score>=60 THEN 4 ELSE 5 END"))
o.append('  与 scoreToLevel(score_total) 不符的行数：')
o.append(q("SELECT COUNT(*) FROM score_record WHERE score_total IS NOT NULL AND score_level <> "
           "CASE WHEN score_total>=90 THEN 1 WHEN score_total>=80 THEN 2 WHEN score_total>=70 THEN 3 "
           "WHEN score_total>=60 THEN 4 ELSE 5 END"))

o.append('')
o.append('== score_record 举例：同一门课在两表里的等级对照 ==')
o.append(q("SELECT sr.id, sr.student_name, c.name AS 课程, sr.score AS 录入分, sr.score_total AS 总评, "
           "sr.grade AS 记录等级, sr.grade_point AS 记录绩点, "
           "s.score_total AS scores总评, s.grade AS scores等级, s.grade_point AS scores绩点 "
           "FROM score_record sr LEFT JOIN courses c ON c.id=sr.course_id "
           "LEFT JOIN scores s ON s.student_id=sr.student_id AND s.course_id=sr.course_id "
           "WHERE sr.grade_point IS NOT NULL AND s.id IS NOT NULL LIMIT 12"))

o.append('')
o.append('== 全库等级词汇表分布 ==')
o.append(q("SELECT 'scores' AS 表, grade, COUNT(*) FROM scores GROUP BY grade "
           "UNION ALL SELECT 'score_record', grade, COUNT(*) FROM score_record GROUP BY grade ORDER BY 1,2"))

o.append('')
o.append('== score_record 里 total/grade/gp 非空但 score 为空的行 ==')
o.append(q("SELECT COUNT(*) FROM score_record WHERE score IS NULL AND (grade IS NOT NULL OR score_total IS NOT NULL)"))

io.open('D:/ABS/db_backups/_score_record.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(o))
print('ok')
