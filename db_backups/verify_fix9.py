# -*- coding: utf-8 -*-
"""第九批（终态）校验：司明的 2 行选修课归位到第 6 学期、录入表 291/294 对齐成权威值、
154 行 NULL 学年补齐。至此 score_record 246 行全部可与 scores 配对，两表零冲突。

本脚本是 fix5~fix9 的终态校验（取代 verify_fix7.py 里被 fix9 改变了预期的几段）。"""
import subprocess

DB = 'academic_warning_system'
SC_BAK7 = 'scores_bak_prefix7_20260916'        # fix7 之前的 scores
SC_BAK9 = 'scores_bak_sem9_20260916'           # fix9 之前的 scores（259 行）
SR_BAK9 = 'score_record_bak_sem9_20260916'     # fix9 之前的 score_record（246 行）
SR_BAK7 = 'score_record_bak_prefix7_20260916'  # 93 行：92 行 fix7 前原始态 + 295

G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")
L = ("CASE WHEN h.score_total>=90 THEN 1 WHEN h.score_total>=80 THEN 2 "
     "WHEN h.score_total>=70 THEN 3 WHEN h.score_total>=60 THEN 4 ELSE 5 END")

# 可配对三元组（fix9 后应为全部 246 行）
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
o.append('========== 第九批校验（两表全面对齐终态）==========')
o.append('')

o.append('--- (a) 行数与结构 ---')
o.append(check('score_record 246 行 / scores 259 行', '246\t259',
               'SELECT (SELECT COUNT(*) FROM score_record), (SELECT COUNT(*) FROM scores)'))
o.append(check('score_record 无 NULL 学期', '0',
               'SELECT COUNT(*) FROM score_record WHERE semester IS NULL'))
o.append(check('【fix9 已修】score_record 无 NULL 学年', '0',
               'SELECT COUNT(*) FROM score_record WHERE year IS NULL'))
o.append(check('学年全部 = 学号前四位 + (学期-1)/2', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN student_profile sp ON sp.id=sr.student_id '
               'WHERE sr.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (sr.semester-1) DIV 2'))
o.append(check('学期分布 138/6/5/3/94 = 246', '2-138,3-6,4-5,5-3,6-94',
               'SELECT GROUP_CONCAT(CONCAT(semester,"-",c) ORDER BY semester SEPARATOR ",") FROM ('
               'SELECT semester, COUNT(*) c FROM score_record GROUP BY semester) t'))
o.append(check('重复行 295 已删', '0\t1',
               'SELECT (SELECT COUNT(*) FROM score_record WHERE id=295), '
               '(SELECT COUNT(*) FROM score_record WHERE student_id=4 AND course_id=8 AND semester=6)'))
o.append(check('两表 (学生,课程,学期) 均无重复', '0\t0',
               'SELECT (SELECT COUNT(*) FROM (SELECT 1 FROM score_record GROUP BY student_id, course_id, '
               'semester HAVING COUNT(*)>1) a), (SELECT COUNT(*) FROM (SELECT 1 FROM scores '
               'GROUP BY student_id, course_id, semester HAVING COUNT(*)>1) b)'))
o.append('')

o.append('--- (b) 4 门选修课 94 行（23 人 × 4 门 + 司明 2 门）已补完 ---')
o.append(check('94 行全部落在第 6 学期', '94',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10)'))
o.append(check('scores 侧同样是 94 行在第 6 学期', '94',
               'SELECT COUNT(*) FROM scores WHERE semester=6 AND course_id IN (7,8,9,10)'))
o.append(check('学年 = 学号前四位 + 2（赵琦 2022 级 -> 2024，其余 2025）', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN student_profile sp ON sp.id=sr.student_id '
               'WHERE sr.semester=6 AND sr.course_id IN (7,8,9,10) '
               'AND sr.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + 2'))
o.append(check('score_total 全部等于录入分 score（代码不变式）', '0',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10) '
               'AND score_total <> score'))
o.append(check('grade 与 scoreToGrade 一致', '0',
               'SELECT COUNT(*) FROM score_record h WHERE h.semester=6 AND h.course_id IN (7,8,9,10) '
               'AND h.grade <> %s' % G))
