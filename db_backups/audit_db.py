# -*- coding: utf-8 -*-
"""全库自洽体检（只读）。

标准：演示数据必须"自洽、经得起追问"。本脚本不预设结论，只把违反不变量的行
列出来，由人判断是缺陷还是合理特例。

第一部分：引用完整性 —— 悬空外键（JOIN 不上任何主表）
第二部分：ID 空间探测 —— 每个 *._id 列到底指向哪张主表（本项目有两套 ID 空间）
第三部分：冗余名称列 —— 反范式存的名字/班级名与它引用的实体是否一致
第四部分：文字垃圾 —— 占位符、示例名、乱码
第五部分：数值域
第六部分：计数冗余列
第七部分：自然键唯一性
第八部分：时间与学年一致性
"""
import subprocess

DB = 'academic_warning_system'
OUT = []


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace').replace('\r\n', '\n')
    if r.returncode != 0:
        return '[SQLERR] ' + r.stderr.decode('utf-8', 'replace').strip().replace('\n', ' ')[:300]
    return out.strip('\n')


def head(t):
    OUT.append('')
    OUT.append('=' * 78)
    OUT.append(t)
    OUT.append('=' * 78)


def probe(label, sql, limit=8):
    """打印一行结果；违规行数 + 前若干行明细"""
    got = q(sql)
    if got.startswith('[SQLERR]'):
        OUT.append('  [!] %s -> %s' % (label, got))
        return None
    lines = [l for l in got.split('\n') if l.strip()] if got else []
    n = len(lines)
    flag = '  [ ] ' if n == 0 else '  [x] '
    OUT.append('%s%s：%d 行' % (flag, label, n))
    for l in lines[:limit]:
        OUT.append('        ' + l)
    if n > limit:
        OUT.append('        ... 另有 %d 行' % (n - limit))
    return n


# ---------------------------------------------------------------- 1. 悬空外键
head('第 1 部分：引用完整性（悬空外键：引用了不存在的主键）')

