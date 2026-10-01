<template>
  <div class="reg-page">
    <div class="reg-card">
      <div class="reg-header">
        <img src="@/assets/xiaohui.jpg" alt="校徽" class="reg-logo">
        <h1>辅导员注册</h1>
        <p>哈尔滨信息工程学院 · 学生管理服务</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent>
        <el-alert v-if="collegesError" :title="collegesError" type="error" :closable="false" style="margin-bottom:14px" />

        <div class="rg-field">
          <label>工号</label>
          <el-input v-model="form.username" placeholder="请输入工号" maxlength="20" clearable>
            <template #prefix><el-icon><Postcard /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-field">
          <label>姓名</label>
          <el-input v-model="form.name" placeholder="请输入真实姓名" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-row">
          <div class="rg-field" style="flex:1">
            <label>性别</label>
            <el-select v-model="form.gender" placeholder="请选择" style="width:100%" clearable>
              <el-option label="男" value="男" /><el-option label="女" value="女" />
            </el-select>
          </div>
          <div class="rg-field" style="flex:2">
            <label>所属学院</label>
            <el-select v-model="form.collegeId" placeholder="请选择学院" style="width:100%" clearable
              :loading="collegesLoading" @change="handleCollegeChange">
              <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <!-- 同 TeacherRegister：/auth/colleges 公开目录，匿名可读。
                 第十八轮已删掉里层的 Feign 计数调用，现在一次请求直达，不再有"每学院 3 次"的链。 -->
            <span v-if="collegesLoading" class="rg-hint dim">正在加载学院列表…</span>
          </div>
        </div>

        <div class="rg-row">
          <div class="rg-field" style="flex:1">
            <label>部门</label>
            <el-input v-model="form.department" placeholder="部门名称" clearable>
              <template #prefix><el-icon><Setting /></el-icon></template>
            </el-input>
          </div>
          <div class="rg-field" style="flex:1">
            <label>职称</label>
            <el-select v-model="form.title" placeholder="请选择" style="width:100%" clearable>
              <el-option label="助教" value="助教" /><el-option label="讲师" value="讲师" />
              <el-option label="副教授" value="副教授" /><el-option label="教授" value="教授" />
            </el-select>
          </div>
        </div>

        <div class="rg-row">
          <div class="rg-field" style="flex:1">
            <label>学历</label>
            <el-select v-model="form.education" placeholder="请选择" style="width:100%" clearable>
              <el-option label="本科" value="本科" /><el-option label="硕士" value="硕士" /><el-option label="博士" value="博士" />
            </el-select>
          </div>
          <div class="rg-field" style="flex:1">
            <label>办公地点</label>
            <el-input v-model="form.officeLocation" placeholder="办公地点" clearable>
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
        </div>

        <div class="rg-field">
          <label>值班电话</label>
          <el-input v-model="form.dutyPhone" placeholder="请输入值班电话" clearable>
            <template #prefix><el-icon><Phone /></el-icon></template>
          </el-input>
        </div>

        <div class="rg-row">
          <div class="rg-field" style="flex:1">
            <label>手机号</label>
            <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" clearable>
              <template #prefix><el-icon><Phone /></el-icon></template>
            </el-input>
          </div>
          <div class="rg-field" style="flex:1">
            <label>邮箱</label>
            <el-input v-model="form.email" placeholder="请输入邮箱" clearable>
              <template #prefix><el-icon><Message /></el-icon></template>
            </el-input>
          </div>
        </div>

        <div class="rg-field">
          <label>个人简介（选填）</label>
          <el-input v-model="form.introduction" type="textarea" placeholder="请输入个人简介" :rows="2" />
        </div>

        <div class="rg-row">
          <div class="rg-field" style="flex:1">
            <label>密码</label>
            <el-input v-model="form.password" type="password" placeholder="至少6位" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
            <span class="rg-hint dim">默认: 123456</span>
          </div>
          <div class="rg-field" style="flex:1">
            <label>确认密码</label>
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </div>
        </div>

        <el-button type="primary" class="reg-submit" :loading="loading" @click="handleRegister">
          {{ loading ? '注册中...' : '注册账号' }}
        </el-button>

        <div class="reg-footer">
          <router-link to="/login">← 已有账号？返回登录</router-link>
          <div class="reg-footer-links">
            <router-link to="/register">学生注册</router-link>
            <router-link to="/teacher-register">教师注册</router-link>
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
import { User, Lock, Phone, Message, Postcard, Setting, Search } from '@element-plus/icons-vue'
import { authAPI } from '@/api/index'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const colleges = ref([])
const collegesError = ref('')
const collegesLoading = ref(false)
const form = ref({
  username: '', name: '', gender: '', collegeId: '', department: '', title: '', education: '',
  officeLocation: '', dutyPhone: '', phone: '', email: '', introduction: '',
  password: '123456', confirmPassword: '123456'
})

