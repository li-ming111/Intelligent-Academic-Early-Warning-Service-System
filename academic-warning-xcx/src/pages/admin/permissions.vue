<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="角色权限" />

    <filter-bar v-model="tab" :options="tabs" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <!-- ---------- 角色 ---------- -->
        <template v-if="tab === 'roles'">
          <empty-state v-if="!roles.length" text="没有角色数据" hint="role 表为空" icon="user" />

          <view v-else class="card card--flush">
            <view
              v-for="(r, i) in roles"
              :key="r.id"
              class="role"
              :class="{ 'role--divided': i > 0 }"
              @tap="openRole(r)"
            >
              <view class="role__main">
                <view class="role__top">
                  <text class="role__name">{{ r.name }}</text>
                  <text class="tag" :class="r.status === 1 ? 'tag--success' : 'tag--pending'">
                    {{ r.status === 1 ? '启用' : '停用' }}
                  </text>
                </view>
                <text class="role__code">{{ r.code }}</text>
                <text class="role__desc">{{ r.description || '无描述' }}</text>
              </view>
              <view class="role__right">
                <text class="role__count">{{ countOf(r.id) }}</text>
                <text class="role__count-label">个账号</text>
              </view>
            </view>
          </view>

          <text class="note">
            `roles` 表现在是 **5 行**，与 `users.role` 的 5 个取值一一对应
            （学生 / 教师 / 辅导员 / 管理员 / 学院管理员）。
            点任意一行可以看到该角色绑定的权限点。
          </text>
        </template>

        <!-- ---------- 权限点 ---------- -->
        <template v-else-if="tab === 'perms'">
          <empty-state v-if="!perms.length" text="没有权限点数据" hint="permission 表为空" icon="lock" />

          <view v-else>
            <view class="summary">
              <text class="summary__text">共 <text class="summary__num">{{ perms.length }}</text> 个权限点</text>
            </view>
            <view class="card card--flush">
              <view
                v-for="(p, i) in perms"
                :key="p.id"
                class="perm"
                :class="{ 'perm--divided': i > 0 }"
              >
                <view class="perm__main">
                  <text class="perm__name">{{ p.name }}</text>
                  <text class="perm__code">{{ p.code }}</text>
                  <text class="perm__desc">{{ p.description || '无描述' }}</text>
                  <text class="perm__roles">适用角色：{{ rolesTextOf(p) }}</text>
                </view>
                <text class="tag" :class="p.status === 1 ? 'tag--success' : 'tag--pending'">
                  {{ p.status === 1 ? '启用' : '停用' }}
                </text>
              </view>
            </view>
            <text class="note">
              「适用角色」来自后端真查 `role_permissions` 表 —— 每行列出**持有该权限的角色名**，
              不是写死的映射表。反过来"某个角色有哪些权限"，点「角色」页的任意一行就能看到。
            </text>
          </view>
        </template>

        <!-- ---------- 账号权限查询 ---------- -->
        <template v-else>
          <view class="lookup">
            <input
              v-model="queryId"
              class="lookup__input"
              type="number"
              placeholder="输入用户 id，如 1"
              placeholder-class="lookup__ph"
              confirm-type="search"
              @confirm="doLookup"
            />
            <view class="lookup__btn" @tap="doLookup">查询</view>
          </view>

          <page-loading v-if="queryLoading" :count="2" />

          <template v-else-if="queried">
            <view class="facts">
              <view class="facts__row">
                <text class="facts__k">用户 id</text>
                <text class="facts__v">{{ queried.userId }}</text>
              </view>
              <view class="facts__row">
                <text class="facts__k">账号</text>
                <text class="facts__v">{{ queried.username || '--' }}</text>
              </view>
              <view class="facts__row">
                <text class="facts__k">角色值</text>
                <text class="facts__v">{{ queried.role }}</text>
              </view>
              <view class="facts__row">
                <text class="facts__k">角色名</text>
                <text class="facts__v">{{ queried.roleName || '未取到' }}</text>
              </view>
              <view v-if="queried.roleCode" class="facts__row">
                <text class="facts__k">角色码</text>
                <text class="facts__v">{{ queried.roleCode }}</text>
              </view>
            </view>

            <section-title plain title="权限点" :extra="`${permList.length} 个`" />
            <empty-state
              v-if="!permList.length"
              text="这个账号没有任何权限点"
              hint="后端如实返回空数组，界面不替它补默认权限"
              icon="lock"
              :top="30"
            />
            <view v-else class="card">
              <view v-for="(p, i) in permList" :key="i" class="plain-row" :class="{ 'plain-row--divided': i > 0 }">
                <text class="plain-row__text">{{ permLabel(p) }}</text>
              </view>
            </view>

            <text class="note">
              权限点按账号的 `users.role` 经 `roles.code` 反查 `role_permissions` 得来，
              是**这个角色实际绑定的那几条**。角色名与角色码都由后端回中文/真实码
              （如 `管理员` / `ROLE_ADMIN`），不是前端拼的。
              若某个内置角色一条都没绑，这里会如实显示 0 个。
            </text>
          </template>

          <empty-state
            v-else
            text="输入用户 id 查询权限"
            hint="这个接口按 users.id 查，不是档案 id"
            icon="search"
            :top="40"
          />
        </template>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'

