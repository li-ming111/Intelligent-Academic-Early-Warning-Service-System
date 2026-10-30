<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="成绩申诉审核" />

    <filter-bar v-model="statusFilter" :options="statusOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ rows.length }}</text> 条申诉 ·
            待处理 <text class="summary__num summary__num--danger">{{ pendingCount }}</text> 条
          </text>
          <text class="summary__hint">状态筛选在本地做</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="rows.length ? '这个状态下没有申诉' : '暂无成绩申诉'"
          :hint="rows.length ? '换一个状态看看' : '学生在「成绩申诉」页提交后会出现在这里'"
          icon="edit"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(a, i) in filtered"
            :key="a.id"
            class="app"
            :class="{ 'app--divided': i > 0 }"
          >
            <view class="app__head">
              <text class="app__course">{{ a.courseName || `课程 id ${a.courseId}` }}</text>
              <text class="tag" :class="statusTagClass(a.status)">{{ statusText(a.status) }}</text>
            </view>
            <view class="app__meta">
              <text class="app__who">{{ a.studentName || '未知学生' }}</text>
              <text class="app__no">{{ a.studentNo || '--' }}</text>
            </view>
            <text class="app__reason">{{ appealReasonText(a.reason) }}：{{ a.description || '（未填说明）' }}</text>

            <!-- 审核意见（只有审过的才有） -->
            <view v-if="a.reply" class="app__reply">
              <text class="app__reply__label">审核意见</text>
              <text class="app__reply__text">{{ a.reply }}</text>
            </view>

            <text class="app__time">提交 {{ formatDate(a.createdAt, true) }} · 最近变更 {{ formatDate(a.updatedAt, true) }}</text>

            <view v-if="isPending(a)" class="app__acts">
              <view class="app__btn app__btn--ok" @tap="enterReview(a, 1)">通过</view>
              <view class="app__btn app__btn--no" @tap="enterReview(a, 2)">驳回</view>
            </view>
          </view>
        </view>

        <text class="note">只有待审核的申诉可以操作，审核结果会通知申诉学生。</text>
      </template>
    </view>

    <!-- ============ 审核 ============ -->
    <detail-sheet v-model="reviewOpen" :title="reviewStatus === 1 ? '通过申诉' : '驳回申诉'" height="56vh">
      <view class="sheet">
        <text v-if="reviewTarget" class="sheet__who">
          {{ reviewTarget.courseName }} · {{ reviewTarget.studentName }}
        </text>
        <view class="field">
          <text class="field__label">审核意见</text>
          <textarea
            v-model="reviewReply"
            class="field__area"
            :maxlength="-1"
            auto-height
            :placeholder="reviewStatus === 1 ? '说明处理结果（如：已更正为 78 分）' : '说明驳回理由'"
            placeholder-class="field__ph"
          />
        </view>

      </view>
      <template #footer>
        <view class="form-acts">
          <view class="form-acts__cancel" @tap="reviewOpen = false">取消</view>
          <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="doReview">
            {{ submitting ? '提交中…' : (reviewStatus === 1 ? '确认通过' : '确认驳回') }}
          </view>
        </view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { APPEAL_STATUS_TEXT, appealReasonText } from '@/constants/dict.js'

const themeStyle = roleThemeStyle('admin')

// score_appeals.status：0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤回（见 constants/dict.js）
const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已驳回', value: '2' },
  { label: '已撤回', value: '3' }
]

const loading = ref(true)
const rows = ref([])
const statusFilter = ref('')

const reviewOpen = ref(false)
const reviewTarget = ref(null)
const reviewStatus = ref(1)
const reviewReply = ref('')
const submitting = ref(false)

const filtered = computed(() => {
  if (!statusFilter.value) return rows.value
  return rows.value.filter(a => String(a.status) === statusFilter.value)
})

/** 待处理 = status 0。别写成 `!== 1` —— 那会把已驳回/已撤回都算进来。 */
const pendingCount = computed(() => rows.value.filter(isPending).length)

