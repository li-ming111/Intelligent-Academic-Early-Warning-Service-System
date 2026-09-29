<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学生管理" :show-back="false" />

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

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="5" />

      <template v-else>
        <empty-state
          v-if="!classes.length"
          text="暂未管理任何班级"
          hint="先在「班级管理」里申请班级，才有学生名单"
          icon="building"
        />

        <template v-else>
          <view class="search">
            <app-icon name="search" :size="34" color="#aeaeb2" />
            <input
              v-model="keyword"
              class="search__input"
              placeholder="搜索姓名或学号"
              placeholder-class="search__ph"
              confirm-type="search"
            />
            <app-icon v-if="keyword" name="close" :size="32" color="#aeaeb2" @tap="keyword = ''" />
          </view>

          <view class="summary">
            <text class="summary__item">共 <text class="summary__num">{{ roster.length }}</text> 名学生</text>
            <text class="summary__item">有挂科 <text class="summary__num summary__num--danger">{{ atRiskCount }}</text> 人</text>
            <text v-if="pendingCount" class="summary__item">待录入 <text class="summary__num">{{ pendingCount }}</text> 门</text>
          </view>

          <empty-state
            v-if="!filtered.length"
            :text="keyword ? '没有匹配的学生' : '这个班还没有成绩记录'"
            :hint="keyword ? '换个关键词试试' : '在「成绩管理」里录入后，这里会出现学生'"
            icon="users"
            :top="60"
          />

          <view v-else class="card card--flush">
            <view
              v-for="(s, i) in filtered"
              :key="s.profileId"
              class="stu"
              :class="{ 'stu--divided': i > 0 }"
              @tap="openStudent(s)"
            >
              <view class="stu__avatar">
                <text class="stu__avatar-text">{{ initial(s.name) }}</text>
              </view>
              <view class="stu__main">
                <view class="stu__top">
                  <text class="stu__name">{{ s.name }}</text>
                  <text v-if="s.failed" class="tag tag--danger">挂 {{ s.failed }} 门</text>
                </view>
                <text class="stu__meta">{{ s.studentNo || '无学号' }} · 修 {{ s.totalCourses }} 门 · 均分 {{ fixed(s.avg) }}</text>
              </view>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>

          <!-- 有学籍、但一条成绩都没有的学生。不列出来教师会以为人不在自己班上。 -->
          <view v-if="noScore.length" class="section">
            <section-title title="暂无成绩记录" :extra="`${noScore.length} 人`" />
            <view class="card">
              <view v-for="(s, i) in noScore" :key="s.studentId" class="plain-row" :class="{ 'plain-row--divided': i > 0 }">
                <text class="plain-row__name">{{ s.studentName }}</text>
                <text class="plain-row__no">{{ s.studentId }}</text>
              </view>
              <text class="note">这些学生在本班有学籍，但没有任何成绩流水；他们不会出现在上方的统计里。</text>
            </view>
          </view>
        </template>
      </template>
    </view>

    <detail-sheet v-model="sheetOpen" :title="current ? current.name : ''" height="78vh">
      <view v-if="current" class="sheet">
        <!-- 一、课程流水（默认视图） -->
        <template v-if="mode === 'courses'">
          <view class="sheet__facts">
            <view class="sheet__fact">
              <text class="sheet__fact-label">学号</text>
              <text class="sheet__fact-value">{{ current.studentNo || '--' }}</text>
            </view>
            <view class="sheet__fact">
              <text class="sheet__fact-label">修读门数</text>
              <text class="sheet__fact-value">{{ current.totalCourses }}</text>
            </view>
            <view class="sheet__fact">
              <text class="sheet__fact-label">挂科</text>
              <text class="sheet__fact-value sheet__fact-value--danger">{{ current.failed }}</text>
            </view>
            <view class="sheet__fact">
              <text class="sheet__fact-label">均分</text>
              <text class="sheet__fact-value">{{ fixed(current.avg) }}</text>
            </view>
          </view>

          <section-title plain title="成绩流水" :extra="`${current.rows.length} 条（含历史学期）`" />
          <view class="ledger">
            <view
              v-for="r in sortedRows"
              :key="r.id"
              class="ledger__row"
              @tap="loadCourseAnalysis(r)"
            >
              <view class="ledger__main">
                <text class="ledger__name">{{ r.courseName || '未知课程' }}</text>
                <text class="ledger__meta">{{ semLabel(r) }} · 平时 {{ fixed(r.regularScore, 0) }} / 期末 {{ fixed(r.finalScore, 0) }}</text>
              </view>
              <score-cell :score="r" />
              <app-icon name="chevron" :size="30" color="#cbd5e1" />
            </view>
          </view>
          <text class="note">点任意一条可看该生该课的排名与教师建议。同一门课有多个学期时会各占一行。</text>
        </template>

        <!-- 二、单课分析 -->
        <template v-else>
          <view class="back" @tap="mode = 'courses'">
            <app-icon name="chevron" :size="30" color="#aeaeb2" />
            <text class="back__text">返回成绩流水</text>
          </view>

          <view v-if="anaLoading" class="ana-loading">
            <text class="ana-loading__text">正在分析…</text>
          </view>

          <template v-else-if="anaError">
            <empty-state :text="anaError" hint="该生在这门课上可能还没有已录入的成绩" icon="chart" :top="40" />
          </template>

          <template v-else-if="ana">
            <view class="ana__head">
              <text class="ana__title">{{ ana.courseName }}</text>
              <warning-tag v-if="ana.warningLevel" :level="ana.warningLevel" />
            </view>

            <view class="ana__score">
              <score-cell :score="ana.currentScore" large />
              <text class="ana__avg">课程均分 {{ fixed(ana.averageScore) }}</text>
            </view>

            <view class="ana__grid">
              <view class="ana__cell">
                <text class="ana__label">排名</text>
                <text class="ana__value">{{ ana.rank }}<text class="ana__unit">/{{ ana.totalStudents }}</text></text>
              </view>
              <view class="ana__cell">
                <text class="ana__label">与均分差</text>
                <text class="ana__value" :class="ana.difference < 0 ? 'ana__value--danger' : 'ana__value--ok'">
                  {{ signed(ana.difference) }}
                </text>
              </view>
              <view class="ana__cell">
                <text class="ana__label">该生挂科</text>
                <text class="ana__value">{{ ana.failedCount }}<text class="ana__unit">门</text></text>
              </view>
            </view>

            <view class="quote">
              <text class="quote__text">{{ ana.comment }}</text>
            </view>
          </template>
        </template>
      </view>

      <template #footer>
        <view v-if="mode === 'courses' && current" class="btn-primary" @tap="openPrediction">查看学分预测</view>
      </template>
    </detail-sheet>

    <app-tab-bar role="teacher" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { useTeacherRoster } from '@/composables/useTeacherRoster.js'
