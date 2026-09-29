<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="首页" :show-back="false" />

    <role-hero
      :title="dash.studentName || userName || '我的学业'"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 学分进度：`completedCredits / requiredCredits` 是后端给的两个真实数字，
             它们同时也是"能否毕业"这个问题的答案，放在首屏比任何说明文字都直接。 -->
        <view class="card progress-card">
          <view class="progress-card__head">
            <text class="progress-card__title">学分进度</text>
            <text class="progress-card__pct">{{ creditPct }}%</text>
          </view>
          <view class="progress-track">
            <view class="progress-fill" :style="{ width: creditPct + '%' }"></view>
          </view>
          <view class="progress-card__foot">
            <text>已修 {{ dash.completedCredits }} / {{ dash.requiredCredits }} 学分</text>
            <text class="progress-card__gap">还差 {{ remainingCredits }} 学分</text>
          </view>
        </view>

        <!-- 快捷入口：图标全部走 app-icon，不再用「成/预/帮/析」这种汉字方块 -->
        <view class="quick-grid">
          <view v-for="q in quickLinks" :key="q.url" class="quick-item" @tap="go(q.url)">
            <!-- 不传 color：app-icon 默认取当前角色主色。
                 切记**不能**传 var(--color-primary) —— SVG 作为 data URI 是独立
                 光栅化的，里面的 CSS 变量不解析，图标会整块消失。 -->
            <view class="quick-item__icon">
              <app-icon :name="q.icon" :size="44" />
            </view>
            <text class="quick-item__label">{{ q.label }}</text>
            <text v-if="q.badge" class="quick-item__badge">{{ q.badge }}</text>
          </view>
        </view>

        <view class="section">
          <section-title title="最近预警" extra="查看全部" arrow @extra="go('/pages/student/warnings')" />
          <empty-state
            v-if="!recentWarnings.length"
            text="暂无预警记录"
            hint="没有预警是好消息：说明当前没有触发任何预警规则"
            icon="check"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(w, i) in recentWarnings"
              :key="w.id"
              class="warn-row"
              :class="{ 'warn-row--divided': i > 0 }"
              @tap="go('/pages/student/warnings')"
            >
              <view class="warn-row__main">
                <text class="warn-row__title">{{ w.title }}</text>
                <text class="warn-row__desc">{{ w.description }}</text>
                <text class="warn-row__time">{{ w.createdDate }}</text>
              </view>
              <warning-tag :level="w.level" />
            </view>
          </view>
        </view>

        <view class="section">
          <!-- 走 extra 插槽而不是 extra 属性：属性分支会自动带一个箭头，
               暗示"点得进去"，但学业建议没有独立页面，箭头是个空承诺。 -->
          <section-title title="学业建议">
            <template #extra>
              <text v-if="suggestions.length" class="section-count">{{ suggestions.length }} 条</text>
            </template>
          </section-title>
          <empty-state
            v-if="!suggestions.length"
            text="暂无学业建议"
            hint="没有低于及格线的课程时，系统不会生成补习建议"
            icon="doc"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(s, i) in suggestions"
              :key="s.id || s.courseId"
              class="suggest-row"
              :class="{ 'suggest-row--divided': i > 0 }"
            >
              <view class="suggest-row__head">
                <text class="suggest-row__course">{{ s.courseName }}</text>
                <view class="suggest-row__score">
                  <score-cell :score="s.score" />
                  <text class="suggest-row__score-label">分</text>
                </view>
              </view>
              <text class="suggest-row__content">{{ s.content }}</text>
            </view>
          </view>
        </view>

        <view class="section section--last">
          <section-title title="帮扶计划" extra="查看全部" arrow @extra="go('/pages/student/assistance')" />
          <empty-state
            v-if="!plans.length"
            text="暂无帮扶计划"
            hint="辅导员为你创建帮扶计划后会出现在这里"
            icon="target"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(p, i) in plans"
              :key="p.id"
              class="plan-row"
              :class="{ 'plan-row--divided': i > 0 }"
              @tap="go('/pages/student/assistance')"
            >
              <view class="plan-row__head">
                <text class="plan-row__title">{{ p.title }}</text>
                <text class="plan-row__status">{{ p.statusText || '' }}</text>
              </view>
              <view class="plan-row__bar">
                <view class="progress-track progress-track--slim">
                  <view class="progress-fill" :style="{ width: barWidth(p.progress) }"></view>
                </view>
                <text class="plan-row__pct">{{ p.progress || 0 }}%</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { normalizeWarnings, countWarnings } from '@/utils/warning.js'
import { number, EMPTY_TEXT } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const dash = ref({})
const warnings = ref([])
const suggestions = ref([])
const plans = ref([])

const subtitle = computed(() => {
  const parts = [dash.value.className, dash.value.majorName].filter(Boolean)
  return parts.join(' · ')
})

const avatarText = computed(() => {
  const name = dash.value.studentName || userName
  return name ? String(name).slice(0, 1) : ''
})

