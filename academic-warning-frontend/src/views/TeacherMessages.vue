<template>
  <div class="tmsg-page">

    <!-- 头部 -->
    <div class="tmsg-head">
      <div class="tmh-left">
        <div class="tmh-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></div>
        <div>
          <h2>消息中心</h2>
          <p>接收辅导员私信与系统通知</p>
        </div>
      </div>
      <div class="tmh-right">
        <span class="tmh-badge red" v-if="unreadCount">{{ unreadCount }}条未读</span>
        <button class="tmh-btn ghost" v-if="unreadCount" @click="markAllRead" :disabled="loading">全部已读</button>
        <button class="tmh-btn" @click="refreshMessages" :disabled="loading">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>
          刷新
        </button>
      </div>
    </div>

    <!-- 筛选 + 搜索 -->
    <div class="tmsg-bar">
      <div class="tmb-tabs">
        <button
          v-for="t in filterTabs"
          :key="t.key"
          class="tmbt"
          :class="{ active: filterStatus === t.key }"
          @click="filterStatus = t.key; currentPage = 1"
        >
          {{ t.label }}<span class="tmbt-cnt" v-if="t.count">{{ t.count }}</span>
        </button>
      </div>
      <div class="tmb-search">
        <el-input v-model="searchKeyword" placeholder="搜索内容或发送人" clearable :prefix-icon="Search" style="width:260px" @input="currentPage=1" />
      </div>
    </div>

    <!-- 消息列表 -->
    <div class="tmsg-list" v-if="pagedMessages.length">
      <div
        v-for="(m, i) in pagedMessages"
        :key="m.id"
        class="tmsg-card"
        :class="{ unread: m.status === 0 }"
        :style="{ animationDelay: i * 0.04 + 's' }"
        @click="openMessage(m)"
      >
        <div class="tmc-bar" :class="m.status === 0 ? 'unread' : ''"></div>
        <div class="tmc-avatar">{{ (m.senderName || '系').charAt(0) }}</div>
        <div class="tmc-body">
          <div class="tmcb-top">
            <span class="tmcb-name">{{ m.senderName || '系统' }}</span>
            <span class="tmcb-tag" :class="typeCls(m.type)">{{ typeLbl(m.type) }}</span>
            <span class="tmcb-dot" v-if="m.status === 0"></span>
          </div>
          <p class="tmcb-title" v-if="m.title">{{ m.title }}</p>
          <p class="tmcb-content">{{ m.content }}</p>
          <div class="tmcb-meta">
            <span>{{ fmtTime(m.createdAt) }}</span>
          </div>
        </div>
        <div class="tmc-actions">
          <button class="tmca-btn primary" v-if="canReply(m)" @click.stop="openReply(m)">回复</button>
          <button class="tmca-btn" v-if="m.status === 0" @click.stop="markOne(m)">标记已读</button>
          <span v-else class="tmc-read-tag">已读</span>
        </div>
      </div>

      <!-- 分页 -->
      <div class="tmsg-pager" v-if="totalPages > 1">
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="filteredMessages.length" layout="prev, pager, next" small />
      </div>
    </div>

    <!-- 空 -->
    <div class="tmsg-empty" v-else>
      <div class="tmge-icon">📭</div>
      <p>{{ loading ? '加载中...' : '暂无消息' }}</p>
    </div>

    <!-- 消息详情 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="520px" destroy-on-close>
      <div v-if="detailMessage" class="tm-detail">
        <div class="tmd-meta">
          <span class="tmd-sender">{{ detailMessage.senderName || '系统' }}</span>
          <span class="tmcb-tag" :class="typeCls(detailMessage.type)">{{ typeLbl(detailMessage.type) }}</span>
          <span class="tmd-time">{{ fmtTime(detailMessage.createdAt) }}</span>
        </div>
        <h3 v-if="detailMessage.title" class="tmd-title">{{ detailMessage.title }}</h3>
        <p class="tmd-content">{{ detailMessage.content }}</p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="canReply(detailMessage)" type="primary" @click="replyFromDetail">回复辅导员</el-button>
      </template>
    </el-dialog>

    <!-- 回复辅导员 -->
    <el-dialog v-model="replyVisible" title="回复辅导员" width="520px" destroy-on-close>
      <div v-if="replyTarget" class="tm-reply">
        <div class="tmr-quote">
          <span class="tmr-quote-name">{{ replyTarget.senderName || '辅导员' }}</span>
          <p class="tmr-quote-content">{{ replyTarget.content }}</p>
        </div>
        <el-input
          v-model="replyContent"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          placeholder="输入回复内容"
        />
      </div>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { teacherAPI } from '@/api/index'
