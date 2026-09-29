<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学分预测" />

    <scroll-view v-if="classes.length" class="course-bar" scroll-x :show-scrollbar="false">
      <view class="course-bar__inner">
        <view
          v-for="c in classes"
          :key="c.id"
          class="course-chip"
          :class="{ 'course-chip--on': c.name === className }"
          @tap="selectClass(c.name)"
        >
          <text class="course-chip__name">{{ c.name }}</text>
          <text class="course-chip__meta">{{ numOr(c.studentCount) }} 人</text>
        </view>
      </view>
    </scroll-view>

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <empty-state
          v-if="!classes.length"
          text="暂未管理任何班级"
          hint="先在「班级管理」里申请班级"
          icon="building"
        />

        <template v-else>
          <view v-if="!roster.length" class="card">
            <text class="note">这个班还没有成绩流水，无法预测学分。先在「成绩管理」里录入成绩。</text>
          </view>

          <template v-else>
            <!-- 全班一起算要 N 个请求，所以做成按需触发：默认只算当前选中的那个学生。 -->
            <view class="card">
              <view class="bn__head">
                <text class="bn__title">全班学分进度</text>
                <view v-if="!batched" class="bn__btn" :class="{ 'bn__btn--busy': running }" @tap="runBatch">
                  {{ running ? `计算中 ${doneCount}/${roster.length}` : '预测全班' }}
                </view>
                <text v-else class="bn__done">已计算 {{ doneCount }} 人</text>
              </view>

              <view v-if="batched" class="bn__stats">
                <view class="bn__stat">
                  <text class="bn__stat-num">{{ batchedStats.safe }}</text>
                  <text class="bn__stat-label">进度正常</text>
                </view>
                <view class="bn__stat">
                  <text class="bn__stat-num bn__stat-num--warn">{{ batchedStats.watch }}</text>
                  <text class="bn__stat-label">需关注</text>
                </view>
                <view class="bn__stat">
                  <text class="bn__stat-num bn__stat-num--danger">{{ batchedStats.risk }}</text>
                  <text class="bn__stat-label">进度落后</text>
                </view>
              </view>

              <text v-else class="note">点「预测全班」会逐个学生请求学分预测接口，用于给出整班的修读进度排行。</text>
            </view>

            <!-- 焦点学生 -->
            <view v-if="focus" class="card hero-card">
              <view class="hero-card__head">
                <view class="hero-card__who">
                  <text class="hero-card__name">{{ focus.studentName || focusName || '该学生' }}</text>
                  <text class="hero-card__no">{{ focusNo || '—' }}</text>
                </view>
                <text class="chip" :class="trendClass(focus)">{{ focus.trend || '—' }}</text>
              </view>

              <view class="prog">
                <view class="prog__bar">
                  <view class="prog__fill" :style="{ width: progressPct(focus) + '%' }" />
                </view>
                <text class="prog__text">{{ pctText(focus.progressRate) }} 已完成</text>
              </view>

              <view class="grid">
                <view class="grid__cell">
                  <text class="grid__label">已获学分</text>
                  <text class="grid__value">{{ numOr(focus.completedCredits) }}</text>
                </view>
                <view class="grid__cell">
                  <text class="grid__label">毕业要求</text>
                  <text class="grid__value">{{ numOr(focus.requiredCredits) }}</text>
                </view>
                <view class="grid__cell">
                  <text class="grid__label">还差</text>
                  <text class="grid__value grid__value--warn">{{ numOr(focus.remainingCredits) }}</text>
                </view>
                <view class="grid__cell">
                  <text class="grid__label">不及格</text>
                  <text class="grid__value grid__value--danger">{{ numOr(focus.failedCourses) }}</text>
                </view>
              </view>

              <text class="note">口径与后端 `ScoreCaliber` 一致：先滤掉"待录入"的行，再按课程取学期最大的一条；已获学分只算及格课程。修读课程共 {{ numOr(focus.totalCourses) }} 门。</text>
            </view>

            <view class="section">
              <section-title title="学生名单" :extra="`${roster.length} 人`" />
              <view class="card card--flush">
                <view
                  v-for="(s, i) in displayList"
                  :key="s.profileId"
                  class="stu"
                  :class="{ 'stu--divided': i > 0 }"
                  @tap="focusOn(s)"
                >
                  <view class="stu__main">
                    <text class="stu__name">{{ s.name }}</text>
                    <text class="stu__no">{{ s.studentNo || '无学号' }}</text>
                  </view>
                  <view class="stu__right">
                    <template v-if="pred(s.profileId)">
                      <text class="stu__pct">{{ pctText(pred(s.profileId).progressRate) }}</text>
                      <view class="stu__bar">
                        <view class="stu__bar-fill" :style="{ width: progressPct(pred(s.profileId)) + '%' }" />
                      </view>
                    </template>
                    <text v-else-if="missing(s.profileId)" class="stu__none">无数据</text>
                    <text v-else class="stu__none">未计算</text>
                  </view>
                  <app-icon name="chevron" :size="32" color="#cbd5e1" />
                </view>
              </view>
              <text class="note">「未计算」表示还没请求过该学生的预测；点一下就会拉取。带 `?profileId=` 进入时会自动选中该生。</text>
            </view>
          </template>
        </template>
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
import { useTeacherRoster } from '@/composables/useTeacherRoster.js'
import { toNumber } from '@/utils/score.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const { classes, roster, className, loading, loadClasses, selectClass: selectRoster } = useTeacherRoster(userId)

