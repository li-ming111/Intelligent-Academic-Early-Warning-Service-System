<template>
  <div class="cp-page">
    <div class="cp-hero">
      <div>
        <h1>排课管理</h1>
        <p>课程的开课安排：任课教师、学期、上课时间、教室与容量</p>
      </div>
      <el-button type="primary" @click="openAdd">
        <el-icon><Plus /></el-icon> 新增排课
      </el-button>
    </div>

    <div class="cp-toolbar">
      <el-input v-model="searchText" placeholder="搜索课程名 / 代码 / 教师 / 教室" style="width: 280px" clearable>
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filterSemester" placeholder="全部学期" style="width: 180px" clearable>
        <el-option v-for="s in semesterOptions" :key="s" :label="semesterLabel(s)" :value="s" />
      </el-select>
      <el-button :loading="loading" @click="loadPlans">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-card>
      <template #header>
        <div class="cp-card-head">
          <span>排课列表</span>
          <span class="cp-count">共 {{ filteredList.length }} 条</span>
        </div>
      </template>
      <el-table :data="filteredList" stripe v-loading="loading" empty-text="暂无排课记录">
        <el-table-column label="课程" min-width="200">
          <template #default="{ row }">
            <span class="cp-name">{{ courseName(row.courseId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任课教师" width="130">
          <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
        </el-table-column>
        <el-table-column label="学期" width="150">
          <template #default="{ row }">{{ semesterLabel(row.semester) }}</template>
        </el-table-column>
        <el-table-column prop="classTime" label="上课时间" width="130">
          <template #default="{ row }">{{ row.classTime || '待定' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="教室" width="140">
          <template #default="{ row }">{{ row.location || '待定' }}</template>
        </el-table-column>
        <el-table-column prop="maxStudents" label="容量" width="80" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '有效' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="askDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑排课' : '新增排课'" width="560px"
               :close-on-click-modal="false" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="form.courseId" filterable placeholder="选择课程" style="width:100%"
                     :disabled="isEdit" @change="onCourseChange">
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id">
              <span>{{ c.name }}</span>
              <span class="opt-sub">{{ c.code }} · {{ c.credits }}学分</span>
            </el-option>
          </el-select>
          <!-- 编辑时不给改课程：`updateCoursePlan` 是整行覆盖，
               换 courseId 等价于"这条排课变成另一门课"，语义上应该是删了重建。 -->
          <div v-if="isEdit" class="cp-hint">编辑时不可更换课程；如需换课请删除后重新新增。</div>
        </el-form-item>
        <el-form-item label="任课教师" prop="teacherId">
          <el-input :model-value="selectedTeacherName" disabled placeholder="选择课程后自动带出" />
          <div class="cp-hint">任课教师由课程的「授课教师」带出；如需更换，请到课程管理修改。</div>
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <!-- 学期是 '2025-2026-2' 这种串，不是数字 —— 与成绩那边的数字学期不同口径。
               用输入框而非下拉，因为校历学年会变，硬编码选项过一两年就成了错的。 -->
          <el-input v-model="form.semester" placeholder="如 2025-2026-2" />
        </el-form-item>
        <el-form-item label="学年" prop="year">
          <el-input-number v-model="form.year" :min="2000" :max="2100" />
        </el-form-item>
        <el-form-item label="上课时间" prop="classTime">
          <el-input v-model="form.classTime" placeholder="如 周一 1-2节" />
        </el-form-item>
        <el-form-item label="教室" prop="location">
          <el-select v-model="form.location" filterable allow-create default-first-option
                     placeholder="选择或输入教室" style="width:100%">
            <el-option v-for="l in locationOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="容量" prop="maxStudents">
          <el-input-number v-model="form.maxStudents" :min="1" :max="500" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="statusOn" active-text="有效" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">
          {{ isEdit ? '保存修改' : '新增排课' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 删除确认 -->
    <el-dialog v-model="deleteVisible" title="确认删除" width="420px">
      <div class="cp-del">
        <el-icon color="#E6A23C" size="20"><WarningFilled /></el-icon>
        <p>确定删除 <strong>{{ courseName(rowToDelete?.courseId) }}</strong> 的这条排课吗？</p>
        <p class="cp-del-hint">删除后学生/教师的课表里将不再显示这条安排。</p>
      </div>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search, WarningFilled } from '@element-plus/icons-vue'
import { adminAPI } from '@/api/index'

const loading = ref(false)
const list = ref([])
const courseOptions = ref([])
const teacherOptions = ref([])
const searchText = ref('')
const filterSemester = ref('')

const dialogVisible = ref(false)
const deleteVisible = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const isEdit = ref(false)
const rowToDelete = ref(null)
const formRef = ref(null)
const dateRange = ref([])
const statusOn = ref(true)

const form = ref({
  id: null, courseId: null, teacherId: null, semester: '', year: null,
  classTime: '', location: '', maxStudents: 60
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  teacherId: [{ required: true, message: '该课程未设置授课教师，请先到课程管理补充', trigger: 'change' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'blur' }],
  year: [{ required: true, message: '请输入学年', trigger: 'blur' }],
  classTime: [{ required: true, message: '请输入上课时间', trigger: 'blur' }],
  location: [{ required: true, message: '请选择或输入教室', trigger: 'change' }],
  maxStudents: [{ required: true, message: '请输入容量', trigger: 'blur' }]
}

// 教室只列**已存在**的取值，且下拉可手输 —— 演示数据里的教室只有
// 教学楼 / 实训楼 / 艺术楼三栋，不凭空造"城市/校区/门牌"这类本系统没有的口径。
const locationOptions = computed(() => {
  const set = new Set()
  list.value.forEach(r => { if (r.location) set.add(r.location) })
  return Array.from(set).sort()
})

const semesterOptions = computed(() => {
  const set = new Set()
  list.value.forEach(r => { if (r.semester) set.add(r.semester) })
  return Array.from(set).sort()
})

const filteredList = computed(() => {
  const kw = searchText.value.trim().toLowerCase()
  return list.value.filter(r => {
    if (filterSemester.value && r.semester !== filterSemester.value) return false
    if (!kw) return true
    const hay = [
      courseName(r.courseId),
      teacherName(r.teacherId),
      r.location,
      r.classTime,
      String(r.courseId)
    ].join(' ').toLowerCase()
    return hay.includes(kw)
  })
})

const courseName = (id) => {
  const c = courseOptions.value.find(x => x.id === id)
  return c ? c.name : '未知课程'
}

const teacherName = (id) => {
  if (id == null) return '—'
  const t = teacherOptions.value.find(x => x.id === id)
  return t ? t.name : '未知教师'
}

// 空值返回空串，让输入框落到 placeholder 而不是显示"未知教师"
const selectedTeacherName = computed(() =>
  form.value.teacherId == null ? '' : teacherName(form.value.teacherId))

const semesterLabel = (s) => {
  if (!s) return '—'
  const m = /^(\d{4}-\d{4})-(\d)$/.exec(s)
  return m ? `${m[1]} 学年 第${m[2]}学期` : s
}

const loadPlans = async () => {
  loading.value = true
  try {
    const res = await adminAPI.getAllCoursePlans()
    // `/course-plans/all` 回的是**裸数组**（Spring ResponseEntity），不是 ApiResponse 信封。
    if (!Array.isArray(res)) throw new Error('排课数据格式异常')
    list.value = res
  } catch (error) {
    console.error('[AdminCoursePlans] 加载排课失败:', error)
    ElMessage.error(error?.message ? `加载排课失败：${error.message}` : '加载排课失败')
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await adminAPI.getCourses()
    const arr = Array.isArray(res) ? res : (res?.items || res?.records || res?.list || [])
    courseOptions.value = arr
  } catch (error) {
    console.error('[AdminCoursePlans] 加载课程选项失败:', error)
  }
}

const loadTeachers = async () => {
  try {
    // role=2 是教师。`/admin/users/page` 回 `{total, items:[...]}`（实测），
    // 不是 records 也不是裸数组 —— 三个分支都写，认不出就空着，不猜。
    const res = await adminAPI.getUsers(1, 999, null, 2)
    let arr = []
    if (Array.isArray(res)) arr = res
    else if (Array.isArray(res?.items)) arr = res.items
    else if (Array.isArray(res?.records)) arr = res.records
    else if (Array.isArray(res?.list)) arr = res.list
    teacherOptions.value = arr
  } catch (error) {
    console.error('[AdminCoursePlans] 加载教师选项失败:', error)
  }
}

const openAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const onCourseChange = (courseId) => {
  const course = courseOptions.value.find(c => c.id === courseId)
  form.value.teacherId = course?.teacherId != null ? Number(course.teacherId) : null
}

const openEdit = (row) => {
  isEdit.value = true
  const course = courseOptions.value.find(c => c.id === row.courseId)
  form.value = {
    id: row.id,
    courseId: row.courseId,
    // 任课教师以课程为准；只有课程查不到时才回落到这条排课自己的值
    teacherId: course?.teacherId != null ? Number(course.teacherId) : row.teacherId,
    semester: row.semester || '',
    year: row.year,
    classTime: row.classTime || '',
    location: row.location || '',
    maxStudents: row.maxStudents ?? 60
  }
  dateRange.value = [row.startDate || null, row.endDate || null].filter(Boolean)
  statusOn.value = row.status !== 0
  dialogVisible.value = true
}

const resetForm = () => {
  form.value = {
    id: null, courseId: null, teacherId: null, semester: '', year: null,
    classTime: '', location: '', maxStudents: 60
  }
  dateRange.value = []
  statusOn.value = true
  formRef.value?.clearValidate?.()
}

const buildPayload = () => ({
  id: form.value.id,
  courseId: form.value.courseId,
  teacherId: form.value.teacherId,
  semester: form.value.semester,
  year: form.value.year,
  classTime: form.value.classTime,
  location: form.value.location,
  maxStudents: form.value.maxStudents,
  startDate: dateRange.value?.[0] || null,
  endDate: dateRange.value?.[1] || null,
  status: statusOn.value ? 1 : 0
})

const submitForm = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await adminAPI.updateCoursePlan(form.value.id, payload)
    } else {
      await adminAPI.createCoursePlan(payload)
    }
    ElMessage.success(isEdit.value ? '排课已更新' : '排课已新增')
    dialogVisible.value = false
    await loadPlans()
  } catch (error) {
    // 写端点回 HTTP 400 + `{success:false, message}`（非 ApiResponse 信封），
    // 拦截器把它当异常抛出，message 里就是后端那句话。
    console.error('[AdminCoursePlans] 保存失败:', error)
    ElMessage.error(error?.message ? `保存失败：${error.message}` : '保存失败')
  } finally {
    submitLoading.value = false
  }
}

const askDelete = (row) => {
  rowToDelete.value = row
  deleteVisible.value = true
}

const confirmDelete = async () => {
  if (!rowToDelete.value) return
  deleteLoading.value = true
  try {
    await adminAPI.deleteCoursePlan(rowToDelete.value.id)
    ElMessage.success('排课已删除')
    deleteVisible.value = false
    rowToDelete.value = null
    await loadPlans()
  } catch (error) {
    console.error('[AdminCoursePlans] 删除失败:', error)
    ElMessage.error(error?.message ? `删除失败：${error.message}` : '删除失败')
  } finally {
    deleteLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadTeachers()])
  await loadPlans()
})
</script>

<style scoped>
.cp-page { padding: 20px; min-height: 100vh; background: #f5f7fb; }
.cp-hero { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.cp-hero h1 { font-size: 24px; margin: 0 0 6px; color: #18181b; }
.cp-hero p { margin: 0; font-size: 13px; color: #71717a; }

.cp-toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.cp-card-head { display: flex; justify-content: space-between; align-items: center; }
.cp-count { font-size: 13px; color: #71717a; font-weight: 400; }

.cp-name { font-weight: 600; color: #18181b; }
.cp-hint { font-size: 11px; color: #a1a1aa; line-height: 1.5; margin-top: 2px; }
.opt-sub { float: right; color: #94a3b8; font-size: 12px; }

.cp-del { display: flex; gap: 10px; align-items: flex-start; }
.cp-del p { margin: 0 0 6px; }
.cp-del-hint { font-size: 12px; color: #a1a1aa; }

@media (max-width: 768px) {
  .cp-hero { flex-direction: column; align-items: stretch; gap: 10px; }
}
</style>
