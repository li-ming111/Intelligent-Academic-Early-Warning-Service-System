<template>
  <div class="ai-assistant">
    <!-- 浮动按钮 - 可拖拽 -->
    <div
      class="chat-trigger"
      :class="{ active: isChatOpen }"
      :style="{ left: ballX + 'px', top: ballY + 'px' }"
      @mousedown="startDrag"
      @touchstart.prevent="startDrag"
      @click="handleClick"
    >
      <img src="@/assets/xiaohui.jpg" alt="AI助手" class="trigger-img" />
    </div>

    <!-- 聊天面板 -->
    <transition name="panel-slide">
      <div class="chat-panel" v-if="isChatOpen" :style="panelStyle">
        <!-- 头部 -->
        <div class="panel-header">
          <div class="header-left">
            <div class="header-avatar">
              <img src="@/assets/xiaohui.jpg" alt="校徽" class="header-avatar-img" />
            </div>
            <div class="header-info">
              <span class="header-name">智学助手</span>
              <span class="header-status">AI · 在线</span>
            </div>
          </div>
          <div class="header-actions">
            <button class="hdr-btn" @click="toggleChat" title="关闭">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 消息区 - 关键：flex:1 + overflow-y:auto -->
        <div class="panel-body" ref="panelBody">
          <!-- 欢迎屏 -->
          <div v-if="messages.length === 0" class="welcome-screen">
            <h3>你好，我是智学助手</h3>
            <p>基于 DeepSeek 大模型，为你提供智能学业分析</p>
            <div class="quick-actions">
              <button v-for="q in quickQuestions" :key="q" @click="sendSuggestion(q)" class="quick-btn">
                {{ q }}
              </button>
            </div>
          </div>

          <!-- 消息列表 -->
          <div v-for="(msg, i) in messages" :key="i" :class="['msg-row', msg.type]">
            <div class="msg-bubble" :class="msg.type">
              <div v-if="msg.type === 'ai'" class="bubble-text" v-html="renderReply(msg.content)"></div>
              <div v-else class="bubble-text">{{ msg.content }}</div>
              <div class="bubble-time">{{ msg.time }}</div>
            </div>
          </div>

          <!-- 加载动画 -->
          <div v-if="loading" class="msg-row ai">
            <div class="msg-bubble ai typing">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="panel-footer">
          <div class="input-wrap">
            <input
              type="text"
              v-model="inputMessage"
              placeholder="输入学业相关问题..."
              @keyup.enter="sendMessage"
              :disabled="loading"
              ref="inputRef"
            />
            <button @click="sendMessage" :disabled="!inputMessage.trim() || loading" class="send-btn">
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M2 21l21-9L2 3v7l15 2-15 2v7z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { aiAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const isChatOpen = ref(false)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const panelBody = ref(null)
const inputRef = ref(null)

// ====== 拖拽状态 ======
// 位置存 localStorage：拖到哪儿就记在哪儿，刷新/切角色不再弹回右下角。
const BALL_POS_KEY = 'aiBallPos'
function clampBall(x, y) {
  return {
    x: Math.min(Math.max(x, 8), window.innerWidth - 62),
    y: Math.min(Math.max(y, 8), window.innerHeight - 62)
  }
}
const savedPos = (() => {
  try { return JSON.parse(localStorage.getItem(BALL_POS_KEY) || 'null') } catch { return null }
})()
const startPos = clampBall(
  savedPos?.x ?? window.innerWidth - 82,
  savedPos?.y ?? window.innerHeight - 82
)
const ballX = ref(startPos.x)  // 默认右下角
const ballY = ref(startPos.y)

// 聊天面板跟着球弹：默认在球右侧、垂直居中；右边放不下翻到左侧，再夹在视口内。
const panelStyle = computed(() => {
  const w = 400, h = 560, m = 12
  let left = ballX.value + 54 + m
  if (left + w > window.innerWidth - m) left = ballX.value - w - m
  left = Math.max(m, Math.min(left, window.innerWidth - w - m))
  let top = ballY.value + 27 - h / 2
  top = Math.max(m, Math.min(top, window.innerHeight - h - m))
  return { left: left + 'px', top: top + 'px' }
})

const dragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const dragOffsetX = ref(0)
const dragOffsetY = ref(0)
const hasMoved = ref(false)  // 区分拖拽和点击

function startDrag(e) {
  dragging.value = true
  hasMoved.value = false
  const touch = e.touches ? e.touches[0] : e
  dragStartX.value = touch.clientX
  dragStartY.value = touch.clientY
  dragOffsetX.value = ballX.value
  dragOffsetY.value = ballY.value
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag, { passive: false })
  document.addEventListener('touchend', stopDrag)
}

