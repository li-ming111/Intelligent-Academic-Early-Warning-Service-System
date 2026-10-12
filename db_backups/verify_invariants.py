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
# 2026-10-12（W39）：原 `L`（score_level 的 CASE）**已移除** —— 它唯一的用途是反查
# `score_record.score_level` 这个存储列，而那列随 score_record 一起消失；`scores` 里
# 从来没有 score_level（counselor-service 的 `ScoreMapper` 用 `ScoreCaliber.SCORE_LEVEL_CASE` 现算）。
# 留着就是一个零引用死变量。

# ⚠️ 2026-10-12（W39）：原 `PAIR`（score_record ⋈ scores）随 score_record 删表一并移除 ——
# 它唯一的作用是把两张成绩表按 (student, course, semester) 连起来逐列比对，现在没有第二张表。
# 原来 `L` 用于反查 `score_record.score_level` 这个**存储列**；那列随表消失（详见上方 L 处注释）。


# 有 warning_id 列的表（bak_ 备份表不算），用来一次性检查有没有悬空引用
WARN_REF_TABLES = ['communication_logs', 'notifications', 'score_appeals',
                   'warning_release_records', 'warning_tracking_records']

# (分组, 说明, 期望值, SQL)   —— SQL 一律返回"违例数"，期望 '0'
CHECKS = []


def c(group, label, sql, expect='0'):
    CHECKS.append((group, label, expect, sql))


# ---------- A. 成绩口径自洽（2026-10-12 W39 起只剩 scores 一张权威表） ----------
#
# 背景：W36（教师端）与 W38（管理员导入）把成绩链路全量迁到 `scores` 之后，`score_record`
# 在 2026-10-12 被整表删除。因此原来的 4 条「双表逐列对齐」断言随之失去对象，改为直接校验
# `scores` 自身：**外部权威（学生档案里的学号）推出的学年必须等于库里存的学年**、
# **counselor-service 运行期派生的 score_level 必须等于规则式**。
# 这不是放水 —— 原来那 4 条也是在守"两表一致"，而两表里只有一张是真源；真源自身的自洽，
# 下面这两条才是唯一守着它的地方（W39 之前**没有任何一条**在守 score_level 与派生规则的一致）。
g = 'A. 成绩口径自洽（scores 是唯一权威表）'
c(g, 'scores 里**已出期末分**的行，grade / grade_point 必须符合换算规则（按 score_total 现算，不是抄别的表）',
  'SELECT COUNT(*) FROM scores h WHERE h.final_score IS NOT NULL '
  'AND (h.grade <> ' + G + ' OR NOT (h.grade_point <=> ' + P + '))')
c(g, 'scores 里**未出期末分**的行，grade / grade_point 必须为空（不许提前给等级）',
  'SELECT COUNT(*) FROM scores h WHERE h.final_score IS NULL '
  'AND (h.grade IS NOT NULL OR h.grade_point IS NOT NULL)')
c(g, '学年 = 学号前四位 + (学期-1) DIV 2（学号是外部权威，学年是派生列）',
  'SELECT COUNT(*) FROM scores h JOIN student_profile sp ON sp.id=h.student_id '
  'WHERE h.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (CAST(h.semester AS UNSIGNED)-1) DIV 2')
c(g, 'scores 不许有 NULL 学期/学年',
  'SELECT SUM(semester IS NULL)+SUM(year IS NULL) FROM scores')
# 下面三条是 W39 删表时**补上的**：`scores` 至此成为唯一权威成绩表，而 D 段一直只守
# warning / class / student 的悬空引用，**从没有一条守着 scores 自己的引用与值域**。
c(g, 'scores.student_id / course_id 都不悬空（唯一权威成绩表自己的引用完整性）',
  'SELECT (SELECT COUNT(*) FROM scores s LEFT JOIN student_profile sp ON sp.id=s.student_id WHERE sp.id IS NULL) '
  '+ (SELECT COUNT(*) FROM scores s LEFT JOIN courses c ON c.id=s.course_id WHERE c.id IS NULL)')
c(g, '三个分数列（score_total / regular_score / final_score）都在 0..100 内',
  'SELECT COUNT(*) FROM scores WHERE score_total < 0 OR score_total > 100 '
  'OR (regular_score IS NOT NULL AND (regular_score < 0 OR regular_score > 100)) '
  'OR (final_score IS NOT NULL AND (final_score < 0 OR final_score > 100))')
