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
            <!-- 2026-10-01 第二十四轮：补 @selection-change。
                 此前三张表都摆了 selection 列却**没有一个把选中项写回 ref**
                 ⇒ selectedRoles 恒为 []，下面「已选择 N 项 / 批量删除」那块**永远不渲染**，
                 batchDeleteRoles 里那句"请先选择要删除的角色"永远不可能被触发。 -->
            <el-table v-else :data="roles" stripe @selection-change="selectedRoles = $event">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="角色ID" width="100" sortable></el-table-column>
              <el-table-column prop="name" label="角色名称" width="120"></el-table-column>
              <el-table-column prop="description" label="角色描述" min-width="200" show-overflow-tooltip></el-table-column>
              <el-table-column label="权限数" width="100">
                <template #default="{ row }">
                  <!-- `|| []` 保留兜底：后端 2026-10-01 起确实会回 permissions（角色绑定权限码），
                       但接口拿不到或报了错时 row.permissions 会是 undefined，直接 .length 会抛
                       TypeError 把整页渲染打断。兜底留着，注释改成与新契约一致。 -->

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

          <el-button type="primary" @click="openCreateRoleDialog">
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
                  <!-- 5 = 学院管理员（活库 users.role 真有 5 的账号）。原来只有 1-4，
                       这些账号的下拉会显示不出当前值。 -->
                  <el-select v-model="row.role" size="small" @change="updateUserRole(row)">
                    <el-option :value="1" label="学生"></el-option>
                    <el-option :value="2" label="教师"></el-option>
                    <el-option :value="3" label="辅导员"></el-option>
                    <el-option :value="4" label="管理员"></el-option>
                    <el-option :value="5" label="学院管理员"></el-option>
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
                  <!-- 2026-10-01 第二十四轮：原来调 getApplicableRoles(row.code)，
                       那张映射表的键是 view_scores / manage_students / manage_all…
                       而活库 permissions 的 9 个码全是 PERM_* ⇒ 两边一个都对不上，
                       这一列 9 行全空。现在读后端从 role_permissions 真查出来的 roleCodes。 -->
                  <div>
                    <el-tag
                      v-for="rc in (row.roleCodes || [])"
                      :key="rc"
                      size="small"
                      style="margin: 2px;"
                    >
                      {{ roleCodeName(rc) }}
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

    <!-- 创建 / 编辑角色对话框。编辑复用同一个弹窗，靠 editingRoleId 区分。 -->
    <el-dialog
      v-model="showCreateRoleDialog"
      :title="editingRoleId ? '编辑角色' : '创建新角色'"
      width="600px"
    >
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
        <el-button @click="closeRoleDialog">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="createRole">
          {{ editingRoleId ? '保存' : '创建' }}
        </el-button>
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

    <!-- 编辑单条权限（2026-10-02 W1.16）：此前这里是个 toast 桩 -->
    <el-dialog v-model="permissionDialogVisible" title="编辑权限" width="500px">
      <el-form label-width="90px">
        <el-form-item label="权限码">
          <el-input :model-value="editingPermission?.code" disabled />
          <div class="form-tip">
            权限码是前端角色绑定与后端按字面引用的标识符，不可修改。
          </div>
        </el-form-item>
        <el-form-item label="权限名称">
          <el-input v-model="permissionForm.name" maxlength="100" show-word-limit placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限描述">
          <el-input
            v-model="permissionForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="留空即清除描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePermission" :loading="permissionSaving">保存</el-button>
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

const permissionDetailVisible = ref(false)
const selectedUser = ref(null)

const showCreateRoleDialog = ref(false)
const newRole = ref({
  name: '',
  description: '',
  permissions: []
})
/** 非空 = 弹窗处于编辑模式（值是角色 id）；null = 新建模式。 */
const editingRoleId = ref(null)
const roleSaving = ref(false)
/** 待删除的角色 id 列表（单个删除 = 长度 1，批量删除 = 勾选的那几个）。 */
const pendingDeleteIds = ref([])
/** 删除弹窗里显示的"选中 N 项"。**算出来的**，不是另存一份（存两份必然漂移）。 */
const deleteCount = computed(() => pendingDeleteIds.value.length)

const availablePermissions = ref([])

