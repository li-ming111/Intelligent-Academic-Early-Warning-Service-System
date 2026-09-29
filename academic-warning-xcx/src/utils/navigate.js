/**
 * 统一的跳转入口。
 *
 * 小程序里 tabBar 页只能用 switchTab、非 tabBar 页只能用 navigateTo；
 * 用错**不会报错**，只会静默什么也不发生（历史代码里的自定义 tab-bar 一律
 * 用 redirectTo，指到 tabBar 页时就静默失败，点了没反应也查不出原因）。
 * 所以跳转一律走这里，由它按目标页决定用哪个 API。
 */

/**
 * tabBar 页清单。
 * **必须与 pages.json 的 tabBar.list 保持一致** —— 改 pages.json 时回来同步这里。
 */
const TAB_PAGES = [
  '/pages/student/dashboard',
  '/pages/student/scores',
  '/pages/student/warnings',
  '/pages/student/profile'
]

/** 去掉 query 后比较，'/pages/x/y?a=1' 与 '/pages/x/y' 视为同一页。 */
function barePath(url) {
  return String(url || '').split('?')[0]
}

export function isTabPage(url) {
  return TAB_PAGES.includes(barePath(url))
}

/**
 * 跳转（保留当前页，可返回）。
 * @param {string} url 以 / 开头的绝对路径
 */
export function go(url) {
  if (!url) return
  if (isTabPage(url)) {
    uni.switchTab({ url: barePath(url) })
  } else {
    uni.navigateTo({ url })
  }
}

/** 跳转并替换当前页（不能返回）。用于登录成功、详情替换列表等场景。 */
export function replace(url) {
  if (!url) return
  if (isTabPage(url)) {
    uni.switchTab({ url: barePath(url) })
  } else {
    uni.redirectTo({ url })
  }
}

/** 关闭所有页面并打开。用于退出登录、切换角色。 */
export function reset(url) {
  if (!url) return
  uni.reLaunch({ url: barePath(url) })
}

export function back(delta = 1) {
  const pages = getCurrentPages()
  if (pages.length > delta) {
    uni.navigateBack({ delta })
  } else {
    // 没有上一页可退（比如从分享直接进来的），回首页而不是卡死
    go('/pages/auth/login')
  }
}
