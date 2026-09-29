<template>
  <view class="stat-card" :class="{ 'stat-card--tap': tapable }" @tap="$emit('tap')">
    <view v-if="icon" class="stat-card__icon">
      <app-icon :name="icon" :size="36" :color="iconColor" />
    </view>
    <view class="stat-card__body">
      <view class="stat-card__value-row">
        <text class="stat-card__value" :style="valueColor ? { color: valueColor } : {}">{{ value }}</text>
        <text v-if="unit" class="stat-card__unit">{{ unit }}</text>
      </view>
      <text class="stat-card__label">{{ label }}</text>
      <text v-if="hint" class="stat-card__hint">{{ hint }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '--' },
  /** 数值后缀，如「人」「门」「%」 */
  unit: { type: String, default: '' },
  /** 副说明，如「较上学期 +0.2」 */
  hint: { type: String, default: '' },
  /** app-icon 的图标名 */
  icon: { type: String, default: '' },
  /** 图标色，留空取角色主色 */
  iconColor: { type: String, default: '' },
  /** 数值色。**只有确实要强调时才传**，如预警数用 --color-danger；
      「待录入」之类的空态不要传红色（见 score-cell） */
  valueColor: { type: String, default: '' },
  /** 显示右侧箭头并让整卡可点 */
  tapable: { type: Boolean, default: false }
})

defineEmits(['tap'])
</script>

<style lang="scss" scoped>
.stat-card {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  @include card;
}

.stat-card--tap:active {
  background: $color-bg;
}

.stat-card__icon {
  width: 72rpx;
  height: 72rpx;
  margin-right: $spacing-md;
  flex-shrink: 0;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  @include flex-center;
}

.stat-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.stat-card__value-row {
  display: flex;
  align-items: baseline;
}

.stat-card__value {
  font-size: $font-2xl;
  font-weight: 600;
  line-height: 1.1;
  color: $color-text;
}

.stat-card__unit {
  margin-left: 4rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.stat-card__label {
  margin-top: 4rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include ellipsis;
}

.stat-card__hint {
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}
</style>
