<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="辅导员工作台" :show-back="false" />

    <role-hero
      :title="counselor.name || userName || '辅导员'"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 待办条：辅导员每天上班第一眼要看的就这一个数。
             数据来自预警列表里 status=0 的条数，与预警页同源，不会出现"首页说 3 条、点进去 2 条"。 -->
        <view class="todo" :class="{ 'todo--clear': !pendingCount }" @tap="go('/pages/counselor/warnings')">
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

        <!-- 学分达标概览：这两个数一个来自花名册、一个来自有成绩的人，
             分母不同，所以并排展示而不是算成一个百分比。 -->
        <view class="section">
          <section-title title="学分达标" extra="按已修成绩统计" />
          <view class="card metric-grid">
            <view class="metric">
              <text class="metric__label">带班学生</text>
              <text class="metric__value">{{ numOr(credit.totalStudents) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">已有成绩</text>
              <text class="metric__value">{{ numOr(credit.gradedStudents) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">学分达标</text>
              <text class="metric__value">{{ numOr(credit.creditSufficient) }}</text>
            </view>
            <view class="metric">
              <text class="metric__label">存在挂科</text>
              <text class="metric__value metric__value--danger">{{ numOr(credit.creditInsufficient) }}</text>
            </view>
          </view>
        </view>

        <view class="section">
          <section-title title="我的班级" :extra="`共 ${classes.length} 个`" />
          <empty-state
            v-if="!classes.length"
            text="暂未管理任何班级"
            hint="可在「班级管理」里申请管理班级"
            icon="building"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(c, i) in classes"
              :key="c.id"
              class="class-row"
              :class="{ 'class-row--divided': i > 0 }"
              @tap="go('/pages/counselor/class-management')"
            >
              <view class="class-row__main">
                <text class="class-row__name">{{ c.name }}</text>
                <!-- 有 0 人班级时给一句说明：不解释会被当成数据显示错了。
                     这是当前库的真实状态 —— 该辅导员名下 6 个班，只有 1 个班有学生。 -->
                <text v-if="!numOr(c.studentCount)" class="class-row__note">该班暂无学生</text>
              </view>
              <text class="class-row__count">{{ numOr(c.studentCount) }} 人</text>
            </view>
          </view>
        </view>

        <view class="section section--last">
          <section-title title="最近预警" extra="查看全部" arrow @extra="go('/pages/counselor/warnings')" />
          <empty-state
            v-if="!recentWarnings.length"
            text="暂无预警记录"
            hint="所带班级没有触发任何预警规则"
            icon="check"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(w, i) in recentWarnings"
              :key="w.id"
              class="warn-row"
              :class="{ 'warn-row--divided': i > 0 }"
              @tap="go('/pages/counselor/warnings')"
            >
              <view class="warn-row__main">
                <view class="warn-row__top">
                  <text class="warn-row__name">{{ w.studentName || '未知学生' }}</text>
                  <text class="warn-row__class">{{ w.className }}</text>
                </view>
                <text class="warn-row__desc">{{ w.description || '暂无说明' }}</text>
                <text class="warn-row__time">{{ w.createdDate }}</text>
              </view>
              <view class="warn-row__right">
                <warning-tag :level="w.level" />
                <text class="tag" :class="warningStatusTagClass(w.status)">{{ w.statusText }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>

    <app-tab-bar role="counselor" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { normalizeWarnings, countWarnings, warningStatusTagClass } from '@/utils/warning.js'
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

const loading = ref(true)
const counselor = ref({})
const classes = ref([])
const credit = ref({})
const warnings = ref([])

const subtitle = computed(() => {
  const parts = [counselor.value.collegeName, counselor.value.department].filter(Boolean)
  return parts.join(' · ') || '学生工作'
})

const avatarText = computed(() => {
  const name = counselor.value.name || userName
  return name ? String(name).slice(0, 1) : ''
})

/**
 * 四个数**全部**取自预警列表本地统计，一处数字只有一个来源。
 *
 * 后端 dashboard 其实也给了 `warningCount` / `redWarnings` / `yellowWarnings` / `blueWarnings`
 * （与 `/students/dashboard` 不同，辅导员端这几项是真算出来的），但它是按等级分桶、
 * **没有待处理/已处理**这个概念，凑不出这四个数。既然总得有一项来自列表，
 * 就整组都从列表取 —— 混用两个来源迟早出现"总数对不上分项之和"。
 */
const summary = computed(() => countWarnings(warnings.value))

const pendingCount = computed(() => summary.value.pending)

const heroStats = computed(() => {
  const s = summary.value
  return [
    { label: '带班学生', value: numOr(credit.value.totalStudents) },
    { label: '预警总数', value: s.total },
    { label: '待处理', value: s.pending, onTap: () => go('/pages/counselor/warnings') },
    { label: '已处理', value: s.handled, onTap: () => go('/pages/counselor/warnings') },
    // 2026-10-02（W1.6）：补第三态。少了它，"总数"就不等于"待处理+已处理"
    // —— 这个注释上面刚说过"一处数字只有一个来源"，状态桶也一样。
    { label: '已解除', value: s.dispelled, onTap: () => go('/pages/counselor/warnings') }
  ]
})

const recentWarnings = computed(() => warnings.value.slice(0, 3))

const quickLinks = computed(() => [
  { label: '预警处理', icon: 'alert', url: '/pages/counselor/warnings', badge: pendingCount.value || '' },
  { label: '帮扶计划', icon: 'target', url: '/pages/counselor/assistance' },
  { label: '学生管理', icon: 'users', url: '/pages/counselor/students' },
  { label: '数据分析', icon: 'chart', url: '/pages/counselor/analytics' }
])

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    /**
     * 四块内容彼此独立，一块挂掉不该让整页空白 —— 各自兜底。
     *
     * 最近预警刻意**不用** dashboard 返回的 `recentWarnings`：
     * 那是 AcademicWarning 实体，字段里**没有 className**，而这一页最需要的
     * 恰恰是"哪条预警属于哪个班"。改用 `/counselors/warnings`（后端已经查好 className），
     * 同一个请求顺便把待处理条数也拿到，总量与分项同源。
     */
    const [d, w, c] = await Promise.all([
      counselorAPI.getDashboard(userId).catch(e => { console.error('[counselor dashboard] 概览失败:', e); return null }),
      counselorAPI.getWarnings(userId).catch(e => { console.error('[counselor dashboard] 预警失败:', e); return [] }),
      counselorAPI.getCreditMonitor(userId).catch(e => { console.error('[counselor dashboard] 学分监控失败:', e); return null })
    ])

    const dash = d && typeof d === 'object' ? d : {}
    counselor.value = dash.counselor && typeof dash.counselor === 'object' ? dash.counselor : {}
    classes.value = (Array.isArray(dash.classes) ? dash.classes : [])
      // 有学生的班排前面：0 人班排在真实班级前面会让人以为工作台是空的
      .slice()
      .sort((a, b) => numOr(b.studentCount) - numOr(a.studentCount))
    warnings.value = normalizeWarnings(w)
    credit.value = c && typeof c === 'object' ? c : {}
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

/* ---------- 待办条 ---------- */
.todo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 $page-padding;
  padding: $spacing-lg;
  border-radius: $radius-lg;
  background: var(--gradient-hero, #{$role-counselor});
  box-shadow: $shadow-raised;
}

/* 没有待办时降级成中性灰：绿色渐变会让人以为"还有事没做" */
.todo--clear {
  background: $color-card;
  border: 1rpx solid $color-divider;
}

.todo__left {
  display: flex;
  align-items: baseline;
}

.todo__num {
  font-size: $font-2xl;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  font-variant-numeric: tabular-nums;
}

.todo--clear .todo__num {
  color: $color-text;
}

.todo__label {
  margin-left: $spacing-sm;
  font-size: $font-md;
  color: rgba(255, 255, 255, 0.9);
}

.todo--clear .todo__label {
  color: $color-text-secondary;
}

.todo__hint {
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.92);
}

.todo--clear .todo__hint {
  color: $color-text-hint;
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

/* ---------- 分节 ---------- */
.section {
  margin-top: $spacing-xl;
}

.section--last {
  padding-bottom: $spacing-3xl;
}

/* ---------- 学分达标 ---------- */
.metric-grid {
  display: flex;
  flex-wrap: wrap;
}

.metric {
  width: 50%;
  padding: $spacing-sm 0;
}

.metric__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.metric__value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xl;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.metric__value--danger {
  color: $color-danger;
}

/* ---------- 班级 ---------- */
.class-row {
  min-height: $tap-min;
  display: flex;
  align-items: center;
}

.class-row--divided {
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1rpx solid $color-divider;
}

.class-row__main {
  flex: 1;
  min-width: 0;
}

.class-row__name {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.class-row__note {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.class-row__count {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

/* ---------- 预警 ---------- */
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

.warn-row__class {
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
