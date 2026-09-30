<template>
  <div class="dashboard-home">
    <!-- Hero 区域 -->
    <section class="hero-section">
      <div class="hero-bg">
        <div class="hero-orb orb-1"></div>
        <div class="hero-orb orb-2"></div>
        <div class="hero-orb orb-3"></div>
      </div>
      <div class="hero-content">
        <div class="hero-left">
          <div class="hero-greeting">
            <span class="hero-wave">&#128075;</span>
            <span>{{ getGreeting() }}</span>
          </div>
          <h1 class="hero-name">{{ username }}</h1>
          <p class="hero-sub">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            最后更新：{{ today }}
          </p>
          <div class="hero-tags">
            <span class="htag">{{ classInfo.classIdentifier || '计科2306班' }}</span>
            <span class="htag" v-if="academicData.warningLevel !== '正常'">预警 {{ academicData.warningLevel }}</span>
            <span class="htag safe" v-else>状态良好</span>
          </div>
        </div>
        <div class="hero-right">
          <div class="hero-stat-card">
            <div class="hsc-icon gpa">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
            </div>
            <div class="hsc-body">
              <div class="hsc-val">{{ academicData.gpa || '--' }}</div>
              <div class="hsc-label">当前 GPA</div>
            </div>
          </div>
          <div class="hero-stat-card">
            <div class="hsc-icon credit">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>
            </div>
            <div class="hsc-body">
              <div class="hsc-val">{{ academicData.totalCourses }}</div>
              <div class="hsc-label">总课程</div>
            </div>
          </div>
          <div class="hero-stat-card">
            <div class="hsc-icon fail">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            </div>
            <div class="hsc-body">
              <div class="hsc-val" :class="{ red: academicData.failedCourses > 0 }">{{ academicData.failedCourses || 0 }}</div>
              <div class="hsc-label">挂科数</div>
            </div>
          </div>
          <div class="hero-stat-card">
            <div class="hsc-icon warn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            </div>
            <div class="hsc-body">
              <div class="hsc-val">{{ academicData.warningCount }}</div>
              <div class="hsc-label">预警</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 主体区域 -->
    <section class="main-body">
      <!-- 左栏 -->
      <div class="col">
        <!-- 预警 -->
        <div class="panel">
          <div class="panel-head">
            <div class="ph-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span>最新预警</span>
              <span class="ph-badge" v-if="warnings.length">{{ warnings.length }}</span>
            </div>
            <router-link to="/student/warnings" class="ph-link">全部 <span>→</span></router-link>
          </div>
          <div class="panel-body">
            <template v-if="warnings.length">
              <div v-for="(w,i) in warnings.slice(0,4)" :key="w.id" class="warn-card" :style="{ animationDelay: i*0.1+'s' }">
                <div class="wc-band" :class="'wb-' + ((w.warningLevel >= 3) ? 3 : (w.warningLevel == 2) ? 2 : 1)"></div>
                <div class="wc-main">
                  <div class="wc-top">
                    <span class="wc-title">{{ w.title || '学业预警' }}</span>
                    <span class="wc-badge" :class="'wbl-' + ((w.warningLevel >= 3) ? 3 : (w.warningLevel == 2) ? 2 : 1)">{{ w.warningLevel >= 3 ? '严重' : w.warningLevel == 2 ? '中度' : '轻度' }}</span>
                  </div>
                  <p class="wc-text">{{ w.description }}</p>
                </div>
              </div>
            </template>
            <div v-else class="panel-empty">
              <svg viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="1.5" width="40" height="40"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              <p>学业状态良好，暂无预警</p>
            </div>
          </div>
        </div>

        <!-- 课程 -->
        <div class="panel">
          <div class="panel-head">
            <div class="ph-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="#7c3aed" stroke-width="2" width="18" height="18"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>
              <span>最新学期课程</span>
            </div>
            <router-link to="/student/scores" class="ph-link">成绩 <span>→</span></router-link>
          </div>
          <div class="panel-body">
            <template v-if="courses.length">
              <div v-for="(c,i) in courses.slice(0,5)" :key="c.id" class="course-item" :style="{ animationDelay: i*0.08+'s' }">
                <div class="ci-avatar" :style="{ background: randomGradient(c.courseId) }">{{ (c.courseName || '课').charAt(0) }}</div>
                <div class="ci-info">
                  <div class="ci-name">{{ c.courseName || '课程' + c.courseId }}</div>
                  <div class="ci-meta">{{ c.credits || '--' }} 学分</div>
                </div>
                <div class="ci-score" :class="{ fail: (c.scoreTotal||0)<60 }">
                  <div class="cis-num">{{ c.scoreTotal || '--' }}</div>
                  <div class="cis-label">分</div>
                </div>
              </div>
            </template>
            <div v-else class="panel-empty">
              <svg viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5" width="40" height="40"><path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>
              <p>暂无课程数据</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏 -->
      <div class="col">
        <!-- 帮扶 -->
        <div class="panel">
          <div class="panel-head">
            <div class="ph-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2" width="18" height="18"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              <span>帮扶计划</span>
            </div>
            <router-link to="/student/assistance" class="ph-link">全部 <span>→</span></router-link>
          </div>
          <div class="panel-body">
            <template v-if="assistancePlans.length">
              <div v-for="(p,i) in assistancePlans.slice(0,2)" :key="p.id" class="plan-panel" :style="{ animationDelay: i*0.1+'s' }">
                <div class="pp-top">
                  <span class="pp-name">{{ p.title || '帮扶计划' }}</span>
                  <el-tag size="small" :type="p.status === '进行中' ? '' : 'success'" effect="plain">{{ p.status || '进行中' }}</el-tag>
                </div>
                <p class="pp-desc">{{ p.description }}</p>
                <div class="pp-bar-row">
                  <span>进度</span>
                  <div class="pp-track"><div class="pp-fill" :style="{width:(p.progress||0)+'%'}"></div></div>
                  <span class="pp-pct">{{ p.progress || 0 }}%</span>
                </div>
              </div>
            </template>
            <div v-else class="panel-empty">
              <svg viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5" width="40" height="40"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              <p>暂无帮扶计划</p>
            </div>
          </div>
        </div>

        <!-- 学业建议 -->
        <div class="panel">
          <div class="panel-head">
            <div class="ph-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="2" width="18" height="18"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              <span>学业建议</span>
            </div>
          </div>
          <div class="panel-body">
            <template v-if="suggestions.length">
              <div v-for="(s,i) in suggestions.slice(0,3)" :key="s.id" class="sug-item" :style="{ animationDelay: i*0.1+'s' }">
                <div class="si-dot" :class="'pri-' + (s.priority || 'medium')"></div>
                <div class="si-content">
                  <div class="si-title">{{ s.title }}</div>
                  <div class="si-text">{{ s.content }}</div>
                </div>
              </div>
            </template>
            <div v-else class="panel-empty">
              <svg viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5" width="40" height="40"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              <p>暂无学业建议</p>
            </div>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="panel">
          <div class="panel-head">
            <div class="ph-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2" width="18" height="18"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
              <span>快捷入口</span>
            </div>
          </div>
          <div class="shortcut-grid">
            <router-link to="/student/scores" class="sc-item">
              <div class="sc-icon sc-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg></div>
              <span>成绩查询</span>
            </router-link>
            <router-link to="/student/warnings" class="sc-item">
              <div class="sc-icon sc-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
              <span>预警管理</span>
            </router-link>
            <router-link to="/student/appeals" class="sc-item">
              <div class="sc-icon sc-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
              <span>成绩申诉</span>
            </router-link>
            <router-link to="/student/messages" class="sc-item">
              <div class="sc-icon sc-purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg></div>
              <span>消息中心</span>
            </router-link>
            <router-link to="/student/benchmark-analysis" class="sc-item">
              <div class="sc-icon sc-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg></div>
              <span>对标分析</span>
            </router-link>
            <router-link to="/student/assistance" class="sc-item">
              <div class="sc-icon sc-teal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg></div>
              <span>帮扶计划</span>
            </router-link>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { ElMessage } from 'element-plus'

