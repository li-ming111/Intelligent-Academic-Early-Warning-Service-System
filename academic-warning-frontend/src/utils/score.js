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

/**
 * 等级对应的图表色。全站统一：严重=红、中度=橙、轻度=蓝。
 *
 * 色值与 `AdminStatistics.vue` 的饼图一致。**别再手写第二张等级色表** ——
 * 2026-10-01 第二十二轮就是因为 `CounselorAnalytics.vue` 自己写了一张
 * `{1:'红色',2:'黄色',3:'蓝色'}` / `{1:'#ef4444',2:'#3b82f6',3:'#3b82f6'}`：
 * 前者把 1 和 3 对调（口径见上面的 {@link warningLevelText}），
 * 后者让"中度"与"严重"同色。两处都不报错，只是**图在撒谎**。
 */
export function warningLevelColor(value) {
  const level = warningLevel(value)
  if (level >= 3) return '#ef4444'
  if (level === 2) return '#f59e0b'
  if (level === 1) return '#3b82f6'
  return '#94a3b8'
}

/**
 * 把「预警趋势」接口的行聚合成可直接喂给 ECharts 的三条序列。
 *
 * 入参形如 `[{month:'2026-09', warning_level:1, count:1}, ...]`，来自
 * `/counselors/analytics/warning-trend`（后端 `WarningMapper.getWarningTrendByCounselor`，
 * 库内按月 + 按等级 `GROUP BY`）。出参 `{months, severe, medium, mild}`，
 * 三个数组与 `months` 等长、按月份（'YYYY-MM'）升序。
 *
 * 分档口径与后端 `getWarningLevelCountByClass` 逐字一致：`>=3` 严重、`==2` 中度、`==1` 轻度。
 * 未被识别的等级**不归入任何一条**（不替它猜一个档），与后端 `SUM(CASE …)` 的行为相同。
 *
 * 2026-10-01 第二十二轮抽成共用函数：此前首页 `CounselorDashboard.vue` 根本没调这个接口，
 * 而是把图画成写死的 `[0,0,0,0,X,X]`（x 轴月份是真的、值全是编的）；数据分析页
 * `CounselorAnalytics.vue` 调了接口却把 1 和 3 对调。两页现在共用这一份实现。
 */
export function buildWarningTrend(rows) {
  const list = Array.isArray(rows) ? rows : []
  const order = []
  const bucket = {}
  for (const row of list) {
    const month = row?.month
    if (!month) continue
    const key = String(month)
    if (!bucket[key]) { bucket[key] = { severe: 0, medium: 0, mild: 0 }; order.push(key) }
    const level = warningLevel(row.warning_level ?? row.level)
    const count = Number(row.count) || 0
    if (level >= 3) bucket[key].severe += count
    else if (level === 2) bucket[key].medium += count
    else if (level === 1) bucket[key].mild += count
  }
  const months = order.sort()
  return {
    months,
    severe: months.map((m) => bucket[m].severe),
    medium: months.map((m) => bucket[m].medium),
    mild: months.map((m) => bucket[m].mild)
  }
}
