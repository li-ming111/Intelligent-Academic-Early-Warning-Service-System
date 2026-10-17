<template>
  <view class="tabbar" :style="{ paddingBottom: safeBottom + 'px' }">
    <view
      v-for="item in tabs"
      :key="item.path"
      class="tabbar__item"
      @tap="switchTo(item.path)"
    >
      <app-icon
        :name="item.icon"
        :size="42"
        :color="current === item.path ? primary : '#94a3b8'"
        :weight="current === item.path ? 2.1 : 1.7"
      />
      <text class="tabbar__label" :class="{ 'tabbar__label--active': current === item.path }">
        {{ item.label }}
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { replace } from '@/utils/navigate.js'
import { roleKey } from '@/constants/roles.js'
import { themeOf } from '@/composables/useRoleTheme.js'

/**
 * 四个非学生角色共用的自定义 tab-bar。
 *
 * 学生的四个 tab 走原生 tabBar（pages.json 里配的），其余角色是分包页、
 * 不能用原生 tabBar，只能自定义一个。
 *
 * 旧版的做法是四个角色各写一个组件（teacher/counselor/admin/collegeadmin），
 * 四个文件除 tab 列表外逐字相同，且**选中色都硬编码 #2563eb** —— 换角色不换色，
 * 是"三套并行调色板"的来源之一。这里合成一个，颜色一律取角色主题。
 */

/** 每角色的 tab 定义。path 必须与 pages.json 完全一致。 */
const TAB_SETS = {
  teacher: [
    { path: '/pages/teacher/dashboard', label: '首页', icon: 'home' },
    { path: '/pages/teacher/scores', label: '成绩', icon: 'edit' },
    { path: '/pages/teacher/students', label: '学生', icon: 'users' },
    { path: '/pages/teacher/warnings', label: '预警', icon: 'alert' },
    { path: '/pages/teacher/profile', label: '我的', icon: 'user' }
  ],
  counselor: [
    { path: '/pages/counselor/dashboard', label: '首页', icon: 'home' },
    { path: '/pages/counselor/warnings', label: '预警', icon: 'alert' },
    { path: '/pages/counselor/assistance', label: '帮扶', icon: 'target' },
    { path: '/pages/counselor/students', label: '学生', icon: 'users' },
    { path: '/pages/counselor/profile', label: '我的', icon: 'user' }
  ],
  admin: [
    { path: '/pages/admin/dashboard', label: '首页', icon: 'home' },
    { path: '/pages/admin/users', label: '用户', icon: 'users' },
    { path: '/pages/admin/colleges', label: '学院', icon: 'building' },
    { path: '/pages/admin/warnings', label: '预警', icon: 'alert' },
    { path: '/pages/admin/profile', label: '我的', icon: 'user' }
  ],
  collegeAdmin: [
    { path: '/pages/college-admin/dashboard', label: '首页', icon: 'home' },
    { path: '/pages/college-admin/students', label: '学生', icon: 'users' },
    { path: '/pages/college-admin/warnings', label: '预警', icon: 'alert' },
    { path: '/pages/college-admin/classes', label: '班级', icon: 'building' },
    { path: '/pages/college-admin/profile', label: '我的', icon: 'user' }
  ]
}

const props = defineProps({
  /** 覆盖角色。默认从 storage 读当前登录角色 */
  role: { type: [String, Number], default: '' }
})

const safeBottom = ref(0)
/** 覆盖高亮项。默认按当前路由推导，一般不用传 */
const activeOverride = ref('')

const role = computed(() => roleKey(props.role || uni.getStorageSync('role')))
const tabs = computed(() => TAB_SETS[role.value] || [])
const primary = computed(() => (themeOf(role.value) || {}).primary || '#217a46')

/**
 * 由当前页面路由推导高亮项，而不是让每个页面手传 `current`。
 * 旧版四套 tab-bar 都要页面传 current，传漏了就整条 tab 无高亮，
 * 而且传的是不带前导斜杠的 'pages/teacher/scores'，与 navigate 用的
 * '/pages/teacher/scores' 两种写法混用，很容易不一致。
 */
const current = computed(() => {
  if (activeOverride.value) return activeOverride.value
  const pages = getCurrentPages()
  const top = pages && pages.length ? pages[pages.length - 1] : null
  const route = top && top.route ? top.route : ''
  return route ? '/' + route.replace(/^\//, '') : ''
})

onMounted(() => {
  // 安全区：底部小黑条高度。旧版用 uni.getSystemInfoSync().safeAreaInsets，
  // 该 API 在小程序端已废弃，优先用 getWindowInfo。
  try {
    const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
    const insets = info && info.safeAreaInsets
    if (insets && insets.bottom) safeBottom.value = insets.bottom
  } catch (e) {
    safeBottom.value = 0
  }
})

function switchTo(path) {
  if (path === current.value) return
  // 必须用 replace 而不是 navigateTo：tab 之间切换是"替换"语义，
  // 用 navigateTo 会让页面栈随点击次数无限增长（小程序栈上限 10 层，
  // 超了之后跳转静默失败）。
  replace(path)
}
</script>

<style lang="scss" scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: $z-tabbar;
  display: flex;
  align-items: center;
  background: $color-card;
  border-top: 1rpx solid $color-border;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.tabbar__item {
  flex: 1;
  min-width: 0;
  height: 100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.tabbar__label {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: #94a3b8;
  @include ellipsis;
  max-width: 100%;
}

/* 选中态的字色继承角色主题：色值由 app-icon 内联，文字这里用 CSS 变量，
   两者同源（都来自 ROLE_THEME.primary），不会出现图标与文字不同色 */
.tabbar__label--active {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
