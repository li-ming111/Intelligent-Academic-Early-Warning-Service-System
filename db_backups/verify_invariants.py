# -*- coding: utf-8 -*-
"""不变量校验：任何时候跑都应该全绿。

和 verify_fix5..fix11.py 的分工：
  verify_fixNN.py     断言"那一批修复**当时**确实落地了" —— 写死预期值 + MD5 终态指纹。
                      它们是**一次性证据**，后续批次有意改动数据后必然转红（例如
                      fix10 断言辅导员带 8 个班，而 2026-09-30 有意摘掉 2 个班）。
                      红 ≠ 坏了，需要人工分辨，不适合当日常体检。
  本脚本             只断言"**不管谁怎么改，这几条都必须成立**"的性质：账实相符、
                      引用不悬空、两套成绩表对齐、台账与主表归属一致。
                      它不记录任何"当前值"，所以不会因为有意改动而过期。

约定：每条断言都是"违例计数"，期望 0（少数是期望的具体形态，见各条注释）。
退出码：有 FAIL 则 1，否则 0。
"""
import subprocess
import sys

DB = 'academic_warning_system'

# 权威口径的换算（与 ScoreSyncSupport / fix10 的 G/P/L 同源），用于反查冗余列是否自洽
G = ("CASE WHEN h.score_total>=90 THEN 'A' WHEN h.score_total>=80 THEN 'A-' "
     "WHEN h.score_total>=70 THEN 'B' WHEN h.score_total>=60 THEN 'C' ELSE 'D' END")
P = ("CASE WHEN h.score_total>=90 THEN 4.0 WHEN h.score_total>=85 THEN 3.7 WHEN h.score_total>=82 THEN 3.3 "
     "WHEN h.score_total>=78 THEN 3.0 WHEN h.score_total>=75 THEN 2.7 WHEN h.score_total>=72 THEN 2.3 "
     "WHEN h.score_total>=68 THEN 2.0 WHEN h.score_total>=64 THEN 1.5 WHEN h.score_total>=60 THEN 1.0 ELSE 0 END")
L = ("CASE WHEN h.score_total>=90 THEN 1 WHEN h.score_total>=80 THEN 2 "
     "WHEN h.score_total>=70 THEN 3 WHEN h.score_total>=60 THEN 4 ELSE 5 END")

PAIR = ("score_record sr JOIN scores h ON h.student_id=sr.student_id AND h.course_id=sr.course_id "
        "AND h.semester=CAST(sr.semester AS UNSIGNED)")

# 有 warning_id 列的表（bak_ 备份表不算），用来一次性检查有没有悬空引用
WARN_REF_TABLES = ['communication_logs', 'notifications', 'score_appeals',
                   'warning_release_records', 'warning_tracking_records']

# (分组, 说明, 期望值, SQL)   —— SQL 一律返回"违例数"，期望 '0'
CHECKS = []


def c(group, label, sql, expect='0'):
    CHECKS.append((group, label, expect, sql))


# ---------- A. 两套成绩表必须对齐（scores 是权威源） ----------
g = 'A. 成绩双表对齐（scores 权威 -> score_record）'
c(g, '每行 score_record 都能在 scores 里找到对应学期的那条',
  'SELECT COUNT(*) FROM score_record sr LEFT JOIN scores h '
  'ON h.student_id=sr.student_id AND h.course_id=sr.course_id AND h.semester=CAST(sr.semester AS UNSIGNED) '
  'WHERE h.id IS NULL')
c(g, 'score / score_total / 平时 / 期末 与权威行逐字相同',
  'SELECT COUNT(*) FROM ' + PAIR + ' WHERE sr.score <> h.score_total OR sr.score_total <> h.score_total '
  'OR NOT (sr.regular_score <=> h.regular_score) OR NOT (sr.final_score <=> h.final_score)')
c(g, 'grade / grade_point 与权威行逐字相同',
  'SELECT COUNT(*) FROM ' + PAIR + ' WHERE NOT (sr.grade <=> h.grade) OR NOT (sr.grade_point <=> h.grade_point)')
c(g, 'score_level 与 ScoreSyncSupport 的口径一致',
  'SELECT COUNT(*) FROM score_record sr WHERE sr.score_level <> ' + L.replace('h.', 'sr.'))
c(g, 'scores 里**已出期末分**的行，grade / grade_point 必须符合换算规则（不是只对上 score_record）',
  'SELECT COUNT(*) FROM scores h WHERE h.final_score IS NOT NULL '
  'AND (h.grade <> ' + G + ' OR NOT (h.grade_point <=> ' + P + '))')
