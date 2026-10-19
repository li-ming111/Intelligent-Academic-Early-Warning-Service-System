<template>
  <div class="tt-page">
    <div class="tt-hero">
      <div>
        <h1>我的课表</h1>
        <p>本学期授课安排：上课时间、教室与选课人数</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadTimetable">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <div class="tt-stats">
      <div class="tts-card">
        <div class="tts-num">{{ list.length }}</div>
        <div class="tts-lbl">排课条数</div>
      </div>
      <div class="tts-card">
        <div class="tts-num">{{ weekDayCount }}</div>
        <div class="tts-lbl">占用天数/周</div>
      </div>
      <div class="tts-card">
        <div class="tts-num">{{ totalEnrolled }}</div>
        <div class="tts-lbl">选课人次合计</div>
      </div>
      <div class="tts-card">
        <div class="tts-num">{{ totalCredits }}</div>
        <div class="tts-lbl">学分合计</div>
      </div>
    </div>

    <!-- 周视图：`class_time` 是自由文本（'周一 1-2节'），按"周几"分列，
         解析不出来的落"其他"——不丢弃、不猜。 -->
    <div class="tt-week">
      <div class="tt-card-title">周课表</div>
      <div class="tt-grid">
        <div v-for="col in weekColumns" :key="col.key" class="tt-col">
          <div class="tt-col-head">{{ col.label }}</div>
          <div class="tt-col-body">
            <div v-if="!col.items.length" class="tt-col-empty">—</div>
            <div v-for="it in col.items" :key="it.planId" class="tt-item">
              <div class="tti-name">{{ it.courseName }}</div>
              <div class="tti-line">{{ it.classTime || '时间待定' }}</div>
              <div class="tti-line">{{ it.location || '教室待定' }}</div>
              <div class="tti-line muted">{{ it.enrolledCount }} / {{ it.maxStudents || '—' }} 人</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="tt-list">
      <div class="tt-card-title">明细</div>
      <el-table :data="list" stripe v-loading="loading" empty-text="暂无排课记录">
        <el-table-column prop="courseCode" label="课程代码" width="120" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.courseType === '必修课' ? 'danger' : 'info'">
              {{ row.courseType || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="credits" label="学分" width="70" align="center" />
        <el-table-column prop="classTime" label="上课时间" width="130">
          <template #default="{ row }">{{ row.classTime || '待定' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="教室" width="140">
          <template #default="{ row }">{{ row.location || '待定' }}</template>
        </el-table-column>
        <!-- 选课人数是现算的（enrollments status=1），容量是计划值 —— 两个数含义不同，
             分成两列摆出来，别合并成"50/60"这种读不出哪个是哪个的写法。 -->
        <el-table-column label="选课人数" width="100" align="center">
          <template #default="{ row }">
            <span class="enrolled">{{ row.enrolledCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="容量" width="90" align="center">
          <template #default="{ row }">{{ row.maxStudents ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="学期" width="150">
          <template #default="{ row }">{{ semesterLabel(row.semester) }}</template>
        </el-table-column>
        <el-table-column label="起止日期" width="200">
          <template #default="{ row }">{{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { teacherAPI } from '@/api/index'

const loading = ref(false)
const list = ref([])

const WEEK_DAYS = [
  { key: '周一', label: '周一' },
  { key: '周二', label: '周二' },
  { key: '周三', label: '周三' },
  { key: '周四', label: '周四' },
  { key: '周五', label: '周五' },
  { key: '周六', label: '周六' },
  { key: '周日', label: '周日' },
  { key: '其他', label: '其他' }
]

const weekOf = (classTime) => {
  const t = classTime || ''
  const hit = WEEK_DAYS.find(d => d.key !== '其他' && t.includes(d.key))
  return hit ? hit.key : '其他'
}

const weekColumns = computed(() => WEEK_DAYS.map(d => ({
  key: d.key,
  label: d.label,
  items: list.value.filter(r => weekOf(r.classTime) === d.key)
})))

const weekDayCount = computed(() => {
  const set = new Set()
  list.value.forEach(r => set.add(weekOf(r.classTime)))
  return set.size
})

const totalEnrolled = computed(() =>
  list.value.reduce((sum, r) => sum + (Number(r.enrolledCount) || 0), 0))

const totalCredits = computed(() => {
  // 同一门课可能有多条排课（不同班/不同学期），学分按课程去重相加，
  // 否则"学分合计"会随排课条数虚增。
  const seen = new Map()
  list.value.forEach(r => { if (r.courseId != null) seen.set(r.courseId, Number(r.credits) || 0) })
  return Array.from(seen.values()).reduce((a, b) => a + b, 0)
})

const semesterLabel = (s) => {
  if (!s) return '—'
  const m = /^(\d{4}-\d{4})-(\d)$/.exec(s)
  return m ? `${m[1]} 学年 第${m[2]}学期` : s
}

const loadTimetable = async () => {
  loading.value = true
  try {
    // 不传 userId：身份只取网关注入的 X-User-Id，后端查的是"排课教师=我"。
    const res = await teacherAPI.getTimetable()
    if (!Array.isArray(res)) {
      throw new Error('课表数据格式异常')
    }
    list.value = res
  } catch (error) {
    console.error('[TeacherTimetable] 加载课表失败:', error)
    ElMessage.error(error?.message ? `加载课表失败：${error.message}` : '加载课表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadTimetable)
</script>

<style scoped>
.tt-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }
.tt-hero { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tt-hero h1 { font-size: 24px; margin: 0 0 6px; color: #18181b; }
.tt-hero p { margin: 0; font-size: 13px; color: #71717a; }

.tt-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
.tts-card { background: #fff; border-radius: 12px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.tts-num { font-size: 26px; font-weight: 700; color: #18181b; }
.tts-lbl { font-size: 12px; color: #71717a; margin-top: 2px; }

.tt-week, .tt-list { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.tt-week { margin-bottom: 14px; }
.tt-card-title { font-size: 15px; font-weight: 600; color: #18181b; margin-bottom: 12px; }

.tt-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 8px; }
.tt-col { border: 1px solid #eef0f4; border-radius: 8px; overflow: hidden; min-height: 120px; }
.tt-col-head { background: #f8f9fb; padding: 6px 8px; font-size: 12px; font-weight: 600; color: #52525b; text-align: center; }
.tt-col-body { padding: 6px; display: flex; flex-direction: column; gap: 6px; }
.tt-col-empty { text-align: center; color: #d4d4d8; font-size: 12px; padding: 8px 0; }
.tt-item { background: #ecfdf5; border-left: 3px solid #10b981; border-radius: 6px; padding: 6px 8px; }
.tti-name { font-size: 12px; font-weight: 600; color: #065f46; margin-bottom: 2px; }
.tti-line { font-size: 11px; color: #52525b; }
.tti-line.muted { color: #9ca3af; }

.enrolled { font-weight: 600; color: #10b981; }

@media (max-width: 1100px) { .tt-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 768px) {
  .tt-stats { grid-template-columns: 1fr 1fr; }
  .tt-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
