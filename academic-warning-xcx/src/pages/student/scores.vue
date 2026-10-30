<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="成绩查询" :show-back="false" />

    <role-hero
      :title="heroTitle"
      :subtitle="heroSubtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <!-- 学期选项由数据里**实际存在**的学期生成，不写死一张学期表。
         后端 `?semester=` 只认数字：传 `2025-2026-1` 这种标签不报错也不筛选，
         静默返回全量（看起来像筛选生效了）。所以这里一次取全量、本地按学期号筛。 -->
    <filter-bar v-model="semesterKey" :options="semesterOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <empty-state
          v-if="!visibleRows.length"
          text="该学期暂无成绩记录"
          :hint="semesterKey ? '换个学期看看，或点「全部」查看所有学期' : '暂无成绩数据'"
          icon="doc"
        />

        <view v-else class="score-list">
          <view v-for="row in visibleRows" :key="row.id || row.courseId" class="score-item">
            <view class="score-item__head">
              <text class="score-item__name">{{ row.courseName || '未命名课程' }}</text>
              <text v-if="row.grade" class="score-item__grade">{{ row.grade }}</text>
            </view>

            <view class="score-item__meta">
              <text>{{ creditsText(row) }}</text>
              <text v-if="row.teacherName" class="score-item__meta-sep">·</text>
              <text v-if="row.teacherName">{{ row.teacherName }}</text>
              <text class="score-item__meta-sep">·</text>
              <text>{{ rowLabel(row) }}</text>
            </view>

            <!-- 平时 / 期末 / 总评 / 绩点 四格分开显示，这是成绩口径最直观的展示位：
                 期末未录入时那一格是灰色「待录入」，总评同样灰 ——
                 旧版这里会把库里的中间值渲染成红色的 40 分，与挂科分不出来。 -->
            <view class="score-item__grid">
              <view class="score-item__cell">
                <view class="score-item__val"><score-cell :score="row.regularScore" /></view>
                <text class="score-item__cell-label">平时</text>
              </view>
              <view class="score-item__cell">
                <view class="score-item__val"><score-cell :score="row.finalScore" /></view>
                <text class="score-item__cell-label">期末</text>
              </view>
              <view class="score-item__cell">
                <view class="score-item__val"><score-cell :score="row" large /></view>
                <text class="score-item__cell-label">总评</text>
              </view>
              <view class="score-item__cell">
                <view class="score-item__val">
                  <text class="score-item__point" :class="{ 'score-item__point--pending': row.pending }">
                    {{ pointText(row) }}
                  </text>
                </view>
                <text class="score-item__cell-label">绩点</text>
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
import { normalizeScoreRows, summarizeScores } from '@/utils/score-normalize.js'
import { toNumber, calcGpa, PENDING_TEXT } from '@/utils/score.js'
import { semesterLabelOf, number, EMPTY_TEXT } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'

// 注意 useUser() 返回的是**快照值**（不是 ref），页面里直接当普通值用，
// 不要再点 .value —— 那是历史代码里最容易写错的一处。
const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const rows = ref([])
/** '' = 全部；其余是学期号字符串，如 '5' */
const semesterKey = ref('')
/** 学期号 → `2025-2026-2`。来源是 `/students/{uid}/gpa`（后端直接给了标签）。 */
const labels = ref({})

const semesterOptions = computed(() => {
  const seen = new Set()
  for (const row of rows.value) {
    const n = toNumber(row.semester)
    if (n !== null) seen.add(String(n))
  }
  const nums = [...seen].sort((a, b) => Number(b) - Number(a))
  return [
    { label: '全部', value: '', count: rows.value.length },
    ...nums.map(n => ({ label: labelOf(n), value: n, count: countOf(n) }))
  ]
})

const visibleRows = computed(() => {
  if (semesterKey.value) {
    return rows.value.filter(r => String(toNumber(r.semester)) === String(semesterKey.value))
  }
  // 「全部」按学期**新的在前**，与上面筛选条的顺序一致。
  // 后端给的行是按学期升序的，直接铺出来最新一学期会压在最底下 ——
  // 筛选条明明把 2025-2026-2 排在前面，列表却把它放到最后，看着像两套数据。
  // Array.sort 是稳定排序，同学期内部保持后端原顺序。
  return [...rows.value].sort((a, b) => {
    const na = toNumber(a.semester)
    const nb = toNumber(b.semester)
    if (na === null || nb === null) return 0
    return nb - na
  })
})

