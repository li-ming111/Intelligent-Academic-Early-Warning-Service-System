<template>
  <div class="teacher-layout">
    <!-- 顶部导航栏 -->
    <div class="top-navbar">
      <div class="navbar-left">
        <div class="logo">
          <img src="@/assets/logo.png" alt="哈尔滨信息工程学院" class="logo-image">
        </div>
        <nav class="main-nav">
          <router-link 
            to="/teacher/dashboard" 
            class="nav-item"
            :class="{ active: isActive('/teacher/dashboard') }"
          >
            <span>首页</span>
          </router-link>
          <router-link
            to="/teacher/courses"
            class="nav-item"
            :class="{ active: isActive('/teacher/courses') }"
          >
            <span>课程管理</span>
          </router-link>
          <router-link
            to="/teacher/timetable"
            class="nav-item"
            :class="{ active: isActive('/teacher/timetable') }"
          >
            <span>我的课表</span>
          </router-link>
          <router-link 
            to="/teacher/scores" 
            class="nav-item"
            :class="{ active: isActive('/teacher/scores') }"
          >
            <span>成绩管理</span>
          </router-link>
          <router-link 
            to="/teacher/warnings" 
            class="nav-item"
            :class="{ active: isActive('/teacher/warnings') }"
          >
            <span>预警管理</span>
          </router-link>
          <router-link 
            to="/teacher/credit-prediction" 
            class="nav-item"
            :class="{ active: isActive('/teacher/credit-prediction') }"
          >
            <span>学分预测</span>
          </router-link>
          <router-link 
            to="/teacher/class-management" 
            class="nav-item"
            :class="{ active: isActive('/teacher/class-management') }"
          >
            <span>班级管理</span>
          </router-link>
          <router-link 
            to="/teacher/analysis" 
            class="nav-item"
            :class="{ active: isActive('/teacher/analysis') }"
          >
            <span>数据分析</span>
          </router-link>
          <router-link 
            to="/teacher/feedback" 
            class="nav-item"
            :class="{ active: isActive('/teacher/feedback') }"
          >
            <span>反馈管理</span>
          </router-link>
          <router-link 
            to="/teacher/audit-log" 
            class="nav-item"
            :class="{ active: isActive('/teacher/audit-log') }"
          >
            <span>审计日志</span>
          </router-link>
          <router-link 
            to="/teacher/settings" 
            class="nav-item"
            :class="{ active: isActive('/teacher/settings') }"
          >
            <span>个人设置</span>
          </router-link>
        </nav>
      </div>
      <div class="navbar-right">
        <div class="user-info">
          <el-dropdown trigger="click">
            <div class="user-profile">
              <div class="user-avatar">{{ teacherName.charAt(0) }}</div>
              <span class="user-name">{{ teacherName }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  <span style="margin-left: 8px;">退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <div class="main-wrapper">
      <!-- 内容区 -->
      <div class="content-area">
        <router-view />
      </div>
    </div>

    <!-- AI助手 -->
    <AIAssistant />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AIAssistant from '@/components/AIAssistant.vue'
import { 
  HomeFilled,
  DocumentCopy,
  Warning,
  DataAnalysis,
  ChatDotRound,
  Reading,
  Management,
  SwitchButton,
  Bell
} from '@element-plus/icons-vue'
import { teacherAPI } from '@/api/index'

const route = useRoute()
const router = useRouter()
const storedName = localStorage.getItem('userName')
const storedRole = localStorage.getItem('role')
let defaultName = '教师'
// 检查storedName是否存在且不是纯数字（避免显示用户ID）
if (storedName && !/^\d+$/.test(storedName)) {
  defaultName = storedName
} else {
  // 如果没有userName或userName是纯数字，根据角色显示默认名称
  if (storedRole === '2' || storedRole === 'teacher') {
    defaultName = '教师'
  } else {
    defaultName = '用户'
  }
}
const teacherName = ref(defaultName)

// 获取教师信息
const loadTeacherInfo = async () => {
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) return
    const response = await teacherAPI.getDashboard(userId)
    if (response && (response.teacherName || response.name)) {
      const teacherNameFromApi = response.teacherName || response.name
      teacherName.value = teacherNameFromApi
      // 更新localStorage中的userName
      localStorage.setItem('userName', teacherNameFromApi)
    }
  } catch (error) {
    console.error('加载教师信息失败:', error)
  }
}

// 组件挂载时加载教师信息
onMounted(() => {
  loadTeacherInfo()
})

const pageMap = {
  '/teacher/dashboard': '教学数据概览',
  '/teacher/courses': '课程管理',
  '/teacher/scores': '学生成绩管理',
  '/teacher/warnings': '学业预警管理',
  '/teacher/credit-prediction': '学分预测',
  '/teacher/class-management': '班级管理',
  '/teacher/analysis': '数据分析',
  '/teacher/feedback': '反馈消息',
  '/teacher/audit-log': '审计日志',
  '/teacher/settings': '个人设置'
}

const currentPageTitle = computed(() => {
  return pageMap[route.path] || '教师工作台'
})

const isActive = (path) => {
  return route.path === path
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
  router.push('/login')
}
</script>

<style scoped>
* {
  box-sizing: border-box;
}

.teacher-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

/* 顶部导航栏 */
.top-navbar {
  height: 60px;
  background: #ffffff;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  justify-content: space-between;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 30px;
}

.navbar-right {
  display: flex;
  align-items: center;
}

.logo-image {
  height: 40px;
  object-fit: contain;
}

.main-nav {
  display: flex;
  gap: 20px;
  align-items: center;
}

.nav-item {
  color: #333;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 0;
  position: relative;
  transition: all 0.3s ease;
}

.nav-item:hover {
  color: #4facfe;
}

.nav-item.active {
  color: #4facfe;
  font-weight: 600;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: #4facfe;
}

.navbar-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 20px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.user-profile:hover {
  background: #f0f0f0;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0f0f0;
  color: #333;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
}

/* 主体区域 */
.main-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 内容区 */
.content-area {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  padding: 0;
  margin: 0;
}

.content-area::-webkit-scrollbar {
  width: 6px;
  background: transparent;
}

.content-area::-webkit-scrollbar-track {
  background: transparent;
}

.content-area::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.content-area::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .sidebar {
    width: 180px;
  }
  
  .menu-title {
    font-size: 11px;
  }
  
  .menu-item {
    font-size: 13px;
    padding: 10px 12px;
  }
}

@media (max-width: 768px) {
  .sidebar {
    width: 160px;
  }
  
  .logo-text {
    display: none;
  }
  
  .breadcrumb {
    font-size: 14px;
  }
}
</style>
