// API服务模块 - 完整版（对齐PC端所有功能）
//
// 注意：baseURL / token 注入 / 401 登出已全部收敛到 services/request.js，
// 本文件只负责"路径 + 方法"的声明，不再自己拼 uni.request。
// 这样 H5 才能走 vite 代理（直接打 8076 会被 CORS 拦），
// 401 也不会并发触发多次 reLaunch。

import { request as httpRequest } from './request.js'

class ApiClient {
  // ========== 通用请求 ==========
  async request(endpoint, options = {}) {
    try {
      return await httpRequest(endpoint, options)
    } catch (e) {
      console.error(`[API] ${options.method || 'GET'} ${endpoint}:`, e)
      throw e
    }
  }

  _get = (url, params) => this.request(url, { params })
  _post = (url, data) => this.request(url, { method: 'POST', data })
  _put = (url, data) => this.request(url, { method: 'PUT', data })
  _del = (url) => this.request(url, { method: 'DELETE' })
  _patch = (url, data) => this.request(url, { method: 'PATCH', data })

  // ========== 认证 ==========
  login = (username, password) => this._post('/auth/login', { username, password })
  logout = (userId, role) => this._post('/auth/logout', { userId, role })
  changePassword = (data) => this._post('/auth/change-password', data)
  registerStudent = (data) => this._post('/auth/register/student', data)
  registerTeacher = (data) => this._post('/auth/register/teacher', data)
  registerCounselor = (data) => this._post('/auth/register/counselor', data)
  getColleges = () => this._get('/auth/colleges')
  getMajorsByCollege = (collegeId) => this._get(`/auth/majors?collegeId=${collegeId}`)
  getClasses = (collegeId) => {
    const u = `/auth/classes`;
    if (collegeId) u += `?collegeId=${collegeId}`;
    return this._get(u)
  }
  getAllCourses = () => this._get('/auth/courses')

  // ========== 学生端 ==========
  student = {
    getDashboard: (userId) => this._get(`/students/dashboard/${userId}`),
    getScores: (userId, semester) => this._get(`/students/scores/${userId}${semester ? '?semester=' + semester : ''}`),
    getWarnings: (userId) => this._get(`/students/warnings/${userId}`),
    getAssistancePlans: (userId) => this._get(`/students/assistance/${userId}`),
    getGPA: (id) => this._get(`/students/${id}/gpa`),
    getStudentInfo: (id) => this._get(`/students/${id}`),
    getStudentByUserId: (userId) => this._get(`/students/student-by-user/${userId}`),
    getSuggestions: (userId) => this._get(`/students/suggestions/${userId}`),
    getClassInfo: (id) => this._get(`/students/${id}/class-info`),
    // ⚠️ `/class-members/{uid}` 名字骗人：它实现里直接 `return getClassInfo(uid)`，
    // 返回的是**本人**的班级摘要（{className, studentNo, rank, totalStudents, rankInClass}），
    // **不是**同学名单。真正的同学名单在 `/class-ranking/{uid}` 的 `list[]` 里。
    getClassMembers: (userId) => this._get(`/students/class-members/${userId}`),
    /** 同 class-members，保留语义化别名便于阅读 */
    getClassInfoByUser: (userId) => this._get(`/students/class-members/${userId}`),
    getClassRanking: (userId) => this._get(`/students/class-ranking/${userId}`),
    // 通知（本角色唯一的收件箱）
    // ⚠️ 分页返回值里的 `total` **恒为 0**（`ip.getTotal()` 拿到了 0，但 `list` 有数据）。
    // 所以**不要**用 total 驱动分页/无限滚动，否则第一页就会显示"没有更多了"。
    // 用法：给定一个够大的 pageSize 一次取回，页面上不做分页控件。
    getNotifications: (userId, page = 1, size = 50) => this._get(`/students/notification-center/${userId}/list?page=${page}&pageSize=${size}`),
    getUnreadNotifications: (userId) => this._get(`/students/notification-center/${userId}/unread`),
    getUnreadCount: (userId) => this._get(`/students/notification-center/${userId}/unread-count`),
    // ⚠️ `POST /notification-center/{id}/mark-read` 是**纯桩**：
    // Controller 里 `return Map.of("success", true, ...)`，一行库都不写。
    // 用它会出现"点了已读、退出再进又是未读"，而且不报错，很难查。
    // 单条已读必须走 batch 接口（它真的调 markBatchRead）。
    markBatchRead: (userId, ids) => this._post(`/students/notification-center/${userId}/mark-batch-read`, ids),
    /** 单条标记已读 —— 内部复用 batch 接口，见上 */
    markRead: (userId, id) => this._post(`/students/notification-center/${userId}/mark-batch-read`, [id]),
    deleteNotification: (id) => this._post(`/students/notification-center/${id}/delete`),
    clearReadNotifications: (userId) => this._post(`/students/notification-center/${userId}/clear-read`),
    // 消息：**已删除**。
    // `/students/messages/{uid}` 的实现是 `notificationMapper.selectByUserId(studentId)`，
    // 与 `/notification-center/{uid}/unread` 读的是**同一张 notifications 表**，
    // 实测两者返回完全相同的 id 与字段（都是 id=1001/1002，warningId=395）。
    // 做成两个页面会在答辩现场被问"这两个有什么区别"，而答案是"没有"。
    // 故收敛为 notification-center 一个收件箱。
    // 另：`POST /students/messages`（发消息）和 `/messages/{id}/mark-read` 在
    // StudentController 里根本不存在，旧的 sendMessage / markMessageRead 必 404，一并删。
    // 申诉
    // 提交体的 key 叫 `studentId`，但值必须是 **users.id**：后端在
    // StudentServiceImpl.submitAppeal 里自己 selectByUserId 换成 profile id。
    // 传 profile id 会得到 {error:"学生档案不存在"}（HTTP 仍是 200）。
    submitAppeal: (data) => this._post('/students/appeals/submit', data),
    getAppeals: (studentId) => this._get(`/students/appeals/${studentId}/list`),
    getAppealStatistics: (studentId) => this._get(`/students/appeals/${studentId}/statistics`),
    // 撤销的 query 参数**名叫 studentId**，但服务端当 userId 用（resolveStudentProfileId）。
    withdrawAppeal: (appealId, userId) => this._post(`/students/appeals/${appealId}/withdraw?studentId=${userId}`),
    // 对标
    // ⚠️ 路径参数名叫 studentId，但**必须传 users.id**（司明=27，不是 profile id 3）。
    // 传 profile id 不会报错 —— 接口静默返回 `{}` / `[]`，页面会表现成"没有对标数据"。
    // 实测：/benchmark/3/latest → {} ；/benchmark/27/latest → 完整数据。
    // 同一坑也适用于 /students/{i}、/students/{i}/gpa、/students/{i}/class-info。
    getLatestBenchmark: (userId) => this._get(`/students/benchmark/${userId}/latest`),
    getBenchmarkHistory: (userId) => this._get(`/students/benchmark/${userId}/history`),
    getBenchmarkProgress: (userId) => this._get(`/students/benchmark/${userId}/progress-report`),
    /** 返回 {studentId, classId, total, rank, semester, list:[{profileId,studentName,studentNo,gpa,gradedCredits}]} */
    getBenchmarkClassRanking: (userId) => this._get(`/students/benchmark/${userId}/class-ranking`),
    getBenchmarkMajorRanking: (userId) => this._get(`/students/benchmark/${userId}/major-ranking`),
    // ⚠️ `/benchmark/{i}/semester` 实测 **500**（NPE），不要接进 UI。
    // 帮扶评价
    // ⚠️ `/evaluations/{i}/list` 的 `{i}` 是 **student_profile.id**（司明=3），不是 users.id。
    // 它内部 SQL 是 `JOIN assistance_plans ap ON ap.student_id = ?`，而
    // `assistance_plans.student_id` 存的就是 profile id。传 users.id 会静默返回空数组。
    // （对比：`/assistance-plans/{i}` 同一份数据却传 users.id。同名不同源，务必看清。）
    getEvaluations: (profileId) => this._get(`/students/evaluations/${profileId}/list`),
    // ⚠️ 提交体只认四个键，名字写错会被**静默丢弃**（同一个坑在 submitAppeal 的 description 上）：
    //   { planId, counselorId, score, content }
    // 评分叫 `score` 不叫 rating；**没有** studentId —— 学生身份靠 planId 反查关联。
    submitEvaluation: (data) => this._post('/students/evaluations/submit', data),
    // 设置：走 /users/{uid}/settings。
    // `/students/settings/{uid}` 2026-09-30 之前是纯桩（PUT 直接 `return {success:true}`
    // 不写库、GET 返回写死的字面量），已补成**同一张表 user_settings 的别名**
    // —— 两条路由现在读写同一行、实体同一个，用哪条都不会"改完退出再进变回去"。
    // 这里仍用规范入口，因为教师/辅导员端也走它，路径统一好记。
    getSettings: (userId) => this._get(`/users/${userId}/settings`),
    updateSettings: (userId, data) => this._put(`/users/${userId}/settings`, data),
    /** 账号本身的信息（username/name/email/phone/role/status/createdAt）。
        与 `/students/student-by-user/{uid}` 互补：那个给学籍（班级/专业/入学年），这个给账号。 */
    getUserInfo: (userId) => this._get(`/users/info/${userId}`)
  }

