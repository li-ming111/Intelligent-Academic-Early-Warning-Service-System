<template>
  <div class="cs-page">
    <div class="cs-head">
      <h1>个人设置</h1>
      <p>管理账户信息和系统偏好</p>
    </div>

    <el-tabs model-value="account">
      <el-tab-pane label="账户管理" name="account">
        <div class="cs-cards">
          <el-card>
            <template #header><span class="cs-card-title">修改密码</span></template>
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
          <el-card>
            <template #header><span class="cs-card-title">个人信息</span></template>
            <el-form :model="profileForm" label-width="100px">
              <el-form-item label="姓名"><el-input v-model="profileForm.name" placeholder="请输入姓名" /></el-form-item>
              <el-form-item label="邮箱"><el-input v-model="profileForm.email" placeholder="请输入邮箱" /></el-form-item>
              <el-form-item label="联系电话"><el-input v-model="profileForm.phone" placeholder="请输入电话" /></el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSaveProfile">保存信息</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="通知偏好" name="notify">
        <el-card>
          <template #header><span class="cs-card-title">通知设置</span></template>
          <el-form :model="notifyForm" label-width="200px">
            <el-form-item label="接收预警通知"><el-switch v-model="notifyForm.warningNotification" /></el-form-item>
            <el-form-item label="接收系统消息"><el-switch v-model="notifyForm.systemMessage" /></el-form-item>
            <el-form-item label="接收班级通知"><el-switch v-model="notifyForm.classNotification" /></el-form-item>
            <el-form-item label="接收学生反馈"><el-switch v-model="notifyForm.studentFeedback" /></el-form-item>
            <el-form-item><el-button type="primary" @click="handleSaveNotify">保存设置</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="工作设置" name="work">
        <el-card>
          <template #header><span class="cs-card-title">工作信息</span></template>
          <el-form :model="workForm" label-width="100px">
            <el-form-item label="学院">
              <el-input :model-value="workForm.college" placeholder="未设置" disabled />
              <div class="cs-hint">学院由辅导员档案维护，如需变更请联系管理员。</div>
            </el-form-item>
            <el-form-item label="办公地点"><el-input v-model="workForm.office" placeholder="输入办公地点" /></el-form-item>
            <el-form-item label="办公电话"><el-input v-model="workForm.officePhone" placeholder="输入办公电话" /></el-form-item>
            <el-form-item><el-button type="primary" @click="handleSaveWork">保存设置</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { counselorAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

// 统一用 getUserId()（登录时写入的 localStorage.userId）。
// 原写法先读 localStorage.counselorId，但全项目**没有任何地方写过这个键**，
// 永远回退到 getUserId()——留着只会让人以为存在"按辅导员档案 id 查"的路径。
const counselorId = getUserId()

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileForm = ref({ name: '', email: '', phone: '' })
const notifyForm = ref({ warningNotification: true, systemMessage: true, classNotification: true, studentFeedback: true })
const workForm = ref({ college: '', office: '', officePhone: '' })
// 账户信息是否真的加载成功。没加载成功时表单是全空的，此时点"保存信息"
// 会把 users 里真实的姓名/邮箱/电话**覆盖成空串**（空串不是 null，
// MyBatis-Plus 的 NOT_NULL 策略不跳过），所以宁可不给保存。
const profileLoaded = ref(false)

onMounted(async () => {
  // 三处请求来源不同、失败原因也不同，各自 try 掉：
  // 任意一个挂了都只该让对应输入框空着，不该让整页白屏或静默全空。
  try {
    const info = await counselorAPI.getProfile(counselorId)
    if (info) {
      profileForm.value.name = info.name || ''
      profileForm.value.email = info.email || ''
      profileForm.value.phone = info.phone || ''
      profileLoaded.value = true
    }
  } catch (e) { console.error('加载账户信息失败', e) }

  // 学院不在 users 表，取自辅导员档案（dashboard 里的 counselor.college_name）。
  // 这一格是只读展示，不参与保存。
  try {
    const dash = await counselorAPI.getDashboard(counselorId)
    workForm.value.college = dash?.counselor?.collegeName || ''
  } catch (e) { console.error('加载学院信息失败', e) }

  try {
    const s = await counselorAPI.getSettings(counselorId)
    if (s) {
      // 后端 GET 在无记录时返回默认值对象，字段是齐的；这里仍逐个 ?? 兜底，
      // 免得以后加字段时旧响应把 v-model 变成 undefined（el-switch 会显示成关闭，
      // 看起来像"用户关掉了通知"，其实是没数据）。
      notifyForm.value = {
        warningNotification: s.warningNotification ?? true,
        systemMessage: s.systemMessage ?? true,
        classNotification: s.classNotification ?? true,
        studentFeedback: s.studentFeedback ?? true
      }
      workForm.value.office = s.officeLocation || ''
      workForm.value.officePhone = s.officePhone || ''
    }
  } catch (e) { console.error('加载偏好设置失败', e) }
})

const handleChangePwd = async () => {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword || !newPassword || !confirmPassword) { ElMessage.error('请填写所有密码字段'); return }
  if (newPassword.length < 6) { ElMessage.error('新密码至少6位'); return }
  if (newPassword !== confirmPassword) { ElMessage.error('两次输入的密码不一致'); return }
  try {
    // 这里**只 await + catch**，不做返回值判定：client.js 的响应拦截器在
    // code!==200 时已经 reject，成功时 res 是后端消息字符串而不是对象。
    // 原写法 `res.code === 200 || res.success` 恒为假，导致改成功了反而弹"修改失败"。
    await counselorAPI.changePassword({ userId: Number(counselorId), oldPassword, newPassword })
    ElMessage.success('密码修改成功')
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e) { ElMessage.error(e?.message || '密码修改失败') }
}
const handleSaveProfile = async () => {
  if (!profileLoaded.value) { ElMessage.error('账户信息未加载成功，请刷新页面后重试'); return }
  try {
    await counselorAPI.updateProfile(Number(counselorId), {
      name: profileForm.value.name, email: profileForm.value.email, phone: profileForm.value.phone
    })
    ElMessage.success('个人信息已保存')
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}
const handleSaveNotify = async () => {
  try {
    await counselorAPI.updateNotifyPrefs({ userId: Number(counselorId), ...notifyForm.value })
    ElMessage.success('通知偏好已保存')
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}
const handleSaveWork = async () => {
  try {
    // 只提交 user_settings 里真实存在的列名。原写法传的 office/college/counselorId
    // 都不是列名，后端当未知字段忽略——弹"保存成功"但一个字节都没落库。
    // 学院是只读展示、也不属于本表，故不提交。
    await counselorAPI.updateWorkInfo({
      userId: Number(counselorId),
      officeLocation: workForm.value.office,
      officePhone: workForm.value.officePhone
    })
    ElMessage.success('工作设置已保存')
  } catch (e) { ElMessage.error(e?.message || '保存失败') }
}
</script>

<style scoped>
.cs-page { padding: 24px; }
.cs-head { margin-bottom: 20px; }
.cs-head h1 { margin: 0 0 4px; font-size: 22px; font-weight: 700; color: #1e3a5f; }
.cs-head p { margin: 0; font-size: 13px; color: #94a3b8; }
.cs-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 16px; }
.cs-card-title { font-size: 15px; font-weight: 600; color: #1e293b; }
.cs-hint { font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 2px; }
:deep(.el-card) { border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
:deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid #f1f5f9; }
:deep(.el-card__body) { padding: 20px; }
:deep(.el-input__wrapper) { border-radius: 8px; }
:deep(.el-tabs__item) { font-size: 14px; }
@media (max-width: 700px) { .cs-cards { grid-template-columns: 1fr; } }
</style>
