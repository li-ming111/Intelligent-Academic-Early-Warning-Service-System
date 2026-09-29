<template>
  <view class="page">
    <view class="blue-bar">
      <text class="bar-title">数据分析</text>
      <text class="bar-sub">学业数据概览</text>
    </view>

    <view class="stats">
      <view class="stat-item">
        <text class="stat-num">{{ stats.gpa }}</text>
        <text class="stat-lbl">GPA</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ stats.totalCredits }}</text>
        <text class="stat-lbl">已获学分</text>
      </view>
      <view class="stat-item">
        <text class="stat-num warn">{{ stats.warningCount }}</text>
        <text class="stat-lbl">预警数</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ stats.classRank || '--' }}</text>
        <text class="stat-lbl">班级排名</text>
      </view>
    </view>

    <view class="card">
      <text class="card-title">成绩趋势</text>
      <view v-if="scores.length" class="score-list">
        <view v-for="s in scores" :key="s.courseName||s.id" class="score-row">
          <text class="score-name">{{ s.courseName }}</text>
          <!-- 期末未录入时 score_total 是"平时分折半"的中间值，不是成绩：
               从前 `Number(s||0)` 把它折成 0 分并染红，与真正挂科无法区分 -->
          <text :class="['score-val', scoreClass(s.scoreTotal ?? s.score_total)]">{{ scoreText(s.scoreTotal ?? s.score_total) }}</text>
        </view>
      </view>
      <view v-else class="empty-sm">暂无成绩数据</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { scoreClass, scoreText } from '@/utils/score.js'
import { studentAPI } from '@/services/api.js'

const stats = ref({ gpa:'--', totalCredits:0, warningCount:0, classRank:'--' })
const scores = ref([])

onMounted(() => { loadData() })

const loadData = async () => {
  try {
    const uid = uni.getStorageSync('userId'); if (!uid) return
    const [dash, scoreRes] = await Promise.all([studentAPI.getDashboard(uid), studentAPI.getScores(uid)])
    if (dash) {
      stats.value.gpa = dash.gpa||'--'
      stats.value.totalCredits = dash.completedCredits||0
      stats.value.warningCount = dash.warningCount||0
    }
    const arr = Array.isArray(scoreRes) ? scoreRes : (scoreRes?.data || [])
    scores.value = arr.slice(0, 10)
  } catch (e) {}
}

</script>

<style scoped>
.page { min-height: 100vh; padding: 0 24rpx 24rpx; }
.blue-bar { background: linear-gradient(135deg, #3b5ce8 0%, #5b7cf0 100%); margin: 0 -24rpx 20rpx; padding: 44rpx 28rpx 36rpx; }
.bar-title { display: block; font-size: 34rpx; font-weight: 700; color: #fff; }
.bar-sub { display: block; font-size: 22rpx; color: rgba(255,255,255,0.7); margin-top: 6rpx; }

.stats {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx;
  margin-bottom: 16rpx;
}
.stat-item {
  background: #ffffff; border-radius: 14rpx; padding: 24rpx;
  text-align: center; border: 2rpx solid #f0f0f2;
}
.stat-num { display: block; font-size: 36rpx; font-weight: 700; color: #1d1d1f; margin-bottom: 6rpx; }
.stat-num.warn { color: #ef4444; }
.stat-lbl { font-size: 22rpx; color: #86868b; }

.card { background: #fff; border-radius: 14rpx; padding: 24rpx; border: 2rpx solid #f0f0f2; }
.card-title { font-size: 28rpx; font-weight: 600; color: #1d1d1f; display: block; margin-bottom: 16rpx; }

.score-row { display: flex; justify-content: space-between; align-items: center; padding: 14rpx 0; border-bottom: 2rpx solid #f5f5f7; }
.score-name { font-size: 26rpx; color: #1d1d1f; }
.score-val { font-size: 26rpx; font-weight: 700; padding: 4rpx 14rpx; border-radius: 8rpx; }
.score-val.a { color: #22c55e; background: #f0fdf4; }
.score-val.b { color: #3b5ce8; background: #eef1ff; }
.score-val.c { color: #ef4444; background: #fef2f2; }
/* 未录入是"暂无成绩"而非"考了 0 分"，用灰色与挂科的红色区分 */
.score-val.pending { color: #94a3b8; background: #f1f5f9; }

.empty-sm { text-align: center; padding: 40rpx; color: #aeaeb2; font-size: 24rpx; }
</style>
