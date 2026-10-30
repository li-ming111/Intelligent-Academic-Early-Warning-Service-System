<template>
  <div class="benchmark-page">
    <!-- 学期选择 -->
    <div class="toolbar">
      <el-select v-model="semester" placeholder="选择学期" @change="onSemesterChange">
        <el-option label="全部学期" value="" />
        <el-option v-for="s in semesters" :key="s" :label="s" :value="s" />
      </el-select>
    </div>

    <!-- 概览卡片 -->
    <div class="stat-row">
      <div class="stat-card my-gpa">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg></div>
        <div class="stat-body">
          <div class="stat-value">{{ bm.gpa }}</div>
          <div class="stat-label">我的GPA</div>
        </div>
        <el-tag size="small" :type="gpaTagType">{{ gpaLabel }}</el-tag>
      </div>
      <div class="stat-card class-avg">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg></div>
        <div class="stat-body">
          <div class="stat-value">{{ bm.classAvg }}</div>
          <div class="stat-label">班级均分</div>
        </div>
        <span class="diff" :class="classDiffClass">{{ classDiff }}</span>
      </div>
      <div class="stat-card dept-avg">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c0 2 6 3 6 3s6-1 6-3v-5"/></svg></div>
        <div class="stat-body">
          <div class="stat-value">{{ bm.deptAvg }}</div>
          <div class="stat-label">专业均分</div>
        </div>
        <span class="diff" :class="deptDiffClass">{{ deptDiff }}</span>
      </div>
      <div class="stat-card rank-card">
        <div class="stat-icon">🏆</div>
        <div class="stat-body">
          <div class="stat-value" v-if="bm.total > 0">{{ bm.rank }}<small>/{{ bm.total }}</small></div>
          <div class="stat-value" v-else style="font-size:18px">--</div>
          <div class="stat-label">{{ bm.total > 0 ? '班级排名' : '全部学期' }}</div>
        </div>
      </div>
    </div>

    <!-- 课程对比图 + GPA趋势图 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">课程成绩对比</div>
        <div ref="courseChartRef" class="chart-box"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">GPA 趋势</div>
        <div ref="gpaChartRef" class="chart-box"></div>
      </div>
    </div>

    <!-- 历史数据表 -->
    <div class="table-card">
      <div class="table-header">学期成绩对比</div>
      <el-table :data="historyData" stripe empty-text="暂无历史数据">
        <el-table-column prop="semester" label="学期" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.semester }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="myGpa" label="我的GPA" width="100" align="center">
          <template #default="{ row }"><strong>{{ row.myGpa }}</strong></template>
        </el-table-column>
        <el-table-column prop="classAvg" label="班级均分" width="100" align="center" />
        <el-table-column prop="deptAvg" label="专业均分" width="100" align="center" />
        <el-table-column prop="rank" label="班级排名" align="center" />
        <el-table-column prop="passed" label="通过/挂科" width="120" align="center">
          <template #default="{ row }">
            <span class="pass-info">{{ row.passInfo }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { toNumber, PASS_LINE, isPending } from '@/utils/score'

// ---- 学期转换工具（与Scores.vue一致） ----
const entranceYear = (() => {
  const sid = localStorage.getItem('studentId') || localStorage.getItem('username') || '2023'
  const y = parseInt(sid.substring(0, 4))
  return (!isNaN(y) && y >= 2000) ? y : 2023
})()

function semLabel(num) {
  const n = parseInt(num); if (isNaN(n) || n < 1) return '第' + num + '学期'
  const y = entranceYear + Math.floor((n - 1) / 2)
  return `${y}-${y + 1}-${(n - 1) % 2 + 1}`
}
function semNum(label) {
  if (!label) return null
  const m = label.match(/^(\d{4})-\d{4}-(\d)/)
  if (!m) return parseInt(label) || null
  return (parseInt(m[1]) - entranceYear) * 2 + parseInt(m[2])
}

// ---- 数据 ----
const semester = ref('')
const semesters = ref([])
const historyData = ref([])
const bm = reactive({ gpa: '--', classAvg: '--', deptAvg: '--', rank: 0, total: 0, courses: [] })
const courseChartRef = ref(null)
const gpaChartRef = ref(null)
const allScores = ref([])

// ---- 计算属性 ----
const classDiff = computed(() => {
  const mine = parseFloat(bm.gpa || 0), avg = parseFloat(bm.classAvg || 0)
  if (!avg || !mine) return ''
  const d = ((mine - avg) / avg * 100).toFixed(0)
  return (d > 0 ? '+' : '') + d + '%'
})
const deptDiff = computed(() => {
  const mine = parseFloat(bm.gpa || 0), avg = parseFloat(bm.deptAvg || 0)
  if (!avg || !mine) return ''
  const d = ((mine - avg) / avg * 100).toFixed(0)
  return (d > 0 ? '+' : '') + d + '%'
})
const classDiffClass = computed(() => parseFloat(classDiff.value) >= 0 ? 'up' : 'down')
const deptDiffClass = computed(() => parseFloat(deptDiff.value) >= 0 ? 'up' : 'down')
const gpaLabel = computed(() => {
  const g = parseFloat(bm.gpa || 0)
  return g >= 3.5 ? '优秀' : g >= 3.0 ? '良好' : g >= 2.0 ? '中等' : '需改进'
})
const gpaTagType = computed(() => {
  const g = parseFloat(bm.gpa || 0)
  return g >= 3.5 ? 'success' : g >= 3.0 ? '' : g >= 2.0 ? 'warning' : 'danger'
})

// ---- 生命周期 ----
onMounted(async () => {
  await loadAllScores()
  await loadHistory()
  // 如果 API 都没数据，从 allScores 构建学期列表
  if (!semesters.value.length && allScores.value.length) {
    const semSet = new Set()
    allScores.value.forEach(s => { if (s.semester) semSet.add(String(s.semester)) })
    semesters.value = Array.from(semSet).map(Number).filter(n => !isNaN(n)).sort((a, b) => b - a).map(semLabel)
    if (semesters.value.length) { semester.value = ''; computeAllSemesters() }
  }
  await nextTick(renderCharts)
})

// ---- 数据加载 ----
async function loadAllScores() {
  const uid = getUserId(); if (!uid) return
  try {
    const res = await studentAPI.getScores(uid)
    if (Array.isArray(res)) allScores.value = res
  } catch (e) { console.error('加载成绩失败', e) }
}

async function loadHistory() {
  const uid = getUserId(); if (!uid) return
  try {
    const res = await studentAPI.getHistoryBenchmark(uid)
    const arr = Array.isArray(res) ? res : (res?.data ?? [])
    const semSet = new Set()
    arr.forEach(x => { if (x.semester) semSet.add(x.semester) })
    // 也从allScores中提取学期
    allScores.value.forEach(s => { if (s.semester) semSet.add(String(s.semester)) })
    semesters.value = Array.from(semSet).map(Number).filter(n => !isNaN(n)).sort((a, b) => b - a).map(semLabel)
    // 构建历史表数据
    historyData.value = buildHistory(arr)
    if (semesters.value.length) { semester.value = ''; computeAllSemesters() }
  } catch (e) { console.error('加载历史失败', e) }
}

function buildHistory(benchmarks) {
  if (benchmarks.length) {
    return benchmarks.map(b => ({
      semester: semLabel(b.semester),
      myGpa: (b.studentGpa || 0).toFixed(2),
      classAvg: (b.classAvgGpa || '--'),
      deptAvg: (b.majorAvgGpa || '--'),
      rank: b.classRank ? `${b.classRank}/${b.classTotal}` : '--',
      passInfo: `${b.coursesPassed || 0}过/${b.coursesFailed || 0}挂`
    }))
  }
  // 从 allScores 自行构建
  const map = {}
  allScores.value.forEach(s => {
    const lb = semLabel(s.semester); if (!lb) return
    // 2026-10-19：未录入的课（scoreTotal 为 null / '待录入'）既不算过也不算挂，
    // 也不进绩点 —— 原先 `(s.scoreTotal || 0) >= 60` 把它们折成 0 分当挂科。
    const sc = toNumber(s.scoreTotal)
    // 判定要用 finalScore：期末未录入时 scoreTotal 有"平时分折半"的中间值，非 null 不代表已录入。
    if (sc === null || isPending(s)) return
    if (!map[lb]) map[lb] = { scores: [], pass: 0, fail: 0 }
    map[lb].scores.push(s)
    if (sc >= PASS_LINE) map[lb].pass++; else map[lb].fail++
  })
  return Object.entries(map).map(([lb, v]) => {
    const gpa = v.scores.length ? (v.scores.reduce((sum, s) => sum + (s.gradePoint || 0) * (s.credits || 0), 0) / v.scores.reduce((sum, s) => sum + (s.credits || 0), 0)) : 0
    return { semester: lb, myGpa: gpa.toFixed(2), classAvg: '--', deptAvg: '--', rank: '--', passInfo: `${v.pass}过/${v.fail}挂` }
  }).sort((a, b) => b.semester.localeCompare(a.semester))
}

async function loadSemesterData() {
  if (!semester.value) { resetBm(); return }
  const uid = getUserId(); if (!uid) return
  const semNumVal = semNum(semester.value)
  try {
    const res = await studentAPI.getBenchmarkBySemester(uid, semNumVal)
    parseBenchmark(res)
  } catch (e) {
    // fallback: 从allScores自行计算
    computeFallback(semNumVal)
  }
  await nextTick(renderCharts)
}

function parseBenchmark(res) {
  // res可能直接是数据对象，或ApiResponse包裹
  let data = res
  // 2026-10-02（W4.15）：删掉恒假的 `res?.code === 200` 支（拦截器已解包，`res.code` 恒 undefined）。
  if (res?.data && !res.code) data = res.data
  // data可能是BenchmarkAnalysis实体 {studentGpa,classAvgGpa,majorAvgGpa,classRank,classTotal,...}
  // 也可能是fallback Map {gpa,classAverage,departmentAverage,rank,totalStudents,courses}
  if (data && typeof data === 'object') {
    if (data.studentGpa !== undefined) {
      bm.gpa = Number(data.studentGpa || 0).toFixed(2)
      bm.classAvg = Number(data.classAvgGpa || 0).toFixed(2)
      bm.deptAvg = Number(data.majorAvgGpa || 0).toFixed(2)
      bm.rank = data.classRank || 0
      bm.total = data.classTotal || 0
      bm.courses = data.courses || []
    } else if (data.gpa !== undefined) {
      bm.gpa = Number(data.gpa || 0).toFixed(2)
      bm.classAvg = isNaN(Number(data.classAverage)) ? '--' : Number(data.classAverage).toFixed(2)
      bm.deptAvg = isNaN(Number(data.departmentAverage)) ? '--' : Number(data.departmentAverage).toFixed(2)
      bm.rank = data.rank || '--'
      bm.total = data.totalStudents || '--'
      bm.courses = data.courses || []
    } else {
      computeFallback(semNum(semester.value))
    }
  }
}

function computeFallback(semNumVal) {
  // 未录入的课不进图表与绩点（同 buildHistory 的口径，'待录入' 不是 0 分）。
  const scores = allScores.value.filter(s => String(s.semester) === String(semNumVal) && toNumber(s.scoreTotal) !== null && !isPending(s))
  if (!scores.length) { resetBm(); return }
  let gpaSum = 0, crSum = 0
  const courses = scores.map(s => {
    const gp = s.gradePoint || 0; const cr = s.credits || 0
    gpaSum += gp * cr; crSum += cr
    return { courseName: s.courseName || ('课程ID:' + s.courseId), studentScore: toNumber(s.scoreTotal), classAverage: '--', departmentAverage: '--', rank: '--', totalStudents: '--' }
  })
  bm.gpa = crSum ? (gpaSum / crSum).toFixed(2) : '--'
  // 2026-10-01 第二十一轮: 原先是 `bm.classAvg='3.00'; bm.deptAvg='2.90'` ——
  // 这是**写死的常量**冒充统计值。走到这个分支说明「该学期的对标接口没给出数据」,
  // 那就该显示 '--' 而不是编一个数; 排名同理(原来还写死成 15/100)。
  bm.classAvg = '--'; bm.deptAvg = '--'; bm.rank = 0; bm.total = 0
  bm.courses = courses
}

function resetBm() { Object.assign(bm, { gpa: '--', classAvg: '--', deptAvg: '--', rank: 0, total: 0, courses: [] }) }

// ---- 图表 ----
function renderCharts() {
  renderCourseChart()
  renderGpaChart()
}

function renderCourseChart() {
  const dom = courseChartRef.value; if (!dom) return
  const chart = echarts.getInstanceByDom(dom) || echarts.init(dom)
  const courses = bm.courses || []
  if (!courses.length) { chart.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } }); return }

  // 按课程名去重合并：同名课取平均值
  const merged = {}
  courses.forEach(c => {
    const name = c.courseName || '课程' + c.courseId
    if (!merged[name]) merged[name] = { courseName: name, myScores: [], classAvgs: [] }
    const val = c.studentScore || 0
    // 「全部学期」视图的每门课没有班级平均（computeAllSemesters 填的是 '--'）。
    // 不能 parseInt('--') || 0 —— 那会把"没有这个数"画成一根 0 分的柱子。
    // 没有值就不进数组，最终为 null，ECharts 不画该柱，图例也只在有数据时出现。
    const cavg = classAvgOf(c.classAverage)
    merged[name].myScores.push(val)
    if (cavg !== null) merged[name].classAvgs.push(cavg)
  })
  const unique = Object.values(merged).map(m => ({
    courseName: m.courseName,
    studentScore: +(m.myScores.reduce((a, b) => a + b, 0) / m.myScores.length).toFixed(0),
    classAverage: m.classAvgs.length
      ? +(m.classAvgs.reduce((a, b) => a + b, 0) / m.classAvgs.length).toFixed(0)
      : null
  }))
  const hasClassAvg = unique.some(c => c.classAverage !== null)

  // 横向柱状图，课程名放 Y 轴不截断
  const names = unique.map(c => c.courseName)
  const fixedHeight = Math.max(names.length * 36, 200)
  dom.style.height = fixedHeight + 'px'
  chart.resize()
  const series = [
    { name: '我的成绩', type: 'bar', data: unique.slice().reverse().map(c => c.studentScore),
      barMaxWidth: 18, itemStyle: { color: '#7c3aed', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, color: '#7c3aed', fontWeight: 600 } }
  ]
  if (hasClassAvg) {
    series.push({
      name: '班级平均', type: 'bar', data: unique.slice().reverse().map(c => c.classAverage),
      barMaxWidth: 18, itemStyle: { color: '#e0d5f7', borderRadius: [0, 4, 4, 0] }
    })
  }

  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: hasClassAvg ? ['我的成绩', '班级平均'] : ['我的成绩'], bottom: 0, textStyle: { fontSize: 12 } },
    grid: { left: 3, right: 45, top: 10, bottom: 35, containLabel: true },
    xAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } }, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'category', data: names.reverse(), axisLabel: { fontSize: 12, color: '#333', width: 90, overflow: 'truncate' }, axisTick: { show: false }, axisLine: { show: false }, inverse: true },
    series
  })
}

