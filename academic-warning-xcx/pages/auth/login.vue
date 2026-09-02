<template>
  <view class="page">
    <view class="brand">
      <view class="logo-wrap">
        <image src="/static/logo.jpg" mode="aspectFit" class="logo" />
      </view>
      <text class="brand-title">智能学业预警服务系统</text>
      <text class="brand-sub">哈尔滨信息工程学院</text>
    </view>

    <view class="card">
      <text class="card-title">登录</text>

      <view class="field">
        <text class="field-label">学号 / 工号</text>
        <input
          v-model="loginForm.username"
          placeholder="请输入学号或工号"
          class="field-input"
          placeholder-class="ph"
        />
      </view>

      <view class="field">
        <text class="field-label">密码</text>
        <view class="input-wrap">
          <input
            v-model="loginForm.password"
            :password="!showPwd"
            placeholder="请输入密码"
            class="field-input"
            placeholder-class="ph"
          />
          <view class="toggle-pwd" @tap="showPwd = !showPwd">
            <image v-if="!showPwd" src="/static/icon/eye-open.svg" mode="aspectFit" class="eye-icon" />
            <image v-else src="/static/icon/eye-close.svg" mode="aspectFit" class="eye-icon" />
          </view>
        </view>
      </view>

      <view class="field captcha-field">
        <text class="field-label">验证码</text>
        <view class="captcha-row">
          <input
            v-model="loginForm.captcha"
            placeholder="输入验证码"
            class="field-input captcha-input"
            placeholder-class="ph"
            maxlength="6"
          />
          <view class="captcha-box" @tap="generateCaptcha">
            <text class="captcha-code">{{ captchaCode }}</text>
          </view>
        </view>
      </view>

      <view v-if="errorMessage" class="error">
        <text>{{ errorMessage }}</text>
      </view>

      <view class="remember" @tap="rememberMe = !rememberMe">
        <view :class="['check', { active: rememberMe }]">
          <text v-if="rememberMe" class="check-mark">✓</text>
        </view>
        <text class="remember-text">记住我30天</text>
      </view>

      <button
        class="btn"
        :disabled="loading"
        @tap="handleLogin"
      >
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <view class="register">
        <text class="reg-text">还没有账户？</text>
        <navigator url="/pages/auth/register" class="reg-link">快速注册</navigator>
      </view>
    </view>

    <view class="footer-links">
      <text class="footer-link">隐私政策</text>
      <text class="footer-dot">·</text>
      <text class="footer-link">服务条款</text>
      <text class="footer-dot">·</text>
      <text class="footer-link">联系我们</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiClient } from '../../services/api'

const loginForm = ref({ username: '', password: '', captcha: '' })
const loading = ref(false)
const showPwd = ref(false)
const rememberMe = ref(false)
const errorMessage = ref('')
const captchaCode = ref('')

const generateCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 4; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  captchaCode.value = code
}

