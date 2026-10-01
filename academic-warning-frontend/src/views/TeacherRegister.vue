<template>
  <div class="reg-page">
    <div class="reg-card">
      <div class="reg-header">
        <img src="@/assets/xiaohui.jpg" alt="校徽" class="reg-logo">
        <h1>教师注册</h1>
        <p>哈尔滨信息工程学院 · 教学数据管理</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent>
        <div class="rg-field">
          <label>工号</label>
          <el-input v-model="form.username" placeholder="请输入工号（5位）" maxlength="5" clearable>
            <template #prefix><el-icon><Postcard /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-field">
          <label>姓名</label>
          <el-input v-model="form.name" placeholder="请输入真实姓名" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </div>

        <el-alert v-if="collegesError" :title="collegesError" type="error" :closable="false" style="margin-bottom:14px" />

        <div class="rg-field">
          <label>所属学院</label>
          <el-select v-model="form.collegeId" placeholder="请选择学院" style="width:100%" clearable
            :loading="collegesLoading">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <!-- 学院下拉走 /auth/colleges（白名单里的公开目录，注册页在登录之前，必须匿名可读）。
               第十八轮已把 college-service 里"每学院 3 次 Feign 取学生/教师/辅导员计数"整族删掉
               —— 那条链下游 authenticated() 无头必 403、计数恒 0 且零读者，现在一次请求直达。
               仍带 loading：服务没起时至少让"在加载"看得见，而不是点不动。 -->
          <span v-if="collegesLoading" class="rg-hint dim">正在加载学院列表…</span>
        </div>

        <div class="rg-field">
          <label>手机号</label>
          <el-input v-model="form.phone" placeholder="请输入11位手机号" maxlength="11" clearable>
            <template #prefix><el-icon><Phone /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-field">
          <label>邮箱</label>
          <el-input v-model="form.email" placeholder="请输入邮箱地址" clearable>
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-field">
          <label>密码</label>
          <el-input v-model="form.password" type="password" placeholder="请输入密码（至少6位）" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
          <span class="rg-hint dim">默认密码: 123456</span>
        </div>

        <div class="rg-field">
          <label>确认密码</label>
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </div>

        <el-button type="primary" class="reg-submit" :loading="loading" @click="handleRegister">
          {{ loading ? '注册中...' : '注册账号' }}
        </el-button>

        <div class="reg-footer">
          <router-link to="/login">← 已有账号？返回登录</router-link>
          <div class="reg-footer-links">
            <router-link to="/register">学生注册</router-link>
            <router-link to="/counselor-register">辅导员注册</router-link>
          </div>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Phone, Message, Postcard } from '@element-plus/icons-vue'
import { authAPI } from '@/api/index'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const colleges = ref([])
const collegesError = ref('')
const collegesLoading = ref(false)
const form = ref({ username: '', name: '', collegeId: '', phone: '', email: '', password: '', confirmPassword: '' })

const vPhone = (_r, v, cb) => !v ? cb(new Error('请输入手机号')) : !/^1[3-9]\d{9}$/.test(v) ? cb(new Error('请输入正确的手机号')) : cb()
const vEmail = (_r, v, cb) => !v ? cb(new Error('请输入邮箱')) : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) ? cb(new Error('请输入正确的邮箱地址')) : cb()
const vPwd = (_r, v, cb) => !v ? cb(new Error('请输入密码')) : v.length < 6 ? cb(new Error('密码至少6位')) : cb()
const vConfirm = (_r, v, cb) => !v ? cb(new Error('请再次输入密码')) : v !== form.value.password ? cb(new Error('两次密码不一致')) : cb()

const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  collegeId: [{ required: true, message: '请选择学院', trigger: 'change' }],
  phone: [{ validator: vPhone, trigger: 'blur' }],
  email: [{ validator: vEmail, trigger: 'blur' }],
  password: [{ validator: vPwd, trigger: 'blur' }],
  confirmPassword: [{ validator: vConfirm, trigger: 'blur' }],
}

