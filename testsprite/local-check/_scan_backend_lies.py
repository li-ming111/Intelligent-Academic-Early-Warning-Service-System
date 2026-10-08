# -*- coding: utf-8 -*-
"""扫后端"说谎的返回"三族（比 _scan_catch_success.py 扩到**全后端**、不只 controller）。

族 1: catch 自己体内 return ApiResponse.success(...)      —— 失败当成功
族 2: catch 自己体内 return ApiResponse.success(<空常量>)  —— 失败当"空"（同族，只是回空而非 0）
族 3: controller 公有方法体内直接 return null               —— 未实现 / 忘了返回

用法: python _scan_backend_lies.py
"""
import io, glob, re, os, sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))


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


def short(p):
    return p.replace(os.sep, '/').split('academic-microservices/')[-1]


files = sorted(glob.glob('academic-microservices/*/src/main/java/**/*.java', recursive=True))
c1 = c2 = 0
print('=== 族1/2: catch 内 return success（含空常量）===')
for p in files:
    s = io.open(p, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'\bcatch\s*\([^)]*\)\s*\{', s):
        b = block_after(s, m.start())
        if not b:
            continue
        if re.search(r'return\s+ApiResponse\.success\s*\(', b):
            ln = s[:m.start()].count('\n') + 1
            ret = re.search(r'return\s+ApiResponse\.success\s*\([^;]*\)', b)
            r = (ret.group(0) if ret else '?').replace('\n', ' ')[:80]
            empty = re.search(r'List\.of\(\)|emptyList\(\)|new ArrayList<>\s*\(\s*\)|0L?\)|\"\"\)', r)
            tag = 'EMPTY' if empty else 'VALUE'
            print('  [%s] %-44s :%-5d %s' % (tag, short(p), ln, r))
            if empty:
                c2 += 1
            else:
                c1 += 1
print('  族1(非空/0等) = %d, 族2(空常量) = %d' % (c1, c2))

print('\n=== 族3: controller 公有方法体内直接 return null ===')
c3 = 0
for p in sorted(glob.glob('academic-microservices/*/src/main/java/com/academic/controller/*.java')):
    s = io.open(p, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'public\s+[A-Za-z_][\w<>,.\[\] ]*\s+\w+\s*\([^;{]*\)\s*\{', s):
        b = block_after(s, m.end() - 1)
        if b and re.search(r'\breturn\s+null\s*;', b):
            ln = s[:m.start()].count('\n') + 1
            name = re.search(r'(\w+)\s*\(', m.group(0))
            print('  %-44s :%-5d %s' % (short(p), ln, name.group(1) if name else '?'))
            c3 += 1
print('  族3 = %d' % c3)
