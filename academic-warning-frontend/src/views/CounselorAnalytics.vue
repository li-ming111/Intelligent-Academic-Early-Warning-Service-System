<template>
  <div class="ca-page">
    <!-- 指标卡 -->
    <div class="ca-metrics">
      <div class="cam-card">
        <div class="cam-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg></div>
        <div class="cam-val red">{{ stats.creditInsufficientRate }}<span class="cam-unit">%</span></div>
        <div class="cam-lbl">学分不足率 / {{ stats.totalStudents }}人</div>
      </div>
      <div class="cam-card">
        <div class="cam-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
        <div class="cam-val">{{ stats.warningHandlingEfficiency }}<span class="cam-unit">天</span></div>
        <div class="cam-lbl">预警平均处理天数</div>
      </div>
      <div class="cam-card">
        <div class="cam-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg></div>
        <div class="cam-val green">{{ stats.assistanceCompletionRate }}<span class="cam-unit">%</span></div>
        <div class="cam-lbl">帮扶完成率 / {{ stats.completedPlans }}/{{ stats.totalPlans }}</div>
      </div>
      <div class="cam-card">
        <div class="cam-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg></div>
        <div class="cam-val blue">{{ stats.creditAchievementRate }}<span class="cam-unit">%</span></div>
        <div class="cam-lbl">平均学分达标 / {{ stats.classCount }}班</div>
      </div>
    </div>

    <!-- 双栏 -->
    <div class="ca-main">
      <div class="ca-left">
        <div class="ca-panel">
          <div class="cap-head"><span class="cap-title">预警级别分布</span></div>
          <div ref="warningChart" style="height:300px"></div>
        </div>
        <div class="ca-panel">
          <div class="cap-head"><span class="cap-title">预警处理效率</span><span class="cap-hint">平均天数</span></div>
          <div ref="efficiencyChart" style="height:200px"></div>
        </div>
      </div>
      <div class="ca-right">
        <div class="ca-panel">
          <div class="cap-head"><span class="cap-title">班级学分达标排名</span></div>
          <div v-if="classRanking.length">
            <div v-for="(r, i) in classRanking.slice(0,8)" :key="i" class="car-item">
              <span class="cari-rank" :class="'r'+Math.min(i+1,4)">{{ i + 1 }}</span>
              <div class="cari-body">
                <div class="cari-top">
                  <span class="cari-name">{{ r.className }}</span>
                  <span class="cari-val" :class="rateClass(r.creditRate)">{{ r.creditRate }}%</span>
                </div>
                <div class="cari-bar"><div class="carib-fill" :style="{width:r.creditRate+'%',background:r.creditRate>=80?'#16a34a':r.creditRate>=60?'#3b82f6':'#ef4444'}"></div></div>
                <!-- 2026-10-01 第二十一轮删：这里原来还跟着一个 `±improvementRate%`
                     （绿↑/红↓）。`/counselors/credit-achievement-ranking` 返回的行里
                     根本没有 improvementRate 这个 key（它来自 getClassScoreStatsByCounselorId，
                     列是 class_id/name/student_count/graded_student_count/…/achievement_rate/
                     score_count/avg_score），`item.improvementRate||0` 于是恒 0 ⇒
                     每个班都显示一个**红色的 "0%"**，看着像"全校零改善"。
                     班级达标率的环比要另立口径（比哪两个学期？上学期没数据算不算？），
                     没定下来之前不编数，先只显示真实的人数。 -->
                <div class="cari-meta">{{ r.studentCount }}人</div>
              </div>
            </div>
          </div>
          <div v-else class="cap-empty">暂无班级数据</div>
        </div>
      </div>
    </div>

    <!-- 预警趋势 - 全宽 -->
    <div class="ca-panel" style="margin: 0 24px 24px;">
      <div class="cap-head"><span class="cap-title">近6个月预警趋势</span></div>
      <div ref="trendChart" style="height:320px"></div>
    </div>

    <!-- AI 预警趋势分析（W26）：`aiAPI.analyzeWarningTrend` 此前全仓零调用。
         端点是**纯 AI**（后端直接问DeepSeek），没配 AI_API_KEY 时会拿占位串
         冒充成功 —— 闸在 utils/ai.js，命中就换成一句实话，不当作分析结果渲染。 -->
    <div class="ca-panel" style="margin: 0 24px 24px;">
      <div class="cap-head"><span class="cap-title">AI 预警趋势分析</span></div>
      <div style="padding: 0 16px 16px;">
        <AiInsight
          title="AI 趋势解读"
          :load="aiWarningTrend"
          hint="把本人管辖范围内的预警趋势交给 AI 解读（需要后端配置 AI_API_KEY）"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import { counselorAPI, aiAPI } from '@/api/index'
