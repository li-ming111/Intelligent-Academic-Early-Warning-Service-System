<template>
  <div class="tmsg-page">

    <!-- 头部 -->
    <div class="tmsg-head">
      <div class="tmh-left">
        <div class="tmh-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></div>
        <div>
          <h2>消息中心</h2>
          <p>管理学生沟通与反馈</p>
        </div>
      </div>
      <div class="tmh-right">
        <span class="tmh-badge red" v-if="unreadCount">{{ unreadCount }}条待回复</span>
        <button class="tmh-btn" @click="refreshMessages" :disabled="loading">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>
          刷新
        </button>
        <!-- 「全部已读」已删（2026-09-30）：见下方 markAsRead 的说明 —— 数据源 feedback
             只有「已回复/未回复」，没有「已读」这个状态可落库。 -->
      </div>
    </div>

    <!-- 筛选 + 搜索 -->
    <div class="tmsg-bar">
      <div class="tmb-tabs">
        <button v-for="t in filterTabs" :key="t.key" class="tmbt" :class="{ active: filterStatus === t.key }" @click="filterStatus = t.key">
          {{ t.label }}<span class="tmbt-cnt" v-if="t.count">{{ t.count }}</span>
        </button>
      </div>
      <div class="tmb-search">
        <el-input v-model="searchKeyword" placeholder="搜索内容或发送者" clearable :prefix-icon="Search" style="width:260px" @input="currentPage=1" />
      </div>
    </div>

    <!-- 消息列表 -->
    <div class="tmsg-list" v-if="pagedMessages.length">
      <div v-for="(m, i) in pagedMessages" :key="m.id" class="tmsg-card" :class="{ unread: m.status === 0 }" :style="{ animationDelay: i * 0.04 + 's' }">
        <div class="tmc-bar" :class="m.status === 0 ? 'unread' : ''"></div>
        <div class="tmc-avatar">
          {{ (m.senderName || '学').charAt(0) }}
        </div>
        <div class="tmc-body" @click="toggleExpand(m.id)">
          <div class="tmcb-top">
            <span class="tmcb-name">{{ m.senderName || '学生' }}</span>
            <span class="tmcb-tag" :class="typeCls(m.type)">{{ typeLbl(m.type) }}</span>
            <span class="tmcb-dot" v-if="m.status === 0"></span>
          </div>
          <p class="tmcb-content">{{ m.content }}</p>
          <p class="tmcb-reply" v-if="m.reply"><span class="tcr-label">回复</span>{{ m.reply }}</p>
          <div class="tmcb-meta">
            <span>{{ fmtTime(m.createdAt) }}</span>
          </div>
        </div>
        <div class="tmc-actions">
          <button class="tmca-btn reply" @click.stop="showReply(m)">{{ m.status === 0 ? '回复' : '重新回复' }}</button>
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
      <p>暂无消息</p>
    </div>

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" :title="'回复 ' + (replyTarget?.senderName || '学生')" width="460px" destroy-on-close>
      <div class="tm-reply-prev" v-if="replyTarget">
        <span class="trp-label">原始消息</span>
        <p class="trp-text">{{ replyTarget.content }}</p>
      </div>
      <el-input v-model="replyForm.content" type="textarea" rows="4" placeholder="输入回复内容..." maxlength="500" show-word-limit style="margin-top:14px" />
      <template #footer>
        <el-button @click="replyVisible=false">取消</el-button>
        <el-button type="primary" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { teacherAPI } from '@/api/index'

const messages = ref([])
const unreadCount = ref(0)
const loading = ref(false)
const filterStatus = ref('all')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const replyVisible = ref(false)
const replyTarget = ref(null)
const replyForm = ref({ content: '' })

// 标签说的是**数据里真的有的状态**。数据源是 `feedback` 表，只有
// status 0=未回复 / 1=已回复（`FeedbackMapper.replyFeedback` 回帖时置 1），
// **没有「已读」这一列** —— 所以原来那对「未读/已读」标签是把"回复状态"叫成了"阅读状态"，
// 而「已读」按钮调的 `POST /teachers/messages/{id}/mark-read` 又是个空壳（不写库）。
// 2026-09-30 改成按真实语义命名。
const filterTabs = computed(() => [
  { key: 'all', label: '全部', count: messages.value.length },
  { key: 'unread', label: '待回复', count: messages.value.filter(m => m.status === 0).length },
  { key: 'read', label: '已回复', count: messages.value.filter(m => m.status === 1 || m.status === 2).length }
])

