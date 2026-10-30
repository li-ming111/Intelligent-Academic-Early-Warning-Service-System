<template>
  <div class="counselor-scores">
    <div class="page-header">
      <h1>学生成绩跟踪</h1>
      <p>按课程查看班级成绩分布、识别低分学生、跟踪成绩变化</p>
    </div>

    <el-card v-loading="loading" style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">选择课程分析</div>
      </template>
      <div class="filter-bar">
        <el-select v-model="selectedCourse" placeholder="选择课程" style="width: 300px;" clearable @change="onCourseChange">
          <el-option
            v-for="course in courseOptions"
            :key="course.id"
            :label="course.courseName"
            :value="course.id"
          />
        </el-select>
        <el-select v-model="selectedClass" placeholder="选择班级" style="width: 200px;" clearable @change="onClassChange">
          <el-option
            v-for="cls in classOptions"
            :key="cls.id"
            :label="cls.name"
            :value="cls.id"
          />
        </el-select>
        <el-button type="primary" @click="analyzeScores">分析</el-button>
        <el-button @click="refreshData">刷新</el-button>
      </div>
    </el-card>

    <!-- AI 班级学业分析（W26）：`aiAPI.analyzeClassScores` 此前全仓零调用。
         端点是**纯 AI**（后端直接问DeepSeek），没配 AI_API_KEY 时会拿占位串
         冒充成功 —— 闸在 utils/ai.js，命中就换成一句实话，不当作分析结果渲染。 -->
    <el-card v-if="selectedClass" style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">AI 班级学业分析</div>
      </template>
      <AiInsight
        title="AI 班级分析"
        :load="aiAnalyzeClass"
        hint="把当前选中的班级交给 AI 做整体学业分析（需要后端配置 AI_API_KEY）"
      />
    </el-card>

    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <template #header>平均成绩</template>
          <div style="font-size: 32px; color: #409eff; font-weight: bold; text-align: center;">{{ scoreStats.average }}<span style="font-size: 16px; color: #999;">分</span></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <template #header>及格率</template>
          <div style="font-size: 32px; color: #67c23a; font-weight: bold; text-align: center;">{{ scoreStats.passRate }}<span style="font-size: 16px; color: #999;">%</span></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <template #header>低分学生</template>
          <div style="font-size: 32px; color: #f56c6c; font-weight: bold; text-align: center;">{{ scoreStats.lowScoreCount }}<span style="font-size: 16px; color: #999;">人</span></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <template #header>学分达标</template>
          <div style="font-size: 32px; color: #2563eb; font-weight: bold; text-align: center;">{{ scoreStats.creditRate }}<span style="font-size: 16px; color: #999;">%</span></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">成绩分布</div>
      </template>
      <div ref="chartContainer" style="height: 350px;"></div>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>低分学生预警</span>
          <el-tag type="danger" style="margin-left: 10px;">{{ lowScoreStudents.length }}人</el-tag>
        </div>
      </template>
      <el-table :data="lowScoreStudents" stripe empty-text="暂无低分学生数据">
        <el-table-column prop="student_name" label="学生" width="120"></el-table-column>
        <el-table-column prop="student_no" label="学号" width="120"></el-table-column>
        <el-table-column prop="course_name" label="课程" width="150"></el-table-column>
        <el-table-column prop="class_name" label="班级" width="100"></el-table-column>
        <el-table-column label="成绩" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.score_total < 60 ? '#f56c6c' : '#2563eb', fontWeight: 'bold' }">{{ row.score_total }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.score_total < 60 ? 'danger' : 'warning'" size="small">
              {{ row.score_total < 60 ? '高风险' : '中风险' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="viewTrend(row)">查看趋势</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="trendDialogVisible" title="学生成绩变化趋势" width="700px">
      <!-- 有成绩才画图；一条都没有时说"暂无"，而不是画一张空坐标系让人以为成绩是 0 -->
      <div v-if="trendEmpty" class="trend-empty">该学生暂无成绩记录</div>
      <div v-else ref="trendChartContainer" style="height: 350px;"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { counselorAPI, aiAPI } from '@/api/index'
import AiInsight from '@/components/AiInsight.vue'
import { getUserId } from '@/utils/userUtils'
import { toNumber } from '@/utils/score'

const route = useRoute()
const loading = ref(false)
const selectedCourse = ref('')
const selectedClass = ref('')
const courseOptions = ref([])
const classOptions = ref([])
const scoreStats = ref({ average: 0, passRate: 0, lowScoreCount: 0, creditRate: 0 })
const lowScoreStudents = ref([])
const allScores = ref([])
const chartContainer = ref(null)
const trendChartContainer = ref(null)
const trendDialogVisible = ref(false)
const trendEmpty = ref(false)
let chartInstance = null

const getCounselorId = () => localStorage.getItem('counselorId') || getUserId()

onMounted(async () => {
  await loadData()
})

onUnmounted(() => {
  if (chartInstance) chartInstance.dispose()
})

const loadData = async () => {
  loading.value = true
  try {
    const counselorId = getCounselorId()
    if (!counselorId) { loading.value = false; return }

    // 加载班级列表
    const classRes = await counselorAPI.getMyClasses(counselorId)
    classOptions.value = Array.isArray(classRes) ? classRes : (classRes?.data || [])

    // 加载低分学生预警
    const lowRes = await counselorAPI.getLowScoreStudents(counselorId)
    const lowScores = Array.isArray(lowRes) ? lowRes : (lowRes?.data || [])
    // 关注线 70 与后端 ScoreCaliber.ATTENTION_LINE 一致（注意它不是 60 的挂科线）。
    // 用 toNumber 而非 Number：Number(null) 为 0，会把空值当成"0 分低分生"列出来。
    lowScoreStudents.value = lowScores.filter(s => {
      const score = toNumber(s.score_total)
      return score !== null && score < 70
    })

    // 加载完整成绩数据用于统计
    if (classOptions.value.length > 0) {
      // 从「我的班级」点「查看成绩」进来时带着 classId，优先用它；否则默认第一个班
      const wanted = Number(route.query.classId)
      const match = classOptions.value.find(c => Number(c.id) === wanted)
      selectedClass.value = match ? match.id : classOptions.value[0].id
      await loadClassScores()
    }

    // 从全部低分数据中提取课程列表（保证课程下拉有数据）
    const allData = [...lowScores, ...allScores.value]
    const courses = new Map()
    allData.forEach(s => {
      if (s.course_id && s.course_name) {
        const id = Number(s.course_id)
        if (!courses.has(id)) {
          courses.set(id, { id, courseName: s.course_name })
        }
      }
    })
    courseOptions.value = Array.from(courses.values())
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载成绩数据失败')
  } finally {
    loading.value = false
  }
}

const loadClassScores = async () => {
  if (!selectedClass.value) return
  const counselorId = getCounselorId()
  try {
    const scoreRes = await counselorAPI.getClassScores(counselorId, selectedClass.value)
    allScores.value = Array.isArray(scoreRes) ? scoreRes : (scoreRes?.data || [])
    updateStats()
    updateCharts()
  } catch (e) { console.error(e) }
}

const updateStats = () => {
  updateStatsForScores(allScores.value)
}

const updateStatsForScores = (scores) => {
  if (!scores || scores.length === 0) {
    scoreStats.value = { average: 0, passRate: 0, lowScoreCount: 0, creditRate: 0 }
    return
  }
  let total = 0, passCount = 0, failCount = 0, scored = 0
  scores.forEach(s => {
    // 用 toNumber 而不是 Number：Number(null) 为 0、Number('') 为 0，
    // 会把"没有成绩"折成 0 分并计入不及格。返回 null 时才真正跳过。
    const score = toNumber(s.score_total)
    if (score !== null) {
      total += score
      scored++
      if (score >= 60) passCount++
      else failCount++
    }
  })
  // 分母用"实际有成绩的条数"而不是 scores.length：
  // 后者会把无成绩的行算进分母，把均分和及格率一起拉低。
  const avg = scored > 0 ? total / scored : 0
  const passRate = scored > 0 ? (passCount / scored) * 100 : 0
  // 2026-10-19：「学分达标」改为与 CounselorCourses.vue 同口径 —— **学生维度**
  // "所有已录入课程均通过"的人数占比。原先算的是"≥80 分记录条数占比"，
  // 与「选课与学分」页的达标名单对不上。
  const byStudent = new Map()
  scores.forEach(s => {
    const sid = s.student_id ?? s.student_no
    if (sid == null) return
    if (!byStudent.has(sid)) byStudent.set(sid, { fail: 0, scored: 0 })
    const st = byStudent.get(sid)
    const score = toNumber(s.score_total)
    if (score !== null) { st.scored++; if (score < 60) st.fail++ }
  })
  let achieved = 0
  byStudent.forEach(st => { if (st.scored > 0 && st.fail === 0) achieved++ })
  const creditRate = byStudent.size > 0 ? (achieved / byStudent.size) * 100 : 0
  scoreStats.value = {
    average: Math.round(avg * 10) / 10,
    passRate: Math.round(passRate),
    lowScoreCount: failCount,
    creditRate: Math.round(creditRate)
  }
}

const updateCharts = () => {
  updateChartsForScores(allScores.value)
}

const updateChartsForScores = (scores) => {
  if (!chartContainer.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartContainer.value)

  const dist = { '90-100': 0, '80-89': 0, '70-79': 0, '60-69': 0, '0-59': 0 }
  const data = scores || allScores.value
  data.forEach(s => {
    const score = toNumber(s.score_total)
    if (score === null) return
    if (score >= 90) dist['90-100']++
    else if (score >= 80) dist['80-89']++
    else if (score >= 70) dist['70-79']++
    else if (score >= 60) dist['60-69']++
    else dist['0-59']++
  })

  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: Object.keys(dist), axisLabel: { fontSize: 13 } },
    yAxis: { type: 'value', name: '人数' },
    series: [{
      data: Object.values(dist),
      type: 'bar',
      barWidth: '50%',
      itemStyle: {
        color: function(params) {
          const colors = ['#f56c6c', '#2563eb', '#409eff', '#67c23a', '#67c23a']
          return colors[params.dataIndex] || '#409eff'
        },
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: true, position: 'top' }
    }]
  })
}

