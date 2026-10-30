<template>
  <view class="page-root" :style="themeStyle">
    <app-navbar title="成绩管理" :show-back="false" />

    <!-- 课程切换。横向滚动，不用 picker：教师只有两三门课，直接点比弹框快。 -->
    <scroll-view v-if="courses.length" class="course-bar" scroll-x :show-scrollbar="false">
      <view class="course-bar__inner">
        <view
          v-for="c in courses"
          :key="c.id"
          class="course-chip"
          :class="{ 'course-chip--on': c.id === courseId }"
          @tap="selectCourse(c.id)"
        >
          <text class="course-chip__name">{{ c.name }}</text>
          <text class="course-chip__meta">{{ creditsOf(c) }} 学分</text>
        </view>
      </view>
    </scroll-view>

    <view class="page-body page-body--tabbar">
      <page-loading v-if="loading" :count="4" />

      <template v-else>
        <empty-state
          v-if="!courses.length"
          text="暂无授课课程"
          hint="课程由管理员分配，分配后这里会出现成绩录入入口"
          icon="book"
        />

        <template v-else>
          <!-- 分析卡：口径全部来自后端 ScoreCaliber（先滤待录入、再按课程取学期最大），
               所以这里的平均分/人数与下面的**流水行数**天然不同 ——
               司明在高等数学上有 3 条流水，统计上只算 1 个人。 -->
          <view class="card">
            <view class="ana__head">
              <text class="ana__title">{{ ana.courseName || courseName }}</text>
              <text class="ana__sub">{{ numOr(ana.totalStudents) }} 名学生 · 已出成绩 {{ numOr(ana.gradedCount) }} 人</text>
            </view>

            <!-- 主角行：平均分（教师看一门课先看的数）+ 及格率/优秀率进度条。
                 次行才放最高/最低/标准差——它们是佐证，不是结论。 -->
            <view class="ana__main">
              <view class="ana__hero">
                <text class="ana__label">平均分</text>
                <text class="ana__hero-value">{{ dec(ana.averageScore) }}</text>
              </view>
              <view class="ana__rates">
                <view class="rate">
                  <view class="rate__top">
                    <text class="rate__label">及格率</text>
                    <text class="rate__num">{{ pct(ana.passRate) }}</text>
                  </view>
                  <view class="rate__track">
                    <view class="rate__fill" :style="{ width: ratePct(ana.passRate) + '%' }" />
                  </view>
                </view>
                <view class="rate">
                  <view class="rate__top">
                    <text class="rate__label">优秀率</text>
                    <text class="rate__num">{{ pct(ana.excellentRate) }}</text>
                  </view>
                  <view class="rate__track">
                    <view class="rate__fill rate__fill--soft" :style="{ width: ratePct(ana.excellentRate) + '%' }" />
                  </view>
                </view>
              </view>
            </view>

            <view class="ana__sub">
              <text class="ana__sub-item">最高 <text class="ana__sub-num">{{ dec(ana.maxScore, 0) }}</text></text>
              <text class="ana__sub-item">最低 <text class="ana__sub-num">{{ dec(ana.minScore, 0) }}</text></text>
              <text class="ana__sub-item">标准差 <text class="ana__sub-num">{{ dec(ana.standardDeviation) }}</text></text>
              <text class="ana__sub-item">挂科 <text class="ana__sub-num ana__sub-num--danger">{{ numOr(ana.failedCount) }}</text> 人</text>
            </view>

            <view class="bars">
              <view v-for="b in bars" :key="b.label" class="bar">
                <text class="bar__label">{{ b.label }}</text>
                <view class="bar__track">
                  <view class="bar__fill" :class="'bar__fill--' + b.key" :style="{ width: b.pct + '%' }" />
                </view>
                <text class="bar__num">{{ b.count }}</text>
              </view>
            </view>
          </view>

          <view class="bar-head">
            <text class="bar-head__title">{{ selectMode ? '选择要删除的记录' : '成绩流水' }}</text>
            <view v-if="selectMode" class="bar-head__actions">
              <view class="bar-head__btn bar-head__btn--ghost" @tap="exitSelect">取消</view>
            </view>
            <view v-else class="bar-head__actions">
              <view class="bar-head__btn bar-head__btn--ghost" @tap="openImport">导入</view>
              <view v-if="rows.length" class="bar-head__btn bar-head__btn--ghost" @tap="enterSelect">批量删除</view>
              <view class="bar-head__btn" @tap="openCreate">录入成绩</view>
            </view>
          </view>
          <!-- 这句话必须留着：一门课 24 人却有 26 行，不解释会被当成重复数据。 -->
          <text class="note">同一学生的多次成绩按学期各占一行；上方统计已按课程去重，人数会少于行数。</text>

          <empty-state
            v-if="!rows.length"
            text="这门课还没有成绩记录"
            hint="点上方「录入成绩」开始录入"
            icon="edit"
            :top="60"
          />

          <view v-else class="card card--flush" :class="{ 'card--select': selectMode }">
            <view
              v-for="(r, i) in rows"
              :key="r.id"
              class="srow"
              :class="{ 'srow--divided': i > 0, 'srow--checked': selectMode && checked.has(r.id) }"
              @tap="selectMode ? toggleCheck(r.id) : openEdit(r)"
            >
              <view v-if="selectMode" class="srow__check" :class="{ 'srow__check--on': checked.has(r.id) }">
                <app-icon v-if="checked.has(r.id)" name="check" :size="22" color="#ffffff" />
              </view>
              <view class="srow__main">
                <view class="srow__top">
                  <text class="srow__name">{{ r.studentName || '未知学生' }}</text>
                  <text class="srow__sem">{{ semLabel(r) }}</text>
                </view>
                <text class="srow__remark">{{ r.remark || '（未填写评语）' }}</text>
              </view>
              <view class="srow__right">
                <score-cell :score="r" />
                <text v-if="gradeOf(r)" class="srow__grade">{{ gradeOf(r) }}</text>
              </view>
            </view>
          </view>

          <!-- 多选操作条：固定在 tab-bar 之上，进多选才出现 -->
          <view v-if="selectMode" class="selbar">
            <view class="selbar__all" @tap="toggleAll">
              <view class="srow__check" :class="{ 'srow__check--on': allChecked }">
                <app-icon v-if="allChecked" name="check" :size="22" color="#ffffff" />
              </view>
              <text class="selbar__all-text">全选</text>
            </view>
            <text class="selbar__count">已选 {{ checked.size }} 条</text>
            <view class="selbar__del" :class="{ 'selbar__del--disabled': !checked.size }" @tap="confirmBatchDelete">
              删除
            </view>
          </view>
          <view v-if="selectMode" class="selbar-pad" />
        </template>
      </template>
    </view>

    <!-- 改分 / 删除 -->
    <detail-sheet v-model="editOpen" :title="editing ? (editing.studentName || '成绩详情') : ''" height="70vh">
      <view v-if="editing" class="form">
        <view class="form__facts">
          <view class="form__fact">
            <text class="form__fact-label">课程</text>
            <text class="form__fact-value">{{ courseName }}</text>
          </view>
          <view class="form__fact">
            <text class="form__fact-label">学期</text>
            <text class="form__fact-value">{{ semLabel(editing, false) }}</text>
          </view>
          <view class="form__fact">
            <text class="form__fact-label">平时 / 期末</text>
            <text class="form__fact-value">{{ splitText(editing) }}</text>
          </view>
        </view>

        <text class="form__label">总评成绩</text>
        <input
          v-model="form.score"
          class="form__input"
          type="digit"
          placeholder="0 ~ 100"
          placeholder-class="form__placeholder"
        />
        <text v-if="editing && isPending(editing)" class="form__tip">该行期末成绩为空（待录入），填写总评会一并补齐等级与绩点。</text>

        <text class="form__label">修改说明 / 评语</text>
        <textarea
          v-model="form.remark"
          class="form__textarea"
          placeholder="例如：试卷复核发现平时分漏计，据实更正"
          placeholder-class="form__placeholder"
          :maxlength="200"
          auto-height
        />
        <text class="form__tip">保存会同时更新成绩流水、权威成绩表，并生成一条成绩修改记录（可在「班级管理」的修改记录里看到）。</text>

        <view class="form__danger" @tap="removeScore">删除这条成绩记录</view>
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !canSaveScore }" @tap="submitScore">
          {{ saving ? '保存中…' : '保存修改' }}
        </view>
      </template>
    </detail-sheet>

    <!-- 批量导入 -->
    <detail-sheet v-model="importOpen" title="批量导入成绩" height="76vh">
      <view class="form">
        <text class="form__label">课程</text>
        <view class="form__static">
          <text class="form__picked">{{ courseName }}</text>
        </view>
        <text class="form__tip">导入目标是当前选中的课程，如需导入其他课请先在上方切换。</text>

        <text class="form__label">学期（选填）</text>
        <picker mode="selector" :range="importSemesterLabels" :value="importSemesterIndex" @change="onImportSemesterChange">
          <view class="form__picker">
            <text class="form__picked">{{ importSemesterLabels[importSemesterIndex] }}</text>
            <app-icon name="chevron" :size="26" color="#aeaeb2" />
          </view>
        </picker>
        <text class="form__tip">未选择时以 Excel 的「学期」列为准；仍为空时按最近学期统计。</text>

        <text class="form__label">Excel 文件</text>
        <view class="file-pick" @tap="chooseFile">
          <app-icon :name="importFile ? 'doc' : 'upload'" :size="34" />
          <view class="file-pick__main">
            <text class="file-pick__name">{{ importFile ? importFile.name : '选择 .xlsx / .xls 文件' }}</text>
            <text class="file-pick__hint">{{ importFile ? '点击可重新选择' : '微信里从聊天记录选取，电脑上直接选文件' }}</text>
          </view>
        </view>
        <text class="form__tip">
          列：学号（必填）、姓名、总评成绩（必填）、学期、备注。
          <text class="form__link" @tap="downloadTemplate">下载模板</text>
        </text>

        <view v-if="importResult" class="import-result">
          <view class="import-result__summary">
            <text class="import-result__num">{{ numOr(importResult.success) }}</text>
            <text class="import-result__lab">导入成功</text>
            <text class="import-result__num import-result__num--muted">{{ numOr(importResult.skipped) }}</text>
            <text class="import-result__lab">跳过</text>
          </view>
          <view v-if="importErrors.length" class="import-result__errors">
            <view v-for="(e, i) in importErrors" :key="i" class="import-result__err">
              <text>第 {{ e.row }} 行</text>
              <text class="import-result__err-msg">{{ e.error || e.message }}</text>
            </view>
          </view>
        </view>
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !importFile || importing }" @tap="submitImport">
          {{ importing ? '导入中…' : '开始导入' }}
        </view>
      </template>
    </detail-sheet>

    <!-- 录入 -->
    <detail-sheet v-model="createOpen" title="录入成绩" height="76vh">
      <view class="form">
        <text class="form__label">学生</text>
        <picker
          mode="selector"
          :range="rosterLabels"
          :value="rosterIndex"
          @change="onRosterChange"
        >
          <view class="form__picker">
            <text :class="rosterIndex < 0 ? 'form__ph' : 'form__picked'">
              {{ rosterIndex < 0 ? '选择一名学生' : rosterLabels[rosterIndex] }}
            </text>
            <app-icon name="chevron" :size="26" color="#aeaeb2" />
          </view>
        </picker>
        <text v-if="!roster.length" class="form__tip">所带班级暂无学生名单，无法录入。</text>

        <text class="form__label">学期</text>
        <picker mode="selector" :range="semesterLabels" :value="semesterIndex" @change="onSemesterChange">
          <view class="form__picker">
            <text class="form__picked">{{ semesterLabels[semesterIndex] }}</text>
            <app-icon name="chevron" :size="26" color="#aeaeb2" />
          </view>
        </picker>
        <!-- 这个行为必须写清楚：学期相同=覆盖，学期不同=新增一条 -->
        <text class="form__tip">同一学生 + 同一课程 + 同学期为**覆盖**；选一个不同的学期则新增一条（重修）。留「沿用最近学期」会覆盖该生该课最近一次录入。</text>

        <text class="form__label">总评成绩</text>
        <input
          v-model="form.score"
          class="form__input"
          type="digit"
          placeholder="0 ~ 100"
          placeholder-class="form__placeholder"
        />

        <text class="form__label">评语</text>
        <textarea
          v-model="form.remark"
          class="form__textarea"
          placeholder="选填，例如：课堂表现良好，期末发挥稳定"
          placeholder-class="form__placeholder"
          :maxlength="200"
          auto-height
        />
      </view>

      <template #footer>
        <view class="btn-primary" :class="{ 'btn-primary--disabled': !canCreate }" @tap="submitCreate">
          {{ saving ? '提交中…' : '保存成绩' }}
        </view>
      </template>
    </detail-sheet>

    <app-tab-bar role="teacher" />
  </view>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { useUser } from '@/composables/useUser.js'
