<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="班级分析" :show-back="false" />

    <view class="tabs">
      <filter-bar v-model="tab" :options="tabOptions" />
    </view>

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <!-- ============ 一、课程分析 ============ -->
        <template v-if="tab === 'course'">
          <empty-state
            v-if="!courses.length"
            text="暂无授课课程"
            hint="课程由管理员分配，分配后这里会出现分析"
            icon="book"
          />

          <template v-else>
            <scroll-view class="chips" scroll-x :show-scrollbar="false">
              <view class="chips__inner">
                <view
                  v-for="c in courses"
                  :key="c.id"
                  class="chip"
                  :class="{ 'chip--on': c.id === courseId }"
                  @tap="selectCourse(c.id)"
                >
                  <text class="chip__text">{{ c.name }}</text>
                </view>
              </view>
            </scroll-view>

            <view class="card">
              <view class="ana__head">
                <text class="ana__title">{{ ana.courseName || courseName }}</text>
                <text class="ana__sub">{{ numOr(ana.gradedCount) }}/{{ numOr(ana.totalStudents) }} 人已出成绩</text>
              </view>

              <view class="ana__grid">
                <view class="ana__cell">
                  <text class="ana__label">平均分</text>
                  <text class="ana__value">{{ dec(ana.averageScore) }}</text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">最高分</text>
                  <text class="ana__value">{{ dec(ana.maxScore, 0) }}</text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">最低分</text>
                  <text class="ana__value">{{ dec(ana.minScore, 0) }}</text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">标准差</text>
                  <text class="ana__value">{{ dec(ana.standardDeviation) }}</text>
                </view>
              </view>

              <view class="bars">
                <view v-for="b in bars" :key="b.label" class="bar">
                  <text class="bar__label">{{ b.label }}</text>
                  <view class="bar__track">
                    <view class="bar__fill" :class="'bar__fill--' + b.key" :style="{ width: b.pct + '%' }" />
                  </view>
                  <text class="bar__num">{{ b.count }}</text>
                </view>
              </view>

              <view class="ana__foot">
                <text class="ana__foot-item">及格率 <text class="ana__foot-num">{{ pct(ana.passRate) }}</text></text>
                <text class="ana__foot-item">优秀率 <text class="ana__foot-num">{{ pct(ana.excellentRate) }}</text></text>
                <text class="ana__foot-item">挂科 <text class="ana__foot-num ana__foot-num--danger">{{ numOr(ana.failedCount) }}</text> 人</text>
              </view>

              <text class="note">统计口径：先滤掉"待录入"的行，再按课程取学期最大的一条，因此人数按学生去重，比成绩流水的行数少。</text>
            </view>

            <view class="section">
              <section-title title="低分学生" :extra="`${anomaly.length} 人`" />
              <empty-state
                v-if="!anomaly.length"
                text="没有低于 60 分的学生"
                hint="按当前口径这门课无人挂科"
                icon="check"
                :top="40"
              />
              <view v-else class="card card--flush">
                <view
                  v-for="(r, i) in anomaly"
                  :key="r.id"
                  class="arow"
                  :class="{ 'arow--divided': i > 0 }"
                  @tap="focusFromAnomaly(r)"
                >
                  <view class="arow__main">
                    <text class="arow__name">{{ r.studentName || '未知学生' }}</text>
                    <text class="arow__meta">{{ semLabel(r) }}</text>
                  </view>
                  <score-cell :score="r" />
                  <app-icon name="chevron" :size="30" color="#cbd5e1" />
                </view>
              </view>
            </view>
          </template>
        </template>

        <!-- ============ 二、班级分析 ============ -->
        <template v-else-if="tab === 'class'">
          <empty-state v-if="!classes.length" text="暂未管理任何班级" hint="先在「班级管理」里申请班级" icon="building" />

          <template v-else>
            <scroll-view class="chips" scroll-x :show-scrollbar="false">
              <view class="chips__inner">
                <view
                  v-for="c in classes"
                  :key="c.id"
                  class="chip"
                  :class="{ 'chip--on': c.id === classId }"
                  @tap="selectClass(c.id, c.name)"
                >
                  <text class="chip__text">{{ c.name }}</text>
                </view>
              </view>
            </scroll-view>

            <view class="card">
              <view class="ana__head">
                <text class="ana__title">{{ cls.className || className }}</text>
                <text class="ana__sub">{{ numOr(cls.totalStudents) }} 名学生</text>
              </view>

              <view class="ana__grid">
                <view class="ana__cell">
                  <text class="ana__label">班级均分</text>
                  <text class="ana__value">{{ dec(cls.averageScore) }}</text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">及格率</text>
                  <text class="ana__value">{{ pct(cls.passRate) }}</text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">优秀率</text>
                  <text class="ana__value">{{ pct(cls.excellentRate) }}</text>
                </view>
              </view>

              <!-- 这个"口径说明"不是客套话：本页的及格率与「课程分析」的及格率
                   会对不上（实测 100% vs 88%），不说清就会被当成数据错误。 -->
              <view class="warn-box">
                <app-icon name="alert-circle" :size="32" color="#b45309" />
                <text class="warn-box__text">班级口径与课程口径不同，两者不相等是正常的。班级及格率取"每个学生在该班**所有课程所有学期**成绩的算术平均"，再数其中 ≥60 分的人；课程及格率只看那一门课、且按课程取最近学期。一个学生挂了一门但别的课分高，班级口径下仍算及格。</text>
              </view>
            </view>

            <view class="section">
              <section-title title="对比" extra="课程维度" />
              <view class="card card--flush">
                <view v-for="c in courseRows" :key="c.id" class="crow">
                  <view class="crow__head">
                    <text class="crow__name">{{ c.name }}</text>
                    <text class="crow__val">{{ dec(anaOf(c.id).averageScore) }} 分</text>
                  </view>
                  <view class="crow__bar">
                    <view class="crow__fill" :style="{ width: barPct(anaOf(c.id).averageScore) + '%' }" />
                  </view>
                  <text class="crow__meta">及格率 {{ pct(anaOf(c.id).passRate) }} · 挂科 {{ numOr(anaOf(c.id).failedCount) }} 人</text>
                </view>
              </view>
              <text class="note">这里列的是你担任教师的课程。班级口径的均分无法再按课程拆开，所以课程对比用的是课程分析那一套数。</text>
            </view>
          </template>
        </template>

        <!-- ============ 三、学生分析 ============ -->
        <template v-else>
          <empty-state
            v-if="!roster.length"
            :text="classes.length ? '这个班还没有成绩流水' : '暂未管理任何班级'"
            :hint="classes.length ? '先在「成绩管理」里录入成绩' : '先在「班级管理」里申请班级'"
            icon="users"
          />

          <template v-else>
            <scroll-view class="chips" scroll-x :show-scrollbar="false">
              <view class="chips__inner">
                <view
                  v-for="c in classes"
                  :key="c.id"
                  class="chip"
                  :class="{ 'chip--on': c.name === rosterClass }"
                  @tap="selectRosterClass(c.name)"
                >
                  <text class="chip__text">{{ c.name }}</text>
                </view>
              </view>
            </scroll-view>

            <view class="card">
              <text class="form__label">学生</text>
              <picker mode="selector" :range="studentLabels" :value="studentIndex" @change="onStudentChange">
                <view class="picker">
                  <text :class="studentIndex < 0 ? 'picker__ph' : 'picker__text'">
                    {{ studentIndex < 0 ? '选择一名学生' : studentLabels[studentIndex] }}
                  </text>
                  <app-icon name="chevron" :size="26" color="#aeaeb2" />
                </view>
              </picker>

              <text class="form__label">课程</text>
              <picker mode="selector" :range="courseLabels" :value="courseIndex" @change="onCourseChange">
                <view class="picker">
                  <text :class="courseIndex < 0 ? 'picker__ph' : 'picker__text'">
                    {{ courseIndex < 0 ? '选择一门课程' : courseLabels[courseIndex] }}
                  </text>
                  <app-icon name="chevron" :size="26" color="#aeaeb2" />
                </view>
              </picker>

              <view class="go" :class="{ 'go--off': !canAnalyze }" @tap="runStudentAnalysis">
                {{ anaLoading ? '分析中…' : '开始分析' }}
              </view>
            </view>

            <template v-if="anaError">
              <empty-state :text="anaError" hint="该生在这门课上可能没有已录入的成绩" icon="chart" :top="40" />
            </template>

            <view v-else-if="sa" class="card">
              <view class="ana__head">
                <text class="ana__title">{{ sa.studentName }} · {{ sa.courseName }}</text>
                <warning-tag v-if="sa.warningLevel" :level="sa.warningLevel" />
              </view>

              <view class="ana__score">
                <score-cell :score="sa.currentScore" large />
                <text class="ana__avg">课程均分 {{ dec(sa.averageScore) }}</text>
              </view>

              <view class="ana__grid">
                <view class="ana__cell">
                  <text class="ana__label">排名</text>
                  <text class="ana__value">{{ sa.rank }}<text class="ana__unit">/{{ sa.totalStudents }}</text></text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">与均分差</text>
                  <text class="ana__value" :class="sa.difference < 0 ? 'ana__value--danger' : 'ana__value--ok'">{{ signed(sa.difference) }}</text>
                </view>
                <view class="ana__cell">
                  <text class="ana__label">该生挂科</text>
                  <text class="ana__value">{{ sa.failedCount }}<text class="ana__unit">门</text></text>
                </view>
              </view>

              <view class="quote">
                <text class="quote__text">{{ sa.comment }}</text>
              </view>
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
import { normalizeScoreRow, scoreSemesterLabel } from '@/utils/score-normalize.js'
import { useTeacherRoster } from '@/composables/useTeacherRoster.js'
import { toNumber } from '@/utils/score.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const tabOptions = [
  { label: '课程', value: 'course' },
  { label: '班级', value: 'class' },
  { label: '学生', value: 'student' }
]

