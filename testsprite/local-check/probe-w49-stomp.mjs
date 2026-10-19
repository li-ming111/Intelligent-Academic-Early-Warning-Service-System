// W49（2026-10-19）端到端探针：PC 端实时推送整条链到底通没通？
//
// 判据分四档，**逐档独立**，别用"能连上"冒充"能收到"：
//   ① 握手：ws://localhost:8076/ws 能升级成 101（W49 修的就是这一跳）
//   ② CONNECT 帧带 Authorization ⇒ 服务端回 CONNECTED（验票在 JwtStompAuthInterceptor）
//   ③ SUBSCRIBE /user/queue/messages 能成功订阅
//   ④ 真的收到一条推送（向 message-service 投递一条定向消息，看本连接是否收到）
//
// 第 ④ 档是本探针的**承重点**：只做 ①–③ 时，即使 Principal 名字错了、
// 或订阅地址写错了，前三档照样全绿而消息一条收不到（W3.2 就栽在这）。
//
// 用法: node probe-w49-stomp.mjs [用户名] [密码]
import { createHmac } from 'node:crypto'

const GW = 'http://localhost:8076'
const USER = process.argv[2] || '2023020616'
const PASS = process.argv[3] || '123456'

let pass = 0, fail = 0
const ok = (m) => { console.log(`  ✔ ${m}`); pass++ }
const bad = (m) => { console.log(`  ✘ ${m}`); fail++ }
const chk = (m, got, want) => (String(got) === String(want) ? ok(`${m} (${got})`) : bad(`${m} —— 期望 ${want}，实得 ${got}`))

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ⚠️ 必须收 username 参数。第一版忘了收，`login('2023020617')` 仍登成 2023020616
//    ⇒ 两个 userId 相同 ⇒ 负向对照整块被 `if` 跳过、**还照样打印"通过 6/失败 0"**。
//    这就是本仓记过的"静默跳过"形状：跳过的断言与通过的断言在输出上长得一样。
async function login(username = USER, password = PASS) {
  const cap = await (await fetch(`${GW}/api/auth/captcha`)).json()
  const { captchaId, captchaText } = cap.data
  const res = await (await fetch(`${GW}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, captchaId, captcha: captchaText }),
  })).json()
  if (!res.data?.token) throw new Error(`登录失败(${username}): ${JSON.stringify(res).slice(0, 300)}`)
  return { token: res.data.token, userId: res.data.userId ?? res.data.user?.id }
}

/**
 * 极简 STOMP over 原生 WebSocket（Node 22+ 自带 WebSocket）。
 * 只实现 CONNECT / SUBSCRIBE / 收 MESSAGE —— 够用来钉判据。
 */
function stompConnect(url, token) {
  return new Promise((resolve) => {
    const state = { connected: false, messages: [], frames: [], error: null }
    let ws
    try { ws = new WebSocket(url) } catch (e) { resolve({ ...state, error: e }); return }
    state.ws = ws
    const sendFrame = (cmd, headers = {}, body = '') => {
      const h = Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')
      ws.send(`${cmd}\n${h}\n\n${body}\0`)
    }
    state.subscribe = (dest, id = 'sub-0') => sendFrame('SUBSCRIBE', { id, destination: dest, ack: 'auto' })
    state.sendFrame = sendFrame

    ws.onopen = () => {
      state.opened = true
      sendFrame('CONNECT', {
        'accept-version': '1.2',
        host: 'localhost',
        'heart-beat': '0,0',
        // ⭐ 身份只能在这里带（浏览器 WebSocket API 设不了握手头）
        Authorization: `Bearer ${token}`,
      })
    }
    ws.onmessage = (ev) => {
      const raw = typeof ev.data === 'string' ? ev.data : ''
      for (const chunk of raw.split('\0')) {
        const f = chunk.trim()
        if (!f) continue
        const [head, ...bodyParts] = f.split('\n\n')
        const lines = head.split('\n')
        const command = lines[0]
        const headers = {}
        for (const line of lines.slice(1)) {
          const i = line.indexOf(':')
          if (i > 0) headers[line.slice(0, i)] = line.slice(i + 1)
        }
        const frame = { command, headers, body: bodyParts.join('\n\n') }
        state.frames.push(frame)
        if (command === 'CONNECTED') state.connected = true
        if (command === 'MESSAGE') state.messages.push(frame)
        if (command === 'ERROR') state.error = frame
      }
    }
    ws.onerror = (e) => { state.error = state.error || e }
    ws.onclose = (e) => { state.closed = { code: e.code, reason: e.reason } }
    resolve(state)
  })
}

const main = async () => {
  console.log(`=== 登录 ${USER} ===`)
  let auth
  try { auth = await login() } catch (e) { console.log(`  ✘ ${e.message}`); process.exit(1) }
  ok(`登录成功 userId=${auth.userId}`)

  // ---------- ① 握手 ----------
  console.log('\n=== ① 握手：直连网关 /ws（不带任何认证头）===')
  const hs = await new Promise((resolve) => {
    const ws = new WebSocket('ws://localhost:8076/ws')
    const t = setTimeout(() => resolve({ result: '超时未决（10s）' }), 10000)
    ws.onopen = () => { clearTimeout(t); resolve({ result: 'open' }); try { ws.close() } catch {} }
    ws.onerror = () => { clearTimeout(t); resolve({ result: 'error（握手被拒）' }) }
    ws.onclose = (e) => { clearTimeout(t); resolve({ result: `close code=${e.code}` }) }
  })
  chk('裸 WebSocket 握手（无 token）', hs.result, 'open')

  // ---------- ② CONNECT ----------
  console.log('\n=== ② STOMP CONNECT 帧带 Authorization ===')
  const st = await stompConnect('ws://localhost:8076/ws', auth.token)
  for (let i = 0; i < 50 && !st.connected && !st.error; i++) await sleep(200)
  chk('收到 CONNECTED 帧', !!st.connected, true)
  if (!st.connected) {
    console.log(`     错误帧: ${JSON.stringify(st.error)}`)
    console.log(`     关闭: ${JSON.stringify(st.closed)}`)
    console.log(`\n通过 ${pass} / 失败 ${fail}`)
    process.exit(1)
  }

  // ---------- ③ SUBSCRIBE ----------
  console.log('\n=== ③ 订阅 /user/queue/messages ===')
  st.subscribe('/user/queue/messages', 'sub-0')
  await sleep(1200)
  const subErr = st.frames.find((f) => f.command === 'ERROR')
  chk('订阅无 ERROR 帧', !subErr, true)

  // ---------- ④ 真收到推送（承重） ----------
  console.log('\n=== ④ 真收到一条推送 ===')
  // 从 message-service 的 REST 端点向"自己"推一条（走网关，与业务路径一致）
  const payload = {
    userId: auth.userId,
    message: { id: 999999, content: 'W49PROBE 端到端探针', type: 0, senderName: '探针' },
  }
  const sendRes = await fetch(`${GW}/ws/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${auth.token}` },
    body: JSON.stringify(payload),
  })
  const sendBody = await sendRes.text()
  console.log(`     投递响应: HTTP ${sendRes.status} ${sendBody.slice(0, 160)}`)

  for (let i = 0; i < 50 && st.messages.length === 0; i++) await sleep(200)
  chk('收到 MESSAGE 帧', st.messages.length > 0, true)
  if (st.messages.length) {
    const m = st.messages[0]
    console.log(`     destination: ${m.headers.destination}`)
    console.log(`     body: ${m.body.slice(0, 160)}`)
    chk('载荷含探针标记', m.body.includes('W49PROBE'), true)
    // ⚠️ 这里**不能**断言 destination 带 `-user{sessionId}`：STOMP 的 MESSAGE 帧回显的是
    //    客户端**订阅时写的那个 destination**（`/user/queue/messages`），服务端内部把它解析成
    //    `/queue/messages-user{id}` 是解析器内部行为，不会回显。第一版这么写是判据错，不是缺陷。
  }

  // ---------- ⑤ 负向对照：别人的连接收不到（承重） ----------
  console.log('\n=== ⑤ 负向对照：换一个用户订阅，推给 A 的消息不该到 B ===')
  let other
  try {
    other = await login('2023020617')   // users.id=31，另一个学生
  } catch (e) {
    bad(`拿不到第二个账号，负向对照跑不了: ${e.message}`)
  }
  if (other && String(other.userId) === String(auth.userId)) {
    bad(`第二个账号登成了同一个人（userId=${other.userId}），负向对照无效`)
    other = null
  }
  if (other) {
    const st2 = await stompConnect('ws://localhost:8076/ws', other.token)
    for (let i = 0; i < 50 && !st2.connected && !st2.error; i++) await sleep(200)
    chk('B 也连上了（证明"收不到"不是因为没连上）', !!st2.connected, true)
    st2.subscribe('/user/queue/messages', 'sub-b')
    await sleep(1200)

    // 再给 A 推一条
    await fetch(`${GW}/ws/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${auth.token}` },
      body: JSON.stringify({ userId: auth.userId, message: { id: 999998, content: 'W49PROBE-ONLY-A' } }),
    })
    for (let i = 0; i < 40 && st.messages.length < 2; i++) await sleep(200)
    await sleep(1500)

    chk('A 收到了第二条', st.messages.length >= 2, true)
    chk('B 一条都没收到（身份隔离）', st2.messages.length, 0)
    try { st2.ws.close() } catch {}
  }

  try { st.ws.close() } catch {}
  console.log(`\n通过 ${pass} / 失败 ${fail}`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => { console.log(`FAIL ${e.message}`); process.exit(1) })
