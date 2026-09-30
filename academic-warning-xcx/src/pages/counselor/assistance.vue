<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="帮扶计划" :show-back="false" />

    <role-hero
      :title="counselorName || '帮扶计划'"
      subtitle="进度会同步到学生端，学生可对已完成计划做评价"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <filter-bar v-model="statusKey" :options="statusOptions" />

    <view class="page-body page-body--tabbar">
      <view class="bar">
        <text class="bar__text">
          {{ statusKey === '' ? `共 ${plans.length} 条计划` : `${statusLabel} ${visible.length} 条` }}
        </text>
        <view class="bar__btn bar__btn--primary" @tap="openCreate">新建计划</view>
      </view>

      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <empty-state
          v-if="!visible.length"
          :text="plans.length ? `没有${statusLabel}的计划` : '暂无帮扶计划'"
          :hint="plans.length
            ? '换个状态看看'
            : '为存在挂科或预警的学生建一条，进度会同步到学生端'"
          icon="target"
        />

        <view v-else class="plan-list">
          <view v-for="p in visible" :key="p.id" class="plan-card" @tap="openEdit(p)">
            <view class="plan-card__head">
              <view class="plan-card__who">
                <text class="plan-card__name">{{ nameOf(p) }}</text>
                <text class="plan-card__class">{{ classOf(p) }}</text>
              </view>
              <text class="tag" :class="statusTag(p.status)">{{ statusText(p.status) }}</text>
            </view>

            <text class="plan-card__content">{{ p.content || '（未填写计划内容）' }}</text>

            <view class="plan-card__bar">
              <view class="track">
                <view class="track__fill" :style="{ width: pct(p.progress) }" />
              </view>
              <text class="plan-card__pct">{{ numOr(p.progress) }}%</text>
            </view>

            <view class="plan-card__foot">
              <text class="plan-card__time">建立于 {{ fmt(p.createdAt) }}</text>
              <text class="plan-card__op">调整进度 ›</text>
            </view>
          </view>
        </view>
      </template>
    </view>

    <!-- 新建 / 编辑 -->
    <detail-sheet v-model="sheetOpen" :title="editing ? '编辑帮扶计划' : '新建帮扶计划'" height="76vh">
      <view class="form">
        <!-- 新建：选学生。编辑时学生不可改 —— 改学生等于换了一条计划，
             那应该是删掉重建，而不是把 A 的记录挂到 B 名下。 -->
        <template v-if="!editing">
          <text class="form__label">帮扶对象</text>
          <picker
            mode="selector"
            :range="studentLabels"
            :value="studentIndex"
            @change="onStudentChange"
          >
            <view class="form__picker">
              <text :class="studentIndex < 0 ? 'form__ph' : 'form__picked'">
                {{ studentIndex < 0 ? '选择一名学生' : studentLabels[studentIndex] }}
              </text>
              <app-icon name="chevron" :size="26" color="#aeaeb2" />
            </view>
          </picker>
          <text v-if="!students.length" class="form__tip">
            你所带班级暂无学生档案，无法建立帮扶计划。
          </text>
        </template>

        <template v-else>
          <view class="form__head">
            <text class="form__who">{{ nameOf(editing) }}</text>
            <text class="tag" :class="statusTag(editing.status)">{{ statusText(editing.status) }}</text>
          </view>
        </template>

        <text class="form__label">计划内容</text>
        <textarea
          v-model="form.content"
          class="form__textarea"
          placeholder="写清帮扶目标与具体安排，例如：每周二、四晚自习集中答疑，期中前补完高数前三章"
          placeholder-class="form__placeholder"
          :maxlength="500"
          auto-height
        />
        <text class="form__count">{{ form.content.length }} / 500</text>

        <!-- 进度控件：**只有一个**。
             后端 `updatePlanProgress` 会顺带同步 status（0→待开始 / 100→已完成 / 其余→进行中），
             再单独放一个状态下拉就能造出"进度 50% 但状态已完成"这种自相矛盾的记录。 -->
        <template v-if="editing">
          <view class="form__row">
            <text class="form__label form__label--inline">完成进度</text>
            <text class="form__value">{{ form.progress }}%</text>
          </view>
          <slider
            class="form__slider"
            :value="form.progress"
            :min="0"
            :max="100"
            :step="5"
            :active-color="primaryColor"
            :block-size="22"
            @change="onProgressChange"
          />
          <view class="presets">
            <view
              v-for="v in PRESETS"
              :key="v"
              class="preset"
              :class="{ 'preset--on': form.progress === v }"
              @tap="form.progress = v"
            >{{ v }}%</view>
          </view>
          <!-- 说明文案必须写在**同一行**：小程序编译器会把 text 内的换行压成一个空格，
               中文句子中间凭空多出一个空格看着像错别字。 -->
          <text class="form__tip">保存进度会同时改写状态：0% 为待开始、100% 为已完成、其余为进行中（后端强联动）。当前将同步为「{{ statusText(derivedStatus) }}」。</text>
        </template>
        <text v-else class="form__tip">新建的计划一律落为「待开始 / 0%」—— 后端创建时会强制覆盖状态与进度，因此这里不提供初始进度，建成后再调。</text>
      </view>

      <template #footer>
        <view
          class="btn-primary"
          :class="{ 'btn-primary--disabled': !canSubmit }"
          @tap="submit"
        >{{ saving ? '提交中…' : (editing ? '保存' : '创建计划') }}</view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { counselorAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle, themeOf } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { PLAN_STATUS_TEXT, dictText } from '@/constants/dict.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('counselor')
