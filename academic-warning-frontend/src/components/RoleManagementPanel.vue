<template>
  <!-- 角色管理 / 权限配置各自是 AdminUsers 的一个顶层标签页，这里只渲染当前那一段；
       弹窗两边都带着，哪个标签页在用就哪个实例弹。 -->
  <div class="role-management-panel">
    <div v-if="section === 'roles'" class="tab-content">
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
        <el-table v-else :data="roles" stripe @selection-change="selectedRoles = $event">
          <el-table-column type="selection" width="55" />
          <el-table-column prop="id" label="角色ID" width="100" sortable></el-table-column>
          <el-table-column prop="name" label="角色名称" width="120"></el-table-column>
          <el-table-column prop="description" label="角色描述" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column label="权限数" width="100">
            <template #default="{ row }">
              <el-tag type="success">{{ (row.permissions || []).length }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权限列表" min-width="300">
            <template #default="{ row }">
              <div class="permission-tags">
                <el-tag
                  v-for="perm in (row.permissions || [])"
                  :key="perm"
                  size="small"
                  style="margin: 2px;"
                  :title="perm"
                >
                  {{ permissionName(perm) }}
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

    <div v-if="section === 'permissions'" class="tab-content">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>权限目录</span>
            <span class="permission-count">
              共 {{ permissions.length }} 项权限 · {{ roles.length }} 个角色
            </span>
          </div>
        </template>

        <div class="permission-toolbar">
          <el-input
            v-model="permissionKeyword"
            placeholder="搜索权限名称、描述或权限码"
            clearable
            style="width: 300px;"
          />
          <span class="permission-hint">
            权限项按功能模块归组；内置角色的授权由网关按接口路径固定，这里只展示现状，自定义角色的权限在「角色管理」中勾选。
          </span>
        </div>

        <div v-if="permissionGroups.length === 0" class="empty-state">
          {{ permissions.length === 0 ? '暂无权限数据' : '没有匹配的权限项' }}
        </div>

        <section v-for="group in permissionGroups" :key="group.key" class="permission-group">
          <div class="group-header">
            <el-icon class="group-icon"><component :is="group.icon" /></el-icon>
            <span class="group-title">{{ group.label }}</span>
            <el-tag size="small" type="info" effect="plain">{{ group.permissions.length }}</el-tag>
          </div>

          <div class="group-panel">
            <div class="group-columns">
              <span>权限项</span>
              <span>持有该权限的角色</span>
              <span></span>
            </div>
            <div v-for="perm in group.permissions" :key="perm.id" class="permission-row">
              <div class="perm-main">
                <span class="perm-name" :title="perm.code">{{ perm.name }}</span>
                <span v-if="showDescription(perm)" class="perm-desc">{{ perm.description }}</span>
              </div>
              <div class="perm-roles">
                <template v-if="(perm.roleCodes || []).length">
                  <el-tag
                    v-for="rc in perm.roleCodes"
                    :key="rc"
                    size="small"
                    effect="plain"
                    :type="isCustomRoleCode(rc) ? 'primary' : 'info'"
                  >
                    {{ roleCodeName(rc) }}
                  </el-tag>
                </template>
                <span v-else class="empty-cell">暂无角色</span>
              </div>
              <div class="perm-actions">
                <el-button link type="primary" @click="editPermission(perm)">
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
              </div>
            </div>
          </div>
        </section>
      </el-card>
    </div>

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
            <el-checkbox v-for="perm in permissions" :key="perm.code" :label="perm.code">
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

    <!-- 编辑单条权限（W1.16） -->
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
import { Plus, Edit, Delete, WarningFilled, User, Reading, Warning, HelpFilled, Setting } from '@element-plus/icons-vue'

defineProps({
  /** 'roles' 渲染角色管理段，'permissions' 渲染权限配置段。 */
  section: { type: String, default: 'roles' }
})

const emit = defineEmits(['changed'])

const roles = ref([])
const permissions = ref([])

const selectedRoles = ref([])

const showCreateRoleDialog = ref(false)
const newRole = ref({ name: '', description: '', permissions: [] })
/** 非空 = 弹窗处于编辑模式（值是角色 id）；null = 新建模式。 */
const editingRoleId = ref(null)
const roleSaving = ref(false)

const deleteDialogVisible = ref(false)
const deleteLoading = ref(false)
const pendingDeleteIds = ref([])
const deleteCount = computed(() => pendingDeleteIds.value.length)

const permissionDialogVisible = ref(false)
const permissionSaving = ref(false)
const editingPermission = ref(null)
const permissionForm = ref({ name: '', description: '' })

onMounted(async () => {
  await Promise.all([loadRoles(), loadPermissions()])
})

const loadRoles = async () => {
  try {
    const response = await adminAPI.getRoles()
    if (Array.isArray(response)) {
      roles.value = response
    }
  } catch (error) {
    console.error('加载角色失败:', error)
  }
}

const loadPermissions = async () => {
  try {
    const response = await adminAPI.getPermissions()
    if (Array.isArray(response)) {
      permissions.value = response
    }
  } catch (error) {
    console.error('加载权限失败:', error)
  }
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

/** 权限码 → 中文名；查不到（权限已被删）就原样显示码，别让标签变空。 */
const permissionName = (code) => {
  const hit = permissions.value.find(p => p.code === code)
  return hit?.name || code
}

/** 权限点按功能模块归组：PERM_{模块}_{动作} 的前缀就是模块。 */
const PERMISSION_MODULES = [
  { key: 'STUDENT', label: '学生管理', icon: User },
  { key: 'COURSE', label: '课程管理', icon: Reading },
  { key: 'WARNING', label: '预警管理', icon: Warning },
  { key: 'ASSISTANCE', label: '帮扶管理', icon: HelpFilled },
  { key: 'SYSTEM', label: '系统管理', icon: Setting }
]

const permissionKeyword = ref('')

const moduleKeyOf = (code) => {
  const parts = String(code || '').split('_')
  return parts[0] === 'PERM' && parts[1] ? parts[1] : ''
}

/** 描述与名称常常一字不差（种子数据如此），重复的就不占一行。 */
const showDescription = (perm) => {
  const desc = (perm.description || '').trim()
  return !!desc && desc !== (perm.name || '').trim()
}

const permissionGroups = computed(() => {
  const kw = permissionKeyword.value.trim().toLowerCase()
  const matches = (p) => !kw || [p.name, p.description, p.code]
    .some(v => (v || '').toLowerCase().includes(kw))
  const groups = PERMISSION_MODULES.map(m => ({
    ...m,
    permissions: permissions.value.filter(p => moduleKeyOf(p.code) === m.key && matches(p))
  }))
  const known = new Set(PERMISSION_MODULES.map(m => m.key))
  const rest = permissions.value.filter(p => !known.has(moduleKeyOf(p.code)) && matches(p))
  if (rest.length > 0) {
    groups.push({ key: 'OTHER', label: '其他', icon: Setting, permissions: rest })
  }
  return groups.filter(g => g.permissions.length > 0)
})

const isCustomRoleCode = (code) => {
  const role = roles.value.find(r => r.code === code)
  return !!role && role.id > 5
}

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

const editRole = (role) => {
  editingRoleId.value = role.id
  newRole.value = {
    name: role.name || '',
    description: role.description || '',
    permissions: Array.isArray(role.permissions) ? [...role.permissions] : []
  }
  showCreateRoleDialog.value = true
}

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
    emit('changed')
  } catch (error) {
    ElMessage.error(error?.message || (editingRoleId.value ? '更新角色失败' : '创建角色失败'))
  } finally {
    roleSaving.value = false
  }
}