/** 班级平均可能是数字、"75.5" 或 "--"；拿不到就返回 null（不是 0）。 */
function classAvgOf(v) {
  if (v === null || v === undefined) return null
  const n = typeof v === 'number' ? v : parseFloat(v)
  return Number.isFinite(n) ? n : null
}

function renderGpaChart() {
  const dom = gpaChartRef.value; if (!dom) return
  const chart = echarts.getInstanceByDom(dom) || echarts.init(dom)
  if (!historyData.value.length) { chart.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } }); return }
  const data = historyData.value.map(h => [h.semester, parseFloat(h.myGpa || 0)]).sort((a, b) => a[0].localeCompare(b[0]))
  const gpaList = data.map(d => d[1])
  const minVal = Math.floor(Math.min(...gpaList, 2.0) * 2) / 2
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#eee',
      textStyle: { color: '#333', fontSize: 13 },
      formatter: (p) => `<strong>${p[0].name}</strong><br/>GPA: <b style='color:#7c3aed;font-size:16px'>${p[0].value}</b>`
    },
    grid: { left: 50, right: 25, top: 30, bottom: 45 },
    xAxis: { type: 'category', data: data.map(d => d[0]), axisLabel: { fontSize: 11, color: '#666', rotate: 0 }, axisTick: { show: false }, boundaryGap: false },
    yAxis: { type: 'value', min: Math.min(minVal, 1.5), max: 4.0, interval: 0.5, name: 'GPA', nameTextStyle: { fontSize: 12, padding: [0, 0, 0, -5] } },
    series: [{
      type: 'line', smooth: true, data: gpaList,
      lineStyle: { color: '#7c3aed', width: 3 },
      symbol: 'circle', symbolSize: 8,
      itemStyle: { color: '#7c3aed', borderColor: '#fff', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(124,58,237,0.25)' },
          { offset: 0.5, color: 'rgba(124,58,237,0.08)' },
          { offset: 1, color: 'rgba(124,58,237,0.01)' }
        ])
      },
      label: { show: true, position: 'top', fontSize: 11, color: '#7c3aed', fontWeight: 600,
        formatter: (p) => p.value >= 0 ? (p.value < 2.0 ? '{e|' : '') + p.value + (p.value < 2.0 ? '{e| 预警}' : '') : '',
        rich: { e: { color: '#ef4444', fontSize: 12 } } },
      markLine: {
        silent: true, symbol: 'none',
        data: [{ yAxis: 2.0, label: { formatter: '及格线 2.0', fontSize: 11, color: '#ef6666' }, lineStyle: { color: '#fca5a5', type: 'dashed', width: 1.5 } },
               { yAxis: 3.0, label: { formatter: '良好线 3.0', fontSize: 11, color: '#f59e0b' }, lineStyle: { color: '#fcd34d', type: 'dashed', width: 1.5 } }]
      }
    }]
  })
}

