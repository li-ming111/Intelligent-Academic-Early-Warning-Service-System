
<template>
  <div class="tc-page">
    <div class="tc-hero">
      <div class="tch-left">
        <h1>课程管理</h1>
        <p>教学课程、学生花名册与选修学分达标追踪</p>
      </div>
      <el-button type="primary" @click="openAddCourse"><el-icon><Plus /></el-icon> 添加课程</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="tc-stats">
      <div class="tcs-card blue">
        <div class="tcs-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg></div>
        <div class="tcs-num">{{ stats.total }}</div>
        <div class="tcs-lbl">课程总数</div>
      </div>
      <div class="tcs-card purple">
        <div class="tcs-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
        <div class="tcs-num">{{ stats.students }}</div>
        <div class="tcs-lbl">选修学生</div>
      </div>
      <div class="tcs-card green">
        <div class="tcs-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/></svg></div>
        <div class="tcs-num">{{ stats.pass }}</div>
        <div class="tcs-lbl">及格率</div>
      </div>
      <div class="tcs-card amber">
        <div class="tcs-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
        <div class="tcs-num">{{ stats.avgRating }}</div>
        <div class="tcs-lbl">平均分</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="tc-tabs">
      <!-- Tab 1: 我的课程 -->
      <el-tab-pane label="我的课程" name="courses">
        <div class="tc-grid" v-if="courseList.length">
          <div v-for="c in courseList" :key="c.courseId" class="tc-card">
            <div class="tcc-top">
              <div class="tcc-avatar" :style="{ background: grad(c.courseId) }">{{ (c.courseName || '课').charAt(0) }}</div>
              <div class="tcc-head">
                <div class="tcch-name">{{ c.courseName }}</div>
                <div class="tcch-meta">
                  <el-tag size="small" :type="c.type === '必修课' ? 'danger' : 'info'" effect="plain">{{ c.type || '选修' }}</el-tag>
                  <span class="tcch-cr">{{ c.credits }}学分</span>
                </div>
              </div>
            </div>
            <div class="tcc-nums">
              <div class="tccn"><span>选修人数</span><b>{{ c.enrollmentCount || 0 }}</b></div>
              <div class="tccn"><span>及格率</span><b>{{ c.passRate || '--' }}</b></div>
              <div class="tccn"><span>班级</span><b>{{ c.className || '--' }}</b></div>
            </div>
            <div class="tcc-acts">
              <el-button size="small" @click="openRoster(c)">花名册</el-button>
              <el-button size="small" type="primary" plain @click="recommend(c)">推荐选课</el-button>
            </div>
          </div>
        </div>
        <div v-else class="tc-empty"><p>暂无已分配课程</p><span>点击右上角"添加课程"创建您的教学课程</span></div>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加课程弹窗（搜索/输入课程+班级选择） -->
    <el-dialog v-model="addVisible" title="添加课程" width="480px" destroy-on-close>
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="课程名称" required>
          <el-select
            v-model="addForm.selectedCourseId"
            filterable
            placeholder="搜索课程名称..."
            style="width:100%"
            @change="onCourseSelect"
          >
            <el-option
              v-for="c in courseSearchResults"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            >
              <span>{{ c.name }}</span>
              <span style="float:right;color:#94a3b8;font-size:12px">{{ c.code }} · {{ c.credits }}学分</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="授课班级" required>
          <el-select v-model="addForm.classId" placeholder="选择授课班级" style="width:100%">
            <el-option v-for="c in myClasses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程描述"><el-input v-model="addForm.description" type="textarea" rows="2" placeholder="课程简介（可选）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAddCourse" :loading="adding">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 花名册弹窗（按班级分组） -->
    <el-dialog v-model="rosterVisible" :title="rosterCourse.courseName + ' 学生花名册'" width="800px" destroy-on-close>
      <div v-if="rosterClasses.length" class="roster-wrap">
        <el-tabs v-model="rosterActiveClass" type="card">
          <el-tab-pane v-for="rc in rosterClasses" :key="rc.name" :label="rc.name + '(' + rc.students.length + '人)'" :name="rc.name">
            <el-table :data="rc.students" stripe max-height="360">
              <el-table-column type="index" label="#" width="45" />
              <el-table-column prop="studentId" label="学号" width="140" />
              <el-table-column prop="studentName" label="姓名" width="90" />
              <el-table-column label="成绩" width="80" align="center">
                <template #default="{ row }"><span :class="row.score >= 60 ? 'cr-ok' : 'cr-low'">{{ row.score || '--' }}</span></template>
              </el-table-column>
              <el-table-column label="等级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.scoreLevel >= 4" type="success" size="small">优秀</el-tag>
                  <el-tag v-else-if="row.scoreLevel >= 3" type="primary" size="small">良好</el-tag>
                  <el-tag v-else-if="row.scoreLevel >= 2" type="warning" size="small">中等</el-tag>
                  <el-tag v-else-if="row.scoreLevel >= 1" type="danger" size="small">及格</el-tag>
                  <el-tag v-else-if="row.scoreLevel === 0" type="danger" size="small">不及格</el-tag>
                  <span v-else>--</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
        <!-- 班级统计 -->
        <div class="roster-summary">
          <span>共 {{ rosterAllStudents.length }} 名学生，分布在 {{ rosterClasses.length }} 个班级</span>
        </div>
      </div>
      <div v-else class="tc-empty" style="padding:30px">暂无学生选修此课程</div>
    </el-dialog>

    <!-- 推荐选课弹窗 -->
    <el-dialog v-model="recVisible" title="推荐选课" width="460px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="推荐课程"><el-tag type="primary" effect="plain" size="large">{{ recForm.courseName }}</el-tag></el-form-item>
        <el-form-item label="推荐理由"><el-input v-model="recForm.reason" type="textarea" rows="3" placeholder="说明推荐理由..." /></el-form-item>
      </el-form>
      <template #footer><el-button @click="recVisible = false">取消</el-button><el-button type="primary" @click="submitRec">发送</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { teacherAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'
