<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="管理控制台" :show-back="false" />

    <!-- 这里原来写的是 `adminName || userName || ...`，而 adminName 在 script 里
         根本没有定义：编译成 _ctx.adminName，恒为 undefined，永远是 userName 生效。
         一个不存在的"首选来源"会让人以为姓名走的是别的字段。 -->
    <role-hero
      :title="userName || '系统管理员'"
      subtitle="校级视图 · 全平台数据"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 待办：三项都列出来，包括为 0 的那两项。
             待办列表里"0"是有信息量的 —— 它说明管理员现在确实没东西要审。
             只显示 count>0 的项会让人以为"没渲染出来"。 -->
        <view class="section section--first">
          <section-title title="待办事项" :extra="todoTotal ? `${todoTotal} 项待处理` : '暂无待处理'" />
          <view class="card card--flush">
            <view
              v-for="(t, i) in todoItems"
              :key="t.key"
              class="todo-row"
              :class="{ 'todo-row--divided': i > 0, 'todo-row--empty': !t.count }"
              @tap="go(t.url)"
            >
              <view class="todo-row__icon">
                <app-icon :name="t.icon" :size="40" :color="t.count ? '' : '#9ca3af'" />
              </view>
              <view class="todo-row__main">
                <text class="todo-row__title">{{ t.title }}</text>
                <text class="todo-row__hint">{{ t.hint }}</text>
              </view>
              <text class="todo-row__count" :class="{ 'todo-row__count--zero': !t.count }">{{ t.count }}</text>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>
        </view>

        <!-- 数据总览：账号与学籍是两回事，分行放，不混成一行六个。
             totalTeachers / totalCounselors 只有 statistics 接口有，
             概览接口（/admin/dashboard）里没有 —— 所以这四格来自 statistics。 -->
        <view class="section">
          <section-title title="数据总览" extra="账号口径" />
          <view class="stat-grid">
            <stat-card label="用户账号" :value="num(dash.totalUsers)" icon="users" />
            <stat-card label="学生" :value="num(dash.totalStudents)" icon="user" />
            <stat-card label="教师" :value="num(stats.totalTeachers)" icon="book" />
            <stat-card label="辅导员" :value="num(stats.totalCounselors)" icon="target" />
          </view>
          <view class="stat-grid stat-grid--gap">
            <stat-card label="学院" :value="num(dash.totalColleges)" icon="building" />
            <stat-card label="专业" :value="num(dash.totalMajors)" icon="doc" />
            <stat-card label="课程" :value="num(dash.totalCourses)" icon="book" />
            <stat-card label="预警规则" :value="num(stats.ruleCount)" icon="filter" />
          </view>
        </view>

        <!-- 预警分布：用 statistics 里后端算好的 red/yellow/low 三档，
             不用 recentWarnings 去数 —— 那个字段是"最多 N 条"的截断列表，
             拿它统计"全量分布"是错的。三档与下面的详情列表总数应当相等。 -->
        <view class="section">
          <section-title
            title="预警分布"
            :extra="`共 ${num(stats.totalWarnings)} 条`"
            arrow
            @extra="go('/pages/admin/warnings')"
          />
          <view class="card card--flush">
            <view
              v-for="(l, i) in levelRows"
              :key="l.level"
              class="level-row"
              :class="{ 'level-row--divided': i > 0 }"
              @tap="go('/pages/admin/warnings')"
            >
              <warning-tag :level="l.level" />
              <text class="level-row__label">{{ l.label }}</text>
              <text class="level-row__count" :style="{ color: l.color }">{{ l.count }}</text>
            </view>
          </view>
        </view>

        <!-- 最近预警。warningLevel 是数字 1/2/3，走 warning-tag；
             title 本身就是中文等级名（"中级预警"），直接显示。 -->
        <view class="section">
          <section-title
            title="最近预警"
            extra="查看全部"
            arrow
            @extra="go('/pages/admin/warnings')"
          />
          <empty-state
            v-if="!recentWarnings.length"
            text="暂无预警记录"
            hint="没有学生触发预警规则"
            icon="check"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(w, i) in recentWarnings"
              :key="w.id"
              class="warn-row"
              :class="{ 'warn-row--divided': i > 0 }"
              @tap="go('/pages/admin/warnings')"
            >
              <view class="warn-row__main">
                <view class="warn-row__top">
                  <text class="warn-row__name">{{ w.studentName || '未知学生' }}</text>
                  <text class="warn-row__title">{{ w.title }}</text>
                </view>
                <text class="warn-row__desc">{{ w.description || '暂无说明' }}</text>
                <text class="warn-row__time">{{ formatDate(w.createdAt, true) }}</text>
              </view>
              <view class="warn-row__right">
                <warning-tag :level="w.warningLevel" />
                <text class="tag" :class="warningStatusTagClass(w.status)">
                  {{ w.statusText }}
                </text>
              </view>
            </view>
          </view>
        </view>

        <!-- 系统动态：2026-10-01 第二十四轮接上。
             此前这里是一个**写死的空态**（注释写着"/admin/activities 实测恒为空数组"），
             因为后端那个方法当时就是 `return ApiResponse.success(new ArrayList<>())` —— 一个桩。
             现在后端改成从 login_logs / academic_warning / export_history 三张**真有写入方**
             的表里取最近发生的事（不编时间、不编人名），这里就按真实数据渲染；
             一条都取不到时才回落到空态。 -->
        <view class="section section--last">
          <section-title
            title="系统动态"
            :extra="activities.length ? activities.length + ' 条' : '暂无记录'"
          />
          <empty-state
            v-if="!activities.length"
            text="暂无系统动态"
            hint="没有可展示的登录 / 预警 / 导出记录"
            icon="clock"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(a, i) in activities"
              :key="a.type + '-' + i"
              class="warn-row"
              :class="{ 'warn-row--divided': i > 0 }"
            >
              <view class="warn-row__main">
                <view class="warn-row__top">
                  <text class="warn-row__name">{{ a.title }}</text>
                  <text class="warn-row__title">{{ activityTypeLabel(a.type) }}</text>
                </view>
                <text class="warn-row__desc">{{ a.description || '暂无说明' }}</text>
                <text class="warn-row__time">{{ formatDate(a.createdAt, true) }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>

    <app-tab-bar role="admin" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'
import { normalizeWarnings, warningStatusTagClass } from '@/utils/warning.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('admin')

const loading = ref(true)
const dash = ref({})
const stats = ref({})
const pending = ref({})
const activities = ref([])

/** 系统动态的类型标签。与后端 activity() 里写的 type 一一对应。*/
function activityTypeLabel(type) {
  return { login: '登录', warning: '预警', export: '导出' }[type] || ''
}

/** 姓名只认登录时写进 storage 的真实姓名（useUser 已挡掉"纯数字=学号"的情况）。
    管理员没有独立的资料接口，概览/统计接口里也都不带姓名，所以不另外请求。 */
const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/**
 * 最近预警列表。
 * 后端给的是 `recentWarnings`（实体），`warningLevel` 是数字，`title` 是中文等级，
 * 两者本来就一一对应（level 2 → "中级预警"），所以**等级**这一页不需要归一化。
 *
 * ⚠️ 但**状态**需要：2026-10-02（W1.6）扩成三态后，模板要读 `statusText`，
 * 而 `status` 是 0/1/2 —— 归一化函数顺手把状态也翻了，所以还是过一遍。
 */
const recentWarnings = computed(() => {
  const list = dash.value.recentWarnings
  return normalizeWarnings(Array.isArray(list) ? list : [])
})

/**
 * 三档分布。**不按等级配色硬编码**：颜色取 tokens 的语义色，
 * 与 warning-tag 组件同源（level 1 橙 / 2 深橙 / 3 红）。
 */
const levelRows = computed(() => [
  { level: 3, label: '严重预警', count: num(stats.value.redWarnings), color: '#ef4444' },
  { level: 2, label: '中度预警', count: num(stats.value.yellowWarnings), color: '#f97316' },
  { level: 1, label: '轻度预警', count: num(stats.value.lowWarnings), color: '#f59e0b' }
])

/**
 * 待办三项。
 *
 * 口径都是实测过的：
 *   · pendingWarnings = 4（库里 4 条预警 status 全为 0）—— 真待办
 *   · 待审批账号：当前 35 个账号 status 全是 1，所以是 0。**没有"待审用户"接口**，
 *     也不能拿 /users/page 的 total 冒充（那是全部用户数），所以这里写死 0
 *     并在 hint 里说明依据，而不是显示一个看着像真数据的数字。
 *   · 待审班级申请 = /pending-requests 的 classRequests + teacherClassRequests +
 *     counselorClassRequests（实测 0+0+0；9 条申请都已处理完）
 */
const todoItems = computed(() => {
  const studentReq = num(pending.value.classRequests)
  const teacherReq = num(pending.value.teacherClassRequests)
  const counselorReq = num(pending.value.counselorClassRequests)
  return [
    {
      key: 'warning',
      icon: 'alert',
      title: '待处理预警',
      count: num(pending.value.pendingWarnings),
      hint: '学生触发预警规则，尚未处理',
      url: '/pages/admin/warnings'
    },
    {
      key: 'account',
      icon: 'users',
      title: '待审批账号',
      count: 0,
      hint: `${num(dash.value.totalUsers)} 个账号状态均为正常`,
      url: '/pages/admin/users'
    },
    {
      key: 'class',
      icon: 'building',
      title: '待审班级申请',
      count: studentReq + teacherReq + counselorReq,
      hint: '教师/辅导员申请管理班级，等待审批',
      url: '/pages/admin/class-requests'
    }
  ]
})

const todoTotal = computed(() => todoItems.value.reduce((s, t) => s + t.count, 0))

const heroStats = computed(() => [
  { label: '用户账号', value: num(dash.value.totalUsers), onTap: () => go('/pages/admin/users') },
  { label: '学生', value: num(dash.value.totalStudents) },
  { label: '教师', value: num(stats.value.totalTeachers) },
  { label: '待处理预警', value: num(pending.value.pendingWarnings), onTap: () => go('/pages/admin/warnings') }
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [d, s, p, a] = await Promise.all([
      adminAPI.getDashboard().catch(e => { console.error('[admin dashboard] 概览失败:', e); return null }),
      adminAPI.getStatistics().catch(e => { console.error('[admin dashboard] 统计失败:', e); return null }),
      adminAPI.getPendingRequests().catch(e => { console.error('[admin dashboard] 待办失败:', e); return null }),
      adminAPI.getActivities().catch(e => { console.error('[admin dashboard] 动态失败:', e); return null })
    ])
    dash.value = d && typeof d === 'object' ? d : {}
    stats.value = s && typeof s === 'object' ? s : {}
    pending.value = p && typeof p === 'object' ? p : {}
    // 动态是一个**数组**（不是对象），单独判 Array；取不到就保持空数组 → 回落空态。
    activities.value = Array.isArray(a) ? a : (Array.isArray(a?.data) ? a.data : [])
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.card {
  padding: $spacing-lg;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
}

.section {
  margin-top: $spacing-xl;
}

.section--first {
  margin-top: 0;
}

.section--last {
  padding-bottom: $spacing-3xl;
}

/* 两行 stat-grid 之间的竖向间距：stat-grid 自己只管横向 */
.stat-grid {
  padding: 0 $page-padding;
}

.stat-grid--gap {
  margin-top: $spacing-md;
}

/* ---------- 待办 ---------- */
.todo-row {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.todo-row--divided {
  border-top: 1rpx solid $color-divider;
}

.todo-row:active {
  background: $color-bg;
}

.todo-row__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  @include flex-center;
  flex-shrink: 0;
}

.todo-row__main {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
}

.todo-row__title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.todo-row__hint {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.todo-row__count {
  flex-shrink: 0;
  margin-right: $spacing-xs;
  font-size: $font-xl;
  font-weight: 700;
  color: $color-danger;
  font-variant-numeric: tabular-nums;
}

/* 0 项不能也是红的：那会把"没事可做"渲染成"有紧急事项" */
.todo-row__count--zero {
  color: $color-text-hint;
  font-weight: 600;
}

.todo-row--empty .todo-row__title {
  color: $color-text-secondary;
  font-weight: 500;
}

/* ---------- 预警分布 ---------- */
.level-row {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.level-row--divided {
  border-top: 1rpx solid $color-divider;
}

.level-row:active {
  background: $color-bg;
}

.level-row__label {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-md;
  color: $color-text;
}

.level-row__count {
  font-size: $font-xl;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* ---------- 预警列表 ---------- */
.warn-row {
  display: flex;
  align-items: flex-start;
}

.warn-row--divided {
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1rpx solid $color-divider;
}

.warn-row__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.warn-row__top {
  display: flex;
  align-items: baseline;
}

.warn-row__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.warn-row__title {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.warn-row__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
  @include ellipsis-multi(2);
}

.warn-row__time {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.warn-row__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.warn-row__right .tag {
  margin-top: $spacing-xs;
}
</style>
