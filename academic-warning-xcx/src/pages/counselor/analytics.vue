<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="数据分析" :show-back="false" />

    <role-hero
      :title="counselorName || '数据分析'"
      subtitle="六个口径各自独立，分母见页底说明"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 1. 学分不足率 -->
        <view class="section">
          <section-title title="学分不足率" extra="按人去重" />
          <view class="card rate">
            <view class="rate__left">
              <text class="rate__num">{{ numOr(insufficient.insufficientRate) }}<text class="rate__unit">%</text></text>
              <text class="rate__label">
                {{ numOr(insufficient.insufficientStudents) }} / {{ numOr(insufficient.totalStudents) }} 人存在挂科
              </text>
            </view>
            <view class="rate__bar">
              <view class="rate__bar-fill" :style="{ width: pct(insufficient.insufficientRate) }" />
            </view>
          </view>
        </view>

        <!-- 2. 预警等级分布 -->
        <view class="section">
          <section-title title="预警等级分布" :extra="`共 ${distTotal} 条`" />
          <view class="card">
            <template v-if="distribution.length">
              <view v-for="d in distribution" :key="d.level" class="dist-row">
                <warning-tag :level="d.level" />
                <view class="dist-row__track">
                  <view
                    class="dist-row__fill"
                    :style="{ width: d.pct + '%', background: LEVEL_COLORS[d.level] }"
                  />
                </view>
                <text class="dist-row__num">{{ d.count }} 条</text>
                <text class="dist-row__pct">{{ d.share }}%</text>
              </view>
            </template>
            <empty-state v-else text="暂无预警记录" hint="所带班级没有触发任何预警规则" icon="check" :top="20" />
          </view>
        </view>

        <!-- 3. 预警趋势（近 6 个月） -->
        <view class="section">
          <section-title title="预警趋势" extra="近 6 个月 · 按创建时间" />
          <view class="card">
            <view class="trend">
              <view v-for="m in trendMonths" :key="m.month" class="trend__col">
                <text class="trend__value">{{ m.total || '' }}</text>
                <view class="trend__stack">
                  <view
                    v-for="lv in [3, 2, 1]"
                    :key="lv"
                    class="trend__seg"
                    :style="{ height: segHeight(m['l' + lv]), background: LEVEL_COLORS[lv] }"
                  />
                </view>
                <text class="trend__label">{{ m.label }}</text>
              </view>
            </view>
            <view class="legend">
              <view v-for="lv in [1, 2, 3]" :key="lv" class="legend__item">
                <view class="legend__dot" :style="{ background: LEVEL_COLORS[lv] }" />
                <text class="legend__text">{{ dictText(WARNING_LEVEL_TEXT, lv) }}</text>
              </view>
              <text class="legend__note">合计 {{ trendTotal }} 条</text>
            </view>
            <text class="note">后端按「创建时间」切近 6 个自然月，没有预警的月份显示为空白 —— 那是「那个月没触发」，不是「数据丢了」。更早的预警会移出窗口、从图上消失。</text>
          </view>
        </view>

        <!-- 4. 处理效率 -->
        <view class="section">
          <section-title title="处理效率" />
          <view class="card">
            <template v-if="hasHandled">
              <text class="efficiency__num">{{ numOr(efficiency.avg_handle_days) }} 天</text>
              <text class="efficiency__label">平均处理时长（已处理预警计）</text>
            </template>
            <template v-else>
              <text class="efficiency__none">暂无已处理预警</text>
              <text class="efficiency__label">平均处理时长 = 处理时间 − 创建时间，没有已处理的预警就算不出天数。后端此时返回 0，直接显示成「0 天」会被误读成秒处理，故按无数据处理。</text>
            </template>
          </view>
        </view>

        <!-- 5. 班级达标率排名 -->
        <view class="section">
          <section-title title="班级达标率" :extra="`${ranking.length} 个班`" />
          <empty-state
            v-if="!ranking.length"
            text="暂无班级数据"
            hint="班级达标率按有成绩的学生计算"
            icon="building"
            :top="20"
          />
          <view v-else class="card">
            <view v-for="(r, i) in ranking" :key="r.className + i" class="rank-row">
              <text class="rank-row__no" :class="{ 'rank-row__no--top': i < 3 }">{{ i + 1 }}</text>
              <view class="rank-row__main">
                <view class="rank-row__top">
                  <text class="rank-row__name">{{ r.className }}</text>
                  <text class="rank-row__pct">{{ numOr(r.achievementRate) }}%</text>
                </view>
                <view class="rank-row__track">
                  <view class="rank-row__fill" :style="{ width: pct(r.achievementRate) }" />
                </view>
                <text class="rank-row__sub">
                  达标 {{ numOr(r.creditAchievementCount) }} / 有成绩 {{ numOr(r.studentCount) }} 人
                </text>
              </view>
            </view>
          </view>
        </view>

        <!-- 6. 帮扶完成率 -->
        <view class="section">
          <section-title title="帮扶完成率" />
          <view class="card">
            <view class="rate">
              <view class="rate__left">
                <text class="rate__num">{{ numOr(assistance.completionRate) }}<text class="rate__unit">%</text></text>
                <text class="rate__label">
                  已完成 {{ numOr(assistance.completedPlans) }} / 共 {{ numOr(assistance.totalPlans) }} 条计划
                </text>
              </view>
              <view class="rate__bar">
                <view class="rate__bar-fill" :style="{ width: pct(assistance.completionRate) }" />
              </view>
            </view>
            <text class="note">「已完成」只认计划状态为「已完成」(status=2) 的记录。进行中与待开始的计划不计入分子，所以完成率偏低时先看有多少条还停在「进行中」。</text>
          </view>
        </view>

        <!-- 口径说明：六个数的分母各不相同，答辩时被追问的就是这里 -->
        <view class="section section--last">
          <section-title title="口径说明" plain />
          <view class="card">
            <view v-for="(n, i) in notes" :key="i" class="kv">
              <text class="kv__k">{{ n.k }}</text>
              <text class="kv__v">{{ n.v }}</text>
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
import { WARNING_LEVEL_TEXT, dictText } from '@/constants/dict.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