import { toNumber, PASS_LINE } from '@/utils/score'

const activeTab = ref('courses')
const courseList = ref([])
const myClasses = ref([])
const recVisible = ref(false)
const addVisible = ref(false)
const adding = ref(false)
const allCourses = ref([])
const courseSearchResults = ref([])
const rosterVisible = ref(false)
const rosterCourse = ref({})
const rosterClasses = ref([])
const rosterActiveClass = ref('')
const rosterAllStudents = ref([])
const recForm = reactive({ courseId: null, courseName: '', reason: '' })
const addForm = reactive({ selectedCourseId: null, name: '', code: '', credits: 2, type: '选修课', description: '', classId: '' })
const stats = reactive({ total: 0, students: 0, pass: 0, avgRating: '--' })

const colors = ['#7c3aed', '#2563eb', '#16a34a', '#f59e0b', '#ef4444', '#8b5cf6', '#db2777', '#ea580c', '#0891b2']
function grad(id) { return `linear-gradient(135deg, ${colors[(id || 1) % colors.length]}, ${colors[(id || 1) % colors.length]}cc)` }
const getTeacherId = () => localStorage.getItem('teacherId') || getUserId() || '9'

onMounted(async () => { await Promise.all([loadAllCourses(), loadMyClasses(), loadData()]) })

async function loadAllCourses() {
  try {
    // 使用教师端 GET /courses（不传 teacher_id 参数获取全部课程）
    const res = await teacherAPI.getAllCourses()
    // 2026-10-02（W4.15）：删掉恒假的 `res?.code === 200` 支（拦截器已解包）。
    const raw = Array.isArray(res) ? res : []
    allCourses.value = raw
    courseSearchResults.value = raw
  } catch (e) { /* 降级 */ }
}

function onCourseSelect(val) {
  if (!val) {
    addForm.name = ''; addForm.code = ''; addForm.credits = 2; addForm.type = '选修课'; addForm.description = ''
    addForm.selectedCourseId = null
    return
  }
  const c = allCourses.value.find(c => c.id === val)
  if (c) {
    addForm.name = c.name || ''
    addForm.code = c.code || ''
    addForm.credits = c.credits || 2
    addForm.type = c.type || '选修课'
    addForm.description = c.description || ''
    addForm.selectedCourseId = val
  }
}

async function loadMyClasses() {
  try {
    const tid = getTeacherId()
    console.log('[TeacherCourses] loading classes for teacherId:', tid)
    const res = await teacherAPI.getMyClasses(tid)
    if (Array.isArray(res)) {
      myClasses.value = res
      console.log('[TeacherCourses] classes loaded:', res.length)
    } else {
      // 2026-10-02（W4.15）：删掉中间的 `res?.code === 200` 支（拦截器已解包，恒假）。
      console.warn('[TeacherCourses] unexpected response:', res)
    }
  } catch (e) {
    console.error('[TeacherCourses] loadMyClasses failed:', e)
    // 降级：加载该教师关联的所有课程信息
    try {
      const tid = getTeacherId()
      const courseRes = await teacherAPI.getCourses(tid)
      if (Array.isArray(courseRes) && courseRes.length) {
        // 从课程记录中提取班级名
        const classNames = [...new Set(courseRes.map(c => c.className).filter(Boolean))]
        if (classNames.length) {
          myClasses.value = classNames.map((name, i) => ({ id: i + 1, name }))
          console.log('[TeacherCourses] fallback classes from courses:', myClasses.value.length)
        }
      }
    } catch (e2) { console.error('[TeacherCourses] fallback also failed:', e2) }
  }
}

