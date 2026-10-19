// W49b（2026-10-19）判据：**小程序 H5 端**的实时推送通不通？
//
// 为什么要单开一支：W49 修好的是网关白名单（`/ws/**` 匹配不上 `/ws`），那是**两侧共用**的。
// 但 PC 端与小程序端各有一份 `vite.config.js`，`/ws` 的代理是**各配各的** ——
// PC 端 W3.2 就补过，小程序端从来没跟上。所以"PC 通了"推不出"小程序也通"。
//
// 判据分三层，逐层承重：
//   A. 打开通知中心时**没有** STOMP 报错，且浏览器**真的**与 `/ws` 握手成功（101）
//   B. 从外部投递一条定向消息后，页面**真的弹出 toast**（onRealtimeMessage 被推送触发）
//   C. 负向对照：投给**别人**，本页**不该**弹出
//
// 承重点在 B：`/ws/send` **不写库**（见 WebSocketMessageController.sendMessageToUser），
// 所以这条消息永远不会出现在页面从库里拉的通知列表里；它出现的唯一地方就是
// `onRealtimeMessage` 抛出的 toast —— 而那个回调只可能由 STOMP 推送触发。
//
// 用法: node probe-w49b-xcx-realtime.mjs [用户名] [密码]
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = process.env.XCX_BASE || 'http://127.0.0.1:5180'
const GW = 'http://localhost:8076'
const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'
const PAGE = '/pages/student/notification-center'

let pass = 0, fail = 0
const ok = (m) => { console.log(`  ✔ ${m}`); pass++ }
const bad = (m) => { console.log(`  ✘ ${m}`); fail++ }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function loginApi(username = USER, password = PASS) {
  const cap = await (await fetch(`${GW}/api/auth/captcha`)).json()
  const { captchaId, captchaText } = cap.data
  const res = await (await fetch(`${GW}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, captchaId, captcha: captchaText }),
  })).json()
  if (!res.data?.token) throw new Error(`登录失败(${username}): ${JSON.stringify(res).slice(0, 200)}`)
  return { token: res.data.token, userId: res.data.userId ?? res.data.user?.id }
}

async function pushMessage(token, userId, content) {
  const r = await fetch(`${GW}/ws/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ userId, message: { id: Date.now(), content, type: 0, senderName: '探针' } }),
  })
  return { status: r.status, body: await r.text() }
}

