<template>
  <div class="msg-page">

    <!-- 头部 -->
    <div class="msg-head">
      <div class="mgh-left">
        <div class="mgh-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></div>
        <div>
          <h2>消息中心</h2>
          <p>查看与教师的沟通记录</p>
        </div>
      </div>
      <div class="mgh-right">
        <span class="mgh-badge" v-if="unreadCount">{{ unreadCount }}条未读</span>
        <button class="mgh-btn" @click="refreshAll">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>
          刷新
        </button>
        <button class="mgh-btn primary" @click="openSend">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
          发送消息
        </button>
      </div>
    </div>

    <!-- 筛选条 -->
    <div class="msg-filter">
      <button v-for="t in filterTabs" :key="t.key" class="mft" :class="{ active: filter === t.key }" @click="filter = t.key">
        {{ t.label }}
        <span class="mft-count" v-if="t.count > 0">{{ t.count }}</span>
      </button>
    </div>

    <!-- 消息列表 -->
    <div class="msg-list" v-if="filtered.length">
      <div v-for="(m, i) in filtered" :key="m.id" class="msg-card" :class="{ unread: m.status === 0 }" :style="{ animationDelay: i * 0.04 + 's' }" @click="viewDetail(m)">
        <div class="mc-bar" :class="m.status === 0 ? 'unread' : ''"></div>
        <div class="mc-avatar" :style="{ background: avatarColors[(m.senderName || '系').charCodeAt(0) % avatarColors.length] }">
          {{ (m.senderName || m.teacherName || '系').charAt(0) }}
        </div>
        <div class="mc-body">
          <div class="mcb-top">
            <span class="mcb-sender">{{ m.senderName || m.teacherName || '系统' }}</span>
            <span class="mcb-type" :class="typeClass(m.type)">{{ typeLabel(m.type) }}</span>
            <span class="mcb-unread" v-if="m.status === 0"></span>
          </div>
          <p class="mcb-content">{{ m.content }}</p>
          <div class="mcb-meta">
            <span class="mcb-time">{{ fmtTime(m.createdAt) }}</span>
            <span class="mcb-reply" v-if="m.reply">已回复</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空 -->
    <div class="msg-empty" v-else>
      <div class="mge-icon">📭</div>
      <p>暂无消息</p>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="500px" destroy-on-close>
      <div v-if="selectedMsg" class="msg-det">
        <div class="md-item">
          <span class="md-label">发送者</span>
          <span class="md-val">{{ selectedMsg.senderName || selectedMsg.teacherName || '系统' }}</span>
        </div>
        <div class="md-item">
          <span class="md-label">类型</span>
          <span class="md-badge" :class="typeClass(selectedMsg.type)">{{ typeLabel(selectedMsg.type) }}</span>
        </div>
        <div class="md-item">
          <span class="md-label">时间</span>
          <span class="md-val">{{ fmtDate(selectedMsg.createdAt) }}</span>
        </div>
        <div class="md-block">
          <span class="md-label">内容</span>
          <div class="md-text">{{ selectedMsg.content }}</div>
        </div>
        <div class="md-block" v-if="selectedMsg.reply">
          <span class="md-label">回复</span>
          <div class="md-text reply">{{ selectedMsg.reply }}</div>
        </div>
      </div>
    </el-dialog>

    <!-- 发送弹窗 -->
    <el-dialog v-model="sendVisible" title="发送消息" width="480px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="接收教师">
          <el-select v-model="sendForm.teacherId" placeholder="选择教师" clearable style="width:100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="sendForm.type" placeholder="选择类型" style="width:100%">
            <el-option label="提问" value="question" />
            <el-option label="反馈" value="feedback" />
            <el-option label="请求" value="request" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="sendForm.content" type="textarea" rows="4" placeholder="请输入消息内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible=false">取消</el-button>
        <el-button type="primary" @click="submitSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentAPI, teacherAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const messages = ref([])
const unreadCount = ref(0)
const filter = ref('all')
const detailVisible = ref(false)
const sendVisible = ref(false)
const selectedMsg = ref(null)
const teachers = ref([])
const sendForm = ref({ teacherId: null, type: '', content: '' })
const avatarColors = ['#e0e7ff|#4f46e5', '#dbeafe|#2563eb', '#dcfce7|#16a34a', '#fef3c7|#d97706', '#fce7f3|#db2777', '#e0f2fe|#0284c7', '#f3e8ff|#9333ea', '#fef2f2|#dc2626']

