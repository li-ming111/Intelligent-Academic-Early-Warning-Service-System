/**
 * 全仓前端「链接指向不存在的路由」检查（2026-10-02 W1.16）。
 *
 * 起因：`AdminDashboard.vue` 里两处 `to="/admin/class-management/pending-requests"`，
 * 而路由表里只有 `/admin/class-management` —— 点了没反应（vue-router 只打一条 warn）。
 * 这类缺陷**构建工具不报**（`vite build` 全绿），只有点的人才知道。
 *
 * 做法：
 *   ① 从 `src/router/index.js` 抽所有 `path:` 字符串，**按花括号深度**拼成绝对路径，
 *      **保留 `:param` 占位符**并编译成正则；
 *   ② 从所有 `.vue` 里抽 `<router-link to="…">`、`router.push('…')`、`router.replace('…')`
 *      以及对象字面量里的 `link:'…'` / `path:'…'`；
 *   ③ 带动态表达式（`{}`）的跳过；剩下的静态目标必须**完整匹配**某条路由正则。
 *
 * ⚠️ 踩过的两个坑（都是「检查器自己坏掉」，两次都是先打靶才发现的）：
 *
 *   坑 1（假阴性）：第一版判据是「`t.startsWith(route + '/')` 就算过」，
 *   用来放行 `/admin/users/123` 这种带参数的详情页 —— **靶子没打中就放过了**：
 *   把 bug 重新塞回去它照样报 OK，因为 `/admin/class-management/pending-requests`
 *   恰好以 `/admin/class-management/` 开头。现在改成**按路由正则完整匹配**，
 *   `/admin/users/:id` 能匹配 `/admin/users/123`，但匹配不了 `/admin/users/123/extra`。
 *
 *   坑 2（假阳性）：第二版判据修对了，但抽路由用的是「`path:` 必须在一行开头」
 *   （`/^(\s*)path:/`）。`/college-admin` 的 9 个子路由全写成了**行内对象**
 *   （`{ path: 'dashboard', name: … }`）⇒ 一条都没抽到 ⇒ 报出 18 个「死链」，
 *   而它们指向的路由**明明存在**。现在改成**扫花括号深度**（跳字符串/注释），
 *   行内与多行两种写法一视同仁。
 *
 * 用法：node testsprite/local-check/check-route-links.mjs
 */
import fs from 'node:fs'
import path from 'node:path'

const ROOT = 'D:/ABS/academic-warning-frontend/src'
const ROUTER = path.join(ROOT, 'router/index.js')

// ---------- 1. 抽路由表（按花括号深度，跳字符串与注释） ----------
const routerSrc = fs.readFileSync(ROUTER, 'utf8')

/**
 * 逐字符扫一遍源码，返回 `{ depth, path }` 列表。
 * `depth` 是**该 path 所属对象字面量所在的嵌套层数**（顶层路由对象 = 1，其 children = 2 …）。
 * 之所以要自己扫而不是正则切行：路由对象有行内（`{ path: 'x', … }`）与多行两种写法，
 * 只有按括号深度才一视同仁。
 */
function extractRoutes(src) {
  const out = []
  let depth = 0
  let i = 0
  while (i < src.length) {
    const c = src[i]
    const two = src.slice(i, i + 2)

    // 注释
    if (two === '//') { const nl = src.indexOf('\n', i); i = nl < 0 ? src.length : nl; continue }
    if (two === '/*') { const e = src.indexOf('*/', i + 2); i = e < 0 ? src.length : e + 2; continue }
    // 字符串（跳过，避免把字符串里的 {} 当成结构）
    if (c === "'" || c === '"' || c === '`') {
      i++
      while (i < src.length && src[i] !== c) { if (src[i] === '\\') i++; i++ }
      i++
      continue
    }
    if (c === '{') { depth++; i++; continue }
    if (c === '}') { depth--; i++; continue }

    // 只在**键**的位置认 path:（跳过 `redirect: '…'` 之类无关字段）
    const m = /^path:\s*'([^']*)'/.exec(src.slice(i, i + 200))
    if (m && /[\s,{]/.test(src[i - 1] ?? ',')) {
      out.push({ depth, raw: m[1] })
      i += m[0].length
      continue
    }
    i++
  }
  return out
}

const parsed = extractRoutes(routerSrc)

const routes = []          // 绝对路径字符串
const stack = []           // { depth, full }
for (const { depth, raw } of parsed) {
  while (stack.length && stack[stack.length - 1].depth >= depth) stack.pop()
  const parent = stack.length ? stack[stack.length - 1].full : ''
  let full = raw.startsWith('/') ? raw : (parent === '/' ? '' : parent) + '/' + raw
  full = full.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
  stack.push({ depth, full })

  // 通配兜底路由（`/:pathMatch(.*)*`）能匹配一切，不算具体目标，跳过
  if (full.includes('*') || full.includes('(.*)')) continue
  routes.push(full)
}

/** 把 `/admin/users/:id` 编译成 `^/admin/users/[^/]+$`。 */
function toRegExp(route) {
  const body = route
    .split('/')
    .map(seg => (seg.startsWith(':') ? '[^/]+' : seg.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
    .join('/')
  return new RegExp('^' + body + '/?$')
}
const matchers = routes.map(r => ({ raw: r, re: toRegExp(r) }))

// ---------- 2. 抽链接 ----------
const vueFiles = []
;(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p)
    else if (e.name.endsWith('.vue')) vueFiles.push(p)
  }
})(ROOT)

const PATTERNS = [
  /<router-link[^>]*\bto="([^"{}]+)"/g,
  /router\.(?:push|replace)\(\s*'([^'{}]+)'/g,
  /\blink:\s*'([^'{}]+)'/g,
  /\bpath:\s*'(\/[^'{}]*)'/g
]

const hits = []
for (const f of vueFiles) {
  const src = fs.readFileSync(f, 'utf8')
  for (const re of PATTERNS) {
    re.lastIndex = 0
    let m
    while ((m = re.exec(src))) {
      const target = m[1].trim()
      if (!target.startsWith('/')) continue
      hits.push({ file: path.relative('D:/ABS', f).replace(/\\/g, '/'), target })
    }
  }
}

// ---------- 3. 比对 ----------
const norm = s => s.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
const bad = []
const seen = new Set()
for (const h of hits) {
  const t = norm(h.target)
  const key = h.file + ' -> ' + t
  if (seen.has(key)) continue
  seen.add(key)
  if (!matchers.some(m => m.re.test(t))) bad.push({ ...h, target: t })
}

console.log(`路由表：${routes.length} 条路径（含 :param 占位符）`)
console.log(`扫描 ${vueFiles.length} 个 .vue，抽出 ${seen.size} 个静态跳转目标`)

if (bad.length === 0) {
  console.log('OK 所有静态跳转目标都能完整匹配到一条路由')
  process.exit(0)
}
console.log(`\n发现 ${bad.length} 个指向不存在路由的跳转：`)
for (const b of bad) console.log(`  ${b.file}  ->  ${b.target}`)
process.exit(1)
