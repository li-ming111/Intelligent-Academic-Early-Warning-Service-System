<template>
  <div class="assistance-page">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="st-icon total"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/></svg></div>
        <div class="st-body"><div class="st-num">{{ plans.length }}</div><div class="st-lbl">全部计划</div></div>
      </div>
      <div class="stat-card">
        <div class="st-icon active"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
        <div class="st-body"><div class="st-num">{{ activeCount }}</div><div class="st-lbl">进行中</div></div>
      </div>
      <div class="stat-card">
        <div class="st-icon done"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg></div>
        <div class="st-body"><div class="st-num">{{ doneCount }}</div><div class="st-lbl">已完成</div></div>
      </div>
      <div class="stat-card">
        <div class="st-icon prog"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg></div>
        <div class="st-body"><div class="st-num">{{ avgProgress }}%</div><div class="st-lbl">平均进度</div></div>
      </div>
    </div>

    <!-- 计划列表 -->
    <div class="plans-grid" v-if="plans.length > 0">
      <div v-for="p in plans" :key="p.id" class="plan-card" :class="{ completed: p.statusCode === 2 }">
        <!-- 卡片头部 -->
        <div class="plan-top">
          <div class="plan-title-row">
            <span class="plan-title">{{ p.title }}</span>
            <el-tag size="small" :type="statusType(p.status)">{{ p.status }}</el-tag>
          </div>
          <div class="plan-sub">{{ p.description }}</div>
        </div>

        <!-- 信息区。
             2026-10-01 第二十一轮: 原来这三块读 courseName/teacherName/startDate —— 后端从来不返回,
             而且 assistance_plans 表**根本没有** course_id / start_date / end_date 列(只有
             counselor_id/student_id/content/status/created_at/updated_at/progress),
             即"目标课程/计划开始"在库层面没有数据源 ⇒ 这三块删掉, 换成真有的两个:
             辅导辅导员(counselorName, counselor_id 就是 users.id)与创建时间。 -->
        <div class="plan-info">
          <div class="pi-item" v-if="p.counselorName">
            <svg viewBox="0 0 24 24" fill="none" stroke="#a1a1aa" stroke-width="2" width="14" height="14"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <span>{{ p.counselorName }}</span>
          </div>
          <div class="pi-item" v-if="p.createdAt">
            <svg viewBox="0 0 24 24" fill="none" stroke="#a1a1aa" stroke-width="2" width="14" height="14"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            <span>{{ formatDate(p.createdAt) }}</span>
          </div>
        </div>

        <!-- 进度条 -->
        <div class="plan-progress">
          <div class="pp-header">
            <span>完成进度</span>
            <span class="pp-val">{{ p.progress || 0 }}%</span>
          </div>
          <div class="pp-bar"><div class="pp-fill" :style="{ width: (p.progress || 0) + '%' }" :class="progressClass(p.progress)"></div></div>
        </div>

        <!-- 操作 -->
        <div class="plan-actions">
          <el-button size="small" @click="viewDetail(p)">详情</el-button>
          <el-button size="small" type="primary" plain @click="openProgress(p)" :disabled="p.status==='已完成'">更新进度</el-button>
        </div>
      </div>
    </div>

    <!-- 空 -->
    <div v-else class="empty-box">
      <svg viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5"><path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>
      <p>暂无帮扶计划</p>
      <span>辅导员将根据学业情况为您创建帮扶计划</span>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="计划详情" width="600px" destroy-on-close>
      <div v-if="detailPlan" class="det-body">
        <div class="det-header">
          <h3>{{ detailPlan.title }}</h3>
          <el-tag :type="statusType(detailPlan.status)">{{ detailPlan.status }}</el-tag>
        </div>
        <div class="det-grid">
          <div class="dg-item"><label>辅导辅导员</label><span>{{ detailPlan.counselorName || '--' }}</span></div>
          <div class="dg-item"><label>创建时间</label><span>{{ formatDate(detailPlan.createdAt) || '--' }}</span></div>
          <div class="dg-item"><label>最近更新</label><span>{{ formatDate(detailPlan.updatedAt) || '--' }}</span></div>
          <div class="dg-item full"><label>计划描述</label><span>{{ detailPlan.description }}</span></div>
        </div>
        <el-divider />
        <div class="det-progress">
          <div class="pp-header"><span>当前进度</span><span class="pp-val">{{ detailPlan.progress || 0 }}%</span></div>
          <div class="pp-bar"><div class="pp-fill" :style="{ width: (detailPlan.progress || 0) + '%' }" :class="progressClass(detailPlan.progress)"></div></div>
        </div>
        <el-alert type="info" :closable="false" show-icon style="margin-top:16px">
          坚持完成帮扶计划，定期与教师沟通，有效提升学业成绩。
        </el-alert>
      </div>
    </el-dialog>

    <!-- 更新进度弹窗 -->
    <el-dialog v-model="progressVisible" title="更新帮扶进度" width="480px" destroy-on-close>
      <el-form :model="progressForm" label-width="80px">
        <el-form-item label="计划名称"><el-input :model-value="progressForm.title" disabled /></el-form-item>
        <el-form-item label="当前进度">
          <el-slider v-model="progressForm.percent" :min="0" :max="100" :step="5" show-input />
        </el-form-item>
        <el-form-item label="完成情况" required>
          <el-input v-model="progressForm.feedback" type="textarea" rows="3" placeholder="描述本次完成情况..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProgress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const plans = ref([])
const detailVisible = ref(false)
const progressVisible = ref(false)
const detailPlan = ref(null)
const progressForm = ref({ id: null, title: '', percent: 0, feedback: '' })

