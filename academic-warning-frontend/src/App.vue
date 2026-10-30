<template>
  <div class="app-wrapper">
    <!-- 固定侧边栏 - 仅在已登录+非登录注册页显示（教师、学生、管理员角色使用专用布局，不显示此导航） -->
    <aside 
      v-if="isLoggedIn && !isPureAuthPage && !isTeacher && !isStudent && !isAdmin" 
      class="sidebar"
      :class="{ 'sidebar-collapsed': isCollapsed }"
    >
      <div class="sidebar-header">
        <h2>{{ isAdmin ? '智能学业预警服务系统' : '智能学习预警' }}</h2>
        <button v-if="!isAdmin" class="collapse-btn" @click="isCollapsed = !isCollapsed">
          {{ isCollapsed ? '▶' : '◀' }}
        </button>
      </div>
      <nav class="sidebar-nav">
        <!-- 管理员端导航 -->
        <template v-if="isAdmin">
          <router-link to="/admin/dashboard" class="nav-item" active-class="active">
            <span>数据统计</span>
          </router-link>
          <router-link to="/admin/colleges" class="nav-item" active-class="active">
            <span>学院管理</span>
          </router-link>
          <router-link to="/admin/majors" class="nav-item" active-class="active">
            <span>专业管理</span>
          </router-link>
          <router-link to="/admin/courses" class="nav-item" active-class="active">
            <span>课程管理</span>
          </router-link>
          <router-link to="/admin/users" class="nav-item" active-class="active">
            <span>用户管理</span>
          </router-link>
          <router-link to="/admin/rules" class="nav-item" active-class="active">
            <span>规则管理</span>
          </router-link>
          <router-link to="/admin/statistics" class="nav-item" active-class="active">
            <span>数据分析</span>
          </router-link>
          <router-link to="/admin/messages" class="nav-item" active-class="active">
            <span>消息通知</span>
          </router-link>
          <router-link to="/admin/data-export" class="nav-item" active-class="active">
            <span>数据导出</span>
          </router-link>
          <router-link to="/admin/settings" class="nav-item" active-class="active">
            <span>个人设置</span>
          </router-link>
        </template>

        <!-- 学生端使用StudentLayout的左侧菜单，不需要全局导航 -->
        <!-- 教师端导航 -->
        <template v-else-if="isTeacher">
          <router-link to="/teacher/dashboard" class="nav-item" active-class="active">
            <span>教师看板</span>
          </router-link>
          <router-link to="/teacher/scores" class="nav-item" active-class="active">
            <span>成绩管理</span>
          </router-link>
          <router-link to="/teacher/warnings" class="nav-item" active-class="active">
            <span>预警管理</span>
          </router-link>
          <router-link to="/teacher/analysis" class="nav-item" active-class="active">
            <span>班级分析</span>
          </router-link>
          <router-link to="/teacher/feedback" class="nav-item" active-class="active">
            <span>反馈管理</span>
          </router-link>
          <router-link to="/teacher/courses" class="nav-item" active-class="active">
            <span>课程管理</span>
          </router-link>
        </template>

        <!-- 辅导员端导航 -->
        <template v-else-if="isCounselor">
          <router-link to="/counselor-dashboard" class="nav-item" active-class="active">
            <span>学生情况概览</span>
          </router-link>
          <router-link to="/counselor/students" class="nav-item" active-class="active">
            <span>学生管理</span>
          </router-link>
          <router-link to="/counselor/warnings" class="nav-item" active-class="active">
            <span>学生预警管理</span>
          </router-link>
          <router-link to="/counselor/courses" class="nav-item" active-class="active">
            <span>选修课管理</span>
          </router-link>
          <router-link to="/counselor/credit-monitor" class="nav-item" active-class="active">
            <span>学生数据分析</span>
          </router-link>
          <router-link to="/counselor/notifications" class="nav-item" active-class="active">
            <span>批量通知</span>
          </router-link>
          <router-link to="/counselor/class-management" class="nav-item" active-class="active">
            <span>班级管理</span>
          </router-link>
          <router-link to="/counselor/settings" class="nav-item" active-class="active">
            <span>个人设置</span>
          </router-link>
        </template>

        <div class="nav-divider"></div>
        <button @click="handleLogout" class="nav-item logout-btn">
          <span>退出登录</span>
        </button>
      </nav>
    </aside>

    <!-- 主内容区 -->
    <div 
      :class="[
        'main-wrapper', 
        { 'full-wrapper': !isLoggedIn || isPureAuthPage || isTeacher || isStudent || isCounselor },
        { 'admin-wrapper': isAdmin },
        { 'main-wrapper-collapsed': isLoggedIn && !isPureAuthPage && !isTeacher && !isStudent && !isAdmin && !isCounselor && isCollapsed }
      ]"
    >
      <!-- 深部 -->
      <header v-if="isLoggedIn && !isPureAuthPage && !isTeacher && !isStudent && !isAdmin && !isCounselor" class="app-header" :class="{ 'admin-header': isAdmin }">
        <div class="header-content">{{ isAdmin ? '智能学习预警云服务平台 - 管理员端' : '智能学习预警云服务平台' }}</div>
        <div v-if="!isAdmin" class="header-user">
          {{ userName }} | {{ roleLabel }}
        </div>
        <div v-else class="header-user">
          <span>{{ userName }}</span>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="app-main" style="background-color: #f8f9fa !important;">
        <router-view></router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from './store'
