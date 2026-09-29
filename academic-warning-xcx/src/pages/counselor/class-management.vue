<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="班级管理" :show-back="false" />

    <role-hero
      :title="counselorName || '班级管理'"
      subtitle="管理关系落在 classes.counselor_id 上"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body page-body--tabbar">
      <view class="tabs">
        <view
          v-for="t in TABS"
          :key="t.key"
          class="tabs__item"
          :class="{ 'tabs__item--on': tab === t.key }"
          @tap="tab = t.key"
        >{{ t.label }}</view>
      </view>

      <page-loading v-if="loadingClasses" :count="3" mode="card" />

      <template v-else>
        <!-- ① 我的班级 -->
        <template v-if="tab === 'mine'">
          <empty-state
            v-if="!classes.length"
            text="暂未管理任何班级"
            hint="在「申请管理」里申请一个当前无辅导员的班级"
            icon="building"
          />
          <view v-else class="list">
            <view v-for="c in classes" :key="c.id" class="cls" @tap="openClass(c)">
              <view class="cls__main">
                <text class="cls__name">{{ c.name }}</text>
                <text class="cls__sub">{{ numOr(c.studentCount) }} 名学生{{ numOr(c.studentCount) ? '' : '（该班暂无学生档案）' }}</text>
              </view>
              <app-icon name="chevron" :size="28" color="#aeaeb2" />
            </view>
          </view>
        </template>

        <!-- ② 申请管理 -->
        <template v-else-if="tab === 'apply'">
          <section-title title="申请记录" :extra="`共 ${requests.length} 条`" />
          <empty-state
            v-if="!requests.length"
            text="暂无申请记录"
            hint="在下方搜索班级并提交申请"
            icon="doc"
            :top="40"
          />
          <view v-else class="list">
            <view v-for="r in requests" :key="r.id" class="req">
              <view class="req__top">
                <text class="req__class">{{ r.className || `班级 #${r.classId}` }}</text>
                <text class="tag" :class="statusTag(r.status)">{{ statusText(r.status) }}</text>
              </view>
              <text class="req__type">{{ typeText(r.applicationType) }}</text>
              <text v-if="r.reason" class="req__reason">理由：{{ r.reason }}</text>
              <text class="req__time">{{ fmt(r.createTime) }}</text>
            </view>
          </view>

          <section-title title="搜索班级" extra="按名称模糊匹配" />
          <view class="search">
            <input
              v-model="keyword"
              class="search__input"
              placeholder="输入班级名称关键字"
              placeholder-class="search__ph"
              confirm-type="search"
              @confirm="doSearch"
            />
            <view class="search__btn" @tap="doSearch">{{ searching ? '搜索中' : '搜索' }}</view>
          </view>

          <view class="note note--inset">
            <text class="note__line">当前无辅导员的班级：{{ noCounselorCount }} 个。只有这类班级才能「申请管理」—— 已有辅导员的班级会被后端直接拒绝（提示「该班级已有辅导员管理，无法重复申请」）。</text>
            <text class="note__line">想演示一条完整的申请，顺序是：先在「我的班级」里对某个班「立即解除」，那个班就变成无辅导员，再回来搜索并申请，申请会落为「待审核」由管理员审批。</text>
          </view>

          <view v-if="results.length" class="list">
            <view v-for="c in results" :key="c.id" class="res">
              <view class="res__main">
                <text class="res__name">{{ c.name }}</text>
                <text class="res__sub">{{ numOr(c.studentCount) }} 名学生</text>
              </view>
              <text class="tag" :class="numOr(c.hasCounselor) ? 'tag--info' : 'tag--success'">
                {{ numOr(c.hasCounselor) ? '已有辅导员' : '可申请' }}
              </text>
              <view
                class="res__btn"
                :class="{ 'res__btn--off': numOr(c.hasCounselor) || String(c.id) === applyingId }"
                @tap="askApply(c)"
              >{{ String(c.id) === applyingId ? '提交中…' : '申请' }}</view>
            </view>
          </view>
          <empty-state
            v-else-if="searched"
            text="没有匹配的班级"
            hint="换个关键字，或留空搜索全部"
            icon="search"
            :top="40"
          />
        </template>

        <!-- ③ 班级预警对比 -->
        <template v-else>
          <empty-state
            v-if="!compare.length"
            text="暂无班级预警数据"
            hint="所带班级没有触发预警规则"
            icon="check"
          />
          <view v-else class="list">
            <view v-for="c in compare" :key="c.classId" class="cmp">
              <view class="cmp__top">
                <text class="cmp__name">{{ c.className }}</text>
                <text class="cmp__total">{{ numOr(c.warningCount) }} 条</text>
              </view>
              <view class="cmp__track">
                <view class="cmp__fill" :style="{ width: warnPct(c.warningCount) }" />
              </view>
              <view class="cmp__sub">
                <text class="cmp__s">待处理 <text class="cmp__v">{{ numOr(c.pendingCount) }}</text></text>
                <text class="cmp__s">高度关注 <text class="cmp__v cmp__v--danger">{{ numOr(c.highRiskCount) }}</text></text>
                <text class="cmp__s">平均绩点 <text class="cmp__v">{{ gpaText(c.avgGpa) }}</text></text>
              </view>
            </view>
          </view>
          <view class="note note--box">
            <text class="note__line">「高度关注」的口径是预警等级 ≥ 2（中度及以上），与后端 high_risk_count 一致，不是"仅严重"。</text>
            <text class="note__line">平均绩点取自各生学分加权 GPA 再平均；库里没有成绩的班级该值为空。</text>
          </view>
        </template>
      </template>
    </view>

    <!-- 班级详情 -->
    <detail-sheet v-model="sheetOpen" :title="current.name || '班级详情'" height="84vh">
      <view v-if="current.id" class="sheet">
        <view class="kv">
          <text class="kv__k">学生人数</text>
          <text class="kv__v">{{ numOr(detail.studentCount) }} 人</text>
        </view>
        <view class="kv">
          <text class="kv__k">班主任</text>
          <text class="kv__v">{{ detail.teacherId ? '已指派' : '未指派' }}</text>
        </view>
        <view class="kv">
          <text class="kv__k">辅导员</text>
          <text class="kv__v">{{ detail.counselorId ? '我（当前管理者）' : '无' }}</text>
        </view>

        <template v-if="myCompare">
          <text class="sheet__label">预警概览</text>
          <view class="cmp-metrics">
            <view class="cmp-metric">
              <text class="cmp-metric__label">预警总数</text>
              <text class="cmp-metric__value">{{ numOr(myCompare.warningCount) }}</text>
            </view>
            <view class="cmp-metric">
              <text class="cmp-metric__label">待处理</text>
              <text class="cmp-metric__value">{{ numOr(myCompare.pendingCount) }}</text>
            </view>
            <view class="cmp-metric">
              <text class="cmp-metric__label">高度关注</text>
              <text class="cmp-metric__value cmp-metric__value--danger">{{ numOr(myCompare.highRiskCount) }}</text>
            </view>
            <view class="cmp-metric">
              <text class="cmp-metric__label">平均绩点</text>
              <text class="cmp-metric__value">{{ gpaText(myCompare.avgGpa) }}</text>
            </view>
          </view>
        </template>

        <text class="sheet__label">学生名单（{{ roster.length }} 人）</text>
        <empty-state
          v-if="!roster.length"
          text="该班暂无学生档案"
          hint="花名册为空，预警与成绩都不会有数据"
          icon="users"
          :top="20"
        />
        <view v-else>
          <view v-for="s in roster" :key="s.studentId" class="roster">
            <view class="roster__main">
              <text class="roster__name">{{ s.studentName || '未命名' }}</text>
              <text class="roster__no">{{ s.studentId }} · {{ s.gender || '未填' }}</text>
            </view>
            <text class="roster__phone">{{ maskPhone(s.phone) }}</text>
          </view>
        </view>

        <!-- 两条"解除"路径完全不同，界面上分开摆：
             申请解除只是建一条待审核记录（管理员批了才生效），
             立即解除是当场把 classes.counselor_id 置空。 -->
        <text class="sheet__label">解除管理</text>
        <textarea
          v-model="reason"
          class="textarea"
          placeholder="填写申请解除的理由（会写入申请记录）"
          placeholder-class="textarea__ph"
          :maxlength="200"
          auto-height
        />
        <view class="sheet__actions">
          <view
            class="btn-outline"
            :class="{ 'btn-outline--disabled': !reason.trim() || busy }"
            @tap="askReleaseApply"
          >申请解除（待审核）</view>
          <view class="btn-danger" :class="{ 'btn-danger--disabled': busy }" @tap="askReleaseNow">立即解除</view>
        </view>
        <text class="sheet__tip">「申请解除」提交后状态为待审核，班级仍归你管理，需管理员审批；「立即解除」会当场解除并撤销该班的历史申请记录。</text>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate, maskPhone } from '@/utils/format.js'
