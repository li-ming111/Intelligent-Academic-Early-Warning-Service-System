// 前端 API 封装 URL  vs  后端真实映射 —— 找「打向不存在的端点」的调用。
//
// 起因：W18-附 手工发现 `aiAPI.getSuggestions` 打 `GET /ai/suggestions/{userId}`，
// 而后端 AIController 只有 `@GetMapping("/student/{studentId}/suggestions")` ⇒ 调则 404。
// 那是**手捡**出来的，说明这一类从来没有被系统扫过。本脚本把它变成全量对照。
//
// 口径：
//   前端 baseURL = `/api`（PC `client.js`）；网关 `StripPrefix` 剥掉 `api`
//   ⇒ 前端封装的路径（去掉 query、把 `${…}` 归一成 `{}`）**应该逐字等于**
//     后端「类级 @RequestMapping + 方法级 @XxxMapping」拼出来的路径（把 `{var}` 归一成 `{}`）。
//
// ⚠️ 本脚本只判「路径存不存在」，**不判方法(GET/POST)对不对**，也**不判网关白名单/前缀角色闸**
//    （跨服务重名路径、`@RequestMapping` 多值别名另算）。所以它是**粗筛**：
//   报出来的每一条都要人工回读两端确认（本仓规矩：工具报的东西先问它凭什么这么说）。
//
// 用法: node check-api-contract.mjs
import { readFileSync, globSync } from 'node:fs'
import { readdirSync, statSync } from 'node:fs'
import { join, basename } from 'node:path'

const ROOT = process.cwd().replace(/\\/g, '/').replace(/\/testsprite\/local-check$/, '')

function walk(dir, out = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e)
    if (statSync(p).isDirectory()) walk(p, out)
    else out.push(p)
  }
  return out
}

// ⚠️ 必须先剥注释再抽 —— 本仓有大量「2026-10-02（W1.15）：删掉 `getAllCourses = () =>
// this._get('/auth/courses')`」式的**注释里写着被删掉的代码**，不剥注释就会把注释当成调用
// （第一版正是在这里报了一条假断链）。行内 `://` 不当注释（本仓无绝对 URL，保险起见留着）。
const stripComments = (s) => s
  .replace(/\r\n?/g, '\n')                    // ⚠️ 先归一 CRLF：JS 的 `.` 不匹配 `\r`，
  .replace(/\/\*[\s\S]*?\*\//g, '')           //    否则 `//.*$` 在 `\r` 前匹配失败、注释整行存活
  .split('\n')
  .map((l) => (l.includes('://') ? l : l.replace(/\/\/[^\n]*$/, '')))
  .join('\n')

const norm = (s) => s
  .replace(/\$\{[^}]*\}/g, '{}')   // 前端模板串 ${x}
  .replace(/\{[^}]*\}/g, '{}')     // 后端 {var}
  .replace(/\?.*$/, '')            // query
  .replace(/\/+$/, '')             // 尾斜杠
  .replace(/\/{2,}/g, '/')

// ---------------------------------------------------------------- 后端映射
const backend = new Map()   // 归一化路径 -> [{file, line, verb}]
const DUMP = join(ROOT, 'academic-microservices')
for (const f of walk(DUMP)) {
  if (!f.endsWith('Controller.java')) continue
  const src = readFileSync(f, 'utf8')
  const short = f.replace(/\\/g, '/').split('academic-microservices/')[1]

  // 类级前缀：先定位 class 声明，再往前找**最近的一个** @RequestMapping
  // （类上可能还压着 @RestController/@Slf4j/@RequiredArgsConstructor 等注解，
  //  所以不能要求 @RequestMapping 紧贴 `public class`）。
  let prefix = ''
  const clsAt = src.search(/\bpublic\s+(?:final\s+)?class\s+\w*Controller\b/)
  if (clsAt > 0) {
    const head = src.slice(0, clsAt)
    const all = [...head.matchAll(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?\{?([^)]*?)\}?\s*\)/g)]
    const last = all[all.length - 1]
    if (last) {
      const first = last[1].match(/"([^"]*)"/)
      if (first) prefix = first[1]
    }
  }

  const re = /@(Get|Post|Put|Delete|Patch)Mapping\s*(\(([^)]*)\))?/g
  let m
  while ((m = re.exec(src))) {
    const verb = m[1].toUpperCase()
    const args = m[3] || ''
    const paths = [...args.matchAll(/"([^"]*)"/g)].map((x) => x[1])
    const line = src.slice(0, m.index).split('\n').length
    if (!paths.length) {
      add(prefix)
    } else {
      for (const p of paths) add(prefix.replace(/\/$/, '') + (p.startsWith('/') ? p : '/' + p))
    }
    function add(p) {
      const key = norm(p)
      if (!backend.has(key)) backend.set(key, [])
      backend.get(key).push({ file: short, line, verb })
    }
  }
}

