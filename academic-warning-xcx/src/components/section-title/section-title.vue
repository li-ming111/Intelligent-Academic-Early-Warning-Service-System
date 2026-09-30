<template>
  <view class="section-title" :class="{ 'section-title--plain': plain }">
    <view class="section-title__bar" />
    <text class="section-title__text">{{ title }}</text>
    <!--
      可点与否，由一个**真的存在与否**的元素决定 —— 而不是挂一个没人听的监听器。
      ==========================================================================
      原先这里是 `@tap="$emit('extra')"` 无条件挂着，于是 24 个页面里那些
      纯说明性文字（「共 24 人」「来自 /admin/dashboard」「1 项」「按人去重」）
      也成了点击目标：H5 上是可点的命中区，小程序上带 hover 态，点下去没有任何反应。
      点击区审计把它们全报出来的正是这件事 —— 症结不是"太小"，是"不该可点"。

      条件为什么用 `arrow` 而不是 `$attrs.onExtra`：
        · arrow 是本组件**已经声明的约定**（「箭头 = 可跳转」），
          12 个真跳转的调用点全部传了 arrow，行为完全保持；
        · attrs 那条路在 mp-weixin 上走不通 —— 小程序的 `bind:extra`
          不会出现在 useAttrs() 里，拿它当条件会让 12 个「查看全部」
          在小程序端集体失效。v-if 分支是两端都确定可靠的写法。
    -->
    <view v-if="extra || $slots.extra" class="section-title__extra">
      <view v-if="arrow" class="section-title__hit" @tap="$emit('extra')">
        <slot name="extra">
          <text>{{ extra }}</text>
          <app-icon name="chevron" :size="24" color="#aeaeb2" />
        </slot>
      </view>
      <template v-else>
        <slot name="extra">
          <text>{{ extra }}</text>
        </slot>
      </template>
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

/* 只在 arrow（= 真跳转）时才渲染的命中层。
   tap-area 只接管命中测试、不动字形与行高 —— 这一层若加 padding，
   33 个区块标题行会整体被撑高，改变的是视觉节奏。 */
.section-title__hit {
  @include tap-area;
  display: flex;
  align-items: center;
}
</style>
