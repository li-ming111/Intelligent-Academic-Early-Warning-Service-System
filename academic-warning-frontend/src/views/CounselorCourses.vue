
<template>
  <div class="cc-page">
    <div class="cc-hero">
      <div class="cch-left">
        <h1>选修课管理</h1>
        <p>班级选课概览、学分核查与修读进度追踪</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="cc-stats">
      <div class="ccs-card blue">
        <div class="ccs-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>
        </div>
        <div class="ccs-body">
          <div class="ccs-num">{{ stats.totalEnrollments }}</div>
          <div class="ccs-lbl">选课记录</div>
        </div>
      </div>
      <div class="ccs-card green">
        <div class="ccs-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>
        </div>
        <div class="ccs-body">
          <div class="ccs-num">{{ stats.avgCredits }}</div>
          <div class="ccs-lbl">人均学分</div>
        </div>
      </div>
      <div class="ccs-card amber">
        <div class="ccs-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        </div>
        <div class="ccs-body">
          <div class="ccs-num amber">{{ stats.insufficientCount }}</div>
          <!-- 2026-10-02（W4.12）：这一格数的是"有挂科的学生"，不是"学分低于某条线"。 -->
          <div class="ccs-lbl">有挂科学生</div>
        </div>
      </div>
      <div class="ccs-card purple">
        <div class="ccs-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
        </div>
        <div class="ccs-body">
          <div class="ccs-num">{{ studentList.length }}</div>
          <div class="ccs-lbl">在读学生</div>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="cc-toolbar">
      <el-select v-model="selectedClassId" placeholder="选择班级" @change="onClassChange" style="width: 220px;">
        <el-option v-for="cls in classList" :key="cls.id" :label="cls.name" :value="cls.id" />
      </el-select>
      <el-input v-model="searchName" placeholder="搜索学生姓名" clearable style="width: 200px;" />
      <!-- 2026-10-02（W4.12）：原来写"学分达标线: 8学分"—— 8 这个数全仓没有来源。
           改为如实写出本页使用的口径（与「学分监控」页一致）。 -->
      <el-tag v-if="selectedClassId" type="info" effect="plain">达标口径：已录入成绩的课程全部通过</el-tag>
    </div>

    <!-- 学生修读详情表 -->
    <div class="cc-card">
      <div class="ccc-head">
        <span>学生修读详情</span>
        <span class="ccc-badge" v-if="filteredStudents.length">{{ filteredStudents.length }}人</span>
      </div>
      <el-table :data="filteredStudents" stripe v-if="filteredStudents.length" row-class-name="cc-row">
        <el-table-column prop="studentName" label="姓名" width="110" />
        <el-table-column label="已修课程数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.courseCount || 0 }}门</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已获学分" width="110" align="center">
          <template #default="{ row }">
            <span :class="row.achieved ? 'credits-ok' : 'credits-low'">{{ (row.earnedCredits || 0).toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="达标状态" width="120">
          <template #default="{ row }">
            <!-- 2026-10-02（W4.12）：原来判 `totalCredits >= 8` 并显示"缺X分"。
                 现在按项目口径判"有没有挂科"，缺的是**因挂科未拿到的学分**（真实减法）。 -->
            <el-tag :type="row.achieved ? 'success' : 'danger'" size="small">
              {{ row.achieved ? '已达标' : row.failedCourses + '门挂科·缺' + (row.lostCredits || 0).toFixed(1) + '分' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avgScore" label="均分" width="80" align="center">
          <template #default="{ row }">
            {{ row.avgScore ? row.avgScore.toFixed(0) : '--' }}
          </template>
        </el-table-column>
        <el-table-column label="最近课程" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.recentCourses" class="recent-txt">{{ row.recentCourses }}</span>
            <span v-else class="recent-none">--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="cc-empty">请先选择一个班级</div>
    </div>

    <!-- 学分不足预警 -->
    <div class="cc-card" v-if="insufficientList.length > 0" style="margin-top: 18px;">
      <div class="ccc-head warn">
        <!-- 2026-10-02（W4.12）：正名 —— 列的是"有挂科"的学生（口径同「学分监控」页）。 -->
        <span>挂科学生（学分未修满）</span>
        <span class="ccc-badge danger">{{ insufficientList.length }}人</span>
      </div>
      <div class="cc-warn-grid">
        <div v-for="s in insufficientList" :key="s.studentId" class="ccw-item">
          <div class="ccw-avatar">{{ (s.studentName || '?').charAt(0) }}</div>
          <div class="ccw-info">
            <div class="ccw-name">{{ s.studentName }}</div>
            <div class="ccw-meta">已修 {{ s.totalCredits.toFixed(1) }} 学分 · 通过 {{ s.earnedCredits.toFixed(1) }} · 挂科 {{ s.failedCourses }} 门 · 均分 {{ s.avgScore ? s.avgScore.toFixed(0) : '--' }}</div>
            <!-- 进度条画"已通过学分 / 已修学分"，不再拿一个凭空的 8 当分母 -->
            <div class="ccw-bar"><div class="ccw-fill" :style="{ width: (s.totalCredits > 0 ? (s.earnedCredits / s.totalCredits * 100) : 0) + '%' }"></div></div>
          </div>
          <el-button type="warning" size="small" plain @click="notifyStudent(s)">提醒</el-button>
        </div>
      </div>
    </div>

    <!-- 学生详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailStudent.studentName + ' 修读详情'" width="560px" destroy-on-close>
      <div v-if="detailStudent" class="cc-detail">
        <div class="ccd-summary">
          <div class="ccds-item"><label>已修课程</label><b>{{ detailStudent.courseCount }}门</b></div>
          <div class="ccds-item"><label>已获学分</label><b>{{ detailStudent.earnedCredits?.toFixed(1) }}</b></div>
          <div class="ccds-item"><label>平均分</label><b>{{ detailStudent.avgScore?.toFixed(1) || '--' }}</b></div>
          <div class="ccds-item"><label>达标状态</label>
            <el-tag :type="detailStudent.achieved ? 'success' : 'danger'" size="small">
              {{ detailStudent.achieved ? '已达标' : '挂科 ' + detailStudent.failedCourses + ' 门，缺 ' + (detailStudent.lostCredits || 0).toFixed(1) + ' 学分' }}
            </el-tag>
          </div>
        </div>
        <div class="ccd-courses" v-if="detailCourses.length">
          <div class="ccdc-title">课程明细</div>
          <div v-for="c in detailCourses" :key="c.courseName" class="ccdc-item">
            <span class="ccdci-name">{{ c.courseName }}</span>
            <!-- 2026-10-02（W4.12）：原来判 `c.score >= 60`，而 score 是字符串 →
                 '待录入' >= 60 恒 false ⇒ 未录入的一律标红"不及格"；
                 数字字符串 `'75.0' >= 60` 恰好是对的，纯属巧合。改用后端算好的布尔。 -->
            <span class="ccdci-score" :class="c.failed ? 'fail' : 'pass'">{{ c.scoreText }}</span>
          </div>
        </div>
        <div v-else class="ccd-empty">暂无课程明细</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { counselorAPI } from '@/api/index'
import { getUserId } from '@/utils/userUtils'

const selectedClassId = ref('')
const searchName = ref('')
const classList = ref([])
const allEnrollments = ref([])
const studentList = ref([])
const insufficientList = ref([])
const detailVisible = ref(false)
const detailStudent = ref({})
const detailCourses = ref([])

const stats = reactive({
  totalEnrollments: 0,
  avgCredits: 0,
  insufficientCount: 0,
  studentCount: 0
})

onMounted(async () => {
  await loadClasses()
})

const loadClasses = async () => {
  try {
    const uid = getUserId()
    if (!uid) return
    const res = await counselorAPI.getClasses(uid)
    // 2026-10-02（W4.15）：删掉恒假的 `res?.code === 200` 支（拦截器已解包）。
    const raw = Array.isArray(res) ? res : []
    classList.value = raw
    if (raw.length > 0) {
      selectedClassId.value = raw[0].id
      await loadEnrollments(raw[0].id)
      buildStudentList()
    }
  } catch (e) { console.error('加载班级失败', e) }
}

const onClassChange = async (classId) => {
  if (!classId) return
  await loadEnrollments(classId)
  buildStudentList()
}

const loadEnrollments = async (classId) => {
  try {
    const uid = getUserId()
    if (!uid) return
    // 使用 getClassScores 按 classId 精准获取 + SQL JOIN 含 course_name/credits
    const res = await counselorAPI.getClassScores(uid, classId)
    // 2026-10-02（W4.15）：删掉恒假的 `res?.code === 200` 支（拦截器已解包）。
    const raw = Array.isArray(res) ? res : []
    allEnrollments.value = raw
  } catch (e) { console.error('加载成绩数据失败', e) }
}

const buildStudentList = () => {
  const raw = allEnrollments.value
  if (!raw.length) {
    studentList.value = []
    insufficientList.value = []
    updateStats(0)
    return
  }

  // 按学生聚合 (snake_case 字段: student_id, student_name, course_id, course_name, credits, score_total)
  //
  // 2026-10-02（W4.12）：「达标」的口径统一到项目既有定义，不再自己写一条线。
  // 本页原来用 `totalCredits < 8`（"毕业学分线 8"）判定，而：
  //   · 8 这个数**全仓没有任何来源** —— 后端 `ScoreCaliber.GRADUATION_CREDITS` 是 120，
  //     且注释写明"目前无对应数据表，全项目只此一处"；
  //   · 同一个辅导员端，「学分监控」页（`CounselorCreditMonitor.vue`，走
  //     `ScoreMapper.getClassScoreStatsByCounselorId`）的口径是
  //     **"该生所有已录入成绩的课程均已通过"**，压根不是学分数量比较。
  // 于是同一个辅导员在两个页面上看到两种"达标"，数字互相打架。这里改用后一种口径。
  //
  // 数据源本身已按课程去重：`getScoresByClassId` 走 `ScoreCaliber.LATEST_VALID_SCORE`，
  // 每 (学生, 课程) 只出一行、且已过滤掉期末未录入的。
  const studentMap = new Map()
  raw.forEach(e => {
    const sid = e.student_id
    if (!sid) return
    if (!studentMap.has(sid)) {
      studentMap.set(sid, {
        studentId: sid,
        studentName: e.student_name || '未知学生',
        scores: [],
        courseNames: new Set(),
        attemptedCredits: 0,
        earnedCredits: 0,
        failedCourses: 0
      })
    }
    const s = studentMap.get(sid)
    const score = e.score_total === null || e.score_total === undefined ? null : Number(e.score_total)
    // ⚠️ 未录入（null）**不计入**挂科，也不计进均分 —— 这正是 ScoreCaliber 第 3 条口径。
    // 老代码写 `e.score_total || 0` 会把 null 变成 0 分，未录入的课被算成不及格。
    if (score !== null) {
      s.scores.push(score)
      if (score < 60) s.failedCourses++
    }
    const credits = Number(e.credits || 0)
    s.attemptedCredits += credits
    if (score !== null && score >= 60) s.earnedCredits += credits
    s.courseNames.add(e.course_name || '未知课程')
  })

  const list = Array.from(studentMap.values()).map(s => ({
    studentId: s.studentId,
    studentName: s.studentName,
    courseCount: s.courseNames.size,
    // 保留 `totalCredits` 这个名字：模板与详情弹窗都在用，语义 = 已修（尝试）学分。
    totalCredits: s.attemptedCredits,
    earnedCredits: s.earnedCredits,
    // 因挂科而未拿到的学分 = 已修 − 已通过。纯减法，不是估的。
    lostCredits: Math.max(s.attemptedCredits - s.earnedCredits, 0),
    failedCourses: s.failedCourses,
    // 达标 = 没有挂科（项目既有口径），不跟任何数字比大小。
    achieved: s.failedCourses === 0,
    avgScore: s.scores.length ? s.scores.reduce((a, b) => a + Number(b), 0) / s.scores.length : 0,
    recentCourses: [...s.courseNames].slice(-3).join('、')
  }))

  studentList.value = list
  insufficientList.value = list.filter(s => !s.achieved)
  updateStats(list.length)
}

const updateStats = (totalStudents) => {
  stats.totalEnrollments = allEnrollments.value.length
  stats.studentCount = totalStudents
  stats.insufficientCount = insufficientList.value.length
  stats.avgCredits = totalStudents > 0
    ? (studentList.value.reduce((s, c) => s + c.totalCredits, 0) / totalStudents).toFixed(1)
    : 0
}

const filteredStudents = computed(() => {
  if (!searchName.value) return studentList.value
  const kw = searchName.value.toLowerCase()
  return studentList.value.filter(s => (s.studentName || '').toLowerCase().includes(kw))
})

const viewDetail = (row) => {
  detailStudent.value = row
  const courses = []
  const seen = new Set()
  allEnrollments.value.forEach(e => {
    if (e.student_id === row.studentId) {
      const key = e.course_name || e.course_id
      if (!seen.has(key)) {
        seen.add(key)
        // 未录入显示「待录入」而不是 0.0 分（ScoreCaliber 第 3 条口径）
        const pending = e.score_total === null || e.score_total === undefined
        courses.push({
          courseName: e.course_name || '未知课程',
          scoreText: pending ? '待录入' : Number(e.score_total).toFixed(1) + '分',
          // `failed` 由后端数据算好再交给模板，模板里不再做字符串比数字。
          failed: !pending && Number(e.score_total) < 60
        })
      }
    }
  })
  detailCourses.value = courses
  detailVisible.value = true
}

/**
 * 「提醒」—— 2026-10-02（W4.5）：原来只弹一句 toast（"已向 X 发送学分预警提醒"），
 * 没有任何请求发出，学生那边收不到任何东西。这是本工程记录过的"
 * 点了就说成功"那一族。
 *
 * 现在打真的接口 `POST /counselors/students/notify`（辅导员通知服务，
 * 一事务写 `communication_logs` + `notifications`，学生通知中心真能收到）。
 * 传 `student_ids` 用 `student_profile.id`（本页聚合出来的就是这一套，
 * 与服务端的 id 解析顺序一致），并带上 `counselorId` 让"不在该辅导员名下"能被拦掉。
 *
 * 回包里 `delivered` 才是真正投递到通知中心的人数（学生可能关了该类通知，
 * 那时记为 muted）。所以按 delivered 判成功，不按 HTTP 200 判。
 */
const notifyStudent = async (student) => {
  const counselorId = getUserId()
  if (!counselorId) { ElMessage.error('未登录，无法发送'); return }
  try {
    const res = await counselorAPI.notifyStudents({
      counselorId,
      student_ids: [student.studentId],
      type: 'warning',
      content: `学分提醒：你目前有 ${student.failedCourses} 门课程未通过，` +
               `共 ${student.lostCredits.toFixed(1)} 学分未取得，请及时联系辅导员与任课教师。`
    })
    const delivered = res?.delivered ?? 0
    if (delivered > 0) {
      ElMessage.success('已向 ' + student.studentName + ' 发送提醒')
    } else if (Array.isArray(res?.muted) && res.muted.length) {
      // 写进了发送记录，但学生关掉了这类通知 —— 如实说，别报"发送成功"。
      ElMessage.warning(student.studentName + ' 已关闭此类通知，提醒未送达其通知中心')
    } else if (Array.isArray(res?.undeliverable) && res.undeliverable.length) {
      ElMessage.error(student.studentName + ' 的账号未绑定用户，无法接收通知')
    } else {
      ElMessage.error('提醒未送达：' + (res?.message || '服务端未投递任何通知'))
    }
  } catch (e) {
    console.error('发送学分提醒失败', e)
    ElMessage.error('发送失败：' + (e?.message || '网络错误'))
  }
}
</script>

<style scoped>
.cc-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }

.cc-hero { margin-bottom: 18px; }
.cch-left h1 { margin: 0; font-size: 22px; color: #1e293b; }
.cch-left p { margin: 4px 0 0; font-size: 13px; color: #94a3b8; }

/* 统计卡片 */
.cc-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 18px; }
.ccs-card { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.ccs-card.blue { border-left: 4px solid #3b82f6; }
.ccs-card.green { border-left: 4px solid #16a34a; }
.ccs-card.amber { border-left: 4px solid #3b82f6; }
.ccs-card.purple { border-left: 4px solid #8b5cf6; }
.ccs-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ccs-card.blue .ccs-icon { background: #eff6ff; color: #3b82f6; }
.ccs-card.green .ccs-icon { background: #ecfdf5; color: #16a34a; }
.ccs-card.amber .ccs-icon { background: #eff6ff; color: #3b82f6; }
.ccs-card.purple .ccs-icon { background: #f5f3ff; color: #8b5cf6; }
.ccs-num { font-size: 24px; font-weight: 700; color: #18181b; }
.ccs-num.amber { color: #1d4ed8; }
.ccs-lbl { font-size: 12px; color: #94a3b8; }

/* 工具栏 */
.cc-toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }

/* 卡片 */
.cc-card { background: #fff; border-radius: 12px; padding: 6px 0 0; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; }
.ccc-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #f1f5f9; }
.ccc-head.warn { background: #eff6ff; }
.ccc-head span:first-child { font-size: 15px; font-weight: 600; color: #1e293b; }
.ccc-head.warn span:first-child { color: #92400e; }
.ccc-badge { font-size: 12px; color: #64748b; background: #f1f5f9; padding: 2px 10px; border-radius: 10px; }
.ccc-badge.danger { background: #fef2f2; color: #ef4444; }
.cc-empty { text-align: center; padding: 60px 20px; color: #94a3b8; font-size: 14px; }

/* 表格 */
:deep(.cc-row) { transition: background .2s; }
:deep(.cc-row:hover) { background: #f8fafc !important; }
.credits-low { color: #ef4444; font-weight: 600; }
.credits-ok { color: #16a34a; font-weight: 600; }
.recent-txt { color: #64748b; font-size: 13px; }
.recent-none { color: #cbd5e1; }

/* 预警网格 */
.cc-warn-grid { display: flex; flex-direction: column; gap: 8px; padding: 14px 20px; }
.ccw-item { display: flex; align-items: center; gap: 12px; background: #fefce8; border-radius: 10px; padding: 14px 16px; border: 1px solid #fef08a; }
.ccw-avatar { width: 36px; height: 36px; border-radius: 8px; background: #3b82f6; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; flex-shrink: 0; }
.ccw-info { flex: 1; min-width: 0; }
.ccw-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.ccw-meta { font-size: 12px; color: #78716c; margin: 2px 0 6px; }
.ccw-bar { height: 4px; border-radius: 4px; background: #fef08a; overflow: hidden; }
.ccw-fill { height: 100%; border-radius: 4px; background: #eab308; transition: width .4s; }

/* 详情弹窗 */
.cc-detail { line-height: 1.6; }
.ccd-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }
.ccds-item { background: #f8fafc; border-radius: 8px; padding: 12px 14px; }
.ccds-item label { display: block; font-size: 12px; color: #94a3b8; margin-bottom: 4px; }
.ccds-item b { font-size: 18px; color: #1e293b; }
.ccd-courses { margin-top: 8px; }
.ccdc-title { font-size: 14px; font-weight: 600; color: #334155; margin-bottom: 10px; }
.ccdc-item { display: flex; justify-content: space-between; padding: 8px 12px; background: #f8fafc; border-radius: 6px; margin-bottom: 6px; }
.ccdci-name { font-size: 13px; color: #475569; }
.ccdci-score { font-weight: 600; font-size: 13px; }
.ccdci-score.pass { color: #16a34a; }
.ccdci-score.fail { color: #ef4444; }
.ccd-empty { text-align: center; padding: 30px; color: #94a3b8; }

@media (max-width: 768px) {
  .cc-stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
