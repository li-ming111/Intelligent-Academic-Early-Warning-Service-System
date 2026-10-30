<template>
  <view class="page">
    <view class="brand">
      <image src="/static/logo.jpg" mode="aspectFit" class="logo" />
      <text class="brand-name">智能学业预警服务系统</text>
      <text class="brand-sub">用户注册</text>
    </view>

    <view class="card">
      <!-- 角色选择 -->
      <view class="role-tabs">
        <view
          v-for="r in roles"
          :key="r.value"
          :class="['role-tab', { active: selectedRole === r.value }]"
          @tap="selectRole(r.value)"
        >{{ r.label }}</view>
      </view>

      <!-- 账号/学号。学生注册与 PC 端同口径：学号第 5-6 位是专业代码，
           学院/专业由此自动带出，不由用户选 —— 选错了归属比漏填更难查。 -->
      <view class="field">
        <text class="field-label">{{ selectedRole === 'student' ? '学号' : '工号' }}</text>
        <input
          v-model="form.username"
          :maxlength="selectedRole === 'student' ? 10 : 20"
          :placeholder="selectedRole === 'student' ? '请输入10位学号' : '请输入'"
          class="input"
          placeholder-class="ph"
        />
        <text v-if="selectedRole === 'student'" class="hint" :class="sidHint.cls">{{ sidHint.text }}</text>
      </view>

      <!-- 学院/专业（仅学生，只读）：值由学号带出，页面不提供改的入口 ——
           要改归属得改学号，而不是改下拉框。 -->
      <view v-if="selectedRole === 'student'" class="field">
        <text class="field-label">专业</text>
        <view class="picker-box picker-box--readonly">
          <text :class="{ placeholder: !derived.majorName }">
            {{ derived.majorName || '输入学号自动填充' }}
          </text>
        </view>
        <text v-if="derived.majorName && derived.ok" class="hint dim">{{ derived.collegeName }} | {{ derived.majorName }}</text>
      </view>

      <!-- 密码 -->
      <view class="field">
        <text class="field-label">密码</text>
        <input v-model="form.password" type="password" placeholder="至少6位" class="input" placeholder-class="ph" />
      </view>

      <!-- 姓名 -->
      <view class="field">
        <text class="field-label">姓名</text>
        <input v-model="form.name" placeholder="请输入真实姓名" class="input" placeholder-class="ph" />
      </view>

      <!-- 手机号 -->
      <view class="field">
        <text class="field-label">手机号</text>
        <input v-model="form.phone" placeholder="请输入手机号" class="input" placeholder-class="ph" />
      </view>

      <!-- 邮箱 -->
      <view class="field">
        <text class="field-label">邮箱</text>
        <input v-model="form.email" placeholder="请输入邮箱" class="input" placeholder-class="ph" />
      </view>

      <!-- 学院（教师/辅导员）：学生走上面的学号自动带出，不在这个选择器里选。 -->
      <view class="field" v-if="selectedRole !== 'student'">
        <text class="field-label">所属学院</text>
        <picker :range="collegeNames" @change="onCollegeChange">
          <view class="picker-box">
            <text :class="{ placeholder: !selectedCollegeName }">{{ selectedCollegeName || '请选择学院' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 班级（仅学生）：第7-8位是班级代码（06=六班），能对上就自动带出；
           对不上（如新建班还没按这个规则命名）时从该专业的班里手选，或留空后补。 -->
      <view class="field" v-if="selectedRole === 'student'">
        <text class="field-label">所属班级</text>
        <picker :range="classNames" @change="onClassChange">
          <view class="picker-box">
            <text :class="{ placeholder: !selectedClassName }">{{ selectedClassName || '输入学号后自动带出' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
        <text v-if="derived.ok && !form.classId && classNames.length" class="hint dim">
          第7-8位班级代码未对上现有班级，可手动选择或留空稍后补
        </text>
      </view>

      <!-- 职称（仅教师） -->
      <view class="field" v-if="selectedRole === 'teacher'">
        <text class="field-label">职称</text>
        <picker :range="['助教', '讲师', '副教授', '教授']" @change="(e) => form.titleIdx = e.detail.value">
          <view class="picker-box">
            <text :class="{ placeholder: form.titleIdx === -1 }">{{ form.titleIdx >= 0 ? ['助教','讲师','副教授','教授'][form.titleIdx] : '请选择职称' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 错误/成功提示 -->
      <view v-if="errorMsg" class="msg error-msg">{{ errorMsg }}</view>
      <view v-if="successMsg" class="msg success-msg">{{ successMsg }}</view>

      <button
        class="btn"
        :disabled="loading"
        @tap="handleRegister"
      >{{ loading ? '注册中...' : '注册' }}</button>

      <view class="login-link">
        <text>已有账号？</text>
        <navigator url="/pages/auth/login" class="link">立即登录</navigator>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { apiClient } from '../../services/api'

const selectedRole = ref('student')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const form = ref({
  username: '', password: '', name: '', phone: '', email: '',
  collegeId: '', majorId: '', classId: '', titleIdx: -1
})

const roles = [
  { label: '学生', value: 'student' },
  { label: '教师', value: 'teacher' },
  { label: '辅导员', value: 'counselor' }
]

const colleges = ref([])
/** 该学院的全部班级（原始列表）。 */
const allClasses = ref([])
/**
 * 供选择器使用的班级列表 = 原始列表按**已选专业**过滤。
 *
 * <p>后端的班级目录只支持按学院过滤（{@code /auth/classes?collegeId=}），一个学院
 * 下挂着好几个专业的班（实测：学院 1 有 5 个班，分属 2 个专业）。如果照原样塞进
 * 选择器，学生（专业由学号带出）仍能选到别的专业的班 —— 后端校验会回
 * 「所选班级不属于所选专业」，而页面上根本看不出为什么。这里按 majorId 过一遍，
 * 让选择器里出现的就是合法选项。
 */
const classes = computed(() => {
  const mid = form.value.majorId
  if (!mid) return []
  return allClasses.value.filter(c => String(c.majorId) === String(mid))
})

const collegeNames = computed(() => colleges.value.map(c => c.name))
const classNames = computed(() => classes.value.map(c => c.name))

const selectedCollegeObj = computed(() =>
  colleges.value.find(c => c.id == form.value.collegeId) || null
)
const selectedCollegeName = computed(() => selectedCollegeObj.value?.name || '')
const selectedClassName = computed(() => {
  const cls = classes.value.find(c => c.id == form.value.classId)
  return cls?.name || cls?.className || ''
})

const selectRole = (role) => {
  selectedRole.value = role
  form.value = { username: '', password: '', name: '', phone: '', email: '', collegeId: '', majorId: '', classId: '', titleIdx: -1 }
  // 换角色时把上一个角色带出来的班级目录清掉，避免切回来时旧列表被当成新数据。
  allClasses.value = []
  derived.value = { majorId: '', majorName: '', collegeId: '', collegeName: '', ok: false }
  errorMsg.value = ''
  successMsg.value = ''
}

const onCollegeChange = (e) => {
  // 教师/辅导员专用：只有学院，没有专业/班级要带。
  const c = colleges.value[e.detail.value]
  form.value.collegeId = c?.id || ''
}

/** 学号 → 学院/专业 派生结果（仅学生角色；教师/辅导员不走这里）。 */
const derived = ref({ majorId: '', majorName: '', collegeId: '', collegeName: '', ok: false })

/**
 * 全量专业目录 + 学院目录，只拉一次，供学号第 5-6 位查专业代码用。
 *
 * <p>与 PC 端 Register.vue 同一口径：{@code major.code === studentId.substring(4, 6)}。
 * 专业表带 collegeId，所以命中专业后学院随之确定，不需要用户再选。
 */
let directoryPromise = null
const loadDirectory = () => {
  if (!directoryPromise) {
    directoryPromise = Promise.all([apiClient.getMajors(), apiClient.getColleges()])
      .then(([m, c]) => ({ majors: m || [], colleges: c || [] }))
      .catch(e => {
        directoryPromise = null // 失败不缓存，下次输入还能重试
        throw e
      })
  }
  return directoryPromise
}

const sidHint = computed(() => {
  if (selectedRole.value !== 'student') return { text: '', cls: '' }
  const sid = String(form.value.username || '')
  if (sid.length !== 10) return { text: '第5-6位为专业代码、第7-8位为班级代码，输完自动带出', cls: 'dim' }
  if (derived.value.ok) return { text: '✓ 学号有效', cls: 'ok' }
  return { text: '未找到匹配专业，请核对学号（第5-6位需是有效的专业代码）', cls: '' }
})

/**
 * 学号第 7-8 位是班级代码（06 = 六班）。班级名里的数字段是"年级后两位+班号"
 * （计科2306班 = 23级06班、计算机2101班 = 21级01班），所以拿 学号[2..4)+[6..8)
 * 拼成 4 位（2023020616 → "23"+"06" = "2306"）去对班级名的数字部分。
 * 对不上（班级没按这个规则命名）就不选，让用户手选或留空。
 */
const classIdFromSid = (sid, sameMajorClasses) => {
  const target = sid.substring(2, 4) + sid.substring(6, 8)
  const hit = sameMajorClasses.find(c => String(c.name || '').replace(/\D+/g, '') === target)
  return hit ? hit.id : ''
}

const deriveFromStudentId = async () => {
  const sid = String(form.value.username || '')
  if (sid.length !== 10) {
    derived.value = { majorId: '', majorName: '', collegeId: '', collegeName: '', ok: false }
    form.value.collegeId = ''
    form.value.majorId = ''
    form.value.classId = ''
    allClasses.value = []
    return
  }
  try {
    const dir = await loadDirectory()
    const code = sid.substring(4, 6)
    const major = dir.majors.find(m => String(m.code) === code)
    if (!major) {
      derived.value = { majorId: '', majorName: '', collegeId: '', collegeName: '', ok: false }
      form.value.collegeId = ''
      form.value.majorId = ''
      form.value.classId = ''
      allClasses.value = []
      return
    }
    const college = dir.colleges.find(c => c.id == major.collegeId)
    derived.value = {
      majorId: major.id,
      majorName: major.name,
      collegeId: major.collegeId,
      collegeName: college?.name || '',
      ok: true
    }
    form.value.collegeId = major.collegeId
    form.value.majorId = major.id
    form.value.classId = ''
    // 班级按学院取（选择器里再按专业过滤，见 classes 计算属性）；
    // 取回来后按学号第 7-8 位把班也自动选上。
    await loadClasses(major.collegeId)
    const sameMajor = allClasses.value.filter(c => String(c.majorId) === String(major.id))
    form.value.classId = classIdFromSid(sid, sameMajor)
  } catch (e) {
    errorMsg.value = '学院/专业目录加载失败：' + (e.message || '') + '（学号无法自动带出归属）'
  }
}

watch(() => form.value.username, () => {
  if (selectedRole.value === 'student') deriveFromStudentId()
})

const onClassChange = (e) => {
  const c = classes.value[e.detail.value]
  form.value.classId = c?.id || ''
}

const handleRegister = async () => {
  errorMsg.value = ''
  if (!form.value.username) { errorMsg.value = '请输入账号'; return }
  if (!form.value.password || form.value.password.length < 6) { errorMsg.value = '密码至少6位'; return }
  if (!form.value.name) { errorMsg.value = '请输入姓名'; return }
  if (!form.value.phone) { errorMsg.value = '请输入手机号'; return }
  if (!form.value.email) { errorMsg.value = '请输入邮箱'; return }
  // 学生：学院/专业必须由学号带出来（与 PC 端同口径），带不出就是学号错；
  // 教师/辅导员：学院仍必选（后端教师注册也强制）。
  if (selectedRole.value === 'student') {
    if (String(form.value.username).length !== 10) { errorMsg.value = '请输入10位学号'; return }
    if (!derived.value.ok) { errorMsg.value = '学号未能带出学院与专业，请核对学号（第5-6位需是有效的专业代码）'; return }
  } else if (!form.value.collegeId) {
    errorMsg.value = '请选择所属学院'; return
  }

  loading.value = true
  try {
    const base = {
      username: form.value.username,
      password: form.value.password,
      name: form.value.name,
      phone: form.value.phone,
      email: form.value.email
    }

    if (selectedRole.value === 'student') {
      await apiClient.registerStudent({
        ...base,
        collegeId: form.value.collegeId,
        majorId: form.value.majorId,
        classId: form.value.classId
      })
    } else if (selectedRole.value === 'teacher') {
      await apiClient.registerTeacher({
        ...base,
        collegeId: form.value.collegeId,
        title: form.value.titleIdx >= 0 ? ['助教','讲师','副教授','教授'][form.value.titleIdx] : ''
      })
    } else if (selectedRole.value === 'counselor') {
      await apiClient.registerCounselor({
        ...base,
        counselorId: form.value.username,
        collegeId: form.value.collegeId
      })
    }

    // 学生注册完 status=1（直接可用）；教师/辅导员是 status=0（待管理员审批），
    // 当场登录会被回「账号正在审批中，请等待管理员审核」。三种角色说同一句
    // "注册成功，即将跳转登录页"就是让老师白跑一趟登录页。
    successMsg.value = selectedRole.value === 'student'
      ? '注册成功，即将跳转登录页...'
      : '注册申请已提交，待管理员审核通过后方可登录'
    errorMsg.value = ''
    setTimeout(() => { uni.redirectTo({ url: '/pages/auth/login' }) }, 1500)
  } catch (error) {
    errorMsg.value = error.message || '注册失败，请重试'
  } finally {
    loading.value = false
  }
}

// ⚠️ 三个 load* 从前都是"catch 掉、只留一行 console.error 或干脆置空"：
//    接口挂了 / 404 / 403 的时候，用户看到的是"下拉框是空的、点了没反应"，
//    控制台里才有原因，手机上连控制台都没有。现在一律把失败写成页面上可见的
//    红字 —— 宁可难看，也不要让人对着一个空选择器猜。
const loadColleges = async () => {
  try {
    const d = await apiClient.getColleges()
    colleges.value = d || []
    if (!colleges.value.length) errorMsg.value = '学院列表为空，无法注册，请联系管理员'
  } catch (e) {
    colleges.value = []
    errorMsg.value = '学院列表加载失败：' + (e.message || '') + '（学院是必选项）'
  }
}
const loadClasses = async (collegeId) => {
  try {
    const d = await apiClient.getClasses(collegeId)
    allClasses.value = d || []
  } catch (e) {
    allClasses.value = []
    errorMsg.value = '班级列表加载失败：' + (e.message || '') + '（可以先不选班级，稍后在个人资料里补）'
  }
}

onMounted(() => { loadColleges() })
</script>

<style lang="scss" scoped>
/* 与登录页同一套品牌蓝（$brand-auth = PC 端 Login.vue 的蓝），
   不随角色变 —— 注册时还没有角色。 */
.page {
  min-height: 100vh;
  background: $brand-auth;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0 36rpx;
}
/* logo.jpg 是白底方图，校徽是内切圆 —— 不裁圆的话四角是白色方块。 */
.logo { width: 72rpx; height: 72rpx; border-radius: 50%; margin-bottom: $spacing-md; }
.brand-name { font-size: $font-lg; font-weight: 700; color: #fff; letter-spacing: 2rpx; }
.brand-sub { font-size: $font-xs; color: rgba(255, 255, 255, 0.55); margin-top: 6rpx; }

.card {
  /* 同登录页：全局 `.card` 的 margin-bottom 会在白卡下方留一条品牌蓝 */
  margin-bottom: 0;
  background: $color-card;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: 36rpx 40rpx calc(48rpx + constant(safe-area-inset-bottom));
  padding: 36rpx 40rpx calc(48rpx + env(safe-area-inset-bottom));
}

/* 角色标签：是个可点控件，高度必须够 $tap-min */
.role-tabs {
  display: flex;
  margin-bottom: $spacing-xl;
  gap: $spacing-md;
}
.role-tab {
  flex: 1;
  height: $tap-min;
  line-height: $tap-min;
  text-align: center;
  font-size: $font-base;
  color: $color-text-secondary;
  background: $color-bg;
  border-radius: $radius-md;
}
.role-tab.active {
  background: $brand-auth;
  color: #fff;
  font-weight: 600;
}

/* 字段 */
.field { margin-bottom: $spacing-lg; }
.field-label { display: block; font-size: $font-sm; color: $color-text-secondary; margin-bottom: $spacing-xs; }
.input {
  width: 100%; height: $tap-min;
  background: $color-bg; border-radius: $radius-md;
  padding: 0 $spacing-lg; font-size: $font-base; color: $color-text;
  box-sizing: border-box;
}
.ph { color: $color-text-hint; }

.picker-box {
  width: 100%; height: $tap-min;
  background: $color-bg; border-radius: $radius-md;
  padding: 0 $spacing-lg; font-size: $font-base; color: $color-text;
  display: flex; align-items: center; justify-content: space-between;
  box-sizing: border-box;
}
.picker-box .placeholder { color: $color-text-hint; }
.arrow { font-size: $font-md; color: $color-text-hint; }

/* 学号/只读专业框下的提示行 */
.hint { display: block; font-size: $font-xs; color: $color-text-hint; margin-top: 8rpx; }
.hint.ok { color: #16a34a; }
.hint.dim { color: $color-text-hint; }

/* 由学号带出的专业：只读展示，与可点的 picker 区分开（无箭头、无按压预期） */
.picker-box--readonly { background: $color-bg; opacity: 0.85; }

/* 按钮 */
.btn {
  width: 100%; height: $tap-min; line-height: $tap-min;
  background: $brand-auth; color: #fff;
  border-radius: $radius-md; border: none;
  font-size: $font-lg; font-weight: 600;
  letter-spacing: 6rpx; margin-top: $spacing-xl;
}
.btn[disabled] { opacity: 0.5; }

/* 消息 */
.msg { padding: $spacing-md $spacing-lg; border-radius: $radius-sm; margin-top: $spacing-md; font-size: $font-sm; }
.error-msg { background: $color-danger-weak; color: $color-danger; }
.success-msg { background: $color-success-weak; color: $color-success; }

/* 底部链接 */
.login-link {
  text-align: center; margin-top: $spacing-xl; font-size: $font-sm; color: $color-text-secondary;
}
.link { color: $brand-auth; font-weight: 500; }
</style>
