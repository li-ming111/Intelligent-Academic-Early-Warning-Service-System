<template>
  <div class="aw-page">
    <div class="aw-head">
      <div class="awh-left">
        <div class="awh-icon">⚠️</div>
        <div><h2>预警管理</h2><p>全校学业预警列表与处理</p></div>
      </div>
      <div class="awh-actions">
        <button class="awh-btn" @click="refresh">刷新</button>
        <!-- W26：这是**重扫**，不是刷新 —— 见 scanWarnings() 的注释。此前这个能力后端有、
             前端全仓零入口（只能等 02:00 的定时任务，或者手打 curl）。 -->
        <button class="awh-btn" :disabled="scanning" @click="scanWarnings">
          {{ scanning ? '扫描中…' : '重新扫描预警' }}
        </button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="aw-filter">
      <el-select v-model="filterLevel" placeholder="预警等级" clearable size="small" style="width:120px">
        <el-option label="严重" value="3" /><el-option label="中度" value="2" /><el-option label="轻度" value="1" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="处理状态" clearable size="small" style="width:120px">
        <el-option label="待处理" value="0" /><el-option label="已处理" value="1" /><el-option label="已解除" value="2" />
      </el-select>
      <el-input v-model="searchName" placeholder="搜索学生姓名" clearable size="small" style="width:180px" />
    </div>

    <!-- 列表 -->
    <div class="aw-list" v-if="paged.length">
      <div v-for="(w, i) in paged" :key="w.id" class="aw-card" :class="{ processed: !isPendingWarning(w.status) }" :style="{ animationDelay: i * 0.03 + 's' }">
        <div class="awc-bar" :class="'lvl-'+w.warningLevel"></div>
        <div class="awc-body">
          <div class="awcb-top">
            <span class="awcb-name">{{ w.studentName }}</span>
            <span class="awcb-lvl" :class="'lvl-'+w.warningLevel">{{ levelLabel(w.warningLevel) }}</span>
            <!-- 2026-10-02（W1.6）：原来是 `status===1 ? '已处理' : '待处理'`，
                 status=2（已解除）被显示成"待处理" —— 学生分数已经上来了，页面上还挂着待办 -->
            <span class="awcb-sts" :class="warningStatusKey(w.status)">{{ warningStatusText(w.status) }}</span>
          </div>
          <p class="awcb-title">{{ w.title || w.warningMessage || w.description }}</p>
          <div class="awcb-meta">
            <span>{{ w.className || '--' }}</span>
            <span>{{ fmtTime(w.createdAt) }}</span>
          </div>
          <!-- 回读：这三样是**库里真存了什么**（/admin/warnings 直接回 AcademicWarning 实体）。
               原来页面上一个字都不显示 —— 处理完到底记没记下、记的是谁，全靠猜，
               于是"处理成功"这句话没有任何东西能佐证。 -->
          <div class="awcb-meta awcb-handled" v-if="warningStatusKey(w.status) === 'handled'">
            <span>处理人：{{ w.handledBy || '未记录' }}</span>
            <span>{{ fmtTime(w.handledAt) || '--' }}</span>
            <span v-if="w.handleResult">结果：{{ w.handleResult }}</span>
          </div>
        </div>
        <button v-if="isPendingWarning(w.status)" class="awc-btn" @click="quickProcess(w)">处理</button>
        <span v-else class="awc-done" :class="{ 'awc-done--dispelled': warningStatusKey(w.status) === 'dispelled' }">{{ warningStatusText(w.status) }}</span>
      </div>
    </div>
    <div v-else class="aw-empty">暂无预警数据</div>

    <div class="aw-pager" v-if="totalPages > 1">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" small />
    </div>

    <!-- 处理弹窗 -->
    <el-dialog v-model="dialogVisible" title="快速处理" width="380px" destroy-on-close>
      <div v-if="processTarget"><b>{{ processTarget.studentName }}</b> · {{ levelLabel(processTarget.warningLevel) }}预警 · {{ processTarget.title || processTarget.description }}</div>
      <el-input v-model="processNote" type="textarea" rows="2" placeholder="处理备注..." style="margin-top:12px" />
      <!-- AI 预警分析（W26）。端点收预警行 id；无 DASHSCOPE_KEY 时后端回占位串，
           闸在 utils/ai.js 把它换成一句实话，这里不把占位符当分析结果展示。 -->
      <div v-if="processTarget" class="aw-ai">
        <AiInsight title="AI 预警分析" :load="aiAnalyzeWarning" hint="分析这条预警的严重性与原因" />
      </div>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="confirmProcess">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAPI, aiAPI } from '@/api/index'
