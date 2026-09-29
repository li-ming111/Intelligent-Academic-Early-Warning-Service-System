<template>
  <view class="page">
    <!-- 蓝色头部 -->
    <view class="blue-bar">
      <text class="bar-title">预警管理</text>
      <text class="bar-sub">学业风险实时监控</text>
    </view>

    <!-- 统计卡片 -->
    <view class="stat-row">
      <view class="stat-box" :class="{ active: activeFilter === 'all' }" @tap="filterBy('all')">
        <text class="stat-num">{{ stats.total }}</text>
        <text class="stat-lbl">全部</text>
      </view>
      <view class="stat-box danger" :class="{ active: activeFilter === 'danger' }" @tap="filterBy('danger')">
        <text class="stat-num">{{ stats.danger }}</text>
        <text class="stat-lbl">严重</text>
      </view>
      <view class="stat-box warn" :class="{ active: activeFilter === 'medium' }" @tap="filterBy('medium')">
        <text class="stat-num">{{ stats.medium }}</text>
        <text class="stat-lbl">中等</text>
      </view>
      <view class="stat-box info" :class="{ active: activeFilter === 'low' }" @tap="filterBy('low')">
        <text class="stat-num">{{ stats.low }}</text>
        <text class="stat-lbl">轻微</text>
      </view>
    </view>

    <!-- 筛选标签 -->
    <scroll-view scroll-x class="filter-tabs" show-scrollbar="false" v-if="false">
      <view class="tabs-inner">
        <view v-for="t in tabs" :key="t.key" :class="['tab', { active: activeFilter === t.key }]" @tap="filterBy(t.key)">
          <text>{{ t.label }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 预警列表 -->
    <view v-if="filteredList.length > 0" class="list">
      <view
        v-for="w in filteredList"
        :key="w.id"
        class="card"
        @tap="showDetailFn(w)"
      >
        <view class="card-left">
          <view :class="['severity-bar', levelClass(w.warningLevel ?? w.level)]"></view>
        </view>
        <view class="card-body">
          <view class="card-head">
            <text class="card-title">{{ w.title }}</text>
            <view :class="['tag', levelClass(w.warningLevel ?? w.level)]">{{ levelText(w.warningLevel ?? w.level) }}</view>
          </view>
          <text class="card-desc">{{ w.description }}</text>
          <view class="card-foot">
            <text class="card-time">{{ fmtTime(w.createdAt) }}</text>
            <text class="card-arrow">查看详情 →</text>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty-card">
      <text class="empty-icon">{{ activeFilter === 'all' ? '✓' : '○' }}</text>
      <text class="empty-title">{{ activeFilter === 'all' ? '暂无预警' : '该等级无预警' }}</text>
      <text class="empty-sub">{{ activeFilter === 'all' ? '学业状态良好，继续保持' : '切换筛选查看更多' }}</text>
    </view>

    <!-- 详情弹窗 -->
    <view v-if="showDetail" class="overlay" @tap="closeDetail">
      <view class="modal" @tap.stop>
        <view :class="['modal-badge', levelClass(selectedWarning?.warningLevel ?? selectedWarning?.level)]">
          <text class="badge-text">{{ levelText(selectedWarning?.warningLevel ?? selectedWarning?.level) }}</text>
        </view>
        <view class="modal-hd">
          <text class="modal-title">{{ selectedWarning?.title }}</text>
          <text class="modal-close" @tap="closeDetail">✕</text>
        </view>
        <view class="modal-bd">
          <view class="mb-row">
            <text class="mb-lbl">预警等级</text>
            <view :class="['tag', levelClass(selectedWarning?.warningLevel ?? selectedWarning?.level)]">{{ levelText(selectedWarning?.warningLevel ?? selectedWarning?.level) }}</view>
          </view>
          <view class="mb-row">
            <text class="mb-lbl">预警描述</text>
            <text class="mb-val">{{ selectedWarning?.description }}</text>
          </view>
          <view class="mb-row">
            <text class="mb-lbl">触发时间</text>
            <text class="mb-val">{{ fmtTime(selectedWarning?.createdAt) }}</text>
          </view>
          <view class="mb-row" v-if="selectedWarning?.courseName">
            <text class="mb-lbl">关联课程</text>
            <text class="mb-val">{{ selectedWarning?.courseName }}</text>
          </view>
          <view class="mb-row">
            <text class="mb-lbl">处理建议</text>
            <text class="mb-val advice">建议尽快联系辅导员沟通学业情况，制定学习改进计划。如有困难可申请帮扶支持。</text>
          </view>
        </view>
        <button class="m-btn" @tap="closeDetail">我知道了</button>
      </view>
    </view>
  </view>
</template>

<script>
import { studentAPI } from '@/services/api.js'
import { warningLevelKey, warningLevelText } from '@/utils/score.js'

export default {
  data() {
    return {
      warnings: [],
      activeFilter: 'all',
      showDetail: false,
      selectedWarning: null
    }
  },
  computed: {
    stats() {
      return {
        total: this.warnings.length,
        danger: this.warnings.filter(w => this.levelClass(w.warningLevel ?? w.level) === 'danger').length,
        medium: this.warnings.filter(w => this.levelClass(w.warningLevel ?? w.level) === 'medium').length,
        low: this.warnings.filter(w => this.levelClass(w.warningLevel ?? w.level) === 'low').length
      }
    },
    filteredList() {
      if (this.activeFilter === 'all') return this.warnings
      return this.warnings.filter(w => this.levelClass(w.warningLevel ?? w.level) === this.activeFilter)
    }
  },
  onLoad() {
    this.load()
  },
  methods: {
    async load() {
      const uid = uni.getStorageSync('userId')
      if (!uid) return
      try {
        const res = await studentAPI.getWarnings(uid)
        this.warnings = Array.isArray(res) ? res : (res?.data || [])
      } catch (e) {
        uni.showToast({ title: '加载失败', icon: 'none' })
      }
    },
    filterBy(key) {
      this.activeFilter = key
    },
    showDetailFn(w) {
      this.selectedWarning = w
      this.showDetail = true
    },
    closeDetail() {
      this.showDetail = false
    },
    // 等级判定收敛到 utils/score.js：后端给的是数字 1/2/3，而从前的实现比的是
    // 'high'/'medium'/'low' 字符串，两边永不相等 —— 所有预警都落进 else 显示成"轻度"。
    levelClass(l) {
      return warningLevelKey(l)
    },
    levelText(l) {
      return warningLevelText(l)
    },
    fmtTime(t) {
      if (!t) return ''
      try {
        return new Date(t).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) +
               ' ' +
               new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      } catch (e) { return String(t).substring(0, 16) }
    }
  }
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 0 24rpx 24rpx; }

