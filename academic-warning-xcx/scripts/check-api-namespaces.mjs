/**
 * `xxxAPI.foo(...)` 的调用点 ↔ `services/api.js` 里真实存在的方法 的一致性核对。
 *
 * 为什么要脚本化：api.js 里是 `studentAPI / teacherAPI / counselorAPI / adminAPI /
 * collegeAdminAPI` 五个命名空间、近 200 个方法，页面里按 `xxxAPI.foo()` 调。
 * **调到一个不存在的方法不会有任何构建期报错** —— 只会在运行时抛 `TypeError`，
 * 而页面普遍是 `try { ... } catch (e) { }` 吞掉，表现就是"列表永远空、也不报错"。
 * 同型的历史缺陷：某个方法其实挂在**别的命名空间**下（`getMessages` 只在 student/teacher 有，
 * `getStatistics` 只在 admin 有），所以 `adminAPI.getMessages` 是死的。
 *
 * 手工抽查只能抽样，这里是穷举：扫 src/ 下所有 .vue/.js，把每个 `\w+API\.\w+(` 都取出来对。
 *
 * 用法：node scripts/check-api-namespaces.mjs      （在 academic-warning-xcx 目录下执行）
 * 退出码：0 = 所有调用点都有对应实现；1 = 有调不到的方法或没有的命名空间
 *
 * ⚠️ 改这个脚本时先打靶：往 src/ 下临时放一个 `<script>` 里写
 * `studentAPI.thisMethodDoesNotExist()` 的 .vue，确认它会红，再删掉。
 * 否则"干净仓库上返回 0"证明不了任何事（正则一行都没匹配上也是 0）。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(here, '..')
const API = path.join(ROOT, 'src', 'services', 'api.js')

const apiSrc = fs.readFileSync(API, 'utf8')

// ---- 1. 命名空间：`export const studentAPI = apiClient.student` ----
const alias = new Map() // 导出名 -> apiClient 上的属性名
for (const m of apiSrc.matchAll(/export const (\w+) = apiClient\.(\w+)/g)) {
  alias.set(m[1], m[2])
}
if (alias.size === 0) {
  console.error('!! 一个命名空间都没解析出来 —— api.js 的导出写法变了，先修这个脚本')
  process.exit(1)
}

// ---- 2. 每个命名空间里挂了哪些方法 ----
// api.js 的结构是 `  <prop> = {` 开头、内层 4 空格缩进的 `name: (..)` / `async name(..)`
const namespaces = new Map() // prop -> Set(method)
{
  const starts = [...apiSrc.matchAll(/^\s{2}(\w+)\s*=\s*\{$/gm)]
  for (let i = 0; i < starts.length; i++) {
    const from = starts[i].index
    const to = i + 1 < starts.length ? starts[i + 1].index : apiSrc.length
    const methods = new Set()
    for (const mm of apiSrc.slice(from, to).matchAll(/^\s{4}(?:async\s+)?(\w+)\s*[:(]/gm)) {
      methods.add(mm[1])
    }
    namespaces.set(starts[i][1], methods)
  }
}

// ---- 3. 扫调用点 ----
function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (/\.(vue|js)$/.test(e.name)) out.push(p)
  }
  return out
}

const calls = new Map() // ns -> Map(method -> Set(file))
let totalCalls = 0
for (const f of walk(path.join(ROOT, 'src'))) {
  const src = fs.readFileSync(f, 'utf8')
  for (const m of src.matchAll(/\b(\w+API)\.(\w+)\s*\(/g)) {
    totalCalls++
    const [, ns, method] = m
    if (!calls.has(ns)) calls.set(ns, new Map())
    const per = calls.get(ns)
    if (!per.has(method)) per.set(method, new Set())
    per.get(method).add(path.relative(ROOT, f).replace(/\\/g, '/'))
  }
}

// ---- 4. 判定 ----
const lines = []
lines.push('命名空间（services/api.js 导出 -> apiClient 属性 -> 方法数）')
for (const [ns, prop] of alias) {
  const s = namespaces.get(prop)
  lines.push(`  ${ns} -> apiClient.${prop}${s ? `  (${s.size} 个方法)` : '  ** 这个属性不存在 **'}`)
}
lines.push(`调用点合计 ${totalCalls} 处，覆盖 ${calls.size} 个命名空间`)

const bad = []
for (const [ns, per] of [...calls].sort()) {
  const prop = alias.get(ns)
  if (prop === undefined) {
    for (const [method, where] of per) {
      bad.push(`  ${ns}.${method}()  <- ${[...where].join(', ')}   [命名空间 ${ns} 根本没导出]`)
    }
    continue
  }
  const pool = namespaces.get(prop) ?? new Set()
  for (const [method, where] of per) {
    if (pool.has(method)) continue
    const elsewhere = [...namespaces].filter(([n, s]) => s.has(method)).map(([n]) => n)
    bad.push(`  ${ns}.${method}()  <- ${[...where].join(', ')}` +
      (elsewhere.length ? `   [同名方法挂在: ${elsewhere.join('/')} 下]` : '   [全库都没有这个方法]'))
  }
}

if (bad.length) {
  lines.push('')
  lines.push(`--- 调不到的方法/命名空间 (${bad.length}) ---`)
  lines.push(...bad)
}

console.log(lines.join('\n'))
console.log('')
console.log(bad.length === 0
  ? '==== PASS：所有 xxxAPI.foo() 都有对应实现 ===='
  : '==== FAIL：有调用点在运行时必然 TypeError ====')
process.exit(bad.length === 0 ? 0 : 1)