import { roleThemeStyle } from '@/composables/useRoleTheme.js'
import { isPending, gradeOf, toNumber } from '@/utils/score.js'
import { scoreSemesterLabel } from '@/utils/score-normalize.js'
import { num } from '@/utils/response.js'

const { userId, userName } = useUser()

const themeStyle = roleThemeStyle('teacher')

/** 分数档位的中文标签与配色键，顺序与后端 scoreDistribution 的键一致。 */
const BINS = [
  { label: '90+', key: 'excellent' },
  { label: '80-89', key: 'good' },
  { label: '70-79', key: 'medium' },
  { label: '60-69', key: 'pass' },
  { label: '<60', key: 'fail' }
]

const loading = ref(true)
const courses = ref([])
const courseId = ref(null)
const rows = ref([])
const ana = ref({})
const roster = ref([])

const editOpen = ref(false)
const editing = ref(null)
const createOpen = ref(false)
const rosterIndex = ref(-1)
const semesterIndex = ref(0)
const saving = ref(false)
const form = ref({ score: '', remark: '' })

// —— 批量删除 ——
const selectMode = ref(false)
/** 已勾选的 scores.id 集合。用 reactive 包 Set，勾选状态才能驱动视图。 */
const checked = reactive(new Set())

// —— 批量导入 ——
const importOpen = ref(false)
const importFile = ref(null)
const importSemesterIndex = ref(0)
const importing = ref(false)
const importResult = ref(null)