const tab = ref('course')
const loading = ref(true)

/* —— 课程维度 —— */
const courses = ref([])
const courseId = ref(null)
const ana = ref({})
const analyses = ref({})
const anomaly = ref([])

/* —— 班级维度 —— */
const classes = ref([])
const classId = ref(null)
const className = ref('')
const cls = ref({})

/* —— 学生维度 —— */
const { roster, className: rosterClass, loadClasses: loadRosterClasses, selectClass: selectRoster } =
  useTeacherRoster(userId)
const studentIndex = ref(-1)
const courseIndex = ref(-1)
const sa = ref(null)
const anaLoading = ref(false)
const anaError = ref('')

const BINS = [
  { label: '90+', key: 'excellent' },
  { label: '80-89', key: 'good' },
  { label: '70-79', key: 'medium' },
  { label: '60-69', key: 'pass' },
  { label: '<60', key: 'fail' }
]

const courseName = computed(() => {
  const c = courses.value.find(x => x.id === courseId.value)
  return c ? c.name : ''
})

const courseRows = computed(() => courses.value)

const bars = computed(() => {
  const dist = ana.value.scoreDistribution && typeof ana.value.scoreDistribution === 'object' ? ana.value.scoreDistribution : {}
  const max = Math.max(1, ...BINS.map(b => numOr(dist[b.label])))
  return BINS.map(b => {
    const count = numOr(dist[b.label])
    return { label: b.label, key: b.key, count, pct: Math.round((count / max) * 100) }
  })
})

