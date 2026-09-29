<template>
  <image
    v-if="src"
    class="app-icon"
    :class="'app-icon--' + name"
    :src="src"
    :style="boxStyle"
    mode="aspectFit"
  />
</template>

<script setup>
import { computed } from 'vue'
import { themeOf } from '@/composables/useRoleTheme.js'
import { iconSvg } from '@/constants/icons.js'

/**
 * 线性图标组件 —— 全工程**唯一**的图标出口，用来彻底去掉 emoji。
 *
 * 为什么不用图片文件：图标要跟随角色主题色变化，用 PNG 就得按角色出五套；
 * 这里在运行时把颜色拼进 SVG 再转成 data URI，一个图标组件覆盖所有配色。
 * 微信小程序 <image> 的官方支持格式含 SVG，data URI 亦可。
 *
 * 例外是原生 tabBar：它只吃图片文件，吃不了 data URI，所以底栏那 8 个图标
 * 由 `tools/gen-tab-icons.mjs` 用同一份路径表栅格化成 PNG。
 *
 * 注意 data URI 必须 percent-encode：SVG 里的 `#` 是颜色值的前导符，
 * 不编码会被当成 URL 的 fragment，图标会整块消失。
 */

const props = defineProps({
  /** 见 `constants/icons.js` 的 ICON_PATHS */
  name: { type: String, required: true },
  /** 尺寸，rpx */
  size: { type: [Number, String], default: 40 },
  /** 描边颜色。留空 = 取当前登录角色的主色；tab 未选中态等场景显式传灰 */
  color: { type: String, default: '' },
  /** 描边粗细，24x24 视口下的值 */
  weight: { type: [Number, String], default: 1.8 },
  /** 次要色（双色图标用），暂未使用，留作扩展 */
  secondary: { type: String, default: '' }
})

/**
 * 颜色必须在**运行时**解析成字面值再拼进 SVG。
 * SVG 作为 data URI 是被独立光栅化的，CSS 变量（--color-primary）在里面
 * 不会解析，写了也只会得到透明或黑色。所以默认色走 ROLE_THEME 查表，
 * 而不是用 var()。
 */
const stroke = computed(() => {
  if (props.color) return props.color
  const theme = themeOf(uni.getStorageSync('role'))
  return (theme && theme.primary) || '#4caf50'
})

const src = computed(() => {
  const svg = iconSvg(props.name, stroke.value, props.weight)
  if (!svg) return ''
  return 'data:image/svg+xml,' + encodeURIComponent(svg)
})

const boxStyle = computed(() => `width:${props.size}rpx;height:${props.size}rpx;`)
</script>

<style lang="scss" scoped>
.app-icon {
  display: block;
  flex-shrink: 0;
}
</style>
