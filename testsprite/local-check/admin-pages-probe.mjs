// 管理员端 9 个页面**将要调用的那批接口**的普查（只读）。
//
// 为什么单写一个：P7 要写的是页面，而页面只能是"路径写对了 + 字段取对了"才叫能用。
// 前两轮 admin-probe / admin-probe2 验的是"修没修好"，这一轮验的是
// "小程序要用的这几条到底存不存在、返回什么形状" —— 免得照着 PC 的 api/index.js
// 抄一条根本不存在的路径（PC 的 viewPassword 就是这么一条）。
//
// 附带做字段体检：把每个接口第一行/顶层字段摊平成 key=value 打出来，
// 写页面时取字段名不用猜。
//
// ⚠️ 只发 GET。写接口（用户 CRUD / 审批 / 广播 / 处理预警 / 规则 CRUD）不打。
//
// 用法: node admin-pages-probe.mjs
// 输出: admin-pages-probe.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { captchaFields } from './_captcha.mjs'

const BASE = process.env.API_BASE || 'http://localhost:8076'
const lines = []
const say = s => { lines.push(s); console.log(s) }
let failed = 0

const login = await (await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456', ...(await captchaFields(BASE)) })
})).json()
if (!login.data || !login.data.token) {
  say(`FAIL 登录失败：${JSON.stringify(login.data).slice(0, 200)}`)
  writeFileSync('admin-pages-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
const H = { Authorization: `Bearer ${login.data.token}` }

/** 值压到一行、截断，避免把长文本灌进报告 */
const brief = v => {
  if (v === null) return 'null'
  if (v === undefined) return 'undefined'
  if (typeof v === 'object') return Array.isArray(v) ? `Array(${v.length})` : `Object{${Object.keys(v).slice(0, 8).join(',')}}`
  const s = String(v)
  return s.length > 42 ? s.slice(0, 42) + '…' : s
}

/** 把 data 的形状摊平：数组看第一行；对象看顶层（嵌套的再展开一层） */
function shape(data, indent = '      ') {
  const out = []
  if (Array.isArray(data)) {
    out.push(`${indent}数组 ${data.length} 行`)
    if (data.length) {
      const f = data[0]
      if (f && typeof f === 'object') {
        for (const [k, v] of Object.entries(f)) out.push(`${indent}  [0].${k} = ${brief(v)}`)
      } else out.push(`${indent}  [0] = ${brief(f)}`)
    }
  } else if (data && typeof data === 'object') {
    for (const [k, v] of Object.entries(data)) {
      out.push(`${indent}${k} = ${brief(v)}`)
      if (v && typeof v === 'object' && !Array.isArray(v)) {
        for (const [k2, v2] of Object.entries(v).slice(0, 10)) out.push(`${indent}  .${k2} = ${brief(v2)}`)
      }
      if (Array.isArray(v) && v.length && v[0] && typeof v[0] === 'object') {
        for (const [k2, v2] of Object.entries(v[0]).slice(0, 10)) out.push(`${indent}  [0].${k2} = ${brief(v2)}`)
      }
    }
  } else {
    out.push(`${indent}${brief(data)}`)
  }
  return out
}

const get = async (path) => {
  const r = await fetch(`${BASE}${path}`, { headers: H })
  const txt = await r.text()
  let body
  try { body = JSON.parse(txt) } catch { body = { __raw: txt.slice(0, 200) } }
  return { status: r.status, code: body && body.code, data: body && body.data, message: body && body.message }
}

/** 按页面分组列出要打的接口；没找到记 FAIL */
async function check(page, path) {
  const r = await get(path)
  const okHttp = r.status === 200 && r.code === 200
  if (!okHttp) {
    failed++
    say(`  FAIL ${path} → HTTP ${r.status} code=${r.code} ${r.message || ''}`)
    return null
  }
  say(`  OK   ${path}`)
  shape(r.data).forEach(s => say(s))
  return r.data
}

const page = (t) => { say(''); say(`=== ${t} ===`) }

// ---------------------------------------------------------------
page('1. pages/admin/dashboard —— 首页')
await check('dashboard', '/api/admin/dashboard')
await check('dashboard', '/api/admin/statistics')
await check('dashboard', '/api/admin/pending-requests')
await check('dashboard', '/api/admin/activities')

// ---------------------------------------------------------------
page('2. pages/admin/users —— 用户管理（列表 / 详情 / 登录日志）')
const users = await check('users', '/api/admin/users/page?page=1&size=3&role=4')
if (users && Array.isArray(users.items) && users.items.length) {
  const id = users.items[0].id
  await check('users', `/api/admin/users/${id}`)
  await check('users', `/api/admin/login-logs/${id}`)
}
// 学员/教师/辅导员/学院管理员各取一行，看字段差异（尤其是 collegeId/collegeName 有没有）
for (const role of [1, 2, 3, 5]) {
  say(`  —— role=${role} 的一行 ——`)
  const r = await get(`/api/admin/users/page?page=1&size=1&role=${role}`)
  const it = r.data && r.data.items && r.data.items[0]
  if (!it) { failed++; say(`  FAIL role=${role} 一行都没取到（HTTP ${r.status} code=${r.code}）`) }
  else {
    for (const [k, v] of Object.entries(it)) say(`      ${k} = ${brief(v)}`)
  }
}

// ---------------------------------------------------------------
page('3. pages/admin/permissions —— 角色权限')
await check('permissions', '/api/admin/permissions/roles')
await check('permissions', '/api/admin/permissions/list')
await check('permissions', '/api/admin/permissions/user/1')

// ---------------------------------------------------------------
page('4. pages/admin/colleges —— 学院与专业')
const colleges = await check('colleges', '/api/admin/colleges')
if (colleges && colleges.length) await check('colleges', `/api/admin/colleges/${colleges[0].id}`)
await check('colleges', '/api/admin/colleges/1/majors')
await check('colleges', '/api/admin/majors')

// ---------------------------------------------------------------
page('5. pages/admin/courses —— 课程与班级')
const courses = await check('courses', '/api/admin/courses')
if (courses && courses.length) await check('courses', `/api/admin/courses/${courses[0].id}`)
const classes = await check('courses', '/api/admin/classes')
if (classes && classes.length) await check('courses', `/api/admin/classes/${classes[0].id}`)

// ---------------------------------------------------------------
page('6. pages/admin/rules —— 预警规则')
const rules = await check('rules', '/api/admin/rules')
if (rules && rules.length) await check('rules', `/api/admin/rules/${rules[0].id}`)

// ---------------------------------------------------------------
page('7. pages/admin/warnings —— 预警总览')
const warns = await check('warnings', '/api/admin/warnings')
if (warns && warns.length) await check('warnings', `/api/admin/warnings/${warns[0].id}`)

// ---------------------------------------------------------------
page('8. pages/admin/class-requests —— 班级申请审批')
await check('class-requests', '/api/admin/class-management/pending-requests')
// ⚠️ 实测 pending-requests 的 total=0 而 applications 有 9 条，两条路径不是同一份数据
await check('class-requests', '/api/admin/class-management/applications?page=1&size=3')
await check('class-requests', '/api/admin/class-management/applications?page=1&size=3&type=teacher')
await check('class-requests', '/api/admin/class-management/applications?page=1&size=3&type=counselor')

// ---------------------------------------------------------------
page('9. pages/admin/profile —— 我的')
await check('profile', '/api/users/info/1')
await check('profile', '/api/users/1/settings')
await check('profile', '/api/admin/export/history?page=1&size=3')

say('')
say(failed ? `==== 有 ${failed} 条接口不可用 ====` : '==== 全部可用 ====')
writeFileSync('admin-pages-probe.txt', lines.join('\n') + '\n', 'utf8')
console.log('\n已写出 admin-pages-probe.txt')
