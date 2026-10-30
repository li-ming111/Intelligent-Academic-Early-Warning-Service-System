<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="我的" :show-back="false" />

    <role-hero
      :title="displayNameText"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="2" mode="card" />

      <template v-else>
        <!-- 教师资料 -->
        <view class="card">
          <section-title title="教师资料" extra="由管理员维护" />
          <view v-for="r in profileRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
          <text v-if="!profileRows.length" class="empty-tip">教师档案尚未完善，可联系管理员补充职称与研究方向。</text>
        </view>

        <!-- 账号 -->
        <view class="card">
          <section-title title="账号" extra="如需修改请联系管理员" />
          <view class="kv-row">
            <text class="kv-row__label">登录账号（工号）</text>
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

        <!-- 功能入口 -->
        <view class="card card--flush">
          <view class="menu-head">
            <section-title title="常用功能" />
          </view>
          <view class="menu">
            <view
              v-for="m in menus"
              :key="m.url"
              class="menu-item"
              @tap="go(m.url)"
            >
              <app-icon :name="m.icon" :size="36" />
              <text class="menu-item__label">{{ m.label }}</text>
              <app-icon name="chevron" :size="28" class="menu-item__arrow" />
            </view>
          </view>
        </view>

        <!-- 学生反馈：只在这里给入口，回复动作统一在「反馈管理」页做 -->
        <view class="card">
          <section-title title="学生反馈" :extra="unread ? `${unread} 条未回复` : '全部已回复'" arrow @extra="goFeedback" />
          <view class="kv-row">
            <text class="kv-row__label">未回复</text>
            <text class="kv-row__value kv-row__value--num">{{ unread }} 条</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">累计收到</text>
            <text class="kv-row__value kv-row__value--num">{{ messages.length }} 条</text>
          </view>
          <text class="empty-tip">回复入口在「反馈管理」页；本页只做汇总，以免同一个动作有两处实现。</text>
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

    <app-tab-bar role="teacher" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { apiClient, teacherAPI } from '@/services/api.js'
import { useUser, logout } from '@/composables/useUser.js'
import { roleThemeStyle, primaryOf } from '@/composables/useRoleTheme.js'
import { USER_STATUS_TEXT, dictText } from '@/constants/dict.js'
import { go } from '@/utils/navigate.js'

const { userId, userName, role } = useUser()

const themeStyle = roleThemeStyle('teacher')

const EMPTY = '—'

const loading = ref(true)
const profile = ref({})
const info = ref({})
const dash = ref({})
const messages = ref([])
const unread = ref(0)

/**
 * 键名**必须**与 `user_settings` 表的列名一致 —— 这四个就是
 * `GET /users/{uid}/settings` 实际返回的键。教师端没有自己的设置接口，
 * 四个开关与其它角色共用同一张表。
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
  { key: 'studentFeedback', label: '学生反馈', hint: '学生提交的反馈与后续回复' }
]

const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const changing = ref(false)

/** switch 是原生组件，不解析 CSS 变量，color 必须给字面量（色值取 ROLE_THEME） */
const switchColor = primaryOf(role, '#2196f3')

const displayNameText = computed(() => profile.value.name || userName || '教师')

const subtitle = computed(() => {
  const parts = [profile.value.title, profile.value.researchAreas].filter(Boolean)
  return parts.join(' · ') || '教学工作台'
})

const avatarText = computed(() => {
  const name = displayNameText.value
  return name ? String(name).slice(0, 1) : ''
})

const heroStats = computed(() => [
  { label: '我的班级', value: numOr(dash.value.totalClasses) },
  { label: '我的学生', value: numOr(dash.value.totalStudents) },
  { label: '我的课程', value: numOr(dash.value.totalCourses) }
])

/**
 * 只渲染后端真的给了值的字段。
 * **不显示"学院"**：`teacher_profile` 里只有 `college_id`（一个数字 1），
 * 教师端没有把 id 换成学院名的接口，显示裸数字比不显示更容易被误读。
 */
const profileRows = computed(() => {
  const p = profile.value || {}
  const rows = [
    { label: '姓名', value: p.name },
    { label: '职称', value: p.title },
    { label: '研究方向', value: p.researchAreas }
  ]
  return rows
    .filter(r => r.value !== null && r.value !== undefined && String(r.value).trim() !== '')
    .map(r => ({ label: r.label, value: String(r.value) }))
})

