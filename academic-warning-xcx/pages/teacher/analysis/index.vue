<template>
  <view class="page">
    <view class="header">
      <text class="title">数据分析</text>
      <text class="sub">班级成绩统计与趋势</text>
    </view>

    <picker :value="classIdx" :range="classNames" @change="onClassChange">
      <view class="class-picker">
        <text class="cp-label">{{ classNames[classIdx] || '选择班级' }}</text>
        <text class="cp-arrow">▾</text>
      </view>
    </picker>

    <view v-if="analysis" class="content">
      <!-- 统计卡片 -->
      <view class="stats-grid">
        <view class="st-card">
          <view class="st-icon si-blue">📊</view>
          <text class="st-val">{{ analysis.averageScore || '--' }}</text>
          <text class="st-lbl">平均分</text>
        </view>
        <view class="st-card">
          <view class="st-icon si-green">✅</view>
          <text class="st-val green">{{ analysis.passRate || '--' }}%</text>
          <text class="st-lbl">及格率</text>
        </view>
        <view class="st-card">
          <view class="st-icon si-purple">🌟</view>
          <text class="st-val purple">{{ analysis.excellentRate || '--' }}%</text>
          <text class="st-lbl">优秀率</text>
        </view>
        <view class="st-card">
          <view class="st-icon si-cyan">👥</view>
          <text class="st-val">{{ analysis.totalStudents || 0 }}</text>
          <text class="st-lbl">学生数</text>
        </view>
      </view>

      <!-- 成绩分布 -->
      <view v-if="analysis.distribution" class="card">
        <text class="card-title">成绩分布</text>
        <view v-for="(v, k) in analysis.distribution" :key="k" class="dist-row">
          <text class="dr-label">{{ k }}</text>
          <view class="dr-track">
            <view class="dr-fill" :style="{width: maxOne(v, analysis.totalStudents) + '%', background: drColor(k)}"></view>
          </view>
          <text class="dr-val">{{ v }}人</text>
        </view>
      </view>

      <!-- 详情列表 -->
      <view v-if="analysis.students && analysis.students.length" class="card">
        <text class="card-title">学生排名</text>
        <view class="rank-list">
          <view v-for="(s, i) in analysis.students.slice(0, 10)" :key="s.id || i" class="rank-row">
            <text class="rank-no" :class="i < 3 ? 'top' + (i + 1) : ''">{{ i + 1 }}</text>
            <text class="rank-name">{{ s.studentName || s.name }}</text>
            <!-- 未录入的成绩被 `(s.score || 0)` 折成 0 分并染红，与真正挂科无法区分 -->
            <text class="rank-score" :class="rankScoreCls(s.score ?? s.scoreTotal)">{{ scoreText(s.score ?? s.scoreTotal) }}</text>
          </view>
        </view>
      </view>
    </view>
    <view v-else class="empty">
      <text class="empty-icon">📈</text>
      <text class="empty-text">选择班级查看分析数据</text>
    </view>
  </view>
</template>

<script>
import { teacherAPI } from '@/services/api.js'
import { toNumber, scoreText } from '@/utils/score.js'