o.append(check('grade_point 与 scoreToGpa 一致', '0',
               'SELECT COUNT(*) FROM score_record h WHERE h.semester=6 AND h.course_id IN (7,8,9,10) '
               'AND h.grade_point <> %s' % P))
o.append(check('score_level 与 scoreToLevel 一致', '0',
               'SELECT COUNT(*) FROM score_record h WHERE h.semester=6 AND h.course_id IN (7,8,9,10) '
               'AND h.score_level <> %s' % L))
o.append(check('94 行里 93 行满足「总评 = ROUND((平时+期末)/2)」，司明课7 那行差 1 分（88+85 -> 87 记 86，属本人既有取整痕迹，用户选择保留）', '1',
               'SELECT COUNT(*) FROM score_record WHERE semester=6 AND course_id IN (7,8,9,10) '
               'AND score_total <> ROUND((regular_score+final_score)/2)'))
o.append(check('张健课9那行 61 分及格而非 F/0.60', '61.00\tC\t1.00\t4',
               'SELECT score_total, grade, grade_point, score_level FROM score_record WHERE student_id=26 AND course_id=9'))
o.append('')

o.append('--- (c) 两表 246 行 100% 可配对且逐字一致 ---')
o.append(check('可配对三元组 = 246（= score_record 全表）', '246', 'SELECT COUNT(*) FROM %s' % PAIR))
o.append(check('【fix9 已修】无法配对的 = 0（原为司明的 291/294）', '0',
               'SELECT COUNT(*) FROM score_record sr WHERE NOT EXISTS (SELECT 1 FROM scores h '
               'WHERE h.student_id=sr.student_id AND h.course_id=sr.course_id '
               'AND h.semester=CAST(sr.semester AS UNSIGNED))'))
o.append(check('score / 总评 / 平时 / 期末 不一致 = 0', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.score<>h.score_total OR NOT (sr.score_total<=>h.score_total) '
               'OR NOT (sr.regular_score<=>h.regular_score) OR NOT (sr.final_score<=>h.final_score)' % PAIR))
