<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="帮扶评价" />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 已提交的评价 -->
        <template v-if="rows.length">
          <view v-for="e in rows" :key="e.id" class="card">
            <view class="fb-head">
              <text class="fb-head__title">{{ planTitleOf(e) }}</text>
              <text class="tag" :class="e.score >= 4 ? 'tag--success' : 'tag--warning'">
                {{ ratingText(e.score) }}
              </text>
            </view>
            <view class="fb-stars">
              <text
                v-for="i in 5"
                :key="i"
                class="fb-stars__item"
                :class="{ 'fb-stars__item--on': i <= Number(e.score) }"
              >★</text>
              <text class="fb-stars__num">{{ numOr(e.score) }} / 5</text>
            </view>
            <text v-if="e.content" class="fb-content">{{ e.content }}</text>
            <view class="fb-foot">
              <text class="fb-foot__time">{{ formatted(e.createdAt) }}</text>
            </view>
          </view>
        </template>

        <!-- 空态：这是当前库里的真实情况（/evaluations/{uid}/list 返回 []），
             不要用假数据把它填满 -->
        <empty-state
          v-else
          text="还没有提交过帮扶评价"
          hint="辅导员为你制定帮扶计划并推进到一定阶段后，可以在这里反馈效果，帮助老师调整方案"
          icon="edit"
        />

        <!-- 可评价的计划 -->
        <view v-if="evaluatable.length" class="card">
          <section-title
            title="帮扶计划评价"
            :extra="`已评价 ${evaluatedPlanIds.size} / ${evaluatable.length}`"
          />
          <view
            v-for="p in evaluatable"
            :key="p.id"
            class="pending-item"
            @tap="openForm(p)"
          >
            <view class="pending-item__info">
              <text class="pending-item__title">{{ p.title }}</text>
              <text class="pending-item__meta">进度 {{ numOr(p.progress) }}% · {{ p.statusText }}</text>
            </view>
            <text
              class="pending-item__action"
              :class="{ 'pending-item__action--done': evaluatedPlanIds.has(String(p.id)) }"
            >
              {{ evaluatedPlanIds.has(String(p.id)) ? '已评价' : '去评价' }}
            </text>
          </view>
          <text class="note">「已评价」的记录再次提交时为修改，不会重复生成评价。</text>
        </view>
      </template>
    </view>

    <!-- 评价表单 -->
    <detail-sheet v-model="formOpen" title="帮扶评价" height="76vh">
      <view class="form">
        <text class="form__plan">{{ current.title || '帮扶计划' }}</text>

        <text class="form__label">整体评分</text>
        <view class="form__stars">
          <text
            v-for="i in 5"
            :key="i"
            class="form__star"
            :class="{ 'form__star--on': i <= form.score }"
            @tap="form.score = i"
          >★</text>
          <text class="form__star-text">{{ form.score }} 分</text>
        </view>

        <text class="form__label">评价内容</text>
        <textarea
          v-model="form.content"
          class="form__textarea"
          placeholder="说说帮扶计划对你的帮助，以及希望老师调整的地方"
          placeholder-class="form__placeholder"
          :maxlength="500"
          auto-height
        />
        <text class="form__count">{{ form.content.length }} / 500</text>
      </view>

      <template #footer>
        <view
          class="btn-primary"
          :class="{ 'btn-primary--disabled': !canSubmit }"
          @tap="doSubmit"
        >
          {{ submitting ? '提交中…' : '提交评价' }}
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

const { userId } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const rows = ref([])
const plans = ref([])
/** 学生档案 id（student_profile.id）。**不是** users.id —— 见 load() 里的说明。 */
const profileId = ref('')

const formOpen = ref(false)
const submitting = ref(false)
const current = ref({})
const form = ref({ score: 5, content: '' })

const canSubmit = computed(() => Boolean(form.value.score) && Boolean(current.value.id))

/**
 * 已评价过的计划 id 集合。
 *
 * 评价行（`/students/evaluations/{sid}/list`）**只回 `planId`、不回计划名**，
 * 所以这个集合同时也是 `planTitleOf` 做映射的依据。
 */
const evaluatedPlanIds = computed(() => new Set(rows.value.map(e => String(e.planId))))

/**
 * 可以评价的计划：进度 > 0 的。
 *
 * ⚠️ 2026-10-02（W5.10）更正了这里的旧注释。旧注释说「后端没有'是否已评价'的标记，
 * 无法去重，接受重复提交，后端也不拦」—— **后半句是错的**：
 * `StudentServiceImpl.submitEvaluation` 对同一个 planId 是**先查后更**，
 * 已有评价时复用那一行并返回「该计划已有评价，本次为更新」，库里不会堆重复行。
 * 真正缺的是**前端没把状态显示出来** —— 提交过的人看不出自己提交过。
 * 所以这里不再"过滤掉已评价的"，而是照列、由模板按 `evaluatedPlanIds` 标「已评价」。
 */
const evaluatable = computed(() =>
  plans.value.filter(p => Number(p.progress) > 0)
)

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 评价行只回 planId，不回计划名（见 evaluationToMap），靠本地映射补。 */
function planTitleOf(e) {
  const p = plans.value.find(x => String(x.id) === String(e.planId))
  return p ? p.title : `帮扶计划 #${e.planId}`
}

function formatted(v) {
  return formatDate(v, true)
}

