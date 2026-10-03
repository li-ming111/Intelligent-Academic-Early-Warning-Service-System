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
// ⚠️ 两个都收：驼峰 `teacherId` 与下划线 `teacher_id`。第四处自身缺陷就栽在这里 ——
// `getCourseList(@RequestParam teacher_id)` 因为写的是下划线，**从来没被扫到过**。
const OWNER_VAR = /^(student|user|teacher|counselor)(Id|_id)$/
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
  // ⚠️ 括号必须可选 —— `@RequestParam Long teacherId`(光秃秃没括号) 是合法写法,
  //    第一版要求 `@RequestParam(` 紧跟括号, 于是 `/class-management/requests`、
  //    `/class-management/my-classes`、`/class-management/applications` **一条都没抽到**。
  const re2 = /@GetMapping\(\s*"([^"]+)"\s*\)([\s\S]{0,500}?)@RequestParam\s*(?:\([^)]*\))?\s*Long\s+(\w+)/g
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
    // 网关模式：token 是登录拿到的 JWT（网关验签后再注入身份头）。
    // 直连模式：token 位置传的是 {id, role} —— 就是网关**本来会注入**的那两个头。
    const headers = DIRECT
      ? { 'X-User-Id': String(token.id), 'X-Role': token.role }
      : { Authorization: 'Bearer ' + token }
    const r = await fetch(url, { headers })
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

// 「被拒」有**三种形状**（本仓手册 §6.30 的老账），只看 HTTP 状态码会把后两种读成"通过"：
//   ① 网关闸 —— HTTP 403 + body `{code:403,message:"权限不足"}`
//   ② 下游 Spring Security —— HTTP 403 + **空体**
//   ③ 下游控制器业务拒绝 —— **HTTP 200 + body `code:403`** ← W10/W11 的两个归属闸都是这一种
// 归属闸（`denyUnlessSelf` / `isSelf`）走的是第 ③ 种（本仓前端一律按 body.code 判），
// 所以"拦住了"必须同时认 ①②③，否则本轮修完 32 条会一条都统计不到 —— 全落进"未定"。
const denied = (r) => String(r.http) === '403' || (String(r.http) === '200' && Number(r.code) === 403)

// ⚠️ 两套 id 空间 —— 本轮我自己的工具就在这里栽过一次：
// `/students/subscription/**` 的路径参数是 **student_profile.id**（司明 3 / 邱琳紫 4），
// 其余 `/students/**` 全是 **users.id**（27 / 30）。第一版给订阅族也传了 users.id ⇒
// 两次调用都回 `{success:false,message:"学生档案不存在: 30"}`，**cross 与 own 的失败串逐字相同**
// ⇒ 被全量判据当成"越权"报了 **2 条假阳性**。所以这里按前缀分别取 id。
// ⚠️ 收 `student_profile.id` 的**不止订阅族** —— `/evaluations/**` 也是（见
// `StudentServiceImpl.getEvaluationsByStudent` 的日志 `profileId={}` 与类注释）。
// 第一版只写了 `/subscription/` ⇒ 给 `/evaluations/**` 传了 users.id(27/30)，
// 于是那几条的结果是"拿错 id 空间"的产物（表现为 测不出/未定），**不是**"没问题"。
// 这是本探针的第三处自身缺陷，已一并修正。
const PROFILES = { 27: 3, 30: 4 }   // users.id -> student_profile.id
const isProfileSpace = (ep) => ep.prefix === '/api/students'
  && (ep.path.startsWith('/subscription/') || ep.path.startsWith('/evaluations/'))
// ⚠️ 教师侧也有一处 —— `/teachers/{teacherId}/courses` 查的是 `teacher_course.teacher_id`,
// 而那是全仓**唯一**收 `teacher_profile.id` 的教师列（活库：user 18↔profile 8、user 29↔profile 1）。
// 不给这一条做映射，探针会拿 users.id 去打 ⇒ 本人也被拒 ⇒ 判成"过度拦截"（假红）。
// 这正是本探针栽的第五处：id 空间不止在 student 侧分裂。
const T_PROFILES = { 18: 8, 19: 2, 22: 3, 28: 5, 29: 1, 55: 7 }   // users.id -> teacher_profile.id
const isTeacherProfileSpace = (ep) => ep.prefix === '/api/teachers' && ep.path === '/{teacherId}/courses'
// 教师端点里收**学生** id 的那几个，收的是 student_profile.id（xcx 的 useTeacherRoster 有明文注释）。
const STUDENT_PARAM_EP = /^\/(students\/\{studentId\}\/credit-prediction|scores\/student\/analyze)$/
const idFor = (ep, userId) => {
  if (isProfileSpace(ep)) return PROFILES[userId] ?? userId
  if (isTeacherProfileSpace(ep)) return T_PROFILES[userId] ?? userId
  return userId
}

