<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="对标分析" />

    <role-hero
      :title="latest.studentName || userName || '对标分析'"
      :subtitle="latest.className ? `${latest.className} · 学期对标` : '与班级/专业平均水平对比'"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" mode="card" />

      <template v-else>
        <!-- 位次：班级 / 专业 -->
        <view class="card">
          <section-title title="学业位次" extra="GPA 排名" />
          <view class="rank-row">
            <view class="rank-cell">
              <text class="rank-cell__label">班级排名</text>
              <view class="rank-cell__num">
                <text class="rank-cell__value">{{ numOr(latest.classRank) }}</text>
                <text class="rank-cell__total">/ {{ numOr(latest.classTotal) }}</text>
              </view>
              <view class="rank-cell__bar">
                <view class="rank-cell__fill" :style="{ width: pctOf(latest.classRank, latest.classTotal) }" />
              </view>
            </view>
            <view class="rank-cell">
              <text class="rank-cell__label">专业排名</text>
              <view class="rank-cell__num">
                <text class="rank-cell__value">{{ numOr(latest.majorRank) }}</text>
                <text class="rank-cell__total">/ {{ numOr(latest.majorTotal) }}</text>
              </view>
              <view class="rank-cell__bar">
                <view class="rank-cell__fill" :style="{ width: pctOf(latest.majorRank, latest.majorTotal) }" />
              </view>
            </view>
          </view>
          <!-- 当前库里 major_id=2 只含 1 个班，两个名次必然相同。
               不解释会被当成算错了；两个查询本身是不同的（一个按 class_id、一个按 major_id）。 -->
          <view v-if="rankSameScope" class="rank-note">
            本专业当前仅有 1 个班级，故专业排名与班级排名一致。
          </view>
        </view>

        <!-- GPA 对比 -->
        <view class="card">
          <section-title title="GPA 对比" extra="我的 · 班级均值 · 专业均值" />
          <view class="compare">
            <view v-for="c in gpaBars" :key="c.label" class="compare__row">
              <text class="compare__label">{{ c.label }}</text>
              <view class="compare__track">
                <view
                  class="compare__fill"
                  :class="{ 'compare__fill--me': c.me }"
                  :style="{ width: c.width, background: c.me ? 'var(--color-primary)' : undefined }"
                />
              </view>
              <text class="compare__value" :class="{ 'compare__value--me': c.me }">{{ c.text }}</text>
            </view>
          </view>
          <view class="compare__note">{{ gpaVerdict }}</view>
        </view>

        <!-- 学分进度 -->
        <view class="card">
          <section-title title="学分进度" extra="培养方案要求" />
          <view class="credit-head">
            <view class="credit-head__left">
              <text class="credit-head__num">{{ numOr(progress.completedCredits) }}</text>
              <text class="credit-head__slash">/ {{ numOr(progress.totalCredits) }}</text>
            </view>
            <text class="credit-head__pct">{{ pctText(progress.completionRate) }}</text>
          </view>
          <view class="credit-track">
            <view class="credit-fill" :style="{ width: clampPct(progress.completionRate) }" />
          </view>
          <view class="credit-grid">
            <view class="credit-grid__cell">
              <text class="credit-grid__label">已通过课程</text>
              <text class="credit-grid__value">{{ numOr(latest.coursesPassed) }}</text>
            </view>
            <view class="credit-grid__cell">
              <text class="credit-grid__label">未通过课程</text>
              <text class="credit-grid__value credit-grid__value--danger">{{ numOr(latest.coursesFailed) }}</text>
            </view>
            <view class="credit-grid__cell">
              <text class="credit-grid__label">剩余学分</text>
              <text class="credit-grid__value">{{ numOr(progress.remainingCredits) }}</text>
            </view>
            <view class="credit-grid__cell">
              <text class="credit-grid__label">在修学分</text>
              <text class="credit-grid__value">{{ numOr(latest.creditsOnTrack) }}</text>
            </view>
          </view>
        </view>

        <!-- 逐学期达成（progress-report.semesters） -->
        <view v-if="semesters.length" class="card">
          <section-title title="学期学分达成" :extra="`近 ${semesters.length} 个学期`" />
          <view v-for="s in semesters" :key="s.semester" class="sem-row">
            <view class="sem-row__head">
              <text class="sem-row__name">{{ s.semester }}</text>
              <text class="sem-row__ratio">
                {{ numOr(s.completedCredits) }} / {{ numOr(s.requiredCredits) }} 学分
              </text>
            </view>
            <view class="sem-row__track">
              <view class="sem-row__fill" :style="{ width: clampPct(s.completionRate) }" />
            </view>
            <text class="sem-row__pct">{{ pctText(s.completionRate) }}</text>
          </view>
        </view>

        <!-- GPA 走势（history） -->
        <view v-if="history.length" class="card">
          <section-title title="GPA 学期走势" extra="自最早学期起" />
          <view class="trend">
            <view v-for="h in historyAsc" :key="h.semester" class="trend__col">
              <text class="trend__value">{{ numFixed(h.studentGpa, 2) }}</text>
              <view class="trend__bar-wrap">
                <view class="trend__bar" :style="{ height: gpaHeight(h.studentGpa) }" />
              </view>
              <text class="trend__label">{{ shortSemester(h.semesterLabel) }}</text>
            </view>
          </view>
          <view class="trend__axis">
            <text class="trend__axis-text">纵轴 0 – 4.0（绩点上限）</text>
          </view>
        </view>

        <empty-state
          v-if="!hasAny"
          text="暂无对标数据"
          hint="对标数据由教务成绩汇总生成，成绩录入后才会出现"
          icon="trend"
        />
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'

