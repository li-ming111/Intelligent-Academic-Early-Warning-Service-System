<template>
  <div class="ta-page">
    <div class="ta-toolbar">
      <el-select v-model="selectedClass" placeholder="选择班级" @change="loadAnalysis" clearable style="width:200px">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="selectedCourse" placeholder="选择课程" v-if="courses.length" clearable style="width:200px" @change="loadAnalysis">
        <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <div class="ta-stats" v-if="stats.total > 0">
      <div class="tas-card"><div class="tas-icon blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div><div class="tas-num">{{ stats.total }}</div><div class="tas-lbl">总人数</div></div>
      <div class="tas-card"><div class="tas-icon green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/></svg></div><div class="tas-num">{{ stats.avg }}</div><div class="tas-lbl">平均分</div></div>
      <div class="tas-card"><div class="tas-icon purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg></div><div class="tas-num">{{ stats.pass }}%</div><div class="tas-lbl">及格率</div></div>
      <div class="tas-card"><div class="tas-icon red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/></svg></div><div class="tas-num red">{{ stats.atRisk }}</div><div class="tas-lbl">风险学生</div></div>
    </div>

    <div class="ta-charts" v-if="stats.total > 0">
      <div class="ta-chart-card"><div class="tcc-title">成绩分布</div><div ref="barRef" class="tcc-box"></div></div>
      <div class="ta-chart-card"><div class="tcc-title">成绩段占比</div><div ref="pieRef" class="tcc-box"></div></div>
    </div>

    <div class="ta-panel" v-if="studentList.length">
      <div class="tp-head"><span>学生成绩排名</span><span class="tp-badge">{{ studentList.length }}人</span></div>
      <el-table :data="pagedStudents" stripe size="medium" style="width:100%">
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="studentId" label="学号" min-width="140" />
        <el-table-column prop="studentName" label="姓名" min-width="100" />
        <el-table-column label="平均分" min-width="100" align="center">
          <template #default="{row}">
            <span class="sc-bdg" :class="row.scoreTotal < 60 ? 'fail' : 'ok'">{{ row.scoreTotal || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120" align="center">
          <template #default="{row}">
            <el-tag :type="row.scoreTotal >= 80 ? 'success' : row.scoreTotal >= 60 ? 'warning' : 'danger'">{{ row.scoreTotal >= 80 ? '优秀' : row.scoreTotal >= 60 ? '及格' : '不及格' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="ta-pager" v-if="studentList.length > pageSize">
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="studentList.length" layout="total, prev, pager, next" small @current-change="currentPage=$event" />
      </div>
    </div>
    <div v-else-if="selectedClass" class="ta-empty">暂无成绩数据</div>
    <div v-else class="ta-empty">请选择班级查看分析</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { teacherAPI } from '@/api/index'
import { getTeacherId } from '@/utils/userUtils'
import { toNumber } from '@/utils/score'

const selectedClass = ref('')
const selectedCourse = ref('')
const currentPage = ref(1)
const pageSize = ref(15)
const classes = ref([])
const courses = ref([])
const studentList = ref([])
const barRef = ref(null)
const pieRef = ref(null)
const stats = reactive({ total: 0, avg: '--', pass: 0, atRisk: 0 })

const pagedStudents = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return studentList.value.slice(start, start + pageSize.value)
})



onMounted(() => loadClasses())

async function loadClasses() {
  try {
    const tid = getTeacherId()
    const r = await teacherAPI.getMyClasses(tid)
    const list = Array.isArray(r) ? r : (r?.data || [])
    classes.value = list.map(c => ({ id: c.id || c.classId, name: c.name || c.className }))
    if (classes.value.length) {
      selectedClass.value = classes.value[0].id
      await loadCourses()
      await loadAnalysis()
    }
  } catch (e) { console.error(e) }
}

async function loadCourses() {
  try {
    const tid = getTeacherId()
    const r = await teacherAPI.getCourses(tid)
    if (Array.isArray(r)) courses.value = r.map(c => ({ id: c.id, name: c.name || c.courseName }))
  } catch (e) {}
}

async function loadAnalysis() {
  if (!selectedClass.value) return
  currentPage.value = 1
  try {
    const className = classes.value.find(c => c.id === selectedClass.value)?.name
    const r = await teacherAPI.getScoresByClass(className)
    const list = Array.isArray(r) ? r : []
    const byStudent = {}
    list.forEach(s => {
      const sid = s.studentNo || s.studentId
      if (!byStudent[sid]) byStudent[sid] = { studentId: sid, studentName: s.studentName, scores: [] }
      // 期末未录入的成绩不能折成 0 分参与平均：`s.totalScore || 0` 会把"待录入"
      // 算成 0 分，既拉低全班平均分，又把该学生误标成"不及格/风险学生"。
      const score = toNumber(s.totalScore ?? s.score)
      if (score !== null) byStudent[sid].scores.push(score)
    })
    studentList.value = Object.values(byStudent)
      .filter(s => s.scores.length > 0)
      .map(s => {
        const avg = +(s.scores.reduce((a,b)=>a+b,0) / s.scores.length).toFixed(1)
        return { studentId: s.studentId, studentName: s.studentName, scoreTotal: avg }
      }).sort((a,b) => b.scoreTotal - a.scoreTotal)

    const sc = studentList.value.map(s => s.scoreTotal)
    stats.total = sc.length
    stats.avg = sc.length ? (sc.reduce((a,b)=>a+b,0) / sc.length).toFixed(1) : '--'
    stats.pass = sc.length ? Math.round(sc.filter(v => v >= 60).length / sc.length * 100) : 0
    stats.atRisk = sc.filter(v => v < 60).length
    await nextTick(renderCharts)
  } catch (e) { console.error(e) }
}

function renderCharts() {
  if (!barRef.value) return
  const sc = studentList.value.map(s => s.scoreTotal)
  const buckets = { '90-100':0,'80-89':0,'70-79':0,'60-69':0,'0-59':0 }
  sc.forEach(v => { if(v>=90)buckets['90-100']++; else if(v>=80)buckets['80-89']++; else if(v>=70)buckets['70-79']++; else if(v>=60)buckets['60-69']++; else buckets['0-59']++ })
  const bar = echarts.getInstanceByDom(barRef.value) || echarts.init(barRef.value)
  bar.setOption({
    tooltip:{}, xAxis:{ type:'category', data:Object.keys(buckets) }, yAxis:{ type:'value' },
    series:[{ type:'bar', data:Object.values(buckets), barMaxWidth:30,
      itemStyle:{ borderRadius:[6,6,0,0], color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#7c3aed'},{offset:1,color:'#a78bfa'}])}}]
  })
  const pie = echarts.getInstanceByDom(pieRef.value) || echarts.init(pieRef.value)
  pie.setOption({
    tooltip:{ trigger:'item' }, legend:{ bottom:0, textStyle:{fontSize:11} },
    series:[{ type:'pie', radius:['45%','70%'], center:['50%','45%'],
      data: Object.entries(buckets).filter(([,v])=>v>0).map(([n,v])=>({name:n,value:v})),
      color:['#16a34a','#7c3aed','#3b82f6','#f59e0b','#ef4444'], label:{show:false} }]
  })
}
</script>

<style scoped>
.ta-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }
.ta-toolbar { display: flex; gap: 12px; margin-bottom: 18px; }
.ta-stats { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; margin-bottom: 18px; }
.tas-card { background:#fff; border-radius:12px; padding:18px 20px; text-align:center; box-shadow:0 1px 3px rgba(0,0,0,.04); }
.tas-icon { width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; margin:0 auto 8px; }
.tas-icon.blue{background:#eff6ff;color:#3b82f6} .tas-icon.green{background:#ecfdf5;color:#16a34a}
.tas-icon.purple{background:#f5f3ff;color:#7c3aed} .tas-icon.red{background:#fef2f2;color:#ef4444}
.tas-num { font-size:24px; font-weight:700; color:#18181b; } .tas-num.red { color:#ef4444; }
.tas-lbl { font-size:12px; color:#a1a1aa; margin-top:2px; }
.ta-charts { display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:18px; }
.ta-chart-card { background:#fff; border-radius:12px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,.04); }
.tcc-title { font-size:14px; font-weight:600; color:#18181b; margin-bottom:8px; }
.tcc-box { height:280px; }
.ta-panel { background:#fff; border-radius:12px; box-shadow:0 1px 3px rgba(0,0,0,.04); overflow:hidden; }
.ta-panel :deep(.el-table) { width: 100% !important; }
.ta-panel :deep(.el-table__body-wrapper) { overflow-x: auto; }
.tp-head { padding:14px 20px; border-bottom:1px solid #f4f4f5; display:flex; justify-content:space-between; font-size:14px; font-weight:600; }
.tp-badge { font-size:12px; background:#f5f3ff; color:#7c3aed; padding:2px 10px; border-radius:10px; }
.sc-bdg { font-weight:700; padding:3px 10px; border-radius:6px; font-size:13px; }
.sc-bdg.ok { background:#ecfdf5; color:#16a34a; } .sc-bdg.fail { background:#fef2f2; color:#ef4444; }
.ta-pager { display: flex; justify-content: center; padding: 14px 0 4px; }
.ta-empty { text-align:center; padding:60px 20px; color:#a1a1aa; font-size:14px; }
@media(max-width:768px){ .ta-stats{grid-template-columns:repeat(2,1fr)} .ta-charts{grid-template-columns:1fr} }
</style>