import { APPLICATION_STATUS_TEXT, dictText } from '@/constants/dict.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

const TABS = [
  { key: 'mine', label: '我的班级' },
  { key: 'apply', label: '申请管理' },
  { key: 'compare', label: '班级预警' }
]

const tab = ref('mine')

const classes = ref([])
const compare = ref([])
const requests = ref([])
const loadingClasses = ref(true)

const keyword = ref('')
const results = ref([])
const searched = ref(false)
const searching = ref(false)
const applyingId = ref('')
const noCounselorCount = ref(0)

const sheetOpen = ref(false)
const current = ref({})
const detail = ref({})
const roster = ref([])
const reason = ref('')
const busy = ref(false)

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const heroStats = computed(() => {
  const students = classes.value.reduce((a, c) => a + numOr(c.studentCount), 0)
  const warns = compare.value.reduce((a, c) => a + numOr(c.warningCount), 0)
  const pendingApp = requests.value.filter(r => numOr(r.status) === 0).length
  return [
    { label: '管理班级', value: classes.value.length },
    { label: '名下学生', value: students },
    { label: '预警总数', value: warns, onTap: () => { tab.value = 'compare' } },
    { label: '待审核申请', value: pendingApp, onTap: () => { tab.value = 'apply' } }
  ]
})

