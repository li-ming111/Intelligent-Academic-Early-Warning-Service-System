<template>
  <div class="ast-page">

    <!-- 头部 -->
    <div class="ast-head">
      <div class="ash-left">
        <div class="ash-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
        </div>
        <div>
          <h2>数据分析</h2>
          <p>全校数据统计分析与可视化</p>
        </div>
      </div>
      <div class="ash-right">
        <button class="ash-btn" @click="refresh">刷新</button>
        <button class="ash-btn primary" @click="doExport" :disabled="exporting">{{ exporting ? '导出中...' : '导出报表' }}</button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="ast-cards">
      <div class="asc-card" v-for="c in cards" :key="c.key">
        <div class="asc-icon" :class="'ic-'+c.color">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20" v-html="c.icon"></svg>
        </div>
        <div class="asc-val">{{ c.value }}</div>
        <div class="asc-lbl">{{ c.label }}</div>
      </div>
    </div>

    <!-- ECharts 图表 -->
    <div class="ast-charts">
      <!-- 预警分布饼图 -->
      <div class="ast-chart-card">
        <div class="acc-head">预警等级分布</div>
        <div ref="pieRef" class="acc-box"></div>
      </div>

      <!-- 用户角色分布柱状图 -->
      <div class="ast-chart-card">
        <div class="acc-head">系统用户角色分布</div>
        <div ref="barRef" class="acc-box"></div>
      </div>
    </div>

    <!-- 数据明细表格 -->
    <div class="ast-charts">
      <!-- 学院统计 -->
      <div class="ast-chart-card">
        <div class="acc-head">学院数据明细</div>
        <el-table :data="collegeData" stripe size="small" style="width:100%">
          <el-table-column prop="name" label="学院" min-width="140" />
          <el-table-column prop="studentCount" label="学生数" width="100" align="center" />
          <el-table-column prop="teacherCount" label="教师数" width="100" align="center" />
          <!-- 「课程数」这一列已删(2026-10-01 第十九轮): courses 表没有任何到学院的链路,
               按"本学院教师任教"间接算会得到 学院1=10/其余=0 的退化结果, 与其编一个数不如不显示。 -->
          <el-table-column prop="classCount" label="班级数" width="100" align="center" />
        </el-table>
      </div>

      <!-- 专业统计 -->
      <div class="ast-chart-card">
        <div class="acc-head">专业数据明细</div>
        <el-table :data="majorData" stripe size="small" style="width:100%" max-height="320">
          <el-table-column prop="name" label="专业" min-width="160" />
          <el-table-column prop="collegeName" label="所属学院" min-width="120" />
          <el-table-column prop="studentCount" label="学生数" width="100" align="center" />
        </el-table>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { adminAPI } from '@/api/index'
// 2026-10-02（W4）：导出要用这两个 —— 直接存 blob 会把"后端回了 JSON 错误体"
// 或"零字节文件"当成导出成功。
import { saveBlobAsFile, describeDownloadError } from '@/utils/download'

const pieRef = ref(null)
const barRef = ref(null)
const exporting = ref(false)
const stats = reactive({ totalStudents: 0, totalTeachers: 0, totalCounselors: 0, totalCollegeAdmins: 0, totalCourses: 0, totalMajors: 0, totalColleges: 0, totalUsers: 0, redWarnings: 0, yellowWarnings: 0, lowWarnings: 0 })
const collegeData = ref([])
const majorData = ref([])

