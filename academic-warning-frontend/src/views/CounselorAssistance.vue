<template>
  <div class="counselor-assistance">
    <div class="page-header">
      <h1>帮扶计划管理</h1>
      <p>为预警学生创建、编辑、跟踪帮扶计划</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-number">{{ stats.totalPlans }}</div>
        <div class="stat-label">总计划数</div>
      </div>
      <div class="stat-card">
        <div class="stat-number">{{ stats.inProgressPlans }}</div>
        <div class="stat-label">进行中</div>
      </div>
      <div class="stat-card">
        <div class="stat-number">{{ stats.completedPlans }}</div>
        <div class="stat-label">已完成</div>
      </div>
      <div class="stat-card">
        <div class="stat-number">{{ stats.completionRate }}%</div>
        <div class="stat-label">完成率</div>
      </div>
    </div>

    <!-- 帮扶计划列表 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">帮扶计划列表</div>
      </template>

      <el-table :data="assistancePlans" stripe style="width: 100%">
        <el-table-column prop="studentName" label="学生" width="100" align="center"></el-table-column>
        <!-- 原先是「计划标题」+「目标」两列，两列读的其实是**同一个** `content` 字段
             （标题是 content 的前 30 字，目标是 content 全文，还带一个 '学分提升' 的
             编造兜底串）。合并成一列，内容只出现一次。 -->
        <el-table-column label="帮扶内容" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '（未填写）' }}</template>
        </el-table-column>
        <el-table-column label="进度" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercentage || 0" :color="customColor"></el-progress>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'in_progress' ? 'warning' : 'info'">
              {{ formatStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="editPlan(row)">编辑</el-button>
            <el-button type="warning" size="small" link @click="updateProgress(row)">更新进度</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑帮扶计划" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="学生">
          <el-input :value="editForm.studentName" disabled></el-input>
        </el-form-item>
        <el-form-item label="帮扶内容">
          <el-input v-model="editForm.content" type="textarea" rows="4" placeholder="输入帮扶措施与目标"></el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="已启动" value="initiated"></el-option>
            <el-option label="进行中" value="in_progress"></el-option>
            <el-option label="已完成" value="completed"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- 进度更新对话框 -->
    <el-dialog v-model="progressDialogVisible" title="更新计划进度" width="500px">
      <el-form :model="progressForm" label-width="100px">
        <el-form-item label="进度">
          <el-slider v-model="progressForm.progress" :min="0" :max="100"></el-slider>
        </el-form-item>
        <el-form-item label="完成情况">
          <el-input v-model="progressForm.notes" type="textarea" rows="2" placeholder="描述完成情况"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProgress">更新</el-button>
      </template>
    </el-dialog>

    <!-- 2026-10-01 第二十一轮删：「查看效果」对话框。
         它弹出的四项里有三项是写死的常量 —— 学分提升 1.5 分、成绩改善 15%、
         预警状态「已解除」（只有完成度取自当前计划）。库里的 `assistance_evaluations`
         是**学生**对计划的评价（content/score/effectiveness），既不是"学分提升"也不是
         "预警解除"，而且活库 0 行 —— 这个"效果"没有任何可算的来源。
         "预警状态：已解除"尤其危险：它是凭计划 id 现编的一句承诺。
         （若日后要做，口径应是"计划创建后该生预警状态的变化"，那要先定清楚再写。） -->
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { counselorAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { toNumber } from '@/utils/score'

const stats = ref({
  totalPlans: 0,
  inProgressPlans: 0,
  completedPlans: 0,
  completionRate: 0
})
const assistancePlans = ref([])
const editDialogVisible = ref(false)
const progressDialogVisible = ref(false)

// `assistance_plans` 表只有 id/counselor_id/student_id/content/status/created_at/
// updated_at/progress 八列，没有"标题/目标/措施"三个字段。原来表单拆成三格，
// 保存时 title 与 measures 被丢弃、target 写进 content —— 即"改了标题和措施看着像成功，
// 其实一个字节都没写"。这里收敛成与列一一对应的内容+状态。
const editForm = ref({
  id: '',
  studentName: '',
  content: '',
  status: 'initiated'
})

const progressForm = ref({
  progress: 0,
  notes: ''
})
const currentPlanId = ref(null)

const customColor = computed(() => {
  return [
    { color: '#f56c6c', percentage: 33 },
    { color: '#e6a23c', percentage: 66 },
    { color: '#67c23a', percentage: 100 }
  ]
})

onMounted(async () => {
  await loadAssistancePlans()
})

const loadAssistancePlans = async () => {
  try {
    const userId = getUserId()
    const counselorId = localStorage.getItem('counselorId') || userId
    if (!counselorId) return

    // 并行获取：学生列表 + 辅导员下所有帮扶计划
    const [studentsRes, plansRes] = await Promise.all([
      counselorAPI.getStudents(counselorId),
      counselorAPI.getCounselorPlans(counselorId)
    ])

    // 解析学生列表
    let students = []
    if (studentsRes && studentsRes.code === 200) {
      students = studentsRes.data || []
    } else if (Array.isArray(studentsRes)) {
      students = studentsRes
    }

    const studentMap = {}
    students.forEach(s => {
      const sid = s.id || s.studentId
      const sname = s.studentName || s.name || '未知'
      studentMap[sid] = sname
    })

    // 解析帮扶计划
    let rawPlans = []
    if (plansRes && plansRes.code === 200) {
      rawPlans = plansRes.data || []
    } else if (Array.isArray(plansRes)) {
      rawPlans = plansRes
    }

    // 映射后端字段到前端表格字段（后端 getCounselorPlans 未按辅导员过滤，这里展示全部返回数据）
    assistancePlans.value = rawPlans.map(p => {
      const sid = p.studentId || p.student_id
      const status = normStatus(p.status)
      return {
        ...p,
        id: p.id,
        studentId: sid,
        studentName: studentMap[sid] || p.studentName || p.student_name || '未知',
        content: p.content || '',
        // 进度读的是**真实列** `progress`（实体字段名也是 progress）。
        // 原来读的是 `p.progressPercentage` —— 后端从来不返回这个 key，
        // 于是每一行都掉进 `|| ` 后面的兜底分支：按状态猜 0 / 50 / 100。
        // 辅导员把进度拖到 80，列表里照样显示 50（因为状态是"进行中"）。
        // 用 toNumber 而不是 `||`：progress 真的是 0 时要保留 0。
        progressPercentage: toNumber(p.progress ?? p.progressPercentage) ?? 0,
        status: status
      }
    })

    // 更新统计数据
    const plans = assistancePlans.value
    stats.value = {
      totalPlans: plans.length,
      inProgressPlans: plans.filter(p => p.status === 'in_progress').length,
      completedPlans: plans.filter(p => p.status === 'completed').length,
      completionRate: plans.length === 0 ? 0 : Math.round((plans.filter(p => p.status === 'completed').length / plans.length) * 100)
    }
  } catch (error) {
    console.error('加载帮扶计划失败:', error)
  }
}

const normStatus = (s) => {
  if (s === 'completed' || s === '已完成' || s === 2 || s === '2') return 'completed'
  if (s === 'in_progress' || s === '进行中' || s === 1 || s === '1') return 'in_progress'
  return 'initiated'
}

const editPlan = (row) => {
  editForm.value = { ...row }
  editDialogVisible.value = true
}

const statusToInt = (s) => {
  if (s === 'completed') return 2
  if (s === 'in_progress') return 1
  return 0
}

const savePlan = async () => {
  try {
    const payload = {
      id: editForm.value.id,
      studentId: editForm.value.studentId,
      counselorId: editForm.value.counselorId || editForm.value.counselor_id,
      content: editForm.value.content || '',
      status: statusToInt(editForm.value.status)
    }
    await counselorAPI.updateAssistancePlan(editForm.value.id, payload)
    ElMessage.success('计划已保存')
    editDialogVisible.value = false
    await loadAssistancePlans()
  } catch (error) {
    console.error('保存计划失败:', error)
    ElMessage.error('保存失败')
  }
}

const updateProgress = (row) => {
  currentPlanId.value = row.id
  progressForm.value.progress = row.progressPercentage || 0
  progressDialogVisible.value = true
}

const saveProgress = async () => {
  try {
    if (!currentPlanId.value) return
    // 只把**进度**交给后端。状态不用前端另算再单独发一次 ——
    // `/progress` 会自己同步（0→未开始 / 100→已完成 / 其余→进行中，
    // 见 AssistancePlanServiceImpl.updatePlanProgress），两边各算一次必然分叉。
    await counselorAPI.updatePlanProgress(currentPlanId.value, progressForm.value.progress || 0)
    ElMessage.success('进度已更新')
    progressDialogVisible.value = false
    await loadAssistancePlans()
  } catch (error) {
    console.error('更新进度失败:', error)
    ElMessage.error(error?.message ? `更新失败：${error.message}` : '更新失败')
  }
}

const formatStatus = (status) => {
  const statusMap = {
    initiated: '已启动',
    in_progress: '进行中',
    completed: '已完成',
    archived: '已归档'
  }
  return statusMap[status] || status
}
</script>

<style scoped>
.counselor-assistance {
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
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
}

.stat-label {
  color: #999;
  font-size: 12px;
  margin-top: 8px;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

</style>