import { scoreSemesterLabel } from '@/utils/score-normalize.js'
import { toNumber } from '@/utils/score.js'
import { go } from '@/utils/navigate.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const { classes, roster, noScore, className, loading, loadClasses, selectClass } = useTeacherRoster(userId)

const keyword = ref('')
const sheetOpen = ref(false)
const current = ref(null)
/** 'courses' 看流水 | 'analysis' 看单课分析 */
const mode = ref('courses')
const ana = ref(null)
const anaLoading = ref(false)
const anaError = ref('')

const atRiskCount = computed(() => roster.value.filter(s => s.failed > 0).length)
const pendingCount = computed(() => roster.value.reduce((sum, s) => sum + s.pendingCourses, 0))

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return roster.value
  return roster.value.filter(s =>
    String(s.name).toLowerCase().includes(k) || String(s.studentNo).includes(k)
  )
})

/** 详情里的流水按学期新的在前；学期取不到时按 id 倒序（新录入的 id 更大）。 */
const sortedRows = computed(() => {
  if (!current.value) return []
  return current.value.rows.slice().sort((a, b) => {
    const sa = toNumber(a.semester)
    const sb = toNumber(b.semester)
    if (sa !== null && sb !== null) return sb - sa
    return toNumber(b.id) - toNumber(a.id)
  })
})

function numOr(v) {
  return Number.isFinite(Number(v)) ? Number(v) : 0
}

function initial(name) {
  return name ? String(name).slice(0, 1) : '?'
}

/** 取不到值给 '--'，**不给 0**：0 分和没成绩是两件事。 */
function fixed(v, digits = 1) {
  const n = toNumber(v)
  return n === null ? '--' : n.toFixed(digits)
}

function signed(v) {
  const n = toNumber(v)
  if (n === null) return '--'
  return `${n > 0 ? '+' : ''}${n.toFixed(1)}`
}

function semLabel(r) {
  return scoreSemesterLabel(r, true)
}

