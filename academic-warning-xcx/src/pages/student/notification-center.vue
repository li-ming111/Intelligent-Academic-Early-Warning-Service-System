<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="通知中心" />

    <view class="head">
      <view class="head__main">
        <text class="head__num">{{ unreadCount }}</text>
        <text class="head__label">条未读通知</text>
      </view>
      <view
        class="head__action"
        :class="{ 'head__action--disabled': !unreadCount }"
        @tap="markAllRead"
      >
        全部已读
      </view>
    </view>

    <filter-bar v-model="filterKey" :options="filterOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!visible.length"
          :text="filterKey === 'unread' ? '没有未读通知' : '暂无通知'"
          :hint="filterKey === 'unread'
            ? '所有通知都已读过了'
            : '当你的成绩触发预警规则时，系统会在这里推送通知'"
          icon="bell"
        />

        <view v-else class="notify-list">
          <view
            v-for="n in visible"
            :key="n.id"
            class="notify-card"
            :class="{ 'notify-card--unread': !n.isRead }"
            @tap="openDetail(n)"
          >
            <view class="notify-card__head">
              <view class="notify-card__dot" />
              <text class="notify-card__title">{{ n.title }}</text>
              <text class="tag tag--info">{{ typeText(n.type) }}</text>
            </view>
            <text class="notify-card__content">{{ n.content }}</text>
            <view class="notify-card__foot">
              <text class="notify-card__time">{{ formatted(n.createdAt) }}</text>
              <view class="notify-card__ops">
                <text v-if="!n.isRead" class="notify-card__op" @tap.stop="markOne(n)">标记已读</text>
                <text class="notify-card__op notify-card__op--danger" @tap.stop="confirmDelete(n)">删除</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>

    <detail-sheet v-model="detailOpen" title="通知详情">
      <view v-if="detail.id" class="detail">
        <view class="detail__row">
          <text class="detail__label">类型</text>
          <text class="tag tag--info">{{ typeText(detail.type) }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">推送渠道</text>
          <text class="detail__value">{{ channelText(detail.pushChannel) }}</text>
        </view>
        <view class="detail__row">
          <text class="detail__label">时间</text>
          <text class="detail__value">{{ formatted(detail.createdAt) }}</text>
        </view>
        <view class="detail__block">
          <text class="detail__title">{{ detail.title }}</text>
          <text class="detail__text">{{ detail.content }}</text>
        </view>

        <view v-if="detail.warningId" class="detail__link" @tap="goWarning">
          <text class="detail__link-text">查看关联的预警记录</text>
          <app-icon name="chevron" :size="28" />
        </view>
      </view>

      <template #footer>
        <view class="btn-primary" @tap="detailOpen = false">知道了</view>
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
import { go } from '@/utils/navigate.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const rows = ref([])
const unreadCount = ref(0)
/** '' = 全部；'unread' = 只看未读 */
const filterKey = ref('')

const detailOpen = ref(false)
const detail = ref({})

const filterOptions = computed(() => [
  { label: '全部', value: '', count: rows.value.length },
  { label: '未读', value: 'unread', count: rows.value.filter(n => !n.isRead).length }
])

const visible = computed(() => {
  if (filterKey.value === 'unread') return rows.value.filter(n => !n.isRead)
  return rows.value
})

function formatted(v) {
  return formatDate(v, true)
}

/** notifications.type 是 varchar，库里现存 'warning'。认不出时原样回显，不吞成"未知"。 */
function typeText(type) {
  const map = { warning: '学业预警', system: '系统通知', message: '消息' }
  if (!type) return '通知'
  return map[String(type)] || String(type)
}

function channelText(ch) {
  const map = { app: '站内推送', email: '邮件', sms: '短信', wechat: '微信' }
  if (!ch) return '站内推送'
  return map[String(ch)] || String(ch)
}

function openDetail(n) {
  detail.value = n
  detailOpen.value = true
  // 打开即视为已读：用户确实看到内容了，比"再点一次标记已读"更符合直觉
  if (!n.isRead) markOne(n, true)
}

function goWarning() {
  detailOpen.value = false
  go('/pages/student/warnings')
}

/**
 * 单条标记已读。
 *
 * ⚠️ 必须走 **mark-batch-read**。`POST /notification-center/{id}/mark-read`
 * 在 Controller 里是 `return Map.of("success", true)`，一行库都不写 ——
 * 调它会提示成功、退出再进还是未读。batch 接口才真的执行 UPDATE。
 */
async function markOne(n, silent = false) {
  try {
    const res = await studentAPI.markRead(userId, n.id)
    if (res && res.success === false) {
      if (!silent) uni.showToast({ title: res.message || '操作失败', icon: 'none' })
      return
    }
    // 本地先改，避免整页重载造成闪烁；失败时下面的 load() 会纠正回来
    n.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (!silent) uni.showToast({ title: '已标记', icon: 'success' })
  } catch (e) {
    console.error('[notify] 标记已读失败:', e)
    if (!silent) uni.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
  }
}

async function markAllRead() {
  const unread = rows.value.filter(n => !n.isRead)
  if (!unread.length) return
  try {
    const res = await studentAPI.markBatchRead(userId, unread.map(n => n.id))
    if (res && res.success === false) {
      uni.showToast({ title: res.message || '操作失败', icon: 'none' })
      return
    }
    rows.value.forEach(n => { n.isRead = 1 })
    unreadCount.value = 0
    uni.showToast({ title: '全部已读', icon: 'success' })
  } catch (e) {
    console.error('[notify] 全部已读失败:', e)
    uni.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
  }
}

function confirmDelete(n) {
  uni.showModal({
    title: '删除通知',
    content: `确定删除「${n.title}」吗？`,
    success: res => {
      if (res.confirm) doDelete(n)
    }
  })
}

async function doDelete(n) {
  try {
    const res = await studentAPI.deleteNotification(n.id)
    if (res && res.success === false) {
      uni.showToast({ title: res.message || '删除失败', icon: 'none' })
      return
    }
    uni.showToast({ title: '已删除', icon: 'success' })
    detailOpen.value = false
    await load()
  } catch (e) {
    console.error('[notify] 删除失败:', e)
    uni.showToast({ title: '删除失败，请稍后重试', icon: 'none' })
  }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    /**
     * ⚠️ 分页返回体里的 `total` **恒为 0**，而 `list` 是有数据的
     * （实测 total=0 / list.length=2）。所以这里读 `list`，**不读 `total`**，
     * 也不做无限滚动 —— 用 total 驱动会立刻显示"没有更多了"。
     * pageSize 给足 50，本角色的通知量级（每学期几条）一次就能取完。
     */
    const [pageData, countData] = await Promise.all([
      studentAPI.getNotifications(userId, 1, 50).catch(e => { console.error('[notify] 列表加载失败:', e); return null }),
      studentAPI.getUnreadCount(userId).catch(e => { console.error('[notify] 未读数加载失败:', e); return null })
    ])

    const list = pageData && Array.isArray(pageData.list) ? pageData.list : []
    rows.value = list.map(n => ({ ...n, isRead: Number(n.isRead) === 1 ? 1 : 0 }))
    unreadCount.value = countData && countData.unreadCount != null
      ? Number(countData.unreadCount)
      : rows.value.filter(n => !n.isRead).length
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
/* ---------- 顶部统计 ---------- */
.head {
  display: flex;
  align-items: center;
  padding: $spacing-xl $page-padding;
  margin: 0 $page-padding;
  background: $color-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
}

.head__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.head__num {
  font-size: $font-2xl;
  font-weight: 700;
  line-height: 1;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.head__label {
  margin-left: $spacing-xs;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.head__action {
  flex-shrink: 0;
  height: 60rpx;
  padding: 0 $spacing-lg;
  border: 1rpx solid var(--color-primary);
  border-radius: $radius-pill;
  font-size: $font-sm;
  color: var(--color-primary);
  @include flex-center;
}

.head__action--disabled {
  border-color: $color-border;
  color: $color-text-hint;
}

/* ---------- 列表 ---------- */
.page-body {
  padding-bottom: $spacing-3xl;
}

.notify-list {
  padding: 0 $page-padding;
}

.notify-card {
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  @include card;
}

/* 未读用左侧色条区分，而不是整块变色 —— 整块变色在列表里非常吵 */
.notify-card--unread {
  border-left: 6rpx solid var(--color-primary);
}

.notify-card__head {
  display: flex;
  align-items: center;
}

.notify-card__dot {
  width: 12rpx;
  height: 12rpx;
  margin-right: $spacing-sm;
  border-radius: 50%;
  background: $color-border;
  flex-shrink: 0;
}

.notify-card--unread .notify-card__dot {
  background: var(--color-primary);
}

.notify-card__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.notify-card__content {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.55;
  @include ellipsis-multi(2);
}

.notify-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-md;
}

.notify-card__time {
  font-size: $font-xs;
  color: $color-text-hint;
}

.notify-card__ops {
  display: flex;
  align-items: center;
}

.notify-card__op {
  margin-left: $spacing-lg;
  font-size: $font-xs;
  color: var(--color-primary);
}

.notify-card__op--danger {
  color: $color-danger;
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
  padding: $spacing-lg 0 0;
}

.detail__title {
  display: block;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.detail__text {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-base;
  color: $color-text-secondary;
  line-height: 1.7;
}

.detail__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $tap-min;
  padding: 0 $spacing-md;
  margin-top: $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.detail__link-text {
  font-size: $font-sm;
  color: var(--color-primary);
  font-weight: 500;
}
</style>
