<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="成绩分析" />

    <role-hero
      :title="userName || '成绩分析'"
      subtitle="本院学生的成绩概况与班级对比"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="tabs">
      <view
        v-for="t in TABS"
        :key="t.key"
        class="tab"
        :class="{ 'tab--on': mode === t.key }"
        @tap="switchTab(t.key)"
      >{{ t.label }}</view>
    </view>

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <!-- ============ 分布与课程 ============ -->
        <template v-if="mode === 'analysis'">
          <section-title plain title="总评分布" :extra="`合计 ${analysis.totalRecords || 0} 条`" />
          <view class="bars">
            <view v-for="b in bands" :key="b.label" class="bar">
              <text class="bar__label">{{ b.label }}</text>
              <view class="bar__track">
                <view
                  class="bar__fill"
                  :class="`bar__fill--${b.key}`"
                  :style="{ width: barWidth(b.value) }"
                />
              </view>
              <text class="bar__value">{{ b.value }}<text class="bar__pct"> ({{ pct(b.value) }}%)</text></text>
            </view>
          </view>

          <section-title plain title="按课程" :extra="`${courses.length} 门 · 按平均分降序`" />
          <view v-if="!courses.length" class="empty-tip">暂无课程成绩。</view>
          <view v-else class="card card--flush">
            <view
              v-for="(c, i) in courses"
              :key="c.courseId"
              class="crs"
              :class="{ 'crs--divided': i > 0 }"
            >
              <view class="crs__head">
                <text class="crs__name">{{ c.courseName }}</text>
                <text class="crs__avg">{{ c.avgScore }}</text>
              </view>
              <text class="crs__meta">
                {{ c.recordCount }} 条成绩 · 及格率 {{ c.passRate }}%
              </text>
            </view>
          </view>

          <text class="note">顶部的「有成绩学生」指已有成绩的学生，可能少于学生总数。</text>
        </template>

        <!-- ============ 班级对比 ============ -->
        <template v-else-if="mode === 'classes'">
          <view v-if="!comparison.length" class="empty-tip">暂无班级数据。</view>
          <view v-else class="card card--flush">
            <view
              v-for="(c, i) in comparison"
              :key="c.classId"
              class="cmp"
              :class="{ 'cmp--divided': i > 0 }"
            >
              <view class="cmp__head">
                <text class="cmp__name">{{ c.className }}</text>
                <text class="tag" :class="Number(c.recordCount) ? 'tag--info' : 'tag--pending'">
                  {{ Number(c.recordCount) ? `${c.recordCount} 条成绩` : '无成绩' }}
                </text>
              </view>
              <view class="cmp__nums">
                <view class="num">
                  <text class="num__v" :class="{ 'num__v--muted': !Number(c.studentCount) }">{{ c.studentCount || 0 }}</text>
                  <text class="num__k">学生</text>
                </view>
                <view class="num">
                  <text class="num__v" :class="{ 'num__v--muted': c.avgScore === null || c.avgScore === undefined }">
                    {{ c.avgScore === null || c.avgScore === undefined ? '--' : c.avgScore }}
                  </text>
                  <text class="num__k">平均分</text>
                </view>
                <view class="num">
                  <text class="num__v" :class="{ 'num__v--muted': !Number(c.recordCount) }">
                    {{ Number(c.recordCount) ? c.passRate + '%' : '--' }}
                  </text>
                  <text class="num__k">及格率</text>
                </view>
              </view>
            </view>
          </view>

          <text class="note">平均分与及格率显示「--」表示该班暂无成绩记录。</text>
        </template>

        <!-- ============ 成绩查询 ============ -->
        <template v-else>
          <view class="search">
            <input
              v-model="keyword"
              class="search__input"
              type="text"
              confirm-type="search"
              placeholder="按姓名 / 学号 / 课程名搜索"
              placeholder-class="search__ph"
              @confirm="doQuery"
            />
            <view class="search__btn" @tap="doQuery">查询</view>
          </view>

          <filter-bar v-model="classId" :options="classOptions" />

          <view class="summary">
            <text class="summary__text">
              命中 <text class="summary__num">{{ total }}</text> 条
            </text>
            <text class="summary__hint">已显示 {{ rows.length }} 条</text>
          </view>

          <empty-state
            v-if="!rows.length && !fetching"
            text="没有匹配的成绩"
            hint="换一个班级或关键字"
            icon="check"
          />

          <template v-else>
            <view class="card card--flush">
              <view
                v-for="(r, i) in rows"
                :key="r.id || i"
                class="srow"
                :class="{ 'srow--divided': i > 0 }"
              >
                <view class="srow__left">
                  <text class="srow__title">{{ r.studentName || '未知学生' }} · {{ r.courseName || '未知课程' }}</text>
                  <text class="srow__meta">
                    {{ r.studentId || '无学号' }} · {{ r.className || '未分班' }} · 第 {{ numOrDash(r.semester) }} 学期
                  </text>
                  <!-- 期末未录入时总评走灰色「待录入」，期末/绩点/等级也得用同一口径，
                       否则同一行里一半写「待录入」一半写「--」。 -->
                  <text class="srow__meta2">
                    平时 {{ numOrDash(r.regularScore) }} / 期末 {{ isPending(r) ? PENDING_TEXT : numOrDash(r.finalScore) }} ·
                    绩点 {{ isPending(r) ? PENDING_TEXT : gradePointText(r.gradePoint) }} · 等级 {{ isPending(r) ? PENDING_TEXT : (r.grade || '—') }}
                  </text>
                </view>
                <text class="srow__total" :class="`srow__total--${scoreClass(r)}`">{{ scoreText(r) }}</text>
              </view>
            </view>

            <view v-if="hasMore" class="more" @tap="loadMore">
              {{ fetching ? '加载中…' : `加载更多（还有 ${total - rows.length} 条）` }}
            </view>
            <text v-else class="more more--done">已显示全部 {{ total }} 条</text>
          </template>

        </template>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { scoreText, scoreClass, isPending, PENDING_TEXT } from '@/utils/score.js'

