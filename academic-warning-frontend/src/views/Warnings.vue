<template>
  <div class="warnings-page">
    <!-- 顶部统计 -->
    <div class="stats-row">
      <div class="stat-card danger">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
        <div class="stat-body"><div class="stat-num">{{ redWarnings.length }}</div><div class="stat-lbl">严重预警</div></div>
      </div>
      <div class="stat-card warning">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></div>
        <div class="stat-body"><div class="stat-num">{{ yellowWarnings.length }}</div><div class="stat-lbl">中度预警</div></div>
      </div>
      <div class="stat-card info">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg></div>
        <div class="stat-body"><div class="stat-num">{{ blueWarnings.length }}</div><div class="stat-lbl">轻度预警</div></div>
      </div>
      <div class="stat-card appeal">
        <div class="stat-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg></div>
        <div class="stat-body"><div class="stat-num">{{ pendingAppeals.length }}</div><div class="stat-lbl">申诉记录</div></div>
      </div>
    </div>

    <!-- 标题栏 -->
    <div class="toolbar">
      <span class="toolbar-title">预警列表</span>
      <el-select v-model="filterLevel" placeholder="全部等级" clearable style="width:140px" size="default">
        <el-option label="严重预警" value="red"><span style="color:#ef4444">●</span> 严重预警</el-option>
        <el-option label="中度预警" value="yellow"><span style="color:#3b82f6">●</span> 中度预警</el-option>
        <el-option label="轻度预警" value="blue"><span style="color:#3b82f6">●</span> 轻度预警</el-option>
      </el-select>
    </div>

    <!-- 预警卡片列表 -->
    <div class="warning-list" v-if="filteredWarnings.length > 0">
      <div v-for="w in filteredWarnings" :key="w.id" class="warning-card" :class="'level-' + getLevelKey(w.warningLevel)">
        <div class="wc-level">
          <div class="level-badge" :class="getLevelKey(w.warningLevel)">{{ w.warningLevel || '--' }}</div>
        </div>
        <div class="wc-body">
          <div class="wc-title">{{ w.title || '学业预警' }}</div>
          <div class="wc-desc">{{ w.description }}</div>
          <div class="wc-meta">
            <span class="wc-tag" v-if="w.courseName">{{ w.courseName }}</span>
            <span class="wc-time">{{ formatDate(w.createdAt) }}</span>
            <!-- 2026-10-02（W1.6）：原来是两态 `status===1 ? '已处理' : '待处理'`，
                 status=2（已解除）会显示成"待处理" —— 学生分数已经上来了，通知里还说他待处理 -->
            <el-tag size="small" :type="warningStatusTagType(w.status)">{{ warningStatusText(w.status) }}</el-tag>
          </div>
        </div>
        <div class="wc-actions">
          <el-button type="primary" plain size="small" @click="viewDetails(w)">详情</el-button>
          <el-button type="warning" plain size="small" @click="openAppealDialog(w)">申诉</el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-box">
      <svg viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>
      <p>暂无预警记录</p>
    </div>

    <!-- 申诉记录 -->
    <div class="toolbar" style="margin-top:24px" v-if="pendingAppeals.length > 0">
      <span class="toolbar-title">申诉记录</span>
    </div>
    <div class="appeal-list" v-if="pendingAppeals.length > 0">
      <div v-for="a in pendingAppeals" :key="a.id" class="appeal-card">
        <div class="ap-info">
          <span class="ap-course">{{ a.courseName }}</span>
          <span class="ap-reason">{{ a.reason }}</span>
        </div>
        <!-- 2026-10-01 第二十一轮: 这里原来判 a.status==='pending'/'approved' —— 后端给的是**数值**
             0/1/2/3, 三个字符串判断全不成立 ⇒ 状态标签**恒走 else 显示「已驳回」**(待处理的申诉
             被标成已驳回); 时间读的是 a.submittedAt, 后端只有 createdAt(现已补 statusText)。 -->
        <div class="ap-meta">
          <el-tag size="small" :type="appealStatusType(a.status)">{{ a.statusText || appealStatusName(a.status) }}</el-tag>
          <span class="ap-time">{{ formatDate(a.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="预警详情" width="560px" destroy-on-close>
      <div v-if="selectedWarning" class="detail-body">
        <div class="detail-header" :class="'dl-' + getLevelKey(selectedWarning.warningLevel)">
          <span class="dl-level">{{ selectedWarning.warningLevel }}</span>
          <span class="dl-title">{{ selectedWarning.title }}</span>
        </div>
        <div class="detail-grid">
          <div class="dg-item"><label>关联课程</label><span>{{ selectedWarning.courseName || '--' }}</span></div>
          <div class="dg-item"><label>创建时间</label><span>{{ formatDate(selectedWarning.createdAt) }}</span></div>
          <div class="dg-item full"><label>详细说明</label><span>{{ selectedWarning.description }}</span></div>
        </div>
        <el-divider />
        <div class="detail-suggest">
          <h4>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            建议行动
          </h4>
          <ul>
            <li>联系任课教师了解详细情况</li>
            <li>制定针对性的学习计划，重点关注薄弱环节</li>
            <li>参加辅导答疑或学习小组</li>
            <li>如有异议，可提交成绩申诉</li>
          </ul>
        </div>

        <!-- AI 预警分析（W26）：这两个 aiAPI 端点此前全仓零调用。
             注意底下那个「生成应对建议」要先把本页的等级口径映射成后端认的词，
             否则后端 switch 落到 default、算出"正常"报警 —— 见 aiGenerateWarning 的注释。 -->
        <el-divider />
        <div class="detail-ai">
          <AiInsight title="AI 预警分析" :load="aiAnalyzeWarning" hint="分析这条预警的严重性与原因" />
          <AiInsight title="AI 生成应对建议" :load="aiGenerateWarning" hint="按这条预警的等级与说明生成建议" />
        </div>
      </div>
    </el-dialog>

    <!-- 申诉弹窗 -->
    <el-dialog v-model="appealVisible" title="提交申诉" width="520px" destroy-on-close>
      <el-form :model="appealForm" label-width="80px">
        <el-form-item label="预警课程"><el-input :model-value="appealForm.courseName" disabled /></el-form-item>
        <el-form-item label="预警标题"><el-input :model-value="appealForm.warningTitle" disabled /></el-form-item>
        <el-form-item label="申诉原因" required>
          <el-select v-model="appealForm.appealReason" placeholder="请选择申诉原因" style="width:100%">
            <el-option label="成绩录入有误" value="成绩录入有误" />
            <el-option label="评分标准不明确" value="评分标准不明确" />
            <el-option label="特殊情况影响考试" value="特殊情况影响考试" />
            <el-option label="系统数据异常" value="系统数据异常" />
            <el-option label="其他原因" value="其他原因" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细说明">
          <el-input v-model="appealForm.appealDesc" type="textarea" rows="3" placeholder="请详细说明您的申诉理由..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentAPI, aiAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import AiInsight from '@/components/AiInsight.vue'
import { warningLevelText, warningLevelTagType } from '@/utils/score'
import { warningStatusText, warningStatusTagType } from '@/utils/warning'

const filterLevel = ref('')
const allWarnings = ref([])
const pendingAppeals = ref([])
const detailVisible = ref(false)
const appealVisible = ref(false)
const selectedWarning = ref(null)
const appealForm = ref({ warningId: null, courseName: '', warningTitle: '', appealReason: '', appealDesc: '' })

onMounted(async () => {
  const uid = getUserId()
  if (!uid) { ElMessage.error('请先登录'); return }
  await loadWarnings()
  await loadAppeals()
})

async function loadWarnings() {
  try {
    const uid = getUserId()
    const res = await studentAPI.getWarnings(uid)
    if (Array.isArray(res)) {
      // W39：`courseName` 原来是用正则从 description 散文里**抠**出来的（见已删除的
      // extractCourseName），抠不出来就空着、抠错了就是编造。后端现在回真实明细
      // `courses`（academic_warning_course）与拼好的 `courseNames`，直接用。
      allWarnings.value = res.map(w => ({
        ...w,
        warningLevel: translateLevel(w.warningLevel),
        courseName: w.courseNames || ''
      }))
    }
  } catch (e) { console.error('加载预警失败', e) }
}

async function loadAppeals() {
  try {
    const uid = getUserId()
    const res = await studentAPI.getAppeals(uid)
    if (Array.isArray(res)) pendingAppeals.value = res
  } catch (e) { console.error('加载申诉失败', e) }
}

const redWarnings = computed(() => allWarnings.value.filter(w => w.warningLevel === '严重'))
const yellowWarnings = computed(() => allWarnings.value.filter(w => w.warningLevel === '中度'))
const blueWarnings = computed(() => allWarnings.value.filter(w => w.warningLevel === '轻度'))
const filteredWarnings = computed(() => {
  if (!filterLevel.value) return allWarnings.value
  const map = { red: '严重', yellow: '中度', blue: '轻度' }
  return allWarnings.value.filter(w => w.warningLevel === map[filterLevel.value])
})

/**
 * 等级归一化，统一交给 utils/score 的 warningLevel 处理。
 *
 * 原实现是一串手写比较，两个具体问题：
 *   - 后端 `warningLevel` 是数字，而这里先跟数字比、再跟中文字符串比。
 *     字符串形态（"1"）会因为 `'2' === 2` 为 false 而落进默认分支，
 *     2 级预警被显示成"轻度"。
 *   - 默认分支返回"轻度"，于是 `null` / 未知等级也被算成一条轻度预警，
 *     三个统计数字里始终有一条是虚的。
 * 现在等级无法识别时返回"未知"，不再冒充轻度。
 */
function translateLevel(level) {
  return warningLevelText(level)
}
function getLevelKey(level) {
  return warningLevelTagType(level)
}
function formatDate(d) {
  if (!d) return '--'
  const s = String(d).replace('T', ' ').substring(0, 16)
  return s
}
// 申诉状态：后端 status 是数值 0 待处理 / 1 已通过 / 2 已驳回 / 3 已撤回（与 Appeals.vue 同口径）。
function appealStatusName(s) {
  if (s === 1) return '已通过'
  if (s === 2) return '已驳回'
  if (s === 3) return '已撤回'
  return '待处理'
}
function appealStatusType(s) {
  if (s === 1) return 'success'
  if (s === 2) return 'danger'
  if (s === 3) return 'info'
  return 'warning'
}
function viewDetails(row) { selectedWarning.value = row; detailVisible.value = true }
function openAppealDialog(row) {
  appealForm.value = { warningId: row.id, courseName: row.courseName || '', warningTitle: row.title || '学业预警', appealReason: '', appealDesc: '' }
  appealVisible.value = true
}
async function submitAppeal() {
  if (!appealForm.value.appealReason) { ElMessage.error('请选择申诉原因'); return }
  try {
    await studentAPI.submitAppeal({
      studentId: getUserId(),
      warningId: appealForm.value.warningId,
      reason: appealForm.value.appealReason,
      description: appealForm.value.appealDesc
    })
    ElMessage.success('申诉已提交')
    appealVisible.value = false
    await loadAppeals()
  } catch (e) { ElMessage.error('提交失败') }
}

// ===== AI 预警分析（W26）=====
// 打 `/ai/analyze-warning/{warningId}`，要的是**预警行 id**（不是学生 id）。
function aiAnalyzeWarning () {
  const w = selectedWarning.value
  if (!w || w.id == null) throw new Error('请先打开一条预警详情')
  return aiAPI.analyzeWarning(w.id)
}

/** 本页显示的等级措辞 → 后端 `generateWarning` 认的词。只映射这三种。 */
const RISK_LEVEL_MAP = { 严重: '高风险', 中度: '中风险', 轻度: '低风险' }

function aiGenerateWarning () {
  const w = selectedWarning.value
  if (!w) throw new Error('请先打开一条预警详情')
  // ⚠️ 后端 `AIServiceImpl.generateWarning:372-389` 的 switch **只认
  //    「高风险 / 中风险 / 低风险」这三个中文词**，其余一律落到 default
  //    ⇒ warningLevelName="正常"、warningLevel=0。而本页 `translateLevel()` 给出的是
  //    「严重 / 中度 / 轻度」—— 直接透传的话，一条严重预警会算出一句"正常"。
  //    所以这里必须显式映射；认不出来的等级宁可报错，也不让它落到 default 编一个"正常"。
  const riskLevel = RISK_LEVEL_MAP[w.warningLevel]
  if (!riskLevel) {
    throw new Error(`这条预警的等级「${w.warningLevel}」不在已知映射里，无法生成建议`)
  }
  return aiAPI.generateWarning({
    studentName: localStorage.getItem('name') || '该学生',
    riskLevel,
    // 风险因素用**这条预警自己的说明**，不编造
    riskFactors: [w.description].filter(Boolean)
  })
}
</script>

<style scoped>
.warnings-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }

/* 统计卡片 */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 20px; }
.stat-card { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.05); border-left: 4px solid #ddd; }
.stat-card.danger { border-left-color: #ef4444; }
.stat-card.warning { border-left-color: #3b82f6; }
.stat-card.info { border-left-color: #3b82f6; }
.stat-card.appeal { border-left-color: #8b5cf6; }
.stat-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.stat-card.danger .stat-icon { background: #fef2f2; color: #ef4444; }
.stat-card.warning .stat-icon { background: #eff6ff; color: #3b82f6; }
.stat-card.info .stat-icon { background: #eff6ff; color: #3b82f6; }
.stat-card.appeal .stat-icon { background: #f5f3ff; color: #8b5cf6; }
.stat-icon svg { width: 22px; height: 22px; }
.stat-num { font-size: 26px; font-weight: 700; color: #18181b; }
.stat-lbl { font-size: 13px; color: #71717a; }

/* 工具栏 */
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.toolbar-title { font-size: 16px; font-weight: 600; color: #18181b; }

/* 预警卡片 */
.warning-list { display: flex; flex-direction: column; gap: 10px; }
.warning-card { background: #fff; border-radius: 12px; padding: 16px 20px; display: flex; align-items: center; gap: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.05); border-left: 4px solid #ddd; transition: box-shadow .2s; }
.warning-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.warning-card.level-danger { border-left-color: #ef4444; }
.warning-card.level-warning { border-left-color: #3b82f6; }
.warning-card.level-info { border-left-color: #3b82f6; }
.wc-level { flex-shrink: 0; }
.level-badge { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; color: #fff; white-space: nowrap; }
.level-badge.danger { background: #ef4444; }
.level-badge.warning { background: #3b82f6; }
.level-badge.info { background: #3b82f6; }
.wc-body { flex: 1; min-width: 0; }
.wc-title { font-size: 15px; font-weight: 600; color: #18181b; margin-bottom: 4px; }
.wc-desc { font-size: 13px; color: #71717a; line-height: 1.5; margin-bottom: 6px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.wc-meta { display: flex; align-items: center; gap: 10px; }
.wc-tag { font-size: 12px; color: #7c3aed; background: #f5f3ff; padding: 1px 8px; border-radius: 4px; }
.wc-time { font-size: 12px; color: #a1a1aa; }
.wc-actions { flex-shrink: 0; display: flex; gap: 8px; }

/* 空 */
.empty-box { text-align: center; padding: 60px 20px; color: #a1a1aa; }
.empty-box svg { width: 60px; margin-bottom: 12px; }
.empty-box p { font-size: 14px; }

/* 申诉列表 */
.appeal-list { display: flex; flex-direction: column; gap: 8px; }
.appeal-card { background: #fff; border-radius: 10px; padding: 12px 18px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 1px 2px rgba(0,0,0,.04); }
.ap-info { display: flex; gap: 16px; align-items: center; }
.ap-course { font-weight: 600; color: #18181b; font-size: 14px; }
.ap-reason { color: #71717a; font-size: 13px; }
.ap-meta { display: flex; gap: 12px; align-items: center; }
.ap-time { font-size: 12px; color: #a1a1aa; }

/* 详情弹窗 */
.detail-body { line-height: 1.6; }
.detail-header { padding: 12px 16px; border-radius: 8px; display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.detail-header.dl-danger { background: #fef2f2; }
.detail-header.dl-warning { background: #eff6ff; }
.detail-header.dl-info { background: #eff6ff; }
.dl-level { padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; color: #fff; }
.dl-danger .dl-level { background: #ef4444; }
.dl-warning .dl-level { background: #3b82f6; }
.dl-info .dl-level { background: #3b82f6; }
.dl-title { font-size: 16px; font-weight: 600; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.dg-item.full { grid-column: span 2; }
.dg-item label { display: block; font-size: 12px; color: #a1a1aa; margin-bottom: 2px; }
.dg-item span { font-size: 14px; color: #3f3f46; }
.detail-suggest { background: #eff6ff; border-radius: 8px; padding: 14px 18px; }
.detail-suggest h4 { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #92400e; margin: 0 0 10px; }
.detail-suggest ul { margin: 0; padding-left: 20px; }
.detail-suggest li { font-size: 13px; color: #78716c; margin-bottom: 4px; }
.detail-ai { display: flex; flex-direction: column; gap: 10px; }

@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .warning-card { flex-direction: column; align-items: flex-start; }
  .wc-actions { width: 100%; justify-content: flex-end; }
}
</style>
