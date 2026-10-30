<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner"><div class="cad-hleft"><h1>学生管理</h1><p class="cad-hsub">{{ collegeName }} · 共 {{ filteredList.length }} / {{ students.length }} 名在籍学生</p></div></div>
    </div>
    <div class="cad-metrics">
      <div class="cadm-card"><div class="cadm-icon ic-purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2 M9 7a4 4 0 100 8 4 4 0 000-8z"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ students.length }}</div><div class="cadm-lbl">总人数</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ avgGpa }}</div><div class="cadm-lbl">平均GPA</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><div class="cadm-body"><div class="cadm-val red">{{ warnTotal }}</div><div class="cadm-lbl">预警学生</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 12l2 2 4-4 M12 2a10 10 0 100 20"/></svg></div><div class="cadm-body"><div class="cadm-val" style="color:#d97706">{{ failTotal }}</div><div class="cadm-lbl">有挂科</div></div></div>
    </div>

    <div class="cad-content">
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">学生列表</span>
          <div style="display:flex;gap:10px;align-items:center">
            <el-input v-model="search" placeholder="搜索学号/姓名/班级" clearable size="small" style="width:220px" @input="onSearch" />
            <el-button size="small" type="primary" :icon="RefreshRight" @click="loadData">刷新</el-button>
          </div>
        </div>
        <el-table :data="paginatedStudents" stripe size="small" max-height="58vh" highlight-current-row @row-click="showDetail" v-if="students.length" style="width:100%">
          <el-table-column prop="studentId" label="学号" min-width="100" sortable />
          <el-table-column prop="name" label="姓名" min-width="80" />
          <el-table-column prop="className" label="班级" min-width="120" />
          <el-table-column label="GPA" width="60" sortable sort-by="gpa" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.gpa >= 3.0 ? '#16a34a' : row.gpa >= 2.0 ? '#d97706' : '#ef4444', fontWeight: 600 }">{{ row.gpa }}</span>
            </template>
          </el-table-column>
          <el-table-column label="课程" min-width="130">
            <template #default="{ row }">
              <span style="color:#16a34a">{{ row.passedCourses || 0 }}通过</span>
              <span v-if="row.failedCourses > 0" style="color:#ef4444;margin-left:4px">{{ row.failedCourses }}挂科</span>
            </template>
          </el-table-column>
          <el-table-column label="预警" width="55" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.warningCount > 0" type="danger" size="small">{{ row.warningCount }}</el-tag>
              <span v-else style="color:#94a3b8">0</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="showDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="students.length" v-model:current-page="currentPage" :page-size="pageSize" :total="filteredList.length" layout="prev, pager, next, total" small background class="cad-pager" @current-change="()=>{}" />
        <div v-else class="cadp-empty">暂无学生数据</div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="'学生详情 - ' + detail.name" width="90%" style="max-width:800px" destroy-on-close>
      <div v-if="detail.id" class="detail-grid">
        <div>
          <h4 style="margin:0 0 12px;color:#1e293b">基本信息</h4>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="学号">{{ detail.studentId }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="班级">{{ detail.className }}</el-descriptions-item>
            <el-descriptions-item label="GPA">
              <span :style="{ color: detail.gpa >= 3.0 ? '#16a34a' : detail.gpa >= 2.0 ? '#d97706' : '#ef4444', fontWeight: 700 }">{{ detail.gpa }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="课程">
              共 {{ detail.totalCourses }} 门 · 通过 {{ detail.passedCourses }} · <span v-if="detail.failedCourses > 0" style="color:#ef4444">挂科 {{ detail.failedCourses }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <div>
          <h4 style="margin:0 0 12px;color:#1e293b">预警记录 ({{ detail.warningCount }})</h4>
          <div v-if="detail.warnings && detail.warnings.length" style="max-height:160px;overflow-y:auto">
            <div v-for="(w,i) in detail.warnings" :key="i" style="padding:8px 0;border-bottom:1px solid #f1f5f9;font-size:13px">
              <!-- 等级统一走 warningLevelText：字段名可能是 level，且缺失时不应默认成"轻度" -->
              <el-tag :type="warningLevelTagType(w.warningLevel ?? w.level)" size="small" style="margin-right:6px">{{ warningLevelText(w.warningLevel ?? w.level) }}</el-tag>
              <span>{{ w.description || '学业预警' }}</span>
              <span style="color:#94a3b8;float:right">{{ fmtDate(w.createdAt) }}</span>
            </div>
          </div>
          <div v-else style="color:#94a3b8;font-size:13px">无预警记录</div>
        </div>
      </div>
      <div v-if="detail.scores && detail.scores.length" style="margin-top:16px">
        <h4 style="margin:0 0 12px;color:#1e293b">各科成绩 ({{ detail.scores.length }} 门)</h4>
        <el-table :data="detail.scores" stripe size="small" max-height="40vh" style="width:100%">
          <el-table-column prop="courseName" label="课程名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="regularScore" label="平时" width="55" align="center" />
          <!-- 期末未录入 → 总评是中间值，不能按分数渲染（`(null||0) >= 60` 为 false，
               会被染成红色并读成"不及格"）。 -->
          <el-table-column prop="finalScore" label="期末" width="55" align="center">
            <template #default="{ row }">
              <span v-if="!isPendingValue(row.finalScore)" style="color:#334155">{{ row.finalScore }}</span>
              <span v-else style="color:#94a3b8">{{ PENDING_TEXT }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总评" width="60" align="center" sortable>
            <template #default="{ row }">
              <span v-if="isPendingValue(row.finalScore)" style="color:#94a3b8">{{ PENDING_TEXT }}</span>
              <span v-else :style="{ color: toNumber(row.totalScore ?? row.score) >= 60 ? '#16a34a' : '#ef4444', fontWeight: 600 }">{{ row.totalScore ?? row.score }}</span>
            </template>
          </el-table-column>
          <!-- 绩点/等级同属"期末录入后才有"的派生值，未录入时留白会被读成"空数据"。 -->
          <el-table-column prop="gradePoint" label="绩点" width="55" align="center">
            <template #default="{ row }">
              <span v-if="isPendingValue(row.finalScore)" style="color:#94a3b8">{{ PENDING_TEXT }}</span>
              <span v-else>{{ row.gradePoint != null ? row.gradePoint : '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="grade" label="等级" width="55" align="center">
            <template #default="{ row }">
              <span v-if="isPendingValue(row.finalScore)" style="color:#94a3b8">{{ PENDING_TEXT }}</span>
              <span v-else>{{ row.grade || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="学期" width="110">
            <template #default="{ row }">{{ row.semester != null ? `第${row.semester}学期` : '--' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { RefreshRight } from '@element-plus/icons-vue'
import { PENDING_TEXT, isPendingValue, toNumber, warningLevelText, warningLevelTagType } from '@/utils/score'

const collegeName = ref('本院')
const students = ref([])
const search = ref('')
const detailVisible = ref(false)
const detail = ref({})
const pageSize = ref(10)
const currentPage = ref(1)

const filteredList = computed(() => {
  if (!search.value) return students.value
  const kw = search.value.toLowerCase()
  return students.value.filter(s =>
    (s.studentId && String(s.studentId).toLowerCase().includes(kw)) ||
    (s.name && s.name.toLowerCase().includes(kw)) ||
    (s.className && s.className.toLowerCase().includes(kw))
  )
})
const paginatedStudents = computed(() => filteredList.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
watch(search, () => { currentPage.value = 1 })
const avgGpa = computed(() => {
  if (!students.value.length) return '--'
  const sum = students.value.reduce((a, s) => a + (s.gpa || 0), 0)
  return (sum / students.value.length).toFixed(2)
})
const warnTotal = computed(() => students.value.filter(s => s.warningCount > 0).length)
const failTotal = computed(() => students.value.filter(s => s.failedCourses > 0).length)

function fmtDate(t) { if (!t) return ''; try { return new Date(t).toLocaleDateString('zh-CN') } catch(e) { return String(t).substring(0,10) } }
function onSearch() {}

async function showDetail(row) {
  try {
    const uid = localStorage.getItem('userId')
    const d = await collegeAdminAPI.getStudentDetail(uid, row.id)
    if (d) { detail.value = d; detailVisible.value = true }
  } catch (e) { console.error(e) }
}

async function loadData() {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  try { students.value = await collegeAdminAPI.getStudents(uid, search.value || undefined) || [] } catch(e){}
}

onMounted(() => loadData())
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
.cad-metrics { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; padding: 20px 24px; }
.cadm-card { background: #fff; border-radius: 14px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.04); display: flex; align-items: center; gap: 14px; }
.cadm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.cadm-icon svg { width: 18px; height: 18px; }
.ic-purple { background:#f5f3ff; color:#7c3aed; } .ic-blue { background:#eff6ff; color:#2563eb; } .ic-red { background:#fef2f2; color:#ef4444; } .ic-green { background:#ecfdf5; color:#16a34a; } .ic-amber { background:#fffbeb; color:#d97706; }
.cadm-val { font-size: 22px; font-weight: 700; color: #1e293b; } .cadm-val.red { color: #ef4444; }
.cadm-lbl { font-size: 12px; color: #94a3b8; }
.cad-content { }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }

@media (max-width:1200px) { .cad-metrics { grid-template-columns: repeat(2,1fr); } }
@media (max-width:768px) {
  .cad-metrics { grid-template-columns: 1fr; padding: 12px 16px; }
  .cad-content { padding: 0 12px; }
  .cad-hero { padding: 16px 20px; }
  .cadp-head { flex-direction: column; align-items: flex-start; gap: 10px; }
  .detail-grid { grid-template-columns: 1fr; }
}
</style>
