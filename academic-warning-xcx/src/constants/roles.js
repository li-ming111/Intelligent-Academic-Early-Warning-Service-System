/**
 * 角色字典 —— 全工程角色相关判断的唯一来源。
 *
 * 后端 users.role 是数字 1..5。历史代码里同一个角色在不同页面写成 '1'、1、
 * 'student'、'college_admin' 四种形态，于是"判断角色"的代码各写各的。
 * 统一到这里：一律用 roleKey() 取字符串主键，再跟 ROLES 的 key 比。
 */

/** 角色 → 元信息。home 是登录后 reLaunch 的目标，也是「回首页」的落点。 */
export const ROLES = {
  1: { key: 'student',      name: '学生',       home: '/pages/student/dashboard' },
  2: { key: 'teacher',      name: '教师',       home: '/pages/teacher/dashboard' },
  3: { key: 'counselor',    name: '辅导员',     home: '/pages/counselor/dashboard' },
  4: { key: 'admin',        name: '系统管理员', home: '/pages/admin/dashboard' },
  5: { key: 'collegeAdmin', name: '学院管理员', home: '/pages/college-admin/dashboard' }
}

/** 数字 role 归一化为字符串主键。无法识别返回 ''。 */
export function roleKey(role) {
  if (role === null || role === undefined) return ''
  const s = String(role).trim()
  if (ROLES[s]) return ROLES[s].key
  // 兼容后端可能返回的字符串形态
  const alias = {
    student: 'student',
    teacher: 'teacher',
    counselor: 'counselor',
    admin: 'admin',
    college_admin: 'collegeAdmin',
    collegeAdmin: 'collegeAdmin'
  }
  return alias[s] || ''
}

/** 该角色的首页路径。识别不出时落到学生首页（与旧登录页兜底一致）。 */
export function homeFor(role) {
  const key = roleKey(role)
  const hit = Object.values(ROLES).find(r => r.key === key)
  return hit ? hit.home : ROLES[1].home
}

/** 角色显示名。识别不出返回空串，调用方自己决定兜底文案。 */
export function roleName(role) {
  const key = roleKey(role)
  const hit = Object.values(ROLES).find(r => r.key === key)
  return hit ? hit.name : ''
}
