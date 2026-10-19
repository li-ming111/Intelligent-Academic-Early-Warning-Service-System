// W49（2026-10-19）判据：已打开的消息页会不会**立刻**收到新消息、未读数自动 +1？
//
// 为什么单开这一支：`probe-w49-stomp.mjs` 证明的是"协议层能连、能订阅、能收到"，
// 但产品要的是**页面反应**。W48 记的那半个能力是「已打开的页面立刻收到新消息 +
// 未读数自动 +1」—— 只有真打开页面、真投递、真看 DOM 才能判定。
//
// 判据分三层，逐层承重：
//   A. 页面打开时**没有** STOMP 报错（修之前稳定报两条 console.error）
//   B. 从外部投递一条定向消息后，页面 DOM **真的多出那条内容**（不是只更新了内存）
//   C. 负向对照：把消息投给**别人**，本页 DOM **不该**出现它
//
// 用法: node probe-w49-realtime-page.mjs
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const GW = 'http://localhost:8076'
const USER = process.argv[2] || '2023020616'
const PASS = '123456'
const PAGE = process.argv[3] || '/student/messages'

let pass = 0, fail = 0
const ok = (m) => { console.log(`  ✔ ${m}`); pass++ }
const bad = (m) => { console.log(`  ✘ ${m}`); fail++ }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function loginApi(username = USER) {
  const cap = await (await fetch(`${GW}/api/auth/captcha`)).json()
  const { captchaId, captchaText } = cap.data
  const res = await (await fetch(`${GW}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PASS, captchaId, captcha: captchaText }),
  })).json()
  if (!res.data?.token) throw new Error(`登录失败(${username}): ${JSON.stringify(res).slice(0, 200)}`)
  return { token: res.data.token, userId: res.data.userId }
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
  console.log(`=== 以 ${USER}（userId=${me.userId}）登录页面 ===`)

  const PORT = 9930 + Math.floor(Math.random() * 40)
  const profile = mkdtempSync(join(tmpdir(), 'cdprt-'))
  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
  ], { stdio: 'ignore' })
  const cleanup = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }

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
  const wsHandshakes = []   // ws://…/ws 的握手响应码
  const wsUrls = new Map()  // requestId → url（握手响应事件里**没有** url，只在 webSocketCreated 里）
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      const text = (m.params.args || []).map(a => a.value ?? a.description ?? '').join(' ')
      consoleErrors.push(text)
    }
    if (m.method === 'Network.webSocketCreated') {
      wsUrls.set(m.params.requestId, m.params.url)
    }
    if (m.method === 'Network.webSocketHandshakeResponseReceived') {
      const { requestId } = m.params
      const url = wsUrls.get(requestId) || ''
      if (/\/ws(\?|$)/.test(url)) wsHandshakes.push({ url, status: m.params.response.status })
    }
  }
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })) })
  const evaluate = async (expr) => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  await send('Page.navigate', { url: `${APP}/login` })
  const waitFor = async (expr, t = 45000) => { const d = Date.now() + t; while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} await sleep(400) } return false }

  await waitFor(`/^[A-Z0-9]{4}$/.test((document.querySelector('.lr-captcha-btn')?.textContent||'').trim())`)
  await evaluate(`(() => {
    const s = (q, v) => { const e = document.querySelector(q); if (!e) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(e, v)
      e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true }
    s('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    s('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    s('input[placeholder="验证码"]', (document.querySelector('.lr-captcha-btn')?.textContent||'').trim())
    document.querySelector('.lr-submit')?.click(); return true
  })()`)
  if (!await waitFor(`location.pathname !== '/login'`, 30000)) { bad('登录未落地'); cleanup(); process.exit(1) }

  // 等落点稳定，再导航到目标页（避免登录后的 push 盖掉我们的导航）
  let prev = ''
  for (let i = 0; i < 40; i++) { const cur = await evaluate('location.pathname'); if (cur && cur === prev) break; prev = cur; await sleep(250) }

  consoleErrors.length = 0
  wsHandshakes.length = 0; wsUrls.clear()   // 只认目标页这一跳的握手，别把登录后布局的旧连接算进来
  await send('Page.navigate', { url: APP + PAGE })
  await waitFor(`location.pathname === ${JSON.stringify(PAGE)}`, 20000)
  await sleep(6000)   // 留足 STOMP 连接 + 首次拉取

  // ---------- A. 没有 STOMP 报错 ----------
  console.log('\n=== A. 页面打开时没有 STOMP 报错 ===')
  const stompErrors = consoleErrors.filter(t => /STOMP/.test(t))
  if (stompErrors.length) { bad(`仍有 ${stompErrors.length} 条 STOMP 报错`); stompErrors.slice(0, 3).forEach(t => console.log(`     ${t.slice(0, 120)}`)) }
  else ok('无 STOMP 报错')

  // ⚠️ 这里**不能**用 `import('/src/utils/stompClient.js').getConnected()` 当判据：
  //    动态 import 拿到的是与 App 打包实例**不同的模块实例**，它的 `connected` 永远是
  //    false（第一版就这么写，还把 false 无条件记成 ✔ —— 恒真断言）。真正的证据在
  //    网络层：CDP 的 `Network.webSocketHandshakeResponseReceived` 会给出握手响应码。
  const ws101 = wsHandshakes.filter(h => h.status === 101)
  const wsCreated = [...wsUrls.values()].filter(u => /\/ws(\?|$)/.test(u))
  if (ws101.length) ok(`浏览器 WebSocket 握手 101（${ws101[0].url}）`)
  else if (wsHandshakes.length) bad(`WebSocket 握手未升级：${wsHandshakes.map(h => `${h.status} ${h.url}`).join(', ')}`)
  else if (wsCreated.length) bad(`发起了 /ws 握手但没拿到响应（被拒/被挂断）：${wsCreated[0]}`)
  else bad('浏览器根本没发起 /ws 握手（页面没连 STOMP）')

  // ---------- B. 实时回调真的被触发 ----------
  // ⚠️ 判据的承重点在哪：`/ws/send` **不写库**（见 WebSocketMessageController.sendMessageToUser），
  //    所以这条消息永远不会出现在页面「从库里拉出来的」通知列表里。它会出现的唯一地方，
  //    是 `onRealtimeMessage` 抛出的那条 Element Plus toast（`.el-message`）——
  //    而那个回调**只可能**由 STOMP 推送触发。所以「toast 里出现探针标记」＝「实时推送到位」。
  //    第一版写的是 `document.body.innerText.includes(marker)`：它既分不清 toast 和列表，
  //    toast 又只活 3 秒，轮询稍慢就会漏（时间竞态）。改用 MutationObserver 记账，无竞态。
  console.log('\n=== B. 外部投递一条消息 ⇒ 页面真的弹出它（实时回调被触发）===')
  await evaluate(`(() => {
    window.__toasts = []
    window.__toastObs = new MutationObserver(() => {
      document.querySelectorAll('.el-message').forEach(el => {
        const t = (el.innerText || '').trim()
        if (t && !window.__toasts.includes(t)) window.__toasts.push(t)
      })
    })
    window.__toastObs.observe(document.body, { childList: true, subtree: true })
    return true
  })()`)
  const marker = `W49REALTIME-${Date.now()}`
  const r = await pushMessage(me.token, me.userId, marker)
  console.log(`     投递: HTTP ${r.status} ${r.body.slice(0, 120)}`)

  const seenToast = async (needle, tries = 25, gap = 200) => {
    for (let i = 0; i < tries; i++) {
      const hit = await evaluate(`(window.__toasts || []).some(t => t.includes(${JSON.stringify(needle)}))`)
      if (hit) return true
      await sleep(gap)
    }
    return false
  }

  if (await seenToast(marker)) ok('页面弹出实时消息（onRealtimeMessage 真的被推送触发）')
  else {
    bad('页面没有弹出实时消息（实时推送没有驱动页面）')
    const dump = await evaluate(`JSON.stringify((window.__toasts || []).slice(-5))`)
    console.log(`     最近 toast: ${dump}`)
  }

  // ---------- C. 负向对照 ----------
  console.log('\n=== C. 负向对照：投给别人，本页不该弹出 ===')
  const other = await loginApi('2023020617')
  if (String(other.userId) === String(me.userId)) {
    bad(`第二个账号登成了同一个人（${other.userId}），负向对照无效`)
  } else {
    const otherMarker = `W49-OTHER-${Date.now()}`
    await pushMessage(other.token, other.userId, otherMarker)
    if (await seenToast(otherMarker)) bad('别人的消息弹到了本页（身份隔离失效）')
    else ok('别人的消息一条都没到本页（身份隔离）')
  }

  cleanup()
  console.log(`\n通过 ${pass} / 失败 ${fail}`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => { console.log(`FAIL ${e.message}`); process.exit(1) })