const handleLogin = async () => {
  errorMessage.value = ''
  if (!loginForm.value.username) { errorMessage.value = '请输入学号或工号'; return }
  if (!loginForm.value.password) { errorMessage.value = '请输入密码'; return }
  if (!loginForm.value.captcha) { errorMessage.value = '请输入验证码'; return }
  if (loginForm.value.captcha.toUpperCase() !== captchaCode.value.toUpperCase()) {
    errorMessage.value = '验证码错误'
    generateCaptcha()
    loginForm.value.captcha = ''
    return
  }

  loading.value = true
  try {
    const response = await apiClient.login(loginForm.value.username, loginForm.value.password)
    if (response && response.token) {
      uni.setStorageSync('token', response.token)
      uni.setStorageSync('userId', response.userId)
      uni.setStorageSync('username', response.username)
      uni.setStorageSync('role', response.role)
      if (response.name) uni.setStorageSync('userName', response.name)
      if (response.teacherId) uni.setStorageSync('teacherId', response.teacherId)
      if (response.counselorId) uni.setStorageSync('counselorId', response.counselorId)

      const role = String(response.role)
      uni.showToast({ title: '登录成功', icon: 'success', duration: 1200 })
      setTimeout(() => {
        const routes = {
          '1': '/pages/student/dashboard',
          student: '/pages/student/dashboard',
          '2': '/pages/teacher/dashboard',
          teacher: '/pages/teacher/dashboard',
          '3': '/pages/counselor/dashboard',
          counselor: '/pages/counselor/dashboard',
          '4': '/pages/admin/dashboard',
          admin: '/pages/admin/dashboard',
          '5': '/pages/college-admin/dashboard',
          college_admin: '/pages/college-admin/dashboard'
        }
        uni.reLaunch({ url: routes[role] || '/pages/student/dashboard' })
      }, 1200)
    } else {
      errorMessage.value = '账号或密码错误'
      generateCaptcha()
      loginForm.value.captcha = ''
    }
  } catch (error) {
    errorMessage.value = '网络连接失败，请检查网络后重试'
    generateCaptcha()
    loginForm.value.captcha = ''
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const keys = ['token','userId','username','role','studentId','teacherId','counselorId','adminId','userName']
  keys.forEach(k => uni.removeStorageSync(k))
  generateCaptcha()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(160deg, #3b5ce8 0%, #5b7cf0 40%, #7b9cf8 100%);
  display: flex;
  flex-direction: column;
}

/* 品牌区 */
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 140rpx 0 60rpx;
  position: relative;
  z-index: 1;
}
.logo-wrap {
  width: 160rpx;
  height: 160rpx;
  background: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.15);
  margin-bottom: 24rpx;
}
.logo {
  width: 130rpx;
  height: 130rpx;
  border-radius: 50%;
}
.brand-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 2rpx;
}
.brand-sub {
  font-size: 24rpx;
  color: rgba(255,255,255,0.6);
  margin-top: 8rpx;
}

/* 卡片 */
.card {
  flex: 1;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 48rpx 40rpx 40rpx;
  position: relative;
  z-index: 1;
}
.card-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 40rpx;
}

/* 字段 */
.field {
  margin-bottom: 28rpx;
  position: relative;
}
.field-label {
  display: block;
  font-size: 24rpx;
  font-weight: 500;
  color: #86868b;
  margin-bottom: 10rpx;
}
.input-wrap {
  position: relative;
  width: 100%;
  height: 88rpx;
}
.field-input {
  width: 100%;
  height: 88rpx;
  background: #f5f5f7;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #1d1d1f;
  box-sizing: border-box;
}
.ph {
  color: #aeaeb2;
  font-size: 28rpx;
}
.toggle-pwd {
  position: absolute;
  right: 8rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.eye-icon {
  width: 36rpx;
  height: 36rpx;
}

/* 验证码 */
.captcha-row {
  display: flex;
  gap: 16rpx;
}
.captcha-input {
  flex: 1;
}
.captcha-box {
  width: 160rpx;
  height: 88rpx;
  background: #f5f5f7;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.captcha-code {
  font-size: 32rpx;
  font-weight: 800;
  color: #3b5ce8;
  letter-spacing: 6rpx;
  font-style: italic;
}

/* 错误 */
.error {
  background: #fef2f2;
  border-radius: 8rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 20rpx;
}
.error text {
  color: #ef4444;
  font-size: 24rpx;
}

/* 记住我 */
.remember {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 32rpx;
}
.check {
  width: 32rpx;
  height: 32rpx;
  border-radius: 6rpx;
  border: 2rpx solid #d4d4d8;
  display: flex;
  align-items: center;
  justify-content: center;
}
.check.active {
  background: #3b5ce8;
  border-color: #3b5ce8;
}
.check-mark {
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
}
.remember-text {
  font-size: 24rpx;
  color: #86868b;
}

/* 按钮 */
.btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #3b5ce8;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 12rpx;
  border: none;
  letter-spacing: 4rpx;
  margin: 0;
}
.btn[disabled] {
  opacity: 0.5;
}

/* 注册 */
.register {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8rpx;
  margin-top: 32rpx;
}
.reg-text {
  font-size: 24rpx;
  color: #86868b;
}
.reg-link {
  font-size: 24rpx;
  color: #3b5ce8;
  font-weight: 500;
}

/* 底部 */
.footer-links {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16rpx;
  padding: 32rpx 0;
}
.footer-link {
  font-size: 22rpx;
  color: #aeaeb2;
}
.footer-dot {
  font-size: 22rpx;
  color: #aeaeb2;
}
</style>
