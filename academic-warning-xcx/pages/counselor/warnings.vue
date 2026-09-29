<template>
  <view class="page">
    <view class="header">
      <text class="title">预警管理</text>
      <text class="sub">学生学业预警处理</text>
    </view>

    <view class="stats-row">
      <view class="st-card" :class="{active: activeTab === 'all'}" @tap="activeTab='all'">
        <text class="st-num">{{ list.length }}</text><text class="st-lbl">全部</text>
      </view>
      <view class="st-card" :class="{active: activeTab === '3'}" @tap="activeTab='3'">
        <text class="st-num red">{{ severe }}</text><text class="st-lbl">严重</text>
      </view>
      <view class="st-card" :class="{active: activeTab === '2'}" @tap="activeTab='2'">
        <text class="st-num orange">{{ medium }}</text><text class="st-lbl">中度</text>
      </view>
      <view class="st-card" :class="{active: activeTab === '1'}" @tap="activeTab='1'">
        <text class="st-num blue">{{ mild }}</text><text class="st-lbl">轻度</text>
      </view>
    </view>

    <scroll-view v-if="filtered.length" scroll-y class="list">
      <view v-for="w in filtered" :key="w.id" class="card" :style="{borderLeftColor:lvColor(w.warningLevel)}">
        <view class="card-head">
          <view class="badge" :style="{background:lvBg(w.warningLevel),color:lvColor(w.warningLevel)}">
            {{ lvText(w.warningLevel) }}
          </view>
          <text class="date">{{ fmtDate(w.createdAt) }}</text>
        </view>
        <view class="card-body">
          <view class="av" :style="{background:avGrad(w.id || 1)}">{{ (w.studentName || '?')[0] }}</view>
          <view class="info">
            <text class="sname">{{ w.studentName }}</text>
            <text class="sclass">{{ w.className || '--' }}</text>
            <text class="reason">{{ w.description || w.title || '学业预警' }}</text>
          </view>
        </view>
        <view class="card-foot">
          <button v-if="w.status === 0" class="handle-btn" size="mini" @click="doHandle(w)">处理</button>
          <text v-else class="done-text">✓ 已处理</text>
        </view>
      </view>
    </scroll-view>
    <view v-else class="empty">
      <text class="empty-icon">{{ activeTab === 'all' ? '✅' : '📋' }}</text>
      <text class="empty-text">{{ activeTab === 'all' ? '暂无预警记录' : '暂无该等级预警' }}</text>
    </view>

    <counselor-tab-bar current="pages/counselor/warnings" />
  </view>
</template>

<script>
import { counselorAPI } from '@/services/api.js'
import { warningLevel, warningLevelText } from '@/utils/score.js'