const courseName = computed(() => {
  const c = courses.value.find(x => x.id === courseId.value)
  return c ? c.name : ''
})

/** 「沿用」放第一个：不传 semester 时后端按"该生该课最近一条"定位并覆盖。 */
const semesterLabels = computed(() => ['沿用最近学期', ...Array.from({ length: 8 }, (_, i) => `第${i + 1}学期`)])

const bars = computed(() => {
  const dist = ana.value.scoreDistribution && typeof ana.value.scoreDistribution === 'object'
    ? ana.value.scoreDistribution
    : {}
  const max = Math.max(1, ...BINS.map(b => num(dist[b.label])))
  return BINS.map(b => {
    const count = num(dist[b.label])
    return { label: b.label, key: b.key, count, pct: Math.round((count / max) * 100) }
  })
})

const rosterLabels = computed(() => roster.value.map(s => s.label))

const canSaveScore = computed(() => editing.value && validScore(form.value.score))
const canCreate = computed(() => rosterIndex.value >= 0 && validScore(form.value.score))

const allChecked = computed(() => rows.value.length > 0 && rows.value.every(r => checked.has(r.id)))

/** 导入的学期选择：与录入不同，「不指定」必须是一个显式选项（Excel 学期列优先级更高）。 */
const importSemesterLabels = computed(() => ['按 Excel 学期列', ...Array.from({ length: 8 }, (_, i) => `第${i + 1}学期`)])

