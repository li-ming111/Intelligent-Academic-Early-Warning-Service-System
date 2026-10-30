<template>
  <div class="login-page">
    <!-- 左侧：品牌展示 + 轨道环绕 + 粒子波纹 -->
    <div class="login-left">
      <!-- 左上角：校名 -->
      <div class="ll-school-name">哈尔滨信息工程学院</div>

      <!-- 浮空粒子 -->
      <div class="ll-particles">
        <span v-for="n in 10" :key="'p'+n" class="ll-particle"
          :style="{
            left: (Math.sin(n*2.7)*42 + 50) + '%',
            top: (Math.cos(n*2.3)*42 + 50) + '%',
            '--pd': (2.5 + n*0.2) + 's',
            '--pdd': n*0.4 + 's',
          }" />
      </div>

      <!-- 中心品牌内容 -->
      <div class="ll-content">
        <div class="ll-center-group">
          <!-- 轨道环线 + 环绕图标 -->
          <div class="ll-orbit-container">
            <!-- 轨道1内圈：r=130px，3图标，顺时针8s -->
            <svg class="ll-orbit-track" viewBox="0 0 260 260" style="width:260px;height:260px">
              <circle cx="130" cy="130" r="120" fill="none" stroke="rgba(255,255,255,0.12)" stroke-width="1" stroke-dasharray="8 6"/>
            </svg>
            <div class="ll-orbit-ring ring1">
              <span class="ll-orbit-icon" style="--orbit-delay:0s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">📚</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-2.7s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">🎓</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-5.3s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">📊</text></svg></span>
            </div>

            <!-- 轨道2中圈：r=200px，4图标，逆时针12s -->
            <svg class="ll-orbit-track" viewBox="0 0 420 420" style="width:420px;height:420px">
              <circle cx="210" cy="210" r="198" fill="none" stroke="rgba(255,255,255,0.1)" stroke-width="1" stroke-dasharray="14 10"/>
            </svg>
            <div class="ll-orbit-ring ring2">
              <span class="ll-orbit-icon" style="--orbit-delay:-2s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">🤖</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-5s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">🔔</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-8s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">📋</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-11s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">💡</text></svg></span>
            </div>

            <!-- 轨道3外圈：r=280px，5图标，顺时针16s -->
            <svg class="ll-orbit-track" viewBox="0 0 580 580" style="width:580px;height:580px">
              <circle cx="290" cy="290" r="278" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="1"/>
            </svg>
            <div class="ll-orbit-ring ring3">
              <span class="ll-orbit-icon" style="--orbit-delay:0s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">🏫</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-3.2s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">⭐</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-6.4s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">📝</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-9.6s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">🔬</text></svg></span>
              <span class="ll-orbit-icon" style="--orbit-delay:-12.8s"><svg viewBox="0 0 40 40"><text x="20" y="28" text-anchor="middle" font-size="26">🎯</text></svg></span>
            </div>
          </div>

          <!-- 波纹从校徽中心发散 -->
          <svg class="ll-ripple-bg" viewBox="0 0 800 800">
            <circle cx="400" cy="400" r="50" fill="none" stroke="rgba(255,255,255,0.4)" stroke-width="1"><animate attributeName="r" values="50;380" dur="6s" repeatCount="indefinite"/><animate attributeName="opacity" values="0.6;0" dur="6s" repeatCount="indefinite"/></circle>
            <circle cx="400" cy="400" r="50" fill="none" stroke="rgba(255,255,255,0.3)" stroke-width="1"><animate attributeName="r" values="50;380" dur="6s" begin="2s" repeatCount="indefinite"/><animate attributeName="opacity" values="0.5;0" dur="6s" begin="2s" repeatCount="indefinite"/></circle>
            <circle cx="400" cy="400" r="50" fill="none" stroke="rgba(255,255,255,0.2)" stroke-width="1.5"><animate attributeName="r" values="50;380" dur="6s" begin="4s" repeatCount="indefinite"/><animate attributeName="opacity" values="0.4;0" dur="6s" begin="4s" repeatCount="indefinite"/></circle>
          </svg>
          <!-- 白底校徽 -->
          <div class="ll-logo-wrap">
            <img src="@/assets/logo.png" alt="校徽" class="ll-logo">
          </div>
        </div>
        <h1 class="ll-title">智能学业预警服务系统</h1>
        <div class="ll-tags">
          <span>学业预警</span><span>成绩分析</span><span>帮扶计划</span><span>智能推荐</span>
        </div>
      </div>
    </div>

    <!-- 右侧：登录表单 -->
    <div class="login-right">
      <div class="lr-card">
        <div class="lr-header">
          <img src="@/assets/logo.png" alt="校徽" class="lr-mobile-logo">
          <h1>欢迎回来</h1>
          <p>请登录您的账户</p>
        </div>

        <form @submit.prevent="handleLogin">
          <div class="lr-field">
            <label>学号/工号</label>
            <el-input v-model="loginForm.username" placeholder="请输入学号或工号"
              clearable size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </div>

          <div class="lr-field">
            <label>密码</label>
            <div class="lr-pwd-wrap">
              <el-input v-model="loginForm.password"
                :type="showPwd ? 'text' : 'password'"
                placeholder="请输入密码" size="large"
                @keyup.enter="handleLogin" autocomplete="current-password">
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
              <button type="button" class="lr-pwd-toggle" @click="showPwd = !showPwd">
                <el-icon><component :is="showPwd ? View : Hide" /></el-icon>
              </button>
            </div>
          </div>

          <div class="lr-field">
            <label>验证码</label>
            <div class="lr-captcha-row">
              <el-input v-model="loginForm.captcha" placeholder="验证码" clearable size="large">
                <template #prefix><el-icon><CircleCheck /></el-icon></template>
              </el-input>
              <button type="button" class="lr-captcha-btn" @click="refreshCaptcha"
                      :title="captchaError || '点击换一张'">
                {{ captchaCode || (captchaLoading ? '加载中…' : '点击刷新') }}
              </button>
            </div>
          </div>

          <div class="lr-extra">
            <el-checkbox v-model="rememberMe">记住我30天</el-checkbox>
          </div>

          <el-alert v-if="errorMessage" :title="errorMessage" type="error"
            closable @close="errorMessage = ''" style="margin-bottom:16px" />

          <el-button type="primary" native-type="submit" class="lr-submit" :loading="loading" size="large">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </form>

        <div class="lr-register">
          还没有账户？快速注册：
          <router-link to="/register">学生</router-link>
          <router-link to="/teacher-register">教师</router-link>
          <router-link to="/counselor-register">辅导员</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '@/api/index'
