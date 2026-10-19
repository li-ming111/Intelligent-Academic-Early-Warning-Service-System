<template>
  <view class="tag" :class="cls"><text v-if="shape" class="tag__shape">{{ shape }}</text>{{ display }}</view>
</template>

<script setup>
import { computed } from 'vue'
import { warningLevel, warningLevelText } from '@/utils/score.js'
import { pickField } from '@/utils/response.js'

/**
 * 预警等级标签。
 *
 * 三类入参都吃：
 *   <warning-tag :warning="row" />   整行（最常见）
 *   <warning-tag :level="3" />       只有等级
 *   <warning-tag :text="自定义" />    纯文案标签
 *
 * 等级必须过 `warningLevel()`。后端 `warningLevel` 在
 * `/teachers/warnings/{id}` 是**中文**（"严重"）、在 `/students/warnings/{id}`
 * 是**数字**（2）—— 页面自己写 `=== 3` 或 `=== '严重'` 一定有一端永远不成立，
 * 历史 bug 就是全表渲染成"轻度"。
 */
const props = defineProps({
  warning: { type: Object, default: null },
  level: { type: [String, Number], default: null },
  /** 覆盖显示文案 */
  text: { type: String, default: '' },
  /** 只显示文案、不带等级底色（用于「已处理」这类中性标签） */
  plain: { type: Boolean, default: false }
})

const resolved = computed(() => {
  if (props.level !== null && props.level !== undefined && props.level !== '') {
    return warningLevel(props.level)
  }
  if (props.warning) {
    // 依次找 warningLevel / warning_level / level，取到第一个非空值
    const raw = pickField(props.warning, 'warningLevel', 'warning_level', 'level')
    return warningLevel(raw)
  }
  return 0
})

const display = computed(() => {
  if (props.text) return props.text
  return resolved.value ? warningLevelText(resolved.value) : '未知'
})

/**
 * 等级形状前缀：▲严重 / ●中度 / ■轻度。
 * 轻度（琥珀 #f59e0b）与中度（橙 #f97316）两个色相太近，色弱用户无法区分，
 * 用「色 + 形」双编码补一档。只在有等级时显示（纯文案/中性标签不加）。
 */
const shape = computed(() => {
  if (props.plain || props.text) return ''
  return { 3: '▲', 2: '●', 1: '■' }[resolved.value] || ''
})

/**
 * 类名直接用 level 数字（tag--level-1/2/3），与 styles/common.scss 的同名类一一对应。
 * 不走 score.js 的 `warningLevelKey()` —— 那个产出 danger/medium/low，
 * 与本文件的类名不同源，多一次映射就多一个写错的地方。
 * level 认不出时落 `tag--pending`（中性灰），不落红色：未知不等于严重。
 */
const cls = computed(() => {
  if (props.plain) return 'tag--pending'
  if (resolved.value >= 3) return 'tag--level-3'
  if (resolved.value === 2) return 'tag--level-2'
  if (resolved.value === 1) return 'tag--level-1'
  return 'tag--pending'
})
</script>

<style lang="scss" scoped>
/* 形状前缀比正文小一号、字距收一点，视觉上是记号不是文字 */
.tag__shape {
  margin-right: 6rpx;
  font-size: $font-xs;
}
</style>
