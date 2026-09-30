# -*- coding: utf-8 -*-
"""扫描文本字段里是否写死了会被重算推翻的数字/结论。

GPA / 挂科数 / 分数 / 等级一旦出现在 notifications、assistance_plans、
academic_warning 之类的正文里，就会和页面按权威口径算出来的值打架。

注意：mysql 客户端在 Windows 下输出 \r\n，所有字段必须 strip('\r')，
否则列名会变成 'content\r' 导致查询报错并被静默当成 0 行（曾经的假阴性）。
"""
import io
import subprocess

DB = 'academic_warning_system'
PAT = 'GPA|绩点|挂科|不及格|不达标|预警|低分|分数|[0-9]+\\.[0-9]+分|[0-9]+分'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    out = r.stdout.decode('utf-8', 'replace')
    if r.returncode != 0:
        out += '  [SQLERR] ' + r.stderr.decode('utf-8', 'replace').strip()
    return out


rows = q("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH "
         "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
         "AND DATA_TYPE IN ('text','mediumtext','longtext','char','varchar') "
         "AND (CHARACTER_MAXIMUM_LENGTH IS NULL OR CHARACTER_MAXIMUM_LENGTH >= 20) "
         "ORDER BY TABLE_NAME, ORDINAL_POSITION")

out = []
out.append('== 扫描范围 ==')
targets = []
for line in rows.split('\n'):
    line = line.strip('\r\n')
    if not line.strip():
        continue
    parts = [p.strip('\r') for p in line.split('\t')]
    if len(parts) < 3:
        continue
    targets.append(parts)
out.append('  共 %d 个文本列' % len(targets))

out.append('')
out.append('== 含"结论型文字"的列 ==')
hits = []
for parts in targets:
    t, c = parts[0], parts[1]
    n = q("SELECT COUNT(*) FROM `%s` WHERE `%s` REGEXP '%s'" % (t, c, PAT)).strip()
    if n.isdigit() and int(n) > 0:
        hits.append((t, c, n))
        out.append('  %-24s %-22s %s 行' % (t, c, n))
if not hits:
    out.append('  （无）')

out.append('')
for t, c, n in hits:
    out.append('==== %s.%s 全部 %s 行 ====' % (t, c, n))
    body = q("SELECT * FROM `%s` LIMIT 200" % t)
    hdr = [p.strip('\r') for p in q("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) "
                                    "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                                    "AND TABLE_NAME='%s'" % t).strip('\r\n').split('\t')]
    out.append('  列: %s' % (hdr[0] if hdr and hdr[0] else '?'))
    for line in body.split('\n'):
        line = line.strip('\r\n')
        if line:
            out.append('  ' + line)
    out.append('')

io.open('D:/ABS/db_backups/_text_claims.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('\n'.join(out[:40]))
