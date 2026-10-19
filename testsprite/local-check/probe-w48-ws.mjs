// W48 诊断：PC 端「消息/通知」页的 STOMP 到底成不成立？
//
// 现象：smoke-pages 三个角色的 messages/notifications 页都稳定报
//   console.error [STOMP] WebSocket错误: Event
//   console.error [STOMP] 连接失败，订阅被跳过: Event
// 候选根因（别猜，抓现场）：
//   A. message-service(8077) 没起 —— 网关路由到不了
//   B. 网关白名单 `/ws/**` 用 `path.matches(pattern.replace("**",".*"))` 全串匹配，
//      匹配不上客户端实际连的 **`/ws`**（无尾段）⇒ 401；而浏览器 WebSocket API
//      **不允许设 Authorization 头** ⇒ 无论登录与否都过不去
//   C. 客户端订阅地址写错
// 本探针把 WebSocket 创建 URL、握手响应、以及后端 /ws 的实际可达性一起打出来。
//
// 用法: node probe-w48-ws.mjs [student]
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const APP = 'http://localhost:5173'
const USER = process.argv[2] || '2023020616'
const PASS = '123456'
// 第 3 个参数=要观察的消息页路径。admin/messages 的订阅发生在
// loadUsers/loadMessages/loadTasks 三个 await **之后**，可能晚于 smoke 的
// 2600ms 采样窗 ⇒ 它在 smoke 里显示 ✓ 只是"还没轮到报错"，不是"没这个 bug"。
const PAGE = process.argv[3] || '/student/messages'

const PORT = 9950 + Math.floor(Math.random() * 40)
const profile = mkdtempSync(join(tmpdir(), 'cdpws-'))
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, 'about:blank',
], { stdio: 'ignore' })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const cleanup = () => { try { chrome.kill() } catch {} try { rmSync(profile, { recursive: true, force: true }) } catch {} }

const main = async () => {
  let target = null
  for (let i = 0; i < 80; i++) {
    try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); target = l.find(t => t.type === 'page'); if (target) break } catch {}
    await sleep(250)
  }
  if (!target) { console.log('FAIL 调试端口未就绪'); cleanup(); process.exit(1) }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  let id = 0
  const pending = new Map()
  const netLog = []
  ws.onmessage = e => {
    const m = JSON.parse(e.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    const p = m.params
    if (m.method === 'Network.webSocketCreated') netLog.push(`WS 创建: ${p.url}`)
    else if (m.method === 'Network.webSocketWillSendHandshakeRequest') netLog.push(`WS 发握手: ${JSON.stringify(p.request?.headers || {})}`)
    else if (m.method === 'Network.webSocketHandshakeResponseReceived') netLog.push(`WS 握手响应: HTTP ${p.response?.status} ${p.response?.statusText || ''}`)
    else if (m.method === 'Network.webSocketFrameError') netLog.push(`WS 帧错误: ${p.errorMessage}`)
    else if (m.method === 'Network.webSocketClosed') netLog.push(`WS 关闭`)
    else if (m.method === 'Network.loadingFailed' && /ws|8076/.test(p.documentURL || '')) netLog.push(`加载失败: ${p.errorText}`)
  }
  const send = (m, params = {}) => new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: m, params })) })
  const evaluate = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  await send('Page.navigate', { url: `${APP}/login` })
  const waitFor = async (expr, t = 45000) => { const d = Date.now() + t; while (Date.now() < d) { try { if (await evaluate(expr)) return true } catch {} await sleep(400) } return false }

  await waitFor(`/^[A-Z0-9]{4}$/.test((document.querySelector('.lr-captcha-btn')?.textContent||'').trim())`)
  await evaluate(`(() => {
    const setVal = (sel, val) => { const el = document.querySelector(sel); if (!el) return false
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(el, val)
      el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true }
    setVal('input[placeholder="请输入学号或工号"]', ${JSON.stringify(USER)})
    setVal('input[placeholder="请输入密码"]', ${JSON.stringify(PASS)})
    setVal('input[placeholder="验证码"]', (document.querySelector('.lr-captcha-btn')?.textContent||'').trim())
    document.querySelector('.lr-submit')?.click(); return true
  })()`)
  if (!await waitFor(`location.pathname !== '/login'`, 30000)) { console.log('FAIL 登录未落地'); cleanup(); process.exit(1) }
  console.log(`已登录 ${USER}\n`)

  netLog.length = 0
  // ⚠️ 登录成功后前端会 `router.push('/<role>/dashboard')`。若在它落地前就 Page.navigate，
  // 那次 push 会**盖掉**我们的导航（落点回到 dashboard）。必须等落点稳定（连续两次相同）
  // 再走，否则会看到"导航到 A 却永远停在 dashboard"这种**探针自己造的**现象。
  let prev = ''
  for (let i = 0; i < 40; i++) {
    const cur = await evaluate('location.pathname')
    if (cur && cur === prev) break
    prev = cur
    await sleep(250)
  }
  await send('Page.navigate', { url: APP + PAGE })
  // ⚠️ W48：判据必须落在**落点**上。原来只 `sleep(N)`，而整页 Page.navigate 到
  // 渲染出目标路由有竞态：有一次采到的还是**上一页 dashboard**，日志里就写成
  // "在 /admin/messages 上没发起 WebSocket"—— 看着像产品缺陷，其实是探针自己的。
  await waitFor(`location.pathname === ${JSON.stringify(PAGE)}`, 20000)
  await sleep(Number(process.env.WAIT_MS || 9000))

  console.log('=== WebSocket 网络事件 ===')
  if (!netLog.length) console.log('  （一条都没有 —— 说明根本没发起 WebSocket 连接）')
  for (const l of netLog) console.log('  ' + l)

  const diag = await evaluate(`JSON.stringify({path: location.pathname, uid: localStorage.getItem('userId'), bodyLen: document.body.innerText.length, head: document.body.innerText.replace(/\s+/g,' ').slice(0,80)})`)
  console.log('页面诊断: ' + diag)
  const tok = await evaluate(`(localStorage.getItem('token')||'').slice(0,18)`)
  console.log(`\nlocalStorage.token 前缀: ${tok}...`)

  // 在浏览器里复刻 stompClient 的 URL 构造，并起一个裸 WebSocket 看握手成败
  const bare = await evaluate(`(async () => {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = proto + '://' + location.host + '/ws'
    return await new Promise(resolve => {
      const t = setTimeout(() => resolve({ url, result: '超时未决（10s）' }), 10000)
      try { const s = new WebSocket(url)
        s.onopen = () => { clearTimeout(t); resolve({ url, result: 'open（连接建立）' }); try{s.close()}catch{} }
        s.onerror = () => { clearTimeout(t); resolve({ url, result: 'error（握手被拒）' }); }
        s.onclose = (e) => { clearTimeout(t); resolve({ url, result: 'close code=' + e.code }) }
      } catch (e) { clearTimeout(t); resolve({ url, result: 'throw: ' + e.message }) }
    })
  })()`)
  console.log(`裸 WebSocket 直连: ${bare?.url} => ${bare?.result}`)

  cleanup(); process.exit(0)
}
main().catch(e => { console.log(`FAIL ${e.message}`); cleanup(); process.exit(1) })
