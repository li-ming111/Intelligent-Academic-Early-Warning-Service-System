// 学院管理员(role 5) 建号链路验证（W32，2026-10-09）。
//
// 背景：`college_admin_profile` 此前**全仓只有读和删、零 INSERT** —— 唯一两行是种子
// SQL 手工插的。于是任何新建的 role 5 账号都拿不到档案行，`CollegeAdminController
// .getCollegeId()` 回 null，该控制器 18 个接口一律 403（"能登录、能进界面、每页没数据"）。
// 本轮给**两条**建号路径都补上了写档案：
//   ① PC        AdminUsers.vue → POST /api/users/add     （user-service）
//   ② 小程序     admin/users.vue → POST /api/admin/users   （admin-service）
//
// 判据为什么必须落到数据库 + 学院端接口：
//   · 两条路径在建号成功时都回 code=200/success —— 修复前后一模一样，接口分不出来；
//   · **承重断言是"用新建账号去调一个学院管理端接口，不再是 403"**：
//     没有 college_admin_profile 行时那条恒 403，有行且 college_id 对才可能 200。
//
// ⚠️ 写目标全部是自建+带前缀(W32PRB)+结束即删的行；**不碰任何演示数据**。
// 收尾断言四张表的行数打前打后一致。
//
// 用法: node w32-college-admin-create-probe.mjs
// 输出: w32-college-admin-create-probe.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { execSync } from 'node:child_process'
import { captchaFields } from './_captcha.mjs'

const BASE = process.env.API_BASE || 'http://localhost:8076'
const MYSQL = process.env.MYSQL_BIN || 'D:/JAVA/mysql/mysql-8.0.44-winx64/mysql-8.0.44-winx64/bin/mysql.exe'
const DB = 'academic_warning_system'

// 两个前缀：一条走 PC 路径，一条走小程序路径。都用 W32PRB 开头好认、好清。
const U_PC = 'W32PRBpc1'
const U_XCX = 'W32PRBxcx1'
const U_NOCOL = 'W32PRBnocol1'
const ALL = [U_PC, U_XCX, U_NOCOL]
const COLLEGE_ID = 1        // 软件学院（活库存在的 4 个学院之一）

const lines = []
const say = s => { lines.push(s); console.log(s) }
let failed = 0
const step = s => { say(''); say(`—— ${s}`) }
const ok = s => say('  PASS ' + s)
const bad = s => { failed++; say('  FAIL ' + s) }
const info = s => say('       ' + s)

function sql(query) {
  const out = execSync(`"${MYSQL}" -uroot -proot -N -B ${DB} -e "${query.replace(/"/g, '\\"')}"`, {
    encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore']
  })
  return out.trim()
}
const count = q => sql(q) === '' ? 0 : Number(sql(q))
const uidOf = u => sql(`SELECT id FROM users WHERE username='${u}'`)

