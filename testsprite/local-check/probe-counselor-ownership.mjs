// 辅导员端归属闸 —— `/api/counselors/**` 全族扫描 (W13, 2026-10-04)
//
// 威胁模型: 网关的 PREFIX_COUNSELOR 只保证"打进来的是个辅导员"(role=3), 挡得住学生/教师,
//           **挡不住辅导员之间互相看/改**; 而这条前缀下近 60 个端点, 标识符全由客户端给。
//
// 判据(每条成对, 缺一条就不算过):
//   cross: 陌生身份(users.id 9999) 拿**别人(53)的** counselor_id / classId / studentId /
//          warningId / planId / logId 去读  -> 必须被拒
//   own  : 本人(53) 拿**自己的**同一批 id 去读  -> 必须放行(200)
//   只测 cross 会把"接口整个坏了"读成"修好了"; 只测 own 会把"没装闸"读成"没误伤"。
//
// 为什么"跨人"在这里跑得起来(而 W12 以为跑不起来):
//   服务层身份来自 `GatewayAuthFilter` 读的 `X-User-Id` + `X-Role` 头 —— **直连服务时手工注入即可**,
//   不需要第二个数据库账号, 也不需要真 token。所以"活库只有 1 个辅导员账号"不是障碍。
//
// 方法论(都是踩过的坑):
//   * 直连**刚构建的备用端口**注入身份头, 不过网关 —— 网关的 403 会把"闸装没装"与"网关拦没拦"
//     混成一个数字。备用端口上的实例必须用**当前源码**起(target/classes, 见文档 §6.46)。
//   * 关键证据是**自然对照**: 同一份 cross 列表打**现役旧 jar** 会看到真实数据泄漏。
//     设 COUNSELOR_BASELINE_PORT(例如 8074)即会跑一遍并**如实报出**泄漏条数(那是证据, 不是判据)。
//   * 被拒的判据**不能只看 HTTP 状态码**: 本仓归属拒绝的惯用形状是 `HTTP 200 + body{code:403}`
//     (前端按 code 判)。只断言状态码会把第 3 种形状读成"通过"。
//   * 全输出只用 ASCII(Windows 控制台 GBK, 打印 U+21D2/U+2605 会抛 UnicodeEncodeError, W10 亲历)。
//
// 🔴 **纯只读, 硬约束(第一版栽过, 必须记住)**:
//   第一版把会**写库**的 `POST /warnings/{warningId}/process` 放进了成对扫描 —— 于是
//   ⓐ candidate 的 own 腿处理掉了真实预警 395, ⓑ **baseline 腿更糟**: 打的是**现役旧 jar**,
//   它没有归属闸 ⇒ cross 与 own 两条腿**都真的处理了一次**, 还各发出一条通知。
//   一次扫描往真实演示数据里写了 3 条通知 + 改了 1 行预警。
//   ⇒ **规则: 基线扫描只准打只读端点。** 会改状态的端点一律从成对扫描里拿掉;
//     它们的归属校验由单测覆盖(那里跑的是 mock, 不碰库)。
//     若确实要 E2E, 只能**单独列出**、且**只对 candidate 跑一次应当被拒的调用**。
//
// 用法: node probe-counselor-ownership.mjs
//   需要: 8189 = counselor-service, 以当前源码起、连活库。
//   可选: COUNSELOR_PORT / COUNSELOR_BASELINE_PORT / DB / DB_USER / DB_PASS

import { execFileSync } from 'node:child_process'

// 输出只用 ASCII, 但 Windows 控制台是 GBK: 万一有非 ASCII 漏进来, 宁可替换也不让
// print 抛 UnicodeEncodeError(那个异常会崩在一条 PASS 上并跳过收尾 —— W10 亲历)。
// 管道(stdout 不是 tty)时没有 reconfigure, 所以先判存在。
if (typeof process.stdout.reconfigure === 'function') {
  process.stdout.reconfigure({ errors: 'replace' })
}