import { ElMessage } from 'element-plus'
import { User, Lock, CircleCheck, View, Hide } from '@element-plus/icons-vue'

const router = useRouter()
const loginForm = ref({ username: '', password: '', captcha: '' })
const loading = ref(false)
const errorMessage = ref('')
// 验证码（2026-10-02 W1.12 起由**服务端**签发）。
// 改之前这里是 `Math.random()` 本地生成、本地比对 —— 页面 DOM 里就有答案，
// 而且直接 POST /auth/login 根本不经过这里，验证码对脚本等于不存在。
// 现在：GET /auth/captcha 拿 {captchaId, captchaText}，登录时把两个都带上，
// 服务端校验并一次性消费。
const captchaCode = ref('')
const captchaId = ref('')
const captchaLoading = ref(false)
const captchaError = ref('')
const showPwd = ref(false)
const rememberMe = ref(false)

// 进入登录页时清理旧登录信息，避免缓存账号导致显示错误
const clearLoginCache = () => {
  const keys = ['token', 'user', 'userId', 'username', 'userName', 'name', 'role', 'email', 'phone', 'adminPreferences', 'pageSize']
  keys.forEach(k => localStorage.removeItem(k))
}
clearLoginCache()

/**
 * 向服务端要一个新的验证码。
 *
 * 失败时**如实把按钮置成"点击刷新"并记下原因**，不偷偷退回本地随机数 ——
 * 那样会让"验证码服务挂了"表现成"登录总是说验证码错误"，最难查的那种。
 */
const refreshCaptcha = async () => {
  captchaLoading.value = true
  captchaError.value = ''
  try {
    const d = await authAPI.getCaptcha()
    if (d && d.captchaText) {
      captchaCode.value = d.captchaText
      captchaId.value = d.captchaId
    } else {
      captchaCode.value = ''
      captchaId.value = ''
      captchaError.value = '验证码服务返回异常'
    }
  } catch (e) {
    captchaCode.value = ''
    captchaId.value = ''
    captchaError.value = e?.message || '验证码加载失败'
    console.error('[captcha] 加载失败:', e)
  } finally {
    captchaLoading.value = false
  }
}
onMounted(refreshCaptcha)

