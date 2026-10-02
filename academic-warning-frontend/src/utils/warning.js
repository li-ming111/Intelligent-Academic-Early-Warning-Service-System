/**
 * 预警处理状态 —— PC 端的**唯一一处**口径（2026-10-02，W1.6）。
 *
 * 状态是**三态**：`0 待处理 / 1 已处理 / 2 已解除`
 * （「已解除」由 warning-service 的定时扫描按成绩口径现算后写回）。
 *
 * 后端权威定义在 `com.academic.common.constant.WarningStatus`，
 * 小程序端在 `academic-warning-xcx/src/utils/warning.js` —— **三处必须同改**。
 * 本轮修的就是"三处各写一遍、然后在 status=2 上给出相反答案"：
 *   · teacher-service 翻成「已处理」
 *   · college-admin 翻成「待处理」
 *   · PC 各页把 `status === 1 ? '已处理' : '待处理'` 抄了六七遍，2 全落进 else
 *
 * ⚠️ 页面里**不要**再自己写 `status === 1 ? ... : ...`。历史 bug 就是那么来的：
 * `CounselorWarnings.vue` 是唯一一处写对三态的，其余页面各自两态，同一条预警在
 * 辅导员页显示"已解除"、在管理员页显示"待处理"。
 */

/** 与后端 `WarningStatus` 一一对应。 */
export const WARNING_STATUS = { PENDING: 0, HANDLED: 1, DISPELLED: 2 }

/** 状态 → 中文。与小程序 `constants/dict.js` 的 `WARNING_STATUS_TEXT` 同表。 */
export const WARNING_STATUS_TEXT = {
  0: '待处理',
  1: '已处理',
  2: '已解除'
}

/**
 * 归一化成 `0 | 1 | 2`。认不出按**待处理** ——
 * 宁可多显示一条待处理（有人会去看），也别让一条预警凭空"消失"。
 *
 * 能吃下 int、数字字符串、以及 teacher-service 那条路回的**中文文案**。
 *
 * ⚠️ 中文分支必须先判「解除」再判「已」：`'已解除'.includes('已')` 是 true，
 * 顺序写反就会把"已解除"归成"已处理"。
 */
export function warningStatus(value) {
  if (value === null || value === undefined) return WARNING_STATUS.PENDING
  if (typeof value === 'number') {
    return value === WARNING_STATUS.HANDLED || value === WARNING_STATUS.DISPELLED
      ? value : WARNING_STATUS.PENDING
  }
  const text = String(value).trim()
  if (text === '') return WARNING_STATUS.PENDING
  if (/^\d+$/.test(text)) {
    const n = Number(text)
    return n === WARNING_STATUS.HANDLED || n === WARNING_STATUS.DISPELLED ? n : WARNING_STATUS.PENDING
  }
  if (text.includes('解除') || /dispelled|released|resolved/i.test(text)) {
    return WARNING_STATUS.DISPELLED
  }
  if (text.includes('已') || /handled|processed|done|closed|finished/i.test(text)) {
    return WARNING_STATUS.HANDLED
  }
  return WARNING_STATUS.PENDING
}

/** 状态文案。 */
export function warningStatusText(value) {
  return WARNING_STATUS_TEXT[warningStatus(value)] ?? '未知'
}

/** 状态样式键：`pending` / `handled` / `dispelled`。 */
export function warningStatusKey(value) {
  const s = warningStatus(value)
  if (s === WARNING_STATUS.HANDLED) return 'handled'
  if (s === WARNING_STATUS.DISPELLED) return 'dispelled'
  return 'pending'
}

/**
 * 状态 → `el-tag` 的 `type`。
 *
 * ⚠️ 「已解除」用 `info`（中性灰）而不是 `success`：`success` 是"人处理完了"，
 * 已解除是"分数上来了、自动写的" —— 混成一个颜色，页面上就分不出哪条真被人处理过。
 */
export function warningStatusTagType(value) {
  const key = warningStatusKey(value)
  if (key === 'handled') return 'success'
  if (key === 'dispelled') return 'info'
  return 'warning'
}

/** 是否待处理（唯一该出现"处理"按钮的状态）。 */
export function isPendingWarning(value) {
  return warningStatus(value) === WARNING_STATUS.PENDING
}
