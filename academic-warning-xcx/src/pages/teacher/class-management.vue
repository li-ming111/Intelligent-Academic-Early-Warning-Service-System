<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="班级管理" :show-back="false" />

    <view class="tabs">
      <filter-bar v-model="tab" :options="tabOptions" />
    </view>

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="3" />

      <template v-else>
        <!-- ============ 一、我的班级 ============ -->
        <template v-if="tab === 'mine'">
          <empty-state
            v-if="!classes.length"
            text="暂未管理任何班级"
            hint="切到「申请班级」提交一份管理申请"
            icon="building"
          />

          <view v-else>
            <view v-for="c in classes" :key="c.id" class="card">
              <view class="c__head">
                <view class="c__title">
                  <text class="c__name">{{ c.name }}</text>
                  <text class="c__meta">{{ numOr(c.studentCount) }} 名学生</text>
                </view>
                <text class="tag tag--info">{{ c.studentCount ? '在管' : '空班' }}</text>
              </view>
              <view class="c__actions">
                <view class="act" @tap="goStudents">学生名单</view>
                <view class="act act--danger" @tap="confirmCancel(c)">解除管理</view>
              </view>
            </view>
            <text class="note">「解除管理」会立即把班级从你名下移除，无需管理员审核。操作后你不再能看到该班的学生与成绩。</text>
          </view>
        </template>

        <!-- ============ 二、申请记录 ============ -->
        <template v-else-if="tab === 'requests'">
          <empty-state
            v-if="!requests.length"
            text="还没有提交过申请"
            hint="切到「申请班级」选择班级并填写理由"
            icon="doc"
          />

          <view v-else class="card card--flush">
            <view
              v-for="(r, i) in requests"
              :key="r.id"
              class="row"
              :class="{ 'row--divided': i > 0 }"
            >
              <view class="row__main">
                <text class="row__title">{{ r.className || '未知班级' }}</text>
                <text class="row__desc">{{ r.reason || '未填写理由' }}</text>
                <!-- 拒绝原因（2026-10-02 W1.14）：管理员拒绝时填的理由，现在真的落库并回读。
                     只有 status=2（已拒绝）才显示。 -->
                <text v-if="Number(r.status) === 2 && r.rejectReason" class="row__reject">
                  拒绝原因：{{ r.rejectReason }}
                </text>
                <text class="row__time">提交于 {{ fmt(r.createTime) }}</text>
              </view>
              <text class="tag" :class="statusClass(r.status)">{{ statusText(r.status) }}</text>
            </view>
          </view>
        </template>

        <!-- ============ 三、申请班级 ============ -->
        <template v-else-if="tab === 'apply'">
          <view class="search">
            <app-icon name="search" :size="34" color="#aeaeb2" />
            <input
              v-model="keyword"
              class="search__input"
              placeholder="输入班级名称搜索"
              placeholder-class="search__ph"
              confirm-type="search"
              @confirm="doSearch"
            />
            <view class="search__btn" @tap="doSearch">搜索</view>
          </view>

          <text class="note">留空搜索会列出全部班级。已经在你名下的班不会出现在结果里。</text>

          <empty-state
            v-if="!found.length"
            text="没有找到班级"
            hint="换个关键词，或留空列出全部"
            icon="search"
            :top="60"
          />

          <view v-else class="card card--flush">
            <view
              v-for="(c, i) in found"
              :key="c.id"
              class="row"
              :class="{ 'row--divided': i > 0 }"
              @tap="openApply(c)"
            >
              <view class="row__main">
                <text class="row__title">{{ c.name }}</text>
                <text class="row__desc">{{ numOr(c.studentCount) }} 名学生 · {{ c.hasTeacher ? '已有负责教师' : '暂无负责教师' }}</text>
              </view>
              <text class="tag" :class="c.hasTeacher ? 'tag--pending' : 'tag--success'">{{ c.hasTeacher ? '已有教师' : '可申请' }}</text>
            </view>
          </view>
          <text v-if="found.length" class="note">班级已有负责教师时仍可提交申请，由管理员在审批时决定是否转交。</text>
        </template>

        <!-- ============ 四、成绩修改记录 ============ -->
        <template v-else>
          <empty-state
            v-if="!audits.length"
            text="暂无成绩修改记录"
            hint="在「成绩管理」里改分后会自动记一条"
            icon="edit"
          />

          <view v-else>
            <view class="summary">
              <text class="summary__item">共 <text class="summary__num">{{ auditTotal }}</text> 条</text>
            </view>
            <view class="card card--flush">
              <view
                v-for="(a, i) in audits"
                :key="a.id"
                class="row"
                :class="{ 'row--divided': i > 0 }"
              >
                <view class="row__main">
                  <text class="row__title">{{ a.studentName || '未知学生' }} · {{ a.courseName || '未知课程' }}</text>
                  <view class="chg">
                    <text class="chg__old">{{ fmtScore(a.oldScore) }}</text>
                    <app-icon name="chevron" :size="26" color="#aeaeb2" />
                    <text class="chg__new">{{ fmtScore(a.newScore) }}</text>
                    <text class="chg__diff" :class="Number(a.difference) >= 0 ? 'chg__diff--up' : 'chg__diff--down'">
                      {{ signed(a.difference) }}
                    </text>
                  </view>
                  <text class="row__desc">{{ a.reason || '未填写原因' }}</text>
                  <text class="row__time">{{ a.modifiedBy || '未知操作人' }} · {{ fmt(a.modifiedAt) }}</text>
                </view>
              </view>
            </view>
            <text class="note">记录由改分接口自动写入，教师端不可编辑、不可删除 —— 这是成绩改动的留痕。</text>
          </view>
        </template>
      </template>
    </view>

    <!-- 申请班级 -->
    <detail-sheet v-model="applyOpen" :title="target ? target.name : '申请管理班级'" height="70vh">
      <view v-if="target" class="form">
        <view class="form__facts">
          <view class="form__fact">
            <text class="form__fact-label">班级</text>
            <text class="form__fact-value">{{ target.name }}</text>
          </view>
          <view class="form__fact">
            <text class="form__fact-label">学生</text>
            <text class="form__fact-value">{{ numOr(target.studentCount) }} 人</text>
          </view>
        </view>

        <text class="form__label">申请理由</text>
        <textarea
          v-model="reason"
          class="form__textarea"
          placeholder="例如：本人承担该班高等数学授课，需管理班级成绩与预警"
          placeholder-class="form__placeholder"
          :maxlength="200"
          auto-height
        />
        <text class="form__tip">提交后进入管理员审批队列，状态为「待审核」。可在「申请记录」里跟踪结果。</text>
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !canApply }" @tap="submitApply">
          {{ saving ? '提交中…' : '提交申请' }}
        </view>
      </template>
    </detail-sheet>

    <app-tab-bar role="teacher" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { APPLICATION_STATUS_TEXT, APPLICATION_STATUS_KEY, STATUS_TAG_TYPE } from '@/constants/dict.js'
