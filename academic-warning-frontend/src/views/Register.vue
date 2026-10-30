<template>
  <div class="reg-page">
    <div class="reg-card">
      <div class="reg-header">
        <img src="@/assets/xiaohui.jpg" alt="校徽" class="reg-logo">
        <h1>学生注册</h1>
        <p>哈尔滨信息工程学院 · 智能学习预警云服务平台</p>
      </div>

      <form @submit.prevent="handleRegister">
        <div class="rg-field">
          <label>学号</label>
          <el-input v-model="form.studentId" placeholder="请输入10位学号" maxlength="10"
            @input="onStudentIdChange" clearable>
            <template #prefix><el-icon><Postcard /></el-icon></template>
          </el-input>
          <span v-if="form.studentId.length === 10" class="rg-hint ok">✓ 学号有效</span>
        </div>

        <div class="rg-field">
          <label>专业</label>
          <el-input v-model="form.majorName" placeholder="输入学号自动填充" disabled>
            <template #prefix><el-icon><School /></el-icon></template>
          </el-input>
          <span v-if="form.majorName" class="rg-hint">{{ form.collegeName }} | {{ form.majorName }}</span>
        </div>

        <div class="rg-field">
          <label>班级</label>
          <el-select v-model="form.classId" placeholder="按学号带出学院后可选" style="width:100%" clearable>
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <span class="rg-hint dim">不选也能注册，但班级排名/班级名单等会没有数据；可稍后在个人资料里补</span>
        </div>

        <div class="rg-field">
          <label>姓名</label>
          <el-input v-model="form.name" placeholder="请输入真实姓名" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-field">
          <label>密码</label>
          <el-input v-model="form.password" type="password" placeholder="请输入密码（至少6位）" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
          <span class="rg-hint dim">默认密码: 123456</span>
        </div>

        <div class="rg-field">
          <label>手机号</label>
          <el-input v-model="form.phone" placeholder="请输入11位手机号" maxlength="11" clearable>
            <template #prefix><el-icon><Phone /></el-icon></template>
          </el-input>
          <span v-if="isValidPhone" class="rg-hint ok">✓ 手机号有效</span>
        </div>

        <div class="rg-field">
          <label>邮箱</label>
          <el-input v-model="form.email" placeholder="请输入邮箱地址" clearable>
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
          <span v-if="isValidEmail" class="rg-hint ok">✓ 邮箱有效</span>
        </div>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" closable
          @close="errorMessage = ''" style="margin-bottom:16px" />
        <el-alert v-if="successMessage" :title="successMessage" type="success" closable
          @close="successMessage = ''" style="margin-bottom:16px" />

        <el-button type="primary" native-type="submit" class="reg-submit" :loading="loading">
          {{ loading ? '注册中...' : '注册账号' }}
        </el-button>

        <div class="reg-footer">
          <router-link to="/login">← 已有账号？返回登录</router-link>
          <div class="reg-footer-links">
            <router-link to="/teacher-register">教师注册</router-link>
            <router-link to="/counselor-register">辅导员注册</router-link>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import apiClient from '@/api/client'
import { User, Lock, Phone, Message, Postcard, School } from '@element-plus/icons-vue'

const router = useRouter()
const form = ref({
  studentId: '', majorName: '', collegeName: '', majorId: null, collegeId: null,
  classId: null,
  name: '', password: '123456', phone: '', email: ''
})
const loading = ref(false), errorMessage = ref(''), successMessage = ref('')
const classes = ref([])

const isValidPhone = computed(() => /^\d{11}$/.test(form.value.phone))
const isValidEmail = computed(() => /@/.test(form.value.email) && /\./.test(form.value.email))

// 目录（学院/专业/班级）必须走 /auth/*：那是**登录前可达**的公开目录接口。
// 从前这里打的是 /admin/majors 与 /admin/colleges/{id} —— 管理员接口，
// 注册页还没有 token，网关直接 401，catch 里只写一句"（加载失败）"，
// 于是"专业"这一格永远是失败态，majorId/collegeId 也永远是 null。
let directoryPromise = null
const loadDirectory = () => {
  if (!directoryPromise) {
    directoryPromise = Promise.all([
      apiClient.get('/auth/majors'),
      apiClient.get('/auth/colleges')
    ]).then(([majors, colleges]) => ({ majors: majors || [], colleges: colleges || [] }))
      .catch(e => { directoryPromise = null; throw e })
  }
  return directoryPromise
}

