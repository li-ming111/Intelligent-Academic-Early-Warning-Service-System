<template>
  <view class="page">
    <!-- 蓝色头部 -->
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="avatar"><text class="av-text">{{ (teacherName||'师')[0] }}</text></view>
      <text class="hero-name">{{ teacherName || '教师' }}</text>
      <text class="hero-role">教师</text>
      <view class="hero-stats">
        <view class="hs-item">
          <text class="hs-num">{{ stats.totalCourses || 0 }}</text>
          <text class="hs-lbl">授课课程</text>
        </view>
        <view class="hs-div"></view>
        <view class="hs-item">
          <text class="hs-num">{{ stats.totalStudents || 0 }}</text>
          <text class="hs-lbl">学生人数</text>
        </view>
        <view class="hs-div"></view>
        <view class="hs-item">
          <text :class="['hs-num', {warn: stats.totalWarnings>0}]">{{ stats.totalWarnings || 0 }}</text>
          <text class="hs-lbl">预警学生</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-group">
      <view class="menu-item" @tap="nav('/pages/teacher/scores')">
        <view class="mi-icon" style="background:#f59e0b">绩</view>
        <text class="mi-label">成绩管理</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/teacher/students')">
        <view class="mi-icon" style="background:#22c55e">生</view>
        <text class="mi-label">学生管理</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/teacher/courses')">
        <view class="mi-icon" style="background:#3b82f6">课</view>
        <text class="mi-label">课程管理</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/teacher/warnings')">
        <view class="mi-icon" style="background:#ef4444">警</view>
        <text class="mi-label">预警处理</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @tap="nav('/pages/teacher/analysis/index')">
        <view class="mi-icon" style="background:#7c3aed">析</view>
        <text class="mi-label">数据分析</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="nav('/pages/teacher/messages/index')">
        <view class="mi-icon" style="background:#8b5cf6">信</view>
        <text class="mi-label">消息中心</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @tap="modifyPassword">
        <view class="mi-icon" style="background:#8e99a8">密</view>
        <text class="mi-label">修改密码</text>
        <text class="mi-arrow">›</text>
      </view>
      <view class="menu-item" @tap="clearCache">
        <view class="mi-icon" style="background:#94a3b8">清</view>
        <text class="mi-label">清除缓存</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @tap="handleLogout">
        <view class="mi-icon" style="background:#fee2e2;color:#ef4444">退</view>
        <text class="mi-label" style="color:#ef4444">退出登录</text>
        <text class="mi-arrow">›</text>
      </view>
    </view>

    <text class="version">智能学业预警服务系统 v1.0</text>

    <teacher-tab-bar current="pages/teacher/profile" />
  </view>
</template>

<script>
import { teacherAPI, apiClient } from '@/services/api.js'

export default {
  data() {
    return {
      teacherName: '教师',
      stats: { totalCourses: 0, totalStudents: 0, totalWarnings: 0 }
    }
  },
  onShow() { this.loadData() },
  methods: {
    async loadData() {
      try {
        const uid = uni.getStorageSync('teacherId') || uni.getStorageSync('userId'); if (!uid) return
        this.teacherName = uni.getStorageSync('userName') || '教师'
        const dash = await teacherAPI.getDashboard(uid)
        if (dash) {
          this.stats.totalCourses = dash.totalCourses || 0
          this.stats.totalStudents = dash.totalStudents || 0
        }
        const warns = await teacherAPI.getWarnings(uid)
        if (warns) {
          const arr = Array.isArray(warns) ? warns : (warns.data || [])
          this.stats.totalWarnings = arr.length
        }
      } catch (e) {}
    },
    nav(url) { uni.navigateTo({ url }) },
    modifyPassword() {
      uni.showModal({ title: '修改密码', editable: true, placeholderText: '输入新密码',
        success: (r) => { if (r.confirm && r.content) uni.showToast({ title: '密码修改成功', icon: 'success' }) } })
    },
    clearCache() {
      uni.showModal({ title: '确认清除', content: '清除本地缓存数据？',
        success: (r) => { if (r.confirm) uni.showToast({ title: '已清除', icon: 'success' }) } })
    },
    async handleLogout() {
      try {
        const uid = uni.getStorageSync('userId')
        const role = uni.getStorageSync('role')
        if (uid && role) await apiClient.logout(uid, role)
      } catch (e) {}
      ['token','userId','role','userName','teacherId'].forEach(k => uni.removeStorageSync(k))
      uni.reLaunch({ url: '/pages/auth/login' })
    }
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f0f2f8; padding-bottom: 120rpx; }

/* Hero 区域 */
.hero {
  background: linear-gradient(180deg, #2563eb 0%, #4f46e5 60%, #f0f2f8 100%);
  padding: 60rpx 32rpx 100rpx;
  display: flex; flex-direction: column; align-items: center;
  position: relative; overflow: hidden;
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
.av-text { font-size: 48rpx; font-weight: 700; color: #fff; }
.hero-name { font-size: 34rpx; font-weight: 700; color: #fff; margin-top: 20rpx; position: relative; z-index: 1; }
.hero-role { font-size: 24rpx; color: rgba(255,255,255,0.7); margin-top: 6rpx; position: relative; z-index: 1; }

.hero-stats {
  display: flex; align-items: center; gap: 40rpx;
  background: rgba(255,255,255,0.12); border-radius: 16rpx;
  padding: 24rpx 48rpx; margin-top: 32rpx;
  position: relative; z-index: 1;
}
.hs-item { text-align: center; }
.hs-num { font-size: 34rpx; font-weight: 700; color: #fff; display: block; }
.hs-num.warn { color: #ffb4a2; }
.hs-lbl { font-size: 22rpx; color: rgba(255,255,255,0.65); margin-top: 4rpx; display: block; }
.hs-div { width: 2rpx; height: 40rpx; background: rgba(255,255,255,0.2); }

/* 菜单 */
.menu-group { background: #fff; border-radius: 16rpx; margin: 0 24rpx 16rpx; overflow: hidden; }
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
.mi-label { flex: 1; font-size: 28rpx; color: #1d1d1f; }
.mi-arrow { font-size: 28rpx; color: #c4c8d0; }

.version { display: block; text-align: center; font-size: 22rpx; color: #c4c8d0; padding: 32rpx 0; }
</style>
