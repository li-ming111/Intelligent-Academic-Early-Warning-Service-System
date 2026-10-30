<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="帮扶情况" />

    <filter-bar v-model="statusFilter" :options="statusOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" mode="card" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ rows.length }}</text> 个帮扶计划 ·
            进行中 <text class="summary__num summary__num--danger">{{ activeCount }}</text> 个
          </text>
          <text class="summary__hint">状态筛选在本地做</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="rows.length ? '这个状态下没有计划' : '本院暂无帮扶计划'"
          :hint="rows.length ? '换一个状态看看' : '本院辅导员还没有为学生制定帮扶计划'"
          icon="check"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(r, i) in filtered"
            :key="r.id"
            class="row"
            :class="{ 'row--divided': i > 0 }"
            @tap="openDetail(r)"
          >
            <view class="row__head">
              <text class="row__name">{{ r.studentName || '未知学生' }}</text>
              <text class="tag" :class="`tag--${r.tagType}`">{{ r.statusText }}</text>
            </view>
            <text class="row__desc">{{ r.content || '（无计划内容）' }}</text>
            <view class="row__progress">
              <view class="bar">
                <view class="bar__fill" :style="{ width: r.progress + '%' }" />
              </view>
              <text class="row__progress-num">{{ r.progress }}%</text>
            </view>
            <view class="row__foot">
              <text class="row__class">{{ r.className || '未分班' }}</text>
              <text class="row__time">{{ formatDate(r.updatedAt || r.createdAt, true) }}</text>
            </view>
          </view>
        </view>

        <text class="note">仅显示本学院班级学生的帮扶计划；本页仅供查看，计划调整请联系辅导员。</text>
      </template>
    </view>

    <!-- ============ 详情 ============ -->
    <detail-sheet v-model="sheetOpen" title="帮扶计划" height="64vh">
      <view v-if="current" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">学生</text>
            <text class="facts__v">{{ current.studentName || '未知' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">学号</text>
            <text class="facts__v">{{ current.studentNo || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">班级</text>
            <text class="facts__v">{{ current.className || '未分班' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">负责辅导员</text>
            <text class="facts__v">{{ current.counselorName || '未取到' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">计划状态</text>
            <text class="facts__v">{{ current.statusText }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">完成进度</text>
            <text class="facts__v">{{ current.progress }}%</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">制定时间</text>
            <text class="facts__v">{{ formatDate(current.createdAt, true) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">最近更新</text>
            <text class="facts__v">{{ formatDate(current.updatedAt, true) }}</text>
          </view>
        </view>

        <section-title plain title="计划内容" />
        <text class="paragraph">{{ current.content || '（无）' }}</text>

        <template v-if="current.progressNote">
          <section-title plain title="进度说明" />
          <text class="paragraph">{{ current.progressNote }}</text>
        </template>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import {
  PLAN_STATUS_TEXT, PLAN_STATUS_KEY, STATUS_TAG_TYPE, dictText
} from '@/constants/dict.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

// `assistance_plans.status`：0 待开始 / 1 进行中 / 2 已完成（见 constants/dict.js）
const statusOptions = [
  { label: '全部', value: '' },
  { label: '待开始', value: '0' },
  { label: '进行中', value: '1' },
  { label: '已完成', value: '2' }
]

const loading = ref(true)
const rows = ref([])
const statusFilter = ref('')

const sheetOpen = ref(false)
const current = ref(null)

const filtered = computed(() => {
  if (!statusFilter.value) return rows.value
  return rows.value.filter(r => String(r.status) === statusFilter.value)
})

/** 「进行中」= status 1。别写成 `!== 2` —— 那会把"待开始"也算进去。 */
const activeCount = computed(() => rows.value.filter(r => r.status === 1).length)

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

/**
 * 归一化一条计划。
 *
 * 状态文案取**本地字典**而不是后端字段：`assistance_plans.status` 是 int，
 * 字典里三个值都核对过来源；同一个 int 在别的表里是另一层意思（见 dict.js 抬头），
 * 所以按 status 现算，不信任任何"顺带回过来的文案"。
 */
function normalizePlan(raw) {
  const p = raw && typeof raw === 'object' ? raw : {}
  const status = Number(p.status)
  const known = Number.isFinite(status) && PLAN_STATUS_KEY[status] !== undefined
  const progressNum = Number(p.progress)
  const progress = Number.isFinite(progressNum)
    ? Math.max(0, Math.min(100, Math.round(progressNum)))
    : 0
  const statusKey = known ? PLAN_STATUS_KEY[status] : 'pending'
  return {
    ...p,
    id: p.id,
    content: p.content || '',
    progressNote: p.progressNote || '',
    progress,
    status,
    statusKey,
    statusText: known ? dictText(PLAN_STATUS_TEXT, status) : '未知',
    tagType: STATUS_TAG_TYPE[statusKey] || 'info'
  }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await collegeAdminAPI.getAssistancePlans(userId)
    // 进行中置顶，其余按最近更新倒序
    rows.value = (Array.isArray(list) ? list : []).map(normalizePlan).sort((a, b) => {
      const pa = a.status === 1 ? 0 : 1
      const pb = b.status === 1 ? 0 : 1
      if (pa !== pb) return pa - pb
      return String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || ''))
    })
  } catch (e) {
    console.error('[college-admin assistance] 帮扶计划失败:', e)
    rows.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(r) {
  current.value = r
  sheetOpen.value = true
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

.row {
  /* 全局 common.scss 里有 `.row { display: flex }`，不显式覆盖会把这几块挤成一行 */
  display: block;
  padding: $spacing-lg;
}

.row--divided {
  border-top: 1rpx solid $color-divider;
}

.row:active {
  background: $color-bg;
}

.row__head {
  display: flex;
  align-items: center;
}

.row__name {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.row__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
  @include ellipsis-multi(3);
}

/* ---------- 进度条 ---------- */
.row__progress {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.bar {
  flex: 1;
  min-width: 0;
  height: 12rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.row__progress-num {
  flex-shrink: 0;
  width: 90rpx;
  margin-left: $spacing-sm;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.row__foot {
  display: flex;
  align-items: baseline;
  margin-top: $spacing-sm;
}

.row__class {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: var(--color-primary);
  @include ellipsis;
}

.row__time {
  flex-shrink: 0;
  margin-left: $spacing-sm;
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
  width: 200rpx;
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
  word-break: break-all;
}

.paragraph {
  display: block;
  padding: $spacing-md $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  line-height: 1.7;
  color: $color-text;
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
</style>
