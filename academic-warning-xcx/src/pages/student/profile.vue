<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="我的" :show-back="false" />

    <role-hero
      :title="dash.studentName || info.name || userName || '我的'"
      :subtitle="heroSubtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 学籍信息 -->
        <view class="card">
          <section-title title="学籍信息" />
          <view v-for="r in eduRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
        </view>

        <!-- 账号信息 -->
        <view class="card">
          <section-title title="账号信息" extra="如需修改请联系管理员" />
          <view v-for="r in accountRows" :key="r.label" class="kv-row">
            <text class="kv-row__label">{{ r.label }}</text>
            <text class="kv-row__value">{{ r.value }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">账号状态</text>
            <text class="tag" :class="statusTagClass">{{ statusText }}</text>
          </view>
        </view>

        <!-- 成绩走势（近几个学期） -->
        <view v-if="gpaHistory.length" class="card">
          <section-title title="GPA 走势" :extra="`近 ${gpaHistory.length} 个学期`" />
          <view class="gpa-list">
            <view v-for="h in gpaHistory" :key="h.semester" class="gpa-row">
              <text class="gpa-row__sem">{{ h.semesterLabel }}</text>
              <view class="gpa-row__track">
                <view class="gpa-row__fill" :style="{ width: gpaWidth(h.studentGpa) }" />
              </view>
              <text class="gpa-row__val">{{ numFixed(h.studentGpa, 2) }}</text>
            </view>
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
              <text v-if="m.badge" class="menu-item__badge">{{ m.badge }}</text>
              <app-icon name="chevron" :size="28" class="menu-item__arrow" />
            </view>
          </view>
        </view>

        <view class="foot">
          <view class="btn-ghost" @tap="confirmLogout">退出登录</view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI } from '@/services/api.js'
import { useUser, logout } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'
import { USER_STATUS_TEXT, dictText } from '@/constants/dict.js'

const { userId, userName } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const dash = ref({})
/** /students/student-by-user/{uid} —— 学籍（班级/专业/入学年） */
const profile = ref({})
/** /users/info/{uid} —— 账号（邮箱/手机/注册时间） */
const info = ref({})
const gpaHistory = ref([])
/** /students/warnings/{uid} —— 预警列表，本地计数（口径见 heroStats 注释） */
const warnings = ref([])

const EMPTY = '—'
/** 绩点上限 4.0（后端 ScoreSyncSupport 最高档 90 分以上） */
const GPA_MAX = 4

const avatarText = computed(() => {
  const name = dash.value.studentName || info.value.name || userName
  return name ? String(name).slice(0, 1) : ''
})

const heroSubtitle = computed(() => {
  const cls = dash.value.className || profile.value.className
  const major = dash.value.majorName
  const parts = [cls, major].filter(Boolean)
  return parts.length ? parts.join(' · ') : '学生'
})

/** 只显示后端真的给了的值。取不到就显破折号，不拿学号/ID 冒充。
 *
 * 预警数**不取** `dash.warningCount`：那一列与 dashboard 页本地统计的口径不同源，
 * 两页数字会打架（dashboard 的注释也明示了不用它）。这里同样拉预警列表本地数，
 * 三个页面（dashboard / warnings / profile）看到的是同一个数。 */
const heroStats = computed(() => [
  { label: '绩点', value: dash.value.gpa == null ? EMPTY : numFixed(dash.value.gpa, 2) },
  { label: '已通过学分', value: dash.value.completedCredits == null ? EMPTY : dash.value.completedCredits },
  { label: '挂科', value: dash.value.failedCoursesCount == null ? EMPTY : dash.value.failedCoursesCount },
  { label: '预警', value: warnings.value.length }
])

const eduRows = computed(() => [
  { label: '学号', value: profile.value.studentId || info.value.username || EMPTY },
  { label: '姓名', value: profile.value.name || dash.value.studentName || EMPTY },
  { label: '班级', value: profile.value.className || dash.value.className || EMPTY },
  // 专业名只有 dashboard 给（student-by-user 只回 majorId）；学院接口对学生是 403，不显示
  { label: '专业', value: dash.value.majorName || EMPTY },
  { label: '入学年份', value: profile.value.enrollmentYear || EMPTY }
])

