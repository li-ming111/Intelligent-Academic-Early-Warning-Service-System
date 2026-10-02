/**
 * 订阅「我自己的」实时消息（W3.2，2026-10-02）。
 *
 * ⭐ **订阅地址是 `/user/queue/messages`，不是 `/user/{userId}/queue/messages`。**
 * 这一条是实测出来的，不是猜的（2026-10-02，起 message-service 单测整条链）：
 *
 * - 服务端推送走 `convertAndSendToUser(userId, "/queue/messages", …)`，Spring 把它
 *   解析成 `/queue/messages-user{sessionId}`（按 `SimpUserRegistry` 查该用户的会话）。
 * - 客户端**订阅**时，`DefaultUserDestinationResolver.parseSubscriptionMessage` 只把
 *   `/user` 这一段前缀切掉再拼 `-user{sessionId}`。所以：
 *     · 订阅 `/user/queue/messages`      → `/queue/messages-user{sessionId}` ✅ 与推送一致
 *     · 订阅 `/user/{userId}/queue/messages` → `/{userId}/queue/messages-user{sessionId}` ❌
 *   写成后者时**连得上、订阅得上、服务端日志还写着「推送成功」，就是一条都收不到**。
 *   实测日志（`UserDestinationMessageHandler` TRACE）：
 *     `Translated /user/56/queue/messages -> [/56/queue/messages-user6c9a68d2-…]`（订阅）
 *     `Translated /user/56/queue/messages -> [/queue/messages-user6c9a68d2-…]`（推送）
 *   两个目的地不一样 ⇒ 永远匹配不上。
 * - 用户身份由**会话 Principal** 决定（服务端 `JwtStompAuthInterceptor` 在 STOMP
 *   CONNECT 帧上装，名字 = 令牌里的 `userId` = `users.id`），所以订阅地址里
 *   **不需要也不应该**再写一遍 userId。
 *
 * 另外两个"收不到也不报错"的原因：
 *   ① 开发态 vite 没代理 `/ws`（见 `vite.config.js`，已补）；
 *   ② STOMP CONNECT 帧没带 `Authorization`（见 `stompClient.js` 的 connectHeaders）。
 */
import stompClient from '@/utils/stompClient'

/** 本人消息目的地（写死即可，身份由会话 Principal 决定）；未登录返回 null。 */
export function currentUserTopic() {
  const userId = localStorage.getItem('userId')
  return userId ? '/user/queue/messages' : null
}

/**
 * 订阅自己的实时消息。`onMessage(payload)` 收到的是**已 JSON.parse 的对象**。
 * 连接不上时**不抛**（返回 null），页面照常显示已加载的历史数据。
 */
export async function subscribeMyMessages(onMessage) {
  const topic = currentUserTopic()
  if (!topic) return null
  try {
    await stompClient.subscribe(topic, onMessage)
    return topic
  } catch (error) {
    // 实时通道是加速器，不是正确性依赖：连不上就退化成"手动刷新"
    console.warn('[realtime] 订阅失败，本次会话不接收实时推送:', error?.message || error)
    return null
  }
}

/** 退订。页面卸载时调用，避免同一个 topic 被重复订阅。 */
export function unsubscribeMyMessages() {
  const topic = currentUserTopic()
  if (!topic) return
  try {
    stompClient.unsubscribe(topic)
  } catch (error) {
    console.warn('[realtime] 退订失败:', error?.message || error)
  }
}
