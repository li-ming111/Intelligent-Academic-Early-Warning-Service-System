<template>
  <div class="tw-page">
    <!-- 统计卡片 -->
    <div class="tw-stats">
      <div class="tw-stat danger">
        <div class="ts-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
        <div class="ts-body"><div class="ts-num">{{ warnings.filter(w=>w.warningLevel==='严重').length }}</div><div class="ts-lbl">严重预警</div></div>
      </div>
      <div class="tw-stat warning">
        <div class="ts-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></div>
        <div class="ts-body"><div class="ts-num">{{ warnings.filter(w=>w.warningLevel==='中度').length }}</div><div class="ts-lbl">中度预警</div></div>
      </div>
      <div class="tw-stat info">
        <div class="ts-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg></div>
        <div class="ts-body"><div class="ts-num">{{ warnings.filter(w=>w.warningLevel==='轻度').length }}</div><div class="ts-lbl">轻度预警</div></div>
      </div>
      <div class="tw-stat done">
        <div class="ts-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg></div>
        <div class="ts-body"><div class="ts-num">{{ warnings.filter(w=>warningStatusKey(w.status)==='handled').length }}</div><div class="ts-lbl">已处理</div></div>
      </div>
      <!-- 2026-10-02（W1.6）：补第三态。原来只有 4 格且第 4 格判 `status==='已处理'`
           —— 后端现在会回「已解除」，它既不是"已处理"也没有任何一格能显示，
           学生明明恢复了却在这页面上凭空少一条。 -->
      <div class="tw-stat done">
        <div class="ts-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="8 12 11 15 16 9"/></svg></div>
        <div class="ts-body"><div class="ts-num">{{ warnings.filter(w=>warningStatusKey(w.status)==='dispelled').length }}</div><div class="ts-lbl">已解除</div></div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="tw-toolbar">
      <span class="tw-title">预警列表</span>
      <el-select v-model="filterLevel" placeholder="全部等级" clearable style="width:140px">
        <el-option label="严重" value="严重" />
        <el-option label="中度" value="中度" />
        <el-option label="轻度" value="轻度" />
      </el-select>
    </div>

    <!-- 预警卡片 -->
    <div class="tw-list" v-if="filtered.length">
      <div v-for="(w,i) in filtered" :key="w.id" class="tw-card" :class="'lvl-' + lvlKey(w.warningLevel)" :style="{animationDelay:i*0.06+'s'}">
        <div class="twc-left">
          <div class="twc-avatar" :style="{background:grad(w.studentId)}">{{ (w.studentName||'?').charAt(0) }}</div>
          <div class="twc-info">
            <div class="twc-name">{{ w.studentName }}
              <span class="twc-badge" :class="'bg-' + lvlKey(w.warningLevel)">{{ w.warningLevel }}</span>
            </div>
            <div class="twc-title">{{ w.title || '学业预警' }}</div>
            <div class="twc-desc">{{ w.description }}</div>
            <div class="twc-meta">
              <span>{{ w.className }}</span>
              <span>{{ fmtDate(w.createdAt) }}</span>
              <el-tag size="small" :type="warningStatusTagType(w.status)">{{ warningStatusText(w.status) }}</el-tag>
            </div>
          </div>
        </div>
        <div class="twc-right">
          <el-button size="small" @click="viewDetail(w)">详情</el-button>
          <el-button size="small" type="primary" @click="openHandle(w)" :disabled="!isPendingWarning(w.status)">处理</el-button>
        </div>
      </div>
    </div>
    <div v-else class="tw-empty">暂无预警记录</div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="预警详情" width="500px" destroy-on-close>
      <div v-if="detailRow" class="det-body">
        <div class="det-head" :class="'dl-' + lvlKey(detailRow.warningLevel)">
          <span class="dh-badge">{{ detailRow.warningLevel }}</span>
          <span class="dh-name">{{ detailRow.studentName }}</span>
          <span class="dh-class">{{ detailRow.className }}</span>
        </div>
        <div class="det-grid">
          <div class="dg"><label>预警标题</label><span>{{ detailRow.title }}</span></div>
          <div class="dg"><label>创建时间</label><span>{{ fmtDate(detailRow.createdAt) }}</span></div>
          <div class="dg full"><label>详细说明</label><span>{{ detailRow.description }}</span></div>
        </div>
        <div class="det-tip">
          <svg viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          <span>建议及时联系该学生了解情况，制定针对性帮扶方案。</span>
        </div>
      </div>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleVisible" title="处理预警" width="480px" destroy-on-close>
      <div v-if="handleRow">
        <div class="hdr-info">
          <span>{{ handleRow.studentName }}</span>
          <span class="hdr-title">{{ handleRow.title }}</span>
        </div>
        <el-form label-width="80px" style="margin-top:16px">
          <el-form-item label="帮扶措施">
            <el-select v-model="handleForm.measure" placeholder="选择措施" style="width:100%">
              <el-option label="个别辅导" value="个别辅导" />
              <el-option label="安排补课" value="安排补课" />
              <el-option label="学习小组" value="学习小组" />
              <el-option label="辅导员约谈" value="辅导员约谈" />
              <el-option label="心理疏导" value="心理疏导" />
            </el-select>
          </el-form-item>
          <el-form-item label="沟通记录">
            <el-input v-model="handleForm.communication" type="textarea" rows="3" placeholder="记录沟通内容..." />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="handleVisible=false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { teacherAPI } from '@/api/index'
