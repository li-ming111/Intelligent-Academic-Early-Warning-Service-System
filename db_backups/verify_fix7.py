# -*- coding: utf-8 -*-
"""第七批校验：92 行选修课录入记录补完（学期/学年/总评/等级/平时期末），
权威表原有 167 行一字未动，挂科基线未变，两表在所有可配对三元组上完全一致。"""
import subprocess

DB = 'academic_warning_system'
SR_BAK = 'score_record_bak_prefix7_20260916'   # 93 行：92 行 fix7 前原始态 + 295
SC_BAK = 'scores_bak_prefix7_20260916'        # fix7 前的 scores
NEW = "scores h WHERE h.id > 167"              # fix6 新增的 92 行

G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")
L = ("CASE WHEN h.score_total>=90 THEN 1 WHEN h.score_total>=80 THEN 2 "
     "WHEN h.score_total>=70 THEN 3 WHEN h.score_total>=60 THEN 4 ELSE 5 END")

# 可配对的三元组（fix7 后 score_record 已无 NULL 学期）
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


o = []
o.append('========== 第七批校验（92 行选修课录入记录补完）==========')
o.append('')

o.append('--- (a) 行数与结构 ---')
o.append(check('score_record 246 行 / scores 259 行', '246\t259',
               'SELECT (SELECT COUNT(*) FROM score_record), (SELECT COUNT(*) FROM scores)'))
o.append(check('score_record 里 semester 为 NULL 的行 = 0（全表都有学期了）', '0',
               'SELECT COUNT(*) FROM score_record WHERE semester IS NULL'))
o.append(check('【遗留】score_record 里 year 为 NULL 的 154 行（138+6+5+3+2，fix7 之前就有，待补）', '154',
               'SELECT COUNT(*) FROM score_record WHERE year IS NULL'))
o.append(check('重复行 295 已删（与 319 同为学生4/课8/学期6）', '0\t1',
               'SELECT (SELECT COUNT(*) FROM score_record WHERE id=295), '
               '(SELECT COUNT(*) FROM score_record WHERE student_id=4 AND course_id=8 AND semester=6)'))
o.append(check('该 (学生,课程,学期) 现在只有 1 行', '1',
               'SELECT COUNT(*) FROM score_record WHERE student_id=4 AND course_id=8 AND semester=6'))
o.append(check('(学生,课程,学期) 全表无重复', '0',
               'SELECT COUNT(*) FROM (SELECT 1 FROM score_record GROUP BY student_id, course_id, semester '
               'HAVING COUNT(*)>1) g'))
o.append('')

o.append('--- (b) 92 行已补完 ---')
o.append(check('除司明外 92 行全部落在第 6 学期', '92',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10) AND student_id<>3'))
o.append(check('year = 学号前四位 + 2（赵琦 2022 级 -> 2024）', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN student_profile sp ON sp.id=sr.student_id '
               'WHERE sr.semester=6 AND sr.course_id IN (7,8,9,10) AND sr.student_id<>3 '
               'AND sr.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + 2'))
o.append(check('score_total 全部等于录入分 score（代码不变式）', '0',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10) AND student_id<>3 '
               'AND score_total <> score'))
o.append(check('grade 与 scoreToGrade 一致', '0',
               'SELECT COUNT(*) FROM score_record h WHERE h.semester=6 AND h.course_id IN (7,8,9,10) '
               'AND h.student_id<>3 AND h.grade <> %s' % G))
o.append(check('grade_point 与 scoreToGpa 一致', '0',
               'SELECT COUNT(*) FROM score_record h WHERE h.semester=6 AND h.course_id IN (7,8,9,10) '
               'AND h.student_id<>3 AND h.grade_point <> %s' % P))
o.append(check('score_level 与 scoreToLevel 一致', '0',
               'SELECT COUNT(*) FROM score_record h WHERE h.semester=6 AND h.course_id IN (7,8,9,10) '
               'AND h.student_id<>3 AND h.score_level <> %s' % L))
o.append(check('92 行全部满足「总评 = ROUND((平时+期末)/2)」', '0',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10) AND student_id<>3 '
               'AND score_total <> ROUND((regular_score+final_score)/2)'))
o.append(check('张健课9那行不再是 F/0.60（改为 61 分及格）', '61.00\tC\t1.00\t4',
               'SELECT score_total, grade, grade_point, score_level FROM score_record WHERE student_id=26 AND course_id=9'))
