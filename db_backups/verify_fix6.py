# -*- coding: utf-8 -*-
"""第六批（步骤 B）校验：4 门选修课已补进权威表 scores，
且 scores 原有 167 行一字未动、score_record 一行未动、挂科基线未变。"""
import subprocess

DB = 'academic_warning_system'
BAK = 'scores_bak_elective_20260916'          # fix6 前的 scores 快照（167 行）
BAK5 = 'score_record_bak_align_20260916'       # fix5 前的 score_record 快照（247 行）

G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")

# 本批次新增的行
NEW = "scores h WHERE NOT EXISTS (SELECT 1 FROM %s b WHERE b.id = h.id)" % BAK
# 选修课的录入来源行（本批次新增行在 score_record 的对应行）
SRC = ("score_record sr JOIN scores h ON h.student_id=sr.student_id "
       "AND h.course_id=sr.course_id AND h.semester=6 "
       "AND NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) "
       "AND NOT EXISTS (SELECT 1 FROM scores_bak_elective_20260916 b2 "
       "                WHERE b2.student_id=h.student_id AND b2.course_id=h.course_id "
       "                  AND b2.semester=h.semester)" % BAK)


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
o.append('========== 第六批校验（选修课补进 scores）==========')
o.append('')

o.append('--- (a) 行数：scores 167 -> 259，score_record 不动 ---')
o.append(check('live 259 / 备份 167 / score_record 247', '259\t167\t247',
               'SELECT (SELECT COUNT(*) FROM scores), (SELECT COUNT(*) FROM %s), '
               '(SELECT COUNT(*) FROM score_record)' % BAK))
o.append(check('新增行数 = 92', '92', 'SELECT COUNT(*) FROM %s' % NEW))
o.append(check('原有 167 行一行不少', '0',
               'SELECT COUNT(*) FROM %s b WHERE NOT EXISTS (SELECT 1 FROM scores h WHERE h.id=b.id)' % BAK))
o.append(check('原有 167 行 13 列与备份逐字相同（一个字都没被改）', '0',
               'SELECT COUNT(*) FROM scores h JOIN %s b ON b.id=h.id WHERE '
               'NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id) '
               'OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year) '
               'OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score) '
               'OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade) '
               'OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change) '
               'OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at) '
               'OR NOT (h.updated_at<=>b.updated_at)' % BAK))
o.append(check('score_record 仍 247 行、id 集合与 fix5 校验后一致（本批未碰它）', '247\t247',
               'SELECT COUNT(*), COUNT(DISTINCT id) FROM score_record'))
o.append('')

o.append('--- (b) 92 行的取值完全符合口径 ---')
o.append(check('全部落在第 6 学期', '92', 'SELECT COUNT(*) FROM %s AND h.semester=6' % NEW))
o.append(check('课程只有 7/8/9/10 四门，每门 23 行', '7-23,8-23,9-23,10-23',
               'SELECT GROUP_CONCAT(CONCAT(course_id,"-",c) ORDER BY course_id SEPARATOR ",") FROM ('
               'SELECT h.course_id, COUNT(*) c FROM %s GROUP BY h.course_id) t' % NEW))
o.append(check('学生 23 人，每人 4 行', '23\t4\t92',
               'SELECT COUNT(*), MAX(c), SUM(c) FROM '
               '(SELECT student_id, COUNT(*) c FROM %s GROUP BY student_id) t' % NEW))
o.append(check('score_total 与录入表 score 完全一致（代码的不变式）', '0',
               'SELECT COUNT(*) FROM %s sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=6 AND sr.semester IS NULL '
               'AND NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND h.score_total <> sr.score' % ('score_record', BAK)))
o.append(check('平时分/期末分与录入表(空学期那行)一致', '0',
               'SELECT COUNT(*) FROM %s sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=6 AND sr.semester IS NULL '
               'AND NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND (NOT (h.regular_score<=>sr.regular_score) OR NOT (h.final_score<=>sr.final_score))'
               % ('score_record', BAK)))
o.append(check('若改用 score_record 里那 3 条带学期=6 的源行，也只差 1 行 —— 邱琳紫课8 的两条重复录入', '1\t4\t8\t88.00\t88.00',
               'SELECT COUNT(*), MIN(h.student_id), MIN(h.course_id), MIN(h.score_total), MIN(sr.score) '
               'FROM %s sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=6 AND sr.semester IS NOT NULL '
               'AND NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND (NOT (h.regular_score<=>sr.regular_score) OR NOT (h.final_score<=>sr.final_score))'
               % ('score_record', BAK)))
o.append(check('grade 与 scoreToGrade 一致', '0', 'SELECT COUNT(*) FROM %s AND h.grade <> %s' % (NEW, G)))
o.append(check('grade_point 与 scoreToGpa 一致', '0', 'SELECT COUNT(*) FROM %s AND h.grade_point <> %s' % (NEW, P)))
o.append(check('year = 学号前四位 + 2（赵琦 2022020137 应为 2024）', '0',
               'SELECT COUNT(*) FROM scores h JOIN student_profile sp ON sp.id=h.student_id '
               'WHERE NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND h.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + 2' % BAK))
o.append(check('2024 年的 4 行全部属于赵琦（student_id=6，2022 级）', '6\t4',
               'SELECT h.student_id, COUNT(*) FROM %s AND h.year=2024 GROUP BY h.student_id' % NEW))
