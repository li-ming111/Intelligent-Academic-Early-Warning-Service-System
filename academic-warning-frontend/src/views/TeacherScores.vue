<template>
  <div class="teacher-scores">
    <div class="page-header">
      <h1>成绩管理</h1>
      <p>查看和管理班级学生成绩</p>
    </div>

    <div class="action-bar">
      <el-select v-model="selectedClass" placeholder="选择班级" @change="loadClassScores" filterable clearable style="width: 200px;">
        <el-option v-for="cls in classes" :key="cls.id" :label="cls.name" :value="cls.name"></el-option>
      </el-select>
      <el-input v-model="searchName" placeholder="搜索学生姓名" clearable :prefix-icon="Search" @input="currentPage=1" style="width: 200px;" />
      <el-select v-model="selectedSemester" placeholder="全部学期" clearable @change="currentPage=1" style="width:170px">
        <el-option v-for="s in availableSemesters" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <!-- 课程选择（W19 加）：既是列表筛选，也是「导入Excel」的目标课程 ——
           成绩必须落到某一门课上，此前页面没有这个上下文，导入的 courseId
           只能靠 scoresList[0] 猜，班级还没成绩时就是空。 -->
      <el-select v-model="selectedCourse" placeholder="选择课程" clearable filterable @change="currentPage=1" style="width: 200px;">
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :on-change="handleFileSelect"
        accept=".xlsx,.xls"
        :show-file-list="false"
      >
        <el-button type="primary">导入Excel</el-button>
      </el-upload>
      <el-button @click="downloadScoresTemplate">下载模板</el-button>
      <el-button @click="exportScores">导出成绩</el-button>
      <el-button @click="analyzeScores" type="info">成绩分析</el-button>
      <el-button @click="openNotifyCounselor" type="success" :disabled="!selectedClass">通知辅导员</el-button>
      <el-button @click="batchOperation" type="warning" :disabled="selectedRows.length === 0">批量操作</el-button>
    </div>

    <!-- 成绩分析卡片 -->
    <div v-if="selectedClass && analysisData" class="content-card">
      <div class="card-title-bar">
        <h2>课程成绩分析</h2>
      </div>
      <div class="card-body-section">
        <div class="analysis-grid">
          <div class="analysis-item">
            <div class="analysis-label">平均分</div>
            <div class="analysis-value">{{ analysisData.averageScore.toFixed(2) }}</div>
          </div>
          <div class="analysis-item">
            <div class="analysis-label">最高分</div>
            <div class="analysis-value">{{ analysisData.maxScore }}</div>
          </div>
          <div class="analysis-item">
            <div class="analysis-label">最低分</div>
            <div class="analysis-value">{{ analysisData.minScore }}</div>
          </div>
          <div class="analysis-item">
            <div class="analysis-label">标准差</div>
            <div class="analysis-value">{{ analysisData.standardDeviation.toFixed(2) }}</div>
          </div>
          <div class="analysis-item">
            <div class="analysis-label">及格率</div>
            <div class="analysis-value">{{ (analysisData.passRate * 100).toFixed(1) }}%</div>
          </div>
          <div class="analysis-item">
            <div class="analysis-label">优秀率</div>
            <div class="analysis-value">{{ (analysisData.excellentRate * 100).toFixed(1) }}%</div>
          </div>
        </div>
        <div class="distribution-section">
          <h3>成绩分布</h3>
          <div class="distribution-chart">
            <div v-for="(count, range) in analysisData.scoreDistribution" :key="range" class="distribution-bar">
              <div class="bar-label">{{ range }}</div>
              <div class="bar-container">
                <div class="bar-fill" :style="{ width: (count / analysisData.totalStudents) * 100 + '%' }"></div>
              </div>
              <div class="bar-count">{{ count }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- AI 分析（W26）：下面这几个 aiAPI 端点此前全仓零调用。
         `analyzeClassScores` 是**纯 AI** 端点（后端直接问DeepSeek），没配 AI_API_KEY 时
         后端会拿占位串冒充成功 —— 闸在 utils/ai.js，命中就换成一句实话，不当作分析结果。 -->
    <div v-if="selectedClass" class="content-card">
      <div class="card-title-bar">
        <h2>AI 辅助分析</h2>
      </div>
      <div class="card-body-section ai-row">
        <AiInsight
          title="AI 班级学业分析"
          :load="aiAnalyzeClass"
          hint="把当前班级交给 AI 做整体分析（需要后端配置 AI_API_KEY）"
        />
        <AiInsight
          title="AI 学习建议"
          :load="aiSuggestClass"
          hint="按当前班级里不及格/薄弱课程的统计生成建议（走规则引擎，不依赖 API Key）"
        />
      </div>
    </div>

    <!-- 成绩表格 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ selectedClass ? '班级成绩列表' : '请选择班级' }}</span>
          <span class="score-count" v-if="selectedClass">共 {{ filteredScores.length }} 条记录</span>
        </div>
      </template>
      <el-table :data="pagedScores" stripe v-if="selectedClass" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column label="学号" width="150">
          <template #default="{ row }">{{ row.studentNo || row.studentId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column label="学期" width="130">
          <template #default="{ row }">{{ row.semesterLabel || row.semester || '--' }}</template>
        </el-table-column>
        <el-table-column label="课程" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.courseName || row.courseId || '--' }}</template>
        </el-table-column>
        <el-table-column label="最终成绩" width="120" sortable>
          <template #header>
            <span>最终成绩</span>
          </template>
          <template #default="{ row }">
            <!-- W36：本列的数来自权威投影（每门课取"期末已录入的最新一次"），期末未录入的行
                 在这条口径下根本不会出现 —— 显示"待录入"是多余且做不到的。这里保留空值兜底，
                 只是防数据源换回不过滤的版本时不显示成 `undefined`。 -->
            <span class="score-value">{{ row.totalScore ?? '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="editScore(row)">修改</el-button>
              <el-button type="danger" size="small" @click="deleteScore(row)">删除</el-button>
              <el-button type="info" size="small" @click="viewStudentAnalysis(row)">分析</el-button>
              <el-button type="success" size="small" @click="notifyCounselor(row)">通知辅导员</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="请先选择班级"></el-empty>

      <div class="table-footer" v-if="selectedClass">
        <div class="selection-info">
          <span v-if="selectedRows.length > 0">已选择 {{ selectedRows.length }} 项</span>
          <el-button type="warning" size="small" @click="batchOperation" :disabled="selectedRows.length === 0">批量操作</el-button>
        </div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredScores.length"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 修改对话框 -->
    <el-dialog v-model="editDialogVisible" title="修改成绩" width="500px">
      <el-form :model="editingScore" label-width="100px">
        <el-form-item label="学生">
          <el-input :value="editingScore.studentName" disabled></el-input>
        </el-form-item>
        <el-form-item label="最终成绩">
          <el-input-number v-model="editingScore.totalScore" :min="0" :max="100"></el-input-number>
        </el-form-item>
        <el-form-item label="修改原因">
          <el-input v-model="editingScore.reason" type="textarea" rows="3" placeholder="请说明修改原因"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">确认</el-button>
      </template>
    </el-dialog>

    <!-- 学生分析对话框 -->
    <el-dialog v-model="studentAnalysisVisible" title="学生成绩分析" width="600px">
      <div v-if="studentAnalysisData" class="student-analysis">
        <div class="analysis-header">
          <h3>{{ studentAnalysisData.studentName }} ({{ studentAnalysisData.studentId }})</h3>
        </div>
        <div class="analysis-content">
          <div class="analysis-row">
            <div class="analysis-item">
              <div class="analysis-label">当前课程成绩</div>
              <div class="analysis-value">{{ studentAnalysisData.currentScore }}</div>
            </div>
            <div class="analysis-item">
              <div class="analysis-label">平均成绩</div>
              <div class="analysis-value">{{ studentAnalysisData.averageScore.toFixed(2) }}</div>
            </div>
            <div class="analysis-item">
              <div class="analysis-label">成绩等级</div>
              <div class="analysis-value">{{ scoreLevelText(studentAnalysisData.level) }}</div>
            </div>
          </div>
          <div class="analysis-row">
            <div class="analysis-item">
              <div class="analysis-label">本课程名次</div>
              <div class="analysis-value">{{ studentAnalysisData.rank }} / {{ studentAnalysisData.totalStudents }}</div>
            </div>
            <div class="analysis-item">
              <div class="analysis-label">挂科次数</div>
              <div class="analysis-value">{{ studentAnalysisData.failedCount }}</div>
            </div>
            <div class="analysis-item">
              <div class="analysis-label">预警状态</div>
              <div class="analysis-value" :class="studentAnalysisData.warningLevel">{{ studentAnalysisData.warningLevel }}</div>
            </div>
          </div>
          <div class="comment-section">
            <h4>教师评语</h4>
            <div class="comment-content">{{ studentAnalysisData.comment }}</div>
          </div>

          <!-- AI 风险预测 / 建议 / 干预计划（W26）。
               数据全部来自本页**已加载**的班级成绩（`scoresList`），不额外请求、不编造：
               挂科数、总课程数、薄弱课程都由 `totalScore < 60` 直接数出来。
               ⚠️ 这里**不传 gpa** —— 本页只有单科分数、没有学分绩点，硬凑一个会算错风险。 -->
          <div v-if="studentAnalysisRow" class="ai-row">
            <AiInsight title="AI 风险预测" :load="aiPredictRisk" hint="按本班已加载成绩统计风险（规则引擎）" />
            <AiInsight title="AI 学习建议" :load="aiStudentSuggestion" hint="按薄弱课程生成建议（规则引擎）" />
            <AiInsight title="AI 干预计划" :load="aiStudentPlan" hint="先风险预测，再按它给出的等级生成计划（规则引擎）" />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="studentAnalysisVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 通知辅导员对话框 -->
    <el-dialog v-model="notifyDialogVisible" title="通知辅导员" width="500px">
      <div class="notify-counselor" v-if="notifyTarget">
        <el-alert 
          :title="`将通知 ${notifyTarget.counselorName || '班级辅导员'}`" 
          :description="notifyTarget.counselorPhone ? `联系电话：${notifyTarget.counselorPhone}` : ''"
          type="info" 
          show-icon 
          :closable="false"
          style="margin-bottom: 16px;"
        />
        <el-form label-width="80px">
          <el-form-item label="涉及学生">
            <el-tag v-if="notifyTarget.studentName">{{ notifyTarget.studentName }} ({{ notifyTarget.studentNo }})</el-tag>
            <el-tag v-else type="warning">全班学生</el-tag>
          </el-form-item>
          <el-form-item label="沟通内容">
            <el-input v-model="notifyContent" type="textarea" rows="4" placeholder="请描述需要辅导员关注的学生情况..." />
          </el-form-item>
        </el-form>
      </div>
      <div v-else class="notify-counselor">
        <el-alert title="当前班级未配置辅导员" type="warning" show-icon :closable="false" />
      </div>
      <template #footer>
        <el-button @click="notifyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNotify" :disabled="!notifyTarget || !notifyContent.trim()">发送通知</el-button>
      </template>
    </el-dialog>

    <!-- 批量操作对话框 -->
    <el-dialog v-model="batchDialogVisible" title="批量操作" width="500px">
      <div class="batch-operation">
        <p>已选择 <strong>{{ selectedRows.length }}</strong> 条成绩记录，请选择操作：</p>
        <div class="batch-actions">
          <el-button type="danger" @click="batchDeleteScores" size="large">
            批量删除
          </el-button>
          <el-button type="primary" @click="batchExportScores" size="large">
            批量导出
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { teacherAPI, aiAPI } from '@/api/index'
import AiInsight from '@/components/AiInsight.vue'
import { getUserId, getTeacherId } from '@/utils/userUtils'
import { saveBlobAsFile, describeDownloadError } from '@/utils/download'

const route = useRoute()
const selectedClass = ref('')
const searchName = ref('')
const selectedSemester = ref('')
const selectedCourse = ref('')
const editDialogVisible = ref(false)
const editingScore = ref({})
const classes = ref([])
const courseMap = ref(new Map())

// 本人所授课程（loadCourseMap 已经把它们放进 courseMap，这里只是转成下拉可用的数组，
// 不额外发请求）。
const courseOptions = computed(() =>
  Array.from(courseMap.value.entries()).map(([id, name]) => ({ id, name }))
)

const scoresList = ref([])
const uploadRef = ref(null)

const analysisData = ref(null)
const studentAnalysisVisible = ref(false)
const studentAnalysisData = ref(null)
/** 「分析」点的那条成绩行 —— AI 面板据此在学生已加载成绩里聚合，见 aiStudentBody。 */
const studentAnalysisRow = ref(null)
const batchDialogVisible = ref(false)
const notifyDialogVisible = ref(false)
const notifyTarget = ref(null)
const notifyContent = ref('')
const selectedRows = ref([])

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const filteredScores = computed(() => {
  let list = scoresList.value
  const kw = searchName.value.toLowerCase()
  if (kw) list = list.filter(s => ('' + (s.studentName || '')).toLowerCase().includes(kw) || ('' + (s.studentNo || '')).includes(String(kw)))
  if (selectedSemester.value) list = list.filter(s => String(s.semester) === String(selectedSemester.value))
  if (selectedCourse.value) list = list.filter(s => String(s.courseId) === String(selectedCourse.value))
  return list
})
const pagedScores = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredScores.value.slice(start, start + pageSize.value)
})

