<template>
  <div class="statistics-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span class="title">学业统计分析</span>
          <el-button type="primary" size="small" @click="refreshData">刷新数据</el-button>
        </div>
      </template>

      <!-- 统计卡片 -->
      <div class="statistics-cards">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="stat-card">
              <div class="stat-icon" style="background-color: #409eff">
                <el-icon><CircleCheck /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-label">当前GPA</div>
                <div class="stat-value">{{ statistics.currentGPA || 0 }}</div>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="stat-card">
              <div class="stat-icon" style="background-color: #67c23a">
                <el-icon><SuccessFilled /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-label">平均GPA</div>
                <div class="stat-value">{{ statistics.averageGPA || 0 }}</div>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="stat-card">
              <div class="stat-icon" style="background-color: #e6a23c">
                <el-icon><WarningFilled /></el-icon>
              </div>
              <div class="stat-info">
                <!-- 这两张卡读的是 highestGPA/lowestGPA（学期绩点接口的 studentGpa，
                     取值 0-5），原标签写「最高成绩／最低成绩」会把 3.6 读成"考了 3.6 分"。
                     成绩的分档看下面的分布统计。 -->
                <div class="stat-label">最高绩点</div>
                <div class="stat-value">{{ statistics.highestGPA || 0 }}</div>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="stat-card">
              <div class="stat-icon" style="background-color: #f56c6c">
                <el-icon><CircleCloseFilled /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-label">最低绩点</div>
                <div class="stat-value">{{ statistics.lowestGPA || 0 }}</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 成绩分布 -->
      <el-divider></el-divider>

      <div class="statistics-section">
        <h3 class="section-title">成绩分布统计</h3>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">优秀（≥90）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #67c23a" :style="{ width: distWidth(statistics.excellentCount) }"></div>
              </div>
              <div class="distribution-count">{{ statistics.excellentCount || 0 }}门</div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">良好（80-89）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #409eff" :style="{ width: distWidth(statistics.goodCount) }"></div>
              </div>
              <div class="distribution-count">{{ statistics.goodCount || 0 }}门</div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">及格（60-79）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #e6a23c" :style="{ width: distWidth(statistics.passCount) }"></div>
              </div>
              <div class="distribution-count">{{ statistics.passCount || 0 }}门</div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">不及格（&lt;60）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #f56c6c" :style="{ width: distWidth(statistics.failCount) }"></div>
              </div>
              <div class="distribution-count">{{ statistics.failCount || 0 }}门</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 预警统计 -->
      <el-divider></el-divider>

      <div class="statistics-section">
        <h3 class="section-title">预警统计</h3>
        <!-- 三类预警的进度条原先写死 80 / 50 / 30，与各自的条数无关；
             另外下面饼图的图例写「蓝色预警」（Level=1 的口径是低风险档），
             这一块却写「绿色预警」—— 同页两个名字指同一档，这里统一成「蓝色预警」。 -->
        <el-row :gutter="20">
          <el-col :xs="24" :sm="8">
            <div class="warning-item high">
              <div class="warning-level">红色预警</div>
              <div class="warning-count">{{ statistics.highWarningCount || 0 }}</div>
              <el-progress :percentage="warnWidth(statistics.highWarningCount)" color="#f56c6c" :show-text="false"></el-progress>
            </div>
          </el-col>

          <el-col :xs="24" :sm="8">
            <div class="warning-item medium">
              <div class="warning-level">黄色预警</div>
              <div class="warning-count">{{ statistics.mediumWarningCount || 0 }}</div>
              <el-progress :percentage="warnWidth(statistics.mediumWarningCount)" color="#e6a23c" :show-text="false"></el-progress>
            </div>
          </el-col>

          <el-col :xs="24" :sm="8">
            <div class="warning-item low">
              <div class="warning-level">蓝色预警</div>
              <div class="warning-count">{{ statistics.lowWarningCount || 0 }}</div>
              <el-progress :percentage="warnWidth(statistics.lowWarningCount)" color="#409eff" :show-text="false"></el-progress>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 学期成绩趋势 -->
      <el-divider></el-divider>

      <div class="statistics-section">
        <h3 class="section-title">学期成绩趋势</h3>
        <div id="semesterChart" style="width: 100%; height: 400px">
          <div class="empty-state">
            <p>暂无成绩数据</p>
            <small>图表将在有成绩数据时显示</small>
          </div>
        </div>
      </div>

      <!-- 预警分布饼图 -->
      <el-divider></el-divider>

      <div class="statistics-section">
        <h3 class="section-title">预警分布统计</h3>
        <div id="warningChart" style="width: 100%; height: 400px">
          <div class="empty-state">
            <p>暂无预警数据</p>
            <small>图表将在有预警数据时显示</small>
          </div>
        </div>
      </div>

      <!-- 课程成绩详情 -->

      <el-divider></el-divider>

      <div class="statistics-section">
        <h3 class="section-title">课程成绩详情</h3>
        <el-table :data="paginatedCourseScores" stripe class="score-table">
          <el-table-column prop="courseName" label="课程名称" width="200" show-overflow-tooltip></el-table-column>
          <el-table-column label="学期" width="150" align="center">
            <template #default="{ row }">{{ row.semester != null ? `第${row.semester}学期` : '--' }}</template>
          </el-table-column>
          <el-table-column prop="scoreTotal" label="成绩" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getScoreTagType(row.scoreTotal)">
                {{ row.scoreTotal }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="学分" width="100" align="center">
            <template #default="{ row }">
              {{ row.credits === null || row.credits === undefined ? '—' : row.credits }}
            </template>
          </el-table-column>
          <!-- 趋势 = 本门课「最近一次」与「上一次」成绩之差（重修才能比出来）。
               原先的配色把上升画成红色、下降画成绿色 —— 那是预警的语义（涨=变差），
               成绩涨是好事，这里改成上升绿、下降红。 -->
          <el-table-column prop="trend" label="趋势" width="80" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.trend === 'up'" style="color: #67c23a"><ArrowUp /></el-icon>
              <el-icon v-else-if="row.trend === 'down'" style="color: #f56c6c"><ArrowDown /></el-icon>
              <span v-else-if="row.trend === 'flat'" style="color: #909399">→</span>
              <span v-else style="color: #c0c4cc" title="只有一次作答，没有可比对象">—</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 15, 20]"
          :total="(statistics.courseScores || []).length"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 20px; text-align: right"
        ></el-pagination>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { ArrowUp, ArrowDown, CircleCheck, SuccessFilled, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { studentAPI } from '@/api';
import { getUserId } from '@/utils/userUtils';
import { toNumber, warningLevel, isPending } from '@/utils/score';

const statistics = ref({
  currentGPA: 0,
  averageGPA: 0,
  highestGPA: 0,
  lowestGPA: 0,
  excellentCount: 0,
  goodCount: 0,
  passCount: 0,
  failCount: 0,
  highWarningCount: 0,
  mediumWarningCount: 0,
  lowWarningCount: 0,
  semesterTrend: [],
  courseScores: [],
  courseTotal: 0,
});

const currentPage = ref(1);
const pageSize = ref(10);

// 获取统计数据
/**
 * 汇总页面所需统计。
 *
 * 原实现调用 `studentAPI.getStatistics(userId)` —— 这个方法在 studentAPI 里**不存在**
 * （`getStatistics` 只定义在 adminAPI 中）。抛出的 TypeError 被 catch 吞掉，
 * 页面永远显示全 0，还额外弹一句"无法加载统计数据"。
 *
 * 现在改为组合已有接口：仪表盘（权威挂科数/GPA）+ 成绩单 + 学期绩点 + 预警列表。
 * 挂科数不再由前端数行得出 —— 成绩单含各学期原始记录与重修记录，
 * 数行会把重修和期末未录入的课一起算进去。
 */
const fetchStatistics = async () => {
  const userId = getUserId()
  if (!userId) return

  try {
    const [dashRes, scoresRes, histRes, warnRes] = await Promise.all([
      studentAPI.getDashboard(userId).catch(() => null),
      studentAPI.getScores(userId).catch(() => null),
      studentAPI.getHistoryBenchmark(userId).catch(() => null),
      studentAPI.getWarnings(userId).catch(() => null),
    ])
    const dash = unwrap(dashRes) || {}
    const scores = arrayOf(scoresRes)
    const history = arrayOf(histRes)
    const warnings = arrayOf(warnRes)

    // 每门课的全部有效作答（期末已录入），按 (semester, id) 降序 —— 与后端权威口径
    // ScoreCaliber.LATEST_VALID_SCORE 的 `ORDER BY semester DESC, id DESC` 同一个序，
    // 于是 [0] 就是该课的"最新成绩"，[1] 是它的上一次作答（重修记录）。
    //
    // 表里原来只有 courseName / scoreTotal / semester 三个 key，而模板读的是
    // `row.score` / `row.credits` / `row.trend` —— 三个都取不到值：成绩与学分两列
    // 每行空白，趋势列因为 undefined 不等于 'up'/'down' 而**每行都画出"→"**，
    // 连从没重修过的课也显示"持平"。这里把三个 key 都按真实数据补上。
    const attemptsByCourse = new Map()
    scores.forEach(s => {
      // 期末未录入的行 scoreTotal 是"平时分折半"的中间值，不是一次有效作答 ——
      // 放进来会让这 4 门课以 20~42 分出现在"课程成绩详情"里，与挂科数对不上。
      if (isPending(s)) return
      const score = toNumber(s.scoreTotal)
      if (score === null) return
      const key = s.courseId ?? s.courseName
      const list = attemptsByCourse.get(key) || []
      list.push({
        courseName: s.courseName,
        scoreTotal: score,
        credits: toNumber(s.credits),   // courses.credits，未关联到课程时为 null
        semester: s.semester,
        id: toNumber(s.id) || 0,
      })
      attemptsByCourse.set(key, list)
    })
    const courses = []
    attemptsByCourse.forEach(list => {
      list.sort((a, b) => ((toNumber(b.semester) || 0) - (toNumber(a.semester) || 0)) || (b.id - a.id))
      const latest = list[0]
      // 只有一次作答 ⇒ 没有可比对象，趋势是**未知**（null），不是"持平"。
      // 模板对 null 显示 '—'，对真正的持平才显示 '→'。
      let trend = null
      if (list.length > 1) {
        const diff = latest.scoreTotal - list[1].scoreTotal
        trend = diff > 0 ? 'up' : (diff < 0 ? 'down' : 'flat')
      }
      courses.push({ ...latest, trend })
    })

    const bucket = (lo, hi) => courses.filter(c => c.scoreTotal >= lo && c.scoreTotal < hi).length
    const gpas = history.map(h => toNumber(h.studentGpa)).filter(v => v !== null)
    const currentGpa = toNumber(dash.gpa)

    const excellentCount = bucket(90, Infinity)
    const goodCount = bucket(80, 90)
    const failCount = dash.failedCoursesCount || 0
    const passCount = Math.max(0, courses.length - excellentCount - goodCount - failCount)

    // 学期趋势：GPA 来自历史绩点接口，均分由该学期已录入成绩求得
    const scoreBySemester = new Map()
    scores.forEach(s => {
      // 同学期均值也只算期末已录入的成绩，否则未录入的行会把它拉低。
      if (isPending(s)) return
      const score = toNumber(s.scoreTotal)
      if (score === null) return
      const key = String(s.semester)
      const acc = scoreBySemester.get(key) || { sum: 0, n: 0 }
      acc.sum += score
      acc.n += 1
      scoreBySemester.set(key, acc)
    })
    const semesterTrend = history.map(h => {
      const acc = scoreBySemester.get(String(h.semester))
      return {
        semester: h.semesterLabel || String(h.semester ?? ''),
        averageGpa: toNumber(h.studentGpa) ?? 0,
        averageScore: acc && acc.n > 0 ? +(acc.sum / acc.n).toFixed(1) : 0,
      }
    })

    const warnCount = level =>
      warnings.filter(w => warningLevel(w.warningLevel ?? w.level) === level).length

    statistics.value = {
      currentGPA: currentGpa ?? 0,
      averageGPA: gpas.length ? +(gpas.reduce((a, b) => a + b, 0) / gpas.length).toFixed(2) : 0,
      highestGPA: gpas.length ? Math.max(...gpas) : 0,
      lowestGPA: gpas.length ? Math.min(...gpas) : 0,
      excellentCount: excellentCount,
      goodCount: goodCount,
      // 四档互斥且合计 = 总门数。不及格取后端权威值（不前端数行），
      // 及格取余数 —— 原来写的是 `courses.length - 挂科数`，把"优秀""良好"又数了一遍，
      // 四档相加比总门数还大；而模板上的标签（≥85 / 75-84 / 60-74）与代码里的
      // 90 / 80 边界也对不上。现在统一到后端 ScoreCaliber.SCORE_LEVEL_CASE 的边界上。
      failCount: failCount,
      passCount: passCount,
      highWarningCount: warnCount(3),
      mediumWarningCount: warnCount(2),
      lowWarningCount: warnCount(1),
      semesterTrend,
      courseScores: courses,
      courseTotal: courses.length,
    }

    // 初始化图表（先销毁上一轮的实例，否则每次刷新都会多一个 canvas + 一个 resize 监听）
    setTimeout(() => {
      disposeCharts()
      initSemesterChart()
      initWarningPieChart()
    }, 200)
  } catch (error) {
    console.error('获取统计数据失败:', error)
    ElMessage.error('无法加载统计数据，请刷新重试')
  }
};

/** 兼容 axios 包装层可能返回 `{code,data}` 与直接返回数据两种形态。 */
function unwrap(res) {
  return res?.data?.data ?? res?.data ?? res
}
/** 从可能被包装的响应里取出数组。 */
function arrayOf(res) {
  const v = unwrap(res)
  return Array.isArray(v) ? v : []
}

// ---- 图表实例管理 ----
// `echarts.init` 每次都会新建一个实例；同一容器重复 init 会警告并泄漏 canvas，
// 而 resize 监听每刷新一次就多挂一个（旧的 chart 被丢弃后监听还在调它的 resize）。
const chartInstances = []
function initChart(dom) {
  dom.innerHTML = ''
  const chart = echarts.init(dom)
  const onResize = () => chart.resize()
  window.addEventListener('resize', onResize)
  chartInstances.push({ chart, onResize })
  return chart
}
function disposeCharts() {
  chartInstances.forEach(({ chart, onResize }) => {
    window.removeEventListener('resize', onResize)
    chart.dispose()
  })
  chartInstances.length = 0
}
onUnmounted(disposeCharts)

// 初始化学期趋势柱状图
const initSemesterChart = () => {
  const chartContainer = document.getElementById('semesterChart');
  if (!chartContainer) return;

  // 检查是否有数据
  if (!statistics.value.semesterTrend || statistics.value.semesterTrend.length === 0) {
    chartContainer.innerHTML = '<div class="empty-state"><p>暂无成绩数据</p><small>图表将在有成绩数据时显示</small></div>';
    return;
  }

  const chart = initChart(chartContainer);
  const semesterLabels = statistics.value.semesterTrend.map(item => item.semester || '未知学期');
  const gpaValues = statistics.value.semesterTrend.map(item => parseFloat(item.averageGpa) || 0);
  const scoreValues = statistics.value.semesterTrend.map(item => parseFloat(item.averageScore) || 0);

  const option = {
    title: {
      text: '学期成绩趋势',
      left: 'center',
      textStyle: { color: '#333', fontSize: 14 }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0, 0, 0, 0.7)',
      borderColor: '#ddd',
      textStyle: { color: '#fff' }
    },
    legend: {
      top: 30,
      data: ['平均成绩', '平均绩点'],
      textStyle: { color: '#666' }
    },
    grid: {
      left: '5%',
      right: '5%',
      bottom: '10%',
      top: '20%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: semesterLabels,
      boundaryGap: true,
      axisLabel: { color: '#666', fontSize: 12 },
      axisLine: { lineStyle: { color: '#ddd' } }
    },
    yAxis: [
      {
        type: 'value',
        name: '成绩',
        axisLabel: { color: '#666', formatter: '{value}' },
        splitLine: { lineStyle: { color: '#eee' } }
      },
      {
        type: 'value',
        name: '绩点',
        axisLabel: { color: '#666', formatter: '{value}' },
        splitLine: { lineStyle: { color: '#eee' } }
      }
    ],
    series: [
      {
        name: '平均成绩',
        type: 'bar',
        data: scoreValues,
        itemStyle: { color: '#409eff' },
        smooth: true,
        yAxisIndex: 0
      },
      {
        name: '平均绩点',
        type: 'line',
        data: gpaValues,
        itemStyle: { color: '#67c23a' },
        smooth: true,
        yAxisIndex: 1,
        symbol: 'circle',
        symbolSize: 6
      }
    ]
  };

  chart.setOption(option);
};

