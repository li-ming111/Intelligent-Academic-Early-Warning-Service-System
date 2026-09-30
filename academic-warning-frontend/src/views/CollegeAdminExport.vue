<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner">
        <div class="cad-hleft left-hero">
          <h1 style="display:flex;align-items:center;gap:8px">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" width="22" height="22"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="11" x2="12" y2="17"/><polyline points="9 14 12 17 15 14"/></svg>
            数据导出
          </h1>
          <p class="cad-hsub">{{ collegeName }} · 选择类型即可下载 CSV 文件</p>
        </div>
        <div class="right-hero">
          <div class="hero-stat"><span class="hs-num">{{ summary.totalRecords }}</span><span class="hs-lbl">总记录</span></div>
          <div class="hero-stat"><span class="hs-num">{{ summary.lastExport || '--' }}</span><span class="hs-lbl">上次导出</span></div>
        </div>
      </div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="ce-overview">
      <div class="ceo-card" v-for="s in overviewStats" :key="s.key" @click="handleExport(s.key, s.label)">
        <div class="ceo-left">
          <div class="ceo-icon" :style="{ background: s.bg }"><span v-html="s.icon"></span></div>
        </div>
        <div class="ceo-mid">
          <div class="ceo-label">{{ s.label }}</div>
          <div class="ceo-desc">{{ s.fields }}</div>
        </div>
        <div class="ceo-right">
          <div class="ceo-count">{{ s.count }}</div>
          <div class="ceo-unit">条</div>
        </div>
        <div class="ceo-actions">
          <el-button size="small" circle @click.stop="previewData(s.key, s.label)" title="预览数据"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg></el-button>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="'预览 - ' + previewLabel" width="90%" style="max-width:950px" destroy-on-close>
      <div v-if="previewRows.length" style="margin-bottom:12px;color:#64748b;font-size:13px">共 {{ previewRows.length }} 条记录，仅显示前 20 条预览</div>
      <el-table :data="previewRows" stripe size="small" max-height="55vh" style="width:100%" v-if="previewRows.length">
        <el-table-column v-for="h in previewHeaders" :key="h" :prop="h" :label="h" min-width="100" show-overflow-tooltip />
      </el-table>
      <div v-else class="cadp-empty">暂无数据</div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="doExportFromPreview">导出 CSV</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { ElMessage } from 'element-plus'

const collegeName = ref('本院')
const userId = ref('')
const summary = reactive({ totalRecords: '--', lastExport: '' })
const previewVisible = ref(false)
const previewLabel = ref('')
const previewRows = ref([])
const previewHeaders = ref([])
const previewKey = ref('')
const exportCounts = reactive({ scores: '--', warnings: '--', students: '--', teachers: '--' })

const headerMap = {
  scores:   { studentName:'学生姓名', className:'班级', courseName:'课程名称', regularScore:'平时成绩', finalScore:'期末成绩', totalScore:'总评成绩', gradePoint:'绩点', grade:'等级', semester:'学期' },
  warnings: { studentName:'学生姓名', className:'班级', description:'预警描述', warningLevel:'预警等级', status:'处理状态' },
  students: { studentId:'学号', name:'姓名', className:'班级' },
  teachers: { name:'教师姓名', title:'职称', researchAreas:'研究方向' },
}

const overviewStats = computed(() => [
  { key:'scores', label:'学生成绩', fields:'姓名·班级·课程·平时·期末·总评·绩点·等级', bg:'#e8f5e9', count: exportCounts.scores,
    icon:'<svg viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="2"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>' },
  { key:'warnings', label:'预警数据', fields:'姓名·班级·描述·等级·状态', bg:'#fce4ec', count: exportCounts.warnings,
    icon:'<svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>' },
  { key:'students', label:'学生名单', fields:'学号·姓名·班级', bg:'#e3f2fd', count: exportCounts.students,
    icon:'<svg viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>' },
  { key:'teachers', label:'教师名单', fields:'姓名·职称·研究方向', bg:'#f3e5f5', count: exportCounts.teachers,
    icon:'<svg viewBox="0 0 24 24" fill="none" stroke="#7c3aed" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>' },
])

// 加载各类型条数
async function loadCounts() {
  const uid = userId.value; if (!uid) return
  try {
    const [scores, warnings, students, teachers] = await Promise.all([
      collegeAdminAPI.exportData(uid, 'scores').catch(() => []),
      collegeAdminAPI.exportData(uid, 'warnings').catch(() => []),
      collegeAdminAPI.exportData(uid, 'students').catch(() => []),
      collegeAdminAPI.exportData(uid, 'teachers').catch(() => []),
    ])
    exportCounts.scores = (scores||[]).length
    exportCounts.warnings = (warnings||[]).length
    exportCounts.students = (students||[]).length
    exportCounts.teachers = (teachers||[]).length
    summary.totalRecords = (scores||[]).length + (warnings||[]).length + (students||[]).length + (teachers||[]).length
  } catch(e){}
}