const filteredMessages = computed(() => {
  let list = [...messages.value]
  if (filterStatus.value === 'unread') list = list.filter(m => m.status === 0)
  if (filterStatus.value === 'read') list = list.filter(m => m.status === 1 || m.status === 2)
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(m => (m.content || '').toLowerCase().includes(kw) || (m.senderName || '').toLowerCase().includes(kw))
  }
  return list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
})

const pagedMessages = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredMessages.value.slice(start, start + pageSize.value)
})

const totalPages = computed(() => Math.ceil(filteredMessages.value.length / pageSize.value))

function typeLbl(t) {
  const m = { APPEAL: '申诉', FEEDBACK: '反馈', SYSTEM: '系统', question: '提问', feedback: '反馈' }
  return m[t] || '消息'
}
function typeCls(t) {
  const m = { APPEAL: 'tg-w', FEEDBACK: 'tg-f', SYSTEM: 'tg-s', question: 'tg-q', feedback: 'tg-f' }
  return m[t] || 'tg-o'
}

onMounted(async () => {
  await loadMessages()
  await loadUnread()
})

async function loadMessages() {
  loading.value = true
  try {
    const uid = localStorage.getItem('userId'); if (!uid) return
    const r = await teacherAPI.getMessages(uid)
    messages.value = Array.isArray(r) ? r : (r?.data || [])
  } catch (e) { console.error(e) } finally { loading.value = false }
}
async function loadUnread() {
  try {
    const uid = localStorage.getItem('userId'); if (!uid) return
    const r = await teacherAPI.getUnreadCount(uid)
    unreadCount.value = typeof r === 'number' ? r : 0
  } catch (e) {}
}
async function refreshMessages() {
  await loadMessages()
  await loadUnread()
  ElMessage.success('已刷新')
}
// 「标记已读」整段已删（2026-09-30）。它做的两件事都是假的：
//   ① 调的 `POST /teachers/messages/{id}/mark-read` 是个**空壳** ——
//      `TeacherController.markMessageAsRead` 只有一行 `return ApiResponse.success("消息已标记为已读")`，
//      不碰任何表；
//   ② 本地 `m.status = 1` 把"回复状态"改成了"已回复"，等于**点一下未回复的反馈就把它标成已回复**，
//      刷新后 `unread-count` 又从库里算回来 —— 界面与库各说各话。
// 数据源 `feedback` 表根本没有"已读"这一列（全库 5 个 dump 里都没有），
// 所以这个动作**没有地方可落**。按本仓既有的口径（见 AdminUsers 的「查看密码」），
// 删掉"看起来能用"的死按钮，比补一个只能骗人的接口诚实。
function toggleExpand() {}
function showReply(m) {
  replyTarget.value = m
  // 已经回过就把原文填回去（回帖是**覆盖**写 `feedback.reply`，不是追加），
  // 否则"重新回复"会把原有内容在整个界面上抹掉，看着像丢了。
  replyForm.value = { content: m.reply || '' }
  replyVisible.value = true
}
async function submitReply() {
  const content = replyForm.value.content.trim()
  if (!content) return ElMessage.warning('请输入回复内容')
  if (!replyTarget.value) return
  try {
    // 真写库。2026-09-30 之前这里**只改本地数组**（`m.reply = ...; m.status = 1`）
    // 然后弹「已回复」—— 一个请求都不发，刷新页面回复就没了，而学生那边
    // 永远看不到任何回复。接口一直是有的：`POST /teachers/feedbacks/{id}/reply`
    // → `FeedbackMapper.replyFeedback`（`UPDATE feedback SET reply=?, status=1, update_time=NOW()`）。
    // 注：`teacherAPI.replyFeedback` 与 `replyToFeedback` 现在打的是**同一路径**
    //（`replyFeedback` 从前打的是 `PUT /teachers/feedbacks/{id}`，后端没这个端点，
    //  2026-09-30 已一并修正），用哪个都行。
    await teacherAPI.replyToFeedback(replyTarget.value.id, { reply: content })
    // 写完之后**重新拉一次**，页面上显示的是库里的那一行，而不是本地拼的
    await loadMessages()
    await loadUnread()
    replyVisible.value = false
    ElMessage.success('已回复')
  } catch (e) {
    console.error('回复失败:', e)
    ElMessage.error(e?.message || '回复失败')
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
.tmsg-card { display: flex; align-items: center; gap: 14px; background: #fff; border-radius: 12px; padding: 16px 18px; box-shadow: 0 1px 3px rgba(0,0,0,.03); transition: all .25s; animation: fadeUp .4s ease both; position: relative; overflow: hidden; }
.tmsg-card:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.tmsg-card.unread { background: #faf8ff; }
.tmc-bar { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: transparent; }
.tmc-bar.unread { background: #7c3aed; }
.tmc-avatar { width: 44px; height: 44px; border-radius: 12px; background: linear-gradient(135deg, #818cf8, #4f46e5); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 17px; font-weight: 700; flex-shrink: 0; }
.tmsg-card:nth-child(2) .tmc-avatar { background: linear-gradient(135deg, #60a5fa, #2563eb); }
.tmsg-card:nth-child(3) .tmc-avatar { background: linear-gradient(135deg, #34d399, #059669); }
.tmsg-card:nth-child(4) .tmc-avatar { background: linear-gradient(135deg, #fbbf24, #d97706); }
.tmsg-card:nth-child(5) .tmc-avatar { background: linear-gradient(135deg, #f472b6, #db2777); }
.tmc-body { flex: 1; min-width: 0; cursor: pointer; }
.tmcb-top { display: flex; align-items: center; gap: 8px; }
.tmcb-name { font-size: 14px; font-weight: 600; color: #18181b; }
.tmcb-tag { font-size: 11px; padding: 2px 8px; border-radius: 8px; font-weight: 600; }
.tg-w { background: #fef2f2; color: #ef4444; }
.tg-f { background: #fffbeb; color: #d97706; }
.tg-s { background: #ecfdf5; color: #16a34a; }
.tg-q { background: #eff6ff; color: #2563eb; }
.tg-o { background: #f4f4f5; color: #71717a; }
.tmcb-dot { width: 7px; height: 7px; border-radius: 50%; background: #7c3aed; }
.tmcb-content { margin: 6px 0; font-size: 13px; color: #71717a; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.tmcb-meta { font-size: 11px; color: #a1a1aa; }
.tmcb-reply { margin: 6px 0 0; padding: 7px 10px; font-size: 12px; color: #3f3f46; line-height: 1.5; background: #f4f4f5; border-left: 2px solid #a1a1aa; border-radius: 0 6px 6px 0; }
.tcr-label { margin-right: 6px; color: #71717a; font-weight: 600; }
.tmc-actions { display: flex; gap: 8px; flex-shrink: 0; }
.tmca-btn { padding: 6px 14px; border-radius: 7px; border: 1px solid #e4e4e7; background: #fff; font-size: 12px; cursor: pointer; transition: all .2s; font-weight: 500; }
.tmca-btn:hover { border-color: #a78bfa; color: #7c3aed; }
.tmca-btn.read { color: #16a34a; border-color: #bbf7d0; }
.tmca-btn.reply { color: #fff; background: #7c3aed; border-color: #7c3aed; }
.tmca-btn.reply:hover { background: #6d28d9; }

.tmsg-pager { display: flex; justify-content: center; padding: 20px 0; }

/* 空 */
.tmsg-empty { text-align: center; padding: 80px 20px; }
.tmge-icon { font-size: 48px; margin-bottom: 12px; }
.tmsg-empty p { font-size: 15px; color: #a1a1aa; }

/* 回复弹窗 */
.tm-reply-prev { background: #f8f7fc; padding: 12px; border-radius: 8px; margin-bottom: 4px; }
.trp-label { font-size: 12px; color: #a1a1aa; }
.trp-text { margin: 6px 0 0; font-size: 14px; color: #3f3f46; line-height: 1.5; }

@keyframes fadeUp { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 640px) {
  .tmsg-head { flex-direction: column; align-items: flex-start; gap: 12px; }
  .tmsg-bar { flex-direction: column; gap: 10px; align-items: flex-start; }
  .tmb-search { width: 100%; }
  .tmsg-card { flex-wrap: wrap; }
  .tmc-actions { width: 100%; justify-content: flex-end; }
}
</style>
