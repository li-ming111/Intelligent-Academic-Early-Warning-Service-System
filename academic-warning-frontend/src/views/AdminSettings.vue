<template>
  <div class="admin-settings-container">
    <div class="page-header">
      <h1>个人设置</h1>
      <p>管理您的账户信息和系统设置</p>
    </div>

    <el-row :gutter="20">
      <!-- 左侧菜单 -->
      <el-col :xs="24" :sm="24" :md="6">
        <div class="settings-menu">
          <div
            v-for="item in menuItems"
            :key="item.id"
            class="menu-item"
            :class="{ active: activeMenu === item.id }"
            @click="activeMenu = item.id"
          >
            <el-icon class="menu-icon">
              <component :is="item.icon"></component>
            </el-icon>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </el-col>

      <!-- 右侧内容 -->
      <el-col :xs="24" :sm="24" :md="18">
        <!-- 账户信息 -->
        <el-card v-if="activeMenu === 'account'" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><User /></el-icon>
              <span>账户信息</span>
            </div>
          </template>

          <el-form :model="accountInfo" label-width="120px">
            <el-form-item label="用户名">
              <el-input v-model="accountInfo.username" disabled></el-input>
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="accountInfo.name"></el-input>
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="accountInfo.email" type="email"></el-input>
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="accountInfo.phone"></el-input>
            </el-form-item>
            <el-form-item label="账户创建">
              <el-input v-model="accountInfo.createdAt" disabled></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="updateAccount">保存修改</el-button>
              <el-button @click="resetAccountForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 修改密码 -->
        <el-card v-if="activeMenu === 'password'" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><Lock /></el-icon>
              <span>修改密码</span>
            </div>
          </template>

          <el-form :model="passwordForm" label-width="120px">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入当前密码"></el-input>
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" placeholder="至少8位，包含字母和数字"></el-input>
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword">确认修改</el-button>
              <el-button @click="resetPasswordForm">取消</el-button>
            </el-form-item>
          </el-form>

          <el-divider></el-divider>

          <div class="password-tips">
            <h4>密码安全提示</h4>
            <ul>
              <li>建议设置复杂密码，包含大小写字母、数字和特殊符号</li>
              <li>定期修改密码，至少90天修改一次</li>
              <li>不要使用与其他账户相同的密码</li>
              <li>避免使用容易被猜测的密码（生日、姓名等）</li>
            </ul>
          </div>
        </el-card>

        <!-- 系统偏好 -->
        <el-card v-if="activeMenu === 'preferences'" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><Setting /></el-icon>
              <span>系统偏好</span>
            </div>
          </template>

          <el-form :model="preferencesForm" label-width="150px">
            <el-form-item label="主题色调">
              <el-select v-model="preferencesForm.theme" placeholder="选择主题">
                <el-option label="浅色（默认）" value="light"></el-option>
                <el-option label="深色" value="dark"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="语言">
              <el-select v-model="preferencesForm.language" placeholder="选择语言">
                <el-option label="中文" value="zh"></el-option>
                <el-option label="English" value="en"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="页面大小">
              <el-select v-model="preferencesForm.pageSize" placeholder="选择页面显示条数">
                <el-option label="10条" value="10"></el-option>
                <el-option label="20条" value="20"></el-option>
                <el-option label="50条" value="50"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="消息通知">
              <el-switch v-model="preferencesForm.notifications"></el-switch>
            </el-form-item>

            <el-form-item label="邮件通知">
              <el-switch v-model="preferencesForm.emailNotifications"></el-switch>
            </el-form-item>

            <el-form-item label="操作确认">
              <el-switch v-model="preferencesForm.confirmDelete"></el-switch>
              <span style="color: #666; margin-left: 10px;">删除操作时显示确认弹窗</span>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="updatePreferences">保存设置</el-button>
              <el-button @click="resetPreferencesForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 安全日志 -->
        <el-card v-if="activeMenu === 'security'" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><Warning /></el-icon>
              <span>安全日志</span>
            </div>
          </template>

          <div class="security-summary">
            <el-row :gutter="20">
              <el-col :xs="24" :sm="12" :md="6">
                <div class="summary-item">
                  <div class="summary-label">账户安全</div>
                  <el-tag type="success">正常</el-tag>
                </div>
              </el-col>
              <el-col :xs="24" :sm="12" :md="6">
                <div class="summary-item">
                  <div class="summary-label">最后登录</div>
                  <div class="summary-value">{{ lastLoginTime }}</div>
                </div>
              </el-col>
              <el-col :xs="24" :sm="12" :md="12">
                <div class="summary-item">
                  <div class="summary-label">活跃设备数</div>
                  <div class="summary-value">1台</div>
                </div>
              </el-col>
            </el-row>
          </div>

          <el-divider></el-divider>

          <h4>登录历史（最近15天）</h4>
          <el-table :data="securityLogs" stripe size="small" v-loading="logsLoading">
            <el-table-column prop="loginTime" label="登录时间" width="180"></el-table-column>
            <el-table-column prop="device" label="设备" width="120"></el-table-column>
            <el-table-column prop="ipAddress" label="IP地址" width="140"></el-table-column>
            <el-table-column prop="location" label="位置"></el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'success'" type="success">成功</el-tag>
                <el-tag v-else type="danger">失败</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 操作日志（审计日志） -->
        <el-card v-if="activeMenu === 'audit'" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>操作日志</span>
            </div>
          </template>

          <div class="audit-filter">
            <el-select v-model="auditFilter.entityType" placeholder="全部对象" clearable
                       style="width: 150px" @change="reloadAuditLogs">
              <el-option v-for="o in auditEntityOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select v-model="auditFilter.actionType" placeholder="全部动作" clearable
                       style="width: 150px" @change="reloadAuditLogs">
              <el-option v-for="o in auditActionOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-button @click="reloadAuditLogs">刷新</el-button>
          </div>

          <el-table :data="auditLogs" stripe size="small" v-loading="auditLoading">
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作人" width="130">
              <template #default="{ row }">
                <span v-if="row.username">{{ row.username }}</span>
                <span v-else class="audit-unknown">未知</span>
              </template>
            </el-table-column>
            <el-table-column label="动作" width="120">
              <template #default="{ row }">{{ actionText(row.actionType) }}</template>
            </el-table-column>
            <el-table-column prop="details" label="详情"></el-table-column>
          </el-table>

          <div class="audit-pager">
            <el-pagination background layout="prev, pager, next, total"
                           :total="auditTotal" :page-size="auditFilter.size"
                           :current-page="auditFilter.page"
                           @current-change="onAuditPageChange" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Setting, Warning, Document } from '@element-plus/icons-vue'