  // ========== 教师端 ==========
  //
  // 三条贯穿本角色的约定（2026-09-29 读 controller / mapper / 活库核实，
  // 30 个 GET 全部实测 code=200，写路径另做了一轮真库往返验证）：
  //
  // 1. **`teacherId` 一律是 `users.id`**（于凤海=29）。
  //    `classes.teacher_id`、`courses.teacher_id`、`score_record.teacher_id`、
  //    `feedback.teacher_id`、`teacher_class_application.teacher_id` 存的都是 users.id。
  //    所以本角色多数接口**直接用 useUser().userId**，不需要 profile 层 id。
  //    唯一的例外是 `GET /teachers/{id}`（教师档案详情），那里是 `teacher_profile.id`（1）。
  //
  // 2. **`studentId` 在本角色有两个含义，看接口分**：
  //    - `/scores/student/analyze?student_id=`、`/students/{id}/credit-prediction`
  //      → **`student_profile.id`**（司明=3）
  //    - `/teachers/students/{id}`、`/classes/{id}/students` 返回的 `studentId`
  //      → **学号字符串**（"2023020616"）
  //    要跳详情必须先把学号换成 profile id；最省事的换法是拿
  //    `getScoresByClass()` 的行（它同时给了 `studentId`=profile id 和 `studentNo`=学号）。
  //
  // 3. **`score_record` 是"每次录入一行"的流水账，不是"一人一行"。**
  //    司明在高等数学上就有 3 行（第 3/4/5 学期 87 / 71.5 / 56.5）。
  //    所以：成绩**列表/流水**页不做去重（26 行就是 26 行），
  //    而**统计**口径由后端 `ScoreCaliber` 负责（先滤待录入、再按课程取学期最大），
  //    `/analysis`、`/scores/analyze`、`/credit-prediction` 给的都是这套口径的结果。
  //    两边数字不同是正常的，不要把统计数拿去跟流水行数对。
  teacher = {
    /**
     * 首页概览。
     * `{teacherName, totalClasses, totalStudents, totalCourses, pendingRequests, pendingFeedbacks}`。
     *
     * ⚠️ `pendingFeedbacks` 后端**写死 0**，不要拿它渲染"待回复反馈"卡片
     *    （真实条数见 `/todos`）。
     * ⚠️ `pendingRequests` 名字骗人：它数的是**全部**申请记录，不是待审核的
     *    （实测 6，而 6 条的状态是 3/2/1/1/1/2，一条待审核都没有）。
     *    待办一律以 `/todos` 为准。
     */
    getDashboard: (userId) => this._get(`/teachers/dashboard/${userId}`),

    /**
     * 待办事项。`[{id, type:'feedback'|'warning', title, count}]`，
     * **只返回 count > 0 的项**（后端已按真实数据算好：未回复反馈、未处理预警）。
     * 这是本角色最可信的待办来源。
     */
    getTodos: (teacherId) => this._get(`/teachers/todos/${teacherId}`),

    /** 教师档案（`teacher_profile.id`，不是 users.id）。`{id,userId,name,collegeId,title}`。 */
    getProfile: (profileId) => this._get(`/teachers/${profileId}`),
    /** 全校教师列表（含 `id`=profile.id 与 `userId`=users.id 的对应关系）。 */
    getTeachers: () => this._get('/teachers/list'),

    // —— 课程 ——
    /** 我的课程（`teacher_id` 传 users.id）→ Course 实体数组。 */
    getCourses: (teacherId) => this._get(`/teachers/courses${teacherId ? '?teacher_id=' + teacherId : ''}`),
    /** 全校课程（不带 teacher_id 时返回所有教师的课）。 */
    getAllCourses: () => this._get('/teachers/courses'),
    /** 更新课程。体 = Course 实体；`id` 由路径给，body 里不用重复。 */
    updateCourse: (id, data) => this._put(`/teachers/courses/${id}`, data),
    /** **纯桩**：只回 `{status:'已推荐', message:'课程推荐成功，学生将收到通知'}`，不写库、不发通知。不要接进 UI。 */
    recommendCourse: (data) => this._post('/teachers/courses/recommend', data),

    // —— 成绩（流水） ——
    /**
     * 某门课的成绩流水行（**不去重**，一门课 24 人 + 司明的历史行 = 26 行）。
     * 行里 `studentId` = **profile id**，`score`/`scoreTotal` 同值，
     * 但 `courseName` 与 `studentNo` 是 **null**（这条 SQL 没 join courses/student_profile），
     * 要显示课程名请用下面 by-class 那条，或直接用页面上选中的课程名。
     */
    getScores: (courseId) => this._get(`/teachers/scores?course_id=${courseId}`),
    /**
     * 按班级名取成绩（join 过，`courseName` 与 `studentNo` 都有值）。
     * 同一个学生的多门课都会返回，也不去重。
     */
    getScoresByClass: (className, teacherId) =>
      this._get(`/teachers/scores/by-class?class_name=${encodeURIComponent(className)}&teacher_id=${teacherId}`),
    /** 新增成绩。体：`{teacherId(users.id), scores:[{studentId(profile), courseId, score, ...}]}`。**真落库**。 */
    saveScores: (data) => this._post('/teachers/scores', data),
    /** 导入成绩，与 saveScores 同一实现、只是提示语不同。体同上。 */
    importScores: (data) => this._post('/teachers/scores/import', data),
    /**
     * 改分。**真落库**（`score_record` + 镜像 `scores` + 写一条 `score_audit_log`）。
     * 体：`{score, remark?, modifiedBy?}`。
     * 已实测：写完回读一致，且**不会**抹掉 `regular_score`/`final_score`
     * （早先这里因为审计日志违反非空约束而整体回滚，接口 500、一分没改，已修）。
     */
    updateScore: (id, data) => this._put(`/teachers/scores/${id}`, data),
    /** 删除成绩。**真落库**。 */
    deleteScore: (id) => this._del(`/teachers/scores/${id}`),
    /** 批量删除。体：`{ids:[...]}`。 */
    batchDeleteScores: (ids) => this._post('/teachers/scores/batch-delete', { ids }),

    // —— 成绩分析（口径统一，统计数只认这几个） ——
    /**
     * 课程成绩分析。`/analysis` 与 `/scores/analyze` **是同一份实现**，
     * 返回：`{courseId, courseName, totalStudents, gradedCount, averageScore, maxScore,
     *        minScore, standardDeviation, passRate, excellentRate, failedCount,
     *        highScoreCount, mediumScoreCount, lowScoreCount, scoreDistribution:{'90+','80-89','70-79','60-69','<60'}}`
     * ⚠️ `passRate` / `excellentRate` 是 **0~1 小数**（0.88），不是百分数。
     * ⚠️ `totalStudents` 是**去重后的人数**（24），不是流水行数（26）。
     */
    getAnalysis: (courseId) => this._get(`/teachers/analysis?course_id=${courseId}`),
    /** 同上（同实现，路径不同）。 */
    getScoreAnalyze: (courseId) => this._get(`/teachers/scores/analyze?course_id=${courseId}`),
    /** 分数档位：`{labels:[5档], distribution:[5个数], byLabel:{档:数}, courseId}`。 */
    getCourseDistribution: (courseId) => this._get(`/teachers/courses/${courseId}/distribution`),
    /**
     * 该课的低分/异常学生行（score < 60 的那批），行形状同成绩流水。
     * 这是"异常学生名单"的**正确来源**。
     */
    getCourseAnomaly: (courseId) => this._get(`/teachers/courses/${courseId}/anomaly`),
    /**
     * ⚠️ **实测恒为 `[]`**，即使 `/courses/{id}/anomaly` 有数据。
     *    两个接口名字近、数据源不同，这个没查出东西来。**不要用**。
     */
    getAnomalies: (courseId) => this._get(`/teachers/scores/anomalies?course_id=${courseId}`),
    /** 班级层面汇总：`{classId, className, totalStudents, averageScore, passRate, excellentRate}`。 */
    getClassScoreAnalysis: (classId) => this._get(`/teachers/classes/${classId}/analysis`),

    /**
     * 单个学生在某门课上的分析。**`student_id` 是 profile id。**
     * {studentId, studentName, courseId, courseName, currentScore, averageScore,
     *  difference, rank, totalStudents, failedCount, level, warningLevel, comment}
     * ⚠️ 没有 `predictedScore`（早先那个是编的，已删）。没成绩时 code=404 + message。
     */
    getStudentScoreAnalyze: (profileId, courseId) =>
      this._get(`/teachers/scores/student/analyze?student_id=${profileId}&course_id=${courseId}`),

    /**
     * 学分预测。**参数是 `student_profile.id`。**
     * `{studentId, studentName, completedCredits, requiredCredits(120), remainingCredits,
     *   totalCourses, failedCourses, progressRate(0~1), trend:'需关注'|'良好'}`
     * 与 student-service 用同一套 `ScoreCaliber` 口径，数字对得上。
     */
    getCreditPrediction: (profileId) => this._get(`/teachers/students/${profileId}/credit-prediction`),

    /**
     * 学生选课/成绩矩阵。带 `teacherId` 时按 (学生,课程) 去重；
     * 带 `courseId` 时**不去重**（司明会出现两行）并多给一个 `status:'已选课'`。
     * ⚠️ 那个 `status` 是**拼出来的**（库里没有选课关系表），别当真实字段用。
     */
    getEnrollments: (teacherId) => this._get(`/teachers/enrollments?teacherId=${teacherId}`),
    getCourseEnrollments: (courseId) => this._get(`/teachers/enrollments?courseId=${courseId}`),

    // —— 预警（**只读**） ——
    /**
     * 我的班级的预警。行里 `warningLevel` 与 `status` 都是**中文**
     * （"中度"/"待处理"），一律过 `utils/warning.js` 的 `normalizeWarning` 再用。
     * `className` 后端已查好，可直接显示。
     * ⚠️ 教师端预警页**不提供处理按钮** —— 处理入口在辅导员端与学院管理员端。
     *    （2026-09-30 之前这里写的是"因为 `process` 是纯桩所以不做"；桩已经补成真实现，
     *     但小程序端仍**有意**不给入口，原因不再是"接口是假的"，而是它与辅导员端职责重叠。
     *     要加的话，接口本身已可用。）
     */
    getWarnings: (teacherId, status) => this._get(`/teachers/warnings/${teacherId}${status !== undefined && status !== null && status !== '' ? '?status=' + status : ''}`),
    /**
     * 处理预警，真落库（2026-09-30 起）。返回体的 `status/handledBy/handleResult/handledAt`
     * 都是**回读数据库**得到的值，不是后端本地拼的。
     * 入参兼容 `{handleResult, handledBy}` 与 PC 端的 `{measures, remark}`。
     */
    processWarning: (id, data) => this._post(`/teachers/warnings/${id}/process`, data),

    // —— 反馈 ——
    /** 反馈列表（体里有 `studentId:null`/`studentName:null`，学生姓名写在 `content` 里）。 */
    getFeedbacks: (teacherId) => this._get(`/teachers/feedbacks?teacherId=${teacherId}`),
    /** 同上（同数据，路径不同）。 */
    getFeedbackList: (teacherId) => this._get(`/teachers/feedbacks/${teacherId}/list`),
    /**
     * 回复反馈。**真落库**（`reply` + `status=1` + `update_time`）。
     * 体：`{reply}`。
     * ⚠️ 早先这里写的是 `PUT /teachers/feedbacks/{id}`，但真正存在的是 POST .../reply；
     *    而且旧 SQL 写了一个**不存在的列** `reply_time`，所以那条路一直是 500。
     */
    replyFeedback: (id, reply) => this._post(`/teachers/feedbacks/${id}/reply`, { reply }),

    /**
     * 消息中心 = 反馈的另一种投影（`{id,title:'学生反馈',category,type:'feedback',content,reply,status,createdAt,repliedAt}`）。
     * ⚠️ 按 `status` 判未读（0 未读 / 1 已读），**没有 `isRead` 字段**。
     */
    getMessages: (userId) => this._get(`/teachers/messages/${userId}`),
    /** 未读数。返回**裸数字**（不是 `{count}`）。 */
    getUnreadCount: (userId) => this._get(`/teachers/messages/${userId}/unread-count`),

    /**
     * 成绩修改审计，分页 `{total, size, page, list:[{...,courseName,oldScore,newScore,difference,reason,modifiedBy,modifiedAt}]}`。
     * ⚠️ 列表里的 `studentId` 是 **profile id**，但旧 PC 页把它标成了「学号」，
     *    小程序端要显示学号就用 `studentNo`（本接口没给）或干脆不显示学号。
     */
    getAuditLogs: (teacherId) => this._get(`/teachers/scores/audit/${teacherId}`),

    // —— 班级 ——
    /** 我带的班：`[{id, name, collegeId, majorId, studentCount}]`。 */
    getMyClasses: (teacherId) => this._get(`/teachers/class-management/my-classes?teacherId=${teacherId}`),
    /**
     * 班级花名册。行只有 `{studentId(**学号**), studentName}` 两列 —— 需要成绩/预警
     * 就得另配 `getScoresByClass`。`/teachers/students/{id}` 返回的形状完全一样。
     */
    getClassStudents: (classId) => this._get(`/teachers/classes/${classId}/students`),
    getStudents: (teacherId) => this._get(`/teachers/students/${teacherId}`),
    /** 可申请/可搜索的班级：`[{id, name, majorId, studentCount, hasTeacher}]`。 */
    searchClasses: (keyword) => this._get(`/teachers/class-management/search?keyword=${encodeURIComponent(keyword || '')}`),
    /** 我的申请记录：`[{id,classId,className,reason,status,applicationType,createTime,teacherName}]`。 */
    getClassRequests: (teacherId) => this._get(`/teachers/class-management/requests?teacherId=${teacherId}`),
    /**
     * 提交申请（**真落库**，status=0 待审核）。
     * 体：`{teacherId(users.id), classId, className, reason, applicationType}`，
     * `applicationType` 0=申请管理 / 1=申请解除。
     */
    applyClass: (data) => this._post('/teachers/class-management/apply', data),
    /**
     * 直接解除班级管理（**当场写库**：`classes.teacher_id` 置 NULL，并把已有申请置为已取消）。
     * 体：`{teacherId(users.id), classId}`。界面上必须是二次确认的危险操作。
     */
    cancelClass: (data) => this._post('/teachers/class-management/cancel', data),
    /** 某班的辅导员 id（`{counselorId}`，只有 id 没有名字）。班级没有辅导员时返回空。 */
    getCounselorByClass: (className) =>
      this._get(`/teachers/counselor/by-class?class_name=${encodeURIComponent(className)}`),

    /** 账号级接口（`/users/**` 不分角色，本角色页面只 import 自己这一个对象）。 */
    getUserInfo: (userId) => this._get(`/users/info/${userId}`),
    /** 设置：`PUT` 是**部分更新**（没传的字段保持不动，实测）。详见 admin 段落的说明。 */
    getSettings: (userId) => this._get(`/users/${userId}/settings`),
    updateSettings: (userId, data) => this._put(`/users/${userId}/settings`, data)
  }

