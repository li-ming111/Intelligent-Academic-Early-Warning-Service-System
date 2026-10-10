<template>
  <div class="ad-page">
    <!-- Hero -->
    <div class="ad-hero">
      <div class="adh-glow adh-g1"></div>
      <div class="adh-glow adh-g2"></div>
      <div class="adh-inner">
        <div class="adh-left">
          <div class="adh-avatar">A</div>
          <div>
            <h1>{{ greetingText }}，{{ adminName }}</h1>
            <p class="adh-sub">全校数据统计 · 预警系统监控中心</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 指标卡片 -->
    <div class="ad-metrics">
      <div class="adm-card">
        <div class="adm-icon ic-purple"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
        <div class="adm-body"><div class="adm-val">{{ stats.totalStudents }}</div><div class="adm-lbl">在籍学生</div></div>
      </div>
      <div class="adm-card">
        <div class="adm-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 00-3-3.87"/></svg></div>
        <div class="adm-body"><div class="adm-val">{{ stats.totalTeachers }}</div><div class="adm-lbl">教师</div></div>
      </div>
      <div class="adm-card">
        <div class="adm-icon ic-teal"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg></div>
        <div class="adm-body"><div class="adm-val">{{ stats.totalCounselors }}</div><div class="adm-lbl">辅导员</div></div>
      </div>
      <div class="adm-card">
        <div class="adm-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
        <div class="adm-body"><div class="adm-val red">{{ stats.totalWarnings }}</div><div class="adm-lbl">预警总数</div></div>
      </div>
      <div class="adm-card">
        <div class="adm-icon ic-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg></div>
        <div class="adm-body"><div class="adm-val">{{ stats.totalCourses }}</div><div class="adm-lbl">课程</div></div>
      </div>
      <div class="adm-card">
        <div class="adm-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg></div>
        <div class="adm-body"><div class="adm-val">{{ stats.totalColleges }}</div><div class="adm-lbl">学院</div></div>
      </div>
    </div>

    <!-- 双栏 -->
    <div class="ad-main">
      <div class="ad-left">
        <!-- 待处理审批 -->
        <div class="ad-panel">
          <div class="adp-head"><span class="adp-title">待处理审批</span></div>
          <div class="adp-approvals">
            <router-link to="/admin/class-management" class="adpa-item">
              <div class="adpai-icon ic-amber"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
              <div class="adpai-info"><span class="adpai-num">{{ pending.classRequests }}</span><span class="adpai-lbl">班级管理申请</span></div>
            </router-link>
            <router-link to="/admin/warnings" class="adpa-item">
              <div class="adpai-icon ic-red"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
              <div class="adpai-info"><span class="adpai-num red">{{ pending.warnings }}</span><span class="adpai-lbl">待处理预警</span></div>
            </router-link>
            <router-link to="/admin/data-export" class="adpa-item">
              <div class="adpai-icon ic-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/></svg></div>
              <div class="adpai-info"><span class="adpai-num">{{ pending.appeals }}</span><span class="adpai-lbl">成绩申诉</span></div>
            </router-link>
          </div>
        </div>

        <!-- 最新预警 -->
        <div class="ad-panel">
          <div class="adp-head">
            <span class="adp-title">最新预警</span>
            <router-link to="/admin/warnings" class="adp-link">查看全部 →</router-link>
          </div>
          <div class="adw-stats">
            <span class="adws-item r3">严重 {{ warnCounts.severe }}</span>
            <span class="adws-item r2">中度 {{ warnCounts.medium }}</span>
            <span class="adws-item r1">轻度 {{ warnCounts.low }}</span>
            <span class="adws-item ok">已处理 {{ warnCounts.processed }}</span>
          </div>
          <div v-if="warnings.length" class="ad-wlist">
            <div v-for="(w, i) in warnings.slice(0,5)" :key="w.id || i" class="adw-item">
              <div class="adwi-bar" :class="'lvl'+w.warningLevel"></div>
              <div class="adwi-body">
                <div class="adwi-top">
                  <span class="adwi-name">{{ w.studentName }}</span>
                  <span class="adwi-lvl" :class="'b'+w.warningLevel">{{ levelLabel(w.warningLevel) }}</span>
                </div>
                <div class="adwi-meta">{{ w.title || w.warningMessage }} · {{ fmtTime(w.createdAt) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="adp-empty">暂无预警数据</div>
        </div>

      </div>

      <div class="ad-right">
        <!-- 管理入口 -->
        <div class="ad-panel">
          <div class="adp-head"><span class="adp-title">管理入口</span></div>
          <div class="adp-actions">
            <router-link v-for="a in actions" :key="a.key" :to="a.link" class="adpa-btn">
              <span class="adpa-icon" :class="'ci-'+a.color"><span v-html="a.icon"></span></span>
              <span>{{ a.label }}</span>
            </router-link>
          </div>
        </div>

        <!-- 预警类型分布 -->
        <div class="ad-panel">
          <div class="adp-head"><span class="adp-title">预警分布</span></div>
          <div class="adpw-row">
            <span class="adpw-lbl r3">严重</span>
            <div class="adpw-track"><div class="adpw-bar r3" :style="{width:warnBar('red')+'%'}"></div></div>
            <span class="adpw-cnt r3">{{ stats.redWarnings }}</span>
          </div>
          <div class="adpw-row">
            <span class="adpw-lbl r2">中度</span>
            <div class="adpw-track"><div class="adpw-bar r2" :style="{width:warnBar('yellow')+'%'}"></div></div>
            <span class="adpw-cnt r2">{{ stats.yellowWarnings }}</span>
          </div>
          <div class="adpw-row">
            <span class="adpw-lbl r1">轻度</span>
            <div class="adpw-track"><div class="adpw-bar r1" :style="{width:warnBar('low')+'%'}"></div></div>
            <span class="adpw-cnt r1">{{ stats.lowWarnings }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { adminAPI } from '@/api/index'

const stats = reactive({ totalStudents:'--', totalTeachers:'--', totalCounselors:'--', totalCourses:'--', totalMajors:'--', totalWarnings:'--', totalColleges:'--', totalUsers:'--', redWarnings:'--', yellowWarnings:'--', lowWarnings:'--', processedWarnings:'--' })
const warnings = ref([])
const pending = reactive({ classRequests: 0, warnings: 0, appeals: 0 })
const greetingText = (() => { const h = new Date().getHours(); return h<9?'早上好':h<12?'上午好':h<14?'中午好':h<18?'下午好':'晚上好' })()

// 问候语后面接**真实姓名**，取法与侧栏 AdminLayout.vue 完全一致：登录时 Login.vue
// 把 users.name（真名）写进 localStorage 的 'name'。此前这里是写死的「管理员」，
// 与侧栏显示的姓名对不上（活库 admin=「系统管理员」、adm001=「夏志浩」）。
// 只读 'name' 不读 'userName'：全项目没有任何地方给管理员写 'userName'（见 AdminLayout
// 里那段注释）；这里也不额外打 /admin/users/{id} —— 姓名在登录时就已进入 localStorage，
// 多打一次只会让首页与侧栏有可能读到两个不同版本的名字。
const pickName = (v) => (v && !/^\d+$/.test(v) ? v : '')
const adminName = ref(pickName(localStorage.getItem('name')) || '管理员')

// 2026-10-01 第十九轮: 这四枚徽章原来是对 `warnings`(来自 /admin/dashboard 的 recentWarnings,
// 后端只给前 5 条)做 filter 统计。数据一超 5 条,"严重/中度/轻度"就会少算、"已处理"更是把
// 没拿到的那部分当 0。现改为与同页柱状图同源 —— 全量口径的 /admin/statistics。
const warnCounts = computed(() => ({
  severe: Number(stats.redWarnings) || 0,
  medium: Number(stats.yellowWarnings) || 0,
  low: Number(stats.lowWarnings) || 0,
  processed: Number(stats.processedWarnings) || 0,
}))

function warnBar(level) {
  const r = Number(stats.redWarnings)||0, y = Number(stats.yellowWarnings)||0, b = Number(stats.lowWarnings)||0
  const t = r+y+b; if (t===0) return 0
  return Math.round(((level==='red'?r:level==='yellow'?y:b)/t)*100)
}

function levelLabel(l) { return l>=3?'严重':l==2?'中度':'轻度' }
function fmtTime(t) { if (!t) return ''; const d = new Date(t); return d.toLocaleDateString('zh-CN')+' '+d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}) }

const actions = [
  { key:'users', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>', label:'用户管理', link:'/admin/users', color:'p' },
  { key:'colleges', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>', label:'学院管理', link:'/admin/colleges', color:'b' },
  { key:'majors', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>', label:'专业管理', link:'/admin/majors', color:'c' },
  { key:'courses', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>', label:'课程管理', link:'/admin/courses', color:'g' },
  { key:'stats', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>', label:'数据分析', link:'/admin/statistics', color:'r' },
  { key:'rules', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>', label:'规则管理', link:'/admin/rules', color:'a' },
  { key:'permissions', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>', label:'权限管理', link:'/admin/permissions', color:'t' },
  { key:'classReq', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>', label:'班级申请', link:'/admin/class-management', color:'y' },
]

onMounted(async () => {
  try { const r = await adminAPI.getStatistics(); if (r && typeof r === 'object') { stats.totalStudents=r.totalStudents||0; stats.totalTeachers=r.totalTeachers||0; stats.totalCounselors=r.totalCounselors||0; stats.totalCourses=r.totalCourses||0; stats.totalColleges=r.totalColleges||0; stats.totalMajors=r.totalMajors||0; stats.totalUsers=r.totalUsers||0; stats.totalWarnings=r.totalWarnings||0; stats.redWarnings=r.redWarnings||0; stats.yellowWarnings=r.yellowWarnings||0; stats.lowWarnings=r.lowWarnings||0; stats.processedWarnings=r.processedWarnings||0 } } catch(e){}
  // 2026-10-01 第十九轮: 这里原先把 /admin/dashboard 的 `totalWarnings` 当作"待处理预警"。
  // 那个数是**全部行数**(后端没按 status 过滤), 而且"待处理"的正确来源是
  // /admin/pending-requests 的 `pendingWarnings`(那边是 eq(status,0))。已改到下面那行取。
  try { const d = await adminAPI.getDashboard(); if (d) { const wl = d.recentWarnings || d.warnings || []; warnings.value = (Array.isArray(wl)?wl:[]).slice(0,20) } } catch(e){}
  try { const pr = await adminAPI.getPendingRequests(); if (pr) { pending.classRequests=pr.classRequests||0; pending.appeals=pr.appeals||0; pending.warnings=pr.pendingWarnings||0 } } catch(e){}
})
</script>

<style scoped>
.ad-page { padding: 0 0 24px; min-height: 100vh; background: #f0f4ff; }

/* Hero
   走 --theme-gradient（由 App.vue 按角色写入 :root），兜底取 admin 主题自己的
   紫渐变，与 config/theme.js 的 admin.gradient 逐字一致 —— 与学院管理员那 9 个
   页面的写法相同。
   原先这里硬编码 linear-gradient(135deg, #1e3a8a, #2563eb, #3b82f6)，是重设计前
   那套蓝。它与全站不一致：role=4 的 Element Plus 主色是 theme.js 里的 #667eea，
   main.js 启动时就写到 :root 了，所以管理员页面上每个按钮、链接、页签本来就是
   紫的，只有这一页的 hero 是蓝的。（同一个三元组此前还作为兜底值挂在学院管理员
   的 9 个页面上，已在 ff7c2d9 清掉。） */
.ad-hero { position: relative; background: var(--theme-gradient, linear-gradient(135deg, #667eea 0%, #764ba2 100%)); padding: 32px 36px; overflow: hidden; }
.adh-glow { position: absolute; border-radius: 50%; filter: blur(80px); pointer-events: none; }
/* 光斑跟着主题走：原来用的是 #3b82f6 / #60a5fa，蓝色抹在紫渐变上会发灰 */
.adh-g1 { width: 260px; height: 260px; background: #764ba2; top: -70px; right: -50px; opacity: .25; }
.adh-g2 { width: 180px; height: 180px; background: #869af5; bottom: -50px; left: 20%; opacity: .18; }
.adh-inner { position: relative; z-index: 1; display: flex; justify-content: space-between; align-items: center; }
.adh-left { display: flex; align-items: center; gap: 14px; }
.adh-avatar { width: 48px; height: 48px; border-radius: 14px; background: rgba(255,255,255,.2); backdrop-filter: blur(8px); display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; color: #fff; flex-shrink: 0; }
.adh-left h1 { margin: 0; font-size: 20px; color: #fff; font-weight: 700; }
.adh-sub { margin: 2px 0 0; font-size: 12px; color: rgba(255,255,255,.6); }

/* Metrics */
.ad-metrics { display: grid; grid-template-columns: repeat(6,1fr); gap: 14px; padding: 20px 24px; }
.adm-card { background: #fff; border-radius: 14px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.04); display: flex; align-items: center; gap: 14px; transition: all .2s; }
.adm-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,.08); }
.adm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.adm-icon svg { width: 18px; height: 18px; }
.ic-purple { background:#f5f3ff; color:#7c3aed; } .ic-blue { background:#eff6ff; color:#2563eb; }
.ic-teal { background:#f0fdfa; color:#0d9488; } .ic-red { background:#fef2f2; color:#ef4444; }
.ic-green { background:#ecfdf5; color:#16a34a; } .ic-amber { background:#fffbeb; color:#d97706; }
.adm-val { font-size: 22px; font-weight: 700; color: #1e293b; } .adm-val.red { color: #ef4444; }
.adm-lbl { font-size: 12px; color: #94a3b8; }

/* Main */
.ad-main { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; padding: 0 24px 24px; }
.ad-panel { background: #fff; border-radius: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; margin-bottom: 18px; }
.adp-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 22px; border-bottom: 1px solid #f1f5f9; }
.adp-title { font-size: 14px; font-weight: 600; color: #1e293b; }
.adp-link { font-size: 12px; color: #2563eb; text-decoration: none; } .adp-link:hover { text-decoration: underline; }
.adp-empty { text-align: center; padding: 32px; font-size: 13px; color: #94a3b8; }

/* Approvals */
.adp-approvals { display: grid; grid-template-columns: repeat(3,1fr); gap: 10px; padding: 16px 20px; }
.adpa-item { display: flex; align-items: center; gap: 10px; padding: 14px; background: #f8fafc; border-radius: 10px; text-decoration: none; transition: all .15s; }
.adpa-item:hover { background: #eff6ff; transform: translateY(-1px); }
.adpai-icon { width: 36px; height: 36px; border-radius: 9px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.adpai-icon svg { width: 17px; height: 17px; }
.adpai-icon.ic-amber { background:#fefce8; color:#ca8a04; } .adpai-icon.ic-red { background:#fef2f2; color:#ef4444; }
.adpai-icon.ic-blue { background:#eff6ff; color:#2563eb; }
.adpai-num { font-size: 16px; font-weight: 700; color:#1e293b; } .adpai-num.red { color:#ef4444; }
.adpai-lbl { font-size: 11px; color:#94a3b8; display: block; }

/* Warning stats */
.adw-stats { display: flex; gap: 8px; padding: 10px 20px; background: #f8fafc; }
.adws-item { font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 4px; }
.adws-item.r3 { background:#fef2f2; color:#ef4444; } .adws-item.r2 { background:#fffbeb; color:#d97706; }
.adws-item.r1 { background:#eff6ff; color:#2563eb; } .adws-item.ok { background:#ecfdf5; color:#16a34a; }

/* Warnings list */
.ad-wlist { padding: 4px 20px; }
.adw-item { display: flex; gap: 10px; padding: 11px 0; border-bottom: 1px solid #f1f5f9; }
.adw-item:last-child { border-bottom: none; }
.adwi-bar { width: 3px; border-radius: 2px; align-self: stretch; flex-shrink: 0; }
.adwi-bar.lvl3 { background:#ef4444; } .adwi-bar.lvl2 { background:#f59e0b; } .adwi-bar.lvl1 { background:#3b82f6; }
.adwi-body { flex: 1; min-width: 0; }
.adwi-top { display: flex; align-items: center; gap: 6px; margin-bottom: 3px; }
.adwi-name { font-size: 13px; font-weight: 500; color: #1e293b; }
.adwi-lvl { font-size: 10px; padding: 1px 7px; border-radius: 8px; font-weight: 600; }
.adwi-lvl.b3 { background:#fef2f2; color:#ef4444; } .adwi-lvl.b2 { background:#fffbeb; color:#d97706; } .adwi-lvl.b1 { background:#eff6ff; color:#2563eb; }
.adwi-meta { font-size: 12px; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Quick actions */
.adp-actions { display: grid; grid-template-columns: repeat(3,1fr); gap: 8px; padding: 12px 16px 16px; }
.adpa-btn { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px 8px; border-radius: 10px; text-decoration: none; background: #f8fafc; transition: all .2s; }
.adpa-btn:hover { background: #eff6ff; transform: translateY(-2px); }
.adpa-icon { width: 34px; height: 34px; border-radius: 9px; display: flex; align-items: center; justify-content: center; }
.ci-p { background:#f5f3ff; color:#7c3aed; } .ci-b { background:#eff6ff; color:#2563eb; }
.ci-c { background:#cffafe; color:#0891b2; } .ci-g { background:#ecfdf5; color:#16a34a; }
.ci-r { background:#fef2f2; color:#ef4444; } .ci-a { background:#fffbeb; color:#d97706; }
.ci-t { background:#f0fdfa; color:#0d9488; } .ci-y { background:#fefce8; color:#ca8a04; }
.adpa-btn > span:last-child { font-size: 11px; color: #475569; font-weight: 500; }

/* Warning distribution */
.adpw-row { display: flex; align-items: center; gap: 10px; padding: 10px 22px; }
.adpw-row:last-child { padding-bottom: 16px; }
.adpw-lbl { font-size: 12px; font-weight: 600; min-width: 32px; }
.adpw-lbl.r3 { color:#ef4444; } .adpw-lbl.r2 { color:#d97706; } .adpw-lbl.r1 { color:#2563eb; }
.adpw-track { flex: 1; height: 6px; background: #f1f5f9; border-radius: 3px; overflow: hidden; }
.adpw-bar { height: 100%; border-radius: 3px; transition: width .6s; }
.adpw-bar.r3 { background:#ef4444; } .adpw-bar.r2 { background:#f59e0b; } .adpw-bar.r1 { background:#3b82f6; }
.adpw-cnt { font-size: 14px; font-weight: 700; min-width: 24px; text-align: right; }
.adpw-cnt.r3 { color:#ef4444; } .adpw-cnt.r2 { color:#d97706; } .adpw-cnt.r1 { color:#2563eb; }

@media (max-width:1200px) { .ad-metrics { grid-template-columns: repeat(3,1fr); } }
@media (max-width:900px) { .ad-metrics { grid-template-columns: repeat(2,1fr); } .ad-main { grid-template-columns: 1fr; } }
@media (max-width:600px) { .ad-hero { padding: 24px 20px; } .ad-metrics { padding: 16px; } .ad-main { padding: 0 16px 24px; } .adp-approvals { grid-template-columns: 1fr; } .adp-actions { grid-template-columns: repeat(2,1fr); } }
</style>
