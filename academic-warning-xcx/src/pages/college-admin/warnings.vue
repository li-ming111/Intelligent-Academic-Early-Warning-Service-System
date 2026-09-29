<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="预警管理" :show-back="false" />

    <filter-bar v-model="levelFilter" :options="levelOptions" />

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ warnings.length }}</text> 条 ·
            待处理 <text class="summary__num summary__num--danger">{{ pendingCount }}</text> 条
          </text>
          <text class="summary__hint">等级筛选在本地做（接口不支持按等级过滤）</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="warnings.length ? '这个等级没有预警' : '本院暂无预警记录'"
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
              <text class="tag" :class="w.status === 1 ? 'tag--success' : 'tag--warning'">
                {{ w.status === 1 ? '已处理' : '待处理' }}
              </text>
            </view>
            <text class="warn__desc">{{ w.description || '无说明' }}</text>
            <view class="warn__foot">
              <text class="warn__class">{{ w.className || '未分班' }}</text>
              <text class="warn__time">{{ formatDate(w.createdAt, true) }}</text>
            </view>
          </view>
        </view>

        <text class="note">
          这一页的「处理」与管理员端**不是同一支接口**：这里只把 `status` 置 1 并写一下
          `updated_at`，**不记录处理人、不记录处理说明**。所以详情里没有"处理备注"栏
          —— 不是没显示，是库里没这个位置可放。
        </text>
        <text class="note">
          删除是**真的删行**（`deleteById`），不是软删除。删掉后这条记录在库里就不存在了，
          「这个学生被预警过」的痕迹一并消失，不可撤销。
        </text>
      </template>
    </view>

    <app-tab-bar role="collegeAdmin" />

    <!-- ============ 详情 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" height="70vh">
      <view v-if="current" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">学生</text>
            <text class="facts__v">{{ current.studentName || `student_profile.id ${current.studentId}` }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">班级</text>
            <text class="facts__v">{{ current.className || '未分班' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">预警等级</text>
            <text class="facts__v">{{ levelText(current.warningLevel) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">处理状态</text>
            <text class="facts__v">{{ current.status === 1 ? '已处理' : '待处理' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">产生时间</text>
            <text class="facts__v">{{ formatDate(current.createdAt, true) }}</text>
          </view>
        </view>

        <section-title plain title="预警说明" />
        <text class="paragraph">{{ current.description || '（无）' }}</text>

        <text class="note note--in">
          这条记录只有上面这些字段。库里另有 `handle_result` / `handled_by` / `handled_at`
          三列，是管理员端的处理接口在写；学院管理员这条处理路径不碰它们，所以这里不展示空行。
        </text>
      </view>

      <template #footer>
        <view class="acts">
          <view class="act act--danger" @tap="askDelete">删除记录</view>
        </view>
        <view
          v-if="current && current.status !== 1"
          class="btn-primary footer-btn"
          :class="{ 'btn-primary--disabled': submitting }"
          @tap="doProcess"
        >{{ submitting ? '提交中…' : '标记为已处理' }}</view>
        <view v-else class="btn-primary btn-primary--disabled footer-btn">这条已处理</view>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate } from '@/utils/format.js'
import { warningLevelText } from '@/utils/score.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const levelOptions = [
  { label: '全部', value: '' },
  { label: '轻度', value: '1' },
  { label: '中度', value: '2' },
  { label: '严重', value: '3' }
]

const loading = ref(true)
const warnings = ref([])
const levelFilter = ref('')

const sheetOpen = ref(false)
const current = ref(null)
const submitting = ref(false)

const sheetTitle = computed(() => (current.value && current.value.studentName ? `${current.value.studentName}的预警` : '预警详情'))

const filtered = computed(() => {
  if (!levelFilter.value) return warnings.value
  return warnings.value.filter(w => String(w.warningLevel) === levelFilter.value)
})

const pendingCount = computed(() => warnings.value.filter(w => w.status !== 1).length)

/** 等级文案统一走 utils/score.js 的 warningLevelText —— 认不出的值它给「未知」，
 *  不再各页各写一份映射（历史上就是那样漂成"中度/中等/轻微"几种写法的）。 */
function levelText(v) {
  return warningLevelText(v)
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await collegeAdminAPI.getWarnings(userId)
    // 待处理置顶，其余按产生时间倒序
    warnings.value = (Array.isArray(list) ? list : []).slice().sort((a, b) => {
      const pa = a.status === 1 ? 1 : 0
      const pb = b.status === 1 ? 1 : 0
      if (pa !== pb) return pa - pb
      return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
    })
  } catch (e) {
    console.error('[college-admin warnings] 预警失败:', e)
    warnings.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(w) {
  current.value = w
  sheetOpen.value = true
}

async function doProcess() {
  if (submitting.value || !current.value) return
  submitting.value = true
  try {
    await collegeAdminAPI.processWarning(userId, current.value.id, 1)
    toast('已标记为已处理')
    const id = current.value.id
    sheetOpen.value = false
    await load()
    // 回读一遍，让人看到落库后的状态，而不是只信那句 toast
    const fresh = warnings.value.find(x => x.id === id)
    if (fresh) openDetail(fresh)
  } catch (e) {
    console.error('[college-admin warnings] 处理失败:', e)
    toast((e && e.message) || '处理失败', 3200)
  } finally {
    submitting.value = false
  }
}

function askDelete() {
  const w = current.value
  if (!w) return
  uni.showModal({
    title: '删除预警记录',
    content: `确认删除「${w.studentName}」的这条${levelText(w.warningLevel)}记录？\n\n这是真删行，库里不会再保留这条记录，删除后无法恢复。`,
    confirmText: '删除',
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await collegeAdminAPI.deleteWarning(w.id)
        toast('已删除')
        sheetOpen.value = false
        await load()
      } catch (e) {
        console.error('[college-admin warnings] 删除失败:', e)
        toast((e && e.message) || '删除失败', 3200)
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-sm;
  padding-bottom: $spacing-3xl;
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
  @include ellipsis-multi(3);
}

.warn__foot {
  display: flex;
  align-items: baseline;
  margin-top: $spacing-sm;
}

.warn__class {
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

.note--in {
  margin-left: 0;
  margin-right: 0;
}

/* ---------- 底部 ---------- */
.acts {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: $spacing-md;
}

.act {
  height: 68rpx;
  padding: 0 $spacing-lg;
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
</style>
