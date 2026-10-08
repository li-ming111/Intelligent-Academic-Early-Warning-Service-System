# -*- coding: utf-8 -*-
"""列出某个 controller 里**没有**出现归属守卫的端点。

"有守卫"的判据：方法体内出现 `denyUnlessXxx(` / `isAdmin(request)` / `callerId(request)`。
只做粗筛，**命中不等于真缺**（公开端点、模板端点本就不该有守卫）—— 逐条人工判。

用法: python _scan_endpoint_guards.py [controller 路径]
"""
import io, os, re, sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))

path = sys.argv[1] if len(sys.argv) > 1 else \
    'academic-microservices/counselor-service/src/main/java/com/academic/controller/CounselorController.java'

s = io.open(path, encoding='utf-8', errors='replace').read()

MAP = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping\s*\(?([^)\n]*)')
SIG = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping[^\n]*\n(?:\s*@[^\n]*\n|\s*//[^\n]*\n)*\s*public[^\{]*\{')
GUARD = re.compile(r'denyUnless\w+\s*\(|isSelf\w*\s*\(|isAdmin\w*\s*\(|callerId\s*\(')


def body(s, start):
    i = s.find('{', start)
    d = 0
    for j in range(i, len(s)):
        if s[j] == '{':
            d += 1
        elif s[j] == '}':
            d -= 1
            if d == 0:
                return s[i + 1:j]
    return ''


total = noguard = 0
for m in SIG.finditer(s):
    sig = m.group(0)
    total += 1
    b = body(s, m.end() - 1)
    if GUARD.search(b):
        continue
    ln = s[:m.start()].count('\n') + 1
    ep = MAP.search(sig)
    name = re.search(r'(\w+)\s*\(', sig[sig.find('public'):])
    params = ','.join(re.findall(r'@(?:RequestParam|PathVariable|RequestBody)[^,)]*', sig))
    noguard += 1
    print('%-5d %-52s %s' % (ln, (ep.group(1).upper() + ' ' + ep.group(2).strip()) if ep else '?',
                             name.group(1) if name else '?'))
print('\n%-40s endpoints=%d  no-guard=%d' % (os.path.basename(path), total, noguard))
