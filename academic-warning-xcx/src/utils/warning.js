/**
 * 预警归一化 —— **跨端字段差异的唯一出口**。
 *
 * 实测到的后端形态（同一张 `academic_warning` 表，不同接口给的形状不同）：
 *
 * | 接口 | warningLevel | status |
 * |---|---|---|
 * | `/teachers/warnings/{id}` | **中文** `"严重"/"中度"/"轻度"` | **中文** `"待处理"/"已处理"/"已解除"` |
 * | `/students/warnings/{id}` | 数字 `1/2/3` | 数字 `0/1/2` |
 * | `/admin/warnings` | 数字 | 数字 `0/1/2` |
 *
 * 表结构（已核对 `information_schema`）：`warning_level` int、
 * `status` int（**0 待处理 / 1 已处理 / 2 已解除**）、
 * `appeal_status` varchar、`handled_by` / `handle_result` / `handled_at` 可为 null。
 *
 * **禁止任何页面自己写 `row.warningLevel === 3` 或 `=== '严重'` 或 `=== 'high'`。**
 * 历史 bug 就是拿 'high' 跟数字比，永不相等、全部落到 else，所有预警都显示成"轻度"。
 *
 * ⚠️ 2026-10-02（W1.6）：状态从**两态**扩到**三态**。此前 `warningStatus()` 把任何
 * 非 1 的值都压成 0，于是 `status=2`（已解除，定时扫描自动写回的）在**所有**走共用层的
 * 小程序页面上都显示成"待处理" —— 学生明明已经恢复了，通知和列表还在说他待处理。
 */

import { warningLevel, warningLevelText, warningLevelKey } from './score.js'
import { pickField } from './response.js'
import { formatDate } from './format.js'
// 状态文案只在 constants/dict.js 维护一份，避免两处各写一套慢慢走偏
import { WARNING_STATUS_TEXT as STATUS_TEXT, STATUS_TAG_TYPE } from '@/constants/dict.js'

/**
 * 预警处理状态：库里是 int，0 待处理 / 1 已处理 / 2 已解除。
 *
 * ⚠️ 后端 `com.academic.common.constant.WarningStatus` 与 PC 端 `utils/score.js`
 * 各有一份对应表，**三处必须同改**（同一个 status 两端显示不同正是本轮修掉的东西）。
 */
export const WARNING_STATUS = { PENDING: 0, HANDLED: 1, DISPELLED: 2 }

/**
 * 处理状态归一化为 `0 | 1 | 2`（认不出按待处理，宁可多显示待处理也不漏）。
 * 能吃下 int、数字字符串、中英文文案。
 *
 * ⚠️ **文案分支必须先判「解除」再判「已」**：`'已解除'.includes('已')` 是 true，
 * 顺序写反了就会把"已解除"归成"已处理"（这正是扩三态时最容易踩的一脚）。
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
  // 先「解除」（它是"已"的子集，顺序反了就会被下面那条吃掉）
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
  return STATUS_TEXT[warningStatus(value)] ?? '未知'
}

/** 状态样式键：pending / handled / dispelled。用于标签配色。 */
export function warningStatusKey(value) {
  const s = warningStatus(value)
  if (s === WARNING_STATUS.HANDLED) return 'handled'
  if (s === WARNING_STATUS.DISPELLED) return 'dispelled'
  return 'pending'
}

/**
 * 状态 → 标签 CSS 类，即 `styles/common.scss` 里的 `.tag--success` / `.tag--warning` / `.tag--info`。
 *
 * ⚠️ 配色只此一份：色值取自 `constants/dict.js` 的 `STATUS_TAG_TYPE`（改配色改那里）。
 * 页面**不要**再自己写 `status === 1 ? 'tag--success' : 'tag--warning'` —— 那正是
 * status=2 被显示成"待处理"（黄色）的原因。
 */
export function warningStatusTagClass(value) {
  return `tag--${STATUS_TAG_TYPE[warningStatusKey(value)] || 'warning'}`
}

/**
 * 预警行归一化。
 * @param {object} raw
 * @returns {object} 原字段 + 统一后的判定字段
 */
