<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学生管理" :show-back="false" />

    <view class="search">
      <input
        v-model="keyword"
        class="search__input"
        type="text"
        confirm-type="search"
        placeholder="按姓名 / 学号 / 班级名搜索（服务端过滤）"
        placeholder-class="search__ph"
        @confirm="doSearch"
      />
      <view class="search__btn" @tap="doSearch">搜索</view>
      <view v-if="appliedKeyword" class="search__clear" @tap="clearSearch">清空</view>
    </view>

    <filter-bar v-model="classFilter" :options="classOptions" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            <text class="summary__num">{{ filtered.length }}</text> 名学生
          </text>
          <text class="summary__hint">
            {{ appliedKeyword ? `服务端命中 ${students.length} 人 · 班级筛选在本地做` : '按班级筛选在本地做（接口只支持关键字）' }}
          </text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="students.length ? '这个班级没有学生' : '没有匹配的学生'"
          :hint="students.length ? '换一个班级看看' : '换一个关键字，或清空搜索'"
          icon="users"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(s, i) in filtered"
            :key="s.id"
            class="stu"
            :class="{ 'stu--divided': i > 0 }"
            @tap="openDetail(s)"
          >
            <view class="stu__head">
              <text class="stu__name">{{ s.name || `学生 ${s.id}` }}</text>
              <text class="tag tag--info">{{ s.className || '未分班' }}</text>
            </view>
            <view class="stu__nums">
              <view class="num">
                <text class="num__v" :class="gpaClass(s.gpa)">{{ gpaText(s.gpa) }}</text>
                <text class="num__k">绩点</text>
              </view>
              <view class="num">
                <text class="num__v">{{ passedText(s) }}</text>
                <text class="num__k">通过 / 总课程</text>
              </view>
              <view class="num">
                <text class="num__v" :class="{ 'num__v--danger': Number(s.failedCourses) > 0 }">{{ s.failedCourses || 0 }}</text>
                <text class="num__k">不及格</text>
              </view>
              <view class="num">
                <text class="num__v" :class="{ 'num__v--warn': Number(s.warningCount) > 0 }">{{ s.warningCount || 0 }}</text>
                <text class="num__k">预警</text>
              </view>
            </view>
            <text class="stu__sid">学号 {{ s.studentId || '未记录' }}</text>
          </view>
        </view>

        <text class="note">
          这一页只列**本学院班级下的**学生。库里的班级表与学籍表是两件事：
          软件学院有 6 个班，但 24 个学生全部挂在「计科2306班」下，另外 5 个班一个学生都没有；
          电子工程学院的 2 个班同样没有人 —— 所以那个学院的这一页是**空的**，不是接口坏了。
        </text>
        <text class="note">
          列表里的 `studentId` 是**学号字符串**（如 2023020616），不是数据库主键；
          点进详情时传的第二参数才是 `student_profile.id`。两个 id 不同源，这是这一角色最容易传错的地方。
        </text>
      </template>
    </view>

    <app-tab-bar role="collegeAdmin" />

    <!-- ============ 学生详情 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" height="82vh">
      <view v-if="detail" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">姓名</text>
            <text class="facts__v">{{ detail.name || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">学号</text>
            <text class="facts__v">{{ detail.studentId || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">班级</text>
            <text class="facts__v">{{ detail.className || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">绩点</text>
            <text class="facts__v">{{ gpaText(detail.gpa) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">课程通过</text>
            <text class="facts__v">{{ passedText(detail) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">预警条数</text>
            <text class="facts__v">{{ detail.warningCount || 0 }}</text>
          </view>
        </view>

        <section-title plain title="成绩明细" :extra="`${scores.length} 条`" />
        <view v-if="!scores.length" class="empty-tip">还没有成绩记录。</view>
        <view v-else class="scores">
          <view v-for="(r, i) in scores" :key="r.id || i" class="srow">
            <view class="srow__left">
              <text class="srow__name">{{ r.courseName || '未知课程' }}</text>
              <text class="srow__meta">
                第 {{ numOrDash(r.semester) }} 学期 · {{ numOrDash(r.credits) }} 学分 ·
                平时 {{ numOrDash(r.regularScore) }} / 期末 {{ numOrDash(r.finalScore) }}
              </text>
            </view>
            <view class="srow__right">
              <text class="srow__total" :class="`srow__total--${scoreClass(r)}`">{{ scoreText(r) }}</text>
              <text class="srow__grade">{{ gradeOf(r) || '—' }}</text>
            </view>
          </view>
        </view>

        <section-title plain title="预警记录" :extra="`${warnings.length} 条`" />
        <view v-if="!warnings.length" class="empty-tip">该学生没有预警记录。</view>
        <view v-else class="warns">
          <view v-for="w in warnings" :key="w.id" class="wrow">
            <view class="wrow__head">
              <warning-tag :level="w.warningLevel" />
              <text class="tag" :class="warningStatusTagClass(w.status)">
                {{ w.statusText }}
              </text>
            </view>
            <text class="wrow__desc">{{ w.description || '无说明' }}</text>
            <text class="wrow__time">{{ formatDate(w.createdAt, true) }}</text>
          </view>
        </view>

        <text class="note note--in">
          成绩里「期末」为空表示**还没录入**，页面上总评显示灰色的「待录入」而不是 0 ——
          0 分和没录是两回事。学分这一列只有详情接口给，成绩查询列表里没有。
        </text>
        <text class="note note--in">
          预警行只带 `description`（一段自然语言）与一个数字 `warningLevel`，
          **没有**触发规则名 —— 规则名在管理员端的「预警规则」页才有。
        </text>
      </view>
      <template #footer>
        <view class="acts">
          <view class="act" @tap="goWarnings">
            <app-icon name="alert" :size="30" />
            <text class="act__text">去预警总览</text>
          </view>
        </view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { scoreText, scoreClass, gradeOf } from '@/utils/score.js'
import { normalizeWarnings, warningStatusTagClass } from '@/utils/warning.js'
import { go } from '@/utils/navigate.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const loading = ref(true)
const students = ref([])
const classes = ref([])

const keyword = ref('')
const appliedKeyword = ref('')
const classFilter = ref('')

const sheetOpen = ref(false)
const detail = ref(null)

const classOptions = computed(() => {
  const opts = [{ label: '全部班级', value: '' }]
  // 有学生的班级才值得做筛选项 —— 空班选了只会得到"这个班没有学生"
  const withStudents = classes.value.filter(c => Number(c.studentCount) > 0)
  const src = withStudents.length ? withStudents : classes.value
  src.forEach(c => opts.push({ label: c.name, value: String(c.id) }))
  return opts
})

const filtered = computed(() => {
  if (!classFilter.value) return students.value
  return students.value.filter(s => String(s.classId) === classFilter.value)
})

const scores = computed(() => (detail.value && Array.isArray(detail.value.scores) ? detail.value.scores : []))
// 2026-10-02（W1.6）：过一遍归一化，模板才能用 `statusText` / `warningStatusTagClass`
// （原来模板自己写 `status === 1 ? '已处理' : '待处理'`，status=2 会被显示成"待处理"）。
const warnings = computed(() => normalizeWarnings(
  detail.value && Array.isArray(detail.value.warnings) ? detail.value.warnings : []
))

const sheetTitle = computed(() => (detail.value && detail.value.name) || '学生详情')

function numOrDash(v) {
  return v === null || v === undefined ? '--' : v
}

/** 绩点为 null = 一条成绩都没有，显示「--」而不是 0.00。 */
function gpaText(gpa) {
  return gpa === null || gpa === undefined ? '--' : Number(gpa).toFixed(2)
}

function gpaClass(gpa) {
  if (gpa === null || gpa === undefined) return ''
  const n = Number(gpa)
  if (n < 1.5) return 'num__v--danger'
  if (n < 2.0) return 'num__v--warn'
  return ''
}

function passedText(s) {
  return `${s.passedCourses || 0} / ${s.totalCourses || 0}`
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

function goWarnings() {
  sheetOpen.value = false
  go('/pages/college-admin/warnings')
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [cls, list] = await Promise.all([
      collegeAdminAPI.getClasses(userId).catch(e => { console.error('[college-admin students] 班级失败:', e); return null }),
      collegeAdminAPI.getStudents(userId).catch(e => { console.error('[college-admin students] 学生失败:', e); return null })
    ])
    classes.value = Array.isArray(cls) ? cls : []
    students.value = Array.isArray(list) ? list : []
  } finally {
    loading.value = false
  }
}

/** 关键字走**服务端**（接口支持 name/学号/班级名子串匹配），所以这里是重新取一页。 */
async function doSearch() {
  appliedKeyword.value = String(keyword.value || '').trim()
  loading.value = true
  try {
    const list = await collegeAdminAPI.getStudents(userId, appliedKeyword.value || undefined)
    students.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('[college-admin students] 搜索失败:', e)
    toast((e && e.message) || '搜索失败')
  } finally {
    loading.value = false
  }
}

async function clearSearch() {
  keyword.value = ''
  appliedKeyword.value = ''
  await load()
}

async function openDetail(s) {
  detail.value = s
  sheetOpen.value = true
  try {
    // 第二参数是 student_profile.id（列表里的 `id`），不是学号
    const d = await collegeAdminAPI.getStudentDetail(userId, s.id)
    if (d && typeof d === 'object' && detail.value && detail.value.id === s.id) {
      detail.value = d
    }
  } catch (e) {
    console.error('[college-admin students] 详情失败:', e)
    toast((e && e.message) || '详情加载失败')
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-sm;
  padding-bottom: $spacing-3xl;
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
  padding: $spacing-md $page-padding 0;
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

.search__clear {
  flex-shrink: 0;
  height: $tap-min;
  padding: 0 $spacing-md;
  margin-left: $spacing-xs;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

/* ---------- 汇总 ---------- */
.summary {
  display: flex;
  align-items: baseline;
  padding: 0 $page-padding $spacing-sm;
}

.summary__text {
  flex-shrink: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.summary__num {
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.summary__hint {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  text-align: right;
  @include ellipsis;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 学生行 ---------- */
.stu {
  padding: $spacing-lg;
}

.stu--divided {
  border-top: 1rpx solid $color-divider;
}

.stu:active {
  background: $color-bg;
}

.stu__head {
  display: flex;
  align-items: center;
}

.stu__name {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.stu__nums {
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

.num__v--danger {
  color: $color-danger;
}

.num__v--warn {
  color: $color-warning;
}

.num__k {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.stu__sid {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 详情 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-xl;
}

.facts {
  padding: 0 $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.facts__row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-md 0;
}

.facts__k {
  width: 200rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

.facts__v {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  text-align: right;
}

.empty-tip {
  display: block;
  padding: $spacing-md 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 成绩行 ---------- */
.srow {
  display: flex;
  align-items: center;
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.srow__left {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-md;
}

.srow__name {
  display: block;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.srow__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.srow__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.srow__total {
  font-size: $font-md;
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

.srow__total--pass,
.srow__total--medium {
  color: $color-text;
}

.srow__total--good,
.srow__total--excellent {
  color: $color-success;
}

.srow__grade {
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 预警行 ---------- */
.wrow {
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.wrow__head {
  display: flex;
  align-items: center;
}

.wrow__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  line-height: 1.6;
  color: $color-text-secondary;
}

.wrow__time {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
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

.note--in {
  margin-left: 0;
  margin-right: 0;
}

/* ---------- 底部 ---------- */
.acts {
  display: flex;
  flex-wrap: wrap;
}

.act {
  display: flex;
  align-items: center;
  height: $tap-min;
  padding: 0 $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
  border: 1rpx solid $color-border;
}

.act__text {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
}
</style>