// 从学号提取入学年份（如 "2023020616" → 2023）
const entranceYear = computed(() => {
  const first = scoresList.value.find(s => s.studentNo && /^\d{10}$/.test(String(s.studentNo)))
  return first ? parseInt(String(first.studentNo).substring(0, 4)) : 2023
})

// 根据入学年份和当前北京时间，计算当前是第几学期
// 学年划分：第一学期9月~次年2月，第二学期3月~8月
const currentSemester = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const academicStartYear = month >= 9 ? year : year - 1
  const term = month >= 3 && month <= 8 ? 2 : 1
  return (academicStartYear - entranceYear.value) * 2 + term
})

function semLabel(num) {
  const n = parseInt(num); if (isNaN(n) || n < 1) return '--'
  const y = entranceYear.value + Math.floor((n - 1) / 2)
  return `${y}-${y + 1}-${(n - 1) % 2 + 1}`
}

// 后端 analyzeStudentScore 的 level 是 1-5 的档位码（ScoreSyncSupport.scoreToLevel），直接显示会是个数字。
const scoreLevelText = (level) => ({ 1: '优秀', 2: '良好', 3: '中等', 4: '及格', 5: '不及格' }[level] || '--')
const availableSemesters = computed(() => {
  // 自动生成第1学期到当前学期的选项
  const list = []
  for (let i = 1; i <= currentSemester.value; i++) {
    list.push({ value: i, label: `第${i}学期  ${semLabel(i)}` })
  }
  return list.reverse()
})

