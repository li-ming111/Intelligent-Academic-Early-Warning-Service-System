<template>
  <div class="admin-rules" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>预警规则管理</h1>
      <p>预警规则配置和管理</p>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="addRuleDialogVisible = true">
        <el-icon><Plus /></el-icon> 添加规则
      </el-button>
      <el-select v-model="filterLevel" placeholder="按预警级别筛选" style="width: 150px;" @change="handleFilter">
        <el-option label="全部" value=""></el-option>
        <el-option label="低级预警" value="1"></el-option>
        <el-option label="中级预警" value="2"></el-option>
        <el-option label="高级预警" value="3"></el-option>
      </el-select>
      <el-input v-model="searchText" placeholder="搜索规则名称" style="width: 250px;" @input="handleSearch" clearable />
      <el-button @click="loadRules" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>预警规则列表</span>
          <span class="rule-count">共 {{ rulesList.length }} 条规则</span>
        </div>
      </template>

      <div v-if="rulesList.length === 0" style="padding: 20px; text-align: center; color: #999;">
        暂无数据
      </div>
      <el-table v-else :data="filteredRulesList" stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="规则ID" width="100" sortable />
        <el-table-column prop="ruleCode" label="规则编码" width="120" />
        <el-table-column prop="ruleName" label="规则名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="触发条件" min-width="250">
          <template #default="{ row }">
            {{ getConditionText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="warningLevel" label="预警级别" width="120">
          <template #default="{ row }">
            <el-tag :type="getLevelTagType(row.warningLevel)">
              {{ getLevelText(row.warningLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="规则描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="editRule(row)">编辑</el-button>
              <el-button type="danger" size="small" @click="deleteRule(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="selection-info" v-if="selectedRules.length > 0">
          已选择 {{ selectedRules.length }} 项
          <el-button type="danger" size="small" @click="batchDelete">批量删除</el-button>
        </div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalRules"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加规则弹窗 -->
    <el-dialog v-model="addRuleDialogVisible" title="添加规则" width="600px" :close-on-click-modal="false">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="formRules" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="例如：低级预警" />
        </el-form-item>
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="ruleForm.ruleCode" placeholder="例如：RULE001" />
        </el-form-item>
        <el-form-item label="触发条件" prop="expression">
          <el-input v-model="ruleForm.expression" type="textarea" :rows="3" placeholder="例如：fail_count >= 1 AND fail_count <= 2" />
        </el-form-item>
        <el-form-item label="预警级别" prop="warningLevel">
          <el-select v-model="ruleForm.warningLevel">
            <el-option label="低级预警" :value="1" />
            <el-option label="中级预警" :value="2" />
            <el-option label="高级预警" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则描述">
          <el-input v-model="ruleForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addRuleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAddRule">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑规则弹窗 -->
    <el-dialog v-model="editRuleDialogVisible" title="编辑规则" width="600px" :close-on-click-modal="false">
      <el-form ref="editRuleFormRef" :model="editingRule" :rules="formRules" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="editingRule.ruleName" placeholder="例如：低级预警" />
        </el-form-item>
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="editingRule.ruleCode" placeholder="例如：RULE001" />
        </el-form-item>
        <el-form-item label="触发条件" prop="expression">
          <el-input v-model="editingRule.expression" type="textarea" :rows="3" placeholder="例如：fail_count >= 1 AND fail_count <= 2" />
        </el-form-item>
        <el-form-item label="预警级别" prop="warningLevel">
          <el-select v-model="editingRule.warningLevel">
            <el-option label="低级预警" :value="1" />
            <el-option label="中级预警" :value="2" />
            <el-option label="高级预警" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则描述">
          <el-input v-model="editingRule.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editRuleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditRule">确定</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除规则 <strong>{{ ruleToDelete?.ruleName }}</strong> 吗？</p>
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
        <p>确定要删除选中的 <strong>{{ selectedRules.length }} 条规则</strong> 吗？</p>
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
import { Plus, Refresh, Edit, Check, Delete, WarningFilled } from '@element-plus/icons-vue'

const addRuleDialogVisible = ref(false)
const editRuleDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const batchDeleteDialogVisible = ref(false)
const rulesList = ref([])
const filterLevel = ref('')
const searchText = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const deleteLoading = ref(false)
const selectedRules = ref([])
const ruleToDelete = ref(null)

const ruleFormRef = ref(null)
const editRuleFormRef = ref(null)

const ruleForm = ref({ 
  ruleName: '', 
  ruleCode: '', 
  expression: '', 
  warningLevel: 1,
  description: '' 
})

const editingRule = ref({ 
  id: null,
  ruleName: '', 
  ruleCode: '', 
  expression: '', 
  warningLevel: 1,
  description: '' 
})

const formRules = {
  ruleName: [
    { required: true, message: '请输入规则名称', trigger: 'blur' },
    { min: 2, max: 100, message: '规则名称长度为 2-100 个字符', trigger: 'blur' }
  ],
  ruleCode: [
    { required: true, message: '请输入规则编码', trigger: 'blur' },
    { min: 3, max: 20, message: '规则编码长度为 3-20 个字符', trigger: 'blur' }
  ],
  expression: [
    { required: true, message: '请输入触发条件', trigger: 'blur' }
  ],
  warningLevel: [
    { required: true, message: '请选择预警级别', trigger: 'change' }
  ]
}

onMounted(async () => {
  await loadRules()
})

const loadRules = async () => {
  loading.value = true
  try {
    const response = await adminAPI.getRules()
    if (Array.isArray(response)) {
      rulesList.value = response
    } else {
      rulesList.value = []
    }
  } catch (error) {
    console.error('加载规则列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  currentPage.value = 1
}

const handleSearch = () => {
  currentPage.value = 1
}

const handleSelectionChange = (selection) => {
  selectedRules.value = selection
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

const filteredRulesList = computed(() => {
  let filtered = rulesList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(rule =>
      rule.ruleName.toLowerCase().includes(searchLower) ||
      String(rule.id).includes(searchLower) ||
      (rule.ruleCode && rule.ruleCode.toLowerCase().includes(searchLower))
    )
  }

  if (filterLevel.value) {
    filtered = filtered.filter(rule => rule.warningLevel === parseInt(filterLevel.value))
  }

  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filtered.slice(start, end)
})

const totalRules = computed(() => {
  let filtered = rulesList.value

  if (searchText.value) {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(rule =>
      rule.ruleName.toLowerCase().includes(searchLower) ||
      String(rule.id).includes(searchLower) ||
      (rule.ruleCode && rule.ruleCode.toLowerCase().includes(searchLower))
    )
  }

  if (filterLevel.value) {
    filtered = filtered.filter(rule => rule.warningLevel === parseInt(filterLevel.value))
  }

  return filtered.length
})

// `testRule` 已删（2026-09-30）：它无条件弹「规则【X】测试通过」，不比对任何数据，
// 而且**模板里根本没有按钮引用它**（操作列只有 编辑/删除）—— 是一段没被调用的死代码。
// 真的"测试规则"不是前端能做的事：判断一条规则会不会生效要看
// warning-service 的 `RuleConditionParser` 能否解析它的 expression、`status` 是否为 1、
// `warningLevel` 是否已配（三项缺一，`WarningScheduledTask.loadRules` 就会把这条规则跳过，
// 只写日志不报警）。要在页面上给出真结论，得在服务端加一个复用 `RuleConditionParser`
// 的接口 —— 见手册 §7 该项的说明，本轮未做。

const getConditionText = (row) => {
  if (row.expression) {
    const expr = row.expression;
    if (expr.includes('fail_count')) {
      if (expr.includes('>= 1 AND fail_count <= 2')) {
        return '累计挂科 1-2 科（含 2 科）'
      } else if (expr.includes('>= 3 AND fail_count <= 5')) {
        return '累计挂科 3-5 科（含 3 科和 5 科）'
      } else if (expr.includes('> 5')) {
        return '累计挂科超过 5 科（不含 5 科）'
      } else if (expr.includes('>= 1')) {
        return '累计挂科 1 科以上'
      }
    }
    return row.expression
  } else if (row.ruleCondition) {
    return row.ruleCondition
  }
  return ''
}

const getLevelText = (level) => {
  if (level === 1) return '低级预警'
  if (level === 2) return '中级预警'
  if (level === 3) return '高级预警'
  return `等级${level}`
}

const getLevelTagType = (level) => {
  if (level === 1) return 'success'
  if (level === 2) return 'warning'
  if (level === 3) return 'danger'
  return 'info'
}

const editRule = (row) => {
  editingRule.value = { ...row }
  editRuleDialogVisible.value = true
}

const submitEditRule = async () => {
  if (!editRuleFormRef.value) return
  
  try {
    await editRuleFormRef.value.validate()
    const data = {
      ruleName: editingRule.value.ruleName,
      ruleCode: editingRule.value.ruleCode,
      expression: editingRule.value.expression,
      warningLevel: editingRule.value.warningLevel,
      description: editingRule.value.description
    }
    await adminAPI.updateRule(editingRule.value.id, data)
    ElMessage.success('规则已更新')
    editRuleDialogVisible.value = false
    const index = rulesList.value.findIndex(item => item.id === editingRule.value.id)
    if (index !== -1) {
      rulesList.value[index] = { ...rulesList.value[index], ...data }
    }
  } catch (error) {
    console.error('更新规则失败:', error)
    ElMessage.error('更新失败')
  }
}

const deleteRule = (row) => {
  ruleToDelete.value = row
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!ruleToDelete.value) return

  deleteLoading.value = true
  try {
    await adminAPI.deleteRule(ruleToDelete.value.id)
    ElMessage.success('规则已删除')
    deleteDialogVisible.value = false
    ruleToDelete.value = null
    // 2026-10-02（W4.3）：原来这里是 `filter(item => item.id !== ruleToDelete.value?.id)`，
    // 而上一行刚把它置 null ⇒ 比较的是 undefined ⇒ 过滤恒真 ⇒ 行不消失。
    // 改成重新拉取（与 AdminCourses/AdminUsers 同形）。
    await loadRules()
  } catch (error) {
    console.error('删除规则失败:', error)
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const batchDelete = () => {
  if (selectedRules.value.length === 0) {
    ElMessage.warning('请先选择要删除的规则')
    return
  }
  batchDeleteDialogVisible.value = true
}

const confirmBatchDelete = async () => {
  deleteLoading.value = true
  try {
    const deletePromises = selectedRules.value.map(rule =>
      adminAPI.deleteRule(rule.id)
    )
    await Promise.all(deletePromises)
    ElMessage.success(`成功删除 ${selectedRules.value.length} 条规则`)
    batchDeleteDialogVisible.value = false
    selectedRules.value = []
    await loadRules()
  } catch (error) {
    console.error('批量删除失败:', error)
    ElMessage.error('批量删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const submitAddRule = async () => {
  if (!ruleFormRef.value) return
  
  try {
    await ruleFormRef.value.validate()
    await adminAPI.createRule(ruleForm.value)
    ElMessage.success('规则已添加')
    addRuleDialogVisible.value = false
    ruleForm.value = { ruleName: '', ruleCode: '', expression: '', warningLevel: 1, description: '' }
    await loadRules()
  } catch (error) {
    console.error('添加规则失败:', error)
    ElMessage.error('添加规则失败：' + (error.message || '未知错误'))
  }
}
</script>

<style scoped>
.admin-rules {
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

.rule-count {
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

.action-buttons {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.action-buttons .el-button {
  margin: 0;
}

.admin-rules :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-rules :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-rules :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-rules :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-rules :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}
</style>