import AiInsight from '@/components/AiInsight.vue'
import { getUserId } from '@/utils/userUtils'
import { buildWarningTrend, warningLevelColor, warningLevelText } from '@/utils/score'

const warningChart = ref(null), efficiencyChart = ref(null), trendChart = ref(null)
const classRanking = ref([])
const stats = reactive({ creditInsufficientRate:0, totalStudents:0, warningHandlingEfficiency:0, assistanceCompletionRate:0, totalPlans:0, completedPlans:0, creditAchievementRate:0, classCount:0 })

let wc=null, ec=null, tc=null
const getCid = () => localStorage.getItem('counselorId') || getUserId()

/** AI 预警趋势分析（W26）。端点收的是**辅导员 id** —— 与页面其余取数同一个口径。 */
const aiWarningTrend = () => {
  const cid = getCid()
  if (!cid) throw new Error('取不到当前辅导员 id，无法做趋势分析')
  return aiAPI.analyzeWarningTrend(cid)
}

onMounted(async () => { await loadData() })
onUnmounted(() => { wc?.dispose(); ec?.dispose(); tc?.dispose() })

/** 达标率配色，与下面那条进度条同一套阈值（≥80 绿 / ≥60 蓝 / 其余红）。 */
function rateClass(rate) {
  const n = Number(rate) || 0
  if (n >= 80) return 'up'
  if (n >= 60) return 'mid'
  return 'down'
}

async function loadData() {
  try {
    const cid = getCid(); if (!cid) return
    const [ir, dr, er, rr, tr, cr] = await Promise.all([
      counselorAPI.getCreditInsufficientRate(cid),
      counselorAPI.getWarningLevelDistribution(cid),
      counselorAPI.getWarningHandlingEfficiency(cid),
      counselorAPI.getClassCreditAchievementRanking(cid),
      counselorAPI.getWarningTrend(cid),
      counselorAPI.getAssistancePlanCompletionRate(cid)
    ])
    // 拦截器已解包，res 即为 data 对象
    const parse = (r) => (r && !r.code) ? r : (r?.data || {})
    const irData = parse(ir); const erData = parse(er); const crData = parse(cr)
    stats.creditInsufficientRate = Math.round(irData.insufficientRate || 0)
    stats.totalStudents = irData.totalStudents || 0
    stats.warningHandlingEfficiency = Math.round(erData.avg_handle_days || 0)
    stats.assistanceCompletionRate = Math.round(crData.completionRate || 0)
    stats.totalPlans = crData.totalPlans || 0
    stats.completedPlans = crData.completedPlans || 0
    const rd = parse(rr)
    const rdList = Array.isArray(rd) ? rd : (rd.list || rd.ranking || [])
    classRanking.value = rdList.map((item, i) => ({
      ranking: i+1, className: item.className||item.name||'未知', studentCount: item.studentCount||item.score_count||0,
      // `||` 会让 0 门成绩的班级回落到 avg_score 冒充达标率（0 是假值）。用 ?? 区分
      // "字段是 0"和"字段不存在"。后端这一列就是 achievement_rate，没有别名。
      creditRate: Math.min(100, Math.round(parseFloat(item.achievementRate ?? item.avg_score ?? 0)))
    }))
    stats.classCount = classRanking.value.length
    // 同上面的 `??`：合法 0 值不该回落到 avg_score 冒充达标率。
    if (rdList.length) { const t = rdList.reduce((s,it)=>s+Math.min(100,parseFloat(it.achievementRate ?? it.avg_score ?? 0)),0); stats.creditAchievementRate=Math.round(t/rdList.length) }
    setTimeout(() => { renderWarn(parse(dr)); renderEfficiency(stats.warningHandlingEfficiency); renderTrend(parse(tr)) }, 200)
  } catch(e) { console.error(e); setTimeout(() => { renderWarn([]); renderEfficiency(0); renderTrend([]) }, 200) }
}