/** profileId → 预测结果 或 { missing: true }。用整体替换触发响应式。 */
const predictions = ref({})
const focusId = ref('')
const focusName = ref('')
const focusNo = ref('')
const running = ref(false)
const doneCount = ref(0)
const batched = ref(false)

const focus = computed(() => {
  const p = predictions.value[focusId.value]
  return p && !p.missing ? p : null
})

const displayList = computed(() => {
  if (!batched.value) return roster.value
  // 算完之后按"进度低的在前"排，最需要关注的排在名单最上面
  return roster.value.slice().sort((a, b) => {
    const pa = pred(a.profileId)
    const pb = pred(b.profileId)
    const va = pa ? toNumber(pa.progressRate) ?? 1 : 2
    const vb = pb ? toNumber(pb.progressRate) ?? 1 : 2
    return va - vb
  })
})

const batchedStats = computed(() => {
  let safe = 0
  let watch = 0
  let risk = 0
  for (const s of roster.value) {
    const p = pred(s.profileId)
    if (!p) continue
    const rate = toNumber(p.progressRate) ?? 0
    if (rate >= 0.5) safe += 1
    else if (rate >= 0.3) watch += 1
    else risk += 1
  }
  return { safe, watch, risk }
})

function pred(id) {
  const p = predictions.value[id]
  return p && !p.missing ? p : null
}

function missing(id) {
  const p = predictions.value[id]
  return !!(p && p.missing)
}

function numOr(v) {
  return Number.isFinite(Number(v)) ? Number(v) : 0
}

/** progressRate 是 0~1 小数 */
function progressPct(p) {
  const n = toNumber(p && p.progressRate)
  if (n === null) return 0
  return Math.max(0, Math.min(100, Math.round(n * 100)))
}

function pctText(v) {
  const n = toNumber(v)
  if (n === null) return '--'
  return `${(n * 100).toFixed(1)}%`
}

function trendClass(p) {
  const t = p && p.trend
  if (t === '需关注') return 'chip--warn'
  return 'chip--ok'
}

/** 学期/序号查询参数：由「学生管理」页带过来 */
function readQuery() {
  const pages = getCurrentPages()
  const cur = pages[pages.length - 1]
  const o = (cur && cur.options) || {}
  focusId.value = o.profileId ? String(o.profileId) : ''
  focusName.value = o.name ? decodeURIComponent(o.name) : ''
}

onMounted(async () => {
  readQuery()
  await loadClasses()
  const first = classes.value.find(c => numOr(c.studentCount) > 0) || classes.value[0]
  if (first) await selectRoster(first.name)

  if (focusId.value) {
    const hit = roster.value.find(s => String(s.profileId) === focusId.value)
    if (hit) {
      focusNo.value = hit.studentNo
      if (!focusName.value) focusName.value = hit.name
      await fetchOne(focusId.value)
    } else {
      focusId.value = ''
      uni.showToast({ title: '该学生不在你所带班级', icon: 'none' })
    }
  } else if (roster.value.length) {
    // 没带参数就默认看第一个（名单里挂科最多的那个）
    focusOn(roster.value[0])
  }
})

async function selectClass(name) {
  batched.value = false
  doneCount.value = 0
  predictions.value = {}
  await selectRoster(name)
  if (roster.value.length) focusOn(roster.value[0])
}