export default {
  data() {
    return {
      classIdx: 0,
      classes: [],
      analysis: null
    }
  },
  computed: {
    classNames() { return this.classes.map(c => c.name || c.className) }
  },
  onShow() { this.loadClasses() },
  methods: {
    /** 分数档位样式，口径与 utils/score.js 一致（未录入 → 灰色 pending）。 */
    rankScoreCls(v) {
      const n = toNumber(v)
      if (n === null) return 'g-pending'
      if (n >= 90) return 'g-a'
      if (n >= 80) return 'g-b'
      if (n >= 60) return 'g-c'
      return 'g-d'
    },

    async loadClasses() {
      try {
        const uid = uni.getStorageSync('teacherId') || uni.getStorageSync('userId'); if (!uid) return
        const res = await teacherAPI.getMyClasses(uid)
        this.classes = Array.isArray(res) ? res : (res?.data || [])
      } catch (e) {}
    },
    onClassChange(e) {
      this.classIdx = Number(e.detail.value)
      this.loadAnalysis()
    },
    async loadAnalysis() {
      if (this.classIdx < 0) return
      try {
        const cid = this.classes[this.classIdx]?.id
        if (!cid) return
        const res = await teacherAPI.getClassScoreAnalysis(cid)
        this.analysis = res || {}
      } catch (e) { this.analysis = null }
    },
    maxOne(v, total) { return total ? Math.max(1, v / total * 100) : 0 },
    drColor(k) {
      const map = { 'A(90-100)': '#16a34a', 'B(80-89)': '#2563eb', 'C(70-79)': '#0891b2', 'D(60-69)': '#f59e0b', 'F(<60)': '#ef4444' }
      return map[k] || '#94a3b8'
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; background: #f0f2f8; min-height: 100vh; }
.header { margin-bottom: 16rpx; }
.title { font-size: 36rpx; font-weight: 800; color: #1e293b; display: block; }
.sub { font-size: 24rpx; color: #94a3b8; }

.class-picker { background: #fff; border-radius: 14rpx; padding: 20rpx 24rpx; margin-bottom: 18rpx; display: flex; justify-content: space-between; align-items: center; }
.cp-label { font-size: 26rpx; color: #475569; font-weight: 500; }
.cp-arrow { font-size: 22rpx; color: #94a3b8; }

.stats-grid { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12rpx; margin-bottom: 18rpx; }
.st-card { background: #fff; border-radius: 14rpx; padding: 18rpx 8rpx; text-align: center; }
.st-icon { width: 48rpx; height: 48rpx; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; margin: 0 auto 8rpx; font-size: 22rpx; }
.si-blue { background: #eff6ff; } .si-green { background: #ecfdf5; } .si-purple { background: #f5f3ff; } .si-cyan { background: #ecfeff; }
.st-val { font-size: 28rpx; font-weight: 800; color: #1e293b; display: block; }
.st-val.green { color: #16a34a; } .st-val.purple { color: #7c3aed; }
.st-lbl { font-size: 20rpx; color: #94a3b8; }

.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 16rpx; }
.card-title { font-size: 28rpx; font-weight: 700; color: #1e293b; display: block; margin-bottom: 16rpx; }

.dist-row { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.dr-label { width: 120rpx; font-size: 22rpx; color: #64748b; text-align: right; flex-shrink: 0; }
.dr-track { flex: 1; height: 24rpx; background: #f1f5f9; border-radius: 12rpx; overflow: hidden; }
.dr-fill { height: 100%; border-radius: 12rpx; min-width: 4rpx; transition: width 0.6s ease; }
.dr-val { width: 50rpx; font-size: 22rpx; color: #64748b; text-align: center; flex-shrink: 0; }

.rank-list { display: flex; flex-direction: column; gap: 4rpx; }
.rank-row { display: flex; align-items: center; gap: 12rpx; padding: 14rpx 0; border-bottom: 1rpx solid #f1f5f9; }
.rank-no { width: 40rpx; height: 40rpx; border-radius: 50%; background: #f1f5f9; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 700; color: #64748b; text-align: center; flex-shrink: 0; }
.rank-no.top1 { background: #fef3c7; color: #d97706; } .rank-no.top2 { background: #f1f5f9; color: #64748b; } .rank-no.top3 { background: #fef2f2; color: #ef4444; }
.rank-name { flex: 1; font-size: 26rpx; color: #1e293b; }
.rank-score { font-size: 26rpx; font-weight: 700; }
.g-a { color: #16a34a; } .g-b { color: #2563eb; } .g-c { color: #f59e0b; } .g-d { color: #ef4444; }
/* 未录入是"暂无成绩"而非"考了 0 分"，用灰色与挂科的红色区分 */
.g-pending { color: #94a3b8; }

.empty { text-align: center; padding: 120rpx 0; }
.empty-icon { font-size: 64rpx; display: block; margin-bottom: 16rpx; }
.empty-text { font-size: 26rpx; color: #94a3b8; }
</style>
