<template>
  <div class="ts-page">
    <div class="ts-head">
      <h1>个人设置</h1>
      <p>管理账户信息和系统偏好</p>
    </div>

    <el-tabs model-value="account">
      <el-tab-pane label="账户管理" name="account">
        <div class="ts-cards">
          <!-- 修改密码 -->
          <el-card>
            <template #header><span class="ts-card-title">修改密码</span></template>
            <el-form :model="pwdForm" label-width="100px">
              <el-form-item label="当前密码">
                <el-input v-model="pwdForm.oldPassword" type="password" placeholder="输入当前密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwdForm.newPassword" type="password" placeholder="输入新密码（至少6位）" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="再次输入新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleChangePwd">确认修改</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 个人信息 -->
          <el-card>
            <template #header><span class="ts-card-title">个人信息</span></template>
            <el-form :model="profileForm" label-width="100px">
              <el-form-item label="姓名">
                <el-input v-model="profileForm.name" placeholder="请输入姓名" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item label="联系电话">
                <el-input v-model="profileForm.phone" placeholder="请输入电话" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSaveProfile">保存信息</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="通知偏好" name="notify">
        <el-card>
          <template #header><span class="ts-card-title">通知设置</span></template>
          <el-form :model="notifyForm" label-width="200px">
            <el-form-item label="接收预警通知">
              <el-switch v-model="notifyForm.warningNotification" />
            </el-form-item>
            <el-form-item label="接收系统消息">
              <el-switch v-model="notifyForm.systemMessage" />
            </el-form-item>
            <el-form-item label="接收班级通知">
              <el-switch v-model="notifyForm.classNotification" />
            </el-form-item>
            <el-form-item label="接收学生反馈提醒">
              <el-switch v-model="notifyForm.studentFeedback" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveNotify">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="工作设置" name="work">
        <el-card>
          <template #header><span class="ts-card-title">工作信息</span></template>
          <el-form :model="workForm" label-width="100px">
            <el-form-item label="学院">
              <el-input v-model="workForm.college" placeholder="所属学院" disabled />
            </el-form-item>
            <el-form-item label="办公地点">
              <el-input v-model="workForm.office" placeholder="输入办公地点" />
            </el-form-item>
            <el-form-item label="办公电话">
              <el-input v-model="workForm.officePhone" placeholder="输入办公电话" />
            </el-form-item>
            <el-form-item label="办公时间">
              <el-input v-model="workForm.officeHours" placeholder="如：周一至周五 8:00-17:00" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveWork">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, inject, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { teacherAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

// 用 getUserId()（登录时写入的 localStorage.userId）。
// 原写法先读 localStorage.teacherId，而那个键被 TeacherScores.vue:384
// 硬编码写成了 '9'——只要访问过教师成绩页，设置页就会去"加载并修改 users.id=9"，
// 也就是改错人的邮箱/电话/密码。这个键没有任何可信来源，不再读它。
const teacherId = getUserId()

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileForm = ref({ name: '', email: '', phone: '' })
const notifyForm = ref({
  warningNotification: true, systemMessage: true,
  classNotification: true, studentFeedback: true
})
const workForm = ref({ college: '', office: '', officePhone: '', officeHours: '' })
// 工作台数据由 TeacherLayout 统一拉取并 provide；这里只消费，避免同一页请求两次 dashboard。
const teacherDashboard = inject('teacherDashboard', null)
watch(teacherDashboard, (dash) => {
  if (dash && dash.college) workForm.value.college = dash.college
}, { immediate: true })
// 账户信息是否真的加载成功。没加载成功时表单是全空的，此时点"保存信息"
// 会把 users 里真实的姓名/邮箱/电话**覆盖成空串**（空串不是 null，
// MyBatis-Plus 的 NOT_NULL 策略不跳过），所以宁可不给保存。
const profileLoaded = ref(false)