const { userId, userName } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const latest = ref({})
const progress = ref({})
const history = ref([])

/** 绩点上限。后端 ScoreSyncSupport 的分段最高档是 90 分以上 → 4.0。 */
const GPA_MAX = 4
/** 取不到数时的占位符。用破折号而不是 0 —— 0 分和"没数据"是两回事。 */
const EMPTY = '—'

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

const semesters = computed(() => {
  const list = Array.isArray(progress.value.semesters) ? progress.value.semesters : []
  // 后端按学期倒序给出，列表按"最近的在上"读着最顺，保留原序，只截断避免页面过长
  return list.slice(0, 6)
})

/**
 * 走势图专用：**按时间正序**（最早在左、最新在右）。
 * 后端给的是倒序，直接画出来趋势是反的 —— 从右往左读会把"在回升"看成"在滑坡"。
 * 列表（学期学分达成）保持倒序没问题，图不行。
 */
const historyAsc = computed(() => [...history.value].reverse())

const hasAny = computed(() =>
  Boolean(latest.value && Object.keys(latest.value).length) ||
  Boolean(history.value.length) ||
  Boolean(semesters.value.length)
)

const heroStats = computed(() => [
  { label: '绩点', value: latest.value.studentGpa == null ? EMPTY : numFixed(latest.value.studentGpa, 2) },
  { label: '班级排名', value: latest.value.classRank == null ? EMPTY : `${latest.value.classRank}` },
  { label: '已通过课程', value: numOr(latest.value.coursesPassed) },
  { label: '未通过', value: numOr(latest.value.coursesFailed) }
])

/** 三根柱子：我 / 班级均值 / 专业均值。宽度按 4.0 上限折算。 */
const gpaBars = computed(() => {
  const rows = [
    { label: '我的 GPA', value: latest.value.studentGpa, me: true },
    { label: '班级均值', value: latest.value.classAvgGpa, me: false },
    { label: '专业均值', value: latest.value.majorAvgGpa, me: false }
  ]
  return rows.map(r => ({
    ...r,
    width: clampPct(((Number(r.value) || 0) / GPA_MAX) * 100),
    text: r.value == null ? EMPTY : numFixed(r.value, 2)
  }))
})

/** 班级名次与专业名次完全重合（数据上只包含一个班）时，给一句说明避免被当成算错 */
const rankSameScope = computed(() => {
  const cr = Number(latest.value.classRank)
  const mr = Number(latest.value.majorRank)
  const ct = Number(latest.value.classTotal)
  const mt = Number(latest.value.majorTotal)
  if (![cr, mr, ct, mt].every(Number.isFinite)) return false
  return cr === mr && ct === mt && ct > 0
})

const gpaVerdict = computed(() => {
  const me = Number(latest.value.studentGpa)
  const cls = Number(latest.value.classAvgGpa)
  if (!Number.isFinite(me) || !Number.isFinite(cls)) return '数据不足，暂无法比较'
  const diff = me - cls
  if (Math.abs(diff) < 0.05) return '与班级平均水平基本持平'
  return diff > 0
    ? `高于班级均值 ${numFixed(diff, 2)}，处于班级中上水平`
    : `低于班级均值 ${numFixed(Math.abs(diff), 2)}，建议重点关注`
})

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function numFixed(v, d = 2) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(d) : EMPTY
}

function clampPct(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0%'
  return `${Math.max(0, Math.min(100, n))}%`
}

/** 排名进度条：名次越小条越长（第 1 名 = 满条） */
function pctOf(rank, total) {
  const r = Number(rank)
  const t = Number(total)
  if (!Number.isFinite(r) || !Number.isFinite(t) || t <= 0) return '0%'
  return clampPct(((t - r + 1) / t) * 100)
}

function pctText(v) {
  const n = Number(v)
  return Number.isFinite(n) ? `${n.toFixed(1)}%` : EMPTY
}