FK = [
    ('student_profile.user_id -> users.id',
     "SELECT sp.id, sp.user_id FROM student_profile sp LEFT JOIN users u ON u.id=sp.user_id WHERE u.id IS NULL"),
    ('student_profile.college_id -> colleges.id',
     "SELECT sp.id, sp.college_id FROM student_profile sp LEFT JOIN colleges c ON c.id=sp.college_id WHERE c.id IS NULL"),
    ('student_profile.major_id -> majors.id',
     "SELECT sp.id, sp.major_id FROM student_profile sp LEFT JOIN majors m ON m.id=sp.major_id WHERE m.id IS NULL"),
    ('student_profile.class_id -> classes.id',
     "SELECT sp.id, sp.class_id FROM student_profile sp LEFT JOIN classes c ON c.id=sp.class_id WHERE c.id IS NULL"),
    ('classes.college_id -> colleges.id',
     "SELECT c.id, c.college_id FROM classes c LEFT JOIN colleges x ON x.id=c.college_id WHERE x.id IS NULL"),
    ('classes.major_id -> majors.id',
     "SELECT c.id, c.major_id FROM classes c LEFT JOIN majors m ON m.id=c.major_id WHERE m.id IS NULL"),
    ('majors.college_id -> colleges.id',
     "SELECT m.id, m.college_id FROM majors m LEFT JOIN colleges c ON c.id=m.college_id WHERE c.id IS NULL"),
    ('courses.module_id -> elective_modules.id',
     "SELECT c.id, c.module_id FROM courses c LEFT JOIN elective_modules e ON e.id=c.module_id WHERE c.module_id IS NOT NULL AND e.id IS NULL"),
    ('course_plans.course_id -> courses.id',
     "SELECT p.id, p.course_id FROM course_plans p LEFT JOIN courses c ON c.id=p.course_id WHERE c.id IS NULL"),
    ('enrollments.student_id -> student_profile.id',
     "SELECT e.id, e.student_id FROM enrollments e LEFT JOIN student_profile sp ON sp.id=e.student_id WHERE sp.id IS NULL"),
    ('enrollments.course_id -> courses.id',
     "SELECT e.id, e.course_id FROM enrollments e LEFT JOIN courses c ON c.id=e.course_id WHERE c.id IS NULL"),
    ('enrollments.class_id -> classes.id',
     "SELECT e.id, e.class_id FROM enrollments e LEFT JOIN classes c ON c.id=e.class_id WHERE e.class_id IS NOT NULL AND c.id IS NULL"),
    ('scores.student_id -> student_profile.id',
     "SELECT s.id, s.student_id FROM scores s LEFT JOIN student_profile sp ON sp.id=s.student_id WHERE sp.id IS NULL"),
    ('scores.course_id -> courses.id',
     "SELECT s.id, s.course_id FROM scores s LEFT JOIN courses c ON c.id=s.course_id WHERE c.id IS NULL"),
    ('teacher_course.course_id -> courses.id',
     "SELECT t.id, t.course_id FROM teacher_course t LEFT JOIN courses c ON c.id=t.course_id WHERE c.id IS NULL"),
    ('counselor_profiles.user_id -> users.id',
     "SELECT c.id, c.user_id FROM counselor_profiles c LEFT JOIN users u ON u.id=c.user_id WHERE u.id IS NULL"),
    ('counselor_profiles.college_id -> colleges.id',
     "SELECT c.id, c.college_id FROM counselor_profiles c LEFT JOIN colleges x ON x.id=c.college_id WHERE x.id IS NULL"),
    ('teacher_profile.user_id -> users.id',
     "SELECT t.id, t.user_id FROM teacher_profile t LEFT JOIN users u ON u.id=t.user_id WHERE u.id IS NULL"),
    ('teacher_profile.college_id -> colleges.id',
     "SELECT t.id, t.college_id FROM teacher_profile t LEFT JOIN colleges c ON c.id=t.college_id WHERE c.id IS NULL"),
    # ⚠️ `admin_profile` 表**没有 college_id 列**（只有 id/user_id/name/phone/email/department），
    #    学院归属在独立的 `college_admin_profile` 表里 —— 下一行才是真正要查的。
    #    早先这里写 `admin_profile.college_id` 每跑必 `[SQLERR] Unknown column`，白扫。
    ('college_admin_profile -> colleges.id',
     "SELECT a.id, a.college_id FROM college_admin_profile a LEFT JOIN colleges c ON c.id=a.college_id WHERE a.college_id IS NOT NULL AND c.id IS NULL"),
    ('login_logs.user_id -> users.id',
     "SELECT l.id, l.user_id FROM login_logs l LEFT JOIN users u ON u.id=l.user_id WHERE u.id IS NULL"),
    ('notifications.user_id -> users.id',
     "SELECT n.id, n.user_id FROM notifications n LEFT JOIN users u ON u.id=n.user_id WHERE u.id IS NULL"),
    ('student_settings.student_id -> student_profile.id',
     "SELECT s.id, s.student_id FROM student_settings s LEFT JOIN student_profile sp ON sp.id=s.student_id WHERE sp.id IS NULL"),
    ('subscription_preferences.student_id -> student_profile.id',
     "SELECT s.id, s.student_id FROM subscription_preferences s LEFT JOIN student_profile sp ON sp.id=s.student_id WHERE sp.id IS NULL"),
    ('counselor_class_application.class_id -> classes.id',
     "SELECT a.id, a.class_id FROM counselor_class_application a LEFT JOIN classes c ON c.id=a.class_id WHERE c.id IS NULL"),
    ('teacher_class_application.class_id -> classes.id',
     "SELECT a.id, a.class_id FROM teacher_class_application a LEFT JOIN classes c ON c.id=a.class_id WHERE c.id IS NULL"),
    ('assistance_plans.student_id -> student_profile.id',
     "SELECT a.id, a.student_id FROM assistance_plans a LEFT JOIN student_profile sp ON sp.id=a.student_id WHERE sp.id IS NULL"),
]
for label, sql in FK:
    probe(label, sql)