const deleteRole = (roleId) => {
  pendingDeleteIds.value = [roleId]
  deleteDialogVisible.value = true
}

const batchDeleteRoles = () => {
  if (selectedRoles.value.length === 0) {
    ElMessage.warning('请先选择要删除的角色')
    return
  }
  pendingDeleteIds.value = selectedRoles.value.map(r => r.id)
  deleteDialogVisible.value = true
}

// 逐个调后端；部分失败不假装全成功。
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
    emit('changed')
  } finally {
    deleteLoading.value = false
    pendingDeleteIds.value = []
  }
}

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
    // 描述显式传空串：省略等于"不改这一列"，而这里用户的意图是清空描述。
    await adminAPI.updatePermission(target.id, {
      name,
      description: String(permissionForm.value.description || '').trim()
    })
    ElMessage.success('权限已更新')
    permissionDialogVisible.value = false
    editingPermission.value = null
    await loadPermissions()
  } catch (error) {
    ElMessage.error(error?.message || '更新权限失败')
  } finally {
    permissionSaving.value = false
  }
}

// 两个标签页各挂一个实例，切回来时重新拉一次，避免看到另一侧改过的旧数据
defineExpose({
  reload: async () => { await Promise.all([loadRoles(), loadPermissions()]) }
})
</script>

<style scoped>
.role-management-panel {
  padding-top: 4px;
}

.tab-content {
  padding: 20px 0 0;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-count, .permission-count {
  font-size: 14px;
  color: #909399;
  font-weight: normal;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.empty-cell {
  color: #c0c4cc;
}

.permission-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.empty-state {
  padding: 24px;
  text-align: center;
  font-size: 14px;
  color: #909399;
}

.permission-hint {
  flex: 1;
  min-width: 260px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}

.permission-group {
  margin-bottom: 20px;
}

.permission-group:last-child {
  margin-bottom: 0;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.group-icon {
  color: #667eea;
  font-size: 16px;
}

.group-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.group-panel {
  border: 1px solid #e9ecef;
  border-radius: 8px;
  overflow: hidden;
}

.group-columns,
.permission-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(280px, 1.2fr) 80px;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
}

.group-columns {
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
  color: #909399;
}

.permission-row {
  border-bottom: 1px solid #f5f5f5;
}

.permission-row:last-child {
  border-bottom: none;
}

.permission-row:hover {
  background: #fafbff;
}

.perm-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.perm-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.perm-desc {
  font-size: 12px;
  color: #909399;
}

.perm-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.perm-actions {
  text-align: right;
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

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.role-management-panel :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.role-management-panel :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.role-management-panel :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.role-management-panel :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.role-management-panel :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}

.role-management-panel :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