// ---------------------------------------------------------------- 前端封装
const FRONT = [
  [join(ROOT, 'academic-warning-frontend/src/api/index.js'), /apiClient\s*\.\s*(get|post|put|delete)\s*\(\s*(?:`([^`]*)`|'([^']*)'|"([^"]*)")/g],
  [join(ROOT, 'academic-warning-xcx/src/services/api.js'), /this\._(get|post|put|del|patch)\s*\(\s*(?:`([^`]*)`|'([^']*)'|"([^"]*)")/g],
]
const VERB = { get: 'GET', post: 'POST', put: 'PUT', delete: 'DELETE', del: 'DELETE', patch: 'PATCH' }

// `.vue` 页面里的**直连**调用（不经过 api/index.js 封装）。W20 手工数出 13 处、
// 逐条回读后端都命中；纳入自动扫描是为了以后新增的直连调用不会再逃掉。
// （xcx 侧未发现直连 —— 页面一律走 services/api.js。）
const DIRECT_RE = /apiClient\s*\.\s*(get|post|put|delete)\s*\(\s*(?:`([^`]*)`|'([^']*)'|"([^"]*)")/g
for (const base of [join(ROOT, 'academic-warning-frontend/src'), join(ROOT, 'academic-warning-xcx/src')]) {
  let files = []
  try { files = walk(base) } catch { continue }
  for (const f of files) {
    if (!f.endsWith('.vue')) continue
    if (readFileSync(f, 'utf8').includes('apiClient')) FRONT.push([f, DIRECT_RE])
  }
}

let calls = 0
const broken = []
const verbMismatch = []

// 网关 `RewritePath`（gateway-service/application.yml）—— 前端路径与后端映射**故意不同名**。
// 漏了这张表就会把它们当成断链（第一版正是如此）。
const REWRITE = {
  '/auth/colleges': '/colleges/all',
  '/auth/majors': '/majors/all',
  '/auth/classes': '/classes/all',
}

// 前端模板串里 `${…}` 有两种语义，必须分开处理（第一版混为一谈，报出一堆假断链）：
//   `${userId}` / `${courseId}`                → 路径变量 → `{}`
//   `${status ? '?status='+status : ''}`       → 拼 query   → 整段丢
const normFront = (raw) => raw
  .replace(/\$\{([^}]*)\}/g, (_, expr) =>
    /^\s*[A-Za-z_$][\w$.]*\s*$/.test(expr) ? '{}' : '')
  .replace(/(?:\{\})+/g, '{}')
  .replace(/\?.*$/, '')
  .replace(/\/+$/, '')
  .replace(/\/{2,}/g, '/')
for (const [file, re] of FRONT) {
  const src = stripComments(readFileSync(file, 'utf8'))
  const short = file.replace(/\\/g, '/').split('/src/')[1]
  let m
  re.lastIndex = 0                              // 同一条正则被多个文件复用，别把 lastIndex 带过去
  while ((m = re.exec(src))) {
    const verb = VERB[m[1]]
    const raw = m[2] ?? m[3] ?? m[4] ?? ''      // 三个引号分支哪个命中取哪个
    // `.vue` 直连常写成拼接（`'/admin/export/reports/' + name`）——闭引号后面紧跟 `+`
    // 说明路径还有一段动态的，补个 `{}`，否则会把真端点报成断链。
    const tail = src.slice(m.index + m[0].length, m.index + m[0].length + 16)
    let p = /^\s*\+/.test(tail) ? raw + '/{}' : raw
    if (!p.startsWith('/')) continue            // 相对片段，跳过
    p = p.replace(/^\/api(?=\/)/, '')            // 有的地方自己写了 /api
    const key = normFront(p)
    const line = src.slice(0, m.index).split('\n').length
    calls++
    // 先在网关改写表里解一层：`/auth/colleges` 真正打到的是 `/colleges/all`。
    // ⚠️ 解出来的目标**同样要核动词** —— 早期版本在改写分支里直接 continue，
    //    于是三条改写路径的动词从来没被检查过（本轮补上）。
    const rw = REWRITE[key]
    const hit = backend.get(key) || (rw ? backend.get(rw) : null)
    if (hit) {
      // 路径存在 —— 顺带核**动词**（前端写 post、后端只有 GET 也是静默 405）
      if (!hit.some((h) => h.verb === verb)) {
        verbMismatch.push({ file: short, verb, raw: raw.trim(), key, line, backend: hit })
      }
      continue
    }
    broken.push({ file: short, verb, raw: raw.trim(), key, line })
  }
}

console.log(`后端映射（归一化路径）: ${backend.size} 条`)
console.log(`前端封装调用          : ${calls} 处`)
console.log(`=> 找不到对应后端映射  : ${broken.length} 处\n`)

for (const b of broken) {
  console.log(`--- ${b.verb} ${b.raw}`)
  console.log(`    ${b.file}:${b.line}   归一后 = ${b.key}`)
  // 有没有「前缀相同」的近亲，便于人工判断是不是拼错
  const near = [...backend.keys()].filter((k) => k.startsWith(b.key.split('/').slice(0, 3).join('/'))).slice(0, 4)
  if (near.length) console.log(`    近亲路径: ${near.join('  ')}`)
}

console.log(`\n=> 路径对得上但**动词对不上** : ${verbMismatch.length} 处`)
for (const v of verbMismatch) {
  console.log(`--- 前端 ${v.verb} ${v.raw}`)
  console.log(`    ${v.file}:${v.line}   后端只有 ${v.backend.map((h) => h.verb).join('/')}（${v.backend[0].file}:${v.backend[0].line}）`)
}
