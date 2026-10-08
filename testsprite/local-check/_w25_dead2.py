# W25：两个前端一起做「零调用封装」清点 + 把零调用的分成「真缺口 / 第二个入口」。
#
# 为什么重写：W24 那份 `_w24_dead_api.py` **只吃了一种写法** ——
#   它找 `^(?:export\s+)?const\s+(\w+API)\s*=\s*\{`。
#   · PC `src/api/index.js` 确实是 `const aiAPI = { chat: (...) => ... }` ⇒ 匹配
#   · 小程序 `src/services/api.js` 是 **class + 实例字段**
#     （`class ApiClient { student = { getDashboard: ... } }` 末尾再
#      `export const studentAPI = apiClient.student`）⇒ **一个命名空间都没匹配上**
#   ⇒ 于是"全仓 61 个零调用"其实是 **PC 独有**。这份扫描器把两种结构都吃进去。
#
# 分类判据（W24 那版判据选错了，这一版是对的 —— 正对照把它拦下来了）：
#   页面调的是**封装方法名**，不是**字面路径**。所以不能拿路径去 .vue 里搜。
#   正确问法：这条死封装指向的端点，有没有**别的（被调用的）封装方法**也指向它？
#     有 ⇒ 「第二个入口」，功能不缺
#     没有 ⇒ 「真缺口」，后端这条端点没有任何界面用
import re, os, sys, glob
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = "D:/ABS"
PC = "academic-warning-frontend/src/api/index.js"
XCX = "academic-warning-xcx/src/services/api.js"
WRAPPERS = {PC, XCX}

# ⚠️ 本仓第三次栽在"剥注释"上，这一版的坑是新的，记下来：
#   旧版**先找 `/*` 再找 `//`**，于是 `// 下面 4 个是 `/students/settings/**`...`
#   这行 `//` 注释里的 `/**` 被当成块注释开头 ⇒ in_block=True 之后**没有任何 `*/`**，
#   从第 96 行起**后面所有行都被吞成空行**。源文件 297 个封装方法，剥完只剩 21 个，
#   而且 7 个顶层 `\n}` 只剩 1 个 ⇒ scan_pc 的 `src.find('\n}', ...)` 全部跑飞。
#   这就是"扫描器报的数字全错"的又一种形状：不是匹配不到，是**把源码删了**。
#   修法：**字符级单遍扫描**，顺序按 JS 真实词法 —— 字符串 > 行注释 > 块注释。
#   `//` 一旦命中就跳到行尾，行尾那个 `/**` 再也不会被读到。
#   块注释内的换行**保留**（否则行号失配，且 `\n}` 结构标记会消失——正是这次的病根）。
def strip_comments(text):
    out, i, n, in_block = [], 0, len(text), False
    while i < n:
        ch = text[i]
        if in_block:
            if text.startswith('*/', i):
                in_block = False; i += 2
            else:
                if ch == '\n': out.append('\n')
                i += 1
            continue
        if ch in '\'"`':                       # 字符串/模板串整体跳过（里面的 // 和 /* 都不是注释）
            q = ch; out.append(ch); i += 1
            while i < n:
                c = text[i]; out.append(c)
                if c == '\\':
                    if i + 1 < n: out.append(text[i + 1]); i += 2; continue
                    i += 1; break
                i += 1
                if c == q: break
            continue
        if text.startswith('//', i):           # 行注释：跳到行尾（\n 留给外层 append）
            while i < n and text[i] != '\n': i += 1
            continue
        if text.startswith('/*', i):           # 块注释
            in_block = True; i += 2; continue
        out.append(ch); i += 1
    return ''.join(out)

def normalize(u):
    if not u: return None
    u = u.strip()
    u = re.sub(r'\$\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}', '{}', u)   # ${...} 含一层嵌套
    u = u.split('?', 1)[0]
    u = u.split('$', 1)[0]                                        # 残留的模板表达式
    u = re.sub(r'\{[^}]*\}', '{}', u)
    if not u.startswith('/'): return None
    u = re.sub(r'/{2,}', '/', u)
    if len(u) > 1 and u.endswith('/'): u = u[:-1]
    if u.endswith('{}') and not u.startswith('/{}'): pass
    return u[:-1] if len(u) > 1 and u.endswith('/') else u

def url_after(text, pos, span=400):
    m = re.search(r'[`\'"](\/[^`\'"]*)[`\'"]', text[pos:pos + span])
    return m.group(1) if m else None

