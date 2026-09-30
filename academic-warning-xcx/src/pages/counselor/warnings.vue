<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="预警处理" :show-back="false" />

    <role-hero
      :title="counselorName || '学业预警'"
      subtitle="处理结果会写入预警记录，学生端同步可见"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <!-- 选项由数据里**实际出现**的状态生成：只有一条待处理时不该显示四个空选项 -->
    <filter-bar v-model="statusKey" :options="statusOptions" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 批量操作条 -->
        <view class="bulk">
          <text class="bulk__text">
            {{ selectMode ? `已选 ${selected.length} / ${selectable.length} 条` : `当前 ${visible.length} 条，其中 ${pendingInVisible} 条待处理` }}
          </text>
          <view class="bulk__actions">
            <template v-if="selectMode">
              <view class="bulk__btn" @tap="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</view>
              <view
                class="bulk__btn bulk__btn--primary"
                :class="{ 'bulk__btn--disabled': !selected.length }"
                @tap="doBatch"
              >处理所选</view>
              <view class="bulk__btn" @tap="exitSelect">退出</view>
            </template>
            <view
              v-else
              class="bulk__btn bulk__btn--primary"
              :class="{ 'bulk__btn--disabled': !pendingInVisible }"
              @tap="enterSelect"
            >批量处理</view>
          </view>
        </view>

        <empty-state
          v-if="!visible.length"
          :text="statusKey === '' ? '暂无预警记录' : `没有${statusLabel}的预警`"
          :hint="statusKey === ''
            ? '所带班级没有触发任何预警规则'
            : '换个状态看看，或点「全部」查看所有预警'"
          icon="check"
        />

        <view v-else class="warn-list">
          <view
            v-for="w in visible"
            :key="w.id"
            class="warn-card"
            :class="['warn-card--level-' + (w.level || 0), { 'warn-card--picked': isPicked(w) }]"
            @tap="onCardTap(w)"
          >
            <view class="warn-card__head">
              <!-- 选择态只在批量模式下出现，且只让待处理的条能被勾 ——
                   已处理的再点一次只会把 handled_at 刷新成现在，没有业务含义 -->
              <view
                v-if="selectMode"
                class="warn-card__check"
                :class="{
                  'warn-card__check--on': isPicked(w),
                  'warn-card__check--off': !w.isPending
                }"
              >{{ isPicked(w) ? '✓' : '' }}</view>
              <text class="warn-card__name">{{ w.studentName || '未知学生' }}</text>
              <text class="warn-card__class">{{ w.className }}</text>
              <warning-tag :level="w.level" />
            </view>

            <text class="warn-card__desc">{{ w.description || '暂无说明' }}</text>

            <view class="warn-card__foot">
              <text class="tag" :class="w.isPending ? 'tag--warning' : 'tag--success'">{{ w.statusText }}</text>
              <text class="warn-card__time">{{ w.createdDate }}</text>
              <view
                v-if="!selectMode && w.isPending"
                class="warn-card__handle"
                @tap.stop="openProcess(w)"
              >处理</view>
            </view>
          </view>
        </view>
      </template>
    </view>

    <!-- 单条处理 -->
    <detail-sheet v-model="processOpen" title="处理预警" height="72vh">
      <view v-if="current.id" class="form">
        <view class="form__head">
          <text class="form__name">{{ current.studentName || '未知学生' }}</text>
          <warning-tag :level="current.level" />
        </view>
        <text class="form__class">{{ current.className }}</text>
        <text class="form__desc">{{ current.description || '暂无说明' }}</text>

        <text class="form__label">处理措施</text>
        <textarea
          v-model="form.handleResult"
          class="form__textarea"
          placeholder="记录已采取的措施，例如：已约谈学生、已安排学习小组、已通知家长"
          placeholder-class="form__placeholder"
          :maxlength="300"
          auto-height
        />
        <text class="form__count">{{ form.handleResult.length }} / 300</text>

        <text class="form__tip">
          提交后该条预警状态变为「已处理」，处理措施与处理人写入预警记录。
        </text>
      </view>

      <template #footer>
        <view
          class="btn-primary"
          :class="{ 'btn-primary--disabled': !canProcess }"
          @tap="doProcess"
        >{{ submitting ? '提交中…' : '标记为已处理' }}</view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { normalizeWarnings, countWarnings } from '@/utils/warning.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