const handleSizeChange = () => { currentPage.value = 1 }
const handleCurrentChange = () => {}

const downloadScoresTemplate = async () => {
  try {
    const blob = await teacherAPI.downloadScoresTemplate()
    await saveBlobAsFile(blob, '成绩导入模板.xlsx')
    ElMessage.success('模板已下载')
  } catch (error) {
    console.error('下载模板失败:', error)
    ElMessage.error(await describeDownloadError(error))
  }
}

onMounted(async () => {
  await loadCourseMap()
  await loadClasses()
})

const handleFileSelect = async (uploadFile) => {
  try {
    if (!selectedClass.value) {
      ElMessage.error('请先选择班级')
      uploadRef.value.clearFiles()
      return
    }
    if (!selectedCourse.value) {
      ElMessage.error('请先选择课程（这批成绩要导入到哪门课）')
      uploadRef.value.clearFiles()
      return
    }
    const file = uploadFile.raw
    const formData = new FormData()
    formData.append('file', file)
    // courseId 用页面上选的那门课。此前这里取 scoresList.value[0]?.courseId
    // （"当前列表第一行是哪门课就导哪门"），班级还没成绩时是空字符串，导入必然失败。
    formData.append('courseId', selectedCourse.value)
    formData.append('className', selectedClass.value)
    // 学期：选了学期筛选就按它导，没选则不发 —— 由后端按"该生已有的最大学期"兜底。
    // 注意**不能**发 "2025-2026-1" 这种学年式字符串：scores.semester 是 int 列。
    if (selectedSemester.value) formData.append('semester', selectedSemester.value)

    const res = await teacherAPI.importScores(formData)

    // 导入是逐行尽力而为的：成功的行已落库，失败的行在 errors 里。
    // 只要跳过了哪怕一行，就不能只报"导入成功"。
    const success = res?.success ?? 0
    const skipped = res?.skipped ?? 0
    const errorList = res?.errors || []
    if (skipped === 0) {
      ElMessage.success(`导入完成：成功 ${success} 条`)
    } else {
      const detail = errorList.slice(0, 5).map(e => `第${e.row}行：${e.reason}`).join('；')
      const more = errorList.length > 5 ? ` 等共 ${errorList.length} 处` : ''
      ElMessage.warning(`导入完成：成功 ${success} 条，跳过 ${skipped} 条。${detail}${more}`)
    }
    uploadRef.value.clearFiles()
    await loadClassScores(selectedClass.value)
  } catch (error) {
    console.error('导入成绩失败:', error)
    ElMessage.error(error.response?.data?.message || error.message || '导入成绩失败')
    uploadRef.value.clearFiles()
  }
}