const req = (method) => async (path, body, headers = {}) => {
  const r = await fetch(`${BASE}${path}`, {
    method, headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const txt = await r.text()
  let j; try { j = JSON.parse(txt) } catch { j = { __raw: txt.slice(0, 200) } }
  return { http: r.status, code: j.code, data: j.data, message: j.message, body: j }
}
const get = req('GET'), post = req('POST'), del = req('DELETE')

const tryLogin = async (username, password) => {
  const r = await post('/api/auth/login', { username, password, ...(await captchaFields(BASE)) })
  const d = r.data || {}
  return { ok: !!d.token, status: d.status, message: d.message, token: d.token }
}

function cleanup() {
  const inList = ALL.map(u => `'${u}'`).join(',')
  sql(`DELETE FROM college_admin_profile WHERE user_id IN (SELECT id FROM users WHERE username IN (${inList}))`)
  sql(`DELETE FROM users WHERE username IN (${inList})`)
}

/** 四张表的行数快照，用来证明"只动了自建行"。 */
function snapshot() {
  return {
    users: count('SELECT COUNT(*) FROM users'),
    collegeAdminProfile: count('SELECT COUNT(*) FROM college_admin_profile'),
    studentProfile: count('SELECT COUNT(*) FROM student_profile'),
    academicWarning: count('SELECT COUNT(*) FROM academic_warning')
  }
}

// ============ 开跑 ============
say('学院管理员(role 5) 建号链路验证（W32）')
say(`时间：${new Date().toISOString()}`)
say(`网关：${BASE}`)

cleanup()
const before = snapshot()
say(`打前快照：users=${before.users} college_admin_profile=${before.collegeAdminProfile}`)

const admin = await tryLogin('admin', '123456')
if (!admin.ok) {
  bad(`管理员登录失败：${admin.status} ${admin.message}`)
  writeFileSync('w32-college-admin-create-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
ok('管理员 admin 登录成功')
const H = { Authorization: `Bearer ${admin.token}` }

// ---------- 一、PC 路径：POST /api/users/add?collegeId=1 ----------
step('一、PC 路径 POST /api/users/add?collegeId=1 建 role 5')
const pc = await post(`/api/users/add?collegeId=${COLLEGE_ID}`, {
  username: U_PC, name: 'W32探针院长甲', role: 5, password: '123456', email: `${U_PC}@probe.local`
}, H)
if (pc.code === 200) ok(`建号返回 code=200（id=${pc.data}）`)
else bad(`建号失败：http=${pc.http} code=${pc.code} ${pc.message}`)

const pcUid = uidOf(U_PC)
if (pcUid) ok(`users 表里有该账号 id=${pcUid}`)
else bad('users 表里查不到刚建的账号')

if (pcUid) {
  const rows = count(`SELECT COUNT(*) FROM college_admin_profile WHERE user_id=${pcUid}`)
  const cid = sql(`SELECT college_id FROM college_admin_profile WHERE user_id=${pcUid}`)
  if (rows === 1) ok(`college_admin_profile 有 1 行`)
  else bad(`college_admin_profile 行数=${rows}（期望 1）`)
  if (cid === String(COLLEGE_ID)) ok(`college_id=${cid}（期望 ${COLLEGE_ID}）`)
  else bad(`college_id=${cid}（期望 ${COLLEGE_ID}）`)

  // ★ 承重断言：没有档案行时这一条恒 403。
  const dash = await get(`/api/college-admin/dashboard/${pcUid}`, undefined, H)
  if (dash.http === 200 && dash.code === 200) ok(`GET /api/college-admin/dashboard/${pcUid} → 200（不再是 403）`)
  else bad(`学院管理端取数失败：http=${dash.http} code=${dash.code} ${dash.message}`)
}

// ---------- 二、小程序路径：POST /api/admin/users?collegeId=1 ----------
step('二、小程序路径 POST /api/admin/users?collegeId=1 建 role 5')
const xcx = await post(`/api/admin/users?collegeId=${COLLEGE_ID}`, {
  username: U_XCX, name: 'W32探针院长乙', role: 5, password: '123456', email: `${U_XCX}@probe.local`
}, H)
if (xcx.code === 200) ok(`建号返回 code=200`)
else bad(`建号失败：http=${xcx.http} code=${xcx.code} ${xcx.message}`)

const xcxUid = uidOf(U_XCX)
if (xcxUid) {
  const rows = count(`SELECT COUNT(*) FROM college_admin_profile WHERE user_id=${xcxUid}`)
  const cid = sql(`SELECT college_id FROM college_admin_profile WHERE user_id=${xcxUid}`)
  if (rows === 1) ok('college_admin_profile 有 1 行')
  else bad(`college_admin_profile 行数=${rows}（期望 1）`)
  if (cid === String(COLLEGE_ID)) ok(`college_id=${cid}（期望 ${COLLEGE_ID}）`)
  else bad(`college_id=${cid}（期望 ${COLLEGE_ID}）`)

  const dash = await get(`/api/college-admin/dashboard/${xcxUid}`, undefined, H)
  if (dash.http === 200 && dash.code === 200) ok(`GET /api/college-admin/dashboard/${xcxUid} → 200（不再是 403）`)
  else bad(`学院管理端取数失败：http=${dash.http} code=${dash.code} ${dash.message}`)
} else bad('users 表里查不到刚建的账号')

// ---------- 三、不带 collegeId：账号建出来、档案不建、如实提示 ----------
step('三、role 5 **不带** collegeId：账号建出、档案不建、message 如实说明')
const noCol = await post('/api/admin/users', {
  username: U_NOCOL, name: 'W32探针无院', role: 5, password: '123456'
}, H)
if (noCol.code === 200) ok(`建号返回 code=200：${noCol.message}`)
else bad(`建号失败：http=${noCol.http} code=${noCol.code} ${noCol.message}`)
if (String(noCol.message || '').includes('未绑定学院')) ok('message 如实说明"未绑定学院"')
else bad(`message 没有如实说明，实际="${noCol.message}"`)

const noColUid = uidOf(U_NOCOL)
if (noColUid) {
  const rows = count(`SELECT COUNT(*) FROM college_admin_profile WHERE user_id=${noColUid}`)
  if (rows === 0) ok('college_admin_profile 无行（不造 college_id=0 的假档案）')
  else bad(`college_admin_profile 行数=${rows}（期望 0）`)
  // 负向对照：没有档案行 ⇒ 学院端就该 403。这条证明上面那条 200 是有信息的。
  const dash = await get(`/api/college-admin/dashboard/${noColUid}`, undefined, H)
  if (dash.code === 403 || dash.http === 403) ok(`无档案时 dashboard → 403（符合"没有学院归属就不给数"）`)
  else bad(`无档案时 dashboard 竟回 http=${dash.http} code=${dash.code}（期望 403）`)
} else bad('users 表里查不到刚建的账号')

// ---------- 四、收尾：只删自建行，行数打前打后一致 ----------
step('四、清理探针行并复核行数')
cleanup()
const after = snapshot()
for (const k of Object.keys(before)) {
  if (before[k] === after[k]) ok(`${k}: ${before[k]} → ${after[k]}（一致）`)
  else bad(`${k}: ${before[k]} → ${after[k]}（不一致！有残留或误删）`)
}

say('')
say(failed === 0 ? '==== 全部 PASS ====' : `==== FAIL ${failed} 项 ====`)
writeFileSync('w32-college-admin-create-probe.txt', lines.join('\n') + '\n', 'utf8')
process.exit(failed === 0 ? 0 : 1)