/**
 * 四个数与成绩页同源：都来自后端算好的口径（`completedCredits` / `failedCoursesCount`
 * / `gpa`），不是前端重算的 —— 首页和成绩页对同一件事给出两个数字是最难解释的 bug。
 *
 * 预警数**不取** `dash.warningCount`，也**不取** `redWarnings`/`yellowWarnings`：
 * 后两者后端恒为 null，取它永远是 0。统一由 warnings 列表本地统计，
 * 与预警页看到的条数天然一致。
 */
const heroStats = computed(() => {
  const c = countWarnings(warnings.value)
  return [
    { label: '已通过学分', value: dash.value.completedCredits ?? EMPTY_TEXT },
    { label: '挂科', value: dash.value.failedCoursesCount ?? EMPTY_TEXT },
    { label: '绩点', value: dash.value.gpa == null ? EMPTY_TEXT : number(dash.value.gpa, 1) },
    { label: '预警', value: c.total, onTap: () => go('/pages/student/warnings') }
  ]
})

const creditPct = computed(() => {
  const done = Number(dash.value.completedCredits)
  const need = Number(dash.value.requiredCredits)
  if (!Number.isFinite(done) || !Number.isFinite(need) || need <= 0) return 0
  return Math.min(100, Math.round((done / need) * 1000) / 10)
})

const remainingCredits = computed(() => {
  const done = Number(dash.value.completedCredits)
  const need = Number(dash.value.requiredCredits)
  if (!Number.isFinite(done) || !Number.isFinite(need)) return EMPTY_TEXT
  return Math.max(0, need - done)
})

const recentWarnings = computed(() => normalizeWarnings(dash.value.recentWarnings).slice(0, 3))

const quickLinks = computed(() => {
  const c = countWarnings(warnings.value)
  return [
    { label: '成绩查询', icon: 'book', url: '/pages/student/scores' },
    { label: '预警记录', icon: 'alert', url: '/pages/student/warnings', badge: c.total || '' },
    { label: '帮扶计划', icon: 'target', url: '/pages/student/assistance' },
    { label: '对标分析', icon: 'chart', url: '/pages/student/benchmark-analysis' }
  ]
})

/** 进度条宽度。progress 是 0-100 的整数，但脏数据可能越界，钳一下免得撑破卡片。 */
function barWidth(progress) {
  const n = Number(progress)
  if (!Number.isFinite(n)) return '0%'
  return Math.max(0, Math.min(100, n)) + '%'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    // 首页的四块内容彼此独立，一块挂掉不该让整页空白 —— 各自兜底后仍展示其余部分。
    const [d, w, s, p] = await Promise.all([
      studentAPI.getDashboard(userId).catch(e => { console.error('[dashboard] 概览加载失败:', e); return null }),
      studentAPI.getWarnings(userId).catch(e => { console.error('[dashboard] 预警加载失败:', e); return [] }),
      studentAPI.getSuggestions(userId).catch(e => { console.error('[dashboard] 建议加载失败:', e); return [] }),
      studentAPI.getAssistancePlans(userId).catch(e => { console.error('[dashboard] 帮扶加载失败:', e); return [] })
    ])

    dash.value = d && typeof d === 'object' ? d : {}
    warnings.value = Array.isArray(w) ? w : []
    suggestions.value = (Array.isArray(s) ? s : []).slice(0, 3)
    plans.value = (Array.isArray(p) ? p : []).slice(0, 2)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
/* page-body 的上内边距在 scores 页是靠 filter-bar 撑的，这里没有筛选条，
   卡片要自己浮上来压住 hero 的圆角 */
.page-body {
  padding-top: $spacing-lg;
}

.card {
  padding: $spacing-lg;
  margin: 0 $page-padding;
  @include card;
}

/* ---------- 学分进度 ---------- */
.progress-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-card__title {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.progress-card__pct {
  font-size: $font-lg;
  font-weight: 600;
  color: var(--color-primary, #{$role-student});
  font-variant-numeric: tabular-nums;
}

.progress-track {
  position: relative;
  height: 14rpx;
  margin-top: $spacing-md;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.progress-track--slim {
  height: 10rpx;
  margin-top: 0;
  flex: 1;
}

.progress-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--gradient-hero, #{$role-student});
}

.progress-card__foot {
  display: flex;
  justify-content: space-between;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.progress-card__gap {
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

.section-count {
  font-size: $font-sm;
  color: $color-text-hint;
}

.section--last {
  padding-bottom: $spacing-3xl;
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
  display: flex;
  flex-direction: column;
  margin-right: $spacing-sm;
}

.warn-row__title {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.warn-row__desc {
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
}

.warn-row__time {
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 建议 ---------- */
.suggest-row--divided {
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1rpx solid $color-divider;
}

.suggest-row__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.suggest-row__course {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.suggest-row__score {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  margin-left: $spacing-sm;
}

.suggest-row__score-label {
  margin-left: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.suggest-row__content {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
}

/* ---------- 帮扶 ---------- */
.plan-row--divided {
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1rpx solid $color-divider;
}

.plan-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plan-row__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.plan-row__status {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary, #{$role-student});
}

.plan-row__bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.plan-row__pct {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}
</style>
