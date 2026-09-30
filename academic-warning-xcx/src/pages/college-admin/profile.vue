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
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 档案 -->
        <view class="card">
          <section-title title="管理档案" extra="来自 /college-admin/profile" />
          <view v-for="r in profileRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
          <text class="empty-tip">
            学院名是从 `colleges` 表 JOIN 出来的（档案表本身只存 `college_id`）。
            这一页**只读** —— 后端没有编辑学院管理员档案的接口，
            所以这里不做"编辑资料"的入口，免得点进去没反应。
          </text>
        </view>

        <!-- 本院概览 -->
        <view class="card">
          <section-title title="本院概览" extra="来自 /college-admin/dashboard" arrow @extra="goDashboard" />
          <view v-for="r in overviewRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value kv-row__value--num">{{ r.value }}</text>
          </view>
          <text class="empty-tip">
            最后一项在接口里叫 `counselorCount`，量的是**本学院里已经分配了辅导员的班级数**，
            不是辅导员人数。软件学院 6 个班的 `counselor_id` 都指向同一位辅导员，
            所以是「6 个班」而非「6 位辅导员」。要看辅导员名单请去「辅导员管理」页。
          </text>
        </view>

        <!-- 账号 -->
        <view class="card">
          <section-title title="登录账号" extra="由系统管理员在「用户管理」页维护" />
          <view v-for="r in accountRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">账号状态</text>
            <text class="tag" :class="statusTagClass">{{ statusText }}</text>
          </view>
          <text class="empty-tip">
            这一页**只读**：姓名/邮箱/手机号要在「用户管理」里改（那里有完整的 CRUD）。
            本页只做汇总，免得同一个动作有两处实现。
          </text>
          <text class="empty-tip">
            ⚠️ 档案表（上面那张卡）有自己的一份 `phone` / `email`，与账号表这里的
            手机号/邮箱**是两列数据** —— 管理员在「用户管理」里改账号的联系方式
            不会同步到档案表，所以两处对不上是正常现象，不是页面读错了。
          </text>
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
          <text class="empty-tip">
            这五个开关与教师/辅导员/学生共用 `user_settings` 同一张表 ——
            学院管理员没有自己的设置接口。`id` 为 null 表示从没保存过
            （接口在内存里生成默认值返回，那一行还没落库）。
          </text>
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
          <text class="empty-tip">
            忘了当前密码的话，得请系统管理员在「用户管理 → 重置密码」里改回默认值 ——
            学院管理员自己没有重置通道。
          </text>
        </view>

        <view class="foot">
          <view class="btn-ghost" @tap="confirmLogout">退出登录</view>
        </view>
      </template>
    </view>

    <app-tab-bar role="collegeAdmin" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { apiClient, collegeAdminAPI } from '@/services/api.js'
import { useUser, logout } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('collegeAdmin')

const EMPTY = '—'

const loading = ref(true)
const profile = ref({})
const info = ref({})
const dash = ref({})

/**
 * 键名**必须**与 `user_settings` 表的列名一致 —— 这五个就是
 * `GET /users/{uid}/settings` 实际返回的键。
 */
const form = ref({
  warningNotification: false,
  systemMessage: false,
  classNotification: false,
  studentFeedback: false,
  emailReminder: false
})

const toggles = [
  { key: 'warningNotification', label: '学业预警通知', hint: '本院有新预警产生时推送' },
  { key: 'systemMessage', label: '系统消息', hint: '系统公告与维护通知' },
  { key: 'classNotification', label: '班级通知', hint: '班级事务与集体活动' },
  { key: 'studentFeedback', label: '学生反馈', hint: '学生提交的反馈与后续回复' },
  { key: 'emailReminder', label: '邮件提醒', hint: '同时发送一份到邮箱' }
]

const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const changing = ref(false)

/** switch 是原生组件，不解析 CSS 变量，color 必须给字面量（与 ROLE_THEME 同源） */
const switchColor = '#0ea5e9'

const displayNameText = computed(() => profile.value.name || userName || '学院管理员')

const subtitle = computed(() => {
  const c = profile.value.college_name || '未归属学院'
  const t = profile.value.title
  return t ? `${c} · ${t}` : c
})

