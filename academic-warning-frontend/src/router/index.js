import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Dashboard from '../views/Dashboard.vue'
import Scores from '../views/Scores.vue'
import Warnings from '../views/Warnings.vue'
import Assistance from '../views/Assistance.vue'
import Appeals from '../views/Appeals.vue'
import Messages from '../views/Messages.vue'
import Settings from '../views/Settings.vue'
import Statistics from '../views/Statistics.vue'
import NotificationCenter from '../views/student/NotificationCenter.vue'
import BenchmarkAnalysis from '../views/student/BenchmarkAnalysis.vue'
import AppealManagement from '../views/student/AppealManagement.vue'
import AssistanceFeedback from '../views/student/AssistanceFeedback.vue'
import ClassMembers from '../views/student/ClassMembers.vue'
import ClassRanking from '../views/student/ClassRanking.vue'
import StudentLayout from '../views/StudentLayout.vue'
import TeacherDashboard from '../views/TeacherDashboard.vue'
import TeacherRegister from '../views/TeacherRegister.vue'
import TeacherScores from '../views/TeacherScores.vue'
import TeacherWarnings from '../views/TeacherWarnings.vue'
import TeacherAnalysis from '../views/TeacherAnalysis.vue'
import TeacherCreditPrediction from '../views/TeacherCreditPrediction.vue'
import TeacherAuditLog from '../views/TeacherAuditLog.vue'
import TeacherFeedbackManagement from '../views/TeacherFeedbackManagement.vue'
import TeacherFeedback from '../views/TeacherFeedback.vue'
import TeacherCourses from '../views/TeacherCourses.vue'
import TeacherClassManagement from '../views/TeacherClassManagement.vue'
import TeacherMessages from '../views/TeacherMessages.vue'
import TeacherSettings from '../views/TeacherSettings.vue'
import CounselorDashboard from '../views/CounselorDashboard.vue'
import CounselorRegister from '../views/CounselorRegister.vue'
import CounselorStudents from '../views/CounselorStudents.vue'
import CounselorWarnings from '../views/CounselorWarnings.vue'
import CounselorCourses from '../views/CounselorCourses.vue'
import CounselorCreditMonitor from '../views/CounselorCreditMonitor.vue'
import CounselorNotifications from '../views/CounselorNotifications.vue'
import CounselorClassManagement from '../views/CounselorClassManagement.vue'
import CounselorSettings from '../views/CounselorSettings.vue'
import CounselorScores from '../views/CounselorScores.vue'
import CounselorAssistance from '../views/CounselorAssistance.vue'
import CounselorAnalytics from '../views/CounselorAnalytics.vue'
import CounselorCommunication from '../views/CounselorCommunication.vue'
import AdminRegister from '../views/AdminRegister.vue'
import AdminColleges from '../views/AdminColleges.vue'
import AdminMajors from '../views/AdminMajors.vue'
import AdminUsers from '../views/AdminUsers.vue'
import AdminRules from '../views/AdminRules.vue'
import AdminStatistics from '../views/AdminStatistics.vue'
import AdminWarnings from '../views/AdminWarnings.vue'
import AdminCourses from '../views/AdminCourses.vue'
import AdminPermissions from '../views/AdminPermissions.vue'
import AdminMessages from '../views/AdminMessages.vue'
import AdminDataExport from '../views/AdminDataExport.vue'
import AdminClassManagement from '../views/AdminClassManagement.vue'
import AdminSettings from '../views/AdminSettings.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
import CollegeAdminDashboard from '../views/CollegeAdminDashboard.vue'
import CollegeAdminStudents from '../views/CollegeAdminStudents.vue'
import CollegeAdminTeachers from '../views/CollegeAdminTeachers.vue'
import CollegeAdminWarnings from '../views/CollegeAdminWarnings.vue'
import CollegeAdminClasses from '../views/CollegeAdminClasses.vue'
import CollegeAdminAnalysis from '../views/CollegeAdminAnalysis.vue'
import CollegeAdminCounselors from '../views/CollegeAdminCounselors.vue'
import CollegeAdminExport from '../views/CollegeAdminExport.vue'
import CollegeAdminScoreQuery from '../views/CollegeAdminScoreQuery.vue'
import TeacherLayout from '../views/TeacherLayout.vue'
import AdminLayout from '../views/AdminLayout.vue'
import CounselorLayout from '../views/CounselorLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { requiresAuth: false }
  },
  {
    path: '/student',
    component: StudentLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        component: Dashboard,
        meta: { requiresAuth: true }
      },
      {
        path: 'scores',
        component: Scores,
        meta: { requiresAuth: true }
      },
      {
        path: 'warnings',
        component: Warnings,
        meta: { requiresAuth: true }
      },
      {
        path: 'assistance',
        component: Assistance,
        meta: { requiresAuth: true }
      },
      {
        path: 'appeals',
        component: Appeals,
        meta: { requiresAuth: true }
      },
      {
        path: 'messages',
        component: Messages,
        meta: { requiresAuth: true }
      },
      {
        path: 'notification-center',
        component: NotificationCenter,
        meta: { requiresAuth: true }
      },
      {
        path: 'benchmark-analysis',
        component: BenchmarkAnalysis,
        meta: { requiresAuth: true }
      },
      {
        path: 'assistance-feedback',
        component: AssistanceFeedback,
        meta: { requiresAuth: true }
      },
      {
        path: 'class-members',
        component: ClassMembers,
        meta: { requiresAuth: true }
      },
      {
        path: 'class-ranking',
        component: ClassRanking,
        meta: { requiresAuth: true }
      },
      {
        path: 'settings',
        component: Settings,
        meta: { requiresAuth: true }
      },
      {
        path: 'statistics',
        component: Statistics,
        meta: { requiresAuth: true }
      }
    ]
  },
  {
    path: '/dashboard',
    redirect: '/student/dashboard',
    meta: { requiresAuth: true }
  },
  {
    path: '/counselor-register',
    name: 'CounselorRegister',
    component: CounselorRegister,
    meta: { requiresAuth: false }
  },

  {
    path: '/admin-register',
    name: 'AdminRegister',
    component: AdminRegister,
    meta: { requiresAuth: true, allowedRoles: [4, 'admin'] }
  },
  {
    path: '/teacher-register',
    name: 'TeacherRegister',
    component: TeacherRegister,
    meta: { requiresAuth: false }
  },
  {
    path: '/teacher',
    component: TeacherLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'TeacherDashboard',
        component: TeacherDashboard
      },
      {
        path: 'scores',
        name: 'TeacherScores',
        component: TeacherScores
      },
      {
        path: 'warnings',
        name: 'TeacherWarnings',
        component: TeacherWarnings
      },
      {
        path: 'analysis',
        name: 'TeacherAnalysis',
        component: TeacherAnalysis
      },
      {
        path: 'credit-prediction',
        name: 'TeacherCreditPrediction',
        component: TeacherCreditPrediction
      },
      {
        path: 'audit-log',
        name: 'TeacherAuditLog',
        component: TeacherAuditLog
      },
      {
        path: 'feedback-management',
        name: 'TeacherFeedbackManagement',
        component: TeacherFeedbackManagement
      },
      {
        path: 'feedback',
        name: 'TeacherFeedback',
        component: TeacherFeedback
      },
      {
        path: 'courses',
        name: 'TeacherCourses',
        component: TeacherCourses
      },
      {
        path: 'class-management',
        name: 'TeacherClassManagement',
        component: TeacherClassManagement
      },
      {
        path: 'messages',
        name: 'TeacherMessages',
        component: TeacherMessages
      },
      {
        path: 'settings',
        name: 'TeacherSettings',
        component: TeacherSettings
      }
    ]
  },
  {
    path: '/counselor',
    component: CounselorLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'CounselorDashboard',
        component: CounselorDashboard
      },
      {
        path: 'students',
        name: 'CounselorStudents',
        component: CounselorStudents
      },
      {
        path: 'warnings',
        name: 'CounselorWarnings',
        component: CounselorWarnings
      },
      {
        path: 'assistance',
        name: 'CounselorAssistance',
        component: CounselorAssistance
      },
      {
        path: 'scores',
        name: 'CounselorScores',
        component: CounselorScores
      },
      {
        path: 'analytics',
        name: 'CounselorAnalytics',
        component: CounselorAnalytics
      },
      {
        path: 'courses',
        name: 'CounselorCourses',
        component: CounselorCourses
      },
      {
        path: 'credit-monitor',
        name: 'CounselorCreditMonitor',
        component: CounselorCreditMonitor
      },
      {
        path: 'notifications',
        name: 'CounselorNotifications',
        component: CounselorNotifications
      },
      {
        path: 'communication',
        name: 'CounselorCommunication',
        component: CounselorCommunication
      },
      {
        path: 'class-management',
        name: 'CounselorClassManagement',
        component: CounselorClassManagement
      },
      {
        path: 'settings',
        name: 'CounselorSettings',
        component: CounselorSettings
      }
    ]
  },
  {
    path: '/counselor-dashboard',
    redirect: '/counselor/dashboard'
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: AdminDashboard
      },
      {
        path: 'colleges',
        name: 'AdminColleges',
        component: AdminColleges
      },
      {
        path: 'majors',
        name: 'AdminMajors',
        component: AdminMajors
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: AdminUsers
      },
      {
        path: 'rules',
        name: 'AdminRules',
        component: AdminRules
      },
      {
        path: 'statistics',
        name: 'AdminStatistics',
        component: AdminStatistics
      },
      {
        path: 'warnings',
        name: 'AdminWarnings',
        component: AdminWarnings
      },
      {
        path: 'courses',
        name: 'AdminCourses',
        component: AdminCourses
      },
      {
        path: 'permissions',
        name: 'AdminPermissions',
        component: AdminPermissions
      },
      {
        path: 'messages',
        name: 'AdminMessages',
        component: AdminMessages
      },
      {
        path: 'data-export',
        name: 'AdminDataExport',
        component: AdminDataExport
      },
      {
        path: 'class-management',
        name: 'AdminClassManagement',
        component: AdminClassManagement
      },
      {
        path: 'settings',
        name: 'AdminSettings',
        component: AdminSettings
      }
    ]
  },
  // 学院管理员端
  {
    path: '/college-admin',
    component: AdminLayout,
    meta: { requiresAuth: true, role: 'college_admin' },
    redirect: '/college-admin/dashboard',
    children: [
      { path: 'dashboard',  name: 'CollegeAdminDashboard',  component: CollegeAdminDashboard },
      { path: 'students',   name: 'CollegeAdminStudents',   component: CollegeAdminStudents },
      { path: 'teachers',   name: 'CollegeAdminTeachers',   component: CollegeAdminTeachers },
      { path: 'warnings',   name: 'CollegeAdminWarnings',   component: CollegeAdminWarnings },
      { path: 'classes',    name: 'CollegeAdminClasses',    component: CollegeAdminClasses },
      { path: 'analysis',   name: 'CollegeAdminAnalysis',   component: CollegeAdminAnalysis },
      { path: 'counselors', name: 'CollegeAdminCounselors', component: CollegeAdminCounselors },
      { path: 'export',     name: 'CollegeAdminExport',     component: CollegeAdminExport },
      { path: 'scores',    name: 'CollegeAdminScoreQuery', component: CollegeAdminScoreQuery }
    ]
  },
  {
    path: '/notifications',
    redirect: '/dashboard'
  },
  {
    path: '/hybridaction/:pathMatch(.*)*',
    redirect: '/dashboard'
  },
  {
    path: '/',
    redirect: (to) => {
      const hasToken = localStorage.getItem('token')
      if (!hasToken) {
        return '/login'
      }
      // 如果已登录，根据角色跳转
      const role = localStorage.getItem('role')
      if (role === '2' || role === 'teacher') {
        return '/teacher/dashboard'
      } else if (role === '3' || role === 'counselor') {
        return '/counselor/dashboard'
      } else if (role === '4' || role === 'admin') {
        return '/admin/dashboard'
      } else if (role === '5' || role === 'college_admin') {
        return '/college-admin/dashboard'
      } else {
        return '/student/dashboard'
      }
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  const hasToken = localStorage.getItem('token')
  const role = localStorage.getItem('role')
  
  // 如果没有token且需要认证，跳转到登录
  if (to.meta.requiresAuth && !hasToken) {
    next('/login')
    return
  }
  
  // 如果已登录且访问登录/注册页，根据角色跳转到对应首页
  // 注：/admin-register 现在需要管理员身份，不在此处跳转
  if ((to.path === '/login' || to.path === '/register' || to.path === '/teacher-register' || to.path === '/counselor-register') && hasToken) {
    if (role === '2' || role === 'teacher') {
      next('/teacher/dashboard')
    } else if (role === '3' || role === 'counselor') {
      next('/counselor/dashboard')
    } else if (role === '4' || role === 'admin') {
      next('/admin/dashboard')
    } else if (role === '5' || role === 'college_admin') {
      next('/college-admin/dashboard')
    } else {
      // 默认学生
      next('/student/dashboard')
    }
    return
  }
  
  // 角色路由保护：防止跨角色访问
  if (hasToken && to.meta.requiresAuth) {
    const roleNum = Number(role)
    const path = to.path
    // 管理员注册页只允许管理员角色(4)访问
    if (path === '/admin-register' && roleNum !== 4 && role !== 'admin') {
      next('/login')
      return
    }
    // 教师路径只允许教师角色(2)访问
    if (path.startsWith('/teacher') && roleNum !== 2 && role !== 'teacher') {
      if (roleNum === 1) next('/student/dashboard')
      else if (roleNum === 3) next('/counselor/dashboard')
      else if (roleNum === 4) next('/admin/dashboard')
      else next('/login')
      return
    }
    // 学生路径只允许学生角色(1)访问
    if (path.startsWith('/student') && roleNum !== 1 && role !== 'student' && roleNum !== 0) {
      if (roleNum === 2) next('/teacher/dashboard')
      else if (roleNum === 3) next('/counselor/dashboard')
      else if (roleNum === 4) next('/admin/dashboard')
      else next('/login')
      return
    }
    // 辅导员路径只允许辅导员角色(3)访问
    if (path.startsWith('/counselor') && roleNum !== 3 && role !== 'counselor') {
      if (roleNum === 1) next('/student/dashboard')
      else if (roleNum === 2) next('/teacher/dashboard')
      else if (roleNum === 4) next('/admin/dashboard')
      else next('/login')
      return
    }
    // 管理员路径只允许管理员角色(4)访问
    if (path.startsWith('/admin') && roleNum !== 4 && role !== 'admin') {
      if (roleNum === 1) next('/student/dashboard')
      else if (roleNum === 2) next('/teacher/dashboard')
      else if (roleNum === 3) next('/counselor/dashboard')
      else next('/login')
      return
    }
    // 学院管理员路径只允许学院管理员角色(5)访问
    if (path.startsWith('/college-admin') && roleNum !== 5 && role !== 'college_admin') {
      if (roleNum === 1) next('/student/dashboard')
      else if (roleNum === 2) next('/teacher/dashboard')
      else if (roleNum === 3) next('/counselor/dashboard')
      else if (roleNum === 4) next('/admin/dashboard')
      else next('/login')
      return
    }
  }
  
  next()
})

export default router
