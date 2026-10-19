<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="我的课表" />

    <role-hero
      title="我的课表"
      :subtitle="subtitle"
      :avatar-text="avatarText"
      :stats="heroStats"
    />

    <view class="page-body">
      <page-loading v-if="loading" :count="3" mode="card" />

      <template v-else>
        <!-- 周视图：class_time 是自由文本（'周一 1-2节'），按「周几」分列；
             解析不出来的归「其他」，不丢、不猜。 -->
        <view class="card card--flush">
          <section-title title="周课表" :extra="`共 ${rows.length} 个安排`" />
          <view v-if="!rows.length" class="empty-tip">
            本学期暂无排课记录。
          </view>
          <view v-else class="week">
            <view v-for="col in weekColumns" :key="col.key" class="week__col">
              <text class="week__head">{{ col.label }}</text>
              <view
                v-for="it in col.items"
                :key="it.planId"
                class="week__item"
              >
                <text class="week__item-name">{{ it.courseName || '未命名课程' }}</text>
                <text class="week__item-line">{{ it.classTime || '时间待定' }}</text>
                <text class="week__item-line">{{ it.location || '教室待定' }}</text>
              </view>
              <text v-if="!col.items.length" class="week__empty">—</text>
            </view>
          </view>
        </view>

        <!-- 明细 -->
        <view class="card card--flush">
          <section-title title="课程明细" :extra="`共 ${rows.length} 门`" />
          <empty-state
            v-if="!rows.length"
            text="暂无排课数据"
            hint="课表来自教务排课，未排课的课程不会出现在这里"
            icon="chart"
            :top="50"
          />
          <view v-else class="list">
            <view v-for="it in rows" :key="it.planId" class="row">
              <view class="row__top">
                <text class="row__name">{{ it.courseName || '未命名课程' }}</text>
                <text class="row__credits">{{ numOr(it.credits) }} 学分</text>
              </view>
              <view class="row__meta">
                <text class="row__tag">{{ it.courseType || '未分类' }}</text>
                <text class="row__code">{{ it.courseCode || EMPTY }}</text>
              </view>
              <view class="row__kv">
                <text class="row__k">时间</text>
                <text class="row__v">{{ it.classTime || '待定' }}</text>
              </view>
              <view class="row__kv">
                <text class="row__k">教室</text>
                <text class="row__v">{{ it.location || '待定' }}</text>
              </view>
              <view class="row__kv">
                <text class="row__k">教师</text>
                <!-- 排课的 teacher_id 取不到档案时后端回 null（LEFT JOIN）⇒ 写「—」，
                     不拿 id 拼一个"教师29"冒充人名。 -->
                <text class="row__v">{{ it.teacherName || EMPTY }}</text>
              </view>
              <view class="row__kv">
                <text class="row__k">学期</text>
                <text class="row__v">{{ semesterLabel(it.semester) }}</text>
              </view>
              <view class="row__kv">
                <text class="row__k">起止</text>
                <text class="row__v">{{ it.startDate || EMPTY }} ~ {{ it.endDate || EMPTY }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="foot-note">
          课表来自教务排课计划；同一门课可能因分班有多条安排，均按实际排课列出。
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'

const { userId, userName } = useUser()
const themeStyle = roleThemeStyle('student')

const loading = ref(true)
const rows = ref([])
const EMPTY = '—'

const WEEK_DAYS = [
  { key: '周一', label: '一' },
  { key: '周二', label: '二' },
  { key: '周三', label: '三' },
  { key: '周四', label: '四' },
  { key: '周五', label: '五' },
  { key: '周六', label: '六' },
  { key: '周日', label: '日' },
  { key: '其他', label: '其他' }
]

const avatarText = computed(() => (userName ? String(userName).slice(0, 1) : ''))

const subtitle = computed(() => {
  const s = rows.value.find(r => r.semester)
  return s ? semesterLabel(s.semester) : '本学期排课安排'
})

const heroStats = computed(() => [
  { label: '排课条数', value: rows.value.length },
  { label: '占用天数', value: weekDayCount.value },
  { label: '学分合计', value: totalCredits.value },
  { label: '任课教师', value: teacherCount.value }
])

function weekOf(classTime) {
  const t = String(classTime || '')
  const hit = WEEK_DAYS.find(d => d.key !== '其他' && t.includes(d.key))
  return hit ? hit.key : '其他'
}

const weekColumns = computed(() => WEEK_DAYS.map(d => ({
  key: d.key,
  label: d.label,
  items: rows.value.filter(r => weekOf(r.classTime) === d.key)
})))

const weekDayCount = computed(() => {
  const set = new Set()
  rows.value.forEach(r => set.add(weekOf(r.classTime)))
  return set.size
})

const totalCredits = computed(() =>
  rows.value.reduce((sum, r) => sum + (Number(r.credits) || 0), 0))

const teacherCount = computed(() => {
  const set = new Set()
  rows.value.forEach(r => { if (r.teacherName) set.add(r.teacherName) })
  return set.size
})

function numOr(v) {
  if (v === null || v === undefined || v === '') return EMPTY
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** '2025-2026-2' → '2025-2026 学年 第2学期'；认不出就原样回。 */
function semesterLabel(s) {
  if (!s) return EMPTY
  const m = /^(\d{4}-\d{4})-(\d)$/.exec(String(s))
  return m ? `${m[1]} 学年 第${m[2]}学期` : String(s)
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    // 路径参数是 **users.id**（与 /students/** 同套）。后端 `denyUnlessSelf` 会
    // 把换 id 的请求判 403，所以这里只可能拿到本人的课表。
    const data = await studentAPI.getTimetable(userId)
    // 后端回裸数组。形状不对就如实回空并留日志 —— 不写 `.records || .list || []`
    // 那种多分支兜底，静默回空会把"接口坏了"显示成"你没课"。
    if (!Array.isArray(data)) {
      console.error('[timetable] 课表数据格式异常:', data)
      rows.value = []
      return
    }
    rows.value = data
  } catch (e) {
    console.error('[timetable] 加载课表失败:', e)
    uni.showToast({ title: '加载课表失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page-body { padding-bottom: 40rpx; }

.week {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 0 24rpx 24rpx;
}
.week__col {
  flex: 0 0 calc(25% - 9rpx);
  border: 1rpx solid #eef0f4;
  border-radius: 12rpx;
  overflow: hidden;
  min-height: 120rpx;
  background: #fff;
}
.week__head {
  display: block;
  text-align: center;
  font-size: 22rpx;
  font-weight: 600;
  color: #52525b;
  background: #f8f9fb;
  padding: 8rpx 0;
}
.week__item {
  background: #eef2ff;
  border-left: 6rpx solid #6366f1;
  border-radius: 8rpx;
  margin: 8rpx;
  padding: 8rpx 10rpx;
}
.week__item-name {
  display: block;
  font-size: 22rpx;
  font-weight: 600;
  color: #312e81;
  margin-bottom: 4rpx;
}
.week__item-line {
  display: block;
  font-size: 20rpx;
  color: #52525b;
  line-height: 1.5;
}
.week__empty {
  display: block;
  text-align: center;
  color: #d4d4d8;
  font-size: 22rpx;
  padding: 16rpx 0;
}

.list { padding: 0 24rpx 24rpx; }
.row {
  background: #fff;
  border: 1rpx solid #eef0f4;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}
.row__top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.row__name {
  font-size: 28rpx;
  font-weight: 600;
  color: #18181b;
  flex: 1;
}
.row__credits {
  font-size: 22rpx;
  color: #6366f1;
  font-weight: 600;
}
.row__meta {
  display: flex;
  gap: 12rpx;
  margin: 10rpx 0 12rpx;
}
.row__tag {
  font-size: 20rpx;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 6rpx;
  padding: 2rpx 10rpx;
}
.row__code {
  font-size: 20rpx;
  color: #a1a1aa;
}
.row__kv {
  display: flex;
  justify-content: space-between;
  font-size: 24rpx;
  line-height: 1.9;
}
.row__k { color: #71717a; }
.row__v { color: #3f3f46; }

.empty-tip {
  display: block;
  text-align: center;
  color: #a1a1aa;
  font-size: 24rpx;
  padding: 30rpx 24rpx;
}

.foot-note {
  padding: 0 24rpx 24rpx;
  font-size: 22rpx;
  color: #9ca3af;
  line-height: 1.6;
}
</style>
