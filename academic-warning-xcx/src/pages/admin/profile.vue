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
        <!-- 账号 -->
        <view class="card">
          <section-title title="账号" extra="由管理员在「用户管理」页维护" />
          <view v-for="r in accountRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">账号状态</text>
            <text class="tag" :class="statusTagClass">{{ statusText }}</text>
          </view>
          <text class="empty-tip">
            这一页**只读**：姓名/邮箱/手机号要在「用户管理」里改（那里有完整的 CRUD 与审批）。
            本页只做汇总，免得同一个动作有两处实现。
          </text>
        </view>

        <!-- 平台概览 -->
        <view class="card">
          <section-title title="平台概览" extra="来自 /admin/dashboard" arrow @extra="goDashboard" />
          <view v-for="r in overviewRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value kv-row__value--num">{{ r.value }}</text>
          </view>
          <text class="empty-tip">
            这是全平台的总量。**没有**「较上月」这类环比数字 —— 后端没有历史快照表，
            那是把"没数据"讲成"没有变化"。完整报表在「数据统计」页。
          </text>
        </view>

        <!-- 导出记录 -->
        <view class="card">
          <section-title title="导出记录" :extra="`共 ${exportTotal} 条`" />
          <view v-if="!exports.length" class="empty-tip">暂无导出记录。</view>
          <view
            v-for="e in exports"
            :key="e.id"
            class="exp"
            @longpress="askDeleteExport(e)"
          >
            <view class="exp__head">
              <text class="exp__name">{{ e.fileName }}</text>
              <text class="tag tag--info">{{ e.dataTypeName || e.dataType || '未知类型' }}</text>
            </view>
            <text class="exp__meta">
              {{ e.recordCount }} 条记录 · {{ formatDate(e.createdAt, true) }} ·
              {{ e.fileSize || '未记录大小' }}
            </text>
          </view>
          <text class="empty-tip">
            24 条记录**全部**产生于 2026-05-21 ~ 05-27，`exported_by` 一列全是 NULL
            ——「谁导的」没记下来。`fileSize` 由后端拿 `exports/<文件名>` 这个**相对路径**
            去磁盘上找文件，而相对路径是相对**服务进程的工作目录**；工程里根本没有 `exports`
            目录，所以 24 条一律显示「文件不存在」。
          </text>
          <text class="empty-tip">
            导出文件的**本体**是 xlsx 二进制流，小程序端收不了也存不下来 ——
            这一页只列历史记录，要拿文件得去 PC 端。长按一行可以删掉这条记录
            （**只删记录**，不涉及文件；删了不可撤销）。
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
            管理端**没有**自己的设置接口，这五个开关和教师/辅导员/学生共用
            `user_settings` 同一张表。`id` 为 null 表示从没保存过（接口返回的是内存里的默认值）。
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
            如果忘了当前密码，管理员的另一条路是「用户管理 → 重置密码」——
            它不解锁也不发邮件，直接把口令改回固定值。
          </text>
        </view>

        <view class="foot">
          <view class="btn-ghost" @tap="confirmLogout">退出登录</view>
        </view>
      </template>
    </view>

    <app-tab-bar role="admin" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { apiClient, adminAPI } from '@/services/api.js'
import { useUser, logout } from '@/composables/useUser.js'
import { roleThemeStyle, primaryOf } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'

const { userId, userName, role } = useUser()

const themeStyle = roleThemeStyle('admin')

const EMPTY = '—'

const loading = ref(true)
const info = ref({})
const dash = ref({})
const exportTotal = ref(0)
const exports = ref([])

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
  { key: 'warningNotification', label: '学业预警通知', hint: '有新预警产生时推送' },
  { key: 'systemMessage', label: '系统消息', hint: '系统公告与维护通知' },
  { key: 'classNotification', label: '班级通知', hint: '班级事务与集体活动' },
  { key: 'studentFeedback', label: '学生反馈', hint: '学生提交的反馈与后续回复' },
  { key: 'emailReminder', label: '邮件提醒', hint: '同时发送一份到邮箱' }
]

const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const changing = ref(false)

