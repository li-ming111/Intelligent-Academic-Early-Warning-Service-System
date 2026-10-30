<template>
  <div class="counselor-warnings">
    <div class="page-header">
      <h1>学生预警管理</h1>
      <p>班级学生预警处理和追踪</p>
    </div>

    <!-- 预警统计 -->
    <div class="stats-grid">
      <div class="stat-card red">
        <div class="stat-number">{{ warningStats.redWarnings || 0 }}</div>
        <div class="stat-label">红色预警</div>
      </div>
      <div class="stat-card yellow">
        <div class="stat-number">{{ warningStats.yellowWarnings || 0 }}</div>
        <div class="stat-label">黄色预警</div>
      </div>
      <div class="stat-card blue">
        <div class="stat-number">{{ warningStats.blueWarnings || 0 }}</div>
        <div class="stat-label">蓝色预警</div>
      </div>
      <div class="stat-card gray">
        <div class="stat-number">{{ warningStats.handledWarnings || 0 }}</div>
        <div class="stat-label">已处理</div>
      </div>
      <!-- 2026-10-02（W1.6）：原来这一格数的是 `status===1 || status===2`，
           标签却写「已处理」—— 把"系统自动解除的"算进了"辅导员处理掉的"。
           现在拆成两格，各自只数自己那一态。 -->
      <div class="stat-card gray">
        <div class="stat-number">{{ warningStats.dispelledWarnings || 0 }}</div>
        <div class="stat-label">已解除</div>
      </div>
    </div>

    <!-- 预警列表 -->
    <el-card>
      <template #header>
        <div class="card-header">预警学生列表</div>
      </template>

      <el-table :data="warningsList" stripe>
        <el-table-column prop="studentName" label="学生" width="100"></el-table-column>
        <el-table-column prop="className" label="班级" width="80"></el-table-column>
        <el-table-column label="预警级别" width="100">
          <template #default="{ row }">
            <!-- 2026-10-02（W4.4）：等级↔文案/颜色只用 utils/score.js 那一份表。
                 原来这里是本页自己写的 `=== 3 ? '红色' : === 2 ? '黄色' : '蓝色'`
                 —— 同一份映射在全仓存在多份手写副本，正是它们互相漂移、
                 把轻度和严重说反的温床（见 warningLevelColor 的注释）。 -->
            <el-tag :type="warningLevelTagType(row.warningLevel)">
              {{ warningLevelText(row.warningLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="预警原因" min-width="200" show-overflow-tooltip></el-table-column>
        <el-table-column label="处理状态" width="100">
          <template #default="{ row }">
            <el-tag :type="warningStatusTagType(row.status)">{{ warningStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="handleWarning(row)" v-if="isPendingWarning(row.status)">处理</el-button>
            <el-button type="info" size="small" link @click="viewDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 处理对话框 -->
    <el-dialog v-model="handleDialogVisible" title="处理预警" width="600px">
      <el-form :model="handleForm" label-width="120px">
        <el-form-item label="学生">
          <el-input :value="handleForm.studentName" disabled></el-input>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="handleForm.opinion" type="textarea" rows="3" placeholder="记录处理意见..."></el-input>
          <el-button type="primary" plain @click="generateOpinion" style="margin-top: 10px;">生成处理意见</el-button>
        </el-form-item>
        <!-- 2026-10-02（W4.4）：原来这里有个「分配协作」下拉，三个选项写死
             `teacher1/teacher2/counselor`，而且选中的值**从来没被发送过**
             （`submitHandle` 只发 `handleResult`）。查了表：`academic_warning`
             根本没有 `assign_to` 之类的列（列清单：handled_by / handle_result / handled_at
             / created_by / updated_by，没有归属协作人的字段），后端
             `processWarning` 也只读 `handleResult`/`handledBy`。
             一个假输入框比没有更糟 —— 它让人以为选了就生效。列不存在、也没人读，
             按本工程的既定判据**整块删掉**（与第十一轮删 `/api/resources/**` 同例）。 -->
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 预警详情（2026-10-02，W4.4）：原来「详情」只弹一句 toast
         `ElMessage.info('X的预警详情')` —— 那是把"没有这个功能"演成了"有这个功能"。
         现在显示这条预警在库里的真实字段。 -->
    <el-dialog v-model="detailDialogVisible" title="预警详情" width="620px">
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="学生">{{ detailRow.studentName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ detailRow.className || '--' }}</el-descriptions-item>
        <el-descriptions-item label="预警级别">
          <el-tag :type="warningLevelTagType(detailRow.warningLevel)">
            {{ warningLevelText(detailRow.warningLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag :type="warningStatusTagType(detailRow.status)">{{ warningStatusText(detailRow.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预警标题" :span="2">{{ detailRow.title || '--' }}</el-descriptions-item>
        <el-descriptions-item label="预警原因" :span="2">{{ detailRow.description || '--' }}</el-descriptions-item>
        <el-descriptions-item label="预警说明" :span="2">{{ detailRow.warningMessage || '--' }}</el-descriptions-item>
        <el-descriptions-item label="挂科门数">{{ detailRow.failCount ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="触发时间">{{ formatTime(detailRow.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detailRow.handledBy || '未处理' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ detailRow.handledAt ? formatTime(detailRow.handledAt) : '未处理' }}</el-descriptions-item>
        <el-descriptions-item label="处理意见" :span="2">{{ detailRow.handleResult || '未填写' }}</el-descriptions-item>
      </el-descriptions>
      <!-- AI 预警分析（W26）：`aiAPI.analyzeWarning` 此前全仓零调用。
           ⚠️ 该端点在没配 AI_API_KEY 时会用占位串冒充成功，
           闸在 utils/ai.js 里把那种"结果"换成一句实话，页面不把它当分析。 -->
      <div v-if="detailRow" class="detail-ai">
        <AiInsight title="AI 预警分析" :load="aiAnalyzeWarning" hint="分析这条预警的严重性与原因" />
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button
          v-if="detailRow && isPendingWarning(detailRow.status)"
          type="primary"
          @click="detailDialogVisible = false; handleWarning(detailRow)"
        >处理这条预警</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElLoading } from 'element-plus'
import { counselorAPI, aiAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
// 2026-10-02（W1.6）：状态是三态，文案/配色/归一化只在 utils/warning.js 维护一份
import { warningStatus, warningStatusText, warningStatusTagType, isPendingWarning, WARNING_STATUS } from '@/utils/warning'
// 2026-10-02（W4.4）：等级↔文案/颜色同样只留 utils/score.js 一份，本页不再手写。
import { warningLevelText, warningLevelTagType } from '@/utils/score'
import AiInsight from '@/components/AiInsight.vue'

const handleDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detailRow = ref(null)
const warningsList = ref([])
const warningStats = ref({
  redWarnings: 0,
  yellowWarnings: 0,
  blueWarnings: 0,
  handledWarnings: 0,
  dispelledWarnings: 0
})

const handleForm = ref({
  warningId: '',
  studentName: '',
  opinion: ''
})

onMounted(async () => {
  await loadWarnings()
})

const loadWarnings = async () => {
  try {
    const userId = getUserId()
    const counselorId = localStorage.getItem('counselorId') || userId
    if (!counselorId) return
    const response = await counselorAPI.getWarnings(counselorId)
    warningsList.value = Array.isArray(response) ? response : (response?.data || [])
    // 计算统计数据
    warningStats.value = {
      redWarnings: warningsList.value.filter(w => w.warningLevel === 3).length,
      yellowWarnings: warningsList.value.filter(w => w.warningLevel === 2).length,
      blueWarnings: warningsList.value.filter(w => w.warningLevel === 1).length,
      // 两态分开数，各自只认自己那一态（原来 1|2 合并成"已处理"是把自动解除算进去了）
      handledWarnings: warningsList.value.filter(w => warningStatus(w.status) === WARNING_STATUS.HANDLED).length,
      dispelledWarnings: warningsList.value.filter(w => warningStatus(w.status) === WARNING_STATUS.DISPELLED).length
    }
  } catch (error) {
    console.error('加载预警失败:', error)
  }
}

const handleWarning = (row) => {
  handleForm.value = {
    warningId: row.id,
    studentName: row.studentName,
    opinion: ''
  }
  handleDialogVisible.value = true
}

const submitHandle = async () => {
  if (!handleForm.value.opinion) {
    ElMessage.error('请输入处理意见')
    return
  }
  try {
    const data = {
      handleResult: handleForm.value.opinion,
      // 2026-10-02（W4.4）：补发 `handledBy` —— 后端 `processWarning` 一直在读它
      // （`AcademicWarning.setHandledBy`，列 `academic_warning.handled_by`），
      // 而前端从来没发过 ⇒ 库里的处理人永远是 NULL，管理员页的「处理人」列因此空白。
      // 名字取自登录时写进 localStorage 的 `name`（Login.vue），退而用用户名、再退用 userId。
      handledBy: localStorage.getItem('name') || localStorage.getItem('username') || String(getUserId() || '')
    }
    await counselorAPI.processWarning(handleForm.value.warningId, data)
    ElMessage.success('预警已处理')
    handleDialogVisible.value = false
    await loadWarnings()
  } catch (error) {
    console.error('处理预警失败:', error)
    ElMessage.error('处理失败')
  }
}

// 2026-10-02（W4.4）：详情改成真弹窗 —— 显示的就是列表接口现在回的那几个真实列。
const viewDetails = (row) => {
  detailRow.value = row
  detailDialogVisible.value = true
}

/** 时间显示：`2026-10-01T10:00:00` → `2026-10-01 10:00:00`。取不到显示 `--`。 */
const formatTime = (v) => (v ? String(v).replace('T', ' ').substring(0, 19) : '--')

/** AI 预警分析（W26）。端点收的是**预警行 id**，不是学生 id。 */
const aiAnalyzeWarning = () => {
  const w = detailRow.value
  if (!w || w.id == null) throw new Error('请先打开一条预警详情')
  return aiAPI.analyzeWarning(w.id)
}

const generateOpinion = async () => {
  if (!handleForm.value.warningId) {
    ElMessage.error('请选择预警记录')
    return
  }
  
  const loading = ElLoading.service({
    lock: true,
    text: 'AI生成处理意见中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })
  
  try {
    const userId = getUserId()
    // 构建AI提示词
    const prompt = `作为高校辅导员，为学生${handleForm.value.studentName}生成一份预警处理意见。请根据学生情况，包括预警原因、学习状态等，生成一段专业、合理的处理意见，内容应包括：1. 对学生情况的分析 2. 具体的帮扶措施 3. 后续跟踪计划。生成的内容要符合辅导员的专业身份，语言要正式、关怀。`
    
    const response = await aiAPI.getResponse(userId, prompt)
    // ⚠️ `/ai/chat` 回的是 `{studentId, reply[, analysis]}`（AIController.chat），
    // 没有 `code`/`data` 两层（拦截器已解包）。原来那个条件**恒假** → 永远走 else，
    // AI 明明答了也一律报"生成处理意见失败"。正文在 `reply`。
    const reply = response?.reply ?? response?.data?.reply ?? response?.data
    if (typeof reply === 'string' && reply.trim()) {
      handleForm.value.opinion = reply
      ElMessage.success('处理意见生成成功')
    } else {
      // 后端回了 200 但没有 reply —— 不填、也不说"成功"。
      ElMessage.error('AI 没有返回内容，处理意见框保持为空')
    }
  } catch (error) {
    console.error('生成处理意见失败:', error)
    // ⚠️ 别抹平成"请稍后重试"：后端现在会明说「没配 AI_API_KEY」。
    // 抹平之后，答辩现场点一次只会看到"稍后重试"，而重试一辈子也没用。
    ElMessage.error('生成处理意见失败：' + (error?.message || '网络错误'))
  } finally {
    loading.close()
  }
}
</script>

<style scoped>
.counselor-warnings {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #333;
}

.page-header p {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 15px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  color: white;
}

.stat-card.red {
  background: linear-gradient(135deg, #f56c6c, #ef4040);
}

.stat-card.yellow {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.stat-card.blue {
  background: linear-gradient(135deg, #409eff, #1e90ff);
}

.stat-card.gray {
  background: linear-gradient(135deg, #909399, #6c7280);
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.detail-ai { margin-top: 12px; }
</style>