const main = async () => {
  const me = await loginApi()
  console.log(`=== 以 ${USER}（userId=${me.userId}）登录小程序 H5 ===`)

  const PORT = 9950 + Math.floor(Math.random() * 40)
  const profile = mkdtempSync(join(tmpdir(), 'cdpxcx-'))
  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
  ], { stdio: 'ignore' })
  const cleanup = () => {
    try { chrome.kill() } catch {}
    // Windows 上 Chrome 退出前目录还锁着，删不掉就留着（临时目录，不影响判定）
    try { rmSync(profile, { recursive: true, force: true }) } catch {}
  }

  let target = null
  for (let i = 0; i < 80; i++) {
    try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); target = l.find(t => t.type === 'page'); if (target) break } catch {}
    await sleep(250)
  }
  if (!target) { bad('调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  const consoleErrors = []
  const wsHandshakes = []
  const wsUrls = new Map()   // ⚠️ 握手响应事件里**没有** url，只在 webSocketCreated 里有
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      consoleErrors.push((m.params.args || []).map(a => a.value ?? a.description ?? '').join(' '))
    }
    if (m.method === 'Network.webSocketCreated') wsUrls.set(m.params.requestId, m.params.url)
    if (m.method === 'Network.webSocketHandshakeResponseReceived') {
      const url = wsUrls.get(m.params.requestId) || ''
      if (/\/ws(\?|$)/.test(url)) wsHandshakes.push({ url, status: m.params.response.status })
    }
  }
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  await send('Page.navigate', { url: APP })
  const waitFor = async (expr, t = 45000) => { const d = Date.now() + t; while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} await sleep(400) } return false }

  // —— 登录（uni-app H5 的 DOM 形态，与 xcx-page-smoke.mjs 同款）——
  // ⚠️ 验证码是服务端异步签发的：表单元素到位 ≠ 码已到手，必须一起等。
  await waitFor(`document.querySelectorAll('uni-input').length >= 3 && (document.querySelector('.captcha-code')?.textContent || '').trim().length > 0`)
  await evaluate(`(() => {
    const byPh = (ph) => {
      const wrap = Array.from(document.querySelectorAll('uni-input'))
        .find(u => (u.querySelector('.uni-input-placeholder')?.textContent || '').trim() === ph)
      return wrap ? wrap.querySelector('input') : null
    }
    const setVal = (el, val) => {
      if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    }
    setVal(byPh('请输入学号或工号'), ${JSON.stringify(USER)})
    setVal(byPh('请输入密码'), ${JSON.stringify(PASS)})
    setVal(byPh('输入验证码'), (document.querySelector('.captcha-code')?.textContent || '').trim())
    document.querySelector('.btn')?.click()
    return true
  })()`)
  if (!await waitFor(`(localStorage.getItem('token') ? 1 : 0) === 1 && location.hash.includes('/pages/student/')`, 30000)) {
    bad('登录未落地'); cleanup(); process.exit(1)
  }

  // 等落点稳定，再切到目标页
  let prev = ''
  for (let i = 0; i < 40; i++) { const cur = await evaluate('location.hash'); if (cur && cur === prev) break; prev = cur; await sleep(250) }

  consoleErrors.length = 0; wsHandshakes.length = 0; wsUrls.clear()
  await evaluate(`location.hash = ${JSON.stringify('#' + PAGE)}`)
  await waitFor(`document.body.innerText.includes('通知')`, 20000)
  await sleep(6000)   // 留足 STOMP 连接 + 首次拉取

  // ---------- A. 没有 STOMP 报错 + 握手成功 ----------
  console.log('\n=== A. 页面打开时没有 STOMP 报错，且 /ws 握手成功 ===')
  const stompErrors = consoleErrors.filter(t => /STOMP/.test(t))
  if (stompErrors.length) { bad(`仍有 ${stompErrors.length} 条 STOMP 报错`); stompErrors.slice(0, 3).forEach(t => console.log(`     ${t.slice(0, 140)}`)) }
  else ok('无 STOMP 报错')

  const ws101 = wsHandshakes.filter(h => h.status === 101)
  const wsCreated = [...wsUrls.values()].filter(u => /\/ws(\?|$)/.test(u))
  if (ws101.length) ok(`浏览器 WebSocket 握手 101（${ws101[0].url}）`)
  else if (wsHandshakes.length) bad(`WebSocket 握手未升级：${wsHandshakes.map(h => `${h.status} ${h.url}`).join(', ')}`)
  else if (wsCreated.length) bad(`发起了 /ws 握手但没拿到响应（被拒/被挂断）：${wsCreated[0]}`)
  else bad('浏览器根本没发起 /ws 握手（页面没连 STOMP）')

  // ---------- B. 实时回调真的被触发 ----------
  console.log('\n=== B. 外部投递一条消息 ⇒ 页面真的弹出它 ===')
  await evaluate(`(() => {
    window.__toasts = []
    window.__toastObs = new MutationObserver(() => {
      // uni-app H5 的 showToast 渲染成 .uni-sample-toast / .uni-simple-toast__text，
      // 不是 .uni-toast。第一版按 PC 端 Element Plus 的 .el-message 类比猜错了类名，
      // 于是"消息到了但判据看不见"，差点读成"小程序实时推送没修好"。
      document.querySelectorAll('.uni-sample-toast, .uni-simple-toast__text, .uni-toast, .el-message').forEach(el => {
        const t = (el.innerText || '').trim()
        if (t && !window.__toasts.includes(t)) window.__toasts.push(t)
      })
    })
    window.__toastObs.observe(document.body, { childList: true, subtree: true })
    return true
  })()`)
  const marker = `W49bXCX-${Date.now()}`
  const r = await pushMessage(me.token, me.userId, marker)
  console.log(`     投递: HTTP ${r.status} ${r.body.slice(0, 120)}`)

  const seenToast = async (needle, tries = 25, gap = 200) => {
    for (let i = 0; i < tries; i++) {
      if (await evaluate(`(window.__toasts || []).some(t => t.includes(${JSON.stringify(needle)}))`)) return true
      await sleep(gap)
    }
    return false
  }

  if (await seenToast(marker)) ok('页面弹出实时消息（onRealtimeMessage 真的被推送触发）')
  else {
    bad('页面没有弹出实时消息（实时推送没有驱动页面）')
    console.log(`     最近 toast: ${await evaluate(`JSON.stringify((window.__toasts || []).slice(-5))`)}`)
  }

  // ---------- C. 负向对照 ----------
  console.log('\n=== C. 负向对照：投给别人，本页不该弹出 ===')
  const other = await loginApi('2023020617')
  if (String(other.userId) === String(me.userId)) {
    bad(`第二个账号登成了同一个人（${other.userId}），负向对照无效`)
  } else {
    const otherMarker = `W49b-OTHER-${Date.now()}`
    await pushMessage(other.token, other.userId, otherMarker)
    if (await seenToast(otherMarker)) bad('别人的消息弹到了本页（身份隔离失效）')
    else ok('别人的消息一条都没到本页（身份隔离）')
  }

  cleanup()
  console.log(`\n通过 ${pass} / 失败 ${fail}`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => { console.log(`FAIL ${e.message}`); process.exit(1) })
