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
            <el-tag :type="row.warningLevel === 3 ? 'danger' : row.warningLevel === 2 ? 'warning' : 'info'">
              {{ row.warningLevel === 3 ? '红色' : row.warningLevel === 2 ? '黄色' : '蓝色' }}
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
        <el-form-item label="分配协作">
          <el-select v-model="handleForm.assignTo" placeholder="选择协作人（如教师）">
            <el-option label="教师A" value="teacher1"></el-option>
            <el-option label="教师B" value="teacher2"></el-option>
            <el-option label="心理咨询师" value="counselor"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确认处理</el-button>
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

const handleDialogVisible = ref(false)
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
  opinion: '',
  assignTo: ''
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
    opinion: '',
    assignTo: ''
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
      handleResult: handleForm.value.opinion
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

const viewDetails = (row) => {
  ElMessage.info(`${row.studentName}的预警详情`)
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
    // ⚠️ 别抹平成"请稍后重试"：后端现在会明说「没配 DASHSCOPE_KEY」。
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
</style>
