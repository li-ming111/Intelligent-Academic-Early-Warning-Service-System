/**
 * 图标几何 —— 全工程**唯一**的图标路径来源。
 *
 * 放在 `constants/` 而不是组件内部，是因为有两个消费方：
 *   1. `components/app-icon/app-icon.vue`  —— 运行时渲染成 SVG data URI；
 *   2. `tools/gen-tab-icons.mjs`           —— 构建前栅格化成 PNG 给原生 tabBar 用。
 *
 * 原生 tabBar 只吃图片文件（png/jpg），吃不了 data URI，所以底栏那 8 个图标
 * 必须落成真实文件。如果路径表写在 .vue 里，生成脚本就得把路径抄一遍 ——
 * 抄完两边就开始漂移，改了组件忘了改底栏。所以抽到这里，一处改、两处生效。
 *
 * 约定：24x24 视口、纯描边（fill=none）、描边粗细由调用方给。
 */

export const ICON_VIEWBOX = 24
export const ICON_WEIGHT = 1.8

export const ICON_PATHS = {
  /* 导航 */
  home: '<path d="M3.2 11.2 12 3.6l8.8 7.6"/><path d="M5.7 9.6V19a1.4 1.4 0 0 0 1.4 1.4h9.8a1.4 1.4 0 0 0 1.4-1.4V9.6"/><path d="M9.9 20.4v-5.1h4.2v5.1"/>',
  chart: '<path d="M3.4 20.4h17.2"/><path d="M6.4 20.4V12"/><path d="M11.2 20.4V4.6"/><path d="M16 20.4v-5.6"/>',
  users: '<circle cx="9.2" cy="8" r="3.4"/><path d="M2.9 20.2a6.3 6.3 0 0 1 12.6 0"/><path d="M16.3 5.2a3.4 3.4 0 0 1 0 6.6"/><path d="M17.7 14.4a6.3 6.3 0 0 1 3.4 5.8"/>',
  user: '<circle cx="12" cy="8" r="3.9"/><path d="M4.6 20.4a7.4 7.4 0 0 1 14.8 0"/>',
  building: '<path d="M4.6 20.4V5.2a1.6 1.6 0 0 1 1.6-1.6h6.6a1.6 1.6 0 0 1 1.6 1.6v15.2"/><path d="M14.4 9.8h3.4a1.6 1.6 0 0 1 1.6 1.6v9"/><path d="M2.6 20.4h18.8"/><path d="M7.8 7.4h3.2"/><path d="M7.8 11.2h3.2"/><path d="M7.8 15h3.2"/>',

  /* 业务 */
  book: '<path d="M4.4 5.2a2 2 0 0 1 2-2H20v14.2H6.4a2 2 0 0 0-2 2Z"/><path d="M4.4 19.4a2 2 0 0 0 2 2H20v-4.2"/>',
  edit: '<path d="M4.6 19.4h3.7L19.4 8.3a2.2 2.2 0 0 0-3.1-3.1L5.2 16.3Z"/><path d="M14.9 6.8l2.9 2.9"/>',
  alert: '<path d="M10.7 4.3 3 17.4a1.6 1.6 0 0 0 1.4 2.4h15.2a1.6 1.6 0 0 0 1.4-2.4L13.3 4.3a1.5 1.5 0 0 0-2.6 0Z"/><path d="M12 9.6v4"/><path d="M12 16.7h.01"/>',
  'alert-circle': '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.8v4.6"/><path d="M12 15.9h.01"/>',
  target: '<circle cx="12" cy="12" r="8.6"/><circle cx="12" cy="12" r="4"/><path d="M12 12h.01"/>',
  doc: '<path d="M6.4 3.4h7l4.6 4.6v12.6a1.4 1.4 0 0 1-1.4 1.4H6.4A1.4 1.4 0 0 1 5 20.6V4.8a1.4 1.4 0 0 1 1.4-1.4Z"/><path d="M13.4 3.4V8h4.6"/>',
  calendar: '<rect x="3.6" y="5.4" width="16.8" height="15" rx="1.8"/><path d="M3.6 10h16.8"/><path d="M8.4 3.4v4"/><path d="M15.6 3.4v4"/>',
  clock: '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.2V12l3.2 1.9"/>',
  bell: '<path d="M18 9.4a6 6 0 0 0-12 0c0 5.2-2 6.8-2 6.8h16s-2-1.6-2-6.8Z"/><path d="M13.7 19.4a2 2 0 0 1-3.4 0"/>',
  mail: '<rect x="3" y="5.4" width="18" height="13.2" rx="1.8"/><path d="M3.4 6.6 12 12.8l8.6-6.2"/>',

  /* 操作 */
  search: '<circle cx="10.8" cy="10.8" r="6.8"/><path d="M15.8 15.8 20.4 20.4"/>',
  filter: '<path d="M3.4 5.6h17.2"/><path d="M6.6 12h10.8"/><path d="M10 18.4h4"/>',
  chevron: '<path d="M9.4 5.6 15.8 12l-6.4 6.4"/>',
  'chevron-down': '<path d="M5.6 9.4 12 15.8l6.4-6.4"/>',
  check: '<path d="M4.8 12.6 9.8 17.6 19.2 6.8"/>',
  close: '<path d="M6.2 6.2 17.8 17.8"/><path d="M17.8 6.2 6.2 17.8"/>',
  plus: '<path d="M12 4.6v14.8"/><path d="M4.6 12h14.8"/>',
  refresh: '<path d="M20.2 11.4A8.4 8.4 0 1 0 18 17"/><path d="M20.4 5.6v5.8h-5.8"/>',
  settings:
    '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>',
  logout: '<path d="M9.4 20.4H5.8a1.8 1.8 0 0 1-1.8-1.8V5.4a1.8 1.8 0 0 1 1.8-1.8h3.6"/><path d="M15.6 16.4 20 12l-4.4-4.4"/><path d="M20 12H9.4"/>',
  lock: '<rect x="4.6" y="10.2" width="14.8" height="10.2" rx="1.8"/><path d="M8.2 10.2V7.4a3.8 3.8 0 0 1 7.6 0v2.8"/>',
  phone: '<rect x="6.2" y="2.6" width="11.6" height="18.8" rx="2.2"/><path d="M10.6 18.6h2.8"/>',
  download: '<path d="M12 3.6v11.2"/><path d="M7.6 10.6 12 15l4.4-4.4"/><path d="M4.4 19.4h15.2"/>',
  upload: '<path d="M12 15.4V4.2"/><path d="M7.6 8.6 12 4.2l4.4 4.4"/><path d="M4.4 19.4h15.2"/>',
  trend: '<path d="M3.4 17.4 9 11.8l3.6 3.6L20.6 7.4"/><path d="M15.4 7.4h5.2v5.2"/>',
  empty: '<rect x="3.2" y="6.4" width="17.6" height="12.4" rx="1.8"/><path d="M3.2 12.6h4.4l1.6 2.6h5.6l1.6-2.6h4.4"/>'
}

/**
 * 拼一段 SVG 源码字符串（不含 data URI 编码）。
 * @param {string} name ICON_PATHS 的键
 * @param {string} color 描边色，**必须是字面值**（CSS 变量在 SVG 里不解析）
 * @param {number} [weight] 描边粗细
 * @returns {string} 拿不到路径时返回空串
 */
export function iconSvg(name, color, weight = ICON_WEIGHT) {
  const body = ICON_PATHS[name]
  if (!body) return ''
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${ICON_VIEWBOX} ${ICON_VIEWBOX}" fill="none" ` +
    `stroke="${color}" stroke-width="${weight}" stroke-linecap="round" stroke-linejoin="round">` +
    `${body}</svg>`
  )
}