/** 与 uni.scss 的 $level-1/2/3 同值；内联样式里不能写 SCSS 变量，只能各留一份字面值 */
const LEVEL_COLORS = { 1: '#f59e0b', 2: '#f97316', 3: '#ef4444' }

const loading = ref(true)
const insufficient = ref({})
const efficiency = ref({})
const assistance = ref({})
const rawDistribution = ref([])
const rawTrend = ref([])
const ranking = ref([])

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

/**
 * 预警等级分布。
 * ⚠️ 后端这个接口的字段是**全下划线**（`warning_level`），
 *    与同页其他接口的 camelCase 不一致 —— 写成 warningLevel 会静默拿到 undefined，
 *    三条柱子全部落到"轻度"。同类的还有 warning-trend 与 handling-efficiency。
 */
const distribution = computed(() => {
  const rows = Array.isArray(rawDistribution.value) ? rawDistribution.value : []
  const total = rows.reduce((a, r) => a + numOr(r.count), 0)
  // 柱长按"最大那一档"归一（最长的撑满），右侧百分比才是占总量多少 ——
  // 两个都按总量算的话，条数少时四根柱子全是短茬，看不出彼此差别
  const max = Math.max(1, ...rows.map(r => numOr(r.count)))
  return rows
    .map(r => {
      const level = numOr(r.warning_level)
      const count = numOr(r.count)
      return {
        level,
        count,
        pct: Math.round((count / max) * 100),
        share: total ? Math.round((count / total) * 100) : 0
      }
    })
    // 等级高的排前面：严重预警是辅导员最先要看的
    .sort((a, b) => b.level - a.level)
})