c(g, 'scores 里**未出期末分**的行，grade / grade_point 必须为空（不许提前给等级）',
  'SELECT COUNT(*) FROM scores h WHERE h.final_score IS NULL '
  'AND (h.grade IS NOT NULL OR h.grade_point IS NOT NULL)')
c(g, '学年 = 学号前四位 + (学期-1) DIV 2（两表都要满足）',
  'SELECT (SELECT COUNT(*) FROM score_record sr JOIN student_profile sp ON sp.id=sr.student_id '
  'WHERE sr.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (CAST(sr.semester AS UNSIGNED)-1) DIV 2) '
  '+ (SELECT COUNT(*) FROM scores h JOIN student_profile sp ON sp.id=h.student_id '
  'WHERE h.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (CAST(h.semester AS UNSIGNED)-1) DIV 2)')
c(g, '两表都不许有 NULL 学期/学年',
  'SELECT (SELECT SUM(semester IS NULL)+SUM(year IS NULL) FROM scores) '
  '+ (SELECT SUM(semester IS NULL)+SUM(year IS NULL) FROM score_record)')

# ---------- B. 冗余计数列必须等于实际（fix10 A13/A14 立的标准） ----------
# ⚠️ 2026-10-01：`colleges.student_count` / `colleges.teacher_count` 两项**已移除**（40 → 38）。
#    那两列已从库里删掉。移除的理由不是"校验太严"，而是**那两列本身没有存在理由**：
#      - Java 侧从来没有读者 —— `College.studentCount/teacherCount` 本就是
#        `@TableField(exist = false)`（只算不存），展示用的数字一律 `COUNT(*)` 现算；
#      - 唯一的写入方是自助注册路径的 `+1`。管理员建号、删号、批量导入、
#        改学生学院……**没有一条会动它** ⇒ 删一个学生这两列就永久偏大。
#    也就是说，这两项校验守的是一个"只被一条路径碰过、随时会漂移"的列 ——
#    与其补全所有写路径，不如把列删掉，让"账实相符"由"根本没有冗余列"来保证。
#    注意：`<>` 对 NULL 是不成立的（NULL <> 24 结果是 NULL 不是 TRUE），
#    所以若那两列哪天变成 NULL，这两项会**空转通过** —— 这也是当初该警觉的信号。
g = 'B. 账实相符（冗余计数列 vs 实际档案）'
c(g, 'counselor_profiles.class_count == classes 里挂在他名下的班数',
  'SELECT COUNT(*) FROM counselor_profiles cp WHERE cp.class_count <> '
  '(SELECT COUNT(*) FROM classes c WHERE c.counselor_id=cp.user_id)')
c(g, 'counselor_profiles.student_count == 他名下班级里的学生数',
  'SELECT COUNT(*) FROM counselor_profiles cp WHERE cp.student_count <> '
  '(SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id=sp.class_id WHERE c.counselor_id=cp.user_id)')

# ---------- C. 班级/专业/学院的归属不能自相矛盾 ----------
g = 'C. 班级归属自洽'
c(g, '班名里的专业 == major_id 指向的专业名（计科/计算机→计算机科学与技术，软工/软件工程→软件工程，人工智能→人工智能）',
  'SELECT COUNT(*) FROM classes c JOIN majors m ON m.id=c.major_id '
  'WHERE (c.name LIKE "计科%" AND m.name <> "计算机科学与技术") '
  'OR (c.name LIKE "计算机%" AND m.name <> "计算机科学与技术") '
  'OR (c.name LIKE "软工%" AND m.name <> "软件工程") '
  'OR (c.name LIKE "软件工程%" AND m.name <> "软件工程") '
  'OR (c.name LIKE "人工智能%" AND m.name <> "人工智能")')
c(g, '班级所在学院 == 该专业所属学院',
  'SELECT COUNT(*) FROM classes c JOIN majors m ON m.id=c.major_id WHERE m.college_id <> c.college_id')
c(g, '学生档案的 class_name 与 class_id 指向的班名一致',
  'SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id=sp.class_id '
  'WHERE NOT (sp.class_name <=> c.name)')
c(g, '学生档案的 major_id/college_id 与他所在班的专业/学院一致',
  'SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id=sp.class_id '
  'WHERE NOT (sp.major_id <=> c.major_id) OR NOT (sp.college_id <=> c.college_id)')
c(g, '同一学院内没有同名专业',
  'SELECT COUNT(*) FROM majors m WHERE EXISTS (SELECT 1 FROM majors o '
  'WHERE o.name=m.name AND o.college_id=m.college_id AND o.id<>m.id)')