import { adminAPI } from '@/api/index'
import { applyThemeMode } from '@/config/theme'

const activeMenu = ref('account')
const logsLoading = ref(false)

const menuItems = [
  { id: 'account', label: '账户信息', icon: User },
  { id: 'password', label: '修改密码', icon: Lock },
  { id: 'preferences', label: '系统偏好', icon: Setting },
  { id: 'security', label: '安全日志', icon: Warning },
  { id: 'audit', label: '操作日志', icon: Document }
]

// 操作日志：2026-10-02 起 audit_logs 真的有写入方了（管理端关键写操作各记一条），
// 此前它是 0 行的空表，也没有任何列表读取口。
const auditLogs = ref([])
const auditTotal = ref(0)
const auditLoading = ref(false)
const auditFilter = reactive({ page: 1, size: 20, entityType: '', actionType: '' })
const auditEntityOptions = [
  { label: '账号', value: 'USER' },
  { label: '预警规则', value: 'WARNING_RULE' },
  { label: '学业预警', value: 'WARNING' },
  { label: '成绩申诉', value: 'APPEAL' }
]
const auditActionOptions = [
  { label: '新建', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '处理', value: 'HANDLE' },
  { label: '审核', value: 'REVIEW' },
  { label: '重置口令', value: 'RESET_PASSWORD' },
  { label: '启停', value: 'TOGGLE_STATUS' },
  { label: '审批', value: 'APPROVE' }
]

