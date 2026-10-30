<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner"><div class="cad-hleft"><h1>成绩查询</h1><p class="cad-hsub">{{ collegeName }} · {{ totalCount }} 条成绩记录</p></div></div>
    </div>
    <div class="cad-content">
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">成绩列表</span>
          <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
            <el-select v-model="filterClass" placeholder="全部班级" clearable size="small" style="width:160px" @change="searchNow">
              <el-option v-for="c in classOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input v-model="search" placeholder="搜索学号/姓名" clearable size="small" style="width:180px" @input="currentPage=1" @keyup.enter="searchNow" />
            <el-button size="small" type="primary" @click="searchNow">查询</el-button>
            <el-button size="small" :icon="Download" @click="exportScores">导出</el-button>
          </div>
        </div>
        <el-table :data="scores" stripe size="small" max-height="58vh" v-if="scores.length" style="width:100%">
          <el-table-column prop="studentName" label="姓名" min-width="80" fixed />
          <el-table-column prop="studentId" label="学号" width="110" sortable />
          <el-table-column prop="className" label="班级" min-width="120" />
          <el-table-column prop="courseName" label="课程" min-width="140" show-overflow-tooltip />
          <el-table-column prop="regularScore" label="平时" width="60" align="center" />
          <!-- 期末未录入时 totalScore 是"平时分折半"的中间值，不是真实成绩，
               裸渲染会被当成不及格。这里统一显示待录入（灰）。 -->
          <el-table-column prop="finalScore" label="期末" width="60" align="center">
            <template #default="{ row }">
              <span v-if="!isPendingValue(row.finalScore)" style="color:#334155">{{ row.finalScore }}</span>
              <span v-else style="color:#94a3b8">{{ PENDING_TEXT }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总评" width="65" align="center" sortable>
            <template #default="{ row }">
              <span v-if="isPendingValue(row.finalScore)" style="color:#94a3b8">{{ PENDING_TEXT }}</span>
              <span v-else :style="{ color: toNumber(row.totalScore) >= 60 ? '#16a34a' : '#ef4444', fontWeight: 600 }">{{ row.totalScore }}</span>
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
        <el-pagination v-if="totalCount" v-model:current-page="currentPage" :page-size="pageSize" :total="totalCount" layout="prev, pager, next, total" small background class="cad-pager" @current-change="loadData" />
        <div v-else class="cadp-empty">暂无成绩数据，请选择班级后点击查询</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { PENDING_TEXT, isPendingValue, toNumber } from '@/utils/score'

const collegeName = ref('本院')
const scores = ref([])
const classOptions = ref([])
const filterClass = ref(null)
const search = ref('')
const pageSize = ref(15)
const currentPage = ref(1)
const totalCount = ref(0)

function queryParams(page, size) {
  const params = { page, size }
  if (filterClass.value) params.classId = filterClass.value
  if (search.value) params.keyword = search.value
  return params
}

async function exportScores() {
  const uid = localStorage.getItem('userId'); if (!uid) return
  // 导出要的是"当前筛选下的全部行"，而列表一次只取一页，所以按页拉全再拼。
  let all = []
  try {
    let page = 1
    for (;;) {
      const res = await collegeAdminAPI.scoreQuery(uid, queryParams(page, 200)) || {}
      all = all.concat(res.items || [])
      if (all.length >= (res.total || 0) || !(res.items || []).length) break
      page += 1
    }
  } catch (e) { console.error(e); ElMessage.error('导出失败'); return }
  if (!all.length) { ElMessage.warning('无数据可导出'); return }
  const keys = ['学生姓名','学号','班级','课程名称','平时成绩','期末成绩','总评成绩','绩点','等级','学期']
  // 期末未录入的行导出成"待录入"而不是空单元格 —— 空白会被当成"忘了填"，
  // 也可能在 Excel 里被误读成 0 分。
  const rows = all.map(r => {
    const pending = isPendingValue(r.finalScore)
    return [r.studentName, r.studentId, r.className, r.courseName, r.regularScore,
      pending ? PENDING_TEXT : r.finalScore, pending ? PENDING_TEXT : r.totalScore,
      pending ? '' : r.gradePoint, pending ? '' : r.grade, r.semester]
  })
  const csv = ['\uFEFF' + keys.join(','), ...rows.map(r => r.map(v => '"' + String(v ?? '').replace(/"/g,'""') + '"').join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const u = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = u; a.download = `成绩查询_${collegeName.value}.csv`; a.click()
  URL.revokeObjectURL(u); ElMessage.success(`导出成功 (${all.length} 条)`)
}

async function loadData() {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try {
    const res = await collegeAdminAPI.scoreQuery(uid, queryParams(currentPage.value, pageSize.value)) || {}
    scores.value = res.items || []
    totalCount.value = res.total || 0
  } catch(e){ console.error(e) }
}

function searchNow() { currentPage.value = 1; loadData() }

onMounted(async () => {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  try { classOptions.value = await collegeAdminAPI.getClasses(uid) || [] } catch(e){}
  loadData()
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
.cad-content { }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; flex-wrap: wrap; gap: 10px; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }
@media (max-width:768px) { .cad-hero { padding: 16px 20px; } }
</style>
