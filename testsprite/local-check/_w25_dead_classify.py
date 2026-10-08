# W25：把「零调用封装」分成两类 —— 真缺口 vs 第二个入口。
#
# 判据：拿死封装指向的**端点路径**去全前端搜。
#   - 同一条端点在别处被调 ⇒ 只是"第二个入口"（功能不缺，只是这条封装没人用）
#   - 一条都没有          ⇒ 后端这条端点**没有任何界面用**（真缺口）
#
# ⚠️ 四个坑（前两个是 memory 记过的，后两个是这一轮现场踩的）：
#   ① 注释里的路径会被当成真调用 ⇒ 必须剥注释；JS 的 `.` 不匹配 `\r`，
#      `//.*$` 在 CRLF 文件上整行存活 ⇒ 逐行剥且先去掉行尾 \r。
#   ② 前端是 `${id}`、后端是 `{id}` ⇒ 两边都归一成 `{}`；query 一律砍掉。
#   ③ **数花括号取命名空间块会提前截断**（模板串里的 `${...}`、字符串里的 `{`
#      都会干扰深度计数）⇒ 第一版 61 条里 58 条抽成 None，报出"59 个缺口"是假的。
#      改成行窗口法：`\b名字\s*=\s*\{(.*?)\n\}` 切块 + 在方法名之后 300 字符内取第一个
#      以 `/` 开头的串 —— 61 条全部抽到（下面 A1/A2 是这一条的打靶）。
#   ④ 静态扫描器**必须带正负对照**（本文 A/B/C 三段）。
import re, os, sys, glob
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = "D:/ABS"
API_FILES = ["academic-warning-frontend/src/api/index.js",
             "academic-warning-xcx/src/services/api.js"]

def strip_comments(text):
    out, in_block = [], False
    for raw in text.split('\n'):
        line = raw.rstrip('\r')
        if in_block:
            if '*/' in line:
                line = line.split('*/', 1)[1]; in_block = False
            else:
                out.append(''); continue
        while True:
            m = re.search(r'/\*.*?\*/', line)
            if m:
                line = line[:m.start()] + ' ' * (m.end() - m.start()) + line[m.end():]; continue
            m = re.search(r'/\*', line)
            if m:
                line = line[:m.start()]; in_block = True
            break
        q, cut = None, -1
        for i, ch in enumerate(line):
            if q:
                if ch == q: q = None
            elif ch in "'\"`":
                q = ch
            elif ch == '/' and i + 1 < len(line) and line[i + 1] == '/':
                cut = i; break
        out.append(line[:cut] if cut >= 0 else line)
    return '\n'.join(out)

def normalize(url):
    if not url: return None
    u = url.strip()
    if '/' not in u: return None
    u = u.split('?', 1)[0]
    u = re.sub(r'\$\{[^}]*\}', '{}', u)
    u = re.sub(r'\{[^}]*\}', '{}', u)
    if not u.startswith('/'): return None
    u = re.sub(r'/{2,}', '/', u)
    if len(u) > 1 and u.endswith('/'): return u[:-1]
    return u

def frontend_files():
    out = []
    for pat in ["academic-warning-frontend/src/**/*", "academic-warning-xcx/src/**/*"]:
        for p in glob.glob(os.path.join(ROOT, pat), recursive=True):
            if os.path.isfile(p) and os.path.splitext(p)[1].lower() in ('.js', '.vue', '.ts'):
                out.append((os.path.relpath(p, ROOT).replace(os.sep, '/'), p))
    return out

PATH_LIT = re.compile(r'''[`'"](/[A-Za-z0-9_\-/.\$\{\},]*(?:\$\{[^}]*\})?[A-Za-z0-9_\-/.\{\}]*)['"`]''')

def paths_in(text):
    res = {}
    for i, line in enumerate(strip_comments(text).split('\n'), 1):
        for m in PATH_LIT.finditer(line):
            n = normalize(m.group(1))
            if n and len(n) > 3:
                res.setdefault(n, []).append(i)
    return res

def wrapper_urls(api_rel, ns, names):
    src = open(os.path.join(ROOT, api_rel), encoding='utf-8', errors='replace').read()
    m = re.search(r'\b' + ns + r'\s*=\s*\{(.*?)\n\}', src, re.S)
    if not m: return {n: 'NO-NS' for n in names}
    body = m.group(1); out = {}
    for n in names:
        d = re.search(r'^  ' + re.escape(n) + r'\s*:', body, re.M)
        if not d:
            out[n] = 'NO-DEF'; continue
        u = re.search(r'[`\'"](\/[^`\'"]*)[`\'"]', body[d.end():d.end() + 300])
        out[n] = u.group(1) if u else 'NO-URL'
    return out