/** slider 的 active-color 只吃字面色值，CSS 变量在这里无效 */
const primaryColor = themeOf('counselor').primary

const PRESETS = [0, 50, 100]

const loading = ref(true)
/** 已按 counselorId 过滤过的计划（见 load() 的注释） */
const plans = ref([])
/** profile id → {name, className}，用于把计划行的 studentId 还原成人名 */
const studentMap = ref({})
const students = ref([])
const statusKey = ref('')

const sheetOpen = ref(false)
/** null = 新建；否则是被编辑的计划行 */
const editing = ref(null)
const form = ref({ content: '', progress: 0 })
const studentIndex = ref(-1)
const saving = ref(false)

const counselorName = computed(() => userName || '')
const avatarText = computed(() => (counselorName.value ? String(counselorName.value).slice(0, 1) : ''))

const statusLabel = computed(() => dictText(PLAN_STATUS_TEXT, Number(statusKey.value)))

const summary = computed(() => {
  const s = { total: 0, 0: 0, 1: 0, 2: 0, students: 0 }
  const ids = new Set()
  plans.value.forEach(p => {
    s.total += 1
    const st = normStatus(p.status)
    s[st] += 1
    if (p.studentId !== null && p.studentId !== undefined) ids.add(String(p.studentId))
  })
  s.students = ids.size
  return s
})

const heroStats = computed(() => {
  const s = summary.value
  return [
    { label: '计划总数', value: s.total, onTap: () => pick('') },
    { label: '进行中', value: s[1], onTap: () => pick('1') },
    { label: '已完成', value: s[2], onTap: () => pick('2') },
    { label: '覆盖学生', value: s.students }
  ]
})

const statusOptions = computed(() => {
  const s = summary.value
  const opts = [{ label: '全部', value: '', count: s.total }]
  if (s[0] > 0) opts.push({ label: '待开始', value: '0', count: s[0] })
  if (s[1] > 0) opts.push({ label: '进行中', value: '1', count: s[1] })
  if (s[2] > 0) opts.push({ label: '已完成', value: '2', count: s[2] })
  return opts
})

const visible = computed(() => {
  if (statusKey.value === '') return plans.value
  const want = Number(statusKey.value)
  return plans.value.filter(p => normStatus(p.status) === want)
})

const studentLabels = computed(() =>
  students.value.map(s => `${s.name || '未命名'} · ${s.studentId || '--'}`)
)

/** 进度预览出的状态，与后端 `updatePlanProgress` 的联动规则逐条对齐 */
const derivedStatus = computed(() => {
  const v = Number(form.value.progress)
  if (!Number.isFinite(v) || v <= 0) return 0
  return v >= 100 ? 2 : 1
})

const canSubmit = computed(() => {
  if (!form.value.content.trim()) return false
  if (!editing.value && studentIndex.value < 0) return false
  return true
})

function numOr(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function fmt(v) {
  return formatDate(v, true)
}

function normStatus(v) {
  const n = Number(v)
  return Number.isFinite(n) && n >= 0 && n <= 2 ? n : 0
}

function statusText(v) {
  return dictText(PLAN_STATUS_TEXT, normStatus(v))
}

function statusTag(v) {
  const n = normStatus(v)
  return n === 2 ? 'tag--success' : n === 1 ? 'tag--info' : 'tag--pending'
}

function pct(v) {
  return Math.max(0, Math.min(100, numOr(v))) + '%'
}

/** 计划行里只有 studentId（profile id），人名/班级要自己 join 回来 */
function nameOf(p) {
  const hit = studentMap.value[String(p && p.studentId)]
  return (hit && hit.name) || '未知学生'
}

function classOf(p) {
  const hit = studentMap.value[String(p && p.studentId)]
  return (hit && hit.className) || ''
}

function pick(value) {
  statusKey.value = statusKey.value === value ? '' : value
}

function onStudentChange(e) {
  const i = Number(e && e.detail ? e.detail.value : -1)
  studentIndex.value = Number.isFinite(i) ? i : -1
}

function onProgressChange(e) {
  const v = Number(e && e.detail ? e.detail.value : 0)
  form.value.progress = Number.isFinite(v) ? v : 0
}

function openCreate() {
  editing.value = null
  studentIndex.value = -1
  // 新建时进度锁死在 0：后端 createPlan 会强制覆盖成 0，
  // 界面上让用户拖一个"初始进度"等于骗他。
  form.value = { content: '', progress: 0 }
  sheetOpen.value = true
}

function openEdit(p) {
  editing.value = p
  form.value = { content: p.content || '', progress: numOr(p.progress) }
  sheetOpen.value = true
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const [rawPlans, list] = await Promise.all([
      counselorAPI.getCounselorPlans(userId).catch(e => { console.error('[assistance] 计划失败:', e); return [] }),
      counselorAPI.getStudents(userId).catch(e => { console.error('[assistance] 学生失败:', e); return [] })
    ])

    students.value = Array.isArray(list) ? list : []

    const map = {}
    students.value.forEach(s => {
      if (s.id !== null && s.id !== undefined) {
        map[String(s.id)] = { name: s.name, className: s.className || '' }
      }
    })
    studentMap.value = map

    /**
     * 后端 `/counselors/{counselorId}/assistance-plans` **完全忽略路径里的 counselorId**
     * （实现是 `assistancePlanService.list()`，返回全库所有辅导员的计划）。
     * 现在库里只有 53 一个人，看着正常；别的辅导员一建计划就会串到这一页来。
     * 所以这里**必须**再按 `assistance_plans.counselor_id` 客户端过滤一次 ——
     * 直接把响应当"我的计划"是在赌库里只有一个人。
     */
    const rows = Array.isArray(rawPlans) ? rawPlans : []
    const mine = rows.filter(p => String(p.counselorId) === String(userId))
    // 按更新时间倒序：最近动过的那条最可能是今天要做完的
    plans.value = mine.slice().sort((a, b) => sortKey(b) - sortKey(a))
  } finally {
    loading.value = false
  }
}