async function loadData() {
  try {
    const tid = getTeacherId()
    const [coursesRes, enrollRes] = await Promise.all([
      teacherAPI.getCourses(tid).catch(() => null),
      teacherAPI.getEnrollments(tid).catch(() => null)
    ])
    // 2026-10-02（W4.15）：两处 `?.code === 200` 支都是恒假的（拦截器已解包）。
    const assignedCourses = Array.isArray(coursesRes) ? coursesRes : []
    const allEnrollments = Array.isArray(enrollRes) ? enrollRes : []

    const courseMap = new Map()
    // `rating` 这个 key 从头到尾没有任何一列、也没有任何模板读它，2026-10-01 第二十一轮删。
    assignedCourses.forEach(c => { courseMap.set(c.id, { courseId: c.id, courseName: c.name || '课程' + c.id, credits: c.credits || 2, type: c.type || '必修', className: '--', enrollmentCount: 0, passRate: '--', students: [] }) })
    allEnrollments.forEach(e => {
      const cid = e.courseId; if (!cid) return
      if (!courseMap.has(cid)) courseMap.set(cid, { courseId: cid, courseName: e.courseName || '课程' + cid, credits: e.credits || 2, type: e.type || '选修', className: e.className || '--', enrollmentCount: 0, passRate: '--', students: [] })
      const c = courseMap.get(cid); c.enrollmentCount++; c.students.push(e)
    })

    // 逐门课算「及格率」与「班级」。这两格原先在两条分支里都被写死成 '--'，
    // 于是卡片上「及格率 -- / 班级 --」永远如此，与旁边真实的选课人数并存 ——
    // 看着像"数据还没加载"，其实是根本没人算。数据就在这批选课记录里
    // （每行都带 score 与 className，见 TeacherController#getEnrollments 的两条分支）。
    courseMap.forEach(c => {
      const classNames = [...new Set(c.students.map(s => s.className).filter(Boolean))]
      c.className = classNames.length ? classNames.join('、') : '--'
      // 用 toNumber 而不是 `score >= 60`：未录入的分数是字符串 '待录入'，
      // `'待录入' >= 60` 是 false，会被当成"没及格"混进分母，把及格率压下去。
      const graded = c.students.map(s => toNumber(s.score)).filter(v => v !== null)
      c.passRate = graded.length
        ? Math.round(graded.filter(v => v >= PASS_LINE).length / graded.length * 100) + '%'
        : '--'
    })

    courseList.value = [...courseMap.values()]
    stats.total = courseList.value.length
    stats.students = courseList.value.reduce((s, c) => s + c.enrollmentCount, 0)
    // 同上一处：只统计**真的录了分**的选课记录，'待录入' 不算及格也不算不及格。
    const gradedAll = allEnrollments.map(e => toNumber(e.score)).filter(v => v !== null)
    if (gradedAll.length) {
      stats.pass = Math.round(gradedAll.filter(v => v >= PASS_LINE).length / gradedAll.length * 100)
      stats.avgRating = (gradedAll.reduce((s, v) => s + v, 0) / gradedAll.length).toFixed(1)
    }

  } catch (e) { console.error(e) }
}

function openAddCourse() {
  addForm.selectedCourseId = null
  addForm.name = ''; addForm.code = ''; addForm.credits = 2; addForm.type = '选修课'; addForm.description = ''; addForm.classId = ''
  addVisible.value = true
}
async function submitAddCourse() {
  if (!addForm.selectedCourseId) { ElMessage.error('请选择课程'); return }
  if (!addForm.classId) { ElMessage.error('请选择授课班级'); return }
  adding.value = true
  try {
    const tid = getTeacherId()
    await teacherAPI.updateCourse(addForm.selectedCourseId, { teacherId: Number(tid) })
    ElMessage.success('课程关联成功')
    addVisible.value = false
    await loadData()
  } catch (e) { ElMessage.error('关联失败: ' + (e?.message || '未知错误')) }
  finally { adding.value = false }
}
function openRoster(c) {
  rosterCourse.value = c
  const all = (c.students || []).map(s => ({
    studentId: s.studentId || s.studentNo,
    studentName: s.studentName,
    className: s.className || '未知班级',
    score: s.score || s.scoreTotal || '--',
    scoreLevel: s.scoreLevel,
    remark: s.remark || ''
  }))
  rosterAllStudents.value = all
  // 按班级分组
  const classMap = new Map()
  all.forEach(s => {
    const key = s.className
    if (!classMap.has(key)) classMap.set(key, [])
    classMap.get(key).push(s)
  })
  rosterClasses.value = [...classMap.entries()].map(([name, students]) => ({ name, students }))
  rosterActiveClass.value = rosterClasses.value[0]?.name || ''
  rosterVisible.value = true
}
function recommend(c) { recForm.courseId = c.courseId; recForm.courseName = c.courseName; recForm.reason = ''; recVisible.value = true }
async function submitRec() {
  if (!recForm.reason) { ElMessage.error('请输入推荐理由'); return }
  try { await teacherAPI.recommendCourse({ courseId: recForm.courseId, reason: recForm.reason }); ElMessage.success('推荐已发送'); recVisible.value = false } catch (e) { ElMessage.error('发送失败') }
}
</script>

