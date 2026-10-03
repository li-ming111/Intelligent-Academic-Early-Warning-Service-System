<template>
  <div class="td-page">

    <!-- Hero 区域 - 蓝色主题 -->
    <div class="td-hero">
      <div class="tdh-glow tdh-g1"></div>
      <div class="tdh-glow tdh-g2"></div>
      <div class="tdh-inner">
        <div class="tdh-left">
          <div class="tdh-logo">
            <img src="@/assets/logo.png" alt="校徽" class="tdh-logo-img" />
          </div>
          <div>
            <h1 class="tdh-greeting">{{ greeting }}，{{ teacherName }}</h1>
            <p class="tdh-date">{{ currentDate }} · 祝您工作顺利</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <div class="td-metrics">
      <div class="tdm-card">
        <div class="tdm-icon ic-blue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
        </div>
        <div class="tdm-val">{{ stats.totalStudents }}</div>
        <div class="tdm-lbl">管理学生</div>
      </div>
      <div class="tdm-card">
        <div class="tdm-icon ic-sky">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>
        </div>
        <div class="tdm-val">{{ stats.totalCourses }}</div>
        <div class="tdm-lbl">授课课程</div>
      </div>
      <div class="tdm-card">
        <div class="tdm-icon ic-red">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        </div>
        <div class="tdm-val red">{{ stats.highWarnings }}</div>
        <div class="tdm-lbl">严重预警</div>
      </div>
      <div class="tdm-card">
        <div class="tdm-icon ic-amber">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        </div>
        <div class="tdm-val amber">{{ stats.mediumWarnings }}</div>
        <div class="tdm-lbl">中度预警</div>
      </div>
    </div>

    <!-- 主体双栏 -->
    <div class="td-main">
      <div class="td-left">

        <div class="td-card" v-if="warnings.length">
          <div class="tdc-head">
            <span class="tdc-title">最新预警</span>
            <router-link to="/teacher/warnings" class="tdc-link">查看全部 →</router-link>
          </div>
          <div class="tdc-warns">
            <div v-for="(w, i) in warnings.slice(0, 5)" :key="w.id" class="tdcw-item" :style="{ animationDelay: i * 0.05 + 's' }">
              <div class="tdcw-bar" :class="w.warningLevel === '严重' ? 'lvl-3' : w.warningLevel === '中度' ? 'lvl-2' : 'lvl-1'"></div>
              <div class="tdcw-body">
                <div class="tdcw-top">
                  <span class="tdcw-name">{{ w.studentName }}</span>
                  <span class="tdcw-lvl" :class="w.warningLevel === '严重' ? 'lvl-3' : w.warningLevel === '中度' ? 'lvl-2' : 'lvl-1'">{{ w.warningLevel }}</span>
                </div>
                <p class="tdcw-title">{{ w.title }}</p>
                <div class="tdcw-meta">
                  <span>{{ w.className }}</span>
                  <span>{{ fmtDate(w.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="td-card" v-if="classes.length">
          <div class="tdc-head">
            <span class="tdc-title">管辖班级</span>
            <span class="tdc-badge">{{ classes.length }}个</span>
          </div>
          <div class="tdc-classes">
            <div v-for="(c, i) in classes" :key="c.id" class="tdcc-item" :style="{ animationDelay: i * 0.06 + 's' }">
              <div class="tdcc-avatar">{{ (c.name || '').substring(0, 2) }}</div>
              <div class="tdcc-info">
                <span class="tdcc-name">{{ c.name }}</span>
                <span class="tdcc-count">{{ c.studentCount || 0 }}名学生</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="td-right">

        <div class="td-card">
          <div class="tdc-head">
            <span class="tdc-title">待办事项</span>
            <span class="tdc-badge" v-if="todos.length">{{ todos.length }}项</span>
          </div>
          <div class="tdc-todos" v-if="todos.length">
            <div v-for="(t, i) in todos" :key="t.id" class="tdct-item" :style="{ animationDelay: i * 0.06 + 's' }">
              <div class="tdct-dot" :class="t.urgent ? 'urgent' : ''"></div>
              <div class="tdct-info">
                <span class="tdct-title">{{ t.title }}</span>
                <span class="tdct-desc">{{ t.description }}</span>
              </div>
              <router-link :to="t.link" class="tdct-btn">{{ t.action }}</router-link>
            </div>
          </div>
          <div v-else class="tdc-none">暂无待办事项，一切正常 👍</div>
        </div>

        <div class="td-card">
          <div class="tdc-head">
            <span class="tdc-title">快捷入口</span>
          </div>
          <div class="tdc-actions">
            <router-link to="/teacher/scores" class="tdca-btn">
              <span class="tdca-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg></span>
              <span>成绩管理</span>
            </router-link>
            <router-link to="/teacher/warnings" class="tdca-btn">
              <span class="tdca-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></span>
              <span>预警管理</span>
            </router-link>
            <router-link to="/teacher/analysis" class="tdca-btn">
              <span class="tdca-icon ic-sky"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg></span>
              <span>数据分析</span>
            </router-link>
            <router-link to="/teacher/courses" class="tdca-btn">
              <span class="tdca-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg></span>
              <span>课程管理</span>
            </router-link>
            <router-link to="/teacher/feedback" class="tdca-btn">
              <span class="tdca-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></span>
              <span>反馈消息</span>
            </router-link>
            <router-link to="/teacher/credit-prediction" class="tdca-btn">
              <span class="tdca-icon ic-teal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg></span>
              <span>学分预测</span>
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { teacherAPI } from '@/api/index'
import { getTeacherId } from '@/utils/userUtils'
import { isPendingWarning } from '@/utils/warning'

const teacherName = ref('教师')
const currentDate = ref('')
const warnings = ref([])
const classes = ref([])
const todos = ref([])
const stats = reactive({ totalStudents: 0, totalCourses: 0, highWarnings: 0, mediumWarnings: 0 })

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

onMounted(async () => {
  // 优先用登录时写入的 'name'（Login.vue 存的是真名）。
  // 只读 'userName' 不行：它由各 Layout 挂载后才写入，首屏读不到，
  // 会先闪一下兜底文案「教师」再被 loadData 纠正。
  const stored = localStorage.getItem('name') || localStorage.getItem('userName')
  if (stored && !/^\d+$/.test(stored)) teacherName.value = stored
  const today = new Date()
  currentDate.value = today.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
  await loadData()
})

async function loadData() {
  try {
    const tid = getTeacherId()
    // 拿不到登录身份就**不发请求**，页面保持空态 —— 原来这里兜底成 '9'，
    // 等于未登录时静默显示 9 号用户的数据（见 utils/userUtils.js 的 getTeacherId）。
    if (!tid) { console.warn('[TeacherDashboard] 未获取到登录用户 ID，跳过加载'); return }
    const [dashRes, warnRes, classRes] = await Promise.all([
      teacherAPI.getDashboard(tid).catch(() => null),
      teacherAPI.getWarnings(tid).catch(() => []),
      teacherAPI.getMyClasses(tid).catch(() => [])
    ])
    if (dashRes) {
      stats.totalStudents = dashRes.totalStudents || 0
      stats.totalCourses = dashRes.totalCourses || 0
      if (dashRes.teacherName) {
        teacherName.value = dashRes.teacherName
        localStorage.setItem('userName', dashRes.teacherName)
      }
    }
    const wlist = Array.isArray(warnRes) ? warnRes : (warnRes?.data || [])
    stats.highWarnings = wlist.filter(w => w.warningLevel === '严重' || w.warningLevel >= 3).length
    stats.mediumWarnings = wlist.filter(w => w.warningLevel === '中度' || w.warningLevel === 2).length
    // 2026-10-02（W1.6）：改调共用层。原来写的是 `w.status === '待处理' || w.status === 0`
    // —— 扩三态后这仍然是对的（"已解除"两条都不等），但它把后端的中文文案**焊死**在页面里，
    // 后端改一个字这里就静默恒假。`isPendingWarning` 同时吃数字与中文。
    warnings.value = wlist.filter(w => isPendingWarning(w.status)).slice(0, 8)
    const clist = Array.isArray(classRes) ? classRes : (classRes?.data || [])
    classes.value = clist.map(c => ({
      id: c.id || c.classId,
      name: c.name || c.className,
      studentCount: c.studentCount || 0
    }))
    const tdList = []
    const pendingWarns = wlist.filter(w => isPendingWarning(w.status)).length
    if (pendingWarns > 0) {
      tdList.push({ id: 1, title: '预警待处理', description: `${pendingWarns}条预警需要您处理`, action: '去处理', link: '/teacher/warnings', urgent: true })
    }
    if (wlist.some(w => w.warningLevel === '严重')) {
      tdList.push({ id: 2, title: '严重预警关注', description: '存在严重预警学生，建议立即跟进', action: '查看', link: '/teacher/warnings', urgent: true })
    }
    // ⚠️ `/teachers/todos/{id}` 回的是 **List<Map>{id,title,count,type}**（TeacherController.getTodos），
    // 不是对象。原来读 `feedbackRes.pendingFeedbacks` —— 数组上取属性恒 undefined，
    // 两个 if 都恒假 → 「学生反馈待回复」「成绩申诉处理」**永远不出现**，且不报错。
    // 按 type 取数，而不是按想象的字段名。
    const todosRes = await teacherAPI.getTodos(tid).catch(() => null)
    const todosList = Array.isArray(todosRes) ? todosRes : (todosRes?.items ?? todosRes?.list ?? [])
    const todoCount = (type) => {
      const hit = todosList.find((t) => t?.type === type)
      return Number(hit?.count) || 0
    }
    const pendingFeedbacks = todoCount('feedback')
    const pendingClassReqs = todoCount('class_request')
    if (pendingFeedbacks > 0) {
      tdList.push({ id: 3, title: '学生反馈待回复', description: `${pendingFeedbacks}条反馈等待回复`, action: '回复', link: '/teacher/feedback' })
    }
    if (pendingClassReqs > 0) {
      tdList.push({ id: 4, title: '班级申请待审核', description: `${pendingClassReqs}条班级申请待审核`, action: '查看', link: '/teacher/class-management' })
    }
    todos.value = tdList
  } catch (e) { console.error('加载看板失败', e) }
}

function fmtDate(d) {
  if (!d) return ''
  const s = typeof d === 'string' ? d : (d[0] || '')
  if (!s) return ''
  return s.split('T')[0] || s.substring(0, 10)
}
</script>

<style scoped>
.td-page { padding: 20px 24px; min-height: 100vh; background: #f0f4ff; }

/* ===== Hero - 蓝色主题 ===== */
.td-hero {
  position: relative;
  /* ⚠️ 这里**故意**不用 var(--theme-gradient)（2026-09-30 定）。
     教师主题的渐变是 Material 蓝 #2196F3→#64B5F6，而本页自己的链接、徽章、
     lvl-1 语义色、头像渐变用的都是 Tailwind 蓝 #2563eb/#3b82f6。同一色相、
     只差深浅，肉眼不构成冲突；换成主题变量反倒让 hero 与本页卡片差一档。
     对照：管理员首页那页是真冲突（hero 蓝 vs 全站 Element Plus 紫），已改，
     见 AdminDashboard.vue。 */
  background: linear-gradient(135deg, #1e40af 0%, #2563eb 30%, #3b82f6 60%, #60a5fa 100%);
  border-radius: 20px;
  padding: 40px 44px;
  margin-bottom: 24px;
  overflow: hidden;
}
.tdh-glow { position: absolute; border-radius: 50%; filter: blur(80px); opacity: .25; pointer-events: none; }
.tdh-g1 { width: 280px; height: 280px; background: #3b82f6; top: -80px; right: -60px; }
.tdh-g2 { width: 220px; height: 220px; background: #60a5fa; bottom: -70px; left: 15%; opacity: .2; }
.tdh-inner { position: relative; z-index: 1; display: flex; justify-content: space-between; align-items: center; }
.tdh-left { display: flex; align-items: center; gap: 18px; }
.tdh-logo {
  width: 64px; height: 64px; border-radius: 16px;
  background: #fff; display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 16px rgba(0,0,0,.15); flex-shrink: 0; overflow: hidden;
}
.tdh-logo-img { width: 100%; height: 100%; object-fit: contain; display: block; }
.tdh-greeting { margin: 0; font-size: 24px; color: #fff; font-weight: 700; }
.tdh-wave { display: inline-block; animation: wave 1.5s ease-in-out infinite; }
.tdh-date { margin: 6px 0 0; font-size: 13px; color: rgba(255,255,255,.7); }

/* ===== 核心指标 ===== */
.td-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.tdm-card {
  background: #fff; border-radius: 14px; padding: 22px 20px;
  box-shadow: 0 2px 8px rgba(59,130,246,.08); text-align: center; transition: all .25s;
}
.tdm-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(59,130,246,.15); }
.tdm-icon { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; margin: 0 auto 10px; }
.ic-blue { background: #eff6ff; color: #2563eb; }
.ic-sky { background: #e0f2fe; color: #0284c7; }
.ic-red { background: #fef2f2; color: #ef4444; }
.ic-amber { background: #fffbeb; color: #d97706; }
.tdm-val { font-size: 26px; font-weight: 800; color: #1e3a5f; }
.tdm-val.red { color: #ef4444; }
.tdm-val.amber { color: #d97706; }
.tdm-lbl { font-size: 12px; color: #94a3b8; margin-top: 4px; }

/* ===== 主体 ===== */
.td-main { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
@media (max-width: 900px) { .td-main { grid-template-columns: 1fr; } .td-metrics { grid-template-columns: repeat(2, 1fr); } }

/* ===== 卡片 ===== */
.td-card {
  background: #fff; border-radius: 16px; box-shadow: 0 2px 8px rgba(0,0,0,.04);
  padding: 20px 24px; margin-bottom: 20px;
}
.tdc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tdc-title { font-size: 15px; font-weight: 600; color: #1e3a5f; }
.tdc-link { font-size: 12px; color: #2563eb; text-decoration: none; }
.tdc-link:hover { text-decoration: underline; }
.tdc-badge { font-size: 11px; background: #eff6ff; color: #2563eb; padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.tdc-none { text-align: center; padding: 30px 0; color: #94a3b8; font-size: 14px; }

/* 预警列表 */
.tdcw-item { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f1f5f9; animation: fadeUp .4s ease both; }
.tdcw-item:last-child { border-bottom: none; padding-bottom: 0; }
.tdcw-bar { width: 4px; border-radius: 4px; flex-shrink: 0; }
.tdcw-bar.lvl-3 { background: #ef4444; }
.tdcw-bar.lvl-2 { background: #f59e0b; }
.tdcw-bar.lvl-1 { background: #3b82f6; }
.tdcw-body { flex: 1; min-width: 0; }
.tdcw-top { display: flex; justify-content: space-between; align-items: center; }
.tdcw-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.tdcw-lvl { font-size: 11px; padding: 1px 8px; border-radius: 10px; font-weight: 600; }
.tdcw-lvl.lvl-3 { background: #fef2f2; color: #ef4444; }
.tdcw-lvl.lvl-2 { background: #fffbeb; color: #d97706; }
.tdcw-lvl.lvl-1 { background: #eff6ff; color: #2563eb; }
.tdcw-title { margin: 4px 0; font-size: 13px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tdcw-meta { display: flex; gap: 12px; font-size: 11px; color: #94a3b8; }

/* 班级 */
.tdcc-item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f1f5f9; animation: fadeUp .4s ease both; }
.tdcc-item:last-child { border-bottom: none; }
.tdcc-avatar { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; color: #fff; flex-shrink: 0; }
.tdcc-item:nth-child(1) .tdcc-avatar { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.tdcc-item:nth-child(2) .tdcc-avatar { background: linear-gradient(135deg, #60a5fa, #3b82f6); }
.tdcc-item:nth-child(3) .tdcc-avatar { background: linear-gradient(135deg, #38bdf8, #0ea5e9); }
.tdcc-item:nth-child(4) .tdcc-avatar { background: linear-gradient(135deg, #818cf8, #6366f1); }
.tdcc-item:nth-child(5) .tdcc-avatar { background: linear-gradient(135deg, #22d3ee, #06b6d4); }
.tdcc-item:nth-child(6) .tdcc-avatar { background: linear-gradient(135deg, #34d399, #10b981); }
.tdcc-info { display: flex; flex-direction: column; }
.tdcc-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.tdcc-count { font-size: 12px; color: #94a3b8; }

/* 待办 */
.tdct-item { display: flex; align-items: center; gap: 10px; padding: 12px 0; border-bottom: 1px solid #f1f5f9; animation: fadeUp .4s ease both; }
.tdct-item:last-child { border-bottom: none; }
.tdct-dot { width: 8px; height: 8px; border-radius: 50%; background: #60a5fa; flex-shrink: 0; }
.tdct-dot.urgent { background: #ef4444; animation: pulse 2s infinite; }
.tdct-info { flex: 1; min-width: 0; }
.tdct-title { font-size: 14px; font-weight: 600; color: #1e293b; }
.tdct-desc { display: block; font-size: 12px; color: #94a3b8; margin-top: 2px; }
.tdct-btn { font-size: 12px; color: #2563eb; text-decoration: none; font-weight: 600; flex-shrink: 0; }
.tdct-btn:hover { text-decoration: underline; }

/* 快捷入口 */
.tdc-actions { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.tdca-btn {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 16px 10px; border-radius: 12px; text-decoration: none;
  background: #f8fafc; transition: all .25s; border: 1px solid transparent;
}
.tdca-btn:hover { background: #eff6ff; border-color: #bfdbfe; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(59,130,246,.1); }
.tdca-icon { width: 38px; height: 38px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.tdca-btn span:last-child { font-size: 12px; color: #475569; font-weight: 500; }

@keyframes fadeUp { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
@keyframes wave { 0%,100% { transform: rotate(0); } 25% { transform: rotate(15deg); } 75% { transform: rotate(-10deg); } }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: .4; } }
</style>
