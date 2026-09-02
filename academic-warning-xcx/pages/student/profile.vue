<template>
  <view class="page">
    <!-- 蓝色头部 -->
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="avatar"><text class="av-text">{{ (userName||'学')[0] }}</text></view>
      <text class="hero-name">{{ userName || '学生' }}</text>
      <text class="hero-role">{{ roleLabel }}</text>
      <view class="hero-stats">
        <view class="hs-item">
          <text class="hs-num">{{ stats.gpa || '--' }}</text>
          <text class="hs-lbl">GPA</text>
        </view>
        <view class="hs-div"></view>
        <view class="hs-item">
          <text class="hs-num">{{ stats.credits || 0 }}</text>
          <text class="hs-lbl">已修学分</text>
        </view>
        <view class="hs-div"></view>
        <view class="hs-item">
          <text :class="['hs-num', {warn: stats.warnings>0}]">{{ stats.warnings || 0 }}</text>
          <text class="hs-lbl">预警</text>
        </view>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="menu-group">
      <view class="menu-item" @tap="nav('/pages/student/assistance')">
        <view class="mi-icon assist-bg">帮</view>
        <text class="mi-label">帮扶计划</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/student/appeals/index')">
        <view class="mi-icon appeal-bg">申</view>
        <text class="mi-label">成绩申诉</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/student/analysis/index')">
        <view class="mi-icon analysis-bg">析</view>
        <text class="mi-label">数据分析</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @tap="nav('/pages/student/messages/index')">
        <view class="mi-icon msg-bg">信</view>
        <text class="mi-label">消息中心</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/student/settings/index')">
        <view class="mi-icon set-bg">设</view>
        <text class="mi-label">个人设置</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @tap="handleLogout">
        <view class="mi-icon logout-bg">退</view>
        <text class="mi-label" style="color:#ef4444">退出登录</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <text class="version">智能学业预警服务系统 v1.0</text>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { studentAPI, apiClient } from '@/services/api.js'

const userName = computed(() => uni.getStorageSync('userName') || '用户')
const roleLabel = computed(() => {
  switch(uni.getStorageSync('role')) {
    case '1': case 'student': return '学生'
    case '2': case 'teacher': return '教师'
    case '3': case 'counselor': return '辅导员'
    default: return '用户'
  }
})
const stats = ref({ gpa:'--', credits:0, warnings:0 })

const loadStats = async () => {
  try {
    const uid = uni.getStorageSync('userId'); if (!uid) return
    const res = await studentAPI.getDashboard(uid)
    if (res) {
      stats.value.gpa = res.gpa || '--'
      stats.value.credits = res.completedCredits || 0
      stats.value.warnings = res.warningCount || 0
    }
  } catch(e) {}
}

const nav = (url) => uni.navigateTo({ url })

const handleLogout = async () => {
  try {
    const uid = uni.getStorageSync('userId')
    const role = uni.getStorageSync('role')
    if (uid && role) await apiClient.logout(uid, role)
  } catch(e) {}
  const keys = ['token','userId','username','role','userName','studentId','teacherId','counselorId','adminId']
  keys.forEach(k => uni.removeStorageSync(k))
  uni.reLaunch({ url: '/pages/auth/login' })
}

onMounted(() => { loadStats() })
</script>

<style scoped>
.page { min-height: 100vh; background: #f0f2f8; }

/* 蓝色头部 */
.hero {
  background: linear-gradient(180deg, #3b5ce8 0%, #5b7cf0 60%, #f0f2f8 100%);
  padding: 60rpx 32rpx 100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  overflow: hidden;
}
.hero-bg {
  position: absolute; top: -120rpx; right: -80rpx;
  width: 340rpx; height: 340rpx; border-radius: 50%;
  background: rgba(255,255,255,0.06);
}
.avatar {
  width: 120rpx; height: 120rpx; border-radius: 50%;
  background: rgba(255,255,255,0.2);
  display: flex; align-items: center; justify-content: center;
  position: relative; z-index: 1;
}
.av-text {
  font-size: 48rpx; font-weight: 700; color: #fff;
}
.hero-name {
  font-size: 34rpx; font-weight: 700; color: #fff;
  margin-top: 20rpx; position: relative; z-index: 1;
}
.hero-role {
  font-size: 24rpx; color: rgba(255,255,255,0.7);
  margin-top: 6rpx; position: relative; z-index: 1;
}

/* 头部统计 */
.hero-stats {
  display: flex; align-items: center; gap: 40rpx;
  background: rgba(255,255,255,0.12);
  border-radius: 16rpx; padding: 24rpx 48rpx;
  margin-top: 32rpx; position: relative; z-index: 1;
}
.hs-item { text-align: center; }
.hs-num { font-size: 34rpx; font-weight: 700; color: #fff; display: block; }
.hs-num.warn { color: #ffb4a2; }
.hs-lbl { font-size: 22rpx; color: rgba(255,255,255,0.65); margin-top: 4rpx; display: block; }
.hs-div { width: 2rpx; height: 40rpx; background: rgba(255,255,255,0.2); }

/* 菜单组 */
.menu-group {
  background: #fff; border-radius: 16rpx;
  margin: 0 24rpx 16rpx;
  overflow: hidden;
}
.menu-item {
  display: flex; align-items: center; padding: 28rpx 24rpx;
  gap: 16rpx; border-bottom: 2rpx solid #f5f5f7;
}
.menu-item:last-child { border-bottom: none; }
.mi-icon {
  width: 56rpx; height: 56rpx; border-radius: 14rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 24rpx; font-weight: 700; color: #fff; flex-shrink: 0;
}
.assist-bg { background: #22c55e; }
.appeal-bg { background: #f59e0b; }
.analysis-bg { background: #7c3aed; }
.msg-bg { background: #3b5ce8; }
.set-bg { background: #8e99a8; }
.logout-bg { background: #fee2e2; color: #ef4444 !important; }
.mi-label { flex: 1; font-size: 28rpx; color: #1d1d1f; }
.mi-arrow { font-size: 28rpx; color: #c4c8d0; }

.version {
  display: block; text-align: center;
  font-size: 22rpx; color: #c4c8d0;
  padding: 32rpx 0;
}
</style>
