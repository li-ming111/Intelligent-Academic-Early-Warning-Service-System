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
 *          按后端给的顺序（`ORDER BY gpa DESC, sp.id`）返回；`rank` 取后端列表位置
 *          （脱敏只清空 GPA，不改变顺序）。
 */
export function toClassMembers(payload) {
  const list = payload && Array.isArray(payload.list) ? payload.list : []
  // `studentId` 在后端装的是**请求者自己的 profileId**（见 StudentServiceImpl.rankingResult），
  // 不是学号字符串。用它标出"你"。
  const meProfileId = payload && payload.studentId != null ? String(payload.studentId) : null
  const selfRank = payload && typeof payload.rank === 'number' ? payload.rank : null

  // 名次取后端列表位置（rankingResult 就是下标 + 1），列表按**真实 GPA** 降序。
  // 不能用脱敏后的 gpa 重算：隐私遮蔽会把同学的 gpa 置空，重算会让本人变成第 1
  // （与响应顶层的 rank 冲突），被遮蔽的同学也会跟着并列第 1。
  return list.map((r, i) => {
    const gpa = toNumber(r.gpa)
    const isCurrent = meProfileId !== null && String(r.profileId) === meProfileId
    return {
      id: r.profileId,
      name: r.studentName || '未知',
      studentId: r.studentNo || '',
      className: r.className || '',
      gpa,
      // gradedCredits = 计入 GPA 的课程学分（ScoreCaliber.STUDENT_GPA 的 graded_credits）。
      // 没有成绩的同学不会出现在这个列表里（后端按 gpa IS NOT NULL 过滤）。
      credits: toNumber(r.gradedCredits),
      // 后端列表本身按真实 GPA 排序，脱敏只清空数值、不改变顺序；
      // 自己的名次直接采用响应顶层的 rank，其余行取列表位置。
      rank: isCurrent && selfRank !== null ? selfRank : i + 1,
      isCurrent,
      // 后端按 `student_profile.privacy_level` 遮蔽了这位同学的成绩（等级不是「公开」）。
      // 行还在（名次与总人数要对得上），只是 gpa/credits 被置空并打了这个标记，
      // 页面据此显示「未公开」而不是一个像出错的 '—'。
      gpaHidden: r.gpaHidden === true,
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