const importErrors = computed(() => {
  const list = importResult.value && Array.isArray(importResult.value.errors) ? importResult.value.errors : []
  return list.slice(0, 20)
})

function validScore(v) {
  const n = Number(v)
  return v !== '' && Number.isFinite(n) && n >= 0 && n <= 100
}

function numOr(v) {
  return num(v, 0)
}

/** 小数位：平均分/标准差给两位，分数给整数。取不到给占位符而不是 0。 */
function dec(v, digits = 2) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return n.toFixed(digits)
}

/** 后端给 0~1 小数 */
function pct(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return `${(n * 100).toFixed(1)}%`
}

/** 进度条宽度：同 pct 的口径，返回 0~100 数字；取不到给 0（空条比 -- 好读）。 */
function ratePct(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.max(0, Math.min(100, Math.round(n * 100))) : 0
}

/**
 * 平时 / 期末 两项的展示文案。
 *
 * ⚠️ 录入表单只收总评，所以**新建出来的行**在库里平时分与期末分就是 0
 * （实测：从界面录一条 77 分，详情里读回 regular_score=0 / final_score=0）。
 * 直接显示「0 / 0」会被读成"这两项考了 0 分"，而事实是"没有分项录入" ——
 * 0 分是对学生下的一个断言，不能因为后端默认填 0 就替它认下来。
 * 所以两项都为 0 时显示「未分项录入」。
 */
function splitText(row) {
  const r = toNumber(row && row.regularScore)
  const f = toNumber(row && row.finalScore)
  if ((r === null || r === 0) && (f === null || f === 0)) return '未分项录入'
  const one = (v) => (v === null ? '—' : String(Number(v.toFixed(1))))
  return `${one(r)} / ${one(f)}`
}

function creditsOf(c) {
  const n = Number(c && c.credits)
  return Number.isFinite(n) ? (Number.isInteger(n) ? n : n.toFixed(1)) : '--'
}

/** 学期标签：流水行给的是学期序号字符串（"3"）+ 学年（2024），换算规则见 format.js。 */
function semLabel(r, short = true) {
  return scoreSemesterLabel(r, short)
}

function toast(title, icon = 'none') {
  uni.showToast({ title, icon })
}

