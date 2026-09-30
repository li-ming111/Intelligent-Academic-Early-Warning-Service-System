/**
 * 角色主题。
 *
 * 用法：页面根节点绑定 :style="roleThemeStyle()"，子树里的 wot 组件与
 * var(--color-primary) 会自然继承。
 *
 * 为什么不直接写 CSS 类：小程序里 page 是全局单例选择器，按角色写
 * `page.role-student { --color-primary: ... }` 是对所有页面同时生效的，
 * 切不开。只能落到根节点上。
 */
import { roleKey } from '@/constants/roles.js'

/**
 * 五个角色的主色与渐变。
 * **与 PC 端 academic-warning-frontend/src/config/theme.js 取同一组值**，
 * 同一个角色在两端必须同色。
 *
 * collegeAdmin 是原先缺的一个：PC 端 theme.js 的 roleMap 没有 '5'，
 * 学院管理员落到 default 紫，与 admin 完全同色。这里显式给足五个。
 */
export const ROLE_THEME = {
  student:      { primary: '#4caf50', weak: '#e8f5e9', gradient: 'linear-gradient(135deg, #4caf50, #81c784)' },
  teacher:      { primary: '#2196f3', weak: '#e3f2fd', gradient: 'linear-gradient(135deg, #2196f3, #64b5f6)' },
  counselor:    { primary: '#2563eb', weak: '#eff6ff', gradient: 'linear-gradient(135deg, #2563eb, #60a5fa)' },
  admin:        { primary: '#667eea', weak: '#f0f2f8', gradient: 'linear-gradient(135deg, #667eea, #764ba2)' },
  collegeAdmin: { primary: '#0ea5e9', weak: '#f0f9ff', gradient: 'linear-gradient(135deg, #0ea5e9, #38bdf8)' }
}

/** 取某角色的主题对象；识别不出返回 null（调用方可用默认值）。 */
export function themeOf(role) {
  return ROLE_THEME[roleKey(role)] || null
}

/**
 * 取某角色的主色**字面量**，识别不出时用 fallback。
 *
 * 为什么需要它：`switch` 是原生组件，不解析 CSS 变量，`:color` 只能收字面量色值，
 * 拿不到 `var(--color-primary)`。四个 profile / settings 页原先各自手抄了一份
 * 五角色色表，与 ROLE_THEME 重复 —— 改色时要记得五处一起改，漏一处就是
 * 「同一个角色在两个页面不同色」。这里改为从 ROLE_THEME 取，色值只有一份出处。
 *
 * 显式传 `role`、不从 storage 读，是为了少一个隐式的初始化顺序依赖。
 * 入参形态交给 roleKey() 归一 —— `'1'`、`1`、`'student'`、`'college_admin'` 都认。
 */
export function primaryOf(role, fallback) {
  const theme = themeOf(role)
  return (theme && theme.primary) || fallback
}

/**
 * 生成可绑到根节点的 style 字符串。
 * 不传 role 时从 storage 读当前登录角色。
 */
export function roleThemeStyle(role) {
  const theme = themeOf(role ?? uni.getStorageSync('role'))
  if (!theme) return ''
  return [
    `--color-primary:${theme.primary}`,
    `--color-primary-weak:${theme.weak}`,
    `--gradient-hero:${theme.gradient}`
  ].join(';')
}
