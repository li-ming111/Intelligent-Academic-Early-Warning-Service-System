# -*- coding: utf-8 -*-
p = 'warning-service/src/main/java/com/academic/task/WarningScheduledTask.java'
d = open(p, 'rb').read()
old = ('        try {\n'
       '            return doScan(verbose);\n'
       '        } finally {\n'
       '            scanning.set(false);\n'
       '        }\n').encode('utf-8')
assert d.count(old) == 1, d.count(old)
new = ('        return doScan(verbose);\n').encode('utf-8')
open(p, 'wb').write(d.replace(old, new))
print('mutation B applied')
