/**
 * 成绩展示口径（小程序端）。
 *
 * **分两层，别搞混：**
 *
 * 1. **判定口径层**（`PENDING_TEXT` / `PASS_LINE` / `isPending` / `isPendingValue` /
 *    `toNumber` / `warningLevel` / `warningLevelText`）——
 *    这是"什么算没录入、什么算挂科、几级预警"的业务定义，
 *    **必须与后端 `academic-common/.../constant/ScoreCaliber.java` 逐字一致**，
 *    Web 端 `academic-warning-frontend/src/utils/score.js` 也保持同一份。
 *    改这里之前先去改后端，再同步两端。
 *
 * 2. **展示适配层**（`scoreText` / `scoreClass` / `calcGpa` 的返回值形状、
 *    以及 `warningLevelKey` 这类映射）——
 *    这是"这个值在本端渲染成什么"，**两端本就该不同**：
 *    Web 端要产出 Element Plus 的 tag type（`warning`/`info`），
 *    小程序端要产出 CSS 类名（`medium`/`low`），词表不一样不是 bug。
 *
 * 根源是 JS 的类型强制转换：`null < 60` 为 true（null 转成 0），`Number(null) === 0`。
 * 于是"期末成绩未录入"的行会被渲染成红色的 0 分，和真正挂科在视觉上完全一样。
 * 结论：**只要期末成绩缺失，任何算术或大小比较都是错的**，必须先过 isPendingValue()。
 *
 * 另注：`calcGpa` 的十段阶梯与后端 `ScoreSyncSupport.scoreToGpa()` 吻合；
 * 后端 `ScoreCaliber.GPA_EXPR` 是**学分加权汇总**（Σ绩点×学分 / Σ学分），
 * 本身不含分段，别把两者当成一回事。
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

/**
 * 取对象里第一个"存在的"字段值（用 `in` 判断，null 也算存在）。
 *
 * 为什么必须区分"字段存在且为 null"与"字段不存在"：
 * 前者是"期末没录入"（要显示灰的待录入），后者是"这个接口根本没这个字段"
 * （不能凭空判成待录入，否则整份成绩单会被清空）。
 * @returns {*} 值；一个都没命中返回 undefined
 */
function firstPresent(obj, names) {
  for (const name of names) {
    if (name in obj) return obj[name]
  }
  return undefined
}

/**
 * 从成绩行里取"用于展示的总评成绩"。
 * 后端同一份数据在不同接口给了四五个名字，这里按优先级收敛。
 * 入参是裸数值时原样返回 —— 表格里常常只拿到某一列。
 */
export function scoreOf(row) {
  if (row === null || row === undefined) return null
  if (typeof row !== 'object') return row
  return firstPresent(row, ['scoreTotal', 'score_total', 'totalScore', 'total_score', 'score'])
}

/** 期末成绩。未录入时后端给 null，别拿它做算术。 */
export function finalOf(row) {
  if (row === null || row === undefined) return null
  if (typeof row !== 'object') return row
  return firstPresent(row, ['finalScore', 'final_score'])
}

/** 平时成绩。 */
export function regularOf(row) {
  if (row === null || row === undefined) return null
  if (typeof row !== 'object') return row
  return firstPresent(row, ['regularScore', 'regular_score', 'usualScore', 'usual_score'])
}

/**
 * 分数展示文本：未录入返回 `PENDING_TEXT`（待录入），正常返回**数字本身**。
 *
 * 入参推荐传整行（`scoreText(row)`），与 `isPending` 对齐；传裸数值也兼容。
 *
 * ⚠️ 这里**刻意不做**"90 分以上显示成『优秀』"的文案替换（旧版做过）。
 * 成绩表里总评那一列在 90 分处突然从数字变成汉字，整列不再可比，
 * 且"优秀"是等级不是分数 —— 等级有 `scores.grade` 列（A/A-/B/C/D）承载，
 * 见 `gradeOf()`。
 */
export function scoreText(row) {
  const n = toNumber(scoreOf(row))
  if (n === null) return PENDING_TEXT
  return String(n)
}

/**
 * 等级（A / A- / B / C / D）。
 * 由后端写入 `scores.grade`，权威实现是 `ScoreSyncSupport`，前端不重算。
 * 字段缺失返回空串，让调用方自己决定隐藏该格。
 */
export function gradeOf(row) {
  if (row === null || row === undefined || typeof row !== 'object') return ''
  const v = firstPresent(row, ['grade', 'gradeText', 'grade_text'])
  return v === null || v === undefined ? '' : String(v)
}

/** 分数样式类：未录入是 pending（灰），不能落进 fail（红）。入参同 `scoreText`。 */
export function scoreClass(row) {
  const n = toNumber(scoreOf(row))
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
  // 教学分析接口对 ≥70 分回「正常」：那是"没有预警"，不能落成一级也不可能显示成"未知"
  if (text.includes('正常')) return 0
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
