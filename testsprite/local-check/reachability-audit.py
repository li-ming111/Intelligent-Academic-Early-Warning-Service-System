"""可达性核对 v4：找出"有路由、有组件，却没有任何入口能走到"的页面。

⚠️ 两点关于本工具自身的实话（前一版的自述与代码不符，已改）：
  1. 下面的 NAV_DEFINED **不是**从布局文件里读出来的，是人工从 4 个 layout 的
     to=/index=/router.push 抄下来并逐个核过的**硬编码集合**。布局一改，这份表就过期，
     必须重新抄。之所以不用"扫字符串"自动取，是因为前两版自动化版本都错了：
     v1 按 `/` 前缀匹配 → 44 个假阳性；v2 认为任何带引号的字符串都是链接 →
     把 StudentLayout.vue 里 pageMap 的**标题键**当成链接；v3 只认导航构造 →
     漏掉 `:to="item.link"` 这种数据驱动的入口（/admin/permissions 其实是可达的）。
  2. 第 2 节的"其他文件引用数"是**子串匹配**，只能当线索：一个 .vue 只要在别处
     以字符串形式出现过就算命中。0 命中才值得人工确认，非 0 不代表真被引用。
"""

import re, io, os, glob

root = r'D:\ABS\academic-warning-frontend\src'
src = io.open(os.path.join(root, 'router', 'index.js'), encoding='utf-8').read()

# 权威导航目标：直接从 4 个 layout 的 to=/index=/router.push 抄下来（已人工核过）
NAV_DEFINED = set("""
/student/dashboard /student/scores /student/warnings /student/assistance /student/appeals
/student/messages /student/benchmark-analysis /student/settings
/teacher/dashboard /teacher/courses /teacher/scores /teacher/warnings /teacher/credit-prediction
/teacher/class-management /teacher/analysis /teacher/feedback /teacher/audit-log /teacher/settings
/counselor/dashboard /counselor/students /counselor/warnings /counselor/courses /counselor/scores
/counselor/class-management /counselor/assistance /counselor/analytics /counselor/communication
/counselor/notifications /counselor/settings
/admin/dashboard /admin/users /admin/colleges /admin/majors /admin/courses /admin/rules
/admin/statistics /admin/warnings /admin/messages /admin/data-export /admin/class-management /admin/settings
/admin/permissions
/college-admin/dashboard /college-admin/students /college-admin/teachers /college-admin/classes
/college-admin/counselors /college-admin/warnings /college-admin/scores /college-admin/analysis
/college-admin/export
/login /register
""".split())
# ↑ /admin/permissions 不来自左侧菜单，而是 AdminDashboard.vue:165 的
#   `link:'/admin/permissions'` 经 :to="a.link"（同文件 :102）渲染出来的**数据驱动入口**。
#   第一版漏了它、把它报成"无入口"，属于本工具自己的假阳性。

lines = src.split('\n')
parent, routes, in_routes = None, [], False
for i, ln in enumerate(lines, 1):
    if re.match(r'\s*const routes', ln):
        in_routes = True; continue
    if not in_routes:
        continue
    m = re.search(r"path:\s*'([^']*)'", ln)
    c = re.search(r"component:\s*(\w+)", ln)
    rr = re.search(r"redirect:", ln)
    if m:
        p = m.group(1)
        if p.startswith('/') and p.count('/') == 1 and p != '/':
            parent = p; full = p
        elif p.startswith('/'):
            full = p
        else:
            full = ((parent or '') + '/' + p).replace('//', '/')
        routes.append([full, None, i, False]); continue
    if rr and routes:
        routes[-1][3] = True
    if c and routes:
        routes[-1][1] = c.group(1)

LAYOUTS = {'StudentLayout', 'TeacherLayout', 'CounselorLayout', 'AdminLayout'}
print('== 【候选】有组件、不是布局、且不在已知导航集合里的路由（须逐个人工确认）==')
miss = []
for full, comp, ln, isredir in routes:
    if not comp or comp in LAYOUTS:
        continue
    if full in NAV_DEFINED:
        continue
    miss.append((full, comp, ln))
for full, comp, ln in miss:
    print('  %-36s -> %-30s :%d' % (full, comp, ln))

print('\n== 有组件但全仓 .vue 里没有任何 import/引用（真死文件）==')
router_imports = set(re.findall(r"import\s+(\w+)\s+from\s+'([^']+)'", src))
used = {n for n, _ in router_imports}
allvue = {f.replace(os.sep, '/') for f in glob.glob(os.path.join(root, 'views', '**', '*.vue'), recursive=True)}
imported_files = set()
for n, p in router_imports:
    imported_files.add(os.path.normpath(os.path.join(root, 'router', p)).replace(os.sep, '/'))
for f in sorted(allvue - imported_files):
    base = os.path.basename(f)
    hits = 0
    for g in glob.glob(os.path.join(root, '**', '*.*'), recursive=True):
        if g.endswith(f.replace('/', os.sep)) or g == f:
            continue
        if not g.endswith(('.vue', '.js')):
            continue
        t = io.open(g, encoding='utf-8', errors='replace').read()
        if base in t:
            hits += 1
    print('  %-46s 其他文件引用数=%d' % (f.replace(root.replace(os.sep, '/') + '/', ''), hits))