const studentLabels = computed(() => roster.value.map(s => `${s.name}${s.studentNo ? '（' + s.studentNo + '）' : ''}`))

/** 课程下拉用**全部课程**：教师可能想看自己所带班级学生在别的课上的表现。 */
const courseLabels = computed(() => courses.value.map(c => c.name))

const canAnalyze = computed(() => studentIndex.value >= 0 && courseIndex.value >= 0)

function numOr(v) {
  return Number.isFinite(Number(v)) ? Number(v) : 0
}

function dec(v, digits = 2) {
  const n = toNumber(v)
  return n === null ? '--' : n.toFixed(digits)
}

/** 后端给 0~1 小数 */
function pct(v) {
  const n = toNumber(v)
  return n === null ? '--' : `${(n * 100).toFixed(1)}%`
}

function signed(v) {
  const n = toNumber(v)
  if (n === null) return '--'
  return `${n > 0 ? '+' : ''}${n.toFixed(1)}`
}

function semLabel(r) {
  return scoreSemesterLabel(r, true)
}

function anaOf(id) {
  return analyses.value[id] || {}
}

/** 均分相对 100 分的宽度，用于课程对比条 */
function barPct(v) {
  const n = toNumber(v)
  return n === null ? 0 : Math.max(0, Math.min(100, n))
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [courseList, classList] = await Promise.all([
      teacherAPI.getCourses(userId).catch((e) => { console.error('[teacher analysis] 课程失败:', e); return [] }),
      loadRosterClasses()
    ])
    courses.value = Array.isArray(courseList) ? courseList : []
    classes.value = Array.isArray(classList) ? classList : []

    if (courses.value.length) await selectCourse(courses.value[0].id)
    const firstClass = classes.value.find(c => numOr(c.studentCount) > 0) || classes.value[0]
    if (firstClass) {
      await selectClass(firstClass.id, firstClass.name)
      await selectRoster(firstClass.name)
    }
  } finally {
    loading.value = false
  }
}