const PORT = process.env.COUNSELOR_PORT || '8189'
const BASELINE_PORT = process.env.COUNSELOR_BASELINE_PORT || ''
const DB = process.env.DB || 'academic_warning_system'
const DB_USER = process.env.DB_USER || 'root'
const DB_PASS = process.env.DB_PASS || 'root'

const ME = 53        // 活库唯一那位辅导员 (users.id)
const FOREIGN = 9999 // 一个**不存在**的用户 id 当"陌生辅导员": 只要不是 53, 就无权看 53 的东西

// ---------------------------------------------------------------- 活库真值
function q(sql) {
  let out
  try {
    out = execFileSync('mysql', [`-u${DB_USER}`, `-p${DB_PASS}`, DB, '-N', '-e', sql],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  } catch (e) {
    console.error('[FATAL] cannot query live DB. This probe refuses to guess ownership.')
    console.error('        ' + String(e.message).split('\n')[0])
    process.exit(2)
  }
  return out.split('\n').map((s) => s.trim()).filter(Boolean)
}
function firstId(sql) {
  const rows = q(sql)
  return rows.length ? rows[0].split('\t')[0] : null
}

console.log('===== DB truth (live) =====')
const MY_CLASS = firstId(`SELECT id FROM classes WHERE counselor_id = ${ME} ORDER BY id LIMIT 1`)
const N_CLASSES = q(`SELECT COUNT(*) FROM classes WHERE counselor_id = ${ME}`)[0] || '0'
const MY_PROFILE = MY_CLASS
  ? firstId(`SELECT id FROM student_profile WHERE class_id = ${MY_CLASS} ORDER BY id LIMIT 1`)
  : null
const MY_WARNING = MY_PROFILE
  ? firstId(`SELECT id FROM academic_warning WHERE student_id = ${MY_PROFILE} ORDER BY id LIMIT 1`)
  : null
const MY_PLAN = firstId(`SELECT id FROM assistance_plans WHERE counselor_id = ${ME} ORDER BY id LIMIT 1`)
const MY_LOG = firstId(`SELECT id FROM communication_logs WHERE counselor_id = ${ME} ORDER BY id LIMIT 1`)
const MY_COURSE = MY_PROFILE
  ? firstId(`SELECT course_id FROM scores WHERE student_id = ${MY_PROFILE} ORDER BY course_id LIMIT 1`)
  : null

console.log(`  counselor(users.id) = ${ME}, owns ${N_CLASSES} classes`)
console.log(`  MY_CLASS   = ${MY_CLASS}    MY_PROFILE = ${MY_PROFILE}`)
console.log(`  MY_WARNING = ${MY_WARNING}  MY_PLAN    = ${MY_PLAN}`)
console.log(`  MY_LOG     = ${MY_LOG}      MY_COURSE  = ${MY_COURSE}`)

// 硬前提: 缺了就没法构造 own 腿 —— 报错退出, 不许把"测不了"当"通过"
if (!MY_CLASS || !MY_PROFILE) {
  console.error('\n[FATAL] live DB has no class/student under counselor 53. Fixture assumptions broken.')
  process.exit(2)
}

// ---------------------------------------------------------------- 发请求
async function call(port, path, userId, role, method = 'GET', body = null) {
  const res = await fetch(`http://localhost:${port}${path}`, {
    method,
    headers: {
      'X-User-Id': String(userId), 'X-Role': role, 'X-Username': 'probe',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  const text = await res.text()
  let parsed = null
  try { parsed = JSON.parse(text) } catch { /* non-JSON body stays null */ }
  return { http: res.status, code: parsed?.code, data: parsed?.data, body: parsed }
}
// 本仓惯例: 归属拒绝 = HTTP 200 + body{code:403}; 也接受真 4xx。
// (文件下载端点是唯一必须回真 4xx 的一族, 本探针不含。)
function isDenied(r) { return r.http >= 400 || r.code === 403 || r.code === 404 }
function isAllowed(r) { return r.http === 200 && (r.code === 200 || r.code === undefined || r.code === null) }

const results = []
function record(kind, label, ok, detail) {
  results.push({ kind, label, ok, detail })
  console.log(`  ${ok ? '[OK]  ' : '[FAIL]'} ${kind.padEnd(5)} ${label.padEnd(52)} ${detail}`)
}

// ---------------------------------------------------------------- 端点族
// 每条 = { label, path, method?, body? } —— path 里的 id 按"属于 53"构造。
const EPS = [
  // counselor_id 型
  { label: '/classes?counselor_id=', path: `/counselors/classes?counselor_id=${ME}` },
  { label: '/credit-monitor?counselor_id=', path: `/counselors/credit-monitor?counselor_id=${ME}` },
  { label: '/credit-insufficient?counselor_id=', path: `/counselors/credit-insufficient?counselor_id=${ME}` },
  { label: '/scores/low-score?counselor_id=', path: `/counselors/scores/low-score?counselor_id=${ME}` },
  { label: `/scores/distribution/{course}?counselor_id=`, path: `/counselors/scores/distribution/${MY_COURSE}?counselor_id=${ME}` },
  { label: '/notifications/history?counselor_id=', path: `/counselors/notifications/history?counselor_id=${ME}` },
  { label: '/notifications/weekly-count?counselor_id=', path: `/counselors/notifications/weekly-count?counselor_id=${ME}` },
  { label: '/enrollments?counselor_id=', path: `/counselors/enrollments?counselor_id=${ME}` },
  { label: '/classes/warnings/compare?counselor_id=', path: `/counselors/classes/warnings/compare?counselor_id=${ME}` },
  { label: '/analytics/credit-insufficient?counselor_id=', path: `/counselors/analytics/credit-insufficient?counselor_id=${ME}` },
  { label: '/analytics/warning-distribution?counselor_id=', path: `/counselors/analytics/warning-distribution?counselor_id=${ME}` },
  { label: '/analytics/handling-efficiency?counselor_id=', path: `/counselors/analytics/handling-efficiency?counselor_id=${ME}` },
  { label: '/analytics/credit-achievement-ranking?counselor_id=', path: `/counselors/analytics/credit-achievement-ranking?counselor_id=${ME}` },
  { label: '/analytics/warning-trend?counselor_id=', path: `/counselors/analytics/warning-trend?counselor_id=${ME}` },
  { label: '/analytics/assistance-completion?counselor_id=', path: `/counselors/analytics/assistance-completion?counselor_id=${ME}` },
  { label: '/dashboard/{userId}', path: `/counselors/dashboard/${ME}` },
  { label: '/students?counselor_id=', path: `/counselors/students?counselor_id=${ME}` },
  { label: '/warnings?counselor_id=', path: `/counselors/warnings?counselor_id=${ME}` },
  { label: '/{counselorId}/assistance-plans', path: `/counselors/${ME}/assistance-plans` },
  { label: '/{counselorId}/evaluations', path: `/counselors/${ME}/evaluations` },
  { label: '/{counselorId}/communication-logs', path: `/counselors/${ME}/communication-logs` },
  { label: '/{counselorId}/students', path: `/counselors/${ME}/students` },
  { label: '/class-management/my-classes?counselorId=', path: `/counselors/class-management/my-classes?counselorId=${ME}` },
  { label: '/class-management/requests?counselorId=', path: `/counselors/class-management/requests?counselorId=${ME}` },
  // classId 型
  { label: '/scores/class/{classId}?counselor_id=', path: `/counselors/scores/class/${MY_CLASS}?counselor_id=${ME}` },
  { label: '/classes/{classId}/detail', path: `/counselors/classes/${MY_CLASS}/detail` },
  { label: '/classes/{classId}/students', path: `/counselors/classes/${MY_CLASS}/students` },
  { label: '/classes/{classId}/warnings', path: `/counselors/classes/${MY_CLASS}/warnings` },
  // studentId 型 (student_profile.id)
  { label: '/students/{studentId}', path: `/counselors/students/${MY_PROFILE}` },
  { label: '/scores/student/{studentId}/trend', path: `/counselors/scores/student/${MY_PROFILE}/trend` },
  { label: '/students/{studentId}/assistance-plans', path: `/counselors/students/${MY_PROFILE}/assistance-plans` },
  { label: '/students/{studentId}/communication-logs', path: `/counselors/students/${MY_PROFILE}/communication-logs` },
  // planId 型 (活库没有就跳过, 记 UNTESTABLE)
  MY_PLAN ? { label: '/assistance-plans/{planId}/evaluations', path: `/counselors/assistance-plans/${MY_PLAN}/evaluations` } : null,
]
// ⚠️ 这里**不放** `POST /warnings/{warningId}/process`: 它会写库。见文件头的红线说明 ——
//    它曾经把真实预警 395 处理掉、还往 notifications 里插了 3 行。
  .filter(Boolean)

// 会**改状态**的端点单独列出, **只对 candidate 打一次"应当被拒"的调用**(被拒 ⇒ 不写库)。
// ⚠️ 绝不能进 baseline 扫描: 旧 jar 没有闸, 那两条腿会真的写进去。
// 参数用**属于本人**的 id: 这样"被拒"只可能来自归属闸, 不可能是"这条不存在"。
const DENY_ONLY_WRITES = MY_WARNING ? [
  {
    label: '/warnings/{warningId}/process (mutating)',
    path: `/counselors/warnings/${MY_WARNING}/process`, method: 'POST', body: { handleResult: 'probe-must-be-denied' },
  },
] : []

// ---------------------------------------------------------------- 扫描
async function scan(port, { baseline }) {
  console.log(`\n===== ${baseline ? 'BASELINE (old live jar)' : 'CANDIDATE (fresh build)'} on port ${port} =====`)
  let leak = 0, overBlock = 0, ok = 0, untestable = 0
  for (const ep of EPS) {
    const cross = await call(port, ep.path, FOREIGN, '3', ep.method || 'GET', ep.body)
    const own = await call(port, ep.path, ME, '3', ep.method || 'GET', ep.body)
    const crossOk = isDenied(cross)
    const ownOk = isAllowed(own)
    if (!crossOk) leak++
    if (!ownOk) overBlock++
    if (crossOk && ownOk) ok++
    if (!crossOk && !ownOk) untestable++
    if (baseline) {
      console.log(`  ${crossOk ? 'blocked' : 'LEAK   '} ${ep.label.padEnd(52)} http=${cross.http} code=${cross.code}`)
    } else {
      record('cross', ep.label, crossOk, `stranger -> http=${cross.http} code=${cross.code}` + (crossOk ? '' : '  <-- LEAK'))
      record('own  ', ep.label, ownOk, `owner    -> http=${own.http} code=${own.code}` + (ownOk ? '' : '  <-- OVER-BLOCK'))
    }
  }
  return { leak, overBlock, ok, untestable, total: EPS.length }
}

// "不写库"要**自证**, 不能靠"我以为我只发了 GET":
// 拿被测那条预警的 (status, handle_result) 当哨兵, **任何请求之前**先读一次。
function warningSentinel() {
  const row = q(`SELECT status, IFNULL(handle_result,'') FROM academic_warning WHERE id = ${MY_WARNING}`)[0]
  return row || '(row missing)'
}
const SENTINEL_BEFORE = warningSentinel()
// 另一个哨兵: 今天的 notifications 行数 —— 处理预警会连带发通知, 这是第二个泄写口。
const NOTIF_TODAY_BEFORE = Number(q(`SELECT COUNT(*) FROM notifications WHERE created_at >= CURDATE()`)[0] || '-1')

const base = BASELINE_PORT ? await scan(BASELINE_PORT, { baseline: true }) : null
const cand = await scan(PORT, { baseline: false })

// 两个额外形状(不是 own/cross 对, 单列): 提权口 与 "以别人的名义发通知"
console.log('\n===== extra: privilege escalation / body-supplied identity =====')
async function extra(label, path, userId, role, body, method = 'POST') {
  const r = await call(PORT, path, userId, role, method, body)
  const ok = isDenied(r)
  record('esc  ', label, ok, `http=${r.http} code=${r.code} message=${r.body?.message ?? ''}`)
  return ok
}
await extra('counselor 53 self-approves a class application',
  '/counselors/class-management/applications/1/review', ME, '3', { status: 1 })
await extra('counselor 53 lists ALL class applications (admin-only)',
  '/counselors/class-management/applications', ME, '3', null, 'GET')
await extra('counselor 53 notifies AS counselor 54 (snake_case spelling)',
  '/counselors/students/notify', ME, '3', { counselor_id: 54, title: 't', content: 'c' })
// 🔴 W17: **必须**同时测驼峰拼写。上面那条只覆盖了闸恰好读的那个键, 于是
//    "闸整个被跳过"这件事在这份探针里**一直是绿的**(判据太窄, 同 W8/W12 那族)。
//    真实调用方发的就是驼峰: CounselorNotifications.vue / CounselorCourses.vue。
await extra('counselor 53 notifies AS counselor 54 (camelCase spelling - the REAL UI spelling)',
  '/counselors/students/notify', ME, '3', { counselorId: 54, title: 't', content: 'c' })
// 会写库的端点: **只对 candidate 打一次"应当被拒"的调用**(被拒 ⇒ 没有写发生)。
// 跨人 + 本人资源的 id ⇒ 403 只可能来自归属闸。baseline 腿**绝不**跑这一组。
console.log('\n===== mutating endpoints: cross-only, candidate only, expect DENY =====')
for (const ep of DENY_ONLY_WRITES) {
  const r = await call(PORT, ep.path, FOREIGN, '3', ep.method, ep.body)
  record('cross', ep.label, isDenied(r), `stranger -> http=${r.http} code=${r.code}` + (isDenied(r) ? '' : '  <-- LEAK (AND A REAL WRITE)'))
}
const adminOk = await call(PORT, `/counselors/classes?counselor_id=${ME}`, 1, '4')
record('admin', 'admin bypass still alive (X-Role: 4)', isAllowed(adminOk),
  `http=${adminOk.http} code=${adminOk.code}`)

// ---------------------------------------------------------------- 无写入自证
const SENTINEL_AFTER = warningSentinel()
record('write', `academic_warning#${MY_WARNING} untouched`, SENTINEL_BEFORE === SENTINEL_AFTER,
  `before=(${SENTINEL_BEFORE}) after=(${SENTINEL_AFTER})`)
const NOTIF_TODAY_AFTER = Number(q(`SELECT COUNT(*) FROM notifications WHERE created_at >= CURDATE()`)[0] || '-1')
record('write', 'no new notification row written today', NOTIF_TODAY_BEFORE === NOTIF_TODAY_AFTER,
  `before=${NOTIF_TODAY_BEFORE} after=${NOTIF_TODAY_AFTER}`)

// ---------------------------------------------------------------- 汇总
console.log('\n===== SUMMARY =====')
console.log(`  candidate port ${PORT}: endpoints=${cand.total}  ok=${cand.ok}  LEAK=${cand.leak}  OVER-BLOCK=${cand.overBlock}`)
if (base) {
  console.log(`  baseline  port ${BASELINE_PORT}: endpoints=${base.total}  blocked=${base.total - base.leak}  LEAK=${base.leak}`)
  console.log('  ^ the baseline column is EVIDENCE (old jar leaks), not a criterion.')
  console.log(`  => natural experiment: ${base.leak} distinct cross-user reads leaked real data on the old jar,`)
  console.log(`     ${cand.leak} on the fresh build.`)
}
const failed = results.filter((r) => !r.ok).length
console.log(`  assertions failed: ${failed}`)
if (failed) {
  console.log('\n  FAILED:')
  for (const r of results.filter((x) => !x.ok)) console.log(`    - [${r.kind}] ${r.label}  ${r.detail}`)
}
console.log(failed === 0 ? '\nRESULT: PASS (0 leaks, 0 over-blocks on the fresh build)' : '\nRESULT: FAIL')
process.exit(failed === 0 ? 0 : 1)
