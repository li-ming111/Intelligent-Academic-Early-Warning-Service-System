<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="学生管理" :show-back="false" />

    <role-hero
      :title="counselorName || '学生管理'"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <filter-bar v-if="classOptions.length > 1" v-model="classKey" :options="classOptions" />

    <view class="page-body page-body--tabbar">
      <view class="search">
        <input
          v-model="keyword"
          class="search__input"
          placeholder="搜索姓名或学号"
          placeholder-class="search__ph"
          confirm-type="search"
        />
        <text v-if="keyword" class="search__clear" @tap="keyword = ''">清空</text>
      </view>

      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!visible.length"
          :text="keyword ? '没有匹配的学生' : '暂无学生'"
          :hint="keyword ? '换个姓名或学号试试' : '该辅导员名下班级暂无学生档案'"
          icon="users"
        />

        <view v-else class="stu-list">
          <view v-for="s in visible" :key="s.id" class="stu-card" @tap="openDetail(s)">
            <view class="stu-card__avatar">{{ initialOf(s.name) }}</view>

            <view class="stu-card__main">
              <view class="stu-card__top">
                <text class="stu-card__name">{{ s.name || '未命名' }}</text>
                <text class="stu-card__class">{{ s.className || classOf(s) }}</text>
              </view>
              <text class="stu-card__no">{{ s.studentId || '--' }}</text>
              <view class="stu-card__tags">
                <text v-if="failOf(s)" class="tag tag--danger">挂 {{ failOf(s) }} 门</text>
                <text v-if="warningOf(s)" class="tag tag--warning">预警 {{ warningOf(s) }} 条</text>
                <text v-if="!failOf(s) && !warningOf(s)" class="tag tag--success">无挂科 · 无预警</text>
              </view>
            </view>

            <app-icon name="chevron" :size="26" color="#aeaeb2" />
          </view>
        </view>
      </template>
    </view>

    <!-- 学生详情：四个 tab 各自懒加载 -->
    <detail-sheet v-model="detailOpen" :title="detail.name || '学生详情'" height="82vh">
      <view v-if="detail.id" class="detail">
        <view class="detail__head">
          <view class="detail__avatar">{{ initialOf(detail.name) }}</view>
          <view class="detail__ids">
            <text class="detail__name">{{ detail.name || '未命名' }}</text>
            <text class="detail__sub">{{ detail.className || classOf(detail) }} · 学号 {{ detail.studentId || '--' }}</text>
          </view>
        </view>

        <view class="tabs">
          <view
            v-for="t in TABS"
            :key="t.key"
            class="tabs__item"
            :class="{ 'tabs__item--on': tab === t.key }"
            @tap="tab = t.key"
          >{{ t.label }}</view>
        </view>

        <!-- 概览 -->
        <view v-if="tab === 'info'" class="pane">
          <view class="kv">
            <text class="kv__k">学号</text>
            <text class="kv__v">{{ detail.studentId || '--' }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">班级</text>
            <text class="kv__v">{{ detail.className || classOf(detail) || '--' }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">已修 / 应修学分</text>
            <text class="kv__v">
              {{ credit ? `${numOr(credit.score)} / ${numOr(credit.attemptedCredits)}` : '--' }}
            </text>
          </view>
          <view class="kv">
            <text class="kv__k">未通过课程</text>
            <text class="kv__v">{{ credit ? numOr(credit.failCount) : '--' }} 门</text>
          </view>
          <view class="kv">
            <text class="kv__k">预警条数</text>
            <text class="kv__v">{{ myWarnings.length }} 条（待处理 {{ myPending }} 条）</text>
          </view>
          <text v-if="!credit" class="pane__note">该生没有挂科记录，因此不在「学分不足」名单里 —— 上面的学分为空属正常，不是加载失败。</text>
        </view>

        <!-- 成绩趋势 -->
        <view v-else-if="tab === 'score'" class="pane">
          <page-loading v-if="subLoading.score" :count="2" mode="card" />
          <empty-state
            v-else-if="!trend.length"
            text="暂无成绩数据"
            hint="该生还没有期末已录入的成绩"
            icon="book"
            :top="20"
          />
          <template v-else>
            <view class="trend">
              <view v-for="h in trend" :key="h.semester" class="trend__col">
                <text class="trend__value">{{ h.avg }}</text>
                <view class="trend__bar-wrap">
                  <view class="trend__bar" :style="{ height: h.height }" />
                </view>
                <text class="trend__label">{{ h.short }}</text>
              </view>
            </view>
            <text class="pane__note">纵轴 0–100 分。已排除「期末未录入」的成绩行 —— 那些行的总评只是平时分折算，画进趋势会被误读成一次暴跌。</text>
            <view class="course-list">
              <view v-for="c in lastSemesterCourses" :key="c.id" class="course-row">
                <text class="course-row__name">{{ c.course_name }}</text>
                <score-cell :score="c.score_total" />
              </view>
            </view>
            <text class="pane__note">上面是最近一学期（{{ trend.length ? trend[trend.length - 1].short : '--' }}）的逐门成绩。</text>
          </template>
        </view>

        <!-- 帮扶计划 -->
        <view v-else-if="tab === 'plan'" class="pane">
          <page-loading v-if="subLoading.plan" :count="2" mode="card" />
          <empty-state
            v-else-if="!plans.length"
            text="暂无帮扶计划"
            hint="可在下方「新建帮扶计划」为该生建档"
            icon="target"
            :top="20"
          />
          <view v-else>
            <view v-for="p in plans" :key="p.id" class="plan">
              <view class="plan__head">
                <text class="plan__title">学业帮扶计划</text>
                <text class="tag" :class="planTagClass(p.status)">{{ planStatusText(p.status) }}</text>
              </view>
              <text class="plan__content">{{ p.content }}</text>
              <view class="plan__bar">
                <view class="track"><view class="track__fill" :style="{ width: clampPct(p.progress) }" /></view>
                <text class="plan__pct">{{ numOr(p.progress) }}%</text>
              </view>
            </view>
          </view>
          <view class="pane__action" @tap="goCreatePlan">新建帮扶计划</view>
        </view>

        <!-- 沟通记录 -->
        <view v-else class="pane">
          <page-loading v-if="subLoading.log" :count="2" mode="card" />
          <empty-state
            v-else-if="!logs.length"
            text="暂无沟通记录"
            hint="约谈、帮扶跟进、日常关怀都可以记在这里"
            icon="doc"
            :top="20"
          />
          <view v-else>
            <view v-for="l in logs" :key="l.id" class="log">
              <view class="log__head">
                <text class="tag tag--info">{{ l.category || '未分类' }}</text>
                <text class="log__time">{{ fmt(l.createdAt) }}</text>
              </view>
              <text class="log__content">{{ l.content }}</text>
              <text v-if="l.reply" class="log__reply">学生反馈：{{ l.reply }}</text>
            </view>
          </view>

          <text class="pane__label">新增沟通记录</text>
          <view class="chips">
            <view
              v-for="c in CATEGORIES"
              :key="c"
              class="chip"
              :class="{ 'chip--on': form.category === c }"
              @tap="form.category = c"
            >{{ c }}</view>
          </view>
          <textarea
            v-model="form.content"
            class="textarea"
            placeholder="记录沟通内容与处理意见"
            placeholder-class="textarea__ph"
            :maxlength="500"
            auto-height
          />
          <textarea
            v-model="form.reply"
            class="textarea textarea--short"
            placeholder="学生反馈（选填）"
            placeholder-class="textarea__ph"
            :maxlength="200"
            auto-height
          />
          <view
            class="pane__action"
            :class="{ 'pane__action--disabled': !form.content.trim() }"
            @tap="submitLog"
          >{{ savingLog ? '提交中…' : '保存沟通记录' }}</view>
        </view>
      </view>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { normalizeWarnings } from '@/utils/warning.js'
import { formatDate, shortSemester } from '@/utils/format.js'
import { go } from '@/utils/navigate.js'
import { PLAN_STATUS_TEXT, dictText } from '@/constants/dict.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')

/**
 * 沟通记录的「分类」取值。
 * 这三个词不是新造的 —— 它们就是库里 `communication_logs.category` 现有的三个值
 * （预警约谈 / 帮扶跟进 / 日常关怀）。新增取值前应先确认库里出现过，否则等于自造词表。
 */
const CATEGORIES = ['预警沟通', '帮扶跟进', '日常关怀']
const TABS = [
  { key: 'info', label: '概览' },
  { key: 'score', label: '成绩' },
  { key: 'plan', label: '帮扶' },
  { key: 'log', label: '沟通' }
]

const loading = ref(true)
const students = ref([])
/** 学分不足名单（有挂科的人），按 profile id 建索引 */
const insufficient = ref({})
const warnings = ref([])
const keyword = ref('')
const classKey = ref('')

const detailOpen = ref(false)
const detail = ref({})
const tab = ref('info')
const subLoading = ref({ score: false, plan: false, log: false })
const trend = ref([])
const plans = ref([])
const logs = ref([])
const form = ref({ category: '日常关怀', content: '', reply: '' })
const savingLog = ref(false)

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const subtitle = computed(() => {
  const n = students.value.length
  const c = classOptions.value.length
  return n ? `共 ${n} 名学生${c > 1 ? ` · ${c} 个班` : ''}` : '所带班级的学生'
})

/** 该生对应的学分不足行（没有则 null —— 说明没挂科，不是加载失败） */
const credit = computed(() => {
  if (!detail.value.id) return null
  return insufficient.value[String(detail.value.id)] || null
})

/** 该生的预警（academic_warning.student_id 就是 profile id，两者同一空间，可直接比） */
const myWarnings = computed(() =>
  warnings.value.filter(w => String(w.studentId) === String(detail.value.id))
)
const myPending = computed(() => myWarnings.value.filter(w => w.isPending).length)

const heroStats = computed(() => [
  { label: '学生总数', value: students.value.length },
  { label: '班级', value: classOptions.value.length ? classOptions.value.length - 1 : 0 },
  { label: '存在挂科', value: Object.keys(insufficient.value).length },
  { label: '预警学生', value: warnedStudentIds.value.size }
])

const warnedStudentIds = computed(() => {
  const s = new Set()
  warnings.value.forEach(w => {
    if (w.studentId !== null && w.studentId !== undefined) s.add(String(w.studentId))
  })
  return s
})

const classOptions = computed(() => {
  const names = []
  students.value.forEach(s => {
    const n = s.className || classOf(s)
    if (n && !names.includes(n)) names.push(n)
  })
  return [{ label: '全部', value: '', count: students.value.length }]
    .concat(names.map(n => ({ label: n, value: n, count: students.value.filter(s => (s.className || classOf(s)) === n).length })))
})

const visible = computed(() => {
  const kw = keyword.value.trim()
  return students.value.filter(s => {
    if (classKey.value && (s.className || classOf(s)) !== classKey.value) return false
    if (!kw) return true
    return String(s.name || '').includes(kw) || String(s.studentId || '').includes(kw)
  })
})

const lastSemesterCourses = computed(() => {
  const last = trend.value.length ? trend.value[trend.value.length - 1].semester : ''
  return rawTrend.value.filter(r => String(r.semester) === String(last))
})

/** 趋势图的原始行，保留下来给"最近一学期逐门"用 */
const rawTrend = ref([])

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function fmt(v) {
  return formatDate(v, true)
}

function initialOf(name) {
  return name ? String(name).slice(0, 1) : '?'
}

/**
 * 学生行上的班级名。
 * `/counselors/students` 返回的是 Student 实体，`className` 是 `@TableField(exist = false)`
 * 的非库字段，由 `StudentServiceImpl` 手工回填；万一没回填上就退回按 classId 匹配，
 * 不显示空串。
 */
function classOf(s) {
  const hit = students.value.find(x => String(x.classId) === String(s.classId) && x.className)
  return hit ? hit.className : ''
}

function failOf(s) {
  const c = insufficient.value[String(s.id)]
  return c ? numOr(c.failCount) : 0
}

function warningOf(s) {
  return warnings.value.filter(w => String(w.studentId) === String(s.id)).length
}

/**
 * 帮扶计划状态文案。
 * 与**学生端帮扶页**共用同一份字典 —— 同一条 `assistance_plans` 记录，
 * 学生端说「进行中」辅导员端不该说「处理中」。
 * （后端 `/students/assistance` 自己那套文案是「未开始」，学生页已放弃它改用本地字典。）
 */
function planStatusText(status) {
  return dictText(PLAN_STATUS_TEXT, Number(status))
}

function planTagClass(status) {
  const n = Number(status)
  return n === 2 ? 'tag--success' : n === 1 ? 'tag--info' : 'tag--pending'
}

function clampPct(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0%'
  return Math.max(0, Math.min(100, n)) + '%'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [list, insuf, warns] = await Promise.all([
      counselorAPI.getStudents(userId).catch(e => { console.error('[students] 名单失败:', e); return [] }),
      counselorAPI.getCreditInsufficientStudents(userId, 1, 500).catch(e => { console.error('[students] 学分不足失败:', e); return null }),
      counselorAPI.getWarnings(userId).catch(e => { console.error('[students] 预警失败:', e); return [] })
    ])

    students.value = Array.isArray(list) ? list : []

    // 学分不足列表：`profileId` 才是 profile id，`studentId` 那一列是**学号**，别拿错。
    const map = {}
    const rows = insuf && Array.isArray(insuf.list) ? insuf.list : []
    rows.forEach(r => {
      if (r.profileId !== null && r.profileId !== undefined) map[String(r.profileId)] = r
    })
    insufficient.value = map

    warnings.value = normalizeWarnings(warns)
  } finally {
    loading.value = false
  }
  openFocusFromQuery()
}

