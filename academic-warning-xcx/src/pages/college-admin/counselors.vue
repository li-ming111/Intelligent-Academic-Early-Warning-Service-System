<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="辅导员管理" />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" />

      <template v-else>
        <view class="summary">
          <text class="summary__text">
            共 <text class="summary__num">{{ counselors.length }}</text> 位辅导员
          </text>
          <text class="summary__hint">按本院班级里的辅导员档案归属</text>
        </view>

        <empty-state
          v-if="!counselors.length"
          text="本院暂无辅导员"
          hint="辅导员档案里的学院为空时，不会出现在这里"
          icon="user"
        />

        <view v-else class="card card--flush">
          <view
            v-for="(c, i) in counselors"
            :key="c.id"
            class="cns"
            :class="{ 'cns--divided': i > 0 }"
            @tap="openDetail(c)"
          >
            <view class="cns__head">
              <text class="cns__name">{{ c.name || `辅导员 ${c.id}` }}</text>
              <text class="tag tag--info">{{ c.classCount || 0 }} 个班</text>
            </view>
            <text class="cns__classes">{{ c.classes || '暂未关联班级' }}</text>
            <text class="cns__phone">联系电话 {{ c.phone || '未填' }}</text>
          </view>
        </view>

        <text class="note">
          「N 个班」与下面那串班名是**这位辅导员名下的全部班级**，按 `classes.counselor_id` 数。
          司明管着软件学院的 6 个班，其中只有「计科2306班」已经招生 ——
          另外 5 个是已建班但还没有学生档案的空班，所以这里 6 个班、而学生管理页只有 24 人。
        </text>
        <text class="note">
          首页概览里那个 `counselorCount` **不是这个数**：它量的是"本学院里已分配辅导员的班级数"，
          是班级数、不是辅导员的任职数。一位辅导员管着本院的 6 个班，概览给 6，而这里只显示他 1 个人。
          两处口径不同，这里按各自的实际含义起名。
        </text>
      </template>
    </view>

    <!-- ============ 辅导员详情 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" height="60vh">
      <view v-if="current" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">姓名</text>
            <text class="facts__v">{{ current.name || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">联系电话</text>
            <text class="facts__v">{{ current.phone || '未填' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">管辖班级数</text>
            <text class="facts__v">{{ current.classCount || 0 }}</text>
          </view>
        </view>

        <section-title plain title="管辖班级" :extra="`${classNames.length} 个`" />
        <view v-if="!classNames.length" class="empty-tip">暂未关联班级。</view>
        <view v-else class="chips">
          <text v-for="(n, i) in classNames" :key="i" class="chip">{{ n }}</text>
        </view>

        <text class="note note--in">
          班名是后端把班级名用顿号拼成的一个字符串，这里再拆开显示成标签 ——
          接口没有给班级 id，所以点进去只能看名字，不能跳到班级详情。
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

const { userId } = useUser()
const themeStyle = roleThemeStyle('collegeAdmin')

const loading = ref(true)
const counselors = ref([])

const sheetOpen = ref(false)
const current = ref(null)

/** 班名字符串 → 标签数组。`classes` 是「、」连起来的一整串。 */
const classNames = computed(() => {
  const s = current.value && current.value.classes
  if (!s) return []
  return String(s).split('、').map(x => x.trim()).filter(Boolean)
})

const sheetTitle = computed(() => (current.value && current.value.name) || '辅导员详情')

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await collegeAdminAPI.getCounselors(userId)
    const rows = Array.isArray(list) ? list : []
    counselors.value = rows.slice().sort((a, b) => (Number(b.classCount) || 0) - (Number(a.classCount) || 0))
  } catch (e) {
    console.error('[college-admin counselors] 辅导员失败:', e)
    counselors.value = []
    toast((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(c) {
  current.value = c
  sheetOpen.value = true
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-md;
  padding-bottom: $spacing-3xl;
}

.summary {
  display: flex;
  align-items: baseline;
  padding: 0 $page-padding $spacing-sm;
}

.summary__text {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.summary__num {
  font-weight: 700;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.summary__hint {
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 辅导员行 ---------- */
.cns {
  padding: $spacing-lg;
}

.cns--divided {
  border-top: 1rpx solid $color-divider;
}

.cns:active {
  background: $color-bg;
}

.cns__head {
  display: flex;
  align-items: center;
}

.cns__name {
  flex: 1;
  min-width: 0;
  margin-right: $spacing-sm;
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
  @include ellipsis;
}

.cns__classes {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: var(--color-primary);
  line-height: 1.6;
  @include ellipsis-multi(3);
}

.cns__phone {
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
  width: 220rpx;
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

.chips {
  display: flex;
  flex-wrap: wrap;
}

.chip {  @include tap-area;
  padding: $spacing-xs $spacing-md;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-pill;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.empty-tip {
  display: block;
  padding: $spacing-md 0;
  font-size: $font-xs;
  color: $color-text-hint;
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
