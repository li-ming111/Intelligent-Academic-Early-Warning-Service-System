<template>
  <view class="section-title" :class="{ 'section-title--plain': plain }">
    <view class="section-title__bar" />
    <text class="section-title__text">{{ title }}</text>
    <view v-if="extra || $slots.extra" class="section-title__extra" @tap="$emit('extra')">
      <slot name="extra">
        <text>{{ extra }}</text>
        <!-- 箭头只在 arrow 显式打开时出现。
             默认关：绝大多数 extra 是**说明性文字**（「我的 · 班级均值」「近 5 个学期」
             「按已评学分加权 GPA」），画个箭头会让人以为能点进去，点下去没反应。
             箭头 = 可跳转，这条约定得靠 arrow 手动声明来保证。 -->
        <app-icon v-if="arrow" name="chevron" :size="24" color="#aeaeb2" />
      </slot>
    </view>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  /** 右侧说明文字，如「我的 · 班级均值」 */
  extra: { type: String, default: '' },
  /** 右侧是否可点进详情。true 时才画箭头并配 @extra 跳转 */
  arrow: { type: Boolean, default: false },
  /** 去掉左侧色条（用于卡片内部的小标题） */
  plain: { type: Boolean, default: false }
})

defineEmits(['extra'])
</script>

<style lang="scss" scoped>
.section-title {
  display: flex;
  align-items: center;
  margin: $spacing-2xl 0 $spacing-md;
}

.section-title__bar {
  width: 6rpx;
  height: 30rpx;
  margin-right: $spacing-sm;
  border-radius: $radius-pill;
  background: var(--color-primary, #{$role-student});
}

.section-title--plain .section-title__bar {
  display: none;
}

.section-title__text {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.section-title__extra {
  display: flex;
  align-items: center;
  font-size: $font-sm;
  color: $color-text-secondary;
}
</style>