const cards = computed(() => [
  { key: 'students', icon: '<path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/>', label: '学生总数', value: stats.totalStudents, color: 'purple' },
  { key: 'teachers', icon: '<rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>', label: '教师总数', value: stats.totalTeachers, color: 'blue' },
  { key: 'counselors', icon: '<path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/>', label: '辅导员', value: stats.totalCounselors, color: 'emerald' },
  { key: 'courses', icon: '<path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/>', label: '课程总数', value: stats.totalCourses, color: 'green' },
  { key: 'colleges', icon: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>', label: '学院数量', value: stats.totalColleges, color: 'amber' },
  { key: 'majors', icon: '<path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>', label: '专业总数', value: stats.totalMajors, color: 'cyan' },
  { key: 'users', icon: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>', label: '系统用户', value: stats.totalUsers, color: 'teal' },
  { key: 'warnings', icon: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>', label: '预警总数', value: (stats.redWarnings||0)+(stats.yellowWarnings||0)+(stats.lowWarnings||0), color: 'red' },
])

import { computed } from 'vue'

onMounted(async () => {
  await loadData()
  await nextTick(renderCharts)
})

async function loadData() {
  try {
    const r = await adminAPI.getStatistics()
    if (r && typeof r === 'object') {
      stats.totalStudents = r.totalStudents || 0
      stats.totalTeachers = r.totalTeachers || 0
      stats.totalCounselors = r.totalCounselors || 0
      stats.totalCollegeAdmins = r.totalCollegeAdmins || 0
      stats.totalCourses = r.totalCourses || 0
      stats.totalColleges = r.totalColleges || 0
      stats.totalMajors = r.totalMajors || 0
      stats.totalUsers = r.totalUsers || 0
      stats.redWarnings = r.redWarnings || 0
      stats.yellowWarnings = r.yellowWarnings || 0
      stats.lowWarnings = r.lowWarnings || 0
    }
  } catch (e) { console.error(e) }
  // 学院 / 专业数据
  try {
    const cls = await adminAPI.getColleges()
    const arr = Array.isArray(cls) ? cls : (cls?.data || [])
    collegeData.value = arr.map((c, i) => ({
      name: c.name || c.collegeName || '学院' + i,
      studentCount: c.studentCount || '--',
      teacherCount: c.teacherCount || '--',
      classCount: c.classCount || '--'
    }))
  } catch (e) {}
  try {
    const mjs = await adminAPI.getMajors()
    const arr = Array.isArray(mjs) ? mjs : (mjs?.data || [])
    majorData.value = arr.map((m, i) => ({
      name: m.name || m.majorName || '专业' + i,
      collegeName: m.collegeName || '--',
      studentCount: m.studentCount || '--'
    }))
  } catch (e) {}
}

function renderCharts() {
  if (pieRef.value) {
    const pie = echarts.getInstanceByDom(pieRef.value) || echarts.init(pieRef.value)
    pie.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie', radius: ['50%', '72%'], center: ['50%', '45%'],
        data: [
          { name: '严重预警', value: stats.redWarnings || 0, itemStyle: { color: '#ef4444' } },
          { name: '中度预警', value: stats.yellowWarnings || 0, itemStyle: { color: '#f59e0b' } },
          { name: '轻度预警', value: stats.lowWarnings || 0, itemStyle: { color: '#3b82f6' } }
        ].filter(d => d.value > 0),
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } }
      }]
    })
  }

  if (barRef.value) {
    const bar = echarts.getInstanceByDom(barRef.value) || echarts.init(barRef.value)
    bar.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['学生', '教师', '辅导员', '学院管理员'], axisTick: { show: false } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'bar', barMaxWidth: 40,
        // 2026-10-09（W32）：原来第四根柱是「管理员」且**写死成字面量 1**（当时的口径是
        // "只有一个系统管理员"），且完全没画 role 5 的学院管理员。现在第四根改为
        // 「学院管理员」，取后端新加的 totalCollegeAdmins（真实 COUNT，见
        // AdminController.getStatistics）。系统管理员本身没进这张图：后端从来没有它的
        // 计数，之前那个 1 是写死的常量 —— 与其留一个假数字，不如画一个真数字。
        data: [stats.totalStudents, stats.totalTeachers, stats.totalCounselors, stats.totalCollegeAdmins],
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#7c3aed' }, { offset: 1, color: '#a78bfa' }])
        }
      }]
    })
  }
}

async function refresh() {
  await loadData()
  await nextTick(renderCharts)
  ElMessage.success('已刷新')
}

async function doExport() {
  exporting.value = true
  try {
    // 2026-10-02（W4）：原来这里 `await adminAPI.exportStatistics()` 之后**把返回值丢掉**，
    // 直接弹"导出成功" —— 文件从来没被保存过（既没 createObjectURL 也没有 <a download>）。
    // 这是本工程记录过的"点了就说成功"那一族，只不过它连响应体都没用上。
    // 现在走共用的 saveBlobAsFile：它会把"错误 JSON"和"零字节"都判成失败。
    const blob = await adminAPI.exportStatistics()
    await saveBlobAsFile(blob, `统计分析_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(await describeDownloadError(e))
  }
  exporting.value = false
}
</script>

<style scoped>
.ast-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }

/* 头部 */
.ast-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.ash-left { display: flex; align-items: center; gap: 14px; }
.ash-icon { width: 48px; height: 48px; border-radius: 12px; background: #f5f3ff; color: #7c3aed; display: flex; align-items: center; justify-content: center; }
.ash-left h2 { margin: 0; font-size: 20px; color: #18181b; font-weight: 700; }
.ash-left p { margin: 2px 0 0; font-size: 13px; color: #a1a1aa; }
.ash-right { display: flex; gap: 10px; }
.ash-btn { padding: 8px 18px; border-radius: 8px; border: 1px solid #e4e4e7; background: #fff; color: #52525b; font-size: 13px; cursor: pointer; transition: all .2s; }
.ash-btn:hover { border-color: #d4d4d8; background: #fafafa; }
.ash-btn.primary { background: #7c3aed; color: #fff; border-color: #7c3aed; }
.ash-btn.primary:hover:not(:disabled) { background: #6d28d9; }
.ash-btn:disabled { opacity: .5; cursor: default; }

/* 卡片 */
.ast-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 24px; }
.asc-card { background: #fff; border-radius: 14px; padding: 20px 18px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.04); transition: all .25s; }
.asc-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,.08); }
.asc-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; margin: 0 auto 10px; }
.ic-purple { background: #f5f3ff; color: #7c3aed; } .ic-blue { background: #eff6ff; color: #3b82f6; }
.ic-green { background: #ecfdf5; color: #16a34a; } .ic-amber { background: #fffbeb; color: #d97706; }
.ic-red { background: #fef2f2; color: #ef4444; } .ic-teal { background: #f0fdfa; color: #0d9488; }
.ic-emerald { background: #d1fae5; color: #059669; }
.asc-val { font-size: 22px; font-weight: 700; color: #18181b; }
.asc-lbl { font-size: 12px; color: #a1a1aa; margin-top: 4px; }

/* 图表 */
.ast-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
.ast-chart-card { background: #fff; border-radius: 16px; padding: 20px 24px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.acc-head { font-size: 15px; font-weight: 600; color: #18181b; margin-bottom: 12px; }
.acc-box { height: 300px; }

@media (max-width: 1400px) { .ast-cards { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 1000px) { .ast-cards { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .ast-cards { grid-template-columns: repeat(2, 1fr); } .ast-charts { grid-template-columns: 1fr; } .ast-head { flex-direction: column; align-items: flex-start; gap: 12px; } }
</style>
