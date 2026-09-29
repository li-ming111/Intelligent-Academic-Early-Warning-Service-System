<template>
  <view class="page">
    <!-- Hero -->
    <view class="hero">
      <view class="hero-row">
        <view class="hero-left">
          <view class="hero-av">院</view>
          <view>
            <text class="hero-name">{{ profile.college_name || '学院' }}</text>
            <text class="hero-role">{{ profile.title || '学院管理员' }}</text>
          </view>
        </view>
        <view class="hero-logout" @tap="handleLogout">
          <text class="logout-icon">⇤</text>
        </view>
      </view>
    </view>

    <!-- 统计卡片 -->
    <view class="section-pad">
      <view class="stats-grid">
        <view class="st-card" @tap="switchTab('students')">
          <text class="st-num blue">{{ stats.studentCount }}</text>
          <text class="st-lbl">学生</text>
        </view>
        <view class="st-card" @tap="switchTab('classes')">
          <text class="st-num green">{{ stats.classCount }}</text>
          <text class="st-lbl">班级</text>
        </view>
        <view class="st-card" @tap="switchTab('teachers')">
          <text class="st-num purple">{{ stats.teacherCount }}</text>
          <text class="st-lbl">教师</text>
        </view>
        <view class="st-card" @tap="switchTab('warnings')">
          <text class="st-num red">{{ stats.warningCount }}</text>
          <text class="st-lbl">预警</text>
        </view>
        <view class="st-card">
          <text class="st-num cyan">{{ stats.majorCount }}</text>
          <text class="st-lbl">专业</text>
        </view>
        <view class="st-card">
          <text class="st-num pink">{{ stats.avgScore || '--' }}</text>
          <text class="st-lbl">均分</text>
        </view>
      </view>
    </view>

    <!-- 标签切换 -->
    <view class="section">
      <view class="tab-bar">
        <view v-for="t in tabs" :key="t.key" class="tab-item" :class="{active: activeTab === t.key}" @tap="switchTab(t.key)">
          <text>{{ t.label }}</text>
        </view>
      </view>
    </view>

    <!-- 内容区 -->
    <view class="section" v-if="activeTab">
      <text class="section-title">{{ activeLabel }}</text>

      <!-- 成绩分析 -->
      <view v-if="activeTab === 'analysis' && analysis" class="analysis-box">
        <view class="ana-row">
          <view class="ana-item"><text class="ana-label">平均分</text><text class="ana-val">{{ analysis.avgScore || '--' }}</text></view>
          <view class="ana-item"><text class="ana-label">及格率</text><text class="ana-val green">{{ analysis.passRate || '--' }}%</text></view>
          <view class="ana-item"><text class="ana-label">总记录</text><text class="ana-val">{{ analysis.totalRecords || 0 }}</text></view>
        </view>
        <view v-for="(v, k) in analysis.distribution" :key="k" class="dist-row">
          <text class="dr-label">{{ k }}</text>
          <view class="dr-track">
            <view class="dr-fill" :style="{width: maxPct(v, analysis.totalRecords) + '%'}"></view>
          </view>
          <text class="dr-val">{{ v }}人</text>
        </view>
      </view>

      <!-- 班级对比 -->
      <view v-else-if="activeTab === 'comparison'">
        <view v-for="r in tabData" :key="r.className" class="content-card">
          <text class="cc-name">{{ r.className }}</text>
          <view class="cc-stats">
            <text class="ccs">均分 {{ r.avgScore || '--' }}</text>
            <text class="ccs">及格率 {{ r.passRate || '--' }}%</text>
            <text class="ccs">{{ r.studentCount || 0 }}人</text>
          </view>
        </view>
      </view>

      <!-- 默认列表 -->
      <view v-else>
        <view v-if="!tabData.length" class="empty-inline">暂无数据</view>
        <view v-for="item in tabData" :key="item.id || item.name" class="content-card">
          <template v-if="activeTab === 'students'">
            <text class="cc-name">{{ item.name || '--' }}</text>
            <text class="cc-sub">{{ item.className || '' }}</text>
          </template>
          <template v-else-if="activeTab === 'teachers'">
            <text class="cc-name">{{ item.name || '--' }}</text>
            <text class="cc-sub">{{ item.title || '' }}</text>
          </template>
          <template v-else-if="activeTab === 'warnings'">
            <text class="cc-name">{{ item.studentName }}</text>
            <text class="cc-sub">{{ item.description }}</text>
            <!-- 三级预警原来被压成两级（1 级也显示"中度"），与其它页面口径不一致 -->
            <text class="cc-tag" :class="warnCls(item.warningLevel ?? item.level)">{{ warnText(item.warningLevel ?? item.level) }}</text>
          </template>
          <template v-else-if="activeTab === 'counselors'">
            <text class="cc-name">{{ item.name }}</text>
            <text class="cc-sub">管理 {{ item.classCount || 0 }} 个班</text>
          </template>
          <template v-else-if="activeTab === 'assistance'">
            <text class="cc-name">{{ item.studentName }}</text>
            <text class="cc-sub">{{ item.description || '' }}</text>
          </template>
          <template v-else>
            <text class="cc-name">{{ item.name || item.className || '--' }}</text>
          </template>
        </view>
      </view>
    </view>
    <view class="empty-tip" v-else>
      <text class="empty-icon">👆</text>
      <text class="empty-text">点击上方标签查看详情</text>
    </view>

    <collegeadmin-tab-bar current="pages/college-admin/dashboard" />
  </view>
