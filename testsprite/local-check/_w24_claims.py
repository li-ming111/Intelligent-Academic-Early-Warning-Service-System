# W24 扫描器：**回话里宣称"做了一件事"，方法体里却没有任何写操作** 的端点。
#
# 为什么扫这个：/teachers/scores/warnings 回「已为 N 名学生生成预警」，而它只数了数分数、
# 一行都没落库。这一族（"假成功 / 名字骗人的接口"）在本仓反复出现，值得用一条可复现的
# 扫描把它一次清干净，而不是靠某个下午的直觉。
#
# 判据（两条都命中才报）：
#   A. 方法体里**没有**任何写调用
#   B. 方法体里有**宣称已完成的字面量**（成功/已生成/已发送/已保存/已同步/…）
#
# ⚠️ 两个坑（都是我在这一轮现学的）：
#   1. **写动词不能"紧跟左括号"** —— `service.updateProfile(...)` 里 `.update` 后面不是 `(`，
#      第一版因此漏判，34 条里绝大多数是假阳性。改成"动词出现在方法名里"。
#   2. **剥注释会让行号整体前移** —— 第一版拿剥完注释的串算行号，报出来的位置全是错的。
#      改成把注释内容**替换成等长空格**，位置一一对应。
import re, glob, os, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = "D:/ABS"
# 写动词出现在**方法名里**就算写（insert/update/save/remove/delete/add/create/modify + 任意前后缀）。
# 刻意不加大写变体：`getUpdatedAt`、`getCreatedAt` 这类 getter 因此不会被误认成写。
WRITE_CALL = re.compile(r'\.\w*(?:insert|update|save|remove|delete|add|create|modify)\w*\s*\(')
CLAIM = re.compile(r'(成功|已生成|已发送|已保存|已同步|已删除|已导入|已处理|已完成|已推送|已创建|已更新|已提交|已解除|已通过|已恢复|已重置)')

def blank_comments(src):
    """把注释内容替换成等长空格（换行保留），这样任何下标都能直接映射回原文。"""
    def repl(m):
        return re.sub(r'[^\n]', ' ', m.group(0))
    src = re.sub(r'/\*.*?\*/', repl, src, flags=re.S)
    return re.sub(r'//[^\n]*', repl, src)

def methods(src):
    for m in re.finditer(r'\n    (?:(?:public|private|protected|@)[^\n]*\n    )*(?:public|private|protected)[^\n]*\(', src):
        i = src.find('{', m.end() - 1)
        if i < 0:
            continue
        depth = 0
        for j in range(i, len(src)):
            if src[j] == '{':
                depth += 1
            elif src[j] == '}':
                depth -= 1
                if depth == 0:
                    yield src[m.start():j + 1], m.start()
                    break

def main():
    hits, total = [], 0
    for f in glob.glob(os.path.join(ROOT, "academic-microservices/*/src/main/java/**/controller/*.java"), recursive=True):
        raw = open(f, encoding='utf-8', errors='replace').read()
        src = blank_comments(raw)          # 与 raw 逐字符对齐
        for body, off in methods(src):
            if WRITE_CALL.search(body):
                continue
            claims = sorted(set(CLAIM.findall(body)))
            if not claims:
                continue
            total += 1
            head = re.search(r'(?:public|private|protected)[^\n(]*\([^\n]*', body)
            sig = head.group(0).strip()[:100] if head else body[:80].strip()
            hits.append((os.path.relpath(f, ROOT).replace(os.sep, '/'), raw[:off].count('\n') + 1, sig, claims))
    print(f"命中 {total} 处（宣称做了事，但方法体无写调用）\n" + "=" * 100)
    for f, line, sig, claims in hits:
        print(f"\n{f}:{line}\n  {sig}\n  宣称: {claims}")

main()
