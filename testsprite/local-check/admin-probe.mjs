// 管理员端**只读**接口普查（直接打网关 8076，不过浏览器）。
//
// 为什么先跑这个再写页面：教师端那次就是这步查出 8 个接口返 code:500
// （handler 把 entity 当 Map 用 / mapper SQL 写错列），页面写完了才发现没数据。
// 先把接口状态摸清楚，写页面时才知道哪些字段是**真的有**。
//
// ⚠️ 只发 GET。写接口（toggle-status / reset-password / approve / handle / broadcast）
//    一律不打 —— 那会改库。写路径等页面写完了，按"先 mysqldump 快照，再写，再回滚并复核"
//    的流程单独验（见 db_backups/ 里的三个 teacher 脚本）。
//
// 用法: node admin-probe.mjs [用户名] [密码]
// 输出: admin-probe.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { captchaFields } from './_captcha.mjs'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const BASE = process.env.API_BASE || 'http://localhost:8076'

const lines = []
const say = s => { lines.push(s); console.log(s) }

/** 接口清单：`[路径, 这条是干嘛的]`。路径里的 `{uid}` 登录后用真实 id 替换。 */
const ENDPOINTS = [
  ['/api/admin/dashboard', '首页概览'],
  ['/api/admin/statistics', '统计'],
  ['/api/admin/pending-requests', '待审批（首页那张卡）'],
  ['/api/admin/users/page?page=1&size=5', '用户分页'],
  ['/api/admin/users/page?page=1&size=5&role=2', '用户分页（按角色过滤）'],
  ['/api/admin/users/page?page=1&size=5&collegeId=1', '用户分页（按学院过滤）'],
  ['/api/admin/colleges', '学院列表'],
  ['/api/admin/majors', '专业列表'],
  ['/api/admin/courses', '课程列表'],
  ['/api/admin/classes', '班级列表'],
  ['/api/admin/rules', '预警规则列表'],
  ['/api/admin/warnings', '预警总览'],
  ['/api/admin/warnings/1', '预警详情'],
  ['/api/admin/messages/list?page=1&size=5', '消息列表'],
  ['/api/admin/class-management/pending-requests', '班级申请待审批'],
  ['/api/admin/class-management/applications', '班级申请全部'],
  ['/api/admin/export/history', '导出历史'],
  ['/api/roles/list', '角色表'],
  ['/api/permissions/list', '权限表'],
  ['/api/role-permissions/by-role/1', '某角色的权限'],
  ['/api/users/info/{uid}', '账号信息（不分角色）'],
  ['/api/users/{uid}/settings', '用户设置'],
  ['/api/admin/login-logs/{uid}', '登录日志（admin 前缀）'],
  ['/api/admin/users/{uid}/login-logs', '登录日志（users 前缀）']
]

/** 分页载荷可能有 list/records/items 三种叫法，逐个数出来 —— 只返回条数会看不出"哪个键"。 */
function shape(v) {
  if (v === null || v === undefined) return String(v)
  if (Array.isArray(v)) {
    const first = v[0]
    const keys = first && typeof first === 'object' ? Object.keys(first) : []
    return `Array(${v.length})` + (keys.length ? ` of {${keys.join(', ')}}` : '')
  }
  if (typeof v === 'object') {
    const parts = []
    for (const [k, val] of Object.entries(v)) {
      if (Array.isArray(val)) parts.push(`${k}: Array(${val.length})`)
      else if (val && typeof val === 'object') parts.push(`${k}: {${Object.keys(val).slice(0, 12).join(', ')}}`)
      else parts.push(`${k}: ${JSON.stringify(val)}`)
    }
    return `{ ${parts.join(' | ')} }`
  }
  return JSON.stringify(v)
}

const login = await (await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: USER, password: PASS, ...(await captchaFields(BASE)) })
})).json()
// 登录信封的 code/success 恒为真，唯一可信的判据是 data.token 在不在
if (!login.data || !login.data.token) {
  say(`FAIL 登录失败：status=${login.data && login.data.status} message=${login.data && login.data.message}`)
  writeFileSync('admin-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
const uid = login.data.userId ?? login.data.user_id ?? login.data.id
say(`登录成功：${USER} / ${login.data.name || login.data.userName || '?'} / role=${JSON.stringify(login.data.role)} / userId=${uid}`)
say('')

const H = { Authorization: `Bearer ${login.data.token}` }
let bad = 0
let empty = 0

for (const [path, what] of ENDPOINTS) {
  const url = path.replace(/\{uid\}/g, String(uid))
  let status = 0
  let body = null
  try {
    const r = await fetch(`${BASE}${url}`, { headers: H })
    status = r.status
    const txt = await r.text()
    try { body = JSON.parse(txt) } catch { body = { __raw: txt.slice(0, 200) } }
  } catch (e) {
    say(`ERR   ${url}`)
    say(`      ${what} —— 请求本身失败：${e.message}`)
    bad++
    continue
  }
  const code = body && body.code
  const okish = status === 200 && (code === 200 || code === 0)
  const data = body && body.data
  const isEmpty = data === null || data === undefined ||
    (Array.isArray(data) && !data.length) ||
    (typeof data === 'object' && !Array.isArray(data) && Object.keys(data).length === 0)

  if (!okish) bad++
  if (okish && isEmpty) empty++

  say(`${okish ? 'OK  ' : 'FAIL'} ${url}`)
  say(`      ${what}`)
  if (!okish) {
    say(`      HTTP ${status} code=${code} message=${body && String(body.message).slice(0, 160)}`)
  } else {
    say(`      ${shape(data)}`)
    if (isEmpty) say('      ⚠️ 载荷为空（这个页面会渲染成空列表/占位，写页面前要想清楚是"真没有"还是"接口没实现"）')
  }
  say('')
}

say(`==== 小计：${ENDPOINTS.length} 个接口，失败 ${bad} 个，成功但载荷为空 ${empty} 个 ====`)
writeFileSync('admin-probe.txt', lines.join('\n') + '\n', 'utf8')
console.log('已写出 admin-probe.txt')
process.exit(bad ? 1 : 0)