function ratingText(r) {
  const n = Number(r)
  if (!Number.isFinite(n)) return '未评分'
  if (n >= 5) return '非常满意'
  if (n >= 4) return '满意'
  if (n >= 3) return '一般'
  if (n >= 2) return '不太满意'
  return '不满意'
}

function openForm(p) {
  current.value = p
  // 已评价的：回填旧值，让「修改」真的是修改 ——
  // 否则默认 5 星 + 空白内容提交会把已有评价覆盖掉，还静默成功。
  const existing = rows.value.find(e => String(e.planId) === String(p.id))
  form.value = existing
    ? { score: Math.max(1, Math.min(5, Number(existing.score) || 5)), content: String(existing.content || '') }
    : { score: 5, content: '' }
  formOpen.value = true
}

async function doSubmit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    /**
     * ⚠️ 提交体的 key 是后端 `submitEvaluation` 实际读的那四个，**一个都不能改**：
     *   planId / counselorId / content / **score**
     * 后端只读这四个名字，多余的键一律忽略。所以：
     *  - 评分必须叫 `score`，叫 `rating` 会被**静默丢弃**（与申诉的 description 同类）。
     *  - `counselorId` 取自计划本身（assistance_plans.counselor_id），不传就写 null。
     *  - **没有** studentId 这个键 —— 学生身份是靠 planId 关联出来的
     *    （列表 SQL 用 `ae.plan_id = ap.id AND ap.student_id = ?` 反查）。
     */
    const res = await studentAPI.submitEvaluation({
      planId: current.value.id,
      counselorId: current.value.counselorId,
      score: form.value.score,
      content: form.value.content
    })
    if (res && res.error) {
      uni.showToast({ title: String(res.error), icon: 'none' })
      return
    }
    uni.showToast({ title: '评价已提交', icon: 'success' })
    formOpen.value = false
    await load()
  } catch (e) {
    console.error('[feedback] 提交评价失败:', e)
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
    /**
     * ⚠️ 两套 ID，这里必须分清：
     *  - `/students/assistance-plans/{uid}` 传 **users.id**（27）
     *  - `/students/evaluations/{sid}/list` 传 **student_profile.id**（3）
     * 后续 SQL 是 `assistance_evaluations JOIN assistance_plans ON ae.plan_id = ap.id
     *  WHERE ap.student_id = ?`，而 `assistance_plans.student_id` 存的是 profile id（1001 号计划 = 3）。
     * 传 27 不会报错，只会**永远返回空数组** —— 明明提交成功了却看不到，最难查的一类。
     */
    const [planList, profile] = await Promise.all([
      studentAPI.getAssistancePlans(userId).catch(e => { console.error('[feedback] 帮扶计划失败:', e); return [] }),
      studentAPI.getStudentByUserId(userId).catch(e => { console.error('[feedback] 学生档案失败:', e); return null })
    ])
    plans.value = Array.isArray(planList) ? planList : []
    profileId.value = profile && profile.id != null ? profile.id : ''

    if (!profileId.value) {
      rows.value = []
      return
    }
    // 实测当前返回 []（该表无行），空态是真实状态，不要造数据填
    const list = await studentAPI.getEvaluations(profileId.value)
      .catch(e => { console.error('[feedback] 评价列表失败:', e); return [] })
    rows.value = Array.isArray(list) ? list : []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

/* ---------- 已提交的评价 ---------- */
.fb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fb-head__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.fb-stars {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.fb-stars__item {
  margin-right: 6rpx;
  font-size: $font-lg;
  color: $color-border;
}

.fb-stars__item--on {
  color: #f59e0b;
}

.fb-stars__num {
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.fb-content {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
}

.fb-foot {
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.fb-foot__time {
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 可评价计划 ---------- */
.pending-item {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.pending-item:last-child {
  border-bottom: none;
}

.pending-item__info {
  flex: 1;
  min-width: 0;
}

.pending-item__title {
  display: block;
  font-size: $font-base;
  color: $color-text;
  @include ellipsis;
}

.pending-item__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.pending-item__action {
  flex-shrink: 0;
  padding: $spacing-xs $spacing-lg;
  border: 1rpx solid var(--color-primary);
  border-radius: $radius-pill;
  font-size: $font-sm;
  color: var(--color-primary);
}

/* 已评价：灰掉，与「去评价」区分开（同一行仍然可点，点进去是修改） */
.pending-item__action--done {
  border-color: $color-border;
  color: $color-text-hint;
}

.note {
  display: block;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
  font-size: $font-xs;
  line-height: 1.6;
  color: $color-text-hint;
}

/* ---------- 表单 ---------- */
.form {
  padding-bottom: $spacing-2xl;
}

.form__plan {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.form__label {
  display: block;
  margin: $spacing-lg 0 $spacing-sm;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.form__stars {
  display: flex;
  align-items: center;
}

.form__star {
  margin-right: $spacing-sm;
  font-size: 56rpx;
  line-height: 1;
  color: $color-border;
}

.form__star--on {
  color: #f59e0b;
}

.form__star-text {
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.form__textarea {
  width: 100%;
  min-height: 200rpx;
  padding: $spacing-md;
  box-sizing: border-box;
  border: 1rpx solid $color-border;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

.form__placeholder {
  color: $color-text-hint;
  font-size: $font-sm;
}

.form__count {
  display: block;
  margin-top: $spacing-xs;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-hint;
}
</style>