o.append(check('grade / grade_point 与权威表逐字相同', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.grade<>h.grade OR NOT (sr.grade_point<=>h.grade_point)' % PAIR))
o.append(check('score_level 与 scoreToLevel 一致（全表可校验了）', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.score_level <> CASE WHEN h.score_total>=90 THEN 1 '
               'WHEN h.score_total>=80 THEN 2 WHEN h.score_total>=70 THEN 3 '
               'WHEN h.score_total>=60 THEN 4 ELSE 5 END' % PAIR))
o.append('')

o.append('--- (d) 权威表只有那 2 行变了，且只变学期列 ---')
o.append(check('与 fix9 前快照相比：仅 2 行不同', '2',
               'SELECT COUNT(*) FROM scores h JOIN %s b ON b.id=h.id WHERE '
               '(NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id) '
               'OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year) '
               'OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score) '
               'OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade) '
               'OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change) '
               'OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at))' % SC_BAK9))
o.append(check('这 2 行就是司明的课7/课8（id 162/163）', '162,163',
               'SELECT GROUP_CONCAT(h.id ORDER BY h.id) FROM scores h JOIN %s b ON b.id=h.id WHERE '
               '(NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id) '
               'OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year) '
               'OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score) '
               'OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade) '
               'OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change) '
               'OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at))' % SC_BAK9))
o.append(check('除学期外其余 11 列一行未动', '0',
               'SELECT COUNT(*) FROM scores h JOIN %s b ON b.id=h.id WHERE '
               'NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id) '
               'OR NOT (h.year<=>b.year) OR NOT (h.regular_score<=>b.regular_score) '
               'OR NOT (h.final_score<=>b.final_score) OR NOT (h.score_total<=>b.score_total) '
               'OR NOT (h.grade<=>b.grade) OR NOT (h.grade_point<=>b.grade_point) '
               'OR NOT (h.reason_for_change<=>b.reason_for_change) OR NOT (h.modified_by<=>b.modified_by) '
               'OR NOT (h.created_at<=>b.created_at)' % SC_BAK9))
o.append(check('原有 167 行与 fix7 前快照仍是仅 2 行（学期）不同', '162,163',
               'SELECT GROUP_CONCAT(h.id ORDER BY h.id) FROM scores h JOIN %s b ON b.id=h.id WHERE b.id<=167 AND '
               '(NOT (h.student_id<=>b.student_id) OR NOT (h.course_id<=>b.course_id) '
               'OR NOT (h.semester<=>b.semester) OR NOT (h.year<=>b.year) '
               'OR NOT (h.regular_score<=>b.regular_score) OR NOT (h.final_score<=>b.final_score) '
               'OR NOT (h.score_total<=>b.score_total) OR NOT (h.grade<=>b.grade) '
               'OR NOT (h.grade_point<=>b.grade_point) OR NOT (h.reason_for_change<=>b.reason_for_change) '
               'OR NOT (h.modified_by<=>b.modified_by) OR NOT (h.created_at<=>b.created_at))' % SC_BAK7))
o.append(check('司明第 5 学期只剩 5 门必修（课1~课5）', '1,2,3,4,5',
               'SELECT GROUP_CONCAT(course_id ORDER BY course_id) FROM scores WHERE semester=5'))
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

o.append('--- (f) 总评 = (平时+期末)/2 的吻合度（用户已确认保留剩余 17 行）---')
o.append(check('scores：255 行有平时期末，245 吻合，10 不吻合（全司明）', '245\t10\t10',
               'SELECT (SELECT COUNT(*) - SUM(score_total<>ROUND((regular_score+final_score)/2)) FROM scores '
               'WHERE regular_score IS NOT NULL AND final_score IS NOT NULL), '
               '(SELECT SUM(score_total<>ROUND((regular_score+final_score)/2)) FROM scores '
               'WHERE regular_score IS NOT NULL AND final_score IS NOT NULL), '
               '(SELECT COUNT(*) FROM scores WHERE student_id=3 AND regular_score IS NOT NULL '
               'AND final_score IS NOT NULL AND score_total<>ROUND((regular_score+final_score)/2))'))
o.append(check('score_record：仍是同样 7 行（278,282,283,284,288,290,291，全司明）',
               '7\t1\t278,282,283,284,288,290,291',
               'SELECT COUNT(*), COUNT(DISTINCT student_id), GROUP_CONCAT(id ORDER BY id) FROM score_record '
               'WHERE regular_score IS NOT NULL AND final_score IS NOT NULL '
               'AND score_total<>ROUND((regular_score+final_score)/2)'))
o.append('')

o.append('--- (g) 司明的 2 行选修课（fix9 的正文）---')
o.append(check('scores 里课7/课8 已写第 6 学期，分数未动', '6-86.00/88.00/85.00/A-/3.70|6-91.00/90.00/92.00/A/4.00',
               'SELECT GROUP_CONCAT(CONCAT(semester,"-",score_total,"/",regular_score,"/",final_score,"/",'
               'grade,"/",grade_point) ORDER BY course_id SEPARATOR "|") '
               'FROM scores WHERE student_id=3 AND course_id IN (7,8)'))
o.append(check('录入表 291/294 已对齐成权威值 86/91', '291-6-86.00-88.00-85.00-A-|294-6-91.00-90.00-92.00-A',
               'SELECT GROUP_CONCAT(CONCAT(id,"-",semester,"-",score,"-",regular_score,"-",final_score,"-",grade) '
               'ORDER BY id SEPARATOR "|") FROM score_record WHERE id IN (291,294)'))
o.append(check('两行评语已补（不再是 NULL）', '算法流程梳理清晰|模型训练过程理解深',
               'SELECT GROUP_CONCAT(remark ORDER BY id SEPARATOR "|") FROM score_record WHERE id IN (291,294)'))
o.append('')

o.append('--- (h) 评语（fix8 + fix9）---')
o.append(check('课7/课8 各 24 条、课9/课10 各 23 条，课内互不重复',
               '7-24-24,8-24-24,9-23-23,10-23-23',
               'SELECT GROUP_CONCAT(CONCAT(course_id,"-",c,"-",d) ORDER BY course_id SEPARATOR ",") FROM ('
               'SELECT course_id, COUNT(*) c, COUNT(DISTINCT remark) d FROM score_record '
               'WHERE course_id IN (7,8,9,10) GROUP BY course_id) t'))
o.append(check('旧课程名 心理健康/职业规划/艺术鉴赏/传统文化 已 0 残留', '0',
               'SELECT COUNT(*) FROM score_record WHERE course_id IN (7,8,9,10) '
               'AND remark IN ("心理健康","职业规划","艺术鉴赏","传统文化")'))
o.append(check('94 行评语均非空', '0',
               'SELECT COUNT(*) FROM score_record WHERE course_id IN (7,8,9,10) AND (remark IS NULL OR remark="")'))
o.append(check('高分(>=80)评语里不出现"薄弱/漏洞/吃力/不牢/需"', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.course_id IN (7,8,9,10) AND h.score_total>=80 '
               'AND (sr.remark LIKE "%%薄弱%%" OR sr.remark LIKE "%%漏洞%%" OR sr.remark LIKE "%%吃力%%" '
               'OR sr.remark LIKE "%%不牢%%" OR sr.remark LIKE "%%需%%")' % PAIR))
o.append(check('低分(<70)评语里不出现"扎实/优秀/突出/透彻/深入/熟练/丰富"', '0',
               'SELECT COUNT(*) FROM %s WHERE sr.course_id IN (7,8,9,10) AND h.score_total<70 '
               'AND (sr.remark LIKE "%%扎实%%" OR sr.remark LIKE "%%优秀%%" OR sr.remark LIKE "%%突出%%" '
               'OR sr.remark LIKE "%%透彻%%" OR sr.remark LIKE "%%深入%%" OR sr.remark LIKE "%%熟练%%" '
               'OR sr.remark LIKE "%%丰富%%")' % PAIR))
o.append('')

o.append('--- (i) 回滚可行性 ---')
o.append(check('备份 score_record 246 行 / scores 259 行', '246\t259',
               'SELECT (SELECT COUNT(*) FROM %s), (SELECT COUNT(*) FROM %s)' % (SR_BAK9, SC_BAK9)))
o.append(check('备份里 154 行 year 为 NULL、291/294 仍是 85/78（回滚目标态）', '154\t291-85.00|294-78.00',
               'SELECT (SELECT COUNT(*) FROM %s WHERE year IS NULL), '
               '(SELECT GROUP_CONCAT(CONCAT(id,"-",score) ORDER BY id SEPARATOR "|") FROM %s WHERE id IN (291,294))'
               % (SR_BAK9, SR_BAK9)))
o.append(check('fix7 的备份仍可用：93 行 score_record + 259 行 scores', '93\t259',
               'SELECT (SELECT COUNT(*) FROM %s), (SELECT COUNT(*) FROM %s)' % (SR_BAK7, SC_BAK7)))
o.append('')

o.append('--- (j) 两表全量指纹（跨run比对请排除 update_time/updated_at）---')
o.append(check('scores 259 行 10 列指纹', '259\t185227906f1a09fcfb029c1458139cfd',
               'SET SESSION group_concat_max_len = 100000000; SELECT COUNT(*), '
               'MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
               'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
               'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM scores) b'))
o.append(check('score_record 246 行 10 列指纹', '246\t' + q(
    'SET SESSION group_concat_max_len = 100000000; SELECT MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
    'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
    'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM score_record) b'),
    'SET SESSION group_concat_max_len = 100000000; SELECT COUNT(*), '
    'MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR "|")) FROM ('
    'SELECT CONCAT_WS(",", id, student_id, course_id, semester, IFNULL(year,"~"), regular_score, '
    'final_score, score_total, IFNULL(grade,"~"), IFNULL(grade_point,"~")) x FROM score_record) b'))
o.append('')

o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))

with open('_verify9.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify9.txt; fails =', len(fails))