<style scoped>
.tc-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }
.tc-hero { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.tch-left h1 { margin: 0; font-size: 22px; color: #1e293b; }
.tch-left p { margin: 4px 0 0; font-size: 13px; color: #94a3b8; }

.tc-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 18px; }
.tcs-card { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.tcs-card.blue { border-left: 4px solid #3b82f6; } .tcs-card.purple { border-left: 4px solid #7c3aed; }
.tcs-card.green { border-left: 4px solid #16a34a; } .tcs-card.amber { border-left: 4px solid #f59e0b; }
.tcs-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.tcs-card.blue .tcs-icon { background: #eff6ff; color: #3b82f6; } .tcs-card.purple .tcs-icon { background: #f5f3ff; color: #7c3aed; }
.tcs-card.green .tcs-icon { background: #ecfdf5; color: #16a34a; } .tcs-card.amber .tcs-icon { background: #fffbeb; color: #f59e0b; }
.tcs-num { font-size: 24px; font-weight: 700; color: #18181b; } .tcs-lbl { font-size: 12px; color: #94a3b8; }

.tc-tabs { margin-bottom: 0; }
:deep(.tc-tabs .el-tabs__header) { margin-bottom: 14px; }

/* 规则提示 */
.tc-rule-bar { display: flex; gap: 24px; background: #eff6ff; border-radius: 8px; padding: 10px 18px; margin-bottom: 14px; font-size: 13px; color: #1e40af; }
.tcr-item strong { color: #1d4ed8; }

/* 选修统计 */
.tc-elective-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.tces { background: #fff; border-radius: 10px; padding: 14px; text-align: center; box-shadow: 0 1px 2px rgba(0,0,0,.03); }
.tces-num { font-size: 22px; font-weight: 700; } .tces-lbl { font-size: 12px; color: #94a3b8; }
.tces-num.green { color: #16a34a; } .tces-num.amber { color: #f59e0b; } .tces-num.red { color: #ef4444; }

.tc-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; }
.tc-card { background: #fff; border-radius: 12px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,.04); display: flex; flex-direction: column; gap: 14px; transition: box-shadow .2s; }
.tc-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.tcc-top { display: flex; align-items: center; gap: 14px; }
.tcc-avatar { width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: 700; flex-shrink: 0; }
.tcc-head { flex: 1; min-width: 0; } .tcch-name { font-size: 15px; font-weight: 600; color: #18181b; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tcch-meta { display: flex; align-items: center; gap: 8px; } .tcch-cr { font-size: 12px; color: #94a3b8; }
.tcc-nums { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
.tccn { background: #f8fafc; border-radius: 8px; padding: 10px; text-align: center; }
.tccn span { display: block; font-size: 11px; color: #94a3b8; margin-bottom: 3px; }
.tccn b { font-size: 16px; color: #18181b; }
.tcc-acts { display: flex; gap: 8px; justify-content: flex-end; }
.tc-table-wrap { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; padding: 0 4px 8px; }
.cr-ok { color: #16a34a; font-weight: 600; } .cr-low { color: #ef4444; font-weight: 600; } .cr-gap { color: #f59e0b; font-weight: 600; } .cr-warn { color: #f59e0b; font-weight: 600; }
.sug-ok { color: #16a34a; } .sug-text { color: #64748b; font-size: 12px; }
.text-muted { color: #94a3b8; }
.tc-empty { text-align: center; padding: 60px 20px; background: #fff; border-radius: 12px; }
.tc-empty p { color: #94a3b8; font-size: 14px; margin: 0 0 6px; } .tc-empty span { color: #cbd5e1; font-size: 12px; }

.roster-wrap { }
.roster-summary { padding: 10px 0 0; font-size: 13px; color: #64748b; text-align: center; }
</style>
