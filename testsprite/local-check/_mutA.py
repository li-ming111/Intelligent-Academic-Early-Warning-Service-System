# -*- coding: utf-8 -*-
p = 'warning-service/src/main/java/com/academic/task/WarningScheduledTask.java'
d = open(p, 'rb').read()
old = ('        if (!scanning.compareAndSet(false, true)) {\n'
       '            log.warn("已有一轮预警扫描在进行中，本次触发被跳过（不做排队）");\n'
       '            return new ScanResult(0, 0, true);\n'
       '        }\n').encode('utf-8')
assert d.count(old) == 1, d.count(old)
open(p, 'wb').write(d.replace(old, b''))
print('mutation A applied')
