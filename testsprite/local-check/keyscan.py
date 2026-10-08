import re, os, collections
ROOT = r'D:\ABS\academic-microservices'
FE = [r'D:\ABS\academic-warning-frontend\src', r'D:\ABS\academic-warning-xcx\src']

# 1) 后端：收集 "body 参数名.get/containsKey/getOrDefault(\"key\")"
param_re = re.compile(r'@RequestBody\s+Map<String,\s*Object>\s+(\w+)')
read_re  = re.compile(r'\b(\w+)\.(?:get|containsKey|getOrDefault|remove)\(\s*"([^"]+)"')
backend = collections.defaultdict(list)   # key -> [(file,line)]
for dirpath,_,files in os.walk(ROOT):
    if 'src\main\java' not in dirpath and '/src/main/java' not in dirpath: continue
    for f in files:
        if not f.endswith('.java'): continue
        p = os.path.join(dirpath,f)
        txt = open(p,encoding='utf-8',errors='replace').read()
        params = set(param_re.findall(txt))
        if not params: continue
        for i,line in enumerate(txt.split('\n'),1):
            for var,key in read_re.findall(line):
                if var in params:
                    backend[key].append((os.path.relpath(p,ROOT),i,var))

# 2) 前端：全文出现次数（粗），以及带引号的字面出现次数
fe_txt = []
for d in FE:
    for dirpath,_,files in os.walk(d):
        for f in files:
            if f.endswith(('.js','.vue','.ts')):
                fe_txt.append(open(os.path.join(dirpath,f),encoding='utf-8',errors='replace').read())
fe_all = '\n'.join(fe_txt)

print(f"后端 body 读取到的 key 共 {len(backend)} 个\n")
zero = []
for k, hits in sorted(backend.items()):
    n = len(re.findall(r'["\']'+re.escape(k)+r'["\']', fe_all))
    if n == 0:
        zero.append((k,hits))
print(f"=== 前端源码里【一次都没出现过】的 key（{len(zero)} 个）===")
for k,hits in zero:
    locs = ', '.join(f"{f}:{l}({v})" for f,l,v in hits[:4])
    print(f'  "{k}"  ← {locs}')
