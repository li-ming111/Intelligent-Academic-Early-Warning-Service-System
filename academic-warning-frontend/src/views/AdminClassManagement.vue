<template>
  <div class="admin-class-management" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>班级管理</h1>
      <p>班级信息管理与教师/辅导员申请审批</p>
    </div>

    <!-- 标签页切换 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="班级管理" name="classes">
        <!-- 班级列表 -->
        <el-card>
          <template #header>
            <div class="card-header">
              <span>班级列表</span>
              <span class="class-count">共 {{ classes.length }} 个班级</span>
            </div>
          </template>

          <div v-if="classes.length === 0" style="padding: 20px; text-align: center; color: #999;">
            暂无班级数据
          </div>
          <el-table style="width: 100%" v-else :data="classes" stripe @selection-change="handleClassSelectionChange">
            <el-table-column type="selection" width="55" />
            <el-table-column prop="id" label="班级ID" width="100" sortable></el-table-column>
            <el-table-column prop="name" label="班级名称" min-width="150" show-overflow-tooltip></el-table-column>
            <el-table-column prop="teacherName" label="关联教师" width="120">
              <template #default="{ row }">
                {{ row.teacherName || '无' }}
              </template>
            </el-table-column>
            <el-table-column prop="studentCount" label="学生数" width="100" sortable>
              <template #default="{ row }">
                <el-tag type="success">{{ row.studentCount || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="教师管理" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.teacher_id" type="success">是</el-tag>
                <el-tag v-else type="warning">否</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button 
                  v-if="row.teacher_id"
                  type="danger" 
                  size="small" 
                  @click="removeTeacher(row.id)"
                >
                  <el-icon><Delete /></el-icon> 解除管理
                </el-button>
                <span v-else style="color: #999;">无教师</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer">
            <div class="selection-info" v-if="selectedClasses.length > 0">
              已选择 {{ selectedClasses.length }} 项
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="教师申请" name="teacher">
        <!-- 教师待审批申请列表 -->
        <el-card>
          <template #header>
            <div class="card-header">
              <span>教师待审批申请</span>
              <span class="teacher-count">共 {{ teacherPendingRequests.length }} 条申请</span>
            </div>
          </template>

          <div v-if="teacherPendingRequests.length === 0" style="padding: 20px; text-align: center; color: #999;">
            暂无待审批的教师申请
          </div>

          <div v-else>
            <el-table style="width: 100%" :data="teacherPendingRequests" stripe @selection-change="handleTeacherSelectionChange">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="申请ID" width="100" sortable></el-table-column>
              <el-table-column prop="teacherName" label="申请教师" width="120"></el-table-column>
              <el-table-column prop="teacherUsername" label="工号" width="100"></el-table-column>
              <el-table-column prop="className" label="申请班级" width="150"></el-table-column>
              <el-table-column prop="applicationType" label="申请类型" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.applicationType === 1 ? 'warning' : 'primary'">
                    {{ row.applicationType === 1 ? '解除管理' : '申请管理' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip></el-table-column>
              <el-table-column label="申请时间" width="180" sortable>
                <template #default="{ row }">
                  {{ formatDate(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button 
                      type="success" 
                      size="small" 
                      @click="approveTeacherRequest(row.id)"
                      :loading="loadingIds.includes(row.id)"
                    >
                      批准
                    </el-button>
                    <el-button 
                      type="danger" 
                      size="small" 
                      @click="showRejectDialog(row, 'teacher')"
                    >
                      拒绝
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer">
              <div class="selection-info" v-if="selectedTeacherRequests.length > 0">
                已选择 {{ selectedTeacherRequests.length }} 项
              </div>
            </div>
          </div>
        </el-card>

        <!-- 教师历史申请记录 -->
        <el-card style="margin-top: 16px;">
          <template #header>
            <div class="card-header">
              <span>教师申请记录</span>
              <span class="request-count">共 {{ allTeacherRecords.length }} 条</span>
            </div>
          </template>
          <div v-if="allTeacherRecords.length === 0" class="adp-empty">暂无记录</div>
          <el-table style="width: 100%" v-else :data="allTeacherRecords" stripe>
            <el-table-column prop="id" label="申请ID" width="100" sortable/>
            <el-table-column prop="teacherName" label="申请教师" width="120"/>
            <el-table-column prop="className" label="申请班级" width="150"/>
            <el-table-column prop="applicationType" label="申请类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.applicationType === 1 ? 'warning' : 'primary'">{{ row.applicationType === 1 ? '解除管理' : '申请管理' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="申请理由" min-width="180" show-overflow-tooltip/>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status===0" type="warning">待审核</el-tag>
                <el-tag v-else-if="row.status===1" type="success">已通过</el-tag>
                <el-tag v-else-if="row.status===2" type="danger">已拒绝</el-tag>
                <el-tag v-else-if="row.status===3" type="info">已取消</el-tag>
              </template>
            </el-table-column>
            <!-- 拒绝原因（2026-10-02 W1.14）：后端此前收下理由就丢，现在真的落库并回读。
                 只有 status===2 才会渲染 —— 库里若因别的路径残留了值也不显示。 -->
            <el-table-column label="拒绝原因" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.status===2 && row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
                <span v-else-if="row.status===2" class="reject-reason reject-reason--empty">未填写</span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="处理时间" width="180" sortable>
              <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="辅导员申请" name="counselor">
        <!-- 辅导员待审批申请列表 -->
        <el-card>
          <template #header>
            <div class="card-header">
              <span>辅导员待审批申请</span>
              <span class="counselor-count">共 {{ counselorPendingRequests.length }} 条申请</span>
            </div>
          </template>

          <div v-if="counselorPendingRequests.length === 0" style="padding: 20px; text-align: center; color: #999;">
            暂无辅导员待审批申请
          </div>
          <el-table style="width: 100%" v-else :data="counselorPendingRequests" stripe @selection-change="handleCounselorSelectionChange">
            <el-table-column type="selection" width="55" />
            <el-table-column prop="id" label="申请ID" width="100" sortable></el-table-column>
            <el-table-column prop="counselorName" label="申请辅导员" width="120"></el-table-column>
            <el-table-column prop="className" label="申请班级" width="150"></el-table-column>
            <el-table-column prop="applicationType" label="申请类型" width="120">
              <template #default="{ row }">
                <el-tag 
                  v-if="row.applicationType === 0 || row.applicationType === null" 
                  type="primary"
                >
                  申请管理
                </el-tag>
                <el-tag 
                  v-else-if="row.applicationType === 1" 
                  type="warning"
                >
                  解除管理
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip></el-table-column>
            <el-table-column label="申请时间" width="180" sortable>
              <template #default="{ row }">
                {{ formatDate(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <div class="action-buttons">
                  <el-button 
                    type="success" 
                    size="small" 
                    @click="approveCounselorRequest(row.id)"
                    :loading="loadingIds.includes(row.id)"
                  >
                    批准
                  </el-button>
                  <el-button 
                    type="danger" 
                    size="small" 
                    @click="showRejectDialog(row, 'counselor')"
                  >
                    拒绝
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer">
            <div class="selection-info" v-if="selectedCounselorRequests.length > 0">
              已选择 {{ selectedCounselorRequests.length }} 项
            </div>
          </div>
        </el-card>

        <!-- 辅导员历史申请记录 -->
        <el-card style="margin-top: 16px;">
          <template #header>
            <div class="card-header">
              <span>辅导员申请记录</span>
              <span class="request-count">共 {{ allCounselorRecords.length }} 条</span>
            </div>
          </template>
          <div v-if="allCounselorRecords.length === 0" class="adp-empty">暂无记录</div>
          <el-table style="width: 100%" v-else :data="allCounselorRecords" stripe>
            <el-table-column prop="id" label="申请ID" width="100" sortable/>
            <el-table-column prop="counselorName" label="申请辅导员" width="120"/>
            <el-table-column prop="className" label="申请班级" width="150"/>
            <el-table-column prop="applicationType" label="申请类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.applicationType === 1 ? 'warning' : 'primary'">{{ row.applicationType === 1 ? '解除管理' : '申请管理' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="申请理由" min-width="180" show-overflow-tooltip/>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status===0" type="warning">待审核</el-tag>
                <el-tag v-else-if="row.status===1" type="success">已通过</el-tag>
                <el-tag v-else-if="row.status===2" type="danger">已拒绝</el-tag>
                <el-tag v-else-if="row.status===3" type="info">已取消</el-tag>
              </template>
            </el-table-column>
            <!-- 拒绝原因（2026-10-02 W1.14）：后端此前收下理由就丢，现在真的落库并回读。
                 只有 status===2 才会渲染 —— 库里若因别的路径残留了值也不显示。 -->
            <el-table-column label="拒绝原因" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.status===2 && row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
                <span v-else-if="row.status===2" class="reject-reason reject-reason--empty">未填写</span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="处理时间" width="180" sortable>
              <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 拒绝理由对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝申请" width="400px">
      <div class="delete-warning">
        <el-icon color="#F56C6C" size="20"><WarningFilled /></el-icon>
        <p>确定要拒绝 <strong>{{ rejectForm.teacherName || rejectForm.counselorName }}</strong> 的申请吗？</p>
        <p>班级：{{ rejectForm.className }}</p>
      </div>
      <el-form :model="rejectForm" label-width="100px" style="margin-top: 20px;">
        <el-form-item label="拒绝理由">
          <el-input 
            v-model="rejectForm.reason" 
            type="textarea" 
            :rows="3"
            placeholder="请输入拒绝理由..."
            maxlength="500"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject" :loading="rejectLoading">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAPI, counselorAPI } from '@/api/index'
import { Delete, Check, WarningFilled } from '@element-plus/icons-vue'

const activeTab = ref('teacher')
const teacherPendingRequests = ref([])
const counselorPendingRequests = ref([])
const allRequests = ref([])
const allTeacherRecords = computed(() => allRequests.value.filter(r => r.type === 'teacher'))
const allCounselorRecords = computed(() => allRequests.value.filter(r => r.type === 'counselor'))
const classes = ref([])
const rejectDialogVisible = ref(false)
const loadingIds = ref([])
const rejectLoading = ref(false)
const currentRequestType = ref('teacher')
const selectedClasses = ref([])
const selectedTeacherRequests = ref([])
const selectedCounselorRequests = ref([])

const rejectForm = ref({
  requestId: '',
  teacherName: '',
  counselorName: '',
  className: '',
  reason: ''
})

onMounted(async () => {
  await loadRequests()
})

const handleTabChange = (tabName) => {
  loadRequests()
  if (tabName === 'classes') {
    loadClasses()
  }
}

// 处理教师申请数据
const processTeacherRequests = (requestsData) => {
  return requestsData.map(req => {
    if (!req.teacherName) {
      if (req.userName) {
        req.teacherName = req.userName
      } else {
        req.teacherName = '未知教师'
      }
    }
    if (!req.teacherUsername) {
      if (req.userUsername) {
        req.teacherUsername = req.userUsername
      } else {
        req.teacherUsername = '未知工号'
      }
    }
    return req
  })
}

// 加载申请列表
const loadRequests = async () => {
  try {
    // 统一从 admin API 获取所有待审批申请（含教师+辅导员）
    const response = await adminAPI.getPendingClassManagementRequests()
    
    let allPendingData = []
    if (response && response.items) {
      allPendingData = response.items
    } else if (Array.isArray(response)) {
      allPendingData = response
    }
    
    // 按类型分离教师和辅导员
    const teachers = allPendingData.filter(r => r.type === 'teacher')
    const counselors = allPendingData.filter(r => r.type === 'counselor')
    
    teacherPendingRequests.value = processTeacherRequests(teachers)
    counselorPendingRequests.value = counselors
    
    // 加载所有申请记录（教师+辅导员）
    await loadAllTeacherRequests()
    
  } catch (error) {
    console.error('加载申请列表失败:', error)
    ElMessage.error('加载申请列表失败')
  }
}

// 加载所有申请记录
const loadAllTeacherRequests = async () => {
  try {
    const response = await adminAPI.getAllClassManagementRequests()
    let allData = []
    if (response && response.items) {
      allData = response.items
    } else if (Array.isArray(response)) {
      allData = response
    }
    // 统一申请人名称字段，方便表格显示
    allRequests.value = allData.map(r => ({
      ...r,
      teacherName: r.teacherName || r.counselorName || '未知',
      teacherUsername: r.teacherUsername || (r.counselorName ? '' : '') || ''
    }))
  } catch (error) {
    console.error('加载所有申请记录失败:', error)
  }
}

// 加载班级列表
const loadClasses = async () => {
  try {
    const response = await adminAPI.getAllClasses()
    if (response && response.data && Array.isArray(response.data)) {
      classes.value = response.data
    } else if (response && Array.isArray(response)) {
      classes.value = response
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
  }
}

// 解除教师与班级关联
const removeTeacher = async (classId) => {
  try {
    await ElMessageBox.confirm(
      '确定要解除该班级的教师关联吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await adminAPI.removeTeacherFromClass(classId)
    ElMessage.success('解除成功')
    loadClasses()
  } catch (error) {
    console.error('解除失败:', error)
    if (error !== 'cancel') {
      ElMessage.error('解除失败')
    }
  }
}

// 批准教师申请
const approveTeacherRequest = (requestId) => {
  const request = teacherPendingRequests.value.find(req => req.id === requestId)
  const isRemoveRequest = request && request.applicationType === 1
  
  const confirmMessage = isRemoveRequest 
    ? '确定要批准这个解除管理申请吗？批准后该教师将失去管理指定班级的权限。'
    : '确定要批准这个申请吗？批准后该教师将获得管理指定班级的权限。'
  
  ElMessageBox.confirm(
    confirmMessage,
    '确认批准',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'success'
    }
  ).then(async () => {
    try {
      loadingIds.value.push(requestId)
      await adminAPI.approveClassManagementRequest(requestId, 'teacher')
      ElMessage.success('申请已批准')
      loadRequests()
      loadAllTeacherRequests()
    } catch (error) {
      console.error('批准申请失败:', error)
      ElMessage.error(error.message || '批准申请失败')
    } finally {
      loadingIds.value = loadingIds.value.filter(id => id !== requestId)
    }
  }).catch(() => {})
}

// 批准辅导员申请
const approveCounselorRequest = (requestId) => {
  ElMessageBox.confirm(
    '确定要批准这个申请吗？批准后该辅导员将获得管理指定班级的权限。',
    '确认批准',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'success'
    }
  ).then(async () => {
    try {
      loadingIds.value.push(requestId)
      await adminAPI.approveClassManagementRequest(requestId, 'counselor')
      ElMessage.success('申请已批准')
      loadRequests()
    } catch (error) {
      console.error('批准申请失败:', error)
      ElMessage.error(error.message || '批准申请失败')
    } finally {
      loadingIds.value = loadingIds.value.filter(id => id !== requestId)
    }
  }).catch(() => {})
}

// 显示拒绝对话框
const showRejectDialog = (request, type = 'teacher') => {
  currentRequestType.value = type
  rejectForm.value = {
    requestId: request.id,
    teacherName: request.teacherName || '',
    counselorName: request.counselorName || '',
    className: request.className,
    reason: ''
  }
  rejectDialogVisible.value = true
}

// 确认拒绝
const confirmReject = async () => {
  if (!rejectForm.value.reason.trim()) {
    ElMessage.error('请输入拒绝理由')
    return
  }

  rejectLoading.value = true
  try {
    await adminAPI.rejectClassManagementRequest(
      rejectForm.value.requestId, 
      rejectForm.value.reason,
      currentRequestType.value
    )
    ElMessage.success('申请已拒绝')
    rejectDialogVisible.value = false
    await loadRequests()
  } catch (error) {
    console.error('拒绝申请失败:', error)
    ElMessage.error(error.message || '拒绝申请失败')
  } finally {
    rejectLoading.value = false
  }
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 选择事件处理
const handleClassSelectionChange = (selection) => {
  selectedClasses.value = selection
}

const handleTeacherSelectionChange = (selection) => {
  selectedTeacherRequests.value = selection
}

const handleCounselorSelectionChange = (selection) => {
  selectedCounselorRequests.value = selection
}
</script>

<style scoped>
.admin-class-management {
  padding: 20px;
  background-color: #f8f9fa !important;
  min-height: 100vh;
}

/* 拒绝原因（W1.14） */
.reject-reason {
  color: #f56c6c;
}

.reject-reason--empty {
  color: #c0c4cc;
  font-style: italic;
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

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.class-count, .teacher-count, .counselor-count, .request-count {
  font-size: 14px;
  color: #909399;
  font-weight: normal;
}

.table-footer {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selection-info {
  color: #606266;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.delete-warning {
  text-align: center;
  padding: 20px;
}

.delete-warning p {
  margin: 10px 0;
  color: #606266;
}

.admin-class-management :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-class-management :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-class-management :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-class-management :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-class-management :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}

.admin-class-management :deep(.el-button--success) {
  background-color: #67c23a !important;
  border-color: #67c23a !important;
}

.admin-class-management :deep(.el-button--success:hover) {
  background-color: #52c41a !important;
  border-color: #52c41a !important;
}

.action-buttons {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.action-buttons .el-button {
  margin: 0;
}
</style>