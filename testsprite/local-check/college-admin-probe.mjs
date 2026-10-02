// 学院管理员端（P8）开工前的**只读**探测。
//
// 为什么先探测：P6/P7 都吃过"页面照接口文档写完，接口其实 500"的亏
// （teacher-service 有 8 个读接口 Feign 403、admin-service 有 CCE）。
// CollegeAdminController 全程只用**本地 mapper**，没有 Feign，所以理论上
// 不会重现 403 那一类；但有两处必须实测才知道：
//   1. `getCollegeId()` 里有个 `(Long) p.get("college_id")` 的**硬转型** ——
//      如果 MyBatis 对那一列返回 Integer，每一个接口都会抛 ClassCastException。
//   2. 这一批 mapper 方法（selectClassStats / selectStudentScoreStats /
//      selectCollegeScoreOverview / selectCourseStats / selectTeacherStats …）
//      是本轮未提交改动里新加的聚合 SQL，之前从未在活库上跑过。
//
// 全程只发 GET（不写库），唯一例外是 `/process-warning` 与 `/delete-warning` ——
// 这两个**故意不调**，写接口另开带快照的探针。
//
// 用法: node college-admin-probe.mjs
// 输出: college-admin-probe.txt（UTF-8）
import { writeFileSync } from 'node:fs'
import { captchaFields } from './_captcha.mjs'

const BASE = process.env.API_BASE || 'http://localhost:8076'

const lines = []
const say = s => { lines.push(s); console.log(s) }
let failed = 0
const info = s => say('       ' + s)
const ok = s => say('  PASS ' + s)
const bad = s => { failed++; say('  FAIL ' + s) }

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

function brief(v, depth = 0) {
  if (v === null) return 'null'
  if (v === undefined) return 'undefined'
  if (Array.isArray(v)) {
    if (!v.length) return '[]'
    return `Array(${v.length}) of ${brief(v[0], depth + 1)}`
  }
  if (typeof v === 'object') {
    if (depth > 1) return 'Object'
    return '{' + Object.keys(v).map(k => `${k}: ${brief(v[k], depth + 1)}`).join(', ') + '}'
  }
  if (typeof v === 'string') return `"${v.length > 24 ? v.slice(0, 24) + '…' : v}"`
  return String(v)
}

/** 一个端点的探针：打印 code / message / 形状，并可选地对形状做断言。 */
async function probe(label, path, opts = {}) {
  const r = await get(path)
  const head = `${label.padEnd(22)} ${path}`
  if (r.status !== 200) { bad(`${head}  → HTTP ${r.status}`); info(brief(r.body)) ; return r }
  if (r.code !== 200) { bad(`${head}  → code=${r.code} message=${r.message}`); return r }
  ok(`${head}  → ${brief(r.data)}`)
  if (opts.assert) {
    const msg = opts.assert(r.data)
    if (msg) bad(`  └─ 断言不成立：${msg}`)
  }
  return r
}

const tryLogin = async (username, password) => {
  // W1.12：登录要带服务端签发的验证码，缺了会被回 code:500「验证码错误或已过期」
  const r = await post('/api/auth/login', { username, password, ...(await captchaFields(BASE)) })
  const d = r.data || {}
  return { ok: !!d.token, status: d.status, message: d.message, token: d.token, uid: d.userId ?? d.id, role: d.role, name: d.name }
}