const vPhone = (_r, v, cb) => !v ? cb(new Error('请输入手机号')) : !/^1[3-9]\d{9}$/.test(v) ? cb(new Error('请输入正确的手机号')) : cb()
const vEmail = (_r, v, cb) => !v ? cb(new Error('请输入邮箱')) : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) ? cb(new Error('请输入正确的邮箱地址')) : cb()
const vPwd = (_r, v, cb) => !v ? cb(new Error('请输入密码')) : v.length < 6 ? cb(new Error('密码至少6位')) : cb()
const vConfirm = (_r, v, cb) => !v ? cb(new Error('请再次输入密码')) : v !== form.value.password ? cb(new Error('两次密码不一致')) : cb()

const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  collegeId: [{ required: true, message: '请选择学院', trigger: 'change' }],
  department: [{ required: true, message: '请输入部门', trigger: 'blur' }],
  phone: [{ validator: vPhone, trigger: 'blur' }],
  email: [{ validator: vEmail, trigger: 'blur' }],
  password: [{ validator: vPwd, trigger: 'blur' }],
  confirmPassword: [{ validator: vConfirm, trigger: 'blur' }],
}

const handleCollegeChange = () => {}
onMounted(async () => {
  // 同 TeacherRegister：学院是必填项，拉不到就没人能提交。闪一句 toast 不够，
  // 要有常驻的横幅把"为什么选不了"说清楚。
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
      // 不传 role：角色由端点决定（/auth/register/counselor → 3）。
      await authAPI.registerCounselor({ ...form.value })
      // 同教师注册：辅导员账号也是 status=0（待审批），当场登录会被拒。
      ElMessage.success({ message: '注册申请已提交，待管理员审核通过后方可登录', duration: 4000 })
      router.push('/login')
    } catch (e) { ElMessage.error(e.message || '注册失败') }
    finally { loading.value = false }
  })
}
</script>

<style scoped>
.reg-page { min-height: 100vh; background: #f0f4ff; display: flex; align-items: center; justify-content: center; padding: 40px 24px; }
.reg-card { width: 100%; max-width: 520px; background: #fff; border-radius: 20px; padding: 40px 36px;
  box-shadow: 0 4px 24px rgba(0,0,0,.06); }
.reg-header { text-align: center; margin-bottom: 28px; }
.reg-logo { width: 48px; height: 48px; max-width: 48px; border-radius: 12px; margin-bottom: 10px; object-fit: contain;
  background: #eff6ff; padding: 5px; display: inline-block; flex-shrink: 0; }
.reg-header h1 { margin: 0 0 4px; font-size: 22px; font-weight: 700; color: #1e3a5f; }
.reg-header p { margin: 0; font-size: 12px; color: #94a3b8; }
.rg-row { display: flex; gap: 12px; }
.rg-field { margin-bottom: 14px; }
.rg-field label { display: block; font-size: 12px; font-weight: 600; color: #334155; margin-bottom: 5px; }
.rg-field :deep(.el-input__wrapper) { border-radius: 10px; background: #fff; border: 1.5px solid #e2e8f0; transition: all .3s; box-shadow: none; }
.rg-field :deep(.el-input__wrapper:hover) { border-color: #93c5fd; }
.rg-field :deep(.el-input__wrapper.is-focus) { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37,99,235,.1); }
.rg-field :deep(.el-select .el-input__wrapper) { border-radius: 10px; border: 1.5px solid #e2e8f0; }
.rg-field :deep(.el-select .el-input__wrapper:hover) { border-color: #93c5fd; }
.rg-field :deep(.el-textarea__inner) { border-radius: 10px; border: 1.5px solid #e2e8f0; font-size: 13px; }
.rg-field :deep(.el-textarea__inner:focus) { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37,99,235,.1); }
.rg-hint { font-size: 11px; margin-top: 3px; display: block; }
.rg-hint.dim { color: #94a3b8; }
.reg-submit { width: 100%; height: 44px; font-size: 15px; font-weight: 700; letter-spacing: 1px; border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #3b82f6); border: none; box-shadow: 0 4px 14px rgba(37,99,235,.3); transition: all .3s; margin-top: 4px; }
.reg-submit:hover { background: linear-gradient(135deg, #1d4ed8, #2563eb); box-shadow: 0 6px 20px rgba(37,99,235,.4); transform: translateY(-1px); }
.reg-footer { text-align: center; margin-top: 18px; }
.reg-footer a { display: inline-block; color: #2563eb; font-size: 13px; text-decoration: none; transition: color .2s; }
.reg-footer a:hover { color: #1d4ed8; }
.reg-footer-links { margin-top: 8px; display: flex; gap: 12px; justify-content: center; }
.reg-footer-links a { font-size: 12px; color: #94a3b8; }
.reg-footer-links a:hover { color: #2563eb; }

@media (max-width: 560px) { .rg-row { flex-direction: column; gap: 0; } }
</style>
