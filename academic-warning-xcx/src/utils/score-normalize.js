/**
 * 成绩行归一化。
 *
 * 后端成绩数据有几套命名并存，实测到的形态：
 * - `/students/scores/{id}`、`/teachers/scores`：驼峰（`scoreTotal` / `finalScore`）
 * - `/counselors/scores/class/{classId}`：**全下划线**（`score_total` / `final_score`）
 * - 少数聚合接口：`totalScore` / `usualScore` 这类同义词
 *
 * 页面里各写一套 `row.scoreTotal ?? row.score_total ?? ...` 是历史 bug 的温床
 * （漏一个就渲染成空），所以统一在这里拍平成一个稳定形状，页面只认规范名。
 *
 * **判定与展示口径一律 import 自 `utils/score.js`，这里不重复实现。**
 */

import { scoreOf, finalOf, regularOf, isPending, toNumber, calcGpa, PASS_LINE } from './score.js'
import { pickField } from './response.js'
import { semesterLabelOf, shortSemester, formatSemester } from './format.js'

/** 规范字段名。归一化后每个键都存在（取不到为 null），页面不需要再判 undefined。 */
export const SCORE_FIELDS = [
  'id', 'studentId', 'studentNo', 'studentName',
  'courseId', 'courseName', 'courseCode', 'courseType',
  'semester', 'credits', 'className', 'teacherName',
  'regularScore', 'finalScore', 'scoreTotal', 'grade', 'gradePoint'
]

/**
 * 把一行成绩拍平成规范形状。
 * @param {object} raw 后端原始行（驼峰或下划线均可）
 * @returns {object} 规范行，含 `raw` 保留原始载荷
 */
export function normalizeScoreRow(raw) {
  const row = raw && typeof raw === 'object' ? raw : {}
  const pick = (...names) => {
    const v = pickField(row, ...names)
    return v === undefined ? null : v
  }

  const normalized = {
    id: pick('id', 'scoreId', 'score_id'),
    // 双 ID 空间：scores.student_id 指向 student_profile.id（不是 users.id）
    studentId: pick('studentId', 'student_id'),
    studentNo: pick('studentNo', 'student_no', 'sno', 'username'),
    studentName: pick('studentName', 'student_name', 'name'),
    courseId: pick('courseId', 'course_id'),
    courseName: pick('courseName', 'course_name'),
    courseCode: pick('courseCode', 'course_code', 'code'),
    courseType: pick('courseType', 'course_type', 'type'),
    semester: pick('semester', 'term'),
    credits: pick('credits', 'credit', 'courseCredits'),
    className: pick('className', 'class_name'),
    teacherName: pick('teacherName', 'teacher_name'),
    grade: pick('grade', 'gradeName'),
    gradePoint: pick('gradePoint', 'grade_point', 'gpa'),
    raw
  }

  // 三个分值直接用 score.js 的取值函数，保证与判定口径同源
  normalized.regularScore = regularOf(row) ?? null
  normalized.finalScore = finalOf(row) ?? null
  normalized.scoreTotal = scoreOf(row) ?? null

  // 归一化后一定存在 finalScore 键，isPending 的"字段缺失"兜底分支不再触发，
  // 判定回到与后端一致的口径：final_score IS NULL 即为待录入。
  normalized.pending = isPending(normalized)

  // ⚠️ 期末未录入时，`scores.score_total` 在库里**不是 NULL**，而是
  // "平时分折半"之类算出来的中间值（如 40），它不代表任何成绩。
  // 这里直接抹成 null：只标一个 `pending` 标志是不够的，
  // 只要有一个消费方忘了判 pending 就会把它当真实分数渲染成红色的 40 分，
  // 与真正挂科在视觉上无法区分 —— 这正是本项目最初那个 bug。
  // 平时分是真实存在的，保留。
  if (normalized.pending) normalized.scoreTotal = null

  return normalized
}

/** 批量归一化，输入可以是数组或 `{list}` 之类的分页载荷。 */
export function normalizeScoreRows(payload) {
  const list = Array.isArray(payload)
    ? payload
    : (payload && typeof payload === 'object'
        ? (Array.isArray(payload.list) ? payload.list
          : Array.isArray(payload.records) ? payload.records
          : Array.isArray(payload.items) ? payload.items
          : [])
        : [])
  return list.map(normalizeScoreRow)
}

/**
 * 成绩行的学期标签 —— 教师端各处显示"这条成绩是哪一学期"统一走这里。
 *
 * 行里只有 `semester`（数字字符串 `"5"`）和 `year`（2025），没有现成的标签，
 * 换算规则与 `/students/{uid}/gpa` 给的 `semesterLabel` 一致（见 `format.js`
 * 的 `semesterLabelOf` 注释，已用司明的数据核对过）。
 *
 * @param {object} row 归一化后的成绩行
 * @param {boolean} short true → `25秋`（列表用）；false → `2025-2026学年 第1学期`（详情用）
 * @returns {string} 学期取不到时返回「未标学期」，**不返回空串** —— 列表里会留一个洞
 */
export function scoreSemesterLabel(row, short = true) {
  const n = toNumber(row && row.semester)
  if (n === null) return '未标学期'
  const label = semesterLabelOf(n, row && row.year)
  if (!label) return '未标学期'
  return short ? shortSemester(label) : formatSemester(label)
}

/**
 * 按学期分组，返回 `[{semester, rows, avg, credits, gpa}]`，学期新的在前。
 * 学生成绩页与辅导员成绩跟踪都要这个形状。
 */
