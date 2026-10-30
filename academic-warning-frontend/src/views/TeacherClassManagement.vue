<template> 
  <div class="class-management-container">
    <!-- 标签页 -->
    <el-tabs v-model="activeTab" class="tabs" @tab-change="handleTabChange">
      <!-- 我的班级 -->
      <el-tab-pane label="我的班级" name="my-classes">
        <div class="my-classes-section">
          <el-empty v-if="myClasses.length === 0" description="暂无管理的班级"></el-empty>
          
          <div v-else class="classes-grid">
            <div v-for="clazz in myClasses" :key="clazz.id" class="class-card">
              <div class="class-header">
                <h3>{{ clazz.name }}</h3>
                <el-tag type="success">已管理</el-tag>
              </div>
              <div class="class-info">
                <p><span class="label">学生数：</span> {{ clazz.studentCount }}</p>
              </div>
              <div class="class-actions">
                <el-button type="primary" size="small" @click="goToScoreImport(clazz.id)">
                  导入成绩
                </el-button>
                <el-button type="success" size="small" @click="openStudentImportDialog(clazz)">
                  导入学生
                </el-button>
                <el-button type="info" size="small" @click="viewStudents(clazz)">
                  查看学生
                </el-button>
                <el-button 
                  type="danger" 
                  size="small" 
                  @click="openCancelApplicationDialog(clazz)"
                >
                  解除申请
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 添加班级 -->
      <el-tab-pane label="添加班级" name="add-class">
        <div class="add-class-section">
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="输入班级名称搜索"
              clearable
              @input="searchClasses"
              class="search-input"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="loadAllClasses()" style="margin-left: 10px" :loading="loading">
              查看所有班级
            </el-button>
            <el-button type="success" @click="loadClasses(false)" style="margin-left: 10px" :loading="loading">
              查看无教师班级
            </el-button>
          </div>

          <el-empty v-if="!searchPerformed" description="加载中..."></el-empty>
          <el-empty v-else-if="searchResults.length === 0" description="未找到匹配的班级"></el-empty>

          <div v-else class="search-results">
            <div v-for="clazz in searchResults" :key="clazz.id" class="result-card">
              <div class="result-header">
                <h4>{{ clazz.name }}</h4>
                <el-tag :type="clazz.hasTeacher ? 'warning' : 'success'">
                  {{ clazz.hasTeacher ? '已有教师' : '无教师' }}
                </el-tag>
              </div>
              <div class="result-info">
                <p><span class="label">学生数：</span> {{ clazz.studentCount }}</p>
                <p><span class="label">专业：</span> {{ getMajorName(clazz.majorId) || '未指定' }}</p>
                <p><span class="label">教师管理：</span> {{ clazz.hasTeacher ? '是' : '否' }}</p>
              </div>
              <div class="result-actions">
                <el-button
                  type="primary"
                  @click="openApplyDialog(clazz)"
                  :disabled="clazz.hasTeacher"
                >
                  {{ clazz.hasTeacher ? '已有教师管理' : '申请管理' }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的申请 -->
      <el-tab-pane label="我的申请" name="my-requests">
        <div class="requests-section">
          <!-- 筛选器 -->
          <div class="filter-bar">
            <el-select 
              v-model="requestTypeFilter" 
              placeholder="选择申请类型" 
              class="filter-select"
            >
              <el-option label="全部" :value="''"></el-option>
              <el-option label="申请管理" :value="0"></el-option>
              <el-option label="申请解散" :value="1"></el-option>
            </el-select>
          </div>

          <!-- 申请列表 -->
          <el-empty v-if="filteredRequests.length === 0" description="暂无申请记录"></el-empty>
          <div v-else class="requests-list">
            <div v-for="req in filteredRequests" :key="req.id" class="request-item">
              <div class="request-header">
                <div class="request-type-tag">
                  <el-tag :type="req.applicationType === 1 ? 'warning' : 'primary'" size="small">
                    {{ req.applicationType === 1 ? '申请解散' : '申请管理' }}
                  </el-tag>
                </div>
                <h4>{{ req.className }}</h4>
                <el-tag :type="getStatusType(req.status)">{{ getStatusText(req.status) }}</el-tag>
              </div>
              <div class="request-info">
                <p><span class="label">{{ req.applicationType === 1 ? '解除原因' : '申请原因' }}：</span> {{ req.reason || '无' }}</p>
                <!-- 拒绝原因（2026-10-02 W1.14）：管理员拒绝时填的理由，此前收下即丢，
                     现在真的落库并在这里回读给申请人。只有 status===2 才显示。 -->
                <p v-if="Number(req.status) === 2 && req.rejectReason">
                  <span class="label">拒绝原因：</span>
                  <span class="reject-reason">{{ req.rejectReason }}</span>
                </p>
                <p><span class="label">申请时间：</span> {{ formatDate(req.createTime) }}</p>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 申请管理班级对话框 -->
    <el-dialog v-model="applyDialogVisible" title="申请管理班级" width="500px">
      <el-form ref="applyFormRef" :model="applyForm" label-width="100px">
        <el-form-item label="班级名称">
          <el-input v-model="applyForm.className" disabled></el-input>
        </el-form-item>
        <el-form-item label="申请原因" prop="reason">
          <el-input
            v-model="applyForm.reason"
            type="textarea"
            rows="4"
            placeholder="请填写申请原因"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApply" :loading="applyLoading">
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 解除申请对话框 -->
    <el-dialog v-model="cancelDialogVisible" title="提交解除管理申请" width="600px">
      <el-form :model="cancelForm" label-width="100px" style="padding: 20px;">
        <el-form-item label="班级名称" style="margin-bottom: 20px;">
          <el-input v-model="cancelForm.className" disabled class="w-full"></el-input>
        </el-form-item>
        <el-form-item label="解除原因" style="margin-bottom: 20px;">
          <el-input
            v-model="cancelForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请填写解除班级管理的原因"
            class="w-full"
            style="width: 100%;"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCancelApplication" :loading="cancelLoading">
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入学生对话框 -->
    <el-dialog v-model="studentImportDialogVisible" title="导入学生信息" width="600px">
      <div class="student-import-content">
        <el-form ref="importFormRef" :model="importForm" label-width="100px">
          <el-form-item label="班级名称">
            <el-input v-model="importForm.className" disabled></el-input>
          </el-form-item>
          <el-form-item label="上传Excel">
            <el-upload
              ref="uploadStudentRef"
              :auto-upload="false"
              :on-change="handleStudentFileSelect"
              accept=".xlsx,.xls"
              class="upload-demo"
            >
              <el-button type="primary">选择文件</el-button>
              <template #tip>
                <div class="el-upload__tip">
                  需要一列「学号」（模板见管理员端「数据导入导出」页）。
                  导入是把已经在系统里的学生归入本班，不会新建账号。
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="studentImportDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="importStudents" :loading="importLoading">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 学生列表对话框 -->
    <el-dialog 
      v-model="studentsDialogVisible" 
      :title="`${currentClass?.name} - 学生列表`" 
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-if="loadingStudents" class="loading-container">
        <el-skeleton :rows="10" animated />
      </div>
      <div v-else>
        <div class="search-bar">
          <el-input
            v-model="studentSearch"
            placeholder="搜索学生姓名或学号"
            clearable
            size="large"
            class="search-input"
          >
            <template #prefix>
              <el-icon color="#64748b"><Search /></el-icon>
            </template>
          </el-input>
          <div class="student-count">
            <el-tag type="info" size="small">共 {{ filteredStudents.length }} 名学生</el-tag>
          </div>
        </div>
        
        <div class="students-table-wrapper">
          <el-table 
            :data="filteredStudents" 
            style="width: 100%"
            border
            :header-cell-style="{ background: '#f8fafc', color: '#475569', fontWeight: '500' }"
            :row-style="{ height: '50px' }"
            highlight-current-row
          >
            <el-table-column 
              prop="studentId" 
              label="学号" 
              width="180"
              align="center"
            >
              <template #default="{ row }">
                <span class="student-id">{{ row.studentId }}</span>
              </template>
            </el-table-column>
            <el-table-column 
              prop="studentName" 
              label="姓名" 
              width="120"
              align="center"
            >
              <template #default="{ row }">
                <span class="student-name">{{ row.studentName }}</span>
              </template>
            </el-table-column>
            <el-table-column 
              label="班级" 
              width="150"
              align="center"
            >
              <template #default>
                <el-tag type="success" size="small">{{ currentClass?.name }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column 
              label="操作" 
              width="140"
              align="center"
            >
              <template #default="{ row }">
                <el-button 
                  size="small" 
                  type="primary" 
                  @click="viewStudentDetail(row)"
                  icon="User"
                >
                  查看详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <div v-if="classStudents.length === 0" class="empty-students">
          <el-empty description="该班级暂无学生" :image-size="100"></el-empty>
        </div>
      </div>
      <template #footer>
        <el-button @click="studentsDialogVisible = false" size="large">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 学生详情对话框 -->
    <el-dialog 
      v-model="studentDetailDialogVisible" 
      title="学生详情" 
      width="550px"
      :close-on-click-modal="false"
    >
      <div v-if="currentStudent" class="student-detail-card">
        <div class="student-avatar">
          <el-avatar :size="100" :icon="User" class="avatar-large"></el-avatar>
        </div>
        <div class="student-info">
          <h3 class="student-name-title">{{ currentStudent.studentName }}</h3>
          <p class="student-id-text">{{ currentStudent.studentId }}</p>
        </div>
        
        <el-divider></el-divider>
        
        <el-descriptions :column="2" :border="false" class="detail-descriptions">
          <el-descriptions-item label="学号" :label-width="80">
            <span class="detail-value">{{ currentStudent.studentId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="姓名" :label-width="80">
            <span class="detail-value">{{ currentStudent.studentName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="班级" :label-width="80">
            <el-tag type="primary" size="small">{{ currentClass?.name }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态" :label-width="80">
            <!-- 2026-10-02（W4.11）：原来写死 `正常`；改读 `users.status`
                 （后端 `/teachers/classes/{id}/students` 已回 `userStatus`）。 -->
            <el-tag v-if="currentStudent.userStatus === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else-if="currentStudent.userStatus === 0" type="warning" size="small">待审批</el-tag>
            <el-tag v-else type="info" size="small">未知</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        
        <div class="detail-actions">
          <el-button
            type="default"
            size="small"
            icon="FileText"
            :loading="studentScoresLoading"
            @click="viewStudentScores"
          >
            {{ studentScores.length ? '刷新成绩' : '查看成绩' }}
          </el-button>
          <!-- 原「发送消息」按钮已删（2026-10-19）：教师→学生没有可用的发送通道
               （消息页数据只读自 feedback 表，saveCommunication 是教师↔辅导员口径），
               与其留一个点了没反应的按钮，不如不展示这个能力。 -->
        </div>

        <template v-if="studentScores.length">
          <el-divider></el-divider>
          <el-table :data="studentScores" size="small" max-height="260">
            <el-table-column prop="courseName" label="课程" min-width="140" show-overflow-tooltip />
            <el-table-column label="学期" width="100">
              <template #default="{ row }">第{{ row.semester }}学期</template>
            </el-table-column>
            <el-table-column label="成绩" width="90">
              <template #default="{ row }">
                <span :class="{ 'score-fail': row.totalScore !== null && row.totalScore < 60 }">
                  {{ row.totalScore ?? '待录入' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty
          v-else-if="studentScoresLoaded"
          description="该生暂无成绩记录"
          :image-size="60"
        />
      </div>
      <template #footer>
        <el-button @click="studentDetailDialogVisible = false" size="large">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// User 是模板里 `:icon="User"`（学生详情的头像）用的：
// 之前漏了这个 import，模板里的 User 编译成 _ctx.User = undefined，
// el-avatar 收到 icon=undefined 就什么都不画 —— 不报错，只是头像空着。
import { Search, Warning, User } from '@element-plus/icons-vue'
import { teacherAPI } from '@/api/index'

const router = useRouter()
const activeTab = ref('my-classes')
const myClasses = ref([])
const searchKeyword = ref('')
const searchResults = ref([])
const searchPerformed = ref(false)
const myRequests = ref([])
const applyDialogVisible = ref(false)
const applyLoading = ref(false)
const loading = ref(false)
const applyForm = ref({
  classId: null,
  className: '',
  reason: ''
})

// 解除申请相关
const cancelDialogVisible = ref(false)
const cancelLoading = ref(false)
const cancelForm = ref({
  classId: null,
  className: '',
  reason: ''
})

// 学生导入相关
const studentImportDialogVisible = ref(false)
const importLoading = ref(false)
const importForm = ref({
  classId: null,
  className: '',
  file: null
})
const uploadStudentRef = ref(null)

// 学生列表相关
const studentsDialogVisible = ref(false)
const currentClass = ref(null)
const classStudents = ref([])
const loadingStudents = ref(false)
const studentSearch = ref('')

// 学生详情相关
const studentDetailDialogVisible = ref(false)
const currentStudent = ref(null)

// 学生成绩（详情弹窗里的「查看成绩」）
const studentScores = ref([])
const studentScoresLoading = ref(false)
const studentScoresLoaded = ref(false)

const teacherId = ref(null)

// 申请类型筛选器
const requestTypeFilter = ref('')

// 根据筛选条件过滤申请列表
const filteredRequests = computed(() => {
  let result = myRequests.value
  
  if (requestTypeFilter.value !== '') {
    const filterType = parseInt(requestTypeFilter.value)
    result = result.filter(req => {
      const appType = req.applicationType
      return appType === filterType
    })
  }
  
  return result
})

// 根据申请类型筛选申请列表（保留用于统计）
const manageRequests = computed(() => {
  return myRequests.value.filter(req => req.applicationType === undefined || req.applicationType === null || req.applicationType === 0)
})

const cancelRequests = computed(() => {
  return myRequests.value.filter(req => req.applicationType === 1)
})

// 获取教师ID（从localStorage或store）
const getTeacherId = () => {
  const userId = localStorage.getItem('userId')
  return userId ? parseInt(userId) : null
}

// 加载我的班级
const loadMyClasses = async () => {
  try {
    const userId = getTeacherId()
    if (!userId) {
      ElMessage.error('用户ID不存在')
      return
    }
    
    const response = await teacherAPI.getMyClasses(userId)
    
    if (response) {
      myClasses.value = response
    }
  } catch (error) {
    console.error('加载班级失败:', error)
    ElMessage.error('加载班级失败')
  }
}

// 搜索班级
const searchClasses = async () => {
  if (!searchKeyword.value || searchKeyword.value.trim() === '') {
    searchResults.value = []
    searchPerformed.value = false
    return
  }

  try {
    const response = await teacherAPI.searchClasses(searchKeyword.value)
    
    if (response) {
      searchResults.value = response
      searchPerformed.value = true
    }
  } catch (error) {
    console.error('搜索班级失败:', error)
    ElMessage.error('搜索班级失败')
  }
}

// 专业名称映射
const majorMap = {
  1: '计算机科学与技术',
  2: '软件工程',
  3: '人工智能',
  4: '信息管理',
  5: '网络工程'
}

// 获取专业名称
const getMajorName = (majorId) => {
  if (!majorId) return null
  return majorMap[majorId] || `专业${majorId}`
}

// 加载班级
const loadClasses = async (showAll = false) => {
  loading.value = true
  try {
    // 传递空字符串获取所有班级
    const response = await teacherAPI.searchClasses('')
    
    if (response) {
      if (showAll) {
        // 显示所有班级
        searchResults.value = response
      } else {
        // 只显示没有教师的班级
        searchResults.value = response.filter(clazz => !clazz.hasTeacher)
      }
      searchPerformed.value = true
    }
  } catch (error) {
    console.error('加载班级失败:', error)
    ElMessage.error('加载班级失败')
  } finally {
    loading.value = false
  }
}

// 处理标签切换
const handleTabChange = (tabName) => {
  if (tabName === 'add-class') {
    loadAllClasses()
  } else if (tabName === 'my-classes') {
    loadMyClasses()
  }
}

// 加载所有班级（切换标签时自动调用）- 默认显示所有班级
const loadAllClasses = async () => {
  loading.value = true
  try {
    // 获取所有班级
    const allClasses = await teacherAPI.searchClasses('')
    
    if (allClasses) {
      // 默认显示所有班级
      searchResults.value = allClasses
      searchPerformed.value = true
    }
  } catch (error) {
    console.error('加载班级失败:', error)
    ElMessage.error('加载班级失败')
    searchPerformed.value = true
  } finally {
    loading.value = false
  }
}

// 打开申请对话框
const openApplyDialog = (clazz) => {
  applyForm.value = {
    classId: clazz.id,
    className: clazz.name,
    reason: ''
  }
  applyDialogVisible.value = true
}

// 打开解除申请对话框
const openCancelApplicationDialog = (clazz) => {
  cancelForm.value = {
    classId: clazz.id,
    className: clazz.name,
    reason: ''
  }
  cancelDialogVisible.value = true
}

// 提交申请
const submitApply = async () => {
  if (!applyForm.value.reason || applyForm.value.reason.trim() === '') {
    ElMessage.warning('请填写申请原因')
    return
  }

  applyLoading.value = true
  try {
    const userId = getTeacherId()
    const response = await teacherAPI.submitClassManagementRequest({
      teacherId: userId,
      classId: parseInt(applyForm.value.classId),
      className: applyForm.value.className,
      reason: applyForm.value.reason,
      applicationType: 0
    })
    
    if (response) {
      ElMessage.success('申请已提交，等待管理员审核')
      applyDialogVisible.value = false
      loadMyRequests()
      loadAllClasses()  // 使用 loadAllClasses 而不是 searchClasses，避免空关键词导致空白
    }
  } catch (error) {
    console.error('提交申请失败:', error)
    ElMessage.error(error.message || '提交申请失败')
  } finally {
    applyLoading.value = false
  }
}

// 提交解除申请
const submitCancelApplication = async () => {
  if (!cancelForm.value.reason || cancelForm.value.reason.trim() === '') {
    ElMessage.warning('请填写解除原因')
    return
  }

  cancelLoading.value = true
  try {
    const userId = getTeacherId()
    const response = await teacherAPI.submitClassManagementRequest({
      teacherId: userId,
      classId: parseInt(cancelForm.value.classId),
      className: cancelForm.value.className,
      reason: cancelForm.value.reason,
      applicationType: 1
    })
    
    if (response) {
      ElMessage.success('解除申请已提交，等待管理员审核')
      cancelDialogVisible.value = false
      loadMyRequests()
      loadMyClasses()
    }
  } catch (error) {
    console.error('提交解除申请失败:', error)
    ElMessage.error(error.message || '提交解除申请失败')
  } finally {
    cancelLoading.value = false
  }
}

// 加载我的申请
const loadMyRequests = async () => {
  try {
    const userId = getTeacherId()
    const response = await teacherAPI.getMyClassManagementRequests(userId)
    
    if (response) {
      myRequests.value = response
      
      // 检查是否有已通过的申请，如果班级列表为空则自动刷新
      const hasApprovedRequest = response.some(req => req.status === 1)
      if (hasApprovedRequest && myClasses.value.length === 0) {
        await loadMyClasses()
      }
    }
  } catch (error) {
    console.error('加载申请失败:', error)
  }
}

// 跳转到成绩导入页面，并带上班级让成绩页预选（原先参数被丢掉，总是落在第一个班）
const goToScoreImport = (classId) => {
  router.push({ path: '/teacher/scores', query: classId ? { classId } : {} })
}

// 打开学生导入对话框
const openStudentImportDialog = (clazz) => {
  importForm.value = {
    classId: clazz.id,
    className: clazz.name,
    file: null
  }
  studentImportDialogVisible.value = true
}

// 2026-10-02（W4）：`viewClassDetails` 已删 —— 它是一个只弹 toast 的桩，
// 而且**模板里没有任何按钮引用它**（全文件仅此一处定义，grep 计数为 1）。
// 与 AdminRules 的 `testRule` 同型：没被调用的死代码。

// 查看学生列表
const viewStudents = async (clazz) => {
  currentClass.value = clazz
  loadingStudents.value = true
  try {
    const response = await teacherAPI.getClassStudents(clazz.id)
    if (response && response.data) {
      classStudents.value = response.data
    } else if (response && Array.isArray(response)) {
      classStudents.value = response
    } else {
      classStudents.value = []
    }
  } catch (error) {
    console.error('加载学生列表失败:', error)
    ElMessage.error('加载学生列表失败')
    classStudents.value = []
  } finally {
    loadingStudents.value = false
    studentsDialogVisible.value = true
  }
}

// 查看学生详情
const viewStudentDetail = (student) => {
  currentStudent.value = student
  studentScores.value = []
  studentScoresLoaded.value = false
  studentDetailDialogVisible.value = true
}

/**
 * 详情弹窗里的「查看成绩」：复用 /teachers/scores/by-class（join 过、带课程名），
 * 按学号匹配本班成绩行。本班接口给的 `studentId` 是学号，by-class 行里的 `studentNo`
 * 同口径；学号对不上时退回姓名匹配，两者都不认的行不显示。
 */
const viewStudentScores = async () => {
  if (!currentStudent.value || !currentClass.value || !teacherId.value) return
  studentScoresLoading.value = true
  try {
    const res = await teacherAPI.getScoresByClass(currentClass.value.name, teacherId.value)
    const list = Array.isArray(res) ? res : []
    const no = String(currentStudent.value.studentId ?? '')
    studentScores.value = list
      .filter(s => (no && s.studentNo ? String(s.studentNo) === no : s.studentName === currentStudent.value.studentName))
      .map(s => ({
        courseName: s.courseName || ('课程' + s.courseId),
        semester: s.semester,
        totalScore: s.scoreTotal ?? s.totalScore ?? null
      }))
    studentScoresLoaded.value = true
  } catch (error) {
    console.error('[class-mgmt] 学生成绩获取失败:', error)
    ElMessage.error('学生成绩获取失败')
  } finally {
    studentScoresLoading.value = false
  }
}

// 筛选学生
const filteredStudents = computed(() => {
  if (!studentSearch.value) {
    return classStudents.value
  }
  const query = studentSearch.value.toLowerCase()
  return classStudents.value.filter(student => 
    student.studentName.toLowerCase().includes(query) || 
    student.studentId.includes(query)
  )
})

// 处理学生文件选择
const handleStudentFileSelect = (uploadFile) => {
  importForm.value.file = uploadFile.raw
}

// 导入学生
// 后端做的是"把已存在的学生归入本班"（改 student_profile.class_id），所以必须带上
// teacherId —— 服务端据此校验这个班确实是他的，而不是信前端传一个布尔值。
// 原来这里对任何成功响应都报"学生导入成功"，而后端那时是个桩、一行库都不写。
const importStudents = async () => {
  if (!importForm.value.file) {
    ElMessage.error('请选择Excel文件')
    return
  }

  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importForm.value.file)
    formData.append('classId', importForm.value.classId)
    formData.append('teacherId', getTeacherId())

    const res = await teacherAPI.importStudents(formData)

    // 逐行尽力而为：成功的行已经落库，失败的行在 errors 里。跳过大于 0 就不能只报"成功"。
    const success = res?.success ?? 0
    const skipped = res?.skipped ?? 0
    const moved = res?.moved ?? 0
    const errorList = res?.errors || []
    const movedNote = moved ? `（其中 ${moved} 名原本在别的班级）` : ''
    if (skipped === 0) {
      ElMessage.success(`导入完成：${success} 名学生已归入本班${movedNote}`)
    } else {
      const detail = errorList.slice(0, 5).map(e => `第${e.row}行：${e.reason}`).join('；')
      const more = errorList.length > 5 ? ` 等共 ${errorList.length} 处` : ''
      ElMessage.warning(`导入完成：成功 ${success} 条${movedNote}，跳过 ${skipped} 条。${detail}${more}`)
    }
    studentImportDialogVisible.value = false
    loadMyClasses()
    uploadStudentRef.value.clearFiles()
  } catch (error) {
    console.error('导入学生失败:', error)
    ElMessage.error(error.response?.data?.message || error.message || '导入学生失败')
  } finally {
    importLoading.value = false
  }
}

// 解除班级管理
const cancelClassManagement = async (clazz) => {
  try {
    await ElMessageBox.confirm(
      `确定要解除对 "${clazz.name}" 的管理权限吗？`,
      '确认解除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const userId = getTeacherId()
    const response = await teacherAPI.cancelClassManagement({
      teacherId: userId,
      classId: clazz.id
    })
    
    // 响应拦截器处理后，成功时返回的是字符串消息，失败时会抛出异常
    // 所以只要到这里就说明成功了
    ElMessage.success('已成功解除班级管理权限')
    // 立即从本地状态中移除该班级，避免缓存问题
    myClasses.value = myClasses.value.filter(c => c.id !== clazz.id)
    loadMyRequests()
    // 刷新添加班级界面的数据，使刚解除管理的班级可以被重新申请
    loadAllClasses()
  } catch (error) {
    console.error('解除班级管理失败:', error)
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解除班级管理失败')
    }
  }
}

// 获取状态文本
const getStatusText = (status) => {
  const statusMap = {
    0: '待审核',
    1: '已通过',
    2: '已拒绝',
    3: '已取消',
    'pending': '待审核',
    'approved': '已通过',
    'rejected': '已拒绝',
    'cancelled': '已取消'
  }
  return statusMap[status] || status
}

// 获取状态标签类型
const getStatusType = (status) => {
  const typeMap = {
    0: 'warning',
    1: 'success',
    2: 'danger',
    3: 'info',
    'pending': 'warning',
    'approved': 'success',
    'rejected': 'danger',
    'cancelled': 'info'
  }
  return typeMap[status] || 'info'
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  teacherId.value = getTeacherId()
  loadMyClasses()
  loadMyRequests()
  // 如果默认显示的是添加班级标签，自动加载班级列表
  if (activeTab.value === 'add-class') {
    loadAllClasses()
  }
})
</script>

<style scoped>
.class-management-container {
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  min-height: 100vh;
}

.tabs {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* 我的班级 */
.my-classes-section,
.add-class-section {
  padding: 20px 0;
}

.requests-section {
  padding: 0;
}

.classes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

.class-card {
  background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  padding: 16px;
  transition: all 0.3s ease;
}

.class-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  border-color: #667eea;
}

.class-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.class-header h3 {
  margin: 0;
  font-size: 16px;
  color: #2c3e50;
}

.class-info,
.result-info {
  margin: 12px 0;
  font-size: 14px;
  color: #666;
}

.label {
  font-weight: 600;
  color: #2c3e50;
  margin-right: 8px;
}

.class-actions,
.result-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

/* 搜索框 */
.search-box {
  margin-bottom: 20px;
}

.search-input {
  max-width: 400px;
}

.search-results {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  overflow-y: auto;
  max-height: calc(100vh - 300px);
  padding-right: 10px;
}

.result-card {
  background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  padding: 16px;
  transition: all 0.3s ease;
}

.result-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  border-color: #667eea;
}

.result-header h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: #2c3e50;
}

.warning {
  color: #f56c6c;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

/* 申请列表 */
.requests-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.request-item {
  background: white;
  border: 1px solid #e8ecf1;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s ease;
}

.request-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.request-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.request-header h4 {
  margin: 0;
  color: #2c3e50;
}

.request-info {
  font-size: 14px;
  color: #666;
}

.request-info p {
  margin: 6px 0;
}

/* 拒绝原因（W1.14） */
.reject-reason {
  color: #f56c6c;
}

/* 学生列表相关样式 */
.loading-container {
  padding: 20px 0;
}

.mb-4 {
  margin-bottom: 16px;
}

.empty-students {
  padding: 40px 0;
  text-align: center;
}

/* 学生列表弹窗美化 */
.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.student-count {
  margin-left: 20px;
}

.students-table-wrapper {
  background: #ffffff;
  border-radius: 8px;
  overflow: hidden;
}

.student-id {
  font-family: 'Monaco', 'Menlo', monospace;
  color: #3b82f6;
  font-weight: 500;
}

.student-name {
  color: #2c3e50;
  font-weight: 500;
}

/* 学生详情弹窗美化 */
.student-detail-card {
  padding: 20px;
}

.student-avatar {
  text-align: center;
  margin-bottom: 16px;
}

.avatar-large {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 40px;
}

.student-info {
  text-align: center;
  margin-bottom: 20px;
}

.student-name-title {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #2c3e50;
  font-weight: 600;
}

.student-id-text {
  margin: 0;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #64748b;
  font-size: 14px;
}

.detail-descriptions {
  padding: 16px 0;
}

.detail-value {
  color: #2c3e50;
  font-weight: 500;
}

.detail-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e8ecf1;
}

.score-fail {
  color: #f56c6c;
  font-weight: 600;
}

/* 弹窗动画效果 */
:deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
}

:deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
  padding: 20px 24px;
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

:deep(.el-dialog__body) {
  padding: 24px;
}

:deep(.el-dialog__footer) {
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  padding: 16px 24px;
}

:deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

:deep(.el-table__row:hover) {
  background-color: #f8fafc !important;
}

:deep(.el-table__row.current-row) {
  background-color: #eff6ff !important;
}
</style>