const filterTabs = computed(() => [
  { key: 'all', label: '全部', count: messages.value.length },
  { key: 'unread', label: '未读', count: messages.value.filter(m => m.status === 0).length },
  { key: 'read', label: '已读', count: messages.value.filter(m => m.status === 1 || m.status === 2).length }
])

const filtered = computed(() => {
  let list = [...messages.value]
  if (filter.value === 'unread') list = list.filter(m => m.status === 0)
  if (filter.value === 'read') list = list.filter(m => m.status === 1 || m.status === 2)
  return list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
})

function typeLabel(t) {
  const m = { question: '提问', feedback: '反馈', request: '请求', other: '其他', APPEAL: '申诉', FEEDBACK: '反馈', SYSTEM: '系统' }
  return m[t] || '消息'
}
function typeClass(t) {
  const m = { question: 'tp-q', feedback: 'tp-f', request: 'tp-r', APPEAL: 'tp-w', FEEDBACK: 'tp-f', SYSTEM: 'tp-s' }
  return m[t] || 'tp-o'
}

onMounted(async () => {
  await loadMessages()
  await loadUnread()
})

async function loadMessages() {
  try {
    const uid = getUserId(); if (!uid) return
    const r = await studentAPI.getMessages(uid)
    if (Array.isArray(r)) messages.value = r
    else if (r?.data && Array.isArray(r.data)) messages.value = r.data
  } catch (e) { console.error(e) }
}
async function loadUnread() {
  try {
    const uid = getUserId(); if (!uid) return
    const r = await studentAPI.getUnreadCount(uid)
    // 同 StudentLayout：后端回 `{unreadCount: n}`，原来只认裸数字/`.data` → 恒 0
    if (typeof r === 'number') unreadCount.value = r
    else if (r?.unreadCount !== undefined) unreadCount.value = Number(r.unreadCount) || 0
    else if (r?.data !== undefined) unreadCount.value = Number(r.data) || 0
  } catch (e) {}
}
async function refreshAll() {
  await loadMessages()
  await loadUnread()
  ElMessage.success('已刷新')
}
function viewDetail(m) {
  selectedMsg.value = m
  detailVisible.value = true
  if (m.status === 0) markRead(m)
}
async function markRead(m) {
  try {
    await studentAPI.markMessageAsRead(m.id)
    m.status = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e) {}
}
async function openSend() {
  sendForm.value = { teacherId: null, type: '', content: '' }
  try {
    const r = await teacherAPI.getTeachers()
    teachers.value = Array.isArray(r) ? r.map(t => ({ id: t.id, name: t.name || t.username || '教师' })) : []
  } catch (e) {}
  sendVisible.value = true
}
async function submitSend() {
  if (!sendForm.value.teacherId) return ElMessage.warning('请选择教师')
  if (!sendForm.value.content) return ElMessage.warning('请输入内容')
  try {
    const uid = getUserId()
    const info = await studentAPI.getStudentInfoByUserId(uid)
    await studentAPI.sendMessage({ teacherId: Number(sendForm.value.teacherId), content: sendForm.value.content, studentId: Number(info?.id || uid), status: 0 })
    ElMessage.success('已发送')
    sendVisible.value = false
    await loadMessages()
  } catch (e) { ElMessage.error('发送失败') }
}
function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t); const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString('zh-CN')
}
function fmtDate(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.msg-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }

/* 头部 */
.msg-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.mgh-left { display: flex; align-items: center; gap: 14px; }
.mgh-icon { width: 48px; height: 48px; border-radius: 12px; background: #f5f3ff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.mgh-left h2 { margin: 0; font-size: 20px; color: #18181b; font-weight: 700; }
.mgh-left p { margin: 2px 0 0; font-size: 13px; color: #a1a1aa; }
.mgh-right { display: flex; align-items: center; gap: 10px; }
.mgh-badge { font-size: 12px; background: #fef2f2; color: #ef4444; padding: 4px 12px; border-radius: 12px; font-weight: 600; }
.mgh-btn { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 8px; border: 1px solid #e4e4e7; background: #fff; color: #52525b; font-size: 13px; cursor: pointer; transition: all .2s; }
.mgh-btn:hover { border-color: #d4d4d8; background: #fafafa; }
.mgh-btn.primary { background: #7c3aed; color: #fff; border-color: #7c3aed; }
.mgh-btn.primary:hover { background: #6d28d9; }

/* 筛选 */
.msg-filter { display: flex; gap: 8px; margin-bottom: 18px; }
.mft { padding: 8px 18px; border-radius: 20px; border: 1px solid #e4e4e7; background: #fff; font-size: 13px; color: #71717a; cursor: pointer; transition: all .2s; display: flex; align-items: center; gap: 6px; }
.mft:hover { border-color: #a78bfa; color: #7c3aed; }
.mft.active { background: #7c3aed; color: #fff; border-color: #7c3aed; }
.mft-count { font-size: 11px; padding: 1px 7px; border-radius: 10px; background: rgba(0,0,0,.06); }
.mft.active .mft-count { background: rgba(255,255,255,.2); }

/* 列表 */
.msg-list { display: flex; flex-direction: column; gap: 10px; }
.msg-card { display: flex; align-items: flex-start; gap: 14px; background: #fff; border-radius: 12px; padding: 16px 18px; cursor: pointer; transition: all .25s; box-shadow: 0 1px 3px rgba(0,0,0,.03); animation: fadeUp .4s ease both; position: relative; overflow: hidden; }
.msg-card:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.msg-card.unread { background: #faf8ff; }
.mc-bar { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: transparent; transition: background .3s; }
.mc-bar.unread { background: #7c3aed; }
.mc-avatar { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 17px; font-weight: 700; flex-shrink: 0; }
.msg-card:nth-child(1) .mc-avatar { background: linear-gradient(135deg, #818cf8, #4f46e5); }
.msg-card:nth-child(2) .mc-avatar { background: linear-gradient(135deg, #60a5fa, #2563eb); }
.msg-card:nth-child(3) .mc-avatar { background: linear-gradient(135deg, #34d399, #059669); }
.msg-card:nth-child(4) .mc-avatar { background: linear-gradient(135deg, #fbbf24, #d97706); }
.msg-card:nth-child(5) .mc-avatar { background: linear-gradient(135deg, #f472b6, #db2777); }
.msg-card:nth-child(6) .mc-avatar { background: linear-gradient(135deg, #38bdf8, #0284c7); }
.msg-card:nth-child(7) .mc-avatar { background: linear-gradient(135deg, #c084fc, #9333ea); }
.msg-card:nth-child(8) .mc-avatar { background: linear-gradient(135deg, #fb923c, #ea580c); }
.mc-body { flex: 1; min-width: 0; }
.mcb-top { display: flex; align-items: center; gap: 8px; }
.mcb-sender { font-size: 14px; font-weight: 600; color: #18181b; }
.mcb-type { font-size: 11px; padding: 2px 8px; border-radius: 8px; font-weight: 600; }
.tp-q { background: #eff6ff; color: #2563eb; }
.tp-f { background: #fffbeb; color: #d97706; }
.tp-r { background: #f5f3ff; color: #7c3aed; }
.tp-w { background: #fef2f2; color: #ef4444; }
.tp-s { background: #ecfdf5; color: #16a34a; }
.tp-o { background: #f4f4f5; color: #71717a; }
.mcb-unread { width: 7px; height: 7px; border-radius: 50%; background: #7c3aed; }
.mcb-content { margin: 6px 0; font-size: 13px; color: #71717a; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.mcb-meta { display: flex; align-items: center; gap: 10px; font-size: 11px; color: #a1a1aa; }
.mcb-reply { color: #16a34a; font-weight: 600; }

/* 空 */
.msg-empty { text-align: center; padding: 80px 20px; }
.mge-icon { font-size: 48px; margin-bottom: 12px; }
.msg-empty p { font-size: 15px; color: #a1a1aa; }

/* 详情 */
.msg-det { display: flex; flex-direction: column; gap: 14px; }
.md-item { display: flex; align-items: center; gap: 12px; }
.md-label { font-size: 13px; color: #a1a1aa; min-width: 56px; }
.md-val { font-size: 14px; color: #3f3f46; }
.md-badge { font-size: 12px; padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.md-block { display: flex; flex-direction: column; gap: 6px; }
.md-text { background: #f8f7fc; padding: 12px; border-radius: 8px; font-size: 14px; line-height: 1.6; color: #3f3f46; white-space: pre-wrap; }
.md-text.reply { background: #ecfdf5; }

@keyframes fadeUp { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 640px) { .msg-head { flex-direction: column; align-items: flex-start; gap: 12px; } .mgh-right { width: 100%; justify-content: flex-end; } }
</style>
