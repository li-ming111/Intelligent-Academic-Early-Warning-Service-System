<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="教师管理" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ teachers.length }}</text> 位教师
          </text>
          <text class="summary__hint">按授课门数从多到少排</text>
        </view>

        <empty-state
          v-if="!teachers.length"
          text="本院暂无教师"
          hint="教师档案里的学院为空时，不会出现在这里"
          icon="user"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(t, i) in teachers"
            :key="t.id"
            class="tch"
            :class="{ 'tch--divided': i > 0 }"
            @tap="openDetail(t)"
          >
            <view class="tch__head">
              <text class="tch__name">{{ t.name || `教师 ${t.id}` }}</text>
              <text class="tag tag--info">{{ t.title || '职称未填' }}</text>
            </view>
            <text class="tch__courses">{{ t.courses || '暂无授课记录' }}</text>
            <view class="tch__nums">
              <view class="num">
                <text class="num__v">{{ t.courseCount || 0 }}</text>
                <text class="num__k">课程</text>
              </view>
              <view class="num">
                <text class="num__v" :class="{ 'num__v--muted': !Number(t.studentCount) }">{{ t.studentCount || 0 }}</text>
                <text class="num__k">授课学生</text>
              </view>
              <view class="num">
                <text class="num__v" :class="{ 'num__v--muted': t.avgScore === null || t.avgScore === undefined }">
                  {{ scoreText(t.avgScore) }}
                </text>
                <text class="num__k">成绩均分</text>
              </view>
            </view>
          </view>
        </view>

        <text class="note">
          「授课学生」是**去重后的人数**（这门课那个学生算一次），不是成绩条数。
          库里 5 位教师都带同一个班，所以每人的「授课学生」都是 24 —— 看着像复制粘贴，
          其实是同一个班的学生数。
        </text>
        <text class="note">
          「成绩均分」为「待录入」表示这位教师**名下一份成绩都没有**（电子工程学院的陈志强就是），
          不是均分为 0。职称与研究方向在库里可能是空的，页面按「未填」显示，不编默认值。
        </text>
      </template>
    </view>

    <!-- ============ 教师详情 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" height="82vh">
      <view v-if="detail" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">姓名</text>
            <text class="facts__v">{{ detail.name || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">职称</text>
            <text class="facts__v">{{ detail.title || '未填' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">研究方向</text>
            <text class="facts__v">{{ detail.researchAreas || '未填' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">授课门数</text>
            <text class="facts__v">{{ detail.courseCount || 0 }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">成绩记录条数</text>
            <text class="facts__v">{{ detail.studentCount || 0 }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">成绩均分</text>
            <text class="facts__v">{{ avgText() }}</text>
          </view>
        </view>

        <text class="note note--in">
          这里显示的「成绩记录条数」在接口里叫 `studentCount`，与列表页那个同名字段**不是一回事**：
          列表页是去重学生数（24），这里是成绩条数（71 = 3 门课 × 24 人上下）。
          同一个键名两种含义，是接口层的历史包袱，页面上按各自实际的口径起名。
        </text>

        <section-title plain title="成绩明细" :extra="`${scores.length} 条`" />
        <view v-if="!scores.length" class="empty-tip">这位教师名下没有成绩记录。</view>
        <view v-else class="scores">
          <view v-for="(r, i) in scores" :key="i" class="srow">
            <view class="srow__left">
              <text class="srow__name">{{ r.studentName || '未知学生' }} · {{ r.courseName || '未知课程' }}</text>
              <text class="srow__meta">
                {{ r.className || '未分班' }} · 第 {{ numOrDash(r.semester) }} 学期 ·
                平时 {{ numOrDash(r.regularScore) }} / 期末 {{ numOrDash(r.finalScore) }}
              </text>
            </view>
            <view class="srow__right">
              <text class="srow__total" :class="`srow__total--${scoreClass(r)}`">{{ scoreText(r) }}</text>
            </view>
          </view>
        </view>

        <text class="note note--in">
          这一层的成绩行比成绩查询页**少两列**：总评字段叫 `score` 而不是 `totalScore`，
          而且没有绩点与等级。所以这里只画总评一列 —— 不是漏了，是接口没给。
        </text>
        <text class="note note--in">
          上面「成绩均分」是看**有没有成绩行**得出的：没有成绩时列表接口给 `null`、
          详情接口给 `0`，同一个字段两种"空"。若直接信那个 0，就会把"这位老师还没录过成绩"
          讲成"均分 0 分"。
        </text>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { scoreText, scoreClass } from '@/utils/score.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const loading = ref(true)
const teachers = ref([])

const sheetOpen = ref(false)
const detail = ref(null)

const scores = computed(() => (detail.value && Array.isArray(detail.value.scores) ? detail.value.scores : []))
const sheetTitle = computed(() => (detail.value && detail.value.name) || '教师详情')

/**
 * 详情里的成绩均分。
 * ⚠️ 没有成绩时**列表接口给 `null`、详情接口给 `0`** —— 同一个字段两种"空"。
 * 所以这里不看那个值，改看"有没有成绩行"：一条都没有就是"待录入"，
 * 否则会把 0 分当成"这位老师均分 0 分"讲出去。
 */
function avgText() {
  if (!scores.value.length) return '待录入'
  return scoreText(detail.value.avgScore)
}

function numOrDash(v) {
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
    const list = await collegeAdminAPI.getTeachers(userId)
    const rows = Array.isArray(list) ? list : []
    teachers.value = rows.slice().sort((a, b) => {
      const ca = Number(a.courseCount) || 0
      const cb = Number(b.courseCount) || 0
      if (ca !== cb) return cb - ca
      return Number(b.studentCount || 0) - Number(a.studentCount || 0)
    })
  } catch (e) {
    console.error('[college-admin teachers] 教师失败:', e)
    teachers.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(t) {
  detail.value = t
  sheetOpen.value = true
  try {
    const d = await collegeAdminAPI.getTeacherDetail(userId, t.id)
    if (d && typeof d === 'object' && detail.value && detail.value.id === t.id) {
      detail.value = d
    }
  } catch (e) {
    console.error('[college-admin teachers] 详情失败:', e)
    toast((e && e.message) || '详情加载失败')
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.summary {
  display: flex;
  align-items: baseline;
  padding: 0 $page-padding $spacing-sm;
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

/* ---------- 教师行 ---------- */
.tch {
  padding: $spacing-lg;
}

.tch--divided {
  border-top: 1rpx solid $color-divider;
}

.tch:active {
  background: $color-bg;
}

.tch__head {
  display: flex;
  align-items: center;
}

.tch__name {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.tch__courses {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary);
  line-height: 1.5;
  @include ellipsis-multi(2);
}

.tch__nums {
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
  width: 240rpx;
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

.srow__total {
  flex-shrink: 0;
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

.srow__total--good,
.srow__total--excellent {
  color: $color-success;
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
</style>
