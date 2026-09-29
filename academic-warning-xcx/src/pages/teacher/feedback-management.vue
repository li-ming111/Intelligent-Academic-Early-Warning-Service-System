<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="反馈管理" :show-back="false" />

    <view class="tabs">
      <filter-bar v-model="filter" :options="options" />
    </view>

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__item">共 <text class="summary__num">{{ list.length }}</text> 条</text>
          <text class="summary__item">未回复 <text class="summary__num summary__num--warn">{{ pendingCount }}</text> 条</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="list.length ? '没有符合条件的反馈' : '暂无学生反馈'"
          :hint="list.length ? '换个分类看看' : '学生对课程的建议会出现在这里'"
          icon="mail"
          :top="60"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(f, i) in filtered"
            :key="f.id"
            class="frow"
            :class="{ 'frow--divided': i > 0 }"
            @tap="openReply(f)"
          >
            <view class="frow__main">
              <view class="frow__top">
                <text class="frow__who">{{ who(f) }}</text>
                <text v-if="f.category" class="tag tag--info">{{ f.category }}</text>
              </view>
              <text class="frow__body">{{ body(f) }}</text>
              <text class="frow__time">{{ timeText(f) }}</text>
            </view>
            <view class="frow__right">
              <text class="tag" :class="f.status === 1 ? 'tag--success' : 'tag--warning'">{{ statusText(f) }}</text>
            </view>
          </view>
        </view>

        <text v-if="list.length" class="note">反馈表里没有单独的「学生姓名」列，学生的名字写在反馈正文里（「姓名：内容」），这里按这个格式拆出来显示。</text>
      </template>
    </view>

    <detail-sheet v-model="replyOpen" :title="current ? who(current) : ''" height="72vh">
      <view v-if="current" class="form">
        <view class="quote">
          <text class="quote__text">{{ current.content }}</text>
        </view>
        <text class="quote__meta">{{ current.category || '未分类' }} · {{ timeText(current) }}</text>

        <text class="form__label">我的回复</text>
        <textarea
          v-model="replyText"
          class="form__textarea"
          placeholder="针对学生提出的问题给出答复或说明"
          placeholder-class="form__placeholder"
          :maxlength="300"
          auto-height
        />
        <text class="form__tip">提交后反馈状态会变为「已回复」，回复内容保存在反馈记录里，学生端可看到。</text>

        <text v-if="current.reply" class="form__label">历史回复</text>
        <view v-if="current.reply" class="quote quote--ok">
          <text class="quote__text">{{ current.reply }}</text>
        </view>
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !canReply }" @tap="submitReply">
          {{ saving ? '提交中…' : (current && current.reply ? '更新回复' : '提交回复') }}
        </view>
      </template>
    </detail-sheet>

    <app-tab-bar role="teacher" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'

const { userId } = useUser()

const themeStyle = roleThemeStyle('teacher')

const loading = ref(true)
const list = ref([])
const filter = ref('all')

const replyOpen = ref(false)
const current = ref(null)
const replyText = ref('')
const saving = ref(false)

/**
 * 筛选项**从数据里现取**，不写死一份分类词表。
 * `feedback.category` 是自由文本（"教学建议" / "学习困难"…），
 * 后端没有枚举；写死一张表就会出现"筛选条件里有、但没有一条数据属于它"的空档。
 */
const options = computed(() => {
  const seen = []
  for (const f of list.value) {
    const c = f.category
    if (c && !seen.includes(c)) seen.push(c)
  }
  return [{ label: '全部', value: 'all' }, { label: '未回复', value: '__pending' }, ...seen.map(c => ({ label: c, value: c }))]
})

const pendingCount = computed(() => list.value.filter(f => f.status !== 1).length)

const filtered = computed(() => list.value.filter((f) => {
  if (filter.value === 'all') return true
  if (filter.value === '__pending') return f.status !== 1
  return f.category === filter.value
}))

const canReply = computed(() => !!String(replyText.value).trim())

function statusText(f) {
  return f.status === 1 ? '已回复' : '未回复'
}

/**
 * 学生姓名：正文里以「姓名：内容」开头。
 *
 * 前缀必须够短才算姓名（≤6 字）：正文里本来就常有冒号
 * （"高等数学：进度有点快"），不加这个限制会把半句话当成姓名。
 */
function who(f) {
  if (f.studentName) return f.studentName
  const text = String(f.content || '')
  const idx = text.search(/[：:]/)
  if (idx > 0 && idx <= 6) {
    const prefix = text.slice(0, idx).trim()
    if (prefix && !/\s/.test(prefix)) return prefix
  }
  return '匿名学生'
}

/** 正文：去掉开头的「姓名：」前缀 */
function body(f) {
  const text = String(f.content || '')
  const idx = text.search(/[：:]/)
  if (idx > 0 && idx <= 6 && who(f) !== '匿名学生') return text.slice(idx + 1).trim()
  return text
}

function timeText(f) {
  const d = formatDate(f.createTime || f.updateTime)
  return d || '--'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const rows = await teacherAPI.getFeedbackList(userId).catch((e) => {
      console.error('[teacher feedback] 列表失败:', e)
      return []
    })
    list.value = Array.isArray(rows) ? rows : []
  } finally {
    loading.value = false
  }
}

function openReply(f) {
  current.value = f
  // 已回复过的把原文填回去，便于修改；不要再写一遍
  replyText.value = f.reply || ''
  replyOpen.value = true
}

async function submitReply() {
  if (!canReply.value || saving.value) return
  saving.value = true
  try {
    // 这条路是**真落库**的：写 reply、把 status 置 1、刷新 update_time。
    // （早先 API 层指向的 `PUT /teachers/feedbacks/{id}` 不存在，一直是 500。）
    await teacherAPI.replyFeedback(current.value.id, String(replyText.value).trim())
    uni.showToast({ title: '回复已提交', icon: 'success' })
    replyOpen.value = false
    await load()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '提交失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.tabs {
  padding: $spacing-sm $page-padding;
  background: $color-card;
  border-bottom: 1rpx solid $color-border;
}

.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.summary {
  display: flex;
  margin: 0 $page-padding $spacing-md;
}

.summary__item {
  margin-right: $spacing-xl;
  font-size: $font-xs;
  color: $color-text-hint;
}

.summary__num {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.summary__num--warn {
  color: $color-warning;
}

.card {
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
}

/* ---------- 反馈行 ---------- */
.frow {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg;
}

.frow--divided {
  border-top: 1rpx solid $color-divider;
}

.frow:active {
  background: $color-bg;
}

.frow__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.frow__top {
  display: flex;
  align-items: center;
}

.frow__who {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.frow__top .tag {
  margin-left: $spacing-sm;
}

.frow__body {
  display: block;
  margin-top: 6rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.5;
}

.frow__time {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.frow__right {
  flex-shrink: 0;
}

.note {
  display: block;
  margin: $spacing-md $page-padding 0;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

/* ---------- 抽屉 ---------- */
.form {
  padding: 0 $page-padding $spacing-2xl;
}

.quote {
  padding: $spacing-md;
  border-left: 6rpx solid var(--color-primary);
  border-radius: $radius-sm;
  background: $color-bg;
}

.quote--ok {
  border-left-color: $color-success;
}

.quote__text {
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

.quote__meta {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__label {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-sm;
  color: $color-text;
}

.form__textarea {
  width: 100%;
  min-height: 200rpx;
  margin-top: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.form__placeholder {
  color: $color-text-hint;
}

.form__tip {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}
</style>