const loadCourseMap = async () => {
  try {
    const teacherId = getTeacherId()
    const response = await teacherAPI.getCourses(teacherId)
    if (Array.isArray(response)) {
      response.forEach(c => {
        if (c.id) courseMap.value.set(String(c.id), c.name || ('课程' + c.id))
      })
    }
  } catch (error) {
    console.error('加载课程映射失败:', error)
  }
}

const loadClasses = async () => {
  try {
    const teacherId = getTeacherId()
    // 2026-10-03（W8.2）：原实现在 teacherId 缺失时**把 '9' 写进 localStorage.teacherId**
    // 再按它加载 —— 那不是兜底，是给这台浏览器**永久钉死**成 9 号用户：该键此后恒为 '9'，
    // 8 个教师端页面都优先读它（设置页还会据此去改 9 号用户的邮箱/电话），
    // 且两条登出路径都不清它。现在拿不到身份就空着，**不写任何键**。
    if (!teacherId) { console.warn('[TeacherScores] 未获取到登录用户 ID，跳过加载'); return }
    try {
      const response = await teacherAPI.getMyClasses(teacherId)
      const classList = Array.isArray(response) ? response : (response?.data || [])
      classes.value = classList.map(cls => ({
        id: cls.id || cls.classId,
        name: cls.name || cls.className
      }))
      if (classes.value.length > 0 && !selectedClass.value) {
        // 班级管理页「导入成绩」会带 ?classId= 过来，优先预选该班；否则落第一个班。
        const wantedId = route.query.classId
        const wanted = wantedId ? classes.value.find(c => String(c.id) === String(wantedId)) : null
        const target = wanted || classes.value[0]
        selectedClass.value = target.name
        await loadClassScores(target.name)
      }
    } catch (error) {
      console.error('加载班级失败:', error)
    }
  } catch (error) {
    console.error('加载班级失败:', error)
  }
}

