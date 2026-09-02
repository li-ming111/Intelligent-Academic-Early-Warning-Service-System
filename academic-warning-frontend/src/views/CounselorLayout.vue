<template>
  <div class="counselor-layout">
    <!-- 顶部导航栏 -->
    <div class="top-navbar">
      <div class="navbar-left">
        <div class="logo">
          <img src="@/assets/logo.png" alt="哈尔滨信息工程学院" class="logo-image">
        </div>
        <nav class="main-nav">
          <router-link 
            to="/counselor/dashboard" 
            class="nav-item"
            :class="{ active: isActive('/counselor/dashboard') }"
          >
            <span>学情视窗</span>
          </router-link>
          <router-link 
            to="/counselor/students" 
            class="nav-item"
            :class="{ active: isActive('/counselor/students') }"
          >
            <span>学生管理</span>
          </router-link>
          <router-link 
            to="/counselor/warnings" 
            class="nav-item"
            :class="{ active: isActive('/counselor/warnings') }"
          >
            <span>预警管理</span>
          </router-link>
          <router-link
            to="/counselor/assistance"
            class="nav-item"
            :class="{ active: isActive('/counselor/assistance') }"
          >
            <span>帮扶计划</span>
          </router-link>
          <router-link
            to="/counselor/communication"
            class="nav-item"
            :class="{ active: isActive('/counselor/communication') }"
          >
            <span>沟通记录</span>
          </router-link>
          <router-link 
            to="/counselor/scores" 
            class="nav-item"
            :class="{ active: isActive('/counselor/scores') }"
          >
            <span>成绩跟踪</span>
          </router-link>
          <router-link 
            to="/counselor/analytics" 
            class="nav-item"
            :class="{ active: isActive('/counselor/analytics') }"
          >
            <span>数据分析</span>
          </router-link>
          <router-link 
            to="/counselor/notifications" 
            class="nav-item"
            :class="{ active: isActive('/counselor/notifications') }"
          >
            <span>通知中心</span>
          </router-link>
          <router-link 
            to="/counselor/courses" 
            class="nav-item"
            :class="{ active: isActive('/counselor/courses') }"
          >
            <span>选修课</span>
          </router-link>
          <router-link 
            to="/counselor/class-management" 
            class="nav-item"
            :class="{ active: isActive('/counselor/class-management') }"
          >
            <span>班级管理</span>
          </router-link>
          <router-link 
            to="/counselor/settings" 
            class="nav-item"
            :class="{ active: isActive('/counselor/settings') }"
          >
            <span>个人设置</span>
          </router-link>
        </nav>
      </div>
      <div class="navbar-right">
        <div class="user-info">
          <el-dropdown trigger="click">
            <div class="user-profile">
              <div class="user-avatar">{{ counselorName.charAt(0) }}</div>
              <span class="user-name">{{ counselorName }}</span>
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
  SwitchButton
} from '@element-plus/icons-vue'
import { counselorAPI } from '@/api/index'

const route = useRoute()
const router = useRouter()
const storedName = localStorage.getItem('userName')
const storedRole = localStorage.getItem('role')
let defaultName = '辅导员'
// 检查storedName是否存在且不是纯数字（避免显示用户ID）
if (storedName && !/^\d+$/.test(storedName)) {
  defaultName = storedName
} else {
  // 如果没有userName或userName是纯数字，根据角色显示默认名称
  if (storedRole === '3' || storedRole === 'counselor') {
    defaultName = '辅导员'
  } else {
    defaultName = '用户'
  }
}
const counselorName = ref(defaultName)

// 获取辅导员信息
const loadCounselorInfo = async () => {
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) return
    const response = await counselorAPI.getDashboard(userId)
    if (response && response.code === 200 && response.data) {
      const data = response.data
      if (data.counselorName || data.name) {
        const counselorNameFromApi = data.counselorName || data.name
        counselorName.value = counselorNameFromApi
        // 更新localStorage中的userName
        localStorage.setItem('userName', counselorNameFromApi)
      }
    }
  } catch (error) {
    console.error('加载辅导员信息失败:', error)
  }
}

// 组件挂载时加载辅导员信息
onMounted(() => {
  loadCounselorInfo()
})

const pageMap = {
  '/counselor/dashboard': '学情视窗',
  '/counselor/students': '学生管理',
  '/counselor/warnings': '预警管理',
  '/counselor/assistance': '帮扶计划',
  '/counselor/communication': '沟通记录',
  '/counselor/scores': '成绩跟踪',
  '/counselor/analytics': '数据分析',
  '/counselor/notifications': '通知中心',
  '/counselor/courses': '选修课',
  '/counselor/class-management': '班级管理',
  '/counselor/settings': '个人设置'
}

const currentPageTitle = computed(() => {
  return pageMap[route.path] || '辅导员工作台'
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

.counselor-layout {
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
  display: flex;
  overflow: hidden;
}

/* 内容区 */
.content-area {
  flex: 1;
  overflow-y: auto;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  padding: 0;
  margin: 0;
  width: 100%;
}

/* 确保表格和卡片充满宽度 */
.content-area :deep(.el-table) {
  width: 100% !important;
}

.content-area :deep(.el-card) {
  width: 100% !important;
}

.content-area :deep(> div) {
  width: 100%;
  max-width: 100%;
}

.content-area::-webkit-scrollbar {
  width: 0 !important;
  background: transparent !important;
}

.content-area::-webkit-scrollbar-track {
  background: transparent !important;
  display: none !important;
}

.content-area::-webkit-scrollbar-thumb {
  background: transparent !important;
  display: none !important;
}

.content-area::-webkit-scrollbar-thumb:hover {
  background: transparent !important;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .main-nav {
    gap: 15px;
  }
  
  .nav-item {
    font-size: 13px;
  }
}

@media (max-width: 768px) {
  .main-nav {
    gap: 10px;
  }
  
  .nav-item {
    font-size: 12px;
  }
  
  .user-name {
    display: none;
  }
}
</style>