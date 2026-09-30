// 静态核查：前端调用的 API 方法是否真的存在。
//
// 为什么需要它：冒烟脚本（smoke-pages.mjs）只跑"页面加载"这条路径，
// 只能发现 onMounted 里触发的调用；写在 @click 里的调用它一辈子也发现不了。
// 而 `xxxAPI.someMissingMethod(...)` 在点击时抛 TypeError，页面不会崩、控制台也不显眼，
// 属于"看着好好的、一按就报错"。本脚本不跑浏览器，纯读源码做交叉引用，秒级覆盖全部页面。
//
// 它还会做**可达性分析**：从 router/main 出发沿 import 图走一遍，
// 没有被任何入口引用到的文件（孤儿）单独列出——那些文件里的问题不会影响演示，
// 但和"真会被用户点到"的问题混在一起报，会让人误判严重性。
//
// 用法: node api-calls-check.mjs
// 退出码 0=没有悬空调用, 1=存在
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { join, relative, dirname, resolve } from 'node:path'

const FE = 'D:\\ABS\\academic-warning-frontend'
const SRC = join(FE, 'src')
const API_FILE = join(SRC, 'api', 'index.js')

// ---- 1. 解析 src/api/index.js，取出每个导出对象的方法名 ----
const apiSrc = readFileSync(API_FILE, 'utf8').split('\n')
const defined = new Map()   // 对象名 -> Set(方法名)
let cur = null
for (const line of apiSrc) {
  const open = line.match(/^export const (\w+)\s*=\s*\{/)
  if (open) { cur = open[1]; defined.set(cur, new Set()); continue }
  if (!cur) continue
  if (/^\}/.test(line)) { cur = null; continue }
  const m = line.match(/^\s{2}([a-zA-Z_][\w]*)\s*:/)
  if (m) defined.get(cur).add(m[1])
}

// ---- 2. 递归收集源文件 ----
const files = []
const walk = (dir) => {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p)
    else if (/\.(vue|js)$/.test(name)) files.push(p)
  }
}
walk(SRC)

// ---- 3. 建 import 图，从入口做可达性分析 ----
const resolveImport = (spec, fromFile) => {
  let base
  if (spec.startsWith('@/')) base = join(SRC, spec.slice(2))
  else if (spec.startsWith('.')) base = resolve(dirname(fromFile), spec)
  else return null                       // 三方包，忽略
  for (const cand of [base, base + '.vue', base + '.js', join(base, 'index.js')]) {
    if (existsSync(cand) && statSync(cand).isFile()) return cand
  }
  return null
}

const imports = new Map()   // 文件 -> Set(它 import 的文件)
for (const f of files) {
  const src = readFileSync(f, 'utf8')
  const set = new Set()
  for (const m of src.matchAll(/from\s+['"]([^'"]+)['"]/g)) {
    const r = resolveImport(m[1], f)
    if (r) set.add(r)
  }
  for (const m of src.matchAll(/import\s*\(\s*['"]([^'"]+)['"]\s*\)/g)) {
    const r = resolveImport(m[1], f)
    if (r) set.add(r)
  }
  imports.set(f, set)
}

// 入口：main.js + 它引到的一切。router 由 main.js 引到，所以不必单列。
const entries = ['main.js', 'App.vue'].map((n) => join(SRC, n)).filter(existsSync)
const reachable = new Set()
const queue = [...entries]
while (queue.length) {
  const f = queue.pop()
  if (reachable.has(f)) continue
  reachable.add(f)
  for (const dep of imports.get(f) || []) queue.push(dep)
}

// ---- 4. 找出全部 `xxxAPI.method(` 调用，逐个核对 ----
const API_NAMES = new Set(defined.keys())
const live = [], orphan = []
let callCount = 0

for (const f of files) {
  // 先把多行块注释 /** ... */ 的内容抹成空白（保留换行，行号才不会错位），
  // 再逐行去掉 // 注释。只按行剥 // 是不够的：本项目大量用块注释写"原实现调用过 xx"，
  // 那些名字会被误判成活调用——Statistics.vue 就因此吃过一次假阳性。
  const src = readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
  const lines = src.split('\n')
  lines.forEach((line, i) => {
    const code = line.replace(/\/\/.*$/, '')
    for (const m of code.matchAll(/\b(\w+API)\.([a-zA-Z_]\w*)\s*\(/g)) {
      const [, obj, method] = m
      if (!API_NAMES.has(obj)) continue
      callCount++
      if (!defined.get(obj).has(method)) {
        const rec = {
          file: relative(SRC, f).replace(/\\/g, '/'),
          line: i + 1,
          call: `${obj}.${method}`,
          reachable: reachable.has(f),
        }
        ;(rec.reachable ? live : orphan).push(rec)
      }
    }
  })
}

// ---- 5. 报表 ----
console.log(`扫描 ${files.length} 个源文件（其中 ${reachable.size} 个从入口可达），${callCount} 处 API 调用`)
console.log(`已定义的对象: ${[...defined.keys()].map((k) => `${k}(${defined.get(k).size})`).join('  ')}\n`)

const groupBy = (arr) => {
  const g = new Map()
  for (const p of arr) {
    if (!g.has(p.call)) g.set(p.call, [])
    g.get(p.call).push(p)
  }
  return [...g.entries()].sort()
}

if (live.length) {
  console.log(`✗ 会被用户触发的悬空调用：${live.length} 处`)
  for (const [call, list] of groupBy(live)) {
    console.log(`    ${call}`)
    for (const p of list) console.log(`        src/${p.file}:${p.line}`)
  }
  console.log()
}
if (orphan.length) {
  console.log(`· 孤儿文件里的悬空调用：${orphan.length} 处（文件未被任何入口引用，不影响演示）`)
  for (const [call, list] of groupBy(orphan)) {
    console.log(`    ${call}  ← ${[...new Set(list.map((p) => p.file))].join(', ')}`)
  }
  console.log()
}
if (!live.length && !orphan.length) {
  console.log('PASS  没有悬空调用：每个 xxxAPI.method() 都能在 src/api/index.js 里找到定义')
}
process.exit(live.length ? 1 : 0)
