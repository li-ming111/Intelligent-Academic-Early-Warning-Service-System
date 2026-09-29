<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="成绩跟踪" :show-back="false" />

    <role-hero
      :title="counselorName || '成绩跟踪'"
      subtitle="数据源为 scores 表，每门课取最新一次期末已录入的成绩"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body">
      <view class="tabs">
        <view
          v-for="t in TABS"
          :key="t.key"
          class="tabs__item"
          :class="{ 'tabs__item--on': tab === t.key }"
          @tap="tab = t.key"
        >{{ t.label }}</view>
      </view>

      <!-- ① 班级成绩 -->
      <template v-if="tab === 'class'">
        <filter-bar v-if="classOptions.length > 1" v-model="classId" :options="classOptions" />

        <page-loading v-if="loading.class" :count="3" mode="card" />
        <template v-else>
          <empty-state
            v-if="!classStudents.length"
            text="该班级暂无成绩"
            hint="要么班上还没有学生，要么期末成绩尚未录入 —— 未录入的成绩不计入统计"
            icon="book"
          />
          <view v-else class="list">
            <view v-for="s in classStudents" :key="s.profileId" class="stu" @tap="openStudent(s)">
              <view class="stu__main">
                <view class="stu__top">
                  <text class="stu__name">{{ s.name }}</text>
                  <text class="stu__count">{{ s.courseCount }} 门</text>
                </view>
                <view class="stu__metrics">
                  <text class="stu__m">均分 <text class="stu__v">{{ s.avg }}</text></text>
                  <text class="stu__m">最低 <text class="stu__v">{{ s.min }}</text></text>
                </view>
              </view>
              <view class="stu__right">
                <text v-if="s.failCount" class="tag tag--danger">挂 {{ s.failCount }} 门</text>
                <text v-else class="tag tag--success">全部及格</text>
                <app-icon name="chevron" :size="26" color="#aeaeb2" />
              </view>
            </view>
          </view>
        </template>
      </template>

      <!-- ② 低分关注 -->
      <template v-else-if="tab === 'low'">
        <view class="hint">
          <text class="hint__text">关注线为 <text class="hint__strong">70 分</text>，高于及格线 60 —— 它捞的是「偏低但还没挂科」的学生。挂科门数另按 60 分口径统计，两者不可混用。</text>
        </view>

        <page-loading v-if="loading.low" :count="3" mode="card" />
        <template v-else>
          <empty-state
            v-if="!lowGroups.length"
            text="没有需要关注的学生"
            hint="所带班级没有低于 70 分的成绩记录"
            icon="check"
          />
          <view v-else class="list">
            <view v-for="g in lowGroups" :key="g.profileId" class="stu" @tap="goStudent(g.profileId)">
              <view class="stu__main">
                <view class="stu__top">
                  <text class="stu__name">{{ g.name }}</text>
                  <text class="stu__count">{{ g.className }}</text>
                </view>
                <text class="stu__courses">{{ g.courseText }}</text>
              </view>
              <view class="stu__right">
                <text class="tag tag--warning">低分 {{ g.count }} 门</text>
                <app-icon name="chevron" :size="26" color="#aeaeb2" />
              </view>
            </view>
          </view>
        </template>
      </template>

      <!-- ③ 分数分布 -->
      <template v-else>
        <filter-bar v-if="courseOptions.length > 1" v-model="courseId" :options="courseOptions" />

        <page-loading v-if="loading.dist" :count="2" mode="card" />
        <template v-else>
          <empty-state
            v-if="!courseOptions.length"
            text="暂无可分析的课程"
            hint="先选一个有成绩的班级"
            icon="chart"
          />
          <template v-else>
            <view class="card">
              <view class="dist__head">
                <text class="dist__title">{{ currentCourseName }}</text>
                <text class="dist__avg">平均分 {{ numOr(dist.average) }}</text>
              </view>
              <view class="dist__total">共 {{ numOr(dist.total) }} 条成绩记录</view>

              <view v-for="b in buckets" :key="b.key" class="dist-row">
                <view class="dist-row__label">
                  <text class="dist-row__name">{{ b.label }}</text>
                  <text class="dist-row__range">{{ b.range }}</text>
                </view>
                <view class="dist-row__track">
                  <view class="dist-row__fill" :style="{ width: b.pct + '%', background: b.color }" />
                </view>
                <text class="dist-row__num">{{ b.count }}</text>
              </view>
            </view>

            <view class="note">
              <text class="note__text">档位线：≥90 优秀 · 80–89 良好 · 70–79 中等 · 60–69 及格 · &lt;60 不及格。各档之和即总数（后端刻意用「各档之和」而非行数作分母，避免两者不等时百分比对不上）。</text>
              <text class="note__text">分布范围是「所带全部班级」中修读该课程的学生，不只是上面选中的那个班，所以人数可能多于班级成绩里该课的人数。</text>
            </view>
          </template>
        </template>
      </template>
    </view>

    <!-- 单名学生逐门成绩 -->
    <detail-sheet v-model="sheetOpen" :title="current.name || '学生成绩'" height="76vh">
      <view v-if="current.profileId" class="sheet">
        <view class="sheet__head">
          <text class="sheet__name">{{ current.name }}</text>
          <text class="sheet__sub">{{ current.className }}</text>
        </view>
        <view class="sheet__metrics">
          <text class="sheet__m">修读 <text class="sheet__v">{{ current.courseCount }}</text> 门</text>
          <text class="sheet__m">均分 <text class="sheet__v">{{ current.avg }}</text></text>
          <text class="sheet__m">
            挂科 <text class="sheet__v" :class="{ 'sheet__v--danger': current.failCount }">{{ current.failCount }}</text> 门
          </text>
        </view>

        <view class="courses">
          <view v-for="c in current.courses" :key="c.key" class="course-row">
            <view class="course-row__main">
              <text class="course-row__name">{{ c.name }}</text>
              <text class="course-row__sub">{{ c.credits }} 学分 · 第 {{ c.semester }} 学期</text>
            </view>
            <score-cell :score="c.score" />
          </view>
        </view>

        <text class="sheet__tip">这里每门课只有一行 —— 同一门课重修多次也只显示最新一次且期末已录入的成绩，重修通过会抵掉历史挂科。想看跨学期走势请到学生管理里看趋势图。</text>
      </view>

      <template #footer>
        <view class="btn-primary" @tap="goStudent(current.profileId, true)">查看该生完整档案 ›</view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