/** 当前打开班级的预警概览：compare 以 classId 为键，与 detail 的 id 同一空间 */
const myCompare = computed(() => {
  if (!current.value.id) return null
  return compare.value.find(c => String(c.classId) === String(current.value.id)) || null
})

const maxWarn = computed(() => Math.max(1, ...compare.value.map(c => numOr(c.warningCount))))

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function fmt(v) {
  return formatDate(v, true)
}

function gpaText(v) {
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? n.toFixed(2) : '--'
}

function warnPct(v) {
  return Math.round((numOr(v) / maxWarn.value) * 100) + '%'
}

function statusText(v) {
  return dictText(APPLICATION_STATUS_TEXT, Number(v))
}

function statusTag(v) {
  const n = Number(v)
  return n === 1 ? 'tag--success' : n === 2 ? 'tag--danger' : n === 3 ? 'tag--info' : 'tag--pending'
}

/** applicationType：0 申请管理 / 1 申请解除 */
function typeText(v) {
  return Number(v) === 1 ? '解除管理申请' : '申请管理班级'
}

onMounted(loadAll)

async function loadAll() {
  loadingClasses.value = true
  try {
    if (!userId) return
    const [cls, cmp, reqs, noC] = await Promise.all([
      counselorAPI.getMyClasses(userId).catch(e => { console.error('[class] 班级失败:', e); return [] }),
      counselorAPI.getClassWarningCompare(userId).catch(e => { console.error('[class] 预警对比失败:', e); return [] }),
      counselorAPI.getClassRequests(userId).catch(e => { console.error('[class] 申请记录失败:', e); return [] }),
      counselorAPI.getClassesWithoutCounselor().catch(e => { console.error('[class] 无辅导员班级失败:', e); return [] })
    ])
    classes.value = Array.isArray(cls) ? cls : []
    // 预警多的班排前面
    compare.value = (Array.isArray(cmp) ? cmp : [])
      .slice()
      .sort((a, b) => numOr(b.warningCount) - numOr(a.warningCount))
    requests.value = Array.isArray(reqs) ? reqs : []
    noCounselorCount.value = Array.isArray(noC) ? noC.length : 0
  } finally {
    loadingClasses.value = false
  }
}

async function openClass(c) {
  current.value = c
  detail.value = {}
  roster.value = []
  reason.value = ''
  sheetOpen.value = true
  try {
    // 详情与花名册是两个接口，互不依赖
    const [d, r] = await Promise.all([
      counselorAPI.getClassDetail(c.id).catch(e => { console.error('[class] 详情失败:', e); return null }),
      counselorAPI.getClassStudents(c.id).catch(e => { console.error('[class] 名单失败:', e); return [] })
    ])
    detail.value = d && typeof d === 'object' ? d : { studentCount: c.studentCount }
    roster.value = Array.isArray(r) ? r : []
  } catch (e) {
    console.error('[class] 详情加载失败:', e)
  }
}