// 优先显示真实姓名（登录时存于 'name'）；缺失或为纯数字（学号/userId）时回退，
// 避免把学号当姓名显示——与 StudentLayout.vue 的防护保持一致
const storedName = localStorage.getItem('name') || localStorage.getItem('userName')
const username = ref(storedName && !/^\d+$/.test(storedName) ? storedName : '同学')
const today = new Date().toLocaleDateString('zh-CN', { year:'numeric', month:'long', day:'numeric', weekday:'short' })
const academicData = ref({ gpa:0, totalCourses:0, failedCourses:0, warningCount:0, warningLevel:'正常' })
const classInfo = ref({})
const warnings = ref([])
const assistancePlans = ref([])
const courses = ref([])
const suggestions = ref([])

const colors = ['#7c3aed','#2563eb','#16a34a','#f59e0b','#ef4444','#8b5cf6','#0891b2','#ea580c','#db2777','#4f46e5']
const randomGradient = (id) => {
  const c = colors[(id || 1) % colors.length]
  return `linear-gradient(135deg, ${c}, ${c}dd)`
}

const getGreeting = () => {
  const h = new Date().getHours()
  if (h<9) return '早上好'
  if (h<12) return '上午好'
  if (h<14) return '中午好'
  if (h<18) return '下午好'
  return '晚上好'
}