  // ========== 辅导员端 ==========
  //
  // 三条贯穿本角色的约定（2026-09-29 读 controller / mapper / 活库核实）：
  //
  // 1. **`counselorId` 一律是 `users.id`**（司明=53）。`classes.counselor_id`、
  //    `counselor_class_management.counselor_id`、`counselor_class_application.counselor_id`、
  //    `communication_logs.counselor_id` 全是 users.id，所以本角色**不需要** profile 层 id。
  //    唯一的例外是 `counselor_profiles.counselor_id`，那是个业务编号字符串（`'C53'`），
  //    只被 `GET /counselors/{counselorId}` 用 —— 传数字 53 只会得到 404。
  //    辅导员资料请走 `getDashboard(uid).counselor`，那里返回完整的 CounselorProfile。
  //
  // 2. **`studentId` 在本角色是 `student_profile.id`**（司明=3），不是 users.id、也不是学号。
  //    `/counselors/students/{id}`、`/counselors/scores/student/{id}/trend`、
  //    `/counselors/students/{id}/communication-logs`、`assistance_plans.student_id` 都吃 profile id。
  //    而 `getStudents()` 返回的行里**三个 id 同时存在**，是最容易搞混的地方：
  //      `id` = profile.id（详情页要这个）/ `userId` = users.id / `studentId` = **学号字符串**
  //
  // 3. `/counselors/**` 由网关按 role=3 拦截（学生 token 打过去是 403），页面拿不到数据先看登录角色。
  counselor = {
    /**
     * 首页。
     * 返回 `{counselor, studentCount, warningCount, redWarnings, yellowWarnings, blueWarnings,
     *        classes[{id,name,collegeId,majorId,studentCount}], recentWarnings[]}`。
     *
     * ⚠️ 与学生首页相反：这里的 `redWarnings/yellowWarnings/blueWarnings` 是**真算出来的**
     *    （level>=3 / ==2 / <=1），可以直接用，不必像学生端那样绕开。
     * ⚠️ `recentWarnings` 是 AcademicWarning **实体**（camelCase：studentName/warningLevel/createdAt…），
     *    最多 5 条，且**没有 className** —— 实体里没这个字段，页面别去取。
     */
    getDashboard: (userId) => this._get(`/counselors/dashboard/${userId}`),
    /** 所带全部班级的学生。行 = Student 实体，见上文第 2 条的三个 id。 */
    getStudents: (counselorId) => this._get(`/counselors/students?counselor_id=${counselorId}`),
    /** ⚠️ 参数是 **profile id**。 */
    getStudentDetail: (profileId) => this._get(`/counselors/students/${profileId}`),
    /**
     * 更新学生档案。⚠️ 请求体就是 `student_profile` 那张表：
     * 能改的只有 name/gender/collegeId/majorId/classId（userId 与 studentId 也映射到列上，
     * 但改学号等于改身份，不要碰）。实体上的 `phone`/`email` 标了 `@TableField(exist = false)`，
     * **传了也不会落库**（通讯方式在 users 表，这里改不到）。
     */
    updateStudent: (profileId, data) => this._put(`/counselors/students/${profileId}`, data),

    /** 预警列表。`status` 传 '0' 待处理 / '1' 已处理，不传为全部。 */
    // 返回 `{id, studentId(profile), studentName, warningLevel(数字), description, status(数字),
    //        createdAt, className}` —— className 由后端按班级查好，可以直接显示。
    // 注意这里**没有** title / handleResult / handledAt：详情要做处理记录就得另找数据源，
    // 所以本角色的预警页是"列表 + 处理"，不做只读详情弹层。
    getWarnings: (counselorId, status) => this._get(`/counselors/warnings?counselor_id=${counselorId}${status ? '&status=' + status : ''}`),
    /** 处理单条预警。**真写库**：status→1、handled_at=now，并落 handleResult/handledBy。 */
    processWarning: (id, data) => this._post(`/counselors/warnings/${id}/process`, data),
    /**
     * 批量处理。**真写库**，但只把 status 置 1 与 handled_at 置 now ——
     * 它不接收 handleResult/handledBy，所以批量处理后那两条预警的处理结果是空的。
     * 界面上要如实体现这个差别（批量 = 快速销项，单条 = 带处理意见）。
     */
    batchProcessWarnings: (ids) => this._post('/counselors/warnings/batch-process', ids),

    /**
     * 帮扶计划列表。
     * ⚠️ 路径里的 `{counselorId}` **被后端完全忽略** —— 实现是 `assistancePlanService.list()`，
     *    返回**全库所有**辅导员的计划。当前库里只有 53 一份数据，所以看着是对的；
     *    一旦有第二名辅导员就会串数据。页面**不能**把它当成"我的计划"的证据，
     *    需要"仅自己"时必须再按 `counselorId` 客户端过滤一次（`assistance` 页就是这么做的）。
     */
    getCounselorPlans: (counselorId) => this._get(`/counselors/${counselorId}/assistance-plans`),
    /** ⚠️ 参数是 **profile id**（计划表 `student_id` 存 profile id）。 */
    getStudentPlans: (profileId) => this._get(`/counselors/students/${profileId}/assistance-plans`),
    /**
     * 创建帮扶计划。请求体 = AssistancePlan：`{counselorId, studentId, content, status, progress}`。
     * ⚠️ `counselorId` 传 **users.id**（53），与库里既有 4 行一致、也与其他所有按辅导员过滤的
     *    查询一致（`/analytics/assistance-completion` 就是按 users.id 过滤的）。
     *    别被删除接口的校验误导去传 profile id —— 那处校验本身是错的，见 `deleteAssistancePlan`。
     * ⚠️ `studentId` 是 profile id。表里**没有 title 列**，计划的"标题"只能由 content 首句推导。
     * ⚠️ `status` / `progress` 传什么都没用：`createPlan` 会强制写成 0 与 0，新计划一律"待开始 / 0%"。
     */
    createAssistancePlan: (data) => this._post('/counselors/assistance-plans', data),
    updateAssistancePlan: (id, data) => this._put(`/counselors/assistance-plans/${id}`, data),
    /**
     * ⚠️ **当前必然 403**（静态分析结论，非实测；因辅导员账号登录被权限策略拦住）。
     *    校验代码是 `!plan.getCounselorId().equals(profile.getId())` —— 左边 `assistance_plans.counselor_id`
     *    存的是 users.id（53），右边 `counselor_profiles.id` 是 1，永不相等，于是非管理员一律被拒。
     *    在它修好之前，界面不提供"删除帮扶计划"，改用「状态 / 进度」两个能落库的入口。
     */
    deleteAssistancePlan: (id) => this._del(`/counselors/assistance-plans/${id}`),
    /**
     * 改状态。库里的 status 是 **0 待开始 / 1 进行中 / 2 已完成**；
     * 后端能吃中文与英文别名（'待开始'/'进行中'/'已完成'、'pending'/'in_progress'/'completed'），
     * 直接传数字字符串最稳。
     */
    updatePlanStatus: (id, status) => this._patch(`/counselors/assistance-plans/${id}/status`, { status }),
    /**
     * 改进度。**0~100 的整数**，与 status 是两个字段，别再把百分比塞进 status。
     * ⚠️ 但后端会**顺带同步 status**：0→待开始、100→已完成、其余→进行中
     *    （`AssistancePlanServiceImpl.updatePlanProgress` 里写死的）。
     *    所以界面上只给一个"进度"控件就够了，再单独放一个状态下拉反而能造出
     *    "进度 50% 但状态已完成"这种自相矛盾的组合。
     */
    updatePlanProgress: (id, progress) => this._patch(`/counselors/assistance-plans/${id}/progress`, { progress }),

    /** ⚠️ 参数是 **profile id**。返回 CommunicationLog[]（counselorId/studentId/warningId/content/category/status/reply）。 */
    getStudentCommunicationLogs: (profileId) => this._get(`/counselors/students/${profileId}/communication-logs`),
    /** ⚠️ 这里的 `{counselorId}` 是 **users.id**（与 `/{cid}/assistance-plans` 相反）。 */
    getCounselorCommunicationLogs: (counselorId) => this._get(`/counselors/${counselorId}/communication-logs`),
    /** 新建沟通记录（**真写库**）。体：`{counselorId(users.id), studentId(profile), content, category, warningId?, status?, reply?}`。 */
    createCommunicationLog: (data) => this._post('/counselors/communication-logs', data),

    // —— 成绩跟踪 ——
    /** 一个班的成绩行。列名是 snake_case（score_total/course_name/grade_point/student_name…）。 */
    getClassScores: (classId, counselorId) => this._get(`/counselors/scores/class/${classId}?counselor_id=${counselorId}`),
    /** 某门课的分数档位分布：`{total, excellent, good, normal, pass, fail, average}`。 */
    getScoreDistribution: (courseId, counselorId) => this._get(`/counselors/scores/distribution/${courseId}?counselor_id=${counselorId}`),
    /**
     * 低分关注名单。阈值是 **70 分**（`ScoreCaliber.ATTENTION_LINE`），**不是挂科线 60** ——
     * 这条线捞的是"偏低但还没挂科"的学生。列名同 class scores。
     */
    getLowScoreStudents: (counselorId) => this._get(`/counselors/scores/low-score?counselor_id=${counselorId}`),
    /** ⚠️ 参数是 **profile id**，且这条**不折叠**多个学期，按学期正序返回，专供趋势图。 */
    getStudentScoreTrend: (profileId) => this._get(`/counselors/scores/student/${profileId}/trend`),

    // —— 班级管理 ——
    /** 我带的班：`[{id, name, collegeId, majorId, studentCount}]`。 */
    getMyClasses: (counselorId) => this._get(`/counselors/class-management/my-classes?counselorId=${counselorId}`),
    /** 同上的别名（路径不同、实现相同）。 */
    getClasses: (counselorId) => this._get(`/counselors/classes?counselor_id=${counselorId}`),
    getClassDetail: (classId) => this._get(`/counselors/classes/${classId}/detail`),
    /**
     * 班级学生。列：`{studentId(**学号字符串**), studentName, gender, phone}` ——
     * ⚠️ **没有 profile id**，所以从这个列表点进学生详情要先把学号换成 profile id
     * （用 `getStudents()` 的 `studentId → id` 建映射）。别拿这里的 studentId 直接当 profile id。
     */
    getClassStudents: (classId) => this._get(`/counselors/classes/${classId}/students`),
    /**
     * 各班预警对比：`[{classId, className, warningCount, pendingCount, highRiskCount, avgGpa}]`。
     * 班级维度的预警汇总用这个，**不要**用 `GET /counselors/classes/{classId}/warnings`。
     */
    getClassWarningCompare: (counselorId) => this._get(`/counselors/classes/warnings/compare?counselor_id=${counselorId}`),
    // ⚠️ **`GET /counselors/classes/{classId}/warnings` 恒为 0，不要接进 UI。**
    // 它把 `student_profile.student_id`（**学号字符串** "2023020616"）当成 `academic_warning.student_id`
    // （bigint 的 profile id）去等值匹配，类型一转换就永远不会相等；
    // 每班预警概览因此恒返回 {totalWarnings:0, pendingCount:0…}，且不报错。
    // 班级预警走上面的 getClassWarningCompare。

    /** 我的班级申请记录：`[{id, classId, className, reason, status, applicationType, createTime}]`。 */
    getClassRequests: (counselorId) => this._get(`/counselors/class-management/requests?counselorId=${counselorId}`),
    /** 可申请的班级（当前无辅导员的）。 */
    getClassesWithoutCounselor: () => this._get('/counselors/class-management/without-counselor'),
    searchClasses: (keyword) => this._get(`/counselors/class-management/search?keyword=${encodeURIComponent(keyword || '')}`),
    /**
     * 申请管理 / 解除管理（**真写库**，写 `counselor_class_application`，status=0 待审核）。
     * 体：`{counselorId(users.id), classId, className, reason, applicationType}`
     * —— `applicationType` 0=申请管理、1=申请解除；`className` **必须由前端带上**（后端原样入库，不反查）。
     */
    applyForClass: (data) => this._post('/counselors/class-management/apply', data),
    /**
     * 直接解除班级管理（**立刻写库**）。
     * ⚠️ 它跟 `applyForClass(type=1)` 是两条不同的路：apply 只是建一条待审核记录，
     *    而 cancel 会**当场**把 `classes.counselor_id` 置 NULL、并把已有申请置为已取消(3)，
     *    没有二次审核。界面上必须是"二次确认 + 明确后果"的按钮，不能跟"申请"并排。
     * 体：`{counselorId(users.id), classId}`。
     */
    cancelClass: (data) => this._post('/counselors/class-management/cancel', data),

    /**
     * 学分监控汇总。
     * `{totalStudents, gradedStudents, creditSufficient, creditInsufficient, insufficientRate,
     *   excellentClasses, lowClasses, classMonitors[{classId,className,studentCount,gradedStudentCount,
     *   creditAchievementCount,failStudentCount,achievementRate,avgScore}]}`
     *
     * ⚠️ 三个口径别混：
     *    - `totalStudents` = **花名册**人数（含无成绩的新生）
     *    - `gradedStudents` = **有成绩**的人数，是达标率的分母
     *    - `insufficientRate` = 挂科人数 / **有成绩人数**（不是 / 花名册）
     *    分母不同，所以 `creditInsufficient / totalStudents` 算出来的数**不等于** `insufficientRate`。
     */
    getCreditMonitor: (counselorId) => this._get(`/counselors/credit-monitor?counselor_id=${counselorId}`),
    /**
     * 学分不足学生明细（有挂科的人）。
     * 行：`{studentId(**学号**), profileId(profile id), studentName, className, score(已修学分),
     *      attemptedCredits(应修学分), failCount, riskLevel('red'|'yellow'), remarks}`。
     * ⚠️ 同一行里 `studentId` 是学号、`profileId` 才是跳详情要用的 id —— 这行是本角色最坑的一行。
     */
    getCreditInsufficientStudents: (counselorId, page = 1, size = 50) =>
      this._get(`/counselors/credit-insufficient?counselor_id=${counselorId}&page=${page}&size=${size}`),

    /**
     * "选课信息"。
     * ⚠️ 名字骗人：实现是遍历所带班级逐个 `getScoresByClassId` 再拼接，
     *    返回的是**成绩行**（列同 class scores），不是选课记录 ——
     *    库里没有课程选修关系表，能拿到的只有"有成绩"的学生-课程对。
     *    所以这一页按**课程**聚合（修读人数/均分/最低分/挂科数），
     *    跟按班级和学生看的 `scores` 页是两个视角，不是同一张表的重复。
     */
    getEnrollments: (counselorId) => this._get(`/counselors/enrollments?counselor_id=${counselorId}`),

    // —— 数据分析（六个真实聚合）——
    /**
     * 学分不足率（按人去重）：
     * `{totalStudents(有成绩人数), insufficientStudents(有挂科人数), insufficientRate(整数百分比)}`
     */
    getCreditInsufficientRate: (counselorId) => this._get(`/counselors/analytics/credit-insufficient?counselor_id=${counselorId}`),
    /**
     * 预警等级分布：`[{warning_level, count}]`
     * ⚠️ **全下划线**（和其他接口的 camelCase 不一致），页面上取 `warning_level`。
     */
    getWarningDistribution: (counselorId) => this._get(`/counselors/analytics/warning-distribution?counselor_id=${counselorId}`),
    /**
     * 处理效率：`{avg_handle_days}`（**下划线**）。
     * ⚠️ 库里 4 条预警的 `handled_at` 全为 NULL，后端于是兜底成 0 ——
     *    显示成"平均 0 天"会被误读成"秒处理"，要按"暂无已处理预警"呈现。
     */
    getHandlingEfficiency: (counselorId) => this._get(`/counselors/analytics/handling-efficiency?counselor_id=${counselorId}`),
    /** 班级达标率排名：`[{className, studentCount(有成绩), creditAchievementCount, achievementRate}]`。 */
    getCreditAchievementRanking: (counselorId) => this._get(`/counselors/analytics/credit-achievement-ranking?counselor_id=${counselorId}`),
    /**
     * 预警趋势：`[{month('YYYY-MM'), warning_level, count}]`，**下划线**，且**只取近 6 个月**。
     * ⚠️ 现有 4 条预警创建于 2026-09-21，落在窗口内，所以有数据；
     *    但它是按创建时间切的，历史预警过 6 个月就会从图上消失（不是"清零"，是出窗）。
     */
    getWarningTrend: (counselorId) => this._get(`/counselors/analytics/warning-trend?counselor_id=${counselorId}`),
    /**
     * 帮扶完成率：`{totalPlans, completedPlans, completionRate}`。
     * ⚠️ "完成"只认 `status == 2`；库里 4 条计划是 0/1/1/1，所以完成率是 0%。
     *    这是真实状态（没有一条被标成已完成），不是 bug —— 页面上要说清"已完成 = 状态已置为已完成"。
     */
    getAssistanceCompletion: (counselorId) => this._get(`/counselors/analytics/assistance-completion?counselor_id=${counselorId}`),

    // `notifyStudents`（POST /counselors/students/notify）**已删除**：
    // 实现只 `log.info` 后返回 {success:true}，一行库都不写、一条通知都不发。
    // 同理 `/counselors/notifications/{history,templates,weekly-count}` 三个都是纯桩
    // （history 恒空、templates 写死 3 条、weekly-count 恒 0），故通知中心整页不做。
    // 要与学生沟通请用 `createCommunicationLog`（真写库）或预警处理意见。

    /**
     * 账号级接口：`/users/**` 不分角色，随便什么 token 都能读，
     * 所以这里和 student 块里的是同两个地址。刻意重复一份，
     * 是为了让各角色的页面**只 import 自己那一个 API 对象**，不必跨端借接口。
     *
     * ⚠️ 辅导员资料**不能**走 `getCounselor(counselorId)` 之类的路径参数接口：
     *    一个人一个接口就给错了 —— 正确来源是 `getDashboard(userId).counselor`。
     */
    getUserInfo: (userId) => this._get(`/users/info/${userId}`),
    /** 设置：`PUT` 是**部分更新**，只发改动的那一个不会重置其余四个（实测）。 */
    getSettings: (userId) => this._get(`/users/${userId}/settings`),
    updateSettings: (userId, data) => this._put(`/users/${userId}/settings`, data)
  }