// ---- 事件 ----
function onSemesterChange() {
  if (!semester.value) { computeAllSemesters(); return }
  loadSemesterData()
}
function computeAllSemesters() {
  // 同 buildHistory：未录入的课不算挂科/0 分，也不进绩点与柱状图。
  const all = allScores.value.filter(s => toNumber(s.scoreTotal) !== null && !isPending(s))
  if (!all.length) { resetBm(); return }
  let gpaSum = 0, crSum = 0, pass = 0, fail = 0
  const courses = all.map(s => {
    const gp = s.gradePoint || 0; const cr = s.credits || 0
    const sc = toNumber(s.scoreTotal)
    gpaSum += gp * cr; crSum += cr
    if (sc >= PASS_LINE) pass++; else fail++
    return { courseName: s.courseName || ('课程ID:' + s.courseId), studentScore: sc, classAverage: '--', departmentAverage: '--' }
  })
  bm.gpa = crSum ? (gpaSum / crSum).toFixed(2) : '--'
  // 2026-10-01 第二十一轮: 原先是 `bm.classAvg='3.00'; bm.deptAvg='2.90'` —— 两个写死的常量。
  // 「全部学期」是这一页的**默认视图**(下拉第一项就是它), 而且本页在 StudentLayout 有导航入口
  // ⇒ 打开页面看到的就是 3.00 / 2.90, 与下面那张历史表的真实值互相矛盾。
  // 现在改成取**历史表里各学期的真实值**再平均: 与表格**同源**(表格由后端
  // classAvgGpa/majorAvgGpa 得来), 某学期后端没给值('--')就跳过, 一个都没有就显示 '--'。
  // 「全部学期」不排一个总名次(total=0 ⇒ 排名卡显示 '--', 标签是「全部学期」),
  // 名次仍按学期在下面那张表里逐行给 —— 不合成一个没有定义的"总排名"。
  const avgOf = (key) => {
    const vals = historyData.value.map(r => parseFloat(r[key])).filter(v => !isNaN(v))
    return vals.length ? (vals.reduce((a, b) => a + b, 0) / vals.length).toFixed(2) : '--'
  }
  bm.classAvg = avgOf('classAvg')
  bm.deptAvg = avgOf('deptAvg')
  bm.rank = 0; bm.total = 0
  bm.courses = courses
  nextTick(renderCharts)
}
</script>

