<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="帮扶计划" />

    <role-hero
      title="学业帮扶"
      subtitle="辅导员为挂科课程安排的补习计划，进度由辅导员更新"
      :stats="heroStats"
    />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!plans.length"
          text="暂无帮扶计划"
          hint="辅导员创建帮扶计划后会出现在这里"
          icon="target"
        />

        <view v-else class="plan-list">
          <view v-for="p in plans" :key="p.id" class="plan-card" @tap="openDetail(p)">
            <view class="plan-card__head">
              <text class="plan-card__title">{{ p.title || '帮扶计划' }}</text>
              <text class="tag" :class="'tag--' + tagTypeOf(p)">{{ p.statusText }}</text>
            </view>

            <text class="plan-card__desc">{{ p.content || p.description || '暂无说明' }}</text>

            <view class="plan-card__bar">
              <view class="plan-track">
                <view class="plan-fill" :style="{ width: p.progress + '%' }"></view>
              </view>
              <text class="plan-card__pct">{{ p.progress }}%</text>
            </view>

            <view class="plan-card__foot">
              <text>创建于 {{ p.createdDate || '--' }}</text>
              <text v-if="p.updatedAt">最近更新 {{ p.updatedDate }}</text>
            </view>
          </view>

          <!-- 帮扶评价是这条链路的下游：学生接受帮扶后要能反馈。
               放在列表底部而不是塞进卡片里，避免"点卡片看详情"和"点卡片去评价"两种手势打架。 -->
          <view class="feedback-entry" @tap="go('/pages/student/assistance-feedback')">
            <view class="feedback-entry__text">
              <text class="feedback-entry__title">帮扶评价</text>
              <text class="feedback-entry__hint">对已完成的帮扶计划提交评价</text>
            </view>
            <app-icon name="chevron" :size="32" color="#aeaeb2" />
          </view>
        </view>
      </template>
    </view>

    <detail-sheet v-model="detailOpen" :title="detail.title || '帮扶计划详情'">
      <view v-if="detail.id" class="detail">
        <view class="detail__row">
          <text class="detail__label">状态</text>
          <text class="tag" :class="'tag--' + tagTypeOf(detail)">{{ detail.statusText }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">完成进度</text>
          <text class="detail__value">{{ detail.progress }}%</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">创建时间</text>
          <text class="detail__value">{{ detail.createdDate || '--' }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">最近更新</text>
          <text class="detail__value">{{ detail.updatedDate || '--' }}</text>
        </view>
        <view class="detail__block">
          <text class="detail__label">计划内容</text>
          <text class="detail__text">{{ detail.content || detail.description || '暂无说明' }}</text>
        </view>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'
import {
  PLAN_STATUS_TEXT, PLAN_STATUS_KEY, STATUS_TAG_TYPE, dictText
} from '@/constants/dict.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const plans = ref([])

const detailOpen = ref(false)
const detail = ref({})

const heroStats = computed(() => {
  const total = plans.value.length
  const active = plans.value.filter(p => p.statusKey === 'active').length
  const done = plans.value.filter(p => p.statusKey === 'done').length
  const avg = total
    ? Math.round(plans.value.reduce((sum, p) => sum + p.progress, 0) / total)
    : 0
  return [
    { label: '计划总数', value: total },
    { label: '进行中', value: active },
    { label: '已完成', value: done },
    { label: '平均进度', value: avg + '%' }
  ]
})

/**
 * 状态文案取本地字典而不是后端给的 `statusText`。
 * `assistance_plans.status` 是 int（0 待开始 / 1 进行中 / 2 已完成），
 * 字典里三个值都核对过来源；后端那份文案在别的表里 `1` 是另一层意思，
 * 一旦有人把文案复制到别处就会串味，不如按 status 现算。
 */
function normalizePlan(raw) {
  const p = raw && typeof raw === 'object' ? raw : {}
  const status = Number(p.status)
  const known = Number.isFinite(status) && PLAN_STATUS_KEY[status] !== undefined
  const progressNum = Number(p.progress)
  const progress = Number.isFinite(progressNum)
    ? Math.max(0, Math.min(100, Math.round(progressNum)))
    : 0
  return {
    ...p,
    id: p.id,
    title: p.title || '',
    content: p.content || p.description || '',
    description: p.description || '',
    progress,
    statusKey: known ? PLAN_STATUS_KEY[status] : 'active',
    statusText: known ? dictText(PLAN_STATUS_TEXT, status) : (p.statusText || '未知'),
    createdDate: formatDate(p.createdAt),
    updatedDate: formatDate(p.updatedAt)
  }
}

function tagTypeOf(p) {
  return STATUS_TAG_TYPE[p && p.statusKey] || 'info'
}

function openDetail(p) {
  detail.value = p
  detailOpen.value = true
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const payload = await studentAPI.getAssistancePlans(userId)
    const list = Array.isArray(payload) ? payload : []
    plans.value = list.map(normalizePlan)
  } catch (e) {
    console.error('[assistance] 加载失败:', e)
    plans.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.plan-list {
  padding: 0 $page-padding;
}

.plan-card {
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  @include card;
}

.plan-card__head {
  display: flex;
  align-items: center;
}

.plan-card__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.plan-card__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.55;
}

.plan-card__bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.plan-track {
  flex: 1;
  height: 12rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.plan-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--gradient-hero, #{$role-student});
}

.plan-card__pct {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary, #{$role-student});
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.plan-card__foot {
  display: flex;
  justify-content: space-between;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 帮扶评价入口 ---------- */
.feedback-entry {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  margin-top: $spacing-sm;
  @include card;
}

.feedback-entry__text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.feedback-entry__title {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.feedback-entry__hint {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 详情弹层 ---------- */
.detail {
  padding: 0 $page-padding $spacing-2xl;
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
</style>
