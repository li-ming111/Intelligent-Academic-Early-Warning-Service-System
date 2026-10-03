<template>
  <div class="ss-page">
    <div class="ss-head">
      <h1>个人设置</h1>
      <p>管理账户信息和系统偏好</p>
    </div>

    <el-tabs model-value="account">
      <el-tab-pane label="账户管理" name="account">
        <div class="ss-cards">
          <!-- 修改密码 -->
          <el-card>
            <template #header><span class="ss-card-title">修改密码</span></template>
            <el-form :model="pw" label-width="100px">
              <el-form-item label="当前密码">
                <el-input v-model="pw.old" type="password" placeholder="输入当前密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pw.newPw" type="password" placeholder="至少8位，含字母和数字" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="pw.confirm" type="password" placeholder="再次输入新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="changePw">确认修改</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 个人信息 -->
          <el-card>
            <template #header><span class="ss-card-title">基本信息</span></template>
            <el-form :model="userInfo" label-width="100px">
              <el-form-item label="学号">
                <el-input :model-value="userInfo.studentId" disabled />
              </el-form-item>
              <el-form-item label="姓名">
                <el-input :model-value="userInfo.name" disabled />
              </el-form-item>
              <el-form-item label="班级">
                <el-input :model-value="userInfo.className" disabled />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="userInfo.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="userInfo.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="入学年份">
                <el-input :model-value="entranceYear" disabled />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveAccount">保存修改</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="隐私设置" name="privacy">
        <el-card>
          <template #header><span class="ss-card-title">隐私设置</span></template>
          <el-form :model="privacy" label-width="140px">
            <!-- 2026-10-01 第二十四轮：这一栏原来有 4 个控件，只有第 1 个真的会存下来。
                 删掉的 3 个都是"点了、提示成功、什么都没发生"：
                   · 个人信息可见范围 —— `privacy_level` 只有**一列**，没有第二个可见范围可存
                   · 预警通知         —— 真正的开关在「消息中心 → 订阅偏好」（subscription_preferences）
                   · 学业数据共享     —— 全仓没有任何列/接口接这个值
                 保留的这一个是真写到 `student_profile.privacy_level` 的，但要说清它
                 **只是存了个偏好**：全仓没有任何读它来做可见性判断的地方（grep 过）。 -->
            <el-form-item label="成绩可见范围">
              <el-radio-group v-model="privacy.scoreVis">
                <el-radio-button value="0">公开</el-radio-button>
                <el-radio-button value="1">仅教师</el-radio-button>
                <el-radio-button value="2">私密</el-radio-button>
              </el-radio-group>
              <div style="font-size:12px;color:#94a3b8;margin-top:4px">保存到你的学生档案；预警订阅在「消息中心」里设置</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePrivacy">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="安全日志" name="security">
        <el-card>
          <template #header><span class="ss-card-title">安全概览</span></template>
          <div class="ss-sec-grid">
            <div class="ss-sec-item safe">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              <div><strong>账户安全</strong><span>状态正常</span></div>
            </div>
            <div class="ss-sec-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
              <div><strong>上次登录</strong><span>{{ lastLoginText }}</span></div>
            </div>
          </div>
        </el-card>

        <el-card style="margin-top:16px">
          <template #header><span class="ss-card-title">登录历史</span></template>
          <div v-if="logs.length">
            <div v-for="l in logs" :key="l.id" class="ss-log">
              <div class="ssl-dot" :class="l.status==='success'?'ok':'fail'"></div>
              <div class="ssl-body">
                <div class="ssl-main">{{ formatTime(l.loginTime) }} · {{ deviceText(l.device) }}</div>
                <div class="ssl-sub">{{ l.ipAddress || '未知 IP' }}<template v-if="l.location"> · {{ l.location }}</template></div>
              </div>
              <span class="ssl-tag" :class="l.status==='success'?'ok':'fail'">{{ l.status==='success'?'成功':'失败' }}</span>
            </div>
          </div>
          <div v-else class="ss-empty">暂无登录记录</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const userInfo = reactive({ studentId:'', name:'', className:'', email:'', phone:'' })