const loading = ref(true)
const rows = ref([])
/** '' = 全部；'0' = 待处理；'1' = 已处理 */
const statusKey = ref('')

/** 处理人写入 `academic_warning.handled_by`（varchar）。取不到姓名时不留空。 */
const counselorName = computed(() => userName || '')

/** 批量模式 */
const selectMode = ref(false)
const selected = ref([])

/** 单条处理 */
const processOpen = ref(false)
const submitting = ref(false)
const current = ref({})
const form = ref({ handleResult: '' })

const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const summary = computed(() => countWarnings(rows.value))

const statusLabel = computed(() => (statusKey.value === '0' ? '待处理' : '已处理'))

const visible = computed(() => {
  if (statusKey.value === '') return rows.value
  const want = Number(statusKey.value)
  return rows.value.filter(w => w.status === want)
})

const pendingInVisible = computed(() => visible.value.filter(w => w.isPending).length)
const selectable = computed(() => visible.value.filter(w => w.isPending))
const allSelected = computed(() => selectable.value.length > 0 && selected.value.length === selectable.value.length)

/** 与 hero 的第 4 格同源：按 studentId 去重，而不是把预警条数当人数 */
const studentCount = computed(() => {
  const ids = new Set(rows.value.map(w => String(w.studentId ?? '')).filter(Boolean))
  return ids.size
})

/** 四张卡与筛选条一一对应，点得进去、数得出来 */
const heroStats = computed(() => {
  const s = summary.value
  return [
    { label: '预警总数', value: s.total },
    { label: '待处理', value: s.pending, onTap: () => pick('0', s.pending) },
    { label: '已处理', value: s.handled, onTap: () => pick('1', s.handled) },
    { label: '涉及学生', value: studentCount.value }
  ]
})

const statusOptions = computed(() => {
  const s = summary.value
  const opts = [{ label: '全部', value: '', count: s.total }]
  if (s.pending > 0) opts.push({ label: '待处理', value: '0', count: s.pending })
  if (s.handled > 0) opts.push({ label: '已处理', value: '1', count: s.handled })
  return opts
})

const canProcess = computed(() => Boolean(current.value.id) && Boolean(form.value.handleResult.trim()))

function pick(status, count) {
  if (!count) return
  statusKey.value = statusKey.value === status ? '' : status
  exitSelect()
}

function isPicked(w) {
  return selected.value.includes(w.id)
}

