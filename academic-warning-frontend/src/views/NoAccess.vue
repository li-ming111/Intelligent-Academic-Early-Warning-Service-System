<template>
  <div class="no-access">
    <el-result
      icon="warning"
      title="暂无可用的 Web 端页面"
      sub-title="当前账号的角色还没有配置 Web 端功能，请联系系统管理员。"
    >
      <template #extra>
        <el-button type="primary" @click="handleLogout">退出登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
  router.replace('/login')
}
</script>

<style scoped>
.no-access {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
}
</style>
