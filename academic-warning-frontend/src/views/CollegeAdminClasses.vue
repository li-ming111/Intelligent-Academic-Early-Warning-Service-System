<template>
  <div class="cad-page">
    <!-- Hero -->
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner">
        <div class="cad-hleft">
          <h1>班级管理</h1>
          <p class="cad-hsub">{{ collegeName }} · {{ classes.length }} 个班级 · {{ totalStudents }} 名在籍学生</p>
        </div>
      </div>
    </div>

    <!-- 指标卡片 -->
    <div class="cad-metrics">
      <div class="cadm-card"><div class="cadm-icon ic-teal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ classes.length }}</div><div class="cadm-lbl">班级总数</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2 M9 7a4 4 0 100 8 4 4 0 000-8z"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ totalStudents }}</div><div class="cadm-lbl">学生总数</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ overallGpa }}</div><div class="cadm-lbl">全院均GPA</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><div class="cadm-body"><div class="cadm-val red">{{ totalWarnings }}</div><div class="cadm-lbl">全院预警</div></div></div>
    </div>

    <!-- 班级列表 -->
    <div class="cad-content">
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">班级列表</span>
          <div style="display:flex;gap:10px;align-items:center">
            <el-input v-model="search" placeholder="搜索班级名称" clearable size="small" style="width:200px" />
            <el-button size="small" :icon="Download" @click="exportClasses">导出</el-button>
            <el-button size="small" type="primary" :icon="RefreshRight" @click="loadData">刷新</el-button>
          </div>
        </div>
        <el-table :data="paginatedClasses" stripe size="small" max-height="55vh" highlight-current-row @row-click="showDetail" v-if="classes.length" style="width:100%">
          <el-table-column prop="name" label="班级名称" min-width="180" />
          <el-table-column prop="studentCount" label="学生数" width="80" align="center" sortable>
            <template #default="{ row }">{{ row.studentCount }} 人</template>
          </el-table-column>
          <el-table-column label="均GPA" width="80" align="center" sortable sort-by="avgGpa">
            <template #default="{ row }">
              <span :style="{ color: row.avgGpa >= 3.0 ? '#16a34a' : row.avgGpa >= 2.0 ? '#d97706' : '#ef4444', fontWeight: 600 }">{{ row.avgGpa }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预警数" width="75" align="center" sortable sort-by="warningCount">
            <template #default="{ row }">
              <el-tag v-if="row.warningCount > 0" :type="row.warningCount >= 3 ? 'danger' : 'warning'" size="small">{{ row.warningCount }}</el-tag>
              <span v-else style="color:#94a3b8">0</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="showDetail(row)">查看学生</el-button>
              <el-button link type="success" size="small" @click.stop="exportClass(row)">导出</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="classes.length" v-model:current-page="currentPage" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next, total" small background class="cad-pager" />
        <div v-else class="cadp-empty">暂无班级数据</div>
      </div>
    </div>

    <!-- 班级详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="'班级详情 - ' + curClassName" width="90%" style="max-width:900px" destroy-on-close>
      <!-- 班级概要统计 -->
      <div class="cd-summary" v-if="classStudents.length">
        <div class="cds-item"><span class="cds-num">{{ classStudents.length }}</span><span class="cds-lbl">学生数</span></div>
        <div class="cds-item"><span class="cds-num green">{{ cgpa }}</span><span class="cds-lbl">均GPA</span></div>
        <div class="cds-item"><span class="cds-num red">{{ cfail }}</span><span class="cds-lbl">挂科学生</span></div>
        <div class="cds-item"><span class="cds-num amber">{{ cwarn }}</span><span class="cds-lbl">预警学生</span></div>
      </div>
      <!-- 学生列表 -->
      <el-table :data="classStudents" stripe size="small" max-height="45vh" style="width:100%" v-if="classStudents.length">
        <el-table-column prop="studentId" label="学号" min-width="110" />
        <el-table-column prop="name" label="姓名" min-width="90" />
        <el-table-column label="GPA" width="70" align="center" sortable sort-by="gpa">
          <template #default="{ row }">
            <span :style="{ color: row.gpa >= 3.0 ? '#16a34a' : row.gpa >= 2.0 ? '#d97706' : '#ef4444', fontWeight: 600 }">{{ row.gpa }}</span>
          </template>
        </el-table-column>
        <el-table-column label="课程" min-width="130" align="center">
          <template #default="{ row }">
            <span style="color:#16a34a">{{ row.passedCourses }}通过</span>
            <span v-if="row.failedCourses > 0" style="color:#ef4444;margin-left:6px">{{ row.failedCourses }}挂科</span>
            <span v-else style="color:#94a3b8;margin-left:6px">0挂科</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="viewStudentDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="cadp-empty">暂无学生数据</div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" :icon="Download" @click="exportClassStudents">导出本班</el-button>
      </template>
    </el-dialog>

    <!-- 学生详情弹窗 -->
    <el-dialog v-model="studentDetailVisible" :title="'学生详情 - ' + studentDetail.name" width="90%" style="max-width:750px" destroy-on-close>
      <div v-if="studentDetail.id">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="学号">{{ studentDetail.studentId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ studentDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ studentDetail.className }}</el-descriptions-item>
          <el-descriptions-item label="GPA">
            <span :style="{ color: studentDetail.gpa >= 3.0 ? '#16a34a' : studentDetail.gpa >= 2.0 ? '#d97706' : '#ef4444', fontWeight: 700 }">{{ studentDetail.gpa }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="课程">共 {{ studentDetail.totalCourses }} 门 · 通过 {{ studentDetail.passedCourses }}</el-descriptions-item>
          <el-descriptions-item label="挂科"><span :style="{ color: studentDetail.failedCourses > 0 ? '#ef4444' : '#16a34a' }">{{ studentDetail.failedCourses || 0 }}</span> 门</el-descriptions-item>
        </el-descriptions>
        <div v-if="studentDetail.warnings && studentDetail.warnings.length" style="margin-top:12px">
          <h4 style="margin:0 0 8px;color:#1e293b">预警记录</h4>
          <div v-for="(w,i) in studentDetail.warnings" :key="i" style="padding:6px 0;font-size:13px;border-bottom:1px solid #f1f5f9">
            <el-tag :type="w.warningLevel >= 3 ? 'danger' : w.warningLevel >= 2 ? 'warning' : 'info'" size="small" style="margin-right:6px">{{ w.warningLevel >= 3 ? '严重' : w.warningLevel >= 2 ? '中度' : '轻度' }}</el-tag>
            {{ w.description }}
            <span style="color:#94a3b8;float:right">{{ fmtDate(w.createdAt) }}</span>
          </div>
        </div>
      </div>
      <template #footer><el-button @click="studentDetailVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { Download, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const collegeName = ref('本院')
const classes = ref([])
const search = ref('')
const detailVisible = ref(false)
const classStudents = ref([])
const curClassName = ref('')
const studentDetailVisible = ref(false)
const studentDetail = ref({})

// 整体统计
const totalStudents = computed(() => classes.value.reduce((a, c) => a + (c.studentCount || 0), 0))
const totalWarnings = computed(() => classes.value.reduce((a, c) => a + (c.warningCount || 0), 0))
const overallGpa = computed(() => {
  const list = classes.value.filter(c => c.avgGpa > 0)
  if (!list.length) return '--'
  return (list.reduce((a, c) => a + c.avgGpa, 0) / list.length).toFixed(2)
})

const filtered = computed(() => {
  if (!search.value) return classes.value
  const kw = search.value.toLowerCase()
  return classes.value.filter(c => c.name && c.name.toLowerCase().includes(kw))
})
const pageSize = ref(8)
const currentPage = ref(1)
const paginatedClasses = computed(() => filtered.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
watch(search, () => { currentPage.value = 1 })

const cgpa = computed(() => { if (!classStudents.value.length) return '--'; const s = classStudents.value.reduce((a, r) => a + (r.gpa || 0), 0); return (s / classStudents.value.length).toFixed(2) })
const cfail = computed(() => classStudents.value.filter(s => s.failedCourses > 0).length)
const cwarn = computed(() => classStudents.value.filter(s => (s.warningCount || 0) > 0).length)

function fmtDate(t) { if (!t) return ''; try { return new Date(t).toLocaleDateString('zh-CN') } catch(e) { return String(t).substring(0,10) } }

async function showDetail(row) {
  try {
    const uid = localStorage.getItem('userId'); curClassName.value = row.name
    classStudents.value = await collegeAdminAPI.getClassStudents(uid, row.id) || []
    detailVisible.value = true
  } catch (e) { console.error(e) }
}

async function viewStudentDetail(row) {
  try {
    studentDetail.value = row
    if (row.warningCount !== undefined && row.warnings) {
      studentDetailVisible.value = true
      return
    }
    const uid = localStorage.getItem('userId')
    const d = await collegeAdminAPI.getStudentDetail(uid, row.id)
    if (d) { studentDetail.value = d; studentDetailVisible.value = true }
  } catch (e) { console.error(e) }
}

function doExport(data, filename) {
  if (!data.length) { ElMessage.warning('无数据可导出'); return }
  const keys = Object.keys(data[0])
  const csv = ['\uFEFF' + keys.join(','), ...data.map(r => keys.map(k => '"' + String(r[k] || '').replace(/"/g,'""') + '"').join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const u = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = u; a.download = filename; a.click()
  URL.revokeObjectURL(u); ElMessage.success('导出成功')
}
function exportClasses() {
  doExport(classes.value.map(c => ({ 班级名称: c.name, 学生数: c.studentCount, 均GPA: c.avgGpa, 预警数: c.warningCount })), `班级列表_${collegeName.value}.csv`)
}
function exportClass(row) {
  doExport(classStudents.value, `${row.name}_${collegeName.value}.csv`)
}
function exportClassStudents() {
  doExport(classStudents.value.map(s => ({ 学号: s.studentId, 姓名: s.name, GPA: s.gpa, 通过: s.passedCourses, 挂科: s.failedCourses })), `${curClassName.value}_学生.csv`)
}

async function loadData() {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  try { classes.value = await collegeAdminAPI.getClasses(uid) || [] } catch(e){}
}
onMounted(() => loadData())
</script>

<style scoped>
.cad-page { padding: 20px; min-height: 100vh; background: #f8f9fa; }
.cad-hero { position: relative; background: var(--theme-gradient, linear-gradient(135deg, #0EA5E9 0%, #38BDF8 100%)); border-radius: 10px; padding: 28px 36px; overflow: hidden; margin-bottom: 20px; }
.cad-hglow { position: absolute; border-radius: 50%; filter: blur(80px); pointer-events: none; }
.cad-hg1 { width: 260px; height: 260px; background: #3b82f6; top: -70px; right: -50px; opacity: .25; }
.cad-hg2 { width: 180px; height: 180px; background: #60a5fa; bottom: -50px; left: 20%; opacity: .18; }
.cad-hinner { position: relative; z-index: 1; }
.cad-hleft h1 { margin: 0; font-size: 20px; color: #fff; font-weight: 700; }
.cad-hsub { margin: 4px 0 0; font-size: 12px; color: rgba(255,255,255,.6); }
.cad-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; padding: 20px 24px; }
.cadm-card { background: #fff; border-radius: 14px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.04); display: flex; align-items: center; gap: 14px; transition: all .2s; }
.cadm-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,.08); }
.cadm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.cadm-icon svg { width: 18px; height: 18px; }
.ic-teal { background: #f0fdfa; color: #0d9488; } .ic-purple { background: #f5f3ff; color: #7c3aed; }
.ic-green { background: #ecfdf5; color: #16a34a; } .ic-red { background: #fef2f2; color: #ef4444; }
.cadm-val { font-size: 22px; font-weight: 700; color: #1e293b; } .cadm-val.red { color: #ef4444; }
.cadm-lbl { font-size: 12px; color: #94a3b8; }
.cad-content { }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }

/* 班级详情摘要 */
.cd-summary { display: flex; gap: 20px; padding: 16px 0 20px; }
.cds-item { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 20px; background: #f8fafc; border-radius: 10px; flex: 1; }
.cds-num { font-size: 24px; font-weight: 700; color: #1e293b; }
.cds-num.green { color: #16a34a; } .cds-num.red { color: #ef4444; } .cds-num.amber { color: #d97706; }
.cds-lbl { font-size: 11px; color: #94a3b8; }

@media (max-width:1200px) { .cad-metrics { grid-template-columns: repeat(2, 1fr); } }
@media (max-width:768px) { .cad-metrics { grid-template-columns: 1fr; } .cad-content { padding: 0 12px; } .cad-hero { padding: 18px 20px; } .cd-summary { flex-wrap: wrap; } .cds-item { flex: 1 1 40%; } }
</style>
