class StompClient {
  constructor() {
    this.client = null
    this.connected = false
    this.subscriptions = {}
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 3000
    this.baseUrl = window.location.host
    // 2026-10-02（W3.2）：原来恒用 `ws://`。页面走 https 时浏览器会**拒绝**把
    // 明文 ws 升级请求发出去（混合内容），控制台只留一行 SecurityError，
    // 表现同样是"推送收不到"。按当前页面协议选 ws / wss。
    const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    // 2026-10-19（W49）：**这里刻意不带 `?token=`**。
    // 身份不在握手这一跳装 —— 浏览器 WebSocket API 设不了 Authorization 头，
    // 真正的验票在 message-service 的 `JwtStompAuthInterceptor`（STOMP CONNECT 帧上
    // 读 `Authorization` native header，验完给会话装 Principal）。握手只需要**能连上**，
    // 而那取决于网关白名单能否匹配 `/ws`（W49 已修 `globMatches`）。
    // 把 JWT 塞进 URL 反而会把它写进访问日志/浏览器历史，且对身份没有任何帮助。
    this.wsUrl = `${wsProtocol}://${this.baseUrl}/ws`
    this.reconnectTimer = null
    this.manualDisconnect = false
    this.connectionError = null
  }

  async loadDependencies() {
    if (this.Client) return
    
    try {
      const stompModule = await import('@stomp/stompjs')
      this.Client = stompModule.Client
      console.log('[STOMP] 依赖加载成功')
    } catch (error) {
      console.error('[STOMP] 依赖加载失败:', error)
      throw error
    }
  }

  async connect() {
    // 如果已经达到最大重连次数，不再尝试连接
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('[STOMP] 已达到最大重连次数，跳过连接尝试')
      throw new Error('已达到最大重连次数')
    }

    await this.loadDependencies()
    