onMounted(() => loadAll())

async function loadAll() {
  const uid = getUserId(); if (!uid) { ElMessage.error('请先登录'); return }
  const [d,w,a,sc,sg,cl] = await Promise.allSettled([
    loadDashboard(uid), loadWarnings(uid), loadAssistance(uid), loadScores(uid), loadSuggestions(uid), loadClass(uid)
  ])
}

async function loadDashboard(uid) {
  try {
    const res = await studentAPI.getDashboard(uid)
    let data = res; if (res?.code === 200 && res?.data) data = res.data
    if (data && typeof data === 'object') {
      academicData.value = {
        gpa: parseFloat(data.gpa) || 0,
        totalCourses: parseInt(data.totalCoursesCount) || 0,
        failedCourses: parseInt(data.failedCoursesCount) || 0,
        warningCount: parseInt(data.warningCount) || 0,
        warningLevel: (parseInt(data.warningCount) || 0) === 0 ? '正常' : '有预警'
      }
    }
  } catch(e) {}
}

async function loadWarnings(uid) {
  try { const r = await studentAPI.getWarnings(uid); if (Array.isArray(r)) warnings.value = r.slice(0,5) } catch(e){}
}
async function loadAssistance(uid) {
  try { const r = await studentAPI.getAssistancePlans(uid); if (Array.isArray(r)) assistancePlans.value = r.slice(0,5) } catch(e){}
}
async function loadScores(uid) {
  try {
    const r = await studentAPI.getScores(uid)
    const all = Array.isArray(r) ? r : []
    if (all.length) {
      const max = Math.max(...all.map(s=>parseInt(s.semester)||0))
      courses.value = all.filter(s=>parseInt(s.semester)===max).slice(0,6)
    }
  } catch(e){}
}
async function loadSuggestions(uid) {
  try { const r = await studentAPI.getSuggestions(uid); if (Array.isArray(r)) suggestions.value = r.slice(0,3) } catch(e){}
}
async function loadClass(uid) {
  try {
    const r = await studentAPI.getClassInfo(uid)
    let d = r; if (r?.code === 200 && r?.data) d = r.data
    if (d?.classIdentifier) classInfo.value = d
  } catch(e){}
}
</script>