const distTotal = computed(() => distribution.value.reduce((a, d) => a + d.count, 0))

/**
 * 近 6 个月趋势。
 * 后端只返回**有数据**的月份，趋势图要的是连续 6 根柱子 ——
 * 缺的月份自己补成 0 高度的空柱（"那个月没触发"和"数据丢了"必须能区分开）。
 */
const trendMonths = computed(() => {
  const now = new Date()
  const keys = []
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    keys.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }

  const bucket = {}
  keys.forEach(k => { bucket[k] = { month: k, label: `${Number(k.slice(5))}月`, l1: 0, l2: 0, l3: 0, total: 0 } })

  ;(Array.isArray(rawTrend.value) ? rawTrend.value : []).forEach(r => {
    const b = bucket[String(r.month || '')]
    // 窗口外的月份直接丢掉，不硬塞进图里（后端只给近 6 个月，出现更早的说明口径变了）
    if (!b) return
    const lv = numOr(r.warning_level)
    const c = numOr(r.count)
    if (lv >= 3) b.l3 += c
    else if (lv === 2) b.l2 += c
    else b.l1 += c
    b.total += c
  })

  return keys.map(k => bucket[k])
})

const trendTotal = computed(() => trendMonths.value.reduce((a, m) => a + m.total, 0))

const maxMonthTotal = computed(() => Math.max(1, ...trendMonths.value.map(m => m.total)))

const hasHandled = computed(() => numOr(efficiency.value.avg_handle_days) > 0)

const heroStats = computed(() => [
  { label: '学分不足率', value: `${numOr(insufficient.value.insufficientRate)}%` },
  { label: '预警总数', value: distTotal.value },
  { label: '有成绩人数', value: numOr(insufficient.value.totalStudents) },
  { label: '达标班级', value: ranking.value.length }
])

const notes = computed(() => [
  { k: '学分不足率', v: `挂科人数 ÷ 有成绩人数（${numOr(insufficient.value.insufficientStudents)} ÷ ${numOr(insufficient.value.totalStudents)}），不含尚无成绩的新生` },
  { k: '班级达标率', v: '学分达标人数 ÷ 该班有成绩人数，花名册里没成绩的人不参与' },
  { k: '帮扶完成率', v: '状态为已完成的计划数 ÷ 计划总数' },
  { k: '预警趋势', v: '按预警创建时间落在近 6 个自然月内计数' },
  { k: '处理效率', v: '已处理预警的「处理时间 − 创建时间」平均值，无已处理记录时不给天数' }
])

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function pct(v) {
  return Math.max(0, Math.min(100, numOr(v))) + '%'
}

