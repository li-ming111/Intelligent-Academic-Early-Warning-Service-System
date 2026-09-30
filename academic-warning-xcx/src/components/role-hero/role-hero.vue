<template>
  <view class="hero" :class="{ 'hero--tall': tall }">
    <view class="hero__top">
      <view v-if="avatarText" class="hero__avatar">
        <text>{{ avatarText }}</text>
      </view>
      <view class="hero__text">
        <text class="hero-title">{{ title }}</text>
        <text v-if="subtitle" class="hero-sub">{{ subtitle }}</text>
      </view>
      <slot name="extra" />
    </view>

    <!-- 统计条：五个角色首页的第一屏结构靠它统一 -->
    <view v-if="stats.length" class="hero__stats">
      <view
        v-for="(s, i) in stats"
        :key="s.label"
        class="hero__stat"
        :class="{ 'hero__stat--divided': i > 0 }"
        @tap="s.onTap && s.onTap()"
      >
        <text class="hero__stat-value">{{ s.value }}</text>
        <text class="hero__stat-label">{{ s.label }}</text>
      </view>
    </view>

    <slot />
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  /** 头像位文字（一般取姓名首字）。传空则不显示头像圈 */
  avatarText: { type: String, default: '' },
  /** [{ label, value, onTap? }]，最多 4 项，再多会挤 */
  stats: { type: Array, default: () => [] },
  /** 更大的底部留白，用于让下方卡片上浮压住 hero */
  tall: { type: Boolean, default: false }
})
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  padding: $spacing-xl $page-padding $spacing-2xl;
  background: var(--gradient-hero, #{$role-student});
  color: #fff;
  /* 通栏渐变头 + 底部圆角：圆角够大才看得出是有意为之（24rpx 在 390px 宽上
     只有 12px，看着像没切干净）。 */
  border-bottom-left-radius: 32rpx;
  border-bottom-right-radius: 32rpx;
}

.hero--tall {
  padding-bottom: 96rpx;
}

.hero__top {
  display: flex;
  align-items: center;
}

.hero__avatar {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  margin-right: $spacing-md;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  @include flex-center;
  font-size: $font-xl;
  font-weight: 600;
  color: #fff;
}

.hero__text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* 字号写在这里而不是靠全局类：这两个类名此前在 styles/common.scss 里也有定义
   （已删除），组件的 scoped 样式只提高优先级、挡不住全局规则里没被覆盖的属性，
   靠继承迟早会出岔子。 */
.hero-title {
  font-size: $font-2xl;
  font-weight: 600;
  line-height: 1.3;
  color: #fff;
}

.hero-sub {
  margin-top: $spacing-xs;
  font-size: $font-base;
  color: rgba(255, 255, 255, 0.85);
}

.hero__stats {
  display: flex;
  margin-top: $spacing-xl;
  padding-top: $spacing-lg;
  border-top: 1rpx solid rgba(255, 255, 255, 0.25);
}

.hero__stat {
  min-height: $tap-min;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 用 margin 画分隔线而不是 gap：小程序对 flex gap 有基础库/系统版本门槛 */
.hero__stat--divided {
  border-left: 1rpx solid rgba(255, 255, 255, 0.25);
}

.hero__stat-value {
  font-size: $font-xl;
  font-weight: 600;
  color: #fff;
  line-height: 1.2;
}

.hero__stat-label {
  margin-top: 6rpx;
  font-size: $font-xs;
  color: rgba(255, 255, 255, 0.85);
  @include ellipsis;
  max-width: 100%;
}
</style>
