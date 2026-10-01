<template>
  <div class="cd-page">
    <!-- 顶部问候 -->
    <div class="cd-topbar">
      <div>
        <h2 class="cd-greet">{{ greeting }}，{{ counselorName }}</h2>
        <p class="cd-date">{{ today }}</p>
      </div>
    </div>

    <!-- 指标卡 -->
    <div class="cd-metrics">
      <div class="cdm-card">
        <div class="cdm-icon-wrap cdm-blue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
        </div>
        <div class="cdm-body">
          <div class="cdm-val">{{ stats.studentCount }}</div>
          <div class="cdm-lbl">管理学生</div>
        </div>
      </div>
      <div class="cdm-card">
        <div class="cdm-icon-wrap cdm-amber">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        </div>
        <div class="cdm-body">
          <div class="cdm-val">{{ stats.warningCount }}</div>
          <div class="cdm-lbl">预警总数</div>
        </div>
      </div>
      <div class="cdm-card">
        <div class="cdm-icon-wrap cdm-red">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg>
        </div>
        <div class="cdm-body">
          <div class="cdm-val red">{{ stats.redWarnings }}</div>
          <div class="cdm-lbl">严重预警</div>
        </div>
      </div>
      <div class="cdm-card">
        <div class="cdm-icon-wrap cdm-teal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        </div>
        <div class="cdm-body">
          <div class="cdm-val">{{ stats.yellowWarnings + stats.blueWarnings }}</div>
          <div class="cdm-lbl">一般预警</div>
        </div>
      </div>
    </div>

    <!-- 双栏 -->
    <div class="cd-main">
      <!-- 左栏：班级概况 -->
      <div class="cd-left">
        <div class="cd-section">
          <div class="cds-title">班级概况</div>
          <div class="cds-count">共 {{ classList.length }} 个班级</div>
          <div v-if="classList.length" class="class-list">
            <div v-for="c in classList" :key="c.id" class="class-row">
              <div class="cls-avatar">{{ c.name?.charAt(0) || '班' }}</div>
              <div class="cls-info">
                <div class="cls-name">{{ c.name }}</div>
                <div class="cls-meta">{{ c.studentCount || 0 }} 名学生</div>
              </div>
              <div class="cls-stats">
                <span v-if="c.redCount" class="cls-badge red">严重 {{ c.redCount }}</span>
                <span v-if="c.yellowCount" class="cls-badge yellow">中度 {{ c.yellowCount }}</span>
                <span v-if="c.blueCount" class="cls-badge blue">轻度 {{ c.blueCount }}</span>
                <span v-if="!c.redCount && !c.yellowCount && !c.blueCount" class="cls-badge ok">正常</span>
              </div>
            </div>
          </div>
          <div v-else class="cds-empty">暂未分配班级</div>
        </div>

        <!-- 预警趋势 -->
        <div class="cd-section">
          <div class="cds-title">预警趋势 <span class="cds-hint">近6个月</span></div>
          <div ref="trendChart" class="trend-chart"></div>
        </div>
      </div>

      <!-- 右栏 -->
      <div class="cd-right">
        <!-- 最近预警 -->
        <div class="cd-section">
          <div class="cds-title">最近预警</div>
          <div v-if="recentWarnings.length" class="warn-list">
            <div v-for="w in recentWarnings" :key="w.id" class="warn-row">
              <div :class="['warn-dot', w.warningLevel >= 3 ? 'red' : w.warningLevel >= 2 ? 'blue' : 'blue']"></div>
              <div class="warn-body">
                <span class="warn-name">{{ w.studentName }}</span>
                <span :class="['warn-tag', w.warningLevel >= 3 ? 'red' : w.warningLevel >= 2 ? 'blue' : 'blue']">
                  {{ w.warningLevel >= 3 ? '严重' : w.warningLevel >= 2 ? '中度' : '轻度' }}
                </span>
              </div>
              <span class="warn-time">{{ formatTime(w.createdAt) }}</span>
            </div>
          </div>
          <div v-else class="cds-empty">暂无预警记录</div>
        </div>

        <!-- 快速操作 -->
        <div class="cd-section">
          <div class="cds-title">快速操作</div>
          <div class="quick-grid">
            <router-link to="/counselor/students" class="qg-item">
              <div class="qg-icon qg1"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
              <span>学生管理</span>
            </router-link>
            <router-link to="/counselor/warnings" class="qg-item">
              <div class="qg-icon qg2"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/></svg></div>
              <span>预警管理</span>
            </router-link>
            <router-link to="/counselor/class-management" class="qg-item">
              <div class="qg-icon qg3"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/></svg></div>
              <span>班级管理</span>
            </router-link>
            <router-link to="/counselor/scores" class="qg-item">
              <div class="qg-icon qg4"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg></div>
              <span>成绩跟踪</span>
            </router-link>
            <router-link to="/counselor/assistance" class="qg-item">
              <div class="qg-icon qg5"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg></div>
              <span>帮扶计划</span>
            </router-link>
            <router-link to="/counselor/notifications" class="qg-item">
              <div class="qg-icon qg6"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></div>
              <span>批量通知</span>
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { counselorAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { buildWarningTrend, warningLevelColor } from '@/utils/score'

const trendChart = ref(null)
const stats = reactive({ studentCount: 0, warningCount: 0, redWarnings: 0, yellowWarnings: 0, blueWarnings: 0 })
const classList = ref([])
const recentWarnings = ref([])
// 预警趋势的原始行（`[{month, warning_level, count}]`），由 renderChart 经
// `buildWarningTrend` 聚合。2026-10-01 第二十二轮新增：这张图此前**不接任何数据**。
const trendRows = ref([])
// 优先取登录时写入的 'name'（Login.vue 存的是真名）。
// 不能只读 'userName'：它是 CounselorLayout 在 onMounted 里才写入的，
// 而子组件的 setup 早于父组件的 onMounted 执行，首屏永远读不到，会退化成「辅导员」。
const pickName = (v) => (v && !/^\d+$/.test(v) ? v : '')
const counselorName = ref(
  pickName(localStorage.getItem('name')) ||
  pickName(localStorage.getItem('userName')) ||
  '辅导员'
)

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' })
const greeting = (() => { const h = new Date().getHours(); return h < 9 ? '早上好' : h < 12 ? '上午好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好' })()

onMounted(async () => { await loadDashboard() })

const loadDashboard = async () => {
  const uid = getUserId(); if (!uid) return
  try {
    const res = await counselorAPI.getDashboard(uid)
    // 拦截器已解包，res 直接是 data Map
    const data = (res && !res.code) ? res : (res && res.data ? res.data : {})
    // 接口已带回辅导员档案，直接用它兜正姓名，不依赖 localStorage 的写入时机
    const apiName = data.counselor && data.counselor.name
    if (apiName) counselorName.value = apiName
    stats.studentCount = data.studentCount || 0
    stats.warningCount = data.warningCount || 0
    stats.redWarnings = data.redWarnings || 0
    stats.yellowWarnings = data.yellowWarnings || 0
    stats.blueWarnings = data.blueWarnings || 0
    // 直接使用返回数据
    const classes = data.classes || []
    const warnByClass = data.warnByClass || {}
    classList.value = classes.map(c => ({
      id: c.id || c.classId,
      name: c.name || c.className,
      studentCount: c.studentCount || 0,
      redCount: (warnByClass[c.id]?.red) || 0,
      yellowCount: (warnByClass[c.id]?.yellow) || 0,
      blueCount: (warnByClass[c.id]?.blue) || 0
    }))
    recentWarnings.value = (data.recentWarnings || []).slice(0, 5)
  } catch (e) { console.error('加载仪表盘失败:', e) }

  // 2026-10-01 第二十二轮: 趋势图的原始数据在**另一个接口**上（/counselors/analytics/warning-trend），
  // 本页此前从来没有调过它 —— 详见下面 renderChart 的注释。单独 try：趋势取不到只该让那张图
  // 显示「暂无预警数据」，不该把整页拖垮。
  try {
    const tr = await counselorAPI.getWarningTrend(uid)
    trendRows.value = Array.isArray(tr) ? tr : (tr?.data ?? tr?.list ?? [])
  } catch (e) { console.error('加载预警趋势失败:', e) }

  await nextTick()
  renderChart()
}

const formatTime = (dt) => {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

let chartInstance = null
function renderChart() {
  if (!trendChart.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(trendChart.value)

  // 2026-10-01 第二十二轮修: 原来这里把三条序列写成**字面量数组** ——
  //   data: [0,0,0,0, red, red] / [0,0,0,0, yellow, yellow] / [0,0,0, blue, blue, blue]
  // 只有 x 轴的月份是真算出来的，所以它**看起来**像一张趋势图，实际值全是编的；
  // 而且"最近两个月永远同值"是那对字面量必然的产物。
  // 实测（辅导员 53，库内 4 条预警全在 2026-09，等级 2×3 + 1×1）：假图在 8 月凭空多 1 条轻度、
  // 10 月凭空多 3 条中度 + 1 条轻度 —— 而**同一屏**上方 KPI 卡的 0/3/1 是对的，自相矛盾。
  // 真数据源一直存在（/counselors/analytics/warning-trend），数据分析页就在用它。
  const { months, severe, medium, mild } = buildWarningTrend(trendRows.value)
  if (!months.length) {
    chartInstance.setOption({
      title: {
        text: '暂无预警数据', left: 'center', top: 'center',
        textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 'normal' }
      }
    })
    return
  }
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['严重', '中度', '轻度'], bottom: 0, textStyle: { fontSize: 11 } },
    grid: { left: 10, right: 20, top: 10, bottom: 35 },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 10, color: '#94a3b8' }, axisLine: { lineStyle: { color: '#e2e8f0' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed', color: '#f1f5f9' } }, axisLabel: { fontSize: 10, color: '#94a3b8' } },
    series: [
      { name: '严重', type: 'line', smooth: true, data: severe, itemStyle: { color: warningLevelColor(3) }, lineStyle: { color: warningLevelColor(3), width: 2 }, areaStyle: { color: 'rgba(239,68,68,0.06)' } },
      { name: '中度', type: 'line', smooth: true, data: medium, itemStyle: { color: warningLevelColor(2) }, lineStyle: { color: warningLevelColor(2), width: 2 }, areaStyle: { color: 'rgba(245,158,11,0.06)' } },
      { name: '轻度', type: 'line', smooth: true, data: mild, itemStyle: { color: warningLevelColor(1) }, lineStyle: { color: warningLevelColor(1), width: 2 }, areaStyle: { color: 'rgba(59,130,246,0.06)' } }
    ]
  })
}
</script>

