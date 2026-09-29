<template>
  <!-- 骨架屏。列表类页面首屏用它，比转圈更能说明"这里将会有什么"。
       数据到位后由页面把 loading 置 false 切走，不需要它自己消失。 -->
  <view v-if="loading" class="skeleton">
    <!-- list：头像 + 两行文字，对应绝大多数列表行 -->
    <view v-for="i in count" :key="i" class="skeleton__row">
      <view v-if="mode === 'list'" class="skeleton__avatar shimmer" />
      <view class="skeleton__lines">
        <view class="skeleton__line shimmer" :style="{ width: widths[i % widths.length] }" />
        <view class="skeleton__line skeleton__line--short shimmer" />
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  loading: { type: Boolean, default: true },
  /** list = 带左头像的列表行；card = 纯文字块（详情页用） */
  mode: { type: String, default: 'list' },
  count: { type: Number, default: 5 }
})

const widths = ['62%', '48%', '74%', '56%']
</script>

<style lang="scss" scoped>
.skeleton {
  padding: $spacing-lg $page-padding 0;
}

.skeleton__row {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  @include card;
}

.skeleton__avatar {
  width: 72rpx;
  height: 72rpx;
  margin-right: $spacing-md;
  flex-shrink: 0;
  border-radius: 50%;
}

.skeleton__lines {
  flex: 1;
  min-width: 0;
}

.skeleton__line {
  height: 28rpx;
  border-radius: $radius-sm;
}

.skeleton__line--short {
  width: 36%;
  height: 22rpx;
  margin-top: $spacing-sm;
}

.shimmer {
  background: linear-gradient(90deg, #eef0f3 25%, #f7f8fa 37%, #eef0f3 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
