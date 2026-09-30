<template>
  <div class="notification-center-container">
    <el-container>
      <!-- 侧边栏 - 订阅管理 -->
      <el-aside width="250px" class="subscription-sidebar">
        <div class="sidebar-title">订阅偏好</div>
        
        <div class="subscription-section">
          <div class="section-title">预警通知</div>
          <el-checkbox v-model="preferences.subscribeWarnings">
            订阅预警通知
          </el-checkbox>
          
          <div class="level-settings">
            <el-checkbox v-model="preferences.subscribeLow">
              <span class="low-badge">低</span> 低级预警
            </el-checkbox>
            <el-checkbox v-model="preferences.subscribeMedium">
              <span class="medium-badge">中</span> 中级预警
            </el-checkbox>
            <el-checkbox v-model="preferences.subscribeHigh">
              <span class="high-badge">高</span> 高级预警
            </el-checkbox>
          </div>
        </div>

        <div class="subscription-section">
          <div class="section-title">其他通知</div>
          <el-checkbox v-model="preferences.subscribeAssistance">
            帮扶计划更新
          </el-checkbox>
          <el-checkbox v-model="preferences.subscribeSystem">
            系统消息
          </el-checkbox>
        </div>

        <div class="subscription-section">
          <div class="section-title">推送渠道</div>
          <el-checkbox v-model="preferences.pushApp"> 
            <i class="el-icon-bell"></i> 应用内通知
          </el-checkbox>
          <el-checkbox v-model="preferences.pushEmail">
            <i class="el-icon-message"></i> 邮件通知
          </el-checkbox>
          <el-checkbox v-model="preferences.pushSms">
            <i class="el-icon-phone"></i> 短信通知
          </el-checkbox>
        </div>

        <el-button type="primary" @click="savePreferences" style="width: 100%; margin-top: 20px">
          保存设置
        </el-button>
      </el-aside>

      <!-- 主内容区 - 通知列表 -->
      <el-main class="notification-main">
        <!-- 顶部工具栏 -->
        <div class="notification-toolbar">
          <div class="toolbar-left">
            <el-tag type="info">未读: {{ unreadCount }}</el-tag>
            <el-button-group style="margin-left: 20px">
              <el-button size="small" :type="activeTab === 'all' ? 'primary' : 'default'" @click="activeTab = 'all'">
                全部
              </el-button>
              <el-button size="small" :type="activeTab === 'unread' ? 'primary' : 'default'" @click="activeTab = 'unread'">
                未读
              </el-button>
            </el-button-group>
          </div>
          
          <div class="toolbar-right">
            <el-button size="small" @click="markAllAsRead">标记全部已读</el-button>
            <el-button size="small" @click="clearReadNotifications" type="danger" plain>清空已读</el-button>
          </div>
        </div>

        <!-- 通知列表 -->
        <el-table 
          :data="activeTab === 'unread' ? unreadNotifications : allNotifications"
          style="width: 100%; margin-top: 20px"
          v-loading="loading"
        >
          <el-table-column prop="title" label="标题" min-width="200"></el-table-column>
          
          <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip></el-table-column>
          
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="getTypeTagType(row.type)">{{ getTypeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="createdAt" label="时间" width="180" :formatter="formatTime"></el-table-column>
          
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <span v-if="row.isRead === 1" style="color: #909399">已读</span>
              <el-tag v-else type="warning">未读</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="150" align="center">
            <template #default="{ row }">
              <el-button v-if="row.isRead === 0" size="small" link @click="markAsRead(row.id)">
                标记已读
              </el-button>
              <el-button size="small" link type="danger" @click="deleteNotification(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <el-pagination
          v-if="activeTab === 'all'"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
          style="margin-top: 20px; text-align: right"
        ></el-pagination>
      </el-main>
    </el-container>
  </div>
</template>

<script>
import { studentAPI } from '@/api'

export default {
  name: 'NotificationCenter',
  data() {
    return {
      activeTab: 'all',
      unreadCount: 0,
      unreadNotifications: [],
      allNotifications: [],
      preferences: {
        subscribeWarnings: 1,
        subscribeLow: 1,
        subscribeMedium: 1,
        subscribeHigh: 1,
        subscribeAssistance: 1,
        subscribeSystem: 1,
        pushEmail: 1,
        pushSms: 0,
        pushApp: 1
      },
      currentPage: 1,
      pageSize: 10,
      total: 0,
      loading: false
    }
  },
  computed: {
    userId() {
      return localStorage.getItem('userId') || this.$store.state.user?.id
    }
  },
  mounted() {
    this.init()
  },
  methods: {
    async init() {
      this.loading = true
      try {
        // 加载未读通知
        await this.loadUnreadNotifications()
        // 加载所有通知
        await this.loadAllNotifications()
        // 加载用户订阅偏好
        await this.loadPreferences()
        // 加载未读数量
        await this.loadUnreadCount()
      } finally {
        this.loading = false
      }
    },

    /**
     * ⚠️ 本页所有请求都已经过 `src/api/client.js` 的响应拦截器：`code === 200` 时它
     * **返回内层 data**（也就是载荷本身）。所以 `response.data?.code === 200` 恒为假、
     * 而且没有 else —— 赋值语句一次都不执行，页面不崩、不报错、控制台也干净，
     * 只是永远显示空态（实测：`/unread` 明明回了 2 条，页面却是「未读: 0 / 暂无数据 / 共 0 条」）。
     * 现在统一按"await 的结果就是载荷"来写；真正的失败（HTTP 200 + code 500）会走 catch。
     */
    async loadUnreadNotifications() {
      try {
        const res = await studentAPI.getUnreadNotificationsAdvanced(this.userId)
        this.unreadNotifications = Array.isArray(res) ? res : []
      } catch (error) {
        this.$message.error('加载未读通知失败')
      }
    },

    async loadAllNotifications() {
      try {
        const res = await studentAPI.getNotificationsPageable(this.userId, this.currentPage, this.pageSize)
        // 后端回的是 `{total, page, pageSize, list:[...]}` —— 列表键叫 **list**，不是 data
        this.allNotifications = (res && res.list) || []
        this.total = (res && res.total) || 0
      } catch (error) {
        this.$message.error('加载通知列表失败')
      }
    },

    async loadPreferences() {
      try {
        // 先根据 userId 获取 profileId（订阅表存的是 student_profile.id）
        const profileId = await this.resolveProfileId()
        if (!profileId) {
          console.warn('未找到学生档案，跳过订阅偏好加载')
          return
        }
        const res = await studentAPI.getSubscriptionPreferences(profileId)
        if (res && res.success === false) {
          console.warn('订阅偏好不可用:', res.message)
          return
        }
        if (res && typeof res === 'object') {
          Object.assign(this.preferences, res)
        }
      } catch (error) {
        console.warn('加载订阅偏好失败', error)
      }
    },

    /** `users.id` → `student_profile.id`；取不到就返回 null（调用方如实提示，不硬发请求）。 */
    async resolveProfileId() {
      const userId = parseInt(localStorage.getItem('userId')) || this.userId
      if (!userId) return null
      const studentInfo = await studentAPI.getStudentInfoByUserId(userId)
      // 查不到人时后端回的是 `{error:"学生不存在"}`（HTTP 200），没有 id
      return studentInfo && studentInfo.id != null ? studentInfo.id : null
    },

    async loadUnreadCount() {
      try {
        const res = await studentAPI.getUnreadNotificationCountAdvanced(this.userId)
        // 后端回的是 `{unreadCount: n}` —— 改前把整个对象赋给了 unreadCount，标签上会显示 [object Object]
        this.unreadCount = res && res.unreadCount != null ? res.unreadCount : 0
      } catch (error) {
        console.error('加载未读数量失败', error)
      }
    },

    async savePreferences() {
      try {
        const profileId = await this.resolveProfileId()
        if (!profileId) {
          this.$message.error('未找到学生档案，无法保存订阅设置')
          return
        }
        // el-checkbox 的 v-model 写出来的是**布尔**，而库里是 1/0 —— 后端两种都收
        // （改前后端是 `(Integer) pref.get(...)` 硬转，收到 true 直接 ClassCastException，
        //   而页面又用死分支读响应，所以"保存设置"一直是个静默失败）。
        const res = await studentAPI.updateSubscriptionPreferences(profileId, this.preferences)
        if (res && res.success === false) {
          this.$message.error(res.message || '保存设置失败')
          return
        }
        this.$message.success('订阅设置已保存')
        await this.loadPreferences()
      } catch (error) {
        this.$message.error('保存设置失败')
      }
    },

    async markAsRead(notificationId) {
      try {
        const res = await studentAPI.markNotificationRead(notificationId)
        // 后端如实返回 {success, affected}：id 不存在时 success=false，不再假装成功
        if (res && res.success === false) {
          this.$message.error(res.message || '标记已读失败')
          return
        }
        this.$message.success('已标记为已读')
        this.init()
      } catch (error) {
        this.$message.error('操作失败')
      }
    },

    async markAllAsRead() {
      try {
        const notificationIds = this.unreadNotifications.map(n => n.id)
        if (notificationIds.length === 0) {
          this.$message.info('没有未读通知')
          return
        }
        const res = await studentAPI.markMultipleNotificationsAsRead(this.userId, notificationIds)
        if (res && res.success === false) {
          this.$message.error(res.message || '批量标记失败')
          return
        }
        this.$message.success('已标记全部为已读')
        this.init()
      } catch (error) {
        this.$message.error('操作失败')
      }
    },

    async deleteNotification(notificationId) {
      this.$confirm('确定删除该通知？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          const res = await studentAPI.deleteNotificationMsg(notificationId)
          if (res && res.success === false) {
            this.$message.error(res.message || '删除失败')
            return
          }
          this.$message.success('删除成功')
          this.init()
        } catch (error) {
          this.$message.error('删除失败')
        }
      }).catch(() => {})
    },

    async clearReadNotifications() {
      this.$confirm('确定清空所有已读通知？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          const res = await studentAPI.clearReadNotifications(this.userId)
          // 后端回 `{success:true, cleared:n}` —— 只回"已清空"会让"一条都没清"也说成功
          const cleared = res && res.cleared != null ? res.cleared : 0
          this.$message.success(cleared > 0 ? `已清空 ${cleared} 条已读通知` : '没有已读通知需要清空')
          this.init()
        } catch (error) {
          this.$message.error('操作失败')
        }
      }).catch(() => {})
    },

    handlePageChange(page) {
      this.currentPage = page
      this.loadAllNotifications()
    },

    handlePageSizeChange(size) {
      this.pageSize = size
      this.currentPage = 1
      this.loadAllNotifications()
    },

    formatTime(row) {
      const date = new Date(row.createdAt)
      return date.toLocaleString('zh-CN')
    },

    getTypeLabel(type) {
      const labels = {
        warning: '预警通知',
        plan_update: '帮扶更新',
        system_message: '系统消息'
      }
      return labels[type] || type
    },

    getTypeTagType(type) {
      const types = {
        warning: 'danger',
        plan_update: 'success',
        system_message: 'info'
      }
      return types[type] || 'info'
    }
  }
}
</script>