# ------------------------------------------------------------- 2. ID 空间探测
head('第 2 部分：ID 空间探测（每个 *._id 指向哪张主表；本项目有两套不相交的 ID 空间）')

PROBE_COLS = [
    ('classes.teacher_id', 'classes', 'teacher_id'),
    ('classes.counselor_id', 'classes', 'counselor_id'),
    ('courses.teacher_id', 'courses', 'teacher_id'),
    ('course_plans.teacher_id', 'course_plans', 'teacher_id'),
    ('teacher_course.teacher_id', 'teacher_course', 'teacher_id'),
    ('communication_record.teacher_id', 'communication_record', 'teacher_id'),
    ('communication_record.student_id', 'communication_record', 'student_id'),
    ('communication_logs.counselor_id', 'communication_logs', 'counselor_id'),
    ('communication_logs.student_id', 'communication_logs', 'student_id'),
    ('communication_logs.teacher_id', 'communication_logs', 'teacher_id'),
    ('feedback.teacher_id', 'feedback', 'teacher_id'),
    ('assistance_plans.counselor_id', 'assistance_plans', 'counselor_id'),
    ('assistance_plans.student_id', 'assistance_plans', 'student_id'),
    ('counselor_class_application.counselor_id', 'counselor_class_application', 'counselor_id'),
    ('teacher_class_application.teacher_id', 'teacher_class_application', 'teacher_id'),
    ('score_audit_log.student_id', 'score_audit_log', 'student_id'),
    ('score_audit_log.teacher_id', 'score_audit_log', 'teacher_id'),
    ('export_history.exported_by', 'export_history', 'exported_by'),
]
CAND = [('users', 'id'), ('student_profile', 'id'), ('teacher_profile', 'id'),
        ('counselor_profiles', 'id'), ('admin_profile', 'id'), ('college_admin_profile', 'id')]
for label, tbl, col in PROBE_COLS:
    hits = []
    for ct, cc in CAND:
        got = q("SELECT COUNT(*) FROM %s a JOIN %s b ON b.%s=a.%s" % (tbl, ct, cc, col))
        if got.startswith('[SQLERR]'):
            continue
        try:
            n = int(got)
        except ValueError:
            continue
        if n:
            hits.append('%s=%d' % (ct, n))
    tot = q("SELECT COUNT(*), COUNT(%s) FROM %s" % (col, tbl))
    OUT.append('  %-46s 总行/非空 = %-9s 命中：%s' % (label, tot, ', '.join(hits) or '(无)'))

# --------------------------------------------------------- 3. 冗余名称列交叉核对
head('第 3 部分：反范式冗余名称列 vs 它引用的实体（名字对不对得上）')