const loadClassScores = async (className) => {
  try {
    if (!className) {
      scoresList.value = []
      selectedSemester.value = ''
      return
    }
    selectedSemester.value = ''
    try {
      const teacherId = getTeacherId()
      const response = await teacherAPI.getScoresByClass(className, teacherId)
      if (Array.isArray(response)) {
        scoresList.value = response.map(item => {
          // W36：数据源由 score_record 改为权威表 scores。
          // · `scoreTotal` 可能为 null（期末未录入）—— 不能用 `|| 0` 吞成 0 分，
          //   0 分会显示成"不及格"并进挂科名单，而事实是"还没录"。
          // · `studentId` 是 student_profile.id，"查看分析"要拿它打 analyzeStudentScore；
          //   旧 remap 漏了这一项 ⇒ 那条路径一直传 undefined。
          const rawTotal = item.scoreTotal ?? item.totalScore ?? item.score
          const totalScore = (rawTotal === null || rawTotal === undefined) ? null : Number(rawTotal)
          const displayStudentNo = item.studentNo || item.studentId || ''
          return {
            id: item.id,
            studentId: item.studentId,
            studentNo: displayStudentNo,
            studentName: item.studentName,
            courseId: item.courseId,
            courseName: item.courseName || courseMap.value.get(String(item.courseId)) || ('课程' + item.courseId),
            totalScore: totalScore,
            semester: item.semester,
            semesterLabel: semLabel(item.semester)
          }
        })
      }
    } catch (error) {
      console.error('加载成绩失败:', error)
    }
  } catch (error) {
    console.error('加载成绩失败:', error)
  }
}

const editScore = (row) => {
  editingScore.value = { ...row }
  editDialogVisible.value = true
}

const submitEdit = async () => {
  try {
    if (!editingScore.value.reason) {
      ElMessage.error('请填写修改原因')
      return
    }
    // W36：这个弹窗**此前一次都没成功过** —— 弹窗只绑了 totalScore（见模板里的
    // `v-model="editingScore.totalScore"`），提交却发 `{regularScore, finalScore, reason}`
    // （这两个字段从没被绑过，永远是 undefined），而后端读的是 `{score, remark, modifiedBy}`
    // ⇒ 键名对不上，后端每次都回「缺少 score 字段」。
    // 现在按后端契约发：`score` = 弹窗里真正改的那个数；`remark` = 修改原因
    // （后端落到 scores.remark 与审计的 reason）。
    const score = editingScore.value.totalScore
    if (score === null || score === undefined || score === '') {
      ElMessage.error('请填写成绩')
      return
    }
    const userId = getUserId()
    const data = {
      score: Number(score),
      remark: editingScore.value.reason,
      modifiedBy: userId
    }
    await teacherAPI.updateScore(editingScore.value.id, data)
    ElMessage.success('成绩已修改，修改记录已保存')
    editDialogVisible.value = false
    await loadClassScores(selectedClass.value)
  } catch (error) {
    console.error('修改成绩失败:', error)
    ElMessage.error('修改成绩失败')
  }
}