<style scoped>
.benchmark-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }
.toolbar { margin-bottom: 16px; display: flex; align-items: center; gap: 12px; }

/* 统计卡 */
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.stat-card { background: #fff; border-radius: 12px; padding: 18px; display: flex; align-items: center; gap: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.06); transition: transform .2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-icon { font-size: 28px; }
.stat-body { flex: 1; }
.stat-value { font-size: 26px; font-weight: 700; color: #18181b; }
.stat-value small { font-size: 14px; font-weight: 400; color: #71717a; }
.stat-label { font-size: 12px; color: #71717a; margin-top: 2px; }
.diff { font-size: 13px; font-weight: 600; }
.diff.up { color: #16a34a; }
.diff.down { color: #ef4444; }
.stat-card.rank-card .stat-value { color: #f59e0b; }

/* 图表 */
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
.chart-card { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.chart-title { font-size: 14px; font-weight: 600; color: #18181b; margin-bottom: 8px; }
.chart-box { width: 100%; height: 320px; }

/* 表格 */
.table-card { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.table-header { font-size: 15px; font-weight: 600; color: #18181b; margin-bottom: 12px; }
.pass-info { font-size: 13px; color: #3f3f46; }

@media (max-width: 768px) {
  .stat-row { grid-template-columns: 1fr 1fr; }
  .chart-row { grid-template-columns: 1fr; }
}
</style>
