<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="通知中心" />

    <role-hero
      title="通知中心"
      subtitle="从这里发给学生，进入每个学生的「通知中心」"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <!-- 发送表单 -->
      <view class="card">
        <section-title title="发送通知" plain />

        <view class="field">
          <text class="field__label">通知类型</text>
          <view class="chips">
            <view
              v-for="t in typeOptions"
              :key="t.value"
              class="chip"
              :class="{ 'chip--on': form.type === t.value }"
              @tap="form.type = t.value"
            >
              {{ t.label }}
            </view>
          </view>
        </view>

        <view class="field">
          <text class="field__label">接收范围</text>
          <view class="chips">
            <view
              v-for="s in scopeOptions"
              :key="s.value"
              class="chip"
              :class="{ 'chip--on': form.scope === s.value }"
              @tap="form.scope = s.value"
            >
              {{ s.label }}
            </view>
          </view>
          <text class="field__hint">「预警学生」= 名下有未处理（待处理）预警的学生</text>
        </view>

        <view class="field">
          <text class="field__label">通知内容</text>
          <textarea
            v-model="form.content"
            class="textarea"
            :maxlength="500"
            placeholder="输入要发给学生的通知内容"
            placeholder-class="ph"
          />
        </view>

        <view class="send-bar">
          <text class="send-bar__hint">发送后：进入每个学生的「通知中心」，并在下方发送历史留一条</text>
          <view
            class="btn"
            :class="{ 'btn--busy': sending }"
            @tap="sendNotification"
          >
            {{ sending ? '发送中…' : '发送通知' }}
          </view>
        </view>
      </view>

      <!-- 发送历史 -->
      <view class="card">
        <section-title title="发送历史" :extra="total ? `共 ${total} 次发送` : ''" plain />

        <page-loading v-if="loading" :count="3" mode="card" />

        <empty-state
          v-else-if="!rows.length"
          text="暂无发送记录"
          hint="发出第一条通知后，这里会按「同一次发送」归并显示"
          icon="bell"
        />

        <view v-else class="hist">
          <view
            v-for="(h, i) in rows"
            :key="h.id"
            class="hist-row"
            :class="{ 'hist-row--divided': i > 0 }"
          >
            <view class="hist-row__head">
              <text class="tag" :class="typeTagClass(h.category)">{{ typeText(h.category) }}</text>
              <text class="hist-row__count">接收 {{ h.recipientCount }} 人</text>
            </view>
            <text class="hist-row__content">{{ h.content }}</text>
            <text class="hist-row__time">{{ formatted(h.createdAt) }}</text>
          </view>

          <view v-if="note" class="hist__note">{{ note }}</view>
        </view>
      </view>
    </view>

    <app-tab-bar role="counselor" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'

const { userId, userName } = useUser()
const themeStyle = roleThemeStyle('counselor')

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

const heroStats = computed(() => [
  { label: '累计发送', value: total.value },
  // 接收人次 = 各次发送的接收人数之和；与「发送次数」不同口径，分开标
  { label: '接收人次', value: rows.value.reduce((s, h) => s + (Number(h.recipientCount) || 0), 0) },
  { label: '本页显示', value: rows.value.length }
])

const typeOptions = [
  { value: 'normal', label: '普通通知' },
  { value: 'important', label: '重要通知' },
  { value: 'warning', label: '预警通知' }
]
// 后端只认这两个 scope（见 CounselorNotificationServiceImpl）。
const scopeOptions = [
  { value: 'all', label: '全部学生' },
  { value: 'warning-students', label: '预警学生' }
]

const form = ref({ type: 'normal', scope: 'all', content: '' })

const loading = ref(true)
const sending = ref(false)
const rows = ref([])
const total = ref(0)
const note = ref('')

function formatted(v) {
  return formatDate(v, true)
}

/** 后端 category 只会有这三个；认不出时原样回显，不吞成"未知"。 */
function typeText(c) {
  const map = { warning: '预警通知', important: '重要通知', normal: '普通通知' }
  if (!c) return '通知'
  return map[String(c)] || String(c)
}