const onCourseChange = () => {
  // 课程变更时只更新过滤显示，不重新加载数据
  filterByCourse()
}

const onClassChange = async () => {
  if (!selectedClass.value) return
  loading.value = true
  try {
    await loadClassScores()
    // 如果已选择课程，自动应用过滤
    if (selectedCourse.value) {
      filterByCourse()
    }
  } finally { loading.value = false }
}

const filterByCourse = () => {
  if (!selectedCourse.value) {
    // 没有选择课程，显示全部数据
    updateStats()
    updateCharts()
    return
  }
  // 按课程过滤用于展示
  const filtered = allScores.value.filter(s => Number(s.course_id) === Number(selectedCourse.value))
  updateStatsForScores(filtered)
  updateChartsForScores(filtered)
  ElMessage.success(`课程分析完成，共 ${filtered.length} 条成绩记录`)
}

const analyzeScores = async () => {
  if (!selectedClass.value) { ElMessage.warning('请先选择班级'); return }
  loading.value = true
  try {
    await loadClassScores()
    // 如果选择了课程，按课程过滤分析
    if (selectedCourse.value) {
      filterByCourse()
    } else {
      ElMessage.success('分析完成，共 ' + allScores.value.length + ' 条成绩记录')
    }
  } finally { loading.value = false }
}