async function selectCourse(id) {
  courseId.value = id
  const [a, rows] = await Promise.all([
    teacherAPI.getAnalysis(id).catch((e) => { console.error('[teacher analysis] 分析失败:', e); return null }),
    teacherAPI.getCourseAnomaly(id).catch((e) => { console.warn('[teacher analysis] 低分名单失败:', e && e.message); return [] })
  ])
  ana.value = a && typeof a === 'object' ? a : {}
  // `/scores/anomalies` 实测恒为 []，低分名单只有 `/courses/{id}/anomaly` 这一条路
  anomaly.value = (Array.isArray(rows) ? rows : []).map(normalizeScoreRow)

  if (a) {
    analyses.value = { ...analyses.value, [id]: a }
  }
}

async function selectClass(id, name) {
  classId.value = id
  className.value = name || ''
  const data = await teacherAPI.getClassScoreAnalysis(id).catch((e) => {
    console.error('[teacher analysis] 班级分析失败:', e)
    return null
  })
  cls.value = data && typeof data === 'object' ? data : {}
}

async function selectRosterClass(name) {
  studentIndex.value = -1
  courseIndex.value = -1
  sa.value = null
  anaError.value = ''
  await selectRoster(name)
}

function onStudentChange(e) {
  studentIndex.value = Number(e.detail.value)
  sa.value = null
  anaError.value = ''
}

function onCourseChange(e) {
  courseIndex.value = Number(e.detail.value)
  sa.value = null
  anaError.value = ''
}

/** 从「课程」tab 的低分名单跳到「学生」tab 并直接出结果。 */
async function focusFromAnomaly(row) {
  const stu = roster.value.find(s => String(s.profileId) === String(row.studentId))
  if (!stu) {
    uni.showToast({ title: '该学生不在你所带班级的名单里', icon: 'none' })
    return
  }
  tab.value = 'student'
  studentIndex.value = roster.value.indexOf(stu)
  const ci = courses.value.findIndex(c => String(c.id) === String(row.courseId))
  courseIndex.value = ci
  await runStudentAnalysis()
}