DEAD = {
 ("academic-warning-frontend/src/api/index.js","adminAPI"):
   ["disableUser","enableUser","exportInsufficientStudents"],
 ("academic-warning-frontend/src/api/index.js","aiAPI"):
   ["analyze","analyzeBehavior","analyzeClassScores","analyzeScores","analyzeWarning",
    "analyzeWarningTrend","assessRisk","generatePlan","generateSuggestion","generateWarning",
    "getRecommendations","getWarningSuggestions","predictRisk","predictWarnings"],
 ("academic-warning-frontend/src/api/index.js","authAPI"):
   ["getMajorsByCollege","registerStudent"],
 ("academic-warning-frontend/src/api/index.js","collegeAdminAPI"):
   ["getAssistance"],
 ("academic-warning-frontend/src/api/index.js","counselorAPI"):
   ["cancelClassManagement","compareClassWarnings","deleteAssistancePlan","getClassActivities",
    "getClassApplications","getClassWarningOverview","getCourseScoreDistribution",
    "getNotificationTemplates","getPlansByStudent","getStudentDetail","getWeeklyNotifications",
    "register","reviewClassApplication"],
 ("academic-warning-frontend/src/api/index.js","studentAPI"):
   ["getClassBenchmarkComparison","getClassMembersList","getMajorBenchmarkComparison","getMajorRanking",
    "getPendingAppeals","getPlanEvaluation","getProgressReport","getSecurityLogs","getStudentAppeals",
    "getStudentGPA","getUnreadNotificationCount","getUnreadNotifications","getUserSettings","register",
    "request","setPushChannel","subscribeWarningLevel","unsubscribeWarningLevel","updateUserSettings"],
 ("academic-warning-frontend/src/api/index.js","teacherAPI"):
   ["detectAnomalies","getCourseAnomalies","getCourseDetail","getCourseScoreDistribution",
    "getCourseStudents","getFeedback","getStudents","inspectCourseScoresForWarnings","register"],
}

all_hits = {}
for rel, p in frontend_files():
    for n, lines in paths_in(open(p, encoding='utf-8', errors='replace').read()).items():
        all_hits.setdefault(n, []).extend((rel, ln) for ln in lines)

# ---------------- 扫描器打靶（先证明它扫得到 / 也停得住）----------------
print("=" * 100)
print("0. 扫描器打靶")
print("=" * 100)
CTRL = [("/ai/chat",                 "正: 该有调用", True),
        ("/teachers/scores/anomalies","负: 零调用封装指向的端点, 不该有调用", False),
        ("/warnings/scan",           "负: W24 查过的零入口端点, 不该有调用", False),
        ("/teachers/scores",         "正: 高频端点", True)]
ctrl_ok = True
for path, label, expect in CTRL:
    outside = [f for f, _ in all_hits.get(path, []) if f not in API_FILES]
    got = len(outside) > 0
    ok = (got == expect)
    ctrl_ok &= ok
    print(f"  [{'OK ' if ok else 'BAD'}] {path:<30} {label:<44} 非封装文件的命中={len(outside)}")
assert ctrl_ok, "扫描器打靶未通过 —— 结论不可信"
# 抽 URL 的打靶
probe = wrapper_urls(*DEAD[0][0:1] + DEAD[0]) if False else None
u_t = wrapper_urls("academic-warning-frontend/src/api/index.js", "aiAPI", ["getResponse","analyzeScores","generatePlan"])
assert u_t["getResponse"] == "/ai/chat" and u_t["analyzeScores"].startswith("/ai/analyze-scores"), u_t
print(f"  [OK ] URL 抽取打靶: aiAPI.getResponse → {u_t['getResponse']}, aiAPI.analyzeScores → {u_t['analyzeScores']}")

# ---------------- 分类 ----------------
gap, second, unresolved = [], [], []
for (api_rel, ns), methods in DEAD.items():
    urls = wrapper_urls(api_rel, ns, methods)
    for m in methods:
        raw = urls[m]
        if not raw.startswith('/'):
            unresolved.append((ns, m, raw)); continue
        url = normalize(raw)
        others = [(f, ln) for f, ln in all_hits.get(url, []) if f != api_rel]
        (second if others else gap).append((ns, m, url, others))

print()
print(f"零调用封装 {sum(len(v) for v in DEAD.values())} 个：真缺口 {len(gap)} / 第二个入口 {len(second)} / 没抽到 {len(unresolved)}")
print()
print("=" * 100)
print("A. 真缺口 —— 后端端点 + 封装都在，全前端没有任何调用")
print("=" * 100)
for ns, m, url, _ in sorted(gap, key=lambda x: (x[0], x[1])):
    print(f"  {ns}.{m:<30} {url}")
if unresolved:
    print("\n未抽到 URL：", unresolved)
print()
print("=" * 100)
print("B. 第二个入口 —— 该端点别处有调用（功能不缺，只是多写了这条封装）")
print("=" * 100)
for ns, m, url, others in sorted(second, key=lambda x: (x[0], x[1])):
    where = ", ".join(f"{f.split('/')[-1]}:{ln}" for f, ln in others[:3])
    print(f"  {ns}.{m:<30} {url:<46} ← {where}")
