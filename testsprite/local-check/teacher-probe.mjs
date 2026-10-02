// 教师端接口实测探针（PC 端 5173，浏览器内 fetch 复用登录态）。
//
// 为什么要在浏览器里打而不是 curl：登录接口要填验证码，脚本化只能走页面。
// 拿到页面里的 token 后，用页面上下文发 fetch，等价于前端自己的调用。
//
// 目的：把「源码读出来这几个接口会 500」变成「实测确认」。
// 只打印 HTTP 状态码、信封里的 code/message、以及 data 的前若干字符 ——
// **不打印 token、不打印任何凭据**。
//
// 用法: node teacher-probe.mjs [用户名] [密码] [users.id]
// 于凤海 = users.id 29（有 24 人的班 + 2 条反馈），是最有代表性的教师账号。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'
const TID = process.argv[4] || '29'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9600 + Math.floor(Math.random() * 100)

// 只关心 GET 读路径（写路径会真改库，不在探针里跑）
const ENDPOINTS = [
  `/api/teachers/dashboard/${TID}`,
  `/api/teachers/todos/${TID}`,
  `/api/teachers/class-management/my-classes?teacherId=${TID}`,
  `/api/teachers/courses?teacher_id=${TID}`,
  `/api/teachers/courses`,
  `/api/teachers/scores?course_id=1`,
  `/api/teachers/scores/by-class?class_name=%E8%AE%A1%E7%A7%912306%E7%8F%AD&teacher_id=${TID}`,
  `/api/teachers/students/${TID}`,
  `/api/teachers/classes/1/students`,
  `/api/teachers/students/3/credit-prediction`,
  `/api/teachers/warnings/${TID}`,
  `/api/teachers/warnings/${TID}?status=0`,
  `/api/teachers/feedbacks?teacherId=${TID}`,
  `/api/teachers/feedbacks/${TID}/list`,
  `/api/teachers/scores/audit/${TID}`,
  `/api/teachers/messages/${TID}`,
  `/api/teachers/messages/${TID}/unread-count`,
  `/api/teachers/enrollments?teacherId=${TID}`,
  `/api/teachers/enrollments?courseId=1`,
  `/api/teachers/analysis?course_id=1`,
  `/api/teachers/classes/1/analysis`,
  `/api/teachers/courses/1/distribution`,
  `/api/teachers/courses/1/anomaly`,
  `/api/teachers/scores/analyze?course_id=1`,
  `/api/teachers/scores/anomalies?course_id=1`,
  `/api/teachers/scores/student/analyze?student_id=3&course_id=1`,
  `/api/teachers/counselor/by-class?class_name=%E8%AE%A1%E7%A7%912306%E7%8F%AD`,
  `/api/teachers/list`,
  `/api/teachers/class-management/search?keyword=`,
  `/api/teachers/class-management/requests?teacherId=${TID}`,
]

const profile = mkdtempSync(join(tmpdir(), 'cdptp-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const cleanup = () => {
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
}

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) { console.log('FAIL  Chrome 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.text)
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 40000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(350)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')

  await send('Page.navigate', { url: `${APP}/login` })
  if (!await waitFor(`!!document.querySelector('.lr-submit')`, 45000)) {
    console.log('FAIL  登录页未渲染'); cleanup(); process.exit(1)
  }

  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', (await (async () => {
      // W1.12：验证码改由服务端签发，按钮会先显示「加载中…」——必须等它变成一个
      // 4 位码再读，否则填进去的是「加载中…」，症状是"登录失败"，看着像账号坏了。
      for (let i = 0; i < 60; i++) {
        const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
        if (/^[A-Z0-9]{4}$/.test(t)) return t
        await new Promise((r) => setTimeout(r, 200))
      }
      return (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    })()))
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.clicked) { console.log(`FAIL  登录表单没填上 ${JSON.stringify(filled)}`); cleanup(); process.exit(1) }

  let path = ''
  for (let i = 0; i < 60; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && !path.includes('/login')) break
  }
  if (!path || path.includes('/login')) {
    const alert = await evaluate(`(document.querySelector('.el-alert__title')||{}).textContent || ''`)
    console.log(`FAIL  登录未成功（当前 ${path}，提示 "${alert}"）—— 探针无法继续`)
    cleanup(); process.exit(1)
  }
  const role = await evaluate(`localStorage.getItem('role')`)
  const uname = await evaluate(`localStorage.getItem('name')`)
  console.log(`PASS  已登录为 ${uname}（role=${role}），落地 ${path}\n`)

  // 在页面上下文里串行打接口。间隔 150ms，避免打爆网关。
  const probe = await evaluate(`(async () => {
    const token = localStorage.getItem('token')
    const urls = ${JSON.stringify(ENDPOINTS)}
    const out = []
    for (const u of urls) {
      const rec = { url: u }
      try {
        const r = await fetch(u, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
        rec.status = r.status
        const txt = await r.text()
        try {
          const j = JSON.parse(txt)
          rec.code = j.code
          rec.message = String(j.message || '').slice(0, 90)
          const d = j.data
          if (d === null || d === undefined) rec.dataKind = 'null'
          else if (Array.isArray(d)) rec.dataKind = 'array[' + d.length + ']'
          else if (typeof d === 'object') rec.dataKind = 'object{' + Object.keys(d).slice(0, 12).join(',') + '}'
          else rec.dataKind = typeof d + '(' + String(d).slice(0, 60) + ')'
          if (Array.isArray(d) && d.length) rec.sample = JSON.stringify(d[0]).slice(0, 180)
          else if (d && typeof d === 'object' && !Array.isArray(d)) rec.sample = JSON.stringify(d).slice(0, 180)
        } catch (e) {
          rec.code = 'NOT_JSON'
          rec.message = txt.slice(0, 120)
        }
      } catch (e) { rec.status = 'ERR'; rec.message = String(e.message).slice(0, 80) }
      out.push(rec)
      await new Promise(r => setTimeout(r, 150))
    }
    return out
  })()`)

  const bad = []
  for (const r of probe || []) {
    const isOk = r.status === 200 && (r.code === 200 || r.code === 0)
    const tag = isOk ? '✅' : '❌'
    if (!isOk) bad.push(r.url)
    console.log(`${tag} ${String(r.status).padEnd(4)} code=${String(r.code).padEnd(6)} ${r.url}`)
    console.log(`        data=${r.dataKind}  msg=${r.message}`)
    if (r.sample) console.log(`        sample=${r.sample}`)
  }

  console.log(`\n共 ${(probe || []).length} 个接口，失败 ${bad.length} 个`)
  if (bad.length) bad.forEach(u => console.log(`  ❌ ${u}`))
  cleanup()
  process.exit(0)
}

main().catch((e) => { console.log(`FAIL  脚本异常: ${e.message}`); cleanup(); process.exit(1) })
