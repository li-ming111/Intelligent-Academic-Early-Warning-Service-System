<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="课程情况" :show-back="false" />

    <role-hero
      :title="counselorName || '课程情况'"
      subtitle="按课程聚合所带班级的成绩记录"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <filter-bar v-if="courses.length > 1" v-model="sortKey" :options="sortOptions" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!courses.length"
          text="暂无可统计的课程"
          hint="所带班级还没有已录入期末成绩的记录"
          icon="book"
        />

        <view v-else class="list">
          <view v-for="c in courses" :key="c.id" class="course" @tap="openCourse(c)">
            <view class="course__head">
              <text class="course__name">{{ c.name }}</text>
              <text class="course__credits">{{ c.credits }} 学分</text>
            </view>

            <view class="course__metrics">
              <view class="m">
                <text class="m__label">修读</text>
                <text class="m__value">{{ c.count }}<text class="m__unit">人</text></text>
              </view>
              <view class="m">
                <text class="m__label">均分</text>
                <text class="m__value">{{ c.avg }}</text>
              </view>
              <view class="m">
                <text class="m__label">最低</text>
                <text class="m__value">{{ c.min }}</text>
              </view>
              <view class="m">
                <text class="m__label">挂科</text>
                <text class="m__value" :class="{ 'm__value--danger': c.failCount }">{{ c.failCount }}<text class="m__unit">人次</text></text>
              </view>
            </view>

            <view class="course__bar">
              <view class="course__track">
                <view class="course__pass" :style="{ width: c.passRate + '%' }" />
              </view>
              <text class="course__rate">通过率 {{ c.passRate }}%</text>
            </view>

            <text v-if="c.lowCount" class="course__low">有 {{ c.lowCount }} 条低于 70 分，值得提前关注 ›</text>
          </view>
        </view>

        <view class="note">
          <text class="note__line">这一页的数据源是 GET /counselors/enrollments，它名字叫「选课」，实际返回的是成绩记录 —— 库里没有选课关系表，能拿到的只有「有成绩的学生-课程」对。所以这里按课程聚合，和按班级、按学生看的成绩页是三个视角，不是同一张表换个排版。</text>
          <text class="note__line">「修读人数」是成绩记录条数（一人一课一条），不是选课人数；重修只保留最新一次有效成绩。</text>
          <text class="note__line">「通过率」= 总评 ≥ 60 的记录 / 全部记录，与「挂科」是同一根线（60 分）的两面。</text>
        </view>
      </template>
    </view>

    <!-- 课程详情：五档分布 + 低分名单 -->
    <detail-sheet v-model="sheetOpen" :title="current.name || '课程情况'" height="80vh">
      <view v-if="current.id" class="sheet">
        <view class="sheet__head">
          <text class="sheet__credits">{{ current.credits }} 学分 · 修读 {{ current.count }} 人</text>
          <text class="sheet__avg">均分 {{ current.avg }}</text>
        </view>

        <text class="sheet__label">分数分布（所带全部班级）</text>
        <page-loading v-if="distLoading" :count="1" mode="card" />
        <template v-else>
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
        </template>

        <text class="sheet__label">低于 70 分的学生（{{ lowForCourse.length }} 人）</text>
        <empty-state
          v-if="!lowForCourse.length"
          text="没有低于 70 分的成绩"
          hint="该课程暂无需重点关注的学生"
          icon="check"
          :top="20"
        />
        <view v-else>
          <view v-for="s in lowForCourse" :key="s.profileId" class="low" @tap="goStudent(s.profileId)">
            <view class="low__main">
              <text class="low__name">{{ s.name }}</text>
              <text class="low__class">{{ s.className }}</text>
            </view>
            <text class="low__score">{{ s.score }}</text>
            <app-icon name="chevron" :size="26" color="#aeaeb2" />
          </view>
        </view>

        <text class="sheet__tip">分数分布取的是「所带全部班级」中修读该课程的学生，低分名单同样来自全辅导员范围，与上面的修读人数同源。</text>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

/** 与得分档位同一组边界（90/80/70/60），改后端时一起改 */
const BUCKETS = [
  { key: 'excellent', label: '优秀', range: '≥90', color: '#22c55e' },
  { key: 'good', label: '良好', range: '80–89', color: '#4caf50' },
  { key: 'normal', label: '中等', range: '70–79', color: '#f59e0b' },
  { key: 'pass', label: '及格', range: '60–69', color: '#f97316' },
  { key: 'fail', label: '不及格', range: '<60', color: '#ef4444' }
]

const SORTS = [
  { key: 'count', label: '按修读人数' },
  { key: 'avg', label: '按均分' },
  { key: 'fail', label: '按挂科数' }
]

const loading = ref(true)
const rows = ref([])
const lowRows = ref([])
const sortKey = ref('count')

const sheetOpen = ref(false)
const current = ref({})
const dist = ref({})
const distLoading = ref(false)

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const sortOptions = computed(() => SORTS.map(s => ({ label: s.label, value: s.key })))

/** 低分名单按 course_id 建索引，供详情抽屉取用 */
const lowByCourse = computed(() => {
  const map = {}
  lowRows.value.forEach(r => {
    const cid = String(r.course_id ?? '')
    if (!cid || cid === 'null') return
    if (!map[cid]) map[cid] = []
    map[cid].push({
      profileId: r.student_id,
      name: r.student_name || '未知学生',
      className: r.class_name || '',
      score: numOr(r.score_total)
    })
  })
  Object.keys(map).forEach(k => map[k].sort((a, b) => a.score - b.score))
  return map
})