import { subscribeMyMessages, unsubscribeMyMessages } from '@/utils/realtimeMessages'

const messages = ref([])
// 未读数直接从本页已加载的消息里算，不再单独请求 unread-count
// （Layout 的路由 watch 已经打了一次，同页再打就是重复请求）。
const unreadCount = computed(() => messages.value.filter(m => m.status === 0).length)
const loading = ref(false)
const filterStatus = ref('all')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const detailMessage = ref(null)
const replyVisible = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const replying = ref(false)

// 数据源是 messages 表：status 0=未读 / 1=已读，
// 与学生反馈的 0=未回复/1=已回复不是一套口径，别把两个页面的状态名混用。
const filterTabs = computed(() => [
  { key: 'all', label: '全部', count: messages.value.length },
  { key: 'unread', label: '未读', count: messages.value.filter(m => m.status === 0).length },
  { key: 'read', label: '已读', count: messages.value.filter(m => m.status !== 0).length }
])

const filteredMessages = computed(() => {
  let list = [...messages.value]
  if (filterStatus.value === 'unread') list = list.filter(m => m.status === 0)
  if (filterStatus.value === 'read') list = list.filter(m => m.status !== 0)
  const kw = searchKeyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(m =>
      (m.content || '').toLowerCase().includes(kw) ||
      (m.senderName || '').toLowerCase().includes(kw) ||
      (m.title || '').toLowerCase().includes(kw)
    )
  }
  return list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
})

const pagedMessages = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredMessages.value.slice(start, start + pageSize.value)
})

const totalPages = computed(() => Math.ceil(filteredMessages.value.length / pageSize.value))

function typeLbl(t) {
  if (t === 2) return '辅导员私信'
  if (t === 1) return '系统通知'
  return '消息'
}
function typeCls(t) {
  if (t === 2) return 'tg-f'
  if (t === 1) return 'tg-s'
  return 'tg-o'
}

onMounted(async () => {
  await loadMessages()
  subscribeMyMessages(onRealtimeMessage)
})

onUnmounted(() => {
  unsubscribeMyMessages()
})

async function onRealtimeMessage(payload) {
  const content = payload && (payload.content || payload.title)
  ElMessage.success(content ? `收到新消息：${content}` : '收到新消息')
  await loadMessages()
}