c(g, '没有班级/学生挂在已不存在的专业上',
  'SELECT (SELECT COUNT(*) FROM classes WHERE major_id IS NOT NULL AND major_id NOT IN (SELECT id FROM majors)) '
  '+ (SELECT COUNT(*) FROM student_profile WHERE major_id IS NOT NULL AND major_id NOT IN (SELECT id FROM majors))')

# ---------- D. 引用完整性：不许有悬空外键 ----------
g = 'D. 引用完整性（无悬空引用）'
for t in WARN_REF_TABLES:
    c(g, '%s.warning_id 都能在 academic_warning 里找到' % t,
      'SELECT COUNT(*) FROM %s x LEFT JOIN academic_warning w ON w.id=x.warning_id '
      'WHERE x.warning_id IS NOT NULL AND w.id IS NULL' % t)
c(g, 'classes.counselor_id / teacher_id 都指向存在的用户',
  'SELECT COUNT(*) FROM classes c LEFT JOIN users u ON u.id=c.counselor_id '
  'WHERE c.counselor_id IS NOT NULL AND u.id IS NULL')
c(g, 'classes.teacher_id 都指向存在的用户',
  'SELECT COUNT(*) FROM classes c LEFT JOIN users u ON u.id=c.teacher_id '
  'WHERE c.teacher_id IS NOT NULL AND u.id IS NULL')
c(g, 'student_profile.class_id 都指向存在的班级',
  'SELECT COUNT(*) FROM student_profile sp LEFT JOIN classes c ON c.id=sp.class_id '
  'WHERE sp.class_id IS NOT NULL AND c.id IS NULL')
c(g, 'courses / score_record / course_plans 的 teacher_id 都不悬空',
  'SELECT (SELECT COUNT(*) FROM courses x LEFT JOIN users u ON u.id=x.teacher_id WHERE x.teacher_id IS NOT NULL AND u.id IS NULL) '
  '+ (SELECT COUNT(*) FROM score_record x LEFT JOIN users u ON u.id=x.teacher_id WHERE x.teacher_id IS NOT NULL AND u.id IS NULL) '
  '+ (SELECT COUNT(*) FROM course_plans x LEFT JOIN users u ON u.id=x.teacher_id WHERE x.teacher_id IS NOT NULL AND u.id IS NULL)')
c(g, '成绩记录里的教师 == 该门课的授课教师（三张表同一个口径）',
  'SELECT (SELECT COUNT(*) FROM score_record sr JOIN courses c ON c.id=sr.course_id '
  'WHERE NOT (sr.teacher_id <=> c.teacher_id)) '
  '+ (SELECT COUNT(*) FROM course_plans p JOIN courses c ON c.id=p.course_id '
  'WHERE NOT (p.teacher_id <=> c.teacher_id))')
c(g, 'academic_warning.student_id 都指向存在的学生档案',
  'SELECT COUNT(*) FROM academic_warning w LEFT JOIN student_profile sp ON sp.id=w.student_id WHERE sp.id IS NULL')

# ---------- E. 申请台账与主表的归属必须一致 ----------
g = 'E. 申请台账 vs 主表归属'
c(g, 'counselor_class_management 每行都与 classes.counselor_id / 班名一致（影子表不许和真源打架）',
  'SELECT COUNT(*) FROM counselor_class_management m LEFT JOIN classes c ON c.id=m.class_id '
  'WHERE c.id IS NULL OR NOT (c.counselor_id <=> m.counselor_id) OR NOT (m.class_name <=> c.name)')
c(g, '辅导员侧：每个班"最后一条已通过申请"推出的辅导员 == classes.counselor_id'
  '（已通过的**解除**申请推出的是"无人管理"）',
  'SELECT COUNT(*) FROM (SELECT a.class_id, IF(a.application_type=1, NULL, a.counselor_id) AS eff '
  'FROM counselor_class_application a WHERE a.status=1 '
  'AND NOT EXISTS (SELECT 1 FROM counselor_class_application b WHERE b.status=1 AND b.class_id=a.class_id '
  'AND (b.create_time > a.create_time OR (b.create_time = a.create_time AND b.id > a.id)))) e '
  'JOIN classes c ON c.id=e.class_id WHERE NOT (e.eff <=> c.counselor_id)')
c(g, '教师侧：每个班"最后一条已通过申请"推出的教师 == classes.teacher_id'
  '（已通过的**解除**申请推出的是"无班主任"）',
  'SELECT COUNT(*) FROM (SELECT a.class_id, IF(a.application_type=1, NULL, a.teacher_id) AS eff '
  'FROM teacher_class_application a WHERE a.status=1 '
  'AND NOT EXISTS (SELECT 1 FROM teacher_class_application b WHERE b.status=1 AND b.class_id=a.class_id '
  'AND (b.create_time > a.create_time OR (b.create_time = a.create_time AND b.id > a.id)))) e '
  'JOIN classes c ON c.id=e.class_id WHERE NOT (e.eff <=> c.teacher_id)')
