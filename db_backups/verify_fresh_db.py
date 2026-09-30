# -*- coding: utf-8 -*-
"""在临时库上验证 init_schema.sql / init_data.sql 能真正建出一个可用的库。

关键点：用与 DataInitializer.executeScript **完全相同**的切分规则（逐行累积、
遇到以分号结尾的行就执行一条），而不是简单按分号 split —— 否则验证的是一套
线上并不存在的执行语义，等于没验证。
"""
import io
import subprocess
import sys

SCRATCH = 'aws_fresh_verify'
SCHEMA = 'academic-microservices/academic-common/src/main/resources/init_schema.sql'
DATA = 'academic-microservices/academic-common/src/main/resources/init_data.sql'


def mysql(sql, db=None, batch=True):
    cmd = ['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4']
    if batch:
        cmd.append('-N')
    if db:
        cmd.append(db)
    return subprocess.run(cmd, input=sql.encode('utf-8'),
                          capture_output=True)


def split_like_java(text):
    """复刻 DataInitializer：逐行累积，行尾分号即一条语句；跳过 -- / # / 空行。"""
    out = []
    cur = []
    for line in text.split('\n'):
        t = line.strip()
        if not t or t.startswith('--') or t.startswith('#'):
            continue
        cur.append(t)
        if t.endswith(';'):
            stmt = ' '.join(cur)
            if stmt.endswith(';'):
                stmt = stmt[:-1].strip()
            if stmt:
                out.append(stmt)
            cur = []
    return out


def run_script(db, path, label):
    text = io.open(path, encoding='utf-8').read()
    stmts = split_like_java(text)
    ok = fail = 0
    errors = []
    for s in stmts:
        r = mysql(s + ';', db=db)
        if r.returncode == 0:
            ok += 1
        else:
            fail += 1
            errors.append((s[:90], r.stderr.decode('utf-8', 'replace').strip().split('\n')[-1]))
    print('[%s] 语句 %d 条: 成功 %d, 失败 %d' % (label, len(stmts), ok, fail))
    for sql, err in errors[:15]:
        print('   FAIL: %s\n         %s' % (sql, err))
    return fail


if __name__ == '__main__':
    mysql('DROP DATABASE IF EXISTS %s;' % SCRATCH)
    mysql('CREATE DATABASE %s DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' % SCRATCH)
    f1 = run_script(SCRATCH, SCHEMA, 'init_schema')
    f2 = run_script(SCRATCH, DATA, 'init_data')
    print('总失败: %d' % (f1 + f2))
