// 管理员端只读接口普查（第二轮）—— 验证 2026-09-29 对 admin-service 的四处修复。
//
// 第一轮 admin-probe.mjs 记录的是**修之前**的状态（`/admin/warnings/1` 与
// `/admin/messages/list` 都是 HTTP 200 + code:500）。这一轮针对修复点做**可判对错**的断言：
// 不是"返回 200 就算过"，而是把期望的数字写死在脚本里，不一致就报 FAIL。
//
// ⚠️ 只发 GET。写接口（用户 CRUD / 审批 / 广播 / 处理预警）一律不打 —— 那会改库。
//    写路径按规范另做：先 mysqldump 快照 → 界面/接口写 → 回滚脚本 → 逐字节复核。
//
// 期望值的来历（都是库里的实测数，写死是为了"变了就会响"）：
//   users 共 35 个：role1 学生 24 / role2 教师 6 / role3 辅导员 1 / role4 管理员 2 / role5 学院管理员 2
//   student_profile 24 条全在 college_id=1；teacher_profile 5 条 college 1 + 1 条 college 2；
//   counselor_profiles 1 条 college 1；college_admin_profile 2 条（college 1 与 2 各一）
//   → collegeId=1 = 24+5+1+1 = 31，collegeId=2 = 1+1 = 2，collegeId=3(商学院) = 0
//
// 用法: node admin-probe2.mjs [用户名] [密码]
// 输出: admin-probe2.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { captchaFields } from './_captcha.mjs'

const USER = process.argv[2] || 'admin'
const PASS = process.argv[3] || '123456'
const BASE = process.env.API_BASE || 'http://localhost:8076'

const lines = []
const say = s => { lines.push(s); console.log(s) }
let failed = 0
const fail = s => { failed++; say('FAIL ' + s) }
const pass = s => say('PASS ' + s)

