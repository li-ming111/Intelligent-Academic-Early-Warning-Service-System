/**
 * 极简 STOMP 客户端（W3.2，2026-10-02）。
 *
 * 为什么不用 `@stomp/stompjs`：小程序端没有 `window`、也没有浏览器的 WebSocket，
 * 只能走 `uni.connectSocket`；`@stomp/stompjs` 依赖 `WebSocket` 全局对象，
 * 在微信小程序里直接不可用（本项目 package.json 里也没有这个依赖）。
 * 而我们只需要 STOMP 的四个帧（CONNECT / CONNECTED / SUBSCRIBE / MESSAGE），
 * 所以这里自己实现，只覆盖用得到的部分。
 *
 * 帧格式（STOMP 1.2）：
 * ```
 * COMMAND\n
 * header:value\n
 * ...
 * \n
 * body
 * \0
 * ```
 * 心跳一律不发（`heart-beat:0,0`）—— 服务端 `WebSocketConfig` 没有配
 * `setHeartbeatValue`，本来也不发；发了反而多一条要解析的空帧。
 *
 * ⚠️ 身份必须在 **CONNECT 帧** 的 `Authorization` 头里带 —— 浏览器的 WebSocket API
 * 不允许自定义握手头，小程序也一样。服务端在 `JwtStompAuthInterceptor` 里
 * 读的就是这个 native header，读到 `userId` 才装 Principal；没有 Principal 时
 * `convertAndSendToUser` 会**静默丢包**（连得上、订阅得上、就是永远收不到）。
 */

import { MP_HOST } from '@/config/env.js'

// #ifdef H5
const WS_URL = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`
// #endif
// #ifndef H5
// 小程序没有代理，只能直连网关；地址与 HTTP 接口同源，见 config/env.js。
const WS_URL = `ws://${MP_HOST}/ws`
// #endif

const MAX_RECONNECT = 5
const RECONNECT_BASE_DELAY = 3000

class MiniStompClient {
  constructor() {
    this.task = null
    this.connected = false
    this.subscriptions = {}   // id -> { destination, callback }
    this.destinationToId = {} // destination -> id
    this.nextId = 0
    this.buffer = ''
    this.reconnectAttempts = 0
    this.reconnectTimer = null
    this.manualClose = false
  }

  _frame(command, headers = {}, body = '') {
    const head = Object.keys(headers)
      .filter(k => headers[k] !== undefined && headers[k] !== null)
      .map(k => `${k}:${headers[k]}`)
      .join('\n')
    return `${command}\n${head}\n\n${body}\0`
  }

  connect() {
    if (this.connected || this.task) return Promise.resolve()

    return new Promise((resolve) => {
      this.manualClose = false
      const task = uni.connectSocket({ url: WS_URL, complete: () => {} })
      this.task = task

      task.onOpen(() => {
        this.connected = true
        this.reconnectAttempts = 0
        this._send(this._frame('CONNECT', {
          'accept-version': '1.2',
          host: 'localhost',
          'heart-beat': '0,0',
          Authorization: `Bearer ${uni.getStorageSync('token') || ''}`
        }))
        resolve()
      })

      task.onMessage((res) => this._onData(res.data))

      task.onClose(() => {
        this.connected = false
        this.task = null
        if (!this.manualClose) this._scheduleReconnect()
      })

      task.onError((err) => {
        console.warn('[STOMP] WebSocket 错误:', err && err.errMsg)
        this.connected = false
        this.task = null
        resolve() // 不 reject：实时通道连不上不该让页面报错，退化成手动刷新
        if (!this.manualClose) this._scheduleReconnect()
      })
    })
  }

  _onData(data) {
    this.buffer += typeof data === 'string' ? data : ''
    let idx
    // STOMP 帧之间以 NUL(\0) 分隔
    while ((idx = this.buffer.indexOf('\0')) >= 0) {
      const raw = this.buffer.slice(0, idx)
      this.buffer = this.buffer.slice(idx + 1)
      if (raw.trim()) this._handleFrame(raw)
    }
  }

