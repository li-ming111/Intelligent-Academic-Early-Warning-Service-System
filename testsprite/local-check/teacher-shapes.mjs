// 把教师端每个接口的**完整响应形状**导出成一个 JSON 文件，供写小程序页面时对照字段名。
// 只读，不改库。用法: node teacher-shapes.mjs [用户名] [密码]
//
// 输出: teacher-shapes.json —— { url: { code, message, shape } }
//   shape 里数组只留前 2 个元素（看清列名就够了），对象全字段保留。
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || '10001'
const PASS = process.argv[3] || '123456'
const TID = '29'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9800 + Math.floor(Math.random() * 100)

const URLS = [
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
  `/api/teachers/1`,
]

const profile = mkdtempSync(join(tmpdir(), 'cdpts-'))
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
  if (!target) { console.log('FAIL 调试端口未就绪'); cleanup(); process.exit(1) }

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
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 45000) => {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      try { if (await evaluate(expr)) return true } catch {}
      await sleep(350)
    }
    return false
  }

  await send('Page.enable'); await send('Runtime.enable')
  await send('Page.navigate', { url: `${APP}/login` })
  if (!await waitFor(`!!document.querySelector('.lr-submit')`)) { console.log('FAIL 登录页未渲染'); cleanup(); process.exit(1) }
  await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    setVal('input[placeholder="验证码"]', (await (async () => {
      // W1.12：验证码改由服务端签发，按钮会先显示「加载中…」——必须等它变成一个
      // 4 位码再读，否则填进去的是「加载中…」，症状是"登录失败"，看着像账号坏了。
      for (let i = 0; i < 60; i++) {
        const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
        if (/^[A-Z0-9]{4}$/.test(t)) return t
        await new Promise((r) => setTimeout(r, 200))
      }
      return (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
    })()))
    document.querySelector('.lr-submit')?.click()
  })()`)
  let path = ''
  for (let i = 0; i < 60; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && !path.includes('/login')) break
  }
  if (!path || path.includes('/login')) { console.log(`FAIL 登录未成功（${path}）`); cleanup(); process.exit(1) }
  console.log(`已登录为 ${await evaluate(`localStorage.getItem('name')`)}`)

  const out = await evaluate(`(async () => {
    const token = localStorage.getItem('token')
    const H = { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }
    const shrink = (v, depth) => {
      if (Array.isArray(v)) return v.slice(0, 2).map((x) => shrink(x, depth + 1))
      if (v && typeof v === 'object') {
        const o = {}
        for (const k of Object.keys(v)) o[k] = shrink(v[k], depth + 1)
        return o
      }
      return v
    }
    const res = {}
    for (const u of ${JSON.stringify(URLS)}) {
      try {
        const r = await fetch(u, { headers: H })
        const txt = await r.text()
        let j = null
        try { j = JSON.parse(txt) } catch {}
        res[u] = j
          ? { http: r.status, code: j.code, message: String(j.message).slice(0, 120), shape: shrink(j.data, 0) }
          : { http: r.status, raw: txt.slice(0, 200) }
      } catch (e) {
        res[u] = { error: String(e) }
      }
    }
    return res
  })()`)

  writeFileSync('teacher-shapes.json', JSON.stringify(out, null, 2), 'utf8')
  const bad = Object.entries(out).filter(([, v]) => v.code !== 200).map(([k]) => k)
  console.log(`写出 teacher-shapes.json，共 ${Object.keys(out).length} 个接口`)
  console.log(bad.length ? `非 200: ${bad.join('\n  ')}` : '全部 code=200')
  cleanup()
  process.exit(0)
}

main().catch((e) => { console.log(`FAIL ${e.message}`); cleanup(); process.exit(1) })
