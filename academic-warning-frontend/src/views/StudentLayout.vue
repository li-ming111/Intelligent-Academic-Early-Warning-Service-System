<template>
  <div class="student-layout">
    <!-- 顶部导航栏 -->
    <div class="top-navbar">
      <div class="navbar-left">
        <div class="logo">
          <img src="@/assets/logo.png" alt="哈尔滨信息工程学院" class="logo-image">
        </div>
        <nav class="main-nav">
          <router-link 
            to="/student/dashboard" 
            class="nav-item"
            :class="{ active: isActive('/student/dashboard') }"
          >
            <span>学业看板</span>
          </router-link>
          <router-link 
            to="/student/scores" 
            class="nav-item"
            :class="{ active: isActive('/student/scores') }"
          >
            <span>成绩管理</span>
          </router-link>
          <router-link 
            to="/student/warnings" 
            class="nav-item"
            :class="{ active: isActive('/student/warnings') }"
          >
            <span>预警管理</span>
          </router-link>
          <router-link 
            to="/student/assistance" 
            class="nav-item"
            :class="{ active: isActive('/student/assistance') }"
          >
            <span>帮扶计划</span>
          </router-link>
          <router-link 
            to="/student/appeals" 
            class="nav-item"
            :class="{ active: isActive('/student/appeals') }"
          >
            <span>申诉管理</span>
          </router-link>
          <router-link 
            to="/student/messages" 
            class="nav-item"
            :class="{ active: isActive('/student/messages') }"
          >
            <span>消息中心</span>
          </router-link>
          <router-link
            to="/student/benchmark-analysis"
            class="nav-item"
            :class="{ active: isActive('/student/benchmark-analysis') }"
          >
            <span>对标分析</span>
          </router-link>
          <router-link
            to="/student/timetable"
            class="nav-item"
            :class="{ active: isActive('/student/timetable') }"
          >
            <span>我的课表</span>
          </router-link>
          <router-link
            to="/student/statistics"
            class="nav-item"
            :class="{ active: isActive('/student/statistics') }"
          >
            <span>统计分析</span>
          </router-link>
          <router-link 
            to="/student/settings" 
            class="nav-item"
            :class="{ active: isActive('/student/settings') }"
          >
            <span>个人设置</span>
          </router-link>
        </nav>
      </div>
      <div class="navbar-right">
        <div class="user-info">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notify-badge">
            <el-icon class="notification-icon" @click="goMessages"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click">
            <div class="user-profile">
              <div class="user-avatar">{{ studentName.charAt(0) }}</div>
              <span class="user-name">{{ studentName }}</span>
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

    <!-- 主内容区 -->
    <div class="main-content">
      <router-view />
    </div>

    <!-- AI小助手 -->
    <AIAssistant />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, SwitchButton } from '@element-plus/icons-vue'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import AIAssistant from '@/components/AIAssistant.vue'

const route = useRoute()
const router = useRouter()
// Login.vue 写入的是 `name`；`userName` 只有教师/辅导员布局才会写。
// 只读后者会让学生顶栏永远显示兜底的「学生」。
const storedName = localStorage.getItem('name') || localStorage.getItem('userName')
const storedRole = localStorage.getItem('role')
const unreadCount = ref(0)
let pollTimer = null
let defaultName = '学生'
// 检查storedName是否存在且不是纯数字（避免显示用户ID）
if (storedName && !/^\d+$/.test(storedName)) {
  defaultName = storedName
} else {
  // 如果没有userName或userName是纯数字，根据角色显示默认名称
  if (storedRole === '1' || storedRole === 'student') {
    defaultName = '学生'
  } else {
    defaultName = '用户'
  }
}
const studentName = ref(defaultName)

onMounted(() => {
  fetchUnread()
  pollTimer = setInterval(fetchUnread, 30000) // 30秒轮询
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function fetchUnread() {
  try {
    const uid = getUserId()
    if (!uid) return
    const res = await studentAPI.getUnreadCount(uid)
    // 后端回的是 `Map.of("unreadCount", count)`（StudentServiceImpl.getUnreadMessageCount），
    // 既不是裸数字、也没有 `.data` —— 原来两个分支都不中，**角标恒 0**（一条新消息也看不见）。
    if (typeof res === 'number') unreadCount.value = res
    else if (res?.unreadCount !== undefined) unreadCount.value = Number(res.unreadCount) || 0
    else if (res?.data !== undefined) unreadCount.value = Number(res.data) || 0
  } catch (e) {}
}

function goMessages() {
  router.push('/student/messages')
}

const isActive = (path) => {
  return route.path === path
}

const handleLogout = () => {
  // 教师/辅导员布局会写 `userName`，学生登录写 `name`；不清会给下一个
  // 在本机登录的角色留下上一个人的姓名。
  for (const key of ['token', 'userId', 'username', 'name', 'userName', 'role', 'email', 'phone', 'studentId']) {
    localStorage.removeItem(key)
  }
  router.push('/login')
}
</script>

<style scoped>
* {
  box-sizing: border-box;
}

.student-layout {
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

.logo-image { height: 40px; max-height: 40px; max-width: 120px; object-fit: contain; display: inline-block; }

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

.notify-badge { margin-right: 10px; }
.notify-badge :deep(.el-badge__content) { border: 2px solid #fff; }

.notification-icon {
  font-size: 20px;
  color: #333;
  transition: all 0.3s ease;
  cursor: pointer;
}

.notification-icon:hover {
  color: #7c3aed;
  transform: scale(1.1);
}

/* 主内容区域 */
.main-content {
  flex: 1;
  overflow-y: auto;
  background: #f5f7fb;
}

/* 主体区域 */
.main-wrapper {
  flex: 1;
  overflow: hidden;
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
  .navbar-left {
    gap: 15px;
  }
  
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
