<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="排名与班级" />

    <role-hero
      :title="me.name || userName || '排名与班级'"
      :subtitle="me.className ? `${me.className} · ${me.studentNo || ''}` : '班级与专业排名'"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" mode="card" />

      <template v-else>
        <!-- 本人摘要（/class-members/{uid} 实际返回的就是这个） -->
        <view class="card">
          <section-title title="我的位次" extra="按已评学分加权 GPA" />
          <view class="me-row">
            <view class="me-cell">
              <text class="me-cell__label">班级排名</text>
              <text class="me-cell__value">{{ me.rankInClass || EMPTY }}</text>
            </view>
            <view class="me-cell">
              <text class="me-cell__label">班级人数</text>
              <text class="me-cell__value">{{ numOr(me.totalStudents) }}</text>
            </view>
            <view class="me-cell">
              <text class="me-cell__label">我的 GPA</text>
              <text class="me-cell__value me-cell__value--primary">{{ myGpaText }}</text>
            </view>
          </view>
          <view class="me-note">{{ posVerdict }}</view>
        </view>

        <!-- 榜单 -->
        <view class="card card--flush">
          <view class="list-head">
            <section-title title="班级成绩榜" :extra="`共 ${rows.length} 人`" />
            <view class="list-head__tabs">
              <text
                v-for="t in tabs"
                :key="t.value"
                class="list-head__tab"
                :class="{ 'list-head__tab--on': tab === t.value }"
                @tap="tab = t.value"
              >{{ t.label }}</text>
            </view>
          </view>

          <view v-if="tab === 'major' && sameScope" class="same-scope">
            <text class="same-scope__text">{{ sameScopeText }}</text>
          </view>

          <empty-state
            v-if="!ranked.length"
            text="暂无排名数据"
            hint="班级排名需要本班同学都有已评成绩，成绩录入后自动生成"
            icon="chart"
            :top="60"
          />

          <view v-else class="rank-list">
            <view
              v-for="r in ranked"
              :key="r.profileId"
              class="rank-item"
              :class="{ 'rank-item--me': isMe(r) }"
            >
              <view class="rank-item__no" :class="rankNoClass(r.rank)">
                <text>{{ r.rank }}</text>
              </view>
              <view class="rank-item__info">
                <view class="rank-item__name-row">
                  <text class="rank-item__name">{{ r.studentName || '未命名' }}</text>
                  <text v-if="isMe(r)" class="rank-item__badge">我</text>
                </view>
                <text class="rank-item__no-text">{{ r.studentNo || EMPTY }}</text>
              </view>
              <view class="rank-item__score">
                <text class="rank-item__gpa">{{ numFixed(r.gpa, 2) }}</text>
                <text class="rank-item__credits">{{ numOr(r.gradedCredits) }} 学分</text>
              </view>
            </view>
          </view>
        </view>

        <view class="foot-note">
          GPA 为已评学分加权平均；仅统计已出分课程，未录入的成绩不计入（也不会按 0 分算）。
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

const { userId, userName } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
/** /class-members/{uid} —— 本人的班级摘要 */
const me = ref({})
/** /class-ranking/{uid}.list —— 全班同学 */
const members = ref([])
/** 榜上我的 profileId，用来高亮自己 */
const myProfileId = ref(null)
/** 'class' = 本班；'major' = 本专业 */
const tab = ref('class')
const majorList = ref([])

const EMPTY = '—'

const tabs = [
  { label: '本班', value: 'class' },
  { label: '本专业', value: 'major' }
]

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

/** 原始顺序即后端的排名顺序（list[0] 是第 1 名），这里只加一个显式名次字段。 */
function withRank(list) {
  return (Array.isArray(list) ? list : []).map((r, i) => ({ ...r, rank: i + 1 }))
}

const classRanked = computed(() => withRank(members.value))
const majorRanked = computed(() => withRank(majorList.value))

const ranked = computed(() => (tab.value === 'major' ? majorRanked.value : classRanked.value))
const rows = computed(() => ranked.value)

const myGpaText = computed(() => {
  const row = classRanked.value.find(r => isMe(r))
  return row ? numFixed(row.gpa, 2) : EMPTY
})

const myGradedCredits = computed(() => {
  const row = classRanked.value.find(r => isMe(r))
  return row ? numOr(row.gradedCredits) : EMPTY
})

const heroStats = computed(() => [
  { label: '班级排名', value: me.value.rank || EMPTY },
  { label: '班级人数', value: numOr(me.value.totalStudents) },
  { label: '我的 GPA', value: myGpaText.value },
  { label: '已评学分', value: myGradedCredits.value }
])

/**
 * 专业榜与本班榜是否为同一批人。
 *
 * 这是真实情况而非缺陷：库里 major_id=2（计算机科学与技术）**只有 1 个班**
 * （计科2306班，24 人），专业榜的 SQL 是 `WHERE sp.major_id = ?`、班级榜是
 * `WHERE sp.class_id = ?`，两个查询本身不同，只是当前数据下结果重合。
 * 不解释的话，看到"两个 tab 一模一样"会以为是写错了。
 */
const sameScope = computed(() => {
  if (!members.value.length || !majorList.value.length) return false
  if (members.value.length !== majorList.value.length) return false
  const a = members.value.map(r => String(r.profileId)).sort().join(',')
  const b = majorList.value.map(r => String(r.profileId)).sort().join(',')
  return a === b
})

