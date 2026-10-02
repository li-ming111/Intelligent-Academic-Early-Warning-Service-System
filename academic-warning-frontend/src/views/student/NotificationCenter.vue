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
import { subscribeMyMessages, unsubscribeMyMessages } from '@/utils/realtimeMessages'

// 订阅设置的 9 个键，顺序与后端 `StudentServiceImpl.SUBSCRIPTION_KEYS` 一致。
// 页面里一律用**布尔**（el-checkbox 只认 `modelValue === trueValue`），
// 与后端交换时用 1/0（loadPreferences / savePreferences 各转一次）。
const SUBSCRIPTION_KEYS = [
  'subscribeWarnings', 'subscribeLow', 'subscribeMedium', 'subscribeHigh',
  'subscribeAssistance', 'subscribeSystem',
  'pushEmail', 'pushSms', 'pushApp'
]

export default {
  name: 'NotificationCenter',
  data() {
    return {
      activeTab: 'all',
      unreadCount: 0,
      unreadNotifications: [],
      allNotifications: [],
      preferences: {
        subscribeWarnings: true,
        subscribeLow: true,
        subscribeMedium: true,
        subscribeHigh: true,
        subscribeAssistance: true,
        subscribeSystem: true,
        pushEmail: true,
        pushSms: false,
        pushApp: true
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
    // 2026-10-02（W3.2）：订阅自己的实时消息。
    // 服务端在「辅导员发通知」「管理员发广播」「预警生成」时会
    // `convertAndSendToUser(userId, "/queue/messages", …)`，收不到时页面只能靠
    // 手动刷新 —— 这一句就是让"已打开的页面立刻收到"成立的那一半。
    subscribeMyMessages(this.onRealtimeMessage)
  },
  beforeUnmount() {
    unsubscribeMyMessages()
  },
  methods: {
    /** 收到实时消息：刷新未读数与列表，并提示。 */
    async onRealtimeMessage(payload) {
      const content = payload && (payload.content || payload.title)
      this.$message.success(content ? `收到新消息：${content}` : '收到新消息')
      await this.loadUnreadCount()
      await this.loadUnreadNotifications()
      await this.loadAllNotifications()
    },

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
          // ⚠️ 2026-10-01 第二十四轮：必须**转成布尔**再绑给 el-checkbox。
          // 库和后端给的都是 1/0，而 `el-checkbox` 判的是 `modelValue === trueValue`
          // （trueValue 默认就是 `true`），`1 === true` 是 false ⇒ **九个勾全部显示为未勾选**，
          // 与库里的实际设置完全相反（实测司明：medium/high/assistance/system/pushApp/pushEmail
          // 库里都是 1，页面上却一个都没勾）。这类"数据全对、只有控件状态错"用接口核不出来，
          // 必须看渲染。下面 savePreferences 再转回 1/0，保持进出同一种表示。
          for (const k of SUBSCRIPTION_KEYS) {
            if (res[k] !== undefined && res[k] !== null) {
              this.preferences[k] = Number(res[k]) === 1
            }
          }
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
        // el-checkbox 的 v-model 是**布尔**，库里是 1/0 —— 后端两种都收
        // （改前后端是 `(Integer) pref.get(...)` 硬转，收到 true 直接 ClassCastException，
        //   而页面又用死分支读响应，所以"保存设置"一直是个静默失败）。
        // 这里显式转回 1/0：读回来的和写进去的用同一种表示，避免"存了 true、读回 1"
        // 这种两边都对但看着像不一致的状态。
        const payload = {}
        for (const k of SUBSCRIPTION_KEYS) payload[k] = this.preferences[k] ? 1 : 0
        const res = await studentAPI.updateSubscriptionPreferences(profileId, payload)
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

    /**
     * 通知类型文案。
     *
     * ⚠️ 2026-10-01 第二十四轮：这张表原来只有 `warning / plan_update / system_message`
     * 三个键，而 **`notifications.type` 里真实出现过的值是 `warning` 和 `system`，
     * 唯一的写入方 `counselor-service` 写的是 `warning / important / normal`**
     * （见 `CounselorNotificationServiceImpl` 把 `category` 直接 `setType(category)`，
     * 取值来自 `CounselorNotifications.vue` 的 typeOptions）。
     * 于是学生页面上辅导员发的「重要通知」「普通通知」会原样显示成 `important` / `normal`，
     * 一条 `system` 显示成 `system` —— 只有 `warning` 恰好命中。
     * 现在按**写入方的词表**对齐，旧键保留作兼容别名。改这里的话，
     * 那边的 typeOptions 也要一起看。
     */
    getTypeLabel(type) {
      const labels = {
        // 写入方（辅导员群发）的三个取值
        warning: '预警通知',
        important: '重要通知',
        normal: '普通通知',
        // 其它实际存在的取值
        system: '系统消息',
        plan_update: '帮扶更新',
        // 2026-10-02：申诉审核结果的通知类型（StudentNotifier 的 Category.APPEAL → "appeal"）
        appeal: '成绩申诉',
        // 帮扶计划创建通知（StudentNotifier 的 Category.ASSISTANCE → "assistance"）
        assistance: '帮扶通知',
        // 教师回复学生反馈（StudentNotifier 的 Category.FEEDBACK → "feedback"）
        feedback: '反馈回复',
        // 班级通知（StudentNotifier 的 Category.CLASS → "class"）
        class: '班级通知',
        // 兼容别名
        system_message: '系统消息'
      }
      return labels[type] || type
    },

    getTypeTagType(type) {
      const types = {
        warning: 'danger',
        important: 'warning',
        normal: 'info',
        system: 'info',
        plan_update: 'success',
        appeal: 'warning',
        assistance: 'success',
        feedback: 'success',
        class: 'info',
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