/** 当前视图的汇总，口径与后端 `ScoreCaliber` 一致（按课程去重取最新一次）。 */
const summary = computed(() => summarizeScores(visibleRows.value))

const heroTitle = computed(() => userName || '我的成绩')

/** 副标题带上"还有几门课期末未录入" —— 这个数与下面的灰色「待录入」格一一对应，
    也是这套成绩口径最容易被追问的一点，主动写出来比让人发现更好。 */
const heroSubtitle = computed(() => {
  const base = semesterKey.value ? labelOf(semesterKey.value) : '全部学期'
  const pending = summary.value.pendingCourses
  return pending > 0 ? `${base} · ${pending} 门课期末待录入` : base
})

const avatarText = computed(() => (userName ? userName.slice(0, 1) : ''))

/**
 * 四个数与 `/students/dashboard/{uid}` 的
 * `totalCoursesCount` / `completedCredits` / `failedCoursesCount` / `gpa`
 * 逐项同口径（司明实测：8 / 17 / 3 / 1.6），可以当场对账。
 */
const heroStats = computed(() => {
  const s = summary.value
  return [
    { label: '课程数', value: s.totalCourses },
    { label: '已通过学分', value: s.passedCredits },
    // 挂科数可点 → 预警页：挂的课正是预警的明细来源，跳过去能对上账
    { label: '挂科', value: s.failed, onTap: () => go('/pages/student/warnings') },
    { label: '绩点', value: s.gpa === null ? EMPTY_TEXT : number(s.gpa, 1) }
  ]
})

/** 学期号 → 标签。优先用后端 `/gpa` 给的 `semesterLabel`，取不到才本地换算。 */
function labelOf(n) {
  const key = String(n)
  if (labels.value[key]) return labels.value[key]
  const row = rows.value.find(r => String(toNumber(r.semester)) === key)
  return semesterLabelOf(n, row && row.year) || `第${n}学期`
}

function countOf(n) {
  return rows.value.filter(r => String(toNumber(r.semester)) === String(n)).length
}

function rowLabel(row) {
  const n = toNumber(row.semester)
  return n === null ? '' : labelOf(n)
}

function creditsText(row) {
  const c = toNumber(row.credits)
  return c === null ? '学分未知' : `${c} 学分`
}

/** 绩点优先用后端写好的 `grade_point`，缺了才本地折算（阶梯与 ScoreSyncSupport 一致）。 */
function pointText(row) {
  if (row.pending) return PENDING_TEXT
  const gp = toNumber(row.gradePoint)
  if (gp !== null) return String(gp)
  return calcGpa(row.scoreTotal)
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [scorePayload, gpaPayload] = await Promise.all([
      studentAPI.getScores(userId),
      // 学期标签来自这里；它挂了不影响成绩展示，所以单独兜底
      studentAPI.getGPA(userId).catch(() => null)
    ])

    rows.value = normalizeScoreRows(scorePayload)

    const map = {}
    for (const item of Array.isArray(gpaPayload) ? gpaPayload : []) {
      const n = toNumber(item && (item.semester ?? item.semesterNo))
      const label = item && (item.semesterLabel || item.semester_label)
      if (n !== null && label) map[String(n)] = String(label)
    }
    labels.value = map
  } catch (e) {
    console.error('[scores] 加载失败:', e)
    rows.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.score-list {
  padding: 0 $page-padding;
}

.score-item {
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  @include card;
}

.score-item__head {
  display: flex;
  align-items: center;
}

.score-item__name {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.score-item__grade {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  padding: 2rpx 14rpx;
  border-radius: $radius-pill;
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-student});
  font-size: $font-xs;
  font-weight: 600;
}

.score-item__meta {
  display: flex;
  align-items: center;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.score-item__meta-sep {
  margin: 0 8rpx;
  color: $color-text-hint;
}

.score-item__grid {
  display: flex;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.score-item__cell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 四格的数字字号不同（总评是 $font-2xl，其余 $font-md），
   不给个等高容器的话总评那一格的标签会被顶下去，四个标签基线就错开了。
   统一 56rpx、底部对齐 —— 数字大小不同但标签永远在同一条线上。 */
.score-item__val {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 56rpx;
}

.score-item__cell-label {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.score-item__point {
  font-size: $font-md;
  line-height: 1;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

/* 绩点未折算时同样是灰色「待录入」，与总评保持一致 */
.score-item__point--pending {
  color: $color-pending;
  font-size: $font-sm;
}
</style>