<style scoped lang="scss">
.notification-center-container {
  .el-container {
    height: 100%;
  }

  .subscription-sidebar {
    background: #f5f7fa;
    border-right: 1px solid #ebeef5;
    padding: 20px;
    overflow-y: auto;

    .sidebar-title {
      font-size: 16px;
      font-weight: bold;
      margin-bottom: 20px;
      color: #303133;
    }

    .subscription-section {
      margin-bottom: 20px;
      padding-bottom: 20px;
      border-bottom: 1px solid #ebeef5;

      .section-title {
        font-size: 14px;
        font-weight: bold;
        color: #606266;
        margin-bottom: 10px;
      }

      .el-checkbox {
        display: block;
        margin-bottom: 8px;
        color: #606266;
      }

      .level-settings {
        padding-left: 20px;
        margin-top: 10px;

        .el-checkbox {
          margin-bottom: 6px;
        }
      }

      .low-badge, .medium-badge, .high-badge {
        display: inline-block;
        width: 20px;
        height: 20px;
        border-radius: 3px;
        color: white;
        text-align: center;
        line-height: 20px;
        font-weight: bold;
        margin-right: 6px;
        font-size: 12px;
      }

      .low-badge {
        background: #67c23a;
      }

      .medium-badge {
        background: #e6a23c;
      }

      .high-badge {
        background: #f56c6c;
      }
    }
  }

  .notification-main {
    padding: 20px;

    .notification-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 15px;
      background: #fff;
      border: 1px solid #ebeef5;
      border-radius: 4px;

      .toolbar-left, .toolbar-right {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }
  }
}
</style>