function renderWarn(data) {
  if (!warningChart.value) return; wc?.dispose(); wc = echarts.init(warningChart.value)
  // 2026-10-01 第二十二轮修: 原表是
  //   lm = {1:'红色', 2:'黄色', 3:'蓝色'}, cm = {1:'#ef4444', 2:'#3b82f6', 3:'#3b82f6'}
  // 两处都错 —— ① 1 和 3 对调了：全仓口径是 1=轻度、2=中度、≥3=严重（见 utils/score.js 的
  // warningLevelText，以及 invariants 的 H 组「1-2 门→1 级、3-5 门→2 级、>5 门→3 级」）；
  // ② `2:'黄色'` 的颜色写成 #3b82f6（蓝），`3:'蓝色'` 也是 #3b82f6 ⇒ 中度与严重同色。
  // 实测辅导员 53 的分布是 level2×3 + level1×1，原图显示「红色 1 / 黄色 3」；真相是
  // 「轻度 1 / 中度 3 / 严重 0」—— 图把**最轻的那条说成了最重的**。色值与文案统一改走
  // utils/score.js，别再手写第二张等级表。
  const dt = Array.isArray(data) ? data : (data?.list || data?.data || [])
  const cd = dt.map(i => {
    const lv = i.warning_level ?? i.level
    return { value: i.count || 0, name: warningLevelText(lv), itemStyle: { color: warningLevelColor(lv) } }
  })
  if (!cd.length) cd.push({ value:1, name:'暂无数据', itemStyle:{color:'#e2e8f0'} })
  wc.setOption({ tooltip:{trigger:'item'}, series:[{ type:'pie', radius:['45%','68%'], center:['50%','52%'], data:cd, label:{show:true,fontSize:12}, emphasis:{label:{fontSize:15,fontWeight:'bold'} }}] })
}

// 补实现。此前模板里有 efficiencyChart 容器、有 ec 实例变量、onUnmounted 里也 dispose 了它，
// 但**这个渲染函数从来没有被定义过**——调用点在 loadData 的 setTimeout 里，
// 抛 ReferenceError 之后同一行的 renderTrend 也不会再执行，趋势图跟着一起消失。
// 接口 /analytics/handling-efficiency 给的是 avg_handle_days 标量，用仪表盘呈现。
function renderEfficiency(days) {
  if (!efficiencyChart.value) return; ec?.dispose(); ec = echarts.init(efficiencyChart.value)
  const d = Number(days) || 0
  // 量程只影响观感、不影响结论：14 天打底，数值更大时按周向上取整，免得指针顶到刻度边。
  // 这里**有意不做"绿/黄/红"分档**——全系统没有任何关于处置时限的规则
  // (warning_rule 管的是成绩阈值)，凭空定一条"3 天良好、7 天超标"在答辩时会被追问依据。
  const max = Math.max(14, Math.ceil(d / 7) * 7)
  ec.setOption({
    tooltip: { formatter: () => `平均 ${d} 天` },
    series: [{
      type: 'gauge', min: 0, max, startAngle: 200, endAngle: -20,
      center: ['50%', '62%'], radius: '92%',
      progress: { show: true, width: 14, itemStyle: { color: '#3b82f6' } },
      axisLine: { lineStyle: { width: 14, color: [[1, '#e2e8f0']] } },
      pointer: { width: 4, length: '58%', itemStyle: { color: '#1e3a5f' } },
      axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false },
      detail: { offsetCenter: [0, '36%'], fontSize: 22, fontWeight: 'bold', color: '#1e3a5f', formatter: (v) => `${v} 天` },
      data: [{ value: d }]
    }]
  })
}