async function doSearch() {
  searching.value = true
  try {
    const list = await counselorAPI.searchClasses(keyword.value.trim()).catch(e => {
      console.error('[class] 搜索失败:', e)
      return []
    })
    results.value = Array.isArray(list) ? list : []
    searched.value = true
  } finally {
    searching.value = false
  }
}

function askApply(c) {
  if (applyingId.value) return
  if (numOr(c.hasCounselor)) {
    // 后端也会拒，但先说清楚原因比让用户点了再收到报错好
    uni.showModal({
      title: '该班级已有辅导员',
      content: `${c.name} 当前已有辅导员管理，无法重复申请。若确实需要接手，请先让原辅导员或管理员解除该班的班级管理关系。`,
      showCancel: false,
      confirmText: '知道了'
    })
    return
  }
  uni.showModal({
    title: '申请管理班级',
    content: `向管理员申请管理「${c.name}」，提交后状态为待审核。`,
    confirmText: '提交申请',
    success: async res => {
      if (!res.confirm) return
      applyingId.value = String(c.id)
      try {
        /**
         * 请求体的四个键一个都不能少：
         *   counselorId → users.id、classId、className（后端原样入库，**不反查**）
         *   applicationType → 0 申请管理 / 1 申请解除
         * className 漏传的话申请记录里班级名会是空的。
         */
        await counselorAPI.applyForClass({
          counselorId: userId,
          classId: c.id,
          className: c.name,
          reason: `申请管理${c.name}`,
          applicationType: 0
        })
        uni.showToast({ title: '已提交，等待管理员审核', icon: 'success' })
        await loadAll()
      } catch (e) {
        console.error('[class] 申请失败:', e)
        uni.showToast({ title: e && e.message ? e.message : '申请失败', icon: 'none' })
      } finally {
        applyingId.value = ''
      }
    }
  })
}

/** 申请解除：只建一条待审核记录，班级仍归我管理 */
function askReleaseApply() {
  if (!reason.value.trim() || busy.value) return
  const c = current.value
  uni.showModal({
    title: '提交解除申请',
    content: `为「${c.name}」提交解除管理申请，状态为待审核，需管理员批准后才生效。`,
    confirmText: '提交',
    success: async res => {
      if (!res.confirm) return
      busy.value = true
      try {
        await counselorAPI.applyForClass({
          counselorId: userId,
          classId: c.id,
          className: c.name,
          reason: reason.value.trim(),
          applicationType: 1
        })
        uni.showToast({ title: '解除申请已提交', icon: 'success' })
        sheetOpen.value = false
        await loadAll()
        tab.value = 'apply'
      } catch (e) {
        console.error('[class] 解除申请失败:', e)
        uni.showToast({ title: e && e.message ? e.message : '提交失败', icon: 'none' })
      } finally {
        busy.value = false
      }
    }
  })
}

/**
 * 立即解除：**当场写库**（`UPDATE classes SET counselor_id = NULL`），
 * 并把该班历史申请记录置为已取消 —— 没有二次审核，所以确认框必须把后果写全。
 */
function askReleaseNow() {
  if (busy.value) return
  const c = current.value
  const n = numOr(c.studentCount)
  uni.showModal({
    title: '立即解除管理',
    content: `将立即解除对「${c.name}」的管理：你的工作台与学生名单不再包含该班${n ? `的 ${n} 名学生` : ''}，该班的历史申请记录会被置为已取消。此操作无需审核，请确认。`,
    confirmText: '确认解除',
    confirmColor: '#ef4444',
    success: async res => {
      if (!res.confirm) return
      busy.value = true
      try {
        await counselorAPI.cancelClass({ counselorId: userId, classId: c.id })
        uni.showToast({ title: '已解除管理', icon: 'success' })
        sheetOpen.value = false
        await loadAll()
      } catch (e) {
        console.error('[class] 解除失败:', e)
        uni.showToast({ title: e && e.message ? e.message : '解除失败', icon: 'none' })
      } finally {
        busy.value = false
      }
    }
  })
}

/** 切到申请 tab 时若还没搜过，先把全部班级列出来当"可申请池" */
watch(tab, key => {
  if (key === 'apply' && !searched.value && !searching.value) doSearch()
})
</script>

<style lang="scss" scoped>
.page-body {
  padding: $spacing-lg $page-padding $spacing-3xl;
}

.list {
  margin-top: $spacing-md;
}

