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

<style lang="scss" scoped>
/* 本页是**未登录**页，不属于任何角色，所以不用 var(--color-primary)
   （那个在 page 上默认是学生绿）。品牌蓝走 $brand-auth，与 PC 端
   Login.vue 同一组值。 */
.page {
  min-height: 100vh;
  background: linear-gradient(160deg, $brand-auth-deep 0%, $brand-auth 40%, $brand-auth-light 100%);
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
  background: $color-card;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.15);
  margin-bottom: $spacing-xl;
}
.logo {
  width: 130rpx;
  height: 130rpx;
  border-radius: 50%;
}
.brand-title {
  font-size: $font-lg;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 2rpx;
}
.brand-sub {
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.6);
  margin-top: $spacing-xs;
}

/* 卡片 */
.card {
  flex: 1;
  background: $color-card;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: 48rpx 40rpx 40rpx;
  position: relative;
  z-index: 1;
}
.card-title {
  display: block;
  font-size: $font-2xl;
  font-weight: 700;
  color: $color-text;
  margin-bottom: $spacing-3xl;
}

/* 字段 */
.field {
  margin-bottom: $spacing-xl;
  position: relative;
}
.field-label {
  display: block;
  font-size: $font-sm;
  font-weight: 500;
  color: $color-text-secondary;
  margin-bottom: 10rpx;
}
.input-wrap {
  position: relative;
  width: 100%;
  height: $tap-min;
}
.field-input {
  width: 100%;
  height: $tap-min;
  background: $color-bg;
  border-radius: $radius-md;
  padding: 0 $spacing-xl;
  font-size: $font-md;
  color: $color-text;
  box-sizing: border-box;
}
.ph {
  color: $color-text-hint;
  font-size: $font-md;
}

/* 眼睛按钮：点击区必须 ≥ $tap-min（88rpx）。原先只写 56rpx —— 28px，
   是全工程最小的可点元素，而它又正好是"密码可见性"这个最容易点错的控件。 */
.toggle-pwd {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: $tap-min;
  height: $tap-min;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.eye-icon {
  width: 40rpx;
  height: 40rpx;
}

/* 验证码 */
.captcha-row {
  display: flex;
  gap: $spacing-md;
}
.captcha-input {
  flex: 1;
}
.captcha-box {
  width: 160rpx;
  height: $tap-min;
  background: $color-bg;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.captcha-code {
  font-size: $font-lg;
  font-weight: 800;
  color: $brand-auth;
  letter-spacing: 6rpx;
  font-style: italic;
}

/* 错误 */
.error {
  background: $color-danger-weak;
  border-radius: $radius-sm;
  padding: $spacing-md $spacing-lg;
  margin-bottom: $spacing-lg;
}
.error text {
  color: $color-danger;
  font-size: $font-sm;
}

/* 记住我：整行可点，所以行高本身就是点击区 */
.remember {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  min-height: $tap-min;
  margin-bottom: $spacing-md;
}
.check {
  width: 40rpx;
  height: 40rpx;
  border-radius: $radius-sm;
  border: 2rpx solid $color-border;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.check.active {
  background: $brand-auth;
  border-color: $brand-auth;
}
.check-mark {
  color: #fff;
  font-size: $font-xs;
  font-weight: 700;
}
.remember-text {
  font-size: $font-sm;
  color: $color-text-secondary;
}

/* 按钮 */
.btn {
  width: 100%;
  height: $tap-min;
  line-height: $tap-min;
  background: $brand-auth;
  color: #ffffff;
  font-size: $font-lg;
  font-weight: 600;
  border-radius: $radius-md;
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
  gap: $spacing-xs;
  margin-top: $spacing-2xl;
}
.reg-text {
  font-size: $font-sm;
  color: $color-text-secondary;
}
.reg-link {
  font-size: $font-sm;
  color: $brand-auth;
  font-weight: 500;
}

/* 底部 */
.footer-links {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-2xl 0;
}
.footer-link {
  font-size: $font-xs;
  color: $color-text-hint;
}
.footer-dot {
  font-size: $font-xs;
  color: $color-text-hint;
}
</style>
