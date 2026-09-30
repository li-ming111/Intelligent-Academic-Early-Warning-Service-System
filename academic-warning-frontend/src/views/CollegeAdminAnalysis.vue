<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner"><div class="cad-hleft"><h1>成绩分析</h1><p class="cad-hsub">{{ collegeName }} · 全院平均分 {{ avgScore }} · 及格率 {{ passRate }}% · {{ totalRecords }} 条成绩记录</p></div></div>
    </div>
    <div class="cad-metrics">
      <div class="cadm-card"><div class="cadm-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ avgScore }}</div><div class="cadm-lbl">全院平均分</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 12l2 2 4-4"/><circle cx="12" cy="12" r="10"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ passRate }}%</div><div class="cadm-lbl">全院及格率</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ totalRecords }}</div><div class="cadm-lbl">成绩条数</div></div></div>
    </div>
    <div class="cad-content">
      <div class="cad-panel" style="margin-bottom:18px">
        <div class="cadp-head">
          <span class="cadp-title">分数段分布</span>
          <el-button size="small" :icon="Download" @click="exportScoreAnalysis">导出分布</el-button>
        </div>
        <div v-if="scoreDist.length" class="cad-dist">
          <div v-for="d in scoreDist" :key="d.label" class="cadd-row">
            <span class="cadd-lbl">{{ d.label }}</span>
            <div class="cadd-track"><div class="cadd-bar" :style="{ width: d.pct+'%', background: d.color }"></div></div>
            <span class="cadd-cnt">{{ d.count }}</span>
          </div>
        </div>
        <div v-else class="cadp-empty">暂无成绩数据</div>
      </div>

      <!-- 各科目成绩分析 -->
      <div class="cad-panel" style="margin-bottom:18px">
        <div class="cadp-head">
          <span class="cadp-title">各科目成绩分析</span>
          <el-button size="small" :icon="Download" @click="exportCourseAnalysis">导出科目</el-button>
        </div>
        <el-table :data="paginatedCourse" stripe size="small" max-height="50vh" v-if="courseStats.length" style="width:100%">
          <el-table-column prop="courseName" label="课程名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="recordCount" label="考核人次" width="90" align="center" sortable />
          <el-table-column prop="avgScore" label="平均分" width="85" align="center" sortable>
            <template #default="{ row }">
              <span :style="{ color: row.avgScore >= 70 ? '#16a34a' : row.avgScore >= 60 ? '#d97706' : '#ef4444', fontWeight: 600 }">{{ row.avgScore }}</span>
            </template>
          </el-table-column>
          <el-table-column label="及格率" width="85" align="center" sortable sort-by="passRate">
            <template #default="{ row }"><span :style="{ color: row.passRate < 60 ? '#ef4444' : '#16a34a', fontWeight: 600 }">{{ row.passRate }}%</span></template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="courseStats.length" v-model:current-page="coursePage" :page-size="coursePageSize" :total="courseStats.length" layout="prev, pager, next, total" small background class="cad-pager" />
        <div v-else class="cadp-empty">暂无科目数据</div>
      </div>

      <!-- 班级对比 -->
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">班级成绩对比</span>
          <el-button size="small" :icon="Download" @click="exportClassComparison">导出对比</el-button>
        </div>
        <el-table :data="paginatedClassComp" stripe size="small" max-height="50vh" v-if="classComp.length" style="width:100%">
          <el-table-column prop="className" label="班级" min-width="140" />
          <el-table-column prop="avgScore" label="平均分" width="80" align="center" sortable />
          <el-table-column label="及格率" width="80" align="center" sortable sort-by="passRate">
            <template #default="{ row }"><span :style="{ color: (row.passRate || 0) < 60 ? '#ef4444' : '#16a34a', fontWeight: 600 }">{{ row.passRate }}%</span></template>
          </el-table-column>
          <el-table-column prop="studentCount" label="学生数" width="75" align="center" />
          <el-table-column prop="recordCount" label="成绩条数" width="80" align="center" />
        </el-table>
        <el-pagination v-if="classComp.length" v-model:current-page="compPage" :page-size="compPageSize" :total="classComp.length" layout="prev, pager, next, total" small background class="cad-pager" />
        <div v-else class="cadp-empty">暂无班级对比数据</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const collegeName = ref('本院')
const avgScore = ref('--')
const passRate = ref('--')
const totalRecords = ref('--')
const scoreDist = ref([])
const classComp = ref([])
const courseStats = ref([])
const userId = ref('')