onMounted(async () => {
  // 学院是**必填项**，列表拉不到就没人能提交这个表单。原来只在 catch 里闪一句
  // ElMessage.error('加载数据失败')，3 秒后消失，用户只看到"下拉框是空的、选不了、
  // 也点不动提交"，看不出发生了什么。改成常驻的红色横幅，并说明能做什么。
  collegesLoading.value = true
  try {
    const r = await authAPI.getColleges()
    colleges.value = r || []
    collegesError.value = colleges.value.length ? '' : '学院列表为空，暂时无法注册；请联系管理员检查学院数据'
  } catch (e) {
    colleges.value = []
    collegesError.value = '学院列表加载失败：' + (e.message || '') + '（学院是必填项，加载成功前无法提交注册）'
  } finally {
    collegesLoading.value = false
  }
})

const handleRegister = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      // 不传 role：角色由端点决定（/auth/register/teacher → 2），请求体里的 role
      // 服务端一律忽略。留在 body 里只会让人以为"角色是前端选的"。
      await authAPI.registerTeacher({ ...form.value })
      // ⚠️ 教师注册出来的账号是 status=0（待审批），**现在登录不了**。
      // 原来这里写"注册成功，请登录"再跳登录页，老师输密码会撞上
      // 「账号正在审批中，请等待管理员审核」—— 一句和上一屏自相矛盾的话。
      ElMessage.success({ message: '注册申请已提交，待管理员审核通过后方可登录', duration: 4000 })
      router.push('/login')
    } catch (e) { ElMessage.error(e.message || '注册失败') }
    finally { loading.value = false }
  })
}
</script>

<style scoped>
.reg-page { min-height: 100vh; background: #f0f4ff; display: flex; align-items: center; justify-content: center; padding: 40px 24px; }
.reg-card { width: 100%; max-width: 460px; background: #fff; border-radius: 20px; padding: 44px 40px;
  box-shadow: 0 4px 24px rgba(0,0,0,.06); }
.reg-header { text-align: center; margin-bottom: 32px; }
.reg-logo { width: 52px; height: 52px; max-width: 52px; border-radius: 14px; margin-bottom: 12px; object-fit: contain;
  background: #eff6ff; padding: 6px; display: inline-block; flex-shrink: 0; }
.reg-header h1 { margin: 0 0 4px; font-size: 24px; font-weight: 700; color: #1e3a5f; }
.reg-header p { margin: 0; font-size: 12px; color: #94a3b8; }
.rg-field { margin-bottom: 16px; }
.rg-field label { display: block; font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 6px; }
.rg-field :deep(.el-input__wrapper) { border-radius: 10px; background: #fff; border: 1.5px solid #e2e8f0; transition: all .3s; box-shadow: none; }
.rg-field :deep(.el-input__wrapper:hover) { border-color: #93c5fd; }
.rg-field :deep(.el-input__wrapper.is-focus) { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37,99,235,.1); }
.rg-field :deep(.el-select .el-input__wrapper) { border-radius: 10px; border: 1.5px solid #e2e8f0; }
.rg-field :deep(.el-select .el-input__wrapper:hover) { border-color: #93c5fd; }
.rg-hint { font-size: 11px; margin-top: 4px; display: block; }
.rg-hint.dim { color: #94a3b8; }
.reg-submit { width: 100%; height: 46px; font-size: 15px; font-weight: 700; letter-spacing: 1px; border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #3b82f6); border: none; box-shadow: 0 4px 14px rgba(37,99,235,.3); transition: all .3s; margin-top: 6px; }
.reg-submit:hover { background: linear-gradient(135deg, #1d4ed8, #2563eb); box-shadow: 0 6px 20px rgba(37,99,235,.4); transform: translateY(-1px); }
.reg-footer { text-align: center; margin-top: 20px; }
.reg-footer a { display: inline-block; color: #2563eb; font-size: 13px; text-decoration: none; transition: color .2s; }
.reg-footer a:hover { color: #1d4ed8; }
.reg-footer-links { margin-top: 10px; display: flex; gap: 12px; justify-content: center; }
.reg-footer-links a { font-size: 12px; color: #94a3b8; }
.reg-footer-links a:hover { color: #2563eb; }
</style>
