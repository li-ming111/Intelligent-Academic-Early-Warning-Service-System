<template>
  <view class="page">
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="hero-content">
        <view class="hero-icon">⚠</view>
        <view class="hero-info">
          <text class="hero-title">预警总览</text>
          <text class="hero-sub">ADMIN · 预警总览</text>
        </view>
      </view>
      <view class="hero-wave">
        <view class="wave-shape"></view>
      </view>
    </view>

    <view class="stats-row">
      <view class="stat-item" v-for="(v,k,i) in stats" :key="k" :style="{backgroundColor: statBg[i]}">
        <text class="stat-num" :style="{color: statColor[i]}">{{ v }}</text>
        <text class="stat-label">{{ statLabel[i] }}</text>
      </view>
    </view>

    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索..." @input="onSearch" />
    </view>

    <view class="section-title">
      <text class="section-text">预警总览列表</text>
      <text class="section-count">共 {{filteredList.length}} 项</text>
    </view>
    <view class="list">
      <view v-for="(item, idx) in filteredList" :key="item.id || idx" class="card" @click="onItemClick(item)">
        <view class="card-left">
          <view class="card-icon" :style="{background: iconBg[idx % iconBg.length]}">
            <text class=\"card-emoji\">{{itemIcon}}</text>
          </view>
        </view>
        <view class="card-body">
          <text class="card-title">{{ item.name || item.title || item.studentName || item.student_name || '--' }}</text>
          <text class="card-desc">{{ item.description || item.content || item.reason || item.className || '' }}</text>
        </view>
        <view class="card-right">
          <text class="card-arrow">›</text>
        </view>
      </view>
      <view v-if="filteredList.length === 0" class="empty">
        <text class="empty-icon">📭</text>
        <text class="empty-text">暂无数据</text>
      </view>
    </view>
    <admin-tab-bar />
  </view>
</template>

<script>
import { adminAPI } from '@/services/api.js'

export default {
  data() {
    return {
      title: '预警总览',
      subtitle: 'ADMIN · 预警总览',
      icon: '⚠',
      itemIcon: '⚠',
      sectionTitle: '预警总览列表',
      list: [],
      keyword: '',
      stats: { total: 0, active: 0, done: 0 }, statLabel: ['总计', '进行中', '已完成'],
      statColor: ['#1e40af', '#f59e0b', '#16a34a'],
      statBg: ['#eff6ff', '#fffbeb', '#ecfdf5'],
      iconBg: ['#eff6ff', '#fef3c7', '#ecfdf5', '#f5f3ff'],
      loading: true
    }
  },
  computed: {
    filteredList() {
      if (!this.keyword) return this.list
      const kw = this.keyword.toLowerCase()
      return this.list.filter(item => {
        const s = (item.name || item.title || item.studentName || item.student_name || '') + (item.description || item.content || '')
        return s.toLowerCase().includes(kw)
      })
    }
  },
  onShow() { this.loadData() },
  methods: {
        async loadData() {
      try {
        const uid = uni.getStorageSync('userId')
        if (!uid) return
        this.loading = true
        const res = await adminAPI.getWarnings(uid)
        const raw = Array.isArray(res) ? res : (res?.data || res?.data?.data || [])
        this.list = raw
        this.stats = {
          total: raw.length,
          active: raw.filter(r => r.status === 1 || r.status === 'in_progress').length,
          done: raw.filter(r => r.status === 2 || r.status === 'completed' || r.status === 'approved').length
        }
        this.loading = false
      } catch (e) { this.loading = false; console.error(e) }
    },
    onItemClick(item) {
      uni.showToast({ title: '已选择: ' + (item.name || item.title || item.studentName || ''), icon: 'none' })
    },
    onSearch() {}
  }
}
</script>

<style scoped>
.page {{ min-height: 100vh; background: #f0f2f8; padding-bottom: 140rpx; }}

/* Hero */
.hero {{ position: relative; overflow: hidden; }}
.hero-bg {{ position: absolute; top: 0; left: 0; right: 0; height: 280rpx; background: linear-gradient(135deg, #1e40af, #f59e0b); }}
.hero-content {{ position: relative; z-index: 1; display: flex; align-items: center; padding: 40rpx 32rpx 32rpx; }}
.hero-icon {{ width: 72rpx; height: 72rpx; background: rgba(255,255,255,.2); border-radius: 20rpx; display: flex; align-items: center; justify-content: center; font-size: 36rpx; margin-right: 20rpx; }}
.hero-info {{ flex: 1; }}
.hero-title {{ font-size: 36rpx; font-weight: 700; color: #fff; display: block; }}
.hero-sub {{ font-size: 24rpx; color: rgba(255,255,255,.7); margin-top: 4rpx; display: block; }}
.hero-wave {{ position: relative; z-index: 1; height: 40rpx; margin-top: -1rpx; }}
.wave-shape {{ position: absolute; bottom: 0; left: 0; right: 0; height: 40rpx; background: #f0f2f8; border-radius: 28rpx 28rpx 0 0; }}

/* Stats */
.stats-row {{ display: flex; gap: 16rpx; padding: 0 24rpx 8rpx; }}
.stat-item {{ flex: 1; border-radius: 16rpx; padding: 24rpx 16rpx; text-align: center; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.04); }}
.stat-num {{ font-size: 40rpx; font-weight: 700; display: block; }}
.stat-label {{ font-size: 22rpx; color: #64748b; margin-top: 6rpx; display: block; }}

/* Search */
.search-bar {{ padding: 16rpx 24rpx; }}
.search-input {{ background: #fff; border-radius: 40rpx; padding: 16rpx 28rpx; font-size: 28rpx; color: #1e293b; box-shadow: 0 2rpx 8rpx rgba(0,0,0,.03); }}

/* Section */
.section-title {{ display: flex; justify-content: space-between; align-items: center; padding: 24rpx 28rpx 12rpx; }}
.section-text {{ font-size: 30rpx; font-weight: 700; color: #1e293b; }}
.section-count {{ font-size: 24rpx; color: #94a3b8; }}

/* List */
.list {{ padding: 0 24rpx; }}
.card {{ background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 14rpx; display: flex; align-items: center; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.04); transition: all .2s; }}
.card:active {{ transform: scale(.98); box-shadow: 0 1rpx 4rpx rgba(0,0,0,.06); }}
.card-left {{ margin-right: 20rpx; }}
.card-icon {{ width: 56rpx; height: 56rpx; border-radius: 14rpx; display: flex; align-items: center; justify-content: center; }}
.card-emoji {{ font-size: 28rpx; }}
.card-body {{ flex: 1; overflow: hidden; }}
.card-title {{ font-size: 28rpx; font-weight: 600; color: #1e293b; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }}
.card-desc {{ font-size: 24rpx; color: #94a3b8; margin-top: 6rpx; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }}
.card-right {{ margin-left: 12rpx; }}
.card-arrow {{ font-size: 32rpx; color: #cbd5e1; font-weight: 300; }}

/* Empty */
.empty {{ text-align: center; padding: 100rpx 0; }}
.empty-icon {{ font-size: 64rpx; display: block; }}
.empty-text {{ font-size: 28rpx; color: #94a3b8; margin-top: 16rpx; display: block; }}
</style>
