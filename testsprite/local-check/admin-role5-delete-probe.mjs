// 管理员删号的**学院管理员分支**验证（P7 修复项，2026-09-29）。
//
// 背景：AdminController.deleteUser 原来只清 role 1/2/3 的档案行，
// 删掉一个学院管理员账号后，college_admin_profile 会留下一行 user_id 指向不存在的账号。
// 它本身不报错（按学院查账号走的是 `users in (ids)`，多出来的 id 匹配不到人），
// 但会让"删干净"这句话不成立，也会污染 userIdsInCollege 的结果集。
//
// 为什么断言必须落到数据库：
//   · 删号接口返回的是 `success` —— 修复前后都是 success，接口自己不区分。
//   · 而建号接口**故意**不给 role=5 建档案行（档案表的 college_id 是 NOT NULL，
//     建号表单里没有学院字段），所以不能靠"建号→删号"来复现那种残留行 ——
//     必须自己用 SQL 造一行出来，才能验证到删除逻辑。
//
// 全程只碰一个临时账号（username=uiprobeadm5）+ 一行手工插入的档案行。
// 前置快照：db_backups/admin_role5_delete_20260929_before.sql
// 回滚：    db_backups/admin_role5_delete_20260929_rollback.sql
//
// 用法: node admin-role5-delete-probe.mjs
// 输出: admin-role5-delete-probe.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { execSync } from 'node:child_process'

const BASE = process.env.API_BASE || 'http://localhost:8076'
const MYSQL = process.env.MYSQL_BIN || 'D:/JAVA/mysql/mysql-8.0.44-winx64/mysql-8.0.44-winx64/bin/mysql.exe'
const DB = 'academic_warning_system'
const TMP_USER = 'uiprobeadm5'
const TMP_NAME = 'tmpadm5'

const lines = []
const say = s => { lines.push(s); console.log(s) }
let failed = 0
const step = s => { say(''); say(`—— ${s}`) }
const ok = s => say('  PASS ' + s)
const bad = s => { failed++; say('  FAIL ' + s) }
const info = s => say('       ' + s)

/** 只取一行一列，拿来做断言。SQL 里一律用 ASCII，避免命令行编码把中文搅坏。 */
function sql(query) {
  const out = execSync(`"${MYSQL}" -uroot -proot -N -B ${DB} -e "${query.replace(/"/g, '\\"')}"`, {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore']
  })
  return out.trim()
}
function sqlRow(query) {
  const s = sql(query)
  return s ? s.split('\t') : []
}