// 初始化预警分布饼图
const initWarningPieChart = () => {
  const chartContainer = document.getElementById('warningChart');
  if (!chartContainer) return;

  const chart = initChart(chartContainer);
  const data = [
    { value: statistics.value.lowWarningCount, name: '蓝色预警' },
    { value: statistics.value.mediumWarningCount, name: '黄色预警' },
    { value: statistics.value.highWarningCount, name: '红色预警' }
  ].filter(item => item.value > 0 || statistics.value.highWarningCount + statistics.value.mediumWarningCount + statistics.value.lowWarningCount === 0);

  const option = {
    title: {
      text: '预警分布统计',
      left: 'center',
      textStyle: { color: '#333', fontSize: 14 }
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(0, 0, 0, 0.7)',
      borderColor: '#ddd',
      textStyle: { color: '#fff' },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 10,
      data: ['蓝色预警', '黄色预警', '红色预警'],
      textStyle: { color: '#666' }
    },
    series: [
      {
        name: '预警数量',
        type: 'pie',
        radius: ['35%', '60%'],
        avoidLabelOverlap: false,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: {
            show: true,
            fontSize: 12,
            fontWeight: 'bold'
          }
        },
        data: [
          { value: statistics.value.lowWarningCount, name: '蓝色预警', itemStyle: { color: '#409eff' } },
          { value: statistics.value.mediumWarningCount, name: '黄色预警', itemStyle: { color: '#e6a23c' } },
          { value: statistics.value.highWarningCount, name: '红色预警', itemStyle: { color: '#f56c6c' } }
        ]
      }
    ]
  };

  chart.setOption(option);
};