// 账户信息是否真的从 /users/info 取到了。取不到时 email/phone 是空串，
// 直接保存会把 users 里的真实值**覆盖成空串**（空串不是 null，
// MyBatis-Plus 的 NOT_NULL 策略不跳过），所以取不到就不给保存。
const profileLoaded = ref(false)
const entranceYear = computed(() => (userInfo.studentId || '').substring(0,4) || '--')
const pw = reactive({ old:'', newPw:'', confirm:'' })
const privacy = reactive({ scoreVis:'1' })
// 登录历史：2026-10-02 起是真数据（user-service 的 LoginLogService 在每次登录时写
// `login_logs`，读取口是 `GET /users/{uid}/login-logs`）。
// 此前这个数组声明后**从不赋值**，而「上次登录」那一格写死了「刚刚 · Windows」——
// 一个永远空、一个永远是假的。
const logs = ref([])

/** 上一行成功的登录**不是**"上次登录" —— 那正是当前这次会话，所以取倒数第二条。 */
const lastLoginText = computed(() => {
  const ok = logs.value.filter(l => l.status === 'success')
  if (!ok.length) return '暂无记录'
  if (ok.length === 1) return '本次为首次登录'
  return `${formatTime(ok[1].loginTime)} · ${deviceText(ok[1].device)}`
})

/** 后端给的是 ISO（`2026-10-02T11:30:00`），与教师端审计日志页同一写法。 */
function formatTime(v) {
  if (!v) return '--'
  return String(v).replace('T', ' ').substring(0, 19)
}

/** User-Agent 太长，取"系统 / 浏览器"两段就够认人；取不到时不编一个平台出来。 */
function deviceText(ua) {
  if (!ua) return '未知设备'
  const os = /Windows NT/i.test(ua) ? 'Windows'
    : /Macintosh|Mac OS X/i.test(ua) ? 'macOS'
    : /Android/i.test(ua) ? 'Android'
    : /iPhone|iPad|iOS/i.test(ua) ? 'iOS'
    : /Linux/i.test(ua) ? 'Linux' : null
  const browser = /Edg\//i.test(ua) ? 'Edge'
    : /Chrome\//i.test(ua) ? 'Chrome'
    : /Firefox\//i.test(ua) ? 'Firefox'
    : /Safari\//i.test(ua) ? 'Safari' : null
  if (os && browser) return `${os} · ${browser}`
  return os || browser || ua.substring(0, 40)
}

onMounted(async () => { await loadInfo(); await loadLogs() })

async function loadLogs() {
  const uid = getUserId(); if (!uid) return
  try {
    // 响应拦截器（api/client.js）已经解包到 `data.data`，这里拿到的就是数组本身。
    const res = await studentAPI.getLoginLogs(uid)
    logs.value = Array.isArray(res) ? res : []
  } catch (e) { console.error('加载登录历史失败', e) }
}

async function loadInfo() {
  const uid = getUserId(); if (!uid) return
  try {
    const res = await studentAPI.getStudentInfo(uid)
    // 2026-10-02（W4.15）：删掉恒假的 `res?.code === 200` 支（拦截器已解包）。
    const d = res
    if (d) {
      Object.assign(userInfo, { studentId: d.studentId||localStorage.getItem('username')||'', name: d.name||'', className: d.className||d.class_name||'' })
      privacy.scoreVis = String(d.privacyLevel ?? 1)
    }
  } catch(e) { console.error('加载账户失败',e) }
  // 邮箱/电话只存在于 users 表：`Student` 实体里这两个字段标了 @TableField(exist=false)，
  // 而 selectByUserId 是 `SELECT * FROM student_profile`，不 JOIN users，所以上面
  // /students/{uid} 返回的 email/phone **恒为 null**。
  // 必须单独取一次——否则表单显示空、一保存就把 users.email 覆盖成空串
  // （MyBatis-Plus 的 NOT_NULL 策略只在 null 时跳过，空串照样写库）。
  try {
    const u = await studentAPI.getProfile(uid)
    if (u) { userInfo.email = u.email || ''; userInfo.phone = u.phone || ''; profileLoaded.value = true }
  } catch(e) { console.error('加载账号信息失败',e) }
}