/* ---------- tab ---------- */
.tabs {
  display: flex;
  padding: 6rpx;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.tabs__item {
  flex: 1;
  min-width: 0;
  padding: $spacing-sm 0;
  border-radius: $radius-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  text-align: center;
}

.tabs__item--on {
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-counselor});
  font-weight: 600;
}

/* ---------- 我的班级 ---------- */
.cls {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-sm;
  padding: $spacing-lg;
  @include card;
}

.cls__main {
  flex: 1;
  min-width: 0;
}

.cls__name {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.cls__sub {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 申请记录 ---------- */
.req {
  margin-bottom: $spacing-sm;
  padding: $spacing-lg;
  @include card;
}

.req__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.req__class {
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.req__type {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.req__reason {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

.req__time {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
}

.search__input {
  flex: 1;
  min-width: 0;
  height: $tap-min;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
  font-size: $font-sm;
  color: $color-text;
}

.search__ph {
  color: $color-text-hint;
}

.search__btn {
  flex-shrink: 0;
  height: $tap-min;
  margin-left: $spacing-sm;
  padding: 0 $spacing-xl;
  border-radius: $radius-md;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-sm;
  @include flex-center;
}

.res {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-sm;
  padding: $spacing-md $spacing-lg;
  @include card;
}

.res__main {
  flex: 1;
  min-width: 0;
}

.res__name {
  display: block;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.res__sub {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.res__btn {
  flex-shrink: 0;
  margin-left: $spacing-md;
  padding: 10rpx 26rpx;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
}

.res__btn--off {
  opacity: 0.4;
}

/* ---------- 预警对比 ---------- */
.cmp {
  margin-bottom: $spacing-sm;
  padding: $spacing-lg;
  @include card;
}

.cmp__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.cmp__name {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.cmp__total {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-sm;
  font-weight: 600;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.cmp__track {
  height: 12rpx;
  margin-top: $spacing-xs;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.cmp__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.cmp__sub {
  display: flex;
  flex-wrap: wrap;
  margin-top: 6rpx;
}

.cmp__s {
  margin-right: $spacing-lg;
  font-size: $font-xs;
  color: $color-text-hint;
}

.cmp__v {
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.cmp__v--danger {
  color: $color-danger;
}

/* ---------- 说明 ---------- */
.note {
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.note--inset {
  margin-top: $spacing-md;
}

.note--box {
  margin-top: $spacing-md;
}

.note__line {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.note__line + .note__line {
  margin-top: $spacing-xs;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding: 0 $page-padding $spacing-2xl;
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.kv__k {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.kv__v {
  font-size: $font-sm;
  color: $color-text;
}

.sheet__label {
  display: block;
  margin-top: $spacing-lg;
  margin-bottom: $spacing-xs;
  font-size: $font-sm;
  color: $color-text;
}

.cmp-metrics {
  display: flex;
  padding: $spacing-md 0;
  border-radius: $radius-md;
  background: $color-bg;
}

.cmp-metric {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cmp-metric__label {
  font-size: $font-xs;
  color: $color-text-hint;
}

.cmp-metric__value {
  margin-top: 4rpx;
  font-size: $font-lg;
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.cmp-metric__value--danger {
  color: $color-danger;
}

.roster {
  display: flex;
  align-items: center;
  padding: $spacing-sm 0;
  border-bottom: 1rpx solid $color-divider;
}

.roster__main {
  flex: 1;
  min-width: 0;
}

.roster__name {
  display: block;
  font-size: $font-sm;
  color: $color-text;
}

.roster__no {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.roster__phone {
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.textarea {
  width: 100%;
  min-height: 140rpx;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

.textarea__ph {
  color: $color-text-hint;
}

.sheet__actions {
  display: flex;
  margin-top: $spacing-md;
}

.btn-outline {
  flex: 1;
  min-width: 0;
  padding: $spacing-md 0;
  border-radius: $radius-md;
  border: 1rpx solid var(--color-primary);
  color: var(--color-primary);
  font-size: $font-sm;
  text-align: center;
}

.btn-outline--disabled {
  opacity: 0.45;
}

.btn-danger {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  padding: $spacing-md 0;
  border-radius: $radius-md;
  border: 1rpx solid $color-danger;
  color: $color-danger;
  font-size: $font-sm;
  text-align: center;
}

.btn-danger--disabled {
  opacity: 0.45;
}

.sheet__tip {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}
</style>