const handleLogin = async () => {
  errorMessage.value = ''
  if (!loginForm.value.username) { ElMessage.error('请输入学号或工号'); return }
  if (!loginForm.value.password) { ElMessage.error('请输入密码'); return }
  if (!loginForm.value.captcha) { ElMessage.error('请输入验证码'); return }
  if (!captchaId.value) {
    // 码没签发出来（服务不可达/已过期被清）—— 重拿一个再让用户填，别拿空 id 去打登录
    ElMessage.error(captchaError.value || '验证码未加载，请点击验证码刷新')
    refreshCaptcha()
    return
  }
  // 这里**不再**做本地比对：对不对由服务端说了算（本地比对既挡不住脚本，
  // 又会把"码已过期"错报成"验证码错误"）。前端只保证两个字段都带上。
  loading.value = true
  try {
    const res = await authAPI.login(
      loginForm.value.username, loginForm.value.password, captchaId.value, loginForm.value.captcha.trim())
    const apiData = res.data
    // 注意：/auth/login 的响应信封 code 恒为 200 —— 密码错误、用户不存在也都是 200
    // （见 api/client.js 对该路径的特判），所以 code 判不出成败。
    // 真正的结果只在 data 上：成功有 data.token，失败只有 data.message。
    // ⚠️ 例外：**验证码不对**时服务端回的是 code:500（在 AuthController 里就拦下了，
    // 没进 userService），所以这一句 code 判断对验证码失败是有效的。
    if (apiData.code !== 200) {
      errorMessage.value = apiData.message || '登录失败'
      // 码已被消费（校验无论成败都会删），必须重新签发，否则下一次一定还是"已过期"
      refreshCaptcha()
      loginForm.value.captcha = ''
      loading.value = false
      return
    }
    const d = apiData.data
    if (d && d.token) {
      localStorage.setItem('token', d.token)
      localStorage.setItem('userId', d.userId)
      localStorage.setItem('username', d.username)
      localStorage.setItem('name', d.name || d.username)
      localStorage.setItem('role', d.role)
      localStorage.setItem('email', d.email || '')
      localStorage.setItem('phone', d.phone || '')
      if (rememberMe.value) {
        localStorage.setItem('rememberMe', 'true')
      }
      const role = d.role == null || d.role === '' ? '1' : String(d.role)
      if (role === '4' || role === 'admin') router.push('/admin/dashboard')
      else if (role === '2' || role === 'teacher') router.push('/teacher/dashboard')
      else if (role === '3' || role === 'counselor') router.push('/counselor/dashboard')
      else if (role === '5' || role === 'college_admin') router.push('/college-admin/dashboard')
      else if (role === '1' || role === 'student' || role === '0') router.push('/student/dashboard')
      // 自定义角色（roles.id>=6）没有对应的 Web 端布局，统一进兜底页
      else router.push('/no-access')
    } else {
      // 失败分支：此前缺失，导致密码错误时既不报错也不跳转，点了登录毫无反应。
      // data.message 是后端给出的原因（「密码错误」「用户不存在」「用户名和密码不能为空」）。
      errorMessage.value = (d && d.message) || '登录失败，请稍后重试'
      // 验证码在这一轮已经被消费掉了，重新签发一个 —— 否则用户改完密码再点一次，
      // 会被"验证码错误或已过期"挡下，看起来像"改了密码反而登不进去了"。
      refreshCaptcha()
      loginForm.value.captcha = ''
    }
    loading.value = false
  } catch (e) {
    loading.value = false
    errorMessage.value = e?.message || '网络连接失败，请稍后重试'
    // 连接层失败时验证码也可能已被消费/过期，同样重拿一个
    refreshCaptcha()
    loginForm.value.captcha = ''
  }
}

refreshCaptcha()
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

/* ========== 左侧 ========== */
.login-left {
  flex: 1;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 40%, #3b82f6 70%, #60a5fa 100%);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  min-width: 0;
}

/* 左上角校名 */
.ll-school-name {
  position: absolute;
  top: 24px;
  left: 28px;
  font-size: 16px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
  letter-spacing: 1px;
  z-index: 20;
  text-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

/* SVG 波纹扩散 - 与校徽共享中心点 */
.ll-center-group {
  position: relative;
  width: 130px;
  height: 130px;
  margin: 0 auto 24px;
}
.ll-ripple-bg {
  position: absolute;
  inset: auto;
  width: 800px; height: 800px;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 0;
}

/* 轨道容器 - 与校徽共享中心点 */
.ll-orbit-container {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 600px;
  height: 600px;
  transform: translate(-50%, -50%);
  z-index: 0;
  pointer-events: none;
}

/* SVG 轨道虚线环 */
.ll-orbit-track {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  width: 1px; height: 1px;
  overflow: visible;
}

/* 轨道旋转环 */
.ll-orbit-ring {
  position: absolute;
  top: 50%; left: 50%;
  width: 0; height: 0;
}

.ring1 { animation: orbit-spin 8s linear infinite; }
.ring2 { animation: orbit-spin 12s linear infinite reverse; }
.ring3 { animation: orbit-spin 16s linear infinite; }

@keyframes orbit-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 轨道图标 */
.ll-orbit-icon {
  position: absolute;
  width: 40px; height: 40px;
  left: -20px;
  background: rgba(255,255,255,0.18);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255,255,255,0.25);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  transition: background 0.3s, box-shadow 0.3s;
}
.ll-orbit-icon svg {
  width: 40px; height: 40px;
  display: block;
}