const { userId, userName } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

const heroStats = computed(() => [
  { label: '平均分', value: numOrDash(stats.value.avgScore) },
  { label: '及格率', value: stats.value.passRate === null || stats.value.passRate === undefined ? '--' : stats.value.passRate + '%' },
  { label: '成绩条数', value: numOrDash(stats.value.recordCount) },
  { label: '有成绩学生', value: numOrDash(stats.value.studentCount) }
])

const TABS = [
  { key: 'analysis', label: '分布与课程' },
  { key: 'classes', label: '班级对比' },
  { key: 'query', label: '成绩查询' }
]

const PAGE_SIZE = 30

const loading = ref(true)
const mode = ref('analysis')

const stats = ref({})
const analysis = ref({})
const comparison = ref([])
const classes = ref([])

const keyword = ref('')
const classId = ref('')
const rows = ref([])
const total = ref(0)
const nextPage = ref(1)
const fetching = ref(false)

const classOptions = computed(() => {
  const opts = [{ label: '全部班级', value: '' }]
  classes.value.forEach(c => opts.push({ label: c.name, value: String(c.id) }))
  return opts
})

const bandDefs = [
  { label: '90-100', key: 'excellent' },
  { label: '80-89', key: 'good' },
  { label: '70-79', key: 'medium' },
  { label: '60-69', key: 'pass' },
  { label: '0-59', key: 'fail' }
]

const bands = computed(() => {
  const d = (analysis.value && analysis.value.distribution) || {}
  return bandDefs.map(b => ({ ...b, value: Number(d[b.label]) || 0 }))
})

const bandMax = computed(() => Math.max(1, ...bands.value.map(b => b.value)))
const courses = computed(() => ((analysis.value && analysis.value.courseStats) || []).slice())

function barWidth(v) {
  return Math.round((Number(v) || 0) * 100 / bandMax.value) + '%'
}

function pct(v) {
  const total = Number(analysis.value.totalRecords) || 0
  if (!total) return 0
  return Math.round((Number(v) || 0) * 100 / total)
}

function numOrDash(v) {
  return v === null || v === undefined ? '--' : v
}

function gradePointText(v) {
  return v === null || v === undefined ? '--' : v
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [s, a, cmp, cls] = await Promise.all([
      collegeAdminAPI.getScoreStats(userId).catch(e => { console.error('[college-admin scores] 概览失败:', e); return null }),
      collegeAdminAPI.getScoreAnalysis(userId).catch(e => { console.error('[college-admin scores] 分析失败:', e); return null }),
      collegeAdminAPI.getClassComparison(userId).catch(e => { console.error('[college-admin scores] 对比失败:', e); return null }),
      collegeAdminAPI.getClasses(userId).catch(e => { console.error('[college-admin scores] 班级失败:', e); return null })
    ])
    stats.value = s && typeof s === 'object' ? s : {}
    analysis.value = a && typeof a === 'object' ? a : {}
    // 有成绩的班排前面，"无成绩"的堆在下面
    comparison.value = (Array.isArray(cmp) ? cmp : []).slice().sort((x, y) => {
      const rx = Number(x.recordCount) || 0
      const ry = Number(y.recordCount) || 0
      if (rx !== ry) return ry - rx
      return Number(y.studentCount || 0) - Number(x.studentCount || 0)
    })
    classes.value = Array.isArray(cls) ? cls : []
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  mode.value = key
  // 成绩查询第一次进来时才取数据，别的 tab 不替它背
  if (key === 'query' && !rows.value.length) doQuery()
}

