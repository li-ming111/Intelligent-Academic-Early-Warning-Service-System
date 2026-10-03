/**
 * 获取当前登录用户的ID（数字类型）
 * @returns {number|null} 用户ID，如果未登录则返回null
 */
export const getUserId = () => {
  const userIdStr = localStorage.getItem('userId')
  return userIdStr ? parseInt(userIdStr) : null
}

/**
 * 当前登录教师用于 `/teachers/**` 的 ID。
 *
 * 2026-10-03（第四十二轮 W8.2）：**不再读 `localStorage.teacherId`**。
 * 那个键全仓**没有任何可信写入方** —— 唯一的写方是 `TeacherScores.vue` 里一句
 * 兜底 `setItem('teacherId','9')`。一旦被写成 `'9'`，8 个教师端页面都会去读
 * **9 号用户**的数据（`TeacherSettings.vue` 甚至会去改 9 号用户的邮箱/电话）。
 *
 * 实测（活库）确认这些接口收的都是 **`users.id`**：`classes.teacher_id` = 18/19/22/29、
 * `courses.teacher_id` = 18/19/22/28/29、`feedback` / `score_audit_log` /
 * `teacher_class_application.teacher_id` = 29 —— 全是 `users.role=2` 的 id，
 * 与 `teacher_profile.id`(1/2/3/5/7/8) 不相交；`/teachers/dashboard/{userId}`
 * 更是直接 `getByUserId()` 查档案。所以这里取 `getUserId()`。
 *
 * ⚠️ 别再加 `|| '9'` / `|| '29'` 这类兜底：那不是"演示方便"，是让未登录或异常
 * 状态下**静默显示另一个人的数据**（与 W4 删掉的 `'计科2306班'` 同类）。
 *
 * @returns {number|null}
 */
export const getTeacherId = () => getUserId()

/**
 * 获取当前登录用户名
 * @returns {string|null} 用户名
 */
export const getUsername = () => {
  return localStorage.getItem('username')
}

/**
 * 获取当前登录用户的角色
 * @returns {string|null} 用户角色
 */
export const getUserRole = () => {
  return localStorage.getItem('role')
}

/**
 * 检查用户是否已登录
 * @returns {boolean} 是否已登录
 */
export const isUserLoggedIn = () => {
  return !!localStorage.getItem('token')
}
