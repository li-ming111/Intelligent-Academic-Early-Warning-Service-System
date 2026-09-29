/**
 * 成绩展示口径（前端唯一权威实现）。
 *
 * 与后端 `ScoreCaliber` 一一对应，改动必须两边同步。
 *
 * 核心问题是 JS 的类型强制转换：
 *   - `null < 60` 在 JS 中为 **true**（null 被转成 0），而在 Java 中为 false。
 *     于是"期末成绩未录入"的行在前端被渲染成"挂科"。
 *   - `Number(null) === 0`、`Number('') === 0`，同理把缺失值变成 0 分。
 *   - `'待录入' < 60` 为 false，但 `'待录入' > 0` 也为 false —— 字符串比较完全不可靠。
 *
 * 结论：**只要一行的期末成绩缺失，任何算术或大小比较都是错的**。
 * 所有判断必须先过 `isPending()`，禁止在页面里直接写 `score < 60`。
 */

/** 期末成绩未录入时的展示文案，与后端 ScoreCaliber.PENDING_TEXT 一致。 */
export const PENDING_TEXT = '待录入'

/** 及格线。低于此值即为挂科。 */
export const PASS_LINE = 60

/**
 * 该行成绩是否"待录入"（期末成绩尚未录入）。
 *
 * 兼容三种字段形态：`finalScore`（后端直接返回）、`final_score`（下划线命名）、
 * 以及后端只给了 `scoreTotal` 而没有期末分的旧接口。
 *
 * @param {object} row 成绩行
 * @returns {boolean}
 */
export function isPending(row) {
  if (!row) return true
  // `== null` 同时覆盖 null 与 undefined，与后端 containsKey + get() == null 等价
  if ('finalScore' in row) return row.finalScore == null
  if ('final_score' in row) return row.final_score == null
  // 字段完全缺失（旧接口载荷）：按"没有这个信息"处理，不判定为待录入。
  // 反过来当成待录入会让整份成绩单被清空，代价远大于多算一门。
  return false
}

/**
 * 单个分值是否"未录入"。与 {@link isPending} 的区别是入参只给一个值（通常来自
 * `row.finalScore`），而不是整行 —— 表格里只拿到某一列时用它。
 *
 * 注意 `isPending(row.finalScore)` 是**错的**：值为数字时 `'finalScore' in 56`
 * 会直接抛 TypeError。
 *
 * @param {*} value 分值（可能为 null / undefined / ''）
 * @returns {boolean}
 */
export function isPendingValue(value) {
  return value === null || value === undefined || String(value).trim() === ''
}

/**
 * 把分值转成数字；无法识别时返回 null（而不是 0）。
 * 返回 0 会让"未录入"变成"考了 0 分"。
 */
export function toNumber(value) {
  if (value === null || value === undefined) return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  const text = String(value).trim()
  if (text === '') return null
  const n = Number(text)
  return Number.isFinite(n) ? n : null
}

/**
 * 分数展示文本。待录入返回 {@link PENDING_TEXT}，正常返回去掉多余小数位的数字。
 */
export function scoreText(row) {
  if (isPending(row)) return PENDING_TEXT
  const n = toNumber(row.scoreTotal ?? row.totalScore ?? row.score)
  if (n === null) return PENDING_TEXT
  return Number.isInteger(n) ? String(n) : String(n)
}

/**
 * 成绩状态，用于着色与标记。
 *
 * @returns {'pending'|'fail'|'attention'|'pass'} 待录入 / 挂科 / 临界 / 通过
 */
export function scoreState(row) {
  if (isPending(row)) return 'pending'
  const n = toNumber(row.scoreTotal ?? row.totalScore ?? row.score)
  if (n === null) return 'pending'
  if (n < PASS_LINE) return 'fail'
  if (n < 70) return 'attention'
  return 'pass'
}

/** 状态对应的 Element Plus 标签类型。待录入用灰色（info），不与被判挂科的红色混淆。 */
export function scoreTagType(row) {
  const state = scoreState(row)
  if (state === 'fail') return 'danger'
  if (state === 'attention') return 'warning'
  if (state === 'pending') return 'info'
  return 'success'
}

/**
 * 预警等级归一化。
 *
 * 后端返回的是数字（1/2/3），而历史代码里写的是中文字符串（'轻度'/'中度'/'严重'），
 * 两者直接比较恒为 false —— `Warnings.vue` 的三个统计数字因此永远是 0。
 * 这里统一成数字，调用方只跟数字比。
 *
 * @returns {number} 1 / 2 / 3；无法识别时为 0
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

/** 等级文案。全站统一"轻度/中度/严重"，不再出现"中等/轻微"等变体。 */
export function warningLevelText(value) {
  const level = warningLevel(value)
  if (level >= 3) return '严重'
  if (level === 2) return '中度'
  if (level === 1) return '轻度'
  return '未知'
}

/** 等级对应的标签类型。 */
export function warningLevelTagType(value) {
  const level = warningLevel(value)
  if (level >= 3) return 'danger'
  if (level === 2) return 'warning'
  if (level === 1) return 'info'
  return 'info'
}