onMounted(async () => {
  // 姓名/邮箱/电话走 /users/info/{uid}。原来读的是 /teachers/dashboard，
  // 那个接口只返回 {pendingFeedbacks,totalClasses,teacherName,pendingRequests,
  // totalStudents,totalCourses}——**没有 email/phone**，所以邮箱和电话永远是空框，
  // 页面却不报错（典型的"看着正常其实没数据"）。
  try {
    const info = await teacherAPI.getProfile(teacherId)
    if (info) {
      profileForm.value.name = info.name || ''
      profileForm.value.email = info.email || ''
      profileForm.value.phone = info.phone || ''
      profileLoaded.value = true
    }
  } catch (e) { console.error('加载账户信息失败', e) }

  try {
    const s = await teacherAPI.getSettings(teacherId)
    if (s) {
      notifyForm.value = {
        warningNotification: s.warningNotification ?? true,
        systemMessage: s.systemMessage ?? true,
        classNotification: s.classNotification ?? true,
        studentFeedback: s.studentFeedback ?? true
      }
      workForm.value.office = s.officeLocation || ''
      workForm.value.officePhone = s.officePhone || ''
      workForm.value.officeHours = s.officeHours || ''
    }
  } catch (e) { console.error('加载偏好设置失败', e) }

})

const handleChangePwd = async () => {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword || !newPassword || !confirmPassword) { ElMessage.error('请填写所有密码字段'); return }
  if (newPassword.length < 6) { ElMessage.error('新密码至少6位'); return }
  if (newPassword !== confirmPassword) { ElMessage.error('两次输入的密码不一致'); return }
  try {
    // 只 await + catch：client.js 的响应拦截器在 code!==200 时已经 reject，
    // 成功时 res 是后端消息字符串而不是对象。原写法
    // `res.code === 200 || res.success` 恒为假，改成功了反而弹"修改失败"。
    await teacherAPI.changePassword({ userId: Number(teacherId), oldPassword, newPassword })
    ElMessage.success('密码修改成功')
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e) { ElMessage.error(e?.message || '密码修改失败') }
}

const handleSaveProfile = async () => {
  if (!profileLoaded.value) { ElMessage.error('账户信息未加载成功，请刷新页面后重试'); return }
  try {
    await teacherAPI.updateProfile(Number(teacherId), {
      name: profileForm.value.name, email: profileForm.value.email, phone: profileForm.value.phone
    })
    ElMessage.success('个人信息已保存')
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}

const handleSaveNotify = async () => {
  try {
    await teacherAPI.updateNotifyPrefs({ userId: Number(teacherId), ...notifyForm.value })
    ElMessage.success('通知偏好已保存')
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}

const handleSaveWork = async () => {
  try {
    // office→officeLocation：后端列名是 office_location，
    // 原写法传的 office 不是列名，会被当未知字段忽略（弹"保存成功"但没落库）。
    await teacherAPI.updateWorkInfo({
      userId: Number(teacherId),
      officeLocation: workForm.value.office,
      officePhone: workForm.value.officePhone,
      officeHours: workForm.value.officeHours
    })
    ElMessage.success('工作设置已保存')
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}
</script>

<style scoped>
.ts-page { padding: 24px; }
.ts-head { margin-bottom: 20px; }
.ts-head h1 { margin: 0 0 4px; font-size: 22px; font-weight: 700; color: #1e3a5f; }
.ts-head p { margin: 0; font-size: 13px; color: #94a3b8; }
.ts-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 16px; }
.ts-card-title { font-size: 15px; font-weight: 600; color: #1e293b; }
:deep(.el-card) { border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
:deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid #f1f5f9; }
:deep(.el-card__body) { padding: 20px; }
:deep(.el-input__wrapper) { border-radius: 8px; }
:deep(.el-tabs__item) { font-size: 14px; }

@media (max-width: 768px) {
  .ts-cards { grid-template-columns: 1fr; }
}
</style>