.ring1 .ll-orbit-icon { top: -130px; animation: icon-counter 8s linear infinite; }
.ring2 .ll-orbit-icon { top: -200px; animation: icon-counter 12s linear infinite; }
.ring3 .ll-orbit-icon { top: -280px; animation: icon-counter 16s linear infinite; }

.ll-orbit-icon { animation-delay: var(--orbit-delay, 0s); }

@keyframes icon-counter {
  from { transform: rotate(0deg); }
  to { transform: rotate(-360deg); }
}

/* 浮空粒子 */
.ll-particles {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}
.ll-particle {
  position: absolute;
  width: 4px; height: 4px;
  border-radius: 50%;
  background: rgba(255,255,255,0.7);
  animation: particle-float var(--pd, 2s) ease-in-out infinite;
  animation-delay: var(--pdd, 0s);
}
@keyframes particle-float {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.15; }
  50% { transform: translateY(-16px) scale(2.5); opacity: 0.9; }
}

/* 品牌内容 */
.ll-content {
  position: relative;
  z-index: 10;
  text-align: center;
  color: #fff;
  padding: 30px 50px;
  border-radius: 24px;
}
.ll-logo-wrap {
  position: relative;
  z-index: 1;
  width: 130px;
  height: 130px;
  background: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.ll-logo {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: contain;
  padding: 6px;
}
.ll-title {
  font-size: 28px; font-weight: 700;
  margin: 0 0 8px; letter-spacing: 2px;
}
.ll-sub {
  font-size: 16px; opacity: 0.8;
  margin: 0 0 20px;
}
.ll-tags {
  display: flex; gap: 10px;
  justify-content: center; flex-wrap: wrap;
}
.ll-tags span {
  background: rgba(255,255,255,0.15);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
  padding: 6px 16px; border-radius: 20px;
  font-size: 13px;
  border: 1px solid rgba(255,255,255,0.2);
  transition: all 0.3s;
}
.ll-tags span:hover {
  background: rgba(255,255,255,0.28);
  transform: translateY(-2px);
}

/* ========== 右侧 ========== */
.login-right {
  flex: 1;
  display: flex; align-items: center;
  justify-content: center;
  background: #fff;
}
.lr-card {
  width: 100%; max-width: 460px;
  padding: 40px 60px;
}
.lr-header { text-align: center; margin-bottom: 32px; }
.lr-mobile-logo {
  width: 48px; height: 48px;
  border-radius: 12px;
  object-fit: contain;
  background: #eff6ff;
  padding: 6px; margin-bottom: 12px;
  display: none;
}
.lr-header h1 {
  margin: 0 0 6px;
  font-size: 26px; font-weight: 700;
  color: #1e293b;
}
.lr-header p {
  margin: 0; font-size: 14px;
  color: #94a3b8;
}

.lr-field { margin-bottom: 18px; }
.lr-field label {
  display: block; font-size: 13px;
  font-weight: 600; color: #334155;
  margin-bottom: 6px;
}
.lr-pwd-wrap { position: relative; }
.lr-pwd-toggle {
  position: absolute; right: 8px; top: 50%;
  transform: translateY(-50%);
  background: none; border: none;
  color: #94a3b8; cursor: pointer;
  z-index: 1; padding: 4px;
}
.lr-captcha-row { display: flex; gap: 10px; }
.lr-captcha-row .el-input { flex: 1; }
.lr-captcha-btn {
  width: 100px; height: 40px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  border: 1px solid #bfdbfe; border-radius: 8px;
  font-size: 16px; font-weight: 700;
  letter-spacing: 3px; color: #2563eb;
  cursor: pointer; font-family: monospace;
  flex-shrink: 0;
}
.lr-extra { margin-bottom: 16px; }
.lr-submit {
  width: 100%; height: 44px;
  font-size: 16px; font-weight: 700;
  letter-spacing: 2px; border-radius: 10px;
  margin-top: 4px;
  background: linear-gradient(135deg, #2563eb, #3b82f6) !important;
  border: none !important;
}
.lr-submit:hover {
  background: linear-gradient(135deg, #1d4ed8, #2563eb) !important;
}
.lr-register {
  text-align: center; margin-top: 24px;
  font-size: 13px; color: #94a3b8;
}
.lr-register a {
  color: #2563eb; text-decoration: none;
  margin-left: 6px; font-weight: 500;
}
.lr-register a:hover { color: #1d4ed8; }

/* ========== 响应式 ========== */
@media (max-width: 900px) {
  .login-left { display: none; }
  .login-right { flex: unset; width: 100%; }
  .lr-mobile-logo { display: inline-block; }
}
@media (max-width: 480px) {
  .lr-card { padding: 0 24px; }
}
</style>
