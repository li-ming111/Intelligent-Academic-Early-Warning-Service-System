<template>
  <div class="admin-courses" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>课程管理</h1>
      <p>课程信息查看和管理</p>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="openAddDialog" style="margin-right: auto;">
        <el-icon><Plus /></el-icon> 添加课程
      </el-button>
      <el-input v-model="searchText" placeholder="搜索课程名称或ID" style="width: 250px;" @input="handleSearch" clearable />
      <el-select v-model="filterType" placeholder="选择课程类型" style="width: 150px;" @change="handleSearch" clearable>
        <el-option label="全部" value=""></el-option>
        <el-option label="必修课" value="必修课"></el-option>
        <el-option label="选修课" value="选修课"></el-option>
      </el-select>
      <el-button @click="loadCourses" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程列表</span>
          <span class="course-count">共 {{ courseList.length }} 门课程</span>
        </div>
      </template>
      <el-table style="width: 100%" :data="filteredCourseList" stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="课程ID" width="100" sortable />
        <el-table-column prop="code" label="课程代码" width="150" />
        <el-table-column prop="name" label="课程名称" min-width="250" show-overflow-tooltip />
        <el-table-column prop="credits" label="学分" width="100" sortable>
          <template #default="{ row }">
            <el-tag type="success">{{ row.credits }}</el-tag>
          </template>
        </el-table-column>
        <!-- 2026-10-02（W4.13）：这一列与下面两处筛选、编辑弹窗统一读 `row.type`。
             `courses` 表的列就叫 `type`（见本文件下方那句列清单注释），admin-service
             `convertCoursesToMaps` 同时吐 `type` 与 `courseType` 两个 key、取值完全相同
             ⇒ 读哪个都对，但同一个值有两个名字迟早漂移，收敛到与列名/表单字段同名的那一个。 -->
        <el-table-column prop="type" label="课程分类" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === '必修课' ? 'primary' : 'info'">
              {{ row.type || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授课教师" width="120">
          <template #default="{ row }">
            <!-- `'教师'+row.teacherId` 恒为真（null 会拼成字符串 "教师null"），
                 所以后面那个 '--' 兜底永远轮不到。改成先判 id 有没有值。 -->
            {{ teacherMap[row.teacherId] || (row.teacherId ? '教师' + row.teacherId : '--') }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <!-- 2026-10-01 第二十一轮删 `:disabled="row.isSystem"`：
                 `courses` 表的列只有 id/code/name/credits/type/description/module_id/
                 score_template/teacher_id/created_at/updated_at，全仓 Java 里也没有
                 isSystem 任何一处 —— 这个 key 恒为 undefined ⇒ 绑定恒为 false，
                 按钮永远可点。它不拦任何东西，只是让人以为"内置课不可删"。
                 要真做这层保护得先有那一列，现在只把假的删掉。 -->
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="selection-info" v-if="selectedCourses.length > 0">
          已选择 {{ selectedCourses.length }} 项
          <el-button type="danger" size="small" @click="batchDelete">批量删除</el-button>
        </div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalCourses"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加/编辑课程弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑课程' : '添加课程'"
      width="600px"
      :close-on-click-modal="false"
      @close="resetForm"
    >
      <el-form ref="courseFormRef" :model="courseForm" :rules="formRules" label-width="100px">
        <el-form-item label="课程代码" prop="code">
          <el-input v-model="courseForm.code" placeholder="请输入课程代码，如：CS101" />
        </el-form-item>
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="courseForm.name" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="学分" prop="credits">
          <el-input-number v-model="courseForm.credits" :min="0" :max="20" :precision="1" placeholder="请输入学分" />
        </el-form-item>
        <el-form-item label="课程类型" prop="type">
          <el-select v-model="courseForm.type" placeholder="请选择课程类型" style="width: 100%;">
            <el-option label="必修课" value="必修课" />
            <el-option label="选修课" value="选修课" />
            <el-option label="实践课" value="实践课" />
            <el-option label="公选课" value="公选课" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程描述">
          <el-input v-model="courseForm.description" type="textarea" :rows="3" placeholder="请输入课程描述（可选）" />
        </el-form-item>
        <el-form-item label="授课教师" prop="teacherId">
          <el-select v-model="courseForm.teacherId" placeholder="选择授课教师" style="width: 100%;">
            <el-option v-for="t in teacherOptions" :key="t.id" :label="t.name + ' (ID:' + t.id + ')'" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块ID">
          <el-input-number v-model="courseForm.moduleId" :min="0" placeholder="请输入模块ID（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm" :loading="submitLoading">
            {{ isEdit ? '保存修改' : '添加课程' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除课程 <strong>{{ courseToDelete?.name }}</strong> 吗？</p>
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
        <p>确定要删除选中的 <strong>{{ selectedCourses.length }} 门课程</strong> 吗？</p>
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

const courseList = ref([])
const teacherMap = ref({})
const teacherOptions = ref([])
const searchText = ref('')
const filterType = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const selectedCourses = ref([])

// 弹窗相关
const dialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const batchDeleteDialogVisible = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const isEdit = ref(false)
const courseToDelete = ref(null)
const courseFormRef = ref(null)

// 课程表单
const courseForm = ref({
  id: null,
  code: '',
  name: '',
  credits: 0,
  type: '',
  description: '',
  teacherId: null,
  moduleId: null
})

// 表单验证规则
const formRules = {
  code: [
    { required: true, message: '请输入课程代码', trigger: 'blur' },
    { min: 2, max: 50, message: '课程代码长度为 2-50 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入课程名称', trigger: 'blur' },
    { min: 2, max: 200, message: '课程名称长度为 2-200 个字符', trigger: 'blur' }
  ],
  credits: [
    { required: true, message: '请输入学分', trigger: 'blur' },
    { type: 'number', min: 0.1, max: 20, message: '学分必须在 0.1-20 之间', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择课程类型', trigger: 'change' }
  ],
  teacherId: [
    { required: true, message: '请输入授课教师ID', trigger: 'blur' }
  ]
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadTeachers()])
})

const loadTeachers = async () => {
  try {
    const res = await adminAPI.getUsers(1, 999, null, 2)
    // ⚠️ `/admin/users/page` 回的是 `{total, items:[...]}`（实测），既不是 MyBatis-Plus 的
    // `records`，也不是裸数组。原来只认 `.records`/数组，三个分支全落空 → teachers 恒为 []
    // → 「授课教师」下拉永远是空的，而接口 200、控制台干净、没有任何提示。
    let teachers = []
    if (Array.isArray(res)) teachers = res
    else if (Array.isArray(res?.items)) teachers = res.items
    else if (Array.isArray(res?.list)) teachers = res.list
    else if (Array.isArray(res?.records)) teachers = res.records
    else if (Array.isArray(res?.data)) teachers = res.data
    // 一个教师都没解析出来时不装作"没有教师"：这条路径要么接口挂了、要么形状又变了
    else console.warn('教师列表形状不认识，授课教师下拉会是空的：', res && Object.keys(res))
    teacherOptions.value = teachers.map(t => ({ id: t.id, name: t.name || t.username }))
    teacherMap.value = {}
    teachers.forEach(t => { teacherMap.value[t.id] = t.name || t.username })
  } catch (e) { console.error('加载教师列表失败', e) }
}

const loadCourses = async () => {
  loading.value = true
  try {
    const response = await adminAPI.getCourses()
    // 2026-10-02（W4.15）：删掉恒假的 `response.code === 200` 支 ——
    // 响应拦截器（api/client.js）在 code===200 时已解开一层，await 出来的就是内层载荷，
    // `response.code` 恒 undefined，那一支永不执行。
    courseList.value = Array.isArray(response) ? response : []
  } catch (error) {
    console.error('加载课程列表失败:', error)
    ElMessage.error('加载课程列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
}

const handleSelectionChange = (selection) => {
  selectedCourses.value = selection
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

const filteredCourseList = computed(() => {
  let filtered = courseList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(course =>
      course.name.toLowerCase().includes(searchLower) ||
      String(course.id).includes(searchLower) ||
      (course.code && course.code.toLowerCase().includes(searchLower))
    )
  }

  if (filterType.value) {
    filtered = filtered.filter(course => course.type === filterType.value)
  }

  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filtered.slice(start, end)
})

const totalCourses = computed(() => {
  let filtered = courseList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(course =>
      course.name.toLowerCase().includes(searchLower) ||
      String(course.id).includes(searchLower) ||
      (course.code && course.code.toLowerCase().includes(searchLower))
    )
  }

  if (filterType.value) {
    filtered = filtered.filter(course => course.type === filterType.value)
  }

  return filtered.length
})

// 打开添加弹窗
const openAddDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 打开编辑弹窗
const openEditDialog = (row) => {
  isEdit.value = true
  courseForm.value = {
    id: row.id,
    code: row.code || '',
    name: row.name || '',
    credits: Number(row.credits) || 0,
    type: row.type || '',
    description: row.description || '',
    teacherId: row.teacherId ? Number(row.teacherId) : null,
    moduleId: row.moduleId ? Number(row.moduleId) : null
  }
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  if (courseFormRef.value) {
    courseFormRef.value.resetFields()
  }
  courseForm.value = {
    id: null,
    code: '',
    name: '',
    credits: 0,
    type: '',
    description: '',
    teacherId: null,
    moduleId: null
  }
}

// 提交表单
const submitForm = async () => {
  if (!courseFormRef.value) return

  try {
    await courseFormRef.value.validate()
    submitLoading.value = true

    const data = {
      code: courseForm.value.code,
      name: courseForm.value.name,
      credits: courseForm.value.credits,
      type: courseForm.value.type,
      description: courseForm.value.description || null,
      teacherId: courseForm.value.teacherId,
      moduleId: courseForm.value.moduleId || null
    }

    if (isEdit.value) {
      // 编辑课程
      await adminAPI.updateCourse(courseForm.value.id, data)
      ElMessage.success('课程更新成功')
    } else {
      // 添加课程
      await adminAPI.createCourse(data)
      ElMessage.success('课程添加成功')
    }

    dialogVisible.value = false
    await loadCourses()
  } catch (error) {
    if (error instanceof Error) {
      console.error('提交失败:', error)
    }
  } finally {
    submitLoading.value = false
  }
}

// 删除课程
const handleDelete = (row) => {
  courseToDelete.value = row
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!courseToDelete.value) return

  deleteLoading.value = true
  try {
    await adminAPI.deleteCourse(courseToDelete.value.id)
    ElMessage.success('课程删除成功')
    deleteDialogVisible.value = false
    courseToDelete.value = null
    await loadCourses()
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 批量删除
const batchDelete = () => {
  if (selectedCourses.value.length === 0) {
    ElMessage.warning('请先选择要删除的课程')
    return
  }
  batchDeleteDialogVisible.value = true
}

const confirmBatchDelete = async () => {
  deleteLoading.value = true
  try {
    const deletePromises = selectedCourses.value.map(course =>
      adminAPI.deleteCourse(course.id)
    )
    await Promise.all(deletePromises)
    ElMessage.success(`成功删除 ${selectedCourses.value.length} 门课程`)
    batchDeleteDialogVisible.value = false
    selectedCourses.value = []
    await loadCourses()
  } catch (error) {
    console.error('批量删除失败:', error)
    ElMessage.error('批量删除失败')
  } finally {
    deleteLoading.value = false
  }
}
</script>

<style scoped>
.admin-courses {
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

.course-count {
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
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

.admin-courses :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-courses :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-courses :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-courses :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-courses :deep(.el-button--danger:hover) {
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