function mapRow(row, type) {
  const map = headerMap[type] || {}
  const mapped = {}
  for (const [k, v] of Object.entries(row)) mapped[map[k] || k] = v
  return mapped
}

async function previewData(type, label) {
  try {
    const data = await collegeAdminAPI.exportData(userId.value, type)
    if (!data || !data.length) { ElMessage.warning('无数据可预览'); return }
    previewKey.value = type; previewLabel.value = label
    const sample = data.slice(0, 20).map(r => mapRow(r, type))
    previewRows.value = sample
    previewHeaders.value = Object.keys(sample[0])
    previewVisible.value = true
  } catch(e) { ElMessage.error('加载失败') }
}

function formatCsv(data, type) {
  const map = headerMap[type] || {}
  const keys = Object.keys(data[0])
  const headers = keys.map(k => map[k] || k)
  const rows = data.map(r => keys.map(k => '"' + String(r[k] ?? '').replace(/"/g, '""') + '"').join(','))
  return '\uFEFF' + headers.join(',') + '\n' + rows.join('\n')
}

async function doExportFromPreview() {
  try {
    const data = await collegeAdminAPI.exportData(userId.value, previewKey.value)
    if (!data || !data.length) { ElMessage.warning('无数据'); return }
    const csv = formatCsv(data, previewKey.value)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = `${previewLabel.value}_${collegeName.value}_${new Date().toISOString().substring(0,10)}.csv`
    a.click(); URL.revokeObjectURL(url)
    const now = new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })
    summary.lastExport = now
    ElMessage.success(`导出成功 (${data.length} 条)`)
    previewVisible.value = false
  } catch(e) { ElMessage.error('导出失败') }
}

async function handleExport(type, label) {
  try {
    const data = await collegeAdminAPI.exportData(userId.value, type)
    if (!data || !data.length) { ElMessage.warning('无数据可导出'); return }
    const csv = formatCsv(data, type)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = `${label}_${collegeName.value}_${new Date().toISOString().substring(0,10)}.csv`
    a.click(); URL.revokeObjectURL(url)
    const now = new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })
    summary.lastExport = now
    ElMessage.success(`${label} 导出成功 (${data.length} 条)`)
  } catch(e) { ElMessage.error('导出失败') }
}

onMounted(async () => {
  const uid = localStorage.getItem('userId'); if (!uid) return; userId.value = uid
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  loadCounts()
})
</script>

<style scoped>
.cad-page { padding: 20px; min-height: 100vh; background: #f8f9fa; }
.cad-hero { position: relative; background: var(--theme-gradient, linear-gradient(135deg, #1e3a8a, #2563eb, #3b82f6)); border-radius: 10px; padding: 24px 36px; overflow: hidden; margin-bottom: 20px; }
.cad-hglow { position: absolute; border-radius: 50%; filter: blur(80px); pointer-events: none; }
.cad-hg1 { width: 200px; height: 200px; background: #3b82f6; top: -50px; right: -30px; opacity: .2; }
.cad-hg2 { width: 120px; height: 120px; background: #60a5fa; bottom: -30px; left: 30%; opacity: .15; }
.cad-hinner { position: relative; z-index: 1; display: flex; justify-content: space-between; align-items: center; }
.left-hero h1 { margin: 0; font-size: 20px; color: #fff; font-weight: 700; }
.cad-hsub { margin: 4px 0 0; font-size: 12px; color: rgba(255,255,255,.6); }
.right-hero { display: flex; gap: 24px; }
.hero-stat { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.hs-num { font-size: 22px; font-weight: 700; color: #fff; }
.hs-lbl { font-size: 10px; color: rgba(255,255,255,.5); }

/* 导出卡片 */
.ce-overview { display: flex; flex-direction: column; gap: 14px; }
.ceo-card { display: flex; align-items: center; gap: 16px; padding: 20px 24px; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); cursor: pointer; transition: all .2s; }
.ceo-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,.12); }
.ceo-icon { width: 52px; height: 52px; border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0; }
.ceo-mid { flex: 1; min-width: 0; }
.ceo-label { font-size: 15px; font-weight: 600; color: #1e293b; margin-bottom: 4px; }
.ceo-desc { font-size: 12px; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ceo-right { text-align: center; min-width: 50px; }
.ceo-count { font-size: 22px; font-weight: 700; color: #2563eb; }
.ceo-unit { font-size: 11px; color: #94a3b8; }
.ceo-actions { display: flex; gap: 6px; }

.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }

@media (max-width:768px) {
  .cad-hero { padding: 16px 20px; }
  .cad-hinner { flex-direction: column; align-items: flex-start; gap: 12px; }
  .right-hero { gap: 16px; }
  .ceo-card { flex-wrap: wrap; gap: 10px; padding: 16px; }
  .ceo-right { margin-left: auto; }
}
</style>
