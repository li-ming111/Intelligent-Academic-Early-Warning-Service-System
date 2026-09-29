<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="用户管理" :show-back="false" />

    <view class="page-body page-body--tabbar">
      <!-- 新建入口放在筛选之上：筛完了发现人要新建，不该先滚回顶部 -->
      <view class="toolbar">
        <view class="toolbar__left">
          <text class="toolbar__count">{{ total }}</text>
          <text class="toolbar__unit">个账号</text>
          <text v-if="users.length < total" class="toolbar__loaded">已加载 {{ users.length }}</text>
        </view>
        <view class="toolbar__add" @tap="openCreate">
          <app-icon name="plus" :size="32" color="#fff" />
          <text class="toolbar__add-text">新建</text>
        </view>
      </view>

      <!-- 两个筛选都是**服务端**过滤（collegeId/role 实测落进 SQL）。
           注意没有按姓名/关键字搜索的接口参数 —— 下面的搜索框只筛"已加载的这几条"，
           placeholder 里写清楚了，不能让人以为它在搜全库。 -->
      <filter-bar v-model="roleFilter" :options="roleOptions" @change="reload" />
      <filter-bar v-model="collegeFilter" :options="collegeOptions" @change="reload" />

      <view class="search">
        <app-icon name="search" :size="34" color="#aeaeb2" />
        <input
          v-model="keyword"
          class="search__input"
          :placeholder="`在已加载的 ${users.length} 个账号中筛选`"
          placeholder-class="search__ph"
          confirm-type="search"
        />
        <app-icon v-if="keyword" name="close" :size="32" color="#aeaeb2" @tap="keyword = ''" />
      </view>

      <page-loading v-if="loading" :count="5" />

      <template v-else>
        <empty-state
          v-if="!filtered.length"
          :text="users.length ? '没有匹配的账号' : '这个筛选条件下没有账号'"
          :hint="users.length ? '换个关键词，或清掉上方搜索框' : '换一个角色或学院再看'"
          icon="users"
          :top="40"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(u, i) in filtered"
            :key="u.id"
            class="usr"
            :class="{ 'usr--divided': i > 0 }"
            @tap="openDetail(u)"
          >
            <view class="usr__avatar">
              <text class="usr__avatar-text">{{ initial(u.name) }}</text>
            </view>
            <view class="usr__main">
              <view class="usr__top">
                <text class="usr__name">{{ u.name || '未填姓名' }}</text>
                <!-- status 0 在库里是**两种**情况共用的：新注册待审批、或被停用。
                     这里不合并成一个词，展示成"未启用"，点进详情有解释。 -->
                <text class="tag" :class="u.status === 1 ? 'tag--success' : 'tag--pending'">
                  {{ u.status === 1 ? '正常' : '未启用' }}
                </text>
              </view>
              <text class="usr__meta">{{ metaOf(u) }}</text>
              <text class="usr__account">账号 {{ u.username }}</text>
            </view>
            <view class="usr__right">
              <text class="tag tag--info">{{ roleName(u.role) || `角色 ${u.role}` }}</text>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>
        </view>

        <view v-if="users.length < total" class="more">
          <view class="more__btn" :class="{ 'more__btn--busy': loadingMore }" @tap="loadMore">
            {{ loadingMore ? '加载中…' : `加载更多（还有 ${total - users.length} 个）` }}
          </view>
        </view>
      </template>
    </view>

    <app-tab-bar role="admin" />

    <!-- ============ 详情 / 编辑 / 新建 ============ -->
    <detail-sheet
      v-model="sheetOpen"
      :title="sheetTitle"
      :height="mode === 'detail' ? '78vh' : '72vh'"
    >
      <!-- ---------- 新建 ---------- -->
      <view v-if="mode === 'create'" class="sheet">
        <view class="field">
          <text class="field__label">账号（登录名）</text>
          <input v-model="form.username" class="field__input" placeholder="如 2023999999 或 teacher010" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">姓名</text>
          <input v-model="form.name" class="field__input" placeholder="真实姓名" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">角色</text>
          <view class="opts">
            <view
              v-for="o in createRoleOptions"
              :key="o.value"
              class="opt"
              :class="{ 'opt--on': form.role === o.value }"
              @tap="form.role = o.value"
            >{{ o.label }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">邮箱</text>
          <input v-model="form.email" class="field__input" placeholder="选填" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">初始口令</text>
          <input v-model="form.password" class="field__input" password placeholder="留空则与演示口令一致" placeholder-class="field__ph" />
        </view>
        <!-- 这条必须写出来：建号接口只写 users 表，教师/学院管理员两张档案表都有
             NOT NULL 的 college_id，而表单里没有学院字段，所以后端**故意**不给他们建档案行。
             不说清楚，就会出现"建完的教师在按学院筛选时查不到"的困惑。 -->
        <text class="note">
          教师与学院管理员建号时不会生成档案行（档案表的学院是必填，而这支表单里没有学院字段）。
          建完后需要在对应角色的档案里补学院，否则按学院筛选时查不到这个账号。
        </text>
      </view>

      <!-- ---------- 编辑 ---------- -->
      <view v-else-if="mode === 'edit'" class="sheet">
        <view class="field">
          <text class="field__label">账号</text>
          <text class="field__readonly">{{ current && current.username }}</text>
        </view>
        <view class="field">
          <text class="field__label">姓名</text>
          <input v-model="form.name" class="field__input" placeholder="真实姓名" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">邮箱</text>
          <input v-model="form.email" class="field__input" placeholder="选填" placeholder-class="field__ph" />
        </view>
        <text class="note">
          这里改的是**账号资料**（users 表）。学生学籍、教师档案里的姓名不在这支接口的范围内，
          改完两端可能不一致 —— 当前库里这两处的姓名本来就各存一份。
        </text>
        <text class="note">
          账号名与角色不可改（后端接口不接受这两个字段）。口令走下方的「重置口令」。
        </text>
      </view>

      <!-- ---------- 详情 ---------- -->
      <view v-else-if="current" class="sheet">
        <filter-bar v-model="detailTab" :options="DETAIL_TABS" />

        <template v-if="detailTab === 'info'">
          <view class="facts">
            <view class="facts__row">
              <text class="facts__k">角色</text>
              <text class="facts__v">{{ roleName(current.role) || `未知（${current.role}）` }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">账号</text>
              <text class="facts__v">{{ current.username }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">状态</text>
              <text class="facts__v">{{ current.status === 1 ? '正常（可登录）' : '未启用（不可登录）' }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">邮箱</text>
              <text class="facts__v">{{ current.email || '--' }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">手机</text>
              <text class="facts__v">{{ current.phone || '--' }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">创建时间</text>
              <text class="facts__v">{{ formatDate(current.createdAt, true) }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">最近改动</text>
              <text class="facts__v">{{ formatDate(current.updatedAt, true) }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">口令</text>
              <text class="facts__v">不回传（password 恒为 null）</text>
            </view>
          </view>

          <section-title plain :title="`${roleName(current.role) || '该角色'}档案`" extra="来自用户列表" />
          <view class="facts">
            <view v-for="r in profileRows" :key="r.k" class="facts__row">
              <text class="facts__k">{{ r.k }}</text>
              <text class="facts__v">{{ r.v }}</text>
            </view>
          </view>

          <text v-if="current.status !== 1" class="note">
            状态 0 在库里是**两种情况共用的**：新注册账号等待审批，或被管理员停用。
            后端没有区分这两者的字段，登录接口对两者都回「账号正在审批中，请等待管理员审核」。
            下面的「审批通过」与「停用/启用」都会把状态改成 1 或 0，无法从状态值反推是哪一种。
          </text>
        </template>

        <template v-else>
          <page-loading v-if="logsLoading" :count="3" />
          <template v-else>
            <empty-state
              v-if="!logs.length"
              text="这个账号没有登录记录"
              hint="login_logs 表里没有它对应的行"
              icon="clock"
              :top="30"
            />
            <view v-else class="card card--flush">
              <view v-for="(l, i) in logs" :key="l.id" class="log" :class="{ 'log--divided': i > 0 }">
                <view class="log__main">
                  <text class="log__time">{{ formatDate(l.loginTime, true) }}</text>
                  <text class="log__addr">{{ l.ipAddress || '--' }}<text v-if="l.location"> · {{ l.location }}</text></text>
                  <text class="log__dev">{{ deviceText(l.device) }}</text>
                </view>
                <text class="tag" :class="l.status === 'success' ? 'tag--success' : 'tag--danger'">
                  {{ l.status === 'success' ? '成功' : (l.status || '未知') }}
                </text>
              </view>
            </view>
            <!-- 这条不是"这个账号没登录过"，是整张表都没有写入方。必须说清楚。 -->
            <text class="note">
              登录日志表全库只有 {{ logs.length }} 条（这个账号），最新一条停在 2026-06-29 ——
              这套后端**没有往 login_logs 写记录的代码**，新登录不会新增行。
              所以"最近没有记录"不等于"这个账号没登录过"。
            </text>
          </template>
        </template>
      </view>

      <template #footer>
        <!-- 详情页：四个小动作平铺 + 一个主按钮。
             删除不做主按钮 —— 它是这一屏唯一不可逆的操作。 -->
        <template v-if="mode === 'detail'">
          <view class="acts">
            <view class="act" @tap="doReset">重置口令</view>
            <view class="act" @tap="doToggle">{{ current && current.status === 1 ? '停用账号' : '启用账号' }}</view>
            <view v-if="current && current.status !== 1" class="act act--ok" @tap="doApprove">审批通过</view>
            <view class="act act--danger" @tap="doDelete">删除</view>
          </view>
          <view class="btn-primary footer-btn" @tap="enterEdit">编辑账号信息</view>
        </template>

        <template v-else>
          <view class="form-acts">
            <view class="form-acts__cancel" @tap="sheetOpen = false">取消</view>
            <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="submit">
              {{ submitting ? '提交中…' : (mode === 'create' ? '建号' : '保存') }}
            </view>
          </view>
        </template>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { roleName } from '@/constants/roles.js'
import { formatDate } from '@/utils/format.js'

const themeStyle = roleThemeStyle('admin')

const PAGE_SIZE = 20

const loading = ref(true)
const loadingMore = ref(false)
const users = ref([])
const total = ref(0)
const page = ref(1)

const roleFilter = ref('')
const collegeFilter = ref('')
const colleges = ref([])
const keyword = ref('')

const sheetOpen = ref(false)
/** 'detail' | 'edit' | 'create' */
const mode = ref('detail')
const detailTab = ref('info')
const current = ref(null)
const logs = ref([])
const logsLoading = ref(false)
const submitting = ref(false)
const form = ref({ username: '', name: '', email: '', password: '', role: 1 })

/**
 * 角色筛选。值用字符串：filter-bar 是严格相等比较，
 * 传数字 1 和选项里的 '1' 对不上，chip 就不会高亮。
 */
const roleOptions = [
  { label: '全部', value: '' },
  { label: '学生', value: '1' },
  { label: '教师', value: '2' },
  { label: '辅导员', value: '3' },
  { label: '系统管理员', value: '4' },
  { label: '学院管理员', value: '5' }
]

const collegeOptions = computed(() => [
  { label: '全部学院', value: '' },
  ...colleges.value.map(c => ({ label: c.name, value: String(c.id) }))
])

const createRoleOptions = [
  { label: '学生', value: 1 },
  { label: '教师', value: 2 },
  { label: '辅导员', value: 3 },
  { label: '系统管理员', value: 4 },
  { label: '学院管理员', value: 5 }
]

/** 详情抽屉内的两个页签。登录日志挂进用户详情，是因为**没有全局日志接口**。 */
const DETAIL_TABS = [
  { label: '账号信息', value: 'info' },
  { label: '登录日志', value: 'logs' }
]

const sheetTitle = computed(() => {
  if (mode.value === 'create') return '新建账号'
  if (mode.value === 'edit') return '编辑账号信息'
  return current.value ? current.value.name || current.value.username : '账号详情'
})

/**
 * 本地筛选。**只筛已加载的行**，不是全库搜索 —— 列表接口没有 keyword 参数，
 * 装了"搜索全库"的样子但只搜到 20 条是最坏的情况，所以 placeholder 里点明了范围。
 */
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter(u => {
    const hay = [u.name, u.username, u.studentId, u.jobNumber, u.collegeName, u.majorName, u.title]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return hay.includes(kw)
  })
})

/** 角色维度能显示的信息各不相同，逐角色拼一行副标题。 */
function metaOf(u) {
  switch (Number(u.role)) {
    case 1:
      return [u.studentId || '无学号', u.collegeName || '未关联学院', u.majorName || '未分专业'].join(' · ')
    case 2:
      // 教师**故意没有 jobNumber**（那个字段曾填 users.id，会把于凤海显示成 29）。
      // 教师账号本身就是工号形态，所以这块只报学院。
      return u.collegeName || '未分配学院（档案行缺失，按学院筛选时查不到）'
    case 3:
      return [u.jobNumber || u.username, u.collegeName || '未关联学院'].join(' · ')
    case 4:
      return '校级管理员，不归属任何学院'
    case 5:
      return [u.title || '未填职务', u.collegeName || '未分配学院（档案行缺失）'].join(' · ')
    default:
      return `未识别的角色值 ${u.role}`
  }
}

/** 详情页里"档案"那一段：列表行带来的角色专属字段，不必再请求一次。 */
const profileRows = computed(() => {
  const u = current.value
  if (!u) return []
  const rows = []
  const push = (k, v) => rows.push({ k, v: v || '--' })
  switch (Number(u.role)) {
    case 1:
      push('学号', u.studentId)
      push('所属学院', u.collegeName)
      push('专业', u.majorName)
      break
    case 2:
      push('所属学院', u.collegeName || '未分配（档案行缺失）')
      break
    case 3:
      push('业务编号', u.jobNumber)
      push('所属学院', u.collegeName)
      break
    case 4:
      push('归属', '校级，无学院')
      break
    case 5:
      push('职务', u.title)
      push('所属学院', u.collegeName || '未分配（档案行缺失）')
      break
    default:
      break
  }
  return rows
})

function initial(name) {
  const s = String(name || '').trim()
  return s ? s.slice(0, 1) : '?'
}

/** User-Agent 太长，列表里只留浏览器与系统两段足够认人。 */
function deviceText(ua) {
  const s = String(ua || '')
  if (!s) return '未知设备'
  const browser = /Edg\//.test(s) ? 'Edge'
    : /Chrome\//.test(s) ? 'Chrome'
      : /Firefox\//.test(s) ? 'Firefox'
        : /Safari\//.test(s) ? 'Safari' : ''
  const os = /Windows/.test(s) ? 'Windows'
    : /Mac OS X/.test(s) ? 'macOS'
      : /Android/.test(s) ? 'Android'
        : /iPhone|iPad/.test(s) ? 'iOS' : ''
  const joined = [browser, os].filter(Boolean).join(' · ')
  return joined || s.slice(0, 40)
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

function apiMessage(e) {
  return (e && e.message) || '操作失败'
}

onMounted(async () => {
  // 学院筛选的选项要从学院接口拿。它挂了不该让整页打不开，所以单独 catch。
  adminAPI.getColleges()
    .then(list => { colleges.value = Array.isArray(list) ? list : [] })
    .catch(e => console.error('[admin users] 学院列表失败:', e))
  await reload()
})

async function fetchPage(p) {
  const res = await adminAPI.getUsers(p, PAGE_SIZE, collegeFilter.value || undefined, roleFilter.value || undefined)
  const rows = Array.isArray(res) ? res : (res && Array.isArray(res.items) ? res.items : [])
  const t = res && res.total !== undefined && res.total !== null ? Number(res.total) : rows.length
  return { rows, total: Number.isFinite(t) ? t : rows.length }
}

async function reload() {
  loading.value = true
  page.value = 1
  try {
    const { rows, total: t } = await fetchPage(1)
    users.value = rows
    total.value = t
  } catch (e) {
    console.error('[admin users] 列表失败:', e)
    users.value = []
    total.value = 0
    toast(apiMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || users.value.length >= total.value) return
  loadingMore.value = true
  try {
    const next = page.value + 1
    const { rows, total: t } = await fetchPage(next)
    // 按 id 去重：翻页期间若有人新建账号，同一条记录可能被推两次
    const seen = new Set(users.value.map(u => u.id))
    users.value = users.value.concat(rows.filter(r => !seen.has(r.id)))
    total.value = t
    page.value = next
  } catch (e) {
    toast(apiMessage(e))
  } finally {
    loadingMore.value = false
  }
}

function openDetail(u) {
  current.value = u
  mode.value = 'detail'
  logs.value = []
  detailTab.value = 'info'
  sheetOpen.value = true
  loadLogs(u.id)
}

async function loadLogs(userId) {
  logsLoading.value = true
  try {
    const res = await adminAPI.getLoginLogs(userId)
    logs.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.error('[admin users] 登录日志失败:', e)
    logs.value = []
  } finally {
    logsLoading.value = false
  }
}

function openCreate() {
  current.value = null
  mode.value = 'create'
  form.value = { username: '', name: '', email: '', password: '', role: 1 }
  sheetOpen.value = true
}

function enterEdit() {
  const u = current.value
  if (!u) return
  form.value = { username: u.username, name: u.name || '', email: u.email || '', password: '', role: u.role }
  mode.value = 'edit'
}

async function submit() {
  if (submitting.value) return
  const f = form.value
  if (!f.name || !String(f.name).trim()) {
    toast('请填写姓名')
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'create') {
      if (!f.username || !String(f.username).trim()) {
        toast('请填写账号')
        return
      }
      const body = {
        username: String(f.username).trim(),
        name: String(f.name).trim(),
        role: f.role
      }
      if (f.email) body.email = String(f.email).trim()
      // 口令留空时不发这个字段：后端对空串的处理与"不发"不同，宁可不发
      if (f.password) body.password = String(f.password)
      await adminAPI.createUser(body)
      toast('账号已创建')
      sheetOpen.value = false
      await reload()
    } else {
      // 只发姓名与邮箱。password 传空串等于"不改"（后端会置 null），
      // 但重构口令本来就是「重置口令」按钮的职责，这里干脆不带这个字段。
      const body = { name: String(f.name).trim(), email: String(f.email || '').trim() }
      await adminAPI.updateUser(current.value.id, body)
      toast('已保存')
      sheetOpen.value = false
      await reload()
    }
  } catch (e) {
    // 重名是业务错误（code=500 + message「用户名已存在：xxx」），必须把 message 露出来
    toast(apiMessage(e), 3200)
  } finally {
    submitting.value = false
  }
}

function confirm(content, title = '确认') {
  return new Promise(resolve => {
    uni.showModal({
      title,
      content,
      success: r => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

async function doReset() {
  const u = current.value
  if (!u) return
  const ok = await confirm(`把「${u.name || u.username}」的口令重置为演示口令？`, '重置口令')
  if (!ok) return
  try {
    const msg = await adminAPI.resetPassword(u.id)
    // 后端返回的 message 里写明了重置成什么，直接照它显示，不自己另编一句
    uni.showModal({ title: '已重置', content: String(msg || '口令已重置'), showCancel: false })
  } catch (e) {
    toast(apiMessage(e), 3200)
  }
}

async function doToggle() {
  const u = current.value
  if (!u) return
  const turningOff = u.status === 1
  const ok = await confirm(
    turningOff
      ? `停用后「${u.name || u.username}」将无法登录（提示语是"账号正在审批中"）。`
      : `启用后「${u.name || u.username}」可以正常登录。`,
    turningOff ? '停用账号' : '启用账号'
  )
  if (!ok) return
  try {
    // 这个接口是**置反**而不是"设为 X"：它按库里当前值取反。
    // 所以不能信本地状态，改完必须回读一遍。
    await adminAPI.toggleUserStatus(u.id)
    const fresh = await adminAPI.getUserById(u.id)
    if (fresh && typeof fresh === 'object') {
      current.value = { ...u, ...fresh, ...pickProfileFields(u) }
      const i = users.value.findIndex(x => x.id === u.id)
      if (i >= 0) users.value[i] = { ...users.value[i], status: fresh.status }
    }
    toast(current.value && current.value.status === 1 ? '账号已启用' : '账号已停用')
  } catch (e) {
    toast(apiMessage(e), 3200)
  }
}

/** 单用户接口只回 users 表的字段，列表行里的档案字段要保留下来，否则详情会变空。 */
function pickProfileFields(u) {
  return {
    studentId: u.studentId,
    collegeName: u.collegeName,
    majorName: u.majorName,
    jobNumber: u.jobNumber,
    title: u.title
  }
}

async function doApprove() {
  const u = current.value
  if (!u) return
  const ok = await confirm(`通过「${u.name || u.username}」的账号审批？`, '审批通过')
  if (!ok) return
  try {
    await adminAPI.approveUser(u.id)
    current.value = { ...u, ...pickProfileFields(u), status: 1 }
    const i = users.value.findIndex(x => x.id === u.id)
    if (i >= 0) users.value[i] = { ...users.value[i], status: 1 }
    toast('已通过审批')
  } catch (e) {
    toast(apiMessage(e), 3200)
  }
}

async function doDelete() {
  const u = current.value
  if (!u) return
  const ok = await confirm(
    `删除「${u.name || u.username}」（${u.username}）？账号行与其档案行会一并删除，不可撤销。`,
    '删除账号'
  )
  if (!ok) return
  try {
    await adminAPI.deleteUser(u.id)
    toast('已删除')
    sheetOpen.value = false
    current.value = null
    await reload()
  } catch (e) {
    toast(apiMessage(e), 3200)
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
}

/* ---------- 顶部 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  padding: 0 $page-padding;
}

.toolbar__left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.toolbar__count {
  font-size: $font-2xl;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.toolbar__unit {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.toolbar__loaded {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.toolbar__add {
  display: flex;
  align-items: center;
  height: 64rpx;
  padding: 0 $spacing-lg;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.toolbar__add-text {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: #fff;
  font-weight: 500;
}

/* ---------- 搜索（只筛已加载行） ---------- */
.search {
  display: flex;
  align-items: center;
  height: 76rpx;
  margin: $spacing-sm $page-padding 0;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  border: 1rpx solid $color-border;
}

.search__input {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-sm;
  color: $color-text;
}

.search__ph {
  color: $color-text-hint;
  font-size: $font-xs;
}

/* ---------- 用户行 ---------- */
.usr {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.usr--divided {
  border-top: 1rpx solid $color-divider;
}

.usr:active {
  background: $color-bg;
}

.usr__avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  @include flex-center;
  flex-shrink: 0;
}

.usr__avatar-text {
  font-size: $font-lg;
  font-weight: 600;
  color: var(--color-primary);
}

.usr__main {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
}

.usr__top {
  display: flex;
  align-items: center;
}

.usr__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.usr__top .tag {
  margin-left: $spacing-sm;
  flex-shrink: 0;
}

.usr__meta {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  @include ellipsis;
}

.usr__account {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.usr__right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-left: $spacing-xs;
}

.usr__right .tag {
  margin-right: $spacing-xs;
}

/* ---------- 加载更多 ---------- */
.more {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

.more__btn {
  height: $tap-min;
  border-radius: $radius-md;
  background: $color-card;
  border: 1rpx solid $color-border;
  color: $color-text-secondary;
  font-size: $font-sm;
  @include flex-center;
}

.more__btn--busy {
  opacity: 0.6;
}

/* ---------- 抽屉内 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-xl;
}

.field {
  margin-bottom: $spacing-lg;
}

.field__label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.field__input {
  height: 80rpx;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.field__ph {
  color: $color-text-hint;
}

.field__readonly {
  display: block;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text-hint;
}

.opts {
  display: flex;
  flex-wrap: wrap;
}

.opt {
  height: 60rpx;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-pill;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.opt--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
  font-weight: 500;
}

.facts {
  margin-top: $spacing-md;
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

.log {
  display: flex;
  align-items: flex-start;
  padding: $spacing-lg;
}

.log--divided {
  border-top: 1rpx solid $color-divider;
}

.log__main {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
}

.log__time {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
}

.log__addr {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.log__dev {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.note {
  display: block;
  margin: $spacing-md 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}

/* ---------- 抽屉底部 ---------- */
.acts {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: $spacing-md;
}

.act {
  height: 68rpx;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-md;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.act--ok {
  background: rgba(34, 197, 94, 0.12);
  border-color: transparent;
  color: $color-success;
}

.act--danger {
  background: rgba(239, 68, 68, 0.1);
  border-color: transparent;
  color: $color-danger;
}

.footer-btn {
  width: 100%;
}

.form-acts {
  display: flex;
  align-items: center;
}

.form-acts__cancel {
  width: 200rpx;
  height: $tap-min;
  margin-right: $spacing-md;
  border-radius: var(--radius-md);
  background: $color-bg;
  color: $color-text-secondary;
  font-size: $font-md;
  @include flex-center;
}

.form-acts__ok {
  flex: 1;
  min-width: 0;
}
</style>