/** 打开详情。四个 tab 的数据按需拉，避免一次打四个请求。 */
async function openDetail(s) {
  detail.value = s
  tab.value = 'info'
  trend.value = []
  rawTrend.value = []
  plans.value = []
  logs.value = []
  form.value = { category: '日常关怀', content: '', reply: '' }
  detailOpen.value = true
  // 概览与帮扶计划成本低，进详情就一起取；成绩与沟通按需
  await Promise.all([loadPlans(s.id), loadLogs(s.id)])
}

async function loadPlans(profileId) {
  subLoading.value.plan = true
  try {
    const r = await counselorAPI.getStudentPlans(profileId)
    plans.value = Array.isArray(r) ? r : []
  } catch (e) {
    console.error('[students] 帮扶计划失败:', e)
    plans.value = []
  } finally {
    subLoading.value.plan = false
  }
}

async function loadLogs(profileId) {
  subLoading.value.log = true
  try {
    const r = await counselorAPI.getStudentCommunicationLogs(profileId)
    logs.value = Array.isArray(r) ? r : []
  } catch (e) {
    console.error('[students] 沟通记录失败:', e)
    logs.value = []
  } finally {
    subLoading.value.log = false
  }
}

async function loadTrend(profileId) {
  subLoading.value.score = true
  try {
    const r = await counselorAPI.getStudentScoreTrend(profileId)
    const rows = Array.isArray(r) ? r : []
    rawTrend.value = rows
    // 按学期聚合平均分。后端已按学期正序返回，这里再排一次序是防御性的 ——
    // 趋势图画反了比没有图更糟。
    const bySem = {}
    rows.forEach(x => {
      const k = String(x.semester || '未知')
      if (!bySem[k]) bySem[k] = []
      const v = Number(x.score_total)
      if (Number.isFinite(v)) bySem[k].push(v)
    })
    trend.value = Object.keys(bySem)
      .sort()
      .map(k => {
        const arr = bySem[k]
        const avg = arr.length ? arr.reduce((a, b) => a + b, 0) / arr.length : 0
        return {
          semester: k,
          avg: avg.toFixed(1),
          height: Math.max(4, Math.min(100, avg)) + '%',
          short: shortSemester(k)
        }
      })
  } catch (e) {
    console.error('[students] 成绩趋势失败:', e)
    trend.value = []
    rawTrend.value = []
  } finally {
    subLoading.value.score = false
  }
}