const onStudentIdChange = async () => {
  form.value.classId = null
  classes.value = []
  if (form.value.studentId.length !== 10) {
    form.value.majorName = ''; form.value.collegeName = ''
    form.value.majorId = null; form.value.collegeId = null
    return
  }
  try {
    const { majors, colleges } = await loadDirectory()
    // 学号第 5-6 位是专业代码（2023020616 → "02" → 计算机科学与技术）
    const majorCode = form.value.studentId.substring(4, 6)
    const major = majors.find(m => String(m.code) === majorCode)
    if (!major) {
      form.value.majorName = '（未找到匹配专业）'; form.value.collegeName = ''
      form.value.majorId = null; form.value.collegeId = null
      return
    }
    form.value.majorName = major.name
    form.value.majorId = major.id
    form.value.collegeId = major.collegeId
    const college = colleges.find(c => c.id === major.collegeId)
    form.value.collegeName = college ? college.name : ''
    // 班级按学院过滤（选填；取不到就留空，不阻塞注册）
    try {
      classes.value = await apiClient.get(`/auth/classes?collegeId=${major.collegeId}`) || []
    } catch (e) {
      classes.value = []
      errorMessage.value = '班级列表加载失败：' + (e.message || '') + '（可以先不选班级继续注册）'
    }
  } catch (e) {
    form.value.majorName = '（加载失败）'; form.value.collegeName = ''
    form.value.majorId = null; form.value.collegeId = null
  }
}

const handleRegister = async () => {
  errorMessage.value = ''; successMessage.value = ''
  if (!form.value.studentId || form.value.studentId.length !== 10) { errorMessage.value = '请输入有效的10位学号'; return }
  // 学院与专业由学号第 5-6 位专业代码带出 —— 带不出（代码不匹配任何专业）就停在这里，
  // 不能让"没有归属"的档案落库（注册接口已同步改为必填，这里先给出能看懂的原因）。
  if (!form.value.collegeId || !form.value.majorId) { errorMessage.value = '学号未能带出学院与专业，请核对学号（第5-6位需是有效的专业代码）'; return }
  if (!form.value.name) { errorMessage.value = '请输入姓名'; return }
  if (!form.value.password || form.value.password.length < 6) { errorMessage.value = '密码至少需要6位'; return }
  if (!isValidPhone.value) { errorMessage.value = '请输入有效的11位手机号'; return }
  if (!isValidEmail.value) { errorMessage.value = '请输入有效的邮箱地址'; return }
  loading.value = true
  try {
    // ⚠️ 键名必须是 username：后端的注册接口只读 username/password/name/phone/email
    //    （学号即账号名，档案里的 student_id 也用它的值），从前这里发的是 studentId，
    //    后端读不到 → 一律回"用户名不能为空"，也就是说**这个页面从来没能注册成功过**。
    //    majorId/collegeId/classId 从前也算了出来却没发，档案里因此没有学院/专业/班级。
    await apiClient.post('/auth/register/student', {
      username: form.value.studentId,
      name: form.value.name,
      password: form.value.password,
      phone: form.value.phone,
      email: form.value.email,
      majorId: form.value.majorId,
      collegeId: form.value.collegeId,
      classId: form.value.classId
    })
    successMessage.value = '注册成功！即将跳转到登录页...'
    setTimeout(() => router.push('/login'), 1200)
  } catch (e) { errorMessage.value = e.message || '注册失败，请重试' }
  finally { loading.value = false }
}
</script>

<style scoped>
.reg-page { min-height: 100vh; background: #f0f4ff; display: flex; align-items: center; justify-content: center; padding: 40px 24px; }
.reg-card { width: 100%; max-width: 460px; background: #fff; border-radius: 20px; padding: 44px 40px;
  box-shadow: 0 4px 24px rgba(0,0,0,.06); }
.reg-header { text-align: center; margin-bottom: 32px; }
.reg-logo { width: 52px; height: 52px; max-width: 52px; border-radius: 14px; margin-bottom: 12px; object-fit: contain;
  background: #eff6ff; padding: 6px; display: inline-block; flex-shrink: 0; }
.reg-header h1 { margin: 0 0 4px; font-size: 24px; font-weight: 700; color: #1e3a5f; }
.reg-header p { margin: 0; font-size: 12px; color: #94a3b8; }
.rg-field { margin-bottom: 16px; }
.rg-field label { display: block; font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 6px; }
.rg-field :deep(.el-input__wrapper) { border-radius: 10px; background: #fff; border: 1.5px solid #e2e8f0; transition: all .3s; box-shadow: none; }
.rg-field :deep(.el-input__wrapper:hover) { border-color: #93c5fd; }
.rg-field :deep(.el-input__wrapper.is-focus) { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37,99,235,.1); }
.rg-hint { font-size: 11px; margin-top: 4px; display: block; }
.rg-hint.ok { color: #16a34a; }
.rg-hint.dim { color: #94a3b8; }
.reg-submit { width: 100%; height: 46px; font-size: 15px; font-weight: 700; letter-spacing: 1px; border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #3b82f6); border: none; box-shadow: 0 4px 14px rgba(37,99,235,.3); transition: all .3s; margin-top: 6px; }
.reg-submit:hover { background: linear-gradient(135deg, #1d4ed8, #2563eb); box-shadow: 0 6px 20px rgba(37,99,235,.4); transform: translateY(-1px); }
.reg-footer { text-align: center; margin-top: 20px; }
.reg-footer a { display: inline-block; color: #2563eb; font-size: 13px; text-decoration: none; transition: color .2s; }
.reg-footer a:hover { color: #1d4ed8; }
.reg-footer-links { margin-top: 10px; display: flex; gap: 12px; justify-content: center; }
.reg-footer-links a { font-size: 12px; color: #94a3b8; }
.reg-footer-links a:hover { color: #2563eb; }
</style>