function onDrag(e) {
  if (!dragging.value) return
  const touch = e.touches ? e.touches[0] : e
  const dx = touch.clientX - dragStartX.value
  const dy = touch.clientY - dragStartY.value
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) hasMoved.value = true
  const p = clampBall(dragOffsetX.value + dx, dragOffsetY.value + dy)
  ballX.value = p.x
  ballY.value = p.y
}

function stopDrag() {
  dragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
  // 拖完落点存档（没动过的点击不写，避免无谓写盘）
  if (hasMoved.value) localStorage.setItem(BALL_POS_KEY, JSON.stringify({ x: ballX.value, y: ballY.value }))
}

function handleClick() {
  if (!hasMoved.value) toggleChat()
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
})

const quickQuestions = [
  '我的成绩怎么样？',
  '帮我分析一下学业风险',
  '如何提高GPA？',
  '有哪些学习方法推荐？'
]

function toggleChat() {
  isChatOpen.value = !isChatOpen.value
  if (isChatOpen.value) {
    nextTick(() => { inputRef.value?.focus(); scrollToBottom() })
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (panelBody.value) {
      panelBody.value.scrollTop = panelBody.value.scrollHeight
    }
  })
}

async function sendMessage() {
  const text = inputMessage.value.trim()
  if (!text || loading.value) return
  inputMessage.value = ''

  messages.value.push({ type: 'user', content: text, time: fmtTime() })
  scrollToBottom()

  loading.value = true
  try {
    const userId = getUserId()
    if (!userId) throw new Error('请先登录')
    const res = await aiAPI.getResponse(userId, text)
    // 后端没给 reply 就是没答，不再自己编一句兜底话顶上去
    const reply = res?.reply || res?.content
    messages.value.push({
      type: 'ai',
      content: reply || 'AI 没有返回内容。',
      time: fmtTime()
    })
  } catch (e) {
    console.error('AI 回复失败:', e)
    // 把后端的原话带出来（例如"未配置 AI_API_KEY"），而不是一律"服务暂时不可用"
    messages.value.push({ type: 'ai', content: 'AI 不可用：' + (e?.message || '网络错误'), time: fmtTime() })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function sendSuggestion(q) {
  inputMessage.value = q
  sendMessage()
}

function fmtTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 模型按 Markdown 回话，气泡原来是纯文本，于是 **、# 原样露出来。
// 先转义再套格式：v-html 里只可能出现下面这几组固定标签，模型返回的 HTML 进不来。
function renderReply(text) {
  const safe = String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/\r\n?/g, '\n')
  return safe
    .replace(/^#{1,6}\s*(.+)$/gm, '<strong class="md-h">$1</strong>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '$1（$2）')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/^\s*[-*]\s+/gm, '• ')
    .replace(/\n/g, '<br>')
}

watch(messages, () => scrollToBottom(), { deep: true })
</script>

<style scoped>
/* ========== 容器 ========== */
.ai-assistant {
  position: fixed;
  top: 0; left: 0;
  width: 0; height: 0;
  z-index: 9999;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', sans-serif;
}

/* ========== 触发按钮 ========== */
.chat-trigger {
  position: absolute;
  width: 54px; height: 54px;
  border-radius: 50%;
  overflow: hidden;
  cursor: grab;
  box-shadow: 0 6px 24px rgba(124, 58, 237, .35);
  transition: transform .15s, box-shadow .3s;
  border: 2px solid transparent;
  user-select: none;
}
.chat-trigger:active { cursor: grabbing; }
.chat-trigger:hover { box-shadow: 0 8px 30px rgba(124, 58, 237, .5); border-color: #7c3aed; }
.chat-trigger.active { box-shadow: 0 6px 24px rgba(0,0,0,.15); border-color: #7c3aed; }
.chat-trigger.active:hover { transform: none; }
.trigger-img { width: 100%; height: 100%; object-fit: cover; display: block; }

/* ========== 聊天面板 (位置由 panelStyle 跟随悬浮球计算，此处只管尺寸外观) ========== */
.chat-panel {
  position: fixed;
  width: 400px; height: 560px;
  max-height: calc(100vh - 24px);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 48px rgba(0,0,0,.12), 0 4px 16px rgba(0,0,0,.06);
  display: flex; flex-direction: column;
  overflow: hidden;
  border: 1px solid #eee;
}

/* ----- 头部 ----- */
.panel-header {
  flex-shrink: 0;
  height: 60px;
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px;
  color: #fff;
}
.header-left  { display: flex; align-items: center; gap: 10px; }
.header-avatar {
  width: 34px; height: 34px; border-radius: 10px;
  background: rgba(255,255,255,.2);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.header-avatar-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.header-info  { display: flex; flex-direction: column; line-height: 1.2; }
.header-name  { font-size: 15px; font-weight: 600; }
.header-status { font-size: 11px; opacity: .75; }
.hdr-btn {
  width: 30px; height: 30px; border-radius: 8px;
  border: none; background: rgba(255,255,255,.15);
  color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background .2s;
}
.hdr-btn:hover { background: rgba(255,255,255,.3); }

/* ----- 消息区（关键：可滚动的核心） ----- */
.panel-body {
  flex: 1;
  min-height: 0;           /* ← 允许收缩！ */
  overflow-y: auto;        /* ← 滚动！ */
  overflow-x: hidden;
  padding: 16px;
  background: #f8f7fc;
  scroll-behavior: smooth;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-track { background: transparent; }
.panel-body::-webkit-scrollbar-thumb { background: #d4d4d8; border-radius: 4px; }

/* ----- 欢迎屏 ----- */
.welcome-screen {
  display: flex; flex-direction: column; align-items: center;
  padding: 30px 10px 10px;
  animation: fadeUp .5s ease;
}
.welcome-screen h3 {
  margin: 12px 0 4px; font-size: 18px; color: #1e1b4b; font-weight: 700;
}
.welcome-screen p {
  margin: 0 0 18px; font-size: 13px; color: #71717a;
}
.quick-actions {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px;
  width: 100%; max-width: 320px;
}
.quick-btn {
  padding: 10px 12px; font-size: 13px;
  border: 1px solid #e4e4e7; border-radius: 12px;
  background: #fff; color: #3f3f46; cursor: pointer;
  transition: all .2s; text-align: left;
}
.quick-btn:hover { border-color: #a78bfa; background: #f5f3ff; color: #7c3aed; }

/* ----- 消息行 ----- */
.msg-row {
  display: flex;
  margin-bottom: 14px;
  animation: fadeUp .35s ease;
}
.msg-row.user { justify-content: flex-end; }
.msg-row.ai   { justify-content: flex-start; }

/* 气泡 */
.msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 14px; line-height: 1.55; word-break: break-word;
  position: relative;
}
.msg-bubble.ai {
  background: #fff;
  color: #27272a;
  border: 1px solid #e4e4e7;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
}
.msg-bubble.user {
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble-time {
  font-size: 10px; margin-top: 4px; opacity: .55;
}
.msg-bubble.user .bubble-time { text-align: right; }

/* renderReply 生成的格式标签（v-html 内容不带 scoped 属性，须用 :deep） */
.bubble-text :deep(strong) { font-weight: 600; }
.bubble-text :deep(.md-h) { display: block; margin: 2px 0 4px; }
.bubble-text :deep(code) {
  padding: 1px 4px; border-radius: 4px;
  background: #f4f4f5; font-family: ui-monospace, Consolas, monospace;
  font-size: 12.5px;
}

/* 打字动画 */
.msg-bubble.typing {
  display: flex; gap: 5px; align-items: center;
  padding: 14px 18px; width: 56px;
}
.dot {
  width: 7px; height: 7px; border-radius: 50%; background: #a78bfa;
  animation: bounce 1.4s infinite ease-in-out both;
}
.dot:nth-child(1) { animation-delay: -.32s; }
.dot:nth-child(2) { animation-delay: -.16s; }

/* ----- 输入区 ----- */
.panel-footer {
  flex-shrink: 0;
  padding: 12px 16px; background: #fff; border-top: 1px solid #f4f4f5;
}
.input-wrap {
  display: flex; align-items: center; gap: 8px;
  background: #f4f4f5; border-radius: 24px; padding: 4px 4px 4px 16px;
  transition: box-shadow .25s;
}
.input-wrap:focus-within { box-shadow: 0 0 0 2px rgba(124,58,237,.15); background: #fff; }
.input-wrap input {
  flex: 1; border: none; background: transparent; outline: none;
  font-size: 14px; padding: 8px 0; color: #18181b;
}
.input-wrap input::placeholder { color: #a1a1aa; }
.send-btn {
  width: 38px; height: 38px; border-radius: 50%;
  border: none; background: linear-gradient(135deg, #7c3aed, #6d28d9);
  color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all .2s; flex-shrink: 0;
}
.send-btn:hover:not(:disabled) { transform: scale(1.06); box-shadow: 0 4px 12px rgba(124,58,237,.4); }
.send-btn:disabled { opacity: .4; cursor: default; }

/* ========== 动画 ========== */
.panel-slide-enter-active { animation: slideIn .3s cubic-bezier(.4,0,.2,1); }
.panel-slide-leave-active { animation: slideIn .25s cubic-bezier(.4,0,.2,1) reverse; }
@keyframes slideIn {
  from { opacity: 0; transform: translateY(16px) scale(.96); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
/* ========== 响应式 ========== */
@media (max-width: 480px) {
  .chat-panel { width: calc(100vw - 40px); height: 65vh; right: -8px; }
}
</style>
