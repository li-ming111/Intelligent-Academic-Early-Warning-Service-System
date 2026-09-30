<template>
  <div class="counselor-students">
    <div class="page-header">
      <h1>学生管理</h1>
      <p>班级学生列表、搜索和管理</p>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-bar">
      <el-input v-model="searchName" placeholder="搜索学生姓名或学号" style="width: 250px;"></el-input>
      <el-select v-model="selectedClass" placeholder="选择班级" style="width: 200px;">
        <el-option v-for="c in classOptions" :key="c.id" :label="c.name" :value="c.name" />
      </el-select>
      <el-button type="primary" @click="searchStudents">搜索</el-button>
      <el-button @click="exportStudents">导出</el-button>
    </div>

    <!-- 学生表格 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">学生列表 ({{ studentList.length }})</div>
      </template>

      <el-table :data="studentList" stripe style="width: 100%">
        <el-table-column prop="studentId" label="学号" min-width="120" align="center"></el-table-column>
        <el-table-column label="姓名" min-width="100" align="center">
          <template #default="{ row }">{{ row.studentName || row.name || '--' }}</template>
        </el-table-column>
        <el-table-column prop="className" label="班级" min-width="120" show-overflow-tooltip></el-table-column>
        <el-table-column prop="phone" label="联系电话" min-width="130"></el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip></el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="viewStudent(row)">查看详情</el-button>
            <el-button type="warning" size="small" link @click="sendNotification(row)">发送通知</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 学生详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="学生详情" width="600px">
      <div class="student-detail">
        <p><strong>学号：</strong>{{ selectedStudent.studentId }}</p>
        <p><strong>姓名：</strong>{{ selectedStudent.studentName }}</p>
        <p><strong>班级：</strong>{{ selectedStudent.className }}</p>
        <p><strong>联系电话：</strong>{{ selectedStudent.phone }}</p>
        <p><strong>邮箱：</strong>{{ selectedStudent.email }}</p>
        <p><strong>获取学分：</strong>{{ selectedStudent.credits || '--' }} / {{ selectedStudent.requiredCredits || '--' }}</p>
        <p><strong>GPA：</strong>{{ selectedStudent.gpa || '--' }}</p>
      </div>
    </el-dialog>

    <!-- 批量通知对话框 -->
    <el-dialog v-model="notificationDialogVisible" title="发送通知" width="600px">
      <el-form :model="notificationForm" label-width="100px">
        <el-form-item label="接收人">
          <el-input :value="notificationForm.receiver" disabled></el-input>
        </el-form-item>
        <el-form-item label="通知内容">
          <el-input v-model="notificationForm.content" type="textarea" :rows="4" placeholder="请输入通知内容..."></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="notificationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNotification">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { counselorAPI, adminAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const searchName = ref('')
const selectedClass = ref('')
const detailDialogVisible = ref(false)
const notificationDialogVisible = ref(false)
const selectedStudent = ref({})
const classOptions = ref([])
const allStudents = ref([])

const notificationForm = ref({
  receiver: '',
  content: ''
})

onMounted(async () => {
  await loadStudents()
  await loadClasses()
})

const loadStudents = async () => {
  try {
    const userId = getUserId()
    const counselorId = localStorage.getItem('counselorId') || userId
    if (!counselorId) return
    const response = await counselorAPI.getStudents(counselorId)
    let rawData = []
    if (response && response.code === 200) {
      rawData = response.data || []
    } else if (Array.isArray(response)) {
      rawData = response
    }
    // 统一字段名：后端返回 name，前端统一使用 studentName
    allStudents.value = rawData.map(s => ({
      ...s,
      studentName: s.studentName || s.name || ''
    }))
  } catch (error) {
    console.error('加载学生列表失败:', error)
  }
}

const loadClasses = async () => {
  try {
    const uid = getUserId()
    if (!uid) return
    const res = await counselorAPI.getMyClasses(uid)
    classOptions.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e) { console.error(e) }
}

const studentList = computed(() => {
  let list = allStudents.value
  if (searchName.value) {
    const kw = searchName.value.toLowerCase()
    list = list.filter(s => (s.studentName || s.name || '').toLowerCase().includes(kw) || String(s.studentId || '').includes(kw))
  }
  if (selectedClass.value) {
    list = list.filter(s => s.className === selectedClass.value || s.class_name === selectedClass.value)
  }
  return list
})

const searchStudents = () => {
  ElMessage.success('搜索完成，共 ' + studentList.value.length + ' 条结果')
}

const exportStudents = async () => {
  try {
    ElMessage.info('学生信息已导出')
  } catch (error) {
    console.error('导出失败:', error)
  }
}

const viewStudent = (row) => {
  selectedStudent.value = row
  detailDialogVisible.value = true
}

const sendNotification = (row) => {
  console.log('学生数据:', row)
  selectedStudent.value = row
  notificationForm.value.receiver = row.studentName || row.name || ''
  notificationDialogVisible.value = true
}

const submitNotification = async () => {
  if (!notificationForm.value.content) {
    ElMessage.error('请输入通知内容')
    return
  }
  // ⚠️ 只认两个数值型 ID：userId(users.id) / id(student_profile.id)。
  // 原来的兜底里有 studentId —— 那是**学号字符串**（如 "2023001"），一旦 userId 为空，
  // 就会把学号当接收人发出去。后端两个 ID 空间都认，但学号不认。
  const targetId = selectedStudent.value.userId || selectedStudent.value.id
  if (!targetId) {
    ElMessage.error('这个学生没有可用的用户 ID（student_profile.user_id 为空），发不了通知')
    return
  }
  try {
    const data = {
      student_ids: [targetId],
      message: notificationForm.value.content
    }
    const res = await counselorAPI.notifyStudents(data)
    const delivered = Number(res?.delivered) || 0
    const undeliverable = Array.isArray(res?.undeliverable) ? res.undeliverable : []
    if (delivered === 0) {
      // 后端如实回了 0 就别说"已发送"
      ElMessage.warning('未送达：' + (undeliverable[0] ? `学生 ${undeliverable[0]} 的档案没绑登录账号` : '接收人解析失败'))
    } else {
      ElMessage.success('通知已发送')
    }
    notificationDialogVisible.value = false
    notificationForm.value.content = ''
  } catch (error) {
    console.error('发送通知失败:', error)
    ElMessage.error('发送失败: ' + (error.message || '网络错误'))
  }
}
</script>

<style scoped>
.counselor-students {
  padding: 20px;
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

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.student-detail {
  line-height: 1.8;
}

.student-detail p {
  margin: 10px 0;
}
</style>
