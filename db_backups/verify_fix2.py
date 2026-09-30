# -*- coding: utf-8 -*-
"""第二批修复的校验：文字是否与权威口径一致，且权威基线未被改动。"""
import io
import subprocess

DB = 'academic_warning_system'
LATEST = ("(SELECT lat.id, lat.student_id, lat.course_id, lat.score_total FROM "
          "(SELECT s.id, s.student_id, s.course_id, s.score_total, "
          "ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id ORDER BY s.semester DESC, s.id DESC) rn "
          "FROM scores s WHERE s.final_score IS NOT NULL) lat WHERE lat.rn=1)")


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace')
    if r.returncode != 0:
        out += '[SQLERR] ' + r.stderr.decode('utf-8', 'replace')
    return out.strip()


o = []
o.append('(a) courses.type 取值分布  [期望 必修课6 / 选修课4]')
o.append(q('SELECT GROUP_CONCAT(CONCAT(type,":",c) ORDER BY type) FROM '
           '(SELECT type, COUNT(*) c FROM courses GROUP BY type) t'))

o.append('')
o.append('(b) 帮扶计划里是否还有不存在的课程名  [期望 空]')
o.append(q("SELECT GROUP_CONCAT(id) FROM assistance_plans "
           "WHERE content REGEXP '线性代数|计算机组成原理|Java程序设计'"))

o.append('')
o.append('(c) 通知/帮扶计划里是否还有 66/69/68 这类"标称不及格但≥60"的分数  [期望 空]')
o.append(q("SELECT GROUP_CONCAT(id) FROM notifications "
           "WHERE content REGEXP '（[^）]*[6-9][0-9]分[^）]*）' AND content LIKE '%不及格%'"))

o.append('')
o.append('(d) warning_id 悬空引用数  [期望 0]')
o.append(q('SELECT COUNT(*) FROM notifications n WHERE n.warning_id IS NOT NULL '
           'AND NOT EXISTS (SELECT 1 FROM academic_warning w WHERE w.id=n.warning_id)'))

o.append('')
o.append('(e) 权威口径基线（挂科门数）  [期望 3:3,13:2,20:3,26:3，不得改变]')
o.append(q('SELECT GROUP_CONCAT(CONCAT(v.student_id,":",c) ORDER BY v.student_id) FROM '
           '(SELECT v.student_id, COUNT(*) c FROM ' + LATEST +
           ' v WHERE v.score_total < 60 GROUP BY v.student_id) v'))

o.append('')
o.append('(f) scores / score_record 行数  [期望 167 / 384]')
o.append(q('SELECT (SELECT COUNT(*) FROM scores), (SELECT COUNT(*) FROM score_record)'))

o.append('')
o.append('==== 改后的通知全文 ====')
o.append(q('SELECT id, title, content FROM notifications ORDER BY id'))

o.append('')
o.append('==== 改后的帮扶计划全文 ====')
o.append(q('SELECT id, student_id, content FROM assistance_plans ORDER BY id'))

o.append('')
o.append('==== 改后的课程表 ====')
o.append(q('SELECT id, code, name, credits, type FROM courses ORDER BY id'))

io.open('D:/ABS/db_backups/_verify2.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(o))
print('\n'.join(o))
