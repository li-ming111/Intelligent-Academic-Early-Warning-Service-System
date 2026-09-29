<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="预警管理" :show-back="false" />

    <view class="filter-wrap">
      <filter-bar v-model="statusFilter" :options="statusOptions" />
      <filter-bar v-model="levelFilter" :options="levelOptions" />
    </view>

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <!-- 这句话必须放在最上面：教师端**没有**处理入口，
             后端那个 process 接口是纯桩（只回一个 Map，不写库），
             接上去会出现"点了处理、刷新又变回待处理"。 -->
        <view class="notice">
          <app-icon name="alert-circle" :size="34" color="#b45309" />
          <text class="notice__text">教师端为只读视图。预警的受理与处理在辅导员端、学院管理员端完成，教师在这里查看所带班级的预警情况。</text>
        </view>

        <view class="summary">
          <text class="summary__item">共 <text class="summary__num">{{ warnings.length }}</text> 条</text>
          <text class="summary__item">待处理 <text class="summary__num summary__num--warn">{{ pendingCount }}</text> 条</text>
          <text v-if="criticalCount" class="summary__item">严重 <text class="summary__num summary__num--danger">{{ criticalCount }}</text> 条</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="warnings.length ? '没有符合条件的预警' : '暂无预警记录'"
          :hint="warnings.length ? '换个筛选条件试试' : '所带班级没有触发任何预警规则'"
          icon="check"
          :top="60"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(w, i) in filtered"
            :key="w.id"
            class="wrow"
            :class="{ 'wrow--divided': i > 0 }"
            @tap="openDetail(w)"
          >
            <view class="wrow__main">
              <view class="wrow__top">
                <text class="wrow__name">{{ w.studentName || '未知学生' }}</text>
                <text class="wrow__class">{{ w.className }}</text>
              </view>
              <text class="wrow__desc">{{ w.description || '暂无说明' }}</text>
              <text class="wrow__time">{{ w.createdDate }}</text>
            </view>
            <view class="wrow__right">
              <warning-tag :level="w.level" />
              <text class="tag" :class="w.isPending ? 'tag--warning' : 'tag--success'">{{ w.statusText }}</text>
            </view>
          </view>
        </view>
      </template>
    </view>

    <detail-sheet v-model="detailOpen" :title="current ? (current.studentName || '预警详情') : ''" height="72vh">
      <view v-if="current" class="detail">
        <view class="detail__tags">
          <warning-tag :level="current.level" />
          <text class="tag" :class="current.isPending ? 'tag--warning' : 'tag--success'">{{ current.statusText }}</text>
          <text v-if="typeText" class="tag tag--type">{{ typeText }}</text>
        </view>

        <view class="kv">
          <view class="kv__row">
            <text class="kv__k">学生</text>
            <text class="kv__v">{{ current.studentName || '--' }}</text>
          </view>
          <view class="kv__row">
            <text class="kv__k">班级</text>
            <text class="kv__v">{{ current.className || '--' }}</text>
          </view>
          <view class="kv__row">
            <text class="kv__k">触发规则</text>
            <text class="kv__v">{{ current.title || '--' }}</text>
          </view>
          <view class="kv__row">
            <text class="kv__k">生成时间</text>
            <text class="kv__v">{{ current.createdDate || '--' }}</text>
          </view>
        </view>

        <view class="quote">
          <text class="quote__text">{{ current.description || '暂无说明' }}</text>
        </view>

        <text class="note">该预警的等级与状态由系统按预警规则自动判定，教师端不提供修改入口，也不显示处理意见 —— 处理记录归辅导员与学院管理员。</text>
      </view>

      <template #footer>
        <view v-if="current && current.studentId" class="btn-primary" @tap="goPrediction">查看该生学分预测</view>
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
import { normalizeWarnings } from '@/utils/warning.js'
import { WARNING_TYPE_TEXT, dictText } from '@/constants/dict.js'
import { go } from '@/utils/navigate.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const loading = ref(true)
const warnings = ref([])
const statusFilter = ref('all')
const levelFilter = ref('all')
const detailOpen = ref(false)
const current = ref(null)

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '待处理', value: 'pending' },
  { label: '已处理', value: 'handled' }
]