NAME_CHECKS = [
    ('student_profile.class_name 与 classes.name 不符',
     "SELECT sp.id, sp.student_id, sp.name, sp.class_name, c.name FROM student_profile sp JOIN classes c ON c.id=sp.class_id WHERE sp.class_name <> c.name"),
    ('student_profile.name 与 users.name 不符',
     "SELECT sp.id, sp.user_id, sp.name, u.name FROM student_profile sp JOIN users u ON u.id=sp.user_id WHERE sp.name <> u.name"),
    ('counselor_class_application.class_name 与 classes.name 不符',
     "SELECT a.id, a.class_id, a.class_name, c.name FROM counselor_class_application a JOIN classes c ON c.id=a.class_id WHERE a.class_name <> c.name"),
    ('teacher_class_application.class_name 与 classes.name 不符',
     "SELECT a.id, a.class_id, a.class_name, c.name FROM teacher_class_application a JOIN classes c ON c.id=a.class_id WHERE a.class_name <> c.name"),
    ('counselor_class_application.counselor_name 与 users.name 不符（按 counselor_id 解析）',
     "SELECT a.id, a.counselor_id, a.counselor_name, u.name FROM counselor_class_application a JOIN counselor_profiles cp ON cp.id=a.counselor_id JOIN users u ON u.id=cp.user_id WHERE a.counselor_name <> u.name"),
    ('teacher_class_application.teacher_name 与 users.name 不符',
     "SELECT a.id, a.teacher_id, a.teacher_name, u.name FROM teacher_class_application a JOIN teacher_profile t ON t.id=a.teacher_id JOIN users u ON u.id=t.user_id WHERE a.teacher_name <> u.name"),
    ('score_audit_log.student_name 与 student_profile.name 不符',
     "SELECT l.id, l.student_id, l.student_name, sp.name FROM score_audit_log l JOIN student_profile sp ON sp.id=l.student_id WHERE l.student_name <> sp.name"),
    ('score_audit_log.course_name 与 courses.name 不符',
     "SELECT l.id, l.course_id, l.course_name, c.name FROM score_audit_log l JOIN courses c ON c.id=l.course_id WHERE l.course_name <> c.name"),
    ('communication_record.student_name 与解析出的学生姓名不符',
     "SELECT r.id, r.student_id, r.student_name, sp.name FROM communication_record r JOIN student_profile sp ON sp.id=r.student_id WHERE r.student_name IS NOT NULL AND r.student_name <> sp.name"),
]
for label, sql in NAME_CHECKS:
    probe(label, sql)

OUT.append('')
OUT.append('  —— 冗余名称列原始内容（人工看措辞）——')
for t, cols in [('communication_record', 'id,student_id,student_name,teacher_id,type,content'),
                ('communication_logs', 'id,counselor_id,teacher_id,student_id,content,category,status,reply'),
                ('counselor_class_application', 'id,counselor_id,class_id,counselor_name,class_name,application_type,reason,status'),
                ('teacher_class_application', 'id,teacher_id,class_id,teacher_name,teacher_username,class_name,application_type,reason,status,reject_reason'),
                ('feedback', 'id,teacher_id,category,content,reply,status'),
                ('notifications', 'id,user_id,title,content,type,warning_id,is_read'),
                ('assistance_plans', 'id,counselor_id,student_id,status,progress,content'),
                ('score_audit_log', 'id,student_id,student_name,course_id,course_name,teacher_id,old_score,new_score,reason,modified_by'),
                ('export_history', 'id,data_type,file_name,record_count,exported_by')]:
    OUT.append('')
    OUT.append('  【%s】' % t)
    got = q('SELECT * FROM (SELECT %s FROM %s LIMIT 30) x' % (cols, t))
    if got.startswith('[SQLERR]'):
        OUT.append('    ' + got)
    else:
        for l in got.split('\n')[:30]:
            OUT.append('    ' + l)

# ---------------------------------------------------------------- 4. 文字垃圾
head('第 4 部分：文字垃圾（占位符 / 示例名 / 乱码）')

BAD = ['%辅导员%', '%张明%', '%范德萨%', '%测试%', '%test%', '%Test%', '%TEST%',
       '%111%', '%aaa%', '%asdf%', '%xxx%', '%待定%', '%未知%', '%张三%', '%李四%',
       '%demo%', '%example%', '%TODO%', '%？？%', '%哈哈%', '%呵呵%', '%随便%']
