<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学分监控" :show-back="false" />

    <role-hero
      :title="counselorName || '学分监控'"
      subtitle="达标率的分母是「有成绩人数」，不是花名册"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 学分不足率 -->
        <view class="card">
          <view class="rate">
            <view class="rate__left">
              <text class="rate__num">{{ numOr(monitor.insufficientRate) }}<text class="rate__unit">%</text></text>
              <text class="rate__label">
                {{ numOr(monitor.creditInsufficient) }} / {{ numOr(monitor.gradedStudents) }} 名有成绩的学生存在挂科
              </text>
            </view>
            <view class="rate__bar">
              <view class="rate__bar-fill" :style="{ width: pct(monitor.insufficientRate) }" />
            </view>
          </view>
          <text class="note">该班带班学生共 {{ numOr(monitor.totalStudents) }} 人，其中 {{ numOr(monitor.gradedStudents) }} 人已有期末成绩。尚无成绩的新生不计入达标率分母 —— 否则开学第一学期的达标率会被人为压低。</text>
        </view>

        <!-- 班级达标情况。点一个班即筛选下方的明细。 -->
        <view class="section">
          <section-title
            title="班级达标情况"
            :extra="classFilter ? '再点一次取消筛选' : `${classMonitors.length} 个班`"
          />
          <empty-state
            v-if="!classMonitors.length"
            text="暂无班级数据"
            hint="先确认你名下是否有班级"
            icon="building"
            :top="40"
          />
          <view v-else class="card">
            <view
              v-for="(c, i) in classMonitors"
              :key="c.classId"
              class="cls"
              :class="{ 'cls--divided': i > 0, 'cls--on': classFilter === c.className }"
              @tap="toggleClass(c.className)"
            >
              <view class="cls__top">
                <text class="cls__name">{{ c.className }}</text>
                <text class="cls__rate">{{ numOr(c.achievementRate) }}%</text>
              </view>
              <view class="cls__track">
                <view class="cls__fill" :style="{ width: pct(c.achievementRate) }" />
              </view>
              <view class="cls__sub">
                <text class="cls__s">达标 {{ numOr(c.creditAchievementCount) }} / 有成绩 {{ numOr(c.gradedStudentCount) }} 人</text>
                <text class="cls__s">挂科 {{ numOr(c.failStudentCount) }} 人</text>
                <text class="cls__s">均分 {{ numOr(c.avgScore) }}</text>
              </view>
            </view>
          </view>
          <text
            v-if="classMonitors.length && !classMonitors.some(c => numOr(c.gradedStudentCount))"
            class="note note--inset"
          >名下班级暂无已录入的期末成绩，达标率一律为 0 属正常。</text>
        </view>

        <!-- 明细 -->
        <view class="section section--last">
          <section-title
            title="学分不足学生"
            :extra="`共 ${insufficient.length} 人`"
          />

          <filter-bar v-model="riskKey" :options="riskOptions" />

          <view v-if="classFilter" class="active-filter">
            <text class="active-filter__text">已筛选：{{ classFilter }}</text>
            <text class="active-filter__clear" @tap="classFilter = ''">清除</text>
          </view>

          <empty-state
            v-if="!visibleStudents.length"
            :text="insufficient.length ? '当前筛选下没有学生' : '没有学分不足的学生'"
            :hint="insufficient.length
              ? '换个风险等级或清除班级筛选'
              : '所带班级暂无「有挂科记录」的学生'"
            icon="check"
          />

          <view v-else class="stu-list">
            <view v-for="s in visibleStudents" :key="s.profileId" class="stu" @tap="goStudent(s.profileId)">
              <view class="stu__head">
                <text class="stu__name">{{ s.studentName || '未知学生' }}</text>
                <text class="stu__class">{{ s.className }}</text>
                <text class="tag" :class="riskTag(s.riskLevel)">{{ riskText(s.riskLevel) }}</text>
              </view>

              <view class="stu__bar">
                <view class="stu__track">
                  <view class="stu__fill" :style="{ width: creditPct(s) }" />
                </view>
                <text class="stu__pct">{{ creditPct(s) }}</text>
              </view>

              <view class="stu__foot">
                <text class="stu__credit">
                  已修 <text class="stu__strong">{{ numOr(s.score) }}</text> / 应修 {{ numOr(s.attemptedCredits) }} 学分
                </text>
                <text class="stu__fail">挂 {{ numOr(s.failCount) }} 门</text>
              </view>
            </view>
          </view>

          <view class="note note--box">
            <text class="note__line">「红色」= 挂科 3 门及以上，其余为「黄色」（后端按 fail_count ≥ 3 判定，不是按学分缺口）。</text>
            <text class="note__line">进度条是「已修 ÷ 应修」，应修学分取自该生实际修读记录。毕业要求的总学分是项目常量 120，不参与这里的计算。</text>
            <text class="note__line">名单只收「存在未通过课程」的学生；学分刚好修够但没有挂科的不会被列进来。</text>
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
import { go } from '@/utils/navigate.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

