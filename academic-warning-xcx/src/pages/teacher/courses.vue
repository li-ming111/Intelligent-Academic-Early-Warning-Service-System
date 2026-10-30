<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="课程管理" :show-back="false" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!courses.length"
          text="暂无授课课程"
          hint="课程由管理员分配给你，分配后这里会出现"
          icon="book"
        />

        <template v-else>
          <view class="hint-card">
            <text class="hint-card__text">这里只显示分配给你的课程。课程类型与开课单位由教学计划决定，教师端只可修改课程名称、学分与简介。</text>
          </view>

          <view v-for="c in courses" :key="c.id" class="card">
            <view class="c__head">
              <view class="c__title-wrap">
                <text class="c__name">{{ c.name }}</text>
                <text class="c__code">{{ c.code || '无课程号' }} · {{ c.type || '未标类型' }}</text>
              </view>
            <view class="c__head-btns">
              <view class="c__edit c__edit--ghost" @tap="openRecommend(c)">推荐</view>
              <view class="c__edit" @tap="openEdit(c)">编辑</view>
            </view>
            </view>

            <view class="c__stats">
              <view class="c__stat">
                <text class="c__stat-num">{{ numOr(anaOf(c.id).totalStudents) }}</text>
                <text class="c__stat-label">修读人数</text>
              </view>
              <view class="c__stat">
                <text class="c__stat-num">{{ dec(anaOf(c.id).averageScore) }}</text>
                <text class="c__stat-label">平均分</text>
              </view>
              <view class="c__stat">
                <text class="c__stat-num">{{ pct(anaOf(c.id).passRate) }}</text>
                <text class="c__stat-label">及格率</text>
              </view>
              <view class="c__stat">
                <text class="c__stat-num c__stat-num--danger">{{ numOr(anaOf(c.id).failedCount) }}</text>
                <text class="c__stat-label">挂科</text>
              </view>
            </view>

            <view class="c__foot">
              <text class="c__credits">学分 {{ creditsText(c.credits) }}</text>
              <view class="c__link" @tap="goScores(c.id)">
                <text class="c__link-text">查看成绩</text>
                <app-icon name="chevron" :size="28" color="#aeaeb2" />
              </view>
            </view>

            <text v-if="c.description" class="c__desc">{{ c.description }}</text>
          </view>
        </template>
      </template>
    </view>

    <detail-sheet v-model="editOpen" title="编辑课程" height="70vh">
      <view v-if="editing" class="form">
        <view class="form__facts">
          <view class="form__fact">
            <text class="form__fact-label">课程号</text>
            <text class="form__fact-value">{{ editing.code || '--' }}</text>
          </view>
          <view class="form__fact">
            <text class="form__fact-label">类型</text>
            <text class="form__fact-value">{{ editing.type || '--' }}</text>
          </view>
        </view>

        <text class="form__label">课程名称</text>
        <input v-model="form.name" class="form__input" placeholder="如：高等数学" placeholder-class="form__placeholder" />

        <text class="form__label">学分</text>
        <input v-model="form.credits" class="form__input" type="digit" placeholder="如：4" placeholder-class="form__placeholder" />
        <text class="form__tip">学分会影响学生已获学分与毕业进度的计算，请与教学计划一致。</text>

        <text class="form__label">课程简介</text>
        <textarea
          v-model="form.description"
          class="form__textarea"
          placeholder="选填，课堂内容与考核方式的简要说明"
          placeholder-class="form__placeholder"
          :maxlength="200"
          auto-height
        />
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !canSave }" @tap="submit">
          {{ saving ? '保存中…' : '保存修改' }}
        </view>
      </template>
    </detail-sheet>

    <!-- 推荐课程 -->
    <detail-sheet v-model="recOpen" title="推荐课程" height="76vh">
      <view v-if="recCourse" class="form">
        <view class="form__facts">
          <view class="form__fact">
            <text class="form__fact-label">课程</text>
            <text class="form__fact-value">{{ recCourse.name }}</text>
          </view>
          <view class="form__fact">
            <text class="form__fact-label">学分</text>
            <text class="form__fact-value">{{ creditsText(recCourse.credits) }}</text>
          </view>
        </view>

        <text class="form__label">推荐对象</text>
        <picker mode="selector" :range="recClassLabels" :value="recClassIndex" @change="onRecClassChange">
          <view class="form__picker">
            <text class="form__picked">{{ recClassLabels[recClassIndex] }}</text>
            <app-icon name="chevron" :size="26" color="#aeaeb2" />
          </view>
        </picker>
        <text class="form__tip">指定班级时只通知该班学生；不指定则推荐给你带的所有班级。</text>

        <text class="form__label">推荐理由</text>
        <textarea
          v-model="recForm.reason"
          class="form__textarea"
          placeholder="例如：下学期开设的进阶课与本课衔接紧密，建议学有余力的同学修读"
          placeholder-class="form__placeholder"
          :maxlength="500"
          auto-height
        />
        <text class="form__tip">必填，500 字以内，学生端会看到这段话。</text>

        <view v-if="recHistory.length" class="rec-history">
          <text class="rec-history__title">我的推荐记录</text>
          <view v-for="h in recHistory" :key="h.id" class="rec-history__row">
            <view class="rec-history__main">
              <text class="rec-history__course">{{ h.courseName || h.course_name || '--' }}</text>
              <text class="rec-history__reason">{{ h.reason || '（无理由）' }}</text>
            </view>
            <text class="rec-history__status">{{ recStatusText(h) }}</text>
          </view>
        </view>
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !canRecommend }" @tap="submitRecommend">
          {{ recSending ? '提交中…' : '提交推荐' }}
        </view>
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
import { toNumber } from '@/utils/score.js'
import { go } from '@/utils/navigate.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const loading = ref(true)
const courses = ref([])
/** courseId → 分析结果。课程数少（一位教师两三门），一次全拉回来即可。 */
const analyses = ref({})

