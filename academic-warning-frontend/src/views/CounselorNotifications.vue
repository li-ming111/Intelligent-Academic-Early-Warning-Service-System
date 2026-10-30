<template>
  <div class="cn-page">
    <div class="cn-head">
      <h1>通知中心</h1>
      <p>向学生发送通知、向教师发送消息，并查看教师回复</p>
    </div>

    <el-tabs v-model="activeTab" class="cn-tabs">
      <el-tab-pane label="学生通知" name="student">
    <el-card style="margin-bottom:20px">
      <template #header><span class="cn-card-title">创建新通知</span></template>
      <el-form :model="form" label-width="100px">
        <el-form-item label="通知类型">
          <el-select v-model="form.type" placeholder="选择通知类型" style="width:200px">
            <el-option
              v-for="t in typeOptions"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="接收范围">
          <!-- 单选：原来是多选，但「全部学生」+「预警学生」叠加就是全部学生，没有意义。
               这里一次只能选一个范围。 -->
          <el-select v-model="form.scope" placeholder="选择接收范围" style="width:300px">
            <el-option label="全部学生" value="all" />
            <el-option label="预警学生（名下有未处理预警）" value="warning-students" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知标题">
          <el-input v-model="form.title" placeholder="输入通知标题（可留空）" />
        </el-form-item>
        <el-form-item label="通知内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="输入通知内容" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="sending" @click="sendNotification">发送通知</el-button>
          <span class="cn-hint">发送后：这条会进入每个接收学生的「通知中心」，同时在辅导员的发送记录里留一条</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <span class="cn-card-title">发送历史</span>
        <span v-if="historyTotal > 0" class="cn-count">共 {{ historyTotal }} 次发送</span>
      </template>

      <el-empty v-if="history.length === 0" description="暂无发送记录" />

      <template v-else>
        <!-- ⚠️ 表头按库里真实有的字段来：communication_logs 里没有"标题"列，
             群发是每人一行（按 内容+时间戳 归并成一条），所以这里显示 内容/类型/接收人数/时间。
             原来那套 标题/查看详情/删除 三列里，前者的数据不存在，后两个按钮没有处理器。 -->
        <el-table :data="history" stripe>
          <el-table-column prop="content" label="内容" min-width="320">
            <template #default="{ row }">
              <div class="cn-content">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag :type="row.category === 'warning' ? 'danger' : row.category === 'important' ? 'warning' : 'info'" size="small">
                {{ typeLabel(row.category) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="recipientCount" label="接收人数" width="100" />
          <el-table-column label="发送时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>

        <div v-if="historyNote" class="cn-note">{{ historyNote }}</div>
      </template>
    </el-card>
      </el-tab-pane>

      <el-tab-pane label="教师消息" name="teacher">
    <el-card style="margin-bottom:20px">
      <template #header><span class="cn-card-title">给教师发消息</span></template>
      <el-form :model="teacherForm" label-width="100px">
        <el-form-item label="接收教师" required>
          <el-select v-model="teacherForm.teacherId" placeholder="选择教师（可按姓名搜索）" filterable style="width:320px">
            <el-option v-for="t in teachers" :key="t.userId" :label="teacherLabel(t)" :value="t.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息内容" required>
          <el-input
            v-model="teacherForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="输入要发送给教师的消息"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="sendingTeacher" @click="sendTeacherMessage">发送消息</el-button>
          <span class="cn-hint">发送后进入教师端「消息中心」，在线教师会实时收到提醒</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-bottom:20px">
      <template #header>
        <span class="cn-card-title">教师回复</span>
        <span v-if="receivedTeacherReplies.length" class="cn-count">最近 {{ receivedTeacherReplies.length }} 条</span>
        <el-button
          v-if="unreadReplyCount > 0"
          type="primary"
          link
          size="small"
          style="margin-left:12px"
          @click="markRepliesRead"
        >{{ unreadReplyCount }} 条未读，全部已读</el-button>
      </template>

      <el-empty v-if="receivedTeacherReplies.length === 0" description="暂无教师回复" />
      <el-table v-else :data="receivedTeacherReplies" stripe>
        <el-table-column prop="senderName" label="回复教师" width="130" />
        <el-table-column prop="content" label="回复内容" min-width="320">
          <template #default="{ row }">
            <div class="cn-content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ row.status === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回复时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-bottom:20px">
      <template #header>
        <span class="cn-card-title">发给教师的记录</span>
        <span v-if="sentTeacherMessages.length" class="cn-count">最近 {{ sentTeacherMessages.length }} 条</span>
      </template>

      <el-empty v-if="sentTeacherMessages.length === 0" description="暂无教师消息记录" />
      <el-table v-else :data="sentTeacherMessages" stripe>
        <el-table-column prop="receiverName" label="接收教师" width="130" />
        <el-table-column prop="content" label="内容" min-width="300">
          <template #default="{ row }">
            <div class="cn-content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ row.status === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { counselorAPI } from '@/api/index'
import { subscribeMyMessages, unsubscribeMyMessages } from '@/utils/realtimeMessages'

const typeOptions = [
  { value: 'warning', label: '预警通知' },
  { value: 'important', label: '重要通知' },
  { value: 'normal', label: '普通通知' }
]

const form = ref({ type: 'normal', scope: 'all', title: '', content: '' })
const activeTab = ref('student')
const history = ref([])
const historyTotal = ref(0)
const historyNote = ref('')
const sending = ref(false)

const teachers = ref([])
const teacherForm = ref({ teacherId: null, content: '' })
const sendingTeacher = ref(false)
const sentTeacherMessages = ref([])
const receivedTeacherReplies = ref([])
const unreadReplyCount = computed(() =>
  receivedTeacherReplies.value.filter(r => r.status === 0).length
)

const teacherLabel = (t) => {
  const name = t.name || t.username || '未知教师'
  return t.college ? `${name} · ${t.college}` : name
}

// `counselorId` 这个 key 全仓没有任何地方写过，实际只有 userId 可用（classes.counselor_id 存的也是 users.id）
const getCounselorId = () => {
  const uid = localStorage.getItem('counselorId') || localStorage.getItem('userId')
  return uid ? parseInt(uid) : null
}

const typeLabel = (v) => (typeOptions.find(t => t.value === v) || {}).label || v

// 后端 LocalDateTime 序列化成 ISO（2026-10-19T16:50:48），原样渲染会露出 T。
const formatDateTime = (v) => {
  if (!v) return '—'
  const d = new Date(v)
  return isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadHistory()
  loadTeachers()
  loadSentTeacherMessages()
  loadReceivedTeacherReplies()
  // 2026-10-02（W3.2）：辅导员也会**收**消息（例如学生提交申诉后的回执），
  // 订阅自己的队列，收到即刷新发送历史并提示。
  subscribeMyMessages(onRealtimeMessage)
})

onUnmounted(() => {
  unsubscribeMyMessages()
})

async function onRealtimeMessage(payload) {
  const content = payload && (payload.content || payload.title)
  ElMessage.success(content ? `收到新消息：${content}` : '收到新消息')
  await loadHistory()
  await loadReceivedTeacherReplies()
  // 教师回复时会把原消息标为已读，发送记录的状态也要跟着变
  await loadSentTeacherMessages()
}

async function loadHistory() {
  try {
    const uid = getCounselorId()
    if (!uid) return
    const res = await counselorAPI.getNotificationHistory(uid)
    history.value = Array.isArray(res?.list) ? res.list : (Array.isArray(res) ? res : [])
    historyTotal.value = Number(res?.total) || history.value.length
    historyNote.value = res?.note || ''
  } catch (e) {
    console.error('[counselor notifications] 加载历史失败:', e)
  }
}

async function loadTeachers() {
  try {
    teachers.value = (await counselorAPI.getTeacherDirectory()) || []
  } catch (e) {
    console.error('[counselor notifications] 加载教师列表失败:', e)
    ElMessage.error('教师列表加载失败')
  }
}

async function loadSentTeacherMessages() {
  try {
    sentTeacherMessages.value = (await counselorAPI.getSentTeacherMessages()) || []
  } catch (e) {
    console.error('[counselor notifications] 加载教师消息记录失败:', e)
  }
}

async function loadReceivedTeacherReplies() {
  try {
    receivedTeacherReplies.value = (await counselorAPI.getReceivedTeacherReplies()) || []
  } catch (e) {
    console.error('[counselor notifications] 加载教师回复失败:', e)
  }
}

async function markRepliesRead() {
  try {
    await counselorAPI.markTeacherRepliesRead()
    await loadReceivedTeacherReplies()
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    console.error('[counselor notifications] 标记教师回复已读失败:', e)
    ElMessage.error('操作失败: ' + (e.message || '网络错误'))
  }
}

const sendTeacherMessage = async () => {
  if (!getCounselorId()) { ElMessage.error('未取到当前辅导员身份，请重新登录'); return }
  if (!teacherForm.value.teacherId) { ElMessage.error('请选择接收教师'); return }
  const content = teacherForm.value.content.trim()
  if (!content) { ElMessage.error('请填写消息内容'); return }

  sendingTeacher.value = true
  try {
    await counselorAPI.sendTeacherMessage({
      teacherId: teacherForm.value.teacherId,
      content
    })
    ElMessage.success('消息已发送')
    teacherForm.value = { teacherId: null, content: '' }
    await loadSentTeacherMessages()
  } catch (e) {
    console.error('[counselor notifications] 发送教师消息失败:', e)
    ElMessage.error('发送失败: ' + (e.message || '网络错误'))
  } finally {
    sendingTeacher.value = false
  }
}

const sendNotification = async () => {  if (!getCounselorId()) { ElMessage.error('未取到当前辅导员身份，请重新登录'); return }
  if (!form.value.content.trim()) { ElMessage.error('请填写通知内容'); return }
  if (!form.value.scope) { ElMessage.error('请选择接收范围'); return }

  sending.value = true
  try {
    const res = await counselorAPI.notifyStudents({
      counselorId: getCounselorId(),
      title: form.value.title,
      content: form.value.content,
      type: form.value.type,
      recipients: [form.value.scope]
    })
    // 汇报真实结果：接口回的是 {recipients, delivered, recorded, skipped, undeliverable}
    // 以前这里无条件弹「通知已发送」，哪怕后端一个人都没发出去。
    const delivered = Number(res?.delivered) || 0
    const skipped = Array.isArray(res?.skipped) ? res.skipped : []
    const undeliverable = Array.isArray(res?.undeliverable) ? res.undeliverable : []
    if (delivered === 0) {
      ElMessage.warning('没有学生收到这条通知：' + (skipped[0] || undeliverable[0] || '接收范围里没有学生'))
    } else if (skipped.length || undeliverable.length) {
      ElMessage.success(`已发送 ${delivered} 人，另有 ${skipped.length + undeliverable.length} 人未送达`)
      await ElMessageBox.alert(
        [...skipped, ...undeliverable.map(x => `学生档案未绑定登录账号：${x}`)].join('\n'),
        '未送达明细',
        { type: 'warning' }
      )
    } else {
      ElMessage.success(`已发送 ${delivered} 人`)
    }
    form.value = { type: 'normal', scope: 'all', title: '', content: '' }
    await loadHistory()
  } catch (e) {
    ElMessage.error('发送失败: ' + (e.message || '网络错误'))
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.cn-page { padding: 24px; }
.cn-head { margin-bottom: 20px; }
.cn-head h1 { margin: 0 0 4px; font-size: 22px; font-weight: 700; color: #1e3a5f; }
.cn-head p { margin: 0; font-size: 13px; color: #94a3b8; }
.cn-tabs { margin-bottom: 4px; }
.cn-card-title { font-size: 15px; font-weight: 600; color: #1e293b; }
.cn-count { margin-left: 12px; font-size: 13px; color: #94a3b8; font-weight: normal; }
.cn-hint { margin-left: 12px; font-size: 12px; color: #94a3b8; }
.cn-content { white-space: pre-line; font-size: 13px; color: #334155; }
.cn-note { margin-top: 12px; font-size: 12px; color: #94a3b8; }
:deep(.el-card) { border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
:deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid #f1f5f9; }
:deep(.el-card__body) { padding: 20px; }
</style>
