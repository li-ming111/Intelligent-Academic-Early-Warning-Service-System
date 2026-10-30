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
 * 五个角色的主色、浅底与渐变。
 * 与 uni.scss 的 $role-* / $role-*-deep / $role-*-weak **同一组值**（两处
 * 声明：uni.scss 给编译期 SCSS 用，这里给运行期 JS 用——switch 等原生
 * 组件只吃字面量色值）。改色必须两处一起改。
 *
 * 2026-10-14 finesse 改版：色相五向拉开（绿/橙/靛/紫/青）。
 * 2026-10-19 教师回蓝 #2196f3（对齐 PC 端 theme.js 与论文截图，两端同色）；
 * 辅导员已换 #4349b8 靛，与教师亮蓝可分，不会复现当年
 * “教师 #2196f3 与辅导员 #2563eb 不可分”的问题。
 * gradient 从 135° 双色斜渐变改为**纵向深→主微渐变**，视觉近似平涂色块。
 */
export const ROLE_THEME = {
  student:      { primary: '#217a46', deep: '#185c34', weak: '#e7f4ec', gradient: 'linear-gradient(180deg, #185c34 0%, #217a46 100%)' },
  teacher:      { primary: '#2196f3', deep: '#1976d2', weak: '#e3f2fd', gradient: 'linear-gradient(180deg, #1976d2 0%, #2196f3 100%)' },
  counselor:    { primary: '#4349b8', deep: '#353a94', weak: '#ebeefa', gradient: 'linear-gradient(180deg, #353a94 0%, #4349b8 100%)' },
  admin:        { primary: '#6b3fa0', deep: '#563281', weak: '#f0eafb', gradient: 'linear-gradient(180deg, #563281 0%, #6b3fa0 100%)' },
  collegeAdmin: { primary: '#0f7b8a', deep: '#0b606c', weak: '#e4f3f6', gradient: 'linear-gradient(180deg, #0b606c 0%, #0f7b8a 100%)' }
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