const editOpen = ref(false)
const editing = ref(null)
const form = ref({ name: '', credits: '', description: '' })
const saving = ref(false)

// —— 课程推荐 ——
const recOpen = ref(false)
const recCourse = ref(null)
const recForm = ref({ reason: '' })
const recClassIndex = ref(0)
const recSending = ref(false)
const recClasses = ref([])
const recHistory = ref([])

const canRecommend = computed(() => !!recCourse.value && !!String(recForm.value.reason).trim() && !recSending.value)

/** 第 0 项 = 不指定班级（推荐给全部所带班级）。 */
const recClassLabels = computed(() => [
  '不指定（全部所带班级）',
  ...recClasses.value.map(c => c.name || '未命名班级')
])

const canSave = computed(() => !!editing.value && !!String(form.value.name).trim() && validCredits(form.value.credits))

function validCredits(v) {
  const n = Number(v)
  return v !== '' && Number.isFinite(n) && n > 0 && n <= 30
}

function numOr(v) {
  return Number.isFinite(Number(v)) ? Number(v) : 0
}

function dec(v) {
  const n = toNumber(v)
  return n === null ? '--' : n.toFixed(1)
}

/** 后端给 0~1 小数 */
function pct(v) {
  const n = toNumber(v)
  return n === null ? '--' : `${Math.round(n * 100)}%`
}

function creditsText(v) {
  const n = toNumber(v)
  if (n === null) return '--'
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

/** 分析还没回来时给空对象，页面各处都已经用 numOr/dec 兜了 '--'。 */
function anaOf(id) {
  return analyses.value[id] || {}
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await teacherAPI.getCourses(userId).catch((e) => {
      console.error('[teacher courses] 课程失败:', e)
      return []
    })
    courses.value = Array.isArray(list) ? list : []

    // 逐门课拉分析。每门课各自 catch：一门课的分析挂了不该让整页空掉。
    const pairs = await Promise.all(courses.value.map(async (c) => {
      const a = await teacherAPI.getAnalysis(c.id).catch((e) => {
        console.warn('[teacher courses] 分析失败:', c.id, e && e.message)
        return null
      })
      return [c.id, a && typeof a === 'object' ? a : {}]
    }))
    analyses.value = Object.fromEntries(pairs)
  } finally {
    loading.value = false
  }
}

function openEdit(c) {
  editing.value = c
  form.value = {
    name: c.name || '',
    credits: c.credits === null || c.credits === undefined ? '' : String(c.credits),
    description: c.description || ''
  }
  editOpen.value = true
}

/**
 * 只发可改的三个字段。
 *
 * 不把整行回传：`updateCourse` 走的是 MyBatis-Plus 的按 id 更新，
 * 整行回传会把 `createdAt` 一并写回去；而只发这三项时其余字段为 null、
 * 被 MP 忽略，`teacherId`/`code`/`type` 都保持原值。
 */
