<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="班级申请审批" />

    <filter-bar v-model="statusFilter" :options="statusOptions" />
    <filter-bar v-model="typeFilter" :options="typeOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ apps.length }}</text> 条申请 ·
            待审核 <text class="summary__num summary__num--danger">{{ pendingCount }}</text> 条
          </text>
          <text class="summary__hint">按处理状态与申请类型筛选</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="apps.length ? '这个条件下没有申请' : '暂无班级申请'"
          :hint="apps.length ? '换一个状态或类型看看' : '教师在「班级管理」里申请管理班级后，会出现在这里'"
          icon="building"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(a, i) in filtered"
            :key="`${a.type}-${a.id}`"
            class="app"
            :class="{ 'app--divided': i > 0 }"
          >
            <view class="app__head">
              <text class="app__class">{{ a.className || `班级 id ${a.classId}` }}</text>
              <text class="tag" :class="statusTagClass(a.status)">{{ statusText(a.status) }}</text>
            </view>
            <view class="app__meta">
              <text class="tag tag--info">{{ a.type === 'counselor' ? '辅导员申请' : '教师申请' }}</text>
              <text class="app__who">{{ applicant(a) }}</text>
              <text class="app__kind">{{ kindText(a.applicationType) }}</text>
            </view>
            <text class="app__reason">{{ a.reason || '（未填申请理由）' }}</text>
            <!-- 拒绝原因（2026-10-02 W1.14）：此前后端收下即丢，现在真的落库并回读。
                 只有 status=2 才会有值，其它状态即便库里留着残留也不显示。 -->
            <view v-if="Number(a.status) === 2 && a.rejectReason" class="app__reject">
              <text class="app__reject__label">拒绝原因</text>
              <text class="app__reject__text">{{ a.rejectReason }}</text>
            </view>
            <text class="app__time">提交 {{ formatDate(a.createTime, true) }} · 最近变更 {{ formatDate(a.updateTime, true) }}</text>

            <view v-if="isPending(a)" class="app__acts">
              <view class="app__btn app__btn--ok" @tap="approve(a)">通过</view>
              <view class="app__btn app__btn--no" @tap="enterReject(a)">拒绝</view>
            </view>
          </view>
        </view>

      </template>
    </view>

    <!-- ============ 拒绝原因 ============ -->
    <detail-sheet v-model="rejectOpen" title="拒绝申请" height="56vh">
      <view class="sheet">
        <text class="sheet__who" v-if="rejectTarget">
          {{ rejectTarget.className }} · {{ applicant(rejectTarget) }}
        </text>
        <view class="field">
          <text class="field__label">拒绝原因</text>
          <textarea
            v-model="rejectReason"
            class="field__area"
            :maxlength="-1"
            auto-height
            placeholder="填写拒绝理由"
            placeholder-class="field__ph"
          />
        </view>

      </view>
      <template #footer>
        <view class="form-acts">
          <view class="form-acts__cancel" @tap="rejectOpen = false">取消</view>
          <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="doReject">
            {{ submitting ? '提交中…' : '确认拒绝' }}
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

const themeStyle = roleThemeStyle('admin')

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待审核', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已拒绝', value: '2' },
  { label: '已取消', value: '3' }
]
const typeOptions = [
  { label: '全部类型', value: '' },
  { label: '教师申请', value: 'teacher' },
  { label: '辅导员申请', value: 'counselor' }
]

const loading = ref(true)
const apps = ref([])
const statusFilter = ref('')
const typeFilter = ref('')

const rejectOpen = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')
const submitting = ref(false)

const filtered = computed(() => apps.value.filter(a => {
  if (statusFilter.value && String(a.status) !== statusFilter.value) return false
  if (typeFilter.value && a.type !== typeFilter.value) return false
  return true
}))

const pendingCount = computed(() => apps.value.filter(isPending).length)

function isPending(a) {
  return Number(a.status) === 0
}

function statusText(s) {
  const n = Number(s)
  if (n === 0) return '待审核'
  if (n === 1) return '已通过'
  if (n === 2) return '已拒绝'
  if (n === 3) return '已取消'
  return `状态 ${s}`
}

function statusTagClass(s) {
  const n = Number(s)
  if (n === 0) return 'tag--warning'
  if (n === 1) return 'tag--success'
  if (n === 2) return 'tag--danger'
  return 'tag--pending'
}

/** `applicationType`：0 = 申请接管班级，1 = 申请解除班级管理（实测只有这两种）。 */
function kindText(t) {
  const n = Number(t)
  if (n === 1) return '申请解除管理'
  if (n === 0) return '申请接管班级'
  return t === null || t === undefined ? '未标注类型' : `类型 ${t}`
}

/** 两类申请的人名字段不同：教师是 teacherName/teacherUsername，辅导员是 counselorName/counselorId。 */
function applicant(a) {
  if (a.type === 'counselor') {
    return `辅导员 ${a.counselorName || '未填姓名'}${a.counselorId ? '（' + a.counselorId + '）' : ''}`
  }
  return `教师 ${a.teacherName || '未填姓名'}${a.teacherUsername ? '（' + a.teacherUsername + '）' : ''}`
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

function confirm(content, title = '确认') {
  return new Promise(resolve => {
    uni.showModal({
      title,
      content,
      success: r => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await adminAPI.getClassApplications(1, 100)
    const rows = Array.isArray(res) ? res : (res && Array.isArray(res.items) ? res.items : [])
    // 待审核的排在最前，其余按提交时间倒序
    apps.value = rows.slice().sort((a, b) => {
      const pa = isPending(a) ? 0 : 1
      const pb = isPending(b) ? 0 : 1
      if (pa !== pb) return pa - pb
      return String(b.createTime || '').localeCompare(String(a.createTime || ''))
    })
  } catch (e) {
    console.error('[admin class-requests] 申请列表失败:', e)
    apps.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

async function approve(a) {
  const ok = await confirm(
    `通过「${a.className}」的${kindText(a.applicationType)}（${applicant(a)}）？\n` +
    '通过后该班级的对应字段会被改写（辅导员申请还会顺带写 classes.counselor_id）。',
    '通过申请'
  )
  if (!ok) return
  submitting.value = true
  try {
    await adminAPI.approveClassRequest(a.id, a.type)
    toast('已通过')
    await load()
  } catch (e) {
    toast((e && e.message) || '操作失败', 3200)
  } finally {
    submitting.value = false
  }
}

function enterReject(a) {
  rejectTarget.value = a
  rejectReason.value = ''
  rejectOpen.value = true
}

async function doReject() {
  const a = rejectTarget.value
  if (!a || submitting.value) return
  submitting.value = true
  try {
    await adminAPI.rejectClassRequest(a.id, String(rejectReason.value || '').trim(), a.type)
    toast('已拒绝')
    rejectOpen.value = false
    await load()
  } catch (e) {
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

/* ---------- 申请行 ---------- */
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

.app__class {
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
  flex-wrap: wrap;
  margin-top: $spacing-sm;
}

.app__who {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.app__kind {
  width: 100%;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: var(--color-primary);
}

.app__reason {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

/* 拒绝原因（W1.14）：与「申请理由」在视觉上分开，别让人读成同一段文字 */
.app__reject {
  display: flex;
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-sm;
  background: rgba(239, 68, 68, 0.08);
}

.app__reject__label {
  flex-shrink: 0;
  margin-right: $spacing-sm;
  font-size: $font-xs;
  color: $color-danger;
}

.app__reject__text {
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
