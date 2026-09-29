/**
 * 展示层格式化。只做"值 → 文本"，不判断业务口径
 * （成绩口径在 utils/score.js，预警口径在 utils/warning.js）。
 */

/** 空值统一显示为占位符，避免页面上出现 "null" / "undefined" / "NaN"。 */
export const EMPTY_TEXT = '--'

export function text(value, fallback = EMPTY_TEXT) {
  if (value === null || value === undefined) return fallback
  const s = String(value).trim()
  return s === '' ? fallback : s
}

/**
 * 日期时间。后端在不同接口给过 `2026-09-21 10:30:00`、
 * `2026-09-21T10:30:00`、时间戳三种形态，统一处理。
 * @param {*} value
 * @param {boolean} withTime 是否带时分
 */
export function formatDate(value, withTime = false) {
  if (!value) return EMPTY_TEXT
  let d
  if (typeof value === 'number') {
    // 10 位是秒，13 位是毫秒
    d = new Date(value < 1e12 ? value * 1000 : value)
  } else {
    // iOS 不认 'YYYY-MM-DD HH:mm:ss'，把空格换成 T
    d = new Date(String(value).replace(' ', 'T'))
  }
  if (Number.isNaN(d.getTime())) return String(value)

  const p = n => String(n).padStart(2, '0')
  const date = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  if (!withTime) return date
  return `${date} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 相对时间：刚刚 / N分钟前 / N小时前 / N天前 / 具体日期。 */
export function fromNow(value) {
  if (!value) return EMPTY_TEXT
  const d = typeof value === 'number'
    ? new Date(value < 1e12 ? value * 1000 : value)
    : new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(value)

  const diff = Date.now() - d.getTime()
  if (diff < 0) return formatDate(value)
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min}分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day}天前`
  return formatDate(value)
}

/**
 * 学期标签。库里存的是 `2023-2024-1` 形态，
 * 展示成 `2023-2024学年 第1学期`。
 */
export function formatSemester(value) {
  if (!value) return EMPTY_TEXT
  const s = String(value)
  const m = s.match(/^(\d{4})-(\d{4})-(\d)$/)
  if (!m) return s
  return `${m[1]}-${m[2]}学年 第${m[3]}学期`
}

/** 学期短标签：`2023-2024-1` → `24春`。第一学期是秋，第二学期是春。 */
export function shortSemester(value) {
  if (!value) return EMPTY_TEXT
  const m = String(value).match(/^(\d{4})-(\d{4})-(\d)$/)
  if (!m) return String(value)
  return `${m[1].slice(2)}${m[3] === '1' ? '秋' : '春'}`
}

/**
 * 由「学期号 + 学年」拼出 `YYYY-YYYY-N` 标签。
 *
 * 为什么要自己拼：`/students/scores` 的行里只有 `semester`（**数字字符串** `"5"`）
 * 和 `year`（2025），**没有** `semesterLabel`；而 `/students/{uid}/gpa` 直接给了
 * `semesterLabel`。两处要能对上，就得有这条换算规则。
 *
 * 规则（已用司明的数据核对过）：
 *   学年起始年 = 行自带的 `year`（库里已是"入学年 + floor((n-1)/2)"算好的值，
 *   所以这里**不再加偏移**），学期序号 = `((n-1) % 2) + 1`。
 * 司明 2023 入学 → n=5 是 `2025-2026-1`、n=6 是 `2025-2026-2`，
 * 与 `/students/27/gpa` 返回的 `{semester:6, semesterLabel:"2025-2026-2"}` 一致。
 *
 * @param {number|string} semester 学期号，如 "5"
 * @param {number|string} year 行自带的学年起始年，如 2025
 */
export function semesterLabelOf(semester, year) {
  const n = Number(semester)
  if (!Number.isFinite(n) || n < 1) return ''
  const term = ((n - 1) % 2) + 1
  const y = Number(year)
  if (!Number.isFinite(y)) return `第${n}学期`
  return `${y}-${y + 1}-${term}`
}

/** 数字保留小数位；非数字返回占位符。 */
export function number(value, digits = 1) {
  const n = Number(value)
  if (!Number.isFinite(n)) return EMPTY_TEXT
  return n.toFixed(digits)
}

/** 百分比。后端有的接口给 0.35，有的给 35，用 asRatio 区分。 */
export function percent(value, digits = 1, asRatio = false) {
  const n = Number(value)
  if (!Number.isFinite(n)) return EMPTY_TEXT
  const v = asRatio ? n * 100 : n
  return `${v.toFixed(digits)}%`
}

/** 学分：整数不带小数点（3 而不是 3.0）。 */
export function credits(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return EMPTY_TEXT
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

/** 姓名脱敏：张三 → 张*，张三丰 → 张*丰。 */
export function maskName(name) {
  const s = text(name, '')
  if (!s) return EMPTY_TEXT
  if (s.length === 1) return s
  if (s.length === 2) return s[0] + '*'
  return s[0] + '*'.repeat(s.length - 2) + s[s.length - 1]
}

/** 手机号脱敏：138****8888 */
export function maskPhone(phone) {
  const s = text(phone, '')
  if (!/^\d{11}$/.test(s)) return s || EMPTY_TEXT
  return `${s.slice(0, 3)}****${s.slice(7)}`
}

/** 学号/工号脱敏：保留前 4 后 2。 */
export function maskId(id) {
  const s = text(id, '')
  if (s.length <= 6) return s
  return `${s.slice(0, 4)}****${s.slice(-2)}`
}
