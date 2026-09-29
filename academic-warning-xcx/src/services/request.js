/**
 * 唯一请求出口。所有 service 都走这里，不要各自 uni.request。
 *
 * 三件必须处理好的事：
 * 1. baseURL 分环境 —— H5 在浏览器里跑，直接打 8076 会被 CORS 拦，
 *    必须走 vite 代理的相对路径 /api；小程序没有代理，只能打绝对地址。
 * 2. 401 防重入 —— 一个页面并发发 5 个请求、token 同时过期时，
 *    旧代码会连开 5 次 reLaunch 到登录页，登录页自己又被弹掉，
 *    表现为"登录后立刻闪回登录页"。用一个 latch + 冷却时间窗压住。
 * 3. 网关错误体没有 data —— 401/403 由 gateway 直接返
 *    `{code, message}`（没有 data 字段），不能按业务信封解析。
 */

// #ifdef H5
// 开发期走 vite.config.js 的 proxy['/api'] → localhost:8076。
// 生产 H5 是静态产物、没有代理，必须显式打后端地址。
const BASE_URL = import.meta.env.DEV ? '/api' : 'http://localhost:8076/api'
// #endif
// #ifndef H5
// 小程序/App 端：微信开发者工具需勾选"不校验合法域名"，
// 否则 localhost 一律被拦（表现为所有接口全挂、无有效报错）。
const BASE_URL = 'http://localhost:8076/api'
// #endif

export const TIMEOUT = 10000

/** 登录失效时的统一出口。latch 期间重复的 401 直接吞掉。 */
let redirecting = false
let lastRedirectAt = 0
const REDIRECT_COOLDOWN = 3000

function handleUnauthorized() {
  const now = Date.now()
  if (redirecting || now - lastRedirectAt < REDIRECT_COOLDOWN) return
  redirecting = true
  lastRedirectAt = now

  try {
    uni.removeStorageSync('token')
    uni.removeStorageSync('userId')
    uni.removeStorageSync('role')
    uni.removeStorageSync('userName')
    uni.removeStorageSync('teacherId')
    uni.removeStorageSync('counselorId')
  } catch (e) {
    // storage 异常不该阻断登出流程
  }

  const pages = getCurrentPages()
  const current = pages.length ? `/${pages[pages.length - 1].route}` : ''
  const suffix = current && current !== '/pages/auth/login'
    ? `?redirect=${encodeURIComponent(current)}`
    : ''

  uni.reLaunch({
    url: `/pages/auth/login${suffix}`,
    complete: () => {
      // reLaunch 是异步的，留一拍再放开 latch，
      // 免得在跳转完成前又放进来一个 401
      setTimeout(() => { redirecting = false }, 300)
    }
  })
}

/** 拼 query，null/undefined/'' 一律跳过（后端对空串会当成有效筛选值）。 */
function buildQuery(params) {
  if (!params) return ''
  const qs = Object.keys(params)
    .filter(k => params[k] !== null && params[k] !== undefined && params[k] !== '')
    .map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

/**
 * 统一错误对象。带 statusCode / body 便于上层区分
 * "网络不通" 与 "后端返回业务错误"。
 */
export class ApiError extends Error {
  constructor(message, statusCode = 0, body = null) {
    super(message)
    this.name = 'ApiError'
    this.statusCode = statusCode
    this.body = body
  }
}

/**
 * 取后端返回里的 message。网关与各 service 的字段名并不统一，
 * 这里按优先级找，找不到给一个按状态码兜底的文案。
 *
 * 一个坑：服务没起或下游不可达时，网关回的是 Spring 默认错误页
 * `{timestamp, path, status, error:"Service Unavailable", requestId}`。
 * 那个 `error` 是 HTTP 原因短语的英文原文，直接弹给用户是"服务不可用"级别的
 * 技术黑话，所以 **5xx 不采信 `error` 字段**，只认真正的业务 message。
 */
function pickMessage(body, statusCode) {
  if (body && typeof body === 'object') {
    const msg = body.message || body.msg || body.detail
    if (msg) return String(msg)
    if (statusCode < 500 && body.error) return String(body.error)
    // 校验失败时后端会给 errors 数组
    if (Array.isArray(body.errors) && body.errors.length) {
      const first = body.errors[0]
      return String(first.defaultMessage || first.message || first)
    }
  }
  if (statusCode === 401) return '登录已过期，请重新登录'
  if (statusCode === 403) return '没有访问该资源的权限'
  if (statusCode === 404) return '接口不存在'
  if (statusCode === 503) return '后端服务未启动，请先启动对应微服务'
  if (statusCode >= 500) return '服务异常，请稍后重试'
  return `请求失败(${statusCode})`
}

/**
 * 发起请求。
 * @param {string} endpoint 以 / 开头，如 '/auth/login'
 * @param {object} options  { method, params, data, header, timeout, silent, raw }
 *   silent: true 时不弹 toast（由调用方自己处理错误展示）
 *   raw:    true 时返回整个信封 {code,message,data}，不做拆包（登录接口需要）
 */
export function request(endpoint, options = {}) {
  const {
    method = 'GET',
    params,
    data,
    // 旧代码里两种写法都有，统一收一下
    header = options.headers || {},
    timeout = TIMEOUT,
    raw = false
  } = options

  const url = `${BASE_URL}${endpoint}${buildQuery(params)}`
  const headers = { 'Content-Type': 'application/json', ...header }

  const token = uni.getStorageSync('token')
  if (token) headers['Authorization'] = `Bearer ${token}`

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data,
      header: headers,
      timeout,
      success: (res) => {
        const { statusCode, data: body } = res

        if (statusCode === 401) {
          handleUnauthorized()
          reject(new ApiError('登录已过期，请重新登录', 401, body))
          return
        }
        if (statusCode === 403) {
          reject(new ApiError(pickMessage(body, 403), 403, body))
          return
        }
        if (statusCode < 200 || statusCode >= 300) {
          reject(new ApiError(pickMessage(body, statusCode), statusCode, body))
          return
        }

        // 网关错误体没有 data 字段：即使 HTTP 200 也要看 code
        if (body && typeof body === 'object' && 'code' in body && body.code !== 200 && body.code !== 0) {
          // 业务失败。登录失败就走这里（HTTP 200 + code 200 由调用方按 status 判）
          if (body.code === 401) {
            handleUnauthorized()
            reject(new ApiError(pickMessage(body, 401), 401, body))
            return
          }
          reject(new ApiError(pickMessage(body, body.code), body.code, body))
          return
        }

        if (raw) {
          resolve(body)
          return
        }
        // 标准信封 {code, message, data}
        if (body && typeof body === 'object' && 'data' in body) {
          resolve(body.data)
          return
        }
        resolve(body)
      },
      fail: (err) => {
        const msg = err && err.errMsg ? err.errMsg : ''
        // 超时与"未勾选不校验合法域名"在这里是同一类表现，文案要给出可操作提示
        const friendly = /timeout/i.test(msg)
          ? '请求超时，请检查网络'
          : '网络连接失败，请确认后端已启动'
        reject(new ApiError(friendly, 0, err))
      }
    })
  })
}

export const http = {
  get: (url, params, options) => request(url, { ...options, method: 'GET', params }),
  post: (url, data, options) => request(url, { ...options, method: 'POST', data }),
  put: (url, data, options) => request(url, { ...options, method: 'PUT', data }),
  patch: (url, data, options) => request(url, { ...options, method: 'PATCH', data }),
  del: (url, params, options) => request(url, { ...options, method: 'DELETE', params })
}

export default http
