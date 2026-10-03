// 读侧横向越权(读 IDOR)**全量**扫描 —— 补 `probe-idor-cross-user.mjs` 的两处判据缺陷。
//
// 为什么另写一个(本轮亲历, 两处都是"工具自己错"):
//   旧探针的 `shape` 是这么算的:
//       let shape = typeof d                                   //  d = body.data
//       if (Array.isArray(d)) … else if (d && typeof d==='object') shape = `object{…}`
//   于是 `d === null` 时 shape 停在 `'object'` —— 也就是说 **`shape === 'object'` 当且仅当 data 为 null**。
//   而它的越权判据写的是 `r.shape === 'object' && r.http === 200`, 正好把这个"没数据"读成了"有数据":
//     · 假阴性: dashboard / scores/audit / scores/logs 回的是**真对象**(shape `object{…}`),
//       判据一眼都不看 ⇒ 那几个端点的越权**从来没被报出来过**。
//     · 假阳性: 任何 200 + `data:null` 的端点都会被报成越权。
//   (本轮实跑: 6 条确证泄漏都是 `array(N>0)`, 侥幸没踩到假阳性; 但假阴性是真的漏了。)
//
// 所以本探针不猜"有没有数据", 而是**三方对照**:
//     self  = 调用者看**自己**      (token A → /x/{A})
//     cross = 调用者看**别人**      (token A → /x/{B})     ← 被指控的那次
//     own   = 别人看**别人自己**    (token B → /x/{B})     ← 真值: B 合法视角
//   越权 ⟺ cross 的内容**与 own 逐字一致**(拿到了对方的视角) **且与 self 不同**(确实不是自己的)。
//   这套判据不依赖字段名、也不依赖"有没有数据"的猜测 —— 空数组会在 nonEmpty 那一步出局,
//   `{success:true}` 这类壳子会因 self 与 cross 相同而出局。
//
// 端点清单**从源码抽**(不是手写清单): 手写清单会随代码漂移, 这正是旧工具过期的成因。
// 只收路径变量属于「归属人 id」的那一族(studentId/userId/teacherId/counselorId)。
//
// 只发 GET, 且排除有副作用的端点(export 等 —— 会写 export_history)。
//
// 用法: node probe-idor-read-sweep.mjs        退出码 0(取证); 有泄漏则打印清单
import { readFileSync } from 'node:fs'
import { captchaFields } from './_captcha.mjs'

const G = 'http://localhost:8076'
const SRC = 'D:/ABS/academic-microservices'

// 路径变量里属于「归属人 id」的那一族。`{appealId}`/`{planId}` 之类是**资源 id**,
// 它们的归属校验是另一回事(要看那一行属于谁), 不在本扫描的射程内 —— 分开记, 不混进结论。
const OWNER_VAR = /^(student|user|teacher|counselor)Id$/
// 有副作用 / 非 JSON 的端点, 一律不发。
const SKIP = /export|rescan|scan|refresh|sync|generate|rebuild|recalc|repair|cache/i

// ------------------------------------------------------------------ 从源码抽端点
function extract(file, prefix) {
  const src = readFileSync(`${SRC}/${file}`, 'utf8')
  const out = []
  // 抓 @GetMapping("…") 之后到方法体 `{` 之前的那一小段(签名)
  const re = /@GetMapping\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)([\s\S]{0,500}?)\{/g
  let m
  while ((m = re.exec(src)) !== null) {
    const path = m[1]
    const sigText = m[2]
    if (SKIP.test(path)) continue
    const vars = [...path.matchAll(/\{(\w+)\}/g)].map((x) => x[1])
    const ownerVars = vars.filter((v) => OWNER_VAR.test(v))
    if (ownerVars.length === 0) continue
    // 路径变量若不止一个归属人 id, 情况复杂, 标出来不猜
    if (ownerVars.length > 1) continue
    out.push({ prefix, path, varName: ownerVars[0], via: 'path' })
  }
  // @RequestParam 形态(如 /teachers/class-management/requests?teacherId=)
  const re2 = /@GetMapping\(\s*"([^"]+)"\s*\)([\s\S]{0,500}?)@RequestParam\([^)]*\)\s*Long\s+(\w+)/g
  while ((m = re2.exec(src)) !== null) {
    if (SKIP.test(m[1])) continue
    if (!OWNER_VAR.test(m[3])) continue
    out.push({ prefix, path: m[1], varName: m[3], via: 'query' })
  }
  return out
}

const endpoints = [
  ...extract('student-service/src/main/java/com/academic/controller/StudentController.java', '/api/students'),
  ...extract('teacher-service/src/main/java/com/academic/controller/TeacherController.java', '/api/teachers'),
  ...extract('counselor-service/src/main/java/com/academic/controller/CounselorController.java', '/api/counselors'),
]

// ------------------------------------------------------------------ HTTP
const login = async (u, p = '123456') => {
  const r = await fetch(G + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: u, password: p, ...(await captchaFields(G)) }),
  }).then((x) => x.json())
  return r?.data?.token || null
}