    return new Promise((resolve, reject) => {
      if (this.connected) {
        console.log('[STOMP] 已连接，直接返回')
        resolve(this.client)
        return
      }

      try {
        // 清除之前的重连定时器
        if (this.reconnectTimer) {
          clearTimeout(this.reconnectTimer)
          this.reconnectTimer = null
        }

        this.client = new this.Client({
          brokerURL: this.wsUrl,
          connectHeaders: {
            Authorization: `Bearer ${localStorage.getItem('token') || ''}`
          },
          debug: (str) => {
            console.log('[STOMP Debug]', str)
          },
          reconnectDelay: 0,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
          connectTimeout: 10000
        })

        const token = localStorage.getItem('token')
        const userId = localStorage.getItem('userId')
        console.log(`[STOMP] 连接配置 - URL: ${this.wsUrl}, token: ${token ? '存在' : '不存在'}, userId: ${userId}`)

        this.client.onConnect = (frame) => {
          console.log('[STOMP] 连接成功:', frame)
          this.connected = true
          this.reconnectAttempts = 0
          this.connectionError = null
          
          // 断线重连后把已有订阅补回来。
          // ⚠️ 2026-10-02（W3.2）：`this.subscriptions[topic]` 存的是
          // `{ callback, subscription }` 这个**对象**，不是回调本身 —— 原来直接把
          // 它当 callback 传给 `handleMessage`，于是重连之后每收到一条消息都会
          // `callback(parsedBody)` → "callback is not a function"，
          // 表现是"刚连上时能收，掉线重连后再也收不到"。必须取 `.callback`。
          // 同时把新的 subscription 写回去，否则旧的那条只会在内存里越积越多。
          Object.keys(this.subscriptions).forEach((topic) => {
            const entry = this.subscriptions[topic]
            const callback = typeof entry === 'function' ? entry : entry.callback
            const subscription = this.client.subscribe(topic, (message) => {
              this.handleMessage(topic, message, callback)
            })
            this.subscriptions[topic] = { callback, subscription }
            console.log('[STOMP] 重新订阅:', topic)
          })
          
          resolve(this.client)
        }

        this.client.onStompError = (error) => {
          console.error('[STOMP] STOMP协议错误:', error)
          this.connected = false
          this.connectionError = error
          if (reject) reject(error)
        }

        this.client.onWebSocketError = (error) => {
          console.error('[STOMP] WebSocket错误:', error)
          this.connected = false
          this.connectionError = error
          if (reject) reject(error)
        }

        this.client.onDisconnect = (frame) => {
          console.warn('[STOMP] 连接关闭:', frame)
          this.connected = false
          if (!this.manualDisconnect) {
            this.scheduleReconnect()
          }
        }

        this.client.activate()
      } catch (error) {
        console.error('[STOMP] 连接初始化失败:', error)
        this.connectionError = error
        reject(error)
      }
    })
  }

  scheduleReconnect() {
    if (this.manualDisconnect) {
      console.log('[STOMP] 手动断开连接，不进行重连')
      return
    }

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`[STOMP] 达到最大重连次数(${this.maxReconnectAttempts}次)，停止尝试。请检查后端WebSocket服务是否运行。`)
      return
    }
    
    this.reconnectAttempts++
    const delay = this.reconnectDelay * this.reconnectAttempts
    console.log(`[STOMP] 第${this.reconnectAttempts}次重连尝试，${delay/1000}秒后执行...`)
    
    this.reconnectTimer = setTimeout(() => {
      if (!this.connected && !this.manualDisconnect) {
        this.connect().catch((err) => {
          console.error('[STOMP] 重连失败:', err)
        })
      }
    }, delay)
  }

  disconnect() {
    this.manualDisconnect = true
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    
    if (this.client) {
      this.client.deactivate().then(() => {
        console.log('[STOMP] 连接已断开')
        this.connected = false
      }).catch((error) => {
        console.warn('[STOMP] 断开连接时出错:', error)
      })
    }
  }

  handleMessage(topic, message, callback) {
    console.log('[STOMP] 收到消息 - topic:', topic, 'destination:', message.destination)
    try {
      const parsedBody = JSON.parse(message.body)
      console.log('[STOMP] 消息内容:', parsedBody)
      callback(parsedBody)
    } catch (e) {
      console.warn('[STOMP] 消息解析失败，直接传递原始内容:', e)
      callback(message.body)
    }
  }

  async subscribe(topic, callback) {
    await this.loadDependencies()
    
    return new Promise((resolve, reject) => {
      const subscribeFn = () => {
        try {
          console.log(`[STOMP] 准备订阅主题: ${topic}`)
          
          const subscription = this.client.subscribe(topic, (message) => {
            this.handleMessage(topic, message, callback)
          })
          
          this.subscriptions[topic] = { callback, subscription }
          console.log('[STOMP] 订阅成功 - topic:', topic, 'subscriptionId:', subscription.id)
          resolve(subscription)
        } catch (error) {
          console.error('[STOMP] 订阅失败:', error)
          reject(error)
        }
      }

      if (this.connected && this.client) {
        subscribeFn()
      } else {
        console.log('[STOMP] 未连接，先连接再订阅')
        this.connect().then(subscribeFn).catch((error) => {
          console.error('[STOMP] 连接失败，订阅被跳过:', error)
          reject(error)
        })
      }
    })
  }

  unsubscribe(topic) {
    if (this.client && this.subscriptions[topic]) {
      try {
        const sub = this.subscriptions[topic]
        if (sub.subscription) {
          sub.subscription.unsubscribe()
          console.log('[STOMP] 取消订阅 - topic:', topic)
        }
      } catch (error) {
        console.warn('[STOMP] 取消订阅时出错:', error)
      }
      delete this.subscriptions[topic]
    }
  }

  async send(destination, body, headers = {}) {
    await this.loadDependencies()
    
    return new Promise((resolve, reject) => {
      const sendFn = () => {
        try {
          const jsonBody = typeof body === 'object' ? JSON.stringify(body) : body
          this.client.publish({ destination, body: jsonBody, headers })
          console.log('[STOMP] 发送消息 - destination:', destination, 'body:', body)
          resolve()
        } catch (error) {
          console.error('[STOMP] 发送消息失败:', error)
          reject(error)
        }
      }

      if (this.connected && this.client) {
        sendFn()
      } else {
        this.connect().then(sendFn).catch(reject)
      }
    })
  }

  getConnected() {
    return this.connected
  }

  getSubscriptions() {
    return Object.keys(this.subscriptions)
  }

  getConnectionError() {
    return this.connectionError
  }

  resetReconnectAttempts() {
    this.reconnectAttempts = 0
  }
}

const stompClient = new StompClient()

export default stompClient