c(g, '(student_id, course_id, semester) 不许重复 —— 重复即"同一门课同一学期两条成绩"',
  'SELECT COUNT(*) FROM (SELECT student_id, course_id, semester FROM scores '
  'GROUP BY student_id, course_id, semester HAVING COUNT(*) > 1) x')

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
c(g, 'courses / course_plans 的 teacher_id 都不悬空',
  'SELECT (SELECT COUNT(*) FROM courses x LEFT JOIN users u ON u.id=x.teacher_id WHERE x.teacher_id IS NOT NULL AND u.id IS NULL) '
  '+ (SELECT COUNT(*) FROM course_plans x LEFT JOIN users u ON u.id=x.teacher_id WHERE x.teacher_id IS NOT NULL AND u.id IS NULL)')
c(g, 'course_plans 的教师 == 该门课的授课教师（score_record 已删，其 teacher_id 一致性改由 courses 单侧守）',
  'SELECT COUNT(*) FROM course_plans p JOIN courses c ON c.id=p.course_id '
  'WHERE NOT (p.teacher_id <=> c.teacher_id)')
c(g, 'academic_warning.student_id 都指向存在的学生档案',
  'SELECT COUNT(*) FROM academic_warning w LEFT JOIN student_profile sp ON sp.id=w.student_id WHERE sp.id IS NULL')
# 2026-10-03（W8.3）：这两列此前全为 NULL，所以从来没人守。补上 157/152 之后它才有意义 ——
# 一行申诉指向的成绩，必须**同一个学生、同一门课**；只查"悬空"不够（指错人也照样是个存在的 id）。
# 用 `<=>`：`<>` 对 NULL 求值是 NULL 不是 TRUE，会让该成立的违例静默通过（见 id-spaces 记忆第 9 条）。
c(g, 'score_appeals.score_id 非空时指向存在的 scores 行，且学生/课程都一致',
  'SELECT COUNT(*) FROM score_appeals a LEFT JOIN scores s ON s.id=a.score_id '
  'WHERE a.score_id IS NOT NULL AND (s.id IS NULL '
  'OR NOT (s.student_id <=> a.student_id) OR NOT (s.course_id <=> a.course_id))')

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
c(g, '成绩行数 259（score_record 已于 2026-10-12 W39 删除，不再有第二个数）',
  'SELECT COUNT(*) FROM scores', '259')

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

# ---------- I. 角色词表：roles 与 users.role 必须对得上（2026-10-01 第二十四轮立的） ----------
#
# 背景：`users.role` 是**固定枚举 1..5**（网关 GatewayAuthFilter.roleMapping 只认这五个，
# 其余一律落到 ROLE_USER；前端各处 roleMap 也只认这五个），而 `roles` 表是这套枚举的
# "展示/绑定副本"。两者一旦对不上，症状是**静默的**：
#   · 2026-10-01 实测：活库 `roles` 只有 1–4 行，`users.role=5` 却有 2 个真实账号
#     （collegeadmin1/2）⇒ 学院管理员的角色名、权限在页面上全查不到，
#     而仓库根的种子 academic_warning_system.sql:1432-1437 明明写了 5 行。
#   这一条就是"照种子重建 == 现在的活库"的那一半，也是唯一能自动发现该漂移的地方。
g = 'I. 角色词表（roles 表必须覆盖 users.role 用到的每个取值）'
c(g, '没有账号指向一个 roles 里不存在的角色（悬空的 users.role）',
  'SELECT COUNT(*) FROM (SELECT DISTINCT role FROM users WHERE role IS NOT NULL) u '
  'LEFT JOIN roles r ON r.id = u.role WHERE r.id IS NULL')
c(g, '五个内置角色 1–5 必须都在（缺一个就说明 roles 与 users.role 的词表不对齐了）',
  'SELECT 5 - COUNT(*) FROM roles WHERE id BETWEEN 1 AND 5')
# 为什么第 3 条必要：上面两条只看「roles 这一侧」，一个绑定都没有的角色照样能过。
# 而 2026-10-01 之前活了很久的状态正是 "roles 1–4 全在、但第 5 个角色根本不存在于 roles"，
# 也顺带说明这套绑定是可以整片缺失而无人报错的 —— 页面上表现为「权限数 0」，
# 与"这个角色确实没有权限"长得一模一样。所以这条守的是"别静默变空"。
c(g, '五个内置角色每个都至少绑了一条权限（绑定整片缺失时页面显示的「权限数 0」和真的一样）',
  'SELECT COUNT(*) FROM roles r WHERE r.id BETWEEN 1 AND 5 '
  'AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id)')


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