TEXT_TABLES = [
    ('users', ['username', 'name', 'email', 'phone']),
    ('student_profile', ['name', 'class_name', 'gender']),
    ('classes', ['name']),
    ('courses', ['name', 'code', 'description']),
    ('majors', ['name', 'short_name', 'description']),
    ('colleges', ['name', 'code', 'description']),
    ('counselor_profiles', ['name', 'phone', 'email', 'title', 'introduction', 'college_name', 'duty_phone']),
    ('teacher_profile', ['name', 'title', 'research_areas']),
    ('counselor_class_application', ['reason', 'counselor_name', 'class_name', 'application_type']),
    ('teacher_class_application', ['reason', 'teacher_name', 'teacher_username', 'class_name', 'application_type', 'reject_reason']),
    ('communication_record', ['content', 'student_name', 'type']),
    ('communication_logs', ['content', 'category', 'reply', 'status']),
    ('feedback', ['content', 'reply', 'category']),
    ('notifications', ['title', 'content', 'type']),
    ('assistance_plans', ['content', 'status']),
    ('export_history', ['file_name', 'data_type']),
    ('login_logs', ['device', 'location', 'ip_address']),
    ('warning_rules', ['name', 'description', 'rule_code', 'responsible_person']),
    ('college_admin_profile', ['name']),
    ('admin_profile', ['name']),
]
found_any = False
for tbl, cols in TEXT_TABLES:
    conds = ' OR '.join('CAST(%s AS CHAR) LIKE "%s"' % (c, b) for c in cols for b in BAD)
    sql = 'SELECT id, %s FROM %s WHERE %s' % (','.join(cols), tbl, conds)
    got = q(sql)
    if got.startswith('[SQLERR]'):
        OUT.append('  [!] %s -> %s' % (tbl, got))
        continue
    lines = [l for l in got.split('\n') if l.strip()] if got else []
    if lines:
        found_any = True
        OUT.append('  [x] %s：%d 行' % (tbl, len(lines)))
        for l in lines[:6]:
            OUT.append('        ' + l)
        if len(lines) > 6:
            OUT.append('        ... 另有 %d 行' % (len(lines) - 6))
if not found_any:
    OUT.append('  [ ] 命中 0 行')

OUT.append('')
OUT.append('  —— 空串 / 纯空白字段（非 NULL 但无内容）——')
for tbl, cols in TEXT_TABLES:
    for c in cols:
        got = q('SELECT COUNT(*) FROM %s WHERE %s IS NOT NULL AND TRIM(%s)=""' % (tbl, c, c))
        try:
            n = int(got)
        except ValueError:
            continue
        if n:
            OUT.append('  [x] %s.%s 空串：%d 行' % (tbl, c, n))

# ---------------------------------------------------------------- 5. 数值域
head('第 5 部分：数值域')

RANGE = [
    ('scores.score_total 不在 0~100', "SELECT id, score_total FROM scores WHERE score_total NOT BETWEEN 0 AND 100"),
    ('scores.regular_score 不在 0~100', "SELECT id, regular_score FROM scores WHERE regular_score IS NOT NULL AND regular_score NOT BETWEEN 0 AND 100"),
    ('scores.final_score 不在 0~100', "SELECT id, final_score FROM scores WHERE final_score IS NOT NULL AND final_score NOT BETWEEN 0 AND 100"),
    ('scores.grade_point 不在 0~4', "SELECT id, grade_point FROM scores WHERE grade_point IS NOT NULL AND grade_point NOT BETWEEN 0 AND 4"),
    ('scores.grade 不是 A/A-/B/C/D 或 NULL', "SELECT id, grade FROM scores WHERE grade IS NOT NULL AND grade NOT IN ('A','A-','B','C','D')"),
    ('scores.semester 不在 1~8', "SELECT id, semester FROM scores WHERE semester NOT BETWEEN 1 AND 8"),
    ('courses.credits 为空或 <=0', "SELECT id, name, credits FROM courses WHERE credits IS NULL OR credits <= 0"),
    ('student_profile.gender 取值异常', "SELECT id, gender FROM student_profile WHERE gender IS NOT NULL AND gender NOT IN ('男','女')"),
    ('users.role 取值清单', "SELECT role, COUNT(*) FROM users GROUP BY role"),
    ('users.status 取值清单', "SELECT status, COUNT(*) FROM users GROUP BY status"),
    ('enrollments.status 取值清单', "SELECT status, COUNT(*) FROM enrollments GROUP BY status"),
    # ⚠️ `courses` 表**没有 status 列**（只有 colleges / majors 有）—— 早先这条每跑必 `[SQLERR]`。
    ('colleges.status / majors.status 取值清单', "SELECT (SELECT GROUP_CONCAT(DISTINCT status) FROM colleges), (SELECT GROUP_CONCAT(DISTINCT status) FROM majors)"),
    ('scores 有期末无平时 或 有平时无期末', "SELECT id, regular_score, final_score FROM scores WHERE (final_score IS NOT NULL AND regular_score IS NULL) OR (final_score IS NULL AND regular_score IS NOT NULL)"),
]
for label, sql in RANGE:
    probe(label, sql)