// 刷新数据
const refreshData = () => {
  fetchStatistics();
  ElMessage.success('数据已刷新');
};

/**
 * 分布条宽度 = 该档门数占已出成绩总门数的百分比。
 * 原先是写死的 80/60/40/20%，与旁边那个数字无关 —— 0 门的档位也画着 20% 的条，
 * 而"优秀 0 门 + 满格绿条"正是答辩时最容易被问住的一帧。
 */
const distWidth = (count) => {
  const total = statistics.value.courseTotal || 0
  const n = Number(count) || 0
  if (total <= 0) return '0%'
  return `${Math.min(100, Math.round((n / total) * 100))}%`
}

/** 预警档占比（三类合计为分母），同样替换掉写死的 80/50/30%。 */
const warnWidth = (count) => {
  const total = (Number(statistics.value.highWarningCount) || 0)
    + (Number(statistics.value.mediumWarningCount) || 0)
    + (Number(statistics.value.lowWarningCount) || 0)
  const n = Number(count) || 0
  if (total <= 0) return 0
  return Math.round((n / total) * 100)
}

// 获取成绩标签类型
const getScoreTagType = (score) => {
  if (!score) return 'info';
  const num = Number(score);
  if (num >= 85) return 'success';
  if (num >= 75) return 'primary';
  if (num >= 60) return 'warning';
  return 'danger';
};