const TABS = [
  { key: 'class', label: '班级成绩' },
  { key: 'low', label: '低分关注' },
  { key: 'dist', label: '分数分布' }
]

/**
 * 档位线与后端 `getCourseScoreDistribution` 里的 if/else 逐条一致
 * （90/80/70/60），同时与 `ScoreCaliber.SCORE_LEVEL_CASE` 是同一组边界。
 * 改动前先改后端那两处，别只改这里。
 */
const BUCKETS = [
  { key: 'excellent', label: '优秀', range: '≥90', color: '#22c55e' },
  { key: 'good', label: '良好', range: '80–89', color: '#4caf50' },
  { key: 'normal', label: '中等', range: '70–79', color: '#f59e0b' },
  { key: 'pass', label: '及格', range: '60–69', color: '#f97316' },
  { key: 'fail', label: '不及格', range: '<60', color: '#ef4444' }
]

const tab = ref('class')

const classes = ref([])
const classId = ref('')
const classRows = ref([])
const lowRows = ref([])
const dist = ref({})
const courseId = ref('')

const loading = ref({ class: false, low: false, dist: false })

const sheetOpen = ref(false)
const current = ref({})

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const classOptions = computed(() =>
  classes.value.map(c => ({ label: c.name, value: String(c.id), count: numOr(c.studentCount) }))
)

/**
 * 班级成绩按学生聚合。
 *
 * 后端给的是**一学生一课程一行**（`scores` 经 LATEST_VALID_SCORE 去重，每门课只留
 * 最新一次且期末已录入的成绩），所以一门课只出现一次，"挂科门数"就是行数里 < 60 的条数，
 * 不需要再去重 —— 去重反而会把重修后的第二条算漏。
 */
const classStudents = computed(() => {
  const byStu = {}
  classRows.value.forEach(r => {
    const pid = String(r.student_id ?? '')
    if (!pid || pid === 'null') return
    if (!byStu[pid]) {
      byStu[pid] = {
        profileId: pid,
        name: r.student_name || '未知学生',
        className: r.class_name || '',
        scores: []
      }
    }
    byStu[pid].scores.push(r)
  })

  return Object.values(byStu)
    .map(s => {
      const nums = s.scores.map(x => numOr(x.score_total))
      return {
        ...s,
        courseCount: s.scores.length,
        avg: avgOf(nums),
        min: nums.length ? Math.min(...nums).toFixed(1) : '--',
        failCount: nums.filter(v => v < 60).length,
        courses: s.scores
          .slice()
          .sort((a, b) => numOr(a.score_total) - numOr(b.score_total))
          .map(c => ({
            key: `${c.course_id}-${c.semester}`,
            name: c.course_name || '未知课程',
            credits: numOr(c.credits),
            semester: c.semester,
            // score-cell 既能吃裸值也能吃整行；这里给**整行**，
            // 让"待录入"判定走同一条路径（当前口径下这里不会出现待录入行）
            score: c
          }))
      }
    })
    .sort((a, b) => b.failCount - a.failCount || Number(b.avg) - Number(a.avg))
})

