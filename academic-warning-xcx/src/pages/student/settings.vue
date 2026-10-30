<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="设置" />

    <view class="page-body">
      <page-loading v-if="loading" :count="2" mode="card" />

      <template v-else>
        <!-- 消息接收 -->
        <view class="card">
          <section-title title="消息接收" extra="关闭后不再推送该类通知" />
          <view v-for="f in toggles" :key="f.key" class="switch-row">
            <view class="switch-row__info">
              <text class="switch-row__label">{{ f.label }}</text>
              <text class="switch-row__hint">{{ f.hint }}</text>
            </view>
            <switch
              class="switch-row__switch"
              :checked="Boolean(form[f.key])"
              :color="switchColor"
              @change="onToggle(f.key, $event)"
            />
          </view>
        </view>

        <!-- 账号信息（只读部分，改资料不在小程序端做） -->
        <view class="card">
          <section-title title="账号" extra="如需修改请联系管理员" />
          <view class="kv-row">
            <text class="kv-row__label">学号</text>
            <text class="kv-row__value">{{ info.username || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">姓名</text>
            <text class="kv-row__value">{{ info.name || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">邮箱</text>
            <text class="kv-row__value">{{ info.email || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">手机号</text>
            <text class="kv-row__value">{{ info.phone || EMPTY }}</text>
          </view>
          <view class="kv-row">
            <text class="kv-row__label">账号状态</text>
            <text class="tag" :class="statusTagClass">{{ statusText }}</text>
          </view>
        </view>

        <!-- 修改密码 -->
        <view class="card">
          <section-title title="修改密码" />
          <view class="field">
            <text class="field__label">当前密码</text>
            <input
              class="field__input"
              type="password"
              password
              v-model="pwd.oldPassword"
              placeholder="请输入当前密码"
              placeholder-class="field__ph"
            />
          </view>
          <view class="field">
            <text class="field__label">新密码</text>
            <input
              class="field__input"
              type="password"
              password
              v-model="pwd.newPassword"
              placeholder="至少 6 位"
              placeholder-class="field__ph"
            />
          </view>
          <view class="field">
            <text class="field__label">确认新密码</text>
            <input
              class="field__input"
              type="password"
              password
              v-model="pwd.confirm"
              placeholder="再次输入新密码"
              placeholder-class="field__ph"
            />
          </view>
          <view class="pwd-tip">{{ pwdTip }}</view>
          <view
            class="btn-primary btn-pwd"
            :class="{ 'btn-primary--disabled': !canChangePwd }"
            @tap="doChangePassword"
          >
            {{ changing ? '提交中…' : '修改密码' }}
          </view>
        </view>

        <view class="foot">
          <view class="btn-ghost" @tap="confirmLogout">退出登录</view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI, apiClient } from '@/services/api.js'
import { useUser, logout } from '@/composables/useUser.js'
import { roleThemeStyle, primaryOf } from '@/composables/useRoleTheme.js'
import { USER_STATUS_TEXT, STATUS_TAG_TYPE, dictText } from '@/constants/dict.js'

const { userId, role } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const info = ref({})

/**
 * 通知开关的表单。
 *
 * 字段名**必须**与 `user_settings` 表的列一致 —— 这四个是
 * `GET /users/{uid}/settings` 实际返回的键（实测）。
 *
 * ⚠️ 2026-10-08（W28）订正一句**已过期**的理由。这里原来写着
 * 「不用 `/students/settings/{uid}`：那个 PUT 直接 `return {success:true}`，一个字段都不写库」——
 * 那句在**当时**是真的，但 `StudentServiceImpl.updateStudentSettings` **后来已经改成真写**
 * `user_settings`（键同样是 users.id，与 `/users/{uid}/settings` 同一张表、同一套键，
 * 见那边的类注释「这条路由是那个入口的学生侧别名，不是第二套设置」）。
 * ⇒ 两条路现在**等价**，继续走 `/users/{uid}/settings` 没问题，但**别再拿"那个是桩"当理由**：
 * 判据比实现旧一版正是本仓反复栽的一类（会让人去"修"一个并不存在的桩）。
 */
const form = ref({
  warningNotification: false,
  systemMessage: false,
  classNotification: false,
  studentFeedback: false
})

const toggles = [
  { key: 'warningNotification', label: '学业预警通知', hint: '成绩触发预警规则时推送' },
  { key: 'systemMessage', label: '系统消息', hint: '系统公告与维护通知' },
  { key: 'classNotification', label: '班级通知', hint: '班级事务与集体活动' },
  { key: 'studentFeedback', label: '帮扶反馈', hint: '帮扶计划进展与回复' }
]

const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const changing = ref(false)

const EMPTY = '—'

/**
 * switch 是**原生组件**，不解析 CSS 变量，`color` 必须给字面量色值。
 * 色值从 ROLE_THEME 取 —— 原先这里手抄了一份五角色色表，
 * 改色要两处一起改，抄漏就是「同一个角色在设置页和其它页不同色」。
 */
const switchColor = primaryOf(role, '#217a46')

const statusText = computed(() => dictText(USER_STATUS_TEXT, info.value.status))

const statusTagClass = computed(() => {
  const n = Number(info.value.status)
  if (n === 1) return 'tag--success'
  if (n === 2) return 'tag--danger'
  if (n === 0) return 'tag--warning'
  return 'tag--pending'
})

const pwdTip = computed(() => {
  const { oldPassword, newPassword, confirm } = pwd.value
  if (!oldPassword && !newPassword && !confirm) return '修改后需使用新密码重新登录'
  if (newPassword && newPassword.length < 6) return '新密码至少 6 位'
  if (newPassword && confirm && newPassword !== confirm) return '两次输入的新密码不一致'
  if (oldPassword && newPassword && newPassword === oldPassword) return '新密码不能与当前密码相同'
  return '修改后需使用新密码重新登录'
})

const canChangePwd = computed(() => {
  const { oldPassword, newPassword, confirm } = pwd.value
  return Boolean(oldPassword) && newPassword.length >= 6 &&
    newPassword !== oldPassword && newPassword === confirm
})

function onToggle(key, e) {
  const next = Boolean(e.detail.value)
  const prev = form.value[key]
  form.value[key] = next
  saveSetting(key, next, prev)
}

async function saveSetting(key, value, prev) {
  try {
    /**
     * `PUT /users/{uid}/settings` 是**部分更新**：请求体里没出现的字段保持原值。
     * 因此这里只提交被拨动的那一个键 —— 设置读接口失败时表单会停在默认 false，
     * 整包提交会把其余四个真实开关静默改成 false。
     */
    const res = await studentAPI.updateSettings(userId, { [key]: value })
    if (res && res.success === false) {
      form.value[key] = prev
      uni.showToast({ title: res.message || '保存失败', icon: 'none' })
      return
    }
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.error('[settings] 保存失败:', e)
    form.value[key] = prev
    uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
  }
}

async function doChangePassword() {
  if (!canChangePwd.value || changing.value) return
  changing.value = true
  try {
    const res = await apiClient.changePassword({
      userId,
      oldPassword: pwd.value.oldPassword,
      newPassword: pwd.value.newPassword
    })
    // 失败同样是 HTTP 200，只能看载荷
    if (res && (res.success === false || res.error)) {
      uni.showToast({ title: res.message || res.error || '修改失败', icon: 'none' })
      return
    }
    uni.showToast({ title: '密码已修改', icon: 'success' })
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  } catch (e) {
    console.error('[settings] 修改密码失败:', e)
    uni.showToast({ title: '修改失败，请稍后重试', icon: 'none' })
  } finally {
    changing.value = false
  }
}

function confirmLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: res => {
      if (!res.confirm) return
      logout()
      uni.reLaunch({ url: '/pages/auth/login' })
    }
  })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [infoData, settings] = await Promise.all([
      studentAPI.getUserInfo(userId).catch(e => { console.error('[settings] 账号信息失败:', e); return null }),
      studentAPI.getSettings(userId).catch(e => { console.error('[settings] 设置读取失败:', e); return null })
    ])
    info.value = infoData && typeof infoData === 'object' ? infoData : {}

    if (settings && typeof settings === 'object') {
      // 只挑已知的四个键，避免把 officeLocation 这类无关字段带进表单再写回去
      const next = { ...form.value }
      for (const t of toggles) {
        if (settings[t.key] !== undefined && settings[t.key] !== null) {
          next[t.key] = Boolean(settings[t.key])
        }
      }
      form.value = next
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

/* ---------- 开关行 ---------- */
.switch-row {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.switch-row:last-child {
  border-bottom: none;
}

.switch-row__info {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-md;
}

.switch-row__label {
  display: block;
  font-size: $font-base;
  color: $color-text;
}

.switch-row__hint {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.switch-row__switch {
  flex-shrink: 0;
  /* switch 是小程序原生组件，尺寸靠 transform 缩放，属性控制有限 */
  transform: scale(0.85);
}

/* ---------- 键值行 ---------- */
.kv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72rpx;
  padding: $spacing-xs 0;
  border-bottom: 1rpx solid $color-divider;
}

.kv-row:last-child {
  border-bottom: none;
}

.kv-row__label {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.kv-row__value {
  font-size: $font-sm;
  color: $color-text;
}

/* ---------- 表单 ---------- */
.field {
  margin-top: $spacing-md;
}

.field__label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.field__input {
  width: 100%;
  height: $tap-min;
  padding: 0 $spacing-md;
  box-sizing: border-box;
  border: 1rpx solid $color-border;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.field__ph {
  color: $color-text-hint;
  font-size: $font-sm;
}

.pwd-tip {
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.btn-pwd {
  margin-top: $spacing-lg;
}

/* ---------- 退出 ---------- */
.foot {
  margin-top: $spacing-lg;
}

.btn-ghost {
  @include flex-center;
  height: $tap-min;
  border-radius: $radius-md;
  background: $color-card;
  border: 1rpx solid $color-danger;
  color: $color-danger;
  font-size: $font-md;
  font-weight: 500;
}
</style>