import { ElMessage, ElMessageBox } from 'element-plus'
import apiClient from './api/client'
import { getTheme, getElementPlusTheme, elementPlusTheme } from './config/theme'

// 初始化
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isLoggedIn = ref(false)
const userRole = ref('1') // 默认学生角色
const isCollapsed = ref(false) // 侧边栏折叠状态

// 统一角色判断（转为数字后判断，避免类型问题）
// 角色映射：1=学生, 2=教师, 3=辅导员, 4=管理员
const roleNum = computed(() => Number(userRole.value) || 1)
const isStudent = computed(() => roleNum.value === 1)
const isTeacher = computed(() => roleNum.value === 2)
const isCounselor = computed(() => roleNum.value === 3)
const isAdmin = computed(() => roleNum.value === 4 || roleNum.value === 5)

// 主题配置
const currentTheme = computed(() => getTheme(userRole.value))

// 更新页面主题样式
const updatePageTheme = () => {
  const theme = currentTheme.value
  // 更新 CSS 变量
  document.documentElement.style.setProperty('--theme-primary', theme.primary)
  document.documentElement.style.setProperty('--theme-primary-light', theme.primaryLight)
  document.documentElement.style.setProperty('--theme-primary-dark', theme.primaryDark)
  document.documentElement.style.setProperty('--theme-gradient', theme.gradient)
  document.documentElement.style.setProperty('--theme-gradient-header', theme.gradientHeader)
  document.documentElement.style.setProperty('--theme-text', theme.text)
  document.documentElement.style.setProperty('--theme-background', theme.background)
  document.documentElement.style.setProperty('--theme-border', theme.border)

  // 更新 Element Plus 主题
  // 5 = 学院管理员，必须是 collegeAdmin —— 早先写成 'admin'，
  // 导致学院管理员端和管理员端同色（getElementPlusTheme 里也缺 '5'，两处一起兜到紫）。
  const roleMap = { 1: 'student', 2: 'teacher', 3: 'counselor', 4: 'admin', 5: 'collegeAdmin' }
  const themeKey = roleMap[roleNum.value] || 'admin'
  const epTheme = elementPlusTheme[themeKey] || elementPlusTheme.admin
  for (const [key, value] of Object.entries(epTheme)) {
    document.documentElement.style.setProperty(key, value)
  }
}