const get = async (url, token) => {
  try {
    const r = await fetch(url, { headers: { Authorization: 'Bearer ' + token } })
    const t = await r.text()
    let j = null
    try { j = JSON.parse(t) } catch {}
    return { http: r.status, code: j?.code, data: j?.data, raw: t.length }
  } catch (e) { return { http: 'ERR', code: e.message, data: null, raw: 0 } }
}

// 内容签名：能区分"同一份数据"与"不同数据", 且不猜有没有数据。
//
// ⚠️ 光用**键名**不够 —— 第一版 object 签名只取 `Object.keys()`, 于是学生 27 和 30 的
// dashboard 键集**完全一样**(都含 studentId/studentName, 只是值不同) ⇒ 三方对照判成"未定",
// 越权**漏报**。所以要把**身份类的值**也签进签名里。
const IDKEY = /^(id|.*Id|.*Name|.*No|.*no|username|realName)$/
const identityBits = (d, depth = 0) => {
  if (depth > 3) return ''
  const bits = []
  const walk = (o) => {
    if (Array.isArray(o)) { o.slice(0, 4).forEach(walk); return }
    if (!o || typeof o !== 'object') return
    for (const k of Object.keys(o)) {
      const v = o[k]
      if (IDKEY.test(k) && (typeof v === 'string' || typeof v === 'number')) bits.push(`${k}=${v}`)
      else if (v && typeof v === 'object') walk(v)
    }
  }
  walk(d)
  // 排序 ⇒ 与数组顺序无关(比的是多重集); 截断 ⇒ 免得签名无限长
  return [...new Set(bits)].sort().slice(0, 12).join('|')
}

const sig = (d) => {
  if (d === null || d === undefined) return 'null'
  if (Array.isArray(d)) {
    const ids = d.map((x) => (x && typeof x === 'object' ? (x.id ?? null) : null))
    const all = d.length > 0 && ids.every((v) => v !== null)
    const bits = identityBits(d)
    return `array(${d.length})${all ? ' ids[' + [...ids].sort((a, b) => a - b).join(',') + ']' : ''}${bits ? ' ' + bits : ''}`
  }
  if (typeof d === 'object') {
    const extra = []
    for (const k of ['list', 'records', 'rows', 'items']) if (Array.isArray(d[k])) extra.push(`${k}(${d[k].length})`)
    if (d.total !== undefined) extra.push(`total(${d.total})`)
    const bits = identityBits(d)
    return `object{${Object.keys(d).sort().join(',')}}${extra.length ? ' ' + extra.join(' ') : ''}${bits ? ' ' + bits : ''}`
  }
  if (typeof d === 'string') return `string(${d.length})${d.length <= 40 ? ' ' + d : ''}`
  return typeof d
}

// 第二判据：**全量 JSON**（键排序后）逐字比对。
//
// 为什么还要它：上面的 `sig` 对"键名相同、只有标量值不同"的响应是盲的 ——
// 实测 `/notification-center/{uid}/unread-count` 三个响应签名都是 `object{unreadCount}`、
// `/teachers/messages/{uid}/unread-count` 都是 `number` ⇒ 越权**漏报**。
// 全量比对把它们抓出来（cross 与 own 连值都一样、且与 self 不同 = 铁证）。
// 代价：响应里若含时间戳之类的易变字段, cross 与 own 会不等 ⇒ 退回"未定"(漏报方向, 安全)。
const canon = (d) => {
  const walk = (o) => {
    if (Array.isArray(o)) return o.map(walk)
    if (o && typeof o === 'object') {
      const r = {}
      for (const k of Object.keys(o).sort()) r[k] = walk(o[k])
      return r
    }
    return o
  }
  try { return JSON.stringify(walk(d)) } catch { return '?' }
}

const nonEmpty = (d) => {
  if (d === null || d === undefined) return false
  if (Array.isArray(d)) return d.length > 0
  if (typeof d === 'object') {
    for (const k of ['list', 'records', 'rows', 'items']) if (Array.isArray(d[k])) return d[k].length > 0
    if (d.total !== undefined) return Number(d.total) > 0
    return Object.keys(d).length > 0
  }
  if (typeof d === 'string') return d.trim().length > 0
  return true
}

// ⚠️ 两套 id 空间 —— 本轮我自己的工具就在这里栽过一次：
// `/students/subscription/**` 的路径参数是 **student_profile.id**（司明 3 / 邱琳紫 4），
// 其余 `/students/**` 全是 **users.id**（27 / 30）。第一版给订阅族也传了 users.id ⇒
// 两次调用都回 `{success:false,message:"学生档案不存在: 30"}`，**cross 与 own 的失败串逐字相同**
// ⇒ 被全量判据当成"越权"报了 **2 条假阳性**。所以这里按前缀分别取 id。
const PROFILES = { 27: 3, 30: 4 }   // users.id -> student_profile.id
const isProfileSpace = (ep) => ep.prefix === '/api/students' && ep.path.startsWith('/subscription/')
const idFor = (ep, userId) => (isProfileSpace(ep) ? (PROFILES[userId] ?? userId) : userId)

