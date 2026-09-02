<template>
  <view class="page">
    <view class="brand">
      <image src="/static/logo.jpg" mode="aspectFit" class="logo" />
      <text class="brand-name">智能学业预警服务系统</text>
      <text class="brand-sub">用户注册</text>
    </view>

    <view class="card">
      <!-- 角色选择 -->
      <view class="role-tabs">
        <view
          v-for="r in roles"
          :key="r.value"
          :class="['role-tab', { active: selectedRole === r.value }]"
          @tap="selectRole(r.value)"
        >{{ r.label }}</view>
      </view>

      <!-- 账号/学号 -->
      <view class="field">
        <text class="field-label">{{ selectedRole === 'student' ? '学号' : selectedRole === 'teacher' ? '工号' : '工号' }}</text>
        <input v-model="form.username" placeholder="请输入" class="input" placeholder-class="ph" />
      </view>

      <!-- 密码 -->
      <view class="field">
        <text class="field-label">密码</text>
        <input v-model="form.password" type="password" placeholder="至少6位" class="input" placeholder-class="ph" />
      </view>

      <!-- 姓名 -->
      <view class="field">
        <text class="field-label">姓名</text>
        <input v-model="form.name" placeholder="请输入真实姓名" class="input" placeholder-class="ph" />
      </view>

      <!-- 手机号 -->
      <view class="field">
        <text class="field-label">手机号</text>
        <input v-model="form.phone" placeholder="请输入手机号" class="input" placeholder-class="ph" />
      </view>

      <!-- 邮箱 -->
      <view class="field">
        <text class="field-label">邮箱</text>
        <input v-model="form.email" placeholder="请输入邮箱" class="input" placeholder-class="ph" />
      </view>

      <!-- 学院（学生/教师/辅导员都需要） -->
      <view class="field">
        <text class="field-label">所属学院</text>
        <picker :range="collegeNames" @change="onCollegeChange">
          <view class="picker-box">
            <text :class="{ placeholder: !selectedCollegeName }">{{ selectedCollegeName || '请选择学院' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 专业（仅学生） -->
      <view class="field" v-if="selectedRole === 'student'">
        <text class="field-label">所属专业</text>
        <picker :range="majorNames" @change="onMajorChange">
          <view class="picker-box">
            <text :class="{ placeholder: !selectedMajorName }">{{ selectedMajorName || '请先选学院' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 班级（仅学生） -->
      <view class="field" v-if="selectedRole === 'student'">
        <text class="field-label">所属班级</text>
        <picker :range="classNames" @change="onClassChange">
          <view class="picker-box">
            <text :class="{ placeholder: !selectedClassName }">{{ selectedClassName || '请先选专业' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 职称（仅教师） -->
      <view class="field" v-if="selectedRole === 'teacher'">
        <text class="field-label">职称</text>
        <picker :range="['助教', '讲师', '副教授', '教授']" @change="(e) => form.titleIdx = e.detail.value">
          <view class="picker-box">
            <text :class="{ placeholder: form.titleIdx === -1 }">{{ form.titleIdx >= 0 ? ['助教','讲师','副教授','教授'][form.titleIdx] : '请选择职称' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 错误/成功提示 -->
      <view v-if="errorMsg" class="msg error-msg">{{ errorMsg }}</view>
      <view v-if="successMsg" class="msg success-msg">{{ successMsg }}</view>

      <button
        class="btn"
        :disabled="loading"
        @tap="handleRegister"
      >{{ loading ? '注册中...' : '注册' }}</button>

      <view class="login-link">
        <text>已有账号？</text>
        <navigator url="/pages/auth/login" class="link">立即登录</navigator>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { apiClient } from '../../services/api'

const selectedRole = ref('student')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const form = ref({
  username: '', password: '', name: '', phone: '', email: '',
  collegeId: '', majorId: '', classId: '', titleIdx: -1
})

const roles = [
  { label: '学生', value: 'student' },
  { label: '教师', value: 'teacher' },
  { label: '辅导员', value: 'counselor' }
]

const colleges = ref([])
const majors = ref([])
const classes = ref([])

const collegeNames = computed(() => colleges.value.map(c => c.name))
const majorNames = computed(() => majors.value.map(m => m.name))
const classNames = computed(() => classes.value.map(c => c.name))

const selectedCollegeObj = computed(() =>
  colleges.value.find(c => c.id == form.value.collegeId) || null
)
const selectedMajorObj = computed(() =>
  majors.value.find(m => m.id == form.value.majorId) || null
)
const selectedCollegeName = computed(() => selectedCollegeObj.value?.name || '')
const selectedMajorName = computed(() => selectedMajorObj.value?.name || '')
const selectedClassName = computed(() => {
  const cls = classes.value.find(c => c.id == form.value.classId)
  return cls?.name || cls?.className || ''
})

const selectRole = (role) => {
  selectedRole.value = role
  form.value = { username: '', password: '', name: '', phone: '', email: '', collegeId: '', majorId: '', classId: '', titleIdx: -1 }
  errorMsg.value = ''
  successMsg.value = ''
}

const onCollegeChange = (e) => {
  const c = colleges.value[e.detail.value]
  form.value.collegeId = c?.id || ''
  form.value.majorId = ''
  form.value.classId = ''
  majors.value = []
  classes.value = []
  if (c) {
    loadMajors(c.id)
    loadClasses(c.id)
  }
}

const onMajorChange = (e) => {
  const m = majors.value[e.detail.value]
  form.value.majorId = m?.id || ''
  form.value.classId = ''
  classes.value = []
  if (m) loadClasses(form.value.collegeId)
}

const onClassChange = (e) => {
  const c = classes.value[e.detail.value]
  form.value.classId = c?.id || ''
}

const handleRegister = async () => {
  errorMsg.value = ''
  if (!form.value.username) { errorMsg.value = '请输入账号'; return }
  if (!form.value.password || form.value.password.length < 6) { errorMsg.value = '密码至少6位'; return }
  if (!form.value.name) { errorMsg.value = '请输入姓名'; return }
  if (!form.value.phone) { errorMsg.value = '请输入手机号'; return }
  if (!form.value.email) { errorMsg.value = '请输入邮箱'; return }

  loading.value = true
  try {
    const base = {
      username: form.value.username,
      password: form.value.password,
      name: form.value.name,
      phone: form.value.phone,
      email: form.value.email
    }

    if (selectedRole.value === 'student') {
      await apiClient.registerStudent({
        ...base,
        studentId: form.value.username,
        collegeId: form.value.collegeId,
        majorId: form.value.majorId,
        classId: form.value.classId
      })
    } else if (selectedRole.value === 'teacher') {
      await apiClient.registerTeacher({
        ...base,
        collegeId: form.value.collegeId,
        title: form.value.titleIdx >= 0 ? ['助教','讲师','副教授','教授'][form.value.titleIdx] : ''
      })
    } else if (selectedRole.value === 'counselor') {
      await apiClient.registerCounselor({
        ...base,
        counselorId: form.value.username,
        collegeId: form.value.collegeId
      })
    }

    successMsg.value = '注册成功，即将跳转登录页...'
    errorMsg.value = ''
    setTimeout(() => { uni.redirectTo({ url: '/pages/auth/login' }) }, 1500)
  } catch (error) {
    errorMsg.value = error.message || '注册失败，请重试'
  } finally {
    loading.value = false
  }
}

const loadColleges = async () => {
  try { const d = await apiClient.getColleges(); colleges.value = d || [] }
  catch (e) { console.error('学院加载失败:', e) }
}
const loadMajors = async (collegeId) => {
  try { const d = await apiClient.getMajorsByCollege(collegeId); majors.value = d || [] }
  catch (e) { console.error('专业加载失败:', e) }
}
const loadClasses = async (collegeId) => {
  try {
    const d = await apiClient.getClasses(collegeId)
    classes.value = d || []
  } catch (e) { classes.value = [] }
}

onMounted(() => { loadColleges() })
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #3b5ce8;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0 36rpx;
}
.logo { width: 72rpx; height: 72rpx; margin-bottom: 16rpx; }
.brand-name { font-size: 30rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }
.brand-sub { font-size: 22rpx; color: rgba(255,255,255,0.55); margin-top: 6rpx; }

.card {
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 36rpx 40rpx 48rpx;
}

/* 角色标签 */
.role-tabs {
  display: flex;
  margin-bottom: 28rpx;
  gap: 16rpx;
}
.role-tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  font-size: 26rpx;
  color: #86868b;
  background: #f5f5f7;
  border-radius: 12rpx;
}
.role-tab.active {
  background: #3b5ce8;
  color: #fff;
  font-weight: 600;
}

/* 字段 */
.field { margin-bottom: 20rpx; }
.field-label { display: block; font-size: 24rpx; color: #86868b; margin-bottom: 8rpx; }
.input {
  width: 100%; height: 80rpx;
  background: #f5f5f7; border-radius: 12rpx;
  padding: 0 20rpx; font-size: 26rpx; color: #1d1d1f;
  box-sizing: border-box;
}
.ph { color: #aeaeb2; }

.picker-box {
  width: 100%; height: 80rpx;
  background: #f5f5f7; border-radius: 12rpx;
  padding: 0 20rpx; font-size: 26rpx; color: #1d1d1f;
  display: flex; align-items: center; justify-content: space-between;
  box-sizing: border-box;
}
.picker-box .placeholder { color: #aeaeb2; }
.arrow { font-size: 28rpx; color: #aeaeb2; }

/* 按钮 */
.btn {
  width: 100%; height: 84rpx; line-height: 84rpx;
  background: #3b5ce8; color: #fff;
  border-radius: 12rpx; border: none;
  font-size: 30rpx; font-weight: 600;
  letter-spacing: 6rpx; margin-top: 28rpx;
}
.btn[disabled] { opacity: 0.5; }

/* 消息 */
.msg { padding: 16rpx 20rpx; border-radius: 10rpx; margin-top: 16rpx; font-size: 24rpx; }
.error-msg { background: #fef2f2; color: #ef4444; }
.success-msg { background: #f0fdf4; color: #22c55e; }

/* 底部链接 */
.login-link {
  text-align: center; margin-top: 24rpx; font-size: 24rpx; color: #86868b;
}
.link { color: #3b5ce8; font-weight: 500; }
</style>
