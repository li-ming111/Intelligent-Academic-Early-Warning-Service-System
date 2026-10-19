<template>
  <div class="admin-layout">
    <!-- 顶部导航栏 -->
    <div class="top-navbar">
      <div class="navbar-left">
        <div class="logo">
          <img src="@/assets/logo.png" alt="哈尔滨信息工程学院" class="logo-image">
        </div>
        <nav class="main-nav">
          <!-- 学校管理员(4)看全菜单 -->
          <template v-if="!isCollegeAdmin">
            <router-link to="/admin/dashboard" class="nav-item" :class="{ active: isActive('/admin/dashboard') }"><span>首页</span></router-link>
            <router-link to="/admin/colleges" class="nav-item" :class="{ active: isActive('/admin/colleges') }"><span>学院管理</span></router-link>
            <router-link to="/admin/majors" class="nav-item" :class="{ active: isActive('/admin/majors') }"><span>专业管理</span></router-link>
            <router-link to="/admin/courses" class="nav-item" :class="{ active: isActive('/admin/courses') }"><span>课程管理</span></router-link>
            <router-link to="/admin/course-plans" class="nav-item" :class="{ active: isActive('/admin/course-plans') }"><span>排课管理</span></router-link>
            <router-link to="/admin/users" class="nav-item" :class="{ active: isActive('/admin/users') }"><span>用户管理</span></router-link>
            <router-link to="/admin/rules" class="nav-item" :class="{ active: isActive('/admin/rules') }"><span>规则管理</span></router-link>
            <router-link to="/admin/warnings" class="nav-item" :class="{ active: isActive('/admin/warnings') }"><span>预警管理</span></router-link>
            <router-link to="/admin/appeals" class="nav-item" :class="{ active: isActive('/admin/appeals') }"><span>申诉审核</span></router-link>
            <router-link to="/admin/statistics" class="nav-item" :class="{ active: isActive('/admin/statistics') }"><span>数据分析</span></router-link>
            <router-link to="/admin/messages" class="nav-item" :class="{ active: isActive('/admin/messages') }"><span>消息通知</span></router-link>
            <router-link to="/admin/data-export" class="nav-item" :class="{ active: isActive('/admin/data-export') }"><span>数据导出</span></router-link>
            <router-link to="/admin/class-management" class="nav-item" :class="{ active: isActive('/admin/class-management') }"><span>班级管理申请</span></router-link>
            <router-link to="/admin/settings" class="nav-item" :class="{ active: isActive('/admin/settings') }"><span>个人设置</span></router-link>
          </template>
          <!-- 学院管理员(5)菜单 -->
          <template v-else>
            <router-link to="/college-admin/dashboard"  class="nav-item" :class="{ active: isActive('/college-admin/dashboard')  }"><span>首页</span></router-link>
            <router-link to="/college-admin/students"   class="nav-item" :class="{ active: isActive('/college-admin/students')   }"><span>学生管理</span></router-link>
            <router-link to="/college-admin/teachers"   class="nav-item" :class="{ active: isActive('/college-admin/teachers')   }"><span>教师管理</span></router-link>
            <router-link to="/college-admin/warnings"   class="nav-item" :class="{ active: isActive('/college-admin/warnings')   }"><span>预警管理</span></router-link>
            <router-link to="/college-admin/classes"    class="nav-item" :class="{ active: isActive('/college-admin/classes')    }"><span>班级管理</span></router-link>
            <router-link to="/college-admin/analysis"   class="nav-item" :class="{ active: isActive('/college-admin/analysis')   }"><span>成绩分析</span></router-link>
            <router-link to="/college-admin/scores"     class="nav-item" :class="{ active: isActive('/college-admin/scores')     }"><span>成绩查询</span></router-link>
            <router-link to="/college-admin/counselors" class="nav-item" :class="{ active: isActive('/college-admin/counselors') }"><span>辅导员</span></router-link>
            <router-link to="/college-admin/export"     class="nav-item" :class="{ active: isActive('/college-admin/export')     }"><span>数据导出</span></router-link>
          </template>
        </nav>
      </div>
      <div class="navbar-right">
        <div class="user-info">
          <el-dropdown trigger="click">
            <div class="user-profile">
              <div class="user-avatar">{{ adminName.charAt(0) }}</div>
              <span class="user-name">{{ adminName }}</span>
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
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AIAssistant from '@/components/AIAssistant.vue'
import { 
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
// 优先读登录时写入的 'name'（Login.vue 存的是真名）。
// 不能只读 'userName'：全项目没有任何地方为管理员/学院管理员写它，
// 而下面 loadAdminInfo 想拿 /admin/dashboard 兜正姓名 —— 该接口实测只返回
// totalMajors/totalUsers/totalColleges/totalWarnings/totalStudents/totalCourses/recentWarnings，
// 没有 adminName/name 字段，兜正分支是死的，只读 'userName' 会永久停在兜底文案
//（学院管理员侧栏一直显示「学院管理员」而不是「张建国」）。
const storedName = localStorage.getItem('name') || localStorage.getItem('userName')
  const storedRole = localStorage.getItem('role')
const isCollegeAdmin = computed(() => storedRole === '5' || storedRole === 'college_admin')
let defaultName = '管理员'
if (storedName && !/^\d+$/.test(storedName)) {
  defaultName = storedName
} else {
  if (storedRole === '4' || storedRole === 'admin') {
    defaultName = '管理员'
  } else if (isCollegeAdmin.value) {
    defaultName = '学院管理员'
  } else {
    defaultName = '用户'
  }
}
const adminName = ref(defaultName)

// 2026-10-02（W4.S3）：删掉 `loadAdminInfo`（原来挂在 onMounted 上）。
// 它 `await adminAPI.getDashboard()` 之后只认 `response.adminName || response.name`，
// 而该接口实测只返回 totalMajors/totalUsers/totalColleges/totalWarnings/totalStudents/
// totalCourses/recentWarnings —— 两个 key 都不存在 ⇒ 这个 if 永远不成立，
// 唯一的副作用就是每次进管理员页面多打一次 dashboard 请求。姓名已经在上面从
// localStorage('name') 取到了，这里没有可补的信息。

const pageMap = {
  '/admin/dashboard': '数据统计',
  '/admin/colleges': '学院管理',
  '/admin/majors': '专业管理',
  '/admin/courses': '课程管理',
  '/admin/users': '用户管理',
  '/admin/rules': '规则管理',
  '/admin/warnings': '预警管理',
  '/admin/appeals': '成绩申诉审核',
  '/admin/statistics': '数据分析',
  '/admin/messages': '消息通知',
  '/admin/data-export': '数据导出',
  '/admin/class-management': '班级管理申请',
  '/admin/settings': '个人设置',
  '/college-admin/dashboard': '学院控制台',
  '/college-admin/students': '学生管理',
  '/college-admin/teachers': '教师管理',
  '/college-admin/warnings': '预警管理',
  '/college-admin/classes': '班级管理',
  '/college-admin/analysis': '成绩分析',
  '/college-admin/scores': '成绩查询',
  '/college-admin/counselors': '辅导员管理',
  '/college-admin/export': '数据导出'
}

const currentPageTitle = computed(() => {
  return pageMap[route.path] || '管理工作台'
})

const isActive = (path) => {
  const base = path.split('#')[0]
  return route.path === base
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

.admin-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
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
  flex-wrap: wrap;
  gap: 20px;
}

.dark-mode .top-navbar {
  background: #1f1f1f;
  border-bottom-color: #333333;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 30px;
  flex-wrap: wrap;
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
  gap: 15px;
  align-items: center;
  flex-wrap: wrap;
}

.nav-item {
  color: #333;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 0;
  position: relative;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.dark-mode .nav-item {
  color: #e5e5e5;
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

.dark-mode .user-profile:hover {
  background: #333;
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

.dark-mode .user-avatar {
  background: #333;
  color: #e5e5e5;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
}

.dark-mode .user-name {
  color: #e5e5e5;
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
  background: #f5f7fa;
  padding: 0;
  margin: 0;
}

.dark-mode .content-area {
  background: #141414;
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
@media (max-width: 1400px) {
  .main-nav {
    gap: 10px;
  }
  
  .nav-item {
    font-size: 13px;
  }
}

@media (max-width: 1200px) {
  .top-navbar {
    padding: 0 15px;
    gap: 15px;
  }
  
  .navbar-left {
    gap: 20px;
  }
  
  .main-nav {
    gap: 10px;
  }
  
  .nav-item {
    font-size: 12px;
  }
}

@media (max-width: 768px) {
  .top-navbar {
    padding: 0 10px;
    gap: 10px;
  }
  
  .navbar-left {
    gap: 15px;
  }
  
  .main-nav {
    gap: 8px;
  }
  
  .nav-item {
    font-size: 11px;
  }
  
  .user-name {
    display: none;
  }
}
</style>