  _handleFrame(raw) {
    const sep = raw.indexOf('\n\n')
    const headPart = sep >= 0 ? raw.slice(0, sep) : raw
    const body = sep >= 0 ? raw.slice(sep + 2) : ''
    const lines = headPart.split('\n')
    const command = (lines.shift() || '').trim()

    const headers = {}
    for (const line of lines) {
      const i = line.indexOf(':')
      if (i > 0) headers[line.slice(0, i)] = line.slice(i + 1)
    }

    if (command === 'MESSAGE') {
      const dest = headers.destination
      const id = headers.subscription || this.destinationToId[dest]
      const entry = this.subscriptions[id]
      if (!entry) return
      let payload = body
      try { payload = JSON.parse(body) } catch (e) { /* 非 JSON 就原样给 */ }
      try { entry.callback(payload) } catch (e) {
        console.error('[STOMP] 订阅回调异常:', e)
      }
    } else if (command === 'ERROR') {
      console.warn('[STOMP] 服务端 ERROR 帧:', headers.message || body)
    }
  }

  _send(frame) {
    if (!this.task) return
    this.task.send({ data: frame, fail: (e) => console.warn('[STOMP] 发送失败:', e && e.errMsg) })
  }

  /** 订阅一个目的地；未连接时先连再订。重复订阅同一目的地会复用。 */
  async subscribe(destination, callback) {
    const existing = this.destinationToId[destination]
    if (existing) {
      this.subscriptions[existing].callback = callback
      return existing
    }

    const id = `sub-${this.nextId++}`
    this.subscriptions[id] = { destination, callback }
    this.destinationToId[destination] = id

    await this.connect()
    this._send(this._frame('SUBSCRIBE', { id, destination, ack: 'auto' }))
    return id
  }

  unsubscribe(destination) {
    const id = this.destinationToId[destination]
    if (!id) return
    this._send(this._frame('UNSUBSCRIBE', { id }))
    delete this.subscriptions[id]
    delete this.destinationToId[destination]
  }

  _scheduleReconnect() {
    if (this.reconnectAttempts >= MAX_RECONNECT) return
    this.reconnectAttempts++
    const delay = RECONNECT_BASE_DELAY * this.reconnectAttempts
    clearTimeout(this.reconnectTimer)
    this.reconnectTimer = setTimeout(() => {
      if (this.connected || this.manualClose) return
      this.connect().then(() => {
        // 重连后把已有订阅补回来
        Object.keys(this.subscriptions).forEach((id) => {
          const { destination } = this.subscriptions[id]
          this._send(this._frame('SUBSCRIBE', { id, destination, ack: 'auto' }))
        })
      })
    }, delay)
  }

  close() {
    this.manualClose = true
    clearTimeout(this.reconnectTimer)
    if (this.task) {
      try { this.task.close({}) } catch (e) { /* ignore */ }
    }
    this.task = null
    this.connected = false
    this.subscriptions = {}
    this.destinationToId = {}
  }
}

const stompClient = new MiniStompClient()

/**
 * 本人消息目的地。
 *
 * ⭐ **写死 `/user/queue/messages`，不要在中间插 userId。** 服务端推送的目的地是
 * `/queue/messages-user{sessionId}`（按 `SimpUserRegistry` 查该用户会话），而客户端
 * **订阅**时 Spring 只切掉 `/user` 这一段前缀再拼同样的后缀：
 *   · `/user/queue/messages`          → `/queue/messages-user{sessionId}` ✅
 *   · `/user/{userId}/queue/messages` → `/{userId}/queue/messages-user{sessionId}` ❌
 * 后者连得上、订阅得上、服务端还打「推送成功」，就是一条收不到（2026-10-02 实测）。
 * 身份由会话 Principal 决定（CONNECT 帧的 Authorization），不需要写在地址里。
 */
export function currentUserTopic() {
  const userId = uni.getStorageSync('userId')
  return userId ? '/user/queue/messages' : null
}

/** 订阅自己的实时消息；未登录 / 连不上都静默跳过（实时通道是加速器，不是依赖）。 */
export async function subscribeMyMessages(onMessage) {
  const topic = currentUserTopic()
  if (!topic) return null
  try {
    await stompClient.subscribe(topic, onMessage)
    return topic
  } catch (e) {
    console.warn('[STOMP] 订阅失败，本次会话不接收实时推送:', e && e.message)
    return null
  }
}

export function unsubscribeMyMessages() {
  const topic = currentUserTopic()
  if (topic) stompClient.unsubscribe(topic)
}

export default stompClient