function isPending(a) {
  return Number(a.status) === 0
}

function statusText(s) {
  const n = Number(s)
  return APPEAL_STATUS_TEXT[n] || `状态 ${s}`
}

function statusTagClass(s) {
  const n = Number(s)
  if (n === 0) return 'tag--warning'
  if (n === 1) return 'tag--success'
  if (n === 2) return 'tag--danger'
  return 'tag--pending'   // 3 已撤回
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const list = await adminAPI.getAdminAppeals()
    // 后端已把待处理排在最前，这里再兜一次（前端筛选不该依赖后端排序）
    rows.value = (Array.isArray(list) ? list : []).slice().sort((a, b) => {
      const pa = isPending(a) ? 0 : 1
      const pb = isPending(b) ? 0 : 1
      if (pa !== pb) return pa - pb
      return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
    })
  } catch (e) {
    console.error('[admin appeals] 申诉列表失败:', e)
    rows.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

function enterReview(a, status) {
  reviewTarget.value = a
  reviewStatus.value = status
  reviewReply.value = ''
  reviewOpen.value = true
}

async function doReview() {
  const a = reviewTarget.value
  if (!a || submitting.value) return
  submitting.value = true
  try {
    await adminAPI.reviewAppeal(a.id, {
      status: reviewStatus.value,
      reply: String(reviewReply.value || '').trim()
    })
    toast(reviewStatus.value === 1 ? '已通过' : '已驳回')
    reviewOpen.value = false
    await load()
  } catch (e) {
    // 重复审核 / 已被撤回都会走到这里，后端 message 有具体原因，照实显示
    toast((e && e.message) || '操作失败', 3200)
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-sm;
  padding-bottom: $spacing-3xl;
}

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

.summary__num--danger {
  color: $color-danger;
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

/* ---------- 申诉行 ---------- */
.app {
  padding: $spacing-lg;
}

.app--divided {
  border-top: 1rpx solid $color-divider;
}

.app__head {
  display: flex;
  align-items: center;
}

.app__course {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.app__meta {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.app__who {
  font-size: $font-xs;
  color: $color-text-secondary;
}

.app__no {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.app__reason {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

/* 审核意见：与「申诉说明」视觉分开，别读成同一段 */
.app__reply {
  display: flex;
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-sm;
  background: rgba(59, 130, 246, 0.08);
}

.app__reply__label {
  flex-shrink: 0;
  margin-right: $spacing-sm;
  font-size: $font-xs;
  color: $color-info;
}

.app__reply__text {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  line-height: 1.6;
  color: $color-text;
  word-break: break-all;
}

.app__time {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.app__acts {
  display: flex;
  margin-top: $spacing-md;
}

.app__btn {
  height: $tap-min;
  padding: 0 $spacing-xl;
  margin-right: $spacing-sm;
  border-radius: $radius-md;
  font-size: $font-sm;
  font-weight: 500;
  @include flex-center;
}

.app__btn--ok {
  background: var(--color-primary);
  color: #fff;
}

.app__btn--no {
  background: rgba(239, 68, 68, 0.1);
  color: $color-danger;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-lg;
}

.sheet__who {
  display: block;
  margin-bottom: $spacing-lg;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  font-size: $font-sm;
  color: var(--color-primary);
}

.field__label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.field__area {
  width: 100%;
  box-sizing: border-box;
  min-height: 140rpx;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  line-height: 1.6;
  color: $color-text;
}

.field__ph {
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

.form-acts {
  display: flex;
  align-items: center;
}

.form-acts__cancel {
  width: 200rpx;
  height: $tap-min;
  margin-right: $spacing-md;
  border-radius: var(--radius-md);
  background: $color-bg;
  color: $color-text-secondary;
  font-size: $font-md;
  @include flex-center;
}

.form-acts__ok {
  flex: 1;
  min-width: 0;
}
</style>
