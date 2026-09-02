<template>
  <div class="cc-page">
    <div class="cc-head">
      <h1>沟通记录</h1>
      <p>记录并跟踪与学生的约谈、帮扶沟通及学生反馈</p>
    </div>

    <el-card>
      <template #header>
        <div class="cc-toolbar">
          <span class="cc-card-title">沟通记录列表</span>
          <div class="cc-toolbar-actions">
            <el-select
              v-model="studentFilter"
              placeholder="按学生筛选"
              clearable
              filterable
              style="width:180px;margin-right:10px"
            >
              <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
            <el-button type="primary" @click="openAddDialog">+ 新增沟通</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="filteredLogs.length === 0" description="暂无沟通记录" />
      <el-table v-else :data="filteredLogs" stripe>
        <el-table-column label="学生" width="110" align="center">
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110" align="center" />
        <el-table-column prop="content" label="沟通内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已反馈' : '跟进中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="学生反馈" min-width="180">
          <template #default="{ row }">{{ row.reply || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="沟通时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              type="primary"
              size="small"
              link
              @click="openReplyDialog(row)"
            >登记反馈</el-button>
            <el-button type="danger" size="small" link @click="removeLog(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增沟通记录 -->
    <el-dialog v-model="addVisible" title="新增沟通记录" width="520px">
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="学生" required>
          <el-select v-model="addForm.studentId" placeholder="选择学生" filterable style="width:100%">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="沟通分类" required>
          <el-select v-model="addForm.category" placeholder="选择分类" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="沟通内容" required>
          <el-input
            v-model="addForm.content"
            type="textarea"
            :rows="4"
            placeholder="记录本次沟通要点（如约谈内容、学生困难、辅导安排等）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 登记学生反馈 -->
    <el-dialog v-model="replyVisible" title="登记学生反馈" width="520px">
      <el-form label-width="90px">
        <el-form-item label="沟通内容">
          <el-input :model-value="currentLog ? currentLog.content : ''" type="textarea" :rows="3" disabled />
        </el-form-item>
        <el-form-item label="学生反馈" required>
          <el-input
            v-model="replyForm.reply"
            type="textarea"
            :rows="3"
            placeholder="记录学生的反馈与后续承诺，保存后该条记录标记为已反馈"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">保存反馈</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { counselorAPI } from '@/api/index'

const students = ref([])
const logs = ref([])
const studentFilter = ref(null)
const addVisible = ref(false)
const replyVisible = ref(false)
const submitting = ref(false)
const currentLog = ref(null)
const addForm = ref({ studentId: null, category: '', content: '' })
const replyForm = ref({ reply: '' })

const categories = ['预警约谈', '成绩沟通', '帮扶跟进', '日常关怀', '家长沟通']

const counselorId = () => {
  const uid = localStorage.getItem('userId')
  return uid ? parseInt(uid) : null
}

const studentName = (id) => {
  const s = students.value.find((x) => x.id === id)
  return s ? s.name : (id ? `学生#${id}` : '—')
}

const filteredLogs = computed(() => {
  if (!studentFilter.value) return logs.value
  return logs.value.filter((l) => l.studentId === studentFilter.value)
})

const loadStudents = async () => {
  const cid = counselorId()
  if (!cid) return
  try {
    const res = await counselorAPI.getStudents(cid)
    if (Array.isArray(res)) students.value = res
  } catch (e) {
    console.error(e)
  }
}

const loadLogs = async () => {
  const cid = counselorId()
  if (!cid) return
  try {
    const res = await counselorAPI.getCommunicationLogs(cid)
    logs.value = Array.isArray(res) ? res : []
  } catch (e) {
    ElMessage.error('加载沟通记录失败: ' + (e.message || '网络错误'))
  }
}

onMounted(() => {
  loadStudents()
  loadLogs()
})

const openAddDialog = () => {
  addForm.value = {
    studentId: students.value.length === 1 ? students.value[0].id : null,
    category: '',
    content: ''
  }
  addVisible.value = true
}

const submitAdd = async () => {
  if (!addForm.value.studentId) { ElMessage.error('请选择学生'); return }
  if (!addForm.value.category) { ElMessage.error('请选择沟通分类'); return }
  if (!addForm.value.content.trim()) { ElMessage.error('请填写沟通内容'); return }
  submitting.value = true
  try {
    await counselorAPI.createCommunicationLog({
      counselorId: counselorId(),
      studentId: addForm.value.studentId,
      category: addForm.value.category,
      content: addForm.value.content
    })
    ElMessage.success('沟通记录已保存')
    addVisible.value = false
    loadLogs()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '网络错误'))
  } finally {
    submitting.value = false
  }
}

const openReplyDialog = (row) => {
  currentLog.value = row
  replyForm.value = { reply: row.reply || '' }
  replyVisible.value = true
}

const submitReply = async () => {
  if (!replyForm.value.reply.trim()) { ElMessage.error('请填写学生反馈'); return }
  submitting.value = true
  try {
    await counselorAPI.updateCommunicationLog(currentLog.value.id, {
      reply: replyForm.value.reply,
      status: 1
    })
    ElMessage.success('反馈已登记')
    replyVisible.value = false
    loadLogs()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '网络错误'))
  } finally {
    submitting.value = false
  }
}

const removeLog = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除与「${studentName(row.studentId)}」的这条沟通记录吗？`, '删除确认', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await counselorAPI.deleteCommunicationLog(row.id)
    ElMessage.success('已删除')
    loadLogs()
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.message || '网络错误'))
  }
}
</script>

<style scoped>
.cc-page {
  padding: 4px;
}
.cc-head {
  margin-bottom: 16px;
}
.cc-head h1 {
  margin: 0 0 6px;
  font-size: 22px;
}
.cc-head p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}
.cc-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cc-card-title {
  font-weight: 600;
}
</style>
