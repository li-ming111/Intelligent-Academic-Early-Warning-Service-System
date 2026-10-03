// 管理员端**写路径**验证：用户 CRUD / 审批 / 广播落库（P7 的验收项）。
//
// 为什么不能只对着接口喊一声 200：本工程已经抓到一个活生生的反例 ——
// message-service 的 /messages/broadcast 把每个用户的插入异常吞在 catch 里，
// 然后照样返回 "Broadcast sent to N users"。也就是说**响应说的是成功，库里什么都没写**。
// 所以这里的每一条断言都落在"事后能观察到的状态"上：
//   · 建号 → 用新号**真的登录一次**（拿得到 data.token 才算数）
//   · 改号 → 再登一次，看新旧口令哪个进得去
//   · 停用 → 登录必须失败（业务上就该失败）
//   · 广播 → 不看 sentCount，去读消息表，比 sentCount 与实到条数
//
// 全程只碰一个临时账号（username=uiprobetmp），最后一步删掉它；
// 中途崩了就用 db_backups/admin_write_probe_20260929_rollback.sql 清干净。
//
// 前置快照（必须先生成）：db_backups/admin_write_probe_20260929_before.sql
//   —— users / teacher_profile / messages 三张表
//
// 用法: node admin-write-probe.mjs
// 输出: admin-write-probe.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { captchaFields } from './_captcha.mjs'

const BASE = process.env.API_BASE || 'http://localhost:8076'
const TMP_USER = 'uiprobetmp'
const TMP_EMAIL = 'uiprobetmp@academic.edu'
const TMP_NAME = '临时验证账号'
const PWD_A = '123456'
const PWD_B = '654321'

const lines = []
const say = s => { lines.push(s); console.log(s) }
let failed = 0
const step = (s) => { say(''); say(`—— ${s}`) }
const ok = s => say('  PASS ' + s)
const bad = s => { failed++; say('  FAIL ' + s) }
const info = s => say('       ' + s)

const post = async (path, body, headers = {}) => {
  const r = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const txt = await r.text()
  let j; try { j = JSON.parse(txt) } catch { j = { __raw: txt.slice(0, 200) } }
  return { status: r.status, code: j.code, data: j.data, message: j.message, body: j }
}
const put = async (path, body, headers = {}) => {
  const r = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const txt = await r.text()
  let j; try { j = JSON.parse(txt) } catch { j = { __raw: txt.slice(0, 200) } }
  return { status: r.status, code: j.code, data: j.data, message: j.message, body: j }
}
const del = async (path, headers = {}) => {
  const r = await fetch(`${BASE}${path}`, { method: 'DELETE', headers })
  const txt = await r.text()
  let j; try { j = JSON.parse(txt) } catch { j = { __raw: txt.slice(0, 200) } }
  return { status: r.status, code: j.code, data: j.data, message: j.message, body: j }
}
const get = async (path, headers = {}) => {
  const r = await fetch(`${BASE}${path}`, { headers })
  const txt = await r.text()
  let j; try { j = JSON.parse(txt) } catch { j = { __raw: txt.slice(0, 200) } }
  return { status: r.status, code: j.code, data: j.data, message: j.message, body: j }
}

/** 登录判据只有一个：data.token 存在（登录失败时 HTTP 仍是 200、code 仍是 200）。 */
// ⚠️ 2026-10-03：W1.12（58b889c2）把验证码改成服务端签发+校验之后，这里**必须**带
// captchaId/captcha —— 少带两个字段一律回 `code:500 验证码错误或已过期`，症状是
// "管理员登录失败"，看着像账号坏了。那次批量改脚本**漏掉了这个函数**（只 import 了
// captchaFields 却没用上），同一次还把这文件第 146 行改成了语法错（模板串里的 `${r.code, ...}`）
// ⇒ 这个探针从 2026-10-02 起**根本跑不起来**，只是一直没人再跑过，所以没暴露。
// 每次登录都取一张新验证码（旧的是单次凭据，复用会失败）。
const tryLogin = async (username, password) => {
  const r = await post('/api/auth/login', { username, password, ...(await captchaFields(BASE)) })
  const d = r.data || {}
  return { ok: !!(d.token), status: d.status, message: d.message, token: d.token, userId: d.userId }
}

