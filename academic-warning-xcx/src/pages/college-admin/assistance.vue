<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="帮扶情况" />

    <filter-bar v-model="statusFilter" :options="statusOptions" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ rows.length }}</text> 条线索 ·
            待处理 <text class="summary__num summary__num--danger">{{ pendingCount }}</text> 条
          </text>
          <text class="summary__hint">状态筛选在本地做</text>
        </view>

        <empty-state
          v-if="!filtered.length"
          :text="rows.length ? '这个状态下没有线索' : '本院暂无帮扶线索'"
          :hint="rows.length ? '换一个状态看看' : '没有学生触发预警规则'"
          icon="check"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(r, i) in filtered"
            :key="i"
            class="row"
            :class="{ 'row--divided': i > 0 }"
            @tap="openDetail(r)"
          >
            <view class="row__head">
              <warning-tag :level="r.level" />
              <text class="row__name">{{ r.studentName || '未知学生' }}</text>
              <text class="tag" :class="warningStatusTagClass(r.status)">
                {{ r.statusText }}
              </text>
            </view>
            <text class="row__desc">{{ r.description || '无说明' }}</text>
            <view class="row__foot">
              <text class="row__class">{{ r.className || '未分班' }}</text>
              <text class="row__time">{{ formatDate(r.createdAt, true) }}</text>
            </view>
          </view>
        </view>

        <text class="note">
          **这一页的数据源就是预警表**，不是帮扶计划表。后端 `CollegeAdminController`
          里 `/assistance/{userId}` 的实现是把预警查询结果换个字段名原样返回
          （`warning_level` 改名成 `level`，其余照搬），接口本身没有 JOIN 帮扶计划。
          所以页面上每一条都是"预警记录"，请不要把它当成"已下达的帮扶措施"来读。
        </text>
        <text class="note">
          真要看帮扶计划，库里确实有 `assistance_plan` 表、辅导员端也有完整的增删改查
          （`/counselors/{cid}/assistance-plans`），但**没有任何一个接口按学院维度查它**
          —— 学院管理员拿不到本院的帮扶计划清单。这是后端的能力缺口，页面补不出来。
        </text>
        <text class="note">
          这些行**没有 id**（接口没往外给），所以这里只能看、不能处理也不能点进学生详情。
          要处理请去「预警管理」页 —— 那边才有 `process-warning` 与 `delete-warning`。
        </text>
      </template>
    </view>

    <!-- ============ 详情 ============ -->
    <detail-sheet v-model="sheetOpen" title="线索详情" height="58vh">
      <view v-if="current" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">学生</text>
            <text class="facts__v">{{ current.studentName || '未知' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">班级</text>
            <text class="facts__v">{{ current.className || '未分班' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">预警等级</text>
            <text class="facts__v">{{ levelText(current.level) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">处理状态</text>
            <text class="facts__v">{{ current.statusText }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">产生时间</text>
            <text class="facts__v">{{ formatDate(current.createdAt, true) }}</text>
          </view>
        </view>

        <section-title plain title="预警说明" />
        <text class="paragraph">{{ current.description || '（无）' }}</text>

        <text class="note note--in">
          这一行的字段只有上面这些 —— 没有帮扶措施、没有责任人、没有完成进度。
          那些字段在帮扶计划表里，而接口没有查它。
        </text>
      </view>
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
// 2026-10-02（W1.6）：这一页的数据源**就是预警表**（后端把 `warning_level` 改名成 `level`
// 原样返回，见下面那条 note），所以 `status` 是 `academic_warning.status`，同样是三态。
import { normalizeWarnings, warningStatusTagClass } from '@/utils/warning.js'

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: '0' },
  { label: '已处理', value: '1' },
  { label: '已解除', value: '2' }
]

const loading = ref(true)
const rows = ref([])
const statusFilter = ref('')

const sheetOpen = ref(false)
const current = ref(null)

const filtered = computed(() => {
  if (!statusFilter.value) return rows.value
  // 归一化后 status 已是 0/1/2，直接比字符串即可（原来先压成两态再比，2 会被并进 0）
  return rows.value.filter(r => String(r.status) === statusFilter.value)
})

// 2026-10-02（W1.6）：`status !== 1` 会把"已解除(2)"算进待处理 ⇒ 只认 0。
const pendingCount = computed(() => rows.value.filter(r => r.status === 0).length)

/** 等级文案统一走 utils/score.js —— 这个接口把键名改成了 `level`，值仍是同一套。 */
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
    const list = await collegeAdminAPI.getAssistance(userId)
    // 待处理置顶，其余按时间倒序（`isPending` 由归一化给出，三态下"已解除"也排后）
    rows.value = normalizeWarnings(Array.isArray(list) ? list : []).sort((a, b) => {
      const pa = a.isPending ? 0 : 1
      const pb = b.isPending ? 0 : 1
      if (pa !== pb) return pa - pb
      return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
    })
  } catch (e) {
    console.error('[college-admin assistance] 帮扶线索失败:', e)
    rows.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(r) {
  current.value = r
  sheetOpen.value = true
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

.row {
  padding: $spacing-lg;
}

.row--divided {
  border-top: 1rpx solid $color-divider;
}

.row:active {
  background: $color-bg;
}

.row__head {
  display: flex;
  align-items: center;
}

.row__name {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.row__desc {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-sm;
  color: $color-text-secondary;
  line-height: 1.6;
  @include ellipsis-multi(3);
}

.row__foot {
  display: flex;
  align-items: baseline;
  margin-top: $spacing-sm;
}

.row__class {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: var(--color-primary);
  @include ellipsis;
}

.row__time {
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
</style>