/** switch 是原生组件，不解析 CSS 变量，color 必须给字面量（色值取 ROLE_THEME） */
const switchColor = primaryOf(role, '#6b3fa0')

const displayNameText = computed(() => info.value.name || userName || '管理员')

const subtitle = computed(() => '系统管理员 · 平台运维')

const avatarText = computed(() => {
  const name = displayNameText.value
  return name ? String(name).slice(0, 1) : ''
})

/** 概览里的三项。取不到就显示 0 —— 这三个接口是全平台计数，0 也是真话。 */
const heroStats = computed(() => [
  { label: '账号总数', value: numOr(dash.value.totalUsers) },
  { label: '学生', value: numOr(dash.value.totalStudents) },
  { label: '预警', value: numOr(dash.value.totalWarnings) }
])

const accountRows = computed(() => {
  const i = info.value || {}
  return [
    { label: '账号 ID', value: i.id === null || i.id === undefined ? EMPTY : String(i.id) },
    { label: '登录账号', value: i.username || EMPTY },
    { label: '姓名', value: i.name || EMPTY },
    { label: '邮箱', value: i.email || EMPTY },
    { label: '手机号', value: i.phone || EMPTY }
  ]
})

const overviewRows = computed(() => {
  const d = dash.value || {}
  return [
    { label: '账号总数', value: numOr(d.totalUsers) },
    { label: '学生', value: numOr(d.totalStudents) },
    { label: '学院', value: numOr(d.totalColleges) },
    { label: '专业', value: numOr(d.totalMajors) },
    { label: '课程', value: numOr(d.totalCourses) },
    { label: '预警记录', value: numOr(d.totalWarnings) }
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

function goDashboard() {
  go('/pages/admin/dashboard')
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
    // 体里没出现的字段保持原值不动。这里发全量只是为了请求体自洽，
    // 少发一个也不会把对方重置成默认值。
    await adminAPI.updateSettings(userId, { ...form.value })
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.error('[admin profile] 保存设置失败:', e)
    form.value[key] = prev
    uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
  }
}

function askDeleteExport(e) {
  uni.showModal({
    title: '删除导出记录',
    content: `删掉「${e.fileName}」这条记录？只删记录本身，不涉及任何文件；删了不可撤销。`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminAPI.deleteExport(e.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        await loadExports()
      } catch (err) {
        console.error('[admin profile] 删除导出记录失败:', err)
        uni.showToast({ title: (err && err.message) || '删除失败', icon: 'none' })
      }
    }
  })
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
    console.error('[admin profile] 修改密码失败:', e)
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

async function loadExports() {
  try {
    // 接口是 `selectList(null)` —— **按入库顺序（id 升序）返回，最老的在最前**，
    // 而这一块叫"导出记录"，人要看的是最近几条。所以默认排序在这里翻过来，
    // 不能指望服务端给到"最新在前"。
    const res = await adminAPI.getExportHistory(1, 100)
    const items = res && Array.isArray(res.items) ? res.items : []
    exportTotal.value = numOr(res && res.total)
    exports.value = items
      .slice()
      .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
      .slice(0, 6)
  } catch (e) {
    console.error('[admin profile] 导出记录失败:', e)
    exports.value = []
    exportTotal.value = 0
  }
}

async function load() {
  loading.value = true
  try {
    if (!userId) return

    const [account, dashboard, settings] = await Promise.all([
      adminAPI.getUserInfo(userId).catch(e => { console.error('[admin profile] 账号失败:', e); return null }),
      adminAPI.getDashboard().catch(e => { console.error('[admin profile] 概览失败:', e); return null }),
      adminAPI.getSettings(userId).catch(e => { console.error('[admin profile] 设置失败:', e); return null })
    ])

    info.value = account && typeof account === 'object' ? account : {}
    dash.value = dashboard && typeof dashboard === 'object' ? dashboard : {}

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

    await loadExports()
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

/* ---------- 导出记录 ---------- */
.exp {
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.exp:active {
  opacity: 0.7;
}

.exp__head {
  display: flex;
  align-items: center;
}

.exp__name {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.exp__meta {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
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
