<template>
  <text class="score" :class="['score--' + cls, large ? 'score--large' : '']">{{ text }}</text>
</template>

<script setup>
import { computed } from 'vue'
import { scoreClass, scoreText, isPending } from '@/utils/score.js'

/**
 * 成绩数值单元格 —— 全工程**唯一**渲染分数的地方。
 *
 * 存在的意义是把"期末未录入"和"考了 0 分"这两件事在视觉上彻底分开：
 * JS 里 `null < 60` 是 true（null 转 0）、`Number(null) === 0`，
 * 所以未录入的行只要参与任何比较就会被判成挂科、渲染成红色 0 分。
 * 这里一律先过 `scoreOf` + 判定，未录入走灰色 `待录入`。
 */
const props = defineProps({
  /** 整行成绩对象，或裸分数值 */
  score: { type: [Object, Number, String], default: null },
  /** 加大字号，用于详情页的主成绩 */
  large: { type: Boolean, default: false },
  /** 强制指定样式类（如整行已判定挂科但总分已录入时） */
  forceClass: { type: String, default: '' }
})

const text = computed(() => scoreText(props.score))

const cls = computed(() => {
  if (props.forceClass) return props.forceClass
  // 整行判定为"待录入"时，无论总分看起来是多少都走灰色 ——
  // 期末没录入，总评这个数本身就不可信
  if (props.score && typeof props.score === 'object' && isPending(props.score)) return 'pending'
  // scoreClass 内部已用 scoreOf 收敛，整行与裸值都吃
  return scoreClass(props.score)
})
</script>

<style lang="scss" scoped>
.score {
  font-size: $font-md;
  /* line-height 归一到 1，盒高才等于字号。否则 44rpx 的总评会被默认行高
     撑到 60rpx 以上，落进 56rpx 的等高容器里就会顶部溢出、标签错行。 */
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.score--large {
  font-size: $font-2xl;
}
</style>
