<template>
  <div class="timetable-page">
    <div class="page-head">
      <h1>我的课表</h1>
      <p>本学期所选课程的授课时间、教室与任课教师</p>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <!-- 学期下拉的取值直接来自后端返回的 `semester` 字符串（如 '2025-2026-2'），
             不在前端自己拼 —— 排课的学期是 varchar，与成绩那边的数字学期不是一套。 -->
        <el-select v-model="selectedSemester" placeholder="全部学期" clearable style="width: 200px">
          <el-option v-for="s in semesterOptions" :key="s" :label="semesterLabel(s)" :value="s" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <span class="total-label">共 {{ filteredList.length }} 个上课安排</span>
        <el-button size="small" @click="loadTimetable" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-body">
          <div class="stat-value">{{ filteredList.length }}</div>
          <div class="stat-label">课程安排</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-body">
          <div class="stat-value">{{ weekDayCount }}</div>
          <div class="stat-label">占用天数/周</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-body">
          <div class="stat-value">{{ totalCredits }}</div>
          <div class="stat-label">学分合计</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-body">
          <div class="stat-value">{{ teacherCount }}</div>
          <div class="stat-label">任课教师</div>
        </div>
      </div>
    </div>

    <!-- 周视图：把 `class_time` 里的"周几"抽出来分列。
         后端给的是 '周一 1-2节' 这种自由文本（varchar），解析不出来的归到"其他"，
         不猜、不丢 —— 直接原样展示。 -->
    <div class="week-card">
      <div class="card-title">周课表</div>
      <div class="week-grid">
        <div v-for="col in weekColumns" :key="col.key" class="week-col">
          <div class="week-head">{{ col.label }}</div>
          <div class="week-body">
            <div v-if="!col.items.length" class="week-empty">—</div>
            <div v-for="it in col.items" :key="it.planId" class="week-item">
              <div class="wi-name">{{ it.courseName }}</div>
              <div class="wi-time">{{ it.classTime || '时间待定' }}</div>
              <div class="wi-loc">{{ it.location || '教室待定' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="table-card">
      <div class="card-title">明细</div>
      <el-table :data="filteredList" stripe v-loading="loading" empty-text="暂无排课记录">
        <el-table-column prop="courseCode" label="课程代码" width="120" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="typeTag(row.courseType)">{{ row.courseType || '未分类' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="credits" label="学分" width="70" align="center" />
        <el-table-column prop="teacherName" label="任课教师" width="110">
          <!-- 排课的 teacher_id 取不到对应档案时后端回 null（LEFT JOIN），
               如实写"—"，不拿 id 拼一个"教师29"出来冒充人名。 -->
          <template #default="{ row }">{{ row.teacherName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="classTime" label="上课时间" width="130">
          <template #default="{ row }">{{ row.classTime || '待定' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="教室" width="140">
          <template #default="{ row }">{{ row.location || '待定' }}</template>
        </el-table-column>
        <el-table-column label="学期" width="130">
          <template #default="{ row }">{{ semesterLabel(row.semester) }}</template>
        </el-table-column>
        <el-table-column label="起止日期" width="200">
          <template #default="{ row }">
            {{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const loading = ref(false)
const list = ref([])
const selectedSemester = ref('')

// 周几的分列顺序。'其他' 兜底放最后 —— 解析不出周几的行仍然看得见，
// 不像"正则不匹配就丢弃"那样静默少几门课。
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

const semesterOptions = computed(() => {
  const set = new Set()
  list.value.forEach(r => { if (r.semester) set.add(r.semester) })
  return Array.from(set).sort()
})

const filteredList = computed(() => {
  if (!selectedSemester.value) return list.value
  return list.value.filter(r => r.semester === selectedSemester.value)
})

const weekOf = (classTime) => {
  const t = classTime || ''
  const hit = WEEK_DAYS.find(d => d.key !== '其他' && t.includes(d.key))
  return hit ? hit.key : '其他'
}

const weekColumns = computed(() => WEEK_DAYS.map(d => ({
  key: d.key,
  label: d.label,
  items: filteredList.value.filter(r => weekOf(r.classTime) === d.key)
})))

const weekDayCount = computed(() => {
  const set = new Set()
  filteredList.value.forEach(r => set.add(weekOf(r.classTime)))
  return set.size
})

const totalCredits = computed(() =>
  filteredList.value.reduce((sum, r) => sum + (Number(r.credits) || 0), 0))

const teacherCount = computed(() => {
  const set = new Set()
  filteredList.value.forEach(r => { if (r.teacherName) set.add(r.teacherName) })
  return set.size
})

const typeTag = (t) => (t === '必修课' ? 'danger' : 'info')

// '2025-2026-2' → '2025-2026 学年 第2学期'
const semesterLabel = (s) => {
  if (!s) return '—'
  const m = /^(\d{4}-\d{4})-(\d)$/.exec(s)
  return m ? `${m[1]} 学年 第${m[2]}学期` : s
}

const loadTimetable = async () => {
  loading.value = true
  try {
    const userId = getUserId()
    if (!userId) {
      ElMessage.error('未登录，无法获取课表')
      return
    }
    const res = await studentAPI.getTimetable(userId)
    // 后端回的是裸数组（student-service 的 ApiResponse.data）。这里不写
    // `.records || .list || []` 那种多分支兜底 —— 形状不对就是要报出来，
    // 静默回空数组会把"接口坏了"显示成"你本学期没课"。
    if (!Array.isArray(res)) {
      throw new Error('课表数据格式异常')
    }
    list.value = res
  } catch (error) {
    console.error('[MyTimetable] 加载课表失败:', error)
    ElMessage.error(error?.message ? `加载课表失败：${error.message}` : '加载课表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadTimetable)
</script>

<style scoped>
.timetable-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }
.page-head { margin-bottom: 16px; }
.page-head h1 { font-size: 24px; margin: 0 0 6px; color: #18181b; }
.page-head p { margin: 0; font-size: 13px; color: #71717a; }

.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; gap: 10px; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.toolbar-right .total-label { font-size: 13px; color: #71717a; }

.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
.stat-card { background: #fff; border-radius: 12px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.stat-value { font-size: 26px; font-weight: 700; color: #18181b; }
.stat-label { font-size: 12px; color: #71717a; margin-top: 2px; }

.week-card, .table-card { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.week-card { margin-bottom: 14px; }
.card-title { font-size: 15px; font-weight: 600; color: #18181b; margin-bottom: 12px; }

.week-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 8px; }
.week-col { border: 1px solid #eef0f4; border-radius: 8px; overflow: hidden; min-height: 120px; }
.week-head { background: #f8f9fb; padding: 6px 8px; font-size: 12px; font-weight: 600; color: #52525b; text-align: center; }
.week-body { padding: 6px; display: flex; flex-direction: column; gap: 6px; }
.week-empty { text-align: center; color: #d4d4d8; font-size: 12px; padding: 8px 0; }
.week-item { background: #eef2ff; border-left: 3px solid #6366f1; border-radius: 6px; padding: 6px 8px; }
.wi-name { font-size: 12px; font-weight: 600; color: #312e81; margin-bottom: 2px; }
.wi-time { font-size: 11px; color: #52525b; }
.wi-loc { font-size: 11px; color: #71717a; }

@media (max-width: 1100px) {
  .week-grid { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 768px) {
  .stat-row { grid-template-columns: 1fr 1fr; }
  .week-grid { grid-template-columns: repeat(2, 1fr); }
  .toolbar { flex-direction: column; align-items: stretch; }
}
</style>
