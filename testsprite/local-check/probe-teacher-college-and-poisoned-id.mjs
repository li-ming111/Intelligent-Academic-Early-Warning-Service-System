// 第四十二轮 W8.2 的两件事，用**真浏览器 + 真后端**验：
//
//   A. 教师「个人设置 → 工作设置 → 学院」原来是个 disabled 的**恒空**框（后端压根没有
//      任何接口返回学院）。现在 dashboard 带出 `college`。断言不能停在"接口回了字段"
//      —— 必须读 **DOM 里那格 input 的 value**，因为本仓有过"数据对、渲染错"的先例
//      （el-checkbox/el-switch 判 === trueValue，接口/库/控制台全正常，只有 DOM 抓得到）。
//
//   B. 教师端曾有一句兜底 `localStorage.setItem('teacherId','9')`，一旦写入就把这台浏览器
//      **永久钉死**成 9 号用户（8 个教师页都优先读它）。现在读点全删。断言方式：
//      **先把毒键种进去**（'9'），再打开页面，看它发出的请求用的是谁 ——
//      应该是**登录教师自己的 users.id(18)**，而不是 9。
//      ⚠ 只断言"键被清掉了"是不够的：那可能只是被谁删了；要断言**请求携带的 id**。
//
//   C. 毒**写入方**确实没了：在键不存在时访问那个原本会写它的页面（成绩页），
//      访问完键必须**仍然不存在**。这一条直接打原来那句 `setItem('teacherId','9')`。
//
// 用法: node probe-teacher-college-and-poisoned-id.mjs
// 前置: 5173(vite) + 8076(网关) + user-service + teacher-service 都在跑
// 退出码 0=全过 / 1=有失败
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const USER = process.argv[2] || 'teacher001'
const PASS = process.argv[3] || '123456'
// teacher001 在活库是 users.id=18（name=刘教授）。断言里写死它，是为了让"用错了人"
// 这件事**必然**表现为失败 —— 若哪天换了账号，下面这条会先红，提示改这里。
const EXPECT_UID = 18
const POISON = '9'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const PORT = 9700 + Math.floor(Math.random() * 100)
const profile = mkdtempSync(join(tmpdir(), 'cdpp-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const done = (code, msg) => {
  console.log(`\n${code === 0 ? 'PASS' : 'FAIL'}  ${msg}`)
  try { chrome.kill() } catch {}
  try { rmSync(profile, { recursive: true, force: true }) } catch {}
  process.exit(code)
}

const results = []
const check = (name, ok, detail) => {
  results.push({ name, ok })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail ? `\n      ${detail}` : ''}`)
}

const main = async () => {
  let target = null
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
      if (target) break
    } catch {}
    await sleep(250)
  }
  if (!target) return done(1, 'Chrome 调试端口未就绪')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

  let id = 0
  const pending = new Map()
  let reqs = []
  let resps = []
  let jsErrors = []
  let apiBody = []      // 捕获 /api/teachers/dashboard/ 的响应体，用来和 DOM 对照
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Network.requestWillBeSent') {
      const r = m.params.request
      if (r.url.includes('/api/')) reqs.push({ method: r.method, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
    } else if (m.method === 'Network.responseReceived') {
      const r = m.params.response
      if (r.url.includes('/api/')) resps.push({ status: r.status, url: r.url.replace(/^https?:\/\/[^/]+/, '').split('?')[0] })
    } else if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      jsErrors.push((d?.exception?.description || d?.text || '').split('\n')[0].slice(0, 160))
    } else if (m.method === 'Network.loadingFinished') {
      // 交给下面主动 fetch 取响应体，这里不用
    }
  }
  const send = (method, params = {}) =>
    new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { __err: r.result.exceptionDetails.exception?.description }
    return r.result?.result?.value
  }
  const waitIdle = async (quietMs = 700, maxMs = 9000) => {
    let last = reqs.length + resps.length, quiet = 0
    for (let t = 0; t < maxMs; t += 200) {
      await sleep(200)
      const now = reqs.length + resps.length
      if (now === last) { quiet += 200; if (quiet >= quietMs) return } else { quiet = 0; last = now }
    }
  }
  const goto = async (path) => {
    reqs = []; resps = []; jsErrors = []
    await send('Page.navigate', { url: APP + path })
    await sleep(1500)
    await waitIdle()
    return { reqs: [...reqs], resps: [...resps], jsErrors: [...jsErrors] }
  }

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')

  // ---------------- 登录 ----------------
  await send('Page.navigate', { url: `${APP}/login` })
  await sleep(3000)
  const filled = await evaluate(`(async () => {
    const setVal = (sel, val) => {
      const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    let shown = ''
    for (let i = 0; i < 60; i++) {
      const t = (document.querySelector('.lr-captcha-btn')?.textContent || '').trim()
      if (/^[A-Z0-9]{4}$/.test(t)) { shown = t; break }
      await new Promise((r) => setTimeout(r, 200))
    }
    const a = setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    const b = setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    const c = setVal('input[placeholder="验证码"]', shown)
    const btn = document.querySelector('.lr-submit'); if (btn) btn.click()
    return { a, b, c, shown, clicked: !!btn }
  })()`)
  if (!filled?.a || !filled?.b || !filled?.c || !filled?.clicked) {
    return done(1, `登录表单元素没找全: ${JSON.stringify(filled)}`)
  }
  let path = ''
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    path = await evaluate('location.pathname')
    if (path && path !== '/login') break
  }
  if (!path || path === '/login') return done(1, `登录失败（账号 ${USER}）`)
  const uidInStorage = await evaluate(`localStorage.getItem('userId')`)
  console.log(`已登录 ${USER} → ${path}   (localStorage.userId=${uidInStorage})`)
  if (String(uidInStorage) !== String(EXPECT_UID)) {
    console.log(`\n!! 这个账号的 users.id 不是 ${EXPECT_UID}，而是 ${uidInStorage}。`)
    console.log(`   B 步的判据写死了 ${EXPECT_UID}，请先改脚本里的 EXPECT_UID。`)
    return done(1, '账号与判据不符，未开始断言')
  }

  // ---------------- A. 学院渲染 ----------------
  console.log('\n【A】教师设置页「学院」是否真的渲染出来了')
  const a = await goto('/teacher/settings')
  await evaluate(`(() => {
    const t = [...document.querySelectorAll('.el-tabs__item')].find((x) => x.textContent.trim() === '工作设置')
    if (t) t.click()
    return !!t
  })()`)
  await sleep(600)
  const collegeDom = await evaluate(`(() => {
    const el = document.querySelector('input[placeholder="所属学院"]')
    if (!el) return { err: '找不到「所属学院」输入框' }
    return { value: el.value, disabled: el.disabled, visible: el.offsetParent !== null }
  })()`)
  const dashReq = a.reqs.find((r) => r.url.includes('/teachers/dashboard/'))
  const dashResp = a.resps.find((r) => r.url.includes('/teachers/dashboard/'))
  console.log(`    请求 ${dashReq ? dashReq.method + ' ' + dashReq.url : '(没发 dashboard 请求)'} → ${dashResp ? dashResp.status : '(无响应)'}`)
  console.log(`    DOM  input[placeholder="所属学院"].value = ${JSON.stringify(collegeDom?.value)}  disabled=${collegeDom?.disabled} visible=${collegeDom?.visible}`)

  check('A1 dashboard 请求发出且 2xx', !!dashReq && dashResp && dashResp.status >= 200 && dashResp.status < 300,
    dashReq ? `${dashReq.url} → ${dashResp?.status}` : '没有发出 dashboard 请求')
  check('A2 学院框在 DOM 里可见（不是被藏起来的空壳）', collegeDom?.visible === true, `visible=${collegeDom?.visible}`)
  const badVals = [undefined, null, '', 'null', 'undefined', '--', '—']
  check('A3 学院框有真实内容（非空、非 null/undefined/-- 字面量）', !badVals.includes(collegeDom?.value),
    `实际 ${JSON.stringify(collegeDom?.value)}`)
  // 关键：DOM 里的值必须**等于**后端真回的那个学院名 —— 只断言"非空"会放过
  // "渲染成了别的字面量/兜底值"。这条把两侧钉在一起。
  let apiCollege = null
  try {
    apiCollege = await evaluate(`(async () => {
      const tok = localStorage.getItem('token')
      const r = await fetch('/api/teachers/dashboard/${EXPECT_UID}', { headers: { Authorization: 'Bearer ' + tok } })
      const j = await r.json()
      return j?.data?.college ?? null
    })()`)
  } catch (e) { apiCollege = null }
  console.log(`    接口 /api/teachers/dashboard/${EXPECT_UID} 的 college = ${JSON.stringify(apiCollege)}`)
  check('A4 DOM 里的学院 === 接口回的 college（两侧钉死）',
    !!apiCollege && collegeDom?.value === apiCollege,
    `DOM=${JSON.stringify(collegeDom?.value)}  接口=${JSON.stringify(apiCollege)}`)

  // ---------------- B. 毒键不再被读（负向对照） ----------------
  console.log(`\n【B】种下 localStorage.teacherId='${POISON}' 后，页面还听不听它`)
  await evaluate(`localStorage.setItem('teacherId', ${JSON.stringify(POISON)}); 'seeded'`)
  const seeded = await evaluate(`localStorage.getItem('teacherId')`)
  console.log(`    已种毒: localStorage.teacherId = ${JSON.stringify(seeded)}`)
  const b = await goto('/teacher/warnings')
  const warnReqs = b.reqs.filter((r) => /^\/api\/teachers\/warnings\/\d+$/.test(r.url))
  const warnIds = warnReqs.map((r) => r.url.split('/').pop())
  const stillPoisoned = await evaluate(`localStorage.getItem('teacherId')`)
  console.log(`    /teacher/warnings 发出的教师预警请求: ${warnReqs.map((r) => r.method + ' ' + r.url).join(', ') || '(无)'}`)
  console.log(`    访问后 localStorage.teacherId = ${JSON.stringify(stillPoisoned)}`)

  check('B1 预警页确实发了 /teachers/warnings/{id} 请求', warnReqs.length > 0,
    b.reqs.map((r) => r.url).join(', ') || '(一个 /api/ 请求都没发)')
  check(`B2 请求里的 id 是登录教师自己的 ${EXPECT_UID}，不是毒值 ${POISON}`,
    warnIds.length > 0 && warnIds.every((x) => Number(x) === EXPECT_UID),
    `实际 id = ${JSON.stringify(warnIds)}`)
  check(`B3 毒键仍在 localStorage 里（证明页面是**无视**它，而不是"它恰好被清了"）`,
    String(stillPoisoned) === POISON,
    `实际 ${JSON.stringify(stillPoisoned)}`)

  // ---------------- C. 毒写入方已消失 ----------------
  console.log('\n【C】原本会写毒键的成绩页，还会不会把它写回来')
  await evaluate(`localStorage.removeItem('teacherId'); 'cleared'`)
  const before = await evaluate(`localStorage.getItem('teacherId')`)
  const c = await goto('/teacher/scores')
  await sleep(1500)
  const after = await evaluate(`localStorage.getItem('teacherId')`)
  console.log(`    访问 /teacher/scores 前 = ${JSON.stringify(before)}，后 = ${JSON.stringify(after)}`)
  console.log(`    该页发出的 /api/ 请求: ${c.reqs.map((r) => r.method + ' ' + r.url).join(', ') || '(无)'}`)
  check('C1 访问成绩页后 teacherId 仍不存在（那句 setItem(...,\'9\') 确实没了）',
    after === null, `实际 ${JSON.stringify(after)}`)
  const scoreIds = c.reqs.filter((r) => /^\/api\/teachers\/(scores|students|courses)/.test(r.url)).map((r) => r.url)
  console.log(`    成绩相关请求: ${scoreIds.join(', ') || '(无)'}`)

  // ---------------- 收尾 ----------------
  const anyJsErr = [...a.jsErrors, ...b.jsErrors, ...c.jsErrors]
  check('D1 三个页面均无未捕获 JS 异常', anyJsErr.length === 0, anyJsErr.join(' | ') || '(无)')

  const bad = results.filter((r) => !r.ok)
  console.log(`\n---- 汇总: ${results.length - bad.length}/${results.length} 通过 ----`)
  if (bad.length) for (const b2 of bad) console.log(`   ✗ ${b2.name}`)
  done(bad.length ? 1 : 0, bad.length ? `${bad.length} 项失败` : 'A/B/C 全部通过')
}

main().catch((e) => done(1, `探针自身异常: ${e?.stack || e}`))