onMounted(async () => {
  const uid = getUserId()
  if (uid) await loadPlans()
})

async function loadPlans() {
  try {
    const uid = getUserId()
    const res = await studentAPI.getAssistancePlans(uid)
    if (Array.isArray(res)) {
      plans.value = res.map(p => ({
        ...p,
        title: p.title || p.description || '学业帮扶计划',
        // 2026-10-01 第二十一轮: 原来只有 translateStatus(p.status), 而它只认字符串
        // ('进行中'/'已完成'), 后端给的却是**数值** 0/1/2 ⇒ 1、2 都落到兜底「未开始」,
        // 于是 4 条计划全显示「未开始」、「进行中/已完成」计数恒 0。
        // 后端本来就给了 statusText, 优先用它; statusCode 留给计数与样式判断。
        status: p.statusText || translateStatus(p.status),
        statusCode: p.status,
        progress: p.progress || 0,
        counselorName: p.counselorName || ''
      }))
    }
  } catch (e) { console.error('加载帮扶计划失败', e) }
}

const activeCount = computed(() => plans.value.filter(p => p.statusCode === 1).length)
const doneCount = computed(() => plans.value.filter(p => p.statusCode === 2).length)
const avgProgress = computed(() => {
  if (!plans.value.length) return 0
  return Math.round(plans.value.reduce((s, p) => s + (p.progress || 0), 0) / plans.value.length)
})

function translateStatus(s) {
  // 兜底：后端 status 是数值 0/1/2（也有服务间传字符串的历史写法，两者都认）
  if (s === 1 || s === '1' || s === 'in_progress' || s === '进行中') return '进行中'
  if (s === 2 || s === '2' || s === 'completed' || s === '已完成') return '已完成'
  return '未开始'
}
function statusType(s) {
  if (s === '进行中') return 'primary'
  if (s === '已完成') return 'success'
  return 'info'
}
function formatDate(d) {
  if (!d) return ''
  return String(d).replace('T', ' ').substring(0, 10)
}
function progressClass(p) {
  if (p >= 80) return 'high'
  if (p >= 40) return 'mid'
  return 'low'
}

function viewDetail(p) { detailPlan.value = p; detailVisible.value = true }
function openProgress(p) {
  progressForm.value = { id: p.id, title: p.title, percent: p.progress || 0, feedback: '' }
  progressVisible.value = true
}
async function submitProgress() {
  if (!progressForm.value.feedback) { ElMessage.error('请填写完成情况'); return }
  try {
    // 2026-10-02 第二十五轮：第三个参数（完成情况）以前根本不存在 —— 文本域是必填的，
    // 校验过了却不发，学生写的内容凭空消失。现在真发出去并落库到 progress_note。
    await studentAPI.updatePlanProgress(
      progressForm.value.id,
      progressForm.value.percent,
      progressForm.value.feedback
    )
    ElMessage.success('进度已更新')
    progressVisible.value = false
    await loadPlans()
  } catch (e) { ElMessage.error('更新失败') }
}
</script>

<style scoped>
.assistance-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }

/* 统计 */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 20px; }
.stat-card { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.05); }
.st-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.st-icon svg { width: 20px; height: 20px; }
.st-icon.total { background: #eff6ff; color: #3b82f6; }
.st-icon.active { background: #fef3c7; color: #f59e0b; }
.st-icon.done { background: #ecfdf5; color: #16a34a; }
.st-icon.prog { background: #f5f3ff; color: #7c3aed; }
.st-num { font-size: 24px; font-weight: 700; color: #18181b; }
.st-lbl { font-size: 13px; color: #71717a; }

/* 计划卡片 2列网格 */
.plans-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.plan-card { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,.05); display: flex; flex-direction: column; gap: 14px; border-top: 4px solid #7c3aed; transition: box-shadow .2s; }
.plan-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.plan-card.completed { border-top-color: #16a34a; opacity: .85; }

.plan-title-row { display: flex; align-items: center; justify-content: space-between; }
.plan-title { font-size: 15px; font-weight: 600; color: #18181b; }
.plan-sub { font-size: 13px; color: #71717a; line-height: 1.5; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.plan-info { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.pi-item { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #52525b; }
.pi-item svg { flex-shrink: 0; }

.plan-progress { }
.pp-header { display: flex; justify-content: space-between; font-size: 13px; color: #52525b; margin-bottom: 6px; }
.pp-val { font-weight: 600; color: #7c3aed; }
.pp-bar { height: 8px; background: #f4f4f5; border-radius: 4px; overflow: hidden; }
.pp-fill { height: 100%; border-radius: 4px; transition: width .4s; }
.pp-fill.high { background: linear-gradient(90deg, #16a34a, #22c55e); }
.pp-fill.mid { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.pp-fill.low { background: linear-gradient(90deg, #ef4444, #f87171); }

.plan-actions { display: flex; gap: 8px; justify-content: flex-end; }

/* 空 */
.empty-box { text-align: center; padding: 80px 20px; color: #a1a1aa; }
.empty-box svg { width: 60px; margin-bottom: 12px; }
.empty-box p { font-size: 15px; margin: 0 0 4px; }
.empty-box span { font-size: 13px; }

/* 详情弹窗 */
.det-body { line-height: 1.6; }
.det-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.det-header h3 { margin: 0; font-size: 16px; }
.det-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.dg-item.full { grid-column: span 2; }
.dg-item label { display: block; font-size: 12px; color: #a1a1aa; margin-bottom: 2px; }
.dg-item span { font-size: 14px; color: #3f3f46; }
.det-progress { margin-top: 8px; }

@media (max-width: 900px) { .plans-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .stats-row { grid-template-columns: repeat(2, 1fr); } }
</style>