import AiInsight from '@/components/AiInsight.vue'
// 2026-10-02（W1.6）：状态是三态，文案/配色只在 utils/warning.js 维护一份
import { warningStatusText, warningStatusKey, isPendingWarning } from '@/utils/warning'

const warnings = ref([])
const filterLevel = ref('')
const filterStatus = ref('')
const searchName = ref('')
const page = ref(1)
const pageSize = ref(10)
const dialogVisible = ref(false)
const processTarget = ref(null)
const processNote = ref('')
/** 「重新扫描预警」按钮的加载态（W26）。防连点 —— 后端也有互斥，这里只是别让界面看起来没反应。 */
const scanning = ref(false)

const filtered = computed(() => {
  let list = warnings.value.slice()
  if (filterLevel.value) list = list.filter(w => String(w.warningLevel) === filterLevel.value)
  if (filterStatus.value) list = list.filter(w => String(w.status) === filterStatus.value)
  if (searchName.value) {
    const kw = searchName.value.toLowerCase()
    list = list.filter(w => (w.studentName || '').toLowerCase().includes(kw))
  }
  return list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
})
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const totalPages = computed(() => Math.ceil(filtered.value.length / pageSize.value))

function levelLabel(l) { return l >= 3 ? '严重' : l === 2 ? '中度' : '轻度' }
function fmtTime(t) { if (!t) return ''; const d = new Date(t); return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }

function quickProcess(w) { processTarget.value = w; processNote.value = ''; dialogVisible.value = true }
async function confirmProcess() {
  if (!processTarget.value) return
  const targetId = processTarget.value.id
  // 处理人：登录时 Login.vue 写进 localStorage 的真名。取不到就不传，
  // 后端如实记 NULL（不编一个名字，也不拿备注去顶替处理人 —— 那是这接口原来的错法）。
  const operator = localStorage.getItem('name') || localStorage.getItem('username') || ''
  try {
    const payload = { handleResult: '已处理', remark: processNote.value || '管理员处理' }
    if (operator) payload.handledBy = operator
    await adminAPI.processWarning(targetId, payload)
    dialogVisible.value = false
    // ⚠️ 原来这里是 `processTarget.value.status = 1` —— 本地改一下就当成功了。
    // 请求失败时它也在 await 后面，走 catch，倒还好；但**响应里的 code 从没被看过**。
    // 现在重新向后端要一遍，页面上显示的必须是库里的那一行。
    await loadData()
    const row = (warnings.value || []).find(w => w.id === targetId)
    if (row && row.status === 1) {
      ElMessage.success(`预警已处理（处理人：${row.handledBy || '未记录'}）`)
    } else {
      ElMessage.warning('后端回了成功，但重新拉取后这条仍是待处理 —— 没写进库')
    }
  } catch (e) {
    ElMessage.error('处理失败：' + (e.message || '网络错误'))
  }
}

/**
 * 手动触发一次**全库**预警重扫（W26）。
 *
 * ⚠️ 这不是「刷新列表」。它会真的写库（`academic_warning` / `warning_release_records` /
 * `notifications`），并给每名新预警的学生与其辅导员各发一条通知 —— 所以先弹二次确认，
 * 文案里把"会发通知"说清楚，别让管理员以为点的是一个只读的刷新按钮。
 *
 * 后端只放行辅导员(3)/管理员(4)/学院管理员(5)；其余角色即使手打这个接口也会被拒。
 */
