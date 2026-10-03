<template>
  <div class="admin-majors" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>专业管理</h1>
      <p>专业信息查看和管理</p>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="addDialogVisible = true">
        <el-icon><Plus /></el-icon> 添加专业
      </el-button>
      <el-select v-model="filterCollegeId" placeholder="按学院筛选" style="width: 200px;" @change="handleFilter">
        <el-option label="全部学院" value=""></el-option>
        <el-option v-for="college in collegeList" :key="college.id" :label="college.name" :value="college.id"></el-option>
      </el-select>
      <el-input v-model="searchText" placeholder="搜索专业名称或ID" style="width: 250px;" @input="handleSearch" clearable />
      <el-button @click="loadMajors" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>专业列表</span>
          <span class="major-count">共 {{ majorList.length }} 个专业</span>
        </div>
      </template>

      <div v-if="majorList.length === 0" style="padding: 20px; text-align: center; color: #999;">
        暂无数据
      </div>
      <el-table v-else :data="filteredMajorList" stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="专业ID" width="100" sortable />
        <el-table-column prop="code" label="专业代码" width="120" />
        <el-table-column prop="name" label="专业名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="shortName" label="专业简称" width="100" />
        <el-table-column label="所属学院" width="150">
          <template #default="{ row }">
            {{ collegeList.find(c => c.id === row.collegeId)?.name || '未指定' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="editMajor(row)">编辑</el-button>
              <el-button type="danger" size="small" @click="deleteMajor(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="selection-info" v-if="selectedMajors.length > 0">
          已选择 {{ selectedMajors.length }} 项
          <el-button type="danger" size="small" @click="batchDelete">批量删除</el-button>
        </div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalMajors"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="addDialogVisible" title="添加专业" width="500px" :close-on-click-modal="false">
      <el-form ref="majorFormRef" :model="majorForm" :rules="formRules" label-width="100px">
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="majorForm.name" placeholder="输入专业名称"></el-input>
        </el-form-item>
        <el-form-item label="专业简称" prop="shortName">
          <el-input v-model="majorForm.shortName" placeholder="输入专业简称，如：计科、软工"></el-input>
        </el-form-item>
        <el-form-item label="所属学院" prop="collegeId">
          <el-select v-model="majorForm.collegeId" placeholder="选择学院">
            <el-option v-for="college in collegeList" :key="college.id" :label="college.name" :value="college.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="专业代码">
          <!-- 2026-10-02（W4.14）：此前没有这一行，而提交时前端悄悄编了一个码
               （`maxId + 1` 补零）。现在由后端按全表最大值 +1 生成，这里只说明去向。 -->
          <span class="muted">留空由系统生成（按现有最大专业码 +1，如 15）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认</el-button>
      </template>
    </el-dialog>

    <!-- 编辑专业对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑专业" width="500px" :close-on-click-modal="false">
      <el-form ref="editMajorFormRef" :model="editingMajor" :rules="formRules" label-width="100px">
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="editingMajor.name" placeholder="输入专业名称"></el-input>
        </el-form-item>
        <el-form-item label="专业简称" prop="shortName">
          <el-input v-model="editingMajor.shortName" placeholder="输入专业简称，如：计科、软工"></el-input>
        </el-form-item>
        <el-form-item label="所属学院" prop="collegeId">
          <el-select v-model="editingMajor.collegeId" placeholder="选择学院">
            <el-option v-for="college in collegeList" :key="college.id" :label="college.name" :value="college.id"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditMajor">确认</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除专业 <strong>{{ majorToDelete?.name }}</strong> 吗？</p>
        <p class="warning-text">此操作不可逆，删除后将无法恢复！</p>
      </div>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete" :loading="deleteLoading">确认删除</el-button>
      </template>
    </el-dialog>

    <!-- 批量删除确认弹窗 -->
    <el-dialog v-model="batchDeleteDialogVisible" title="批量删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#F56C6C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除选中的 <strong>{{ selectedMajors.length }} 个专业</strong> 吗？</p>
        <p class="warning-text">此操作不可逆，删除后将无法恢复！</p>
      </div>
      <template #footer>
        <el-button @click="batchDeleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBatchDelete" :loading="deleteLoading">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { adminAPI } from '@/api/index'
import { Plus, Refresh, Edit, Delete, WarningFilled } from '@element-plus/icons-vue'

const addDialogVisible = ref(false)
const editDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const batchDeleteDialogVisible = ref(false)
const majorList = ref([])
const collegeList = ref([])
const filterCollegeId = ref('')
const searchText = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const deleteLoading = ref(false)
const selectedMajors = ref([])
const majorToDelete = ref(null)

const majorFormRef = ref(null)
const editMajorFormRef = ref(null)

// 2026-10-02（W4.14）：去掉 `code` —— 表单里从来没有这个输入框，
// 专业码由后端按全表生成（见 submitAdd 的说明）。
const majorForm = ref({ name: '', shortName: '', collegeId: '' })
const editingMajor = ref({ id: null, code: '', name: '', shortName: '', collegeId: '' })

const formRules = {
  name: [
    { required: true, message: '请输入专业名称', trigger: 'blur' },
    { min: 2, max: 100, message: '专业名称长度为 2-100 个字符', trigger: 'blur' }
  ],
  shortName: [
    { required: true, message: '请输入专业简称', trigger: 'blur' },
    { min: 1, max: 20, message: '专业简称长度为 1-20 个字符', trigger: 'blur' }
  ],
  collegeId: [
    { required: true, message: '请选择所属学院', trigger: 'change' }
  ]
}

onMounted(async () => {
  await loadColleges()
  await loadMajors()
})

const loadColleges = async () => {
  try {
    const response = await adminAPI.getColleges()
    // 2026-10-02（W4.15）：删掉恒真的 `response.code === 200` 支 —— 拦截器已解包，
    // 那一支永不执行，留着会让人以为接口有两种返回形状。
    if (Array.isArray(response)) {
      collegeList.value = response
    }
  } catch (error) {
    console.error('加载学院列表失败:', error)
  }
}

const loadMajors = async () => {
  loading.value = true
  try {
    const response = await adminAPI.getMajors()
    // 2026-10-02（W4.15）：删掉恒真的 `response.code === 200` 支（拦截器已解包）。
    const allMajors = Array.isArray(response) ? response : []
    
    if (filterCollegeId.value) {
      majorList.value = allMajors.filter(major => major.collegeId === parseInt(filterCollegeId.value))
    } else {
      majorList.value = allMajors
    }
  } catch (error) {
    console.error('加载专业列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  currentPage.value = 1
  loadMajors()
}

const handleSearch = () => {
  currentPage.value = 1
}

const handleSelectionChange = (selection) => {
  selectedMajors.value = selection
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

const filteredMajorList = computed(() => {
  let filtered = majorList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(major =>
      major.name.toLowerCase().includes(searchLower) ||
      String(major.id).includes(searchLower) ||
      (major.code && major.code.toLowerCase().includes(searchLower))
    )
  }

  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filtered.slice(start, end)
})

const totalMajors = computed(() => {
  let filtered = majorList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(major =>
      major.name.toLowerCase().includes(searchLower) ||
      String(major.id).includes(searchLower) ||
      (major.code && major.code.toLowerCase().includes(searchLower))
    )
  }

  return filtered.length
})

const editMajor = (row) => {
  editingMajor.value = { ...row }
  editDialogVisible.value = true
}

const submitEditMajor = async () => {
  if (!editMajorFormRef.value) return
  
  try {
    await editMajorFormRef.value.validate()
    const data = {
      name: editingMajor.value.name,
      shortName: editingMajor.value.shortName,
      collegeId: parseInt(editingMajor.value.collegeId)
    }
    await adminAPI.updateMajor(editingMajor.value.id, data)
    ElMessage.success('专业已更新')
    editDialogVisible.value = false
    const index = majorList.value.findIndex(item => item.id === editingMajor.value.id)
    if (index !== -1) {
      majorList.value[index].name = editingMajor.value.name
      majorList.value[index].shortName = editingMajor.value.shortName
      majorList.value[index].collegeId = editingMajor.value.collegeId
    }
  } catch (error) {
    console.error('更新专业失败:', error)
    ElMessage.error('更新失败')
  }
}

const deleteMajor = (row) => {
  majorToDelete.value = row
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!majorToDelete.value) return

  deleteLoading.value = true
  try {
    await adminAPI.deleteMajor(majorToDelete.value.id)
    ElMessage.success('专业已删除')
    deleteDialogVisible.value = false
    majorToDelete.value = null
    // 2026-10-02（W4.3）：原来这里是 `filter(item => item.id !== majorToDelete.value?.id)`，
    // 而上一行刚把它置 null ⇒ 比较的是 undefined ⇒ 过滤恒真 ⇒ 行不消失。
    // 改成重新拉取（与 AdminCourses/AdminUsers 同形）。
    await loadMajors()
  } catch (error) {
    console.error('删除专业失败:', error)
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const batchDelete = () => {
  if (selectedMajors.value.length === 0) {
    ElMessage.warning('请先选择要删除的专业')
    return
  }
  batchDeleteDialogVisible.value = true
}

const confirmBatchDelete = async () => {
  deleteLoading.value = true
  try {
    const deletePromises = selectedMajors.value.map(major =>
      adminAPI.deleteMajor(major.id)
    )
    await Promise.all(deletePromises)
    ElMessage.success(`成功删除 ${selectedMajors.value.length} 个专业`)
    batchDeleteDialogVisible.value = false
    selectedMajors.value = []
    await loadMajors()
  } catch (error) {
    console.error('批量删除失败:', error)
    ElMessage.error('批量删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const submitAdd = async () => {
  if (!majorFormRef.value) return
  
  try {
    await majorFormRef.value.validate()
    // 2026-10-02（W4.14）：**不再在前端编专业码**。
    // 原来这里是 `String(maxId + 1).padStart(2, '0')`，而 maxId 取自当前列表 ——
    // 列表可能正被学院筛选过，算出来的码会撞 `majors.code` 的 UNIQUE；
    // 而且它比的是 `id` 不是 `code`，只是活库里两者恰好对齐才没暴露。
    // 现在不传 code，由后端按**全表**最大值 +1 生成（见 AdminController.createMajor）。
    const data = {
      name: majorForm.value.name,
      shortName: majorForm.value.shortName,
      collegeId: parseInt(majorForm.value.collegeId)
    }
    await adminAPI.createMajor(data)
    ElMessage.success('专业已添加')
    addDialogVisible.value = false
    majorForm.value = { name: '', shortName: '', collegeId: '' }
    await loadMajors()
  } catch (error) {
    console.error('添加专业失败:', error)
    ElMessage.error('添加失败')
  }
}
</script>

<style scoped>
.admin-majors {
  padding: 20px;
  background-color: #f8f9fa !important;
  min-height: 100vh;
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

.action-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  align-items: center;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.major-count {
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

.delete-warning .warning-text {
  color: #F56C6C;
  font-size: 14px;
}

.admin-majors :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-majors :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-majors :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-majors :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-majors :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}

.action-buttons {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.action-buttons .el-button {
  margin: 0;
}

/* 「专业代码由系统生成」的说明文字（W4.14） */
.muted {
  color: #909399;
  font-size: 13px;
}
</style>