/** 低分关注按学生聚合 */
const lowGroups = computed(() => {
  const byStu = {}
  lowRows.value.forEach(r => {
    const pid = String(r.student_id ?? '')
    if (!pid || pid === 'null') return
    if (!byStu[pid]) {
      byStu[pid] = { profileId: pid, name: r.student_name || '未知学生', className: r.class_name || '', items: [] }
    }
    byStu[pid].items.push({ name: r.course_name || '未知课程', score: numOr(r.score_total) })
  })

  return Object.values(byStu)
    .map(g => {
      const sorted = g.items.slice().sort((a, b) => a.score - b.score)
      return {
        ...g,
        count: sorted.length,
        // 每门课取最低的 2 门写在行上：扫一眼就知道是"全面偏低"还是"单科拖后腿"
        courseText: sorted
          .slice(0, 2)
          .map(i => `${i.name} ${i.score.toFixed(0)}`)
          .join(' · ') + (sorted.length > 2 ? ` 等 ${sorted.length} 门` : '')
      }
    })
    .sort((a, b) => b.count - a.count)
})

/**
 * 可选课程：从**当前班级**的成绩行里取，不另开接口。
 * 顺带把"本班该课的均分/人数"算出来，用于和分布接口（全辅导员范围）对照。
 */
const classCourses = computed(() => {
  const map = {}
  classRows.value.forEach(r => {
    const cid = String(r.course_id ?? '')
    if (!cid || cid === 'null') return
    if (!map[cid]) map[cid] = { id: cid, name: r.course_name || '未知课程', nums: [] }
    map[cid].nums.push(numOr(r.score_total))
  })
  return Object.values(map).map(c => ({
    id: c.id,
    name: c.name,
    count: c.nums.length,
    avg: avgOf(c.nums)
  }))
})

const courseOptions = computed(() =>
  classCourses.value.map(c => ({ label: c.name, value: c.id, count: c.count }))
)

const currentCourseName = computed(() => {
  const hit = classCourses.value.find(c => c.id === courseId.value)
  return hit ? hit.name : '分数分布'
})

const buckets = computed(() => {
  const d = dist.value && typeof dist.value === 'object' ? dist.value : {}
  // 柱长按最大档归一，不是按总数：一班 30 人时"不及格 3 人"若按总数画只有 10% 长，看不出问题
  const max = Math.max(1, ...BUCKETS.map(b => numOr(d[b.key])))
  return BUCKETS.map(b => ({
    ...b,
    count: numOr(d[b.key]),
    pct: Math.round((numOr(d[b.key]) / max) * 100)
  }))
})

const heroStats = computed(() => {
  const graded = classStudents.value.length
  const failing = classStudents.value.filter(s => s.failCount > 0).length
  const allNums = classRows.value.map(r => numOr(r.score_total))
  return [
    { label: '有成绩学生', value: graded },
    { label: '存在挂科', value: failing },
    { label: '低于 70 分', value: lowRows.value.length },
    { label: '班级均分', value: allNums.length ? avgOf(allNums) : '--' }
  ]
})

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function avgOf(nums) {
  if (!nums.length) return '--'
  return (nums.reduce((a, b) => a + b, 0) / nums.length).toFixed(1)
}

function openStudent(s) {
  current.value = s
  sheetOpen.value = true
}

/** 跳到学生档案；fromSheet=true 时先关掉抽屉，否则新页面盖在抽屉上 */
function goStudent(profileId, fromSheet = false) {
  if (fromSheet) sheetOpen.value = false
  if (!profileId) return
  // students 页用 `focus=<profileId>` 直接展开这名学生
  go(`/pages/counselor/students?focus=${profileId}`)
}

onMounted(loadClasses)

async function loadClasses() {
  loading.value.class = true
  try {
    if (!userId) return
    const list = await counselorAPI.getMyClasses(userId).catch(e => {
      console.error('[scores] 班级失败:', e)
      return []
    })
    classes.value = Array.isArray(list) ? list : []
    // 默认选第一个**有学生**的班：库里 8 个班只有 1 个有学生，
    // 默认落在 0 人班上会让整页空着，看着像坏了
    const first = classes.value.find(c => numOr(c.studentCount) > 0) || classes.value[0]
    if (first) {
      classId.value = String(first.id)
      await loadClassScores()
    }
  } finally {
    loading.value.class = false
  }
}