const accountRows = computed(() => [
  { label: '邮箱', value: info.value.email || EMPTY },
  { label: '手机号', value: info.value.phone || EMPTY },
  { label: '注册时间', value: info.value.createdAt ? formatDate(info.value.createdAt) : EMPTY }
])

const statusText = computed(() => dictText(USER_STATUS_TEXT, info.value.status))

const statusTagClass = computed(() => {
  const n = Number(info.value.status)
  if (n === 1) return 'tag--success'
  if (n === 2) return 'tag--danger'
  if (n === 0) return 'tag--warning'
  return 'tag--pending'
})

/** 预警条数做成徽标。0 或取不到时留空，而不是显示一个"0" */
const warningBadge = ref('')

const menus = computed(() => [
  { label: '成绩查询', icon: 'doc', url: '/pages/student/scores' },
  { label: '我的课表', icon: 'calendar', url: '/pages/student/timetable' },
  { label: '预警记录', icon: 'alert', url: '/pages/student/warnings', badge: warningBadge.value },
  { label: '帮扶计划', icon: 'target', url: '/pages/student/assistance' },
  { label: '成绩申诉', icon: 'edit', url: '/pages/student/appeals' },
  { label: '对标分析', icon: 'trend', url: '/pages/student/benchmark-analysis' },
  { label: '排名与班级', icon: 'chart', url: '/pages/student/class-ranking' },
  { label: '通知中心', icon: 'bell', url: '/pages/student/notification-center' },
  { label: '设置', icon: 'settings', url: '/pages/student/settings' }
])

function numFixed(v, d = 2) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(d) : EMPTY
}

function gpaWidth(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0%'
  return `${Math.max(0, Math.min(100, (n / GPA_MAX) * 100))}%`
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
     * 四个接口都传 **users.id**。注意 `/students/{i}` 系列看着像要 profile id，
     * 实际要的是 users.id —— 传 profile id 会得到 `{error:"学生不存在"}`（HTTP 仍是 200）。
     */
    const [d, p, i, gh, w] = await Promise.all([
      studentAPI.getDashboard(userId).catch(e => { console.error('[profile] 首页数据失败:', e); return null }),
      studentAPI.getStudentByUserId(userId).catch(e => { console.error('[profile] 学籍失败:', e); return null }),
      studentAPI.getUserInfo(userId).catch(e => { console.error('[profile] 账号失败:', e); return null }),
      studentAPI.getGPA(userId).catch(() => null),
      studentAPI.getWarnings(userId).catch(e => { console.error('[profile] 预警加载失败:', e); return [] })
    ])

    dash.value = d && typeof d === 'object' ? d : {}
    profile.value = p && typeof p === 'object' ? p : {}
    info.value = i && typeof i === 'object' ? i : {}
    // /students/{uid}/gpa 返回按学期倒序的数组，取近 5 个学期
    gpaHistory.value = (Array.isArray(gh) ? gh : []).slice(0, 5)
    warnings.value = Array.isArray(w) ? w : []

    const wc = warnings.value.length
    warningBadge.value = Number.isFinite(wc) && wc > 0 ? String(wc) : ''
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
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
  font-size: $font-sm;
  color: $color-text-secondary;
}

.kv-row__value {
  max-width: 60%;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

/* ---------- GPA 走势 ---------- */
.gpa-row {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-sm;
}

.gpa-row:last-child {
  margin-bottom: 0;
}

.gpa-row__sem {
  width: 180rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.gpa-row__track {
  flex: 1;
  min-width: 0;
  height: 16rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.gpa-row__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
  opacity: 0.85;
}

.gpa-row__val {
  width: 80rpx;
  flex-shrink: 0;
  margin-left: $spacing-sm;
  text-align: right;
  font-size: $font-xs;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

/* ---------- 功能入口 ---------- */
.menu-head {
  padding: $spacing-xl $spacing-xl 0;
}

.menu {
  padding: 0 $spacing-xl $spacing-lg;
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

.menu-item__badge {
  flex-shrink: 0;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  margin-right: $spacing-sm;
  border-radius: $radius-pill;
  background: $color-danger;
  color: #fff;
  font-size: $font-xs;
  line-height: 36rpx;
  text-align: center;
}

.menu-item__arrow {
  flex-shrink: 0;
  opacity: 0.35;
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
