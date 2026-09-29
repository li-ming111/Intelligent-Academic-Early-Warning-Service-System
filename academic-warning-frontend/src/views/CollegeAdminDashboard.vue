<template>
  <div class="cad-page">
    <!-- Hero -->
    <div class="cad-hero">
      <div class="cad-hglow cad-hg1"></div><div class="cad-hglow cad-hg2"></div>
      <div class="cad-hinner">
        <div class="cad-hleft">
          <div class="cad-havatar">{{ collegeName ? collegeName.charAt(0) : '院' }}</div>
          <div>
            <h1>{{ greetingText }}，{{ profile.name || '学院管理员' }}</h1>
            <p class="cad-hsub">{{ collegeName || '学院' }} · 管理控制台</p>
          </div>
        </div>
        <div class="cad-hright">
          <div class="cad-hstat"><span class="cad-hsnum">{{ dateStr }}</span><span class="cad-hslbl">今日日期</span></div>
        </div>
      </div>
    </div>

    <!-- 6 指标卡片 -->
    <div class="cad-metrics">
      <router-link v-for="m in metrics" :key="m.label" :to="m.link" class="cadm-card">
        <div class="cadm-icon" :class="m.cls"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path :d="m.path"/></svg></div>
        <div class="cadm-body"><div class="cadm-val" :class="m.color">{{ m.val }}</div><div class="cadm-lbl">{{ m.label }}</div></div>
      </router-link>
    </div>

    <!-- 双栏主体 -->
    <div class="cad-main">
      <!-- 左栏：预警 + 成绩概要 -->
      <div class="cad-left">
        <div class="cad-panel">
          <div class="cadp-head">
            <span class="cadp-title">最新预警</span>
            <router-link to="/college-admin/warnings" class="cadp-link">查看全部 →</router-link>
          </div>
          <div v-if="recentWarnings.length" class="cadw-list">
            <div v-for="w in recentWarnings" :key="w.id" class="cadw-item">
              <el-tag :type="(w.warningLevel||w.level||0)>=3?'danger':(w.warningLevel||w.level||0)>=2?'warning':'info'" size="small">{{ lvlLabel(w.warningLevel||w.level) }}</el-tag>
              <span class="cadw-name">{{ w.studentName }}</span>
              <span class="cadw-cls">{{ w.className }}</span>
              <span class="cadw-desc">{{ w.description }}</span>
              <span class="cadw-time">{{ fmtDate(w.createdAt) }}</span>
            </div>
          </div>
          <div v-else class="cadp-empty">暂无预警，一切正常</div>
        </div>

        <div class="cad-panel">
          <div class="cadp-head">
            <span class="cadp-title">全院成绩概要</span>
            <router-link to="/college-admin/analysis" class="cadp-link">详细分析 →</router-link>
          </div>
          <div class="cads-summary">
            <div class="cadss-item"><div class="cadss-val green">{{ stats.avgScore }}</div><div class="cadss-lbl">平均分</div></div>
            <div class="cadss-item"><div class="cadss-val amber">{{ stats.passRate }}%</div><div class="cadss-lbl">及格率</div></div>
            <div class="cadss-item"><div class="cadss-val blue">{{ stats.totalStudents }}</div><div class="cadss-lbl">在籍学生</div></div>
          </div>
          <div v-if="scoreDist.length" class="cads-dist">
            <div v-for="d in scoreDist" :key="d.label" class="cadd-row">
              <span class="cadd-lbl">{{ d.label }}</span>
              <div class="cadd-track"><div class="cadd-bar" :style="{ width: d.pct+'%', background: d.color }"></div></div>
              <span class="cadd-cnt">{{ d.count }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：管理入口 -->
      <div class="cad-right">
        <div class="cad-panel">
          <div class="cadp-head"><span class="cadp-title">快捷管理入口</span></div>
          <div class="cadp-actions">
            <router-link v-for="a in quickActions" :key="a.key" :to="a.link" class="cadpa-btn">
              <span class="cadpa-icon" :class="'ci-'+a.color"><span v-html="a.icon"></span></span>
              <span>{{ a.label }}</span>
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { collegeAdminAPI } from '@/api/index'

// 首屏先用登录时写入的 'name'（Login.vue 存的是真名）。
// 只靠 getProfile 接口填不行：接口返回前 profile.name 是空的，
// 标题会先闪一下兜底文案「学院管理员」再变真名。
const pickName = (v) => (v && !/^\d+$/.test(v) ? v : '')
const profile = reactive({ name: pickName(localStorage.getItem('name')), collegeId: null })
const collegeName = ref('')
const stats = reactive({ totalStudents:'--', totalTeachers:'--', totalClasses:'--', totalWarnings:'--', avgScore:'--', passRate:'--' })
const recentWarnings = ref([])
const scoreDist = ref([])
const greetingText = (()=>{ const h=new Date().getHours(); return h<9?'早上好':h<12?'上午好':h<14?'中午好':h<18?'下午好':'晚上好' })()
const dateStr = new Date().toLocaleDateString('zh-CN', { year:'numeric', month:'2-digit', day:'2-digit' })

const metrics = computed(()=>[
  { label:'在籍学生', val:stats.totalStudents, link:'/college-admin/students',   cls:'ic-purple', path:'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2 M9 7a4 4 0 100 8 4 4 0 000-8z' },
  { label:'教师',     val:stats.totalTeachers, link:'/college-admin/teachers',   cls:'ic-blue',   path:'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2 M22 21v-2a4 4 0 00-3-3.87' },
  { label:'班级',     val:stats.totalClasses,  link:'/college-admin/classes',    cls:'ic-teal',   path:'M2 3h20v14a2 2 0 01-2 2H4a2 2 0 01-2-2V3z M8 21h8 M12 17v4' },
  { label:'预警',     val:stats.totalWarnings, link:'/college-admin/warnings',   cls:'ic-red',    path:'M12 2a10 10 0 100 20 10 10 0 000-20z M12 8v4 M12 16h.01', color:'red' },
  { label:'平均成绩', val:stats.avgScore,      link:'/college-admin/analysis',   cls:'ic-green',  path:'M22 12l-4-4v3H3v2h15v3l4-4z' },
  { label:'及格率',   val:stats.passRate+'%',  link:'/college-admin/analysis',   cls:'ic-amber',  path:'M9 12l2 2 4-4 M12 2a10 10 0 100 20 10 10 0 000-20z', color:'#d97706' },
])

const quickActions = [
  { key:'students',   icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>', label:'学生管理', link:'/college-admin/students',   color:'p' },
  { key:'teachers',   icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 00-3-3.87"/></svg>', label:'教师管理', link:'/college-admin/teachers',   color:'b' },
  { key:'scores',     icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>', label:'成绩查询', link:'/college-admin/scores',     color:'s' },
  { key:'analysis',   icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>', label:'成绩分析', link:'/college-admin/analysis',   color:'t' },
  { key:'warnings',   icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>', label:'预警管理', link:'/college-admin/warnings',   color:'r' },
  { key:'classes',    icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg>', label:'班级管理', link:'/college-admin/classes',    color:'g' },
  { key:'counselors', icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>', label:'辅导员',   link:'/college-admin/counselors', color:'c' },
  { key:'export',     icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>', label:'数据导出', link:'/college-admin/export',     color:'y' },
]

function fmtDate(t){ if(!t)return'';try{return new Date(t).toLocaleDateString('zh-CN')}catch(e){return String(t).substring(0,10)}}
function lvlLabel(l){ const v=Number(l)||0; return v>=3?'严重':v==2?'中度':'轻度' }

onMounted(async()=>{
  const uid=localStorage.getItem('userId');if(!uid)return
  try{const p=await collegeAdminAPI.getProfile(uid);if(p){profile.name=pickName(p.name)||profile.name;profile.collegeId=p.college_id;collegeName.value=p.college_name||'本院'}}catch(e){}
  try{
    const d=await collegeAdminAPI.getDashboard(uid)
    if(d){stats.totalStudents=d.studentCount||0;stats.totalTeachers=d.teacherCount||0;stats.totalClasses=d.classCount||0;stats.totalWarnings=d.warningCount||0}
  }catch(e){}
  try{const sa=await collegeAdminAPI.getScoreStats(uid);if(sa){stats.avgScore=sa.avgScore??'--';stats.passRate=sa.passRate??'--'}}catch(e){}
  try{
    const w=await collegeAdminAPI.getWarnings(uid)||[]
    recentWarnings.value=w.slice(0,6)
  }catch(e){}
  try{
    const r=await collegeAdminAPI.getScoreAnalysis(uid)
    if(r&&r.distribution){
      const dist=r.distribution
      const colors={'90-100':'#16a34a','80-89':'#3b82f6','70-79':'#0891b2','60-69':'#d97706','0-59':'#ef4444'}
      const total=Object.values(dist).reduce((a,b)=>a+b,0)||1
      scoreDist.value=Object.entries(dist).map(([k,v])=>({label:k,count:v,pct:Math.round(v*100/total),color:colors[k]||'#94a3b8'}))
    }
  }catch(e){}
})
</script>

<style scoped>
.cad-page { padding: 20px; min-height: 100vh; background: #f8f9fa; }
.cad-hero { position: relative; background: linear-gradient(135deg, #1e3a8a, #2563eb, #3b82f6); border-radius: 10px; padding: 32px 36px; overflow: hidden; margin-bottom: 20px; }
.cad-hglow { position: absolute; border-radius: 50%; filter: blur(80px); pointer-events: none; }
.cad-hg1 { width: 260px; height: 260px; background: #3b82f6; top: -70px; right: -50px; opacity: .25; }
.cad-hg2 { width: 180px; height: 180px; background: #60a5fa; bottom: -50px; left: 20%; opacity: .18; }
.cad-hinner { position: relative; z-index: 1; display: flex; justify-content: space-between; align-items: center; }
.cad-hleft { display: flex; align-items: center; gap: 14px; }
.cad-havatar { width: 48px; height: 48px; border-radius: 14px; background: rgba(255,255,255,.2); backdrop-filter: blur(8px); display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; color: #fff; flex-shrink: 0; }
.cad-hleft h1 { margin: 0; font-size: 20px; color: #fff; font-weight: 700; }
.cad-hsub { margin: 2px 0 0; font-size: 12px; color: rgba(255,255,255,.6); }
.cad-hright { text-align: right; }
.cad-hstat { display: flex; flex-direction: column; align-items: flex-end; }
.cad-hsnum { font-size: 13px; color: rgba(255,255,255,.9); font-weight: 600; }
.cad-hslbl { font-size: 10px; color: rgba(255,255,255,.5); margin-top: 2px; }

/* 指标卡片 */
.cad-metrics { display: grid; grid-template-columns: repeat(6, 1fr); gap: 14px; margin-bottom: 20px; }
.cadm-card { background: #fff; border-radius: 10px; padding: 18px 14px; box-shadow: 0 2px 8px rgba(0,0,0,.08); display: flex; align-items: center; gap: 12px; transition: all .2s; text-decoration: none; }
.cadm-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,.1); }
.cadm-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.cadm-icon svg { width: 18px; height: 18px; }
.ic-purple { background: #f5f3ff; color: #7c3aed; } .ic-blue { background: #eff6ff; color: #2563eb; }
.ic-teal { background: #f0fdfa; color: #0d9488; } .ic-red { background: #fef2f2; color: #ef4444; }
.ic-green { background: #ecfdf5; color: #16a34a; } .ic-amber { background: #fffbeb; color: #d97706; }
.cadm-val { font-size: 22px; font-weight: 700; color: #1e293b; } .cadm-val.red { color: #ef4444; }
.cadm-lbl { font-size: 12px; color: #94a3b8; }

/* 双栏主体 */
.cad-main { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.cad-panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,.08); overflow: hidden; margin-bottom: 18px; }
.cadp-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.cadp-title { font-size: 14px; font-weight: 600; color: #333; }
.cadp-link { font-size: 12px; color: #2563eb; text-decoration: none; }
.cadp-empty { text-align: center; padding: 32px 20px; font-size: 13px; color: #94a3b8; }

/* 预警列表 */
.cadw-list { padding: 8px 0; }
.cadw-item { display: flex; align-items: center; gap: 8px; padding: 9px 20px; border-bottom: 1px solid #f8fafc; font-size: 13px; transition: background .15s; }
.cadw-item:hover { background: #f8fafc; }
.cadw-name { font-weight: 600; color: #1e293b; min-width: 50px; }
.cadw-cls { color: #64748b; font-size: 12px; min-width: 80px; }
.cadw-desc { flex: 1; color: #475569; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cadw-time { color: #94a3b8; font-size: 11px; white-space: nowrap; }

/* 入口按钮 */
.cadp-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding: 14px 16px 18px; }
.cadpa-btn { display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 14px 6px; border-radius: 10px; text-decoration: none; background: #f8fafc; transition: all .2s; }
.cadpa-btn:hover { background: #eff6ff; transform: translateY(-2px); }
.cadpa-icon { width: 36px; height: 36px; border-radius: 8px; display: flex; align-items: center; justify-content: center; }
.ci-p { background: #f5f3ff; color: #7c3aed; } .ci-b { background: #eff6ff; color: #2563eb; }
.ci-r { background: #fef2f2; color: #ef4444; } .ci-g { background: #ecfdf5; color: #16a34a; }
.ci-t { background: #f0fdfa; color: #0d9488; } .ci-c { background: #cffafe; color: #0891b2; }
.ci-y { background: #fefce8; color: #ca8a04; } .ci-s { background: #e8f5e9; color: #2e7d32; }
.cadpa-btn > span:last-child { font-size: 12px; color: #475569; font-weight: 500; }

/* 成绩概要 */
.cads-summary { display: flex; padding: 16px 20px; gap: 0; }
.cadss-item { flex: 1; text-align: center; }
.cadss-val { font-size: 24px; font-weight: 700; }
.cadss-val.green { color: #16a34a; } .cadss-val.amber { color: #d97706; } .cadss-val.blue { color: #2563eb; }
.cadss-lbl { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.cads-dist { padding: 0 20px 16px; }
.cadd-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.cadd-lbl { font-size: 11px; font-weight: 600; min-width: 44px; color: #475569; }
.cadd-track { flex: 1; height: 6px; background: #f1f5f9; border-radius: 3px; overflow: hidden; }
.cadd-bar { height: 100%; border-radius: 3px; transition: width .6s; min-width: 2px; }
.cadd-cnt { font-size: 12px; font-weight: 700; min-width: 22px; text-align: right; color: #1e293b; }

@media (max-width:1100px) { .cad-main { grid-template-columns: 1fr; } .cad-metrics { grid-template-columns: repeat(3,1fr); } }
@media (max-width:768px) { .cad-metrics { grid-template-columns: repeat(2,1fr); } .cad-hero { padding: 20px; } .cadp-actions { grid-template-columns: repeat(2,1fr); } }
</style>