/**
 * 导出当前班级的成绩单。
 *
 * <p>改之前这里连着调了两个端点（`exportScores` + `downloadScores`），两个在后端
 * 都只回 JSON 列表，而两个请求都带 `responseType:'blob'` ⇒ 存下来的
 * `scores_xxx.xlsx` 其实是一段 JSON 文本，而且中间还先弹了一句
 * 「成绩单已导出为Excel」。现在只调一个端点（后端真的吐 xlsx），并且**先确认
 * 拿到的是文件**再报成功。
 */
const exportScores = async () => {
  try {
    if (!selectedClass.value) {
      ElMessage.error('请先选择班级')
      return
    }
    // 选了课程就导出那门课；没选才退回"列表第一行那门课"（旧行为）。
    const defaultCourseId = selectedCourse.value || scoresList.value[0]?.courseId
    if (!defaultCourseId) {
      ElMessage.error('当前班级暂无成绩数据，无法导出')
      return
    }
    const blob = await teacherAPI.exportScores(defaultCourseId)
    await saveBlobAsFile(blob, `成绩单_${selectedClass.value}_${Date.now()}.xlsx`)
    ElMessage.success('成绩单已导出')
  } catch (error) {
    console.error('导出成绩失败:', error)
    ElMessage.error(await describeDownloadError(error))
  }
}

const analyzeScores = async () => {
  try {
    if (!selectedClass.value) {
      ElMessage.error('请先选择班级')
      return
    }
    const defaultCourseId = selectedCourse.value || scoresList.value[0]?.courseId
    if (!defaultCourseId) {
      ElMessage.error('当前班级暂无成绩数据')
      return
    }
    const response = await teacherAPI.analyzeScores(defaultCourseId)
    analysisData.value = response
    ElMessage.success('成绩分析完成')
  } catch (error) {
    console.error('成绩分析失败:', error)
    ElMessage.error('成绩分析失败')
  }
}

const viewStudentAnalysis = async (row) => {
  try {
    const response = await teacherAPI.analyzeStudentScore(row.studentId, row.courseId)
    studentAnalysisData.value = response
    // AI 面板要用这个学生在本班已加载的成绩来算，所以留一份原始行
    studentAnalysisRow.value = row
    studentAnalysisVisible.value = true
  } catch (error) {
    console.error('学生分析失败:', error)
    ElMessage.error('学生分析失败')
  }
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

// ===== AI 接线（W26）=====

/** 本班已加载的成绩里，属于某个学生的那些行（按学号优先、姓名兜底）。 */
const rowsOfStudent = (row) => {
  if (!row) return []
  const sameNo = scoresList.value.filter(s => s.studentNo && row.studentNo && String(s.studentNo) === String(row.studentNo))
  if (sameNo.length) return sameNo
  return scoresList.value.filter(s => row.studentName && s.studentName === row.studentName)
}

/**
 * 由**本页已有数据**拼 `predictRisk` / `generateSuggestion` 的 body。
 *
 * 全部字段都是数出来的，没有估的：
 *   - `failCount` / `weakSubjects` 由 `totalScore < 60` 直接筛；
 *   - `totalCourses` = 该生在本班的成绩行数。
 * ⚠️ **不传 `gpa`** —— 本页只有总分、没有学分绩点，凑一个 0-100 的平均分当 GPA
 *   会让后端的 `gpa < 2.0` 那几条规则全部误判。宁可少一条规则，不编一个数字。
 */
const aiStudentBody = () => {
  const row = studentAnalysisRow.value
  if (!row) throw new Error('请先点某条成绩的「分析」')
  const rows = rowsOfStudent(row)
  if (!rows.length) throw new Error('本页没有这个学生的已加载成绩，无法统计')
  const weak = rows
    .filter(s => Number(s.totalScore) < 60)
    .map(s => ({ courseName: s.courseName, score: Number(s.totalScore) }))
  return {
    studentId: row.studentId ?? row.studentNo ?? '',
    studentName: row.studentName || '该学生',
    totalCourses: rows.length,
    failCount: weak.length,
    weakSubjects: weak
  }
}

/** classId：下拉里绑的是**班级名**，这里换回 id（换不到就如实报错，不猜）。 */
const currentClassId = () => {
  const cls = classes.value.find(c => c.name === selectedClass.value)
  if (!cls || cls.id == null) throw new Error('取不到当前班级的 id，无法做班级分析')
  return cls.id
}

const aiAnalyzeClass = () => aiAPI.analyzeClassScores(currentClassId())

/** 班级级学习建议：把全班每个学生的成绩行都当成"薄弱课程"来源会太吵，
 *  这里只把**不及格**的课汇总给后端（同一门课多人挂就合并成一条，分数取最低）。 */
const aiSuggestClass = () => {
  const worst = new Map()
  for (const s of scoresList.value) {
    const score = Number(s.totalScore)
    if (!(score < 60)) continue
    const key = s.courseName || ('课程' + s.courseId)
    const prev = worst.get(key)
    if (!prev || score < prev.score) worst.set(key, { courseName: key, score })
  }
  const weakSubjects = [...worst.values()]
  if (!weakSubjects.length) throw new Error('本班当前没有不及格的课程，生成不了针对性建议')
  return aiAPI.generateSuggestion({
    studentId: '',
    studentName: selectedClass.value + '（全班）',
    totalCourses: scoresList.value.length,
    failCount: scoresList.value.filter(s => Number(s.totalScore) < 60).length,
    weakSubjects
  })
}

const aiPredictRisk = () => aiAPI.predictRisk(aiStudentBody())
const aiStudentSuggestion = () => aiAPI.generateSuggestion(aiStudentBody())

/** 干预计划：**先问后端的规则引擎要 riskLevel**，再把后端自己给的那个等级
 *  喂给 `generatePlan`。不在前端复刻那套阈值 —— 后端 `generateWarning` 只认
 *  「高风险/中风险/低风险/正常」四个词，自己拼一个词就是又一处口径漂移。 */
const aiStudentPlan = async () => {
  const body = aiStudentBody()
  const risk = await aiAPI.predictRisk(body)
  const level = risk?.riskLevel
  if (!level) throw new Error('风险预测没有返回 riskLevel，无法据此生成计划')
  return aiAPI.generatePlan({
    studentId: body.studentId,
    studentName: body.studentName,
    riskLevel: level,
    riskFactors: risk.riskFactors || ['挂科 ' + body.failCount + ' 门']
  })
}

// 打开批量操作对话框
const batchOperation = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.error('请先选择要操作的成绩记录')
    return
  }
  batchDialogVisible.value = true
}