const avatarText = computed(() => {
  const name = displayNameText.value
  return name ? String(name).slice(0, 1) : ''
})

/** 只用 dashboard 一个接口的数，本院口径。 */
const heroStats = computed(() => [
  { label: '本院学生', value: numOr(dash.value.studentCount) },
  { label: '班级', value: numOr(dash.value.classCount) },
  { label: '预警', value: numOr(dash.value.warningCount) }
])

const profileRows = computed(() => {
  const p = profile.value || {}
  return [
    { label: '姓名', value: p.name || EMPTY },
    { label: '所属学院', value: p.college_name || EMPTY },
    { label: '职务', value: p.title || '未填' },
    { label: '联系电话', value: p.phone || '未填' },
    { label: '邮箱', value: p.email || '未填' }
  ]
})

const accountRows = computed(() => {
  const i = info.value || {}
  return [
    { label: '账号 ID', value: i.id === null || i.id === undefined ? EMPTY : String(i.id) },
    { label: '登录账号', value: i.username || EMPTY },
    { label: '姓名', value: i.name || EMPTY },
    { label: '邮箱', value: i.email || '未填' },
    { label: '手机号', value: i.phone || '未填' }
  ]
})

const overviewRows = computed(() => {
  const d = dash.value || {}
  return [
    { label: '学生', value: numOr(d.studentCount) },
    { label: '班级', value: numOr(d.classCount) },
    { label: '教师', value: numOr(d.teacherCount) },
    { label: '专业', value: numOr(d.majorCount) },
    { label: '预警记录', value: numOr(d.warningCount) },
    { label: '有辅导员的班级', value: numOr(d.counselorCount) }
  ]
})

/**
 * 状态文案与「用户管理」页保持一致：后端把"审批中"和"被停用"都写成 status=0，
 * 库里现在也没有待审批的账号，所以展示成"未启用"而不是替它猜一个词。
 */
const statusText = computed(() => (Number(info.value.status) === 1 ? '正常（可登录）' : '未启用（不可登录）'))

const statusTagClass = computed(() => {
  const n = Number(info.value.status)
  if (n === 1) return 'tag--success'
  if (n === 2) return 'tag--danger'
  return 'tag--warning'
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
    // PUT /users/{uid}/settings 是**部分更新**（MyBatis-Plus 的 NOT_NULL 策略）：
    // 体里没出现的字段保持原值不动。这里发全量只是为了让请求体自洽，
    // 少发一个也不会把对方重置成默认值。
    await collegeAdminAPI.updateSettings(userId, { ...form.value })
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.error('[college-admin profile] 保存设置失败:', e)
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
    console.error('[college-admin profile] 修改密码失败:', e)
    uni.showToast({ title: '修改失败，请稍后重试', icon: 'none' })
  } finally {
    changing.value = false
  }
}

function goDashboard() {
  go('/pages/college-admin/dashboard')
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

    // `/users/**` 这几支对 role=5 是**只对本人 id** 放行的（网关里做了归属校验），
    // 传别人的 userId 依然 403 —— 这里传的一直是登录态里的 userId，没问题。
    const [pf, account, d, settings] = await Promise.all([
      collegeAdminAPI.getProfile(userId).catch(e => { console.error('[college-admin profile] 档案失败:', e); return null }),
      collegeAdminAPI.getUserInfo(userId).catch(e => { console.error('[college-admin profile] 账号失败:', e); return null }),
      collegeAdminAPI.getDashboard(userId).catch(e => { console.error('[college-admin profile] 概览失败:', e); return null }),
      collegeAdminAPI.getSettings(userId).catch(e => { console.error('[college-admin profile] 设置失败:', e); return null })
    ])

    profile.value = pf && typeof pf === 'object' ? pf : {}
    info.value = account && typeof account === 'object' ? account : {}
    dash.value = d && typeof d === 'object' ? d : {}

    if (settings && typeof settings === 'object') {
      // 只挑已知的五个键，免得把无关字段带进表单再写回去
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

.kv-row__value--num {
  font-variant-numeric: tabular-nums;
}

.empty-tip {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
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