/** 「课程管理」页点「查看成绩」会带 `?courseId=` 过来，取不到返回空串。 */
function readQueryCourseId() {
  const pages = getCurrentPages()
  const cur = pages[pages.length - 1]
  const o = (cur && cur.options) || {}
  return o.courseId ? String(o.courseId) : ''
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    if (!userId) return
    const list = await teacherAPI.getCourses(userId).catch(e => {
      console.error('[teacher scores] 课程失败:', e)
      return []
    })
    courses.value = Array.isArray(list) ? list : []
    if (!courses.value.length) return
    // 「课程管理」页带 `?courseId=` 进来时直接落在那门课上；
    // 参数认不出（课程已不归自己 / 手改了 URL）就退回第一门，不要卡在空白页。
    const picked = courses.value.find(c => String(c.id) === readQueryCourseId())
    // 默认选第一门课，并且**连带把花名册拉好**（录入表单要用）
    await selectCourse(picked ? picked.id : courses.value[0].id, true)
  } finally {
    loading.value = false
  }
}

async function selectCourse(id, initial = false) {
  if (!initial) loading.value = true
  courseId.value = id
  // 换课后流水行整体换血，多选态里的旧 id 必须清掉
  if (selectMode.value) exitSelect()
  try {
    const [s, a] = await Promise.all([
      teacherAPI.getScores(id).catch(e => { console.error('[teacher scores] 流水失败:', e); return [] }),
      teacherAPI.getAnalysis(id).catch(e => { console.error('[teacher scores] 分析失败:', e); return null })
    ])
    rows.value = Array.isArray(s) ? s : []
    ana.value = a && typeof a === 'object' ? a : {}
    if (!roster.value.length) await loadRoster()
  } finally {
    loading.value = false
  }
}

/**
 * 花名册 = 学生的 (profile id, 姓名) 对。
 *
 * 为什么不用 `/teachers/classes/{id}/students`：那条只返回 `{studentId(学号), studentName}`，
 * **没有 profile id**，而录入成绩要的 `studentId` 恰恰是 profile id。
 * `/teachers/scores/by-class` 的行里两个都有（`studentId`=profile id、`studentNo`=学号），
 * 所以从它去重取人最省事。
 */
async function loadRoster() {
  const classes = await teacherAPI.getMyClasses(userId).catch(() => [])
  const target = (Array.isArray(classes) ? classes : [])
    .filter(c => numOr(c.studentCount) > 0)
    .sort((a, b) => numOr(b.studentCount) - numOr(a.studentCount))[0]
  if (!target) return

  const list = await teacherAPI.getScoresByClass(target.name, userId).catch(e => {
    console.error('[teacher scores] 花名册失败:', e)
    return []
  })

  const seen = new Map()
  for (const r of Array.isArray(list) ? list : []) {
    const id = r.studentId
    if (id === null || id === undefined || seen.has(String(id))) continue
    const no = r.studentNo ? `（${r.studentNo}）` : ''
    seen.set(String(id), {
      id,
      name: r.studentName || '未知学生',
      className: r.className || target.name,
      label: `${r.studentName || '未知学生'}${no}`
    })
  }
  roster.value = [...seen.values()]
}

function openEdit(row) {
  editing.value = row
  form.value = { score: row.score === null || row.score === undefined ? '' : String(row.score), remark: row.remark || '' }
  editOpen.value = true
}

function openCreate() {
  editing.value = null
  rosterIndex.value = -1
  semesterIndex.value = 0
  form.value = { score: '', remark: '' }
  createOpen.value = true
}

function onRosterChange(e) {
  rosterIndex.value = Number(e.detail.value)
}

function onSemesterChange(e) {
  semesterIndex.value = Number(e.detail.value)
}

// —— 批量删除 ——
// 后端 batch-delete 收**裸数组**（@RequestBody List<Long>），历史 bug 就是多包了一层
// {ids:[...]} 导致恒 400 —— api.js 已修，这里是它的第一个真实调用方。

function enterSelect() {
  checked.clear()
  selectMode.value = true
}

function exitSelect() {
  selectMode.value = false
  checked.clear()
}

function toggleCheck(id) {
  if (checked.has(id)) checked.delete(id)
  else checked.add(id)
}

function toggleAll() {
  if (allChecked.value) checked.clear()
  else rows.value.forEach(r => checked.add(r.id))
}

