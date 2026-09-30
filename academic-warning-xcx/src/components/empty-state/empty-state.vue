<template>
  <view class="empty" :style="{ paddingTop: top + 'rpx', paddingBottom: top + 'rpx' }">
    <app-icon :name="icon" :size="120" color="#d1d5db" :weight="1.2" />
    <text class="empty__text">{{ text }}</text>
    <text v-if="hint" class="empty__hint">{{ hint }}</text>
    <view v-if="actionText" class="empty__action" @tap="$emit('action')">
      <text>{{ actionText }}</text>
    </view>
    <slot />
  </view>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  /** 第二行灰字，用来说明"为什么没有"，如「本学期尚未产生预警记录」 */
  hint: { type: String, default: '' },
  icon: { type: String, default: 'empty' },
  /** 给了就渲染一个主色按钮 */
  actionText: { type: String, default: '' },
  /** 上下留白，rpx */
  top: { type: [Number, String], default: 120 }
})

defineEmits(['action'])
</script>

<style lang="scss" scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-left: $page-padding;
  padding-right: $page-padding;
}

.empty__text {
  margin-top: $spacing-md;
  font-size: $font-base;
  color: $color-text-secondary;
}

.empty__hint {
  margin-top: $spacing-xs;
  font-size: $font-sm;
  color: $color-text-hint;
  text-align: center;
  line-height: 1.6;
}

.empty__action {
  margin-top: $spacing-xl;
  min-width: 220rpx;
  height: $tap-min;
  padding: 0 $spacing-2xl;
  border-radius: $radius-pill;
  background: var(--color-primary, #{$role-student});
  color: #fff;
  font-size: $font-base;
  @include flex-center;
}
</style>
