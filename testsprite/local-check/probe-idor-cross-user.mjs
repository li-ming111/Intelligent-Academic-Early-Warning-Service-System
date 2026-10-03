// 横向越权（IDOR）定点探针 —— 补 `idor-probe.mjs` 的盲区。
//
// 为什么另写一个：`idor-probe.mjs` 里"辅导员"那一组拿的是**辅导员 53 自己的令牌**
// 去打 `/api/users/info/53`，标签却写着"读**别人的**用户资料" —— 那是**读自己**，
// 放行本来就对。于是它既报不出真越权，也把"读自己"标成了可疑。
// 本探针只问一件事：**A 用别人的 id 能不能读到 B 的数据**。
//
// 只做**读**（GET），不发任何写请求 —— 不改数据、不改密码、不建账号。
//
// 用法: node probe-idor-cross-user.mjs     退出码 0（取证，不是断言）
//
// 判据形态：每条给出「本人资源」（应放行）与「他人资源」（应 403）成对对照 ——
// 只测"他人资源 403"会被"接口根本不存在也 403"骗过；配上"本人资源 200"才说明
// 拦的确实是归属，不是路由。**返回 200 且有他人数据 = 越权**。
import { captchaFields } from './_captcha.mjs'

const G = 'http://localhost:8076'

const login = async (u, p = '123456') => {
  const r = await fetch(G + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: u, password: p, ...(await captchaFields(G)) }),
  }).then((x) => x.json())
  return r?.data?.token || null
}

const get = async (path, token) => {
  try {
    const r = await fetch(G + path, { headers: { Authorization: 'Bearer ' + token } })
    const t = await r.text()
    let j = null
    try { j = JSON.parse(t) } catch {}
    const d = j?.data
    // ⚠️ 2026-10-03（W10）**修正** —— 原写法是 `let shape = typeof d`，
    // d === null 时 typeof 也是 'object'，于是 `shape === 'object'` **当且仅当 data 为 null**。
    // 而下面的越权判据恰恰拿 `shape === 'object'` 当"有数据"，两个方向同时错：
    //   · 漏报：回**真对象**的端点（dashboard / scores/audit / scores/logs）shape 是 `object{…}`，
    //     判据一眼都不看 ⇒ 它们的越权**从来没被报出来过**（本轮实测：dashboard 确实越权）。
    //   · 误报：任何 200 + `data:null` 的端点都会被报成越权。
    // 全量、更严的读侧扫描见同目录 `probe-idor-read-sweep.mjs`（三方对照 self/cross/own）。
    const shape = d === null || d === undefined ? 'null'
      : Array.isArray(d) ? `array(${d.length})`
        : typeof d === 'object' ? `object{${Object.keys(d).slice(0, 7).join(',')}}`
          : typeof d
    return { http: r.status, code: j?.code, shape, data: d, sample: typeof d === 'string' ? d.slice(0, 90) : '' }
  } catch (e) { return { http: 'ERR', code: e.message, shape: 'ERR', data: null } }
}

/** "有内容"的判据：不猜 shape，直接看载荷。空数组/空页/null 一律不算内容。 */
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

const leaks = []   // 确证的越权
const notes = []
console.log(`用户 id: 教师 18=teacher001(刘教授) / 29=10001(于凤海) / 55=teacher006`)
console.log(`         学生 27=2023020616(司明) / 7=另一个学生   辅导员 53=1000000001\n`)

// ---------------------------------------------------------------- A) 学生自助面
console.log('===== A) 学生 27 令牌：自助面的"本人 vs 同学" =====')
const st = await login('2023020616')
if (!st) { console.log('拿不到学生令牌，退出'); process.exit(2) }
for (const [name, mk, okSelf] of [
  ['用户资料 /users/info/{id}', (id) => `/api/users/info/${id}`, () => true],
  ['个人设置 /users/{id}/settings', (id) => `/api/users/${id}/settings`, () => true],
  ['登录历史 /users/{id}/login-logs', (id) => `/api/users/${id}/login-logs`, () => true],
]) {
  const me = await get(mk(27), st), other = await get(mk(7), st)
  console.log(`  · ${name}\n      本人(27) http=${me.http} | 他人(7) http=${other.http}`)
  if (String(other.http) === '200') leaks.push(`${name} 学生27 读到了 7 的数据`)
}
// 学生本人的业务面（路径参数也是 users.id）
for (const [name, mk] of [
  ['申诉 /students/appeals/{id}/list', (id) => `/api/students/appeals/${id}/list`],
  ['成绩 /students/scores/{id}', (id) => `/api/students/scores/${id}`],
  ['消息 /students/messages/{id}', (id) => `/api/students/messages/${id}`],
]) {
  const me = await get(mk(27), st), other = await get(mk(7), st)
  const leak = String(other.http) === '200' && nonEmpty(other.data)
  console.log(`  · ${name}\n      本人(27) http=${me.http} ${me.shape} | 他人(7) http=${other.http} ${other.shape}${leak ? '   ← 有内容！' : ''}`)
  if (leak) leaks.push(`${name} 学生27 读到了 7 的 ${other.shape}`)
}

