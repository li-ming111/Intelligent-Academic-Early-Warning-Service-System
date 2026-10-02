/**
 * WebSocket 真推送端到端探针（W3.2，2026-10-02）。
 *
 * 判据链：STOMP 连接（CONNECT 帧带 Authorization）→ 订阅 `/user/{id}/queue/messages`
 * → `POST /api/messages/send` → **推送真的到达订阅端**。
 *
 * 负向对照（同一次运行里做，缺了它这个探针没有意义）：
 *   B. CONNECT 帧**不带** Authorization ⇒ 服务端没有 Principal ⇒
 *      `convertAndSendToUser` 静默丢包 ⇒ 同一个收件人**收不到**。
 *      这一条正是修之前的状态（`WebSocketInterceptor` 恒 return true、不装身份）。
 *   C. 订阅的是**别人的**队列 ⇒ 收不到（证明推送不是广播给所有人的）。
 *
 * 用法：`node probe-ws-push.mjs <messageServicePort> <receiverUserId>`
 * 脚本自己用仓库里的**开发默认密钥**签 JWT（不打印、不外传），不依赖 auth-service。
 */
import crypto from 'node:crypto'
// Node 按**脚本所在目录**解析裸模块名，本脚本在 testsprite/ 下、不在前端工程里，
// 所以用绝对路径 import 前端工程已装好的 @stomp/stompjs（可用 STOMP_PATH 覆盖）。
const STOMP_ENTRY = process.env.STOMP_PATH
  || 'file:///D:/ABS/academic-warning-frontend/node_modules/@stomp/stompjs/esm6/index.js'
const { Client } = await import(STOMP_ENTRY)

const PORT = process.argv[2] || '8077'
const RECEIVER = Number(process.argv[3] || 56)
const SENDER = 1                     // 真实存在的管理员账号（messages.sender_id 有外键）
const SECRET = 'academic-jwt-secret-key-for-academic-warning-system-2026'
const MARKER = `probe-ws-${Date.now()}`

const b64url = (buf) => Buffer.from(buf).toString('base64url')

function mintToken(userId, username, role) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const now = Math.floor(Date.now() / 1000)
  const payload = b64url(JSON.stringify({
    userId, username, role, sub: username, iat: now, exp: now + 3600
  }))
  const sig = crypto.createHmac('sha256', SECRET).update(`${header}.${payload}`).digest('base64url')
  return `${header}.${payload}.${sig}`
}

/** 起一个 STOMP 客户端，订阅 topic，返回 { received, label, client } */
function subscriber({ withAuth, topic, label, asUser = RECEIVER }) {
  const received = []
  return new Promise((resolve, reject) => {
    const headers = {}
    if (withAuth) headers.Authorization = `Bearer ${mintToken(asUser, 'probe', 5)}`
    const client = new Client({
      brokerURL: `ws://localhost:${PORT}/ws`,
      connectHeaders: headers,
      reconnectDelay: 0,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      debug: () => {}
    })
    const timer = setTimeout(() => reject(new Error(`${label}: 连接超时`)), 8000)
    client.onConnect = () => {
      clearTimeout(timer)
      client.subscribe(topic, (msg) => {
        try { received.push(JSON.parse(msg.body)) } catch { received.push(msg.body) }
      })
      // 订阅是异步生效的，给服务端一点时间登记
      setTimeout(() => resolve({ received, label, client }), 500)
    }
    client.onStompError = (f) => { clearTimeout(timer); reject(new Error(`${label}: STOMP ERROR ${f.headers?.message}`)) }
    client.onWebSocketError = (e) => { clearTimeout(timer); reject(new Error(`${label}: WS ERROR ${e?.message || e}`)) }
    client.activate()
  })
}

async function send(content) {
  const res = await fetch(`http://localhost:${PORT}/api/messages/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': String(SENDER), 'X-Role': '4' },
    body: JSON.stringify({ userId: RECEIVER, content, type: 0 })
  })
  const body = await res.json().catch(() => ({}))
  return { http: res.status, code: body.code }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))
const results = []
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`)
}

// A：收件人本人，带令牌
const A = await subscriber({ withAuth: true, topic: '/user/queue/messages', label: 'A-本人' })
// B：**不带**令牌（服务端没有 Principal）—— 修之前所有会话都是这个状态
const B = await subscriber({ withAuth: false, topic: '/user/queue/messages', label: 'B-不带头' })
// C：带**另一个用户**的令牌，订阅同一个地址 —— 用户隔离是否真的成立
const C = await subscriber({ withAuth: true, topic: '/user/queue/messages', label: 'C-别的用户', asUser: RECEIVER + 1 })
// D：收件人本人，但订阅地址写成 `/user/{id}/queue/messages`（客户端原本的写法）
//    这一条是**回归哨兵**：证明"地址里多写一个 id 就静默收不到"，防止有人改回去
const D = await subscriber({ withAuth: true, topic: `/user/${RECEIVER}/queue/messages`, label: 'D-旧地址写法' })

const r1 = await send(`${MARKER}-A`)
await sleep(1500)

check('A. 本人 + 带令牌 + `/user/queue/messages` ⇒ 收到推送',
  A.received.some(m => typeof m === 'object' && String(m.content || '').includes(MARKER)),
  `HTTP ${r1.http}/code ${r1.code}，收到 ${A.received.length} 条`)
check('B. 不带 Authorization ⇒ 收不到（这正是修之前的状态）',
  !B.received.some(m => String(m.content || '').includes(MARKER)),
  `收到 ${B.received.length} 条`)
check('D. 订阅 `/user/{id}/queue/messages`（旧写法）⇒ 收不到',
  !D.received.some(m => String(m.content || '').includes(MARKER)),
  `收到 ${D.received.length} 条`)
check('C. 另一个用户的会话收不到（推送不是广播）',
  !C.received.some(m => String(m.content || '').includes(MARKER)),
  `收到 ${C.received.length} 条`)

const r2 = await send(`${MARKER}-A2`)
await sleep(1200)
check('G. 第二条消息同样到达（不是"第一条碰巧"）',
  A.received.filter(m => String(m.content || '').includes(MARKER)).length >= 2,
  `A 累计 ${A.received.length} 条`)

const r3 = await fetch(`http://localhost:${PORT}/api/messages/send`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ userId: RECEIVER, content: `${MARKER}-noheader`, type: 0 })
})
check('E. 缺 X-User-Id ⇒ 被拒（不会静默写出一条发件人是 28 的假消息）',
  r3.status !== 200, `HTTP ${r3.status}`)

// F 是这次改动的**正面证据**：发件人来自身份头，不再是写死的 28。
const pushed = A.received.find(m => String(m.content || '').includes(MARKER))
check('F. 推送里的 senderId = 发送人（不再是写死的 28）',
  pushed && Number(pushed.senderId) === SENDER,
  `senderId=${pushed ? pushed.senderId : 'null'}（期望 ${SENDER}）`)

A.client.deactivate(); B.client.deactivate(); C.client.deactivate(); D.client.deactivate()

console.log(`\nMARKER=${MARKER}`)
console.log(`汇总：${results.filter(r => r.ok).length}/${results.length} 通过`)
process.exit(results.every(r => r.ok) ? 0 : 1)
