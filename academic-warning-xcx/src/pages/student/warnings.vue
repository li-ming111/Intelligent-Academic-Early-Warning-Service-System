<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="预警记录" :show-back="false" />

    <role-hero
      :title="userName || '我的预警'"
      subtitle="按预警等级查看，点任意一条看处理详情"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <!-- 等级选项由数据里**实际出现**的等级生成。写死「轻度/中度/严重」三项的话，
         学生只有中度预警时，两个空选项点进去永远是空列表，像功能坏了。 -->
    <filter-bar v-model="levelKey" :options="levelOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!visible.length"
          :text="levelKey === '' ? '暂无预警记录' : `没有${labelOfLevel(levelKey)}预警`"
          :hint="levelKey === ''
            ? '没有预警说明当前没有触发任何预警规则'
            : '换个等级看看，或点「全部」查看所有预警'"
          icon="check"
        />

        <view v-else class="warn-list">
          <view
            v-for="w in visible"
            :key="w.id"
            class="warn-card"
            :class="'warn-card--level-' + (w.level || 0)"
            @tap="openDetail(w)"
          >
            <view class="warn-card__head">
              <text class="warn-card__title">{{ w.title || '学业预警' }}</text>
              <warning-tag :level="w.level" />
            </view>

            <text class="warn-card__desc">{{ w.description || w.title || '暂无说明' }}</text>

            <!-- W39：挂科课程明细（库里真实的关系，不再靠正则从正文里抠） -->
            <view v-if="w.courses.length" class="warn-card__courses">
              <text
                v-for="c in w.courses"
                :key="c.courseId"
                class="course-chip"
              >{{ c.courseName }} {{ fmtScore(c.scoreTotal) }}</text>
            </view>

            <view class="warn-card__meta">
              <view v-if="w.triggerScore !== null" class="warn-card__meta-item">
                <text class="warn-card__meta-label">触发分数</text>
                <score-cell :score="w.triggerScore" />
              </view>
              <view class="warn-card__meta-item">
                <text class="warn-card__meta-label">发生时间</text>
                <text class="warn-card__meta-value">{{ w.createdDate || '--' }}</text>
              </view>
            </view>

            <view class="warn-card__foot">
              <text class="tag" :class="statusTagClass(w)">{{ w.statusText }}</text>
              <!-- 判的是**原始** handledAt，不是格式化后的 handledDate：
                   formatDate 在空值上返回 '--'，那是个非空字符串，拿它做 v-if
                   永远为真，未处理的预警会显示成「-- 处理」。 -->
              <text v-if="w.handledAt" class="warn-card__handled">{{ w.handledDate }} 处理</text>
            </view>
          </view>
        </view>
      </template>
    </view>

    <detail-sheet v-model="detailOpen" :title="detail.title || '预警详情'">
      <view v-if="detail.id" class="detail">
        <view class="detail__row">
          <text class="detail__label">预警等级</text>
          <warning-tag :level="detail.level" />
        </view>
        <view class="detail__row">
          <text class="detail__label">处理状态</text>
          <text class="tag" :class="statusTagClass(detail)">{{ detail.statusText }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">发生时间</text>
          <text class="detail__value">{{ detail.createdDate || '--' }}</text>
        </view>
        <view v-if="detail.triggerScore !== null" class="detail__row">
          <text class="detail__label">触发分数</text>
          <score-cell :score="detail.triggerScore" />
        </view>
        <view v-if="detail.courses && detail.courses.length" class="detail__block">
          <text class="detail__label">挂科课程（{{ detail.courses.length }} 门）</text>
          <view
            v-for="c in detail.courses"
            :key="c.courseId"
            class="detail__course"
          >
            <text class="detail__course-name">{{ c.courseName }}</text>
            <text class="detail__course-score">{{ fmtScore(c.scoreTotal) }} 分</text>
            <text v-if="c.semester" class="detail__course-sem">第 {{ c.semester }} 学期</text>
          </view>
        </view>
        <view class="detail__block">
          <text class="detail__label">预警说明</text>
          <text class="detail__text">{{ detail.description || '暂无说明' }}</text>
        </view>
        <view class="detail__block">
          <text class="detail__label">处理结果</text>
          <text class="detail__text">{{ detail.handleResult || '尚未处理' }}</text>
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
import { normalizeWarnings, countWarnings, warningStatusTagClass } from '@/utils/warning.js'
import { warningLevelText } from '@/utils/score.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const rows = ref([])
/** '' = 全部；其余是 '1' / '2' / '3' */
const levelKey = ref('')

const detailOpen = ref(false)
const detail = ref({})

const summary = computed(() => countWarnings(rows.value))

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

/** 与筛选条一一对应，四个数各自都能在下面点得出来，不出现"数是 3 但筛不出来"的情况。 */
const heroStats = computed(() => {
  const s = summary.value
  return [
    { label: '预警总数', value: s.total },
    { label: '轻度', value: s.level1, onTap: () => pick('1', s.level1) },
    { label: '中度', value: s.level2, onTap: () => pick('2', s.level2) },
    { label: '严重', value: s.level3, onTap: () => pick('3', s.level3) }
  ]
})

const levelOptions = computed(() => {
  const s = summary.value
  const opts = [{ label: '全部', value: '', count: s.total }]
  const pairs = [[1, s.level1], [2, s.level2], [3, s.level3]]
  for (const [level, count] of pairs) {
    // count 为 0 的等级不进筛选条：选项点进去必然是空列表，等于给自己挖坑
    if (count > 0) opts.push({ label: warningLevelText(level), value: String(level), count })
  }
  return opts
})

const visible = computed(() => {
  if (!levelKey.value) return rows.value
  return rows.value.filter(w => String(w.level) === String(levelKey.value))
})

function labelOfLevel(key) {
  return warningLevelText(Number(key))
}

/** 点 hero 上的等级数字 = 切到那个筛选，0 条时不响应（避免切进空列表）。 */
function pick(level, count) {
  if (!count) return
  levelKey.value = levelKey.value === level ? '' : level
}

/** 状态标签配色复用 common.scss 的全局 .tag--* ，不在这里另起一套 --weak 变量。
 *  2026-10-02（W1.6）：改为直接调共用层 —— 原来这里只有"已处理→绿 / 其余→黄"两支，
 *  扩三态后"已解除"会落到黄色（看着像待办）。 */
function statusTagClass(w) {
  return warningStatusTagClass(w && w.status)
}

function openDetail(w) {
  detail.value = w
  detailOpen.value = true
}

/** 分数可能是 56.5 也可能是整数 40 —— 后者别显示成 "40.0"。 */
function fmtScore(s) {
  if (s === null || s === undefined) return '--'
  return String(Number(s))
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const payload = await studentAPI.getWarnings(userId)
    // 归一化负责把 warningLevel(可能是 1/2/3、也可能是「严重」这类中文) 收敛成数字，
    // 页面里一律不再自己比字符串 —— 历史 bug 就是字符串比较导致全部渲染成「轻度」。
    rows.value = normalizeWarnings(payload)
  } catch (e) {
    console.error('[warnings] 加载失败:', e)
    rows.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.warn-list {
  padding: 0 $page-padding;
}

.warn-card {
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  /* 左侧色条按等级走：一眼扫过去就知道哪几条更急 */
  border-left: 6rpx solid $color-pending;
  @include card;
}

.warn-card__courses {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: $spacing-sm;
}

.course-chip {
  font-size: $font-xs;
  color: $color-text-secondary;
  background: $color-divider;
  border-radius: $radius-sm;
  padding: 4rpx 12rpx;
}

.detail__course {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: 10rpx 0;
  border-bottom: 1rpx solid $color-border;
}

.detail__course-name {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
}

.detail__course-score {
  font-size: $font-sm;
  color: $color-danger;
  font-weight: 600;
}

.detail__course-sem {
  font-size: $font-xs;
  color: $color-text-hint;
}

.warn-card--level-1 { border-left-color: $level-1; }
.warn-card--level-2 { border-left-color: $level-2; }
.warn-card--level-3 { border-left-color: $level-3; }

.warn-card__head {
  display: flex;
  align-items: center;
}

.warn-card__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.warn-card__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.55;
}

.warn-card__meta {
  display: flex;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.warn-card__meta-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.warn-card__meta-label {
  font-size: $font-xs;
  color: $color-text-hint;
}

.warn-card__meta-value {
  margin-top: 4rpx;
  font-size: $font-sm;
  color: $color-text;
}

.warn-card__foot {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.warn-card__handled {
  margin-left: $spacing-sm;
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
  border-bottom: 1rpx solid $color-divider;
}

.detail__text {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}
</style>