async function runStudentAnalysis() {
  if (!canAnalyze.value || anaLoading.value) return
  const stu = roster.value[studentIndex.value]
  const course = courses.value[courseIndex.value]
  anaLoading.value = true
  sa.value = null
  anaError.value = ''
  try {
    const data = await teacherAPI.getStudentScoreAnalyze(stu.profileId, course.id)
    if (!data || data.currentScore === undefined || data.currentScore === null) {
      anaError.value = '没有该课程的已录入成绩'
    } else {
      sa.value = data
    }
  } catch (e) {
    console.error('[teacher analysis] 学生分析失败:', e)
    anaError.value = (e && e.message) || '分析失败'
  } finally {
    anaLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.tabs {
  padding: $spacing-sm $page-padding;
  background: $color-card;
  border-bottom: 1rpx solid $color-border;
}

.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

/* ---------- 横向 chips ---------- */
.chips {
  width: 100%;
  white-space: nowrap;
}

.chips__inner {
  display: inline-flex;
  /* 上下各 18rpx：chip 本体回到原高（实测 54rpx = 27px），而伪元素命中区
     是 88rpx，多出来的 (88-54)/2 = 17rpx 必须落在 .chips__inner 的盒子里。
     原因：.chips__inner 在 <scroll-view> 内，scroll-view 是 overflow:hidden，
     超出行的部分会被裁掉 —— 实测不加这两句，有效高度就只有本体的 27px，
     看着有命中区，点上去不生效。取 18rpx 而非 17rpx 是留 1rpx 余量。 */
  padding: 18rpx $page-padding;
}

.chip {
  @include tap-area;
  margin-right: $spacing-sm;
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-pill;
  background: $color-card;
}

.chip--on {
  background: var(--color-primary);
}

.chip__text {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.chip--on .chip__text {
  color: #fff;
  font-weight: 600;
}

/* ---------- 卡片 ---------- */
.card {
  padding: $spacing-lg;
  margin: $spacing-md $page-padding 0;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
}

.section {
  margin-top: $spacing-xl;
}

.section .card {
  margin-top: $spacing-sm;
}

/* ---------- 分析块 ---------- */
.ana__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.ana__title {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.ana__sub {
  font-size: $font-xs;
  color: $color-text-hint;
}

.ana__grid {
  display: flex;
  margin-top: $spacing-md;
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
  font-size: $font-xl;
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

.ana__foot {
  display: flex;
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.ana__foot-item {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

.ana__foot-num {
  color: $color-text;
  font-weight: 600;
}

.ana__foot-num--danger {
  color: $color-danger;
}

/* ---------- 分布条 ---------- */
.bars {
  margin-top: $spacing-lg;
}

.bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.bar__label {
  width: 110rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.bar__track {
  flex: 1;
  min-width: 0;
  height: 16rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: $radius-pill;
}

.bar__fill--excellent { background: #16a34a; }
.bar__fill--good      { background: #2563eb; }
.bar__fill--medium    { background: #94a3b8; }
.bar__fill--pass      { background: #f59e0b; }
.bar__fill--fail      { background: $color-danger; }

.bar__num {
  width: 60rpx;
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* ---------- 口径提示 ---------- */
.warn-box {
  display: flex;
  align-items: flex-start;
  margin-top: $spacing-lg;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: rgba(245, 158, 11, 0.1);
}

.warn-box__text {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: #92400e;
  line-height: 1.6;
}

.note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

/* ---------- 低分名单 ---------- */
.arow {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.arow--divided {
  border-top: 1rpx solid $color-divider;
}

.arow:active {
  background: $color-bg;
}

.arow__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.arow__name {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.arow__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 课程对比 ---------- */
.crow {
  padding: $spacing-lg;
}

.crow + .crow {
  border-top: 1rpx solid $color-divider;
}

.crow__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.crow__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.crow__val {
  font-size: $font-sm;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.crow__bar {
  margin-top: $spacing-sm;
  height: 12rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.crow__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--gradient-hero);
}

.crow__meta {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 表单 ---------- */
.form__label {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-sm;
  color: $color-text;
}

.picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $tap-min;
  margin-top: $spacing-sm;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.picker__ph {
  font-size: $font-sm;
  color: $color-text-hint;
}

.picker__text {
  font-size: $font-sm;
  color: $color-text;
}

.go {
  margin-top: $spacing-lg;
  height: $tap-min;
  border-radius: $radius-md;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-md;
  @include flex-center;
}

.go--off {
  opacity: 0.45;
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
