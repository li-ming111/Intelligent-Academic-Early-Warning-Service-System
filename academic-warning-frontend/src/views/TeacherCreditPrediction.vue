
<template>
  <div class="tcp-page">
    <div class="tcp-hero">
      <div class="tcph-left">
        <h1>学分预测</h1>
        <p>毕业学分要求追踪、学生学分达标趋势与选修类别覆盖分析</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="tcp-stats">
      <div class="tcps-card total">
        <div class="tcps-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
        <div class="tcps-body"><div class="tcps-num">{{ stats.total }}</div><div class="tcps-lbl">学生总数</div></div>
      </div>
      <div class="tcps-card danger">
        <div class="tcps-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
        <div class="tcps-body"><div class="tcps-num">{{ stats.highRisk }}</div><div class="tcps-lbl">选修高风险</div></div>
      </div>
      <div class="tcps-card warning">
        <div class="tcps-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg></div>
        <div class="tcps-body"><div class="tcps-num">{{ stats.electiveOk }}</div><div class="tcps-lbl">选修已达标</div></div>
      </div>
      <div class="tcps-card success">
        <div class="tcps-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/></svg></div>
        <div class="tcps-body"><div class="tcps-num">{{ electiveAvg }}</div><div class="tcps-lbl">人均选修学分</div></div>
      </div>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="tcp-tabs">
      <!-- Tab 1: 毕业学分预测 -->
      <el-tab-pane label="毕业学分预测" name="prediction">
        <!-- 毕业学分要求表格 -->
        <div class="tcp-req-card">
          <div class="tcprq-title">毕业所需学分要求</div>
          <div class="tcprq-desc">学生须同时满足以下条件方可毕业</div>
          <el-table :data="reqTableData" stripe border class="tcp-req-table" size="small">
            <el-table-column prop="item" label="要求项" width="140" />
            <el-table-column prop="requirement" label="具体要求" width="200" />
            <el-table-column label="要求类别" width="110">
              <template #default="{ row }">
                <el-tag :type="row.tagType" size="small" effect="plain">{{ row.category }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="说明" min-width="180" />
          </el-table>
        </div>

        <!-- 学生学分预测表格 -->
        <div class="tcpm-card" style="margin-top:16px">
          <div class="tcpm-head"><span>学生学分预测详情</span><span class="tcpm-badge">{{ predictions.length }}人</span></div>
          <el-table :data="pagedPredictions" stripe v-if="predictions.length" class="tcp-table">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="studentName" label="姓名" width="90" />
            <el-table-column label="毕业学分" width="150">
              <template #default="{ row }">
                <div class="tcp-credit-cell">
                  <span :class="row.totalCredits >= 120 ? 'cr-ok' : row.totalCredits >= 100 ? 'cr-warn' : 'cr-low'">{{ row.totalCredits }}</span>
                  <span class="cr-div">/</span>
                  <span class="cr-req">120</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="选修学分" width="140">
              <template #default="{ row }">
                <div class="tcp-credit-cell">
                  <span :class="row.electiveCredits >= 8 ? 'cr-ok' : 'cr-low'">{{ row.electiveCredits }}</span>
                  <span class="cr-div">/</span>
                  <span class="cr-req">8</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="毕业达标" min-width="140">
              <template #default="{ row }">
                <div class="tcp-bar-cell">
                  <el-progress :percentage="row.totalPercent" :color="row.totalPercent >= 100 ? '#16a34a' : row.totalPercent >= 70 ? '#f59e0b' : '#ef4444'" :stroke-width="8" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="选修达标" min-width="120">
              <template #default="{ row }">
                <div class="tcp-bar-cell">
                  <el-progress :percentage="row.electivePercent" :color="row.electivePercent >= 100 ? '#16a34a' : row.electivePercent >= 50 ? '#f59e0b' : '#ef4444'" :stroke-width="8" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="风险" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.risk === 'high' ? 'danger' : row.risk === 'medium' ? 'warning' : 'success'" size="small">
                  {{ row.risk === 'high' ? '高' : row.risk === 'medium' ? '中' : '低' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" link @click="viewDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="tcp-pagination" v-if="predictions.length > pageSize">
            <el-pagination v-model:current-page="currentPage" :page-size="pageSize" layout="total, prev, pager, next" :total="predictions.length" background small />
          </div>
          <div v-else class="tcpm-empty">暂无学分数据，请确保学生有选课和成绩记录</div>
        </div>

        <!-- 图表 -->
        <div class="tcp-chart-row" style="margin-top:16px">
          <div class="tcpr-card" ref="chartRef" style="height:340px"></div>
          <div class="tcpr-card tcp-suggest-card">
            <div class="tcpr-title">干预建议</div>
            <div class="tcpr-suggest">
              <div class="tcs-item" v-for="(s, i) in suggestions" :key="i">
                <div class="tcs-dot" :class="s.level"></div>
                <div class="tcs-text">{{ s.text }}</div>
              </div>
              <div v-if="!suggestions.length" class="tcpm-empty" style="padding:30px">暂无建议</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 选修学分核查 -->
      <el-tab-pane :label="'选修学分核查' + (creditList.length > 0 ? '(' + creditList.length + ')' : '')" name="elective">
        <!-- 选修学分规则 -->
        <div class="tcp-rule-bar">
          <div class="tcr-item"><strong>学分要求：</strong>选修课 ≥ 8 学分</div>
          <div class="tcr-item"><strong>类别要求：</strong>人文社科、自然科学、艺术体育、创新创业 各至少 1 门</div>
        </div>

        <!-- 选修统计 -->
        <div class="tcp-elective-stats" v-if="creditList.length">
          <div class="tces"><div class="tces-num green">{{ electiveOkCount }}</div><div class="tces-lbl">全部达标<br>(学分+类别)</div></div>
          <div class="tces"><div class="tces-num amber">{{ electiveNearCount }}</div><div class="tces-lbl">部分达标</div></div>
          <div class="tces"><div class="tces-num red">{{ electiveLowCount }}</div><div class="tces-lbl">严重不足</div></div>
          <div class="tces"><div class="tces-num">{{ categoryOkCount }}</div><div class="tces-lbl">类别全覆盖</div></div>
          <div class="tces"><div class="tces-num">{{ electiveAvg }}</div><div class="tces-lbl">人均学分</div></div>
        </div>

        <!-- 选修核查表格 -->
        <div class="tcp-table-wrap" v-if="creditList.length">
          <el-table :data="pagedCreditList" stripe>
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="studentName" label="姓名" width="80" />
            <el-table-column label="已选" width="65" align="center">
              <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.courseCount }}门</el-tag></template>
            </el-table-column>
            <el-table-column label="已获学分" width="85" align="center">
              <template #default="{ row }"><span :class="row.completedCredits >= 8 ? 'cr-ok' : 'cr-low'">{{ row.completedCredits }}</span></template>
            </el-table-column>
            <el-table-column label="仍需" width="65" align="center">
              <template #default="{ row }"><span class="cr-gap">{{ Math.max(0, 8 - row.completedCredits) }}</span></template>
            </el-table-column>
            <el-table-column label="类别覆盖" min-width="140">
              <template #default="{ row }">
                <template v-if="row.categories && row.categories.length">
                  <el-tag v-for="cat in row.categories" :key="cat" size="small" :type="catType(cat)" effect="plain" style="margin-right:3px;margin-bottom:2px">{{ cat }}</el-tag>
                </template>
                <span v-else class="text-muted">--</span>
              </template>
            </el-table-column>
            <el-table-column label="类别达标" width="75" align="center">
              <template #default="{ row }">
                <span :class="(row.categoryScore || 0) >= 4 ? 'cr-ok' : (row.categoryScore || 0) >= 2 ? 'cr-warn' : 'cr-low'">{{ row.categoryScore || 0 }}/4</span>
              </template>
            </el-table-column>
            <el-table-column label="综合达标" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="row.completedCredits >= 8 && row.categoryScore >= 4 ? 'success' : 'warning'" size="small">
                  {{ row.completedCredits >= 8 && row.categoryScore >= 4 ? '已达标' : '未达标' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="recentCourses" label="最近选修" min-width="120" show-overflow-tooltip />
            <el-table-column label="建议" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.completedCredits >= 8 && row.categoryScore >= 4" class="sug-ok">✔ 全部达标</span>
                <span v-else class="sug-text">{{ getSuggestionText(row) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="tcp-pagination">
            <el-pagination v-model:current-page="electivePage" :page-size="electivePageSize" layout="total, prev, pager, next" :total="creditList.length" background small />
          </div>
        </div>
        <div v-else class="tcpm-empty">暂无选修数据，请确保有学生选课记录</div>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailStudent.studentName + ' 学分预测详情'" width="560px" destroy-on-close>
      <div v-if="detailStudent" class="tcp-detail">
        <div class="tcpd-section">
          <div class="tcpds-title">毕业学分</div>
          <div class="tcpd-summary">
            <div class="tcpds"><label>已获</label><b>{{ detailStudent.totalCredits }}</b></div>
            <div class="tcpds"><label>需修</label><b>120</b></div>
            <div class="tcpds"><label>缺口</label><b class="gap">{{ Math.max(0, 120 - (detailStudent.totalCredits || 0)) }}</b></div>
          </div>
          <el-progress :percentage="detailStudent.totalPercent" :color="detailStudent.totalPercent >= 100 ? '#16a34a' : detailStudent.totalPercent >= 70 ? '#f59e0b' : '#ef4444'" :stroke-width="10" />
        </div>
        <div class="tcpd-section">
          <div class="tcpds-title">选修学分 <span style="font-weight:400;font-size:12px;color:#94a3b8;">（要求≥8，分类别各≥1门）</span></div>
          <div class="tcpd-summary">
            <div class="tcpds"><label>已获</label><b>{{ detailStudent.electiveCredits }}</b></div>
            <div class="tcpds"><label>需修</label><b>8</b></div>
            <div class="tcpds"><label>缺口</label><b class="gap">{{ Math.max(0, 8 - (detailStudent.electiveCredits || 0)) }}</b></div>
          </div>
          <el-progress :percentage="detailStudent.electivePercent" :color="detailStudent.electivePercent >= 100 ? '#16a34a' : detailStudent.electivePercent >= 50 ? '#f59e0b' : '#ef4444'" :stroke-width="10" />
        </div>
        <div class="tcpd-courses" v-if="detailStudent.courseNames"><div class="tcpdc-title">已修课程</div><p>{{ detailStudent.courseNames }}</p></div>
        <div class="tcpd-courses" v-if="detailStudent.electiveNames" style="margin-top:8px"><div class="tcpdc-title">选修课程</div><p>{{ detailStudent.electiveNames || '--' }}</p></div>
        <div class="tcpd-tip" v-if="detailStudent.risk !== 'low'">
          <svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/></svg>
          <span>{{ detailStudent.risk === 'high' ? '选修学分缺口较大，建议立即安排辅导并制定补修计划。' : '选修学分进度偏慢，建议加强关注并定期跟踪。' }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { teacherAPI } from '@/api/index'
import { getTeacherId } from '@/utils/userUtils'
import { toNumber, PASS_LINE } from '@/utils/score'

const activeTab = ref('prediction')
const predictions = ref([])
const creditList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const electivePage = ref(1)
const electivePageSize = ref(12)

const pagedPredictions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return predictions.value.slice(start, start + pageSize.value)
})
const pagedCreditList = computed(() => {
  const start = (electivePage.value - 1) * electivePageSize.value
  return creditList.value.slice(start, start + electivePageSize.value)
})
const detailVisible = ref(false)
const detailStudent = ref({})
const chartRef = ref(null)

const stats = reactive({ total: 0, highRisk: 0, mediumRisk: 0, lowRisk: 0, electiveOk: 0 })

// 毕业学分要求表（静态数据）
const reqTableData = ref([
  { item: '总学分', requirement: '≥ 120 学分', tagType: 'success', category: '硬性指标', note: '含必修课、选修课、实践环节等所有课程' },
  { item: '选修总学分', requirement: '≥ 8 学分', tagType: 'warning', category: '硬性指标', note: '从校级选修课库中修读，每门通常2学分' },
  { item: '人文社科类', requirement: '≥ 1 门', tagType: 'primary', category: '类别覆盖', note: '如心理健康教育、大学英语、中国传统文化等' },
  { item: '自然科学类', requirement: '≥ 1 门', tagType: 'success', category: '类别覆盖', note: '如高等数学、线性代数、人工智能导论等' },
  { item: '艺术体育类', requirement: '≥ 1 门', tagType: 'warning', category: '类别覆盖', note: '如公共艺术鉴赏、美术鉴赏、体育舞蹈等' },
  { item: '创新创业类', requirement: '≥ 1 门', tagType: 'danger', category: '类别覆盖', note: '如职业生涯规划、创业基础、创新实践等' }
])

// 类别映射
const categoryMap = { '数学': '自然科学', '英语': '人文社科', '计算机': '自然科学', 'C语言': '自然科学', 'Java': '自然科学', 'Python': '自然科学', '物理': '自然科学', '化学': '自然科学', '生物': '自然科学', '语文': '人文社科', '历史': '人文社科', '政治': '人文社科', '哲学': '人文社科', '法律': '人文社科', '心理': '人文社科', '文化': '人文社科', '社会': '人文社科', '体育': '艺术体育', '美术': '艺术体育', '音乐': '艺术体育', '艺术': '艺术体育', '设计': '艺术体育', '创新': '创新创业', '创业': '创新创业', '实践': '创新创业', '职业': '创新创业', '管理': '创新创业', '大数据': '自然科学', '人工智能': '自然科学', '算法': '自然科学', '编程': '自然科学', '网络': '自然科学', '电路': '自然科学' }
const REQUIRED_CATEGORIES = ['人文社科', '自然科学', '艺术体育', '创新创业']

function guessCategory(courseName) {
  if (!courseName) return '自然科学'
  for (const [key, cat] of Object.entries(categoryMap)) {
    if (courseName.includes(key)) return cat
  }
  return '自然科学'
}

function catType(cat) {
  const m = { '人文社科': 'primary', '自然科学': 'success', '艺术体育': 'warning', '创新创业': 'danger' }
  return m[cat] || 'info'
}

function getSuggestionText(row) {
  const creditGap = Math.max(0, 8 - (row.completedCredits || 0))
  const coursesByCredit = Math.ceil(creditGap / 2)
  const covered = new Set(row.categories || [])
  const missingCategories = REQUIRED_CATEGORIES.filter(c => !covered.has(c))
  const coursesByCategory = missingCategories.length
  const needCourses = Math.max(coursesByCredit, coursesByCategory)
  const parts = []
  if (creditGap > 0) parts.push(`缺${creditGap}学分(约${coursesByCredit}门课)`)
  if (missingCategories.length > 0) parts.push(`缺类别:${missingCategories.join('、')}`)
  return `需修 ${needCourses} 门课 · ` + (parts.length ? parts.join('；') : '暂无缺口')
}

const electiveOkCount = computed(() => creditList.value.filter(s => s.completedCredits >= 8 && (s.categoryScore || 0) >= 4).length)
const electiveNearCount = computed(() => creditList.value.filter(s => (s.completedCredits >= 4 || (s.categoryScore || 0) >= 2) && !(s.completedCredits >= 8 && (s.categoryScore || 0) >= 4)).length)
const electiveLowCount = computed(() => creditList.value.filter(s => s.completedCredits < 4 && (s.categoryScore || 0) < 2).length)
const categoryOkCount = computed(() => creditList.value.filter(s => (s.categoryScore || 0) >= 4).length)
const electiveAvg = computed(() => creditList.value.length ? (creditList.value.reduce((a, b) => a + b.completedCredits, 0) / creditList.value.length).toFixed(1) : '0.0')

const suggestions = computed(() => {
  const list = []
  const notOk = creditList.value.filter(s => !(s.completedCredits >= 8 && (s.categoryScore || 0) >= 4))
  if (notOk.length > 0) list.push({ level: 'danger', text: `${notOk.length} 名学生未满足选修毕业要求（8学分+4类各1门），需重点关注` })
  if (categoryOkCount.value < creditList.value.length && creditList.value.length > 0)
    list.push({ level: 'warning', text: `${creditList.value.length - categoryOkCount.value} 名学生选修类别未全覆盖，注意引导选课` })
  if (electiveLowCount.value > 0) list.push({ level: 'danger', text: `${electiveLowCount.value} 名学生学分和类别均严重不足，需制定补修计划` })
  if (notOk.length === 0 && creditList.value.length > 0) list.push({ level: 'success', text: '所有学生选修达标情况良好，继续保持' })
  return list
})

onMounted(async () => { await loadData() })

async function loadData() {
  try {
    const tid = getTeacherId()
    if (!tid) { console.warn('[TeacherCreditPrediction] 未获取到登录用户 ID，跳过加载'); return }
    const enrollRes = await teacherAPI.getEnrollments(tid)
    // 2026-10-02（W4.15）：删掉恒假的 `enrollRes?.code === 200` 支（拦截器已解包）。
    const allEnrollments = Array.isArray(enrollRes) ? enrollRes : []

    // 毕业学分预测（全部课程）
    const studentMap = new Map()
    // 选修学分核查（仅选修课）
    const electiveMap = new Map()

    allEnrollments.forEach(e => {
      const sid = e.studentId; if (!sid) return
      const credit = Number(e.credits || 0)
      const type = (e.type || '').replace('课', '')
      const isElective = type === '选修'
      // 2026-10-19：学分只认**真及格**的课。score 可能是 '待录入' 字符串或 null，
      // 原先不读 score 直接累加，挂科/未录入也计入"已获学分"，达标率虚高。
      // 口径与 CounselorCourses.vue（score 非空且 ≥60 才计学分）一致。
      const score = toNumber(e.score)
      const passed = score !== null && score >= PASS_LINE

      // 毕业学分聚合
      if (!studentMap.has(sid)) {
        studentMap.set(sid, { id: sid, name: e.studentName || '学生' + sid, totalCredits: 0, electiveCredits: 0, courseNames: [], electiveNames: [] })
      }
      const s = studentMap.get(sid)
      if (passed) {
        s.totalCredits += credit
        if (isElective) { s.electiveCredits += credit; s.electiveNames.push(e.courseName || '') }
      }
      s.courseNames.push(e.courseName || '')

      // 选修核查聚合
      if (isElective && passed) {
        if (!electiveMap.has(sid)) {
          electiveMap.set(sid, { studentId: sid, studentNo: e.studentNo || '', studentName: e.studentName || '学生' + sid, courseIds: new Set(), courseNames: [], totalCredits: 0, categories: new Set() })
        }
        const em = electiveMap.get(sid)
        if (!em.courseIds.has(e.courseId)) {
          em.courseIds.add(e.courseId)
          em.totalCredits += credit
          em.courseNames.push(e.courseName || '课程' + e.courseId)
          em.categories.add(guessCategory(e.courseName))
        }
      }
    })

    // 构建毕业预测列表
    const list = [...studentMap.values()].map(s => {
      // 2026-10-19：毕业总学分与后端统一取 120（ScoreCaliber.GRADUATION_CREDITS，
      // student-service 学分进度、teacher-service 同口径），原先写 150，
      // 同一学生在学生端学分条与这里对不上。
      const totalPercent = Math.min(100, Math.round(s.totalCredits / 120 * 100))
      const electivePercent = Math.min(100, Math.round(s.electiveCredits / 8 * 100))
      const risk = electivePercent < 50 ? 'high' : electivePercent < 80 ? 'medium' : 'low'
      return { studentId: s.id, studentName: s.name, totalCredits: s.totalCredits, electiveCredits: s.electiveCredits, totalPercent, electivePercent, risk, courseNames: s.courseNames.slice(0, 5).join('、'), electiveNames: s.electiveNames.slice(0, 3).join('、') }
    })

    predictions.value = list
    currentPage.value = 1
    stats.total = list.length
    stats.highRisk = list.filter(p => p.risk === 'high').length
    stats.mediumRisk = list.filter(p => p.risk === 'medium').length
    stats.lowRisk = list.filter(p => p.risk === 'low').length

    // 构建选修核查列表
    creditList.value = [...electiveMap.values()].map(s => {
      const covered = new Set(s.categories)
      const categoryScore = REQUIRED_CATEGORIES.filter(c => covered.has(c)).length
      return { studentNo: s.studentNo, studentName: s.studentName, courseCount: s.courseIds.size, completedCredits: s.totalCredits, requiredCredits: 8, categories: [...s.categories], categoryScore, recentCourses: s.courseNames.slice(-3).join('、') }
    })
    // 顶部卡片与选修 tab 用同一个"达标"定义：学分 ≥8 且 4 类全覆盖。
    stats.electiveOk = electiveOkCount.value

    await nextTick()
    if (chartRef.value) renderChart()
  } catch (e) { console.error('loadData', e) }
}

let chartInstance = null
function renderChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption({
    title: { text: '学分达标率分布', left: 'center', top: 12, textStyle: { fontSize: 15, color: '#1e293b', fontWeight: 600 } },
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { orient: 'horizontal', bottom: 8, left: 'center', icon: 'circle', itemWidth: 10, itemHeight: 10, itemGap: 30, textStyle: { fontSize: 12, color: '#475569' } },
    series: [{ type: 'pie', radius: ['40%', '62%'], center: ['50%', '50%'], avoidLabelOverlap: false, minAngle: 3,
      label: { show: true, position: 'outside', distanceToLabelLine: 6, formatter: (p) => `{name|${p.name}}\n{value|${p.value}人  ${p.percent}%}`, rich: { name: { fontSize: 12, color: '#64748b', lineHeight: 18 }, value: { fontSize: 13, color: '#1e293b', fontWeight: 600, lineHeight: 20 } } },
      labelLine: { show: true, length: 25, length2: 20, smooth: 0.3, lineStyle: { width: 1.5, color: '#cbd5e1' } },
      emphasis: { scale: true, scaleSize: 6, itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.1)' } },
      data: [{ name: '高风险', value: stats.highRisk, itemStyle: { color: '#ef4444' } }, { name: '中风险', value: stats.mediumRisk, itemStyle: { color: '#f59e0b' } }, { name: '低风险', value: stats.lowRisk, itemStyle: { color: '#16a34a' } }]
    }]
  })
}

function viewDetail(row) { detailStudent.value = row; detailVisible.value = true }
</script>

<style scoped>
.tcp-page { padding: 20px 24px; min-height: 100vh; background: #f5f7fb; }
.tcp-hero { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.tcph-left h1 { margin: 0; font-size: 22px; color: #1e293b; }
.tcph-left p { margin: 4px 0 0; font-size: 13px; color: #94a3b8; }

/* 状态卡片 */
.tcp-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.tcps-card { background: #fff; border-radius: 12px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.tcps-card.total { border-left: 4px solid #3b82f6; } .tcps-card.danger { border-left: 4px solid #ef4444; }
.tcps-card.warning { border-left: 4px solid #16a34a; } .tcps-card.success { border-left: 4px solid #7c3aed; }
.tcps-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.tcps-card.total .tcps-icon { background: #eff6ff; color: #3b82f6; } .tcps-card.danger .tcps-icon { background: #fef2f2; color: #ef4444; }
.tcps-card.warning .tcps-icon { background: #ecfdf5; color: #16a34a; } .tcps-card.success .tcps-icon { background: #f5f3ff; color: #7c3aed; }
.tcps-num { font-size: 26px; font-weight: 700; color: #18181b; }
.tcps-lbl { font-size: 12px; color: #94a3b8; }

/* Tab 样式 */
.tcp-tabs { margin-bottom: 0; }
::v-deep(.tcp-tabs .el-tabs__header) { margin-bottom: 12px; }

/* 毕业学分要求卡片 */
.tcp-req-card { background: #fff; border-radius: 12px; padding: 16px 20px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.tcprq-title { font-size: 15px; font-weight: 600; color: #1e293b; margin-bottom: 4px; }
.tcprq-desc { font-size: 12px; color: #94a3b8; margin-bottom: 12px; }

/* 表格 */
.tcpm-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; }
.tcpm-head { display: flex; justify-content: space-between; align-items: center; padding: 14px 20px; border-bottom: 1px solid #f1f5f9; font-size: 15px; font-weight: 600; }
.tcpm-badge { font-size: 12px; color: #64748b; background: #f1f5f9; padding: 2px 10px; border-radius: 10px; }
.tcpm-empty { text-align: center; padding: 60px 20px; color: #94a3b8; font-size: 13px; }
.tcp-pagination { display: flex; justify-content: center; padding: 14px 0; }

/* 图表行 */
.tcp-chart-row { display: grid; grid-template-columns: 1fr 320px; gap: 16px; }
.tcpr-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; }
.tcp-suggest-card { padding: 16px 20px; }
.tcpr-title { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 12px; }
.tcpr-suggest { display: flex; flex-direction: column; gap: 10px; }
.tcs-item { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; color: #475569; }
.tcs-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 4px; flex-shrink: 0; }
.tcs-dot.danger { background: #ef4444; } .tcs-dot.warning { background: #f59e0b; } .tcs-dot.success { background: #16a34a; }

/* 选修核查样式 */
.tcp-rule-bar { display: flex; gap: 24px; background: #eff6ff; border-radius: 8px; padding: 10px 18px; margin-bottom: 14px; font-size: 13px; color: #1e40af; }
.tcr-item strong { color: #1d4ed8; }
.tcp-elective-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.tces { background: #fff; border-radius: 10px; padding: 14px; text-align: center; box-shadow: 0 1px 2px rgba(0,0,0,.03); }
.tces-num { font-size: 22px; font-weight: 700; } .tces-lbl { font-size: 12px; color: #94a3b8; }
.tces-num.green { color: #16a34a; } .tces-num.amber { color: #f59e0b; } .tces-num.red { color: #ef4444; }
.tcp-table-wrap { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.04); overflow: hidden; padding: 0 4px 8px; }

/* 通用 */
.tcp-credit-cell { display: flex; align-items: baseline; gap: 2px; font-size: 14px; }
.cr-ok { color: #16a34a; font-weight: 700; } .cr-low { color: #ef4444; font-weight: 700; } .cr-warn { color: #f59e0b; font-weight: 700; }
.cr-div { color: #cbd5e1; } .cr-req { color: #94a3b8; } .cr-gap { color: #f59e0b; font-weight: 600; }
.tcp-bar-cell { padding-top: 4px; }
.sug-ok { color: #16a34a; } .sug-text { color: #64748b; font-size: 12px; }
.text-muted { color: #94a3b8; }

/* 详情弹窗 */
.tcp-detail { line-height: 1.6; }
.tcpd-section { margin-bottom: 16px; }
.tcpds-title { font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 8px; }
.tcpd-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 10px; }
.tcpds { background: #f8fafc; border-radius: 8px; padding: 10px; text-align: center; }
.tcpds label { display: block; font-size: 11px; color: #94a3b8; margin-bottom: 2px; }
.tcpds b { font-size: 16px; color: #1e293b; } .tcpds b.gap { color: #ef4444; }
.tcpd-courses { background: #f8fafc; border-radius: 8px; padding: 12px 14px; }
.tcpdc-title { font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 6px; }
.tcpd-courses p { font-size: 13px; color: #64748b; margin: 0; line-height: 1.6; }
.tcpd-tip { display: flex; align-items: center; gap: 8px; margin-top: 14px; padding: 12px 14px; background: #fef2f2; border-radius: 8px; font-size: 13px; color: #b91c1c; }

@media (max-width: 900px) {
  .tcp-stats { grid-template-columns: repeat(2, 1fr); }
  .tcp-chart-row { grid-template-columns: 1fr; }
  .tcp-elective-stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