// 取值必须与 `warningLevelKey()` 一致（danger/medium/low）。
// 写成 'high' 是最经典的那个坑：两边永不相等，筛选点了没反应。
const levelOptions = [
  { label: '全部等级', value: 'all' },
  { label: '严重', value: 'danger' },
  { label: '中度', value: 'medium' },
  { label: '轻度', value: 'low' }
]

const pendingCount = computed(() => warnings.value.filter(w => w.isPending).length)
const criticalCount = computed(() => warnings.value.filter(w => w.level === 3).length)

/** 预警类型文案。字典里只有 type=1「成绩预警」，认不出时返回空串把标签整个隐掉，
 *  而不是显示「未知类型」——那会让人以为是数据缺了字段。 */
const typeText = computed(() => dictText(WARNING_TYPE_TEXT, current.value && current.value.type, ''))

/** 两个筛选条件都是"客户端过滤"：接口只支持按 status 过滤，
 *  数据量是几十条量级，一次全取回来再过滤比多次请求更快也更稳。 */
const filtered = computed(() => warnings.value.filter((w) => {
  if (statusFilter.value === 'pending' && !w.isPending) return false
  if (statusFilter.value === 'handled' && w.isPending) return false
  if (levelFilter.value !== 'all' && w.levelKey !== levelFilter.value) return false
  return true
}))

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await teacherAPI.getWarnings(userId).catch((e) => {
      console.error('[teacher warnings] 预警失败:', e)
      return []
    })
    // 级别与状态在响应里是**中文**（"中度"/"待处理"），
    // 一律过 utils/warning.js 归一化，页面里不出现任何 level === 3 之外的比较。
    warnings.value = normalizeWarnings(list)
  } finally {
    loading.value = false
  }
}

function openDetail(w) {
  current.value = w
  detailOpen.value = true
}

function goPrediction() {
  const c = current.value
  detailOpen.value = false
  // `academic_warning.student_id` 存的**就是** student_profile.id，
  // 正好是学分预测接口要的参数，不用再换成学号。
  go(`/pages/teacher/credit-prediction?profileId=${c.studentId}&name=${encodeURIComponent(c.studentName || '')}`)
}
</script>

<style lang="scss" scoped>
.filter-wrap {
  padding: $spacing-md $page-padding $spacing-sm;
  background: $color-card;
  border-bottom: 1rpx solid $color-border;
}

.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

/* ---------- 只读提示 ---------- */
.notice {
  display: flex;
  align-items: flex-start;
  margin: 0 $page-padding $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: rgba(245, 158, 11, 0.1);
}

.notice__text {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: #92400e;
  line-height: 1.5;
}

.summary {
  display: flex;
  margin: 0 $page-padding $spacing-md;
}

.summary__item {
  margin-right: $spacing-xl;
  font-size: $font-xs;
  color: $color-text-hint;
}

.summary__num {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.summary__num--warn {
  color: $color-warning;
}

.summary__num--danger {
  color: $color-danger;
}

.card {
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
}

/* ---------- 预警行 ---------- */
.wrow {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg;
}

.wrow--divided {
  border-top: 1rpx solid $color-divider;
}

.wrow:active {
  background: $color-bg;
}

.wrow__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.wrow__top {
  display: flex;
  align-items: baseline;
}

.wrow__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.wrow__class {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.wrow__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
}

.wrow__time {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.wrow__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.wrow__right .tag {
  margin-top: $spacing-xs;
}

/* ---------- 详情 ---------- */
.detail {
  padding: 0 $page-padding $spacing-2xl;
}

.detail__tags {
  display: flex;
  align-items: center;
  padding-bottom: $spacing-md;
  border-bottom: 1rpx solid $color-divider;
}

.detail__tags .tag {
  margin-left: $spacing-sm;
}

/* 预警类型标签：中性灰。共用样式里没有对应类，就地定义一个，
   不去改 common.scss —— 只有这一处用得到。 */
.tag--type {
  background: rgba(156, 163, 175, 0.16);
  color: #6b7280;
}

.kv {
  margin-top: $spacing-md;
}

.kv__row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-sm 0;
}

.kv__k {
  width: 150rpx;
  flex-shrink: 0;
  font-size: $font-sm;
  color: $color-text-hint;
}

.kv__v {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
}

.quote {
  margin-top: $spacing-md;
  padding: $spacing-md;
  border-left: 6rpx solid $color-warning;
  border-radius: $radius-sm;
  background: $color-bg;
}

.quote__text {
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
}

.note {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}
</style>
