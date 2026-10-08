# 把 §6.57 (W24) 与 §7 的 W24 条目插进手册, **按字节做、保持 CRLF**。
# 为什么不用文本模式: memory 记着 W21 用文本模式批量改标签把 CRLF 静默抹成 LF(2871 → 0),
# 而自检的 `grep -c $'\r'` 在 MSYS bash 里 $'\r' 未展开成空模式 ⇒ 报"每一行都有 CR"(假绿)。
# 这里一律字节级操作, 并用 b.count(b'\r\n') / b.count(b'\n') 判行尾。
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

HB = r"D:/ABS/答辩准备-启动与演示手册.md"
SEC = r"D:/ABS/testsprite/local-check/_w24_section.md"

d = open(HB, 'rb').read()
sec = open(SEC, 'rb').read().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n')
if not sec.endswith(b'\r\n'):
    sec += b'\r\n'
sec += b'\r\n'

MARK = '---\r\n## 7. 已知风险'.encode('utf-8')
i = d.find(MARK)
assert i > 0, "找不到 §7 前的 --- 分隔符"
assert '§6.57'.encode('utf-8') not in d, "§6.57 已存在, 拒绝重复插入"

BULLET = (
    '- [x] **第五十八轮 W24 —— 一个「触发了预警」的按钮, 一行都没落库, 详见 §6.57。**\r\n'
    '  端点 `POST /teachers/scores/warnings` 回的是「已为 N 名学生生成预警」, 而方法体里**一行都没写库** ——\r\n'
    '  只数了数低于 60 分的人数。改法是**把说法改成事实**: 删掉冒充动作的 `warningsTriggered`,\r\n'
    '  换 `studentsBelowPassLine` + `passLine`, message 明说「只做统计、不生成预警记录」。\r\n'
    '  另: 真正的全库重扫 `POST /warnings/scan` 在 PC 与小程序里**都没有入口**(grep 零命中), 本轮**只报告、不动它**。\r\n'
).encode('utf-8')

W23 = '第五十七轮 W23'.encode('utf-8')
j = d.find(W23, i)
assert j > 0, "找不到 §7 里的 W23 条目"
# 回退到该行行首
j = d.rfind(b'\r\n', 0, j) + 2
assert d[j:j + 3] == b'- [', "W23 条目不是一行列表项的开头"

out = d[:i] + sec + d[i:j] + BULLET + d[j:]
open(HB, 'wb').write(out)

crlf = out.count(b'\r\n')
bare = out.count(b'\n') - crlf
print(f"写入完成: {len(d)} → {len(out)} 字节")
print(f"CRLF {crlf} / 裸 LF {bare}  (裸 LF 必须是 0)")
print(f"§6.57 出现次数: {out.count('§6.57'.encode('utf-8'))}, W24 条目出现次数: {out.count('第五十八轮 W24'.encode('utf-8'))}")