export function groupBySemester(rows) {
  const buckets = new Map()
  for (const row of rows) {
    const key = row.semester || '未知学期'
    if (!buckets.has(key)) buckets.set(key, [])
    buckets.get(key).push(row)
  }

  return [...buckets.entries()]
    .map(([semester, list]) => {
      // 平均分只统计已录入的科目，未录入混进来会把平均分拉成 0
      const scored = list.filter(r => !r.pending)
      const scores = scored.map(r => toNumber(scoreOf(r))).filter(n => n !== null)
      const avg = scores.length
        ? scores.reduce((a, b) => a + b, 0) / scores.length
        : null

      // 学分加权绩点，与后端 GPA_EXPR 同构：Σ(绩点×学分)/Σ学分
      let num = 0
      let den = 0
      for (const r of scored) {
        const gp = toNumber(r.gradePoint)
        const cr = toNumber(r.credits)
        if (gp === null || cr === null) continue
        num += gp * cr
        den += cr
      }

      return {
        semester,
        rows: list,
        total: list.length,
        scored: scored.length,
        pending: list.length - scored.length,
        avg,
        credits: list.reduce((sum, r) => sum + (toNumber(r.credits) || 0), 0),
        gpa: den > 0 ? num / den : null
      }
    })
    // 学期新在前。**不能直接用字符串比较** —— "10" < "9"，
    // 一旦出现第 10 学期（五年制/延毕）顺序就会乱。可解析成数字时按数字比。
    .sort((a, b) => {
      const na = toNumber(a.semester)
      const nb = toNumber(b.semester)
      if (na !== null && nb !== null) return nb - na
      return String(b.semester).localeCompare(String(a.semester))
    })
}

/**
 * 全量成绩汇总 —— **口径必须与后端 `ScoreCaliber` 一致**。
 *
 * ⚠️ 两步的**顺序不能反**，这是本项目最容易算错的地方：
 *
 *   1. 先滤掉"期末未录入"（`final_score IS NULL`）的行
 *   2. 再按课程去重，取学期号最大的那一条
 *
 * 反过来做（先去重、再看最后一条是不是待录入）会让同一门课第 6 学期的
 * "待录入"行盖掉第 5 学期那条**真实存在**的成绩。司明的实测数据上，
 * 反序会把「挂 3 门 / 已通过 17 学分 / 绩点 1.62」算成
 * 「挂 1 门 / 已通过 10 学分」，而首页接口给的是前者 —— 一眼就能看出对不上。
 *
 * 已用 `/students/scores/27`（29 行）与 `/students/dashboard/27` 逐项核对：
 * 滤掉 4 条待录入后按课程取学期最大 → 8 门课；
 * 挂科 = 课1(56.5) / 课3(47) / 课6(40) = 3；
 * 已通过学分 = 3+4+3+3+4 = 17；绩点 = 45.4/28 = 1.62。
 *
 * @param {Array} rows `normalizeScoreRow` 之后的行
 * @returns {{totalCourses:number, passed:number, failed:number, pendingCourses:number,
 *            passedCredits:number, gradedCredits:number, gpa:number|null, avg:number|null}}
 */
export function summarizeScores(rows) {
  const list = rows || []
  const keyOf = row => row.courseId ?? row.courseName ?? `row-${row.id}`

  /** 按课程去重，保留学期号最大的一行。 */
  const latestByCourse = source => {
    const map = new Map()
    for (const row of source) {
      const key = keyOf(row)
      const prev = map.get(key)
      if (!prev) {
        map.set(key, row)
        continue
      }
      const cur = toNumber(row.semester)
      const old = toNumber(prev.semester)
      // 学期号取不到时保留先到的（接口返回顺序通常已是旧的在前）
      if (cur !== null && old !== null && cur > old) map.set(key, row)
    }
    return [...map.values()]
  }

  // 第 1 步：只留有真实总评的行（这一步必须在去重之前）
  const recorded = list.filter(r => !r.pending && toNumber(scoreOf(r)) !== null)

  // 第 2 步：按课程取最新
  const courses = latestByCourse(recorded)

  // 另有口径之外但值得展示的数：最新一次仍是"待录入"的课程有几门。
  // 它和 courses 会重叠（一门课可以既有历史成绩、本学期又还没录入），
  // 所以单独返回，不去动上面那些与后端对齐的数。
  const pendingCourses = latestByCourse(list).filter(r => r.pending).length

  let passed = 0
  let failed = 0
  let passedCredits = 0
  let gradedCredits = 0
  let gpaNum = 0
  let gpaDen = 0
  const scoredList = []

  for (const row of courses) {
    const credits = toNumber(row.credits) || 0
    const score = toNumber(scoreOf(row))
    gradedCredits += credits
    scoredList.push(score)

    if (score < PASS_LINE) {
      failed += 1
    } else {
      passed += 1
      passedCredits += credits
    }

    // 绩点优先用后端写好的 grade_point；缺了才用 calcGpa 折算
    const gp = toNumber(row.gradePoint)
    const point = gp === null ? toNumber(calcGpa(score)) : gp
    if (point !== null && credits > 0) {
      gpaNum += point * credits
      gpaDen += credits
    }
  }

  return {
    totalCourses: courses.length,
    passed,
    failed,
    pendingCourses,
    passedCredits,
    gradedCredits,
    gpa: gpaDen > 0 ? gpaNum / gpaDen : null,
    avg: scoredList.length
      ? scoredList.reduce((a, b) => a + b, 0) / scoredList.length
      : null
  }
}