onMounted(async () => {
  await loadClasses()
  const first = classes.value.find(c => toNumber(c.studentCount) > 0) || classes.value[0]
  if (first) await selectClass(first.name)
})

function openStudent(s) {
  current.value = s
  mode.value = 'courses'
  ana.value = null
  anaError.value = ''
  sheetOpen.value = true
}

/** 点一条流水 → 拉该生该课的分析。失败要把原因显示出来，不能停在空白页。 */
async function loadCourseAnalysis(row) {
  mode.value = 'analysis'
  ana.value = null
  anaError.value = ''
  anaLoading.value = true
  try {
    const data = await teacherAPI.getStudentScoreAnalyze(current.value.profileId, row.courseId)
    if (!data || data.currentScore === undefined || data.currentScore === null) {
      anaError.value = '没有该课程的已录入成绩'
    } else {
      ana.value = data
    }
  } catch (e) {
    console.error('[teacher students] 单课分析失败:', e)
    anaError.value = (e && e.message) || '分析失败'
  } finally {
    anaLoading.value = false
  }
}

function openPrediction() {
  sheetOpen.value = false
  go(`/pages/teacher/credit-prediction?profileId=${current.value.profileId}&name=${encodeURIComponent(current.value.name)}`)
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
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
  margin: 0 $page-padding;
  padding: 0 $spacing-md;
  height: $tap-min;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.search__input {
  flex: 1;
  min-width: 0;
  margin: 0 $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
}

.search__ph {
  color: $color-text-hint;
}

/* ---------- 概览 ---------- */
.summary {
  display: flex;
  margin: $spacing-md $page-padding 0;
}

.summary__item {
  margin-right: $spacing-xl;
  font-size: $font-xs;
  color: $color-text-hint;
}

.summary__num {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.summary__num--danger {
  color: $color-danger;
}

/* ---------- 学生行 ---------- */
.card {
  margin: $spacing-md $page-padding 0;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
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

.stu__avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: $radius-pill;
  background: var(--color-primary-weak, #{$color-bg});
  @include flex-center;
  flex-shrink: 0;
}

.stu__avatar-text {
  font-size: $font-md;
  font-weight: 600;
  color: var(--color-primary);
}

.stu__main {
  flex: 1;
  min-width: 0;
  margin: 0 $spacing-md;
}

.stu__top {
  display: flex;
  align-items: center;
}

.stu__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.stu__top .tag {
  margin-left: $spacing-sm;
}

.stu__meta {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.section {
  margin-top: $spacing-xl;
}

.plain-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plain-row--divided {
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.plain-row__name {
  font-size: $font-sm;
  color: $color-text;
}

.plain-row__no {
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding: 0 $page-padding $spacing-2xl;
}

.sheet__facts {
  display: flex;
  padding-bottom: $spacing-md;
  border-bottom: 1rpx solid $color-divider;
}

.sheet__fact {
  flex: 1;
  min-width: 0;
}

.sheet__fact-label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.sheet__fact-value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.sheet__fact-value--danger {
  color: $color-danger;
}

.ledger {
  margin-top: $spacing-sm;
}

.ledger__row {
  display: flex;
  align-items: center;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.ledger__row + .ledger__row {
  margin-top: $spacing-sm;
}

.ledger__row:active {
  background: $color-divider;
}

.ledger__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.ledger__name {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.ledger__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 单课分析 ---------- */
.back {
  display: flex;
  align-items: center;
  padding: $spacing-xs 0;
}

.back__text {
  margin-left: 4rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.ana-loading {
  padding: $spacing-3xl 0;
  text-align: center;
}

.ana-loading__text {
  font-size: $font-sm;
  color: $color-text-hint;
}

.ana__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-md;
}

.ana__title {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.ana__score {
  display: flex;
  align-items: baseline;
  margin-top: $spacing-md;
}

.ana__avg {
  margin-left: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
}

.ana__grid {
  display: flex;
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.ana__cell {
  flex: 1;
  min-width: 0;
}

.ana__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.ana__value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-lg;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.ana__value--danger {
  color: $color-danger;
}

.ana__value--ok {
  color: $color-success;
}

.ana__unit {
  font-size: $font-xs;
  font-weight: 400;
  color: $color-text-hint;
}

.quote {
  margin-top: $spacing-lg;
  padding: $spacing-md;
  border-left: 6rpx solid var(--color-primary);
  border-radius: $radius-sm;
  background: $color-bg;
}

.quote__text {
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
}
</style>
