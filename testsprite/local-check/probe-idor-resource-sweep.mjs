// 读侧横向越权(读 IDOR) —— **资源 id 型**端点的归一化扫描 (W12, 2026-10-04)
//
// 为什么另写一个, 而不是扩 `probe-idor-read-sweep.mjs`:
//   那个探针**明确**只收路径变量属于「归属人 id」的那一族(studentId/userId/teacherId),
//   并在注释里写明资源 id(appealId/courseId/classId)不混进结论 —— 因为它的判据是
//   "token A 打 /x/{B} 与 token B 打 /x/{B} 逐字对照", 而资源 id 型的路径里**根本没有 B**:
//   `/teachers/courses/1` 里的 1 既不是 A 也不是 B, 它是**一个东西**。
//   要知道那个东西属于谁, 只能**查一次库**。
//
//   所以本探针把"谁的东西"这一半交给**活库直查**(不是猜、不是硬编码),
//   另一半(接口认不认)交给对备用端口的真请求。
//
// 判据(每条都成对, 缺一条就不算过):
//   cross: A 拿着**B 的资源 id** 去读   -> 必须被拒(403 信封 / 非 200)
//   own  : A 拿着**自己的资源 id** 去读 -> 必须放行(200)
//   只测 cross 会把"接口整个坏了"读成"修好了"; 只测 own 会把"没装闸"读成"没误伤"。
//
// 方法论(与前几个探针一致, 都是踩过的坑):
//   * 直连**刚构建的备用端口**注入 `X-User-Id`/`X-Role`, 不过网关 —— 网关的 403 会把
//     "闸装没装"和"网关拦没拦"混成一个数字。备用端口上的实例必须是用**当前源码**起的。
//   * 真值取自活库。库连不上就**报错退出**, 不许静默跳过(静默跳过 = 假绿)。
//   * 全部输出只用 ASCII。Windows 控制台是 GBK, 打印 `⇒`/`★` 这类字符会抛
//     UnicodeEncodeError(探针崩在一条 PASS 上、还跳过收尾清理 —— W10 亲历)。
//   * 有副作用的端点(POST mark-read / delete)不在这里测: 它们已经在单测里覆盖
//     (StudentControllerResourceOwnershipTest), 这里保持纯只读。
//
// 用法: node probe-idor-resource-sweep.mjs
//   需要: 8187 = teacher-service、8188 = student-service, 均以当前源码起、已连活库。
//   可选环境变量: TEACHER_PORT / STUDENT_PORT / DB / DB_USER / DB_PASS

import { execFileSync } from 'node:child_process'

const TEACHER_PORT = process.env.TEACHER_PORT || '8187'
const STUDENT_PORT = process.env.STUDENT_PORT || '8188'
const DB = process.env.DB || 'academic_warning_system'
const DB_USER = process.env.DB_USER || 'root'
const DB_PASS = process.env.DB_PASS || 'root'

// 两位被试(活库真值, 脚本会再查一遍库核对; 对不上就报错, 不静默降级):
//   教师 A = users.id 18 (teacher001 刘教授)  教师 B = users.id 29 (10001 于凤海)
//   学生 A = users.id 27 / 档案 3 (司明)      学生 B = users.id 30 / 档案 4 (邱琳紫)
const TA = 18, TB = 29
const SA = 27, SA_PROFILE = 3, SB = 30, SB_PROFILE = 4

