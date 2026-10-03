<template>
  <div class="admin-colleges" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>学院管理</h1>
      <p>学院信息查看和管理</p>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="addCollegDialogVisible = true">
        <el-icon><Plus /></el-icon> 添加学院
      </el-button>
      <el-input v-model="searchText" placeholder="搜索学院名称或ID" style="width: 250px;" @input="handleSearch" clearable />
      <el-button @click="loadColleges" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
      <el-button @click="exportColleges">导出</el-button>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>学院列表</span>
          <span class="college-count">共 {{ collegeList.length }} 个学院</span>
        </div>
      </template>

      <div v-if="collegeList.length === 0" style="padding: 20px; text-align: center; color: #999;">
        暂无数据
      </div>
      <el-table style="width: 100%" v-else :data="filteredCollegeList" stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="学院ID" width="100" sortable />
        <el-table-column prop="name" label="学院名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="studentCount" label="学生数" width="100" sortable>
          <template #default="{ row }">
            <el-tag type="success">{{ row.studentCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherCount" label="教师数" width="100" sortable>
          <template #default="{ row }">
            <el-tag type="info">{{ row.teacherCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="counselorCount" label="辅导员数" width="120" sortable>
          <template #default="{ row }">
            <el-tag type="warning">{{ row.counselorCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="majorCount" label="专业数" width="100" sortable>
          <template #default="{ row }">
            <el-tag type="primary">{{ row.majorCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="editCollege(row)">编辑</el-button>
              <el-button type="danger" size="small" @click="deleteCollege(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="selection-info" v-if="selectedColleges.length > 0">
          已选择 {{ selectedColleges.length }} 项
          <el-button type="danger" size="small" @click="batchDelete">批量删除</el-button>
        </div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalColleges"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加对话框 -->
    <el-dialog v-model="addCollegDialogVisible" title="添加学院" width="500px" :close-on-click-modal="false">
      <el-form ref="collegeFormRef" :model="collegeForm" :rules="formRules" label-width="100px">
        <el-form-item label="学院名称" prop="name">
          <el-input v-model="collegeForm.name" placeholder="请输入学院名称"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addCollegDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAddCollege">确认</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editCollegDialogVisible" title="编辑学院" width="500px" :close-on-click-modal="false">
      <el-form ref="editCollegeFormRef" :model="editingCollege" :rules="formRules" label-width="100px">
        <el-form-item label="学院名称" prop="name">
          <el-input v-model="editingCollege.name" placeholder="请输入学院名称"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editCollegDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditCollege">确认</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除学院 <strong>{{ collegeToDelete?.name }}</strong> 吗？</p>
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
        <p>确定要删除选中的 <strong>{{ selectedColleges.length }} 个学院</strong> 吗？</p>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAPI } from '@/api/index'
import { Plus, Refresh, Edit, Delete, WarningFilled } from '@element-plus/icons-vue'

const addCollegDialogVisible = ref(false)
const editCollegDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const batchDeleteDialogVisible = ref(false)
const collegeList = ref([])
const majorList = ref([])
const searchText = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const deleteLoading = ref(false)
const selectedColleges = ref([])
const collegeToDelete = ref(null)

const collegeFormRef = ref(null)
const editCollegeFormRef = ref(null)

const collegeForm = ref({
  name: ''
})

const editingCollege = ref({
  id: null,
  name: ''
})

const formRules = {
  name: [
    { required: true, message: '请输入学院名称', trigger: 'blur' },
    { min: 2, max: 100, message: '学院名称长度为 2-100 个字符', trigger: 'blur' }
  ]
}

onMounted(async () => {
  await loadMajors()
  await loadColleges()
})

const loadColleges = async () => {
  loading.value = true
  try {
    console.log('[AdminColleges] 开始加载学院列表...')
    const response = await adminAPI.getColleges()
    console.log('[AdminColleges] 获取响应:', response)
    // 2026-10-02（W4.15）：原来这里还有一支 `else if (response.code === 200)`
    // —— 响应拦截器（api/client.js）在 code===200 时**已经解开一层**并返回内层载荷，
    // 所以 await 出来的就是数组本身，`response.code` 恒为 undefined，那一支**永不执行**。
    // 死分支的害处不是"多写了几行"，而是让人以为接口有另一种返回形状。
    let data = []
    if (Array.isArray(response)) {
      data = response
    } else {
      console.warn('[AdminColleges] 响应格式异常:', response)
    }
    collegeList.value = data.map(college => ({
      ...college,
      majorCount: getMajorCountByCollege(college.id)
    }))
    console.log('[AdminColleges] 学院列表已加载:', collegeList.value.length, '条数据')
  } catch (error) {
    console.error('[AdminColleges] 加载学院列表失败:', error)
  } finally {
    loading.value = false
  }
}

const loadMajors = async () => {
  try {
    console.log('[AdminColleges] 开始加载专业列表...')
    const response = await adminAPI.getMajors()
    // 2026-10-02（W4.15）：删掉恒假的 `response.code === 200` 支（拦截器已解包）。
    if (Array.isArray(response)) {
      majorList.value = response
      console.log('[AdminColleges] 专业列表已加载:', majorList.value.length, '条数据')
    } else {
      console.warn('[AdminColleges] 专业响应格式异常:', response)
    }
  } catch (error) {
    console.error('[AdminColleges] 加载专业列表失败:', error)
  }
}

const getMajorCountByCollege = (collegeId) => {
  return majorList.value.filter(major => major.collegeId === collegeId).length
}

const handleSearch = () => {
  currentPage.value = 1
}

const handleSelectionChange = (selection) => {
  selectedColleges.value = selection
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

const filteredCollegeList = computed(() => {
  let filtered = collegeList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(college =>
      college.name.toLowerCase().includes(searchLower) ||
      String(college.id).includes(searchLower)
    )
  }

  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filtered.slice(start, end)
})

const totalColleges = computed(() => {
  let filtered = collegeList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(college =>
      college.name.toLowerCase().includes(searchLower) ||
      String(college.id).includes(searchLower)
    )
  }

  return filtered.length
})

const editCollege = (row) => {
  editingCollege.value = { ...row }
  editCollegDialogVisible.value = true
}

const submitEditCollege = async () => {
  if (!editCollegeFormRef.value) return
  
  try {
    await editCollegeFormRef.value.validate()
    await adminAPI.updateCollege(editingCollege.value.id, { name: editingCollege.value.name })
    ElMessage.success('学院已更新')
    editCollegDialogVisible.value = false
    const index = collegeList.value.findIndex(item => item.id === editingCollege.value.id)
    if (index !== -1) {
      collegeList.value[index].name = editingCollege.value.name
    }
  } catch (error) {
    console.error('更新学院失败:', error)
    ElMessage.error('更新失败')
  }
}

const deleteCollege = (row) => {
  collegeToDelete.value = row
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!collegeToDelete.value) return

  deleteLoading.value = true
  try {
    await adminAPI.deleteCollege(collegeToDelete.value.id)
    ElMessage.success('学院已删除')
    deleteDialogVisible.value = false
    collegeToDelete.value = null
    // 2026-10-02（W4.3）：这里原来是
    //   `collegeList.value.filter(item => item.id !== collegeToDelete.value?.id)`
    // —— 上一行刚把它置成 null，`?.id` 求值成 undefined，`item.id !== undefined`
    // **恒真**，于是过滤后一行不少：后端删成功了、页面上那行还在。
    // 改成重新拉取（与 AdminCourses/AdminUsers 同形），顺带把"本院专业数"之类的
    // 派生数据也刷新一遍，不必手工维护本地数组。
    // 专业也要重拉：学院删掉后它名下的专业在后端一起没了，而本页的「专业数」
    // 是用本地 majorList 现算的（getMajorCountByCollege），不重拉就会多算。
    await Promise.all([loadColleges(), loadMajors()])
  } catch (error) {
    console.error('删除学院失败:', error)
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const batchDelete = () => {
  if (selectedColleges.value.length === 0) {
    ElMessage.warning('请先选择要删除的学院')
    return
  }
  batchDeleteDialogVisible.value = true
}

const confirmBatchDelete = async () => {
  deleteLoading.value = true
  try {
    const deletePromises = selectedColleges.value.map(college =>
      adminAPI.deleteCollege(college.id)
    )
    await Promise.all(deletePromises)
    ElMessage.success(`成功删除 ${selectedColleges.value.length} 个学院`)
    batchDeleteDialogVisible.value = false
    selectedColleges.value = []
    await loadColleges()
  } catch (error) {
    console.error('批量删除失败:', error)
    ElMessage.error('批量删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const submitAddCollege = async () => {
  if (!collegeFormRef.value) return
  
  try {
    await collegeFormRef.value.validate()
    const data = { name: collegeForm.value.name }
    await adminAPI.createCollege(data)
    ElMessage.success('学院已添加')
    addCollegDialogVisible.value = false
    collegeForm.value.name = ''
    await loadColleges()
  } catch (error) {
    console.error('添加学院失败:', error)
    ElMessage.error('添加失败')
  }
}

const exportColleges = async () => {
  try {
    const blob = await adminAPI.exportColleges()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `colleges_${new Date().getTime()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('学院列表已导出')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败，请稍后重试')
  }
}
</script>

<style scoped>
.admin-colleges {
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

.college-count {
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

.admin-colleges :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-colleges :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-colleges :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-colleges :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-colleges :deep(.el-button--danger:hover) {
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
</style>