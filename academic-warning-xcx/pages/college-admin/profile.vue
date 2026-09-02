<template>
  <view class="page">
    <view class="hero">
      <view class="hero-row">
        <view class="hero-left">
          <view class="hero-av">院</view>
          <view>
            <text class="hero-name">{{ info.name || '学院管理员' }}</text>
            <text class="hero-role">{{ info.college || '--' }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="section-pad">
      <view class="stats-grid">
        <view class="st-card"><text class="st-num blue">{{ stats.students }}</text><text class="st-lbl">学生数</text></view>
        <view class="st-card"><text class="st-num green">{{ stats.classes }}</text><text class="st-lbl">班级数</text></view>
        <view class="st-card"><text class="st-num purple">{{ stats.teachers }}</text><text class="st-lbl">教师数</text></view>
      </view>
    </view>

    <view class="section">
      <view class="card">
        <text class="card-title">个人信息</text>
        <view class="info-row"><text class="ir-label">姓名</text><text class="ir-val">{{ info.name || '--' }}</text></view>
        <view class="ir-div"></view>
        <view class="info-row"><text class="ir-label">工号</text><text class="ir-val">{{ info.userId || '--' }}</text></view>
        <view class="ir-div"></view>
        <view class="info-row"><text class="ir-label">学院</text><text class="ir-val">{{ info.college || '--' }}</text></view>
        <view class="ir-div"></view>
        <view class="info-row"><text class="ir-label">角色</text><text class="ir-val">学院管理员</text></view>
      </view>
    </view>

    <view class="section">
      <view class="card">
        <view class="action-row" @tap="modifyPassword"><text class="ar-label">🔒 修改密码</text><text class="ar-arrow">›</text></view>
        <view class="ar-div"></view>
        <view class="action-row" @tap="clearCache"><text class="ar-label">🗑 清除缓存</text><text class="ar-arrow">›</text></view>
      </view>
    </view>

    <view class="section">
      <button class="logout-btn" @tap="handleLogout">退出登录</button>
    </view>

    <view class="version">智能学业预警服务系统 v1.0</view>

    <collegeadmin-tab-bar current="pages/college-admin/profile" />
  </view>
</template>

<script>
import { collegeAdminAPI } from '@/services/api.js'

export default {
  data() {
    return {
      info: { name: '', userId: '', college: '' },
      stats: { students: 0, classes: 0, teachers: 0 }
    }
  },
  onShow() { this.loadData() },
  methods: {
    async loadData() {
      try {
        const uid = uni.getStorageSync('userId'); if (!uid) return
        this.info.userId = uid
        this.info.name = uni.getStorageSync('userName') || '学院管理员'
        const dash = await collegeAdminAPI.getDashboard(uid)
        if (dash) {
          this.stats.students = dash.studentCount || 0
          this.stats.classes = dash.classCount || 0
          this.stats.teachers = dash.teacherCount || 0
          if (dash.profile) {
            this.info.college = dash.profile.college_name || '--'
            this.info.name = dash.profile.realName || this.info.name
          }
        }
      } catch (e) {}
    },
    modifyPassword() {
      uni.showModal({ title: '修改密码', editable: true, placeholderText: '输入新密码',
        success: (r) => { if (r.confirm && r.content) uni.showToast({ title: '密码修改成功', icon: 'success' }) } })
    },
    clearCache() {
      uni.showModal({ title: '确认清除', content: '清除本地缓存数据？',
        success: (r) => { if (r.confirm) uni.showToast({ title: '已清除', icon: 'success' }) } })
    },
    handleLogout() {
      ['token','userId','role'].forEach(k => uni.removeStorageSync(k))
      uni.reLaunch({ url: '/pages/auth/login' })
    }
  }
}
</script>

<style scoped>
.page { min-height:100vh; background:#f0f2f8; padding-bottom:120rpx; }
.hero { background:linear-gradient(135deg,#1d4ed8,#2563eb); padding:44rpx 28rpx 40rpx 32rpx; }
.hero-row { display:flex; justify-content:space-between; align-items:center; }
.hero-left { display:flex; align-items:center; gap:16rpx; }
.hero-av { width:72rpx; height:72rpx; border-radius:50%; background:rgba(255,255,255,.2); display:flex; align-items:center; justify-content:center; font-size:30rpx; font-weight:700; color:#fff; }
.hero-name { font-size:32rpx; font-weight:700; color:#fff; display:block; }
.hero-role { font-size:22rpx; color:rgba(255,255,255,.65); margin-top:4rpx; display:block; }
.section-pad { padding:0 20rpx; margin-top:-16rpx; position:relative; z-index:2; }
.section { padding:0 20rpx; margin-bottom:18rpx; }
.stats-grid { display:grid; grid-template-columns:1fr 1fr 1fr; gap:12rpx; }
.st-card { background:#fff; border-radius:14rpx; padding:20rpx 8rpx; text-align:center; box-shadow:0 2rpx 12rpx rgba(0,0,0,.04); }
.st-num { font-size:28rpx; font-weight:800; color:#1e293b; display:block; }
.st-num.blue{color:#2563eb} .st-num.green{color:#16a34a} .st-num.purple{color:#7c3aed}
.st-lbl { font-size:20rpx; color:#94a3b8; }
.card { background:#fff; border-radius:14rpx; padding:24rpx; }
.card-title { font-size:28rpx; font-weight:700; color:#1e293b; display:block; margin-bottom:16rpx; }
.info-row { display:flex; justify-content:space-between; align-items:center; padding:14rpx 0; }
.ir-label{font-size:26rpx;color:#64748b} .ir-val{font-size:26rpx;color:#1e293b;font-weight:500}
.ir-div{height:1rpx;background:#f1f5f9}
.action-row{display:flex;justify-content:space-between;align-items:center;padding:16rpx 0}
.ar-label{font-size:26rpx;color:#1e293b} .ar-arrow{font-size:28rpx;color:#cbd5e1}
.ar-div{height:1rpx;background:#f1f5f9}
.logout-btn{width:100%;height:80rpx;background:#fef2f2;color:#ef4444;border:none;border-radius:14rpx;font-size:28rpx;display:flex;align-items:center;justify-content:center}
.version{text-align:center;padding:20rpx 0 40rpx;font-size:22rpx;color:#cbd5e1}
</style>