const req = (method) => async (path, body, headers = {}) => {
  const r = await fetch(`${BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const txt = await r.text()
  let j; try { j = JSON.parse(txt) } catch { j = { __raw: txt.slice(0, 200) } }
  return { status: r.status, code: j.code, data: j.data, message: j.message, body: j }
}
const get = req('GET')
const post = req('POST')
const del = req('DELETE')

const tryLogin = async (username, password) => {
  const r = await post('/api/auth/login', { username, password })
  const d = r.data || {}
  return { ok: !!d.token, status: d.status, message: d.message, token: d.token }
}

function cleanup() {
  sql(`DELETE FROM college_admin_profile WHERE user_id IN (SELECT id FROM users WHERE username='${TMP_USER}')`)
  sql(`DELETE FROM users WHERE username='${TMP_USER}'`)
}

// ============ 开跑 ============
say('管理员删号 · 学院管理员（role=5）分支验证')
say(`时间：${new Date().toISOString()}`)

// 起手先清残留，避免上一次跑崩了影响这次判据
cleanup()

const admin = await tryLogin('admin', '123456')
if (!admin.ok) {
  bad(`管理员登录失败：${admin.status} ${admin.message}`)
  writeFileSync('admin-role5-delete-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
ok('管理员 admin 登录成功')

// 管理员端点全部要登录态：网关按 Authorization 解析出 X-User-Id/X-Role 再透传给 admin-service，
// 不带就是 401「缺少认证信息」。
const H = { Authorization: `Bearer ${admin.token}` }

// ---------- 一、建一个 role=5 账号 ----------
step('一、POST /admin/users 建 role=5 账号')
const created = await post('/api/admin/users', {
  username: TMP_USER,
  name: TMP_NAME,
  role: 5,
  email: `${TMP_USER}@academic.edu`,
  password: '123456'
}, H)
if (created.code === 200) ok(`建号返回：${created.data || created.message}`)
else bad(`建号失败：code=${created.code} ${created.message}`)

let uid = sql(`SELECT id FROM users WHERE username='${TMP_USER}'`)
if (uid) ok(`users 表里查到该账号：id=${uid}`)
else bad('users 表里查不到刚建的账号')
if (!uid) {
  // 没有 id 后面的 SQL 会拼出 `WHERE user_id=` 直接语法报错，不如在这里停下
  say('')
  say('==== FAIL：建号没成功，后续断言无法进行 ====')
  writeFileSync('admin-role5-delete-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
if (uid) info(`该行 role=${sql(`SELECT role FROM users WHERE id=${uid}`)}（期望 5）`)

// ---------- 二、建号不建档案（这是设计，不是缺陷） ----------
step('二、建号时**不**生成 college_admin_profile 行（后端有意为之）')
const afterCreate = sql(`SELECT COUNT(*) FROM college_admin_profile WHERE user_id=${uid}`)
if (afterCreate === '0') ok('建号后档案行数 = 0 —— 与 createProfileIfNeeded 的"拿不到必填列就不建"一致')
else bad(`建号后已有 ${afterCreate} 行档案，与设计不符`)

// ---------- 三、手工插一行档案（复现修复前会残留的那种行） ----------
step('三、用 SQL 手工插一行档案，模拟"这个账号确实有档案"')
sql(`INSERT INTO college_admin_profile (user_id, name, college_id) VALUES (${uid}, '${TMP_NAME}', 1)`)
const afterInsert = sqlRow(`SELECT id, user_id, name, college_id FROM college_admin_profile WHERE user_id=${uid}`)
if (afterInsert.length) ok(`档案行已插入：id=${afterInsert[0]} user_id=${afterInsert[1]} college_id=${afterInsert[3]}`)
else bad('档案行插入失败，后面的断言没意义')

// ---------- 四、删号 ----------
step('四、DELETE /admin/users/{id}')
// 注意 req() 的签名是 (path, body, headers)，删号没有 body，headers 是第三个参数
const removed = await del(`/api/admin/users/${uid}`, undefined, H)
if (removed.code === 200) ok(`删号返回：${removed.data || removed.message}`)
else bad(`删号失败：code=${removed.code} ${removed.message}`)

const userLeft = sql(`SELECT COUNT(*) FROM users WHERE id=${uid}`)
if (userLeft === '0') ok('users 行已删除')
else bad(`users 里还剩 ${userLeft} 行`)

// 核心断言：这正是修复的那一行
const profileLeft = sql(`SELECT COUNT(*) FROM college_admin_profile WHERE user_id=${uid}`)
if (profileLeft === '0') ok('college_admin_profile 里的那一行**也**被删掉了（本次修复的落点）')
else bad(`college_admin_profile 还残留 ${profileLeft} 行 —— 修复没生效`)

// ---------- 五、不变量：四张档案表都不该有孤儿行 ----------
step('五、四张档案表均无孤儿行（user_id 指向不存在的账号）')
const tables = [
  ['student_profile', 'student_profile'],
  ['teacher_profile', 'teacher_profile'],
  ['counselor_profiles', 'counselor_profiles'],
  ['college_admin_profile', 'college_admin_profile']
]
for (const [label, t] of tables) {
  const n = sql(`SELECT COUNT(*) FROM ${t} p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id=p.user_id)`)
  if (n === '0') ok(`${label}：0 行孤儿`)
  else bad(`${label}：${n} 行孤儿`)
}

// ---------- 六、残留检查 ----------
step('六、残留检查')
cleanup()
const residueUser = sql(`SELECT COUNT(*) FROM users WHERE username='${TMP_USER}'`)
const residueProfile = sql(`SELECT COUNT(*) FROM college_admin_profile WHERE name='${TMP_NAME}'`)
if (residueUser === '0' && residueProfile === '0') ok('临时账号与临时档案行均已清理，残留 0')
else bad(`残留：users=${residueUser} college_admin_profile=${residueProfile}`)

say('')
if (failed === 0) {
  say('==== PASS：学院管理员删号分支断言全部通过 ====')
  say('（需要说明：为此手工插了一行档案 —— 这是为了构造出"有档案的 role=5 账号"，')
  say('  真实流程里学院管理员的档案行由别处（学院管理员端/初始化）写入。）')
} else {
  say(`==== FAIL：${failed} 条断言未通过 ====`)
  say('用 db_backups/admin_role5_delete_20260929_rollback.sql 回滚。')
}

writeFileSync('admin-role5-delete-probe.txt', lines.join('\n') + '\n', 'utf8')
process.exit(failed === 0 ? 0 : 1)