// 获取分页后的课程成绩
const paginatedCourseScores = computed(() => {
  const scores = statistics.value.courseScores || [];
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return scores.slice(start, end);
});

// 页面加载
onMounted(() => {
  fetchStatistics()
});

</script>

<style scoped>
.statistics-container {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: calc(100vh - 60px);
}

.box-card {
  margin: 20px auto;
  max-width: 1200px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

/* 统计卡片 */
.statistics-cards {
  margin: 20px 0;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  margin-right: 20px;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

/* 统计分析 */
.statistics-section {
  padding: 20px 0;
}

.section-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 20px;
  border-left: 4px solid #409eff;
  padding-left: 10px;
}

/* 成绩分布 */
.distribution-item {
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.distribution-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}

.distribution-bar {
  width: 100%;
  height: 8px;
  background-color: #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}

.distribution-fill {
  height: 100%;
  border-radius: 4px;
}

.distribution-count {
  font-size: 14px;
  font-weight: bold;
  color: #333;
  text-align: center;
}

/* 预警统计 */
.warning-item {
  padding: 15px;
  border-radius: 4px;
  text-align: center;
}

.warning-item.high {
  background-color: #fef0f0;
  border: 1px solid #fde2e2;
}

.warning-item.medium {
  background-color: #fdf6ec;
  border: 1px solid #fce9d6;
}

.warning-item.low {
  background-color: #f0f9ff;
  border: 1px solid #d4e6ff;
}

.warning-level {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}

.warning-count {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 10px;
}

/* 图表 */
#semesterChart {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background-color: #fafafa;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-state p {
  margin: 0;
  font-size: 16px;
}

.empty-state small {
  margin-top: 10px;
  font-size: 12px;
}

/* 成绩表格 */
.score-table {
  width: 100%;
}

/* 响应式 */
@media (max-width: 768px) {
  .statistics-container {
    padding: 10px;
  }

  .box-card {
    margin: 10px auto;
  }

  .stat-card {
    flex-direction: column;
    text-align: center;
  }

  .stat-icon {
    margin-right: 0;
    margin-bottom: 10px;
  }

  .section-title {
    font-size: 14px;
  }
}
</style>
