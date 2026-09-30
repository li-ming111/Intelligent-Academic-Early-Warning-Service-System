<template>
  <div class="cn-page">
    <div class="cn-head">
      <h1>通知中心</h1>
      <p>向学生发送通知，并查看发送历史</p>
    </div>

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
          <el-table-column prop="createdAt" label="发送时间" width="180" />
        </el-table>

        <div v-if="historyNote" class="cn-note">{{ historyNote }}</div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { counselorAPI } from '@/api/index'

const typeOptions = [
  { value: 'warning', label: '预警通知' },
  { value: 'important', label: '重要通知' },
  { value: 'normal', label: '普通通知' }
]

const form = ref({ type: 'normal', scope: 'all', title: '', content: '' })
const history = ref([])
const historyTotal = ref(0)
const historyNote = ref('')
const sending = ref(false)

// `counselorId` 这个 key 全仓没有任何地方写过，实际只有 userId 可用（classes.counselor_id 存的也是 users.id）
const getCounselorId = () => {
  const uid = localStorage.getItem('counselorId') || localStorage.getItem('userId')
  return uid ? parseInt(uid) : null
}

const typeLabel = (v) => (typeOptions.find(t => t.value === v) || {}).label || v

onMounted(loadHistory)

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

const sendNotification = async () => {
  if (!getCounselorId()) { ElMessage.error('未取到当前辅导员身份，请重新登录'); return }
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
.cn-card-title { font-size: 15px; font-weight: 600; color: #1e293b; }
.cn-count { margin-left: 12px; font-size: 13px; color: #94a3b8; font-weight: normal; }
.cn-hint { margin-left: 12px; font-size: 12px; color: #94a3b8; }
.cn-content { white-space: pre-line; font-size: 13px; color: #334155; }
.cn-note { margin-top: 12px; font-size: 12px; color: #94a3b8; }
:deep(.el-card) { border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
:deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid #f1f5f9; }
:deep(.el-card__body) { padding: 20px; }
</style>