/** 切到成绩 tab 时再拉数据（只拉一次） */
function watchTab() {
  if (tab.value === 'score' && !rawTrend.value.length && detail.value.id) loadTrend(detail.value.id)
  if (tab.value === 'plan' && !plans.value.length && detail.value.id) loadPlans(detail.value.id)
  if (tab.value === 'log' && !logs.value.length && detail.value.id) loadLogs(detail.value.id)
}

async function submitLog() {
  if (!form.value.content.trim() || savingLog.value) return
  savingLog.value = true
  try {
    /**
     * 沟通记录是辅导员端**少数几个真写库的入口之一**。
     * 请求体的 id 空间极易搞错（`CommunicationLog` 实体里三种 id 并列）：
     *   counselorId → users.id（53）   studentId → **profile id**（3）
     * `warningId` 本页不填：这些沟通大多是日常的，硬挂到某条预警上是编造关联。
     */
    await counselorAPI.createCommunicationLog({
      counselorId: userId,
      studentId: detail.value.id,
      content: form.value.content.trim(),
      reply: form.value.reply.trim() || null,
      category: form.value.category,
      status: 0
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    form.value = { category: '日常关怀', content: '', reply: '' }
    await loadLogs(detail.value.id)
  } catch (e) {
    console.error('[students] 保存沟通记录失败:', e)
    uni.showToast({ title: e && e.message ? e.message : '保存失败', icon: 'none' })
  } finally {
    savingLog.value = false
  }
}

function goCreatePlan() {
  detailOpen.value = false
  go('/pages/counselor/assistance')
}

/** tab 切到哪个再拉哪个的数据，不一次性打四个请求 */
watch(tab, watchTab)

/**
 * credit-monitor / class-management 通过 `?focus=<profileId>` 直接打开某名学生。
 * 必须在 `load()` **之后**查表 —— 名单还没回来时 `students` 是空数组，
 * 那时候找 `focus` 必然找不到，页面会静默地只停在列表上。
 */
function openFocusFromQuery() {
  const pages = getCurrentPages()
  const cur = pages[pages.length - 1]
  const focus = cur && cur.options ? cur.options.focus : ''
  if (!focus) return
  const hit = students.value.find(s => String(s.id) === String(focus))
  if (hit) openDetail(hit)
  else uni.showToast({ title: '该学生不在你所带班级', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
  margin: 0 $page-padding;
  padding: 0 $spacing-md;
  height: $tap-min;
  border-radius: $radius-md;
  background: $color-card;
  box-shadow: $shadow-card;
}

.search__input {
  flex: 1;
  min-width: 0;
  height: $tap-min;
  font-size: $font-sm;
  color: $color-text;
}

.search__ph {
  color: $color-text-hint;
}

.search__clear {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary);
}

/* ---------- 学生列表 ---------- */
.stu-list {
  margin-top: $spacing-md;
  padding: 0 $page-padding;
}

.stu-card {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  @include card;
}

.stu-card__avatar {
  width: 76rpx;
  height: 76rpx;
  flex-shrink: 0;
  margin-right: $spacing-md;
  border-radius: $radius-pill;
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-counselor});
  font-size: $font-lg;
  font-weight: 600;
  @include flex-center;
}

.stu-card__main {
  flex: 1;
  min-width: 0;
}

.stu-card__top {
  display: flex;
  align-items: baseline;
}

.stu-card__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.stu-card__class {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.stu-card__no {
  display: block;
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.stu-card__tags {
  display: flex;
  flex-wrap: wrap;
  margin-top: $spacing-xs;
}

.stu-card__tags .tag {
  margin-right: $spacing-xs;
}

/* ---------- 详情 ---------- */
.detail {
  padding: 0 $page-padding $spacing-2xl;
}

.detail__head {
  display: flex;
  align-items: center;
  padding-bottom: $spacing-lg;
}

.detail__avatar {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  margin-right: $spacing-md;
  border-radius: $radius-pill;
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-counselor});
  font-size: $font-xl;
  font-weight: 600;
  @include flex-center;
}

.detail__ids {
  flex: 1;
  min-width: 0;
}

.detail__name {
  display: block;
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.detail__sub {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.tabs {
  display: flex;
  padding: 6rpx;
  border-radius: $radius-md;
  background: $color-bg;
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
  background: $color-card;
  color: var(--color-primary, #{$role-counselor});
  font-weight: 600;
}

.pane {
  padding-top: $spacing-lg;
}

.pane__note {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.pane__label {
  display: block;
  margin-top: $spacing-xl;
  font-size: $font-sm;
  color: $color-text;
}

.pane__action {
  margin-top: $spacing-md;
  padding: $spacing-md 0;
  border-radius: $radius-md;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-sm;
  text-align: center;
}

.pane__action--disabled {
  opacity: 0.45;
}

/* ---------- 概览 ---------- */
.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.kv__k {
  font-size: $font-sm;
  color: $color-text-secondary;
}

.kv__v {
  font-size: $font-sm;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

/* ---------- 趋势 ---------- */
.trend {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 220rpx;
}

.trend__col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.trend__value {
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.trend__bar-wrap {
  flex: 1;
  width: 36rpx;
  display: flex;
  align-items: flex-end;
  margin: $spacing-xs 0;
}

.trend__bar {
  width: 100%;
  border-radius: $radius-sm;
  background: var(--color-primary);
  opacity: 0.85;
}

.trend__label {
  font-size: 20rpx;
  color: $color-text-hint;
}

.course-list {
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.course-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm 0;
}

.course-row__name {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

/* ---------- 帮扶 ---------- */
.plan {
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.plan__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plan__title {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.plan__content {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
}

.plan__bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.track {
  flex: 1;
  min-width: 0;
  height: 12rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.track__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.plan__pct {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

/* ---------- 沟通 ---------- */
.log {
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.log__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.log__time {
  font-size: $font-xs;
  color: $color-text-hint;
}

.log__content {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

.log__reply {
  display: block;
  margin-top: $spacing-xs;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-sm;
  background: $color-bg;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.6;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  margin-top: $spacing-sm;
}

.chip {
  margin: 0 $spacing-sm $spacing-sm 0;
  padding: 8rpx 22rpx;
  border-radius: $radius-pill;
  border: 1rpx solid $color-border;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.chip--on {
  border-color: var(--color-primary);
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-counselor});
}

.textarea {
  width: 100%;
  min-height: 160rpx;
  margin-top: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.6;
}

.textarea--short {
  min-height: 110rpx;
}
</style>
