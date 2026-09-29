/**
 * 教师端「学生名册」。
 *
 * 它存在的原因只有一个：**把学号换成 `student_profile.id`**。
 *
 * `/teachers/students/{teacherId}` 与 `/teachers/classes/{classId}/students`
 * 返回的行是 `{studentId: "2023020616"（**学号字符串**）, studentName}` ——
 * **没有 profile id**。而"按学生看成绩/看学分预测"要的 `student_id` 恰恰是
 * profile id（`/scores/student/analyze?student_id=`、`/students/{id}/credit-prediction`）。
 * 拿学号去查不会报错，只会查空，页面表现成"这个学生没有数据"。
 *
 * 唯一**同时**给出两者的接口是 `/teachers/scores/by-class`：它的行里
 * `studentId` = profile id、`studentNo` = 学号。所以名册从它来。
 *
 * 副作用必须显式处理：**只有产生过成绩的学生才在名册里**。班里没有成绩的
 * 学生由 `getClassStudents` 补成"暂无成绩记录"的一组单独列出，不能静默丢掉 ——
 * 教师会以为学生不在自己班上。
 */
import { ref } from 'vue'
import { teacherAPI } from '@/services/api.js'
import { normalizeScoreRow, summarizeScores } from '@/utils/score-normalize.js'
import { toNumber } from '@/utils/score.js'

/**
 * 把成绩流水按学生分组。
 *
 * **不做去重**：`score_record` 是"每次录入一行"的流水账，同一门课可以有多个学期
 * 的行（司明在高等数学上有 3 行）。行全留着给详情展示，另外用
 * `summarizeScores`（先滤待录入、再按课程取学期最大）算出口径与后端一致的那几个数。
 *
 * @param {Array} rows `/teachers/scores/by-class` 的原始行
 * @returns {Array} `[{profileId, name, studentNo, className, rows, ...汇总}]`，挂科多的在前
 */
export function groupByStudent(rows) {
  const map = new Map()

  for (const raw of rows || []) {
    const row = normalizeScoreRow(raw)
    if (row.studentId === null || row.studentId === undefined) continue
    const key = String(row.studentId)

    if (!map.has(key)) {
      map.set(key, {
        profileId: row.studentId,
        name: row.studentName || '未知学生',
        studentNo: row.studentNo || '',
        className: row.className || '',
        rows: []
      })
    }
    const stu = map.get(key)
    stu.rows.push(row)
    // 同一学生的行里 studentNo 可能只有部分有值（没 join 到的那条就是 null）
    if (!stu.studentNo && row.studentNo) stu.studentNo = row.studentNo
    if (!stu.className && row.className) stu.className = row.className
  }

  return [...map.values()]
    .map((stu) => {
      // ⚠️ by-class 的行**没有 credits 字段**，所以 summarizeScores 里的
      // 学分与绩点必然算不出来（den = 0 → null）。页面只能显示
      // 门数/挂科数/均分这几项，**不要**显示"已获得学分"——那会恒为 0。
      const sum = summarizeScores(stu.rows)
      const scored = stu.rows.filter(r => !r.pending)
      const nums = scored.map(r => toNumber(r.scoreTotal)).filter(n => n !== null)
      return {
        ...stu,
        failed: sum.failed,
        passed: sum.passed,
        totalCourses: sum.totalCourses,
        pendingCourses: sum.pendingCourses,
        avg: sum.avg,
        lowest: nums.length ? Math.min(...nums) : null
      }
    })
    // 挂科多的在前、其次均分低的：名单的第一屏应该是需要关注的人
    .sort((a, b) => (b.failed - a.failed) || ((a.avg ?? 999) - (b.avg ?? 999)))
}

/**
 * @param {string|number} userId `users.id`（教师端绝大多数接口都用它）
 */
export function useTeacherRoster(userId) {
  const classes = ref([])
  const roster = ref([])
  /** 班里有学籍、但一条成绩都没有的学生：`[{studentNo, studentName}]` */
  const noScore = ref([])
  const className = ref('')
  const loading = ref(false)

  /** 班级列表。带学生的排前面，0 人班垫底。 */
  async function loadClasses() {
    const list = await teacherAPI.getMyClasses(userId).catch((e) => {
      console.error('[teacher roster] 班级列表失败:', e)
      return []
    })
    classes.value = (Array.isArray(list) ? list : [])
      .slice()
      .sort((a, b) => toNumber(b.studentCount) - toNumber(a.studentCount))
    return classes.value
  }

  /**
   * 取某个班的名册。
   *
   * 两个请求都发：`by-class` 给"有成绩的人"（带 profile id），
   * `classes/{id}/students` 给"全班学籍" —— 两者相减就是"暂无成绩"的人。
   * 学籍那条失败也不影响主流程，所以各自 catch。
   */
  async function selectClass(name) {
    className.value = name || ''
    roster.value = []
    noScore.value = []
    if (!name) return
    loading.value = true
    try {
      const [scoreRows, enrolled] = await Promise.all([
        teacherAPI.getScoresByClass(name, userId).catch((e) => {
          console.error('[teacher roster] 班级成绩失败:', e)
          return []
        }),
        loadEnrolled(name)
      ])

      roster.value = groupByStudent(scoreRows)

      const known = new Set(roster.value.map(s => String(s.studentNo || '')).filter(Boolean))
      noScore.value = enrolled.filter(s => !known.has(String(s.studentId || '')))
    } finally {
      loading.value = false
    }
  }

  async function loadEnrolled(name) {
    const cls = classes.value.find(c => c.name === name)
    if (!cls) return []
    const list = await teacherAPI.getClassStudents(cls.id).catch((e) => {
      console.error('[teacher roster] 班级学籍失败:', e)
      return []
    })
    return Array.isArray(list) ? list : []
  }

  return { classes, roster, noScore, className, loading, loadClasses, selectClass }
}
