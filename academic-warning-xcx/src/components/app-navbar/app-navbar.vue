<template>
  <view class="navbar">
    <!-- 用 fixed 而不是 sticky：小程序端 sticky 支持不稳（部分基础库直接失效，
           表现为滚动时导航栏被内容盖住），fixed + 等高占位是确定行为。
           占位块与 fixed 块同高，页面文档流高度才不会塌。 -->
    <view class="navbar__fixed" :class="{ 'navbar__fixed--transparent': transparent }">
      <!-- 状态栏占位：各机型高度不同，必须动态取，写死会在刘海屏上错位 -->
      <view :style="{ height: statusBarHeight + 'px' }" />

      <view class="navbar__bar" :style="{ height: barHeight + 'px' }">
        <view v-if="showBack" class="navbar__side navbar__side--left" @tap="onBack">
          <text class="navbar__back">‹</text>
        </view>
        <text class="navbar__title">{{ title }}</text>
        <!-- 与返回键等宽的占位，让标题真正居中 -->
        <view v-if="showBack" class="navbar__side" />
      </view>
    </view>

    <view
      v-if="placeholder"
      :style="{ height: statusBarHeight + barHeight + 'px' }"
    />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { back } from '@/utils/navigate.js'

const props = defineProps({
  title: { type: String, default: '' },
  /** 是否显示返回箭头。首页传 false */
  showBack: { type: Boolean, default: true },
  /** 透明底（压在 hero 渐变上时用），此时文字自动转白 */
  transparent: { type: Boolean, default: false },
  /** 是否输出等高占位块。压在 hero 上时应传 false，由 hero 自己留顶部空间 */
  placeholder: { type: Boolean, default: true },
  /** 覆盖返回行为；不传则走 utils/navigate.js 的 back() */
  onBackTap: { type: Function, default: null }
})

const statusBarHeight = ref(20)
const barHeight = ref(44)

onMounted(() => {
  // getWindowInfo 是新 API，旧基础库没有，容错回退。
  // 不用 uni.getSystemInfoSync：小程序端已废弃且会打警告。
  let info = null
  try {
    info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
  } catch (e) {
    info = null
  }
  if (info && info.statusBarHeight) statusBarHeight.value = info.statusBarHeight

  // #ifdef MP-WEIXIN
  // 微信端用胶囊按钮位置反推导航栏高度：
  // barHeight = (胶囊 top - 状态栏高) * 2 + 胶囊高
  try {
    const rect = uni.getMenuButtonBoundingClientRect()
    if (rect && rect.height && rect.top) {
      barHeight.value = (rect.top - statusBarHeight.value) * 2 + rect.height
    }
  } catch (e) {
    /* 取不到就用默认 44 */
  }
  // #endif
})

function onBack() {
  if (props.onBackTap) props.onBackTap()
  else back()
}
</script>

<style lang="scss" scoped>
.navbar__fixed {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: $z-navbar;
  background: $color-card;
  /* 底色与页面背景同为白/浅色时不需要分割线；有了下面这条 1rpx 线，
     浅色卡片内容滚到导航栏下方时边界才看得清 */
  border-bottom: 1rpx solid $color-divider;
}

.navbar__fixed--transparent {
  background: transparent;
  border-bottom-color: transparent;

  .navbar__title,
  .navbar__back {
    color: #fff;
  }
}

.navbar__bar {
  display: flex;
  align-items: center;
  padding: 0 $page-padding;
}

.navbar__side {
  /* 可点区域 ≥ 88rpx，箭头本身很小，靠这一格撑开 */
  width: $tap-min;
  height: $tap-min;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.navbar__side--left {
  margin-left: -#{$spacing-md};
}

.navbar__back {
  font-size: 56rpx;
  line-height: 1;
  color: $color-text;
  margin-top: -8rpx;
}

.navbar__title {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}
</style>