// 分页
const compPageSize = ref(8); const compPage = ref(1)
const coursePageSize = ref(10); const coursePage = ref(1)
const paginatedClassComp = computed(() => classComp.value.slice((compPage.value - 1) * compPageSize.value, compPage.value * compPageSize.value))
const paginatedCourse = computed(() => courseStats.value.slice((coursePage.value - 1) * coursePageSize.value, coursePage.value * coursePageSize.value))

function doExport(data, fn) {
  if (!data.length) { ElMessage.warning('无数据'); return }
  const keys = Object.keys(data[0])
  const csv = ['\uFEFF' + keys.join(','), ...data.map(r => keys.map(k => '"' + String(r[k] || '').replace(/"/g, '""') + '"').join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const u = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = u; a.download = fn; a.click()
  URL.revokeObjectURL(u); ElMessage.success('导出成功')
}
function exportScoreAnalysis() {
  doExport(scoreDist.value.map(d => ({ 分数段: d.label, 人数: d.count, 占比: d.pct + '%' })), `分数段分布_${collegeName.value}.csv`)
}
function exportClassComparison() {
  doExport(classComp.value, `班级成绩对比_${collegeName.value}.csv`)
}
function exportCourseAnalysis() {
  doExport(courseStats.value, `各科目成绩分析_${collegeName.value}.csv`)
}

onMounted(async () => {
  const uid = localStorage.getItem('userId'); if (!uid) return; userId.value = uid
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  try {
    const r = await collegeAdminAPI.getScoreAnalysis(uid)
    if (r) {
      avgScore.value = r.avgScore || 0; passRate.value = r.passRate || 0; totalRecords.value = r.totalRecords || 0
      const dist = r.distribution || {}
      const colors = { '90-100':'#16a34a', '80-89':'#3b82f6', '70-79':'#0891b2', '60-69':'#d97706', '0-59':'#ef4444' }
      const total = Object.values(dist).reduce((a,b) => a+b, 0) || 1
      scoreDist.value = Object.entries(dist).map(([k,v]) => ({ label:k, count:v, pct:Math.round(v*100/total), color:colors[k]||'#94a3b8' }))
      courseStats.value = r.courseStats || []
    }
  } catch(e){}
  try { classComp.value = await collegeAdminAPI.getClassComparison(uid) || [] } catch(e){}
})
</script>

<style scoped>
.cad-page { padding: 20px; min-height: 100vh; background: #f8f9fa; }
.cad-hero { position: relative; background: var(--theme-gradient, linear-gradient(135deg, #0EA5E9 0%, #38BDF8 100%)); border-radius: 10px; padding: 24px 36px; overflow: hidden; margin-bottom: 20px; }
.cad-hglow { position: absolute; border-radius: 50%; filter: blur(80px); pointer-events: none; }
.cad-hg1 { width: 200px; height: 200px; background: #3b82f6; top: -50px; right: -30px; opacity: .2; }
.cad-hg2 { width: 120px; height: 120px; background: #60a5fa; bottom: -30px; left: 30%; opacity: .15; }
.cad-hinner { position: relative; z-index: 1; }
.cad-hleft h1 { margin: 0; font-size: 20px; color: #fff; font-weight: 700; }
.cad-hsub { margin: 4px 0 0; font-size: 12px; color: rgba(255,255,255,.6); }
.cad-metrics { display: grid; grid-template-columns: repeat(3,1fr); gap: 14px; padding: 20px 24px; }
.cadm-card { background: #fff; border-radius: 14px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.04); display: flex; align-items: center; gap: 14px; }
.cadm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.cadm-icon svg { width: 18px; height: 18px; }
.ic-green { background:#ecfdf5; color:#16a34a; } .ic-amber { background:#fffbeb; color:#d97706; } .ic-blue { background:#eff6ff; color:#2563eb; }
.cadm-val { font-size: 22px; font-weight: 700; color: #1e293b; }
.cadm-lbl { font-size: 12px; color: #94a3b8; }
.cad-content { }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }
.cad-dist { padding: 12px 22px 16px; }
.cadd-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.cadd-lbl { font-size: 12px; font-weight: 600; min-width: 48px; color: #475569; }
.cadd-track { flex: 1; height: 8px; background: #f1f5f9; border-radius: 4px; overflow: hidden; }
.cadd-bar { height: 100%; border-radius: 4px; transition: width .6s; min-width: 2px; }
.cadd-cnt { font-size: 14px; font-weight: 700; min-width: 28px; text-align: right; color: #1e293b; }
@media (max-width:768px) { .cad-metrics { grid-template-columns: 1fr; } .cad-hero { padding: 16px 20px; } .cad-content { padding: 0 12px; } }
</style>