function sortKey(p) {
  const t = Date.parse(p.updatedAt || p.createdAt || '')
  return Number.isFinite(t) ? t : 0
}

async function submit() {
  if (!canSubmit.value || saving.value) return
  saving.value = true
  try {
    if (editing.value) {
      // 内容与进度是**两个接口**：PUT 改内容、PATCH 改进度。
      // 后端 PUT 不碰 progress，PATCH 不碰 content，所以两个都得发。
      const id = editing.value.id
      await counselorAPI.updateAssistancePlan(id, { content: form.value.content.trim() })
      if (numOr(editing.value.progress) !== Number(form.value.progress)) {
        await counselorAPI.updatePlanProgress(id, Number(form.value.progress))
      }
      uni.showToast({ title: '已保存', icon: 'success' })
    } else {
      const s = students.value[studentIndex.value]
      await counselorAPI.createAssistancePlan({
        // counselorId 是 users.id（与库里既有行一致，也与所有按辅导员过滤的查询一致）
        counselorId: userId,
        // studentId 是 **profile id**
        studentId: s.id,
        content: form.value.content.trim(),
        status: 0,
        progress: 0
      })
      uni.showToast({ title: '已创建，状态为待开始', icon: 'success' })
    }
    sheetOpen.value = false
    await load()
  } catch (e) {
    console.error('[assistance] 保存失败:', e)
    uni.showToast({ title: e && e.message ? e.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-lg;
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

/* ---------- 顶部条 ---------- */
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 $page-padding;
}

.bar__text {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.bar__btn {  @include tap-area;
  flex-shrink: 0;
  margin-left: $spacing-sm;
  padding: 10rpx 28rpx;
  border-radius: $radius-pill;
  border: 1rpx solid $color-border;
  background: $color-card;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.bar__btn--primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

/* ---------- 计划卡 ---------- */
.plan-list {
  margin-top: $spacing-md;
}

.plan-card {
  margin: 0 $page-padding $spacing-md;
  padding: $spacing-lg;
  @include card;
}

.plan-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plan-card__who {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.plan-card__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.plan-card__class {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.plan-card__content {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
}

.plan-card__bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.plan-card__pct {
  flex-shrink: 0;
  width: 80rpx;
  margin-left: $spacing-sm;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-secondary;
  font-variant-numeric: tabular-nums;
}

.plan-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.plan-card__time {
  font-size: $font-xs;
  color: $color-text-hint;
}

.plan-card__op {
  font-size: $font-xs;
  color: var(--color-primary);
}

/* ---------- 表单 ---------- */
.form {
  padding: 0 $page-padding $spacing-2xl;
}

.form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: $spacing-sm;
}

.form__who {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.form__label {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-sm;
  color: $color-text;
}

.form__label--inline {
  margin-top: 0;
}

.form__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-lg;
}

.form__value {
  font-size: $font-sm;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.form__picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $tap-min;
  margin-top: $spacing-sm;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.form__ph {
  font-size: $font-sm;
  color: $color-text-hint;
}

.form__picked {
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
  line-height: 1.6;
}

.form__placeholder {
  color: $color-text-hint;
}

.form__count {
  display: block;
  margin-top: 6rpx;
  text-align: right;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__slider {
  margin: $spacing-md 0 0;
}

.form__tip {
  display: block;
  margin-top: $spacing-md;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.6;
}

.presets {
  display: flex;
  margin-top: $spacing-xs;
}

.preset {
  margin-right: $spacing-sm;
  padding: 8rpx 26rpx;
  border-radius: $radius-pill;
  border: 1rpx solid $color-border;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.preset--on {
  border-color: var(--color-primary);
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary, #{$role-counselor});
}
</style>