// 监听角色变化，更新主题
watch(userRole, () => {
  updatePageTheme()
}, { immediate: false })

// 验证区域判定（仅登录/注册页）
const isPureAuthPage = computed(() => {
  const path = route.path
  const authPaths = ['/login', '/register', '/teacher-register', '/counselor-register', '/admin-register']
  return authPaths.includes(path)
})

// 用户信息
const userName = computed(() => localStorage.getItem('userName') || localStorage.getItem('username') || '未知用户')
const roleLabel = computed(() => {
  const labels = { 1: '学生', 2: '教师', 3: '辅导员', 4: '管理员', 5: '学院管理员' }
  return labels[roleNum.value] || '自定义角色'
})

// 验证Token有效性（简化）
const validateToken = async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    isLoggedIn.value = false
    return false
  }
  // 在有token的情况下，仅根据 localStorage 中的role信息设置使用户状态
  isLoggedIn.value = true
  userRole.value = localStorage.getItem('role') || '1'
  return true
}

// 退出登录（增加确认弹窗）
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    // 调用后端退出接口（可选）
    try {
      const userId = localStorage.getItem('userId')
      const role = localStorage.getItem('role')
      if (userId && role) {
        await apiClient.post(`/auth/logout?userId=${userId}&role=${role}`)
      }
    } catch (e) {
      // 无论后端是否响应，都下面清理本地存储
    }
  } catch (e) {
    ElMessage.info('已取消退出')
    return
  }
  // 清除本地存储
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
  localStorage.removeItem('userName')
  localStorage.removeItem('email')
  localStorage.removeItem('phone')
  localStorage.removeItem('role')
  localStorage.removeItem('adminPreferences')
  localStorage.removeItem('pageSize')
  // 2026-10-03（W8.2）：`teacherId` 原来三条登出路径一条都不清，而它曾被写成 '9'
  // ⇒ 换个人登录仍读 9 号用户的数据。写入方已删，这里清它是为了收拾中毒的浏览器。
  localStorage.removeItem('teacherId')
  userStore.logout()
  isLoggedIn.value = false
  router.push('/login')
  ElMessage.success('已退出登录')
}

// 管理员下拉菜单处理已移除，个人设置链接已放到左侧导航
const routeWatcher = router.afterEach(async (to) => {
  if (isPureAuthPage.value) return
  await validateToken()
})

onMounted(async () => {
  // 初始化验证Token
  await validateToken()
  // 加载系统偏好
  loadAdminPreferences()
  // 应用主题配置
  updatePageTheme()
  console.log('用户role:', userRole.value, 'type:', typeof userRole.value, '主题已更新')
})

// 加载管理员系统偏好
const loadAdminPreferences = () => {
  try {
    const saved = localStorage.getItem('adminPreferences')
    if (saved) {
      const prefs = JSON.parse(saved)
      // 应用主题
      if (prefs.theme === 'dark') {
        document.documentElement.style.colorScheme = 'dark'
        document.body.classList.add('dark-theme')
        document.body.classList.remove('light-theme')
      } else {
        document.documentElement.style.colorScheme = 'light'
        document.body.classList.add('light-theme')
        document.body.classList.remove('dark-theme')
      }
    }
  } catch (error) {
    console.error('加载系统偏好失败:', error)
  }
}

onUnmounted(() => {
  // 移除路由监听
  routeWatcher()
})
</script>

<style>
/* 全局样式 - 必须在 scoped 之外 */
html,
body,
#app {
  margin: 0 !important;
  padding: 0 !important;
  width: 100% !important;
  height: 100% !important;
  overflow: hidden !important;
}

/* 主内容区域默认布局 - 不有sidebar */
.app-wrapper .main-wrapper {
  margin-left: 0 !important;
  width: 100% !important;
  flex: 1 !important;
}