const themeStyle = roleThemeStyle('admin')

const tabs = [
  { label: '角色', value: 'roles' },
  { label: '权限点', value: 'perms' },
  { label: '账号权限查询', value: 'lookup' }
]

const tab = ref('roles')

const loading = ref(true)
const roles = ref([])
const perms = ref([])
/** role 表的 id → 账号数。来自 /admin/users/page 的 total（role 参数实测落进 SQL）。 */
const roleCounts = ref({})

const queryId = ref('')
const queryLoading = ref(false)
const queried = ref(null)

const permList = computed(() => {
  const q = queried.value
  return q && Array.isArray(q.permissions) ? q.permissions : []
})

function countOf(roleId) {
  const n = roleCounts.value[roleId]
  return n === undefined || n === null ? '--' : n
}

function toast(msg) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: 2600 })
}

onMounted(async () => {
  loading.value = true
  try {
    const [r, p] = await Promise.all([
      adminAPI.getRoles().catch(e => { console.error('[admin permissions] 角色失败:', e); return null }),
      adminAPI.getPermissions().catch(e => { console.error('[admin permissions] 权限点失败:', e); return null })
    ])
    roles.value = Array.isArray(r) ? r : []
    perms.value = Array.isArray(p) ? p : []
  } finally {
    loading.value = false
  }
  // 角色行上的账号数是加分项，单独等：它挂了不该让角色表显示不出来
  loadRoleCounts()
})

/**
 * 每个角色的账号数。
 * 只能逐个 role 调列表接口取 `total` —— 没有"按角色聚合"的接口。
 * 数量太少（5 个）所以并发打完就完了，不做缓存失效那套。
 */
async function loadRoleCounts() {
  const ids = roles.value.map(r => r.id)
  const results = await Promise.all(ids.map(id =>
    adminAPI.getUsers(1, 1, undefined, String(id))
      .then(res => ({ id, total: res && res.total !== undefined ? Number(res.total) : 0 }))
      .catch(() => ({ id, total: null }))
  ))
  const next = {}
  results.forEach(r => { if (r.total !== null) next[r.id] = r.total })
  roleCounts.value = next
}

function openRole(r) {
  const n = countOf(r.id)
  // `/admin/permissions/roles` 从第二十四轮起就一并回 `permissions`（权限码数组）,
  // 所以这里列得出「这个角色有哪些权限点」—— 之前的文案说"没有开放查询接口"已经过期。
  const codes = Array.isArray(r.permissions) ? r.permissions : []
  const names = codes.map(code => {
    const hit = perms.value.find(p => p.code === code)
    return hit ? hit.name : code
  })
  uni.showModal({
    title: r.name,
    content:
      `编码：${r.code}\n` +
      `账号数：${n === '--' ? '未取到' : n + ' 个'}\n` +
      `说明：${r.description || '无'}\n\n` +
      `权限点（${names.length} 个）：\n` +
      (names.length ? names.map(x => `· ${x}`).join('\n') : '（这个角色没有绑定任何权限点）'),
    showCancel: false
  })
}