// ============ 登录管理员 ============
const admin = await tryLogin('admin', '123456')
if (!admin.ok) {
  say(`FAIL 管理员登录失败：${admin.status} ${admin.message}`)
  writeFileSync('admin-write-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}
const H = { Authorization: `Bearer ${admin.token}` }
say(`管理员登录成功（userId=${admin.userId}）`)

/** 在 role=2 的分页里找临时账号，返回整行（找得到才说明列表接口也看得见它）。 */
const findTmpUser = async () => {
  const r = await get('/api/admin/users/page?page=1&size=100&role=2', H)
  const items = (r.data && r.data.items) || []
  return items.find(u => u.username === TMP_USER) || null
}

// ============ 零、前置：确认临时账号本来不存在 ============
step('零、前置检查：临时账号应当不存在')
{
  const before = await findTmpUser()
  if (before) bad(`临时账号 ${TMP_USER} 已存在（id=${before.id}）—— 先跑 rollback 脚本再验`)
  else ok(`role=2 里没有 ${TMP_USER}`)
  info('（若这条就失败，后面所有步骤都会往别人的数据上写，必须停）')
  if (before) {
    writeFileSync('admin-write-probe.txt', lines.join('\n') + '\n', 'utf8')
    process.exit(1)
  }
}

// ============ 一、建号：能用新号登录才算建成功 ============
step('一、POST /admin/users 建号 → 用新号登录')
let tmpId = null
{
  const r = await post('/api/admin/users', {
    username: TMP_USER, name: TMP_NAME, role: 2, email: TMP_EMAIL, password: PWD_A
  }, H)
  if (r.code !== 200) bad(`建号失败：code=${r.code} message=${r.message}`)
  else ok('建号返回 200')

  const row = await findTmpUser()
  if (!row) bad('建号后列表里找不到这个账号')
  else {
    tmpId = row.id
    ok(`列表里找到了：id=${tmpId} name=${row.name} role=${row.role} status=${row.status}` +
       (row.collegeName ? ` college=${row.collegeName}` : ''))
    if (row.status !== 1) bad(`status=${row.status}（应为 1，新账号就该是可用状态）`)
    else ok('status=1')
  }

  const lg = await tryLogin(TMP_USER, PWD_A)
  if (!lg.ok) bad(`新号登录失败：${lg.status} ${lg.message} —— 口令没被正确加密，或者账号状态不对`)
  else ok(`新号登录成功（userId=${lg.userId}），说明口令已按 BCrypt 入库`)
}
if (tmpId === null) {
  say('')
  say('==== 建号没成功，后面的步骤没有意义，中止 ====')
  writeFileSync('admin-write-probe.txt', lines.join('\n') + '\n', 'utf8')
  process.exit(1)
}

// ============ 二、重名建号必须被拒 ============
step('二、重名建号必须被拒（createUser 的重名检查）')
{
  const r = await post('/api/admin/users', {
    username: TMP_USER, name: '重名测试', role: 2, password: PWD_A
  }, H)
  if (r.code === 200) bad(`重名建号竟然返回成功：${JSON.stringify(r.body).slice(0, 160)}`)
  else ok(`重名被拒：code=${r.code} message=${String(r.message).slice(0, 80)}`)
}

// ============ 三、改资料但口令留空：不许动口令 ============
step('三、PUT /admin/users/{id} 改姓名、password 传空串 → 口令不许被清掉')
{
  const r = await put(`/api/admin/users/${tmpId}`, { name: TMP_NAME + '改名', password: '' }, H)
  if (r.code !== 200) bad(`改名失败：code=${r.code} message=${r.message}`)
  else ok('改名返回 200')

  const row = await findTmpUser()
  if (row && row.name === TMP_NAME + '改名') ok(`列表里姓名已变：${row.name}`)
  else bad(`列表里姓名没变：${row && row.name}`)

  const lg = await tryLogin(TMP_USER, PWD_A)
  if (lg.ok) ok('原名口令仍能登录（空串没有把口令写成空）')
  else bad(`原名口令登不上了：${lg.status} ${lg.message} —— 这就是"留空改资料把账号锁死"那个坑`)
}

// ============ 四、指定新口令：新口令进得去、旧口令进不去 ============
step('四、PUT /admin/users/{id} 传 password=654321 → 新口令生效、旧口令失效')
{
  const r = await put(`/api/admin/users/${tmpId}`, { password: PWD_B }, H)
  if (r.code !== 200) bad(`改口令失败：code=${r.code} message=${r.message}`)
  else ok('改口令返回 200')

  const newLg = await tryLogin(TMP_USER, PWD_B)
  if (newLg.ok) ok('新口令 654321 能登录')
  else bad(`新口令登不上：${newLg.status} ${newLg.message}`)

  const oldLg = await tryLogin(TMP_USER, PWD_A)
  if (oldLg.ok) bad('旧口令 123456 仍然能登录 —— 改口令没真正生效')
  else ok(`旧口令已失效：${oldLg.message}`)
}

// ============ 五、重置口令 ============
step('五、POST /admin/users/{id}/reset-password → 回到演示口令 123456')
{
  const r = await post(`/api/admin/users/${tmpId}/reset-password`, undefined, H)
  if (r.code !== 200) bad(`重置失败：code=${r.code} message=${r.message}`)
  else ok(`重置返回：${String(r.message).slice(0, 100)}`)

  const lg = await tryLogin(TMP_USER, PWD_A)
  if (lg.ok) ok('重置后 123456 能登录 —— 提示语与真实行为一致')
  else bad(`重置后 123456 登不上：${lg.status} ${lg.message} —— 提示语在说谎`)
}

// ============ 六、停用 / 启用 ============
step('六、POST /admin/users/{id}/toggle-status 停用 → 登录必须失败；再点一次恢复')
{
  const off = await post(`/api/admin/users/${tmpId}/toggle-status`, undefined, H)
  if (off.code !== 200) bad(`停用失败：code=${off.code} message=${off.message}`)
  else ok('停用返回 200')

  const row = await findTmpUser()
  if (row && row.status === 0) ok('列表里 status=0')
  else bad(`列表里 status=${row && row.status}（应为 0）`)

  const lg = await tryLogin(TMP_USER, PWD_A)
  if (lg.ok) bad('停用后居然还能登录 —— 状态位没被登录校验用上')
  else ok(`停用后登录被拒：${lg.message}`)

  const on = await post(`/api/admin/users/${tmpId}/toggle-status`, undefined, H)
  if (on.code === 200) ok('再次点击返回 200')
  const row2 = await findTmpUser()
  if (row2 && row2.status === 1) ok('列表里 status=1（恢复可用）')
  else bad(`恢复后 status=${row2 && row2.status}（应为 1）`)
  const lg2 = await tryLogin(TMP_USER, PWD_A)
  if (lg2.ok) ok('恢复后又能登录')
  else bad(`恢复后仍登不上：${lg2.status} ${lg2.message}`)
}

// ============ 七、审批 ============
step('七、PUT /admin/users/{id}/approve → 通过后 status=1')
{
  const r = await put(`/api/admin/users/${tmpId}/approve`, undefined, H)
  if (r.code !== 200) bad(`审批失败：code=${r.code} message=${r.message}`)
  else ok(`审批返回：${String(r.message).slice(0, 80)}`)
  const row = await findTmpUser()
  if (row && row.status === 1) ok('status=1')
  else bad(`审批后 status=${row && row.status}`)
}

// ============ 八、读接口不许回传口令哈希 ============
step('八、GET /admin/users/{id} 与 /admin/users/all 不许带出口令哈希')
{
  // 判据是"没回传口令哈希"，不是"字段名不出现"：Jackson 默认把 null 也序列化出来，
  // `"password": null` 是干净的值，字段缺失或缺失都不算泄露。所以这里看的是**值**。
  const r = await get(`/api/admin/users/${tmpId}`, H)
  const d = r.data || {}
  if (d.password) bad(`单用户详情回传了口令值（长度 ${String(d.password).length}）`)
  else ok('单用户详情没回传口令（password 为 null 或字段缺省）')

  const all = await get('/api/admin/users/all', H)
  const rows = Array.isArray(all.data) ? all.data : []
  const leaked = rows.filter(u => u && u.password)
  if (leaked.length) bad(`/users/all 里有 ${leaked.length} 行回传了口令值`)
  else ok(`/users/all 的 ${rows.length} 行都没回传口令`)
}

// ============ 九、广播：不看 sentCount，去数消息表 ============
step('九、POST /admin/messages/broadcast 广播给 role=5 → 以消息表实到条数为准')
{
  const before = await get('/api/admin/messages/list?page=1&size=100', H)
  const beforeTotal = (before.data && before.data.total) || 0
  info(`广播前消息总数 = ${beforeTotal}`)

  const title = '写路径验证-临时广播'
  const r = await post('/api/admin/messages/broadcast', {
    title, content: '这条是 P7 写路径验证用的广播，验证后会清掉。', targetRole: 5
  }, H)
  const claimed = r.data && r.data.sentCount
  if (r.code !== 200) bad(`广播接口返回 code=${r.code} message=${r.message}`)
  else ok(`广播接口返回 200，自称 sentCount=${claimed}`)

  const after = await get('/api/admin/messages/list?page=1&size=100', H)
  const afterTotal = (after.data && after.data.total) || 0
  const items = (after.data && after.data.items) || []
  const mine = items.filter(m => m.title === title)
  info(`广播后消息总数 = ${afterTotal}（其中标题为「${title}」的 ${mine.length} 条）`)

  if (!mine.length) {
    bad(`接口自称发出 ${claimed} 条，但消息表里一条都没有 —— 这就是"响应成功、库里没写"` +
        `（message-service 的 broadcast 把异常吞在 catch 里，sentCount 照加）`)
  } else if (claimed !== afterTotal - beforeTotal) {
    bad(`自称 ${claimed} 条，实际新增 ${afterTotal - beforeTotal} 条`)
  } else {
    // ⚠️ 2026-10-03：这里原来打的是 `实到 ${mine.length} 条`，而 mine 是**标题匹配的全部行**，
    //   会把上一轮残留的同类广播一起数进来（这次实测 4 条里有 2 条是 2026-09-29 的残留），
    //   于是输出成 "实到 4 条，与自称的 2 条一致" —— 判据用的是增量（对的），措辞是错的。
    //   判据与措辞都改成**本次新增**（afterTotal - beforeTotal）。
    ok(`本次实到 ${afterTotal - beforeTotal} 条（标题匹配共 ${mine.length} 条，含历史残留），与自称的 ${claimed} 条一致`)
    if (mine[0].receiverId) ok(`抄一条看内容：receiverId=${mine[0].receiverId} type=${mine[0].type} status=${mine[0].status}`)
  }
}

// ============ 十、删号：账号与配套资料一起走 ============
step('十、DELETE /admin/users/{id} → 账号消失、登录失败、profile 一起清掉')
{
  const r = await del(`/api/admin/users/${tmpId}`, H)
  if (r.code !== 200) bad(`删号失败：code=${r.code} message=${r.message}`)
  else ok(`删号返回：${String(r.message).slice(0, 80)}`)

  const row = await findTmpUser()
  if (row) bad('删号后列表里还能找到它')
  else ok('列表里已消失')

  const lg = await tryLogin(TMP_USER, PWD_A)
  if (lg.ok) bad('删号后仍能登录')
  else ok(`删号后登录被拒：${lg.message}`)

  const r2 = await get('/api/admin/users/page?page=1&size=100&role=2', H)
  const n = ((r2.data && r2.data.items) || []).length
  info(`role=2 现有 ${n} 个账号（验证前是 6 个）`)
  if (n !== 6) bad(`role=2 账号数 ${n}，应回到 6 —— 临时账号没删干净，或者删多了`)
  else ok('role=2 账号数回到 6')
}

say('')
say(`==== ${failed === 0 ? 'PASS：写路径全部断言通过' : `FAIL：${failed} 项断言失败`} ====`)
say('（消息表里那条临时广播、以及自增计数器要按 rollback 脚本清理）')
writeFileSync('admin-write-probe.txt', lines.join('\n') + '\n', 'utf8')
console.log('已写出 admin-write-probe.txt')
process.exit(failed ? 1 : 0)
