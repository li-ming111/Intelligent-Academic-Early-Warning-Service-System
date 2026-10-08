# -*- coding: utf-8 -*-
"""扫「SQL 里用等号比较一个**可空**参数」的候选 —— 传 null 时 `= NULL` 恒假，
于是"不传/传空 = 全部"的语义在 SQL 层根本没实现，接口静默返回空数组或 0。

做法：把每个 `.java` 源文件里的 SQL 字符串片段（@Select/@Update/@Delete/@Insert 的
字符串，含 `<script>` 多行注解）抽出来，找 `xxx_id = #{yyy}` 的形状，并打印所在文件行。
之后**人工**判断那个 `#{yyy}` 对应的参数是不是 nullable（`required=false` / 无 `@RequestParam`
的包装类型 / service 方法允许 null）。

用法: python _scan_null_eq.py
"""
import io, glob, os, re, sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))

ANN = re.compile(r'@(Select|Update|Delete|Insert)\s*\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)', re.S)
STR = re.compile(r'"((?:[^"\\]|\\.)*)"')
EQ = re.compile(r'([A-Za-z_][\w.]*)\s*=\s*#\{\s*([\w.]+)\s*\}')

hits = 0
files = sorted(glob.glob('academic-microservices/*/src/main/java/**/*.java', recursive=True))
for p in files:
    s = io.open(p, encoding='utf-8', errors='replace').read()
    if '@Select' not in s and '@Update' not in s:
        continue
    for m in ANN.finditer(s):
        sql = ''.join(STR.findall(m.group(2)))
        if '=' not in sql:
            continue
        for e in EQ.finditer(sql):
            col, param = e.group(1), e.group(2)
            if not col.lower().endswith('id'):
                continue
            ln = s[:m.start()].count('\n') + 1
            short = p.replace(os.sep, '/').split('academic-microservices/')[-1]
            print('%-56s :%-5d %-22s = #{%s}' % (short, ln, col, param))
            hits += 1
print('\n候选(列名以 id 结尾且用等号比 #{}): %d' % hits)