function renderTrend(data) {
  if (!trendChart.value) return; tc?.dispose(); tc = echarts.init(trendChart.value)
  // 2026-10-01 第二十二轮修: 原来这里是
  //   l===1 ? map[m].r += c : l===2 ? map[m].y += c : map[m].b += c
  // —— 又把 1 累加进"红色"、≥3 累加进"蓝色"，和上面环形图同一个 1↔3 对调。
  // 改用 utils/score.js 的共用聚合，与辅导员首页同一份实现（那边此前压根没调接口）。
  const { months, severe, medium, mild } = buildWarningTrend(data)
  tc.setOption({
    tooltip:{trigger:'axis'}, legend:{data:['严重','中度','轻度'],bottom:0}, grid:{left:30,right:10,top:10,bottom:40},
    xAxis:{type:'category',data:months.length?months:['暂无'],axisLabel:{fontSize:10}}, yAxis:{type:'value',min:0,splitLine:{lineStyle:{type:'dashed',color:'#f1f5f9'}}},
    series:[
      {name:'严重',type:'line',smooth:true,data:months.length?severe:[0],itemStyle:{color:warningLevelColor(3)},lineStyle:{color:warningLevelColor(3),width:2},areaStyle:{color:'rgba(239,68,68,.1)'}},
      {name:'中度',type:'line',smooth:true,data:months.length?medium:[0],itemStyle:{color:warningLevelColor(2)},lineStyle:{color:warningLevelColor(2),width:2},areaStyle:{color:'rgba(245,158,11,.1)'}},
      {name:'轻度',type:'line',smooth:true,data:months.length?mild:[0],itemStyle:{color:warningLevelColor(1)},lineStyle:{color:warningLevelColor(1),width:2},areaStyle:{color:'rgba(59,130,246,.1)'}}
    ]
  })
}
</script>

<style scoped>
.ca-page { padding: 0 0 24px; min-height: 100vh; background: #f0f4ff; }

.ca-metrics { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; padding: 20px 24px; }
.cam-card { background: #fff; border-radius: 14px; padding: 18px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.04); transition: transform .2s; }
.cam-card:hover { transform: translateY(-2px); }
.cam-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; margin: 0 auto 8px; }
.cam-icon svg { width: 18px; height: 18px; }
.ic-red { background: #fef2f2; color: #ef4444; } .ic-amber { background: #eff6ff; color: #2563eb; }
.ic-green { background: #ecfdf5; color: #16a34a; } .ic-blue { background: #eff6ff; color: #2563eb; }
.cam-val { font-size: 26px; font-weight: 700; color: #1e293b; }
.cam-val.red { color: #ef4444; } .cam-val.green { color: #16a34a; } .cam-val.blue { color: #2563eb; }
.cam-unit { font-size: 14px; color: #94a3b8; margin-left: 2px; }
.cam-lbl { font-size: 12px; color: #94a3b8; margin-top: 2px; }

.ca-main { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; padding: 0 24px 24px; }
.ca-panel { background: #fff; border-radius: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; }
.cap-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 22px; border-bottom: 1px solid #f1f5f9; }
.cap-title { font-size: 14px; font-weight: 600; color: #1e293b; } .cap-hint { font-size: 12px; color: #94a3b8; }
.cap-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }

.car-item { display: flex; align-items: center; gap: 12px; padding: 12px 20px; border-bottom: 1px solid #f1f5f9; }
.car-item:last-child { border-bottom: none; }
.cari-rank { width: 28px; height: 28px; border-radius: 7px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; flex-shrink: 0; }
.cari-rank.r1 { background: #fef2f2; color: #ef4444; } .cari-rank.r2 { background: #eff6ff; color: #2563eb; }
.cari-rank.r3 { background: #eff6ff; color: #2563eb; } .cari-rank.r4 { background: #f1f5f9; color: #64748b; }
.cari-body { flex: 1; min-width: 0; }
.cari-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.cari-name { font-size: 13px; font-weight: 500; color: #1e293b; }
.cari-val { font-size: 13px; font-weight: 700; } .cari-val.up { color: #16a34a; } .cari-val.mid { color: #3b82f6; } .cari-val.down { color: #ef4444; }
.cari-bar { height: 5px; background: #f1f5f9; border-radius: 3px; overflow: hidden; margin-bottom: 3px; }
.carib-fill { height: 100%; border-radius: 3px; transition: width .5s; }
.cari-meta { font-size: 11px; color: #94a3b8; } .cari-meta .up { color: #16a34a; } .cari-meta .down { color: #ef4444; }

@media (max-width: 900px) { .ca-metrics { grid-template-columns: repeat(2,1fr); } .ca-main { grid-template-columns: 1fr; } }
</style>
