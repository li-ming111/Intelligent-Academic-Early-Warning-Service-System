<template>
  <div class="admin-users" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>用户管理</h1>
      <p>用户信息、角色和权限</p>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="addUserDialogVisible = true">
        <el-icon><Plus /></el-icon> 添加用户
      </el-button>
      <el-select v-model="filterCollege" placeholder="学院筛选" style="width: 200px;" @change="searchUsers">
        <el-option label="全部" value=""></el-option>
        <el-option v-for="college in colleges" :key="college.id" :label="college.name" :value="college.id">
          {{ college.name }}
        </el-option>
      </el-select>
      <el-select v-model="filterRole" placeholder="角色筛选" style="width: 150px;" @change="searchUsers">
        <el-option label="全部" value=""></el-option>
        <el-option label="学生" value="1"></el-option>
        <el-option label="教师" value="2"></el-option>
        <el-option label="辅导员" value="3"></el-option>
        <el-option label="管理员" value="4"></el-option>
      </el-select>
      <el-input v-model="searchText" placeholder="搜索用户名或姓名" style="width: 250px;" @input="searchUsers" clearable />
      <el-button @click="loadUsers" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <span class="user-count">共 {{ total }} 个用户</span>
        </div>
      </template>

      <div v-if="userList.length === 0" style="padding: 20px; text-align: center; color: #999;">
        暂无数据
      </div>
      <el-table v-else :data="userList" stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55"></el-table-column>
        <el-table-column prop="id" label="ID" width="100" sortable />
        <!-- 根据角色显示学号或工号 -->
        <el-table-column label="学号/工号" width="120">
          <template #default="{ row }">
            {{ row.role === '1' || row.role === 1 ? (row.studentId || row.username) : (row.jobNumber || row.username) }}
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)">
              {{ getRoleText(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="collegeName" label="学院" width="200" show-overflow-tooltip />
        <el-table-column prop="majorName" label="专业" width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning">待审批</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger">已锁定</el-tag>
            <el-tag v-else type="info">已禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button v-if="row.status === 0" type="success" size="small" @click="approveUser(row)">审批通过</el-button>
              <el-button type="primary" size="small" @click="editUser(row)">编辑</el-button>
              <el-button :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : row.status === 0 ? '启用' : '解锁' }}</el-button>
              <el-button type="danger" size="small" @click="deleteUser(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="selection-info" v-if="selectedUsers.length > 0">
          已选择 {{ selectedUsers.length }} 项
          <el-button type="danger" size="small" @click="batchDeleteUsers">批量删除</el-button>
        </div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加用户弹窗 -->
    <el-dialog v-model="addUserDialogVisible" title="添加用户" width="500px" :close-on-click-modal="false">
      <el-form ref="addUserFormRef" :model="addUserForm" :rules="formRules" label-width="100px">
        <el-form-item label="角色" prop="role">
          <el-select v-model="addUserForm.role" placeholder="请选择角色">
            <el-option label="学生" value="1"></el-option>
            <el-option label="教师" value="2"></el-option>
            <el-option label="辅导员" value="3"></el-option>
            <el-option label="管理员" value="4"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="addUserForm.role === '1'" label="学号" prop="username">
          <el-input v-model="addUserForm.username" placeholder="请输入学号"></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">学号将作为用户名</small>
        </el-form-item>
        <el-form-item v-else-if="addUserForm.role === '2'" label="工号" prop="username">
          <el-input v-model="addUserForm.username" placeholder="请输入教师工号"></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">工号将作为用户名</small>
        </el-form-item>
        <el-form-item v-else-if="addUserForm.role === '3'" label="工号" prop="username">
          <el-input v-model="addUserForm.username" placeholder="请输入辅导员工号"></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">工号将作为用户名</small>
        </el-form-item>
        <el-form-item v-else-if="addUserForm.role === '4'" label="账号" prop="username">
          <el-input v-model="addUserForm.username" placeholder="请输入管理员账号"></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">账号将作为用户名</small>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="addUserForm.name" placeholder="请输入姓名"></el-input>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="addUserForm.email" placeholder="请输入邮箱（可选）"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addUserForm.password" type="password" placeholder="请输入密码（默认123456）"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addUserDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAddUser">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="500px" :close-on-click-modal="false">
      <el-form ref="editUserFormRef" :model="editingUser" label-width="100px">
        <el-form-item v-if="editingUser.role === '1' || editingUser.role === 1" label="学号">
          <el-input v-model="editingUser.studentId" placeholder="请输入学号"></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">修改学号会自动更新用户名</small>
        </el-form-item>
        <el-form-item v-else-if="editingUser.role === '2' || editingUser.role === 2" label="工号">
          <el-input v-model="editingUser.jobNumber" placeholder="请输入教师工号"></el-input>
        </el-form-item>
        <el-form-item v-else-if="editingUser.role === '3' || editingUser.role === 3" label="工号">
          <el-input v-model="editingUser.jobNumber" placeholder="请输入辅导员工号"></el-input>
        </el-form-item>
        <el-form-item v-else-if="editingUser.role === '4' || editingUser.role === 4" label="账号">
          <el-input v-model="editingUser.jobNumber" placeholder="请输入管理员账号"></el-input>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="editingUser.username" disabled placeholder="自动由学号/工号生成"></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">用户名由学号/工号自动生成，不可手动修改</small>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editingUser.name"></el-input>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editingUser.email"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="editingUser.password" placeholder="留空表示不修改密码" show-password></el-input>
          <small style="color: #999; margin-top: 5px; display: block;">留空表示不修改密码，输入新密码将自动加密</small>
        </el-form-item>
        <el-form-item>
          <el-button type="info" size="small" @click="resetPassword(editingUser.id)">重置为默认密码</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除用户 <strong>{{ userToDelete?.username }}</strong> 吗？</p>
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
        <p>确定要删除选中的 <strong>{{ selectedUsers.length }} 个用户</strong> 吗？</p>
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
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAPI } from '@/api/index'
import { Plus, Refresh, Edit, Delete, WarningFilled } from '@element-plus/icons-vue'