export function normalizeWarning(raw) {
  const row = raw && typeof raw === 'object' ? raw : {}
  const pick = (...names) => {
    const v = pickField(row, ...names)
    return v === undefined ? null : v
  }

  const levelRaw = pick('warningLevel', 'warning_level', 'level')
  const statusRaw = pick('status', 'handleStatus', 'handle_status')

  const level = warningLevel(levelRaw)
  return {
    ...row,
    id: pick('id', 'warningId', 'warning_id'),
    // 双 ID 空间：academic_warning.student_id 是 student_profile.id，不是 users.id
    studentId: pick('studentId', 'student_id'),
    studentName: pick('studentName', 'student_name'),
    title: pick('title'),
    // 描述字段三个名字轮流出现，取到哪个用哪个
    description: pick('description', 'warningMessage', 'warning_message') || '',
    type: pick('type', 'warningType', 'warning_type'),
    triggerScore: pick('triggerScore', 'trigger_score'),
    gpaBefore: pick('gpaBefore', 'gpa_before'),
    gpaAfter: pick('gpaAfter', 'gpa_after'),
    handledBy: pick('handledBy', 'handled_by'),
    handleResult: pick('handleResult', 'handle_result') || '',
    handledAt: pick('handledAt', 'handled_at'),
    createdAt: pick('createdAt', 'created_at'),
    appealStatus: pick('appealStatus', 'appeal_status'),

    // —— 归一化后的判定字段，页面只用这些 ——
    level,
    levelText: warningLevelText(levelRaw),
    levelKey: warningLevelKey(levelRaw),
    status: warningStatus(statusRaw),
    statusText: warningStatusText(statusRaw),
    statusKey: warningStatusKey(statusRaw),
    isPending: warningStatus(statusRaw) === WARNING_STATUS.PENDING,
    isHandled: warningStatus(statusRaw) === WARNING_STATUS.HANDLED,
    isDispelled: warningStatus(statusRaw) === WARNING_STATUS.DISPELLED,
    createdDate: formatDate(pick('createdAt', 'created_at'), true),
    handledDate: formatDate(pick('handledAt', 'handled_at'), true),
    // W39 挂科课程明细（academic_warning_course）。此前预警只有一句散文正文，
    // 「挂了哪几门课」无处可读；后端现在回 courses（数组）+ courseNames（拼串）。
    // 缺失时归成空数组/空串，页面 v-if 判长度即可，不用自己兜 null。
    className: pick('className', 'class_name') || '',
    courses: Array.isArray(row.courses) ? row.courses : [],
    courseNames: pick('courseNames', 'course_names') || '',
    // 只有教师端那条路会回它；其余角色的载荷里没有这个键 ⇒ 恒 false，不影响。
    involvesMyCourse: row.involvesMyCourse === true
  }
}

/** 批量归一化。输入可以是数组或 `{list}` / `{records}` 分页载荷。 */
export function normalizeWarnings(payload) {
  const list = Array.isArray(payload)
    ? payload
    : (payload && typeof payload === 'object'
        ? (Array.isArray(payload.list) ? payload.list
          : Array.isArray(payload.records) ? payload.records
          : Array.isArray(payload.items) ? payload.items
          : Array.isArray(payload.data) ? payload.data
          : [])
        : [])
  return list.map(normalizeWarning)
}

/**
 * 按等级统计。
 *
 * **必须用这个，不能用首页返回的 `redWarnings`/`yellowWarnings`** ——
 * 那两个字段后端恒为 null（历史遗留），拿它渲染永远是 0，
 * 而"预警为 0"在答辩现场是最容易被追问的点。
 *
 * @returns {{total:number, level1:number, level2:number, level3:number,
 *            pending:number, handled:number, dispelled:number, severe:number}}
 *
 * ⚠️ 2026-10-02（W1.6）：原来是**两桶**（`isPending ? pending++ : handled++`），
 * 于是 `status=2`（已解除）被算进"已处理" —— 首页卡片上"已处理"的数字比真实处理量多，
 * 而"已解除"永远是 0。现在三个桶各自独立，**`pending + handled + dispelled === total`**
 * 是这个函数的不变量（页面若把三桶分开显示，加起来必须等于 total，否则就是这里漏了状态）。
 */
export function countWarnings(list) {
  const stat = {
    total: 0, level1: 0, level2: 0, level3: 0,
    pending: 0, handled: 0, dispelled: 0,
    // 后端 `high_risk_count` 的口径是"等级 ≥ 2"，保持一致
    severe: 0
  }
  for (const raw of normalizeWarnings(list)) {
    stat.total += 1
    if (raw.level === 1) stat.level1 += 1
    else if (raw.level === 2) stat.level2 += 1
    else if (raw.level >= 3) stat.level3 += 1
    if (raw.level >= 2) stat.severe += 1
    if (raw.isDispelled) stat.dispelled += 1
    else if (raw.isPending) stat.pending += 1
    else stat.handled += 1
  }
  return stat
}

/** 按等级筛选（不改动原数组）。 */
export function filterByLevel(list, level) {
  return normalizeWarnings(list).filter(w => w.level === Number(level))
}

/** 只看未处理。辅导员/管理员的待办列表用。 */
export function pendingOnly(list) {
  return normalizeWarnings(list).filter(w => w.isPending)
}
