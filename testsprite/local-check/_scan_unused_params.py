# -*- coding: utf-8 -*-
"""扫「方法收了 @RequestParam / @PathVariable / @RequestBody 但在方法体里从不引用」。

修过一版 bug：旧正则用 `[\\w<>,.\\s]+` 贪婪吃掉了后面的参数，
于是 `@PathVariable String type, HttpServletResponse response` 里的
`response` 被误当成 `@PathVariable` 的参数名。
现在按**顶层逗号**切分形参表（忽略 <> 与 () 内的逗号），每段取最后一个标识符当名字。
"""
import io, os, re, sys, glob

sys.stdout.reconfigure(encoding='utf-8', errors='replace')


def split_top(s):
    parts, depth, cur = [], 0, ''
    for ch in s:
        if ch in '<([':
            depth += 1
        elif ch in '>)]':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def method_body(s, start):
    i = s.find('{', start)
    if i < 0:
        return None
    d = 0
    for j in range(i, len(s)):
        if s[j] == '{':
            d += 1
        elif s[j] == '}':
            d -= 1
            if d == 0:
                return s[i + 1:j]
    return None


SIG = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping[^\n]*\n(?:\s*@[^\n]*\n)*\s*public[^\{]*\{')
PARAM_ANN = re.compile(r'@(RequestParam|PathVariable|RequestBody)')
NAME = re.compile(r'([A-Za-z_]\w*)\s*$')

n = 0
seen = 0
for p in sorted(glob.glob('academic-microservices/*/src/main/java/com/academic/controller/*.java')):
    s = io.open(p, encoding='utf-8', errors='replace').read()
    fname = p.replace(os.sep, '/').split('/')[-1]
    for m in SIG.finditer(s):
        sig = m.group(0)
        # SIG 以 `{`（方法体开括号）结尾 ⇒ 从 m.end()-1 起找。
        # ⚠️ 2026-10-04（W16）亲历：这个文件一度被写成 `m.start()` —— 那会先撞上
        # **路径串里的 `{id}`**，把"方法体"抽成路径串本身 ⇒ 带 `{teacherId}` 的方法里，
        # 明明被用到的 `category` 被报成"未使用"，一次报出 **90 条假阳性**（真值 0）。
        # 教训：**判据/工具的"突变"本身就是信号** —— 上一轮 6、这一轮 90，先别信数，
        # 去看它到底抽出了什么。（同族：`check-route-links.mjs` 的假阴性/假阳性。）
        body = method_body(s, m.end() - 1)
        if body is None:
            continue
        lp = sig.find('(', sig.find('public'))
        if lp < 0:
            continue
        plist = sig[lp + 1:sig.rfind(')')]
        for chunk in split_top(plist):
            seen += 1
            ann = PARAM_ANN.search(chunk)
            if not ann:
                continue
            nm = NAME.search(chunk.strip())
            if not nm:
                continue
            pname = nm.group(1)
            if not re.search(r'\b' + re.escape(pname) + r'\b', body):
                ln = s[:m.start()].count('\n') + 1
                ep = re.search(r'@(?:Get|Post|Put|Delete|Patch)Mapping\(([^)]*)\)', sig)
                print('%-26s :%-5d %-13s %-18s %s' % (
                    fname, ln, ann.group(1), pname, (ep.group(1) if ep else '')[:44]))
                n += 1
print('\nparams examined: %d   unused: %d' % (seen, n))
