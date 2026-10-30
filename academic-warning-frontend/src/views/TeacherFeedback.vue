<template>
  <div class="tfb-page">
    <div class="tfb-stats">
      <div class="tfs-card"><div class="tfs-icon all"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></div><div class="tfs-num">{{ feedbacks.length }}</div><div class="tfs-lbl">全部反馈</div></div>
      <div class="tfs-card"><div class="tfs-icon pend"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div><div class="tfs-num" style="color:#f59e0b">{{ pendingCount }}</div><div class="tfs-lbl">待回复</div></div>
      <div class="tfs-card"><div class="tfs-icon done"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg></div><div class="tfs-num" style="color:#16a34a">{{ repliedCount }}</div><div class="tfs-lbl">已回复</div></div>
      <div class="tfs-card"><div class="tfs-icon rate"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg></div><div class="tfs-num">{{ replyRate }}%</div><div class="tfs-lbl">回复率</div></div>
    </div>
    <div class="tfb-tool">
      <span class="tfb-title">反馈列表</span>
      <el-select v-model="filter" placeholder="全部分类" clearable style="width:150px"><el-option v-for="c in categories" :key="c" :label="c" :value="c"/></el-select>
    </div>
    <div class="tfb-list" v-if="filtered.length">
      <div v-for="(f,i) in filtered" :key="f.id" class="tfb-card" :class="{pending:f.status===0}" :style="{animationDelay:i*0.06+'s'}">
        <div class="tfc-left">
          <div class="tfc-name"><span class="tfc-student" v-if="studentNameOf(f)">{{ studentNameOf(f) }}</span><el-tag size="small">{{ f.category||'其他' }}</el-tag><el-tag size="small" :type="f.status===0?'warning':'success'">{{ f.status===0?'待回复':'已回复' }}</el-tag></div>
          <div class="tfc-content">{{ contentOf(f) }}</div>
          <div v-if="f.reply" class="tfc-reply"><svg viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="2" width="14" height="14"><polyline points="9 10 4 15 9 20"/><path d="M20 4v7a4 4 0 01-4 4H4"/></svg><span>{{ f.reply }}</span></div>
          <div class="tfc-time">{{ fmtDate(f.createTime) }}</div>
        </div>
        <div class="tfc-right"><el-button size="small" type="primary" v-if="f.status===0" @click="openReply(f)">回复</el-button><el-button size="small" v-else @click="viewReply(f)">查看回复</el-button></div>
      </div>
    </div>
    <div v-else class="tfb-empty">暂无反馈</div>
    <el-dialog v-model="replyVisible" title="回复反馈" width="500px" destroy-on-close>
      <div v-if="replyRow"><div class="rpl-info"><span class="rpl-name">{{ studentNameOf(replyRow) || '学生' }}</span><el-tag size="small">{{ replyRow.category }}</el-tag></div><div class="rpl-content">{{ contentOf(replyRow) }}</div><el-divider/><el-input v-model="replyText" type="textarea" :rows="3" placeholder="输入回复..."/></div>
      <template #footer><el-button @click="replyVisible=false">取消</el-button><el-button type="primary" @click="submitReply">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { teacherAPI } from '@/api/index'
import { getTeacherId } from '@/utils/userUtils'

const feedbacks = ref([])
const filter = ref('')
const replyVisible = ref(false)
const replyRow = ref(null)
const replyText = ref('')
// 学生端提交时把「姓名：内容」拼进 content（feedback 表没有学生列），
// 列表里把姓名拆出来单独展示，正文去掉重复的前缀。
const categories = computed(() => [...new Set(feedbacks.value.map(f => f.category).filter(Boolean))])
function studentNameOf(f) {
  if (!f || f.studentName) return f?.studentName || ''
  const m = String(f.content || '').match(/^\s*([^：:]{1,20})[：:]/)
  return m ? m[1].trim() : ''
}
function contentOf(f) {
  return String(f?.content || '').replace(/^\s*[^：:]{1,20}[：:]\s*/, '')
}

const pendingCount = computed(() => feedbacks.value.filter(f => f.status === 0).length)
const repliedCount = computed(() => feedbacks.value.filter(f => f.status === 1).length)
const replyRate = computed(() => feedbacks.value.length ? Math.round(repliedCount.value / feedbacks.value.length * 100) : 0)
const filtered = computed(() => filter.value ? feedbacks.value.filter(f => f.category === filter.value) : feedbacks.value)

onMounted(() => loadData())

