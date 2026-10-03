<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="成绩申诉" />

    <role-hero
      :title="userName || '成绩申诉'"
      subtitle="对成绩有异议可提交申诉，待处理中可撤销"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <filter-bar v-model="statusKey" :options="statusOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!visible.length"
          :text="statusKey === '' ? '暂无申诉记录' : `没有${labelOfStatus(statusKey)}的申诉`"
          :hint="statusKey === ''
            ? '对某门课的成绩有疑问时，点下方按钮提交申诉'
            : '换个状态看看，或点「全部」查看所有申诉'"
          icon="doc"
        />

        <view v-else class="appeal-list">
          <view v-for="a in visible" :key="a.id" class="appeal-card" @tap="openDetail(a)">
            <view class="appeal-card__head">
              <text class="appeal-card__course">{{ scoreLabelOf(a) }}</text>
              <text class="tag" :class="'tag--' + tagTypeOf(a)">{{ statusTextOf(a) }}</text>
            </view>

            <view class="appeal-card__row">
              <text class="appeal-card__label">申诉原因</text>
              <text class="appeal-card__value">{{ reasonText(a.reason) }}</text>
            </view>
            <view class="appeal-card__row">
              <text class="appeal-card__label">提交时间</text>
              <text class="appeal-card__value">{{ formatted(a.createdAt) }}</text>
            </view>

            <view v-if="a.reply" class="appeal-card__reply">
              <text class="appeal-card__reply-label">教师回复</text>
              <text class="appeal-card__reply-text">{{ a.reply }}</text>
            </view>

            <!-- 只有待处理(0)能撤销。已通过/已驳回/已撤回都没有撤销接口支持，
                 按钮给出来就是死链接。 -->
            <view v-if="canWithdraw(a)" class="appeal-card__actions">
              <text class="appeal-card__withdraw" @tap.stop="confirmWithdraw(a)">撤销申诉</text>
            </view>
          </view>
        </view>
      </template>

      <view v-if="!loading" class="submit-bar">
        <view class="btn-primary" @tap="openForm">提交新申诉</view>
      </view>
    </view>

    <!-- 详情 -->
    <detail-sheet v-model="detailOpen" title="申诉详情">
      <view v-if="detail.id" class="detail">
        <view class="detail__row">
          <text class="detail__label">申诉成绩</text>
          <text class="detail__value">{{ scoreLabelOf(detail) }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">当前状态</text>
          <text class="tag" :class="'tag--' + tagTypeOf(detail)">{{ statusTextOf(detail) }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">申诉原因</text>
          <text class="detail__value">{{ reasonText(detail.reason) }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">提交时间</text>
          <text class="detail__value">{{ formatted(detail.createdAt) }}</text>
        </view>
        <view class="detail__block">
          <text class="detail__label">教师回复</text>
          <text class="detail__text">{{ detail.reply || '暂无回复' }}</text>
        </view>
      </view>
    </detail-sheet>

    <!-- 提交表单 -->
    <detail-sheet v-model="formOpen" title="提交申诉" height="80vh">
      <view class="form">
        <text class="form__label">申诉课程</text>
        <view class="chip-wrap">
          <view
            v-for="c in courseOptions"
            :key="c.courseId"
            class="chip"
            :class="{ 'chip--on': String(form.courseId) === String(c.courseId) }"
            @tap="form.courseId = c.courseId"
          >
            {{ c.courseName }}
          </view>
        </view>

        <text class="form__label">申诉原因</text>
        <view class="chip-wrap">
          <view
            v-for="r in reasons"
            :key="r.value"
            class="chip"
            :class="{ 'chip--on': form.reason === r.value }"
            @tap="form.reason = r.value"
          >
            {{ r.label }}
          </view>
        </view>

        <!-- 这里**刻意没有**「详细说明」输入框。
             ⚠️ 2026-09-30 起这条**不再是**"后端存不下"：score_appeals 已经加了
             description 列（db_backups/appeal_description_20260930_apply.sql），
             submitAppeal 也真的写它、并回读返回。PC 端两个页面的那个必填框从"填了等于没填"
             变成了"真的存进去"。
             小程序这边仍然不给这个框，是**产品选择**（小屏输长文体验差，且申诉在
             PC 端有完整入口），不是后端限制 —— 将来要加就发 `description` 这个 key。 -->
        <text class="form__note">说明：申诉仅记录所选课程与原因，如需补充材料请联系任课教师。</text>
      </view>

      <template #footer>
        <view
          class="btn-primary"
          :class="{ 'btn-primary--disabled': !canSubmit }"
          @tap="doSubmit"
        >
          {{ submitting ? '提交中…' : '确认提交' }}
        </view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { normalizeScoreRows, scoreSemesterLabel } from '@/utils/score-normalize.js'
import {
  APPEAL_STATUS_TEXT, APPEAL_STATUS_KEY, APPEAL_REASONS,
  STATUS_TAG_TYPE, appealReasonText, dictText
} from '@/constants/dict.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const rows = ref([])
const stats = ref({})
/** courses: courseId -> courseName，用来给申诉补课程名 */
const courseMap = ref({})
const courseOptions = ref([])
/** '' = 全部；其余是 '0'..'3' */
const statusKey = ref('')

const detailOpen = ref(false)
const detail = ref({})

const formOpen = ref(false)
const submitting = ref(false)
const form = ref({ courseId: '', reason: '' })

const reasons = APPEAL_REASONS

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

/**
 * 四个数直接取 `/students/appeals/{uid}/statistics`，后端按状态**各自 COUNT**。
 *
 * 2026-10-02（W5.8）：后端此前是 `rejected = total - pending - approved`，
 * 减法把**已撤回(3)** 折进了「已驳回」。已改成四态分别计数，
 * 现在 pending + approved + rejected + withdrawn == total。
 * 接口另回了 `withdrawn`，本页不展示（四个格子够了），状态筛选里能看到。
 */
const heroStats = computed(() => {
  const s = stats.value || {}
  return [
    { label: '申诉总数', value: numOr(s.total) },
    { label: '待处理', value: numOr(s.pending) },
    { label: '已通过', value: numOr(s.approved) },
    { label: '已驳回', value: numOr(s.rejected) }
  ]
})

const statusOptions = computed(() => {
  const counts = {}
  for (const a of rows.value) {
    const k = String(statusOf(a))
    counts[k] = (counts[k] || 0) + 1
  }
  const opts = [{ label: '全部', value: '', count: rows.value.length }]
  // 只放数据里真实出现过的状态，避免出现点进去必然为空的选项
  for (const k of [0, 1, 2, 3]) {
    if (counts[k]) {
      opts.push({ label: dictText(APPEAL_STATUS_TEXT, k), value: String(k), count: counts[k] })
    }
  }
  return opts
})

const visible = computed(() => {
  if (!statusKey.value) return rows.value
  return rows.value.filter(a => String(statusOf(a)) === String(statusKey.value))
})

const canSubmit = computed(() => Boolean(form.value.courseId && form.value.reason))

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function statusOf(a) {
  const n = Number(a && a.status)
  return Number.isFinite(n) ? n : 0
}

function statusTextOf(a) {
  return dictText(APPEAL_STATUS_TEXT, statusOf(a))
}

function tagTypeOf(a) {
  const key = APPEAL_STATUS_KEY[statusOf(a)]
  return STATUS_TAG_TYPE[key] || 'info'
}

function labelOfStatus(key) {
  return dictText(APPEAL_STATUS_TEXT, Number(key))
}

function reasonText(reason) {
  return appealReasonText(reason)
}

function formatted(v) {
  return formatDate(v, true)
}

/** `/appeals/{uid}/list` 只回 courseId，不回 courseName（见 appealToMap），
    所以课程名要靠本地映射补；补不到就退化成「课程 #id」，不显示空白。 */
function courseNameOf(a) {
  const id = a && a.courseId
  const name = id != null ? courseMap.value[String(id)] : ''
  return name || (id != null ? `课程 #${id}` : '未指定课程')
}

/**
 * 申诉指向的那条成绩：`高等数学 · 25秋 · 56.5分`。
 *
 * 第四十三轮 W9：后端 `appealToMap` 在 `score_id` 非空时带出 `scoreSemester` / `scoreYear` /
 * `scoreTotal` / `scoreGrade`。老行与课程级入口提交的行没有这些字段 —— 那就**退化成
 * `courseNameOf`**（只到课程级），不显示空白、也不显示 `null`。
 *
 * 学期标签走 `score-normalize.js` 的 `scoreSemesterLabel`，与成绩页同一套换算，
 * 不在本页另写一份（否则两处会分叉）。
 */
function scoreLabelOf(a) {
  const course = courseNameOf(a)
  if (!a) return course
  const sem = a.scoreSemester
  const total = a.scoreTotal

  const tail = []
  if (sem !== null && sem !== undefined && sem !== '') {
    tail.push(scoreSemesterLabel({ semester: sem, year: a.scoreYear }))
  }
  if (total !== null && total !== undefined) tail.push(`${total}分`)

  return tail.length ? `${course} · ${tail.join(' · ')}` : course
}

function canWithdraw(a) {
  return statusOf(a) === 0
}

function openDetail(a) {
  detail.value = a
  detailOpen.value = true
}

function openForm() {
  form.value = { courseId: courseOptions.value[0] ? courseOptions.value[0].courseId : '', reason: '' }
  formOpen.value = true
}

function confirmWithdraw(a) {
  uni.showModal({
    title: '撤销申诉',
    content: `确定撤销对「${courseNameOf(a)}」的申诉吗？撤销后状态变为已撤回。`,
    success: res => {
      if (res.confirm) doWithdraw(a)
    }
  })
}

async function doWithdraw(a) {
  try {
    const res = await studentAPI.withdrawAppeal(a.id, userId)
    // 撤销失败同样是 HTTP 200 + code 200，成功与否只能看载荷里的 success
    if (res && res.success === false) {
      uni.showToast({ title: res.message || '撤销失败', icon: 'none' })
      return
    }
    uni.showToast({ title: '已撤销', icon: 'success' })
    detailOpen.value = false
    await load()
  } catch (e) {
    console.error('[appeals] 撤销失败:', e)
    uni.showToast({ title: '撤销失败，请稍后重试', icon: 'none' })
  }
}

async function doSubmit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    // key 叫 studentId，值是 users.id（后端自己换成 profile id）
    const res = await studentAPI.submitAppeal({
      studentId: userId,
      courseId: form.value.courseId,
      reason: form.value.reason
    })
    // 提交失败是 HTTP 200 + code 200 + 载荷里带 error，必须显式判
    if (res && res.error) {
      uni.showToast({ title: String(res.error), icon: 'none' })
      return
    }
    uni.showToast({ title: '申诉已提交', icon: 'success' })
    formOpen.value = false
    await load()
  } catch (e) {
    console.error('[appeals] 提交失败:', e)
    uni.showToast({ title: '提交失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [list, statistics, scores] = await Promise.all([
      studentAPI.getAppeals(userId).catch(e => { console.error('[appeals] 列表加载失败:', e); return [] }),
      studentAPI.getAppealStatistics(userId).catch(e => { console.error('[appeals] 统计加载失败:', e); return {} }),
      // 成绩单只用来建 courseId→courseName 映射，挂了不影响申诉本身，单独兜底
      studentAPI.getScores(userId).catch(() => [])
    ])

    rows.value = Array.isArray(list) ? list : []
    stats.value = statistics && typeof statistics === 'object' ? statistics : {}

    const map = {}
    const seen = new Set()
    const options = []
    for (const s of normalizeScoreRows(scores)) {
      if (s.courseId == null) continue
      const key = String(s.courseId)
      if (s.courseName) map[key] = s.courseName
      if (!seen.has(key)) {
        seen.add(key)
        options.push({ courseId: s.courseId, courseName: s.courseName || `课程 #${s.courseId}` })
      }
    }
    courseMap.value = map
    courseOptions.value = options
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.appeal-list {
  padding: 0 $page-padding;
}

.appeal-card {
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  @include card;
}

.appeal-card__head {
  display: flex;
  align-items: center;
  padding-bottom: $spacing-md;
  border-bottom: 1rpx solid $color-divider;
}

.appeal-card__course {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.appeal-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-sm;
}

.appeal-card__label {
  font-size: $font-xs;
  color: $color-text-hint;
}

.appeal-card__value {
  font-size: $font-sm;
  color: $color-text;
}

.appeal-card__reply {
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.appeal-card__reply-label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.appeal-card__reply-text {
  display: block;
  margin-top: 4rpx;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.55;
}

.appeal-card__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.appeal-card__withdraw {  @include tap-area;
  padding: $spacing-xs $spacing-lg;
  border: 1rpx solid $color-danger;
  border-radius: $radius-pill;
  font-size: $font-sm;
  color: $color-danger;
}

/* ---------- 提交按钮 ---------- */
.submit-bar {
  padding: $spacing-lg $page-padding;
  padding-bottom: calc(#{$spacing-3xl} + env(safe-area-inset-bottom));
}

/* ---------- 详情 ---------- */
.detail {
  padding-bottom: $spacing-2xl;
}

.detail__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.detail__label {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.detail__value {
  font-size: $font-sm;
  color: $color-text;
}

.detail__block {
  padding: $spacing-md 0;
}

.detail__text {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

/* ---------- 表单 ---------- */
.form {
  padding-bottom: $spacing-2xl;
}

.form__label {
  display: block;
  margin: $spacing-lg 0 $spacing-sm;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.chip-wrap {
  display: flex;
  flex-wrap: wrap;
}

.chip {  @include tap-area;
  margin: 0 $spacing-sm $spacing-sm 0;
  padding: $spacing-xs $spacing-lg;
  border: 1rpx solid $color-border;
  border-radius: $radius-pill;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.chip--on {
  border-color: var(--color-primary, #{$role-student});
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-student});
  font-weight: 600;
}

.form__note {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}
</style>
