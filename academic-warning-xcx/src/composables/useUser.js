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

/** 清掉登录态。退出登录与 401 都走这里，避免两处各清一半。 */
export function logout() {
  const keys = ['token', 'userId', 'username', 'userName', 'role', 'teacherId', 'counselorId']
  keys.forEach(k => uni.removeStorageSync(k))
  user.value = null
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