/* 强制隐藏 App 中的侧边栏 */
.app-wrapper > aside {
  display: none !important;
  visibility: hidden !important;
  width: 0 !important;
  position: fixed !important;
  left: -99999px !important;
}
</style>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.app-wrapper {
  display: flex;
  height: 100vh;
  background: var(--theme-background, #f8f9fa);
  transition: all 0.3s ease;
  overflow: hidden;
  margin: 0;
  padding: 0;
}

/* 侧边栏 - App 中的一个隔离规则，不影响 TeacherLayout */
.sidebar {
  display: none !important;
  width: 0 !important;
}

.sidebar-collapsed {
  width: 60px;
}

.sidebar-collapsed .nav-item span {
  display: none;
}

.sidebar-collapsed .sidebar-header h2 {
  justify-content: center;
}

.sidebar-header {
  background: var(--theme-gradient, linear-gradient(135deg, #667eea 0%, #66b1ff 100%));
  color: white;
  padding: 0 14px;
  height: 64px;
  min-height: 64px;
  max-height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  text-align: center;
  border-bottom: none;
  box-shadow: none;
  flex-shrink: 0;
  position: relative;
  overflow: visible;
  line-height: 1;
}

.sidebar-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 0, 0, 0.08), transparent);
}

.collapse-btn {
  background: transparent;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 14px;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  transition: background 0.2s;
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.sidebar-header h2 {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  line-height: 1;
  height: 20px;
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 6px 4px;
  gap: 2px;
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #fafafc 100%);
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 14px;
  margin: 0 3px;
  border-radius: 8px;
  color: #555;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.25s ease;
  position: relative;
  cursor: pointer;
  border: none;
  background: transparent;
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: var(--theme-gradient, linear-gradient(180deg, #409eff, #66b1ff));
  border-radius: 0 2px 2px 0;
  transition: height 0.3s ease;
}

.nav-item:hover {
  background: color-mix(in srgb, var(--theme-primary) 8%, transparent);
  color: var(--theme-primary);
  transform: translateX(2px);
}

.nav-item:hover::before {
  height: 24px;
}

.nav-item.active {
  background: color-mix(in srgb, var(--theme-primary) 15%, transparent);
  color: var(--theme-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--theme-primary) 20%, transparent);
  font-weight: 600;
}

.nav-item.active::before {
  height: 32px;
  width: 4px;
}

.nav-divider {
  height: 1px;
  background: color-mix(in srgb, var(--theme-primary) 10%, transparent);
  margin: 6px 8px;
}

.logout-btn {
  margin-top: auto;
  margin-bottom: 8px;
  padding: 10px 16px !important;
  margin-left: 3px !important;
  margin-right: 3px !important;
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%) !important;
  color: white !important;
  border: none !important;
  border-radius: 6px !important;
  font-weight: 500 !important;
  transition: all 0.3s ease !important;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.3) !important;
}

.logout-btn:hover {
  transform: translateY(-2px) !important;
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.4) !important;
  background: linear-gradient(135deg, #ff7a7e 0%, #f06478 100%) !important;
}

.logout-btn:active {
  transform: translateY(0) !important;
}

/* 主内容区 */
.main-wrapper {
  flex: 1;
  margin-left: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-wrapper-collapsed {
  margin-left: 60px !important;
}

/* 头部 */
.app-header {
  background: var(--theme-gradient-header, linear-gradient(90deg, #667eea 0%, #66b1ff 50%, #667eea 100%));
  color: white;
  padding: 0 28px;
  height: 64px;
  min-height: 64px;
  max-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 12px color-mix(in srgb, var(--theme-primary) 15%, transparent);
  flex-shrink: 0;
  position: relative;
  overflow: visible;
  border-bottom: none;
  line-height: 1;
}

.app-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
}

.app-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 0, 0, 0.08), transparent);
}

.header-content {
  font-size: 18px;
  font-weight: bold;
  letter-spacing: 0.5px;
  line-height: 1;
  height: 24px;
  display: flex;
  align-items: center;
}