<style scoped>
.cd-page { padding: 20px 24px 30px; min-height: 100vh; background: #f5f7fb; }

/* Topbar */
.cd-topbar { margin-bottom: 20px; }
.cd-greet { margin: 0; font-size: 20px; font-weight: 700; color: #1e293b; }
.cd-date { margin: 2px 0 0; font-size: 13px; color: #94a3b8; }

/* Metrics */
.cd-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.cdm-card { background: #fff; border-radius: 14px; padding: 20px; display: flex; align-items: center; gap: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.04); transition: transform .15s, box-shadow .15s; }
.cdm-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.cdm-icon-wrap { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.cdm-blue { background: #eff6ff; color: #2563eb; } .cdm-amber { background: #eff6ff; color: #2563eb; }
.cdm-red { background: #fef2f2; color: #ef4444; } .cdm-teal { background: #f0fdfa; color: #0d9488; }
.cdm-val { font-size: 26px; font-weight: 700; color: #1e293b; line-height: 1.2; }
.cdm-val.red { color: #ef4444; }
.cdm-lbl { font-size: 12px; color: #94a3b8; }

/* Main */
.cd-main { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
@media (max-width: 1000px) { .cd-metrics { grid-template-columns: repeat(2, 1fr); } .cd-main { grid-template-columns: 1fr; } }

/* Section */
.cd-section { background: #fff; border-radius: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); padding: 20px 22px; margin-bottom: 18px; }
.cds-title { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 12px; }
.cds-hint { font-size: 11px; color: #94a3b8; font-weight: 400; margin-left: 6px; }
.cds-count { font-size: 12px; color: #94a3b8; margin-bottom: 10px; margin-top: -8px; }
.cds-empty { text-align: center; padding: 28px 0; font-size: 13px; color: #94a3b8; }

/* Class List */
.class-row { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f8fafc; }
.class-row:last-child { border-bottom: none; }
.cls-avatar { width: 40px; height: 40px; border-radius: 10px; background: linear-gradient(135deg, #eff6ff, #dbeafe); color: #2563eb; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; flex-shrink: 0; }
.cls-info { flex: 1; min-width: 0; }
.cls-name { font-size: 14px; font-weight: 500; color: #334155; }
.cls-meta { font-size: 12px; color: #94a3b8; }
.cls-stats { display: flex; gap: 4px; flex-shrink: 0; }
.cls-badge { font-size: 11px; padding: 2px 7px; border-radius: 6px; font-weight: 500; }
.cls-badge.red { background: #fef2f2; color: #dc2626; } .cls-badge.yellow { background: #eff6ff; color: #2563eb; }
.cls-badge.blue { background: #eff6ff; color: #2563eb; } .cls-badge.ok { background: #f0fdf4; color: #16a34a; }

/* Warn List */
.warn-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f8fafc; }
.warn-row:last-child { border-bottom: none; }
.warn-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.warn-dot.red { background: #ef4444; } .warn-dot.yellow { background: #3b82f6; } .warn-dot.blue { background: #3b82f6; }
.warn-body { flex: 1; display: flex; align-items: center; gap: 8px; }
.warn-name { font-size: 13px; color: #334155; }
.warn-tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; }
.warn-tag.red { background: #fef2f2; color: #dc2626; } .warn-tag.yellow { background: #eff6ff; color: #2563eb; } .warn-tag.blue { background: #eff6ff; color: #2563eb; }
.warn-time { font-size: 11px; color: #94a3b8; flex-shrink: 0; }

/* Quick Grid */
.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.qg-item { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 14px 8px; border-radius: 10px; text-decoration: none; background: #f8fafc; transition: all .15s; }
.qg-item:hover { background: #eff6ff; transform: translateY(-2px); }
.qg-icon { width: 36px; height: 36px; border-radius: 9px; display: flex; align-items: center; justify-content: center; }
.qg1 { background: #eff6ff; color: #2563eb; } .qg2 { background: #fef2f2; color: #ef4444; }
.qg3 { background: #eff6ff; color: #2563eb; } .qg4 { background: #ecfdf5; color: #16a34a; }
.qg5 { background: #f5f3ff; color: #7c3aed; } .qg6 { background: #cffafe; color: #0891b2; }
.qg-item span { font-size: 12px; color: #475569; font-weight: 500; }

/* Trend Chart */
.trend-chart { height: 240px; }
</style>