# ---------- 前端源码文件（排除两个封装）----------
def pages():
    out = []
    for pat in ["academic-warning-frontend/src/**/*", "academic-warning-xcx/src/**/*"]:
        for p in glob.glob(os.path.join(ROOT, pat), recursive=True):
            if os.path.isfile(p) and os.path.splitext(p)[1].lower() in ('.js', '.vue', '.ts'):
                rel = os.path.relpath(p, ROOT).replace(os.sep, '/')
                if rel not in WRAPPERS:
                    out.append((rel, p))
    return out

PAGE_TEXT = {rel: strip_comments(open(p, encoding='utf-8', errors='replace').read()) for rel, p in pages()}

def call_count(names):
    """names 是若干写法（如 `studentAPI.getDashboard`），返回非封装文件里的命中数。"""
    n = 0
    for t in PAGE_TEXT.values():
        for name in names:
            n += len(re.findall(r'(?<![\w.])' + re.escape(name) + r'\s*\(', t))
    return n

def literal_witnesses(url):
    """同一道判据的第二只眼睛：这个 URL 有没有被**页面直接以字面路径**打（绕开封装）？
    ⚠️ 只数封装方法调用点会漏掉 `.vue` 直连 —— W20 就查出全仓有 8 处 `.vue` 直连后端。
    两处实测被这条逮住的假"真缺口"：`/college-admin/assistance/{}`(assistance.vue)、
    `/auth/logout`(PC App.vue:232)。"""
    if not url: return []
    r = re.compile(re.escape(url).replace(re.escape('{}'), r"[^/`'\"\s]*"))
    return [rel for rel, t in PAGE_TEXT.items() if r.search(t)]

# ---------- 抽封装方法 ----------
def scan_pc():
    """`const aiAPI = { chat: (a,b) => {...}, }` → (组名, 方法名, url)"""
    src = strip_comments(open(os.path.join(ROOT, PC), encoding='utf-8').read())
    out = []
    for ns_m in re.finditer(r'^(?:export\s+)?const\s+(\w+API)\s*=\s*\{', src, re.M):
        ns = ns_m.group(1)
        end = src.find('\n}', ns_m.end())
        body = src[ns_m.end():end if end > 0 else len(src)]
        for d in re.finditer(r'^  (\w+)\s*:', body, re.M):
            name = d.group(1)
            out.append((PC, ns, name, url_after(body, d.end()), f"{ns}.{name}"))
    return out

def scan_xcx():
    """class ApiClient { student = { getDashboard: (u) => this._get('/students/dashboard/${u}') } }
       末尾 `export const studentAPI = apiClient.student` 给出组→导出名。"""
    src = strip_comments(open(os.path.join(ROOT, XCX), encoding='utf-8').read())
    alias = dict(re.findall(r'^export const (\w+API) = apiClient\.(\w+)$', src, re.M))  # 导出名 → 组名
    rev = {v: k for k, v in alias.items()}
    out = []
    # (a) 顶层（非分组）方法：class 体内缩进 2 的 `name = (...) =>`，直接可经 apiClient.X 调
    top = re.search(r'class ApiClient\s*\{', src)
    if top:
        # 顶层方法出现在缩进 2；分组名也缩进 2，用"下一字符不是 {"来区分
        for d in re.finditer(r'^  (\w+)\s*=\s*\(', src[top.end():], re.M):
            name = d.group(1)
            pos = top.end() + d.end()
            out.append((XCX, 'apiClient', name, url_after(src, pos),
                        f"apiClient.{name}"))
    # (b) 分组方法：`^  (\w+) = \{$` ... `^  \}$`
    for g in re.finditer(r'^  (\w+)\s*=\s*\{$', src, re.M):
        grp = g.group(1)
        rest = src[g.end():]
        stop = re.search(r'^  \}$', rest, re.M)
        body = rest[:stop.start()] if stop else rest
        exported = rev.get(grp, grp)
        for d in re.finditer(r'^    (\w+)\s*:', body, re.M):
            name = d.group(1)
            pos = g.end() + d.end()
            out.append((XCX, grp, name, url_after(src, pos), f"{exported}.{name}"))
    return out

HELPERS = {'request','_get','_post','_put','_patch','_del'}
METHODS = [m for m in (scan_pc() + scan_xcx()) if m[2] not in HELPERS]
print(f"封装方法总数：PC {len(scan_pc())} / 小程序 {len(scan_xcx())}\n")

for f, ns, name, url, callname in METHODS:
    pass
DEAD = []
for f, ns, name, url, callname in METHODS:
    if call_count([callname]) == 0:
        DEAD.append((f, ns, name, normalize(url), callname))

print(f"零调用封装：{len(DEAD)}  (PC {sum(1 for d in DEAD if d[0]==PC)} / 小程序 {sum(1 for d in DEAD if d[0]==XCX)})\n")