// 有些端点的必填**查询参数**不在抽取范围内（本扫描只看路径变量）⇒ 不给就 400，
// 那样只会得到一条"未定"，等于把这块地方留成盲区。这里按路径补上合理默认值。
const EXTRA_Q = {
  '/appeals/{studentId}/by-status': 'status=0',
  '/benchmark/{studentId}/semester': 'semester=5',
  '/settings/{studentId}/security-logs': 'page=1&pageSize=10',
  // W11：抽取器修好后新浮出来的两条，各自还差一个必填查询参数，不给就只会得到 400（=盲区）。
  '/scores/by-class': 'class_name=' + encodeURIComponent('计科2306班'),
  '/scores/student/analyze': 'course_id=1',
}

// 直连模式（SWEEP_DIRECT=1）—— 给**刚构建的备用端口**跑，不改现役服务、不重启任何东西。
//
// 为什么需要：12 个现役服务都以 `java -jar` 占着自己的 jar ⇒ `mvn package` 会失败并**留下 thin jar**，
// 而"新代码经网关"只能靠重启现役服务。备用端口从 `target/classes` 起（新代码），
// 代价是**不过网关** —— 所以这里自己补上网关本来会注入的两个头
// （`X-User-Id` / `X-Role`，见网关 AuthenticationFilter），复现的就是"过完网关、到控制器之前"那一刻。
// ⚠️ 直连模式**验不到网关自己的闸**（前缀角色闸、白名单），只验控制器里那段归属判据。
const DIRECT = process.env.SWEEP_DIRECT === '1'
const SVC = { '/api/students': 'http://localhost:8188/students', '/api/teachers': 'http://localhost:8187/teachers' }

const url = (ep, userId) => {
  const id = idFor(ep, userId)
  const base = (DIRECT && SVC[ep.prefix] ? SVC[ep.prefix] : G + ep.prefix) + ep.path.replace(/\{\w+\}/g, String(id))
  const qs = []
  if (ep.via === 'query') qs.push(`${ep.varName}=${id}`)
  if (EXTRA_Q[ep.path]) qs.push(EXTRA_Q[ep.path])
  return base + (qs.length ? '?' + qs.join('&') : '')
}

// ------------------------------------------------------------------ 跑
console.log('扫描端点来源: 从三个 controller 源码抽 @GetMapping（不是手写清单）')
console.log(`共抽出「归属人 id 型」端点 ${endpoints.length} 条（已排除 export 等有副作用的）\n`)

const tok = {}
const IDENT = { 27: { id: 27, role: '1' }, 30: { id: 30, role: '1' }, 18: { id: 18, role: '2' }, 29: { id: 29, role: '2' } }
if (DIRECT) {
  console.log('模式: **直连备用端口**（SWEEP_DIRECT=1）—— 学生 8188 / 教师 8187，身份靠手工注入的 X-User-Id/X-Role。')
  console.log('      ⚠️ 这一模式验不到网关的闸，只验控制器里的归属判据。\n')
} else {
  for (const [u, label] of [['2023020616', '学生27'], ['2023020615', '学生30'],
                            ['teacher001', '教师18'], ['10001', '教师29']]) {
    tok[u] = await login(u)
    if (!tok[u]) { console.log(`拿不到 ${label} 的令牌，退出`); process.exit(2) }
  }
}
const T = DIRECT
  ? { s27: IDENT[27], s30: IDENT[30], t18: IDENT[18], t29: IDENT[29] }
  : { s27: tok['2023020616'], s30: tok['2023020615'], t18: tok['teacher001'], t29: tok['10001'] }

const leaks = [], guarded = [], inconclusive = [], undecidable = [], overBlocked = []

