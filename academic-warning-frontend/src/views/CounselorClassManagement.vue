<template> 
  <div class="ccm-container">
    <el-tabs v-model="activeTab" class="tabs" @tab-change="handleTabChange">
      <!-- 我的班级 -->
      <el-tab-pane label="我的班级" name="my-classes">
        <div class="section">
          <el-empty v-if="myClasses.length === 0" description="暂无管理的班级" />
          <div v-else class="classes-grid">
            <div v-for="clazz in myClasses" :key="clazz.id" class="class-card">
              <div class="class-header">
                <h3>{{ clazz.name }}</h3>
                <el-tag type="success">已管理</el-tag>
              </div>
              <div class="class-info">
                <p><span class="label">学生数：</span>{{ clazz.studentCount }}</p>
              </div>
              <div class="class-actions">
                <el-button type="primary" size="small" @click="goToScores(clazz.id)">查看成绩</el-button>
                <el-button type="info" size="small" @click="viewStudents(clazz)">查看学生</el-button>
                <el-button type="danger" size="small" @click="openCancelDialog(clazz)">解除申请</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 添加班级 -->
      <el-tab-pane label="添加班级" name="add-class">
        <div class="section">
          <div class="search-box">
            <el-input v-model="searchKeyword" placeholder="输入班级名称搜索" clearable @input="searchClasses" class="search-input">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="loadAllClasses()" style="margin-left:10px" :loading="loading">查看所有班级</el-button>
            <el-button type="success" @click="loadClasses(false)" style="margin-left:10px" :loading="loading">无辅导员班级</el-button>
          </div>
          <el-empty v-if="!searchPerformed" description="点击按钮加载班级" />
          <el-empty v-else-if="searchResults.length === 0" description="未找到匹配的班级" />
          <div v-else class="search-results">
            <div v-for="clazz in searchResults" :key="clazz.id" class="result-card">
              <div class="result-header">
                <h4>{{ clazz.name }}</h4>
                <el-tag :type="clazz.hasCounselor ? 'warning' : 'success'">{{ clazz.hasCounselor ? '已有辅导员' : '无辅导员' }}</el-tag>
              </div>
              <div class="result-info">
                <p><span class="label">学生数：</span>{{ clazz.studentCount }}</p>
                <p><span class="label">辅导员管理：</span>{{ clazz.hasCounselor ? '是' : '否' }}</p>
              </div>
              <div class="result-actions">
                <el-button type="primary" @click="openApplyDialog(clazz)" :disabled="clazz.hasCounselor">
                  {{ clazz.hasCounselor ? '已有辅导员管理' : '申请管理' }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的申请 -->
      <el-tab-pane label="我的申请" name="my-requests">
        <div class="section">
          <div class="filter-bar" style="margin-bottom:16px">
            <el-select v-model="requestTypeFilter" placeholder="选择申请类型">
              <el-option label="全部" :value="''" />
              <el-option label="申请管理" :value="0" />
              <el-option label="申请解散" :value="1" />
            </el-select>
          </div>
          <el-empty v-if="filteredRequests.length === 0" description="暂无申请记录" />
          <div v-else class="requests-list">
            <div v-for="req in filteredRequests" :key="req.id" class="request-item">
              <div class="request-header">
                <el-tag :type="req.applicationType === 1 ? 'warning' : 'primary'" size="small">
                  {{ req.applicationType === 1 ? '申请解散' : '申请管理' }}
                </el-tag>
                <h4>{{ req.className }}</h4>
                <el-tag :type="getStatusType(req.status)">{{ getStatusText(req.status) }}</el-tag>
              </div>
              <div class="request-info">
                <p><span class="label">{{ req.applicationType === 1 ? '解除原因' : '申请原因' }}：</span>{{ req.reason || '无' }}</p>
                <!-- 拒绝原因（2026-10-02 W1.14）：管理员拒绝时填的理由，此前收下即丢，
                     现在真的落库并在这里回读给申请人。只有 status===2 才显示。 -->
                <p v-if="Number(req.status) === 2 && req.rejectReason">
                  <span class="label">拒绝原因：</span>
                  <span class="reject-reason">{{ req.rejectReason }}</span>
                </p>
                <p><span class="label">申请时间：</span>{{ formatDate(req.createTime) }}</p>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 申请管理对话框 -->
    <el-dialog v-model="applyDialogVisible" title="申请管理班级" width="500px">
      <el-form :model="applyForm" label-width="100px">
        <el-form-item label="班级名称"><el-input v-model="applyForm.className" disabled /></el-form-item>
        <el-form-item label="申请原因"><el-input v-model="applyForm.reason" type="textarea" rows="4" placeholder="请填写申请原因" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApply" :loading="applyLoading">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 解除申请对话框 -->
    <el-dialog v-model="cancelDialogVisible" title="提交解除管理申请" width="500px">
      <el-form :model="cancelForm" label-width="100px">
        <el-form-item label="班级名称"><el-input v-model="cancelForm.className" disabled /></el-form-item>
        <el-form-item label="解除原因"><el-input v-model="cancelForm.reason" type="textarea" rows="4" placeholder="请填写解除原因" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCancelApplication" :loading="cancelLoading">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 学生列表对话框 -->
    <el-dialog v-model="studentsDialogVisible" :title="`${currentClass?.name} - 学生列表`" width="900px">
      <div v-if="loadingStudents"><el-skeleton :rows="10" animated /></div>
      <div v-else>
        <div class="search-bar">
          <el-input v-model="studentSearch" placeholder="搜索学生姓名或学号" clearable style="max-width:300px">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-tag type="info" size="small">共 {{ filteredStudents.length }} 名学生</el-tag>
        </div>
        <el-table :data="filteredStudents" border stripe>
          <el-table-column prop="studentId" label="学号" width="180" align="center">
            <template #default="{ row }"><span style="font-family:monospace;color:#3b82f6;font-weight:500">{{ row.studentId }}</span></template>
          </el-table-column>
          <el-table-column prop="studentName" label="姓名" width="120" align="center" />
          <el-table-column label="班级" width="150" align="center">
            <template #default><el-tag type="success" size="small">{{ currentClass?.name }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="viewStudentDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="classStudents.length === 0" class="empty-students"><el-empty description="暂无学生" :image-size="100" /></div>
      </div>
      <template #footer><el-button @click="studentsDialogVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 学生详情对话框 -->
    <el-dialog v-model="studentDetailDialogVisible" title="学生详情" width="550px">
      <div v-if="currentStudent" class="student-detail-card">
        <div class="student-avatar"><el-avatar :size="100" class="avatar-large">{{ (currentStudent.studentName || '').charAt(0) }}</el-avatar></div>
        <div class="student-info">
          <h3>{{ currentStudent.studentName }}</h3>
          <p style="font-family:monospace;color:#64748b">{{ currentStudent.studentId }}</p>
        </div>
        <el-divider />
        <el-descriptions :column="2" :border="false">
          <el-descriptions-item label="学号">{{ currentStudent.studentId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ currentStudent.studentName }}</el-descriptions-item>
          <el-descriptions-item label="班级"><el-tag type="primary" size="small">{{ currentClass?.name }}</el-tag></el-descriptions-item>
          <!-- 2026-10-02（W4.11）：原来写死 `正常`。现在读 `users.status`
               （后端 `/counselors/classes/{id}/students` 已回 `userStatus`）。 -->
          <el-descriptions-item label="状态">
            <el-tag v-if="currentStudent.userStatus === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else-if="currentStudent.userStatus === 0" type="warning" size="small">待审批</el-tag>
            <el-tag v-else type="info" size="small">未知</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer><el-button @click="studentDetailDialogVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { counselorAPI } from '@/api/index'

const router = useRouter()
const activeTab = ref('my-classes')
const myClasses = ref([])
const searchKeyword = ref('')
const searchResults = ref([])
const searchPerformed = ref(false)
const myRequests = ref([])
const applyDialogVisible = ref(false), applyLoading = ref(false)
const cancelDialogVisible = ref(false), cancelLoading = ref(false)
const loading = ref(false)
const applyForm = ref({ classId: null, className: '', reason: '' })
const cancelForm = ref({ classId: null, className: '', reason: '' })
const studentsDialogVisible = ref(false)
const currentClass = ref(null)
const classStudents = ref([])
const loadingStudents = ref(false)
const studentSearch = ref('')
const studentDetailDialogVisible = ref(false)
const currentStudent = ref(null)
const requestTypeFilter = ref('')

const filteredRequests = computed(() => {
  let result = myRequests.value
  if (requestTypeFilter.value !== '') result = result.filter(r => r.applicationType === parseInt(requestTypeFilter.value))
  return result
})

const filteredStudents = computed(() => {
  if (!studentSearch.value) return classStudents.value
  const q = studentSearch.value.toLowerCase()
  return classStudents.value.filter(s => s.studentName?.toLowerCase().includes(q) || String(s.studentId).includes(q))
})

const getCounselorId = () => { const uid = localStorage.getItem('userId'); return uid ? parseInt(uid) : null }

const loadMyClasses = async () => {
  try { const uid = getCounselorId(); if (!uid) return; const r = await counselorAPI.getMyClasses(uid); if (r) myClasses.value = Array.isArray(r) ? r : (r.data || []) } catch (e) { ElMessage.error('加载班级失败') }
}
const searchClasses = async () => {
  if (!searchKeyword.value?.trim()) { searchResults.value = []; searchPerformed.value = false; return }
  try { const r = await counselorAPI.searchClasses(searchKeyword.value); searchResults.value = Array.isArray(r) ? r : (r?.data || []); searchPerformed.value = true } catch (e) {}
}
const loadClasses = async (showAll = false) => {
  loading.value = true
  // 2026-10-01 第二十一轮: 这里原来读 c.hasTeacher —— counselor-service 的 SQL 别名是
  // **hasCounselor**(ClassInfoMapper.searchClassesMap), 所以 hasTeacher 恒 undefined,
  // `!undefined` 恒 true ⇒ 「只看无辅导员班级」实际返回**全部班级**;上面那几处 :disabled 也永不禁用。
  // (teacher-service 的同名接口返回的**才是** hasTeacher —— TeacherClassManagement.vue 是对的。)
  try { const r = await counselorAPI.searchClasses(''); const all = Array.isArray(r) ? r : (r?.data || []); searchResults.value = showAll ? all : all.filter(c => !c.hasCounselor); searchPerformed.value = true } catch (e) { ElMessage.error('加载失败') }
  finally { loading.value = false }
}
const loadAllClasses = async () => {
  loading.value = true
  try { const r = await counselorAPI.searchClasses(''); searchResults.value = Array.isArray(r) ? r : (r?.data || []); searchPerformed.value = true } catch (e) {}
  finally { loading.value = false }
}
const handleTabChange = (tab) => { if (tab === 'my-classes') loadMyClasses(); if (tab === 'add-class') loadAllClasses() }

const loadMyRequests = async () => {
  try { const uid = getCounselorId(); if (!uid) return; const r = await counselorAPI.getClassRequests(uid); if (r) myRequests.value = Array.isArray(r) ? r : (r.data || []) } catch (e) {}
}

const openApplyDialog = (clazz) => { applyForm.value = { classId: clazz.id, className: clazz.name, reason: '' }; applyDialogVisible.value = true }
const openCancelDialog = (clazz) => { cancelForm.value = { classId: clazz.id, className: clazz.name, reason: '' }; cancelDialogVisible.value = true }

const submitApply = async () => {
  if (!applyForm.value.reason?.trim()) { ElMessage.warning('请填写申请原因'); return }
  applyLoading.value = true
  try {
    await counselorAPI.submitClassManagementRequest({ counselorId: getCounselorId(), classId: parseInt(applyForm.value.classId), className: applyForm.value.className, reason: applyForm.value.reason, applicationType: 0 })
    ElMessage.success('申请已提交'); applyDialogVisible.value = false; loadMyRequests(); loadAllClasses()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
  finally { applyLoading.value = false }
}

const submitCancelApplication = async () => {
  if (!cancelForm.value.reason?.trim()) { ElMessage.warning('请填写解除原因'); return }
  cancelLoading.value = true
  try {
    await counselorAPI.submitClassManagementRequest({ counselorId: getCounselorId(), classId: parseInt(cancelForm.value.classId), className: cancelForm.value.className, reason: cancelForm.value.reason, applicationType: 1 })
    ElMessage.success('申请已提交'); cancelDialogVisible.value = false; loadMyRequests(); loadMyClasses()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
  finally { cancelLoading.value = false }
}

const goToScores = (classId) => { router.push({ path: '/counselor/scores', query: { classId } }) }

const viewStudents = async (clazz) => {
  currentClass.value = clazz; loadingStudents.value = true
  try {
    const r = await counselorAPI.getClassStudents(clazz.id)
    classStudents.value = Array.isArray(r) ? r : (r?.data || [])
  } catch (e) { ElMessage.error('加载学生失败') }
  finally { loadingStudents.value = false; studentsDialogVisible.value = true }
}

const viewStudentDetail = (student) => { currentStudent.value = student; studentDetailDialogVisible.value = true }

const getStatusText = (s) => ({ 0:'待审核',1:'已通过',2:'已拒绝',3:'已取消' }[s] || s)
const getStatusType = (s) => ({ 0:'warning',1:'success',2:'danger',3:'info' }[s] || 'info')
const formatDate = (d) => { if (!d) return ''; const dt = new Date(d); return `${dt.getFullYear()}-${String(dt.getMonth()+1).padStart(2,'0')}-${String(dt.getDate()).padStart(2,'0')}` }

onMounted(() => { loadMyClasses(); loadMyRequests() })
</script>

<style scoped>
.ccm-container { padding: 24px; min-height: 100vh; background: #f0f4ff; }
.tabs { background: #fff; border-radius: 14px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.section { padding: 16px 0; }
.classes-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.class-card, .result-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; transition: all .2s; }
.class-card:hover, .result-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,.08); border-color: #93c5fd; }
.class-header, .result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.class-header h3, .result-header h4 { margin: 0; font-size: 15px; color: #1e293b; }
.class-info, .result-info { font-size: 13px; color: #64748b; margin: 10px 0; }
.class-actions, .result-actions { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
.label { font-weight: 600; color: #334155; }
.search-box { margin-bottom: 16px; display: flex; align-items: center; }
.search-input { max-width: 360px; }
.search-results { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; max-height: calc(100vh - 300px); overflow-y: auto; padding-right: 8px; }
.requests-list { display: flex; flex-direction: column; gap: 10px; }
.request-item { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px; }
.request-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.request-header h4 { margin: 0; flex: 1; color: #1e293b; }
.request-info { font-size: 13px; color: #64748b; }
/* 拒绝原因（W1.14） */
.reject-reason { color: #f56c6c; }
.search-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.empty-students { padding: 40px 0; }
.student-detail-card { padding: 20px; }
.student-avatar { text-align: center; margin-bottom: 12px; }
.avatar-large { background: linear-gradient(135deg, #2563eb, #7c3aed); color: #fff; font-size: 36px; }
.student-info { text-align: center; margin-bottom: 16px; }
.student-info h3 { margin: 0 0 4px; font-size: 20px; color: #1e293b; }
:deep(.el-dialog) { border-radius: 14px; }
:deep(.el-dialog__header) { border-bottom: 1px solid #f1f5f9; padding: 18px 24px; }
</style>