function confirmBatchDelete() {
  if (!checked.size) return
  uni.showModal({
    title: '批量删除成绩',
    content: `确定删除选中的 ${checked.size} 条成绩记录？统计与分析会随之变化，此操作不可撤销。`,
    confirmText: '删除',
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await teacherAPI.batchDeleteScores([...checked])
        toast('已删除', 'success')
        exitSelect()
        await selectCourse(courseId.value)
      } catch (e) {
        toast(e && e.message ? e.message : '删除失败')
      }
    }
  })
}

// —— 批量导入 ——

function openImport() {
  importFile.value = null
  importSemesterIndex.value = 0
  importResult.value = null
  importOpen.value = true
}

function onImportSemesterChange(e) {
  importSemesterIndex.value = Number(e.detail.value)
}

/**
 * 选文件。两端能力不同必须分平台写：
 * H5 走 uni.chooseFile（浏览器文件框）；微信小程序只能从聊天记录里选（chooseMessageFile）。
 */
function chooseFile() {
  // #ifdef H5
  uni.chooseFile({
    count: 1,
    extension: ['.xlsx', '.xls'],
    success: (res) => {
      const f = res.tempFiles && res.tempFiles[0]
      // raw 存原始 File：uni-h5 的 uploadFile 对游离 blob URL 会丢扩展名，
      // 带 file 参数才能把 .name 原样送到后端（见 request.js upload 注释）
      if (f) importFile.value = { path: f.path, name: f.name || '成绩表.xlsx', raw: f.file || null }
    }
  })
  // #endif
  // #ifdef MP-WEIXIN
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['xlsx', 'xls'],
    success: (res) => {
      const f = res.tempFiles && res.tempFiles[0]
      if (f) importFile.value = { path: f.path, name: f.name || '成绩表.xlsx' }
    }
  })
  // #endif
}

function downloadTemplate() {
  teacherAPI.downloadScoresTemplate().catch(e => {
    toast(e && e.message ? e.message : '模板下载失败')
  })
}

async function submitImport() {
  if (!importFile.value || importing.value) return
  importing.value = true
  importResult.value = null
  try {
    const formData = { courseId: String(courseId.value) }
    if (importSemesterIndex.value > 0) formData.semester = String(importSemesterIndex.value)
    const r = await teacherAPI.importScoresFile(importFile.value.path, formData, importFile.value.raw)
    importResult.value = r || {}
    toast(`成功导入 ${numOr(r.success)} 条`, 'success')
    // 导入写的是 scores 表（saveScores 事务性落库），刷新即可见
    await selectCourse(courseId.value)
  } catch (e) {
    toast(e && e.message ? e.message : '导入失败')
  } finally {
    importing.value = false
  }
}

