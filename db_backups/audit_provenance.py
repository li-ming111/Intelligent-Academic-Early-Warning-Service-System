# -*- coding: utf-8 -*-
"""盘点每个表的数据来源线索：行数、created_at 的去重值个数与时间跨度。

判据：真实业务流程产生的数据，created_at 会分散在很多天的很多个时刻；
脚本批量伪造的数据往往集中在极少数几个时刻（甚至一个）。
"""
import io
import subprocess

DB = 'academic_warning_system'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')


tables = [l.strip() for l in q(
    "SELECT TABLE_NAME FROM information_schema.TABLES "
    "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' "
    "AND TABLE_NAME NOT LIKE '%_bak_%' ORDER BY TABLE_NAME").split('\n') if l.strip()]

with_cat = set(l.strip() for l in q(
    "SELECT TABLE_NAME FROM information_schema.COLUMNS "
    "WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='created_at'").split('\n') if l.strip())

rows = []
for t in tables:
    n = q('SELECT COUNT(*) FROM `%s`' % t).strip()
    if n in ('0', ''):
        continue
    if t in with_cat:
        r = q('SELECT COUNT(DISTINCT created_at), MIN(created_at), MAX(created_at) '
              'FROM `%s`' % t).strip().split('\t')
        distinct, lo, hi = r[0], r[1], r[2]
    else:
        distinct, lo, hi = '-', '(无该列)', ''
    rows.append((int(n), t, distinct, lo, hi))

rows.sort(key=lambda x: (x[2].isdigit() and int(x[2]) <= 3, -x[0]))

out = ['%-30s %7s %9s  %-20s %-20s' % ('表', '行数', '时刻数', '最早', '最晚'),
       '-' * 95]
for n, t, distinct, lo, hi in rows:
    flag = '  <== 高度集中' if distinct.isdigit() and int(distinct) <= 3 and n > 3 else ''
    out.append('%-30s %7d %9s  %-20s %-20s%s' % (t, n, distinct, lo, hi, flag))

io.open('D:/ABS/db_backups/_provenance.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('有数据的表:', len(rows))
print('高度集中(<=3个时刻且>3行):',
      sum(1 for n, t, d, lo, hi in rows if d.isdigit() and int(d) <= 3 and n > 3))