const courses = computed(() => {
  const map = {}
  rows.value.forEach(r => {
    const cid = String(r.course_id ?? '')
    if (!cid || cid === 'null') return
    if (!map[cid]) {
      map[cid] = { id: cid, name: r.course_name || '未知课程', credits: numOr(r.credits), nums: [] }
    }
    map[cid].nums.push(numOr(r.score_total))
  })

  const list = Object.values(map).map(c => {
    const passed = c.nums.filter(v => v >= 60).length
    return {
      id: c.id,
      name: c.name,
      credits: c.credits,
      count: c.nums.length,
      avg: avgOf(c.nums),
      min: c.nums.length ? Math.min(...c.nums).toFixed(1) : '--',
      failCount: c.nums.length - passed,
      passRate: c.nums.length ? Math.round((passed / c.nums.length) * 100) : 0,
      lowCount: (lowByCourse.value[c.id] || []).length
    }
  })

  if (sortKey.value === 'avg') {
    // 均分排序把没数据的排最后（这里不存在 0 条记录的课程，防御性保留）
    list.sort((a, b) => Number(b.avg) - Number(a.avg))
  } else if (sortKey.value === 'fail') {
    list.sort((a, b) => b.failCount - a.failCount || Number(a.avg) - Number(b.avg))
  } else {
    list.sort((a, b) => b.count - a.count || Number(a.avg) - Number(b.avg))
  }
  return list
})

const lowForCourse = computed(() => lowByCourse.value[String(current.value.id)] || [])

const buckets = computed(() => {
  const d = dist.value && typeof dist.value === 'object' ? dist.value : {}
  const max = Math.max(1, ...BUCKETS.map(b => numOr(d[b.key])))
  return BUCKETS.map(b => ({
    ...b,
    count: numOr(d[b.key]),
    pct: Math.round((numOr(d[b.key]) / max) * 100)
  }))
})

const heroStats = computed(() => {
  const all = rows.value.map(r => numOr(r.score_total))
  return [
    { label: '课程数', value: courses.value.length },
    { label: '成绩记录', value: rows.value.length },
    { label: '挂科记录', value: all.filter(v => v < 60).length },
    { label: '平均分', value: all.length ? avgOf(all) : '--' }
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

function openCourse(c) {
  current.value = c
  dist.value = {}
  sheetOpen.value = true
  loadDist(c.id)
}

async function loadDist(courseId) {
  distLoading.value = true
  try {
    const d = await counselorAPI.getScoreDistribution(courseId, userId).catch(e => {
      console.error('[courses] 分布失败:', e)
      return null
    })
    dist.value = d && typeof d === 'object' ? d : {}
  } finally {
    distLoading.value = false
  }
}

function goStudent(profileId) {
  if (!profileId) return
  sheetOpen.value = false
  go(`/pages/counselor/students?focus=${profileId}`)
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [e, low] = await Promise.all([
      counselorAPI.getEnrollments(userId).catch(err => { console.error('[courses] 课程记录失败:', err); return [] }),
      counselorAPI.getLowScoreStudents(userId).catch(err => { console.error('[courses] 低分名单失败:', err); return [] })
    ])
    rows.value = Array.isArray(e) ? e : []
    lowRows.value = Array.isArray(low) ? low : []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.list {
  padding: 0 $page-padding;
}

.course {
  margin-bottom: $spacing-md;
  padding: $spacing-lg;
  @include card;
}

.course__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.course__name {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.course__credits {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.course__metrics {
  display: flex;
  margin-top: $spacing-md;
}

.m {
  flex: 1;
  min-width: 0;
}

.m__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.m__value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.m__value--danger {
  color: $color-danger;
}

.m__unit {
  font-size: $font-xs;
  font-weight: 400;
  color: $color-text-hint;
}

.course__bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.course__track {
  flex: 1;
  min-width: 0;
  height: 12rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.course__pass {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.course__rate {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.course__low {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-warning;
}

/* ---------- 说明 ---------- */
.note {
  margin: $spacing-md $page-padding 0;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.note__line {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.note__line + .note__line {
  margin-top: $spacing-xs;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding: 0 $page-padding $spacing-2xl;
}

.sheet__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: $spacing-sm;
  border-bottom: 1rpx solid $color-divider;
}

.sheet__credits {
  font-size: $font-xs;
  color: $color-text-secondary;
}

.sheet__avg {
  font-size: $font-sm;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.sheet__label {
  display: block;
  margin-top: $spacing-lg;
  margin-bottom: $spacing-xs;
  font-size: $font-sm;
  color: $color-text;
}

.sheet__tip {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.dist-row {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.dist-row__label {
  width: 140rpx;
  flex-shrink: 0;
}

.dist-row__name {
  display: block;
  font-size: $font-xs;
  color: $color-text;
}

.dist-row__range {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.dist-row__track {
  flex: 1;
  min-width: 0;
  height: 18rpx;
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
  width: 56rpx;
  flex-shrink: 0;
  text-align: right;
  font-size: $font-xs;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.low {
  display: flex;
  align-items: center;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.low__main {
  flex: 1;
  min-width: 0;
}

.low__name {
  display: block;
  font-size: $font-sm;
  color: $color-text;
}

.low__class {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.low__score {
  flex-shrink: 0;
  margin-right: $spacing-xs;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-warning;
  font-variant-numeric: tabular-nums;
}
</style>