// 批量删除
const batchDeleteScores = async () => {
  try {
    if (confirm(`确定要删除选中的 ${selectedRows.value.length} 条成绩记录吗？`)) {
      const scoreIds = selectedRows.value.map(row => row.id)
      await teacherAPI.batchDeleteScores(scoreIds)
      ElMessage.success(`成功删除 ${selectedRows.value.length} 条成绩记录`)
      batchDialogVisible.value = false
      selectedRows.value = []
      await loadClassScores(selectedClass.value)
    }
  } catch (error) {
    console.error('批量删除成绩失败:', error)
    ElMessage.error('批量删除成绩失败')
  }
}

// 批量导出
const batchExportScores = async () => {
  try {
    const rows = selectedRows.value
    const header = ['学号', '姓名', '课程', '最终成绩']
    const data = rows.map(r => [r.studentNo, r.studentName, r.courseName, r.totalScore])
    const csvContent = '\uFEFF' + [header, ...data].map(row => row.join(',')).join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `成绩_${selectedClass.value}_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success(`成功导出 ${rows.length} 条成绩记录`)
    batchDialogVisible.value = false
  } catch (error) {
    console.error('批量导出失败:', error)
    ElMessage.error('批量导出失败')
  }
}

// 通知辅导员（工具栏按钮 - 全班）
const openNotifyCounselor = async () => {
  if (!selectedClass.value) return
  try {
    const response = await teacherAPI.getCounselorByClass(selectedClass.value)
    const counselor = response?.data || response
    if (counselor && counselor.counselorId) {
      notifyTarget.value = { ...counselor }
    } else {
      notifyTarget.value = null
    }
  } catch (error) {
    notifyTarget.value = null
  }
  notifyContent.value = ''
  notifyDialogVisible.value = true
}

// 通知辅导员（行按钮 - 单个学生）
const notifyCounselor = async (row) => {
  if (!selectedClass.value) return
  try {
    const response = await teacherAPI.getCounselorByClass(selectedClass.value)
    const counselor = response?.data || response
    if (counselor && counselor.counselorId) {
      notifyTarget.value = { ...counselor, studentName: row.studentName, studentNo: row.studentNo, studentId: row.studentId }
    } else {
      notifyTarget.value = null
    }
  } catch (error) {
    notifyTarget.value = null
  }
  notifyContent.value = row.totalScore < 60 
    ? `${row.studentName}同学${row.courseName}课程成绩为${row.totalScore}分，未达到及格线，请辅导员关注该生学习情况。`
    : ''
  notifyDialogVisible.value = true
}

// 提交通知
const submitNotify = async () => {
  if (!notifyContent.value.trim()) {
    ElMessage.error('请输入沟通内容')
    return
  }
  try {
    const teacherId = getUserId()
    await teacherAPI.saveCommunication({
      teacherId: teacherId,
      // 单个学生 → 该生的 student_profile.id；全班通知没有具体学生，保持 0
      // （旧代码两分支都写死 0，单个学生的通知在库里查不到归属）
      studentId: notifyTarget.value.studentId ?? 0,
      content: notifyContent.value,
      type: 'notify_counselor',
      studentName: notifyTarget.value.studentName || '全班'
    })
    ElMessage.success('已通知辅导员')
    notifyDialogVisible.value = false
  } catch (error) {
    console.error('通知辅导员失败:', error)
    ElMessage.error('通知辅导员失败')
  }
}

const deleteScore = async (row) => {
  try {
    if (!row.id) {
      ElMessage.error('成绩ID不存在')
      return
    }
    if (confirm('确定要删除这条成绩记录吗？')) {
      await teacherAPI.deleteScore(row.id)
      ElMessage.success('成绩删除成功')
      await loadClassScores(selectedClass.value)
    }
  } catch (error) {
    console.error('删除成绩失败:', error)
    ElMessage.error('删除成绩失败')
  }
}
</script>

<style scoped>
.teacher-scores {
  padding: 20px;
  background-color: #f8f9fa !important;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #333;
}

.page-header p {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.action-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  align-items: center;
  flex-wrap: wrap;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-count {
  font-size: 14px;
  color: #909399;
  font-weight: normal;
}

.table-footer {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selection-info {
  color: #606266;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.score-value {
  color: #667eea;
  font-weight: 600;
  padding: 2px 8px;
  background: #f0f2f8;
  border-radius: 4px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.action-buttons .el-button {
  margin: 0;
}

/* 内容卡片 */
.content-card {
  background: white;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  margin-bottom: 20px;
}

.card-title-bar {
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafbfc;
}

.card-title-bar h2 {
  margin: 0;
  font-size: 16px;
  color: #333;
  font-weight: 600;
}

.card-body-section {
  padding: 20px;
}

.teacher-scores :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.teacher-scores :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.teacher-scores :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.teacher-scores :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.teacher-scores :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}

/* 成绩分析 */
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.analysis-item {
  background: #f8f9fa;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.analysis-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.analysis-value {
  font-size: 20px;
  font-weight: 600;
  color: #667eea;
}

.distribution-section {
  margin-top: 30px;
}

.distribution-section h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 15px;
  color: #333;
}

.distribution-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.distribution-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bar-label {
  width: 60px;
  font-size: 14px;
  color: #666;
}

.bar-container {
  flex: 1;
  height: 20px;
  background: #f0f2f5;
  border-radius: 10px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 10px;
  transition: width 0.3s ease;
}

.bar-count {
  width: 40px;
  text-align: right;
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

/* 学生分析 */
.student-analysis {
  padding: 20px 0;
}

.analysis-header h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.analysis-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.analysis-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 15px;
}

.comment-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e8ecf1;
}

.comment-section h4 {
  margin: 0 0 10px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.comment-content {
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: #666;
}

/* 批量操作对话框 */
.batch-operation {
  padding: 10px 0;
}

.batch-operation p {
  margin: 0 0 20px 0;
  font-size: 14px;
  color: #606266;
}

.batch-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

/* 预警状态 */
.warning-level {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.低级预警 {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #fde2b3;
}

.中级预警 {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fbc4c4;
}

.高级预警 {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fbc4c4;
  font-weight: 600;
}

.无预警 {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #c2e7b0;
}

/* 响应式 */
@media (max-width: 1024px) {
  .action-bar {
    flex-wrap: wrap;
  }
  .analysis-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .analysis-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
  .analysis-row {
    grid-template-columns: 1fr;
  }
  .distribution-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }
  .bar-label {
    width: 100%;
  }
  .bar-count {
    width: 100%;
    text-align: left;
  }
}

.ai-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 900px) { .ai-row { grid-template-columns: 1fr; } }
</style>