async function scanWarnings() {
  try {
    await ElMessageBox.confirm(
      '这会按当前成绩重扫全校学生：新生成预警、自动解除已恢复的预警，' +
      '并给相关学生与其辅导员发送通知。这不是刷新列表。确认执行吗？',
      '确认重新扫描预警',
      { type: 'warning', confirmButtonText: '开始扫描', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户点了取消
  }
  scanning.value = true
  try {
    const res = await adminAPI.scanWarnings()
    if (res?.skipped) {
      ElMessage.warning(res.message || '已有一轮扫描正在进行中，本次未重复执行')
    } else {
      // 两个数都要说出来：只报「新生成 0 条」会把「自动解除了 N 条」这件事藏起来。
      const generated = res?.generatedCount ?? 0
      const released = res?.releasedCount ?? 0
      ElMessage.success(`扫描完成：新生成 ${generated} 条，自动解除 ${released} 条`)
    }
    await loadData()
  } catch (e) {
    ElMessage.error('扫描失败：' + (e.message || '网络错误'))
  } finally {
    scanning.value = false
  }
}

async function refresh() { await loadData(); ElMessage.success('已刷新') }
async function loadData() {
  try {
    // 优先使用独立预警API获取全量数据
    const wRes = await adminAPI.getAllWarnings()
    if (wRes && Array.isArray(wRes)) {
      warnings.value = wRes
      return
    }
    // fallback to dashboard
    const dRes = await adminAPI.getDashboard()
    if (dRes) {
      const wlist = dRes.recentWarnings || dRes.warnings || []
      warnings.value = Array.isArray(wlist) ? wlist : []
    }
  } catch (e) {
    console.error('加载预警失败:', e)
    try {
      // final fallback
      const dRes = await adminAPI.getDashboard()
      if (dRes) {
        const wlist = dRes.recentWarnings || dRes.warnings || []
        warnings.value = Array.isArray(wlist) ? wlist : []
      }
    } catch (e2) { console.error(e2) }
  }
}
onMounted(loadData)

/** AI 预警分析（W26）。端点收的是**预警行 id**。 */
function aiAnalyzeWarning () {
  const w = processTarget.value
  if (!w || w.id == null) throw new Error('请先打开一条预警')
  return aiAPI.analyzeWarning(w.id)
}
</script>

<style scoped>
.aw-page { padding: 20px 24px; min-height: 100vh; background: #f0f4ff; }
.aw-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.awh-left { display: flex; align-items: center; gap: 12px; }
.awh-icon { width: 44px; height: 44px; border-radius: 12px; background: #eff6ff; display: flex; align-items: center; justify-content: center; font-size: 20px; }
.awh-left h2 { margin: 0; font-size: 18px; color: #1e3a5f; } .awh-left p { margin: 2px 0 0; font-size: 12px; color: #94a3b8; }
.awh-btn { padding: 7px 16px; border-radius: 8px; border: 1px solid #e2e8f0; background: #fff; color: #475569; font-size: 12px; cursor: pointer; }
.awh-btn:hover { border-color: #93c5fd; color: #2563eb; }
.awh-actions { display: flex; gap: 8px; align-items: center; }
.awh-btn:disabled { opacity: .55; cursor: not-allowed; }
.awh-btn:disabled:hover { border-color: #e2e8f0; color: #475569; }
.aw-filter { display: flex; gap: 10px; margin-bottom: 16px; }
.aw-list { display: flex; flex-direction: column; gap: 8px; }
.aw-card { display: flex; align-items: center; gap: 12px; background: #fff; border-radius: 12px; padding: 14px 18px; box-shadow: 0 1px 3px rgba(0,0,0,.03); animation: fadeUp .4s ease both; }
.aw-card.processed { opacity: .75; }
.awc-bar { width: 4px; border-radius: 4px; align-self: stretch; flex-shrink: 0; }
.awc-bar.lvl-3 { background: #ef4444; } .awc-bar.lvl-2 { background: #f59e0b; } .awc-bar.lvl-1 { background: #3b82f6; }
.awc-body { flex: 1; min-width: 0; }
.awcb-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.awcb-lvl,.awcb-sts { font-size: 10px; padding: 1px 7px; border-radius: 8px; font-weight: 600; margin-left: 6px; }
.awcb-lvl.lvl-3 { background: #fef2f2; color: #ef4444; } .awcb-lvl.lvl-2 { background: #fffbeb; color: #d97706; } .awcb-lvl.lvl-1 { background: #eff6ff; color: #2563eb; }
/* 三态三色。原来只有 pending / ok 两支（`ok` 现在叫 `handled`），
   2026-10-02（W1.6）补 `dispelled` —— 中性灰，与"已处理"的绿区分开：
   绿色是"辅导员处理完了"，灰色是"分数上来了、系统自动写的"。 */
.awcb-sts.pending { background: #fef2f2; color: #ef4444; }
.awcb-sts.handled { background: #ecfdf5; color: #16a34a; }
.awcb-sts.dispelled { background: #f3f4f6; color: #6b7280; }
.awcb-title { margin: 4px 0; font-size: 12px; color: #64748b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 360px; }
.awcb-meta { display: flex; gap: 10px; font-size: 11px; color: #94a3b8; }
.awc-btn { padding: 5px 14px; border-radius: 6px; border: 1px solid #93c5fd; background: #eff6ff; color: #2563eb; font-size: 12px; cursor: pointer; transition: all .2s; flex-shrink: 0; }
.awc-btn:hover { background: #2563eb; color: #fff; }
.awc-done { font-size: 12px; color: #16a34a; flex-shrink: 0; }
.awc-done--dispelled { color: #6b7280; }
.aw-empty { text-align: center; padding: 60px 20px; color: #94a3b8; font-size: 14px; }
.aw-pager { display: flex; justify-content: center; padding-top: 18px; }
@keyframes fadeUp { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
.aw-ai { margin-top: 10px; }
</style>