async function saveAccount() {
  const uid = getUserId(); if (!uid) return
  if (!profileLoaded.value) return ElMessage.error('账户信息未加载成功，请刷新页面后重试')
  try {
    // 只提交姓名/邮箱/电话三项。users 表里还有 username/role/status/password，
    // 不提交它们（updateById 的 NOT_NULL 策略会跳过 null，不会误改）。
    await studentAPI.updateProfile(uid, { name: userInfo.name, email: userInfo.email, phone: userInfo.phone })
    ElMessage.success('账户信息已保存')
  } catch(e) { ElMessage.error(e?.message || '保存失败') }
}

async function changePw() {
  if (!pw.old || !pw.newPw) return ElMessage.error('请填写完整')
  if (pw.newPw.length < 8) return ElMessage.error('新密码至少8位')
  if (pw.newPw !== pw.confirm) return ElMessage.error('两次密码不一致')
  try {
    // 对象形式，与教师/辅导员两端统一。
    await studentAPI.changePassword({ userId: Number(getUserId()), oldPassword: pw.old, newPassword: pw.newPw })
    ElMessage.success('密码已修改'); pw.old=''; pw.newPw=''; pw.confirm=''
  } catch(e) { ElMessage.error(e?.message || '修改失败，请检查原密码') }
}

async function savePrivacy() {
  try { await studentAPI.updatePrivacy(getUserId(), parseInt(privacy.scoreVis)); ElMessage.success('隐私设置已保存') }
  catch(e) { ElMessage.error('保存失败') }
}
</script>

<style scoped>
.ss-page { padding: 24px; }
.ss-head { margin-bottom: 20px; }
.ss-head h1 { margin: 0 0 4px; font-size: 22px; font-weight: 700; color: #1e3a5f; }
.ss-head p { margin: 0; font-size: 13px; color: #94a3b8; }
.ss-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 16px; }
.ss-card-title { font-size: 15px; font-weight: 600; color: #1e293b; }
:deep(.el-card) { border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
:deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid #f1f5f9; }
:deep(.el-card__body) { padding: 20px; }
:deep(.el-input__wrapper) { border-radius: 8px; }
:deep(.el-tabs__item) { font-size: 14px; }

/* Security */
.ss-sec-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.ss-sec-item { display: flex; align-items: center; gap: 12px; padding: 18px; border-radius: 12px; background: #f8fafc; }
.ss-sec-item.safe { background: #ecfdf5; }
.ss-sec-item.safe svg { color: #16a34a; }
.ss-sec-item strong { display: block; font-size: 14px; color: #1e293b; }
.ss-sec-item span { font-size: 12px; color: #64748b; }

/* Logs */
.ss-log { display: flex; align-items: center; gap: 10px; padding: 11px 0; border-bottom: 1px solid #f1f5f9; }
.ss-log:last-child { border-bottom: none; }
.ssl-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.ssl-dot.ok { background: #16a34a; } .ssl-dot.fail { background: #ef4444; }
.ssl-body { flex: 1; min-width: 0; } .ssl-main { font-size: 13px; color: #1e293b; }
.ssl-sub { font-size: 12px; color: #94a3b8; }
.ssl-tag { font-size: 11px; padding: 2px 8px; border-radius: 6px; }
.ssl-tag.ok { background: #ecfdf5; color: #16a34a; } .ssl-tag.fail { background: #fef2f2; color: #ef4444; }
.ss-empty { text-align: center; padding: 24px; font-size: 13px; color: #94a3b8; }

@media (max-width: 700px) { .ss-cards { grid-template-columns: 1fr; } .ss-sec-grid { grid-template-columns: 1fr; } }
</style>