const login = await (await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: USER, password: PASS, ...(await captchaFields(BASE)) })
})).json()
// 登录信封的 code 恒为 200，唯一可信的判据是 data.token
if (!login.data || !login.data.token) {
  say(`FAIL 登录失败：status=${login.data && login.data.status} message=${login.data && login.data.message}`)
  writeFileSync('admin-probe2.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
const H = { Authorization: `Bearer ${login.data.token}` }
say(`登录成功：${USER} / ${login.data.name} / role=${login.data.role} / userId=${login.data.userId}`)
say('')

const get = async (path) => {
  const r = await fetch(`${BASE}${path}`, { headers: H })
  const txt = await r.text()
  let body
  try { body = JSON.parse(txt) } catch { body = { __raw: txt.slice(0, 200) } }
  return { status: r.status, body, code: body && body.code, data: body && body.data }
}

// ---------- 一、按学院过滤：落进 SQL 之后 total 与分页都要对 ----------
say('=== 一、/admin/users/page 按学院过滤（原来是在内存里滤这一页）===')
for (const [q, expectTotal] of [
  ['collegeId=1', 31],
  ['collegeId=2', 2],
  ['collegeId=3', 0],           // 商学院一个 profile 都没有 → 走空集合分支
  ['collegeId=1&role=2', 5],
  ['collegeId=2&role=2', 1]
]) {
  const r = await get(`/api/admin/users/page?page=1&size=100&${q}`)
  const total = r.data && r.data.total
  const n = r.data && r.data.items && r.data.items.length
  if (r.status !== 200 || r.code !== 200) fail(`${q} → HTTP ${r.status} code=${r.code} ${r.body && r.body.message}`)
  else if (total !== expectTotal) fail(`${q} → total=${total}（应 ${expectTotal}）`)
  else if (n !== expectTotal) fail(`${q} → items=${n}（应 ${expectTotal}，size=100 时应一页装下）`)
  else pass(`${q} → total=${total} items=${n}`)
}
{
  // 分页正确性：第 2 页 5 条、total 仍是 31（内存过滤时代 total 恒为 35 且页内容会漂移）
  const r = await get('/api/admin/users/page?page=2&size=5&collegeId=1')
  const total = r.data && r.data.total
  const n = r.data && r.data.items && r.data.items.length
  if (total !== 31 || n !== 5) fail(`collegeId=1 第 2 页 → total=${total} items=${n}（应 31 / 5）`)
  else pass(`collegeId=1 第 2 页 → total=${total} items=${n}`)
}

// ---------- 二、"工号"字段：教师的不能是 users.id ----------
say('')
say('=== 二、教师行的 jobNumber（原来填的是 users.id，于凤海会显示成 29）===')
{
  const r = await get('/api/admin/users/page?page=1&size=100&role=2')
  const items = (r.data && r.data.items) || []
  const bad = items.filter(u => u.jobNumber !== undefined)
  if (bad.length) {
    fail(`有 ${bad.length} 个教师行仍带着 jobNumber，例：${JSON.stringify(bad[0])}`)
  } else {
    pass(`6 个教师行都没有 jobNumber（前端会落到 username = 真实工号）；示例：` +
      items.slice(0, 3).map(u => `${u.name}/${u.username}`).join('、'))
  }
  const yfh = items.find(u => u.name === '于凤海')
  if (yfh) pass(`于凤海：username=${yfh.username}（工号）、collegeName=${yfh.collegeName}`)
}

// ---------- 三、辅导员与学院管理员两类账号的 enrichment ----------
say('')
say('=== 三、辅导员 / 学院管理员（原来辅导员那支静默失效、学院管理员根本没有分支）===')
{
  const r = await get('/api/admin/users/page?page=1&size=100&role=3')
  const c = ((r.data && r.data.items) || [])[0]
  if (!c) fail('role=3 一个都没返回')
  else if (!c.collegeId || !c.jobNumber) fail(`辅导员行没被补全：${JSON.stringify(c)}`)
  else pass(`辅导员 ${c.name}：jobNumber=${c.jobNumber} collegeId=${c.collegeId} collegeName=${c.collegeName}`)
}
{
  const r = await get('/api/admin/users/page?page=1&size=100&role=5')
  const items = (r.data && r.data.items) || []
  const missing = items.filter(u => !u.collegeId || !u.collegeName)
  if (!items.length) fail('role=5 一个都没返回')
  else if (missing.length) fail(`学院管理员未补学院：${JSON.stringify(missing[0])}`)
  else pass(`学院管理员 ${items.map(u => `${u.name}(${u.collegeName}/${u.title || '—'})`).join('、')}`)
}

// ---------- 四、两个 code:500 ----------
say('')
say('=== 四、原来返 code:500 的两个接口 ===')
{
  // 预警表的主键是 395-398，不是 1-4（第一轮探针就是拿 id=1 试的，
  // 所以先确认"存在的那条"能查出来）
  const r = await get('/api/admin/warnings/395')
  if (r.code !== 200 || !r.data || !r.data.id) fail(`/admin/warnings/395 → code=${r.code} ${r.body && r.body.message}`)
  else pass(`/admin/warnings/395 → ${r.data.studentName} 等级=${r.data.warningLevel} ${r.data.title} 状态=${r.data.status}`)
}
{
  // 不存在的 id：修好之后应该是明确的 404「未找到」，不是 500「系统内部错误」
  const r = await get('/api/admin/warnings/999999')
  if (r.code === 404) pass('/admin/warnings/999999 → code=404（不存在的 id 是"没找到"，不是"服务器错误"）')
  else fail(`/admin/warnings/999999 → code=${r.code} ${r.body && r.body.message}（应 404）`)
}
{
  const r = await get('/api/admin/messages/list?page=1&size=5')
  if (r.code !== 200) fail(`/admin/messages/list → code=${r.code} ${r.body && r.body.message}`)
  else pass(`/admin/messages/list → code=200，total=${r.data && r.data.total}（表是空的，这是实情而非报错）`)
}

// ---------- 五、辅导员接口（原来 /counselors/{id} 按编号列查，永远 notFound）----------
say('')
say('=== 五、/admin/counselors 与 /admin/counselors/{id} ===')
{
  const r = await get('/api/admin/counselors')
  const n = Array.isArray(r.data) ? r.data.length : -1
  if (n !== 1) fail(`/admin/counselors → n=${n}（应 1）code=${r.code} ${r.body && r.body.message}`)
  else pass(`/admin/counselors → 1 条：${r.data[0].name}（counselor_id=${r.data[0].counselorId}）`)
}
for (const [id, why] of [['1', 'profile 主键'], ['53', 'users.id 兜底']]) {
  const r = await get(`/api/admin/counselors/${id}`)
  if (r.code !== 200 || !r.data) fail(`/admin/counselors/${id}（${why}）→ code=${r.code} ${r.body && r.body.message}`)
  else pass(`/admin/counselors/${id}（${why}）→ ${r.data.name}`)
}

// ---------- 六、PC 端管理员页面在调的几条路径，是否真的存在 ----------
say('')
say('=== 六、PC 端 adminAPI 调的路径是否存在（小程序端写页面前要挑对路径）===')
const PC_PATHS = [
  ['/api/admin/permissions/roles', 'PC getRoles'],
  ['/api/admin/permissions/list', 'PC getPermissions'],
  ['/api/admin/permissions/user/1', 'PC getUserPermissions'],
  ['/api/admin/users/1/password', 'PC viewPassword'],
  ['/api/admin/statistics', 'PC 统计'],
  ['/api/admin/pending-requests', 'PC 待审批'],
  ['/api/admin/login-logs/29', '登录日志（于凤海 users.id=29）'],
  ['/api/admin/export/history', '导出历史']
]
for (const [p, why] of PC_PATHS) {
  const r = await get(p)
  const ok = r.status === 200 && r.code === 200
  const shape = r.data === null ? 'null' : Array.isArray(r.data) ? `Array(${r.data.length})` :
    typeof r.data === 'object' ? `{${Object.keys(r.data).slice(0, 8).join(',')}}` : JSON.stringify(r.data)
  say(`${ok ? 'OK  ' : 'BAD '} ${p}  [${why}] → HTTP ${r.status} code=${r.code} data=${shape}` +
    (ok ? '' : ` message=${r.body && String(r.body.message).slice(0, 120)}`))
}

say('')
say(`==== 断言失败 ${failed} 项（第六部分只是"存在性盘点"，不参与计数）====`)
writeFileSync('admin-probe2.txt', lines.join('\n') + '\n', 'utf8')
console.log('已写出 admin-probe2.txt')
process.exit(failed ? 1 : 0)