import { formatDate } from '@/utils/format.js'
import { toNumber } from '@/utils/score.js'
import { go } from '@/utils/navigate.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const tabOptions = [
  { label: '我的班级', value: 'mine' },
  { label: '申请记录', value: 'requests' },
  { label: '申请班级', value: 'apply' },
  { label: '改分记录', value: 'audit' }
]

const tab = ref('mine')
const loading = ref(true)

const classes = ref([])
const requests = ref([])
const found = ref([])
const auditTotal = ref(0)
const audits = ref([])

const keyword = ref('')
const applyOpen = ref(false)
const target = ref(null)
const reason = ref('')
const saving = ref(false)

/** 「我的班级」在首屏就拉；申请记录与改分记录切到对应 tab 再拉，省掉两次冷请求。 */
const loaded = { requests: false, audit: false }

const canApply = computed(() => !!target.value && !!String(reason.value).trim())

function numOr(v) {
  return Number.isFinite(Number(v)) ? Number(v) : 0
}

function fmt(v) {
  return formatDate(v) || '--'
}

function fmtScore(v) {
  const n = toNumber(v)
  return n === null ? '--' : String(Number(n.toFixed(1)))
}

function signed(v) {
  const n = toNumber(v)
  if (n === null) return '--'
  return `${n > 0 ? '+' : ''}${Number(n.toFixed(1))}`
}

function statusText(v) {
  return APPLICATION_STATUS_TEXT[Number(v)] || '未知状态'
}

/** 状态 → 标签配色。字典是唯一口径，页面不自己写 if 链。 */
function statusClass(v) {
  const key = APPLICATION_STATUS_KEY[Number(v)]
  const type = STATUS_TAG_TYPE[key] || 'pending'
  return `tag--${type}`
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await teacherAPI.getMyClasses(userId).catch((e) => {
      console.error('[teacher class] 班级失败:', e)
      return []
    })
    classes.value = (Array.isArray(list) ? list : []).slice()
      .sort((a, b) => numOr(b.studentCount) - numOr(a.studentCount))
  } finally {
    loading.value = false
  }
}

watch(tab, (v) => {
  if (v === 'requests') loadRequests()
  if (v === 'apply' && !found.value.length) doSearch()
  if (v === 'audit') loadAudit()
})

async function loadRequests() {
  const rows = await teacherAPI.getClassRequests(userId).catch((e) => {
    console.error('[teacher class] 申请记录失败:', e)
    return []
  })
  requests.value = Array.isArray(rows) ? rows : []
  loaded.requests = true
}