// —— 单条权限的编辑弹窗（2026-10-02 W1.16）——
// 此前 `editPermission` 只有一句 `ElMessage.info('编辑功能开发中...')`：
// 按钮点了有反馈、弹窗不开、库里不动。现在真打 `PUT /admin/permissions/{id}`。
const permissionDialogVisible = ref(false)
const permissionSaving = ref(false)
/** 正在编辑的那条权限（null = 弹窗没开）。 */
const editingPermission = ref(null)
const permissionForm = ref({ name: '', description: '' })

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
    // ⚠️ `/admin/users/page` 回的是 **{total, items:[...]}**（已实测），不是裸数组。
    // 原来只判 `Array.isArray` → users 恒 []、totalUsers 恒 0，权限页的用户列表永远空。
    const list = Array.isArray(response) ? response
      : Array.isArray(response?.items) ? response.items
      : Array.isArray(response?.records) ? response.records
      : Array.isArray(response?.list) ? response.list
      : []
    users.value = list.map(u => ({
      ...u,
      role: u.role || 1,
      enabled: u.status !== 1 ? 1 : 0
    }))
    totalUsers.value = Number(response?.total) || list.length
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
    // ⚠️ 契约是 {userId, role}。这里原来发的是 {user_id, role_id}，后端
    //    `data.get("userId").toString()` 直接 NPE，被 catch 吞成 body code 500
    //    ⇒ 这一格下拉从来就没保存成功过（用户看到"更新角色失败"）。
    await adminAPI.assignRole({
      userId: user.id,
      role: user.role
    })
    ElMessage.success('角色已更新')
  } catch (error) {
    ElMessage.error(error?.message || '更新角色失败')
    // 回滚本地显示：下拉已经改成新值了，但后端没收下，界面不能比库里"新"。
    await loadUsers()
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

// 获取角色名称（users.role 是数值：1学生 2教师 3辅导员 4管理员 5学院管理员）
const getRoleName = (roleId) => {
  const roleMap = {
    1: '学生',
    2: '教师',
    3: '辅导员',
    4: '管理员',
    5: '学院管理员'
  }
  return roleMap[roleId] || '未知'
}

/** 角色码 → 中文名。角色码取自 /admin/permissions/roles 的 code（ROLE_*），
    名字以**后端 roles.name 为准**，取不到才退回这张兜底表。 */
const roleCodeName = (code) => {
  const hit = roles.value.find(r => r.code === code)
  if (hit && hit.name) return hit.name
  const fallback = {
    ROLE_STUDENT: '学生',
    ROLE_TEACHER: '教师',
    ROLE_COUNSELOR: '辅导员',
    ROLE_ADMIN: '管理员'
  }
  return fallback[code] || code
}

// 打开「创建新角色」弹窗（清空上一次编辑留下的内容，回到新建模式）
const openCreateRoleDialog = () => {
  editingRoleId.value = null
  newRole.value = { name: '', description: '', permissions: [] }
  showCreateRoleDialog.value = true
}

const closeRoleDialog = () => {
  showCreateRoleDialog.value = false
  editingRoleId.value = null
  newRole.value = { name: '', description: '', permissions: [] }
}

// 编辑角色：复用同一个弹窗，预填当前值后进入编辑模式
const editRole = (role) => {
  editingRoleId.value = role.id
  newRole.value = {
    name: role.name || '',
    description: role.description || '',
    // row.permissions 是后端新补的权限码数组（没有时兜底空数组，否则 checkbox-group 会拿到 undefined）
    permissions: Array.isArray(role.permissions) ? [...role.permissions] : []
  }
  showCreateRoleDialog.value = true
}

// 删除角色（单个）
const deleteRole = (roleId) => {
  pendingDeleteIds.value = [roleId]
  deleteDialogVisible.value = true
}

// 批量删除角色
const batchDeleteRoles = () => {
  if (selectedRoles.value.length === 0) {
    ElMessage.warning('请先选择要删除的角色')
    return
  }
  pendingDeleteIds.value = selectedRoles.value.map(r => r.id)
  deleteDialogVisible.value = true
}

// 编辑权限（2026-10-02 W1.16）
//
// 此前是 `ElMessage.info('编辑功能开发中...')` —— 同文件里角色的新建/编辑/删除
// 都在第二十四轮做掉了，这个被落下。现在打开弹窗、真打后端。
//
// 只允许改 名称 / 描述：`code` 是被前端角色弹窗（`v-model="newRole.permissions"`
// 存的就是权限码数组）与 `resolvePermissions` 按字面引用的标识符，改了会留下
// "绑定还在、码却变了"的口子，所以弹窗里显示为禁用。
const editPermission = (permission) => {
  editingPermission.value = permission
  permissionForm.value = {
    name: permission.name || '',
    description: permission.description || ''
  }
  permissionDialogVisible.value = true
}

const savePermission = async () => {
  const target = editingPermission.value
  if (!target || permissionSaving.value) return
  const name = String(permissionForm.value.name || '').trim()
  if (!name) {
    ElMessage.error('权限名称不能为空')
    return
  }
  permissionSaving.value = true
  try {
    // ⚠️ 描述要**显式传空串**而不是省略：省略等于"不改这一列"（后端只改传了的字段），
    //    而这里用户的意图是"我把描述清掉了"。后端会把空串归成 NULL。
    await adminAPI.updatePermission(target.id, {
      name,
      description: String(permissionForm.value.description || '').trim()
    })
    ElMessage.success('权限已更新')
    permissionDialogVisible.value = false
    editingPermission.value = null
    await loadPermissions()
  } catch (error) {
    // 后端把"名称空/带 code/权限不存在"翻成 400/404，message 就是原因，直接透出来
    ElMessage.error(error?.message || '更新权限失败')
  } finally {
    permissionSaving.value = false
  }
}

// 创建 / 保存角色
//
// 2026-10-01 第二十四轮：此前这里只有一句 `ElMessage.success('角色已创建')` 就完事 ——
// **没有任何 API 调用**，弹窗关了、列表刷新了，库里一行没多（假成功）。
// 现在真打后端：新建 POST /admin/permissions/roles，编辑 PUT /admin/permissions/roles/{id}。
const createRole = async () => {
  if (!newRole.value.name) {
    ElMessage.error('请输入角色名称')
    return
  }
  if (newRole.value.permissions.length === 0) {
    ElMessage.error('请至少选择一个权限')
    return
  }

  roleSaving.value = true
  try {
    const payload = {
      name: newRole.value.name,
      description: newRole.value.description,
      permissions: newRole.value.permissions
    }
    if (editingRoleId.value) {
      await adminAPI.updateRole(editingRoleId.value, payload)
      ElMessage.success('角色已更新')
    } else {
      await adminAPI.createRole(payload)
      ElMessage.success('角色已创建')
    }
    closeRoleDialog()
    await loadRoles()
  } catch (error) {
    // 后端把"名称重复/权限码不存在"翻成 400，message 里就是原因，直接透出来
    ElMessage.error(error?.message || (editingRoleId.value ? '更新角色失败' : '创建角色失败'))
  } finally {
    roleSaving.value = false
  }
}

// 确认删除
//
// 原来这里是 `ElMessage.info('删除功能开发中...')` —— 一个明确的未实现桩。
// 现在逐个调后端；**部分失败不假装全成功**：成功的算成功，失败的把后端原话报出来。
const confirmDelete = async () => {
  if (pendingDeleteIds.value.length === 0) {
    deleteDialogVisible.value = false
    return
  }
  deleteLoading.value = true
  const ids = [...pendingDeleteIds.value]
  const failed = []
  try {
    for (const id of ids) {
      try {
        await adminAPI.deleteRole(id)
      } catch (error) {
        const name = roles.value.find(r => r.id === id)?.name || `#${id}`
        failed.push(`${name}：${error?.message || '删除失败'}`)
      }
    }
    if (failed.length === 0) {
      ElMessage.success(`已删除 ${ids.length} 个角色`)
    } else if (failed.length === ids.length) {
      ElMessage.error(failed.join('；'))
    } else {
      ElMessage.warning(`删除 ${ids.length - failed.length} 个，失败 ${failed.length} 个 —— ${failed.join('；')}`)
    }
    deleteDialogVisible.value = false
    selectedRoles.value = []
    await loadRoles()
  } finally {
    deleteLoading.value = false
    pendingDeleteIds.value = []
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

/* 编辑权限弹窗里的说明行（W1.16） */
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
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