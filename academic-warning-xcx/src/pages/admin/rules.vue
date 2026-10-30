<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="预警规则" />

    <view class="page-body">
      <view class="toolbar">
        <text class="toolbar__title">共 {{ rules.length }} 条规则</text>
        <view class="toolbar__add" @tap="openForm(null)">
          <app-icon name="plus" :size="32" color="#fff" />
          <text class="toolbar__add-text">新建规则</text>
        </view>
      </view>

      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <empty-state
          v-if="!rules.length"
          text="暂无预警规则"
          hint="规则是预警产生的源头，没有规则就不会有预警"
          icon="filter"
        />

        <template v-else>
          <view class="card card--flush">
            <view
              v-for="(r, i) in rules"
              :key="r.id"
              class="rule"
              :class="{ 'rule--divided': i > 0 }"
              @tap="openDetail(r)"
            >
              <view class="rule__head">
                <warning-tag :level="r.warningLevel" />
                <text class="rule__name">{{ r.ruleName }}</text>
                <text class="tag" :class="r.status === 1 ? 'tag--success' : 'tag--pending'">
                  {{ r.status === 1 ? '启用' : '停用' }}
                </text>
              </view>
              <text class="rule__expr">{{ r.expression || '未填写判定条件' }}</text>
              <text class="rule__desc">{{ r.description || '无说明' }}</text>
              <text class="rule__code">{{ r.ruleCode || '无规则编码' }} · 统计周期 {{ cycleText(r.statisticCycle) }}</text>
            </view>
          </view>

          <text class="note">未填写的统计周期与触发方式显示为「未填」，系统不会自动补默认值。</text>
        </template>
      </template>
    </view>

    <!-- ============ 详情 / 表单 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" :height="mode === 'detail' ? '80vh' : '80vh'">
      <!-- ---------- 详情 ---------- -->
      <view v-if="mode === 'detail' && current" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">预警等级</text>
            <text class="facts__v">{{ levelText(current.warningLevel) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">规则编码</text>
            <text class="facts__v">{{ current.ruleCode || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">统计周期</text>
            <text class="facts__v">{{ cycleText(current.statisticCycle) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">触发方式</text>
            <text class="facts__v">{{ triggerText(current.triggerType) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">状态</text>
            <text class="facts__v">{{ current.status === 1 ? '启用' : '停用' }}</text>
          </view>
        </view>

        <section-title plain title="判定条件" extra="系统按此规则判定预警" />
        <view class="expr">
          <text class="expr__text">{{ current.expression || '（未填写）' }}</text>
        </view>

        <!-- 与表单共用同一份字段清单：改了一处不会两边漂移 -->
        <section-title plain title="规则的完整说明" :extra="`${longFields.length} 项`" />
        <view class="blocks">
          <view v-for="f in longFields" :key="f.key" class="block">
            <text class="block__label">{{ f.label }}</text>
            <text class="block__text" :class="{ 'block__text--empty': !current[f.key] }">
              {{ current[f.key] || '（未填写）' }}
            </text>
          </view>
        </view>
      </view>

      <!-- ---------- 表单 ---------- -->
      <view v-else-if="mode === 'form'" class="sheet">
        <view class="field">
          <text class="field__label">规则名称</text>
          <input v-model="form.ruleName" class="field__input" placeholder="如 中级预警" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">规则编码</text>
          <input v-model="form.ruleCode" class="field__input" placeholder="如 RULE002" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">预警等级</text>
          <view class="opts">
            <view
              v-for="o in levelOptions"
              :key="o.value"
              class="opt"
              :class="{ 'opt--on': form.warningLevel === o.value }"
              @tap="form.warningLevel = o.value"
            >{{ o.label }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">判定条件（表达式）</text>
          <input v-model="form.expression" class="field__input" placeholder="如 fail_count >= 3 AND fail_count <= 5" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">统计周期</text>
          <view class="opts">
            <view class="opt" :class="{ 'opt--on': !form.statisticCycle }" @tap="form.statisticCycle = ''">未填</view>
            <view
              v-for="o in cycleOptions"
              :key="o.value"
              class="opt"
              :class="{ 'opt--on': form.statisticCycle === o.value }"
              @tap="form.statisticCycle = o.value"
            >{{ o.label }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">触发方式</text>
          <view class="opts">
            <view class="opt" :class="{ 'opt--on': !form.triggerType }" @tap="form.triggerType = ''">未填</view>
            <view
              v-for="o in triggerOptions"
              :key="o.value"
              class="opt"
              :class="{ 'opt--on': form.triggerType === o.value }"
              @tap="form.triggerType = o.value"
            >{{ o.label }}</view>
          </view>
        </view>

        <view v-for="f in longFields" :key="f.key" class="field">
          <text class="field__label">{{ f.label }}</text>
          <!-- maxlength 必须给 -1：uni 的 textarea 默认只让输 140 字，
               而干预方案这类文本本身就一百多字，会被无声截断 -->
          <textarea
            v-model="form[f.key]"
            class="field__area"
            :maxlength="-1"
            auto-height
            :placeholder="'选填'"
            placeholder-class="field__ph"
          />
        </view>

        <text class="note">保存会提交整份表单，未修改的字段保持原值。</text>
      </view>

      <template #footer>
        <template v-if="mode === 'detail'">
          <view class="acts">
            <view class="act act--danger" @tap="remove">删除规则</view>
          </view>
          <view class="btn-primary footer-btn" @tap="openForm(current)">编辑规则</view>
        </template>

        <template v-else>
          <view class="form-acts">
            <view class="form-acts__cancel" @tap="sheetOpen = false">取消</view>
            <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="submit">
              {{ submitting ? '提交中…' : (editingId ? '保存' : '创建') }}
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

const themeStyle = roleThemeStyle('admin')

const loading = ref(true)
const rules = ref([])

const sheetOpen = ref(false)
/** detail | form */
const mode = ref('detail')
const current = ref(null)
const editingId = ref(null)
const submitting = ref(false)
const form = ref({})

const levelOptions = [
  { label: '轻度（1）', value: 1 },
  { label: '中度（2）', value: 2 },
  { label: '严重（3）', value: 3 }
]

/** 字典值来自库里实际出现过的取值（statistic_cycle 只有 full_program）。 */
const cycleOptions = [{ label: '全程（入学至今）', value: 'full_program' }]
const triggerOptions = [
  { label: '周期触发', value: 'periodic' },
  { label: '实时触发', value: 'real_time' }
]

/**
 * 规则的长文本字段清单 —— 详情页与表单共用这一份。
 * 字段名用**接口里的 JSON 名**（`ruleName`/`warningLevel`/`expression` 而不是库里的
 * `name`/`level`/`rule_condition`），实体上挂了 @TableField 做映射，提交时要用 JSON 名。
 */
const longFields = [
  { key: 'description', label: '规则说明' },
  { key: 'failDefinition', label: '挂科判定口径' },
  { key: 'releaseCondition', label: '预警解除条件' },
  { key: 'interventionPlan', label: '干预方案' },
  { key: 'notificationFlow', label: '通知流程' },
  { key: 'responsiblePerson', label: '责任人' },
  { key: 'trackingMechanism', label: '跟踪机制' },
  { key: 'archiveSpec', label: '归档规范' },
  { key: 'dataCaliber', label: '数据口径' },
  { key: 'departmentResponsibility', label: '部门职责' },
  { key: 'appealProcess', label: '申诉流程' },
  { key: 'exceptionHandling', label: '异常处理' }
]

const sheetTitle = computed(() => {
  if (mode.value === 'detail') return current.value ? current.value.ruleName : '规则详情'
  return editingId.value ? '编辑预警规则' : '新建预警规则'
})

function levelText(level) {
  const n = Number(level)
  if (n === 3) return '严重预警（3）'
  if (n === 2) return '中度预警（2）'
  if (n === 1) return '轻度预警（1）'
  return level === null || level === undefined ? '未填' : `未识别的等级 ${level}`
}

/** 字典值 → 中文。库里没出现过的值原样显示，不猜。 */
function cycleText(v) {
  if (!v) return '未填'
  const hit = cycleOptions.find(o => o.value === v)
  return hit ? hit.label : v
}

function triggerText(v) {
  if (!v) return '未填'
  const hit = triggerOptions.find(o => o.value === v)
  return hit ? hit.label : v
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
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

onMounted(load)

async function load() {
  loading.value = true
  try {
    const list = await adminAPI.getRules()
    // 后端是 `list()` 的默认顺序（按 id 升序），页面上按等级从高到低排，与预警列表口径一致
    rules.value = (Array.isArray(list) ? list : [])
      .slice()
      .sort((a, b) => Number(b.warningLevel || 0) - Number(a.warningLevel || 0))
  } catch (e) {
    console.error('[admin rules] 规则列表失败:', e)
    rules.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(r) {
  current.value = r
  mode.value = 'detail'
  sheetOpen.value = true
}

function openForm(r) {
  editingId.value = r ? r.id : null
  const next = {
    ruleName: r ? r.ruleName || '' : '',
    ruleCode: r ? r.ruleCode || '' : '',
    warningLevel: r && r.warningLevel ? Number(r.warningLevel) : 1,
    expression: r ? r.expression || '' : '',
    statisticCycle: r && r.statisticCycle ? r.statisticCycle : '',
    triggerType: r && r.triggerType ? r.triggerType : ''
  }
  longFields.forEach(f => { next[f.key] = r && r[f.key] ? r[f.key] : '' })
  form.value = next
  mode.value = 'form'
  sheetOpen.value = true
}

async function submit() {
  if (submitting.value) return
  const f = form.value
  if (!String(f.ruleName || '').trim()) {
    toast('请填写规则名称')
    return
  }
  if (!String(f.expression || '').trim()) {
    toast('请填写判定条件')
    return
  }

  submitting.value = true
  try {
    const body = {
      ruleName: String(f.ruleName).trim(),
      ruleCode: String(f.ruleCode || '').trim(),
      warningLevel: Number(f.warningLevel) || 1,
      expression: String(f.expression).trim(),
      // 这两个是字典值，空串代表"未填" —— 空串不是 null，会被写进库（这正是我们要的：
      // 规则 4 在库里就是这样），所以这里显式带上
      statisticCycle: f.statisticCycle || '',
      triggerType: f.triggerType || ''
    }
    // 长文本：带上（含空串）。不带 = 不修改（MyBatis-Plus 跳过 null），
    // 带空串 = 真的清空。这里选"带上"，因为表单里用户看得见这些值。
    longFields.forEach(x => { body[x.key] = String(f[x.key] || '') })

    if (editingId.value) await adminAPI.updateRule(editingId.value, body)
    else await adminAPI.createRule(body)

    toast(editingId.value ? '已保存' : '已创建')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '提交失败', 3200)
  } finally {
    submitting.value = false
  }
}

async function remove() {
  const r = current.value
  if (!r) return
  const ok = await confirm(
    `删除规则「${r.ruleName}」？已产生的预警记录不会被删除，但之后不会再按这条规则判预警。`,
    '删除规则'
  )
  if (!ok) return
  try {
    await adminAPI.deleteRule(r.id)
    toast('已删除')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '删除失败', 3200)
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.toolbar {
  display: flex;
  align-items: center;
  padding: 0 $page-padding $spacing-sm;
}

.toolbar__title {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.toolbar__add {
  @include tap-area;

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

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 规则行 ---------- */
.rule {
  padding: $spacing-lg;
}

.rule--divided {
  border-top: 1rpx solid $color-divider;
}

.rule:active {
  background: $color-bg;
}

.rule__head {
  display: flex;
  align-items: center;
}

.rule__name {
  margin-left: $spacing-sm;
  flex: 1;
  min-width: 0;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.rule__expr {
  display: block;
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-sm;
  background: $color-bg;
  font-size: $font-sm;
  color: var(--color-primary);
  font-family: Consolas, Menlo, monospace;
}

.rule__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-secondary;
  line-height: 1.5;
  @include ellipsis-multi(2);
}

.rule__code {
  display: block;
  margin-top: $spacing-xs;
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
}

.expr {
  padding: $spacing-md $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.expr__text {
  font-size: $font-md;
  color: var(--color-primary);
  font-family: Consolas, Menlo, monospace;
  word-break: break-all;
}

.blocks {
  padding: 0;
}

.block {
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $color-divider;
}

.block__label {
  display: block;
  font-size: $font-xs;
  color: var(--color-primary);
  font-weight: 600;
}

.block__text {
  display: block;
  margin-top: 6rpx;
  font-size: $font-sm;
  color: $color-text;
  line-height: 1.7;
}

.block__text--empty {
  color: $color-text-hint;
}

/* ---------- 表单 ---------- */
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

.field__area {
  width: 100%;
  box-sizing: border-box;
  min-height: 120rpx;
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

.opts {
  display: flex;
  flex-wrap: wrap;
}

.opt {
  height: $tap-min;
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
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-md;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
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
