# W24 扫描器：前端 API 封装里「定义了但零调用点」的方法。
#
# 为什么要单独扫这个：api/index.js 里一条 `aiAPI.analyzeWarning: (id) => axios.get(...)`
# **既不是调用点、也不代表功能可用** —— 它只是一段可被调用的定义。W20 对照的是
# 「定义 × 后端映射」（断链 0），从来没问过「定义有没有人用」。
# 于是「封装 + 后端都齐、就是没人调」会一路静默：接口活着，功能是死的。
import re, glob, os, sys, json
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = "D:/ABS"
API_FILES = {
    "academic-warning-frontend/src/api/index.js",
    "academic-warning-xcx/src/services/api.js",
}

def frontend_files():
    out = []
    for pat in ["academic-warning-frontend/src/**/*", "academic-warning-xcx/src/**/*"]:
        for p in glob.glob(os.path.join(ROOT, pat), recursive=True):
            if not os.path.isfile(p):
                continue
            if os.path.splitext(p)[1].lower() not in ('.js', '.vue', '.ts'):
                continue
            rel = os.path.relpath(p, ROOT).replace(os.sep, '/')
            out.append((rel, p))
    return out

def scan_namespace(ns, api_rel):
    """返回 (定义的方法名列表)。"""
    path = os.path.join(ROOT, api_rel)
    src = open(path, encoding='utf-8').read()
    m = re.search(r'\b' + ns + r'\s*=\s*\{(.*?)\n\}', src, re.S)
    if not m:
        return []
    return re.findall(r'^\s{2}(\w+)\s*:', m.group(1), re.M)

def call_sites(ns, methods, api_rels):
    files = [(rel, p) for rel, p in frontend_files() if rel not in api_rels]
    rx = {m: re.compile(r'\b' + ns + r'\.' + re.escape(m) + r'\b') for m in methods}
    hits = {m: [] for m in methods}
    for rel, p in files:
        text = open(p, encoding='utf-8', errors='replace').read()
        for m, r in rx.items():
            n = len(r.findall(text))
            if n:
                hits[m].append((rel, n))
    return hits

def main():
    # 扫两个封装里的所有命名空间
    results = {}
    for api_rel in sorted(API_FILES):
        if not os.path.exists(os.path.join(ROOT, api_rel)):
            continue
        src = open(os.path.join(ROOT, api_rel), encoding='utf-8').read()
        namespaces = sorted(set(re.findall(r'^(?:export\s+)?const\s+(\w+API)\s*=', src, re.M)))
        for ns in namespaces:
            methods = scan_namespace(ns, api_rel)
            if not methods:
                continue
            hits = call_sites(ns, methods, API_FILES)
            dead = [m for m in methods if not hits[m]]
            results[f"{api_rel}::{ns}"] = {
                "total": len(methods),
                "dead": sorted(dead),
            }
    for key, r in results.items():
        print(f"{key}  共 {r['total']} 个方法, 零调用 {len(r['dead'])}")
        for d in r["dead"]:
            print(f"    - {d}")
    print()
    total_dead = sum(len(r["dead"]) for r in results.values())
    print("零调用点方法合计:", total_dead)

main()
