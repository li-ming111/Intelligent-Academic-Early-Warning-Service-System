<template>
  <view class="filter-bar">
    <!-- 横向 chip 行：学期、等级、状态这类"三五项选一"的筛选一律用它，
         不引入下拉弹层。项数少时可横滑，项数多时也不换行，高度恒定。 -->
    <scroll-view
      v-if="options.length"
      class="filter-bar__scroll"
      scroll-x
      :show-scrollbar="false"
      :enhanced="true"
    >
      <view class="filter-bar__row">
        <view
          v-for="o in options"
          :key="String(o.value)"
          class="filter-bar__chip"
          :class="{ 'filter-bar__chip--active': isActive(o.value) }"
          @tap="pick(o.value)"
        >
          <text>{{ o.label }}</text>
          <text v-if="o.count !== undefined && o.count !== null" class="filter-bar__count">
            {{ o.count }}
          </text>
        </view>
      </view>
    </scroll-view>

    <slot />
  </view>
</template>

<script setup>
/**
 * 筛选条。普通 chip 组（可多选）。
 *
 * 用法：
 *   <filter-bar v-model="level" :options="[{label:'全部', value:''}, ...]" />
 *   <filter-bar v-model="levels" :options="..." multiple />
 */
const props = defineProps({
  /** 单选时为 String/Number；multiple 时为数组 */
  modelValue: { type: [String, Number, Array, null], default: '' },
  /** [{ label, value, count? }] */
  options: { type: Array, default: () => [] },
  /** 多选模式，modelValue 是数组 */
  multiple: { type: Boolean, default: false },
  /** 多选模式下"全部"对应的值（选中它即清空其它） */
  allValue: { type: [String, Number], default: '' }
})

const emit = defineEmits(['update:modelValue', 'change'])

function isActive(value) {
  if (props.multiple) {
    const list = Array.isArray(props.modelValue) ? props.modelValue : []
    return list.includes(value)
  }
  return props.modelValue === value
}

function pick(value) {
  let next
  if (props.multiple) {
    const list = Array.isArray(props.modelValue) ? [...props.modelValue] : []
    if (value === props.allValue) {
      next = []
    } else {
      const i = list.indexOf(value)
      if (i >= 0) list.splice(i, 1)
      else list.push(value)
      next = list
    }
  } else {
    // 再点一次已选中的项不取消 —— 单选筛选必须始终有一个值，
    // 否则列表会在"全部"和"空筛选"之间来回跳
    if (props.modelValue === value) return
    next = value
  }
  emit('update:modelValue', next)
  emit('change', next)
}
</script>

<style lang="scss" scoped>
.filter-bar {
  display: flex;
  align-items: center;
  padding: $spacing-md $page-padding;
}

.filter-bar__scroll {
  flex: 1;
  min-width: 0;
  /* scroll-view 必须有确定宽度（小程序里 flex:1 不够，得配 min-width:0） */
  white-space: nowrap;
}

.filter-bar__row {
  display: inline-flex;
  align-items: center;
}

.filter-bar__chip {
  flex-shrink: 0;
  height: 60rpx;
  padding: 0 $spacing-lg;
  margin-right: $spacing-sm;
  border-radius: $radius-pill;
  background: $color-card;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.filter-bar__chip--active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
  font-weight: 500;
}

.filter-bar__count {
  margin-left: 6rpx;
  font-size: $font-xs;
  opacity: 0.75;
}
</style>
