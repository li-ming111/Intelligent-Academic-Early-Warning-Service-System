<template>
  <view class="page">
    <!-- 蓝色 Hero -->
    <view class="hero">
      <view class="hero-top">
        <view class="hero-user">
          <text class="hero-greeting">你好，{{ userName }}</text>
          <text class="hero-role">{{ roleText }}</text>
        </view>
        <view class="hero-av">{{ avatarChar }}</view>
      </view>
      <view class="hero-stats">
        <view class="hs-box" v-for="s in stats" :key="s.label">
          <text :class="['hs-val', s.warn ? 'warn' : '']">{{ s.val }}</text>
          <text class="hs-lbl">{{ s.label }}</text>
        </view>
      </view>
    </view>

    <!-- 快捷入口 -->
    <view class="quick-grid">
      <view class="q-item" @tap="nav('/pages/student/scores')">
        <view class="q-icon blue">成</view><text class="q-label">成绩</text>
      </view>
      <view class="q-item" @tap="nav('/pages/student/warnings')">
        <view class="q-icon red">预</view><text class="q-label">预警</text>
      </view>
      <view class="q-item" @tap="nav('/pages/student/assistance')">
        <view class="q-icon green">帮</view><text class="q-label">帮扶</text>
      </view>
      <view class="q-item" @tap="nav('/pages/student/analysis/index')">
        <view class="q-icon orange">析</view><text class="q-label">分析</text>
      </view>
    </view>

    <!-- 最近预警 -->
    <view class="section">
      <view class="sec-hd">
        <text class="sec-title">最近预警</text>
        <text class="sec-more" @tap="nav('/pages/student/warnings')">查看全部</text>
      </view>
      <view class="card" v-if="warnings.length">
        <view v-for="w in warnings" :key="w.id" class="warn-row">
          <view :class="['warn-dot', w.cls]"></view>
          <text class="warn-name">{{ w.title }}</text>
          <text class="warn-tag" :class="w.cls">{{ w.lvl }}</text>
        </view>
      </view>
      <view v-else class="card empty"><text class="empty-text">暂无预警</text></view>
    </view>

    <!-- 帮扶计划 -->
    <view class="section">
      <view class="sec-hd">
        <text class="sec-title">帮扶计划</text>
        <text class="sec-more" @tap="nav('/pages/student/assistance')">查看全部</text>
      </view>
      <view class="card" v-if="plans.length">
        <view v-for="p in plans" :key="p.id" class="plan-row">
          <text class="plan-title">{{ p.title }}</text>
          <view class="plan-progress">
            <view class="plan-track"><view class="plan-fill" :style="{width:(p.progress||0)+'%'}"></view></view>
            <text class="plan-pct">{{ p.progress || 0 }}%</text>
          </view>
        </view>
      </view>
      <view v-else class="card empty"><text class="empty-text">暂无帮扶计划</text></view>
    </view>
  </view>
</template>

<script>
import { studentAPI } from '@/services/api.js'
import { warningLevelKey, warningLevelText } from '@/utils/score.js'