async function loadData() {
  // 2026-10-02（W4.16）：原来 `if (Array.isArray(r)) feedbacks.value = r` **没有 else**
  // —— 后端一旦回了非数组（错误体、分页对象），列表就静静地保持空，页面显示「暂无反馈」，
  // 与"这个老师确实没有反馈"一模一样。现在非数组一律报错。
  // 2026-10-03（W8.2）：原兜底 `|| '29'` 已删 —— 那是"未登录直开页面时用 29 号教师
  // 的数据演示"，本质是让异常状态下**静默显示另一个人的反馈**（同 W4 删掉的 `'计科2306班'`）。
  // 现在拿不到身份就空着并报错，不拿别人的数据充数。
  try {
    const tid = getTeacherId()
    if (!tid) { console.warn('[TeacherFeedback] 未获取到登录用户 ID，跳过加载'); feedbacks.value = []; ElMessage.error('未获取到登录身份，请重新登录'); return }
    const r = await teacherAPI.getFeedbacks(tid)
    if (Array.isArray(r)) feedbacks.value = r
    else { console.error('反馈列表返回了非数组', r); feedbacks.value = []; ElMessage.error('反馈列表加载失败') }
  } catch(e){ console.error(e); ElMessage.error('反馈列表加载失败') }
}
function fmtDate(d) { if (!d) return '--'; return String(d).replace('T',' ').substring(0,16) }
function openReply(f) { replyRow.value = f; replyText.value = ''; replyVisible.value = true }
function viewReply(f) { replyRow.value = f; replyText.value = f.reply || ''; replyVisible.value = true }
async function submitReply() {
  if (!replyText.value.trim()) { ElMessage.error('请输入回复内容'); return }
  try { await teacherAPI.replyFeedback(replyRow.value.id, { reply: replyText.value }); ElMessage.success('已回复'); replyVisible.value = false; await loadData() } catch(e){ ElMessage.error('回复失败') }
}
</script>

<style scoped>
.tfb-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }
.tfb-stats { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; margin-bottom: 18px; }
.tfs-card { background:#fff; border-radius:12px; padding:18px 20px; display:flex; align-items:center; gap:14px; box-shadow:0 1px 3px rgba(0,0,0,.04); }
.tfs-icon { width:38px; height:38px; border-radius:10px; display:flex; align-items:center; justify-content:center; }
.tfs-icon.all{background:#eff6ff;color:#3b82f6} .tfs-icon.pend{background:#fffbeb;color:#f59e0b}
.tfs-icon.done{background:#ecfdf5;color:#16a34a} .tfs-icon.rate{background:#f5f3ff;color:#7c3aed}
.tfs-num { font-size:24px; font-weight:700; color:#18181b; }
.tfs-lbl { font-size:12px; color:#71717a; }
.tfb-tool { display:flex; align-items:center; gap:12px; margin-bottom:14px; }
.tfb-title { font-size:16px; font-weight:600; color:#18181b; }
.tfb-list { display:flex; flex-direction:column; gap:10px; }
.tfb-card { background:#fff; border-radius:12px; padding:16px 20px; display:flex; justify-content:space-between; align-items:flex-start; gap:16px; box-shadow:0 1px 3px rgba(0,0,0,.04); border-left:4px solid #16a34a; animation:fadeUp .4s ease both; }
.tfb-card.pending { border-left-color: #f59e0b; }
@keyframes fadeUp { from{opacity:0;transform:translateY(8px)} to{} }
.tfc-left { flex:1; min-width:0; }
.tfc-name { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.tfc-student { font-size:15px; font-weight:600; color:#18181b; }
.tfc-content { font-size:13px; color:#52525b; line-height:1.5; margin-bottom:8px; white-space:pre-wrap; }
.tfc-reply { background:#ecfdf5; border-radius:8px; padding:10px 14px; margin-bottom:6px; display:flex; gap:8px; font-size:13px; color:#065f46; }
.tfc-time { font-size:12px; color:#a1a1aa; }
.tfc-right { flex-shrink:0; }
.tfb-empty { text-align:center; padding:60px; color:#a1a1aa; font-size:14px; }
.rpl-info { display:flex; align-items:center; gap:10px; margin-bottom:10px; }
.rpl-name { font-size:15px; font-weight:600; }
.rpl-content { background:#f5f5f5; border-radius:8px; padding:14px; font-size:13px; line-height:1.5; white-space:pre-wrap; }
@media(max-width:768px){ .tfb-stats{grid-template-columns:repeat(2,1fr)} .tfb-card{flex-direction:column} }
</style>