/** 拉一页并追加：接口一次只回 PAGE_SIZE 条，翻页靠 nextPage。 */
function fetchPage() {
  if (fetching.value) return Promise.resolve()
  fetching.value = true
  const cls = classId.value || undefined
  const kw = String(keyword.value || '').trim() || undefined
  return collegeAdminAPI
    .getScoreQuery(userId, { classId: cls, keyword: kw, page: nextPage.value, size: PAGE_SIZE })
    .then(res => {
      const items = res && Array.isArray(res.items) ? res.items : []
      rows.value = rows.value.concat(items)
      total.value = Number(res && res.total) || 0
      nextPage.value += 1
    })
    .catch(e => {
      console.error('[college-admin scores] 查询失败:', e)
      toast((e && e.message) || '查询失败')
    })
    .finally(() => { fetching.value = false })
}

function doQuery() {
  rows.value = []
  total.value = 0
  nextPage.value = 1
  return fetchPage()
}

function loadMore() {
  return fetchPage()
}

const hasMore = computed(() => rows.value.length < total.value)

watch(classId, () => {
  if (mode.value === 'query') doQuery()
})
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

/* ---------- tab ---------- */
.tabs {
  display: flex;
  padding: $spacing-sm $page-padding 0;
}

.tab {
  flex: 1;
  min-width: 0;
  height: $tap-min;
  border-radius: $radius-md;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.tab--on {
  background: var(--color-primary);
  color: #fff;
  font-weight: 500;
}

/* ---------- 汇总 ---------- */
.summary {
  display: flex;
  align-items: baseline;
  padding: $spacing-md $page-padding $spacing-sm;
}

.summary__text {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.summary__num {
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.summary__hint {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

.empty-tip {
  display: block;
  padding: $spacing-lg $page-padding;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 分布条 ---------- */
.bars {
  padding: $spacing-md $spacing-lg;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: var(--shadow-card, none);
}

.bar {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-sm;
}

.bar:last-child {
  margin-bottom: 0;
}

.bar__label {
  width: 130rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.bar__track {
  flex: 1;
  min-width: 0;
  height: 24rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.bar__fill--excellent,
.bar__fill--good {
  background: $color-success;
}

.bar__fill--medium {
  background: var(--color-primary);
}

.bar__fill--pass {
  background: $color-warning;
}

.bar__fill--fail {
  background: $color-danger;
}

.bar__value {
  width: 170rpx;
  flex-shrink: 0;
  text-align: right;
  font-size: $font-xs;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.bar__pct {
  color: $color-text-hint;
}

/* ---------- 课程行 ---------- */
.crs {
  padding: $spacing-md $spacing-lg;
}

.crs--divided {
  border-top: 1rpx solid $color-divider;
}

.crs__head {
  display: flex;
  align-items: baseline;
}

.crs__name {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.crs__avg {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-md;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.crs__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 班级对比 ---------- */
.cmp {
  padding: $spacing-lg;
}

.cmp--divided {
  border-top: 1rpx solid $color-divider;
}

.cmp__head {
  display: flex;
  align-items: center;
}

.cmp__name {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.cmp__nums {
  display: flex;
  margin-top: $spacing-md;
}

.num {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.num__v {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.num__v--muted {
  color: $color-text-hint;
  font-weight: 400;
}

.num__k {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 查询 ---------- */
.search {
  display: flex;
  align-items: center;
  padding: $spacing-sm $page-padding 0;
}

.search__input {
  flex: 1;
  min-width: 0;
  height: $tap-min;
  padding: 0 $spacing-md;
  box-sizing: border-box;
  border-radius: $radius-md;
  background: $color-card;
  font-size: $font-sm;
  color: $color-text;
}

.search__ph {
  color: $color-text-hint;
  font-size: $font-sm;
}

.search__btn {
  flex-shrink: 0;
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin-left: $spacing-sm;
  border-radius: $radius-md;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-sm;
  @include flex-center;
}

.srow {
  display: flex;
  align-items: center;
  padding: $spacing-md $spacing-lg;
}

.srow--divided {
  border-top: 1rpx solid $color-divider;
}

.srow__left {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-md;
}

.srow__title {
  display: block;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.srow__meta,
.srow__meta2 {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.srow__meta2 {
  color: $color-text-secondary;
}

.srow__total {
  flex-shrink: 0;
  font-size: $font-lg;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.srow__total--pending {
  color: $color-pending;
  font-weight: 400;
  font-size: $font-sm;
}

.srow__total--fail {
  color: $color-danger;
}

.srow__total--good,
.srow__total--excellent {
  color: $color-success;
}

.more {
  height: $tap-min;
  margin: $spacing-md $page-padding 0;
  border-radius: $radius-md;
  background: $color-card;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: var(--color-primary);
  @include flex-center;
}

.more--done {
  border-color: transparent;
  background: transparent;
  color: $color-text-hint;
  font-size: $font-xs;
}

.note {
  display: block;
  margin: $spacing-lg $page-padding 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}
</style>