/** 权限码 → 中文名。`perms` 已整表加载，本地查表即可，不再多打一次接口。 */
function permLabel(p) {
  const code = typeof p === 'string' ? p : (p && p.code)
  if (!code) return typeof p === 'string' ? p : JSON.stringify(p)
  const hit = perms.value.find(x => x.code === code)
  return hit ? `${hit.name}（${code}）` : code
}

/**
 * 一条权限被哪些角色持有 —— 后端 `roleCodes` 是 `ROLE_*` 英文码，
 * 换成 `roles` 里的中文名显示（查不到就退回码本身，不静默吞掉）。
 */
function rolesTextOf(p) {
  const codes = Array.isArray(p.roleCodes) ? p.roleCodes : []
  if (!codes.length) return '未绑定任何角色'
  return codes.map(code => {
    const hit = roles.value.find(r => r.code === code)
    return hit ? hit.name : code
  }).join(' / ')
}

async function doLookup() {
  const id = String(queryId.value || '').trim()
  if (!id) {
    toast('请输入用户 id')
    return
  }
  queryLoading.value = true
  queried.value = null
  try {
    const res = await adminAPI.getUserPermissions(id)
    queried.value = res && typeof res === 'object' ? res : null
    if (!queried.value) toast('接口没有返回内容')
  } catch (e) {
    toast((e && e.message) || '查询失败')
  } finally {
    queryLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-sm;
  padding-bottom: $spacing-3xl;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 角色 ---------- */
.role {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.role--divided {
  border-top: 1rpx solid $color-divider;
}

.role:active {
  background: $color-bg;
}

.role__main {
  flex: 1;
  min-width: 0;
}

.role__top {
  display: flex;
  align-items: center;
}

.role__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.role__top .tag {
  margin-left: $spacing-sm;
}

.role__code {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: var(--color-primary);
}

.role__desc {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.role__right {
  flex-shrink: 0;
  margin-left: $spacing-md;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.role__count {
  font-size: $font-xl;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.role__count-label {
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 权限点 ---------- */
.summary {
  padding: 0 $page-padding $spacing-sm;
}

.summary__text {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.summary__num {
  font-weight: 700;
  color: $color-text;
}

.perm {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg;
}

.perm--divided {
  border-top: 1rpx solid $color-divider;
}

.perm__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.perm__name {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.perm__code {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: var(--color-primary);
}

.perm__desc {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis-multi(2);
}

.perm__roles {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: var(--color-primary);
}

/* ---------- 查询 ---------- */
.lookup {
  display: flex;
  align-items: center;
  padding: 0 $page-padding;
}

.lookup__input {
  flex: 1;
  min-width: 0;
  height: 80rpx;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text;
}

.lookup__ph {
  color: $color-text-hint;
}

.lookup__btn {
  width: 160rpx;
  height: $tap-min;
  margin-left: $spacing-sm;
  border-radius: $radius-md;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-sm;
  font-weight: 500;
  @include flex-center;
}

.facts {
  margin: $spacing-lg $page-padding 0;
  padding: 0 $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.facts__row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-md 0;
}

.facts__k {
  width: 180rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

.facts__v {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  text-align: right;
  word-break: break-all;
}

.plain-row {
  padding: $spacing-md 0;
}

.plain-row--divided {
  border-top: 1rpx solid $color-divider;
}

.plain-row__text {
  font-size: $font-sm;
  color: $color-text;
}

.note {
  display: block;
  margin: $spacing-lg $page-padding 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}
</style>