function onCardTap(w) {
  if (!selectMode.value) return
  if (!w.isPending) return
  const i = selected.value.indexOf(w.id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(w.id)
}

function enterSelect() {
  if (!pendingInVisible.value) return
  selectMode.value = true
  selected.value = []
}

function exitSelect() {
  selectMode.value = false
  selected.value = []
}

function toggleAll() {
  selected.value = allSelected.value ? [] : selectable.value.map(w => w.id)
}

function openProcess(w) {
  current.value = w
  form.value = { handleResult: '' }
  processOpen.value = true
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const payload = await counselorAPI.getWarnings(userId)
    rows.value = normalizeWarnings(payload)
  } catch (e) {
    console.error('[counselor warnings] 加载失败:', e)
    rows.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 单条处理：**真写库** —— status→1、handled_at=now、handle_result 与 handled_by 落库。
 */
async function doProcess() {
  if (!canProcess.value || submitting.value) return
  submitting.value = true
  try {
    await counselorAPI.processWarning(current.value.id, {
      // 这两个 key 是后端 processWarning 里逐个 containsKey 检查的，
      // 名字写错不会报错，只会让 handle_result / handled_by 留空。
      handleResult: form.value.handleResult.trim(),
      handledBy: counselorName.value || '辅导员'
    })
    uni.showToast({ title: '已标记为已处理', icon: 'success' })
    processOpen.value = false
    await load()
  } catch (e) {
    console.error('[counselor warnings] 处理失败:', e)
    uni.showToast({ title: e && e.message ? e.message : '处理失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

/**
 * 批量处理：**真写库**，但只置 status 与 handled_at。
 *
 * 后端 `batch-process` 的入参就是一个 `List<Long>`，**没有** handleResult / handledBy 的位置，
 * 所以批量处理过的预警在"处理措施"上是空的。这里如实提示，不假装批量也能写意见 ——
 * 要让某条留有措施记录，就用单条处理。
 */
function doBatch() {
  if (!selected.value.length) return
  const n = selected.value.length
  uni.showModal({
    title: '批量处理',
    content: `将 ${n} 条预警标记为「已处理」。批量处理只更新状态与处理时间，不写入处理措施；需要留措施请逐条处理。`,
    confirmText: '确认处理',
    success: async (res) => {
      if (!res.confirm) return
      try {
        const r = await counselorAPI.batchProcessWarnings(selected.value.slice())
        const ok = r && typeof r.processed === 'number' ? r.processed : n
        uni.showToast({ title: `已处理 ${ok} 条`, icon: 'success' })
        exitSelect()
        await load()
      } catch (e) {
        console.error('[counselor warnings] 批量处理失败:', e)
        uni.showToast({ title: e && e.message ? e.message : '批量处理失败', icon: 'none' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

/* ---------- 批量操作条 ---------- */
.bulk {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 $page-padding;
}

.bulk__text {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.bulk__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.bulk__btn {  @include tap-area;
  margin-left: $spacing-sm;
  padding: 8rpx 22rpx;
  border-radius: $radius-pill;
  background: $color-card;
  border: 1rpx solid $color-border;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.bulk__btn--primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.bulk__btn--disabled {
  opacity: 0.45;
}

/* ---------- 列表 ---------- */
.warn-list {
  margin-top: $spacing-md;
  padding: 0 $page-padding;
}

.warn-card {
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  /* 左侧色条按等级走：一眼扫过去就知道哪几条更急 */
  border-left: 6rpx solid $color-pending;
  @include card;
}

.warn-card--level-1 { border-left-color: $level-1; }
.warn-card--level-2 { border-left-color: $level-2; }
.warn-card--level-3 { border-left-color: $level-3; }

.warn-card--picked {
  box-shadow: 0 0 0 2rpx var(--color-primary);
}

.warn-card__head {
  display: flex;
  align-items: center;
}

.warn-card__check {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  margin-right: $spacing-sm;
  border-radius: $radius-sm;
  border: 2rpx solid $color-border;
  color: #fff;
  font-size: $font-sm;
  line-height: 36rpx;
  text-align: center;
}

.warn-card__check--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

/* 已处理的条不可勾选：视觉上直接虚化，省得点了没反应还被当成 bug */
.warn-card__check--off {
  opacity: 0.3;
}

.warn-card__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.warn-card__class {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.warn-card__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.55;
}

.warn-card__foot {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.warn-card__time {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.warn-card__handle {  @include tap-area;
  flex-shrink: 0;
  min-width: 120rpx;
  padding: 8rpx 0;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
}

/* ---------- 处理表单 ---------- */
.form {
  padding: 0 $page-padding $spacing-2xl;
}

.form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form__name {
  flex: 1;
  min-width: 0;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.form__class {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__desc {
  display: block;
  margin-top: $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
}

.form__label {
  display: block;
  margin-top: $spacing-xl;
  font-size: $font-sm;
  color: $color-text;
}

.form__textarea {
  width: 100%;
  min-height: 180rpx;
  margin-top: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

.form__count {
  display: block;
  margin-top: 6rpx;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__tip {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}
</style>