const searchText = ref('')
const filterCollege = ref('')
const filterRole = ref('')
const userList = ref([])
const editDialogVisible = ref(false)
const addUserDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const batchDeleteDialogVisible = ref(false)
const editingUser = ref({})
const userToDelete = ref(null)
const colleges = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedUsers = ref([])
const loading = ref(false)
const deleteLoading = ref(false)

const addUserFormRef = ref(null)
const editUserFormRef = ref(null)

const addUserForm = ref({
  username: '',
  name: '',
  role: '',
  email: '',
  password: '123456'
})

const formRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度为 2-50 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 1, max: 50, message: '姓名长度为 1-50 个字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

onMounted(async () => {
  await loadColleges()
  await loadUsers()
})

const loadColleges = async () => {
  try {
    const response = await adminAPI.getColleges()
    if (Array.isArray(response)) {
      colleges.value = response
    } else {
      colleges.value = []
    }
  } catch (error) {
    console.error('加载学院列表失败:', error)
  }
}

const loadUsers = async () => {
  loading.value = true
  try {
    const collegeId = filterCollege.value && filterCollege.value !== '' ? parseInt(filterCollege.value) : null
    const role = filterRole.value && filterRole.value !== '' ? parseInt(filterRole.value) : null
    const response = await adminAPI.getUsers(currentPage.value, pageSize.value, collegeId, role)
    
    let users = []
    if (response && response.items && Array.isArray(response.items)) {
      users = response.items
      total.value = response.total || users.length
    } else if (Array.isArray(response)) {
      users = response
      total.value = users.length
    } else if (response && response.data && response.data.items) {
      users = response.data.items
      total.value = response.data.total || users.length
    } else {
      users = []
      total.value = 0
    }
    
    users = users.map(user => ({
      ...user,
      status: user.status !== undefined ? user.status : 1
    }))
    // 待审批(status=0)置顶，其余按ID倒序
    users.sort((a, b) => {
      if (a.status !== b.status) return a.status - b.status
      return (b.id || 0) - (a.id || 0)
    })
    userList.value = users
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const getRoleText = (role) => {
  if (role === '1' || role === 1) return '学生'
  if (role === '2' || role === 2) return '教师'
  if (role === '3' || role === 3) return '辅导员'
  if (role === '4' || role === 4) return '管理员'
  return '未知'
}

const getRoleTagType = (role) => {
  if (role === '1' || role === 1) return 'success'
  if (role === '2' || role === 2) return 'info'
  if (role === '3' || role === 3) return 'warning'
  if (role === '4' || role === 4) return 'primary'
  return 'info'
}

const editUser = (row) => {
  editingUser.value = { ...row }
  editDialogVisible.value = true
}

// 监听学号/工号变化，自动更新用户名
watch(
  () => editingUser.value,
  (newVal) => {
    if (!newVal) return
    const role = newVal.role
    if (role === '1' || role === 1) {
      // 学生：学号变化时更新用户名
      if (newVal.studentId && newVal.studentId !== newVal.username) {
        newVal.username = newVal.studentId
      }
    } else if ((role === '2' || role === 2) || (role === '3' || role === 3) || (role === '4' || role === 4)) {
      // 教师、辅导员、管理员：工号/账号变化时更新用户名
      if (newVal.jobNumber && newVal.jobNumber !== newVal.username) {
        newVal.username = newVal.jobNumber
      }
    }
  },
  { deep: true }
)

const submitEdit = async () => {
  try {
    const userId = editingUser.value.id
    if (!userId) {
      ElMessage.error('用户ID不存在')
      return
    }
    const response = await adminAPI.updateUser(userId, editingUser.value)
    ElMessage.success('用户已更新')
    
    const currentUserId = parseInt(localStorage.getItem('userId'))
    if (currentUserId === userId) {
      if (editingUser.value.name) {
        localStorage.setItem('userName', editingUser.value.name)
      }
      if (editingUser.value.email) {
        localStorage.setItem('email', editingUser.value.email)
      }
    }
    
    editDialogVisible.value = false
    await loadUsers()
  } catch (error) {
    console.error('更新用户失败:', error)
    ElMessage.error('更新失败')
  }
}

const submitAddUser = async () => {
  if (!addUserFormRef.value) return
  
  try {
    await addUserFormRef.value.validate()
    const data = {
      username: addUserForm.value.username,
      name: addUserForm.value.name,
      role: parseInt(addUserForm.value.role),
      email: addUserForm.value.email || null,
      password: addUserForm.value.password || '123456'
    }
    await adminAPI.createUser(data)
    ElMessage.success('用户已添加')
    addUserDialogVisible.value = false
    addUserForm.value = {
      username: '',
      name: '',
      role: '',
      email: '',
      password: '123456'
    }
    await loadUsers()
  } catch (error) {
    // 显示后端原因（重名时是"用户名已存在"）。写死"添加失败"会把
    // "用户名重复"和"网络断了"显示成同一句话，用户不知道该改哪里。
    console.error('添加用户失败:', error)
    ElMessage.error(error?.message || '添加失败')
  }
}

const resetPassword = async (userId) => {
  try {
    if (!userId) {
      ElMessage.error('用户ID不存在')
      return
    }
    await adminAPI.resetPassword(userId)
    ElMessage.success('密码已重置为默认密码: 123456')
    editingUser.value.password = '123456'
  } catch (error) {
    console.error('重置密码失败:', error)
    ElMessage.error('重置密码失败')
  }
}

const approveUser = async (row) => {
  try {
    await adminAPI.approveUser(row.id)
    row.status = 1
    ElMessage.success('审批通过，用户已激活')
  } catch (error) {
    console.error('审批失败:', error)
    ElMessage.error('审批失败')
  }
}

const toggleStatus = async (row) => {
  const userId = row.id
  if (!userId) {
    ElMessage.error('用户ID不存在')
    return
  }
  // W26：加二次确认。停用账号是**影响真实登录**的操作，此前点一下就执行、没有任何提示。
  const label = row.name || row.username || userId
  const action = row.status === 1 ? '禁用' : row.status === 0 ? '启用' : '解锁'
  try {
    await ElMessageBox.confirm(
      row.status === 1
        ? `禁用后「${label}」将无法登录，确认禁用吗？`
        : `确认${action}「${label}」？`,
      `确认${action}账号`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await adminAPI.toggleUserStatus(userId)
    // ⚠️ 原来这里是就地改 `row.status`（0→1 启用 / 1→0 禁用 / 2→1 解锁）—— 界面自己猜结果。
    //    后端是**严格取反**（status==1→0，否则→1），到底落在哪个值以库为准；
    //    而且当前页可能带筛选/分页，就地改会让视图与库不一致 ⇒ 重新拉一遍。
    await loadUsers()
    const fresh = (userList.value || []).find(u => u.id === userId)
    if (fresh) {
      ElMessage.success(fresh.status === 1 ? '用户已启用' : '用户已禁用')
    } else {
      ElMessage.success('状态已更新')
    }
  } catch (error) {
    console.error('状态更新失败:', error)
    ElMessage.error('操作失败：' + (error?.message || '网络错误'))
  }
}

const deleteUser = (row) => {
  userToDelete.value = row
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!userToDelete.value) return
  
  deleteLoading.value = true
  try {
    await adminAPI.deleteUser(userToDelete.value.id)
    ElMessage.success('用户已删除')
    deleteDialogVisible.value = false
    userToDelete.value = null
    await loadUsers()
  } catch (error) {
    console.error('删除用户失败:', error)
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 「查看密码」已删除（2026-09-30）。原来这里有个 viewPassword：
//   · 它调 `GET /admin/users/{id}/password` —— 该端点**根本不存在**，实测 HTTP 404
//     （admin-service 里只有 POST /users/{id}/reset-password）；
//   · 解包写法也是错的（`response.data?.data || response.data`，拦截器已解包 → 恒 undefined），
//     真要是通了，管理员看到的会是「用户密码: undefined」还配一个绿色成功提示；
//   · 且**没有任何按钮调用它**（行内只有 审批/编辑/禁用/删除，重置在编辑弹窗里），是死代码。
// 没有改成"真去读密码"：库里存的是 BCrypt 哈希，读出来也没有意义；而管理员的正当需求是
// **重置**（`POST /users/{id}/reset-password`，接口是好的，入口在编辑弹窗的「重置为默认密码」）。

const searchUsers = async () => {
  currentPage.value = 1
  await loadUsers()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadUsers()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  loadUsers()
}

const handleSelectionChange = (val) => {
  selectedUsers.value = val
}

const batchDeleteUsers = () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请先选择要删除的用户')
    return
  }
  batchDeleteDialogVisible.value = true
}

const confirmBatchDelete = async () => {
  deleteLoading.value = true
  try {
    const userIds = selectedUsers.value.map(user => user.id)
    for (const userId of userIds) {
      await adminAPI.deleteUser(userId)
    }
    ElMessage.success(`成功删除 ${selectedUsers.value.length} 个用户`)
    batchDeleteDialogVisible.value = false
    selectedUsers.value = []
    await loadUsers()
  } catch (error) {
    console.error('批量删除用户失败:', error)
    ElMessage.error('批量删除失败')
  } finally {
    deleteLoading.value = false
  }
}
</script>

<style scoped>
.admin-users {
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

.user-count {
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

.admin-users :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-users :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-users :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-users :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-users :deep(.el-button--danger:hover) {
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