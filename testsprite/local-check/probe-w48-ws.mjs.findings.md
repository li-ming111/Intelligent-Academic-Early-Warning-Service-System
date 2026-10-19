# W48 发现：PC 端实时推送（STOMP/WebSocket）整条链**从未成立**

> ✅ **W49（2026-10-19）已修好，正反两向验证过。本文第 ② 条根因与「未修」两节是错的，
> 见文末「W49 订正」。** 保留原文是因为它准确记录了当时看到的现场。

日期 2026-10-18。由 `probe-w48-ws.mjs` 现场取证，不是读代码推的。

## 症状

`smoke-pages.mjs` 三个角色（student/teacher/counselor）的 `messages`/`notifications`
页稳定报两条 `console.error`：

```
[STOMP] WebSocket错误: Event
[STOMP] 连接失败，订阅被跳过: Event
```

## 取证（CDP 网络层 + curl 对照）

浏览器实际发起的握手（`probe-w48-ws.mjs`）：

```
WS 创建: ws://localhost:5173/ws
WS 发握手: {"Upgrade":"websocket","Origin":"http://localhost:5173", ... }   ← 没有 Authorization
WS 帧错误: HTTP Authentication failed; no valid credentials available
WS 关闭
裸 WebSocket 直连: ws://localhost:5173/ws => error（握手被拒）
```

网关侧逐步对照（curl 直接打 8076）：

| 请求 | 无 token | 带 token |
|---|---|---|
| `/ws`（客户端实际连的） | **401 缺少认证信息** | 101 |
| `/ws/` | 101 | 101 |
| `/ws/info` | 101 | 101 |

## 根因（两层，都在握手这一跳）

**① 网关白名单的 `**` 写法匹配不上 `/ws`**（承重原因）

`AuthenticationFilter.isWhiteListed`：

```java
WHITE_LIST.stream().anyMatch(pattern -> path.matches(pattern.replace("**", ".*")));
```

`String.matches` 是**全串匹配**，于是白名单里的 `"/ws/**"` 变成正则 `/ws/.*`，
**匹配不上客户端实际请求的 `/ws`**（无尾段）。复刻验证：

```
/ws          -> 未命中 ⇒ 401
/ws/         -> 命中
/ws/info     -> 命中
/wsx         -> 未命中（正确）
```

于是浏览器请求 `/ws` 在网关这一跳就被 `unauthorized()` 挡下，
**根本没机会走到下游 message-service**，也不会带请求头往上传。

**② 浏览器 WebSocket API 不允许设自定义请求头**

即便放过 ①，网关取 token 只认 `Authorization` 头：
`String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)`。
而 `new WebSocket()` **无法设置任何自定义头** —— `@stomp/stompjs` 的
`connectHeaders` 是发在 **STOMP CONNECT 帧**里的（socket 建立**之后**）。
这一点下游 `message-service/WebSocketInterceptor` 的注释里**早就写明**：

> 那段"从请求头取 Authorization"是**取不到的**：浏览器的 WebSocket API 不允许设置自定义 HTTP 头
> ……这里只留查询参数那条路（给非浏览器客户端/探针脚本用）

即：这条链的设计里，握手阶段**只有 `?token=` 这一条路**能带身份，
而客户端 `stompClient.js` 的 `brokerURL` 是 `` `${proto}://${baseUrl}/ws` `` —— **没带 query**。

③ 补充：诊断期间 message-service(8077) 本来没起。但它**不是原因** ——
把它起来之后 `/ws` 仍然 401；而 `/ws/`、`/ws/info` 在它起来前后都是 101/503 随路由变，
与 401 是两回事（401 发生在网关鉴权阶段，早于路由转发）。

## 影响

- 实时推送在 PC 端**从来就没成功过**（三个角色的消息/通知页都一样）。
- **不是白屏**：`NotificationCenter.loadUnreadNotifications()` 等仍照常拉取历史数据，
  页面显示正常。丢掉的是「已打开的页面立刻收到新消息 + 未读数自动 +1」这半个能力。
- 小程序端 `services/api.js` 另有自己的实时实现，本发现不覆盖它（未验）。