const loading = ref(true)
const monitor = ref({})
const classMonitors = ref([])
const insufficient = ref([])
/** '' = 全部；'red' / 'yellow' */
const riskKey = ref('')
const classFilter = ref('')

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const riskOptions = computed(() => {
  const red = insufficient.value.filter(s => s.riskLevel === 'red').length
  const yellow = insufficient.value.filter(s => s.riskLevel !== 'red').length
  return [
    { label: '全部', value: '', count: insufficient.value.length },
    { label: '红色', value: 'red', count: red },
    { label: '黄色', value: 'yellow', count: yellow }
  ]
})

const visibleStudents = computed(() =>
  insufficient.value.filter(s => {
    if (classFilter.value && s.className !== classFilter.value) return false
    if (riskKey.value === '') return true
    if (riskKey.value === 'red') return s.riskLevel === 'red'
    return s.riskLevel !== 'red'
  })
)

const heroStats = computed(() => [
  { label: '带班学生', value: numOr(monitor.value.totalStudents) },
  { label: '已有成绩', value: numOr(monitor.value.gradedStudents) },
  { label: '学分达标', value: numOr(monitor.value.creditSufficient) },
  { label: '存在挂科', value: numOr(monitor.value.creditInsufficient) }
])

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function pct(v) {
  return Math.max(0, Math.min(100, numOr(v))) + '%'
}

/** 已修 ÷ 应修。应修为 0 时给 0%，不显示 NaN。 */
function creditPct(s) {
  const a = numOr(s && s.attemptedCredits)
  const e = numOr(s && s.score)
  if (a <= 0) return '0%'
  return Math.max(0, Math.min(100, Math.round((e / a) * 100))) + '%'
}

function riskText(level) {
  return level === 'red' ? '高风险' : '需关注'
}

function riskTag(level) {
  return level === 'red' ? 'tag--danger' : 'tag--warning'
}

function toggleClass(name) {
  classFilter.value = classFilter.value === name ? '' : name
}

function goStudent(profileId) {
  if (!profileId) return
  go(`/pages/counselor/students?focus=${profileId}`)
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [m, list] = await Promise.all([
      counselorAPI.getCreditMonitor(userId).catch(e => { console.error('[credit] 汇总失败:', e); return null }),
      // size 给大：这一页要按风险等级本地分级，只取第一页会让红黄计数偏小。
      // 好在这个接口的 `total` 是真的（= 名单总人数），不是那个恒 0 的坑。
      counselorAPI.getCreditInsufficientStudents(userId, 1, 500).catch(e => { console.error('[credit] 明细失败:', e); return null })
    ])

    const mo = m && typeof m === 'object' ? m : {}
    monitor.value = mo
    classMonitors.value = (Array.isArray(mo.classMonitors) ? mo.classMonitors : [])
      // 有成绩的班排前面：0 人班排在前头会让人以为整个辅导员没数据
      .slice()
      .sort((a, b) => numOr(b.gradedStudentCount) - numOr(a.gradedStudentCount))

    const rows = list && Array.isArray(list.list) ? list.list : []
    insufficient.value = rows.slice().sort((a, b) => numOr(b.failCount) - numOr(a.failCount))
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.card {
  margin: 0 $page-padding;
  padding: $spacing-lg;
  @include card;
}

.section {
  margin-top: $spacing-xl;
}

.section--last {
  padding-bottom: $spacing-2xl;
}

.note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

/* 直接挂在 .section 下（不在卡片里）的说明文字要自己让出页面边距 */
.note--inset {
  margin-left: $page-padding;
  margin-right: $page-padding;
}

.note--box {
  margin: $spacing-md 0 0;
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

/* ---------- 比率 ---------- */
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
  line-height: 1.5;
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

/* ---------- 班级 ---------- */
.cls {
  padding: $spacing-md;
  border-radius: $radius-md;
}

.cls--divided {
  margin-top: $spacing-sm;
  border-top: 1rpx solid $color-divider;
  padding-top: $spacing-lg;
}

.cls--on {
  background: var(--color-primary-weak, #{$color-bg});
}

.cls__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.cls__name {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.cls__rate {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-sm;
  font-weight: 600;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.cls__track {
  height: 12rpx;
  margin-top: $spacing-xs;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.cls__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.cls__sub {
  display: flex;
  flex-wrap: wrap;
  margin-top: 6rpx;
}

.cls__s {
  margin-right: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

/* ---------- 已选筛选 ---------- */
.active-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 $page-padding $spacing-sm;
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-sm;
  background: var(--color-primary-weak, #{$color-bg});
}

.active-filter__text {
  font-size: $font-xs;
  color: var(--color-primary, #{$role-counselor});
}

.active-filter__clear {
  font-size: $font-xs;
  color: $color-text-secondary;
}

/* ---------- 学生 ---------- */
.stu-list {
  padding: 0 $page-padding;
}

.stu {
  margin-bottom: $spacing-sm;
  padding: $spacing-lg;
  @include card;
}

.stu__head {
  display: flex;
  align-items: center;
}

.stu__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.stu__class {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.stu__bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.stu__track {
  flex: 1;
  min-width: 0;
  height: 12rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.stu__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.stu__pct {
  flex-shrink: 0;
  width: 72rpx;
  margin-left: $spacing-sm;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.stu__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-sm;
}

.stu__credit {
  font-size: $font-xs;
  color: $color-text-secondary;
}

.stu__strong {
  color: $color-text;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stu__fail {
  font-size: $font-xs;
  color: $color-danger;
  font-variant-numeric: tabular-nums;
}
</style>
