# -*- coding: utf-8 -*-
"""扫「catch 块自己体内 return ApiResponse.success(...)」—— 失败当成功的家族。
只认 catch 自己的 {} 直系方法体（花括号配平），避免把下一个方法的 return 算进来。
"""
import io, os, re, sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
os.chdir(os.path.join(os.path.dirname(__file__), '..', '..'))


def block_after(s, start):
    i = s.find('{', start)
    if i < 0:
        return None
    depth = 0
    for j in range(i, len(s)):
        if s[j] == '{':
            depth += 1
        elif s[j] == '}':
            depth -= 1
            if depth == 0:
                return s[i + 1:j]
    return None


import glob
files = sorted(glob.glob('academic-microservices/*/src/main/java/com/academic/controller/*.java'))
total = 0
for p in files:
    s = io.open(p, encoding='utf-8', errors='replace').read()
    name = p.replace(os.sep, '/').split('/')[-1]
    for m in re.finditer(r'\bcatch\s*\([^)]*\)\s*\{', s):
        body = block_after(s, m.start())
        if body is None:
            continue
        if re.search(r'return\s+ApiResponse\.success\s*\(', body):
            ln = s[:m.start()].count('\n') + 1
            ret = re.search(r'return\s+ApiResponse\.success\s*\([^;]*\)', body)
            r = (ret.group(0) if ret else '?').replace('\n', ' ')
            print('%-26s :%-5d -> %s' % (name, ln, r[:95]))
            total += 1
print('\ntotal catch-bodies returning success:', total)