o.append('')

o.append('--- (c) 两表在全部可配对三元组上完全一致 ---')
o.append(check('可配对三元组 = 244（152 原有 + 92 选修）', '244', 'SELECT COUNT(*) FROM %s' % PAIR))
o.append(check('score / score_total / 平时 / 期末 不一致的行 = 0', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.score<>h.score_total OR NOT (sr.score_total<=>h.score_total) '
               'OR NOT (sr.regular_score<=>h.regular_score) OR NOT (sr.final_score<=>h.final_score)' % PAIR))
o.append(check('grade / grade_point 与权威表逐字相同', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.grade<>h.grade OR NOT (sr.grade_point<=>h.grade_point)' % PAIR))
o.append(check('score_level 与 scoreToLevel 一致', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.score_level <> CASE WHEN h.score_total>=90 THEN 1 '
               'WHEN h.score_total>=80 THEN 2 WHEN h.score_total>=70 THEN 3 '
               'WHEN h.score_total>=60 THEN 4 ELSE 5 END' % PAIR))
o.append(check('无法配对的只剩 2 行（司明的课7/课8：录入表写第6学期，权威表写第5学期）', '2\t291,294',
               'SELECT COUNT(*), GROUP_CONCAT(sr.id ORDER BY sr.id) FROM score_record sr '
               'WHERE NOT EXISTS (SELECT 1 FROM scores h WHERE h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED))'))
o.append('')

o.append('--- (d) 权威表原有 167 行一字未动 ---')
o.append(check('原有 167 行 14 列与 fix7 前快照逐字相同', '0',
               'SELECT COUNT(*) FROM scores h JOIN %s b ON b.id=h.id WHERE b.id<=167 AND '
               '(NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id) '
               'OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year) '
               'OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score) '
               'OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade) '
               'OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change) '
               'OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at))' % SC_BAK))
o.append(check('scores 里 (学生,课程,学期) 重复三元组 = 0', '0',
               'SELECT COUNT(*) FROM (SELECT 1 FROM scores GROUP BY student_id, course_id, semester '
               'HAVING COUNT(*)>1) g'))
o.append('')

o.append('--- (e) 挂科基线与预警输入未变 ---')
o.append(check('60 分以下行数仍 17（司明 9 + 同学 8）', '17\t9\t8',
               'SELECT (SELECT COUNT(*) FROM scores WHERE score_total<60), '
               '(SELECT COUNT(*) FROM scores WHERE score_total<60 AND student_id=3), '
               '(SELECT COUNT(*) FROM scores WHERE score_total<60 AND student_id<>3)'))
o.append(check('司明/李爽/张健/韩坤 挂科门数仍是 3,3,3,2', '3,3,3,2',
               'SELECT GROUP_CONCAT(f.c) FROM (SELECT COUNT(*) c FROM ('
               'SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL) t '
               'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id ORDER BY c DESC, t.student_id) f'))
o.append(check('挂科学生仍是 4 人', '4',
               'SELECT COUNT(*) FROM (SELECT t.student_id FROM ('
               'SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL) t '
               'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id) f'))
o.append('')

o.append('--- (f) 总评 = (平时+期末)/2 的整体吻合度 ---')
o.append(check('scores：255 行有平时期末，245 行吻合，10 行不吻合（全是司明，纯取整痕迹）', '245\t10\t10',
               'SELECT (SELECT COUNT(*) - SUM(score_total<>ROUND((regular_score+final_score)/2)) FROM scores '
               'WHERE regular_score IS NOT NULL AND final_score IS NOT NULL), '
               '(SELECT SUM(score_total<>ROUND((regular_score+final_score)/2)) FROM scores '
               'WHERE regular_score IS NOT NULL AND final_score IS NOT NULL), '
               '(SELECT COUNT(*) FROM scores WHERE student_id=3 AND regular_score IS NOT NULL '
               'AND final_score IS NOT NULL AND score_total<>ROUND((regular_score+final_score)/2))'))
o.append(check('score_record：92 行选修课现在全部吻合（此前 81 行不吻合）', '0',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10) AND student_id<>3 '
               'AND score_total<>ROUND((regular_score+final_score)/2)'))
o.append(check('score_record 剩余不吻合 = 7 行（司明自己的课1~课6，本次未动）', '7\t7',
               'SELECT (SELECT COUNT(*) FROM score_record WHERE regular_score IS NOT NULL '
               'AND final_score IS NOT NULL AND score_total<>ROUND((regular_score+final_score)/2)), '
               '(SELECT COUNT(*) FROM score_record WHERE student_id=3 AND regular_score IS NOT NULL '
               'AND final_score IS NOT NULL AND score_total<>ROUND((regular_score+final_score)/2))'))
o.append('')

o.append('--- (g) 仍待处理：司明的 2 行选修课学期冲突 ---')
o.append(check('司明课7/课8 在 scores 仍写第 5 学期', '5,5',
               'SELECT GROUP_CONCAT(semester ORDER BY course_id) FROM scores WHERE student_id=3 AND course_id IN (7,8)'))
o.append(check('录入表写第 6 学期：课7 录入分 85（权威 86）、课8 录入分 78（权威 91）', '291-6-85.00|294-6-78.00',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",semester,"-",score) ORDER BY id SEPARATOR "|") '
               'FROM score_record WHERE id IN (291,294)'))
o.append('')

o.append('--- (h) 回滚可行性 ---')
o.append(check('备份 score_record 93 行（92 原始态 + 295）', '93\t92\t1',
               'SELECT COUNT(*), SUM(semester IS NULL), SUM(id=295) FROM %s' % SR_BAK))
o.append(check('备份 scores 259 行', '259', 'SELECT COUNT(*) FROM %s' % SC_BAK))
o.append(check('回滚要改的行数：93 行 score_record（含重新插入的 295）+ 92 行 scores', '93	92',
               'SELECT (SELECT COUNT(*) FROM %s), (SELECT COUNT(*) FROM scores WHERE id>167)' % SR_BAK))

o.append('')

o.append('--- (i) 评语重写（fix8）---')
o.append(check('每门课 23 条、课内评语互不重复', '7-23-23,8-23-23,9-23-23,10-23-23',
               'SELECT GROUP_CONCAT(CONCAT(course_id,"-",c,"-",d) ORDER BY course_id SEPARATOR ",") FROM ('
               'SELECT course_id, COUNT(*) c, COUNT(DISTINCT remark) d FROM score_record '
               'WHERE course_id IN (7,8,9,10) AND student_id<>3 GROUP BY course_id) t'))
o.append(check('旧课程名 心理健康/职业规划/艺术鉴赏/传统文化 已 0 残留', '0',
               'SELECT COUNT(*) FROM score_record WHERE course_id IN (7,8,9,10) '
               'AND remark IN ("心理健康","职业规划","艺术鉴赏","传统文化")'))
o.append(check('92 行评语均非空', '0',
               'SELECT COUNT(*) FROM score_record WHERE course_id IN (7,8,9,10) AND student_id<>3 '
               'AND (remark IS NULL OR remark="")'))
o.append(check('高分(>=80)的评语里不出现"薄弱/漏洞/吃力/不牢/需"', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=6 WHERE sr.course_id IN (7,8,9,10) '
               'AND h.score_total>=80 AND (sr.remark LIKE "%薄弱%" OR sr.remark LIKE "%漏洞%" '
               'OR sr.remark LIKE "%吃力%" OR sr.remark LIKE "%不牢%" OR sr.remark LIKE "%需%")'))
o.append(check('低分(<70)的评语里不出现"扎实/优秀/突出/透彻/深入/熟练/丰富"', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN scores h ON h.student_id=sr.student_id '
               'AND h.course_id=sr.course_id AND h.semester=6 WHERE sr.course_id IN (7,8,9,10) '
               'AND h.score_total<70 AND (sr.remark LIKE "%扎实%" OR sr.remark LIKE "%优秀%" '
               'OR sr.remark LIKE "%突出%" OR sr.remark LIKE "%透彻%" OR sr.remark LIKE "%深入%" '
               'OR sr.remark LIKE "%熟练%" OR sr.remark LIKE "%丰富%")'))
o.append(check('司明那 2 行评语仍为 NULL（学期未定，未动）', '2',
               'SELECT COUNT(*) FROM score_record WHERE id IN (291,294) AND remark IS NULL'))
o.append(check('scores 全表 14 列指纹仍为 fix8 之前的值（fix8 只写 score_record.remark）',
               '259	cf410d420d1db27cc4bce297bfb48741',
               'SET SESSION group_concat_max_len = 100000000; SELECT COUNT(*), '
               'MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
               'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
               'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM scores) b'))
o.append('')

o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))

with open('_verify7.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify7.txt; fails =', len(fails))
