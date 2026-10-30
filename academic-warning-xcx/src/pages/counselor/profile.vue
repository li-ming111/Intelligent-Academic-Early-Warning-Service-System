<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="我的" :show-back="false" />

    <role-hero
      :title="profile.name || userName || '辅导员'"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="2" mode="card" />

      <template v-else>
        <!-- 辅导员资料 -->
        <view class="card">
          <section-title title="辅导员资料" extra="由管理员维护" />
          <view v-for="r in profileRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
        </view>

        <!-- 账号 -->
        <view class="card">
          <section-title title="账号" extra="如需修改请联系管理员" />
          <view class="kv-row">
            <text class="kv-row__label">登录账号</text>
            <text class="kv-row__value">{{ info.username || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">姓名</text>
            <text class="kv-row__value">{{ info.name || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">邮箱</text>
            <text class="kv-row__value">{{ info.email || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">手机号</text>
            <text class="kv-row__value">{{ info.phone || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">账号状态</text>
            <text class="tag" :class="statusTagClass">{{ statusText }}</text>
          </view>
        </view>

        <!-- 消息接收 -->
        <view class="card">
          <section-title title="消息接收" extra="关闭后不再推送该类通知" />
          <view v-for="f in toggles" :key="f.key" class="switch-row">
            <view class="switch-row__info">
              <text class="switch-row__label">{{ f.label }}</text>
              <text class="switch-row__hint">{{ f.hint }}</text>
            </view>
            <switch
              class="switch-row__switch"
              :checked="Boolean(form[f.key])"
              :color="switchColor"
              @change="onToggle(f.key, $event)"
            />
          </view>
        </view>

        <!-- 修改密码 -->
        <view class="card">
          <section-title title="修改密码" />
          <view class="field">
            <text class="field__label">当前密码</text>
            <input
              v-model="pwd.oldPassword"
              class="field__input"
              type="password"
              password
              placeholder="请输入当前密码"
              placeholder-class="field__ph"
            />
          </view>
          <view class="field">
            <text class="field__label">新密码</text>
            <input
              v-model="pwd.newPassword"
              class="field__input"
              type="password"
              password
              placeholder="至少 6 位"
              placeholder-class="field__ph"
            />
          </view>
          <view class="field">
            <text class="field__label">确认新密码</text>
            <input
              v-model="pwd.confirm"
              class="field__input"
              type="password"
              password
              placeholder="再次输入新密码"
              placeholder-class="field__ph"
            />
          </view>
          <text class="pwd-tip">{{ pwdTip }}</text>
          <view
            class="btn-primary btn-pwd"
            :class="{ 'btn-primary--disabled': !canChangePwd }"
            @tap="doChangePassword"
          >{{ changing ? '提交中…' : '修改密码' }}</view>
        </view>

        <view class="foot">
          <view class="btn-ghost" @tap="confirmLogout">退出登录</view>
        </view>
      </template>
    </view>

    <app-tab-bar role="counselor" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { apiClient, counselorAPI } from '@/services/api.js'
import { useUser, logout } from '@/composables/useUser.js'
import { roleThemeStyle, primaryOf } from '@/composables/useRoleTheme.js'
import { normalizeWarnings, countWarnings } from '@/utils/warning.js'
import { USER_STATUS_TEXT, dictText } from '@/constants/dict.js'

const { userId, userName, role } = useUser()

const themeStyle = roleThemeStyle('counselor')

const EMPTY = '—'

const loading = ref(true)
const profile = ref({})
const info = ref({})
const warnings = ref([])

/**
 * 通知开关的键**必须**与 `user_settings` 表的列名一致 ——
 * 这四个就是 `GET /users/{uid}/settings` 实际返回的键。
 * 不要用 `/counselors/**` 下的任何设置接口（不存在），也不要走 `/students/settings/{uid}`
 * （那是纯桩，PUT 一个字段都不写库）。
 */
const form = ref({
  warningNotification: false,
  systemMessage: false,
  classNotification: false,
  studentFeedback: false
})

const toggles = [
  { key: 'warningNotification', label: '学业预警通知', hint: '所带班级触发预警时推送' },
  { key: 'systemMessage', label: '系统消息', hint: '系统公告与维护通知' },
  { key: 'classNotification', label: '班级通知', hint: '班级事务与集体活动' },
  { key: 'studentFeedback', label: '学生反馈', hint: '学生对帮扶计划的评价与回复' }
]

const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const changing = ref(false)

/** switch 是原生组件，不解析 CSS 变量，color 必须给字面量（色值取 ROLE_THEME） */
const switchColor = primaryOf(role, '#4349b8')

const summary = computed(() => countWarnings(warnings.value))

const subtitle = computed(() => {
  const parts = [profile.value.department, profile.value.title].filter(Boolean)
  return parts.join(' · ') || '学生工作'
})

const avatarText = computed(() => {
  const name = profile.value.name || userName
  return name ? String(name).slice(0, 1) : ''
})

const heroStats = computed(() => {
  const s = summary.value
  return [
    { label: '管理班级', value: numOr(profile.value.classCount) },
    { label: '名下学生', value: numOr(profile.value.studentCount) },
    { label: '预警总数', value: s.total },
    { label: '待处理', value: s.pending }
  ]
})

/**
 * 资料行只渲染**后端真的给了值**的字段。
 * 空字段不占位 —— 一列「职称 —、学历 —」会让页面看着像没加载出来。
 */
const profileRows = computed(() => {
  const p = profile.value || {}
  const rows = [
    { label: '工号', value: p.staffId || p.counselorId },
    { label: '姓名', value: p.name },
    { label: '性别', value: p.gender },
    { label: '学院', value: p.collegeName },
    { label: '系/部门', value: p.department },
    { label: '职称', value: p.title },
    { label: '学历', value: p.education },
    { label: '办公室', value: p.officeLocation },
    { label: '办公电话', value: p.dutyPhone || p.phone },
    { label: '邮箱', value: p.email }
  ]
  return rows
    .filter(r => r.value !== null && r.value !== undefined && String(r.value).trim() !== '')
    .map(r => ({ label: r.label, value: String(r.value) }))
})

const statusText = computed(() => dictText(USER_STATUS_TEXT, info.value.status))

const statusTagClass = computed(() => {
  const n = Number(info.value.status)
  if (n === 1) return 'tag--success'
  if (n === 2) return 'tag--danger'
  if (n === 0) return 'tag--warning'
  return 'tag--pending'
})

const pwdTip = computed(() => {
  const { oldPassword, newPassword, confirm } = pwd.value
  if (!oldPassword && !newPassword && !confirm) return '修改后需使用新密码重新登录'
  if (newPassword && newPassword.length < 6) return '新密码至少 6 位'
  if (newPassword && confirm && newPassword !== confirm) return '两次输入的新密码不一致'
  if (oldPassword && newPassword && newPassword === oldPassword) return '新密码不能与当前密码相同'
  return '修改后需使用新密码重新登录'
})

const canChangePwd = computed(() => {
  const { oldPassword, newPassword, confirm } = pwd.value
  return Boolean(oldPassword) && newPassword.length >= 6 &&
    newPassword !== oldPassword && newPassword === confirm
})

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function onToggle(key, e) {
  const next = Boolean(e.detail.value)
  const prev = form.value[key]
  form.value[key] = next
  saveSetting(key, next, prev)
}

async function saveSetting(key, value, prev) {
  try {
    // `PUT /users/{uid}/settings` 是**部分更新**，不是整表覆盖 ——
    // 体里没出现的字段保持原值不动（MyBatis-Plus 的 NOT_NULL 策略，四个开关都是包装类型
    // Boolean，所以「传 false」和「没传」分得开）。发全量只是让请求体自洽，少发不会重置别人。
    const res = await counselorAPI.updateSettings(userId, { ...form.value })
    if (res && res.success === false) {
      form.value[key] = prev
      uni.showToast({ title: res.message || '保存失败', icon: 'none' })
      return
    }
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.error('[profile] 保存设置失败:', e)
    form.value[key] = prev
    uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
  }
}

async function doChangePassword() {
  if (!canChangePwd.value || changing.value) return
  changing.value = true
  try {
    const res = await apiClient.changePassword({
      userId,
      oldPassword: pwd.value.oldPassword,
      newPassword: pwd.value.newPassword
    })
    // 失败同样是 HTTP 200 + code 200，只能看载荷
    if (res && (res.success === false || res.error)) {
      uni.showToast({ title: res.message || res.error || '修改失败', icon: 'none' })
      return
    }
    uni.showToast({ title: '密码已修改', icon: 'success' })
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  } catch (e) {
    console.error('[profile] 修改密码失败:', e)
    uni.showToast({ title: '修改失败，请稍后重试', icon: 'none' })
  } finally {
    changing.value = false
  }
}

function confirmLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: res => {
      if (!res.confirm) return
      logout()
      uni.reLaunch({ url: '/pages/auth/login' })
    }
  })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    /**
     * 辅导员资料**只能**从 dashboard 拿。
     * `GET /counselors/{counselorId}` 吃的是 `counselor_profiles.counselor_id`，
     * 那是个业务编号字符串（司明是 'C53'），传数值 53 只会拿到 404 ——
     * 所以这里不调那个接口，用 dashboard 里的 `counselor` 对象。
     */
    const [d, w, account, settings] = await Promise.all([
      counselorAPI.getDashboard(userId).catch(e => { console.error('[profile] 概览失败:', e); return null }),
      counselorAPI.getWarnings(userId).catch(e => { console.error('[profile] 预警失败:', e); return [] }),
      counselorAPI.getUserInfo(userId).catch(e => { console.error('[profile] 账号失败:', e); return null }),
      counselorAPI.getSettings(userId).catch(e => { console.error('[profile] 设置失败:', e); return null })
    ])

    const dash = d && typeof d === 'object' ? d : {}
    profile.value = dash.counselor && typeof dash.counselor === 'object' ? dash.counselor : {}
    // dashboard 没回填 classCount/studentCount 时用 dashboard 自己的两个数兜底
    if (profile.value.classCount === undefined || profile.value.classCount === null) {
      profile.value = { ...profile.value, classCount: Array.isArray(dash.classes) ? dash.classes.length : 0 }
    }
    if (profile.value.studentCount === undefined || profile.value.studentCount === null) {
      profile.value = { ...profile.value, studentCount: numOr(dash.studentCount) }
    }

    warnings.value = normalizeWarnings(w)
    info.value = account && typeof account === 'object' ? account : {}

    if (settings && typeof settings === 'object') {
      // 只挑已知的四个键，免得把 officeLocation 之类无关字段带进表单再整表写回去
      const next = { ...form.value }
      for (const t of toggles) {
        if (settings[t.key] !== undefined && settings[t.key] !== null) {
          next[t.key] = Boolean(settings[t.key])
        }
      }
      form.value = next
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

.card {
  margin-bottom: $spacing-md;
  padding: $spacing-lg;
  @include card;
}

/* ---------- 键值行 ---------- */
.kv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72rpx;
  padding: $spacing-xs 0;
  border-bottom: 1rpx solid $color-divider;
}

.kv-row:last-child {
  border-bottom: none;
}

.kv-row__label {
  flex-shrink: 0;
  margin-right: $spacing-md;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.kv-row__value {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

/* ---------- 开关行 ---------- */
.switch-row {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.switch-row:last-child {
  border-bottom: none;
}

.switch-row__info {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-md;
}

.switch-row__label {
  display: block;
  font-size: $font-base;
  color: $color-text;
}

.switch-row__hint {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.switch-row__switch {
  flex-shrink: 0;
  transform: scale(0.85);
}

/* ---------- 表单 ---------- */
.field {
  margin-top: $spacing-md;
}

.field__label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.field__input {
  width: 100%;
  height: $tap-min;
  padding: 0 $spacing-md;
  box-sizing: border-box;
  border: 1rpx solid $color-border;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.field__ph {
  color: $color-text-hint;
  font-size: $font-sm;
}

.pwd-tip {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.btn-pwd {
  margin-top: $spacing-lg;
}

/* ---------- 退出 ---------- */
.foot {
  margin-top: $spacing-lg;
}

.btn-ghost {
  @include flex-center;
  height: $tap-min;
  border-radius: $radius-md;
  background: $color-card;
  border: 1rpx solid $color-danger;
  color: $color-danger;
  font-size: $font-md;
  font-weight: 500;
}
</style>
