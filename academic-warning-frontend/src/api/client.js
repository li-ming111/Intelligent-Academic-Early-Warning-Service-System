import axios from 'axios'

// 开发环境使用相对路径（Vite代理），生产环境使用环境变量
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
})

// 请求拦接器
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 添加时间戳參数以避免缓存
    if (config.method === 'get' || config.method === 'GET') {
      config.params = config.params || {}
      config.params._t = new Date().getTime()
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => {
    // 对于blob类型的响应，直接返回response.data
    if (response.config.responseType === 'blob') {
      return response.data
    }
    
    // 登录接口特殊处理：返回包含 code/data/message 的完整 ApiResponse
    // 因为登录需要访问 response.data.data.token 等字段
    if (response.config.url && response.config.url.includes('/auth/login')) {
      // 返回整个 axios response，让调用者自己处理
      return response
    }
    
    if (response.data && response.data.code !== undefined) {
      if (response.data.code === 200) {
        return response.data.data
      } else {
        return Promise.reject(new Error(response.data.message || '请求失败'))
      }
    }
    return response.data
  },
  (error) => {
    // 仅处理 401 错误（没有个token 或 token 无效）
    if (error.response && error.response.status === 401) {
      console.warn('[API] 401 Unauthorized - 清除token并跳转登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('userName')
      localStorage.removeItem('email')
      localStorage.removeItem('phone')
      localStorage.removeItem('role')
      localStorage.removeItem('adminPreferences')
      // 2026-10-03（W8.2）：同 App.vue 的登出，一并清掉曾被写成 '9' 的 teacherId。
      localStorage.removeItem('teacherId')
      window.location.href = '/login'
    }
    // 403 仅记录不跳转，交由前端处理
    if (error.response && error.response.status === 403) {
      console.error('[API] 403 Forbidden', error.config.url, error.response.data)
    }
    return Promise.reject(error)
  }
)

export default apiClient
