<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="教师工作台" :show-back="false" />

    <role-hero
      :title="teacherName || userName || '教师'"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 待办：数据来自 /teachers/todos，它只返回 count>0 的项，
             是这一页唯一可信的待办来源。
             首页概览里的 pendingFeedbacks 后端写死 0、pendingRequests 数的又是
             "全部申请"而不是待审核，两个都不能拿来渲染待办。 -->
        <view class="section section--first">
          <section-title title="待办事项" :extra="todoItems.length ? '需要你处理' : '暂无待办'" />
          <view v-if="!todoItems.length" class="card todo-empty">
            <app-icon name="check" :size="40" color="#22c55e" />
            <text class="todo-empty__text">当前没有待处理事项</text>
          </view>
          <view v-else class="card card--flush">
            <view
              v-for="(t, i) in todoItems"
              :key="t.type + '-' + t.id"
              class="todo-row"
              :class="{ 'todo-row--divided': i > 0 }"
              @tap="go(todoUrl(t))"
            >
              <view class="todo-row__icon">
                <app-icon :name="t.type === 'warning' ? 'alert' : 'mail'" :size="40" />
              </view>
              <view class="todo-row__main">
                <text class="todo-row__title">{{ t.title }}</text>
                <text class="todo-row__hint">{{ todoHint(t) }}</text>
              </view>
              <text class="todo-row__count">{{ t.count }}</text>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>
        </view>

        <view class="section">
          <section-title title="我的班级" :extra="`共 ${classes.length} 个`" arrow @extra="go('/pages/teacher/class-management')" />
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
              @tap="go('/pages/teacher/students')"
            >
              <view class="class-row__main">
                <text class="class-row__name">{{ c.name }}</text>
                <!-- 0 人班要说明一句：库里真实存在 0 人的班（计算机2101班），
                     不解释会被当成数据显示错了。 -->
                <text v-if="!numOr(c.studentCount)" class="class-row__note">该班暂无学生</text>
              </view>
              <text class="class-row__count">{{ numOr(c.studentCount) }} 人</text>
            </view>
          </view>
        </view>

        <!-- 最近预警。等级与状态字段都是**中文**，统一过 utils/warning.js 归一化，
             页面里不出现任何 level === 3 之类的比较。 -->
        <view class="section section--last">
          <section-title title="最近预警" extra="查看全部" arrow @extra="go('/pages/teacher/warnings')" />
          <empty-state
            v-if="!warnings.length"
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
              @tap="go('/pages/teacher/warnings')"
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
                <text class="tag" :class="w.isPending ? 'tag--warning' : 'tag--success'">{{ w.statusText }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>

    <app-tab-bar role="teacher" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { normalizeWarnings, countWarnings } from '@/utils/warning.js'
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('teacher')

const loading = ref(true)
const dash = ref({})
const todos = ref([])
const classes = ref([])
const warnings = ref([])

const teacherName = computed(() => dash.value.teacherName || '')

/** 副标题用真实班级数据拼，不用后端没给的学院名（教师概览接口没返回学院）。 */
const subtitle = computed(() => {
  const c = numOr(dash.value.totalClasses)
  const s = numOr(dash.value.totalStudents)
  if (!c && !s) return '教学工作台'
  return `带 ${c} 个班 · ${s} 名学生`
})

const avatarText = computed(() => {
  const name = teacherName.value || userName
  return name ? String(name).slice(0, 1) : ''
})

const summary = computed(() => countWarnings(warnings.value))

/** 只保留 count>0 的项。后端已经这么做了，这里再兜一次底，免得 0 条也占一行。 */
const todoItems = computed(() =>
  (Array.isArray(todos.value) ? todos.value : []).filter(t => numOr(t.count) > 0)
)

const heroStats = computed(() => [
  { label: '我的班级', value: numOr(dash.value.totalClasses) },
  { label: '我的学生', value: numOr(dash.value.totalStudents) },
  { label: '预警总数', value: summary.value.total, onTap: () => go('/pages/teacher/warnings') },
  { label: '待处理', value: summary.value.pending, onTap: () => go('/pages/teacher/warnings') }
])

const recentWarnings = computed(() => warnings.value.slice(0, 3))

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 待办点进去哪一页。 */
function todoUrl(t) {
  if (t.type === 'warning') return '/pages/teacher/warnings'
  if (t.type === 'feedback') return '/pages/teacher/feedback-management'
  return '/pages/teacher/dashboard'
}

/** 待办说明：讲清"这个数是什么"，避免只给一个裸数字。 */
function todoHint(t) {
  if (t.type === 'warning') return '班级学生触发了预警规则，需要跟进'
  if (t.type === 'feedback') return '学生提交的反馈还没有回复'
  return ''
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [d, t, w, c] = await Promise.all([
      teacherAPI.getDashboard(userId).catch(e => { console.error('[teacher dashboard] 概览失败:', e); return null }),
      teacherAPI.getTodos(userId).catch(e => { console.error('[teacher dashboard] 待办失败:', e); return [] }),
      teacherAPI.getWarnings(userId).catch(e => { console.error('[teacher dashboard] 预警失败:', e); return [] }),
      teacherAPI.getMyClasses(userId).catch(e => { console.error('[teacher dashboard] 班级失败:', e); return [] })
    ])

    dash.value = d && typeof d === 'object' ? d : {}
    todos.value = Array.isArray(t) ? t : []
    warnings.value = normalizeWarnings(w)
    classes.value = (Array.isArray(c) ? c : [])
      // 有学生的班排前面：0 人班排在最前会让人以为工作台是空的
      .slice()
      .sort((a, b) => numOr(b.studentCount) - numOr(a.studentCount))
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

/* ---------- 待办 ---------- */
.todo-empty {
  display: flex;
  align-items: center;
}

.todo-empty__text {
  margin-left: $spacing-sm;
  font-size: $font-md;
  color: $color-text-secondary;
}

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
