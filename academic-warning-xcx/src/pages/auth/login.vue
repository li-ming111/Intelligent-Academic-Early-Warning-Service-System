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

      <view class="remember" @tap="toggleRemember">
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
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiClient } from '../../services/api'
import { logout, rememberSession, stillRemembered } from '../../composables/useUser'

/**
 * 登录成功后各角色回哪个首页。
 *
 * 提到模块作用域是**两处共用**：`handleLogin` 与 `onMounted` 的
 * 「记住我还在期内就直接进去」。写在函数里就只能有一处用，另一处必然抄一份。
 */
const ROLE_HOME = {
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

/** 「记住我」勾选状态的记忆键（只是 UI 偏好，不属于登录态，退出登录不清它）。 */
const REMEMBER_PREF_KEY = 'rememberMePref'

const loginForm = ref({ username: '', password: '', captcha: '' })
const loading = ref(false)
const showPwd = ref(false)
const rememberMe = ref(false)
const errorMessage = ref('')
/** 401 被踢回登录页时 request.js 会带 `?redirect=原页面`，登录成功后回到那里。 */
const redirectTarget = ref('')

/** 只接受站内页面路径，避免把 redirect 当成任意外链/登录页自身。 */
function readRedirect() {
  const pages = getCurrentPages()
  const cur = pages && pages.length ? pages[pages.length - 1] : null
  const raw = cur && cur.options ? cur.options.redirect : ''
  if (!raw) return ''
  let path = ''
  try {
    path = decodeURIComponent(String(raw))
  } catch (e) {
    return ''
  }
  if (!/^\/pages\/[\w-]+\/[\w-]+(\?.*)?$/.test(path)) return ''
  if (path.startsWith('/pages/auth/login')) return ''
  return path
}
// 验证码（2026-10-02 W1.12 起由**服务端**签发）。
// 改之前这里是 `Math.random()` 本地生成、本地比对 —— 直接 POST /auth/login
// 根本不经过这一页，验证码对脚本等于不存在。
const captchaCode = ref('')
const captchaId = ref('')

/**
 * 向服务端要一个新的验证码。
 *
 * 失败时**如实把码置空并给出原因**，不偷偷退回本地随机数 ——
 * 那样会让"验证码服务挂了"表现成"登录总说验证码错误"。
 */
const generateCaptcha = async () => {
  try {
    const d = await apiClient.getCaptcha()
    if (d && d.captchaText) {
      captchaCode.value = d.captchaText
      captchaId.value = d.captchaId
    } else {
      captchaCode.value = ''
      captchaId.value = ''
      errorMessage.value = '验证码服务返回异常'
    }
  } catch (e) {
    captchaCode.value = ''
    captchaId.value = ''
    errorMessage.value = '验证码加载失败，请点击验证码重试'
  }
}

/** 勾选「记住我」并记住这个偏好（下次进本页仍勾着）。 */
const toggleRemember = () => {
  rememberMe.value = !rememberMe.value
  uni.setStorageSync(REMEMBER_PREF_KEY, rememberMe.value ? '1' : '')
}

const handleLogin = async () => {
  errorMessage.value = ''
  if (!loginForm.value.username) { errorMessage.value = '请输入学号或工号'; return }
  if (!loginForm.value.password) { errorMessage.value = '请输入密码'; return }
  if (!loginForm.value.captcha) { errorMessage.value = '请输入验证码'; return }
  if (!captchaId.value) {
    errorMessage.value = '验证码未加载，请点击验证码刷新'
    generateCaptcha()
    return
  }
  // 本地**不再**比对：对不对由服务端说了算（本地比对既挡不住脚本，
  // 又会把"码已过期"错报成"验证码错误"）。

  loading.value = true
  try {
    const response = await apiClient.login(
      loginForm.value.username, loginForm.value.password, captchaId.value, loginForm.value.captcha.trim())
    if (response && response.token) {
      // 自定义角色（users.role ≥ 6）在小程序端没有对应首页：不能回落到学生端，
      // 否则登录后每个接口都 403。如实提示走 PC 端，不写登录态。
      const role = String(response.role)
      const home = ROLE_HOME[role]
      if (!home) {
        errorMessage.value = '当前账号的角色暂不支持小程序端，请使用 PC 端登录'
        generateCaptcha()
        loginForm.value.captcha = ''
        return
      }

      uni.setStorageSync('token', response.token)
      uni.setStorageSync('userId', response.userId)
      uni.setStorageSync('username', response.username)
      uni.setStorageSync('role', response.role)
      if (response.name) uni.setStorageSync('userName', response.name)
      if (response.teacherId) uni.setStorageSync('teacherId', response.teacherId)
      if (response.counselorId) uni.setStorageSync('counselorId', response.counselorId)

      // 「记住我」：勾了就写一个 30 天后的到期时间（不勾则**什么都不写**，
      // 于是下次进这一页照样要求重新登录）。见 useUser.js 的说明。
      if (rememberMe.value) {
        rememberSession(30)
      }

      uni.showToast({ title: '登录成功', icon: 'success', duration: 1200 })
      setTimeout(() => {
        uni.reLaunch({ url: redirectTarget.value || home })
      }, 1200)
    } else {
      // 走到这里说明 HTTP 200 且 code 200，但 data 里没有 token —— 后端把失败原因
      // 放在 data.message（「密码错误」「用户不存在」「账号正在审批中」…），照实显示。
      errorMessage.value = (response && response.message) || '账号或密码错误'
      // 验证码这一轮已被服务端消费（校验无论成败都删），必须重新签发，
      // 否则用户改完密码再点一次会被"验证码错误或已过期"挡下。
      generateCaptcha()
      loginForm.value.captcha = ''
    }
  } catch (error) {
    // 别把一切都吞成"网络连接失败"：验证码不对时服务端回 code:500，
    // 被 request.js 抛成 ApiError('验证码错误或已过期…')，照实显示才有指导性。
    errorMessage.value = (error && error.message) || '网络连接失败，请检查网络后重试'
    generateCaptcha()
    loginForm.value.captcha = ''
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  redirectTarget.value = readRedirect()

  // 「记住我」还在期内 ⇒ 直接回各自首页，不用再登一次。
  // 这是这个勾选**唯一**有实际作用的地方（改之前它只翻转一个 ref）。
  if (stillRemembered()) {
    const home = ROLE_HOME[String(uni.getStorageSync('role') || '')]
    if (home) {
      uni.reLaunch({ url: redirectTarget.value || home })
      return
    }
    // 自定义角色没有小程序首页：清掉登录态，留在本页提示
    logout()
  }

  // 没记住（或已过期）：清空登录态再进来。
  // 用 logout() 而不是在这里另抄一份键名数组 —— 原先那份既漏了
  // 后来新增的键，又清着 `studentId`/`adminId` 两个**全仓从未写过**的键。
  logout()

  // 恢复上次的勾选状态（偏好本身不属于登录态，退出登录不清它）
  rememberMe.value = uni.getStorageSync(REMEMBER_PREF_KEY) === '1'
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
  overflow: hidden;
}
.logo {
  /* logo.jpg 是 536×536 的方图，校徽本体只占中间 87%（四周自带一圈白边）。
     按 160rpx 放就会露出那圈白边，看着像"没占满圆"；放大到 184rpx
     （160 ÷ 0.873）让白边被 .logo-wrap 的圆形裁掉。 */
  width: 184rpx;
  height: 184rpx;
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
  /* 全局 common.scss 的 `.card` 带 margin-bottom，白卡下面会露出一条品牌蓝；
     这里既要去掉它，也要把底部安全区一并铺成白色。 */
  margin-bottom: 0;
  background: $color-card;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: 48rpx 40rpx calc(40rpx + constant(safe-area-inset-bottom));
  padding: 48rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
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

/* 底部：原来这里是一行「隐私政策 · 服务条款 · 联系我们」三个 <text>，
   它们**没有 @tap、也没有任何对应页面**（小程序里没有隐私政策页，
   PC 端也没有同类入口）—— 三个看着像链接、点了没反应的字。
   2026-10-02（W5.12）连同 `.footer-links/.footer-link/.footer-dot` 一起删掉，
   不留假入口。 */
</style>