<style scoped>
.dashboard-home { padding: 0; min-height: 100vh; background: #f5f7fb; }

/* ===== HERO ===== */
.hero-section {
  position: relative; overflow: hidden;
  /* ⚠️ 这里**故意**不用 var(--theme-gradient)（2026-09-30 定）。
     学生主题的渐变是绿 #4CAF50→#81C784，但本页是一套刻意的紫/靛配色 ——
     hero 的紫靛、.pp-fill 的 #7c3aed→#a78bfa、.orb-1 的 #a78bfa，
     以及 randomGradient()（Dashboard.vue:249）那条 10 色轮转，彼此是一体的。
     把 hero 单独改成绿色不是修一致性，是重新设计这一屏。
     对照：管理员首页那页是真冲突（hero 蓝 vs 全站 Element Plus 紫），已改，
     见 AdminDashboard.vue。 */
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 30%, #3730a3 60%, #4f46e5 100%);
  padding: 40px 32px 48px; color: #fff;
}
.hero-bg { position: absolute; inset: 0; overflow: hidden; }
.hero-orb { position: absolute; border-radius: 50%; filter: blur(80px); opacity: .15; }
.orb-1 { width: 300px; height: 300px; background: #a78bfa; top: -80px; right: -60px; }
.orb-2 { width: 200px; height: 200px; background: #60a5fa; bottom: -60px; left: 20%; }
.orb-3 { width: 150px; height: 150px; background: #34d399; top: 40%; right: 40%; }
.hero-content { position: relative; z-index: 1; display: flex; justify-content: space-between; align-items: flex-start; gap: 32px; flex-wrap: wrap; }
.hero-left { flex: 1; min-width: 260px; }
.hero-greeting { font-size: 14px; opacity: .8; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
.hero-wave { display: inline-block; animation: wave .8s ease-in-out infinite alternate; }
@keyframes wave { from{transform:rotate(-10deg)} to{transform:rotate(15deg)} }
.hero-name { font-size: 34px; font-weight: 800; margin: 0 0 4px; letter-spacing: -.5px; }
.hero-sub { font-size: 13px; opacity: .65; margin: 0 0 12px; display: flex; align-items: center; gap: 6px; }
.hero-sub svg { flex-shrink: 0; }
.hero-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.htag { padding: 4px 12px; border-radius: 20px; font-size: 12px; background: rgba(255,255,255,.15); backdrop-filter: blur(4px); }
.htag.safe { background: rgba(52,211,153,.25); }

.hero-right { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.hero-stat-card {
  background: rgba(255,255,255,.1); backdrop-filter: blur(12px);
  border-radius: 14px; padding: 18px 20px; display: flex; gap: 14px; align-items: center;
  border: 1px solid rgba(255,255,255,.08); min-width: 140px;
  transition: all .3s;
}
.hero-stat-card:hover { background: rgba(255,255,255,.16); transform: translateY(-2px); }
.hsc-icon { width: 42px; height: 42px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.hsc-icon svg { width: 20px; height: 20px; }
.hsc-icon.gpa { background: rgba(167,139,250,.3); color: #c4b5fd; }
.hsc-icon.credit { background: rgba(96,165,250,.3); color: #93c5fd; }
.hsc-icon.fail { background: rgba(252,165,165,.3); color: #fca5a5; }
.hsc-icon.warn { background: rgba(252,211,77,.3); color: #fcd34d; }
.hsc-val { font-size: 26px; font-weight: 800; }
.hsc-val.red { color: #fca5a5; }
.hsc-label { font-size: 12px; opacity: .65; margin-top: 2px; }

/* ===== 主体 ===== */
.main-body {
  display: grid; grid-template-columns: 1fr 1fr; gap: 18px;
  padding: 20px 24px; position: relative; z-index: 2; margin-top: -8px;
}

/* ===== 面板 ===== */
.panel { background: #fff; border-radius: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.04), 0 4px 16px rgba(0,0,0,.03); overflow: hidden; }
.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18px 22px; border-bottom: 1px solid #f3f4f6;
}
.ph-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #18181b; }
.ph-badge { font-size: 11px; background: #ef4444; color: #fff; border-radius: 10px; padding: 1px 7px; }
.ph-link { font-size: 13px; color: #6b7280; text-decoration: none; display: flex; align-items: center; gap: 4px; transition: color .2s; }
.ph-link:hover { color: #7c3aed; }
.ph-link span { font-size: 16px; }
.panel-body { padding: 8px 22px 18px; }
.panel-empty { text-align: center; padding: 36px 20px; color: #9ca3af; font-size: 13px; }
.panel-empty svg { display: block; margin: 0 auto 10px; }

/* ===== 预警卡片 ===== */
.warn-card {
  display: flex; gap: 0; padding: 14px 0; border-bottom: 1px solid #f3f4f6;
  animation: fadeUp .4s ease both;
}
.warn-card:last-child { border-bottom: none; }
@keyframes fadeUp { from{opacity:0;transform:translateY(8px)} to{} }
.wc-band { width: 3px; border-radius: 2px; flex-shrink: 0; margin-right: 14px; }
.wb-1 { background: #3b82f6; }
.wb-2 { background: #f59e0b; }
.wb-3 { background: #ef4444; }
.wc-main { flex: 1; min-width: 0; }
.wc-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.wc-title { font-size: 14px; font-weight: 500; color: #18181b; }
.wc-badge { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.wbl-1 { background: #eff6ff; color: #3b82f6; }
.wbl-2 { background: #fffbeb; color: #f59e0b; }
.wbl-3 { background: #fef2f2; color: #ef4444; }
.wc-text { margin: 0; font-size: 12px; color: #6b7280; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

/* ===== 课程 ===== */
.course-item {
  display: flex; align-items: center; gap: 14px; padding: 12px 0;
  border-bottom: 1px solid #f3f4f6; animation: fadeUp .4s ease both;
}
.course-item:last-child { border-bottom: none; }
.ci-avatar { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 15px; font-weight: 700; flex-shrink: 0; }
.ci-info { flex: 1; min-width: 0; }
.ci-name { font-size: 14px; font-weight: 500; color: #18181b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ci-meta { font-size: 12px; color: #9ca3af; margin-top: 2px; }
.ci-score { text-align: center; flex-shrink: 0; }
.cis-num { font-size: 18px; font-weight: 700; color: #16a34a; }
.ci-score.fail .cis-num { color: #ef4444; }
.cis-label { font-size: 11px; color: #9ca3af; }

/* ===== 帮扶 ===== */
.plan-panel { padding: 14px 0; border-bottom: 1px solid #f3f4f6; animation: fadeUp .4s ease both; }
.plan-panel:last-child { border-bottom: none; }
.pp-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.pp-name { font-size: 14px; font-weight: 500; color: #18181b; }
.pp-desc { margin: 0 0 10px; font-size: 12px; color: #6b7280; line-height: 1.5; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pp-bar-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #6b7280; }
.pp-track { flex: 1; height: 6px; background: #f3f4f6; border-radius: 3px; overflow: hidden; }
.pp-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg,#7c3aed,#a78bfa); transition: width .5s ease; }
.pp-pct { font-weight: 600; color: #7c3aed; width: 32px; text-align: right; }

/* ===== 建议 ===== */
.sug-item { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f3f4f6; animation: fadeUp .4s ease both; }
.sug-item:last-child { border-bottom: none; }
.si-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; }
.si-dot.pri-high { background: #ef4444; }
.si-dot.pri-medium { background: #f59e0b; }
.si-dot.pri-low { background: #3b82f6; }
.si-content { flex:1; min-width:0; }
.si-title { font-size:13px; font-weight:500; color:#18181b; margin-bottom:2px; }
.si-text { font-size:12px; color:#6b7280; line-height:1.4; }

/* ===== 快捷 ===== */
.shortcut-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; padding: 14px 22px; }
.sc-item { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 16px 8px; border-radius: 12px; text-decoration: none; transition: all .2s; }
.sc-item:hover { background: #f5f3ff; transform: translateY(-2px); }
.sc-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.sc-icon svg { width: 18px; height: 18px; }
.sc-blue { background: #eff6ff; color: #3b82f6; }
.sc-red { background: #fef2f2; color: #ef4444; }
.sc-amber { background: #fffbeb; color: #f59e0b; }
.sc-purple { background: #f5f3ff; color: #7c3aed; }
.sc-green { background: #ecfdf5; color: #16a34a; }
.sc-teal { background: #f0fdfa; color: #14b8a6; }
.sc-item span { font-size: 12px; color: #52525b; font-weight: 500; }

/* ===== 响应式 ===== */
@media (max-width: 1200px) { .hero-right { grid-template-columns: 1fr 1fr; } }
@media (max-width: 900px) {
  .main-body { grid-template-columns: 1fr; }
  .hero-content { flex-direction: column; }
  .hero-right { width: 100%; grid-template-columns: repeat(4,1fr); }
  .hero-name { font-size: 26px; }
}
@media (max-width: 600px) {
  .hero-section { padding: 24px 16px 36px; }
  .hero-right { grid-template-columns: repeat(2,1fr); }
  .hero-name { font-size: 22px; }
  .shortcut-grid { grid-template-columns: 1fr 1fr; }
}
</style>