function gpaHeight(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0%'
  return clampPct((n / GPA_MAX) * 100)
}

/** '2025-2026-2' → '25-26-2'，柱状图下方放不下全称 */
function shortSemester(label) {
  const s = String(label || '')
  return s.replace(/^(\d{2})(\d{2})-(\d{2})(\d{2})/, '$2-$4') || s
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    /**
     * ⚠️ 这四个接口的路径参数叫 studentId，但**必须传 users.id**。
     * 传 profile id 不报错，只静默返回 `{}` / `[]`，页面会表现成"暂无对标数据"。
     * 实测 /benchmark/3/latest → {} ；/benchmark/27/latest → 完整数据。
     * 另外 /benchmark/{i}/semester 实测 500，未接入。
     */
    const [l, p, h] = await Promise.all([
      studentAPI.getLatestBenchmark(userId).catch(e => { console.error('[benchmark] latest 失败:', e); return null }),
      studentAPI.getBenchmarkProgress(userId).catch(e => { console.error('[benchmark] progress 失败:', e); return null }),
      studentAPI.getBenchmarkHistory(userId).catch(e => { console.error('[benchmark] history 失败:', e); return null })
    ])
    latest.value = l && typeof l === 'object' ? l : {}
    progress.value = p && typeof p === 'object' ? p : {}
    history.value = Array.isArray(h) ? h : []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

/* .card 的 padding/margin 走 styles/common.scss 的全局定义，
   这里不再覆盖 —— 与首页保持同一套卡片密度。 */

/* 学分总进度条 */
.credit-track {
  height: 20rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.credit-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

/* ---------- 位次 ---------- */
.rank-row {
  display: flex;
}

.rank-cell {
  flex: 1;
  min-width: 0;
}

.rank-cell:first-child {
  margin-right: $spacing-xl;
}

.rank-cell__label {
  font-size: $font-xs;
  color: $color-text-hint;
}

.rank-cell__num {
  display: flex;
  align-items: baseline;
  margin-top: $spacing-xs;
}

.rank-cell__value {
  font-size: $font-2xl;
  font-weight: 700;
  line-height: 1;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.rank-cell__total {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.rank-cell__bar {
  height: 10rpx;
  margin-top: $spacing-sm;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.rank-cell__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.rank-note {
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.55;
}

/* ---------- GPA 对比 ---------- */
.compare__row {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-md;
}

.compare__label {
  width: 140rpx;
  flex-shrink: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.compare__track {
  flex: 1;
  min-width: 0;
  height: 20rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.compare__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $color-text-hint;
}

.compare__fill--me {
  /* 主色由行内 style 注入（角色主题），这里只兜底高度 */
  background: var(--color-primary);
}

.compare__value {
  width: 96rpx;
  flex-shrink: 0;
  margin-left: $spacing-md;
  text-align: right;
  font-size: $font-sm;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.compare__value--me {
  color: var(--color-primary);
  font-weight: 600;
}

.compare__note {
  margin-top: $spacing-sm;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

/* ---------- 学分 ---------- */
.credit-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.credit-head__num {
  font-size: $font-2xl;
  font-weight: 700;
  line-height: 1;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.credit-head__slash {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.credit-head__pct {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.credit-grid {
  display: flex;
  flex-wrap: wrap;
  margin-top: $spacing-lg;
}

.credit-grid__cell {
  width: 50%;
  padding: $spacing-sm 0;
}

.credit-grid__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.credit-grid__value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.credit-grid__value--danger {
  color: $color-danger;
}

/* ---------- 学期达成 ---------- */
.sem-row {
  position: relative;
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.sem-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.sem-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
}

.sem-row__name {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.sem-row__ratio {
  font-size: $font-xs;
  color: $color-text-secondary;
}

.sem-row__track {
  height: 14rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.sem-row__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.sem-row__pct {
  display: block;
  margin-top: 6rpx;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

/* ---------- 走势 ---------- */
.trend {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 260rpx;
  padding-top: $spacing-md;
}

.trend__col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.trend__value {
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.trend__bar-wrap {
  flex: 1;
  width: 40rpx;
  display: flex;
  align-items: flex-end;
  margin: $spacing-xs 0;
}

.trend__bar {
  width: 100%;
  min-height: 6rpx;
  border-radius: $radius-sm;
  background: var(--color-primary);
  opacity: 0.85;
}

.trend__label {
  font-size: 20rpx;
  color: $color-text-hint;
}

.trend__axis {
  margin-top: $spacing-sm;
  padding-top: $spacing-sm;
  border-top: 1rpx solid $color-divider;
}

.trend__axis-text {
  font-size: $font-xs;
  color: $color-text-hint;
}
</style>
