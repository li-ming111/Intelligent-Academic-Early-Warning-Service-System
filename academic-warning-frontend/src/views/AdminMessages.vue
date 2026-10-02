<template>
  <div class="admin-messages" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>消息与任务管理</h1>
      <p>系统广播消息、定向通知、任务分配与追踪</p>
    </div>

    <!-- Tab页签 -->
    <el-tabs type="card">
      <!-- 消息管理 -->
      <el-tab-pane label="消息管理">
        <div class="tab-content">
          <!-- 发送消息卡片 -->
          <el-card style="margin-bottom: 20px;">
            <template #header>
              <div class="card-header">发送新消息</div>
            </template>

            <el-form :model="newMessage" label-width="80px">
              <el-form-item label="消息标题">
                <el-input v-model="newMessage.title" placeholder="输入消息标题"></el-input>
              </el-form-item>
              <el-form-item label="消息内容">
                <el-input
                  v-model="newMessage.content"
                  type="textarea"
                  :rows="4"
                  placeholder="输入消息内容"
                ></el-input>
              </el-form-item>
              <!-- 2026-10-01 第二十一轮删：「消息等级」(信息/警告/危急) 这个单选框。
                   它只绑到 newMessage.level，而这个 key 到不了库里任何一列 ——
                   广播走 /api/messages/broadcast，后端只读 title/content/targetRole；
                   定向走 /api/messages/send，只读 userId/content/type。messages 表
                   唯一的等级类字段是 `type`(1=广播/3=预警)，与「信息/警告/危急」不是一回事。
                   选它等于什么也没选，所以删控件 + 删那一列，改显示真实存在的 `type`。 -->
              <el-form-item label="发送范围">
                <el-radio-group v-model="newMessage.target">
                  <el-radio value="all">全部用户</el-radio>
                  <el-radio value="targeted">指定用户</el-radio>
                </el-radio-group>
              </el-form-item>

              <div v-if="newMessage.target === 'targeted'" style="margin-bottom: 20px;">
                <el-form-item label="选择用户">
                  <el-select
                    v-model="newMessage.user_ids"
                    multiple
                    placeholder="选择接收消息的用户"
                    style="width: 100%;"
                  >
                    <el-option
                      v-for="(user, idx) in users"
                      :key="idx"
                      :label="user.username"
                      :value="user.id"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </div>

              <el-form-item>
                <el-button type="primary" @click="sendMessage">发送消息</el-button>
                <el-button @click="resetMessageForm">重置</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 消息列表 -->
          <el-card>
            <template #header>
              <div class="card-header">
                <span>消息历史</span>
                <span class="message-count">共 {{ messageTotal }} 条消息</span>
              </div>
            </template>

            <div v-if="messages.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无数据
            </div>
            <el-table v-else :data="messages" stripe v-loading="loading" @selection-change="handleMessageSelectionChange">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="消息ID" width="100" sortable />
              <el-table-column prop="title" label="标题" min-width="150"></el-table-column>
              <el-table-column prop="content" label="内容" min-width="250"></el-table-column>
              <!-- 2026-10-01 第二十一轮改：这两列原来读 `row.level` / `row.target`，
                   而 `/admin/messages/list` 回的是裸 Message 实体（只有 id/senderId/
                   receiverId/title/content/type/status/createdAt），两个 key 恒 undefined。
                   三元的 else 分支于是**每行都渲染成**「危急」+「指定用户」——
                   不是空白（空白还看得见是缺数据），是把编造的值画得跟真的一样。
                   改成真实存在的 `type` 与 `receiverId`。 -->
              <el-table-column prop="type" label="类型" width="110">
                <template #default="{ row }">
                  <el-tag :type="messageTypeTag(row.type)">{{ messageTypeName(row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="receiverId" label="接收人" width="140">
                <template #default="{ row }">
                  {{ receiverName(row.receiverId) }}
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="发送时间" width="180" sortable></el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button type="danger" size="small" @click="deleteMessage(row.id)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer">
              <div class="selection-info" v-if="selectedMessages.length > 0">
                已选择 {{ selectedMessages.length }} 项
                <el-button type="danger" size="small" @click="batchDeleteMessages">批量删除</el-button>
              </div>
              <el-pagination
                v-model:current-page="messagePage"
                v-model:page-size="messagePageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="messageTotal"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleMessageSizeChange"
                @current-change="handleMessageCurrentChange"
              />
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 任务管理 -->
      <el-tab-pane label="任务管理">
        <div class="tab-content">
          <!-- 创建任务卡片 -->
          <el-card style="margin-bottom: 20px;">
            <template #header>
              <div class="card-header">创建新任务</div>
            </template>

            <el-form :model="newTask" label-width="80px">
              <el-form-item label="任务标题">
                <el-input v-model="newTask.title" placeholder="输入任务标题"></el-input>
              </el-form-item>
              <el-form-item label="任务描述">
                <el-input
                  v-model="newTask.description"
                  type="textarea"
                  :rows="4"
                  placeholder="输入任务描述"
                ></el-input>
              </el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="newTask.priority" placeholder="选择优先级">
                  <el-option label="低" value="low"></el-option>
                  <el-option label="中" value="medium"></el-option>
                  <el-option label="高" value="high"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="createTask">创建任务</el-button>
                <el-button @click="resetTaskForm">重置</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 任务统计 -->
          <div class="stats-grid" style="margin-bottom: 20px;">
            <div class="stat-card">
              <div class="stat-title">总任务数</div>
              <div class="stat-value">{{ tasks.length }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-title">待处理</div>
              <div class="stat-value" style="color: #f56c6c;">
                {{ tasks.filter(t => t.status === 'pending').length }}
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-title">进行中</div>
              <div class="stat-value" style="color: #e6a23c;">
                {{ tasks.filter(t => t.status === 'in_progress').length }}
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-title">已完成</div>
              <div class="stat-value" style="color: #67c23a;">
                {{ tasks.filter(t => t.status === 'completed').length }}
              </div>
            </div>
          </div>

          <!-- 任务列表 -->
          <el-card>
            <template #header>
              <div class="card-header">
                <span>任务列表</span>
                <span class="task-count">共 {{ tasks.length }} 个任务</span>
              </div>
            </template>

            <div style="margin-bottom: 15px;">
              <el-button
                v-for="s in ['all', 'pending', 'in_progress', 'completed']"
                :key="s"
                :type="taskFilter === s ? 'primary' : 'info'"
                size="small"
                @click="taskFilter = s"
              >
                {{ s === 'all' ? '全部' : (s === 'pending' ? '待处理' : (s === 'in_progress' ? '进行中' : '已完成')) }}
              </el-button>
            </div>

            <div v-if="filteredTasks.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无数据
            </div>
            <el-table v-else :data="filteredTasks" stripe @selection-change="handleTaskSelectionChange">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="任务ID" width="100" sortable />
              <el-table-column prop="title" label="任务标题" min-width="150"></el-table-column>
              <el-table-column prop="description" label="描述" min-width="250"></el-table-column>
              <el-table-column label="优先级" width="100">
                <template #default="{ row }">
                  <el-tag
                    :type="row.priority === 'high' ? 'danger' : (row.priority === 'medium' ? 'warning' : 'info')"
                  >
                    {{ row.priority === 'high' ? '高' : (row.priority === 'medium' ? '中' : '低') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.status" size="small" @change="updateTaskStatus(row)">
                    <el-option label="待处理" value="pending"></el-option>
                    <el-option label="进行中" value="in_progress"></el-option>
                    <el-option label="已完成" value="completed"></el-option>
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180" sortable></el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button type="danger" size="small" @click="deleteTask(row.id)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer">
              <div class="selection-info" v-if="selectedTasks.length > 0">
                已选择 {{ selectedTasks.length }} 项
                <el-button type="danger" size="small" @click="batchDeleteTasks">批量删除</el-button>
              </div>
              <el-pagination
                v-model:current-page="taskPage"
                v-model:page-size="taskPageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="tasks.length"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleTaskSizeChange"
                @current-change="handleTaskCurrentChange"
              />
            </div>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除 <strong>{{ deleteItemName }}</strong> 吗？</p>
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
        <p>确定要删除选中的 <strong>{{ batchDeleteCount }} 项</strong> 吗？</p>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { adminAPI } from '@/api/index'
import apiClient from '@/api/client'
import { ElMessage } from 'element-plus'
import { Delete, WarningFilled } from '@element-plus/icons-vue'

// 数据
const messages = ref([])
const tasks = ref([])
const loading = ref(false)
const taskFilter = ref('all')
const messagePage = ref(1)
const messagePageSize = ref(20)
/** 消息总数（服务端 total）。别再拿 `messages.length` 当总数 —— 那只是当前这一页 */
const messageTotal = ref(0)
const taskPage = ref(1)
const taskPageSize = ref(20)
const selectedMessages = ref([])
const selectedTasks = ref([])
const deleteDialogVisible = ref(false)
const batchDeleteDialogVisible = ref(false)
const deleteLoading = ref(false)
const deleteItemName = ref('')
const deleteItemId = ref(null)
const deleteType = ref('')
const batchDeleteCount = ref(0)

const newMessage = ref({
  title: '',
  content: '',
  target: 'all',
  user_ids: []
})

const newTask = ref({
  title: '',
  description: '',
  priority: 'medium'
})

const users = ref([])

// 计算属性
const filteredTasks = computed(() => {
  let filtered = tasks.value
  if (taskFilter.value !== 'all') {
    filtered = filtered.filter(t => t.status === taskFilter.value)
  }
  const start = (taskPage.value - 1) * taskPageSize.value
  const end = start + taskPageSize.value
  return filtered.slice(start, end)
})

// 消息**不**做本地切片：`/admin/messages/list` 是服务端分页，自己也带 page/size。
// 原来两边各切一刀（服务端只回第 1 页 20 条，本地再按页码切这 20 条），
// 结果翻到第 2 页永远是空的，而卡片上「共 N 条」写的是**已取回的那一页的长度**，
// 不是库里的总数。现在总数用服务端回的 total，翻页由服务端做。

// 订阅实时消息
const subscribeToMessages = async () => {
  const userId = localStorage.getItem('userId')
  if (!userId) return
  
  const topic = `/user/${userId}/queue/messages`
  try {
    const { default: stompClient } = await import('@/utils/stompClient')
    await stompClient.subscribe(topic, (message) => {
      console.log('[AdminMessages] 收到实时消息:', message)
      handleNewMessage(message)
    })
    console.log(`[AdminMessages] 已订阅主题: ${topic}`)
  } catch (error) {
    console.error('[AdminMessages] 订阅失败:', error)
  }
}

// 消息类型文案。`messages.type` 是唯一存在的等级类字段，取值只有三处写入方：
//   1 —— message-service 广播时显式 setType(1)（`MessageController.sendBroadcast`）
//   3 —— 预警通知 setType(3)（`WarningNotificationServiceImpl`）
//   其他/0 —— 普通消息
// 不认识的取值原样报出来，不猜（原来那列把不认识的一律当"危急"）。
const messageTypeName = (type) => {
  if (type === 1) return '广播通知'
  if (type === 3) return '预警通知'
  if (type === 0 || type === null || type === undefined) return '普通消息'
  return `未知(${type})`
}

const messageTypeTag = (type) => {
  if (type === 1) return 'warning'
  if (type === 3) return 'danger'
  return 'info'
}

// 接收人姓名。广播是**逐用户写一行**（每个人自己的 receiver_id），所以同一标题会在
// 列表里出现 N 行 —— 这一列就是为了让"N 行"读得懂，而不是让人以为消息重复了。
// 名字从已加载的 /admin/users/all 里按 id 找；找不到就诚实地回落到 id，不编名字。
const receiverName = (receiverId) => {
  if (receiverId === null || receiverId === undefined) return '—'
  const u = users.value.find(item => item.id === receiverId)
  if (!u) return `用户 #${receiverId}`
  return u.name || u.username || `用户 #${receiverId}`
}

// 处理新消息
const handleNewMessage = (message) => {
  if (!message || !message.id) return
  
  const existingIndex = messages.value.findIndex(m => m.id === message.id)
  
  if (existingIndex >= 0) {
    messages.value[existingIndex] = { ...messages.value[existingIndex], ...message }
  } else {
    messages.value.unshift(message)
  }
  
  ElMessage.success('收到新消息')
}

onMounted(async () => {
  await loadUsers()
  await loadMessages()
  await loadTasks()
  subscribeToMessages()
})

onUnmounted(async () => {
  const userId = localStorage.getItem('userId')
  if (userId) {
    const topic = `/user/${userId}/queue/messages`
    try {
      const { default: stompClient } = await import('@/utils/stompClient')
      stompClient.unsubscribe(topic)
      console.log(`[AdminMessages] 已取消订阅主题: ${topic}`)
    } catch (error) {
      console.error('[AdminMessages] 取消订阅失败:', error)
    }
  }
})

// 加载用户列表
//
// `src/api/client.js` 的响应拦截器在 code===200 时**已经解开一层**，await 出来就是载荷本身。
// `/admin/users/all` 后端签名是 `ApiResponse<List<User>>`，所以这里拿到的是一个**数组**。
// 从前那两级 `response.data.code === 200` / `response.data` 永远为假（静默、无 else），
// 只有最后那个 `Array.isArray(response)` 兜底分支真的在工作 —— 三个分支里两个是死的，
// 还把整张用户表（含 id/username/role/status）打进了控制台。现在只留真实的那一条。
const loadUsers = async () => {
  try {
    const response = await apiClient.get('/admin/users/all')
    if (!Array.isArray(response)) {
      // 形状不对就说出来：不要再猜一个分支继续往下走，那正是它以前沉默的原因
      console.warn('[AdminMessages] /admin/users/all 返回的不是数组，收到的类型:', typeof response)
      users.value = []
      return
    }
    users.value = response
  } catch (error) {
    // code !== 200（例如 Sentinel 降级回的那句 degraded 文案）会被拦截器 reject 到这里
    console.error('加载用户列表失败:', error)
    users.value = []
  }
}

// 加载消息
//
// ⚠️ `/admin/messages/list` 回的是 `ApiResponse<Map>`，解开后是 `{total, items:[...]}` ——
// **不是数组**。所以原来的 `if (Array.isArray(response))` 恒为假，一条消息也不会渲染，
// 而且没有 else、没有提示：接口 200、控制台干净、页面写"暂无消息"。这里改读 `items`。
const loadMessages = async () => {
  loading.value = true
  try {
    const response = await adminAPI.getMessages(messagePage.value, messagePageSize.value)
    messages.value = (response && response.items) || []
    messageTotal.value = (response && response.total) || 0
  } catch (error) {
    ElMessage.error(error?.message ? `加载消息失败：${error.message}` : '加载消息失败')
  } finally {
    loading.value = false
  }
}

// 加载任务
// 2026-10-02（W1.11）：后端已改成真查 `admin_tasks` 表（此前是写死空列表的桩，
// 所以这里永远显示空 —— 那时这个空是后端的真话，不是前端读错键）。
// 页面的筛选与分页都是**本地**做的（顶部三个按钮 + 本地 `filteredTasks`），
// 所以这里一次取满服务端允许的上限（200），别用默认的 20 —— 否则第 21 条起
// 本地分页和右上角「共 N 个任务」都会少算。
const loadTasks = async () => {
  loading.value = true
  try {
    const response = await adminAPI.getTasks(1, 200)
    tasks.value = (response && response.items) || []
  } catch (error) {
    ElMessage.error(error?.message ? `加载任务失败：${error.message}` : '加载任务失败')
  } finally {
    loading.value = false
  }
}

// 发送消息
const sendMessage = async () => {
  if (!newMessage.value.title || !newMessage.value.content) {
    ElMessage.error('请输入消息标题和内容')
    return
  }

  try {
    if (newMessage.value.target === 'all') {
      await adminAPI.broadcastMessage(newMessage.value)
    } else {
      await adminAPI.sendTargetedMessage(newMessage.value)
    }
    ElMessage.success('消息已发送')
    resetMessageForm()
    await loadMessages()
  } catch (error) {
    // 后端失败时把**它的原话**带出来（例如"0/2 条写入成功"、"接收人不存在"），
    // 不再一律吞成一句"发送消息失败" —— 否则用户无法追问到底发给了谁
    ElMessage.error(error?.message ? `发送消息失败：${error.message}` : '发送消息失败')
  }
}

// 删除消息
const deleteMessage = (messageId) => {
  const message = messages.value.find(m => m.id === messageId)
  deleteItemName.value = message?.title || '消息'
  deleteItemId.value = messageId
  deleteType.value = 'message'
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  deleteLoading.value = true
  try {
    if (deleteType.value === 'message') {
      await adminAPI.deleteMessage(deleteItemId.value)
      ElMessage.success('消息已删除')
      messages.value = messages.value.filter(m => m.id !== deleteItemId.value)
      // 总数同步减一：否则删完之后右上角「共 N 条」还挂着已经不存在的那条
      messageTotal.value = Math.max(0, messageTotal.value - 1)
    } else if (deleteType.value === 'task') {
      await adminAPI.deleteTask(deleteItemId.value)
      ElMessage.success('任务已删除')
      tasks.value = tasks.value.filter(t => t.id !== deleteItemId.value)
    }
    deleteDialogVisible.value = false
    deleteItemId.value = null
    deleteType.value = ''
  } catch (error) {
    // 带上后端的原话：删除接口失败时会说明是"消息不存在"还是"下游服务不可用"
    ElMessage.error(error?.message ? `删除失败：${error.message}` : '删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const handleMessageSelectionChange = (selection) => {
  selectedMessages.value = selection
}

const handleTaskSelectionChange = (selection) => {
  selectedTasks.value = selection
}

const batchDeleteMessages = () => {
  if (selectedMessages.value.length === 0) {
    ElMessage.warning('请先选择要删除的消息')
    return
  }
  batchDeleteCount.value = selectedMessages.value.length
  deleteType.value = 'message'
  batchDeleteDialogVisible.value = true
}

const batchDeleteTasks = () => {
  if (selectedTasks.value.length === 0) {
    ElMessage.warning('请先选择要删除的任务')
    return
  }
  batchDeleteCount.value = selectedTasks.value.length
  deleteType.value = 'task'
  batchDeleteDialogVisible.value = true
}

const confirmBatchDelete = async () => {
  deleteLoading.value = true
  try {
    const isMessage = deleteType.value === 'message'
    const selected = isMessage ? selectedMessages.value : selectedTasks.value
    const call = isMessage
      ? (id) => adminAPI.deleteMessage(id)
      : (id) => adminAPI.deleteTask(id)

    // 用 allSettled 而不是 all：原来只要有一条失败，Promise.all 立刻 reject，
    // 于是 ① 一条成功记录都不报、② 本地列表**一条都不移除**。
    // 结果就是"库里已经删掉的几行还留在页面上"，而用户收到一句"批量删除失败"。
    // 现在逐条对账：成功的从列表里移除，失败的把后端原话带出来。
    const settled = await Promise.allSettled(selected.map(x => call(x.id)))
    const okIds = new Set()
    const failures = []
    settled.forEach((r, i) => {
      if (r.status === 'fulfilled') {
        okIds.add(selected[i].id)
      } else {
        failures.push(`${selected[i].id}: ${r.reason?.message || r.reason}`)
      }
    })

    if (isMessage) {
      messages.value = messages.value.filter(m => !okIds.has(m.id))
      messageTotal.value = Math.max(0, messageTotal.value - okIds.size)
      selectedMessages.value = []
    } else {
      tasks.value = tasks.value.filter(t => !okIds.has(t.id))
      selectedTasks.value = []
    }

    if (failures.length === 0) {
      ElMessage.success(`成功删除 ${okIds.size} ${isMessage ? '条消息' : '个任务'}`)
    } else if (okIds.size === 0) {
      ElMessage.error(`全部 ${failures.length} 条都删除失败：${failures[0]}`)
    } else {
      ElMessage.warning(`成功 ${okIds.size} 条，失败 ${failures.length} 条：${failures[0]}`)
    }
    batchDeleteDialogVisible.value = false
    deleteType.value = ''
  } catch (error) {
    ElMessage.error(error?.message ? `批量删除失败：${error.message}` : '批量删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 创建任务
const createTask = async () => {
  if (!newTask.value.title) {
    ElMessage.error('请输入任务标题')
    return
  }

  try {
    await adminAPI.createTask(newTask.value)
    ElMessage.success('任务已创建')
    resetTaskForm()
    await loadTasks()
  } catch (error) {
    ElMessage.error(error?.message ? `创建任务失败：${error.message}` : '创建任务失败')
  }
}

// 更新任务状态
// `@change` 触发时 `row.status` 已经被 v-model 改成新值了 —— 后端拒绝的话
// 这个下拉会停在"改成功了"的样子上（比如那条任务已被别人删掉，后端回「任务不存在」）。
// 所以失败时重新拉一次，让控件回到库里的真实状态，而不是留个假象。
const updateTaskStatus = async (task) => {
  try {
    await adminAPI.updateTaskStatus(task.id, task.status)
    ElMessage.success('任务状态已更新')
  } catch (error) {
    ElMessage.error(error?.message ? `更新任务状态失败：${error.message}` : '更新任务状态失败')
    await loadTasks()
  }
}

// 删除任务
const deleteTask = (taskId) => {
  const task = tasks.value.find(t => t.id === taskId)
  deleteItemName.value = task?.title || '任务'
  deleteItemId.value = taskId
  deleteType.value = 'task'
  deleteDialogVisible.value = true
}

// 重置表单
const resetMessageForm = () => {
  newMessage.value = {
    title: '',
    content: '',
    target: 'all',
    user_ids: []
  }
}

const resetTaskForm = () => {
  newTask.value = {
    title: '',
    description: '',
    priority: 'medium'
  }
}

// 分页处理
// 改页码/页大小都要**回服务端取**：数据在服务端分页，本地没有第 2 页可切
const handleMessageSizeChange = (val) => {
  messagePageSize.value = val
  messagePage.value = 1
  loadMessages()
}

const handleMessageCurrentChange = (val) => {
  messagePage.value = val
  loadMessages()
}

const handleTaskSizeChange = (val) => {
  taskPageSize.value = val
  taskPage.value = 1
}

const handleTaskCurrentChange = (val) => {
  taskPage.value = val
}
</script>

<style scoped>
.admin-messages {
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

.message-count, .task-count {
  font-size: 14px;
  color: #909399;
  font-weight: normal;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 15px;
}

.stat-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.stat-title {
  color: #999;
  font-size: 12px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
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

.admin-messages :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-messages :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-messages :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-messages :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-messages :deep(.el-button--danger:hover) {
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

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>