# 把 §6.58 (W25) 与 §7 的 W25 条目插进手册, **按字节做、保持 CRLF**（同 W24 的 _w24_splice.py）。
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

HB = r"D:/ABS/答辩准备-启动与演示手册.md"
SEC = r"D:/ABS/testsprite/local-check/_w25_section.md"

d = open(HB, 'rb').read()
sec = open(SEC, 'rb').read().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n')
if not sec.endswith(b'\r\n'):
    sec += b'\r\n'
sec += b'\r\n'

MARK = '---\r\n## 7. 已知风险'.encode('utf-8')
i = d.find(MARK)
assert i > 0, "找不到 §7 前的 --- 分隔符"
assert '§6.58'.encode('utf-8') not in d, "§6.58 已存在, 拒绝重复插入"

BULLET = (
    '- [x] **第五十九轮 W25 —— 文档核对 + 一处新查到的「假成功」, 详见 §6.58。**\r\n'
    '  ① 把 `FEATURES.md` 改成事实（十处 + §八）：假路径、假表名、假加密方式，逐条核过代码；\r\n'
    '  ② 修好扫描器（它漏掉小程序整块、且剥注释把后半个文件吞成空行），两前端重新清点：\r\n'
    '  封装 513 / 零调用 82 / 真缺口 47 / 第二个入口 35；\r\n'
    '  ③ 顺带查到 `POST /students/register` 回 `success:true` + `"注册成功"` 却**一行都没写库**\r\n'
    '  （实测 users 35 / student_profile 24 打前打后一致）—— 与 W24 同族，本轮**只报告、未修**。\r\n'
).encode('utf-8')

W24 = '第五十八轮 W24'.encode('utf-8')
j = d.find(W24, i)
assert j > 0, "找不到 §7 里的 W24 条目"
j = d.rfind(b'\r\n', 0, j) + 2
assert d[j:j + 3] == b'- [', "W24 条目不是一行列表项的开头"

out = d[:i] + sec + d[i:j] + BULLET + d[j:]
open(HB, 'wb').write(out)

crlf = out.count(b'\r\n')
bare = out.count(b'\n') - crlf
print(f"写入完成: {len(d)} → {len(out)} 字节")
print(f"CRLF {crlf} / 裸 LF {bare}  (裸 LF 必须是 0)")
print(f"§6.58 出现 {out.count('§6.58'.encode('utf-8'))} 次, "
      f"W25 条目出现 {out.count('第五十九轮 W25'.encode('utf-8'))} 次")