async function pair(ep, callerTok, callerId, otherTok, otherId, tag) {
  const u = (id) => url(ep, id)
  const self = await get(u(callerId), callerTok)
  const cross = await get(u(otherId), callerTok)
  const own = await get(u(otherId), otherTok)

  const selfSig = sig(self.data), crossSig = sig(cross.data), ownSig = sig(own.data)
  const selfFull = canon(self.data), crossFull = canon(cross.data), ownFull = canon(own.data)

  const bySig = crossSig === ownSig && crossSig !== selfSig
  const byFull = crossFull === ownFull && crossFull !== selfFull
  // 越权 = 对方**没被拒** + 拿到了非空数据 + 与对方自有视角逐字一致。
  const leak = !denied(cross) && String(cross.http) === '200' && nonEmpty(cross.data) && (bySig || byFull)
  const how = bySig && byFull ? '签名+全量一致' : bySig ? '签名一致' : byFull ? '全量一致(签名盲区)' : ''

  // 过度拦截：**本人读自己的也被拒**。加闸时最容易犯的错（选择器写反 / id 空间选错），
  // 而它在上面的越权判据里表现为"已拦"，看着像成功 —— 所以必须单列一条。
  const selfDenied = denied(self)

  const line = `  ${leak ? '!!' : '  '} ${tag} ${ep.via === 'query' ? ep.path + '?' + ep.varName : ep.path}\n` +
    `       self(${callerId}) http=${self.http} code=${self.code} ${selfSig}\n` +
    `       cross(${otherId}) http=${cross.http} code=${cross.code} ${crossSig}\n` +
    `       own(${otherId}) http=${own.http} code=${own.code} ${ownSig}`

  if (selfDenied) {
    // 排在越权之前：本人读自己被拒 ⇒ 门槛立错了地方（写反 / id 空间选错），
    // 这条在别处会伪装成"已拦"，必须先炸出来。
    overBlocked.push(line)
    console.log(line + `   <-- !!过度拦截：本人(${callerId})读自己的也被拒 (${self.http}${String(self.http) === '403' ? '' : ' + code ' + self.code})`)
  }
  else if (leak) { leaks.push(line); console.log(line + `   <-- 越权：${how}`) }
  else if (denied(cross)) {
    guarded.push(line)
    console.log(line + `   (拦住了: ${cross.http}${String(cross.http) === '403' ? '' : ' + code ' + cross.code})`)
  }
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
const teacherEps = endpoints.filter((e) => e.prefix === '/api/teachers')
// 参数是**学生** id 的教师端端点（`studentId` / `student_id`）：三方对照（拿"调用者自己的 id 空间"
// 打两遍）在这里**根本不成立** —— 教师读学生是设计如此，不是越权。硬套会把
// `/teachers/students/{studentId}/credit-prediction` 报成假越权（第一版就这么错了一次）。
// 但仍要**证伪地**看一眼：它是不是真的"按传入的学生 id 回答"。回显对得上才算设计如此，
// 对不上（比如把参数当调用者 id 用）就得回来重判。
const studentParamEps = teacherEps.filter((e) => /^student/i.test(e.varName))
for (const ep of teacherEps.filter((e) => !/^student/i.test(e.varName))) {
  await pair(ep, T.t18, 18, T.t29, 29, '[teachers]')
}
console.log('\n  -- 以下端点收的是**学生** id，不是调用者自己的 id ⇒ 三方对照不适用，单列 --')
for (const ep of studentParamEps) {
  // 传**档案 id 3（司明）**而不是 users.id 27：这两个端点收的是 student_profile.id
  // （xcx `useTeacherRoster.js` 明文写着 `/scores/student/analyze?student_id=`、
  //  `/students/{id}/credit-prediction` 两条都收 profile id）。
  // 断言也据此加强：光"回显了我传的数"什么都证明不了 —— 传 27 时它照样回 `studentId:27`，
  // 只是后面全 0（查不到该档案）。所以要**打出这个学生的姓名**才算真解析到了。
  const r = await get(url(ep, 3), T.t18)             // 教师 18 读学生档案 3 = 司明
  const said = JSON.stringify(r.data ?? null)
  const named = said.includes('司明')
  const verdict = String(r.http) !== '200' ? `**非 200（http=${r.http} code=${r.code}）** ⇒ 需人工重判`
    : named ? '解析到司明本人 ⇒ 参数确是学生档案 id，属"教师读学生"的设计用法'
      : '**没解析出该学生** ⇒ 需人工重判'
  console.log(`    · ${ep.path}?${ep.varName}  教师18→档案3: http=${r.http} code=${r.code} ${verdict}`)
}

console.log('\n===== C) 辅导员 =====')
const coEps = endpoints.filter((e) => e.prefix === '/api/counselors')
console.log(`  counselor-service 抽出 ${coEps.length} 条归属人型端点。活库**只有 1 个辅导员账号**（id=53），`)
console.log(`  没有第二个账号 ⇒ 三方对照跑不了。这不是"没问题"，是"测不了"，如实记。`)
for (const ep of coEps) console.log(`    · ${ep.path}`)

console.log('\n===== 结果 =====')
console.log(`越权 ${leaks.length} 条 / 已拦 ${guarded.length} 条 / 过度拦截 ${overBlocked.length} 条 / 测不出 ${undecidable.length} 条 / 未定 ${inconclusive.length} 条`)
if (overBlocked.length) {
  console.log('\n⚠️ 过度拦截（**本人读自己**都被拒 ⇒ 闸装错了位置，不是"更安全"，是坏了）:')
  overBlocked.forEach((l) => console.log(l))
}
if (undecidable.length) {
  console.log('\n测不出的那些（**对方本身没数据** ⇒ 既不能判越权、也不能判安全，别读成"没问题"）:')
  undecidable.forEach((l) => console.log('    ' + l.split('\n')[0].trim() + '  ' + l.split('\n')[2].trim()))
}
if (inconclusive.length) {
  console.log('\n未定的那些（需人工看）:')
  inconclusive.forEach((l) => console.log(l))
}
process.exit(0)
