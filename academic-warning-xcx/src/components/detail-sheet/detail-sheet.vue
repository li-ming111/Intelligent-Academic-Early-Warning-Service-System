<template>
  <view v-if="modelValue" class="sheet-mask" @tap="close">
    <!-- @tap.stop 在小程序里编译成 catchtap，能拦住冒泡到遮罩，否则点内容就关 -->
    <view class="sheet" :style="{ height: height }" @tap.stop>
      <view class="sheet__handle" />
      <view class="sheet__head">
        <text class="sheet__title">{{ title }}</text>
        <view class="sheet__close" @tap="close">
          <app-icon name="close" :size="32" color="#86868b" />
        </view>
      </view>

      <!-- 内容用 scroll-view，保证长内容能滚而不是撑破 80vh -->
      <scroll-view class="sheet__body" scroll-y>
        <slot />
      </scroll-view>

      <view v-if="$slots.footer" class="sheet__footer">
        <slot name="footer" />
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 底部抽屉。列表页点某一项看详情时用它，避免为每条记录开一个独立页面
 * （小程序分包体积和页面栈都是稀缺资源）。
 *
 * 用法：<detail-sheet v-model="visible" title="预警详情"> ... </detail-sheet>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '详情' },
  /** 抽屉高度，默认 70vh；内容少时传 'auto' 会退化成固定高度，谨慎用 */
  height: { type: String, default: '70vh' },
  /** 点遮罩是否关闭 */
  maskClosable: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'close'])

function close() {
  if (!props.maskClosable) return
  emit('update:modelValue', false)
  emit('close')
}
</script>

<style lang="scss" scoped>
.sheet-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: $z-sheet;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  display: flex;
  flex-direction: column;
  background: $color-card;
  border-top-left-radius: $radius-xl;
  border-top-right-radius: $radius-xl;
  /* 上滑入场。小程序支持 transform 过渡 */
  animation: sheet-up 0.22s ease-out;
}

@keyframes sheet-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.sheet__handle {
  width: 72rpx;
  height: 8rpx;
  margin: $spacing-md auto 0;
  border-radius: $radius-pill;
  background: $color-border;
}

.sheet__head {
  display: flex;
  align-items: center;
  padding: $spacing-md $page-padding;
  border-bottom: 1rpx solid $color-divider;
}

.sheet__title {
  flex: 1;
  min-width: 0;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.sheet__close {
  width: $tap-min;
  height: $tap-min;
  /* 之前这里靠一个 -12rpx 的负外边距把 64rpx 的按钮视觉上顶到右边缘。
     按钮尺寸提到 $tap-min 之后补偿量就是 0，负外边距要一起去掉 ——
     只改尺寸不改这里，图标会从右边缘往里缩 12rpx。 */
  margin-right: 0;
  @include flex-center;
}

.sheet__body {
  flex: 1;
  min-height: 0;
  padding: 0 $page-padding;
}

.sheet__footer {
  padding: $spacing-md $page-padding;
  padding-bottom: calc(#{$spacing-md} + env(safe-area-inset-bottom));
  border-top: 1rpx solid $color-divider;
}
</style>
