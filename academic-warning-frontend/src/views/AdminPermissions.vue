<template>
  <div class="admin-permissions" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>权限管理</h1>
      <p>灵活的权限角色组合配置</p>
    </div>

    <!-- Tab页签 -->
    <el-tabs type="card">
      <!-- 角色管理 -->
      <el-tab-pane label="角色管理">
        <div class="tab-content">
          <el-card style="margin-bottom: 20px;">
            <template #header>
              <div class="card-header">
                <span>系统角色</span>
                <span class="role-count">共 {{ roles.length }} 个角色</span>
              </div>
            </template>

            <div v-if="roles.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无角色数据
            </div>
            <el-table v-else :data="roles" stripe>
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="角色ID" width="100" sortable></el-table-column>
              <el-table-column prop="name" label="角色名称" width="120"></el-table-column>
              <el-table-column prop="description" label="角色描述" min-width="200" show-overflow-tooltip></el-table-column>
              <el-table-column label="权限数" width="100">
                <template #default="{ row }">
                  <!-- 必须兜底：/admin/permissions/roles 返回的角色对象是
                       {id,name,code,description,status,createdAt,updatedAt}，**没有 permissions 字段**，
                       直接 .length 会抛 TypeError 把整页渲染打断（下面用户表那处本来就有 v-if 守卫，这里漏了）。 -->
                  <el-tag type="success">{{ (row.permissions || []).length }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="权限列表" min-width="300">
                <template #default="{ row }">
                  <div class="permission-tags">
                    <el-tag v-for="perm in row.permissions" :key="perm" size="small" style="margin: 2px;">
                      {{ perm }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" @click="editRole(row)">
                    <el-icon><Edit /></el-icon> 编辑
                  </el-button>
                  <el-button type="danger" size="small" @click="deleteRole(row.id)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer">
              <div class="selection-info" v-if="selectedRoles.length > 0">
                已选择 {{ selectedRoles.length }} 项
                <el-button type="danger" size="small" @click="batchDeleteRoles">批量删除</el-button>
              </div>
            </div>
          </el-card>

          <el-button type="primary" @click="showCreateRoleDialog = true">
            <el-icon><Plus /></el-icon> 创建新角色
          </el-button>
        </div>
      </el-tab-pane>

      <!-- 用户权限管理 -->
      <el-tab-pane label="用户权限">
        <div class="tab-content">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>用户权限分配</span>
                <span class="user-count">共 {{ totalUsers }} 个用户</span>
              </div>
            </template>

            <div style="margin-bottom: 20px;">
              <el-input
                v-model="searchUsername"
                placeholder="搜索用户名..."
                style="width: 250px; margin-right: 10px;"
                @input="searchUsers"
                clearable
              />
              <el-button @click="loadUsers">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </div>

            <div v-if="filteredUsers.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无用户数据
            </div>
            <el-table v-else :data="filteredUsers" stripe v-loading="loading">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="用户ID" width="100" sortable></el-table-column>
              <el-table-column prop="username" label="用户名" width="150"></el-table-column>
              <el-table-column prop="name" label="姓名" width="120"></el-table-column>
              <el-table-column label="当前角色" width="150">
                <template #default="{ row }">
                  <el-select v-model="row.role" size="small" @change="updateUserRole(row)">
                    <el-option :value="1" label="学生"></el-option>
                    <el-option :value="2" label="教师"></el-option>
                    <el-option :value="3" label="辅导员"></el-option>
                    <el-option :value="4" label="管理员"></el-option>
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="权限数" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.permissions" type="info">{{ row.permissions.length }}</el-tag>
                  <span v-else style="color: #999;">-</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.enabled === 1 ? 'success' : 'danger'">
                    {{ row.enabled === 1 ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button type="primary" size="small" @click="viewUserPermissions(row)">查看权限</el-button>
                    <el-button :type="row.enabled === 1 ? 'danger' : 'success'" size="small" @click="toggleUserStatus(row)">{{ row.enabled === 1 ? '禁用' : '启用' }}</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="table-footer">
              <div class="selection-info" v-if="selectedUsers.length > 0">
                已选择 {{ selectedUsers.length }} 项
              </div>
              <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50]"
                :total="totalUsers"
                layout="total, sizes, prev, pager, next"
                @size-change="handlePageSizeChange"
                @current-change="handlePageChange"
              />
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 权限配置 -->
      <el-tab-pane label="权限配置">
        <div class="tab-content">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>系统权限配置</span>
                <span class="permission-count">共 {{ permissions.length }} 项权限</span>
              </div>
            </template>

            <div v-if="permissions.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无权限数据
            </div>
            <el-table v-else :data="permissions" stripe>
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="权限ID" width="100" sortable></el-table-column>
              <el-table-column prop="code" label="权限码" width="150"></el-table-column>
              <el-table-column prop="name" label="权限名称" min-width="150"></el-table-column>
              <el-table-column label="适用角色" min-width="250">
                <template #default="{ row }">
                  <div>
                    <el-tag
                      v-for="role in getApplicableRoles(row.code)"
                      :key="role"
                      size="small"
                      style="margin: 2px;"
                    >
                      {{ role }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" @click="editPermission(row)">
                    <el-icon><Edit /></el-icon> 编辑
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer">
              <div class="selection-info" v-if="selectedPermissions.length > 0">
                已选择 {{ selectedPermissions.length }} 项
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 用户权限详情对话框 -->
    <el-dialog v-model="permissionDetailVisible" title="用户权限详情" width="600px">
      <div v-if="selectedUser" class="permission-detail">
        <p><strong>用户名：</strong>{{ selectedUser.username }}</p>
        <p><strong>用户ID：</strong>{{ selectedUser.userId }}</p>
        <p><strong>角色：</strong>{{ getRoleName(selectedUser.role) }}</p>
        <p><strong>拥有权限：</strong></p>
        <div class="permission-tags">
          <el-tag
            v-for="perm in selectedUser.permissions"
            :key="perm"
            type="success"
            style="margin: 5px;"
          >
            {{ perm }}
          </el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- 创建角色对话框 -->
    <el-dialog v-model="showCreateRoleDialog" title="创建新角色" width="600px">
      <el-form :model="newRole" label-width="100px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="newRole.name" placeholder="输入角色名称"></el-input>
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input v-model="newRole.description" placeholder="输入角色描述"></el-input>
        </el-form-item>
        <el-form-item label="分配权限">
          <el-checkbox-group v-model="newRole.permissions">
            <el-checkbox v-for="perm in availablePermissions" :key="perm.code" :label="perm.code">
              {{ perm.name }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateRoleDialog = false">取消</el-button>
        <el-button type="primary" @click="createRole">创建</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#F56C6C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除选中的 <strong>{{ deleteCount }} 项</strong> 吗？</p>
        <p class="warning-text">此操作不可逆，删除后将无法恢复！</p>
      </div>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete" :loading="deleteLoading">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { adminAPI } from '@/api/index'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Edit, Delete, WarningFilled } from '@element-plus/icons-vue'

// 数据
const roles = ref([])
const users = ref([])
const permissions = ref([])
const searchUsername = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalUsers = ref(0)
const selectedRoles = ref([])
const selectedUsers = ref([])
const selectedPermissions = ref([])
const deleteDialogVisible = ref(false)
const deleteLoading = ref(false)
const deleteCount = ref(0)

const permissionDetailVisible = ref(false)
const selectedUser = ref(null)

const showCreateRoleDialog = ref(false)
const newRole = ref({
  name: '',
  description: '',
  permissions: []
})

const availablePermissions = ref([])

// 计算属性
const filteredUsers = computed(() => {
  let filtered = users.value
  
  if (searchUsername.value) {
    const searchLower = searchUsername.value.toLowerCase()
    filtered = filtered.filter(u =>
      u.username.toLowerCase().includes(searchLower) ||
      (u.name && u.name.toLowerCase().includes(searchLower))
    )
  }

  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filtered.slice(start, end)
})

onMounted(async () => {
  await loadRoles()
  await loadPermissions()
  await loadUsers()
})

// 加载角色
const loadRoles = async () => {
  try {
    const response = await adminAPI.getRoles()
    if (response) {
      roles.value = response
    }
  } catch (error) {
    console.error('加载角色失败:', error)
  }
}

// 加载权限
const loadPermissions = async () => {
  try {
    const response = await adminAPI.getPermissions()
    if (response) {
      permissions.value = response
      availablePermissions.value = response
    }
  } catch (error) {
    console.error('加载权限失败:', error)
  }
}

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const response = await adminAPI.getUsers(currentPage.value, pageSize.value)
    if (Array.isArray(response)) {
      users.value = response.map(u => ({
        ...u,
        role: u.role || 1,
        enabled: u.status !== 1 ? 1 : 0
      }))
      totalUsers.value = response.length
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索用户
const searchUsers = () => {
  currentPage.value = 1
}

// 更新用户角色
const updateUserRole = async (user) => {
  try {
    await adminAPI.assignRole({
      user_id: user.id,
      role_id: user.role
    })
    ElMessage.success('角色已更新')
  } catch (error) {
    ElMessage.error('更新角色失败')
  }
}

// 切换用户状态
const toggleUserStatus = async (user) => {
  try {
    await adminAPI.toggleUserStatus(user.id)
    user.enabled = user.enabled === 1 ? 0 : 1
    ElMessage.success(user.enabled === 1 ? '用户已启用' : '用户已禁用')
  } catch (error) {
    ElMessage.error('更新用户状态失败')
  }
}

// 查看用户权限
const viewUserPermissions = async (user) => {
  try {
    const response = await adminAPI.getUserPermissions(user.id)
    if (response) {
      selectedUser.value = response
      permissionDetailVisible.value = true
    }
  } catch (error) {
    ElMessage.error('加载用户权限失败')
  }
}

// 获取角色名称
const getRoleName = (roleId) => {
  const roleMap = {
    1: '学生',
    2: '教师',
    3: '辅导员',
    4: '管理员'
  }
  return roleMap[roleId] || '未知'
}

// 获取权限适用的角色
const getApplicableRoles = (permCode) => {
  const mapping = {
    'view_scores': ['学生', '教师'],
    'view_warnings': ['学生', '教师', '辅导员'],
    'submit_appeals': ['学生'],
    'submit_feedback': ['学生', '教师'],
    'manage_students': ['辅导员'],
    'manage_warnings': ['辅导员'],
    'view_analytics': ['教师', '辅导员'],
    'manage_all': ['管理员']
  }
  return mapping[permCode] || []
}

// 编辑角色
const editRole = (role) => {
  ElMessage.info('编辑功能开发中...')
}

// 删除角色
const deleteRole = (roleId) => {
  deleteCount.value = 1
  deleteDialogVisible.value = true
}

// 批量删除角色
const batchDeleteRoles = () => {
  if (selectedRoles.value.length === 0) {
    ElMessage.warning('请先选择要删除的角色')
    return
  }
  deleteCount.value = selectedRoles.value.length
  deleteDialogVisible.value = true
}

// 编辑权限
const editPermission = (permission) => {
  ElMessage.info('编辑功能开发中...')
}

// 创建角色
const createRole = async () => {
  if (!newRole.value.name) {
    ElMessage.error('请输入角色名称')
    return
  }
  if (newRole.value.permissions.length === 0) {
    ElMessage.error('请至少选择一个权限')
    return
  }

  ElMessage.success('角色已创建')
  showCreateRoleDialog.value = false
  newRole.value = { name: '', description: '', permissions: [] }
  await loadRoles()
}

// 确认删除
const confirmDelete = async () => {
  deleteLoading.value = true
  try {
    ElMessage.info('删除功能开发中...')
    deleteDialogVisible.value = false
  } catch (error) {
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 分页处理
const handlePageChange = () => {
  loadUsers()
}

const handlePageSizeChange = () => {
  currentPage.value = 1
  loadUsers()
}
</script>

<style scoped>
.admin-permissions {
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

.tab-content {
  padding: 20px 0;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-count, .user-count, .permission-count {
  font-size: 14px;
  color: #909399;
  font-weight: normal;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.permission-detail {
  line-height: 1.8;
}

.permission-detail p {
  margin: 10px 0;
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

.admin-permissions :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-permissions :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-permissions :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-permissions :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-permissions :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}

.admin-permissions :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 10px;
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