async function fetchOne(profileId) {
  try {
    const data = await teacherAPI.getCreditPrediction(profileId)
    predictions.value = {
      ...predictions.value,
      [profileId]: data && typeof data === 'object' ? data : { missing: true }
    }
    doneCount.value += 1
    return data
  } catch (e) {
    // 404（该生没有任何已录入成绩）会走到这里。标记成 missing 而不是静默失败，
    // 名单上要显示"无数据"，否则看起来像"还没点"。
    console.warn('[teacher credit] 学分预测失败:', profileId, e && e.message)
    predictions.value = { ...predictions.value, [profileId]: { missing: true } }
    doneCount.value += 1
    return null
  }
}

function focusOn(s) {
  focusId.value = String(s.profileId)
  focusNo.value = s.studentNo
  if (!focusName.value) focusName.value = s.name
  if (!predictions.value[s.profileId]) fetchOne(s.profileId)
}

/**
 * 全班逐个预测。
 *
 * 并发限制在 3：小程序同时发太多请求会排队甚至失败，
 * 24 个学生 × 并发 3 大约四五秒能跑完，期间显示"计算中 x/24"。
 */
async function runBatch() {
  if (running.value || !roster.value.length) return
  running.value = true
  doneCount.value = Object.keys(predictions.value).length
  const queue = roster.value.filter(s => !predictions.value[s.profileId])
  try {
    const worker = async () => {
      while (queue.length) {
        const s = queue.shift()
        if (!s) break
        await fetchOne(s.profileId)
      }
    }
    await Promise.all([worker(), worker(), worker()])
    batched.value = true
  } finally {
    running.value = false
  }
}
</script>

<style lang="scss" scoped>
.course-bar {
  width: 100%;
  white-space: nowrap;
  background: $color-card;
  border-bottom: 1rpx solid $color-border;
}

.course-bar__inner {
  display: inline-flex;
  padding: $spacing-sm $page-padding;
}

.course-chip {
  display: flex;
  flex-direction: column;
  margin-right: $spacing-sm;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.course-chip--on {
  background: var(--color-primary-weak, #{$color-bg});
}

.course-chip__name {
  font-size: $font-sm;
  color: $color-text;
}

.course-chip--on .course-chip__name {
  color: var(--color-primary);
  font-weight: 600;
}

.course-chip__meta {
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.page-body {
  padding-top: $spacing-lg;
  padding-bottom: $spacing-3xl;
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

.hero-card {
  margin-top: $spacing-md;
}

/* ---------- 全班块 ---------- */
.bn__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bn__title {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.bn__btn {
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
}

.bn__btn--busy {
  opacity: 0.6;
}

.bn__done {
  font-size: $font-xs;
  color: $color-text-hint;
}

.bn__stats {
  display: flex;
  margin-top: $spacing-md;
}

.bn__stat {
  flex: 1;
  min-width: 0;
}

.bn__stat-num {
  display: block;
  font-size: $font-xl;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.bn__stat-num--warn {
  color: $color-warning;
}

.bn__stat-num--danger {
  color: $color-danger;
}

.bn__stat-label {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 焦点学生 ---------- */
.hero-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero-card__who {
  min-width: 0;
}

.hero-card__name {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.hero-card__no {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.chip {
  flex-shrink: 0;
  padding: 6rpx $spacing-md;
  border-radius: $radius-pill;
  font-size: $font-xs;
}

.chip--warn {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.chip--ok {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.prog {
  margin-top: $spacing-lg;
}

.prog__bar {
  height: 16rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.prog__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--gradient-hero);
}

.prog__text {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-hint;
}

.grid {
  display: flex;
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.grid__cell {
  flex: 1;
  min-width: 0;
}

.grid__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.grid__value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-lg;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.grid__value--warn {
  color: $color-warning;
}

.grid__value--danger {
  color: $color-danger;
}

/* ---------- 名单 ---------- */
.section {
  margin-top: $spacing-xl;
}

.section .card {
  margin-top: $spacing-sm;
}

.stu {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.stu--divided {
  border-top: 1rpx solid $color-divider;
}

.stu:active {
  background: $color-bg;
}

.stu__main {
  flex: 1;
  min-width: 0;
}

.stu__name {
  display: block;
  font-size: $font-md;
  color: $color-text;
  @include ellipsis;
}

.stu__no {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.stu__right {
  flex-shrink: 0;
  width: 180rpx;
  margin-right: $spacing-xs;
  text-align: right;
}

.stu__pct {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.stu__bar {
  margin-top: 8rpx;
  height: 8rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.stu__bar-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.stu__none {
  font-size: $font-xs;
  color: $color-text-hint;
}

.note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}
</style>
