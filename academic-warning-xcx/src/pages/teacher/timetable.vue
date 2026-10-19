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
             认不出的一律归「其他」，不丢、不猜。 -->
        <view class="card card--flush">
          <section-title title="周课表" :extra="`共 ${rows.length} 个安排`" />
          <view v-if="!rows.length" class="empty-tip">暂无排课记录。</view>
          <view v-else class="week">
            <view v-for="col in weekColumns" :key="col.key" class="week__col">
              <text class="week__head">{{ col.label }}</text>
              <view v-for="it in col.items" :key="it.planId" class="week__item">
                <text class="week__item-name">{{ it.courseName || '未命名课程' }}</text>
                <text class="week__item-line">{{ it.classTime || '时间待定' }}</text>
                <text class="week__item-line">{{ it.location || '教室待定' }}</text>
                <text class="week__item-line week__item-line--muted">
                  {{ numOr(it.enrolledCount) }}/{{ it.maxStudents || EMPTY }} 人
                </text>
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
            hint="排课由管理员维护，排课完成后这里会出现你的上课安排"
            icon="book"
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
              <!-- 选课人数是现算的（enrollments status=1），容量是计划值 —— 两个数含义
                   不同，分开两行写，别拼成 50/60 那种读不出哪个是哪个的写法。 -->
              <view class="row__kv">
                <text class="row__k">选课人数</text>
                <text class="row__v row__v--primary">{{ numOr(it.enrolledCount) }}</text>
              </view>
              <view class="row__kv">
                <text class="row__k">容量</text>
                <text class="row__v">{{ it.maxStudents === null || it.maxStudents === undefined ? EMPTY : it.maxStudents }}</text>
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
          仅显示排课教师为你本人的安排；选课人数按当前在读选课记录实时统计，容量为排课计划值。
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'

const { userName } = useUser()
const themeStyle = roleThemeStyle('teacher')

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
  return s ? semesterLabel(s.semester) : '本学期授课安排'
})

const heroStats = computed(() => [
  { label: '排课条数', value: rows.value.length },
  { label: '占用天数', value: weekDayCount.value },
  { label: '选课人次', value: totalEnrolled.value },
  { label: '学分合计', value: totalCredits.value }
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

const totalEnrolled = computed(() =>
  rows.value.reduce((sum, r) => sum + (Number(r.enrolledCount) || 0), 0))

const totalCredits = computed(() => {
  // 同一门课可能有多条排课（不同班/学期），学分按 courseId 去重相加，
  // 否则「学分合计」会随排课条数虚增。
  const seen = new Map()
  rows.value.forEach(r => { if (r.courseId != null) seen.set(r.courseId, Number(r.credits) || 0) })
  return Array.from(seen.values()).reduce((a, b) => a + b, 0)
})

function numOr(v) {
  if (v === null || v === undefined || v === '') return 0
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function semesterLabel(s) {
  if (!s) return EMPTY
  const m = /^(\d{4}-\d{4})-(\d)$/.exec(String(s))
  return m ? `${m[1]} 学年 第${m[2]}学期` : String(s)
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    // **不收参数** —— 身份只取网关注入的 X-User-Id，后端查 `course_plans.teacher_id = 我`。
    const data = await teacherAPI.getTimetable()
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
  background: #ecfdf5;
  border-left: 6rpx solid #10b981;
  border-radius: 8rpx;
  margin: 8rpx;
  padding: 8rpx 10rpx;
}
.week__item-name {
  display: block;
  font-size: 22rpx;
  font-weight: 600;
  color: #065f46;
  margin-bottom: 4rpx;
}
.week__item-line {
  display: block;
  font-size: 20rpx;
  color: #52525b;
  line-height: 1.5;
}
.week__item-line--muted { color: #9ca3af; }
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
  color: #10b981;
  font-weight: 600;
}
.row__meta {
  display: flex;
  gap: 12rpx;
  margin: 10rpx 0 12rpx;
}
.row__tag {
  font-size: 20rpx;
  color: #10b981;
  background: #ecfdf5;
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
.row__v--primary { color: #10b981; font-weight: 600; }

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
