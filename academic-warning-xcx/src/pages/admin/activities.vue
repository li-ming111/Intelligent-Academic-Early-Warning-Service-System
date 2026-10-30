<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="系统动态" />

    <view class="page-body">
      <page-loading v-if="loading" :count="5" mode="card" />

      <template v-else>
        <empty-state
          v-if="!activities.length"
          text="暂无系统动态"
          hint="没有可展示的登录 / 预警 / 导出记录"
          icon="clock"
          :top="80"
        />

        <template v-else>
          <view class="card card--flush">
            <view
              v-for="(a, i) in activities"
              :key="a.type + '-' + i"
              class="act"
              :class="{ 'act--divided': i > 0 }"
            >
              <view class="act__icon">
                <app-icon :name="typeIcon(a.type)" :size="40" />
              </view>
              <view class="act__main">
                <view class="act__top">
                  <text class="act__title">{{ a.title }}</text>
                  <text class="tag" :class="typeTagClass(a.type)">{{ typeLabel(a.type) }}</text>
                </view>
                <text class="act__desc">{{ a.description || '暂无说明' }}</text>
                <text class="act__time">{{ formatDate(a.createdAt, true) }}</text>
              </view>
            </view>
          </view>

          <text class="note">汇总登录、预警与导出三类记录，按时间倒序展示最近 20 条。管理端的关键写操作请到 PC 端「操作日志」查看。</text>
        </template>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'

const themeStyle = roleThemeStyle('admin')

const loading = ref(true)
const activities = ref([])

/** 与后端 activity() 里写的 type 一一对应（与首页 dashboard 同一张对照表）。 */
function typeLabel(type) {
  return { login: '登录', warning: '预警', export: '导出' }[type] || '动态'
}

function typeIcon(type) {
  return { login: 'user', warning: 'alert', export: 'download' }[type] || 'clock'
}

function typeTagClass(type) {
  return { login: 'tag--info', warning: 'tag--warning', export: 'tag--success' }[type] || 'tag--info'
}

onMounted(async () => {
  try {
    const res = await adminAPI.getActivities()
    activities.value = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
  } catch (e) {
    console.error('[admin activities] 列表失败:', e)
    activities.value = []
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

.card--flush {
  padding: 0;
  overflow: hidden;
  @include card;
}

.act {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg;
}

.act--divided {
  border-top: 1rpx solid $color-divider;
}

.act__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  @include flex-center;
  flex-shrink: 0;
}

.act__main {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
}

.act__top {
  display: flex;
  align-items: center;
}

.act__title {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.act__top .tag {
  flex-shrink: 0;
  margin-left: $spacing-sm;
}

.act__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  @include ellipsis;
}

.act__time {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}
</style>
