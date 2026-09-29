/**
 * 成绩展示口径（小程序端）。与 Web 端 `academic-warning-frontend/src/utils/score.js`、
 * 后端 `academic-common/.../constant/ScoreCaliber.java` 一一对应，三处改动必须同步。
 *
 * 根源是 JS 的类型强制转换：`null < 60` 为 true（null 转成 0），`Number(null) === 0`。
 * 于是"期末成绩未录入"的行在小程序里被渲染成红色的 0 分，和真正挂科在视觉上完全一样。
 * 结论：**只要期末成绩缺失，任何算术或大小比较都是错的**，必须先过 isPendingValue()。
 */

/** 期末成绩未录入时的展示文案，与后端 ScoreCaliber.PENDING_TEXT 一致。 */
export const PENDING_TEXT = '待录入'

/** 及格线。低于此值即为挂科。 */
export const PASS_LINE = 60

/**
 * 单个分值是否"未录入"。入参是值本身而不是整行 —— 表格里通常只拿到某一列。
 * @param {*} value
 * @returns {boolean}
 */
export function isPendingValue(value) {
  return value === null || value === undefined || String(value).trim() === ''
}

/**
 * 整行是否"待录入"。判定口径与后端一致：`final_score IS NULL` 即为待录入。
 *
 * 兼容三种字段形态：`finalScore`（后端直接返回）、`final_score`（下划线命名），
 * 以及完全没给期末分的旧接口载荷（按"没有这个信息"处理，不判定为待录入，
 * 否则整份成绩单会被清空）。
 *
 * @param {object} row 成绩行
 * @returns {boolean}
 */
export function isPending(row) {
  if (!row) return true
  if ('finalScore' in row) return row.finalScore == null
  if ('final_score' in row) return row.final_score == null
  return false
}

/**
 * 把分值转成数字；无法识别时返回 null（而不是 0）。
 * @param {*} value
 * @returns {number|null}
 */
export function toNumber(value) {
  if (value === null || value === undefined) return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  const text = String(value).trim()
  if (text === '') return null
  const n = Number(text)
  return Number.isFinite(n) ? n : null
}

/** 分数展示文本：未录入返回"待录入"，正常返回数字文本。 */
export function scoreText(value) {
  const n = toNumber(value)
  if (n === null) return PENDING_TEXT
  return n >= 90 ? '优秀' : String(n)
}

/** 分数样式类：未录入是 pending（灰），不能落进 fail（红）。 */
export function scoreClass(value) {
  const n = toNumber(value)
  if (n === null) return 'pending'
  if (n >= 90) return 'excellent'
  if (n >= 80) return 'good'
  if (n >= 70) return 'medium'
  if (n >= 60) return 'pass'
  return 'fail'
}

/** 未录入时不给绩点。返回 '--' 而不是 '0' —— '0' 会被读成"绩点为 0"。 */
export const GPA_PENDING_TEXT = '--'

/** 由分数折算绩点，与后端 GPA_EXPR 的分段一致。未录入返回 GPA_PENDING_TEXT。 */
export function calcGpa(value) {
  const n = toNumber(value)
  if (n === null) return GPA_PENDING_TEXT
  if (n >= 90) return '4.0'
  if (n >= 85) return '3.7'
  if (n >= 82) return '3.3'
  if (n >= 78) return '3.0'
  if (n >= 75) return '2.7'
  if (n >= 72) return '2.3'
  if (n >= 68) return '2.0'
  if (n >= 64) return '1.5'
  if (n >= 60) return '1.0'
  return '0'
}

/**
 * 预警等级归一化。后端返回数字 1/2/3，而历史代码比的是 'high'/'medium'/'low' 字符串，
 * 两边永远不相等 —— 所有预警都落到 else 分支显示成"轻度"。统一成数字，调用方只跟数字比。
 * @returns {number} 1/2/3；无法识别为 0
 */
export function warningLevel(value) {
  if (value === null || value === undefined) return 0
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0
  const text = String(value).trim()
  if (/^\d+$/.test(text)) return Number(text)
  if (text.includes('严重') || text.includes('高')) return 3
  if (text.includes('中度') || text.includes('中等') || text.includes('中')) return 2
  if (text.includes('轻度') || text.includes('轻微') || text.includes('低')) return 1
  return 0
}

/** 等级对应的样式类（小程序里用 danger/medium/low 三个类）。 */
export function warningLevelKey(value) {
  const level = warningLevel(value)
  if (level >= 3) return 'danger'
  if (level === 2) return 'medium'
  return 'low'
}

/** 等级文案。全端统一"轻度/中度/严重"，不再出现"中等/轻微"等变体。 */
export function warningLevelText(value) {
  const level = warningLevel(value)
  if (level >= 3) return '严重'
  if (level === 2) return '中度'
  if (level === 1) return '轻度'
  return '未知'
}