const run = async () => {
  say('==== 学院管理员端接口探测（只读） ====')
  say(`BASE = ${BASE}`)

  const accounts = [
    { username: 'collegeadmin1', expect: '软件学院（collegeId=1）' },
    { username: 'collegeadmin2', expect: '电子工程学院（collegeId=2）' }
  ]

  for (const acc of accounts) {
    say('')
    say(`#### 账号 ${acc.username} —— ${acc.expect}`)
    const login = await tryLogin(acc.username, '123456')
    if (!login.ok) { bad(`登录失败：status=${login.status} message=${login.message}`); continue }
    ok(`登录成功 userId=${login.uid} role=${login.role} name=${login.name}`)
    const H = { Authorization: 'Bearer ' + login.token }
    const uid = login.uid

    const g = async (label, path, opts) => {
      const r = await get(path, undefined, H)
      const head = `${label.padEnd(20)}`
      if (r.status !== 200) { bad(`${head} ${path}  → HTTP ${r.status} ${brief(r.body)}`); return r }
      if (r.code !== 200) { bad(`${head} ${path}  → code=${r.code} message=${r.message}`); return r }
      ok(`${head} ${path}\n         → ${brief(r.data)}`)
      if (opts && opts.assert) {
        const msg = opts.assert(r.data)
        if (msg) bad(`         └─ 断言不成立：${msg}`)
      }
      return r
    }

    // 1. 档案 —— 注意 getCollegeId 的硬转型：这个接口 500 就意味着**所有**接口都 500
    await g('档案', `/api/college-admin/profile/${uid}`)
    await g('概览', `/api/college-admin/dashboard/${uid}`)
    await g('班级', `/api/college-admin/classes/${uid}`)
    await g('学生', `/api/college-admin/students/${uid}`)
    await g('学生(关键词)', `/api/college-admin/students/${uid}?keyword=张`)
    await g('教师', `/api/college-admin/teachers/${uid}`)
    await g('辅导员', `/api/college-admin/counselors/${uid}`)
    await g('预警', `/api/college-admin/warnings/${uid}`)
    await g('帮扶情况', `/api/college-admin/assistance/${uid}`)
    await g('成绩统计', `/api/college-admin/scores-stats/${uid}`)
    await g('成绩分析', `/api/college-admin/score-analysis/${uid}`)
    await g('班级对比', `/api/college-admin/class-comparison/${uid}`)
    await g('成绩查询', `/api/college-admin/score-query/${uid}`)
    await g('成绩查询(关键词)', `/api/college-admin/score-query/${uid}?keyword=张`)
    await g('导出(scores)', `/api/college-admin/export/${uid}?type=scores`)
    await g('导出(warnings)', `/api/college-admin/export/${uid}?type=warnings`)

    // 2. 带第二参数的详情：第二参是 **profile id**，不是 users.id
    const classes = await get(`/api/college-admin/classes/${uid}`, undefined, H)
    const firstClass = Array.isArray(classes.data) && classes.data.length ? classes.data[0] : null
    if (firstClass) {
      const cid = firstClass.id
      info(`首个班级：id=${cid} name=${firstClass.name} 学生数=${firstClass.studentCount}`)
      await g('班级学生', `/api/college-admin/class-students/${uid}/${cid}`)
      await g('班级学生(成绩查询)', `/api/college-admin/score-query/${uid}?classId=${cid}`)
    } else {
      info('该学院没有班级，跳过班级学生/按班成绩查询')
    }

    const students = await get(`/api/college-admin/students/${uid}`, undefined, H)
    if (Array.isArray(students.data) && students.data.length) {
      const s = students.data[0]
      info(`首个学生：profileId=${s.id} 学号=${s.studentId} 姓名=${s.name}`)
      await g('学生详情', `/api/college-admin/student-detail/${uid}/${s.id}`)
    }

    const teachers = await get(`/api/college-admin/teachers/${uid}`, undefined, H)
    if (Array.isArray(teachers.data) && teachers.data.length) {
      const t = teachers.data[0]
      info(`首位教师：profileId=${t.id} 姓名=${t.name}`)
      await g('教师详情', `/api/college-admin/teacher-detail/${uid}/${t.id}`)
    }

    // 3. 越权探测：拿 A 学院管理员的 uid 去查 B 学院的班级花名册（只读，不改任何东西）。
    //    2026-10-02（W1.13）之前这里回 code=200 + 24 行（软件学院计科2306班全班的姓名学号），
    //    所以这一条从"记一笔"升格成断言 —— 回 200 就是回归。
    const otherClassId = acc.username === 'collegeadmin1' ? 10 : 1
    const idor = await get(`/api/college-admin/class-students/${uid}/${otherClassId}`, undefined, H)
    if (idor.code === 403) {
      ok(`越权探测 class-students/${uid}/${otherClassId} → 403（跨院花名册被拒）`)
    } else {
      bad(`越权探测 class-students/${uid}/${otherClassId} → code=${idor.code} ${brief(idor.data)}  期望 403`)
    }
    // 4. 身份绑定：拿 A 的 token 去读 B 的概览（uid 换成对方的）
    const otherUid = acc.username === 'collegeadmin1' ? 56 : 57
    const swapped = await get(`/api/college-admin/dashboard/${otherUid}`, undefined, H)
    if (swapped.code === 403) {
      ok(`换 uid 探测 dashboard/${otherUid} → 403（路径 uid 与登录身份不符被拒）`)
    } else {
      bad(`换 uid 探测 dashboard/${otherUid} → code=${swapped.code}  期望 403`)
    }
  }

  say('')
  say(failed === 0 ? '==== PASS：所有只读接口都返回 code=200 ====' : `==== FAIL：${failed} 处异常 ====`)
  writeFileSync(new URL('./college-admin-probe.txt', import.meta.url), lines.join('\n') + '\n', 'utf8')
}

run().catch(e => {
  console.error(e)
  lines.push('CRASH: ' + (e && e.stack ? e.stack : String(e)))
  writeFileSync(new URL('./college-admin-probe.txt', import.meta.url), lines.join('\n') + '\n', 'utf8')
  process.exit(1)
})