// 有些端点的必填**查询参数**不在抽取范围内（本扫描只看路径变量）⇒ 不给就 400，
// 那样只会得到一条"未定"，等于把这块地方留成盲区。这里按路径补上合理默认值。
const EXTRA_Q = {
  '/appeals/{studentId}/by-status': 'status=0',
  '/benchmark/{studentId}/semester': 'semester=5',
  '/settings/{studentId}/security-logs': 'page=1&pageSize=10',
}

const url = (ep, userId) => {
  const id = idFor(ep, userId)
  const base = G + ep.prefix + ep.path.replace(/\{\w+\}/g, String(id))
  const qs = []
  if (ep.via === 'query') qs.push(`${ep.varName}=${id}`)
  if (EXTRA_Q[ep.path]) qs.push(EXTRA_Q[ep.path])
  return base + (qs.length ? '?' + qs.join('&') : '')
}

// ------------------------------------------------------------------ 跑
console.log('扫描端点来源: 从三个 controller 源码抽 @GetMapping（不是手写清单）')
console.log(`共抽出「归属人 id 型」端点 ${endpoints.length} 条（已排除 export 等有副作用的）\n`)

const tok = {}
for (const [u, label] of [['2023020616', '学生27'], ['2023020615', '学生30'],
                          ['teacher001', '教师18'], ['10001', '教师29']]) {
  tok[u] = await login(u)
  if (!tok[u]) { console.log(`拿不到 ${label} 的令牌，退出`); process.exit(2) }
}
const T = { s27: tok['2023020616'], s30: tok['2023020615'], t18: tok['teacher001'], t29: tok['10001'] }

const leaks = [], guarded = [], inconclusive = [], undecidable = []

async function pair(ep, callerTok, callerId, otherTok, otherId, tag) {
  const u = (id) => url(ep, id)
  const self = await get(u(callerId), callerTok)
  const cross = await get(u(otherId), callerTok)
  const own = await get(u(otherId), otherTok)

  const selfSig = sig(self.data), crossSig = sig(cross.data), ownSig = sig(own.data)
  const selfFull = canon(self.data), crossFull = canon(cross.data), ownFull = canon(own.data)

  const bySig = crossSig === ownSig && crossSig !== selfSig
  const byFull = crossFull === ownFull && crossFull !== selfFull
  const leak = String(cross.http) === '200' && nonEmpty(cross.data) && (bySig || byFull)
  const how = bySig && byFull ? '签名+全量一致' : bySig ? '签名一致' : byFull ? '全量一致(签名盲区)' : ''

  const line = `  ${leak ? '!!' : '  '} ${tag} ${ep.via === 'query' ? ep.path + '?' + ep.varName : ep.path}\n` +
    `       self(${callerId}) http=${self.http} ${selfSig}\n` +
    `       cross(${otherId}) http=${cross.http} ${crossSig}\n` +
    `       own(${otherId}) http=${own.http} ${ownSig}`

  if (leak) { leaks.push(line); console.log(line + `   <-- 越权：${how}`) }
  else if (cross.http === 403) { guarded.push(line); console.log(line + '   (拦住了: 403)') }
  else if (String(cross.http) === '200' && !nonEmpty(own.data)) {
    // ⚠️ 关键区分："对方自己没有数据" ≠ "没问题"。这条**测不出来**，不能读成安全。
    //    旧探针把这种和"拦住了"混在一起，正是"0 条被读成拦住了"那一族。
    undecidable.push(line); console.log(line + '   (测不出: 对方本身没数据)')
  } else {
    inconclusive.push(line); console.log(line + '   (未定: cross 与 own 不一致)')
  }
}

console.log('===== A) 学生 27 令牌 → 学生 30 的资源 =====')
for (const ep of endpoints.filter((e) => e.prefix === '/api/students')) {
  await pair(ep, T.s27, 27, T.s30, 30, '[students]')
}

console.log('\n===== B) 教师 18 令牌 → 教师 29 的资源 =====')
for (const ep of endpoints.filter((e) => e.prefix === '/api/teachers')) {
  await pair(ep, T.t18, 18, T.t29, 29, '[teachers]')
}

console.log('\n===== C) 辅导员 =====')
const coEps = endpoints.filter((e) => e.prefix === '/api/counselors')
console.log(`  counselor-service 抽出 ${coEps.length} 条归属人型端点。活库**只有 1 个辅导员账号**（id=53），`)
console.log(`  没有第二个账号 ⇒ 三方对照跑不了。这不是"没问题"，是"测不了"，如实记。`)
for (const ep of coEps) console.log(`    · ${ep.path}`)

console.log('\n===== 结果 =====')
console.log(`越权 ${leaks.length} 条 / 已拦 ${guarded.length} 条 / 测不出 ${undecidable.length} 条 / 未定 ${inconclusive.length} 条`)
if (undecidable.length) {
  console.log('\n测不出的那些（**对方本身没数据** ⇒ 既不能判越权、也不能判安全，别读成"没问题"）:')
  undecidable.forEach((l) => console.log('    ' + l.split('\n')[0].trim() + '  ' + l.split('\n')[2].trim()))
}
if (inconclusive.length) {
  console.log('\n未定的那些（需人工看）:')
  inconclusive.forEach((l) => console.log(l))
}
process.exit(0)
