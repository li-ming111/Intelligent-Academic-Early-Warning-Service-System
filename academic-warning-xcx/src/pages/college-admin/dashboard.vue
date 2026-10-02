<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学院管理" :show-back="false" />

    <role-hero
      :title="profile.college_name || '未归属学院'"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 待办条：学院管理员每天先看的一个数。
             与预警页同源（同一个 selectWarningsByCollege），不会"首页说 4 条、点进去 2 条"。 -->
        <view class="todo" :class="{ 'todo--clear': !pendingCount }" @tap="go('/pages/college-admin/warnings')">
          <view class="todo__left">
            <text class="todo__num">{{ pendingCount }}</text>
            <text class="todo__label">条预警待处理</text>
          </view>
          <text class="todo__hint">{{ pendingCount ? '去处理 ›' : '已全部处理' }}</text>
        </view>

        <view class="quick-grid">
          <view v-for="q in quickLinks" :key="q.url" class="quick-item" @tap="go(q.url)">
            <!-- 不传 color：app-icon 默认取当前角色主色。
                 传 var(--color-primary) 会让图标整块消失（SVG data URI 不解析 CSS 变量）。 -->
            <view class="quick-item__icon">
              <app-icon :name="q.icon" :size="44" />
            </view>
            <text class="quick-item__label">{{ q.label }}</text>
            <text v-if="q.badge" class="quick-item__badge">{{ q.badge }}</text>
          </view>
        </view>

        <!-- 本院概览：六个数都来自同一支 /college-admin/dashboard 接口 -->
        <view class="section">
          <section-title title="本院概览" extra="来自 /college-admin/dashboard" />
          <view class="card metric-grid">
            <view class="metric">
              <text class="metric__label">学生</text>
              <text class="metric__value">{{ numOr(dash.studentCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">班级</text>
              <text class="metric__value">{{ numOr(dash.classCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">教师</text>
              <text class="metric__value">{{ numOr(dash.teacherCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">专业</text>
              <text class="metric__value">{{ numOr(dash.majorCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">预警记录</text>
              <text class="metric__value metric__value--danger">{{ numOr(dash.warningCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">有辅导员的班级</text>
              <text class="metric__value">{{ numOr(dash.counselorCount) }}</text>
            </view>
          </view>
          <text class="note">
            上面这项在接口里叫 `counselorCount`，量的是
            **本学院里已经分配了辅导员的班级数**（班级的 `counselor_id` 能对上一位真辅导员），
            不是辅导员人数，也不是学生数。软件学院 6 个班的 `counselor_id` 都指向同一位辅导员，
            所以这里的 6 是「6 个班」，不是「6 位辅导员」。要看辅导员名单请去「辅导员管理」页。
          </text>
          <text class="note">
            「学生」是按 `student_profile.class_id IN (本院班级)` 数的 ——
            **没有分班的学生不计入**。学院里若有人还没挂到班上，他不会出现在这个数里，
            也不会出现在「学生管理」页。
          </text>
          <text class="note">
            「预警记录」来自聚合查询 `selectCollegeWarningStats`，与上方待办条的来源
            不是同一支 SQL（那个取的是预警行本身）。两者现在都是 4，对得上；
            若哪天对不上，说明有预警的学生没落进本院班级里。
          </text>
        </view>

        <!-- 成绩情况 -->
        <view class="section">
          <section-title title="成绩情况" extra="来自成绩聚合" arrow @extra="go('/pages/college-admin/scores')" />
          <view class="card metric-grid">
            <view class="metric">
              <text class="metric__label">成绩条数</text>
              <text class="metric__value">{{ numOr(stats.recordCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">有成绩学生</text>
              <text class="metric__value">{{ numOr(stats.studentCount) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">平均分</text>
              <text class="metric__value">{{ dashText(stats.avgScore) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">及格率</text>
              <text class="metric__value">{{ pctText(stats.passRate) }}</text>
            </view>
          </view>
          <text class="note">
            电子工程学院这四个数全是 0 —— 它名下有 2 个班、1 位教师，但**没有任何学生和成绩**。
            这是库里的实情：该学院建了架子没铺数据，不是接口算错。
          </text>
        </view>

        <!-- 档案 -->
        <view class="section">
          <section-title title="我的档案" extra="来自 /college-admin/profile" arrow @extra="go('/pages/college-admin/profile')" />
          <view class="card">
            <view v-for="r in profileRows" :key="r.label" class="kv-row">
              <text class="kv-row__label">{{ r.label }}</text>
              <text class="kv-row__value">{{ r.value }}</text>
            </view>
          </view>
        </view>
      </template>
    </view>

    <app-tab-bar role="collegeAdmin" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { go } from '@/utils/navigate.js'
import { normalizeWarnings } from '@/utils/warning.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('collegeAdmin')

const loading = ref(true)
const profile = ref({})
const dash = ref({})
const stats = ref({})
const warnings = ref([])

// 2026-10-02（W1.6）：`status !== 1` 会把"已解除(2)"也算成待处理 ⇒ 只认 0。
const pendingCount = computed(() => warnings.value.filter(w => w.status === 0).length)

const subtitle = computed(() => {
  const t = profile.value.title
  const name = profile.value.name || userName || ''
  if (t && name) return `${name} · ${t}`
  return name || '学院管理员'
})

const avatarText = computed(() => {
  const name = profile.value.name || userName || ''
  return name ? String(name).slice(0, 1) : ''
})

/** Hero 里只放最三行能读完的数，全部是本院口径。 */
const heroStats = computed(() => [
  { label: '本院学生', value: numOr(dash.value.studentCount) },
  { label: '班级', value: numOr(dash.value.classCount) },
  { label: '待处理预警', value: pendingCount.value }
])

/** 学生/班级/预警在 tabBar 上，不重复放进快捷入口 */
const quickLinks = computed(() => [
  { label: '教师', icon: 'user', url: '/pages/college-admin/teachers' },
  { label: '辅导员', icon: 'users', url: '/pages/college-admin/counselors' },
  { label: '成绩分析', icon: 'chart', url: '/pages/college-admin/scores' },
  { label: '帮扶情况', icon: 'target', url: '/pages/college-admin/assistance' }
])

const profileRows = computed(() => {
  const p = profile.value || {}
  return [
    { label: '姓名', value: p.name || '未填' },
    { label: '职务', value: p.title || '未填' },
    { label: '联系电话', value: p.phone || '未填' },
    { label: '邮箱', value: p.email || '未填' }
  ]
})

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 平均分可能没有（本院没成绩）—— 那显示「--」，不显示 0 分 */
function dashText(v) {
  return v === null || v === undefined || !Number.isFinite(Number(v)) ? '--' : Number(v).toFixed(1)
}

/** 及格率同理：后端在无记录时返回 0，但"0%"和"没有成绩"是两件事，靠 recordCount 区分 */
function pctText(v) {
  if (!numOr(stats.value.recordCount)) return '--'
  return `${numOr(v)}%`
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return

    const [d, s, pf, wl] = await Promise.all([
      collegeAdminAPI.getDashboard(userId).catch(e => { console.error('[college-admin dashboard] 概览失败:', e); return null }),
      collegeAdminAPI.getScoreStats(userId).catch(e => { console.error('[college-admin dashboard] 成绩失败:', e); return null }),
      collegeAdminAPI.getProfile(userId).catch(e => { console.error('[college-admin dashboard] 档案失败:', e); return null }),
      collegeAdminAPI.getWarnings(userId).catch(e => { console.error('[college-admin dashboard] 预警失败:', e); return null })
    ])

    dash.value = d && typeof d === 'object' ? d : {}
    stats.value = s && typeof s === 'object' ? s : {}
    profile.value = pf && typeof pf === 'object' ? pf : {}
    warnings.value = normalizeWarnings(Array.isArray(wl) ? wl : [])
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

/* ---------- 待办条 ---------- */
.todo {
  display: flex;
  align-items: center;
  margin: 0 $page-padding;
  padding: $spacing-lg;
  border-radius: $radius-md;
  background: rgba(239, 68, 68, 0.1);
}

.todo--clear {
  background: rgba(34, 197, 94, 0.12);
}

.todo:active {
  opacity: 0.8;
}

.todo__left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.todo__num {
  font-size: 56rpx;
  font-weight: 700;
  color: $color-danger;
  font-variant-numeric: tabular-nums;
}

.todo--clear .todo__num {
  color: $color-success;
}

.todo__label {
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.todo__hint {
  flex-shrink: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

/* ---------- 快捷入口 ---------- */
.quick-grid {
  display: flex;
  margin: $spacing-md $page-padding 0;
}

.quick-item {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-lg 0;
  @include card;
}

/* 用 margin 代替 gap：小程序对 flex gap 有基础库/系统版本门槛 */
.quick-item + .quick-item {
  margin-left: $spacing-sm;
}

.quick-item:active {
  opacity: 0.8;
}

.quick-item__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  @include flex-center;
}

.quick-item__label {
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
}

.quick-item__badge {
  position: absolute;
  top: $spacing-md;
  right: 22rpx;
  min-width: 30rpx;
  padding: 0 8rpx;
  border-radius: $radius-pill;
  background: $color-danger;
  color: #fff;
  font-size: $font-xs;
  line-height: 30rpx;
  text-align: center;
}

/* ---------- 概览卡 ---------- */
.section {
  margin-top: $spacing-xs;
}

.card {
  margin: 0 $page-padding;
  padding: $spacing-lg;
  @include card;
}

.metric-grid {
  display: flex;
  flex-wrap: wrap;
}

/* 三列：6 个数正好两行，比四列的两行半整齐 */
.metric {
  width: 33.333%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-sm 0;
}

.metric__label {
  font-size: $font-xs;
  color: $color-text-hint;
  text-align: center;
}

.metric__value {
  margin-top: 6rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.metric__value--danger {
  color: $color-danger;
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

.note {
  display: block;
  margin: $spacing-md $page-padding 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}
</style>