## 未修（本轮只取证，不改）

真修需要**同时**动两处，缺一不可：
1. 网关白名单：`"/ws/**"` 之外**补一条精确的 `"/ws"`**（或把 `replace("**",".*")` 换成
   对 glob 的正确处理，别让 `**` 兼作 `.*` 又受全串匹配约束）；
2. 客户端 `stompClient.js`：`brokerURL` 带上 `?token=${localStorage.getItem('token')}`，
   下游 `WebSocketInterceptor` 的 `getParameter("token")` 已经在读了。

⚠️ 修之前先确认下游读的是不是 token 里的 userId（`JwtStompAuthInterceptor` 装 Principal），
别把"能连上"当成"身份对"。

---

# W49 订正（2026-10-19）

**上面第 ② 条根因和「未修」整节都是错的。** 修好之后整条链全通，`?token=` 从头到尾不需要。

## 订正一：根因只有一层

浏览器 WebSocket API 设不了 Authorization 头 —— 这是**事实**，但它**不是缺陷**。
身份本来就不在握手这一跳装：验票在 message-service 的 `JwtStompAuthInterceptor`，
它读 **STOMP CONNECT 帧**上的 `Authorization` native header，验完给会话装 Principal
（名字 = token 里的 `userId`）。`stompClient.js` 的 `connectHeaders` 正是这么发的。

W49 实测服务端回：

```
[STOMP Debug] >>> CONNECT
Authorization:Bearer eyJhbGciOiJIUzI1NiJ9...
[STOMP Debug] <<< CONNECTED
user-name:27
```

`user-name:27` 就是 Principal 装对了。**握手只需要"能连上"，身份在下一跳装。**

下游 `WebSocketInterceptor.getParameter("token")` 那条路只是留给**非浏览器客户端**
（探针脚本、小程序）的，不是 PC 端的必需项。小程序端 `xcx/src/utils/stompClient.js`
本来也是不带 query 的写法，且一直能跑 —— 反证。

**把 JWT 塞进 URL 反而有害**：会写进访问日志/浏览器历史，对身份没有任何帮助。

## 订正二：修法

不补 `"/ws"` 特例，而是**把尺子修对**（这样以后任何 `/**` 白名单条目都不会再踩）：

`AuthenticationFilter.globMatches(pattern, path)` —— 结尾的 `/**` 解释成「零段或多段」
（`(?:/.*)?`），`*` 不跨 `/`，正则元字符一律转义。`"/ws/**"` 于是同时覆盖 `/ws`、`/ws/`、
`/ws/info`，而 `/wsx`、`/ws-secret` 仍然不放行。

`stompClient.js` 只加了注释说明为什么**不带** `?token=`（净功能无改动）。

## 验证

| 项 | 结果 |
|---|---|
| `AuthenticationFilterTest` | **20/20**（网关全量 29/29） |
| `probe-w49-stomp.mjs`（协议层） | **9/9** —— 含真收到 MESSAGE 载荷 + 身份隔离负向对照 |
| `probe-w49-realtime-page.mjs`（浏览器 DOM 层） | **4/4** —— 握手 101 + 页面真弹出 + 隔离负向对照 |
| 打靶① 订阅地址改回 `/user/{userId}/queue/messages` | B 红、A 仍绿（精确复现"连得上、订阅得上、一条收不到"） |
| 打靶② `globMatches` 退回 `replace("**",".*")` | A/B 双红，握手连响应都没有 |
| 回归 smoke | student 13/13、teacher 12/12、counselor 12/12、admin 15/15 |

两个探针自己的坑也记一下，别下次再踩：
1. `import('/src/utils/stompClient.js').getConnected()` 拿到的是与 App 打包实例**不同的模块实例**，
   恒为 false；第一版还把 false 无条件记成 ✔（恒真断言）。改从 CDP 握手响应码读。
2. `Network.webSocketHandshakeResponseReceived` 事件里**没有 url 字段**（url 只在
   `webSocketCreated` 里），按 url 过滤恒不命中，会误报"根本没发起握手"。要按 `requestId` 映射。