import { getTeacherId } from '@/utils/userUtils'
// 2026-10-02（W1.6）：teacher-service 这条路的 status 是**中文**，共用层同时吃数字与中文
import { warningStatusText, warningStatusKey, warningStatusTagType, isPendingWarning } from '@/utils/warning'

const warnings = ref([])
const filterLevel = ref('')
const detailVisible = ref(false)
const handleVisible = ref(false)
const detailRow = ref(null)
const handleRow = ref(null)
const handleForm = ref({ measure: '', communication: '' })

const filtered = computed(() => {
  if (!filterLevel.value) return warnings.value
  return warnings.value.filter(w => w.warningLevel === filterLevel.value)
})

onMounted(() => loadWarnings())

async function loadWarnings() {
  try {
    const tid = getTeacherId()
    if (!tid) { console.warn('[TeacherWarnings] 未获取到登录用户 ID，跳过加载'); return }
    const res = await teacherAPI.getWarnings(tid)
    if (Array.isArray(res)) warnings.value = res
  } catch (e) { console.error('加载预警失败', e) }
}

function lvlKey(l) { if (l === '严重') return 'danger'; if (l === '中度') return 'warning'; return 'info' }
function fmtDate(d) { if (!d) return '--'; return String(d).replace('T', ' ').substring(0, 16) }
const colors = ['#7c3aed','#2563eb','#16a34a','#f59e0b','#ef4444','#8b5cf6','#db2777','#ea580c']
function grad(id) {
  const c = colors[(id || 1) % colors.length]
  return `linear-gradient(135deg,${c},${c}cc)`
}

function viewDetail(w) { detailRow.value = w; detailVisible.value = true }
function openHandle(w) { handleRow.value = w; handleForm.value = { measure: '', communication: '' }; handleVisible.value = true }
async function submitHandle() {
  if (!handleForm.value.measure) { ElMessage.error('请选择帮扶措施'); return }
  try {
    await teacherAPI.processWarning(handleRow.value.id, { measures: handleForm.value.measure, remark: handleForm.value.communication })
    ElMessage.success('已处理')
    handleVisible.value = false
    await loadWarnings()
  } catch (e) { ElMessage.error('处理失败') }
}
</script>

<style scoped>
.tw-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }

