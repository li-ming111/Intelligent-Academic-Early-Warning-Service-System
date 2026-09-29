/**
 * 载荷归一化。
 *
 * 后端不是一个人写的：同一个"列表"在三个接口里给过 `[]`、`{list}`、`{items}`、
 * `{records}`；同一个"页码"给过 `total`、`count`、`totalCount`。
 * 页面里到处写 `res.list || res.items || []` 既难看又容易漏，
 * 统一在这里收口。
 */

/** 按序找第一个存在的字段名（兼容 camelCase / snake_case）。 */
export function pickField(obj, ...names) {
  if (!obj || typeof obj !== 'object') return undefined
  for (const name of names) {
    if (obj[name] !== undefined && obj[name] !== null) return obj[name]
  }
  return undefined
}

/** 把任意"列表形"载荷转成数组。转不出来给空数组，绝不返回 undefined。 */
export function toArray(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  const inner = pickField(payload, 'list', 'items', 'records', 'rows', 'data', 'content')
  if (Array.isArray(inner)) return inner
  // {list: {records: []}} 这类嵌套也见得到
  if (inner && typeof inner === 'object') return toArray(inner)
  return []
}

/**
 * 转成 z-paging 需要的分页结构。
 * 后端分页字段不统一，这里全部归一。
 * @returns {{list: Array, total: number}}
 */
export function toPage(payload) {
  const list = toArray(payload)
  if (Array.isArray(payload)) {
    return { list, total: payload.length }
  }
  const total = Number(pickField(payload, 'total', 'totalCount', 'count', 'totalElements'))
  return { list, total: Number.isFinite(total) ? total : list.length }
}

/** 数字化，取不到给 0（用于统计卡片，避免 NaN 渲染）。 */
export function num(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

/**
 * 解包，兼容 `{code,message,data}` 与直接返回裸对象两种情况。
 * request.js 已做过一次，这里留给"service 里又包了一层"的场景。
 */
export function unwrap(payload) {
  if (payload && typeof payload === 'object' && !Array.isArray(payload) && 'data' in payload) {
    const hasOther = 'code' in payload || 'message' in payload || 'msg' in payload
    if (hasOther) return payload.data
  }
  return payload
}

/**
 * 判断响应是否"成功"。
 * **不能用 HTTP 状态码或信封 code 判** —— 登录失败时后端 HTTP 200、
 * code 也是 200，只有 `data.status === 'error'` 说明失败（历史 bug 的根因）。
 */
export function isSuccess(data) {
  if (data === null || data === undefined) return false
  if (typeof data !== 'object') return true
  if ('status' in data) return data.status === 'success' || data.status === true
  if ('success' in data) return data.success === true
  return true
}

/** 从响应里取后端给的提示语，取不到用兜底文案。 */
export function messageOf(data, fallback = '操作失败') {
  if (data && typeof data === 'object') {
    const msg = pickField(data, 'message', 'msg', 'error')
    if (msg) return String(msg)
  }
  return fallback
}
