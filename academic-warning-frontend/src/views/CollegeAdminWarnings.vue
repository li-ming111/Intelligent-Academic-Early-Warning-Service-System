<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner"><div class="cad-hleft"><h1>预警管理</h1><p class="cad-hsub">{{ collegeName }} · {{ filtered.length }} / {{ warnings.length }} 条预警</p></div></div>
    </div>
    <div class="cad-metrics">
      <div class="cadm-card"><div class="cadm-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><div class="cadm-body"><div class="cadm-val red">{{ warnings.length }}</div><div class="cadm-lbl">预警总数</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18 M6 6l12 12"/></svg></div><div class="cadm-body"><div class="cadm-val red">{{ warnCounts.severe }}</div><div class="cadm-lbl">严重预警</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg></div><div class="cadm-body"><div class="cadm-val" style="color:#d97706">{{ warnCounts.medium }}</div><div class="cadm-lbl">中度预警</div></div></div>
      <div class="cadm-card"><div class="cadm-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg></div><div class="cadm-body"><div class="cadm-val" style="color:#2563eb">{{ warnCounts.low }}</div><div class="cadm-lbl">轻度预警</div></div></div>
    </div>
    <div class="cad-content">
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">预警列表</span>
          <div style="display:flex;gap:10px;align-items:center">
            <el-select v-model="levelFilter" placeholder="等级筛选" clearable size="small" style="width:100px">
              <el-option label="严重" :value="3" /><el-option label="中度" :value="2" /><el-option label="轻度" :value="1" />
            </el-select>
            <el-select v-model="statusFilter" placeholder="状态" clearable size="small" style="width:90px">
              <el-option label="待处理" :value="0" /><el-option label="已处理" :value="1" />
            </el-select>
            <el-input v-model="search" placeholder="搜索学生/班级" clearable size="small" style="width:160px" />
            <el-button size="small" type="primary" :icon="RefreshRight" @click="loadData">刷新</el-button>
          </div>
        </div>
        <el-table :data="paginatedWarnings" stripe size="small" max-height="58vh" v-if="warnings.length" style="width:100%">
          <el-table-column prop="studentName" label="学生" min-width="80" />
          <el-table-column prop="className" label="班级" min-width="110" />
          <el-table-column label="等级" width="65" align="center">
            <template #default="{ row }">
              <el-tag :type="(row.warningLevel || row.level || 0) >= 3 ? 'danger' : (row.warningLevel || row.level || 0) >= 2 ? 'warning' : 'info'" size="small">
                {{ levelLabel(row.warningLevel || row.level) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status == 1 ? 'success' : 'warning'" size="small">{{ row.status == 1 ? '已处理' : '待处理' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="105" align="center">
            <template #default="{ row }">{{ fmtDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status != 1" link type="success" size="small" @click.stop="markProcessed(row)">标记已处理</el-button>
              <el-button link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="warnings.length" v-model:current-page="currentPage" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next, total" small background class="cad-pager" />
        <div v-else class="cadp-empty">暂无预警数据</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const collegeName = ref('本院')
const warnings = ref([])
const search = ref('')
const levelFilter = ref(null)
const statusFilter = ref(null)
const pageSize = ref(10)
const currentPage = ref(1)

const filtered = computed(() => {
  let list = warnings.value
  if (levelFilter.value) list = list.filter(w => (w.warningLevel || w.level || 0) >= levelFilter.value && (w.warningLevel || w.level || 0) < levelFilter.value + 1)
  if (statusFilter.value !== null && statusFilter.value !== '') list = list.filter(w => (w.status || 0) == statusFilter.value)
  if (search.value) {
    const kw = search.value.toLowerCase()
    list = list.filter(w => (w.studentName && w.studentName.toLowerCase().includes(kw)) || (w.className && w.className.toLowerCase().includes(kw)))
  }
  return list
})
const paginatedWarnings = computed(() => filtered.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
watch([search, levelFilter, statusFilter], () => { currentPage.value = 1 })
const warnCounts = computed(() => ({
  severe: warnings.value.filter(w => (w.warningLevel || w.level || 0) >= 3).length,
  medium: warnings.value.filter(w => (w.warningLevel || w.level || 0) == 2).length,
  low: warnings.value.filter(w => (w.warningLevel || w.level || 0) == 1).length
}))

function levelLabel(l) { const v = Number(l) || 0; return v >= 3 ? '严重' : v == 2 ? '中度' : '轻度' }
function fmtDate(t) { if (!t) return ''; try { return new Date(t).toLocaleDateString('zh-CN') } catch(e) { return String(t).substring(0,10) } }

async function markProcessed(row) {
  try {
    await collegeAdminAPI.processWarning({ id: row.id, status: 1 })
    row.status = 1
    ElMessage.success('已标记为已处理')
  } catch (e) { ElMessage.error('操作失败') }
}
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定要删除该预警记录吗？此操作不可恢复。', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await collegeAdminAPI.deleteWarning(row.id)
    warnings.value = warnings.value.filter(w => w.id !== row.id)
    ElMessage.success('已删除')
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}
async function loadData() {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){}
  try { warnings.value = await collegeAdminAPI.getWarnings(uid) || [] } catch(e){}
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
.cadm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.cadm-icon svg { width: 18px; height: 18px; }
.ic-red { background:#fef2f2; color:#ef4444; } .ic-amber { background:#fffbeb; color:#d97706; } .ic-blue { background:#eff6ff; color:#2563eb; }
.cadm-val { font-size: 22px; font-weight: 700; color: #1e293b; } .cadm-val.red { color: #ef4444; }
.cadm-lbl { font-size: 12px; color: #94a3b8; }
.cad-content { }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; flex-wrap: wrap; gap: 8px; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }
@media (max-width:900px) { .cad-metrics { grid-template-columns: repeat(2,1fr); } }
@media (max-width:600px) { .cad-metrics { grid-template-columns: 1fr; } .cad-hero { padding: 16px 20px; } .cad-content { padding: 0 12px; } }
</style>