# ---------- 打靶 ----------
print("=" * 100); print("0. 打靶"); print("=" * 100)
# ⚠️ 负对照会**过期**：W25 写这条时 `aiAPI.predictRisk` 确是零调用，但 W19 在
# `TeacherScores.vue` 给它接了 2 个调用点（`aiPredictRisk` / 直接 await）⇒ 打靶报 BAD。
# 这是**判据过期**、不是产品坏了 —— 处置办法是换一个**先 grep 过确实零调用**的样本，
# 而不是把断言放宽。（同 W25 自己的教训：换对照前先 grep 一遍它真的在／真的不在。）
CTRL = [("studentAPI.getDashboard", True,  "正: 小程序学生首页务必在用"),
        ("aiAPI.getResponse",      True,  "正: PC 唯一在用的 AI 封装"),
        ("studentAPI.getUserSettings", False, "负: 零调用（2026-10-08 换，原 aiAPI.predictRisk 已被 W19 接线）"),
        ("teacherAPI.getCourseAnomalies", False, "负: 零调用")]
ok_all = True
for name, expect, label in CTRL:
    n = call_count([name]); got = n > 0
    ok = got == expect; ok_all &= ok
    print(f"  [{'OK ' if ok else 'BAD'}] {name:<34} {label:<30} 调用点={n}")
assert ok_all, "打靶未过，结论不可信"

# 字面路径搜索器自己也要打靶（0 命中才可信 —— 正对照必须打中，负对照必须打不中）
c_pos = literal_witnesses('/auth/logout')        # PC App.vue:232 真写了 apiClient.post(`/auth/logout?...`)
c_neg = literal_witnesses('/this/path/does/not/exist/at/all')
print(f"  [{'OK ' if c_pos else 'BAD'}] 字面路径正对照 /auth/logout            → {len(c_pos)} 处 {[x.split('/')[-1] for x in c_pos]}")
print(f"  [{'OK ' if not c_neg else 'BAD'}] 字面路径负对照 编造路径              → {len(c_neg)} 处")
assert c_pos, "字面路径搜索器扫不到 /auth/logout，它报的 0 不可信"
assert not c_neg, "字面路径搜索器把不存在的路径也搜出来了"

# ---------- 分类 ----------
by_url = {}
for f, ns, name, url, callname in METHODS:
    u = normalize(url)
    if u: by_url.setdefault(u, []).append((f, ns, name, callname))

gap, second = [], []
for f, ns, name, url, callname in DEAD:
    if not url:
        gap.append((f, ns, name, '(无 url)', callname, [])); continue
    sibs = [(g, n, c, call_count([c])) for g, _, n, c in by_url.get(url, []) if c != callname]
    live = [s[2] for s in sibs if s[3] > 0]
    lit = literal_witnesses(url)                 # 第二只眼睛: 页面直连字面路径
    (second if (live or lit) else gap).append((f, ns, name, url, callname, live, lit))

short = lambda f: 'PC' if f == PC else 'XCX'
print(f"\n零调用 {len(DEAD)} 个 ⇒ 真缺口 {len(gap)} / 第二个入口 {len(second)}\n")

# 按命名空间拆（FEATURES.md §8.1 要写的就是这张表）
by_ns = {}
for f, ns, name, url, callname, *_ in DEAD:
    k = f"{short(f)}/{ns}"
    by_ns.setdefault(k, [0, 0])
    by_ns[k][0] += 1
for f, ns, name, url, callname, *_ in gap:
    by_ns[f"{short(f)}/{ns}"][1] += 1
print("零调用 / 其中真缺口，按命名空间：")
for k in sorted(by_ns):
    d, gp = by_ns[k]
    print(f"    {k:<34} {d:>3} / {gp:>3}")
print()

print("=" * 100); print("A. 真缺口（端点既没被别的封装指向、也没被页面字面路径直连）"); print("=" * 100)
for f, ns, name, url, callname, _, _ in sorted(gap, key=lambda x: (short(x[0]), x[1], x[2])):
    print(f"  [{short(f)}] {ns}.{name:<30} {url}")
print()
print("=" * 100); print("B. 第二个入口（同端点另有被调用的封装 / 或页面直连了字面路径）"); print("=" * 100)
for f, ns, name, url, callname, live, lit in sorted(second, key=lambda x: (short(x[0]), x[1], x[2])):
    why = ' / '.join(filter(None, [', '.join(live) or None,
                                   ('字面直连: ' + ', '.join(x.split('/')[-1] for x in lit)) if lit else None]))
    print(f"  [{short(f)}] {ns}.{name:<28} {url:<44} ← {why}")