async function loadAudit() {
  const data = await teacherAPI.getAuditLogs(userId).catch((e) => {
    console.error('[teacher class] 改分记录失败:', e)
    return null
  })
  // 分页载荷：`{total, page, size, list}`。
  // ⚠️ 列表里的 `studentId` 是 **profile id**，不是学号 ——
  // 所以这一页只显示姓名，不显示学号（PC 端那页把它标成了「学号」，是错的）。
  audits.value = (data && Array.isArray(data.list)) ? data.list : []
  auditTotal.value = numOr(data && data.total)
  loaded.audit = true
}

async function doSearch() {
  const rows = await teacherAPI.searchClasses(keyword.value).catch((e) => {
    console.error('[teacher class] 搜索失败:', e)
    return []
  })
  const mineNames = new Set(classes.value.map(c => c.name))
  found.value = (Array.isArray(rows) ? rows : []).filter(c => !mineNames.has(c.name))
}

function openApply(c) {
  target.value = c
  reason.value = ''
  applyOpen.value = true
}

async function submitApply() {
  if (!canApply.value || saving.value) return
  saving.value = true
  try {
    // `applicationType = 0` 是「申请管理」（1 才是申请解除）。
    // ⚠️ 后端这条会自己写 `teacherUsername = "T" + teacherId`（→ "T29"），
    //    而库里历史记录的同一列是登录工号（"10001"）。管理员审批页会同时看到
    //    这两种格式 —— 这是后端的一处不一致，不是本页写错了。
    await teacherAPI.applyClass({
      teacherId: userId,
      classId: target.value.id,
      className: target.value.name,
      reason: String(reason.value).trim(),
      applicationType: 0
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    applyOpen.value = false
    // 提交完切到记录页，让用户直接看到结果，而不是停在原地猜有没有成功
    tab.value = 'requests'
    await loadRequests()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '提交失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

/**
 * 解除管理是**当场写库**的危险操作（`classes.teacher_id` 置 NULL，
 * 已有申请一并置为已取消），所以必须二次确认，且文案要说清后果。
 */
function confirmCancel(c) {
  uni.showModal({
    title: '解除班级管理',
    content: `确定解除对「${c.name}」的管理？该班 ${numOr(c.studentCount)} 名学生的成绩与预警将不再对你可见，且无需管理员审核。`,
    confirmText: '解除',
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await teacherAPI.cancelClass({ teacherId: userId, classId: c.id })
        uni.showToast({ title: '已解除管理', icon: 'success' })
        await load()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
      }
    }
  })
}

function goStudents() {
  go('/pages/teacher/students')
}
</script>

<style lang="scss" scoped>
.tabs {
  padding: $spacing-sm $page-padding;
  background: $color-card;
  border-bottom: 1rpx solid $color-border;
}

.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.note {
  display: block;
  margin: $spacing-md $page-padding 0;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

.summary {
  display: flex;
  margin: 0 $page-padding $spacing-md;
}

.summary__item {
  font-size: $font-xs;
  color: $color-text-hint;
}

.summary__num {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.card {
  padding: $spacing-lg;
  margin: 0 $page-padding $spacing-md;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
}

/* ---------- 我的班级 ---------- */
.c__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.c__title {
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

.c__meta {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.c__actions {
  display: flex;
  margin-top: $spacing-lg;
}

.act {
  flex: 1;
  height: $tap-min;
  margin-right: $spacing-sm;
  border-radius: $radius-md;
  background: $color-bg;
  color: var(--color-primary);
  font-size: $font-sm;
  @include flex-center;
}

.act:last-child {
  margin-right: 0;
}

.act--danger {
  background: rgba(239, 68, 68, 0.08);
  color: $color-danger;
}

/* ---------- 通用行 ---------- */
.row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg;
}

.row--divided {
  border-top: 1rpx solid $color-divider;
}

.row:active {
  background: $color-bg;
}

.row__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.row__title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.row__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
}

/* 拒绝原因（W1.14）：与「申请理由」用颜色区分开 */
.row__reject {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-danger;
  line-height: 1.5;
}

.row__time {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 分数变化 ---------- */
.chg {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.chg__old {
  font-size: $font-base;
  color: $color-text-hint;
  text-decoration: line-through;
  font-variant-numeric: tabular-nums;
}

.chg__new {
  margin-left: 4rpx;
  font-size: $font-lg;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.chg__diff {
  margin-left: $spacing-sm;
  font-size: $font-xs;
}

.chg__diff--up {
  color: $color-success;
}

.chg__diff--down {
  color: $color-danger;
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
  margin: 0 $page-padding;
  padding: 0 $spacing-md;
  height: $tap-min;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.search__input {
  flex: 1;
  min-width: 0;
  margin: 0 $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
}

.search__ph {
  color: $color-text-hint;
}

.search__btn {
  flex-shrink: 0;
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
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

.form__textarea {
  width: 100%;
  min-height: 200rpx;
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