c(g, '同一班同一申请类型不许有两条待审核（真的会打架的那种重复）',
  'SELECT COUNT(*) FROM (SELECT class_id, COALESCE(application_type,0) t FROM counselor_class_application '
  'WHERE status=0 GROUP BY class_id, t HAVING COUNT(*)>1) x')
c(g, '教师侧同理',
  'SELECT COUNT(*) FROM (SELECT class_id, COALESCE(application_type,0) t FROM teacher_class_application '
  'WHERE status=0 GROUP BY class_id, t HAVING COUNT(*)>1) x')

# ---------- F. 演示基线（故事性事实，改动前要想清楚） ----------
g = 'F. 演示基线（非不变量，红了先问"是不是有意改的"）'
CHECKS.append((g, '挂科口径：17 行 / 4 人 / 司明 9 行', '17\t4\t9',
               'SELECT COUNT(*), COUNT(DISTINCT student_id), SUM(student_id=3) FROM scores WHERE score_total < 60'))
CAL = ('SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
       'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL')
c(g, '司明/李爽/张健/韩坤 的挂科门数仍是 3,3,3,2（权威口径）',
  'SELECT GROUP_CONCAT(f.c) FROM (SELECT COUNT(*) c FROM (' + CAL + ') t '
  'WHERE t.rn=1 AND t.score_total<60 GROUP BY t.student_id ORDER BY c DESC, t.student_id) f',
  '3,3,3,2')
c(g, '两表行数 259 / 246',
  'SELECT (SELECT COUNT(*) FROM scores), (SELECT COUNT(*) FROM score_record)', '259\t246')

# ---------- G. 预警判重口径（2026-09-30 修"已处理的预警被重扫复活"时立的） ----------
# 判重键是 (student_id, fail_count)，不看 status。下面两条直接守着这个口径：
# 第 1 条保证键的另一半没缺，第 2 条保证键没有被重复插过。
g = 'G. 预警判重口径（student_id + fail_count，不看 status）'
c(g, '每行 academic_warning 都有 fail_count（NULL 就比不出"是不是同一个问题"）',
  'SELECT COUNT(*) FROM academic_warning WHERE fail_count IS NULL')
c(g, '判重键 (student_id, fail_count) 不许重复 —— 重复即判重被绕过（一人挂两条）',
  'SELECT COUNT(*) FROM (SELECT student_id, fail_count FROM academic_warning '
  'GROUP BY student_id, fail_count HAVING COUNT(*) > 1) x')

# ---------- H. 演示基线之二（档位；规则改了就要一起改） ----------
g = 'H. 预警档位与门数（基线：1-2 门→1 级、3-5 门→2 级、>5 门→3 级）'
c(g, '每行 fail_count 都落在它 warning_level 对应的档里',
  'SELECT COUNT(*) FROM academic_warning WHERE warning_level IS NULL OR fail_count IS NULL '
  'OR NOT ((warning_level=1 AND fail_count BETWEEN 1 AND 2) '
  'OR (warning_level=2 AND fail_count BETWEEN 3 AND 5) '
  'OR (warning_level=3 AND fail_count > 5))')


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace').replace('\r\n', '\n').strip()
    if r.returncode != 0:
        return '[SQLERR] ' + r.stderr.decode('utf-8', 'replace').strip()
    return out


def main():
    lines = ['========== 不变量校验（任何时候都应全绿）==========', '']
    fails = []
    group = None
    for gname, label, expect, sql in CHECKS:
        if gname != group:
            group = gname
            lines.append('--- %s ---' % gname)
        got = q(sql)
        ok = (got == expect)
        if not ok:
            fails.append(label)
        lines.append('%s %s' % ('[PASS]' if ok else '[FAIL]', label))
        if not ok:
            lines.append('    期望: %s' % (expect or '(空)'))
            lines.append('    实际: %s' % (got or '(空)'))
    lines.append('')
    lines.append('========== 汇总 ==========')
    lines.append('通过 %d 项，失败 %d 项' % (len(CHECKS) - len(fails), len(fails)))
    if fails:
        lines.append('')
        lines.append('失败项：')
        for f in fails:
            lines.append('  - %s' % f)
    text = '\n'.join(lines)
    with open('_verify_invariants.txt', 'w', encoding='utf-8') as fh:
        fh.write(text)
    print(text)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
