<template>
  <div class="cad-page">
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner"><div class="cad-hleft"><h1>辅导员管理</h1><p class="cad-hsub">{{ collegeName }} · {{ filtered.length }} / {{ counselors.length }} 名辅导员</p></div></div>
    </div>
    <div class="cad-content">
      <div class="cad-panel">
        <div class="cadp-head">
          <span class="cadp-title">辅导员列表</span>
          <div style="display:flex;gap:10px">
            <el-input v-model="search" placeholder="搜索姓名/管辖班级" clearable size="small" style="width:220px" />
            <el-button size="small" type="primary" :icon="Download" @click="exportCounselors">导出</el-button>
          </div>
        </div>
        <el-table :data="paginatedCounselors" stripe size="small" max-height="60vh" v-if="counselors.length" style="width:100%">
          <el-table-column prop="name" label="姓名" min-width="80" />
          <el-table-column prop="phone" label="电话" min-width="120" />
          <el-table-column prop="classCount" label="管辖班级数" width="100" align="center" sortable />
          <el-table-column prop="classes" label="管辖班级" min-width="250" show-overflow-tooltip />
        </el-table>
        <el-pagination v-if="counselors.length" v-model:current-page="currentPage" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next, total" small background class="cad-pager" />
        <div v-else class="cadp-empty">暂无辅导员数据</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { collegeAdminAPI } from '@/api/index'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const collegeName = ref('本院')
const counselors = ref([])
const search = ref('')
const filtered = computed(() => {
  if (!search.value) return counselors.value
  const kw = search.value.toLowerCase()
  return counselors.value.filter(c =>
    (c.name && c.name.toLowerCase().includes(kw)) ||
    (c.classes && c.classes.toLowerCase().includes(kw))
  )
})
const pageSize = ref(10)
const currentPage = ref(1)
const paginatedCounselors = computed(() => filtered.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
watch(search, () => { currentPage.value = 1 })
function exportCounselors() {
  if (!counselors.value.length) { ElMessage.warning('无数据'); return }
  const keys = ['姓名', '电话', '管辖班级数', '管辖班级']
  const rows = counselors.value.map(c => [c.name, c.phone, c.classCount, c.classes])
  const csv = ['\uFEFF' + keys.join(','), ...rows.map(r => r.map(v => '"' + String(v || '').replace(/"/g,'""') + '"').join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const u = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = u; a.download = `辅导员_${collegeName.value}.csv`; a.click()
  URL.revokeObjectURL(u); ElMessage.success('导出成功')
}
onMounted(async () => {
  const uid = localStorage.getItem('userId'); if (!uid) return
  try { const p = await collegeAdminAPI.getProfile(uid); if (p) collegeName.value = p.college_name || '本院' } catch(e){ console.error('getProfile err:', e) }
  try { const data = await collegeAdminAPI.getCounselors(uid); counselors.value = data || [] } catch(e){ console.error('getCounselors err:', e) }
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
.cad-content { margin-top: 0; }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.cadp-title { font-size: 15px; font-weight: 600; color: #333; }
.cadp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }
.cad-pager { padding: 12px 22px; display: flex; justify-content: center; }
@media (max-width:768px) { .cad-content { padding: 0 12px; padding-top: 16px; } .cad-hero { padding: 16px 20px; } .cadp-head { flex-direction: column; align-items: flex-start; gap: 10px; } }
</style>
