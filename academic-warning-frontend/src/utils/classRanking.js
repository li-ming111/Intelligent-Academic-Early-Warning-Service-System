/**
 * 班级排行榜响应的规范化。
 *
 * 背景（2026-10-01 第二十一轮）：`/students/class-members/{uid}` 与
 * `/students/class-ranking/{uid}` 这两个端点**都不返回数组** ——
 *   - `/class-members/{uid}` 实现里是 `return getClassInfo(uid)`，回的是**本人**的
 *     班级摘要对象（`{classIdentifier, classId, className, studentNo, name, rank,
 *     totalStudents, rankInClass}`），名字像"同学名单"但它不是；
 *   - `/class-ranking/{uid}` 回的是 `{studentId, classId, semester, rank, total, list[]}`，
 *     全班同学在 `list` 里（行字段是 `profileId / studentNo / studentName / className /
 *     gpa / gradedCredits`）。
 * 学生端两个页面都写成 `Array.isArray(response) && response.length > 0` —— 恒为假，
 * 于是页面永远"暂无数据"，而真正拿到的那份数据被直接丢掉。
 * 小程序端早已按这个真实形状取值（见 `pages/student/class-ranking.vue`），这里对齐。
 *
 * 这个文件同时是 PC 两个页面（ClassMembers / ClassRanking）的唯一映射点，
 * 免得同一套字段名在两边各写一遍再各自漂移。
 */

import { toNumber } from './score'

/**
 * 把 `/students/class-ranking/{uid}` 的响应转成页面用的成员数组。
 *
 * @param {object} payload 已解包的响应体 `{studentId, rank, total, list[]}`
 * @returns {Array<{id, name, studentId, className, gpa, credits, rank, isCurrent}>}
 *          按后端给的顺序（`ORDER BY gpa DESC, sp.id`）返回；`rank` 并列同名次。
 */
export function toClassMembers(payload) {
  const list = payload && Array.isArray(payload.list) ? payload.list : []
  // `studentId` 在后端装的是**请求者自己的 profileId**（见 StudentServiceImpl.rankingResult），
  // 不是学号字符串。用它标出"你"。
  const meProfileId = payload && payload.studentId != null ? String(payload.studentId) : null

  // 名次口径与后端 StudentServiceImpl 的 calcClassRank 一致：GPA **严格**高于该生的人数 + 1，
  // 即并列同名次。后端那个方法没有出现在这个响应里，所以在前端按同一规则现算。
  const gpaDesc = list
    .map(r => toNumber(r.gpa))
    .filter(v => v !== null)
    .sort((a, b) => b - a)
  const rankOf = (gpa) => {
    if (gpa === null) return null
    const i = gpaDesc.findIndex(v => v === gpa)
    return i < 0 ? null : i + 1
  }

  return list.map(r => {
    const gpa = toNumber(r.gpa)
    return {
      id: r.profileId,
      name: r.studentName || '未知',
      studentId: r.studentNo || '',
      className: r.className || '',
      gpa,
      // gradedCredits = 计入 GPA 的课程学分（ScoreCaliber.STUDENT_GPA 的 graded_credits）。
      // 没有成绩的同学不会出现在这个列表里（后端按 gpa IS NOT NULL 过滤）。
      credits: toNumber(r.gradedCredits),
      rank: rankOf(gpa),
      isCurrent: meProfileId !== null && String(r.profileId) === meProfileId,
    }
  })
}

/** GPA 展示文本：取两位小数；没有 GPA 显示 '—'（不写 0.00，那会被读成"绩点为 0"）。 */
export function fmtGpa(gpa) {
  const n = toNumber(gpa)
  return n === null ? '—' : n.toFixed(2)
}

/** 学分展示文本；同上，缺值不写 0。 */
export function fmtCredits(credits) {
  const n = toNumber(credits)
  return n === null ? '—' : String(n)
}