// ---------------------------------------------------------------- 活库真值
function q(sql) {
  const args = [`-u${DB_USER}`, `-p${DB_PASS}`, DB, '-N', '-e', sql]
  let out
  try {
    out = execFileSync('mysql', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  } catch (e) {
    console.error('[FATAL] cannot query live DB (mysql CLI failed).')
    console.error('        This probe refuses to run without DB truth: guessing ownership')
    console.error('        would turn "cannot verify" into a green light.')
    console.error('        ' + String(e.message).split('\n')[0])
    process.exit(2)
  }
  return out.split('\n').map((s) => s.trim()).filter(Boolean)
}

function ids(table, col, value) {
  const rows = q(`SELECT id FROM ${table} WHERE ${col} = ${value} ORDER BY id`)
  return rows.map((r) => Number(r.split('\t')[0])).filter((n) => Number.isFinite(n))
}

// cross 腿真正需要的是"**不属于 A** 的行", 不必非得是 B 的。
// 只盯 B 的话常常**没得测**（邱琳紫名下一条申诉都没有）—— 那会把
// "对方没数据"读成"没泄漏"。放宽成"任何别人的"之后覆盖面立刻打开;
// 至于那个"别人"具体是谁, 对判据没有影响: 只要不是 A 的, A 就无权读。
function idsNotOwnedBy(table, col, value, limit = 3) {
  const rows = q(`SELECT id FROM ${table} WHERE ${col} <> ${value} ORDER BY id LIMIT ${limit}`)
  return rows.map((r) => Number(r.split('\t')[0])).filter((n) => Number.isFinite(n))
}

console.log('===== DB truth (live) =====')
const FACTS = {
  // 教师侧: courses / classes 的 teacher_id 都是 users.id
  myCourses: ids('courses', 'teacher_id', TA),
  otherCourses: ids('courses', 'teacher_id', TB),
  myClasses: ids('classes', 'teacher_id', TA),
  otherClasses: ids('classes', 'teacher_id', TB),
  // 学生侧: score_appeals / assistance_plans 的 student_id 都是 student_profile.id
  myAppeals: ids('score_appeals', 'student_id', SA_PROFILE),
  otherAppeals: ids('score_appeals', 'student_id', SB_PROFILE),
  myPlans: ids('assistance_plans', 'student_id', SA_PROFILE),
  otherPlans: ids('assistance_plans', 'student_id', SB_PROFILE),
  // 学生侧的 cross 目标: "任何不是司明的" 申诉 / 帮扶计划
  foreignAppeals: idsNotOwnedBy('score_appeals', 'student_id', SA_PROFILE),
  foreignPlans: idsNotOwnedBy('assistance_plans', 'student_id', SA_PROFILE),
}
for (const [k, v] of Object.entries(FACTS)) {
  console.log(`  ${k.padEnd(14)} = [${v.join(', ')}]`)
}
// 硬前提对不上就报错 —— 数据被改过时探针的结论会失效, 必须当场知道
const need = [
  ['courses of teacher A', FACTS.myCourses],
  ['courses of teacher B', FACTS.otherCourses],
  ['classes of teacher A', FACTS.myClasses],
  ['classes of teacher B', FACTS.otherClasses],
]
for (const [what, v] of need) {
  if (v.length === 0) {
    console.error(`\n[FATAL] live DB has no ${what}. Re-read the fixture assumptions`)
    console.error('        (this round: courses.teacher_id 18->[6], 29->[1,4,10];')
    console.error('         classes.teacher_id 18->[31,32], 29->[1,30]). Probe aborted, not passed.')
    process.exit(2)
  }
}

// ---------------------------------------------------------------- 发请求
async function call(port, path, userId, role, method = 'GET') {
  const res = await fetch(`http://localhost:${port}${path}`, {
    method,
    headers: { 'X-User-Id': String(userId), 'X-Role': role, 'X-Username': 'probe' },
  })
  const text = await res.text()
  let body = null
  try { body = JSON.parse(text) } catch { /* 非 JSON 就留 null */ }
  return { http: res.status, code: body?.code, body }
}

// 被拒的判据 —— **不能只看 HTTP 状态码**。
// 本仓归属拒绝的惯用形状是 `HTTP 200 + body{code:403,message}`(前端按 code 判),
// 所以 `http=200` 与"放行"根本不是一回事。第一版探针就栽在这里: 9 条 cross 腿
// 全被报成 LEAK, 而它们的 body 明明写着 code:403 —— **红的是探针, 不是代码**。
// (同族教训在 memory 里记着: "三种被拒形状要分开写判据, 只断言 HTTP 状态码会把第三种读成通过"。)
// 文件下载端点(export)是唯一例外: 那里必须回真 4xx, 否则前端会把它存成文件。
// 本探针不含下载端点, 所以统一按下面的判据。
function isDenied(r) {
  if (r.http >= 400) return true          // 网关/Spring Security/守卫写响应流的真 4xx
  return r.code === 403 || r.code === 404 // 本仓惯例的 200 + code
}
function isAllowed(r) {
  return r.http === 200 && (r.code === 200 || r.code === undefined || r.code === null)
}

const results = []
function record(kind, label, leg, ok, detail) {
  results.push({ kind, label, leg, ok, detail })
  const tag = ok ? '[OK]  ' : '[FAIL]'
  const pad = leg.padEnd(5)
  console.log(`  ${tag} ${pad} ${label}  ${detail}`)
}

// 成对判据: 读自己的资源必须 200; 读别人的必须不是 200。
// 一侧缺资源(A 自己没有 appeal)如实标 UNTESTABLE, 不当成通过。
async function pairResource({ port, role, label, makePath, myIds, otherIds, userA, userB }) {
  // own 腿: A 读 A 自己的
  if (myIds.length) {
    const r = await call(port, makePath(myIds[0]), userA, role)
    const ok = isAllowed(r)
    record('own', label, 'own', ok,
      `A -> own#${myIds[0]}  http=${r.http} code=${r.code}` + (ok ? '' : '   <-- OVER-BLOCK: owner denied'))
  } else {
    record('own', label, 'own', false, 'UNTESTABLE: A has no row in that table (not a pass)')
  }
  // cross 腿: A 读 B 的
  if (otherIds.length) {
    const r = await call(port, makePath(otherIds[0]), userA, role)
    const ok = isDenied(r)
    record('cross', label, 'cross', ok,
      `A -> B#${otherIds[0]}    http=${r.http} code=${r.code}` + (ok ? '' : '   <-- LEAK: other\'s resource returned'))
  } else {
    record('cross', label, 'cross', false, 'UNTESTABLE: B has no row in that table (not a pass)')
  }
}

// ---------------------------------------------------------------- 教师侧
console.log('\n===== A) teacher-service (resource: courseId) =====')
const teacherCourseEps = [
  (id) => `/teachers/courses/${id}`,
  (id) => `/teachers/courses/${id}/distribution`,
  (id) => `/teachers/courses/${id}/anomaly`,
  (id) => `/teachers/courses/${id}/students`,
  (id) => `/teachers/courses/${id}/recommendations`,
]
for (const mk of teacherCourseEps) {
  await pairResource({
    port: TEACHER_PORT, role: 'teacher', label: mk('{id}'),
    makePath: mk, myIds: FACTS.myCourses, otherIds: FACTS.otherCourses, userA: TA, userB: TB,
  })
}
// 这两条把课程 id 放在**查询参数**里 —— 路径变量抽取器永远看不见它们, 手写进来。
for (const [label, mk] of [
  ['/teachers/analysis?course_id={id}', (id) => `/teachers/analysis?course_id=${id}`],
  ['/teachers/scores?course_id={id}', (id) => `/teachers/scores?course_id=${id}`],
]) {
  await pairResource({
    port: TEACHER_PORT, role: 'teacher', label,
    makePath: mk, myIds: FACTS.myCourses, otherIds: FACTS.otherCourses, userA: TA, userB: TB,
  })
}

console.log('\n===== B) teacher-service (resource: classId) =====')
for (const mk of [
  (id) => `/teachers/classes/${id}/analysis`,
  (id) => `/teachers/classes/${id}/students`,
]) {
  await pairResource({
    port: TEACHER_PORT, role: 'teacher', label: mk('{id}'),
    makePath: mk, myIds: FACTS.myClasses, otherIds: FACTS.otherClasses, userA: TA, userB: TB,
  })
}

// ---------------------------------------------------------------- 学生侧
console.log('\n===== C) student-service (resource: appealId / planId) =====')
await pairResource({
  port: STUDENT_PORT, role: 'student', label: '/students/appeals/{id}/detail',
  makePath: (id) => `/students/appeals/${id}/detail`,
  // 这里 A=司明(27/档案3), B=邱琳紫(30/档案4)。邱琳紫名下**没有**申诉 ——
  // 所以 own 腿测不了(她自己没东西可读), cross 腿测得了(司明的申诉 6)。
  myIds: FACTS.myAppeals, otherIds: FACTS.foreignAppeals, userA: SA, userB: SB,
})
await pairResource({
  port: STUDENT_PORT, role: 'student', label: '/students/evaluations/{id}/detail',
  makePath: (id) => `/students/evaluations/${id}/detail`,
  myIds: FACTS.myPlans, otherIds: FACTS.foreignPlans, userA: SA, userB: SB,
})

// cross 腿需要"B 有、A 没有"那种资源。申诉那张表里 B(邱琳紫)没有, 反过来
// 让**邱琳紫去读司明的**、以及让**司明去读司明的** —— 两条正好凑齐一对。
console.log('\n===== C2) student-service: appeal detail from the other side =====')
if (FACTS.myAppeals.length) {
  const own = await call(STUDENT_PORT, `/students/appeals/${FACTS.myAppeals[0]}/detail`, SA, 'student')
  record('own', '/students/appeals/{id}/detail', 'own', isAllowed(own),
    `siming(27) -> own#${FACTS.myAppeals[0]}    http=${own.http} code=${own.code}` +
    (isAllowed(own) ? '' : '   <-- OVER-BLOCK'))
  const cross = await call(STUDENT_PORT, `/students/appeals/${FACTS.myAppeals[0]}/detail`, SB, 'student')
  const ok = isDenied(cross)
  record('cross', '/students/appeals/{id}/detail', 'cross', ok,
    `qiulinzi(30) -> siming#${FACTS.myAppeals[0]}  http=${cross.http} code=${cross.code}` +
    (ok ? '' : '   <-- LEAK'))
}

// ---------------------------------------------------------------- 未覆盖的写侧
console.log('\n===== D) not covered here on purpose =====')
console.log('  POST /students/notification-center/{id}/mark-read')
console.log('  POST /students/notification-center/{id}/delete')
console.log('  -> these WRITE. Covered by unit tests')
console.log('     (StudentControllerResourceOwnershipTest), not by a read-only sweep.')

// ---------------------------------------------------------------- 结论
const leaks = results.filter((r) => r.leg === 'cross' && !r.ok && !/UNTESTABLE/.test(r.detail))
const over = results.filter((r) => r.leg === 'own' && !r.ok && !/UNTESTABLE/.test(r.detail))
const untest = results.filter((r) => /UNTESTABLE/.test(r.detail))
console.log('\n===== summary =====')
console.log(`  cases ${results.length} / leaks ${leaks.length} / over-blocked ${over.length} / untestable ${untest.length}`)
if (leaks.length) {
  console.log('\n  LEAKS (a caller read a resource it does not own):')
  leaks.forEach((r) => console.log('    ' + r.label + '  ' + r.detail))
}
if (over.length) {
  console.log('\n  OVER-BLOCKED (an owner was denied -> the guard is in the wrong place):')
  over.forEach((r) => console.log('    ' + r.label + '  ' + r.detail))
}
if (untest.length) {
  console.log('\n  UNTESTABLE (the peer has no row in that table -> NOT "safe"):')
  untest.forEach((r) => console.log('    ' + r.label + '  ' + r.detail))
}
process.exit(0)