const statusText = computed(() => dictText(USER_STATUS_TEXT, info.value.status))

/**
 * 常用功能入口。
 * 只放**不在底栏 tabBar 里**的页面 —— 成绩/学生/预警已经占了三个 tab，
 * 再在「我的」里重复一遍会让同一个去处有两处入口。
 */
const menus = [
  { label: '我的课表', icon: 'calendar', url: '/pages/teacher/timetable' },
  { label: '课程管理', icon: 'book', url: '/pages/teacher/courses' },
  { label: '班级管理', icon: 'building', url: '/pages/teacher/class-management' },
  { label: '班级分析', icon: 'chart', url: '/pages/teacher/analysis' },
  { label: '学分预测', icon: 'trend', url: '/pages/teacher/credit-prediction' },
  { label: '反馈管理', icon: 'mail', url: '/pages/teacher/feedback-management' }
]

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

function goFeedback() {
  go('/pages/teacher/feedback-management')
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
    await teacherAPI.updateSettings(userId, { ...form.value })
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.error('[teacher profile] 保存设置失败:', e)
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
    console.error('[teacher profile] 修改密码失败:', e)
    uni.showToast({ title: '修改失败，请稍后重试', icon: 'none' })
  } finally {
    changing.value = false
  }
}

function confirmLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
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
     * 教师档案要分两步：`GET /teachers/{id}` 吃的是 `teacher_profile.id`（于凤海=1），
     * 而登录态里只有 `users.id`（=29）。两者不同源，传错了会 404 或查空。
     * `/teachers/list` 同时给出 `id` 与 `userId`，正好用来做这层映射。
     */
    const [account, dashboard, settings, msgs, count, teachers] = await Promise.all([
      teacherAPI.getUserInfo(userId).catch(e => { console.error('[teacher profile] 账号失败:', e); return null }),
      teacherAPI.getDashboard(userId).catch(e => { console.error('[teacher profile] 概览失败:', e); return null }),
      teacherAPI.getSettings(userId).catch(e => { console.error('[teacher profile] 设置失败:', e); return null }),
      teacherAPI.getMessages(userId).catch(e => { console.error('[teacher profile] 消息失败:', e); return [] }),
      teacherAPI.getUnreadCount(userId).catch(e => { console.error('[teacher profile] 未读数失败:', e); return 0 }),
      teacherAPI.getTeachers().catch(e => { console.error('[teacher profile] 教师列表失败:', e); return [] })
    ])

    info.value = account && typeof account === 'object' ? account : {}
    dash.value = dashboard && typeof dashboard === 'object' ? dashboard : {}
    messages.value = Array.isArray(msgs) ? msgs : []
    // 未读接口返回的是**裸数字**，不是 {count}
    unread.value = numOr(count)

    const mine = (Array.isArray(teachers) ? teachers : []).find(t => String(t.userId) === String(userId))
    if (mine) {
      const detail = await teacherAPI.getProfile(mine.id).catch(e => {
        console.error('[teacher profile] 档案失败:', e)
        return null
      })
      profile.value = detail && typeof detail === 'object' ? detail : mine
    }

    if (settings && typeof settings === 'object') {
      // 只挑已知的四个键，免得把无关字段带进表单再整表写回去
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

/* ---------- 功能入口 ---------- */
/* ⚠️ 这里的 .card--flush 必须在本文件里再写一遍：本页有一条 scoped 的
   `.card { padding: $spacing-lg }`，它的属性选择器优先级高于全局
   `.card--flush { padding: 0 }` ⇒ 不覆盖的话卡片会多出一圈内边距，
   菜单行与卡片边缘之间凭空多出空隙（全局类"没生效"看起来就像样式错乱）。 */
.card--flush {
  padding: 0;
  overflow: hidden;
}

.menu-head {
  padding: $spacing-lg $spacing-lg 0;
}

.menu {
  padding: 0 $spacing-lg $spacing-md;
}

.menu-item {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  border-bottom: 1rpx solid $color-divider;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item__label {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
  font-size: $font-base;
  color: $color-text;
}

.menu-item__arrow {
  flex-shrink: 0;
  opacity: 0.35;
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

.kv-row__value--num {
  font-variant-numeric: tabular-nums;
}

.empty-tip {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
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