async function submit() {
  if (!canSave.value || saving.value) return
  saving.value = true
  try {
    await teacherAPI.updateCourse(editing.value.id, {
      name: String(form.value.name).trim(),
      credits: Number(form.value.credits),
      description: form.value.description
    })
    uni.showToast({ title: '课程已更新', icon: 'success' })
    editOpen.value = false
    await load()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

function goScores(courseId) {
  go(`/pages/teacher/scores?courseId=${courseId}`)
}

// —— 课程推荐 ——

async function openRecommend(c) {
  recCourse.value = c
  recForm.value = { reason: '' }
  recClassIndex.value = 0
  recOpen.value = true
  // 班级和历史并行拉，失败不拦着填表（提交时班级列表空 = 只剩「不指定」一项）
  teacherAPI.getMyClasses(userId).then((list) => {
    recClasses.value = Array.isArray(list) ? list : []
  }).catch(() => { recClasses.value = [] })
  teacherAPI.listMyRecommendations().then((list) => {
    recHistory.value = Array.isArray(list) ? list : []
  }).catch(() => { recHistory.value = [] })
}

function onRecClassChange(e) {
  recClassIndex.value = Number(e.detail.value)
}

function recStatusText(h) {
  const s = h.status
  if (s === 1 || s === '1' || s === '已推荐') return '已推荐'
  if (s === 2 || s === '2' || s === '已处理') return '已处理'
  return s !== null && s !== undefined ? String(s) : '--'
}

async function submitRecommend() {
  if (!canRecommend.value) return
  recSending.value = true
  try {
    const data = {
      courseId: recCourse.value.id,
      reason: String(recForm.value.reason).trim()
    }
    if (recClassIndex.value > 0) {
      const cls = recClasses.value[recClassIndex.value - 1]
      if (cls) data.targetClassId = cls.id
    }
    await teacherAPI.recommendCourse(data)
    uni.showToast({ title: '推荐成功', icon: 'success' })
    recOpen.value = false
    // 刷新历史，重新打开时能看到这条
    teacherAPI.listMyRecommendations().then((list) => {
      recHistory.value = Array.isArray(list) ? list : []
    }).catch(() => {})
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '推荐失败', icon: 'none' })
  } finally {
    recSending.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.hint-card {
  margin: 0 $page-padding $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
}

.hint-card__text {
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
}

.card {
  padding: $spacing-lg;
  margin: 0 $page-padding $spacing-md;
  @include card;
}

/* ---------- 课程卡 ---------- */
.c__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.c__title-wrap {
  flex: 1;
  min-width: 0;
}

.c__name {
  display: block;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.c__code {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* 本体保持原来的 38×22 小胶囊，由伪元素撑命中区。
   为什么不用 min-height 撑本体：圆角是 $radius-pill，
   盒子一旦到 44 高就变成正圆（见 uni.scss 里 tap-area 的说明）。
   横向要给 8rpx 外扩：两个字加内边距只有 38px 宽，只撑高仍不足 44。
   卡片右上角左右都是空白，外扩不会抢到别的控件的点击。 */
.c__edit {
  @include tap-area($tap-min, 8rpx);
  flex-shrink: 0;
  margin-left: $spacing-sm;
  padding: 6rpx $spacing-md;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
}

.c__head-btns {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* 次级动作用弱底：编辑是主操作保持实心，推荐不与它抢 */
.c__edit--ghost {
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary);
}

.form__picker {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  margin-top: $spacing-sm;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.form__picked {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  color: $color-text;
}

/* ---------- 推荐历史 ---------- */
.rec-history {
  margin-top: $spacing-xl;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.rec-history__title {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.rec-history__row {
  display: flex;
  align-items: flex-start;
  margin-top: $spacing-md;
}

.rec-history__main {
  flex: 1;
  min-width: 0;
}

.rec-history__course {
  display: block;
  font-size: $font-sm;
  color: $color-text;
  font-weight: 500;
}

.rec-history__reason {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
  @include ellipsis-multi(2);
}

.rec-history__status {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary);
}

.c__stats {
  display: flex;
  margin-top: $spacing-lg;
  padding: $spacing-md 0;
  border-top: 1rpx solid $color-divider;
  border-bottom: 1rpx solid $color-divider;
}

.c__stat {
  flex: 1;
  min-width: 0;
}

.c__stat-num {
  display: block;
  font-size: $font-lg;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.c__stat-num--danger {
  color: $color-danger;
}

.c__stat-label {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.c__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-md;
}

.c__credits {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.c__link {
  @include tap-area;
  display: flex;
  align-items: center;
}

.c__link-text {
  margin-right: 4rpx;
  font-size: $font-sm;
  color: var(--color-primary);
}

.c__desc {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

/* ---------- 表单 ---------- */
.form {
  padding: 0 $page-padding $spacing-2xl;
}

.form__facts {
  display: flex;
  padding-bottom: $spacing-md;
  border-bottom: 1rpx solid $color-divider;
}

.form__fact {
  flex: 1;
  min-width: 0;
}

.form__fact-label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__fact-value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-sm;
  color: $color-text;
}

.form__label {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-sm;
  color: $color-text;
}

.form__input {
  width: 100%;
  height: $tap-min;
  margin-top: $spacing-sm;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-md;
  color: $color-text;
}

.form__textarea {
  width: 100%;
  min-height: 160rpx;
  margin-top: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.form__placeholder {
  color: $color-text-hint;
}

.form__tip {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}
</style>