  // ========== 管理员端 ==========
  //
  // 本角色的接口在 2026-09-29 做过一轮**逐条实测**（testsprite/local-check/
  // admin-pages-probe.mjs，输出 admin-pages-probe.txt），写路径另有一轮真库往返
  // （admin-write-probe.mjs）。下面每条注释里的数字都是那时候量到的。
  //
  // 三条贯穿全角色的约定：
  //
  // 1. **列表的"人数/条数"两类口径别混用**：`/admin/colleges` 给的是带统计的
  //    Map（studentCount/teacherCount/counselorCount），而 `/admin/colleges/{id}`
  //    是 College 实体，那三个字段**恒为 null**。同一个实体，两个接口两种形状。
  //
  // 2. **`/admin/*` 的字段命名不统一**：`/admin/classes` 是 snake_case
  //    （college_id/major_id/teacher_id）却混着 camelCase（studentCount/teacherName），
  //    而 `/admin/classes/{id}` 又是纯 camelCase。班级页两种都用得到，务必按来源取。
  //
  // 3. **写接口一律没有"名字"这一层**：Course 存 `teacherId`（users.id）但**不返回
  //    教师姓名**，班级行里的 teacherName 是后端查好塞进去的。所以课程页要显示
  //    教师名，只能自己用 role=2 的用户列表建 id→name 映射（6 条，很便宜）。
  admin = {
    // —— 概览 ——
    /**
     * 首页概览：`{totalUsers, totalStudents, totalColleges, totalMajors, totalCourses,
     * totalWarnings, recentWarnings[]}`。
     * ⚠️ **没有** totalTeachers / totalCounselors —— 那两个在 statistics 里。
     */
    getDashboard: () => this._get('/admin/dashboard'),
    /**
     * 统计页：在概览之上多了 `totalTeachers, totalCounselors, ruleCount,
     * redWarnings/yellowWarnings/lowWarnings`，以及两组**恒为 0** 的字段：
     *   · `changeRates.{user,student,course,warning}ChangeRate` 全是 0
     *     —— 没有历史快照表，算不出环比。界面上**不能**画成"较上月 +0%"，
     *     那是在把"没数据"讲成"没有变化"。
     *   · `todayStats.*` 全是 0 —— 没有写入方（在线人数、今日新增预警都无人统计）。
     * 这两个卡片的正确呈现是"暂无环比数据 / 今日暂无记录"，或干脆不展示。
     */
    getStatistics: () => this._get('/admin/statistics'),
    /**
     * 待办汇总：`{pendingWarnings, appeals, classRequests, teacherClassRequests,
     * counselorClassRequests}`。
     * ⚠️ 实测 `appeals/classRequests/teacherClassRequests/counselorClassRequests` 都是 0
     *    （学生的申诉表是空的、9 条班级申请没有一条是待审核），只有
     *    `pendingWarnings=4` 是有数的。这个 0 是**实情**，不是接口坏了。
     */
    getPendingRequests: () => this._get('/admin/pending-requests'),
    /** 系统动态。⚠️ **实测恒为 `[]`** —— 没有任何服务往这张表写，页面上如实展示为空。 */
    getActivities: () => this._get('/admin/activities'),

    // —— 用户 ——
    /**
     * 用户分页。`{total, items[]}`；`items` 的行是"基础字段 + 按角色补的档案字段"：
     *   角色 1 学生 → `studentId`(学号) `collegeId` `collegeName` `majorId` `majorName`
     *   角色 2 教师 → `collegeId` `collegeName`
     *   角色 3 辅导员 → `jobNumber`(业务编号，如 1000000001) `collegeId` `collegeName`
     *   角色 5 学院管理员 → `title`(职务) `collegeId` `collegeName`
     * ⚠️ 教师行**故意没有 jobNumber**：那个字段曾经填的是 `users.id`（于凤海会显示成 29，
     *    而他的工号是 10001）。教师工号 = `username`，页面上直接用它。
     * ⚠️ 排序是 `status ASC, id DESC` —— **待审批（status=0）自动置顶**，这是后端的用意，
     *    但当前库里 35 个账号 status 全是 1，所以看不到效果。
     * ⚠️ **没有 keyword 参数**，按姓名搜索做不到服务端过滤，所以界面只给
     *    角色/学院两个筛选（都实测落进 SQL：collegeId=1→31、2→2、3→0）。
     */
    getUsers: (page = 1, size = 20, collegeId, role) => this._get(`/admin/users/page?page=${page}&size=${size}${collegeId ? '&collegeId=' + collegeId : ''}${role ? '&role=' + role : ''}`),
    /** 单用户。`password` **恒为 null**（后端显式抹掉），不是被过滤掉的字段名。 */
    getUserById: (id) => this._get(`/admin/users/${id}`),
    /**
     * 建号。体 `{username, name, role, email, password}`（PC 表单就这五个）。
     * ⚠️ 建号**不会**给教师(2)/学院管理员(5)建档案行 —— 那两张表的 `college_id`
     *    是 NOT NULL 而建号表单里没有学院，硬填 0 会造出"不属于任何学院"的幽灵账号。
     *    所以新建的教师要在「编辑」里补学院，否则按学院筛选时查不到他。
     * ⚠️ 重名是**业务错误**：`code=500` + message「用户名已存在：xxx」，要按 message 提示，
     *    不能只判 HTTP。
     */
    createUser: (data) => this._post('/admin/users', data),
    /**
     * 改用户。体只发改动的字段。
     * ⚠️ `password` 传**空串等于"不改"**（后端会置 null，MyBatis-Plus 默认不更新 null 字段）；
     *    传明文会被加密入库。这两条都已实测（改名前后的口令都还能登）。
     */
    updateUser: (id, data) => this._put(`/admin/users/${id}`, data),
    /** 删用户。连带清掉档案行、消息、以及（role=2 时的）教师资料。 */
    deleteUser: (id) => this._del(`/admin/users/${id}`),
    /** 重置口令。⚠️ **不是发邮件**：后端把它改成固定的 `123456`（与种子数据同一口令）。 */
    resetPassword: (id) => this._post(`/admin/users/${id}/reset-password`),
    /**
     * 停用/启用。`status` 1↔0。
     * ⚠️ 停用后登录会被拒，但提示语是「账号正在审批中，请等待管理员审核」
     *    （登录接口把 status!=1 一律归到这句），别在界面上另编一个说法。
     */
    toggleUserStatus: (id) => this._post(`/admin/users/${id}/toggle-status`),
    /** 审批通过（`status` → 1）。 */
    approveUser: (id) => this._put(`/admin/users/${id}/approve`),
    /**
     * 登录日志。**必须带 userId**（没有全局日志接口）。
     * 行：`{id, loginTime, ipAddress, location, device, status}`。
     * ⚠️ 实测全库只有 41 条、最新一条停在 2026-06-29 —— login_logs **没有写入方**，
     *    新登录不会新增记录。页面上要说明"日志停止更新于 2026-06"，不然会被当成
     *    "这个账号没登录过"。
     */
    getLoginLogs: (userId) => this._get(`/admin/login-logs/${userId}`),

    // —— 角色权限 ——
    /** 4 条：ROLE_STUDENT / TEACHER / COUNSELOR / ADMIN。 */
    getRoles: () => this._get('/admin/permissions/roles'),
    /** 9 条权限点（PERM_STUDENT_VIEW 等）。 */
    getPermissions: () => this._get('/admin/permissions/list'),
    /**
     * 某用户的角色与权限点：`{userId, username, role, roleName, permissions[]}`。
     * ⚠️ 实测 `roleName` 是**英文枚举**（ADMIN/TEACHER…）且 `permissions` 可能是空数组
     *    ——权限点表里没有 ADMIN 的映射行。空数组是真话，不要补默认权限。
     * ⚠️ 它认的数字角色到 4 为止，role=5（学院管理员）会落到 `UNKNOWN`。
     */
    getUserPermissions: (userId) => this._get(`/admin/permissions/user/${userId}`),

    // —— 学院与专业 ——
    /** 学院列表（带统计）：`{id, name, code, description, studentCount, teacherCount, counselorCount}`。 */
    getColleges: () => this._get('/admin/colleges'),
    /** 单学院 = College 实体：**统计三个字段为 null**（见类注释第 1 条）。 */
    getCollegeById: (id) => this._get(`/admin/colleges/${id}`),
    createCollege: (data) => this._post('/admin/colleges', data),
    updateCollege: (id, data) => this._put(`/admin/colleges/${id}`, data),
    deleteCollege: (id) => this._del(`/admin/colleges/${id}`),
    /** 某学院下的专业（Major 实体数组，带 `collegeId`）。 */
    getMajorsByCollege: (collegeId) => this._get(`/admin/colleges/${collegeId}/majors`),
    /** 全部专业 12 条。 */
    getMajors: () => this._get('/admin/majors'),
    createMajor: (data) => this._post('/admin/majors', data),
    updateMajor: (id, data) => this._put(`/admin/majors/${id}`, data),
    deleteMajor: (id) => this._del(`/admin/majors/${id}`),

    // —— 教师 / 辅导员 / 学生档案（只读，留给别的页面解析姓名用） ——
    /**
     * 教师档案 6 行：`{id, userId, name, collegeId, title, researchAreas}`
     * （id 是 `teacher_profile.id`，userId 是 `users.id`）。
     * ⚠️ **关键口径**：`courses.teacher_id` 与 `classes.teacher_id` 里存的是
     *    **userId**，不是这里的 `id`。于凤海 = id 1 / userId 29，课表里的 29 指的是后者。
     *    按 `id` 去对，10 门课会全部对不上人。
     */
    getTeachers: () => this._get('/admin/teachers'),
    /** 辅导员档案 1 行。字段很宽，带 `counselorId`（业务编号 C53）、`classCount`、`studentCount`。 */
    getCounselors: () => this._get('/admin/counselors'),
    /**
     * ⚠️ **实测恒为空数组**，别用它取"全部学生"。
     * 它内部调的是 `selectByCollegeId(collegeId)`，SQL 写死
     * `WHERE college_id = #{collegeId}`，管理员不传学院时 collegeId=null，
     * 于是条件成了 `college_id = NULL` —— 恒不成立，返回空。
     * 而且它外面那层 catch 是 `return ApiResponse.success(List.of())`，
     * 就算真报错也会伪装成"成功且没有学生"。
     * 要全部学生请用 `getUsers(1, n, undefined, 1)`（role=1，实测 24 个）。
     */
    getStudents: () => this._get('/admin/students'),

    // —— 课程与班级 ——
    /**
     * 课程 10 门。行里有 `code/name/credits/type/courseType/teacherId/description`，
     * **没有教师姓名**（见类注释第 3 条）—— 姓名要用 getTeachers() 按
     * `teacherId === userId` 自己拼。
     */
    getCourses: () => this._get('/admin/courses'),
    getCourseById: (id) => this._get(`/admin/courses/${id}`),
    createCourse: (data) => this._post('/admin/courses', data),
    updateCourse: (id, data) => this._put(`/admin/courses/${id}`, data),
    deleteCourse: (id) => this._del(`/admin/courses/${id}`),
    /**
     * 班级 8 个。行：`{id, name, college_id, major_id, teacher_id, studentCount, teacherName}`
     * —— **同一行里两种命名**（见类注释第 2 条）：取学科/专业 id 用下划线，
     * 取人数与教师名用驼峰。`counselorId` 只有详情接口才返回。
     */
    getClasses: () => this._get('/admin/classes'),
    /**
     * 单班级 = `Classes` 实体（驼峰：`collegeId/majorId/teacherId/counselorId`）。
     * ⚠️ `counselorId` **只有这支接口给**，列表接口没有这个字段。
     * ⚠️ 它存的是 `counselor_profiles.user_id`（司明 = 53），不是档案的 `id`（= 1）。
     */
    getClassById: (id) => this._get(`/admin/classes/${id}`),
    createClass: (data) => this._post('/admin/classes', data),
    /**
     * 改班级。体用驼峰：`{name, collegeId, majorId, teacherId, counselorId}`。
     * ⚠️ 底层是 MyBatis-Plus 的 `updateById`，**跳过 null 字段** ——
     *    传 `teacherId: null` 等于没传，清不掉班主任。清班主任只能走
     *    `removeTeacherFromClass`。想让某个字段生效就必须给非 null 值。
     */
    updateClass: (id, data) => this._put(`/admin/classes/${id}`, data),
    deleteClass: (id) => this._del(`/admin/classes/${id}`),
    /** 把班主任移除（`classes.teacher_id` 置 null），当场写库。 */
    removeTeacherFromClass: (classId) => this._post(`/admin/class-management/remove-teacher/${classId}`),

    // —— 预警规则 ——
    /** 4 条规则。字段很宽：`ruleName/ruleCode/warningLevel/expression/statisticCycle/
     *  triggerType/failDefinition/interventionPlan/notificationFlow/...` ——
     *  这是预警系统的**规则源**，也是答辩最能讲的一屏。 */
    getRules: () => this._get('/admin/rules'),
    getRuleById: (id) => this._get(`/admin/rules/${id}`),
    createRule: (data) => this._post('/admin/rules', data),
    updateRule: (id, data) => this._put(`/admin/rules/${id}`, data),
    deleteRule: (id) => this._del(`/admin/rules/${id}`),

    // —— 预警 ——
    /** 全部预警 4 条（实体数组，`warningLevel` 是**数字** 1/2/3，`title` 是中文等级名）。 */
    getWarnings: () => this._get('/admin/warnings'),
    /** 单条预警。⚠️ 不存在的 id 是 **404**（`code=404`），不是 500。 */
    getWarningById: (id) => this._get(`/admin/warnings/${id}`),
    /**
     * 处理预警。体 `{handleResult, remark}` → `status=1` + `handleResult` + `handledAt`。
     * ⚠️ `remark` 被后端写进了 **`handledBy` 列**（列名是"处理人"，存的是备注文字）。
     *    所以界面上这一列要显示成「处理备注」，显示成"处理人"就与库里的事实不符。
     */
    handleWarning: (id, data) => this._put(`/admin/warnings/${id}/handle`, data),

    // —— 班级申请审批 ——
    /**
     * 待审批的班级申请 `{total, items[]}`。
     * ⚠️ **实测 total=0**：库里 9 条申请的状态是 3 通过 / 2 拒绝 / 1 取消，没有待审核的。
     *    所以这一页的"待审批"标签页**现在就是空的**，这是实情。
     *    要演示审批链路，得先从教师端提交一条（教师端的 `applyClass` 真的写 status=0）。
     */
    getPendingClassRequests: () => this._get('/admin/class-management/pending-requests'),
    /**
     * 全部申请（不分状态）`{total, items[]}`，共 9 条，按创建时间倒序。
     * 行：`{id, type:'teacher'|'counselor', classId, className, reason, applicationType,
     * status, createTime, updateTime, teacherId, teacherName}`（辅导员那批是 `counselorName`）。
     * `status`：0 待审核 / 1 通过 / 2 拒绝 / 3 取消。
     * ⚠️ 它**不认 `type` 参数** —— 实测 `type=teacher`、`type=counselor`、不传，三者
     *    返回完全相同（total 都是 9）。所以界面上不做"按类型"的服务端筛选，
     *    要分就在拿到的一页里客户端分。
     */
    getClassApplications: (page = 1, size = 20) => this._get(`/admin/class-management/applications?page=${page}&size=${size}`),
    /**
     * 通过申请（`status` → 1）。⚠️ `type` 必须是 `teacher` 或 `counselor` ——
     * 两条路查的是**不同的表**（`teacher_class_application` / `counselor_class_application`），
     * id 会撞（两边都有 id=1），传错 type 就会改到别人那条上。
     * 传 `counselor` 时还会**顺带把 `classes.counselor_id` 写上**。
     */
    approveClassRequest: (id, type = 'teacher') => this._post(`/admin/class-management/approve/${id}?type=${type}`),
    /** 拒绝申请（`status` → 2）。体 `{reason}`（后端收了但**没有列存它**，只改状态）。 */
    rejectClassRequest: (id, reason = '', type = 'teacher') =>
      this._post(`/admin/class-management/reject/${id}?type=${type}`, { reason }),

    // —— 导出历史（只有"历史"，导出本身是 xlsx 二进制流，小程序收不了） ——
    /** `{total, items[]}`，24 条。行：`{id, fileName, dataType, dataTypeName, recordCount,
     *  fileSize, createdAt, exportedBy}`；`fileSize` 可能是字符串「文件不存在」。 */
    getExportHistory: (page = 1, size = 20) => this._get(`/admin/export/history?page=${page}&size=${size}`),
    deleteExport: (exportId) => this._del(`/admin/export/${exportId}`),

    // —— 账号级（/users/** 不分角色） ——
    getUserInfo: (userId) => this._get(`/users/info/${userId}`),
    /**
     * 读设置。**没有记录时返回默认值、不返回 404** —— 那时返回的行 `id` 是 null
     * （默认值在内存里生成，没落库）。所以 `id` 为 null 表示"从没保存过"，不是数据丢了。
     */
    getSettings: (userId) => this._get(`/users/${userId}/settings`),
    /**
     * 存设置。`PUT` 是**部分更新**，不是整表覆盖 —— 请求体里没出现的字段保持不动
     * （底层 MyBatis-Plus `updateById` 的 NOT_NULL 策略，实体里开关都是包装类型 Boolean，
     * 所以"传了 false"和"没传"分得开）。2026-09-29 实测：只发 `{emailReminder}`，
     * 另外四个开关保持原值。第一次保存会**插一行**（`user_settings` 原本没有该用户的行）。
     */
    updateSettings: (userId, data) => this._put(`/users/${userId}/settings`, data)

    // `broadcastMessage`（POST /admin/messages/broadcast）**已删除**：
    // 「消息管理」页按用户决定砍掉了。服务端那条路 2026-09-29 已修好（原来每条插入都
    // 因为 sender_id NOT NULL 而抛异常、被 catch 吞掉后照旧回 success），
    // 现在真能写进 messages 表 —— 但库里现在一条消息都没有，"消息管理"页会是空的，
    // 所以这个页面暂时不做；哪天要恢复，先把这行加回来。
  }
  // ========== 学院管理员端（college-admin） ==========
  //
  // 21 个接口：18 个实现在 admin-service 的 CollegeAdminController（**只读本地 mapper、
  // 不走 Feign** —— 所以不会重现教师端那一批 403），外加 3 个账号级自助（见块尾）。
  // 2026-09-29 拿两个真账号把只读接口逐条打过
  // （testsprite/local-check/college-admin-probe.txt）：18 个只读路径全部
  // code=200，`getCollegeId()` 里 `(Long) p.get("college_id")` 的硬转型也没炸
  // （那一列 BIGINT → MyBatis 给 Long）。
  //
  // ── 这一段的两个**反复踩**的坑 ──
  //
  // 1. **第一参数一律是 `users.id`，不是档案表的 id。**
  //    库里的两个学院管理员：张建国 users.id=57 / college_admin_profile.id=1（软件学院），
  //    李明远 users.id=56 / college_admin_profile.id=2（电子工程学院）。
  //    档案 id 与 users id 不同源，传错就查不到自己的学院。
  //    只有 `/student-detail`、`/teacher-detail`、`/class-students` 的**第二个参数**
  //    才是档案 id（student_profile.id / teacher_profile.id / classes.id）。
  //
  // 2. **同名不同义的键**：`studentId` 在 `/students` 里是**学号字符串**（"2023020616"），
  //    在 `/warnings` 里却是 `student_profile.id`（数字 26）。跨接口拼数据时不能径直拿来用。
  collegeAdmin = {
    /**
     * 学院管理员档案：`{id(档案 id), user_id(users.id), name, college_id, college_name,
     * phone, email, title}` —— 注意它**带 `college_name`**（学院名是 JOIN 出来的），
     * 但键名是 snake_case，别跟 camelCase 的接口混着记。
     * ⚠️ 没有档案行时返回 `code=500 未找到学院管理员档案`（不是空对象）。
     */
    getProfile: (uid) => this._get(`/college-admin/profile/${uid}`),

    /**
     * 概览：`{classCount, studentCount, teacherCount, counselorCount, warningCount,
     * majorCount, profile:{…}}` —— 档案嵌在里面，所以首页不用再调 getProfile。
     * ⚠️ `counselorCount` **不是辅导员人数**：实现是"本学院里被某个辅导员管着的班级数"，
     *    还夹了一句 `Math.min(…, 全部辅导员行数)`。实测电子工程学院 counselors 列表是 `[]`，
     *    概览却给 1（因为它的两个班被软件学院的辅导员司明管着）。界面上要按它实际的含义起名。
     */
    getDashboard: (uid) => this._get(`/college-admin/dashboard/${uid}`),

    /**
     * 本学院班级：`{id, name, studentCount, gradedStudentCount, avgGpa, warningCount}`。
     * ⚠️ `avgGpa` 为 **null** 表示该班一个成绩都没有 —— 不能当 0 显示（"全班 GPA 为零"
     *    是另一回事）。有成绩的班才给数，`gradedStudentCount` 是用来对分母的。
     */
    getClasses: (uid) => this._get(`/college-admin/classes/${uid}`),

    /**
     * 本学院学生：`{id(student_profile.id), studentId(**学号**), name, className, classId,
     * collegeId, gpa, totalCourses, passedCourses, failedCourses, warningCount}`。
     * `keyword` 是**服务端**过滤（匹配姓名 / 学号 / 班级名的子串），比拿一页在本地筛强。
     * 注意它按 `student_profile.class_id ∈ 本学院班级` 取人 —— 班级没学生的学院会返回 `[]`
     * （电子工程学院就是 `[]`，不是接口坏了）。
     */
    getStudents: (uid, keyword) =>
      this._get(`/college-admin/students/${uid}${keyword ? '?keyword=' + encodeURIComponent(keyword) : ''}`),

    /**
     * 学生详情：`{id, studentId(学号), name, className, gpa, totalCourses, passedCourses,
     * failedCourses, warningCount, scores[], warnings[]}`。
     * 成绩行比 `/score-query` **多一个 `credits`**（学分），`finalScore` 为 null = 期末未录入。
     * 预警行只有 `{id, description, warningLevel, status, createdAt}`，**没有**课程/规则名。
     */
    getStudentDetail: (uid, profileId) => this._get(`/college-admin/student-detail/${uid}/${profileId}`),

    /**
     * 本学院教师：`{id(teacher_profile.id), name, title, researchAreas, courseCount,
     * studentCount(**去重后的学生人数**), courses(「、」连起来的课程名), avgScore}`。
     * `title` / `researchAreas` 库里可能是 null（于凤海两个都是 null），要兜底显示。
     */
    getTeachers: (uid) => this._get(`/college-admin/teachers/${uid}`),

    /**
     * 教师详情：`{id, name, title, researchAreas, collegeId, courseCount,
     * studentCount(**成绩记录条数**), avgScore, scores[]}`。
     * ⚠️ 两个坑：
     *    · `studentCount` 与列表接口**同名不同义** —— 于凤海在列表里是 24（去重学生），
     *      在这里是 71（他 3 门课的成绩条数）。
     *    · 成绩行里总评字段叫 **`score`**（不是别处的 `totalScore`），且**没有**
     *      `gradePoint` / `grade`。
     */
    getTeacherDetail: (uid, teacherId) => this._get(`/college-admin/teacher-detail/${uid}/${teacherId}`),

    /**
     * 本学院预警：`{id, studentId(**student_profile.id**), studentName, className,
     * description, warningLevel, status, createdAt}`。
     * 没有规则名，只有一个数字 `warningLevel`（复用 utils/score.js 的 warningLevel 判级）。
     */
    getWarnings: (uid) => this._get(`/college-admin/warnings/${uid}`),

    /**
     * 标记预警已处理。体 `{id: 预警id, status}`（不传 status 时后端默认 1）。
     * ⚠️ 它只写 `status` 与 `updated_at` —— **不写** `handle_result` / `handled_by` /
     *    `handled_at` 那三列（那一套只有管理员的 `/admin/warnings/{id}/handle` 会写，
     *    而且那边的「备注」其实是塞进 `handled_by`）。所以这一页不给"处理说明"输入框，
     *    给了也没地方落。
     * ⚠️ 也没有反向接口：status 只能从 0 置 1，**撤销不了**。
     */
    processWarning: (uid, warningId, status = 1) =>
      this._post('/college-admin/process-warning', { id: warningId, status }),

    /**
     * 删除预警记录。⚠️ **真删行**（`deleteById`），不是软删；删掉之后库里就没有这条了，
     * 已产生的"某学生被预警过"的痕迹一起消失。界面上必须是二次确认的危险操作。
     */
    deleteWarning: (warningId) => this._del(`/college-admin/delete-warning/${warningId}`),

    /**
     * 本学院辅导员：`{id(counselor_profiles.id), name, phone, classCount,
     * classes(「、」连起来的班级名)}`。
     * `classCount` / `classes` 是**该辅导员名下的全部班级**，不限本学院 ——
     * 司明一个人管着 8 个班，横跨软件学院与电子工程学院，所以他在软件学院这一页里
     * 会显示 8 个班，比本学院班级总数（6）还多。
     */
    getCounselors: (uid) => this._get(`/college-admin/counselors/${uid}`),

    /**
     * 「帮扶情况」。⚠️ 它查的是**预警表**（跟 getWarnings 同一个 mapper），
     * 不是 `assistance_plan` —— 返回的行只有 `{studentName, className, level, description,
     * status, createdAt}`，**连 id 都没有**，所以只能看不能操作。
     * 字段名也换了一套：等级叫 `level`（不是 warningLevel），说明叫 `description`。
     */
    getAssistance: (uid) => this._get(`/college-admin/assistance/${uid}`),

    /** 学院成绩总览：`{avgScore, passRate(0-100 的整数), recordCount, studentCount}`。 */
    getScoreStats: (uid) => this._get(`/college-admin/scores-stats/${uid}`),

    /**
     * 成绩分析：`{avgScore, passRate, totalRecords, distribution:{'90-100','80-89','70-79',
     * '60-69','0-59'}, courseStats:[{courseId, courseName, recordCount, avgScore, passRate}]}`。
     * `distribution` 的五档之和实测等于 `totalRecords`（238），可以直接画条形图。
     * 课程维度已按平均分降序返回，不用再排。
     */
    getScoreAnalysis: (uid) => this._get(`/college-admin/score-analysis/${uid}`),

    /**
     * 班级对比：`{classId, className, avgScore, passRate, studentCount, recordCount}`。
     * ⚠️ `avgScore` 为 **null** = 该班没有任何成绩（软件学院 6 个班里 5 个都是 null），
     *    不能当 0 参与排序或显示成 0 分。
     */
    getClassComparison: (uid) => this._get(`/college-admin/class-comparison/${uid}`),

    /**
     * 某班花名册：`{id(student_profile.id), studentId(学号), name, className, gpa,
     * totalCourses, passedCourses, failedCourses, warningCount}`。
     * ⚠️ **它不校验这个班属不属于你的学院** —— 实现只查了 `class_id = 传入的 classId`。
     *    实测电子工程学院管理员（uid=56）请求 `/class-students/56/1` 能拿到软件学院
     *    计科2306班 24 个人的姓名与学号（成绩类字段因为按本院统计而全是 null）。
     *    这是服务端的越权读取，页面上挡不住，只能记在案。
     */
    getClassStudents: (uid, classId) => this._get(`/college-admin/class-students/${uid}/${classId}`),

    /**
     * 成绩查询：`{id, studentName, studentId(学号), className, courseName, regularScore,
     * finalScore, totalScore, gradePoint, grade, semester}`。
     * `classId` 与 `keyword` 都是**服务端**参数（keyword 匹配姓名/学号/课程名），
     * 所以这一页的筛选是真的下推到 SQL 的，不是本地筛一页。
     */
    getScoreQuery: (uid, { classId, keyword } = {}) => {
      const qs = []
      if (classId) qs.push('classId=' + encodeURIComponent(classId))
      if (keyword) qs.push('keyword=' + encodeURIComponent(keyword))
      return this._get(`/college-admin/score-query/${uid}${qs.length ? '?' + qs.join('&') : ''}`)
    },

    /**
     * 导出本学院数据。⚠️ **返回的是 JSON 数组，不是 xlsx 流**（与 `/admin/export` 不同），
     * 所以小程序端**能**把它渲染成表格预览。
     * `type` = scores | warnings | students | teachers；未知 type 返回空数组。
     * 每类的行字段各不相同（warnings 的 status 已经被后端翻成「已处理/待处理」中文字符串）。
     */
    getExport: (uid, type = 'scores') => this._get(`/college-admin/export/${uid}?type=${type}`),

    // —— 账号级自助（「我的」页要用） ——
    //
    // 这三支不在 CollegeAdminController 里，属于 user-service 的 `/users/**`。
    //
    // ⚠️ 2026-09-29 这里踩过两次：
    //  1. 最初**漏了这三个方法名** → `profile.vue` 的 `Promise.all([...])` 在
    //     **构造参数数组**时就同步抛 TypeError（`getUserInfo is not a function`），
    //     整行没执行完，同批那几个本来能成功的请求也全被跳过 → 页面显示全 0 +
    //     「未归属学院」。看着像"接口全挂了"，其实只缺一个方法名。
    //  2. 补上之后露出底下真正的墙：网关原来把 role=5 限死在
    //     `/api/college-admin/**` + `/api/admin/**`，这三支全 403。
    //     已在网关 `AuthenticationFilter.hasPermission()` 里按"**仅限本人 id**"
    //     放行（`/api/users/info/{ownId}`、`/api/users/{ownId}/settings`、
    //     `/api/auth/change-password`、`/api/auth/logout|refresh-token`），
    //     并实测过越权面：别人 id 的资料/设置、`/users/list|delete|by-username`、
    //     `/auth/info/**` 仍是 403。
    //
    // 所以这三支现在对 role=5 是通的，但**只对本人 id 通** ——
    // 传别人的 userId 依然 403。
    getUserInfo: (userId) => this._get(`/users/info/${userId}`),
    /**
     * 读设置。**没有记录时返回默认值、不返回 404** —— 那时返回的行 `id` 是 null
     * （默认值在内存里生成，没落库）。所以 `id` 为 null 表示"从没保存过"，不是数据丢了。
     */
    getSettings: (userId) => this._get(`/users/${userId}/settings`),
    /**
     * 存设置。`PUT` 是**部分更新**，不是整表覆盖 —— 请求体里没出现的字段保持不动
     * （底层 MyBatis-Plus `updateById` 的 NOT_NULL 策略，实体里开关都是包装类型 Boolean，
     * 所以"传了 false"和"没传"分得开）。第一次保存会**插一行**。
     */
    updateSettings: (userId, data) => this._put(`/users/${userId}/settings`, data)
  }
}

export const apiClient = new ApiClient()
export const studentAPI = apiClient.student
export const teacherAPI = apiClient.teacher
export const counselorAPI = apiClient.counselor
export const adminAPI = apiClient.admin
export const collegeAdminAPI = apiClient.collegeAdmin
