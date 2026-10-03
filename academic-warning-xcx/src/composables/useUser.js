/**
 * 登录态读取。登录时写入的 storage 键由 auth/login 页负责，这里只读。
 *
 * 两个反复踩到的坑，都在这里统一处理：
 *
 * 1. **姓名键名**：登录成功只写 `userName`（真名）。历史代码有的地方读 `name`、
 *    有的读 `userName`，读错了就显示学号或兜底文案。这里只认 `userName`，
 *    并加纯数字守卫 —— 学号是纯数字，误把学号当姓名时应判为无效。
 *
 * 2. **两套 ID 空间**：`userId` 是 `users.id`，`teacherId` / `counselorId` 是各
 *    自 profile 表的 id，两者不相交。学院管理员端的**所有**接口第一个路径参数
 *    都是 `users.id`，而 `/student-detail/{uid}/{profileId}` 的第二个参数才是
 *    profile id。混用不会报错，只会静默查空，所以两个必须分开取。
 */
import { ref } from 'vue'

const user = ref(null)

/** 从 storage 读一次，构造当前会话快照。未登录时各字段为空。 */
function read() {
  return {
    token: uni.getStorageSync('token') || '',
    userId: uni.getStorageSync('userId') || '',
    username: uni.getStorageSync('username') || '',
    userName: uni.getStorageSync('userName') || '',
    role: uni.getStorageSync('role') || '',
    // profile 层 id，仅教师/辅导员有
    teacherId: uni.getStorageSync('teacherId') || '',
    counselorId: uni.getStorageSync('counselorId') || ''
  }
}

/** 取姓名，纯数字（学号）视为无效，返回空串。 */
export function displayName() {
  const name = String(uni.getStorageSync('userName') || '').trim()
  if (!name) return ''
  if (/^\d+$/.test(name)) return ''
  return name
}

export function getToken() {
  return uni.getStorageSync('token') || ''
}

/** users.id。学院管理员端所有接口的第一个参数用它。 */
export function getUserId() {
  return uni.getStorageSync('userId') || ''
}

export function getRole() {
  return uni.getStorageSync('role') || ''
}

/** 是否已登录：只认 token。 */
export function isLoggedIn() {
  return !!getToken()
}

/**
 * 启动时调用。storage 是同步 API，read() 本身不需要 await，
 * 这里显式建一次快照，供 useUser() 的响应式引用使用。
 *
 * 之所以不在这里做"没登录就跳登录页"：App.onLaunch 触发时首个页面
 * 的 setup 可能已经跑过了，此时跳转会被覆盖。跳转交给各页面的守卫或
 * request 层的 401 处理。
 */
export function restoreSession() {
  user.value = read()
  return user.value
}

/** 「记住我」到期时间戳（毫秒）的 storage 键。 */
const SESSION_EXPIRE_KEY = 'sessionExpireAt'

/** 清掉登录态。退出登录与 401 都走这里，避免两处各清一半。 */
export function logout() {
  const keys = ['token', 'userId', 'username', 'userName', 'role', 'teacherId', 'counselorId']
  keys.forEach(k => uni.removeStorageSync(k))
  // 「记住我」的到期时间也算登录态，一起清 —— 否则退出后它还在，
  // 下次进登录页会拿它去判断"是不是还在记住期内"。
  uni.removeStorageSync(SESSION_EXPIRE_KEY)
  user.value = null
}

// ==================== 「记住我 30 天」 ====================
//
// 2026-10-02（W5.4）。改之前这个勾选**只翻转一个 ref**，登录逻辑从不读它，
// 而登录页 onMounted 又会把所有登录态清空 ⇒ 勾不勾完全一样、永远记不住。
//
// 现在的语义（与 PC 端 Login.vue 的 `rememberMe` 一致，但那边只存了个标记）：
//   - 勾了：登录成功时写一个**到期时间**，之后进登录页若还在期内就直接回各自首页；
//   - 没勾：不写到期时间，进登录页照旧清空登录态、要求重新登录。

/** 记住 30 天。 */
export function rememberSession(days = 30) {
  uni.setStorageSync(SESSION_EXPIRE_KEY, Date.now() + days * 24 * 60 * 60 * 1000)
}

/**
 * 是否处于「记住我」的有效期内。
 *
 * 两个条件都要满足：**有 token** 且**到期时间还没到**。
 * 只看到期时间是不够的 —— 退出登录（或 401）会清掉 token，
 * 那时即便时间戳还在，也必须重新登录。
 */
export function stillRemembered() {
  const at = Number(uni.getStorageSync(SESSION_EXPIRE_KEY) || 0)
  return !!getToken() && at > Date.now()
}

/** 组合式用法：const { token, userId } = useUser() */
export function useUser() {
  if (!user.value) user.value = read()
  return {
    user,
    token: getToken(),
    userId: getUserId(),
    role: getRole(),
    userName: displayName()
  }
}