function typeTagClass(c) {
  if (c === 'warning') return 'tag--danger'
  if (c === 'important') return 'tag--warning'
  return 'tag--info'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const res = await counselorAPI.getNotificationHistory(userId, 1, 20)
    rows.value = Array.isArray(res?.list) ? res.list : []
    total.value = Number(res?.total) || rows.value.length
    note.value = res?.note || ''
  } catch (e) {
    console.error('[counselor notify] 加载历史失败:', e)
  } finally {
    loading.value = false
  }
}

async function sendNotification() {
  if (!userId) {
    uni.showToast({ title: '未取到当前辅导员身份，请重新登录', icon: 'none' })
    return
  }
  if (!form.value.content.trim()) {
    uni.showToast({ title: '请填写通知内容', icon: 'none' })
    return
  }

  sending.value = true
  try {
    // 身份由网关按 token 注入，请求体里带的 counselorId 只作提示（后端 W17 只认头部）
    const res = await counselorAPI.notifyStudents({
      counselorId: userId,
      content: form.value.content,
      type: form.value.type,
      recipients: [form.value.scope]
    })

    // ⚠️ 按后端真实结果汇报，不能无条件弹「已发送」。
    // 返回 {message, recipients, delivered, recorded, skipped[], undeliverable[], scopeNote}
    const delivered = Number(res?.delivered) || 0
    const skipped = Array.isArray(res?.skipped) ? res.skipped : []
    const undeliverable = Array.isArray(res?.undeliverable) ? res.undeliverable : []

    if (delivered === 0) {
      uni.showModal({
        title: '没有人收到',
        content: skipped[0] || undeliverable[0] || '所选接收范围里没有学生',
        showCancel: false
      })
    } else if (skipped.length || undeliverable.length) {
      const missed = skipped.length + undeliverable.length
      uni.showModal({
        title: `已发送 ${delivered} 人`,
        content: `另有 ${missed} 人未送达：\n` +
          [...skipped, ...undeliverable.map(x => '学生档案未绑定登录账号：' + x)].join('\n'),
        showCancel: false
      })
    } else {
      uni.showToast({ title: `已发送 ${delivered} 人`, icon: 'success' })
    }

    form.value = { type: 'normal', scope: 'all', content: '' }
    await load()
  } catch (e) {
    uni.showToast({ title: '发送失败：' + (e?.message || '网络错误'), icon: 'none' })
  } finally {
    sending.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

.card {
  margin: 0 $page-padding $spacing-lg;
  padding: $spacing-lg;
  border-radius: $radius-lg;
  background: $color-card;
  box-shadow: $shadow-card;
}

/* ---------- 表单 ---------- */
.field {
  margin-top: $spacing-lg;
}

.field__label {
  display: block;
  margin-bottom: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.field__hint {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-hint;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  /* 不用 gap（基础库兼容），靠 chip 自身的 margin 排布 */
  margin: -$spacing-xs;
}

.chip {
  margin: $spacing-xs;
  padding: 10rpx 24rpx;
  border-radius: $radius-pill;
  border: 2rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  background: $color-bg;
}

.chip--on {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-weak);
}

.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 180rpx;
  padding: $spacing-md;
  border-radius: $radius-md;
  border: 2rpx solid $color-border;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.ph {
  color: $color-text-hint;
}

.send-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-lg;
}

.send-bar__hint {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
}

.btn {
  flex-shrink: 0;
  padding: 16rpx 40rpx;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-sm;
  font-weight: 600;
}

.btn--busy {
  opacity: 0.6;
}

/* ---------- 历史 ---------- */
.hist {
  margin-top: $spacing-sm;
}

.hist-row {
  padding: $spacing-md 0;
}

.hist-row--divided {
  border-top: 2rpx solid $color-border;
}

.hist-row__head {
  display: flex;
  align-items: center;
}

.hist-row__head .tag {
  margin-right: $spacing-sm;
}

.hist-row__count {
  font-size: $font-xs;
  color: $color-text-hint;
}

.hist-row__content {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.5;
}

.hist-row__time {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-hint;
}

.hist__note {
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}
</style>