/** AI 班级学业分析（W26）。`selectedClass` 在这个页面绑的就是**班级 id**（`:value="cls.id"`）。 */
const aiAnalyzeClass = () => {
  if (!selectedClass.value) throw new Error('请先选择班级')
  return aiAPI.analyzeClassScores(selectedClass.value)
}

const refreshData = async () => {
  selectedCourse.value = ''
  allScores.value = []
  await loadData()
}

const viewTrend = async (row) => {
  trendDialogVisible.value = true
  try {
    const studentId = row.student_id
    const res = await counselorAPI.getStudentScoreTrend(studentId)
    // ⚠️ 拦截器已经解包：`res` 就是接口里的 `data`，而这个端点的 data 是
    // **List<Map>**（实测返回 25 行）。原写法 `res.data?.data || []` 在数组上取
    // `.data` 恒为 undefined → trendData 永远是 []，弹窗里那张图**永远是空的**，
    // 而接口明明有数据、这里也不报错（只有 console 干净地什么也不说）。
    const trendData = Array.isArray(res) ? res : (res?.data ?? res?.list ?? [])
    trendEmpty.value = trendData.length === 0

    if (trendEmpty.value) return
    setTimeout(() => {
      if (!trendChartContainer.value) return
      const chart = echarts.init(trendChartContainer.value)
      chart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: trendData.map(s => s.semester || s.course_name || '') || ['暂无数据']
        },
        yAxis: { type: 'value', min: 0, max: 100 },
        series: [{
          // 缺成绩的学期在折线上断开（null）而不是掉到 0 分
          data: trendData.map(s => toNumber(s.score_total)),
          type: 'line',
          smooth: true,
          lineStyle: { width: 3, color: '#67c23a' },
          areaStyle: { color: 'rgba(103, 194, 58, 0.1)' },
          itemStyle: { color: '#67c23a' },
          markLine: {
            data: [
              { yAxis: 60, label: { formatter: '及格线' }, lineStyle: { color: '#f56c6c', type: 'dashed' } }
            ]
          }
        }]
      })
    }, 200)
  } catch (error) {
    console.error('加载趋势失败:', error)
  }
}
</script>

<style scoped>
.trend-empty {
  height: 350px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.counselor-scores {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.page-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  display: flex;
  align-items: center;
}
</style>