// -------------------------------------------------------------- B) 教师读面（片状）
console.log('\n===== B) 教师 18 令牌：打自己的 18 vs 打别人的 29 =====')
const t18 = await login('teacher001'), t29 = await login('10001')
if (!t18 || !t29) console.log('教师令牌拿不到，跳过 B')
else {
  // login() 回的是 token 字符串，没有 .name —— 原来打印 t18.name 恒为 undefined（看着像"拿不到账号"）。
  console.log(`  （参照）已拿到教师18 与教师29 的令牌`)
  const pairs = [
    ['看板 /teachers/dashboard/{id}', (id) => `/api/teachers/dashboard/${id}`, true],
    ['待办 /teachers/todos/{id}', (id) => `/api/teachers/todos/${id}`, true],
    ['学生 /teachers/students/{id}', (id) => `/api/teachers/students/${id}`, true],
    ['反馈 /teachers/feedbacks/{id}/list', (id) => `/api/teachers/feedbacks/${id}/list`, true],
    ['成绩审计 /teachers/scores/audit/{id}', (id) => `/api/teachers/scores/audit/${id}`, true],
    ['变更日志 /teachers/scores/logs/{id}', (id) => `/api/teachers/scores/logs/${id}`, true],
    ['消息 /teachers/messages/{id}', (id) => `/api/teachers/messages/${id}`, true],
    ['预警 /teachers/warnings/{id}', (id) => `/api/teachers/warnings/${id}`, true],
    ['申请记录 /teachers/class-management/requests?teacherId={id}',
      (id) => `/api/teachers/class-management/requests?teacherId=${id}`, true],
  ]
  for (const [name, mk] of pairs) {
    const me = await get(mk(18), t18), other = await get(mk(29), t18)
    // 修正后：判"有没有内容"直接看载荷，不看 shape（见文件头的同一段说明）。
    const leak = String(other.http) === '200' && nonEmpty(other.data)
    console.log(`  · ${name}\n      本人(18) http=${me.http} ${me.shape} | 他人(29) http=${other.http} ${other.shape}${leak ? '   ← 越权' : ''}`)
    if (leak) leaks.push(`${name} 教师18 → 29 ${other.shape}${other.sample ? ' ' + other.sample : ''}`)
  }
  // 负向对照：把 29 的令牌打自己（应同样放行）—— 证明拦的不是"29"这个数字
  const back = await get('/api/teachers/dashboard/18', t29)
  console.log(`  （负向对照）教师 29 的令牌打 /dashboard/18：http=${back.http} ${back.shape}`)
  if (String(back.http) !== '200') notes.push('对照异常：29 打 18 也被拒，说明拦的不是归属')
}

// -------------------------------------------------- C) 学生令牌打教师面（应被前缀闸挡）
console.log('\n===== C) 学生 27 令牌打教师面（网关前缀闸应 403）=====')
const cross = await get('/api/teachers/dashboard/29', st)
console.log(`  · 学生 → /api/teachers/dashboard/29：http=${cross.http} code=${cross.code}`)

// ---------------------------------------------------------------- D) 辅导员
console.log('\n===== D) 辅导员 53 令牌 =====')
const co = await login('1000000001')
if (!co) console.log('拿不到辅导员令牌，跳过 D')
else {
  const me = await get('/api/users/info/53', co), other = await get('/api/users/info/27', co)
  console.log(`  · /users/info 本人(53) http=${me.http} | 学生(27) http=${other.http}`)
  const dash = await get('/api/counselors/dashboard/53', co)
  console.log(`  · /counselors/dashboard/53 http=${dash.http} ${dash.shape}`)
  console.log(`    （活库只有 1 个辅导员账号 id=53，辅导员之间的横向越权**无法用两个真账号实测**；`)
  console.log(`     但 counselor-service 59 个映射里 0 处读 X-User-Id，属同一族，只能读码定性）`)
}

console.log(`\n===== 结果 =====`)
if (leaks.length) {
  console.log(`确证的横向越权 ${leaks.length} 条：`)
  leaks.forEach((l, i) => console.log(`  ${i + 1}. ${l}`))
} else console.log('未发现"他人资源返回 200 且有内容"')
notes.forEach((n) => console.log('  注: ' + n))
process.exit(0)