export default {
  data() {
    return {
      stats: [
        { label: '绩点', val: '0.0', warn: false },
        { label: '学分', val: 0, warn: false },
        { label: '预警', val: 0, warn: true }
      ],
      warnings: [],
      plans: []
    }
  },
  computed: {
    userName() {
      return uni.getStorageSync('userName') || uni.getStorageSync('username') || '学生'
    },
    roleText() {
      const r = uni.getStorageSync('role')
      const m = { '1': '学生', '2': '教师', '3': '辅导员', '4': '管理员', '5': '学院管理员' }
      return m[r] || '学生'
    },
    avatarChar() {
      return (this.userName || '学')[0]
    }
  },
  onLoad() {
    this.loadData()
  },
  methods: {
    async loadData() {
      const uid = uni.getStorageSync('userId')
      if (!uid) return
      try {
        const dash = await studentAPI.getDashboard(uid)
        if (dash) {
          this.stats[0].val = (dash.gpa || 0).toFixed(1)
          this.stats[1].val = dash.completedCredits || 0
          this.stats[2].val = dash.warningCount || 0
          // 等级映射收敛到 utils/score.js：
          //   1. 后端字段是 warningLevel，也兼容旧载荷的 level；
          //   2. 文案从前第二档写"中等"、第一档写"轻微"，与预警页的"中度/轻度"不一致，
          //      同一个等级在两个页面显示成两个词。现在全端统一。
          this.warnings = (dash.recentWarnings || []).slice(0, 3).map(w => {
            const level = w.warningLevel ?? w.level
            return {
              id: w.id, title: w.title || '预警',
              cls: warningLevelKey(level),
              lvl: warningLevelText(level)
            }
          })
        }
        const p = await studentAPI.getAssistancePlans(uid)
        if (p) this.plans = (Array.isArray(p) ? p : (p?.data || [])).slice(0, 2)
      } catch (e) {}
    },
    nav(url) {
      const tb = ['/pages/student/dashboard', '/pages/student/scores', '/pages/student/warnings', '/pages/student/profile']
      if (tb.some(p => url === p || url.startsWith(p + '?'))) {
        uni.switchTab({ url })
      } else {
        uni.navigateTo({ url })
      }
    }
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f0f2f8; }
.hero { background: linear-gradient(180deg, #3b5ce8 0%, #5b7cf0 100%); padding: 60rpx 28rpx 80rpx; }
.hero-top { display: flex; justify-content: space-between; align-items: center; }
.hero-greeting { font-size: 36rpx; font-weight: 700; color: #fff; display: block; }
.hero-role { font-size: 24rpx; color: rgba(255,255,255,0.7); display: block; margin-top: 6rpx; }
.hero-av { width: 80rpx; height: 80rpx; border-radius: 50%; background: rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center; font-size: 32rpx; font-weight: 700; color: #fff; }
.hero-stats { display: flex; gap: 24rpx; margin-top: 40rpx; }
.hs-box { flex: 1; background: rgba(255,255,255,0.12); border-radius: 14rpx; padding: 24rpx; text-align: center; }
.hs-val { display: block; font-size: 40rpx; font-weight: 700; color: #fff; }
.hs-val.warn { color: #ffb4a2; }
.hs-lbl { display: block; font-size: 22rpx; color: rgba(255,255,255,0.65); margin-top: 4rpx; }

.quick-grid { display: flex; padding: 28rpx 24rpx 0; gap: 16rpx; }
.q-item { flex: 1; background: #fff; border-radius: 16rpx; padding: 28rpx 12rpx; display: flex; flex-direction: column; align-items: center; gap: 10rpx; }
.q-icon { width: 64rpx; height: 64rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 700; color: #fff; }
.q-icon.blue { background: #3b5ce8; } .q-icon.red { background: #ef4444; }
.q-icon.green { background: #22c55e; } .q-icon.orange { background: #f59e0b; }
.q-label { font-size: 24rpx; color: #1d1d1f; font-weight: 500; }

.section { padding: 20rpx 24rpx 0; }
.sec-hd { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: #1d1d1f; }
.sec-more { font-size: 24rpx; color: #3b5ce8; }
.card { background: #fff; border-radius: 14rpx; padding: 20rpx 24rpx; }

.warn-row { display: flex; align-items: center; gap: 14rpx; padding: 16rpx 0; border-bottom: 1rpx solid #f5f5f7; }
.warn-row:last-child { border-bottom: none; }
.warn-dot { width: 10rpx; height: 10rpx; border-radius: 50%; flex-shrink: 0; }
.warn-dot.danger { background: #ef4444; } .warn-dot.medium { background: #f59e0b; } .warn-dot.low { background: #3b5ce8; }
.warn-name { flex: 1; font-size: 26rpx; color: #1d1d1f; font-weight: 500; }
.warn-tag { font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 12rpx; font-weight: 500; }
.warn-tag.danger { background: #fef2f2; color: #ef4444; } .warn-tag.medium { background: #fffbeb; color: #f59e0b; } .warn-tag.low { background: #eef1ff; color: #3b5ce8; }

.plan-row { padding: 16rpx 0; border-bottom: 1rpx solid #f5f5f7; }
.plan-row:last-child { border-bottom: none; }
.plan-title { font-size: 26rpx; font-weight: 500; color: #1d1d1f; display: block; margin-bottom: 10rpx; }
.plan-progress { display: flex; align-items: center; gap: 10rpx; }
.plan-track { flex: 1; height: 6rpx; background: #e5e5ea; border-radius: 3rpx; overflow: hidden; }
.plan-fill { height: 100%; background: #22c55e; border-radius: 3rpx; }
.plan-pct { font-size: 20rpx; color: #22c55e; font-weight: 600; }

.empty { padding: 40rpx 24rpx; text-align: center; }
.empty-text { font-size: 24rpx; color: #aeaeb2; }
</style>