/** 堆叠柱里一段的高度：按当月总数在图上的最大值的占比 */
function segHeight(count) {
  return (numOr(count) / maxMonthTotal.value) * 100 + '%'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    // 六块各自兜底：一个聚合挂掉不该让整页空白（它们是六个独立接口）
    const [ci, wd, he, rk, wt, ac] = await Promise.all([
      counselorAPI.getCreditInsufficientRate(userId).catch(e => { console.error('[analytics] 学分不足率失败:', e); return null }),
      counselorAPI.getWarningDistribution(userId).catch(e => { console.error('[analytics] 等级分布失败:', e); return [] }),
      counselorAPI.getHandlingEfficiency(userId).catch(e => { console.error('[analytics] 处理效率失败:', e); return null }),
      counselorAPI.getCreditAchievementRanking(userId).catch(e => { console.error('[analytics] 达标排名失败:', e); return [] }),
      counselorAPI.getWarningTrend(userId).catch(e => { console.error('[analytics] 预警趋势失败:', e); return [] }),
      counselorAPI.getAssistanceCompletion(userId).catch(e => { console.error('[analytics] 帮扶完成率失败:', e); return null })
    ])

    insufficient.value = ci && typeof ci === 'object' ? ci : {}
    efficiency.value = he && typeof he === 'object' ? he : {}
    assistance.value = ac && typeof ac === 'object' ? ac : {}
    rawDistribution.value = Array.isArray(wd) ? wd : []
    rawTrend.value = Array.isArray(wt) ? wt : []
    ranking.value = (Array.isArray(rk) ? rk : [])
      // 达标率高的排前面；同率按班名，保证每次刷新顺序一致
      .slice()
      .sort((a, b) => numOr(b.achievementRate) - numOr(a.achievementRate)
        || String(a.className || '').localeCompare(String(b.className || '')))
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.section {
  margin-top: $spacing-md;
}

.section:first-child {
  margin-top: 0;
}

.section--last {
  padding-bottom: $spacing-3xl;
}

.card {
  margin: 0 $page-padding;
  padding: $spacing-lg;
  @include card;
}

.note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

/* ---------- 比率条 ---------- */
.rate__left {
  display: flex;
  align-items: baseline;
}

.rate__num {
  font-size: $font-2xl;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.rate__unit {
  font-size: $font-md;
  font-weight: 500;
}

.rate__label {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.rate__bar {
  height: 14rpx;
  margin-top: $spacing-md;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.rate__bar-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

/* ---------- 等级分布 ---------- */
.dist-row {
  display: flex;
  align-items: center;
  padding: $spacing-sm 0;
}

.dist-row__track {
  flex: 1;
  min-width: 0;
  height: 16rpx;
  margin: 0 $spacing-md;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.dist-row__fill {
  height: 100%;
  border-radius: $radius-pill;
}

.dist-row__num {
  flex-shrink: 0;
  width: 90rpx;
  font-size: $font-xs;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.dist-row__pct {
  flex-shrink: 0;
  width: 70rpx;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

/* ---------- 趋势堆叠柱 ---------- */
.trend {
  display: flex;
  align-items: flex-end;
  height: 240rpx;
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
  height: 28rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.trend__stack {
  flex: 1;
  width: 36rpx;
  display: flex;
  flex-direction: column;
  /* 段从下往上堆：严重在最上 —— 与图例、与"等级越高越靠上"的直觉一致 */
  justify-content: flex-end;
  margin: $spacing-xs 0;
}

.trend__seg {
  width: 100%;
}

.trend__label {
  font-size: 20rpx;
  color: $color-text-hint;
}

.legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-top: $spacing-md;
}

.legend__item {
  display: flex;
  align-items: center;
  margin-right: $spacing-md;
}

.legend__dot {
  width: 16rpx;
  height: 16rpx;
  margin-right: 6rpx;
  border-radius: $radius-sm;
}

.legend__text {
  font-size: $font-xs;
  color: $color-text-secondary;
}

.legend__note {
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 处理效率 ---------- */
.efficiency__num {
  display: block;
  font-size: $font-2xl;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.efficiency__none {
  display: block;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text-hint;
}

.efficiency__label {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.6;
}

/* ---------- 排名 ---------- */
.rank-row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-sm 0;
}

.rank-row + .rank-row {
  margin-top: $spacing-sm;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.rank-row__no {
  width: 44rpx;
  flex-shrink: 0;
  font-size: $font-sm;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.rank-row__no--top {
  color: var(--color-primary);
  font-weight: 700;
}

.rank-row__main {
  flex: 1;
  min-width: 0;
}

.rank-row__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.rank-row__name {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.rank-row__pct {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.rank-row__track {
  height: 12rpx;
  margin-top: $spacing-xs;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.rank-row__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.rank-row__sub {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 口径说明 ---------- */
.kv {
  display: flex;
  align-items: flex-start;
  padding: $spacing-sm 0;
}

.kv + .kv {
  border-top: 1rpx solid $color-divider;
}

.kv__k {
  width: 160rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text;
}

.kv__v {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.6;
}
</style>