async function loadMessages() {
  loading.value = true
  try {
    const r = await teacherAPI.getInbox()
    messages.value = Array.isArray(r) ? r : (r?.data || [])
  } catch (e) {
    console.error('加载消息失败:', e)
    ElMessage.error('消息加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshMessages() {
  await loadMessages()
  ElMessage.success('已刷新')
}

function openMessage(m) {
  detailMessage.value = m
  detailVisible.value = true
  markOne(m)
}

// 只有辅导员发来的私信可以回复：type=2 且发送方 role=3。
// 管理员广播（type=1）不走这条 —— 回复它没有人接收。
function canReply(m) {
  return !!m && m.type === 2 && m.senderRole === 3
}

function openReply(m) {
  if (!canReply(m)) return
  replyTarget.value = m
  replyContent.value = ''
  replyVisible.value = true
}

function replyFromDetail() {
  const m = detailMessage.value
  detailVisible.value = false
  openReply(m)
}

async function submitReply() {
  if (!replyTarget.value) return
  const content = replyContent.value.trim()
  if (!content) {
    ElMessage.error('请填写回复内容')
    return
  }
  replying.value = true
  try {
    await teacherAPI.replyInboxMessage(replyTarget.value.id, content)
    ElMessage.success('回复已发送')
    replyVisible.value = false
    replyTarget.value = null
    replyContent.value = ''
    await loadMessages()
  } catch (e) {
    console.error('回复失败:', e)
    ElMessage.error('回复失败: ' + (e.message || '网络错误'))
  } finally {
    replying.value = false
  }
}

async function markOne(m) {
  if (!m || m.status !== 0) return
  try {
    await teacherAPI.markInboxRead(m.id)
    m.status = 1
  } catch (e) {
    console.error('标记已读失败:', e)
    ElMessage.error('标记已读失败')
  }
}

async function markAllRead() {
  try {
    await teacherAPI.markAllInboxRead()
    messages.value.forEach(m => { m.status = 1 })
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    console.error('全部已读失败:', e)
    ElMessage.error('操作失败')
  }
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t); const n = new Date(); const diff = n - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.tmsg-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }

/* 头部 */
.tmsg-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.tmh-left { display: flex; align-items: center; gap: 14px; }
.tmh-icon { width: 48px; height: 48px; border-radius: 12px; background: #f5f3ff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.tmh-left h2 { margin: 0; font-size: 20px; color: #18181b; font-weight: 700; }
.tmh-left p { margin: 2px 0 0; font-size: 13px; color: #a1a1aa; }
.tmh-right { display: flex; align-items: center; gap: 10px; }
.tmh-badge { font-size: 12px; padding: 4px 12px; border-radius: 12px; font-weight: 600; }
.tmh-badge.red { background: #fef2f2; color: #ef4444; }
.tmh-btn { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 8px; border: 1px solid #e4e4e7; background: #fff; color: #52525b; font-size: 13px; cursor: pointer; transition: all .2s; }
.tmh-btn:hover:not(:disabled) { border-color: #d4d4d8; background: #fafafa; }
.tmh-btn.ghost { color: #7c3aed; border-color: #ddd6fe; background: #f5f3ff; }
.tmh-btn:disabled { opacity: .4; cursor: default; }

/* 筛选条 */
.tmsg-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tmb-tabs { display: flex; gap: 8px; }
.tmbt { padding: 8px 18px; border-radius: 20px; border: 1px solid #e4e4e7; background: #fff; font-size: 13px; color: #71717a; cursor: pointer; transition: all .2s; display: flex; align-items: center; gap: 6px; }
.tmbt:hover { border-color: #a78bfa; color: #7c3aed; }
.tmbt.active { background: #7c3aed; color: #fff; border-color: #7c3aed; }
.tmbt-cnt { font-size: 11px; padding: 1px 7px; border-radius: 10px; background: rgba(0,0,0,.06); }
.tmbt.active .tmbt-cnt { background: rgba(255,255,255,.2); }

/* 列表 */
.tmsg-list { display: flex; flex-direction: column; gap: 10px; }
.tmsg-card { display: flex; align-items: center; gap: 14px; background: #fff; border-radius: 12px; padding: 16px 18px; box-shadow: 0 1px 3px rgba(0,0,0,.03); transition: all .25s; animation: fadeUp .4s ease both; position: relative; overflow: hidden; cursor: pointer; }
.tmsg-card:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.tmsg-card.unread { background: #faf8ff; }
.tmc-bar { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: transparent; }
.tmc-bar.unread { background: #7c3aed; }
.tmc-avatar { width: 44px; height: 44px; border-radius: 12px; background: linear-gradient(135deg, #818cf8, #4f46e5); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 17px; font-weight: 700; flex-shrink: 0; }
.tmsg-card:nth-child(2) .tmc-avatar { background: linear-gradient(135deg, #60a5fa, #2563eb); }
.tmsg-card:nth-child(3) .tmc-avatar { background: linear-gradient(135deg, #34d399, #059669); }
.tmsg-card:nth-child(4) .tmc-avatar { background: linear-gradient(135deg, #fbbf24, #d97706); }
.tmsg-card:nth-child(5) .tmc-avatar { background: linear-gradient(135deg, #f472b6, #db2777); }
.tmc-body { flex: 1; min-width: 0; }
.tmcb-top { display: flex; align-items: center; gap: 8px; }
.tmcb-name { font-size: 14px; font-weight: 600; color: #18181b; }
.tmcb-tag { font-size: 11px; padding: 2px 8px; border-radius: 8px; font-weight: 600; }
.tg-w { background: #fef2f2; color: #ef4444; }
.tg-f { background: #eff6ff; color: #2563eb; }
.tg-s { background: #ecfdf5; color: #16a34a; }
.tg-q { background: #eff6ff; color: #2563eb; }
.tg-o { background: #f4f4f5; color: #71717a; }
.tmcb-dot { width: 7px; height: 7px; border-radius: 50%; background: #7c3aed; }
.tmcb-title { margin: 6px 0 0; font-size: 13px; font-weight: 600; color: #3f3f46; }
.tmcb-content { margin: 6px 0; font-size: 13px; color: #71717a; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.tmcb-meta { font-size: 11px; color: #a1a1aa; }
.tmc-actions { display: flex; gap: 8px; flex-shrink: 0; align-items: center; }
.tmca-btn { padding: 6px 14px; border-radius: 7px; border: 1px solid #e4e4e7; background: #fff; font-size: 12px; cursor: pointer; transition: all .2s; font-weight: 500; color: #52525b; }
.tmca-btn:hover { border-color: #a78bfa; color: #7c3aed; }
.tmca-btn.primary { background: #7c3aed; border-color: #7c3aed; color: #fff; }
.tmca-btn.primary:hover { background: #6d28d9; border-color: #6d28d9; color: #fff; }
.tmc-read-tag { font-size: 12px; color: #a1a1aa; }

.tmsg-pager { display: flex; justify-content: center; padding: 20px 0; }

/* 空 */
.tmsg-empty { text-align: center; padding: 80px 20px; }
.tmge-icon { font-size: 48px; margin-bottom: 12px; }
.tmsg-empty p { font-size: 15px; color: #a1a1aa; }

/* 详情 */
.tm-detail { padding: 2px; }
.tmd-meta { display: flex; align-items: center; gap: 10px; font-size: 12px; color: #a1a1aa; }
.tmd-sender { font-size: 14px; font-weight: 600; color: #18181b; }
.tmd-time { margin-left: auto; }
.tmd-title { margin: 14px 0 8px; font-size: 15px; color: #18181b; }
.tmd-content { margin: 12px 0 0; font-size: 14px; line-height: 1.7; color: #3f3f46; white-space: pre-wrap; word-break: break-word; }

/* 回复 */
.tm-reply { display: flex; flex-direction: column; gap: 14px; }
.tmr-quote { padding: 10px 14px; border-left: 3px solid #c4b5fd; background: #faf8ff; border-radius: 6px; }
.tmr-quote-name { font-size: 12px; font-weight: 600; color: #7c3aed; }
.tmr-quote-content { margin: 4px 0 0; font-size: 13px; color: #52525b; line-height: 1.5; white-space: pre-wrap; word-break: break-word; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }

@keyframes fadeUp { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 640px) {
  .tmsg-head { flex-direction: column; align-items: flex-start; gap: 12px; }
  .tmsg-bar { flex-direction: column; gap: 10px; align-items: flex-start; }
  .tmb-search { width: 100%; }
  .tmsg-card { flex-wrap: wrap; }
  .tmc-actions { width: 100%; justify-content: flex-end; }
}
</style>