</template>

<script>
import { collegeAdminAPI } from '@/services/api.js'
import { warningLevel, warningLevelText } from '@/utils/score.js'

export default {
  data() {
    return {
      profile: {},
      stats: { studentCount: 0, classCount: 0, teacherCount: 0, warningCount: 0, majorCount: 0, avgScore: 0 },
      activeTab: '',
      tabData: [],
      analysis: null,
      tabs: [
        { key: 'students', label: '学生' }, { key: 'teachers', label: '教师' },
        { key: 'warnings', label: '预警' }, { key: 'classes', label: '班级' },
        { key: 'analysis', label: '成绩分析' }, { key: 'comparison', label: '班级对比' },
        { key: 'counselors', label: '辅导员' }, { key: 'assistance', label: '帮扶' },
      ]
    }
  },
  computed: {
    activeLabel() {
      const t = this.tabs.find(t => t.key === this.activeTab)
      return t ? t.label : ''
    }
  },
  onShow() { this.loadDashboard() },
  methods: {
    // 等级映射收敛到 utils/score.js，与其它端同一口径
    warnCls(l) { const lv = warningLevel(l); return lv >= 3 ? 'red' : lv === 2 ? 'yellow' : lv === 1 ? 'blue' : 'blue' },
    warnText(l) { return warningLevelText(l) },

    async loadDashboard() {
      try {
        const uid = uni.getStorageSync('userId'); if (!uid) return
        const [d, s] = await Promise.all([
          collegeAdminAPI.getDashboard(uid),
          collegeAdminAPI.getScoreStats(uid).catch(() => null)
        ])
        if (d) { this.profile = d.profile || {}; Object.assign(this.stats, d) }
        if (s) this.stats.avgScore = s.avgScore || 0
      } catch (e) {}
    },
    async switchTab(tab) {
      if (this.activeTab === tab) { this.activeTab = ''; return }
      this.activeTab = tab
      const uid = uni.getStorageSync('userId'); if (!uid) return
      try {
        if (tab === 'analysis') {
          this.analysis = await collegeAdminAPI.getScoreAnalysis(uid)
        } else {
          const switchers = {
            students: collegeAdminAPI.getStudents,
            teachers: collegeAdminAPI.getTeachers,
            warnings: collegeAdminAPI.getWarnings,
            classes: collegeAdminAPI.getClasses,
            comparison: collegeAdminAPI.getClassComparison,
            counselors: collegeAdminAPI.getCounselors,
            assistance: collegeAdminAPI.getAssistance
          }
          if (switchers[tab]) {
            const r = await switchers[tab](uid)
            this.tabData = Array.isArray(r) ? r : (r?.data || [])
          }
        }
      } catch (e) { this.tabData = []; this.analysis = null }
    },
    maxPct(v, total) { return total ? Math.max(2, v / total * 100) : 0 },
    handleLogout() {
      ['token', 'userId', 'role'].forEach(k => uni.removeStorageSync(k))
      uni.reLaunch({ url: '/pages/auth/login' })
    }
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f0f2f8; padding-bottom: 120rpx; }

/* Hero - 深紫渐变色（区别于辅导员和教师 */
.hero { background: linear-gradient(135deg, #0f766e 0%, #0891b2 100%); padding: 44rpx 28rpx 40rpx 32rpx; }
.hero-row { display: flex; justify-content: space-between; align-items: center; }
.hero-left { display: flex; align-items: center; gap: 16rpx; }
.hero-av { width: 72rpx; height: 72rpx; border-radius: 50%; background: rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center; font-size: 30rpx; font-weight: 700; color: #fff; flex-shrink: 0; }
.hero-name { font-size: 32rpx; font-weight: 700; color: #fff; display: block; }
.hero-role { font-size: 22rpx; color: rgba(255,255,255,0.65); margin-top: 4rpx; display: block; }
.hero-logout { width: 60rpx; height: 60rpx; border-radius: 50%; background: rgba(255,255,255,0.15); display: flex; align-items: center; justify-content: center; }
.logout-icon { font-size: 40rpx; color: #fff; transform: rotate(180deg); }

.section-pad { padding: 0 20rpx; margin-top: -16rpx; position: relative; z-index: 2; }
.section { padding: 0 20rpx; margin-bottom: 18rpx; }
.section-title { font-size: 28rpx; font-weight: 700; color: #1e293b; display: block; margin-bottom: 16rpx; }

.stats-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12rpx; }
.st-card { background: #fff; border-radius: 14rpx; padding: 20rpx 10rpx; text-align: center; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04); }
.st-num { font-size: 30rpx; font-weight: 800; color: #1e293b; display: block; }
.st-num.blue { color: #2563eb; } .st-num.green { color: #16a34a; } .st-num.purple { color: #7c3aed; } .st-num.red { color: #ef4444; } .st-num.cyan { color: #0891b2; } .st-num.pink { color: #db2777; }
.st-lbl { font-size: 20rpx; color: #94a3b8; }

.tab-bar { display: flex; flex-wrap: wrap; gap: 10rpx; }
.tab-item { padding: 12rpx 22rpx; background: #fff; border-radius: 10rpx; font-size: 24rpx; color: #475569; border: 2rpx solid transparent; }
.tab-item.active { border-color: #2563eb; color: #2563eb; background: #eff6ff; font-weight: 600; }

.analysis-box { background: #fff; border-radius: 14rpx; padding: 24rpx; }
.ana-row { display: flex; gap: 20rpx; margin-bottom: 20rpx; }
.ana-item { flex: 1; text-align: center; }
.ana-label { font-size: 20rpx; color: #94a3b8; display: block; margin-bottom: 4rpx; }
.ana-val { font-size: 26rpx; font-weight: 700; color: #1e293b; display: block; }
.ana-val.green { color: #16a34a; }
.dist-row { display: flex; align-items: center; gap: 12rpx; margin-bottom: 10rpx; }
.dr-label { width: 130rpx; font-size: 22rpx; color: #64748b; text-align: right; flex-shrink: 0; }
.dr-track { flex: 1; height: 22rpx; background: #f1f5f9; border-radius: 11rpx; overflow: hidden; }
.dr-fill { height: 100%; background: linear-gradient(90deg, #1d4ed8, #2563eb); border-radius: 11rpx; }
.dr-val { width: 50rpx; font-size: 22rpx; color: #64748b; text-align: center; flex-shrink: 0; }

.content-card { background: #fff; border-radius: 12rpx; padding: 18rpx 20rpx; margin-bottom: 10rpx; display: flex; align-items: center; gap: 12rpx; }
.cc-name { font-size: 26rpx; font-weight: 600; color: #1e293b; }
.cc-sub { font-size: 22rpx; color: #94a3b8; flex: 1; }
.cc-stats { display: flex; gap: 16rpx; }
.ccs { font-size: 22rpx; color: #64748b; }
.cc-tag { font-size: 20rpx; padding: 3rpx 14rpx; border-radius: 20rpx; font-weight: 500; flex-shrink: 0; }
.cc-tag.red { background: #fef2f2; color: #ef4444; }
.cc-tag.yellow { background: #fffbeb; color: #d97706; }
.cc-tag.blue { background: #eff6ff; color: #3b82f6; }

.empty-inline { text-align: center; padding: 48rpx; color: #94a3b8; font-size: 24rpx; }
.empty-tip { text-align: center; padding: 60rpx; }
.empty-icon { font-size: 48rpx; display: block; margin-bottom: 12rpx; }
.empty-text { font-size: 24rpx; color: #94a3b8; }
</style>
