# W28 打靶：把「预警趋势」那条改回修之前的写法，确认新测试真的会红。
# 收尾 `--restore` 从 .bak 恢复并逐字节比对。
# ⚠️ 一律**二进制**读写（本仓踩过：python 文本模式会把 CRLF 静默抹成 LF）。
import sys, shutil, os

P = 'academic-microservices/ai-service/src/main/java/com/academic/controller/AIController.java'

FIXED = (
    '            if (aiAnalysis == null || aiAnalysis.isBlank()) {\n'
    '                return aiUnavailable("预警趋势分析");\n'
    '            }\n'
    '            Map<String, Object> result = new HashMap<>();\n'
    '            result.put("counselorId", counselorId);\n'
    '            result.put("aiAnalysis", aiAnalysis);'
).encode('utf-8')

BROKEN = (
    '            Map<String, Object> result = new HashMap<>();\n'
    '            result.put("counselorId", counselorId);\n'
    '            result.put("aiAnalysis", aiAnalysis != null ? aiAnalysis : "预警趋势分析中，请稍后重试");'
).encode('utf-8')

if '--restore' in sys.argv:
    shutil.copyfile(P + '.bak', P)
    if open(P, 'rb').read() == open(P + '.bak', 'rb').read():
        print('RESTORED, bytes identical')
    else:
        print('RESTORE FAILED'); sys.exit(1)
    os.remove(P + '.bak')
    sys.exit(0)

shutil.copyfile(P, P + '.bak')
b = open(P, 'rb').read()
assert b.count(FIXED) == 1, f'anchor not unique: {b.count(FIXED)}'
open(P, 'wb').write(b.replace(FIXED, BROKEN))
print('mutated; CRLF lines =', open(P, 'rb').read().count(b'\r\n'))