/** 后端存的是大写枚举，直接显示对管理员不友好。 */
const actionText = (a) => {
  const hit = auditActionOptions.find(o => o.value === a)
  return hit ? hit.label : (a || '--')
}

const reloadAuditLogs = () => { auditFilter.page = 1; loadAuditLogs() }
const onAuditPageChange = (p) => { auditFilter.page = p; loadAuditLogs() }

const loadAuditLogs = async () => {
  auditLoading.value = true
  try {
    // 空串会被 axios 原样发出去，后端已把空串归一成"不过滤"，这里不再特殊处理。
    const res = await adminAPI.getAuditLogs({
      page: auditFilter.page,
      size: auditFilter.size,
      entityType: auditFilter.entityType || undefined,
      actionType: auditFilter.actionType || undefined
    })
    auditLogs.value = Array.isArray(res?.items) ? res.items : []
    auditTotal.value = Number(res?.total) || 0
  } catch (e) {
    console.error('加载操作日志失败:', e)
    auditLogs.value = []
    auditTotal.value = 0
  } finally {
    auditLoading.value = false
  }
}

const accountInfo = ref({
  username: '',
  name: '',
  email: '',
  phone: '',
  createdAt: ''
})

const accountInfoBackup = ref({})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const preferencesForm = ref({
  theme: 'light',
  language: 'zh',
  pageSize: '20',
  notifications: true,
  emailNotifications: true,
  confirmDelete: true
})

const securityLogs = ref([])
const lastLoginTime = ref('')

onMounted(async () => {
  await loadAdminInfo()
  await loadSecurityLogs()
  await loadAuditLogs()
  await loadPreferences()
})

// 加载管理员信息
const loadAdminInfo = async () => {
  try {
    const username = localStorage.getItem('username') || '管理员'
    const name = localStorage.getItem('userName') || '系统管理员'
    const email = localStorage.getItem('email') || ''
    const userId = localStorage.getItem('userId')
    
    // 从后端获取准确信息
    let createdAt = ''
    if (userId) {
      try {
        const userInfo = await adminAPI.getUserById(parseInt(userId))
        if (userInfo) {
          createdAt = userInfo.createdAt || ''
        }
      } catch (e) { console.warn('获取用户信息失败:', e.message) }
    }
    
    accountInfo.value = {
      username: username,
      name: name,
      email: email,
      phone: localStorage.getItem('phone') || '',
      createdAt: createdAt || new Date().toISOString().split('T')[0]
    }
    accountInfoBackup.value = JSON.parse(JSON.stringify(accountInfo.value))
  } catch (error) {
    console.error('加载管理员信息失败:', error)
  }
}

// 更新账户信息
const updateAccount = async () => {
  try {
    if (!accountInfo.value.name) {
      ElMessage.error('姓名不能为空')
      return
    }
    if (accountInfo.value.email && !isValidEmail(accountInfo.value.email)) {
      ElMessage.error('邮箱格式不正确')
      return
    }
    
    const userId = localStorage.getItem('userId')
    if (!userId) {
      ElMessage.error('未取到当前账号，请重新登录')
      return
    }

    // 真的写库：`users` 表是账号属性的**唯一权威源**，与 student/teacher/counselor
    // 的设置页走同一条路径（PUT /users/update/{自己的id}）。
    // 2026-09-30 之前这里**只改 localStorage 就弹成功** —— 而 loadAdminInfo 又优先
    // 从 localStorage 读姓名/邮箱，所以页面看着"改成功了"，库里那行一个字没动。
    await adminAPI.updateProfile(parseInt(userId), {
      name: accountInfo.value.name,
      email: accountInfo.value.email,
      phone: accountInfo.value.phone
    })

    // 库写成功之后再落 localStorage。顺序反了的话，接口失败时本地已经"改"了，
    // 页面显示与库不一致 —— 正是上面那个坑的翻版。
    localStorage.setItem('userName', accountInfo.value.name)
    localStorage.setItem('email', accountInfo.value.email)
    localStorage.setItem('phone', accountInfo.value.phone)

    ElMessage.success('账户信息已更新')
    accountInfoBackup.value = JSON.parse(JSON.stringify(accountInfo.value))
  } catch (error) {
    console.error('更新账户信息失败:', error)
    ElMessage.error(error?.message || '更新失败')
  }
}

