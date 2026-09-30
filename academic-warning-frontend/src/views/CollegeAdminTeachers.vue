<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner"><div class="cad-hleft"><h1>教师管理</h1><p class="cad-hsub">{{ collegeName }} · {{ filtered.length }} / {{ teachers.length }} 名教师</p></div></div>
    </div>
    <div class="cad-metrics">
      <div class="cadm-card"><div class="cadm-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2 M22 21v-2a4 4 0 00-3-3.87 M16 3.13a4 4 0 010 7.75"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ teachers.length }}</div><div class="cadm-lbl">教师总数</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg></div><div class="cadm-body"><div class="cadm-val">{{ courseTotal }}</div><div class="cadm-lbl">授课门次</div></div></div>
    </div>
    <div class="cad-content">
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">教师列表</span>
          <div style="display:flex;gap:10px">
            <el-input v-model="search" placeholder="搜索姓名/职称/研究方向" clearable size="small" style="width:220px" />
            <el-button size="small" type="primary" :icon="RefreshRight" @click="loadData">刷新</el-button>
          </div>
        </div>
        <el-table :data="paginatedTeachers" stripe size="small" max-height="58vh" highlight-current-row @row-click="showDetail" v-if="teachers.length" style="width:100%">
          <el-table-column prop="name" label="姓名" min-width="80" />
          <el-table-column prop="title" label="职称" width="80" align="center" />
          <el-table-column prop="researchAreas" label="研究方向" min-width="110" show-overflow-tooltip />
          <el-table-column prop="courses" label="课程" min-width="140" show-overflow-tooltip />
          <el-table-column prop="courseCount" label="授课门数" width="80" align="center" sortable />
          <el-table-column prop="studentCount" label="授课人次" width="80" align="center" sortable />
          <el-table-column label="操作" width="70" align="center" fixed="right">
            <template #default="{ row }"><el-button link type="primary" size="small" @click.stop="showDetail(row)">详情</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="teachers.length" v-model:current-page="currentPage" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next, total" small background class="cad-pager" />
        <div v-else class="cadp-empty">暂无教师数据</div>
      </div>
    </div>

    <el-dialog v-model="detailVisible" :title="'教师详情 - ' + detail.name" width="90%" style="max-width:700px" destroy-on-close>
      <div v-if="detail.id">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="职称">{{ detail.title || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="研究方向" :span="2">{{ detail.researchAreas || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="授课门数">{{ detail.courseCount }}</el-descriptions-item>
          <el-descriptions-item label="授课人次">{{ detail.studentCount }}</el-descriptions-item>
          <el-descriptions-item label="授课均分">{{ detail.avgScore }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.scores && detail.scores.length" style="margin-top:16px">
          <h4 style="margin:0 0 10px;color:#1e293b">授课记录 ({{ detail.scores.length }} 条)</h4>
          <el-table :data="detail.scores" stripe size="small" max-height="35vh" style="width:100%">
            <el-table-column prop="studentName" label="学生" min-width="80" />
            <el-table-column prop="className" label="班级" min-width="110" />
            <el-table-column prop="courseName" label="课程" min-width="120" show-overflow-tooltip />
            <el-table-column prop="score" label="分数" width="65" align="center" sortable>
              <template #default="{ row }"><span :style="{ color: row.score >= 60 ? '#16a34a' : '#ef4444', fontWeight: 600 }">{{ row.score }}</span></template>
            </el-table-column>
            <el-table-column prop="semester" label="学期" min-width="100" />
          </el-table>
        </div>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { RefreshRight } from '@element-plus/icons-vue'
const collegeName = ref('本院')
const teachers = ref([])
const search = ref('')
const detailVisible = ref(false)
const detail = ref({})
const pageSize = ref(10)
const currentPage = ref(1)

const filtered = computed(() => {
  if (!search.value) return teachers.value
  const kw = search.value.toLowerCase()
  return teachers.value.filter(t =>
    (t.name && t.name.toLowerCase().includes(kw)) ||
    (t.title && t.title.toLowerCase().includes(kw)) ||
    (t.researchAreas && t.researchAreas.toLowerCase().includes(kw))
  )
})
const paginatedTeachers = computed(() => filtered.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
watch(search, () => { currentPage.value = 1 })
const courseTotal = computed(() => teachers.value.reduce((a, t) => a + (t.courseCount || 0), 0))

async function showDetail(row) {
  try {
    const uid = localStorage.getItem('userId')
    const d = await collegeAdminAPI.getTeacherDetail(uid, row.id)
    if (d) { detail.value = d; detailVisible.value = true }
  } catch (e) { console.error(e) }
}
async function loadData() {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  try { teachers.value = await collegeAdminAPI.getTeachers(uid) || [] } catch(e){}
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
.cad-metrics { display: grid; grid-template-columns: repeat(2,1fr); gap: 14px; padding: 20px 24px; }
.cadm-card { background: #fff; border-radius: 14px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.04); display: flex; align-items: center; gap: 14px; }
.cadm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.cadm-icon svg { width: 18px; height: 18px; }
.ic-blue { background:#eff6ff; color:#2563eb; } .ic-green { background:#ecfdf5; color:#16a34a; }
.cadm-val { font-size: 22px; font-weight: 700; color: #1e293b; }
.cadm-lbl { font-size: 12px; color: #94a3b8; }
.cad-content { }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }
@media (max-width:768px) { .cad-metrics { grid-template-columns: 1fr; } .cad-content { padding: 0 12px; } .cad-hero { padding: 16px 20px; } .cadp-head { flex-direction: column; align-items: flex-start; gap: 10px; } }
</style>