export default {
  data() { return { activeTab: 'all', list: [] } },
  computed: {
    filtered() {
      if (this.activeTab === 'all') return this.list
      return this.list.filter(w => {
        const wl = this.normLevel(w.warningLevel)
        if (this.activeTab === '3') return wl >= 3
        if (this.activeTab === '2') return wl === 2
        return wl === 1
      })
    },
    severe() { return this.list.filter(w => this.normLevel(w.warningLevel) >= 3).length },
    medium() { return this.list.filter(w => this.normLevel(w.warningLevel) === 2).length },
    mild() { return this.list.filter(w => this.normLevel(w.warningLevel) === 1).length }
  },
  onShow() { this.loadData() },
  methods: {
    async loadData() {
      try {
        const uid = uni.getStorageSync('counselorId') || uni.getStorageSync('userId'); if (!uid) return
        const res = await counselorAPI.getWarnings(uid)
        this.list = Array.isArray(res) ? res : (res?.data || [])
      } catch (e) {}
    },
    // 等级归一化收敛到 utils/score.js。原实现在这里各写了一份，且无法识别时
    // 兜底成 1（轻度）—— 等级缺失的预警会被算成一条"轻度预警"，统计数字里
    // 始终有一条是虚的。现在无法识别返回 0，不计入任何一档。
    normLevel(wl) {
      return warningLevel(wl)
    },
    // 等级无法识别时给中性灰色，不再冒充"轻度"（蓝色）
    lvColor(wl) { const lv = this.normLevel(wl); return lv >= 3 ? '#ef4444' : lv >= 2 ? '#f59e0b' : lv === 1 ? '#3b82f6' : '#94a3b8' },
    lvBg(wl) { const lv = this.normLevel(wl); return lv >= 3 ? '#fef2f2' : lv >= 2 ? '#fffbeb' : lv === 1 ? '#eff6ff' : '#f1f5f9' },
    lvText(wl) { return warningLevelText(wl) },
    fmtDate(d) { return d ? d.substring(0, 10) : '' },
    avGrad(id) {
      const cs = ['#7c3aed,#6d28d9','#2563eb,#1d4ed8','#16a34a,#15803d','#f59e0b,#d97706']
      return `linear-gradient(135deg,${cs[(id||1) % cs.length]})`
    },
    doHandle(w) {
      uni.showModal({
        title: '处理预警',
        content: `${w.studentName}: ${w.description || ''}`,
        editable: true,
        placeholderText: '输入处理意见和措施',
        success: async (r) => {
          if (r.confirm) {
            try {
              await counselorAPI.processWarning(w.id, { handleResult: r.content || '已处理' })
              uni.showToast({ title: '处理完成', icon: 'success' })
              this.loadData()
            } catch (e) { uni.showToast({ title: '处理失败', icon: 'none' }) }
          }
        }
      })
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; background: #f0f2f8; min-height: 100vh; padding-bottom: 120rpx; }
.header { margin-bottom: 16rpx; }
.title { font-size: 36rpx; font-weight: 800; color: #1e293b; display: block; }
.sub { font-size: 24rpx; color: #94a3b8; }

.stats-row { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12rpx; margin-bottom: 18rpx; }
.st-card { background: #fff; border-radius: 14rpx; padding: 18rpx 8rpx; text-align: center; border: 2rpx solid transparent; }
.st-card.active { border-color: #2563eb; background: #eff6ff; }
.st-num { font-size: 30rpx; font-weight: 800; color: #1e293b; display: block; }
.st-num.red { color: #ef4444; } .st-num.orange { color: #f59e0b; } .st-num.blue { color: #3b82f6; }
.st-lbl { font-size: 20rpx; color: #94a3b8; }

.list { max-height: calc(100vh - 280rpx); }
.card { background: #fff; border-radius: 14rpx; padding: 18rpx 20rpx; margin-bottom: 12rpx; border-left: 6rpx solid; box-shadow: 0 1rpx 4rpx rgba(0,0,0,0.02); }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.badge { font-size: 20rpx; padding: 3rpx 14rpx; border-radius: 10rpx; font-weight: 700; }
.date { font-size: 20rpx; color: #94a3b8; }
.card-body { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.av { width: 56rpx; height: 56rpx; border-radius: 50%; color: #fff; font-size: 24rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.sname { font-size: 26rpx; font-weight: 600; color: #1e293b; display: block; }
.sclass { font-size: 20rpx; color: #94a3b8; display: block; }
.reason { font-size: 22rpx; color: #64748b; display: block; margin-top: 2rpx; }
.card-foot { text-align: right; }
.handle-btn { background: linear-gradient(135deg, #2563eb, #3b82f6); color: #fff; border: none; border-radius: 10rpx; font-size: 22rpx; padding: 0 24rpx; height: 54rpx; line-height: 54rpx; }
.done-text { font-size: 22rpx; color: #16a34a; font-weight: 500; }

.empty { text-align: center; padding: 100rpx 0; }
.empty-icon { font-size: 56rpx; display: block; margin-bottom: 12rpx; }
.empty-text { font-size: 26rpx; color: #94a3b8; }
</style>
