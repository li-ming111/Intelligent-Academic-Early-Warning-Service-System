<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="预警总览" :show-back="false" />

    <role-hero
      :title="userName || '预警总览'"
      subtitle="全校预警，点等级数字可按等级筛选"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <filter-bar v-model="levelFilter" :options="levelOptions" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ warnings.length }}</text> 条 ·
            待处理 <text class="summary__num summary__num--danger">{{ pendingCount }}</text> 条
          </text>
          <text class="summary__hint">按预警等级筛选</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="warnings.length ? '这个等级没有预警' : '暂无预警记录'"
          :hint="warnings.length ? '换一个等级看看' : '没有学生触发预警规则'"
          icon="check"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(w, i) in filtered"
            :key="w.id"
            class="warn"
            :class="{ 'warn--divided': i > 0 }"
            @tap="openDetail(w)"
          >
            <view class="warn__head">
              <warning-tag :level="w.warningLevel" />
              <text class="warn__name">{{ w.studentName || `学生 ${w.studentId}` }}</text>
              <text class="tag" :class="warningStatusTagClass(w.status)">
                {{ w.statusText }}
              </text>
            </view>
            <text class="warn__desc">{{ w.description || w.warningMessage || '无说明' }}</text>
            <!-- W39：挂科课程明细 + 班级（此前 /admin/warnings 直接回裸实体，
                 没有 className，学院管理端那一列只能是空）。 -->
            <view v-if="(w.courses || []).length" class="warn__courses">
              <text
                v-for="c in w.courses"
                :key="c.courseId"
                class="course-chip"
              >{{ c.courseName }} {{ fmtScore(c.scoreTotal) }}</text>
            </view>
            <view class="warn__foot">
              <text class="warn__rule">{{ ruleName(w.ruleId) }}</text>
              <text v-if="w.className" class="warn__class">{{ w.className }}</text>
              <text class="warn__time">{{ formatDate(w.createdAt, true) }}</text>
            </view>
          </view>
        </view>

      </template>
    </view>

    <app-tab-bar role="admin" />

    <!-- ============ 详情 / 处理 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" :height="mode === 'handle' ? '68vh' : '78vh'">
      <!-- ---------- 详情 ---------- -->
      <view v-if="mode === 'detail' && current" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">学生</text>
            <text class="facts__v">{{ current.studentName || `学号 ${current.studentId}` }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">预警等级</text>
            <text class="facts__v">{{ levelText(current.warningLevel) }}（{{ current.title || '--' }}）</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">触发规则</text>
            <text class="facts__v">{{ ruleName(current.ruleId) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">触发成绩</text>
            <text class="facts__v">{{ current.triggerScore === null || current.triggerScore === undefined ? '未记录' : current.triggerScore }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">触发时间</text>
            <text class="facts__v">{{ formatDate(current.createdAt, true) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">处理状态</text>
            <text class="facts__v">{{ current.statusText }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">申诉状态</text>
            <text class="facts__v">{{ appealText(current.appealStatus) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">学院</text>
            <text class="facts__v">{{ current.collegeId === null || current.collegeId === undefined ? '未填（这条预警没写学院）' : current.collegeId }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">预警前/后 GPA</text>
            <text class="facts__v">{{ gpaText(current) }}</text>
          </view>
          <view v-if="current.className" class="facts__row">
            <text class="facts__k">班级</text>
            <text class="facts__v">{{ current.className }}</text>
          </view>
          <view v-if="current.courses && current.courses.length" class="facts__row">
            <text class="facts__k">挂科课程</text>
            <text class="facts__v">{{ current.courses.length }} 门：{{ current.courseNames }}</text>
          </view>
        </view>

        <section-title plain title="预警说明" />
        <text class="paragraph">{{ current.description || current.warningMessage || '（无）' }}</text>

        <template v-if="current.isHandled">
          <section-title plain title="处理记录" />
          <view class="facts">
            <view class="facts__row">
              <text class="facts__k">处理结果</text>
              <text class="facts__v">{{ current.handleResult || '（未填）' }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">处理备注</text>
              <text class="facts__v">{{ current.handledBy || '（未填）' }}</text>
            </view>
            <view class="facts__row">
              <text class="facts__k">处理时间</text>
              <text class="facts__v">{{ formatDate(current.handledAt, true) }}</text>
            </view>
          </view>
        </template>
        <text v-else-if="current.isDispelled" class="note">
          这条预警由系统自动解除：成绩重算后已不再达到预警阈值，因此没有人工处理记录。
        </text>
        <text v-else class="note">
          还没有处理记录。
        </text>
      </view>

      <!-- ---------- 处理 ---------- -->
      <view v-else-if="mode === 'handle' && current" class="sheet">
        <text class="handle__who">
          {{ current.studentName || `学号 ${current.studentId}` }} ·
          {{ levelText(current.warningLevel) }} · {{ ruleName(current.ruleId) }}
        </text>
        <view class="field">
          <text class="field__label">处理结果</text>
          <textarea
            v-model="handleForm.handleResult"
            class="field__area"
            :maxlength="-1"
            auto-height
            placeholder="如：已联系学生谈话，安排补考辅导"
            placeholder-class="field__ph"
          />
        </view>
        <view class="field">
          <text class="field__label">处理备注</text>
          <textarea
            v-model="handleForm.remark"
            class="field__area"
            :maxlength="-1"
            auto-height
            placeholder="补充说明（会写进 handled_by 列）"
            placeholder-class="field__ph"
          />
        </view>
        <text class="note">提交后预警状态将置为「已处理」，当前版本不支持撤销。</text>
      </view>

      <template #footer>
        <template v-if="mode === 'detail'">
          <view class="acts">
            <view class="act act--ok" @tap="showRules">
              <app-icon name="doc" :size="30" color="#16a34a" />
              <text class="act__text">查看规则</text>
            </view>
          </view>
          <view
            v-if="current && current.isPending"
            class="btn-primary footer-btn"
            @tap="enterHandle"
          >处理这条预警</view>
          <view
            v-else-if="current && current.isDispelled"
            class="btn-primary btn-primary--disabled footer-btn"
          >这条预警已自动解除</view>
          <view v-else class="btn-primary btn-primary--disabled footer-btn">这条预警已处理</view>
        </template>

        <template v-else>
          <view class="form-acts">
            <view class="form-acts__cancel" @tap="mode = 'detail'">返回</view>
            <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="submitHandle">
              {{ submitting ? '提交中…' : '提交处理' }}
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
import { useUser } from '@/composables/useUser.js'
import { formatDate } from '@/utils/format.js'
// 2026-10-02（W1.6）：状态是三态（0 待处理 / 1 已处理 / 2 已解除）。
// 这一页原来自己写 `status === 1 ? '已处理' : '待处理'`，于是 status=2 被显示成"待处理"。
// 收敛到共用层（`utils/warning.js`），页面不再自己判状态。
import { normalizeWarning, normalizeWarnings, warningStatusTagClass } from '@/utils/warning.js'

const themeStyle = roleThemeStyle('admin')
const { userName } = useUser()

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

/** 点同一档再点一次 = 取消筛选，与筛选条的行为保持一致 */
function pick(level) {
  levelFilter.value = levelFilter.value === level ? '' : level
}

const heroStats = computed(() => {
  const count = l => warnings.value.filter(w => String(w.warningLevel) === l).length
  return [
    { label: '待处理', value: pendingCount.value },
    { label: '轻度', value: count('1'), onTap: () => pick('1') },
    { label: '中度', value: count('2'), onTap: () => pick('2') },
    { label: '严重', value: count('3'), onTap: () => pick('3') }
  ]
})

const levelOptions = [
  { label: '全部', value: '' },
  { label: '轻度', value: '1' },
  { label: '中度', value: '2' },
  { label: '严重', value: '3' }
]

const loading = ref(true)
const warnings = ref([])
const rules = ref([])
const levelFilter = ref('')

const sheetOpen = ref(false)
/** detail | handle */
const mode = ref('detail')
const current = ref(null)
const submitting = ref(false)
const handleForm = ref({ handleResult: '', remark: '' })

const sheetTitle = computed(() => {
  if (mode.value === 'handle') return '处理预警'
  return current.value ? (current.value.studentName || '预警详情') : '预警详情'
})

const filtered = computed(() => {
  if (!levelFilter.value) return warnings.value
  return warnings.value.filter(w => String(w.warningLevel) === levelFilter.value)
})

// 2026-10-02（W1.6）：原来是 `status !== 1`（"非已处理都算待处理"）⇒ status=2（已解除）
// 被算进待处理，首页/列表上"待处理 N 条"永远偏高。改成只认 0。
const pendingCount = computed(() => warnings.value.filter(w => w.status === 0).length)

function levelText(level) {
  const n = Number(level)
  if (n === 3) return '严重预警'
  if (n === 2) return '中度预警'
  if (n === 1) return '轻度预警'
  return level === null || level === undefined ? '未分级' : `未识别的等级 ${level}`
}

/** `appealStatus` 实测是个字符串 'none'，不是 null。 */
function appealText(v) {
  if (!v || v === 'none') return '无申诉'
  if (v === 'pending') return '申诉中'
  if (v === 'accepted') return '申诉已受理'
  if (v === 'rejected') return '申诉被驳回'
  return v
}

/**
 * 预警前/后的 GPA。两列在库里都是 NULL —— 这一版没有"预警前后算一次 GPA"的写入方，
 * 所以如实显示"未记录"，而不是算一个看起来像那么回事的数。
 */
function gpaText(w) {
  const before = w && w.gpaBefore !== null && w.gpaBefore !== undefined ? w.gpaBefore : null
  const after = w && w.gpaAfter !== null && w.gpaAfter !== undefined ? w.gpaAfter : null
  if (before === null && after === null) return '未记录'
  return `${before === null ? '--' : before} → ${after === null ? '--' : after}`
}

/** ruleId → 规则名。预警行只给 id，名字要靠规则接口对。 */
function ruleName(ruleId) {
  if (ruleId === null || ruleId === undefined) return '未关联规则'
  const r = rules.value.find(x => x.id === ruleId)
  return r ? `${r.ruleName}（${r.ruleCode || '无编码'}）` : `未匹配的规则 id ${ruleId}`
}

/** 分数可能是 56.5 也可能是整数 40 —— 后者别显示成 "40.0"。 */
function fmtScore(s) {
  if (s === null || s === undefined) return '--'
  return String(Number(s))
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

function showRules() {
  const id = current.value ? current.value.ruleId : null
  const r = rules.value.find(x => x.id === id)
  uni.showModal({
    title: r ? r.ruleName : '触发规则',
    content: r
      ? `编码：${r.ruleCode || '--'}\n判定条件：${r.expression || '--'}\n统计周期：${r.statisticCycle || '未填'}\n\n完整说明在「预警规则」页。`
      : '这条预警没有关联到任何一条现存规则。',
    showCancel: false
  })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [w, r] = await Promise.all([
      adminAPI.getWarnings().catch(e => { console.error('[admin warnings] 预警失败:', e); return null }),
      adminAPI.getRules().catch(e => { console.error('[admin warnings] 规则失败:', e); return null })
    ])
    warnings.value = normalizeWarnings(Array.isArray(w) ? w : [])
    rules.value = Array.isArray(r) ? r : []
  } finally {
    loading.value = false
  }
}

function openDetail(w) {
  current.value = w
  mode.value = 'detail'
  sheetOpen.value = true
  // 列表行已经是完整实体（含 handleResult / handledBy / handledAt），
  // 不额外请求详情；下面这个只是为了拿到"别人刚改过"的最新状态
  adminAPI.getWarningById(w.id)
    .then(fresh => {
      if (fresh && typeof fresh === 'object' && current.value && current.value.id === w.id) {
        // ⚠️ 详情接口回的是**原始**实体，必须同样过一遍归一化 ——
        // 否则这里一覆盖，上面 load() 归一化出来的 statusText/isHandled 就全没了，
        // 页面又会退回自己判 status。这是"归一化只做了一半"最典型的一脚。
        current.value = normalizeWarning(fresh)
      }
    })
    .catch(e => console.error('[admin warnings] 预警详情失败:', e))
}

function enterHandle() {
  handleForm.value = { handleResult: '', remark: '' }
  mode.value = 'handle'
}

async function submitHandle() {
  if (submitting.value) return
  const f = handleForm.value
  if (!String(f.handleResult || '').trim()) {
    toast('请填写处理结果')
    return
  }
  submitting.value = true
  try {
    // `handledBy` 必须显式带上：后端原来是把 **remark 写进「处理人」列**、从不读这个键，
    // 于是详情里「处理人」显示的是备注文本。后端已改为读 handledBy，这里补上当前登录者。
    const operator = uni.getStorageSync('userName') || uni.getStorageSync('username') || ''
    await adminAPI.handleWarning(current.value.id, {
      handleResult: String(f.handleResult).trim(),
      remark: String(f.remark || '').trim(),
      handledBy: String(operator).trim()
    })
    toast('已提交处理')
    const id = current.value.id
    sheetOpen.value = false
    await load()
    // 重新打开详情，让人看到落库后的样子（而不是只信那句 toast）
    const fresh = warnings.value.find(x => x.id === id)
    if (fresh) openDetail(fresh)
  } catch (e) {
    toast((e && e.message) || '提交失败', 3200)
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
}

.summary {
  display: flex;
  align-items: baseline;
  padding: 0 $page-padding $spacing-sm;
}

.summary__text {
  flex-shrink: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.summary__num {
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.summary__num--danger {
  color: $color-danger;
}

.summary__hint {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  text-align: right;
  @include ellipsis;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 预警行 ---------- */
.warn {
  padding: $spacing-lg;
}

.warn--divided {
  border-top: 1rpx solid $color-divider;
}

.warn:active {
  background: $color-bg;
}

.warn__head {
  display: flex;
  align-items: center;
}

.warn__name {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.warn__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
  @include ellipsis-multi(2);
}

/* W39 挂科课程明细 */
.warn__courses {
  display: flex;
  flex-wrap: wrap;
  margin-top: $spacing-sm;
}

.course-chip {
  font-size: $font-xs;
  color: $color-text-secondary;
  background: $color-divider;
  border-radius: $radius-sm;
  padding: 4rpx 12rpx;
  margin: 0 $spacing-xs $spacing-xs 0;
}

.warn__class {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.warn__foot {
  display: flex;
  align-items: baseline;
  margin-top: $spacing-sm;
}

.warn__rule {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: var(--color-primary);
  @include ellipsis;
}

.warn__time {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 详情 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-xl;
}

.facts {
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
  width: 200rpx;
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

.paragraph {
  display: block;
  padding: $spacing-md $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  line-height: 1.7;
  color: $color-text;
}

/* ---------- 处理表单 ---------- */
.handle__who {
  display: block;
  margin-bottom: $spacing-lg;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: var(--color-primary-weak, #{$color-bg});
  font-size: $font-sm;
  color: var(--color-primary);
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

.field__area {
  width: 100%;
  box-sizing: border-box;
  min-height: 140rpx;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  line-height: 1.6;
  color: $color-text;
}

.field__ph {
  color: $color-text-hint;
}

.note {
  display: block;
  margin: $spacing-lg 0 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}

/* ---------- 底部 ---------- */
.acts {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: $spacing-md;
}

.act {
  display: flex;
  align-items: center;
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-md;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.act--ok {
  background: rgba(34, 197, 94, 0.12);
  border-color: transparent;
  color: $color-success;
}

.act__text {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: inherit;
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