.header-user {
  font-size: 14px;
  opacity: 0.9;
}

.app-main {
  flex: 1;
  padding: 24px 24px 24px 28px;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: var(--theme-background, #f8f9fa) !important;
  scroll-behavior: smooth;
  border-left: none !important;
  background: linear-gradient(90deg, var(--theme-background, #f8f9fa) 0%, #f5f7fb 100%) !important;
  position: relative;
}

.app-main::before {
  display: none !important;
  content: none !important;
}

.full-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-left: 0;
}

.full-wrapper .app-main {
  flex: 1;
  background: var(--theme-gradient, linear-gradient(135deg, #667eea 0%, #66b1ff 100%));
  padding: 0;
}

.admin-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-left: 0;
  height: 100%;
}

.admin-wrapper .app-main {
  flex: 1;
  background-color: var(--theme-background, #f5f7fa) !important;
  padding: 0;
  margin: 0;
  height: 100%;
}
</style>

<style>
/* 全局管理员端样式优化 - 使用主题变量 */

/* 背景色 - 最高优先级 */
body,
html,
#app,
.app-wrapper,
.main-wrapper,
.app-main {
  background-color: var(--theme-background, #f8f9fa) !important;
}

body,
html {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

#app {
  height: 100%;
  width: 100%;
}

.app-wrapper {
  overflow: hidden !important;
}

.main-wrapper {
  overflow: hidden !important;
}

.app-main {
  overflow-y: auto !important;
  overflow-x: hidden !important;
}

.full-wrapper .app-main {
  background: var(--theme-gradient, linear-gradient(135deg, #667eea 0%, #66b1ff 100%)) !important;
}

/* Element Plus 全局组件样式 */
.el-card,
.metric-card,
.chart-card {
  border: 1px solid var(--theme-border, #e9ecef) !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08) !important;
  background-color: white !important;
}

.el-card__header {
  border-bottom: 1px solid var(--theme-border, #e9ecef) !important;
  background-color: white !important;
}

/* 按钮主色调 - 使用主题变量 */
.el-button--primary,
.el-button.is-primary {
  background-color: var(--el-color-primary, #667eea) !important;
  border-color: var(--el-color-primary, #667eea) !important;
  color: white !important;
}

.el-button--primary:hover,
.el-button.is-primary:hover,
.el-button--primary:focus {
  background-color: var(--el-color-primary-dark-2, #5568d3) !important;
  border-color: var(--el-color-primary-dark-2, #5568d3) !important;
}

.el-button--primary.is-active,
.el-button--primary:active {
  background-color: var(--el-color-primary-dark-2, #4a52b8) !important;
  border-color: var(--el-color-primary-dark-2, #4a52b8) !important;
}

/* 标签和标记 */
.el-tabs__active-bar {
  background-color: var(--el-color-primary, #667eea) !important;
}

.el-tabs__item.is-active {
  color: var(--el-color-primary, #667eea) !important;
}

.el-tag {
  border: 1px solid var(--theme-border, #e9ecef) !important;
}

/* 表格样式 */
.el-table,
.el-table__wrapper {
  border: 1px solid var(--theme-border, #e9ecef) !important;
}

.el-table__header th,
.el-table__header tr {
  background-color: var(--theme-background, #f8f9fa) !important;
  border-bottom: 1px solid var(--theme-border, #e9ecef) !important;
}

/* 表头文字和排序图标不换行 */
.el-table th .cell {
  display: inline-flex !important;
  align-items: center !important;
  white-space: nowrap !important;
  gap: 4px !important;
}
.el-table .caret-wrapper {
  display: inline-flex !important;
  flex-shrink: 0 !important;
}

/* 操作列按钮不换行 */
.el-table td .cell {
  white-space: nowrap !important;
}

.el-table__body tr {
  border-bottom: 1px solid var(--theme-border, #e9ecef) !important;
}

.el-table__body td {
  border-right: 1px solid var(--theme-border, #e9ecef) !important;
}

/* 输入框 */
.el-input__wrapper,
.el-input--large .el-input__wrapper {
  border: 1px solid var(--theme-border, #e9ecef) !important;
  background-color: white !important;
}

.el-input__wrapper:hover,
.el-input__wrapper.is-focus {
  border-color: var(--el-color-primary, #667eea) !important;
}

.el-input__wrapper.is-focus {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary, #667eea) 20%, transparent) !important;
}

/* 对话框 */
.el-dialog {
  border: 1px solid var(--theme-border, #e9ecef) !important;
}

.el-dialog__header {
  border-bottom: 1px solid var(--theme-border, #e9ecef) !important;
  background-color: white !important;
}

.el-dialog__body {
  background-color: white !important;
}

.el-dialog__footer {
  border-top: 1px solid var(--theme-border, #e9ecef) !important;
  background-color: white !important;
}

/* 分页器 */
.el-pagination .btn-prev,
.el-pagination .btn-next,
.el-pagination .el-pager li {
  border: 1px solid var(--theme-border, #e9ecef) !important;
  background-color: white !important;
}

.el-pagination .btn-prev:hover,
.el-pagination .btn-next:hover,
.el-pagination .el-pager li:hover {
  color: var(--el-color-primary, #667eea) !important;
  border-color: var(--el-color-primary, #667eea) !important;
}

/* Element Plus 2.x 的当前页类名是 `is-active`，只写 `.active` 会漏掉它：
   上面把 li 背景强制成白色，而选中项的文字是白色，结果就是页码格空白。 */
.el-pagination .el-pager li.active,
.el-pagination .el-pager li.is-active {
  color: var(--el-color-primary, #667eea) !important;
  background-color: var(--el-color-primary-light-9, #f0f2f8) !important;
}

/* 选择器和下拉菜单 */
.el-select__wrapper,
.el-select .el-input__wrapper {
  border: 1px solid var(--theme-border, #e9ecef) !important;
}

.el-select__wrapper:hover,
.el-select .el-input__wrapper:hover {
  border-color: var(--el-color-primary, #667eea) !important;
}

/* 进度条 */
.el-progress__bar .el-progress-bar__inner {
  background-color: var(--el-color-primary, #667eea) !important;
}

/* 徽章 */
.el-badge__content {
  background-color: var(--el-color-primary, #667eea) !important;
}

/* 表单项 */
.el-form-item__label {
  color: #333 !important;
}

/* 统计卡片和指标卡 - 使用主题变量 */
.metric-card.blue {
  border-top-color: var(--el-color-primary, #667eea) !important;
}

.metric-card.green {
  border-top-color: var(--el-color-primary, #667eea) !important;
}

.metric-card.red {
  border-top-color: var(--el-color-primary, #667eea) !important;
}

.metric-card.purple {
  border-top-color: var(--el-color-primary, #667eea) !important;
}

.metric-value {
  color: var(--el-color-primary, #667eea) !important;
}

/* 图标颜色统一为主题色 */
.metric-icon,
.action-icon,
.online-icon,
.today-icon {
  color: var(--el-color-primary, #667eea) !important;
}

/* 预警条颜色统一 */
.warning-bar {
  background-color: var(--el-color-primary, #667eea) !important;
}

/* 活动类型标签统一 */
.activity-type {
  background: var(--el-color-primary-light-9, #f0f2f8) !important;
  color: var(--el-color-primary, #667eea) !important;
}

/* 状态标签统一颜色 */
.el-tag.el-tag--success,
.el-tag.el-tag--warning,
.el-tag.el-tag--danger,
.el-tag.el-tag--info {
  background-color: var(--el-color-primary-light-9, #f0f2f8) !important;
  color: var(--el-color-primary, #667eea) !important;
  border-color: var(--el-color-primary, #667eea) !important;
}
</style>