OUT.append('')
OUT.append('  —— scores 里 final_score 为 NULL 的行（口径上算"待录入"）——')
got = q("SELECT COUNT(*), GROUP_CONCAT(DISTINCT course_id), GROUP_CONCAT(DISTINCT student_id) FROM scores WHERE final_score IS NULL")
OUT.append('    ' + got)

# ---------------------------------------------------------------- 6. 计数冗余列
head('第 6 部分：冗余计数列 vs 实际行数')

COUNT_CHECKS = [
    # ⚠️ `colleges.student_count` / `colleges.teacher_count` 两项**已删**（2026-10-12 W40）。
    #    那两列已从活库与 init_schema.sql 一并删掉（自助注册的 `+1` 是唯一写入方，删个学生就永久偏大），
    #    再查它们只会得到 `[SQLERR] Unknown column`。
    # ⚠️ `counselor_class_management` / `counselor_profiles.class_count` 的归属要 join `users`：
    #    `classes.counselor_id` 存的是 **users.id**，而 `counselor_profiles.id` 是**档案表自己的主键**
    #    （两套 id 空间不重合，见 `abs-db-id-spaces-and-calibers`）—— 早先这里写 `classes.counselor_id=cp.id`
    #    是**跨 id 空间**的错连接，必然恒为 0，白扫。
    ('counselor_profiles.class_count 与实际带班数不符',
     "SELECT cp.id, cp.name, cp.class_count, (SELECT COUNT(*) FROM classes c WHERE c.counselor_id=cp.user_id) FROM counselor_profiles cp WHERE cp.class_count <> (SELECT COUNT(*) FROM classes c WHERE c.counselor_id=cp.user_id)"),
    ('counselor_profiles.student_count 与实际学生数不符',
     "SELECT cp.id, cp.name, cp.student_count, (SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id=sp.class_id WHERE c.counselor_id=cp.user_id) FROM counselor_profiles cp WHERE cp.student_count <> (SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id=sp.class_id WHERE c.counselor_id=cp.user_id)"),
    ('export_history.record_count 与真实数据量无法核对（仅列出取值）',
     "SELECT id, data_type, file_name, record_count FROM export_history"),
]
for label, sql in COUNT_CHECKS:
    probe(label, sql, limit=30)

# ---------------------------------------------------------------- 7. 唯一性
head('第 7 部分：自然键唯一性')

