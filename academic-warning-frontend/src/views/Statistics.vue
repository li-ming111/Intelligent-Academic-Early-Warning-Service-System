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
                <i class="el-icon-circle-check"></i>
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
                <i class="el-icon-success"></i>
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
                <i class="el-icon-warning"></i>
              </div>
              <div class="stat-info">
                <div class="stat-label">最高成绩</div>
                <div class="stat-value">{{ statistics.highestGPA || 0 }}</div>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="stat-card">
              <div class="stat-icon" style="background-color: #f56c6c">
                <i class="el-icon-close"></i>
              </div>
              <div class="stat-info">
                <div class="stat-label">最低成绩</div>
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
              <div class="distribution-label">优秀（≥85）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #67c23a; width: 80%"></div>
              </div>
              <div class="distribution-count">{{ statistics.excellentCount || 0 }}门</div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">良好（75-84）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #409eff; width: 60%"></div>
              </div>
              <div class="distribution-count">{{ statistics.goodCount || 0 }}门</div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">及格（60-74）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #e6a23c; width: 40%"></div>
              </div>
              <div class="distribution-count">{{ statistics.passCount || 0 }}门</div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <div class="distribution-item">
              <div class="distribution-label">不及格（<60）</div>
              <div class="distribution-bar">
                <div class="distribution-fill" style="background-color: #f56c6c; width: 20%"></div>
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
        <el-row :gutter="20">
          <el-col :xs="24" :sm="8">
            <div class="warning-item high">
              <div class="warning-level">红色预警</div>
              <div class="warning-count">{{ statistics.highWarningCount || 0 }}</div>
              <el-progress :percentage="80" color="#f56c6c" :show-text="false"></el-progress>
            </div>
          </el-col>

          <el-col :xs="24" :sm="8">
            <div class="warning-item medium">
              <div class="warning-level">黄色预警</div>
              <div class="warning-count">{{ statistics.mediumWarningCount || 0 }}</div>
              <el-progress :percentage="50" color="#e6a23c" :show-text="false"></el-progress>
            </div>
          </el-col>

          <el-col :xs="24" :sm="8">
            <div class="warning-item low">
              <div class="warning-level">绿色预警</div>
              <div class="warning-count">{{ statistics.lowWarningCount || 0 }}</div>
              <el-progress :percentage="30" color="#67c23a" :show-text="false"></el-progress>
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
          <el-table-column prop="semester" label="学期" width="150" align="center"></el-table-column>
          <el-table-column prop="score" label="成绩" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getScoreTagType(row.score)">
                {{ row.score }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="credits" label="学分" width="100" align="center"></el-table-column>
          <el-table-column prop="trend" label="趋势" width="80" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.trend === 'up'" style="color: #f56c6c"><ArrowUp /></el-icon>
              <el-icon v-else-if="row.trend === 'down'" style="color: #67c23a"><ArrowDown /></el-icon>
              <span v-else style="color: #909399">→</span>
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
import { ref, onMounted, computed } from 'vue';
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { studentAPI } from '@/api';
import { getUserId } from '@/utils/userUtils';
import { toNumber, warningLevel } from '@/utils/score';

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

    // 每门课只保留最新一次已录入的成绩：重修记录与期末未录入的行都不参与统计
    const byCourse = new Map()
    scores.forEach(s => {
      const score = toNumber(s.scoreTotal)
      if (score === null) return
      const key = s.courseId ?? s.courseName
      const prev = byCourse.get(key)
      if (!prev || (toNumber(s.semester) || 0) >= (toNumber(prev.semester) || 0)) {
        byCourse.set(key, s)
      }
    })
    const courses = Array.from(byCourse.values()).map(s => ({
      courseName: s.courseName,
      scoreTotal: toNumber(s.scoreTotal),
      semester: s.semester,
    }))

    const bucket = (lo, hi) => courses.filter(c => c.scoreTotal >= lo && c.scoreTotal < hi).length
    const gpas = history.map(h => toNumber(h.studentGpa)).filter(v => v !== null)
    const currentGpa = toNumber(dash.gpa)

    // 学期趋势：GPA 来自历史绩点接口，均分由该学期已录入成绩求得
    const scoreBySemester = new Map()
    scores.forEach(s => {
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
      excellentCount: bucket(90, Infinity),
      goodCount: bucket(80, 90),
      // "及格"与"不及格"互斥且合计等于总门数；不及格取后端权威值而非前端数行
      failCount: dash.failedCoursesCount || 0,
      passCount: Math.max(0, courses.length - (dash.failedCoursesCount || 0)),
      highWarningCount: warnCount(3),
      mediumWarningCount: warnCount(2),
      lowWarningCount: warnCount(1),
      semesterTrend,
      courseScores: courses,
    }

    // 初始化图表
    setTimeout(() => {
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

// 初始化学期趋势柱状图
const initSemesterChart = () => {
  const chartContainer = document.getElementById('semesterChart');
  if (!chartContainer) return;

  // 检查是否有数据
  if (!statistics.value.semesterTrend || statistics.value.semesterTrend.length === 0) {
    chartContainer.innerHTML = '<div class="empty-state"><p>暂无成绩数据</p><small>图表将在有成绩数据时显示</small></div>';
    return;
  }

  const chart = echarts.init(chartContainer);
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
  window.addEventListener('resize', () => chart.resize());
};

// 初始化预警分布饼图
const initWarningPieChart = () => {
  const chartContainer = document.getElementById('warningChart');
  if (!chartContainer) return;

  const chart = echarts.init(chartContainer);
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
  window.addEventListener('resize', () => chart.resize());
};

// 刷新数据
const refreshData = () => {
  fetchStatistics();
  ElMessage.success('数据已刷新');
};

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