/* 蓝色头 */
.blue-bar {
  background: linear-gradient(135deg, #3b5ce8 0%, #5b7cf0 100%);
  margin: 0 -24rpx 16rpx;
  padding: 44rpx 28rpx 36rpx;
}
.bar-title { display: block; font-size: 34rpx; font-weight: 700; color: #fff; }
.bar-sub { display: block; font-size: 22rpx; color: rgba(255,255,255,0.7); margin-top: 6rpx; }

/* 统计卡片 */
.stat-row { display: flex; gap: 12rpx; margin-bottom: 16rpx; }
.stat-box {
  flex: 1; background: #fff; border-radius: 14rpx; padding: 20rpx 8rpx;
  text-align: center; border: 2rpx solid transparent; transition: all 0.2s;
}
.stat-box.active { border-color: #3b5ce8; background: #eef1ff; }
.stat-box.danger.active { border-color: #ef4444; background: #fef2f2; }
.stat-box.warn.active { border-color: #f59e0b; background: #fffbeb; }
.stat-box.info.active { border-color: #3b5ce8; background: #eef1ff; }
.stat-num { display: block; font-size: 36rpx; font-weight: 700; color: #1d1d1f; }
.stat-box.danger .stat-num { color: #ef4444; }
.stat-box.warn .stat-num { color: #f59e0b; }
.stat-box.info .stat-num { color: #3b5ce8; }
.stat-lbl { display: block; font-size: 20rpx; color: #86868b; margin-top: 4rpx; }

/* 列表 */
.list { display: flex; flex-direction: column; gap: 12rpx; }

/* 卡片 */
.card {
  background: #fff; border-radius: 14rpx; display: flex; overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}
.card-left { width: 8rpx; flex-shrink: 0; }
.severity-bar { width: 100%; height: 100%; }
.severity-bar.danger { background: #ef4444; }
.severity-bar.medium { background: #f59e0b; }
.severity-bar.low { background: #3b5ce8; }

.card-body { flex: 1; padding: 20rpx 20rpx 20rpx 16rpx; overflow: hidden; }
.card-head { display: flex; align-items: center; gap: 12rpx; margin-bottom: 8rpx; }
.card-title { flex: 1; font-size: 28rpx; font-weight: 600; color: #1d1d1f; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tag {
  font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 8rpx; font-weight: 500; flex-shrink: 0;
}
.tag.danger { background: #fef2f2; color: #ef4444; }
.tag.medium { background: #fffbeb; color: #f59e0b; }
.tag.low { background: #eef1ff; color: #3b5ce8; }

.card-desc {
  display: block; font-size: 24rpx; color: #86868b; line-height: 36rpx;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-bottom: 8rpx;
}
.card-foot { display: flex; justify-content: space-between; align-items: center; }
.card-time { font-size: 20rpx; color: #aeaeb2; }
.card-arrow { font-size: 22rpx; color: #3b5ce8; }

/* 空 */
.empty-card { padding: 80rpx 24rpx; text-align: center; background: #fff; border-radius: 14rpx; }
.empty-icon { display: block; font-size: 48rpx; color: #22c55e; margin-bottom: 16rpx; }
.empty-title { display: block; font-size: 28rpx; font-weight: 600; color: #1d1d1f; margin-bottom: 8rpx; }
.empty-sub { display: block; font-size: 24rpx; color: #aeaeb2; }

/* 弹窗 */
.overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.4); display: flex; align-items: center;
  justify-content: center; z-index: 9999;
}
.modal { width: 86%; background: #fff; border-radius: 20rpx; padding: 0 32rpx 32rpx; position: relative; }
.modal-badge {
  position: absolute; top: -20rpx; left: 32rpx;
  padding: 8rpx 24rpx; border-radius: 20rpx;
}
.modal-badge.danger { background: #ef4444; }
.modal-badge.medium { background: #f59e0b; }
.modal-badge.low { background: #3b5ce8; }
.badge-text { font-size: 22rpx; color: #fff; font-weight: 600; }

.modal-hd { display: flex; justify-content: space-between; align-items: center; padding: 28rpx 0 16rpx; border-bottom: 1rpx solid #f0f0f2; margin-bottom: 16rpx; }
.modal-title { font-size: 30rpx; font-weight: 700; color: #1d1d1f; flex: 1; }
.modal-close { font-size: 28rpx; color: #aeaeb2; padding: 8rpx; }

.modal-bd { margin-bottom: 24rpx; }
.mb-row { margin-bottom: 18rpx; }
.mb-lbl { display: block; font-size: 24rpx; color: #86868b; margin-bottom: 6rpx; }
.mb-val { display: block; font-size: 26rpx; color: #1d1d1f; line-height: 38rpx; }
.mb-val.advice { color: #3b5ce8; background: #eef1ff; padding: 16rpx 20rpx; border-radius: 10rpx; line-height: 40rpx; }

.m-btn { width: 100%; height: 80rpx; line-height: 80rpx; background: #3b5ce8; color: #fff; border: none; border-radius: 12rpx; font-size: 28rpx; font-weight: 600; }
</style>
