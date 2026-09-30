<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="课程与班级" />

    <filter-bar v-model="tab" :options="tabs" />

    <view class="page-body">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <!-- ---------- 课程 ---------- -->
        <template v-if="tab === 'courses'">
          <view class="toolbar">
            <text class="toolbar__title">共 {{ courses.length }} 门课程</text>
            <view class="toolbar__add" @tap="openCourseForm(null)">
              <app-icon name="plus" :size="32" color="#fff" />
              <text class="toolbar__add-text">新建课程</text>
            </view>
          </view>

          <empty-state v-if="!courses.length" text="暂无课程" hint="新建一门课程后，教师才能录入成绩" icon="book" />

          <view v-else class="card card--flush">
            <view
              v-for="(c, i) in courses"
              :key="c.id"
              class="course"
              :class="{ 'course--divided': i > 0 }"
              @tap="openCourse(c)"
            >
              <view class="course__main">
                <view class="course__top">
                  <text class="course__name">{{ c.name }}</text>
                  <text class="tag tag--info">{{ typeOf(c) }}</text>
                </view>
                <text class="course__meta">{{ c.code || '无课程代码' }} · {{ credits(c.credits) }} 学分</text>
                <text class="course__teacher">授课教师：{{ teacherName(c.teacherId) }}</text>
              </view>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>

          <text class="note">
            课程接口本身**不带教师姓名**，只给一个 `teacherId`。这里的姓名是页面拿
            `/admin/teachers` 的 `userId` 对出来的 —— 注意对的是 `userId`（于凤海 = 29），
            不是教师档案的 `id`（= 1）。按 `id` 对，10 门课会全部显示成"未分配"。
          </text>
        </template>

        <!-- ---------- 班级 ---------- -->
        <template v-else>
          <view class="toolbar">
            <text class="toolbar__title">共 {{ classes.length }} 个班级</text>
            <view class="toolbar__add" @tap="openClassForm(null)">
              <app-icon name="plus" :size="32" color="#fff" />
              <text class="toolbar__add-text">新建班级</text>
            </view>
          </view>

          <empty-state v-if="!classes.length" text="暂无班级" hint="新建后学生才能归属到班" icon="building" />

          <view v-else class="card card--flush">
            <view
              v-for="(k, i) in classes"
              :key="k.id"
              class="klass"
              :class="{ 'klass--divided': i > 0 }"
              @tap="openClass(k)"
            >
              <view class="klass__main">
                <view class="klass__top">
                  <text class="klass__name">{{ k.name }}</text>
                  <!-- 没有班主任是真实状态（软工2306班就是这样），显示成灰标签而不是留空 -->
                  <text v-if="!teacherNameOfClass(k)" class="tag tag--pending">未分配班主任</text>
                </view>
                <text class="klass__meta">
                  {{ collegeName(k.college_id) }} · {{ majorName(k.major_id) }}
                </text>
                <text class="klass__sub">
                  班主任 {{ teacherNameOfClass(k) || '—' }} · {{ n(k.studentCount) }} 名学生
                </text>
              </view>
              <app-icon name="chevron" :size="34" color="#cbd5e1" />
            </view>
          </view>

          <text class="note">
            班级行的字段**同一行里两种命名**：`college_id` / `major_id` / `teacher_id` 是下划线，
            `studentCount` / `teacherName` 是驼峰。这是后端 SQL 直出的结果，页面按各自的名字取。
          </text>
        </template>
      </template>
    </view>

    <!-- ============ 抽屉 ============ -->
    <detail-sheet v-model="sheetOpen" :title="sheetTitle" :height="mode.endsWith('form') ? '76vh' : '62vh'">
      <!-- ---------- 课程详情 ---------- -->
      <view v-if="mode === 'course' && currentCourse" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">课程代码</text>
            <text class="facts__v">{{ currentCourse.code || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">学分</text>
            <text class="facts__v">{{ credits(currentCourse.credits) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">课程类型</text>
            <text class="facts__v">{{ typeOf(currentCourse) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">授课教师</text>
            <text class="facts__v">{{ teacherName(currentCourse.teacherId) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">说明</text>
            <text class="facts__v">{{ currentCourse.description || '--' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">创建时间</text>
            <text class="facts__v">{{ formatDate(currentCourse.createdAt, true) }}</text>
          </view>
        </view>
        <text class="note">
          改这里的授课教师**不会**动已有的成绩记录 —— 成绩是按 `student_id + course_id` 存的，
          换老师只是换这门课的责任人。
        </text>
      </view>

      <!-- ---------- 班级详情 ---------- -->
      <view v-else-if="mode === 'class' && currentClass" class="sheet">
        <view class="facts">
          <view class="facts__row">
            <text class="facts__k">所属学院</text>
            <text class="facts__v">{{ collegeName(currentClass.college_id) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">专业</text>
            <text class="facts__v">{{ majorName(currentClass.major_id) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">班主任</text>
            <text class="facts__v">{{ teacherNameOfClass(currentClass) || '未分配' }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">学生人数</text>
            <text class="facts__v">{{ n(currentClass.studentCount) }}</text>
          </view>
          <view class="facts__row">
            <text class="facts__k">辅导员</text>
            <text class="facts__v">{{ counselorNameOf(currentClass) }}</text>
          </view>
        </view>
        <text class="note">
          辅导员这一行来自班级**详情**接口 —— 列表接口里没有 `counselorId` 字段。
          `classes.counselor_id` 存的是 `counselor_profiles.user_id`（司明 = 53），不是档案的 `id`（= 1）。
        </text>
        <text class="note">
          「移除班主任」走的是单独的一支接口（`POST /class-management/remove-teacher/{id}`）。
          不能用编辑班级把班主任清空 —— 改动接口底层是 MyBatis-Plus 的 `updateById`，
          它**跳过 null 字段**，传 `teacherId: null` 等于没传，库里那一列不会变。
        </text>
      </view>

      <!-- ---------- 课程表单 ---------- -->
      <view v-else-if="mode === 'course-form'" class="sheet">
        <view class="field">
          <text class="field__label">课程名称</text>
          <input v-model="form.name" class="field__input" placeholder="如 高等数学" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">课程代码</text>
          <input v-model="form.code" class="field__input" placeholder="如 MATH001" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">学分</text>
          <input v-model="form.credits" class="field__input" type="digit" placeholder="如 4" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">课程类型</text>
          <view class="opts">
            <view
              v-for="o in courseTypeOptions"
              :key="o"
              class="opt"
              :class="{ 'opt--on': form.type === o }"
              @tap="form.type = o"
            >{{ o }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">授课教师</text>
          <view class="opts">
            <view class="opt" :class="{ 'opt--on': form.teacherId === null }" @tap="form.teacherId = null">不指定</view>
            <view
              v-for="t in teachers"
              :key="t.userId"
              class="opt"
              :class="{ 'opt--on': form.teacherId === t.userId }"
              @tap="form.teacherId = t.userId"
            >{{ t.name }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">说明</text>
          <input v-model="form.description" class="field__input" placeholder="选填" placeholder-class="field__ph" />
        </view>
        <text class="note">
          授课教师选项来自 `/admin/teachers` 的 6 位教师，值取的是 `userId`
          —— 与 `courses.teacher_id` 的口径一致。
        </text>
      </view>

      <!-- ---------- 班级表单 ---------- -->
      <view v-else-if="mode === 'class-form'" class="sheet">
        <view class="field">
          <text class="field__label">班级名称</text>
          <input v-model="form.name" class="field__input" placeholder="如 计科2307班" placeholder-class="field__ph" />
        </view>
        <view class="field">
          <text class="field__label">所属学院</text>
          <view class="opts">
            <view
              v-for="c in colleges"
              :key="c.id"
              class="opt"
              :class="{ 'opt--on': form.collegeId === c.id }"
              @tap="pickCollege(c.id)"
            >{{ c.name }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">专业</text>
          <view class="opts">
            <view class="opt" :class="{ 'opt--on': form.majorId === null }" @tap="form.majorId = null">不指定</view>
            <view
              v-for="m in majorOptions"
              :key="m.id"
              class="opt"
              :class="{ 'opt--on': form.majorId === m.id }"
              @tap="form.majorId = m.id"
            >{{ m.name }}</view>
          </view>
          <text v-if="!majorOptions.length" class="field__hint">先选学院，专业列表按学院过滤</text>
        </view>
        <view class="field">
          <text class="field__label">班主任</text>
          <view class="opts">
            <view class="opt" :class="{ 'opt--on': form.teacherId === null }" @tap="form.teacherId = null">不指定</view>
            <view
              v-for="t in teachers"
              :key="t.userId"
              class="opt"
              :class="{ 'opt--on': form.teacherId === t.userId }"
              @tap="form.teacherId = t.userId"
            >{{ t.name }}</view>
          </view>
        </view>
        <view class="field">
          <text class="field__label">辅导员</text>
          <view class="opts">
            <view class="opt" :class="{ 'opt--on': form.counselorId === null }" @tap="form.counselorId = null">不指定</view>
            <view
              v-for="c in counselors"
              :key="c.userId"
              class="opt"
              :class="{ 'opt--on': form.counselorId === c.userId }"
              @tap="form.counselorId = c.userId"
            >{{ c.name }}<text v-if="c.counselorId">（{{ c.counselorId }}）</text></view>
          </view>
        </view>
        <text class="note">
          「不指定」在**新建**时是不写这一列；在**编辑**时它不会清掉原有值
          （底层跳过 null 字段）。要清班主任，请用详情里的「移除班主任」。
        </text>
      </view>

      <template #footer>
        <template v-if="mode === 'course'">
          <view class="acts">
            <view class="act act--danger" @tap="deleteCourse">删除课程</view>
          </view>
          <view class="btn-primary footer-btn" @tap="openCourseForm(currentCourse)">编辑课程信息</view>
        </template>

        <template v-else-if="mode === 'class'">
          <view class="acts">
            <view v-if="teacherNameOfClass(currentClass)" class="act" @tap="removeTeacher">移除班主任</view>
            <view class="act act--danger" @tap="deleteClass">删除班级</view>
          </view>
          <view class="btn-primary footer-btn" @tap="openClassForm(currentClass)">编辑班级信息</view>
        </template>

        <template v-else>
          <view class="form-acts">
            <view class="form-acts__cancel" @tap="sheetOpen = false">取消</view>
            <view class="btn-primary form-acts__ok" :class="{ 'btn-primary--disabled': submitting }" @tap="submit">
              {{ submitting ? '提交中…' : (editingId ? '保存' : '创建') }}
            </view>
          </view>
        </template>
      </template>
    </detail-sheet>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/services/api.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { formatDate, credits as creditsText, number } from '@/utils/format.js'

const themeStyle = roleThemeStyle('admin')

const tabs = [
  { label: '课程', value: 'courses' },
  { label: '班级', value: 'classes' }
]
const tab = ref('courses')

const loading = ref(true)
const courses = ref([])
const classes = ref([])
const teachers = ref([])
const counselors = ref([])
const colleges = ref([])
const majors = ref([])

const sheetOpen = ref(false)
/** course | class | course-form | class-form */
const mode = ref('course')
const currentCourse = ref(null)
const currentClass = ref(null)
const editingId = ref(null)
const submitting = ref(false)
const form = ref({
  name: '', code: '', credits: '', type: '必修课', description: '',
  teacherId: null, collegeId: null, majorId: null, counselorId: null
})

const sheetTitle = computed(() => {
  if (mode.value === 'course') return currentCourse.value ? currentCourse.value.name : '课程'
  if (mode.value === 'class') return currentClass.value ? currentClass.value.name : '班级'
  if (mode.value === 'course-form') return editingId.value ? '编辑课程' : '新建课程'
  return editingId.value ? '编辑班级' : '新建班级'
})

/** 类型候选。库里出现过的值不在候选里时，把它补进去，免得改一次就把原值弄丢。 */
const courseTypeOptions = computed(() => {
  const base = ['必修课', '选修课', '公共课']
  const cur = form.value.type
  if (cur && !base.includes(cur)) base.push(cur)
  return base
})

const majorOptions = computed(() => {
  if (!form.value.collegeId) return majors.value
  return majors.value.filter(m => m.collegeId === form.value.collegeId)
})

/** teacher_profile.userId → 姓名。`courses.teacher_id` 存的就是这个 userId。 */
const teacherByUserId = computed(() => {
  const map = {}
  teachers.value.forEach(t => { map[t.userId] = t.name })
  return map
})

function teacherName(id) {
  if (id === null || id === undefined) return '未分配'
  return teacherByUserId.value[id] || `未匹配的教师 id ${id}`
}

/** 班级行的教师名后端已经给了（teacherName），优先用它；缺了再本地对。 */
function teacherNameOfClass(k) {
  if (!k) return ''
  return k.teacherName || (k.teacher_id ? teacherName(k.teacher_id) : '')
}

function collegeName(id) {
  const c = colleges.value.find(x => x.id === id)
  return c ? c.name : (id ? `未匹配的学院 id ${id}` : '未填学院')
}

function majorName(id) {
  const m = majors.value.find(x => x.id === id)
  return m ? m.name : (id ? `未匹配的专业 id ${id}` : '未填专业')
}

function counselorNameOf(k) {
  if (!k || k.counselorId === null || k.counselorId === undefined) return '未分配'
  // classes.counselor_id 存的是 counselor_profiles.user_id（司明 = 53）
  const c = counselors.value.find(x => x.userId === k.counselorId)
  return c ? `${c.name}${c.counselorId ? '（' + c.counselorId + '）' : ''}` : `未匹配的辅导员 id ${k.counselorId}`
}

function typeOf(c) {
  return c.type || c.courseType || '未分类'
}

function credits(v) {
  return creditsText(v)
}

function n(v) {
  return number(v, 0)
}

function toast(msg, ms = 2600) {
  uni.showToast({ title: String(msg || ''), icon: 'none', duration: ms })
}

function confirm(content, title = '确认') {
  return new Promise(resolve => {
    uni.showModal({
      title,
      content,
      success: r => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [co, cl, te, cn, col, ma] = await Promise.all([
      adminAPI.getCourses().catch(e => { console.error('[admin courses] 课程失败:', e); return null }),
      adminAPI.getClasses().catch(e => { console.error('[admin courses] 班级失败:', e); return null }),
      adminAPI.getTeachers().catch(e => { console.error('[admin courses] 教师失败:', e); return null }),
      adminAPI.getCounselors().catch(e => { console.error('[admin courses] 辅导员失败:', e); return null }),
      adminAPI.getColleges().catch(e => { console.error('[admin courses] 学院失败:', e); return null }),
      adminAPI.getMajors().catch(e => { console.error('[admin courses] 专业失败:', e); return null })
    ])
    courses.value = Array.isArray(co) ? co : []
    classes.value = Array.isArray(cl) ? cl : []
    teachers.value = Array.isArray(te) ? te : []
    counselors.value = Array.isArray(cn) ? cn : []
    colleges.value = Array.isArray(col) ? col : []
    majors.value = Array.isArray(ma) ? ma : []
  } finally {
    loading.value = false
  }
}

function openCourse(c) {
  currentCourse.value = c
  mode.value = 'course'
  sheetOpen.value = true
}

function openClass(k) {
  currentClass.value = k
  mode.value = 'class'
  sheetOpen.value = true
  // 列表行**没有 counselorId**（只有详情接口给），不补这一枪详情里会永远显示"未分配辅导员"。
  // 详情返回的是驼峰实体（collegeId/teacherId/counselorId），与列表行的下划线字段并存，
  // 所以合并而不是替换 —— 替换会丢掉 studentCount / teacherName。
  adminAPI.getClassById(k.id)
    .then(d => {
      if (d && typeof d === 'object' && currentClass.value && currentClass.value.id === k.id) {
        currentClass.value = { ...k, ...d }
      }
    })
    .catch(e => console.error('[admin courses] 班级详情失败:', e))
}

function openCourseForm(c) {
  editingId.value = c ? c.id : null
  form.value = {
    name: c ? c.name || '' : '',
    code: c ? c.code || '' : '',
    credits: c && c.credits !== null && c.credits !== undefined ? String(c.credits) : '',
    type: c ? typeOf(c) : '必修课',
    description: c ? c.description || '' : '',
    teacherId: c && c.teacherId !== undefined ? c.teacherId : null,
    collegeId: null,
    majorId: null,
    counselorId: null
  }
  mode.value = 'course-form'
  sheetOpen.value = true
}

function openClassForm(k) {
  editingId.value = k ? k.id : null
  form.value = {
    name: k ? k.name || '' : '',
    code: '',
    credits: '',
    type: '',
    description: '',
    teacherId: k && k.teacher_id !== undefined && k.teacher_id !== null ? k.teacher_id : null,
    collegeId: k && k.college_id !== undefined && k.college_id !== null
      ? k.college_id
      : (colleges.value[0] ? colleges.value[0].id : null),
    majorId: k && k.major_id !== undefined && k.major_id !== null ? k.major_id : null,
    counselorId: k && k.counselorId !== undefined && k.counselorId !== null ? k.counselorId : null
  }
  mode.value = 'class-form'
  sheetOpen.value = true
}

/** 换学院时把专业清掉：原来的专业多半不属于新学院，留着就是个错值。 */
function pickCollege(id) {
  if (form.value.collegeId !== id) form.value.majorId = null
  form.value.collegeId = id
}

async function submit() {
  if (submitting.value) return
  const f = form.value
  if (!String(f.name || '').trim()) {
    toast(mode.value === 'course-form' ? '请填写课程名称' : '请填写班级名称')
    return
  }
  const isCourse = mode.value === 'course-form'

  let creditValue = null
  if (isCourse) {
    const raw = String(f.credits || '').trim()
    if (raw) {
      const parsed = Number(raw)
      if (!Number.isFinite(parsed) || parsed < 0) {
        toast('学分要填数字')
        return
      }
      creditValue = parsed
    }
  }

  submitting.value = true
  try {
    if (isCourse) {
      const body = {
        name: String(f.name).trim(),
        code: String(f.code || '').trim(),
        type: String(f.type || '').trim(),
        description: String(f.description || '').trim()
      }
      // 学分留空时带上 null：新建时不写这一列，编辑时也不改它（底层跳过 null）
      if (creditValue !== null) body.credits = creditValue
      if (f.teacherId !== null) body.teacherId = f.teacherId
      if (editingId.value) await adminAPI.updateCourse(editingId.value, body)
      else await adminAPI.createCourse(body)
    } else {
      const body = { name: String(f.name).trim() }
      if (f.collegeId !== null) body.collegeId = f.collegeId
      if (f.majorId !== null) body.majorId = f.majorId
      if (f.teacherId !== null) body.teacherId = f.teacherId
      if (f.counselorId !== null) body.counselorId = f.counselorId
      if (editingId.value) await adminAPI.updateClass(editingId.value, body)
      else await adminAPI.createClass(body)
    }
    toast(editingId.value ? '已保存' : '已创建')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '提交失败', 3200)
  } finally {
    submitting.value = false
  }
}

async function deleteCourse() {
  const c = currentCourse.value
  if (!c) return
  const ok = await confirm(`删除课程「${c.name}」？已有成绩记录不会被删除，但会失去对应课程。`, '删除课程')
  if (!ok) return
  try {
    await adminAPI.deleteCourse(c.id)
    toast('已删除')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '删除失败', 3200)
  }
}

async function deleteClass() {
  const k = currentClass.value
  if (!k) return
  const ok = await confirm(`删除班级「${k.name}」？班内 ${n(k.studentCount)} 名学生的班级归属需要另行处理。`, '删除班级')
  if (!ok) return
  try {
    await adminAPI.deleteClass(k.id)
    toast('已删除')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '删除失败', 3200)
  }
}

async function removeTeacher() {
  const k = currentClass.value
  if (!k) return
  const ok = await confirm(`把「${k.name}」的班主任（${teacherNameOfClass(k)}）移除？`, '移除班主任')
  if (!ok) return
  try {
    await adminAPI.removeTeacherFromClass(k.id)
    toast('已移除')
    sheetOpen.value = false
    await load()
  } catch (e) {
    toast((e && e.message) || '操作失败', 3200)
  }
}
</script>

<style lang="scss" scoped>
.page-body {
  padding-top: $spacing-sm;
  padding-bottom: $spacing-3xl;
}

.toolbar {
  display: flex;
  align-items: center;
  padding: 0 $page-padding $spacing-sm;
}

.toolbar__title {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text-secondary;
}

.toolbar__add {
  @include tap-area;

  display: flex;
  align-items: center;
  height: 64rpx;
  padding: 0 $spacing-lg;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

.toolbar__add-text {
  margin-left: 6rpx;
  font-size: $font-sm;
  color: #fff;
  font-weight: 500;
}

.card {
  padding: 0;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  overflow: hidden;
}

/* ---------- 课程行 ---------- */
.course,
.klass {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.course--divided,
.klass--divided {
  border-top: 1rpx solid $color-divider;
}

.course:active,
.klass:active {
  background: $color-bg;
}

.course__main,
.klass__main {
  flex: 1;
  min-width: 0;
}

.course__top,
.klass__top {
  display: flex;
  align-items: center;
}

.course__name,
.klass__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.course__top .tag,
.klass__top .tag {
  margin-left: $spacing-sm;
}

.course__meta,
.course__teacher,
.klass__meta,
.klass__sub {
  display: block;
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
  @include ellipsis;
}

.course__teacher,
.klass__sub {
  color: $color-text-secondary;
}

/* ---------- 抽屉 ---------- */
.sheet {
  padding-top: $spacing-md;
  padding-bottom: $spacing-xl;
}

.facts {
  padding: 0 $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.facts__row {
  display: flex;
  align-items: flex-start;
  padding: $spacing-md 0;
}

.facts__k {
  width: 180rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-hint;
}

.facts__v {
  flex: 1;
  min-width: 0;
  font-size: $font-sm;
  color: $color-text;
  text-align: right;
  word-break: break-all;
}

.field {
  margin-bottom: $spacing-lg;
}

.field__label {
  display: block;
  margin-bottom: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.field__input {
  height: 80rpx;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.field__ph {
  color: $color-text-hint;
}

.field__hint {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-text-hint;
}

.opts {
  display: flex;
  flex-wrap: wrap;
}

.opt {
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-pill;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.opt--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
  font-weight: 500;
}

.note {
  display: block;
  margin: $spacing-lg $page-padding 0;
  padding: $spacing-md;
  border-radius: $radius-sm;
  background: rgba(245, 158, 11, 0.1);
  font-size: $font-xs;
  line-height: 1.6;
  color: #92400e;
}

.sheet .note {
  margin-left: 0;
  margin-right: 0;
}

/* ---------- 底部 ---------- */
.acts {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: $spacing-md;
}

.act {
  height: $tap-min;
  padding: 0 $spacing-lg;
  margin: 0 $spacing-sm $spacing-sm 0;
  border-radius: $radius-md;
  background: $color-bg;
  border: 1rpx solid $color-border;
  font-size: $font-sm;
  color: $color-text-secondary;
  @include flex-center;
}

.act--danger {
  background: rgba(239, 68, 68, 0.1);
  border-color: transparent;
  color: $color-danger;
}

.footer-btn {
  width: 100%;
}

.form-acts {
  display: flex;
  align-items: center;
}

.form-acts__cancel {
  width: 200rpx;
  height: $tap-min;
  margin-right: $spacing-md;
  border-radius: var(--radius-md);
  background: $color-bg;
  color: $color-text-secondary;
  font-size: $font-md;
  @include flex-center;
}

.form-acts__ok {
  flex: 1;
  min-width: 0;
}
</style>