async function submitScore() {
  if (!canSaveScore.value || saving.value) return
  const row = editing.value
  saving.value = true
  try {
    await teacherAPI.updateScore(row.id, {
      score: Number(form.value.score),
      remark: form.value.remark,
      // 审计日志的 modified_by 用真实姓名，别留默认的「教师」
      modifiedBy: userName || '教师'
    })
    toast('成绩已更新', 'success')
    editOpen.value = false
    await selectCourse(courseId.value)
  } catch (e) {
    toast(e && e.message ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function removeScore() {
  const row = editing.value
  if (!row) return
  uni.showModal({
    title: '删除成绩',
    content: `确定删除「${row.studentName || '该学生'}」这条成绩记录？删除后该门课的统计会随之变化。`,
    confirmText: '删除',
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await teacherAPI.deleteScore(row.id)
        toast('已删除', 'success')
        editOpen.value = false
        await selectCourse(courseId.value)
      } catch (e) {
        toast(e && e.message ? e.message : '删除失败')
      }
    }
  })
}

async function submitCreate() {
  if (!canCreate.value || saving.value) return
  const student = roster.value[rosterIndex.value]
  if (!student) return
  saving.value = true
  try {
    const item = {
      studentId: student.id,
      studentName: student.name,
      className: student.className,
      courseId: courseId.value,
      score: Number(form.value.score),
      remark: form.value.remark
    }
    // 选「沿用最近学期」时不传 semester，后端会沿用该生该课最近一条的学期（覆盖语义）
    if (semesterIndex.value > 0) item.semester = String(semesterIndex.value)

    await teacherAPI.saveScores({ teacherId: userId, scores: [item] })
    toast('成绩已保存', 'success')
    createOpen.value = false
    await selectCourse(courseId.value)
  } catch (e) {
    toast(e && e.message ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
/* ---------- 课程切换 ---------- */
.course-bar {
  width: 100%;
  white-space: nowrap;
  background: $color-card;
  border-bottom: 1rpx solid $color-border;
}

.course-bar__inner {
  display: inline-flex;
  padding: $spacing-sm $page-padding;
}

.course-chip {
  display: flex;
  flex-direction: column;
  margin-right: $spacing-sm;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  background: $color-bg;
}

.course-chip--on {
  background: var(--color-primary-weak, #{$color-bg});
}

.course-chip__name {
  font-size: $font-sm;
  color: $color-text;
}

.course-chip--on .course-chip__name {
  color: var(--color-primary);
  font-weight: 600;
}

.course-chip__meta {
  margin-top: 2rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.page-body {
  padding-top: $spacing-lg;
}

.card {
  padding: $spacing-lg;
  margin: 0 $page-padding;
  @include card;
}

.card--flush {
  padding: 0;
  overflow: hidden;
}

/* ---------- 分析卡 ---------- */
.ana__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.ana__title {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.ana__sub {
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 分析卡主角行 ---------- */
.ana__main {
  display: flex;
  align-items: center;
  margin-top: $spacing-md;
}

.ana__hero {
  flex-shrink: 0;
  min-width: 200rpx;
}

.ana__hero-value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-2xl;
  font-weight: 700;
  line-height: 1.1;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.ana__rates {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-xl;
}

.rate + .rate {
  margin-top: $spacing-md;
}

.rate__top {
  display: flex;
  justify-content: space-between;
}

.rate__label {
  font-size: $font-xs;
  color: $color-text-hint;
}

.rate__num {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.rate__track {
  margin-top: 6rpx;
  height: 10rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.rate__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: var(--color-primary);
}

/* 优秀率条比及格率浅一档：同为结论指标，及格率是主。 */
.rate__fill--soft {
  opacity: 0.45;
}

/* ---------- 次行：佐证指标 ---------- */
.ana__sub {
  display: flex;
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.ana__sub-item {
  flex: 1;
  min-width: 0;
  font-size: $font-xs;
  color: $color-text-hint;
  text-align: center;
}

.ana__sub-item + .ana__sub-item {
  border-left: 1rpx solid $color-divider;
}

.ana__sub-num {
  font-size: $font-sm;
  font-weight: 600;
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.ana__sub-num--danger {
  color: $color-danger;
}

.ana__label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.bars {
  margin-top: $spacing-lg;
}

.bar {
  display: flex;
  align-items: center;
  margin-top: $spacing-sm;
}

.bar__label {
  width: 110rpx;
  flex-shrink: 0;
  font-size: $font-xs;
  color: $color-text-secondary;
}

.bar__track {
  flex: 1;
  min-width: 0;
  height: 16rpx;
  border-radius: $radius-pill;
  background: $color-bg;
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: $radius-pill;
}

.bar__fill--excellent { background: #1976d2; }
.bar__fill--good      { background: #2196f3; }
.bar__fill--medium    { background: #64b5f6; }
.bar__fill--pass      { background: #bbdefb; }
.bar__fill--fail      { background: $color-danger; }

.bar__num {
  width: 60rpx;
  flex-shrink: 0;
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* ---------- 列表头 ---------- */
.bar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: $spacing-xl $page-padding 0;
}

.bar-head__title {
  font-size: $font-lg;
  font-weight: 600;
  color: $color-text;
}

.bar-head__actions {
  display: flex;
  align-items: center;
}

.bar-head__btn {  @include tap-area;
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-pill;
  background: var(--color-primary);
  color: #fff;
  font-size: $font-xs;
}

.bar-head__btn + .bar-head__btn {
  margin-left: $spacing-sm;
}

/* 次级动作（导入/批量删除/取消）：主按钮留给「录入」，避免三个实心橙挤一排 */
.bar-head__btn--ghost {
  background: var(--color-primary-weak, #{$color-bg});
  color: var(--color-primary);
}

/* ---------- 多选模式 ---------- */
.card--select {
  border-color: rgba(239, 68, 68, 0.18);
}

.srow--checked {
  background: var(--color-primary-weak, #{$color-bg});
}

.srow__check {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  margin-right: $spacing-md;
  border-radius: 50%;
  border: 2rpx solid $color-divider;
  box-sizing: border-box;
  @include flex-center;
}

.srow__check--on {
  border-color: $color-danger;
  background: $color-danger;
}

.selbar {
  position: fixed;
  left: $page-padding;
  right: $page-padding;
  /* 操作条压在内容上、贴着自定义 tab-bar（100rpx）之上 */
  bottom: calc(100rpx + 24rpx);
  z-index: $z-tabbar - 1;
  display: flex;
  align-items: center;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-lg;
  background: $color-card;
  box-shadow: $shadow-overlay;
}

.selbar__all {
  display: flex;
  align-items: center;
  @include tap-area;
}

.selbar__all .srow__check {
  margin-right: $spacing-sm;
}

.selbar__all-text {
  font-size: $font-sm;
  color: $color-text;
}

.selbar__count {
  flex: 1;
  margin-left: $spacing-lg;
  font-size: $font-sm;
  color: $color-text-hint;
  font-variant-numeric: tabular-nums;
}

.selbar__del {
  @include tap-area;
  padding: $spacing-xs $spacing-xl;
  border-radius: $radius-pill;
  background: $color-danger;
  color: #fff;
  font-size: $font-sm;
}

.selbar__del--disabled {
  opacity: 0.4;
}

/* 操作条是 fixed 的，占位防最后一行被遮 */
.selbar-pad {
  height: 120rpx;
}

/* ---------- 导入表单 ---------- */
.form__static {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.file-pick {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  border: 1rpx dashed $color-border;
  background: $color-bg;
  @include tap-area;
}

.file-pick__main {
  flex: 1;
  min-width: 0;
  margin-left: $spacing-md;
  display: flex;
  flex-direction: column;
}

.file-pick__name {
  font-size: $font-sm;
  color: $color-text;
  @include ellipsis;
}

.file-pick__hint {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__link {
  color: var(--color-primary);
}

.import-result {
  margin-top: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1rpx solid $color-divider;
}

.import-result__summary {
  display: flex;
  align-items: baseline;
}

.import-result__num {
  font-size: $font-xl;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.import-result__num--muted {
  margin-left: $spacing-lg;
  color: $color-warning;
}

.import-result__lab {
  margin-left: 8rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

.import-result__errors {
  margin-top: $spacing-md;
  max-height: 320rpx;
  overflow-y: auto;
}

.import-result__err {
  display: flex;
  margin-top: $spacing-xs;
  font-size: $font-xs;
  color: $color-danger;
}

.import-result__err-msg {
  margin-left: $spacing-sm;
  color: $color-text-hint;
}

.note {
  display: block;
  margin: $spacing-sm $page-padding 0;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

/* ---------- 成绩行 ---------- */
.srow {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
}

.srow--divided {
  border-top: 1rpx solid $color-divider;
}

.srow:active {
  background: $color-bg;
}

.srow__main {
  flex: 1;
  min-width: 0;
}

.srow__top {
  display: flex;
  align-items: baseline;
}

.srow__name {
  font-size: $font-md;
  font-weight: 600;
  color: $color-text;
}

.srow__sem {
  margin-left: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
}

.srow__remark {
  display: block;
  margin-top: 6rpx;
  font-size: $font-xs;
  color: $color-text-secondary;
  @include ellipsis;
}

.srow__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: $spacing-sm;
}

.srow__grade {
  margin-top: 4rpx;
  font-size: $font-xs;
  color: $color-text-hint;
}

/* ---------- 表单 ---------- */
.form {
  padding: 0 $page-padding $spacing-2xl;
}

.form__facts {
  display: flex;
  padding-bottom: $spacing-md;
  border-bottom: 1rpx solid $color-divider;
}

.form__fact {
  flex: 1;
  min-width: 0;
}

.form__fact-label {
  display: block;
  font-size: $font-xs;
  color: $color-text-hint;
}

.form__fact-value {
  display: block;
  margin-top: 4rpx;
  font-size: $font-sm;
  color: $color-text;
}

.form__label {
  display: block;
  margin-top: $spacing-lg;
  font-size: $font-sm;
  color: $color-text;
}

.form__input {
  width: 100%;
  height: $tap-min;
  margin-top: $spacing-sm;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-md;
  color: $color-text;
}

.form__textarea {
  width: 100%;
  min-height: 160rpx;
  margin-top: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
  font-size: $font-sm;
  color: $color-text;
}

.form__placeholder {
  color: $color-text-hint;
}

.form__picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $tap-min;
  margin-top: $spacing-sm;
  padding: 0 $spacing-md;
  border-radius: $radius-md;
  background: $color-bg;
}

.form__ph {
  font-size: $font-sm;
  color: $color-text-hint;
}

.form__picked {
  font-size: $font-sm;
  color: $color-text;
}

.form__tip {
  display: block;
  margin-top: $spacing-sm;
  font-size: $font-xs;
  color: $color-text-hint;
  line-height: 1.5;
}

.form__danger {
  margin-top: $spacing-2xl;
  padding: $spacing-md 0;
  border-radius: $radius-md;
  background: rgba(239, 68, 68, 0.08);
  color: $color-danger;
  font-size: $font-sm;
  text-align: center;
}
</style>