UNIQ = [
    ('users.username 重复', "SELECT username, COUNT(*) FROM users GROUP BY username HAVING COUNT(*)>1"),
    ('users.email 重复（非空）', "SELECT email, COUNT(*) FROM users WHERE email IS NOT NULL AND email<>'' GROUP BY email HAVING COUNT(*)>1"),
    ('users.phone 重复（非空）', "SELECT phone, COUNT(*) FROM users WHERE phone IS NOT NULL AND phone<>'' GROUP BY phone HAVING COUNT(*)>1"),
    ('student_profile.student_id（学号）重复', "SELECT student_id, COUNT(*) FROM student_profile GROUP BY student_id HAVING COUNT(*)>1"),
    ('classes.name 重复', "SELECT name, COUNT(*) FROM classes GROUP BY name HAVING COUNT(*)>1"),
    ('courses.code 重复', "SELECT code, COUNT(*) FROM courses GROUP BY code HAVING COUNT(*)>1"),
    ('courses.name 重复', "SELECT name, COUNT(*) FROM courses GROUP BY name HAVING COUNT(*)>1"),
    ('majors.code 重复', "SELECT code, COUNT(*) FROM majors GROUP BY code HAVING COUNT(*)>1"),
    ('colleges.code 重复', "SELECT code, COUNT(*) FROM colleges GROUP BY code HAVING COUNT(*)>1"),
    ('course_plans (课程,教师,学期,学年) 重复', "SELECT course_id, teacher_id, semester, year, COUNT(*) FROM course_plans GROUP BY course_id, teacher_id, semester, year HAVING COUNT(*)>1"),
    ('enrollments (学生,课程,学期) 重复', "SELECT student_id, course_id, semester, COUNT(*) FROM enrollments GROUP BY student_id, course_id, semester HAVING COUNT(*)>1"),
    ('teacher_course (教师,课程,学期,学年) 重复', "SELECT teacher_id, course_id, semester, year, COUNT(*) FROM teacher_course GROUP BY teacher_id, course_id, semester, year HAVING COUNT(*)>1"),
    ('一个 user_id 对应多个 student_profile', "SELECT user_id, COUNT(*) FROM student_profile GROUP BY user_id HAVING COUNT(*)>1"),
]
for label, sql in UNIQ:
    probe(label, sql)

# ---------------------------------------------------------------- 8. 时间/学年
head('第 8 部分：时间与学年一致性')

TIME = [
    ('student_profile.enrollment_year <> 学号前四位',
     "SELECT id, student_id, name, enrollment_year FROM student_profile WHERE enrollment_year <> CAST(LEFT(student_id,4) AS UNSIGNED)"),
    ('student_profile 学号不是 10 位数字',
     "SELECT id, student_id FROM student_profile WHERE student_id NOT REGEXP '^[0-9]{10}$'"),
    ('enrollments.year 与学期推算的学年不符',
     "SELECT e.id, e.student_id, e.semester, e.year, CAST(LEFT(sp.student_id,4) AS UNSIGNED)+(CAST(e.semester AS UNSIGNED)-1) DIV 2 FROM enrollments e JOIN student_profile sp ON sp.id=e.student_id WHERE CAST(e.semester AS UNSIGNED) BETWEEN 1 AND 8 AND e.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED)+(CAST(e.semester AS UNSIGNED)-1) DIV 2"),
    ('course_plans 学期 vs 开课日期年份不符',
     "SELECT id, course_id, semester, year, start_date, end_date FROM course_plans WHERE start_date IS NOT NULL AND (YEAR(start_date) < year OR YEAR(end_date) > year+1)"),
    ('created_at 晚于当前（2026-09-16）',
     "SELECT 'users' t, id FROM users WHERE created_at > '2026-09-16 23:59:59' UNION ALL SELECT 'scores', id FROM scores WHERE created_at > '2026-09-16 23:59:59' UNION ALL SELECT 'classes', id FROM classes WHERE created_at > '2026-09-16 23:59:59'"),
    ('student_profile.created_at 在 2020 之前',
     "SELECT id, created_at FROM student_profile WHERE created_at < '2020-01-01'"),
    ('users.deleted_at 非空但 status 仍是启用类',
     "SELECT id, username, status, deleted_at FROM users WHERE deleted_at IS NOT NULL AND status <> 0"),
    ('users.deleted_at 格式异常（非日期）',
     "SELECT id, deleted_at FROM users WHERE deleted_at IS NOT NULL AND CAST(deleted_at AS CHAR) NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}'"),
    ('login_logs.status 取值清单', "SELECT status, COUNT(*) FROM login_logs GROUP BY status"),
    ('login_logs.login_time 晚于当前', "SELECT id, login_time FROM login_logs WHERE login_time > '2026-09-16 23:59:59'"),
]
for label, sql in TIME:
    probe(label, sql)

with open('_audit_db.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(OUT))
print('written _audit_db.txt;', len(OUT), 'lines')