/* 统计 */
.tw-stats { display: grid; grid-template-columns: repeat(5,1fr); gap: 14px; margin-bottom: 20px; }
.tw-stat { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); border-left: 4px solid #ddd; }
.tw-stat.danger { border-left-color: #ef4444; }
.tw-stat.warning { border-left-color: #f59e0b; }
.tw-stat.info { border-left-color: #3b82f6; }
.tw-stat.done { border-left-color: #16a34a; }
.ts-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.tw-stat.danger .ts-icon { background: #fef2f2; color: #ef4444; }
.tw-stat.warning .ts-icon { background: #fffbeb; color: #f59e0b; }
.tw-stat.info .ts-icon { background: #eff6ff; color: #3b82f6; }
.tw-stat.done .ts-icon { background: #ecfdf5; color: #16a34a; }
.ts-icon svg { width: 20px; height: 20px; }
.ts-num { font-size: 26px; font-weight: 700; color: #18181b; }
.ts-lbl { font-size: 13px; color: #71717a; }

/* 工具栏 */
.tw-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.tw-title { font-size: 16px; font-weight: 600; color: #18181b; }

/* 预警卡片 */
.tw-list { display: flex; flex-direction: column; gap: 10px; }
.tw-card { background: #fff; border-radius: 12px; padding: 16px 20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.04); border-left: 4px solid #ddd; animation: fadeUp .4s ease both; transition: box-shadow .2s; }
.tw-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.tw-card.lvl-danger { border-left-color: #ef4444; }
.tw-card.lvl-warning { border-left-color: #f59e0b; }
.tw-card.lvl-info { border-left-color: #3b82f6; }
@keyframes fadeUp { from{opacity:0;transform:translateY(8px)} to{} }

.twc-left { display: flex; align-items: flex-start; gap: 14px; flex: 1; min-width: 0; }
.twc-avatar { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 16px; font-weight: 700; flex-shrink: 0; }
.twc-info { flex: 1; min-width: 0; }
.twc-name { font-size: 15px; font-weight: 600; color: #18181b; display: flex; align-items: center; gap: 8px; margin-bottom: 3px; }
.twc-badge { font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 10px; }
.twc-badge.bg-danger { background: #fef2f2; color: #ef4444; }
.twc-badge.bg-warning { background: #fffbeb; color: #f59e0b; }
.twc-badge.bg-info { background: #eff6ff; color: #3b82f6; }
.twc-title { font-size: 13px; color: #52525b; margin-bottom: 2px; }
.twc-desc { font-size: 12px; color: #a1a1aa; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.twc-meta { display: flex; gap: 12px; align-items: center; margin-top: 6px; font-size: 12px; color: #a1a1aa; }
.twc-right { flex-shrink: 0; display: flex; gap: 8px; }

.tw-empty { text-align: center; padding: 60px 20px; color: #a1a1aa; font-size: 15px; }

/* 详情 */
.det-head { padding: 12px 16px; border-radius: 8px; display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.det-head.dl-danger { background: #fef2f2; }
.det-head.dl-warning { background: #fffbeb; }
.det-head.dl-info { background: #eff6ff; }
.dh-badge { padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; color: #fff; }
.dl-danger .dh-badge { background: #ef4444; }
.dl-warning .dh-badge { background: #f59e0b; }
.dl-info .dh-badge { background: #3b82f6; }
.dh-name { font-size: 16px; font-weight: 600; }
.dh-class { font-size: 13px; color: #a1a1aa; margin-left: auto; }
.det-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dg.full { grid-column: span 2; }
.dg label { display: block; font-size: 12px; color: #a1a1aa; margin-bottom: 2px; }
.dg span { font-size: 14px; color: #3f3f46; }
.det-tip { margin-top: 16px; background: #fffbeb; border-radius: 8px; padding: 12px 14px; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #92400e; }

.hdr-info { display: flex; align-items: center; gap: 12px; padding: 12px; background: #f5f5f5; border-radius: 8px; font-size: 15px; font-weight: 600; }
.hdr-title { font-size: 13px; color: #71717a; font-weight: 400; }

@media (max-width: 768px) { .tw-stats { grid-template-columns: repeat(2,1fr); } .tw-card { flex-direction: column; } .twc-right { width: 100%; justify-content: flex-end; } }
</style>