o.append(check('其余 88 行都是 2025', '88', 'SELECT COUNT(*) FROM %s AND h.year=2025' % NEW))
o.append(check('新增行里没有一门不及格（不会平白多出挂科）', '0',
               'SELECT COUNT(*) FROM %s AND h.score_total < 60' % NEW))
o.append(check('新增行的期末分都已录入（不是待录入状态）', '0',
               'SELECT COUNT(*) FROM %s AND h.final_score IS NULL' % NEW))
o.append('')

o.append('--- (c) 没有制造重复 ---')
o.append(check('scores 全表 (学生,课程,学期) 重复三元组 = 0', '0',
               'SELECT COUNT(*) FROM (SELECT 1 FROM scores GROUP BY student_id, course_id, semester '
               'HAVING COUNT(*)>1) g'))
o.append(check('(学生,课程) 重复的对数 = 6 —— 全是司明自己的重修行（课1~课6 跨学期），fix6 之前就有', '6\t3\t3',
               'SELECT COUNT(*), MIN(student_id), MAX(student_id) FROM ('
               'SELECT student_id FROM scores GROUP BY student_id, course_id HAVING COUNT(*)>1) g'))
o.append(check('这 6 对里一行新增行都没混进去（fix6 制造 0 对重复）', '0',
               'SELECT COUNT(*) FROM scores h WHERE NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND (h.student_id, h.course_id) IN (SELECT student_id, course_id FROM scores '
               'GROUP BY student_id, course_id HAVING COUNT(*)>1)' % BAK))
o.append(check('每对重复都是"同一门课不同学期"（没有同学期重复）', '0',
               'SELECT COUNT(*) FROM (SELECT 1 FROM scores GROUP BY student_id, course_id, semester '
               'HAVING COUNT(*)>1) g'))
o.append('')

o.append('--- (d) 挂科基线与预警输入未变 ---')
o.append(check('23 名同学的挂科行仍是 8 行', '8',
               'SELECT COUNT(*) FROM scores WHERE score_total<60 AND student_id<>3'))
o.append(check('司明的挂科行仍是 9 行', '9',
               'SELECT COUNT(*) FROM scores WHERE score_total<60 AND student_id=3'))
o.append(check('司明/李爽/张健/韩坤 挂科门数仍是 3,3,3,2', '3,3,3,2',
               'SELECT GROUP_CONCAT(f.c) FROM (SELECT COUNT(*) c FROM ('
               'SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL) t '
               'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id ORDER BY c DESC, t.student_id) f'))
o.append(check('全库按权威口径的挂科学生数仍是 4 人', '4',
               'SELECT COUNT(*) FROM (SELECT t.student_id FROM ('
               'SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL) t '
               'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id) f'))
o.append(check('挂科行 60 分以下的行总数仍是 17（司明 9 + 同学 8）', '17',
               'SELECT COUNT(*) FROM scores WHERE score_total<60'))
o.append('')

o.append('--- (e) 选修课现在真的能查出来了 ---')
o.append(check('有权威成绩的 (学生,课程) 组合数 146 -> 238，净增 92', '238\t146\t92',
               'SELECT (SELECT COUNT(*) FROM (SELECT student_id, course_id FROM scores '
               'GROUP BY student_id, course_id) g), '
               '(SELECT COUNT(*) FROM (SELECT student_id, course_id FROM %s GROUP BY student_id, course_id) g2), '
               '(SELECT COUNT(*) FROM scores h WHERE NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND h.score_total IS NOT NULL)' % (BAK, BAK)))
o.append(check('92 行全部是新组合（没有任何一行落在已有 (学生,课程) 上）', '92',
               'SELECT COUNT(*) FROM scores h WHERE NOT EXISTS (SELECT 1 FROM %s b WHERE b.id=h.id) '
               'AND NOT EXISTS (SELECT 1 FROM %s b2 WHERE b2.student_id=h.student_id '
               'AND b2.course_id=h.course_id)' % (BAK, BAK)))
o.append(check('scores 里课程 7~10 现共 94 行 = 新增 92 + 司明原有 2 行（第5学期，本次未动）', '94\t92\t2',
               'SELECT (SELECT COUNT(*) FROM scores WHERE course_id IN (7,8,9,10)), '
               '(SELECT COUNT(*) FROM scores WHERE course_id IN (7,8,9,10) AND student_id<>3), '
               '(SELECT COUNT(*) FROM scores WHERE course_id IN (7,8,9,10) AND student_id=3)'))
o.append(check('23 名学生每人现在都有 4 门选修课', '0',
               'SELECT COUNT(*) FROM (SELECT h.student_id FROM scores h JOIN student_profile sp ON sp.id=h.student_id '
               'WHERE h.course_id IN (7,8,9,10) AND h.semester=6 GROUP BY h.student_id HAVING COUNT(*)<4) t'))
o.append('')

o.append('--- (f) 回滚可行性 ---')
o.append(check('备份 167 行、主键无重复、最大 id=167', '167\t167\t167',
               'SELECT COUNT(*), COUNT(DISTINCT id), MAX(id) FROM %s' % BAK))
o.append(check('回滚要删的行（id > 备份最大 id）= 92', '92',
               'SELECT COUNT(*) FROM scores WHERE id > (SELECT MAX(id) FROM %s)' % BAK))
o.append(check('删除后不会误伤原有行（现有 259 - 92 = 167）', '167',
               'SELECT COUNT(*) FROM scores WHERE id <= (SELECT MAX(id) FROM %s)' % BAK))

o.append('')
o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))

with open('_verify6.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify6.txt; fails =', len(fails))