async function loadClassScores() {
  if (!classId.value) return
  loading.value.class = true
  try {
    const rows = await counselorAPI.getClassScores(classId.value, userId).catch(e => {
      console.error('[scores] 班级成绩失败:', e)
      return []
    })
    classRows.value = Array.isArray(rows) ? rows : []
    // 班级换了，课程也就换了：原选中课程若不在新班里就自动落到第一门
    if (!classCourses.value.some(c => c.id === courseId.value)) {
      courseId.value = classCourses.value.length ? classCourses.value[0].id : ''
    }
  } finally {
    loading.value.class = false
  }
}

async function loadLow() {
  if (lowRows.value.length || loading.value.low) return
  loading.value.low = true
  try {
    const rows = await counselorAPI.getLowScoreStudents(userId).catch(e => {
      console.error('[scores] 低分名单失败:', e)
      return []
    })
    lowRows.value = Array.isArray(rows) ? rows : []
  } finally {
    loading.value.low = false
  }
}

async function loadDist() {
  if (!courseId.value) return
  loading.value.dist = true
  try {
    const d = await counselorAPI.getScoreDistribution(courseId.value, userId).catch(e => {
      console.error('[scores] 分布失败:', e)
      return null
    })
    dist.value = d && typeof d === 'object' ? d : {}
  } finally {
    loading.value.dist = false
  }
}

watch(classId, () => {
  if (tab.value === 'class') loadClassScores()
})
watch(courseId, () => {
  if (tab.value === 'dist' && courseId.value) loadDist()
})

/** 切 tab 时才拉各自的数据 —— 三块互不依赖，没必要一进页面就发三个请求 */
watch(tab, key => {
  if (key === 'low') loadLow()
  if (key === 'dist') {
    if (!courseId.value && classCourses.value.length) courseId.value = classCourses.value[0].id
    else loadDist()
  }
})
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

.card {
  padding: $spacing-lg;
  @include card;
}

/* ---------- tab ---------- */
.tabs {
  display: flex;
  padding: 6rpx;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.tabs__item {
  flex: 1;
  min-width: 0;
  padding: $spacing-sm 0;
  border-radius: $radius-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  text-align: center;
}

.tabs__item--on {
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-counselor});
  font-weight: 600;
}

/* ---------- 提示 ---------- */
.hint {
  margin-top: $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.hint__text {
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.6;
}

.hint__strong {
  color: var(--color-primary);
  font-weight: 600;
}

/* ---------- 列表 ---------- */
.list {
  margin-top: $spacing-md;
}

.stu {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-sm;
  padding: $spacing-lg;
  @include card;
}

.stu__main {
  flex: 1;
  min-width: 0;
}

.stu__top {
  display: flex;
  align-items: baseline;
}

.stu__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.stu__count {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.stu__metrics {
  display: flex;
  margin-top: 6rpx;
}

.stu__m {
  margin-right: $spacing-lg;
  font-size: $font-xs;
  color: $color-text-hint;
}

.stu__v {
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.stu__courses {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
}

.stu__right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-left: $spacing-md;
}

.stu__right .tag {
  margin-right: $spacing-xs;
}

/* ---------- 分布 ---------- */
.dist__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.dist__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.dist__avg {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.dist__total {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.dist-row {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.dist-row__label {
  width: 150rpx;
  flex-shrink: 0;
}

.dist-row__name {
  display: block;
  font-size: $font-xs;
  color: $color-text;
}

.dist-row__range {
  display: block;
  font-size: 20rpx;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.dist-row__track {
  flex: 1;
  min-width: 0;
  height: 20rpx;
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
  width: 60rpx;
  flex-shrink: 0;
  text-align: right;
  font-size: $font-xs;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

/* ---------- 说明 ---------- */
.note {
  margin-top: $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.note__text {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.note__text + .note__text {
  margin-top: $spacing-sm;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding: 0 $page-padding $spacing-2xl;
}

.sheet__head {
  display: flex;
  align-items: baseline;
}

.sheet__name {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.sheet__sub {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.sheet__metrics {
  display: flex;
  margin-top: $spacing-xs;
  padding-bottom: $spacing-md;
  border-bottom: 1rpx solid $color-divider;
}

.sheet__m {
  margin-right: $spacing-lg;
  font-size: $font-xs;
  color: $color-text-hint;
}

.sheet__v {
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.sheet__v--danger {
  color: $color-danger;
}

.courses {
  margin-top: $spacing-xs;
}

.course-row {
  display: flex;
  align-items: center;
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.course-row__main {
  flex: 1;
  min-width: 0;
}

.course-row__name {
  display: block;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.course-row__sub {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.sheet__tip {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}
</style>
