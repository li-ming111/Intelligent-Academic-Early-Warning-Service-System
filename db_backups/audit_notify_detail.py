# -*- coding: utf-8 -*-
"""拉出每个挂科学生在权威口径下的真实明细，用于重写通知/计划文案。"""
import io
import subprocess

DB = 'academic_warning_system'
LATEST = ("(SELECT lat.id, lat.student_id, lat.course_id, lat.semester, lat.score_total "
          "FROM (SELECT s.id, s.student_id, s.course_id, s.semester, s.score_total, "
          "ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id ORDER BY s.semester DESC, s.id DESC) rn "
          "FROM scores s WHERE s.final_score IS NOT NULL) lat WHERE lat.rn=1)")


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')


out = []
out.append('== 各挂科学生的真实挂科明细（权威口径）==')
out.append('学生  profile_id  挂科门数  明细')
for pid in (3, 13, 20, 26):
    rows = q('SELECT c.name, v.score_total, v.semester FROM ' + LATEST +
             ' v JOIN courses c ON c.id=v.course_id WHERE v.student_id=%d AND v.score_total<60 '
             'ORDER BY v.score_total' % pid).strip().split('\n')
    name = q('SELECT name FROM student_profile WHERE id=%d' % pid).strip()
    detail = '、'.join('%s %.1f分(第%s学期)' % (r.split('\t')[0], float(r.split('\t')[1]),
                                              r.split('\t')[2]) for r in rows if r.strip())
    out.append('%s  %d  %d门  %s' % (name, pid, len(rows), detail))

out.append('')
out.append('== 4 人所在班级 ==')
out.append(q('SELECT sp.id, sp.name, sp.class_id, c.name, sp.student_id FROM student_profile sp '
             'LEFT JOIN classes c ON c.id=sp.class_id WHERE sp.id IN (3,13,20,26) ORDER BY sp.class_id, sp.id;'))

out.append('== 各班级挂科人数（权威口径，用于班级汇总通知）==')
out.append(q('SELECT sp.class_id, c.name, COUNT(DISTINCT v.student_id) AS fail_students, '
             'GROUP_CONCAT(DISTINCT sp.name) FROM ' + LATEST +
             ' v JOIN student_profile sp ON sp.id=v.student_id LEFT JOIN classes c ON c.id=sp.class_id '
             'WHERE v.score_total<60 GROUP BY sp.class_id, c.name;'))

out.append('== notifications 全部字段结构（看有哪些列可改）==')
out.append(q('SHOW COLUMNS FROM notifications;'))

out.append('== notifications 收件人对照 ==')
out.append(q('SELECT n.id, n.user_id, u.username, u.real_name, n.type, n.is_read, n.created_at '
             'FROM notifications n LEFT JOIN users u ON u.id=n.user_id ORDER BY n.id;'))

io.open('D:/ABS/db_backups/_notify_detail.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('ok')