const sameScopeText = computed(() =>
  `本专业当前仅有 1 个班级（${me.value.className || '本班'}），共 ${majorList.value.length} 人，故专业榜与班级榜一致。`
)

const posVerdict = computed(() => {
  const r = Number(me.value.rank)
  const t = Number(me.value.totalStudents)
  if (!Number.isFinite(r) || !Number.isFinite(t) || t <= 0) return '数据不足，暂无法定位'
  const pct = (r / t) * 100
  if (pct <= 25) return `位列班级前 25%（第 ${r} / ${t} 名）`
  if (pct <= 50) return `位列班级中上（第 ${r} / ${t} 名）`
  if (pct <= 75) return `位列班级中下（第 ${r} / ${t} 名），建议加强薄弱科目`
  return `位列班级后 25%（第 ${r} / ${t} 名），建议尽快制定补习计划`
})

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function numFixed(v, d = 2) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(d) : EMPTY
}

/** 后端列表里没有"是不是我"，靠 profileId 比对。取不到就退化成按姓名比。 */
function isMe(r) {
  if (myProfileId.value != null && r.profileId != null) {
    return String(r.profileId) === String(myProfileId.value)
  }
  return Boolean(me.value.name) && r.studentName === me.value.name
}

function rankNoClass(rank) {
  if (rank === 1) return 'rank-item__no--gold'
  if (rank === 2) return 'rank-item__no--silver'
  if (rank === 3) return 'rank-item__no--bronze'
  return ''
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    /**
     * 两个接口的路径参数都传 **users.id**：
     *  - `/class-members/{uid}` 名字像"同学名单"，实现里其实是 `return getClassInfo(uid)`，
     *    给的是**本人**的班级摘要；
     *  - `/class-ranking/{uid}.list` 才是全班同学（含 gpa / gradedCredits）。
     * 传 profile id 会静默返回 {} / 空数组。
     */
    const [meData, classData, majorData, profile] = await Promise.all([
      studentAPI.getClassMembers(userId).catch(e => { console.error('[ranking] 班级摘要失败:', e); return null }),
      studentAPI.getClassRanking(userId).catch(e => { console.error('[ranking] 班级榜失败:', e); return null }),
      studentAPI.getBenchmarkMajorRanking(userId).catch(e => { console.error('[ranking] 专业榜失败:', e); return null }),
      studentAPI.getStudentByUserId(userId).catch(() => null)
    ])

    me.value = meData && typeof meData === 'object' ? meData : {}
    members.value = classData && Array.isArray(classData.list) ? classData.list : []
    majorList.value = majorData && Array.isArray(majorData.list) ? majorData.list : []
    myProfileId.value = profile && profile.id != null ? profile.id : null

    // 专业榜是超集：本班同学必然也在本专业榜里，若为空则退回班级榜，不留空 tab
    if (!majorList.value.length && members.value.length) {
      majorList.value = members.value
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

/* ---------- 我的位次 ---------- */
.me-row {
  display: flex;
}

.me-cell {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.me-cell__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.me-cell__value {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xl;
  font-weight: 700;
  line-height: 1.2;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.me-cell__value--primary {
  color: var(--color-primary);
}

.me-note {
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

/* ---------- 榜单 ---------- */
.list-head {
  padding: $spacing-xl $spacing-xl 0;
}

.list-head__tabs {
  display: flex;
  margin-top: $spacing-md;
  padding: 4rpx;
  border-radius: $radius-pill;
  background: $color-bg;
}

.list-head__tab {
  flex: 1;
  height: $tap-min;
  border-radius: $radius-pill;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.list-head__tab--on {
  background: $color-card;
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: $shadow-card;
}

.rank-list {
  padding: 0 $spacing-xl $spacing-lg;
}

/* 专业榜与班级榜重合时的说明条 */
.same-scope {
  margin: 0 $spacing-xl;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.same-scope__text {
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.55;
}

.rank-item {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.rank-item:last-child {
  border-bottom: none;
}

/* 自己那一行加浅底色，扫一眼就能定位 */
.rank-item--me {
  margin: 0 (-$spacing-md);
  padding-left: $spacing-md;
  padding-right: $spacing-md;
  border-radius: $radius-md;
  background: var(--color-primary-weak);
}

.rank-item__no {
  width: 52rpx;
  height: 52rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: $color-bg;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
  @include flex-center;
}

.rank-item__no--gold {
  background: #fef3c7;
  color: #b45309;
}

.rank-item__no--silver {
  background: #f1f5f9;
  color: #475569;
}

.rank-item__no--bronze {
  background: #ffedd5;
  color: #c2410c;
}

.rank-item__info {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
}

.rank-item__name-row {
  display: flex;
  align-items: center;
}

.rank-item__name {
  font-size: $font-base;
  color: $color-text;
  @include ellipsis;
}

.rank-item__badge {
  flex-shrink: 0;
  margin-left: $spacing-xs;
  padding: 0 10rpx;
  border-radius: $radius-sm;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
  line-height: 1.7;
}

.rank-item__no-text {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.rank-item__score {
  flex-shrink: 0;
  text-align: right;
}

.rank-item__gpa {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.rank-item__credits {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.foot-note {
  padding: 0 $spacing-xs;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}
</style>