const resetAccountForm = () => {
  accountInfo.value = JSON.parse(JSON.stringify(accountInfoBackup.value))
}

// 修改密码
const changePassword = async () => {
  if (!passwordForm.value.oldPassword) {
    ElMessage.error('请输入当前密码')
    return
  }
  if (!passwordForm.value.newPassword) {
    ElMessage.error('请输入新密码')
    return
  }
  if (passwordForm.value.newPassword.length < 8) {
    ElMessage.error('新密码至少需要8位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }

  const userId = localStorage.getItem('userId')
  if (!userId) {
    ElMessage.error('未取到当前账号，请重新登录')
    return
  }

  try {
    // 真的打后端。2026-09-30 之前这里**注释写着"调用后端修改密码接口"、
    // 下一行就直接弹「密码修改成功」**，一个请求都没发出去 —— 管理员以为改了口令，
    // 实际还是原来的（答辩话术要讲 BCrypt，这个按钮恰好是反例，现在补真了）。
    // 后端 /auth/change-password 会**校验旧密码**：旧密码不对 → 里层 code:500，
    // `client.js` 把它们 reject 成 Error(message)，所以成功提示只可能在真改成功时出现。
    // 身份以网关注入的 `X-User-Id` 为准，body 里的 userId 只是"未经网关"时的兜底。
    await adminAPI.changePassword({
      userId: parseInt(userId),
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功！')
    resetPasswordForm()
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error(error?.message || '密码修改失败')
  }
}

const resetPasswordForm = () => {
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
}

// 加载系统偏好
const loadPreferences = async () => {
  try {
    const saved = localStorage.getItem('adminPreferences')
    if (saved) {
      const prefs = JSON.parse(saved)
      preferencesForm.value = { ...preferencesForm.value, ...prefs }
    }
  } catch (error) {
    console.error('加载系统偏好失败:', error)
  }
}

// 更新系统偏好
const updatePreferences = async () => {
  try {
    // 保存到 localStorage
    localStorage.setItem('adminPreferences', JSON.stringify(preferencesForm.value))
    
    // 应用主题设置（使用统一的主题切换函数）
    applyThemeMode(preferencesForm.value.theme)
    
    // 应用语言设置
    applyLanguage(preferencesForm.value.language)
    
    // 保存页面大小设置
    localStorage.setItem('pageSize', preferencesForm.value.pageSize)
    
    ElMessage.success('系统偏好已保存')
  } catch (error) {
    console.error('保存偏好失败:', error)
    ElMessage.error('保存失败')
  }
}

// 应用语言
const applyLanguage = (language) => {
  localStorage.setItem('language', language)
  // 这里可以集成国际化库(i18n)来完整实现多语言
  console.log('语言已切换为:', language === 'zh' ? '中文' : 'English')
}

const resetPreferencesForm = () => {
  preferencesForm.value = {
    theme: 'light',
    language: 'zh',
    pageSize: '20',
    notifications: true,
    emailNotifications: true,
    confirmDelete: true
  }
}

// 加载安全日志
const loadSecurityLogs = async () => {
  logsLoading.value = true
  try {
    const userId = localStorage.getItem('userId')
    if (userId) {
      const response = await adminAPI.getLoginLogs(parseInt(userId))
      if (Array.isArray(response)) {
        securityLogs.value = response
        if (response.length > 0) {
          lastLoginTime.value = formatDateTime(response[0].loginTime)
        }
      }
    }
  } catch (error) {
    console.error('加载安全日志失败:', error)
    securityLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

const formatDateTime = (dt) => {
  if (!dt) return ''
  const d = new Date(dt)
  return d.toLocaleString('zh-CN')
}

// 邮箱验证
const isValidEmail = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}
</script>

<style scoped>
.admin-settings-container {
  padding: 20px;
  background-color: #f8f9fa !important;
  min-height: 100vh;
}

.dark-mode .admin-settings-container {
  background-color: #141414 !important;
}

.page-header {
  margin-bottom: 30px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #333;
  font-weight: bold;
}

.dark-mode .page-header h1 {
  color: #e5e5e5;
}

.page-header p {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.dark-mode .page-header p {
  color: #999;
}

.settings-menu {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 20px;
}

.dark-mode .settings-menu {
  background: #1f1f1f;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.menu-item {
  padding: 12px 20px;
  cursor: pointer;
  border-left: 4px solid transparent;
  color: #666;
  transition: all 0.3s;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.dark-mode .menu-item {
  color: #ccc;
}

.menu-icon {
  font-size: 18px;
}

.menu-item:hover {
  background-color: #f5f7fa;
  color: #409eff;
}

.dark-mode .menu-item:hover {
  background-color: #2a2a2a;
  color: #66b1ff;
}

.menu-item.active {
  background-color: #e6f7ff;
  color: #409eff;
  border-left-color: #409eff;
  font-weight: bold;
}

.dark-mode .menu-item.active {
  background-color: #1e3a5f;
  color: #66b1ff;
  border-left-color: #66b1ff;
}

.settings-card {
  margin-bottom: 20px;
  border: 1px solid #e9ecef !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06) !important;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  align-items: center;
  gap: 10px;
}

.dark-mode .card-header {
  color: #e5e5e5;
}

.card-header :deep(.el-icon) {
  font-size: 18px;
}

.password-tips {
  background-color: #f9f9f9;
  padding: 15px;
  border-radius: 6px;
  margin-top: 20px;
}

.dark-mode .password-tips {
  background-color: #2a2a2a;
}

.password-tips h4 {
  margin: 0 0 12px 0;
  color: #333;
  font-size: 14px;
  font-weight: bold;
}

.dark-mode .password-tips h4 {
  color: #e5e5e5;
}

.password-tips ul {
  margin: 0;
  padding-left: 20px;
  color: #666;
  font-size: 13px;
}

.dark-mode .password-tips ul {
  color: #ccc;
}

.password-tips li {
  margin-bottom: 8px;
  line-height: 1.6;
}

.security-summary {
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 6px;
  margin-bottom: 20px;
}

.dark-mode .security-summary {
  background-color: #2a2a2a;
}

.summary-item {
  background: white;
  padding: 15px;
  border-radius: 4px;
  border: 1px solid #e9ecef;
}

.dark-mode .summary-item {
  background: #1f1f1f;
  border-color: #333;
}

.summary-label {
  color: #999;
  font-size: 12px;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.dark-mode .summary-label {
  color: #888;
}

.summary-value {
  color: #333;
  font-size: 16px;
  font-weight: bold;
}

.dark-mode .summary-value {
  color: #e5e5e5;
}

h4 {
  margin: 20px 0 15px 0;
  color: #333;
  font-size: 14px;
  font-weight: bold;
}

.dark-mode h4 {
  color: #e5e5e5;
}

/* 操作日志 */
.audit-filter {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.audit-pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.audit-unknown {
  color: #94a3b8;
}
</style>
