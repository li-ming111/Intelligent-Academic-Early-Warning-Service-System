import apiClient from './client'

export const authAPI = {
  login: async (username, password) => {
    return apiClient.post('/auth/login', { username, password })
  },
  getColleges: () => {
    return apiClient.get('/auth/colleges')
  },
  getAllCourses: () => {
    return apiClient.get('/auth/courses')
  },
  getMajorsByCollege: (collegeId) => {
    return apiClient.get('/auth/majors', { params: { collegeId } })
  },
  registerStudent: (data) => {
    return apiClient.post('/auth/register/student', data)
  },
  registerTeacher: (data) => {
    return apiClient.post('/auth/register/teacher', data)
  },
  registerCounselor: (data) => {
    return apiClient.post('/auth/register/counselor', data)
  },
  registerAdmin: (data) => {
    return apiClient.post('/auth/register/admin', data)
  }
}

export const studentAPI = {
  register: (data) => {
    return apiClient.post('/students/register', data)
  },
  request: (url, method = 'GET', params = {}) => {
    if (method.toUpperCase() === 'GET') {
      return apiClient.get(url, { params })
    } else if (method.toUpperCase() === 'POST') {
      return apiClient.post(url, params)
    } else if (method.toUpperCase() === 'PUT') {
      return apiClient.put(url, params)
    } else if (method.toUpperCase() === 'DELETE') {
      return apiClient.delete(url, { params })
    }
  },
  // ---- 账号级自助设置 ----
  // 密码是账号属性、唯一权威源是 users 表，所以走 /auth/change-password；
  // 姓名/邮箱/电话同理走 /users/update，不走 student_profile（否则会出现两份真相）。
  changePassword: ({ userId, oldPassword, newPassword }) => {
    return apiClient.post('/auth/change-password', { userId, oldPassword, newPassword })
  },
  updateProfile: (userId, data) => {
    return apiClient.put(`/users/update/${userId}`, data)
  },
  getProfile: (userId) => {
    return apiClient.get(`/users/info/${userId}`)
  },
  getSettings: (userId) => {
    return apiClient.get(`/users/${userId}/settings`)
  },
  getStudentInfo: (studentId) => {
    return apiClient.get(`/students/${studentId}`)
  },
  getStudentInfoByUserId: (userId) => {
    return apiClient.get(`/students/student-by-user/${userId}`)
  },
  getStudentGPA: (studentId) => {
    return apiClient.get(`/students/${studentId}/gpa`)
  },
  getDashboard: (userId) => {
    console.log('调用 getDashboard API, userId:', userId)
    return apiClient.get(`/students/dashboard/${userId}`)
  },
  getScores: (userId, semester) => {
    return apiClient.get(`/students/scores/${userId}`, { params: { semester } })
  },
  getWarnings: (userId) => {
    return apiClient.get(`/students/warnings/${userId}`)
  },
  getAssistancePlans: (userId) => {
    return apiClient.get(`/students/assistance/${userId}`)
  },
  getSuggestions: (userId) => {
    return apiClient.get(`/students/suggestions/${userId}`)
  },
  updatePlanProgress: (planId, progress) => {
    // 实际接口在 counselor-service: PATCH /counselors/assistance-plans/{id}/status
    return apiClient.patch(`/counselors/assistance-plans/${planId}/status`, { status: progress })
  },
  // ========== 班级信息 ==========
  getClassInfo: (studentId) => {
    return apiClient.get(`/students/${studentId}/class-info`)
  },
  getClassMembersList: (userId) => {
    return apiClient.get(`/students/class-members/${userId}`)
  },
  getClassRankingList: (userId) => {
    return apiClient.get(`/students/class-ranking/${userId}`)
  },
  // ========== 通知管理 ==========
  
  getUnreadNotifications: (userId) => {
    return apiClient.get(`/students/notification-center/${userId}/unread`)
  },
  getNotificationsPageable: (userId, page = 1, pageSize = 10) => {
    return apiClient.get(`/students/notification-center/${userId}/list`, {
      params: { page, pageSize }
    })
  },
  markNotificationRead: (notificationId) => {
    return apiClient.post(`/students/notification-center/${notificationId}/mark-read`)
  },
  markMultipleNotificationsAsRead: (userId, notificationIds) => {
    return apiClient.post(`/students/notification-center/${userId}/mark-batch-read`, notificationIds)
  },
  deleteNotificationMsg: (notificationId) => {
    return apiClient.post(`/students/notification-center/${notificationId}/delete`)
  },
  getUnreadNotificationCount: (userId) => {
    return apiClient.get(`/students/notification-center/${userId}/unread-count`)
  },
  clearReadNotifications: (userId) => {
    return apiClient.post(`/students/notification-center/${userId}/clear-read`)
  },
  
  // 订阅偏好管理API
  getSubscriptionPreferences: (studentId) => {
    return apiClient.get(`/students/subscription/${studentId}/preferences`)
  },
  updateSubscriptionPreferences: (studentId, preference) => {
    return apiClient.post(`/students/subscription/${studentId}/update`, preference)
  },
  subscribeWarningLevel: (studentId, level) => {
    return apiClient.post(`/students/subscription/${studentId}/subscribe-level`, null, {
      params: { level }
    })
  },
  unsubscribeWarningLevel: (studentId, level) => {
    return apiClient.post(`/students/subscription/${studentId}/unsubscribe-level`, null, {
      params: { level }
    })
  },
  setPushChannel: (studentId, channel, enabled) => {
    return apiClient.post(`/students/subscription/${studentId}/set-channel`, null, {
      params: { channel, enabled }
    })
  },
  
  // ========== 对标分析 ==========
  
  getLatestBenchmark: (studentId) => {
    return apiClient.get(`/students/benchmark/${studentId}/latest`)
  },
  getBenchmarkBySemester: (studentId, semester) => {
    return apiClient.get(`/students/benchmark/${studentId}/semester`, { params: { semester } })
  },
  getHistoryBenchmark: (studentId) => {
    return apiClient.get(`/students/benchmark/${studentId}/history`)
  },
  getClassRanking: (studentId, classId, semester) => {
    return apiClient.get(`/students/benchmark/${studentId}/class-ranking`, {
      params: { classId, semester }
    })
  },
  getMajorRanking: (studentId, majorId, semester) => {
    return apiClient.get(`/students/benchmark/${studentId}/major-ranking`, {
      params: { majorId, semester }
    })
  },
  getClassBenchmarkComparison: (classId, semester) => {
    return apiClient.get(`/students/benchmark/class/${classId}/comparison`, { params: { semester } })
  },
  getMajorBenchmarkComparison: (majorId, semester) => {
    return apiClient.get(`/students/benchmark/major/${majorId}/comparison`, { params: { semester } })
  },
  getProgressReport: (studentId) => {
    return apiClient.get(`/students/benchmark/${studentId}/progress-report`)
  },
  
  // ========== 申诉管理 ==========
  
  
  submitAppeal: (appeal) => {
    return apiClient.post(`/students/appeals/submit`, appeal)
  },
  getStudentAppeals: (studentId) => {
    return apiClient.get(`/students/appeals/${studentId}/list`)
  },
  getPendingAppeals: (studentId) => {
    return apiClient.get(`/students/appeals/${studentId}/pending`)
  },
  getAppealDetail: (appealId) => {
    return apiClient.get(`/students/appeals/${appealId}/detail`)
  },
  withdrawAppeal: (appealId, studentId) => {
    return apiClient.post(`/students/appeals/${appealId}/withdraw`, null, { params: { studentId } })
  },
  getAppealStatistics: (studentId) => {
    return apiClient.get(`/students/appeals/${studentId}/statistics`)
  },
  getAppealSuccessRate: (studentId) => {
    return apiClient.get(`/students/appeals/${studentId}/success-rate`)
  },
  getAppealsByStatus: (studentId, status) => {
    return apiClient.get(`/students/appeals/${studentId}/by-status`, { params: { status } })
  },
  getAppealHistory: (studentId, page = 1, pageSize = 10) => {
    return apiClient.get(`/students/appeals/${studentId}/history`, { params: { page, pageSize } })
  },
  getAppealReasonStatistics: (studentId) => {
    return apiClient.get(`/students/appeals/${studentId}/reason-statistics`)
  },
  
  // ========== 帮扶反馈 ==========
  
  
  submitEvaluation: (evaluation) => {
    return apiClient.post(`/students/evaluations/submit`, evaluation)
  },
  getStudentEvaluations: (studentId) => {
    return apiClient.get(`/students/evaluations/${studentId}/list`)
  },
  getPlanEvaluation: (planId) => {
    return apiClient.get(`/students/evaluations/${planId}/detail`)
  },
  getEvaluationStatistics: (studentId) => {
    return apiClient.get(`/students/evaluations/${studentId}/statistics`)
  },
  // 下面 4 个是 `/students/settings/**`，**当前没有任何页面调用**（学生设置页走的是
  // 第 58 行那个 `/users/${userId}/settings`）。后端 2026-09-30 之前这四个都不可信：
  // GET 返回写死的字面量、PUT 直接 `return {success:true}` 一个字段都不写。
  // 现已补成同一张 `user_settings` 表的别名（键 users.id），两条路由读写同一行，
  // 所以留着也不会踩坑；但新代码请直接用 `/users/{uid}/settings`。
  getUserSettings: (userId) => {
    return apiClient.get(`/students/settings/${userId}`)
  },
  updateUserSettings: (userId, settings) => {
    return apiClient.put(`/students/settings/${userId}`, settings)
  },
  updatePrivacy: (userId, privacyLevel) => {
    return apiClient.post(`/students/settings/${userId}/privacy-level`, null, { params: { privacyLevel } })
  },
  getSecurityLogs: (userId, page = 1, pageSize = 10) => {
    return apiClient.get(`/students/settings/${userId}/security-logs`, { params: { page, pageSize } })
  },
  getAppeals: (studentId) => {
    return apiClient.get(`/students/appeals/${studentId}/list`)
  },
  getUnreadNotificationCountAdvanced: (userId) => {
    return apiClient.get(`/students/notification-center/${userId}/unread-count`)
  },
  getUnreadNotificationsAdvanced: (userId) => {
    return apiClient.get(`/students/notification-center/${userId}/unread`)
  },
  // ========== 消息管理API ==========
  getMessages: (userId) => {
    return apiClient.get(`/students/messages/${userId}`)
  },
  getUnreadCount: (userId) => {
    return apiClient.get(`/students/messages/${userId}/unread-count`)
  },
  sendMessage: (data) => {
    return apiClient.post(`/students/messages`, data)
  },
  markMessageAsRead: (messageId) => {
    return apiClient.post(`/students/messages/${messageId}/mark-read`)
  },
  // 导出个人数据
  exportScoresExcel: (userId) => {
    return apiClient.get(`/students/export/scores/${userId}/excel`)
  },
  downloadScoresExcel: (userId) => {
    return apiClient.get(`/students/download/scores/${userId}`, { responseType: 'blob' })
  }
}

export const teacherAPI = {
  register: (data) => {
    return apiClient.post(`/teachers/register`, data)
  },
  // ---- 账号级自助设置 ----
  // 说明：邮箱/电话在 users 表（teacher_profile 只有 8 列，压根没有这两列），
  // 密码走 /auth/change-password；通知偏好与办公信息统一存 user_settings（键 users.id）。
  getProfile: (userId) => {
    return apiClient.get(`/users/info/${userId}`)
  },
  changePassword: ({ userId, oldPassword, newPassword }) => {
    return apiClient.post('/auth/change-password', { userId, oldPassword, newPassword })
  },
  updateProfile: (userId, data) => {
    return apiClient.put(`/users/update/${userId}`, data)
  },
  getSettings: (userId) => {
    return apiClient.get(`/users/${userId}/settings`)
  },
  updateNotifyPrefs: ({ userId, ...prefs }) => {
    return apiClient.put(`/users/${userId}/settings`, prefs)
  },
  updateWorkInfo: ({ userId, ...work }) => {
    return apiClient.put(`/users/${userId}/settings`, work)
  },
  getDashboard: (userId) => {
    return apiClient.get(`/teachers/dashboard/${userId}`)
  },
  getStudents: (teacherId) => {
    return apiClient.get(`/teachers/students/${teacherId}`)
  },
  getCourses: (teacherId) => {
    return apiClient.get(`/teachers/courses`, { params: { teacher_id: teacherId } })
  },
  getAllCourses: () => {
    return apiClient.get(`/teachers/courses`)  // 不传teacher_id，后端返回全部课程
  },
  updateCourse: (courseId, data) => {
    return apiClient.put(`/teachers/courses/${courseId}`, data)
  },
  getScores: (courseId) => {
    return apiClient.get(`/teachers/scores`, { params: { course_id: courseId } })
  },
  saveScores: (scores) => {
    return apiClient.post(`/teachers/scores`, scores)
  },
  updateScore: (scoreId, data) => {
    return apiClient.put(`/teachers/scores/${scoreId}`, data)
  },
  exportScores: (courseId) => {
    return apiClient.get(`/teachers/export/scores`, { params: { course_id: courseId }, responseType: 'blob' })
  },
  downloadScores: (courseId) => {
    return apiClient.get(`/teachers/download/scores`, { params: { course_id: courseId }, responseType: 'blob' })
  },
  importScores: (data) => {
    return apiClient.post(`/teachers/scores/import`, data)
  },
  getFeedbacks: (teacherId, category) => {
    return apiClient.get(`/teachers/feedbacks`, { params: { teacherId: teacherId, category } })
  },
  // ⚠️ 2026-09-30 修正：原来打的是 `PUT /teachers/feedbacks/{id}`，**后端没有这个端点**
  // （teacher-service 只有 `GET /feedbacks` 与 `POST /feedbacks/{id}/reply`），
  // 所以 PC 教师「反馈管理」页的回复按钮**从来没成功过一次**，只会弹"回复失败"。
  // 现在与下面「反馈管理API」里的 `replyToFeedback` 指向同一路径 ——
  // 保留 `replyFeedback` 这个名字是因为 `TeacherFeedback.vue` 已经在调它。
  replyFeedback: (feedbackId, data) => {
    return apiClient.post(`/teachers/feedbacks/${feedbackId}/reply`, data)
  },
  getEnrollments: (teacherId, courseId) => {
    const params = {}
    if (teacherId) params.teacherId = teacherId
    if (courseId) params.courseId = courseId
    return apiClient.get(`/teachers/enrollments`, { params })
  },
  saveCommunication: (data) => {
    return apiClient.post(`/teachers/communications`, data)
  },
  getCounselorByClass: (className) => {
    return apiClient.get(`/teachers/counselor/by-class`, { params: { class_name: className } })
  },
  getAnalysis: (courseId) => {
    return apiClient.get(`/teachers/analysis`, { params: { course_id: courseId } })
  },
  getCourseDetail: (courseId) => {
    return apiClient.get(`/teachers/courses/${courseId}`)
  },
  recommendCourse: (data) => {
    return apiClient.post(`/teachers/courses/recommend`, data)
  },
  getWarnings: (teacherId, status) => {
    return apiClient.get(`/teachers/warnings/${teacherId}`, { params: { status } })
  },
  processWarning: (warningId, data) => {
    return apiClient.post(`/teachers/warnings/${warningId}/process`, data)
  },
  // ============= 班级管理申请API =============
  getMyClassManagementRequests: (teacherId) => {
    return apiClient.get(`/teachers/class-management/requests`, { params: { teacherId } })
  },
  getTodos: (teacherId) => {
    return apiClient.get(`/teachers/todos/${teacherId}`)
  },
  // ============= 成绩分析API =============
  getCourseScoreDistribution: (courseId) => {
    return apiClient.get(`/teachers/courses/${courseId}/distribution`)
  },
  getCourseAnomalies: (courseId, threshold = 60) => {
    return apiClient.get(`/teachers/courses/${courseId}/anomaly`, { params: { threshold } })
  },
  getCourseStudents: (courseId, page = 1, size = 20) => {
    return apiClient.get(`/teachers/courses/${courseId}/students`, { params: { page, size } })
  },
  // ============= 学分预测API =============
  getCreditPrediction: (studentId) => {
    return apiClient.get(`/teachers/students/${studentId}/credit-prediction`)
  },
  getCourseRecommendations: (courseId, limit = 5) => {
    return apiClient.get(`/teachers/courses/${courseId}/recommendations`, { params: { limit } })
  },
  // ============= 审计日志API =============
  getScoreLogs: (teacherId, page = 1, size = 20) => {
    return apiClient.get(`/teachers/scores/audit/${teacherId}`, { params: { page, size } })
  },
  // ============= 反馈管理API =============
  getFeedbackList: (teacherId, category, page = 1, size = 20) => {
    return apiClient.get(`/teachers/feedbacks/${teacherId}/list`, { params: { category, page, size } })
  },
  replyToFeedback: (feedbackId, data) => {
    return apiClient.post(`/teachers/feedbacks/${feedbackId}/reply`, data)
  },
  // 兼容性别名
  getFeedback: (teacherId, category) => {
    return apiClient.get(`/teachers/feedbacks/${teacherId}/list`, { params: { category } })
  },
  // 新增成绩分析相关API
  analyzeScores: (courseId) => {
    return apiClient.get(`/teachers/scores/analyze`, { params: { course_id: courseId } })
  },
  detectAnomalies: (courseId) => {
    return apiClient.get(`/teachers/scores/anomalies`, { params: { course_id: courseId } })
  },
  triggerWarnings: (courseId) => {
    return apiClient.post(`/teachers/scores/warnings`, null, { params: { course_id: courseId } })
  },
  analyzeStudentScore: (studentId, courseId) => {
    return apiClient.get(`/teachers/scores/student/analyze`, { params: { student_id: studentId, course_id: courseId } })
  },
  deleteScore: (scoreId) => {
    return apiClient.delete(`/teachers/scores/${scoreId}`)
  },
  batchDeleteScores: (scoreIds) => {
    return apiClient.post(`/teachers/scores/batch-delete`, scoreIds)
  },
  getScoresByClass: (className, teacherId) => {
    return apiClient.get(`/teachers/scores/by-class`, { params: { class_name: className, teacher_id: teacherId } })
  },
  getClassScoreAnalysis: (classId) => {
    return apiClient.get(`/teachers/classes/${classId}/analysis`)
  },
  getMyClasses: (teacherId) => {
    return apiClient.get(`/teachers/class-management/my-classes`, { params: { teacherId } })
  },
  searchClasses: (keyword) => {
    return apiClient.get(`/teachers/class-management/search`, { params: { keyword } })
  },
  submitClassManagementRequest: (data) => {
    return apiClient.post(`/teachers/class-management/apply`, data)
  },
  cancelClassManagement: (data) => {
    return apiClient.post(`/teachers/class-management/cancel`, data)
  },
  importStudents: (formData) => {
    return apiClient.post(`/teachers/students/import`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  getClassStudents: (classId) => {
    return apiClient.get(`/teachers/classes/${classId}/students`)
  },
  getTeachers: () => {
    return apiClient.get(`/teachers/list`)
  },
  // ========== 消息管理API ==========
  getMessages: (userId) => {
    return apiClient.get(`/teachers/messages/${userId}`)
  },
  getUnreadCount: (userId) => {
    return apiClient.get(`/teachers/messages/${userId}/unread-count`)
  }
  // markMessageAsRead 已删（2026-09-30）：它打的 `POST /teachers/messages/{id}/mark-read`
  // 后端只有一行 `return ApiResponse.success("消息已标记为已读")`，**不碰任何表**。
  // 数据源 `feedback` 表也没有"已读"列（只有 status 0=未回复/1=已回复），
  // 所以这个动作没有地方可落 —— 保留一个"看起来能用"的调用只会让人以为标记生效了。
  // 未读口径用上面的 `getUnreadCount`（= status 0 的条数），回复走 `replyToFeedback`。
}

export const counselorAPI = {
  register: (data) => {
    return apiClient.post(`/counselors/register`, data)
  },
  // ---- 账号级自助设置 ----
  // 与 teacherAPI 同一套：姓名/邮箱/电话读 users 表（counselor_profiles 里的
  // office_location/duty_phone 是历史遗留的空列，不再往里写），
  // 通知偏好与办公信息存 user_settings（键 users.id）。
  getProfile: (userId) => {
    return apiClient.get(`/users/info/${userId}`)
  },
  changePassword: ({ userId, oldPassword, newPassword }) => {
    return apiClient.post('/auth/change-password', { userId, oldPassword, newPassword })
  },
  updateProfile: (userId, data) => {
    return apiClient.put(`/users/update/${userId}`, data)
  },
  getSettings: (userId) => {
    return apiClient.get(`/users/${userId}/settings`)
  },
  updateNotifyPrefs: ({ userId, ...prefs }) => {
    return apiClient.put(`/users/${userId}/settings`, prefs)
  },
  updateWorkInfo: ({ userId, ...work }) => {
    return apiClient.put(`/users/${userId}/settings`, work)
  },
  getDashboard: (userId) => {
    return apiClient.get(`/counselors/dashboard/${userId}`)
  },
  getStudents: (counselorId) => {
    return apiClient.get(`/counselors/students`, { params: { counselor_id: counselorId } })
  },
  searchStudents: (searchName, classId) => {
    // 前端过滤，后端不提供此接口
    return Promise.resolve([])
  },
  getStudentDetail: (studentId) => {
    return apiClient.get(`/counselors/students/${studentId}`)
  },
  notifyStudents: (data) => {
    return apiClient.post(`/counselors/students/notify`, data)
  },
  getWarnings: (counselorId, status) => {
    return apiClient.get(`/counselors/warnings`, { params: { counselor_id: counselorId, status } })
  },
  processWarning: (warningId, data) => {
    return apiClient.post(`/counselors/warnings/${warningId}/process`, data)
  },
  batchProcessWarnings: (warningIds) => {
    return apiClient.post(`/counselors/warnings/batch-process`, warningIds)
  },
  getEnrollments: (counselorId) => {
    return apiClient.get(`/counselors/enrollments`, { params: { counselor_id: counselorId } })
  },
  // ============= 帮扶计划API =============
  getPlansByStudent: (studentId) => {
    return apiClient.get(`/counselors/students/${studentId}/assistance-plans`)
  },
  getCounselorPlans: (counselorId) => {
    return apiClient.get(`/counselors/${counselorId}/assistance-plans`)
  },
  createAssistancePlan: (data) => {
    return apiClient.post(`/counselors/assistance-plans`, data)
  },
  updateAssistancePlan: (id, data) => {
    return apiClient.put(`/counselors/assistance-plans/${id}`, data)
  },
  deleteAssistancePlan: (id) => {
    return apiClient.delete(`/counselors/assistance-plans/${id}`)
  },
  updatePlanProgress: (planId, data) => {
    return apiClient.patch(`/counselors/assistance-plans/${planId}/status`, data)
  },
  // ============= 成绩跟踪API =============
  getClassScores: (counselorId, classId) => {
    return apiClient.get(`/counselors/scores/class/${classId}`, { params: { counselor_id: counselorId } })
  },
  getCourseScoreDistribution: (courseId, counselorId) => {
    return apiClient.get(`/counselors/scores/distribution/${courseId}`, { params: { counselor_id: counselorId } })
  },
  getLowScoreStudents: (counselorId) => {
    return apiClient.get(`/counselors/scores/low-score`, { params: { counselor_id: counselorId } })
  },
  getStudentScoreTrend: (studentId) => {
    return apiClient.get(`/counselors/scores/student/${studentId}/trend`)
  },
  // ============= 通知中心API =============
  getNotificationHistory: (counselorId, page, size) => {
    return apiClient.get(`/counselors/notifications/history`, { params: { counselor_id: counselorId, page, size } })
  },
  getNotificationTemplates: () => {
    return apiClient.get(`/counselors/notifications/templates`)
  },
  getWeeklyNotifications: (counselorId) => {
    return apiClient.get(`/counselors/notifications/weekly-count`, { params: { counselor_id: counselorId } })
  },
  // ============= 班级管理API =============
  getClasses: (counselorId) => {
    return apiClient.get(`/counselors/classes`, { params: { counselor_id: counselorId } })
  },
  getClassActivities: (counselorId) => {
    return apiClient.get(`/counselors/classes/activities`, { params: { counselor_id: counselorId } })
  },
  getClassDetail: (classId) => {
    return apiClient.get(`/counselors/classes/${classId}/detail`)
  },
  getClassStudents: (classId) => {
    return apiClient.get(`/counselors/classes/${classId}/students`)
  },
  getClassWarningOverview: (classId) => {
    return apiClient.get(`/counselors/classes/${classId}/warnings`)
  },
  compareClassWarnings: (counselorId) => {
    return apiClient.get(`/counselors/classes/warnings/compare`, { params: { counselor_id: counselorId } })
  },
  // ============= 数据分析API =============
  getCreditInsufficientRate: (counselorId) => {
    return apiClient.get(`/counselors/analytics/credit-insufficient`, { params: { counselor_id: counselorId } })
  },
  getWarningLevelDistribution: (counselorId) => {
    return apiClient.get(`/counselors/analytics/warning-distribution`, { params: { counselor_id: counselorId } })
  },
  getWarningHandlingEfficiency: (counselorId) => {
    return apiClient.get(`/counselors/analytics/handling-efficiency`, { params: { counselor_id: counselorId } })
  },
  getClassCreditAchievementRanking: (counselorId) => {
    return apiClient.get(`/counselors/analytics/credit-achievement-ranking`, { params: { counselor_id: counselorId } })
  },
  getWarningTrend: (counselorId) => {
    return apiClient.get(`/counselors/analytics/warning-trend`, { params: { counselor_id: counselorId } })
  },
  getAssistancePlanCompletionRate: (counselorId) => {
    return apiClient.get(`/counselors/analytics/assistance-completion`, { params: { counselor_id: counselorId } })
  },
  // ============= 学分监控API =============
  getCreditMonitor: (counselorId) => {
    return apiClient.get(`/counselors/credit-monitor`, { params: { counselor_id: counselorId } })
  },
  getCreditInsufficientStudents: (counselorId, page = 1, size = 20) => {
    return apiClient.get(`/counselors/credit-insufficient`, { params: { counselor_id: counselorId, page, size } })
  },
  // ============= 班级管理API =============
  getMyClasses: (counselorId) => {
    return apiClient.get(`/counselors/class-management/my-classes`, { params: { counselorId } })
  },
  searchClasses: (keyword) => {
    return apiClient.get(`/counselors/class-management/search`, { params: { keyword } })
  },
  getClassRequests: (counselorId) => {
    return apiClient.get(`/counselors/class-management/requests`, { params: { counselorId } })
  },
  submitClassManagementRequest: (data) => {
    return apiClient.post(`/counselors/class-management/apply`, data)
  },
  cancelClassManagement: (data) => {
    return apiClient.post(`/counselors/class-management/cancel`, data)
  },
  // 获取辅导员班级申请列表（供管理员使用）
  getClassApplications: (params) => {
    return apiClient.get(`/counselors/class-management/applications`, { params })
  },
  // 审核辅导员班级申请（供管理员使用）
  reviewClassApplication: (applicationId, data) => {
    return apiClient.post(`/counselors/class-management/applications/${applicationId}/review`, data)
  },
  // ============= 沟通记录API =============
  getCommunicationLogs: (counselorId) => {
    return apiClient.get(`/counselors/${counselorId}/communication-logs`)
  },
  getStudentCommunicationLogs: (studentId) => {
    return apiClient.get(`/counselors/students/${studentId}/communication-logs`)
  },
  createCommunicationLog: (data) => {
    return apiClient.post(`/counselors/communication-logs`, data)
  },
  updateCommunicationLog: (id, data) => {
    return apiClient.put(`/counselors/communication-logs/${id}`, data)
  },
  deleteCommunicationLog: (id) => {
    return apiClient.delete(`/counselors/communication-logs/${id}`)
  }
}

export const aiAPI = {
  getResponse: (userId, message) => {
    return apiClient.post(`/ai/chat`, { userId, message })
  },
  getSuggestions: (userId) => {
    return apiClient.get(`/ai/suggestions/${userId}`)
  },
  // 成绩分析
  analyzeScores: (studentId) => {
    return apiClient.get(`/ai/analyze-scores/${studentId}`)
  },
  // 行为/趋势分析
  analyzeBehavior: (data) => {
    return apiClient.post(`/ai/analyze-behavior`, data)
  },
  // 风险预测
  predictRisk: (data) => {
    return apiClient.post(`/ai/predict-risk`, data)
  },
  assessRisk: (studentId) => {
    return apiClient.get(`/ai/assess-risk/${studentId}`)
  },
  // 生成预警建议
  generateWarning: (data) => {
    return apiClient.post(`/ai/generate-warning`, data)
  },
  predictWarnings: (studentId) => {
    return apiClient.get(`/ai/predict-warnings/${studentId}`)
  },
  // 预警分析
  analyzeWarningTrend: (counselorId) => {
    return apiClient.get(`/ai/analyze-warning-trend/${counselorId}`)
  },
  analyzeWarning: (warningId) => {
    return apiClient.get(`/ai/analyze-warning/${warningId}`)
  },
  // 班级成绩分析
  analyzeClassScores: (classId) => {
    return apiClient.get(`/ai/analyze-class-scores/${classId}`)
  },
  // 生成学习建议/计划
  generateSuggestion: (data) => {
    return apiClient.post(`/ai/generate-suggestion`, data)
  },
  generatePlan: (data) => {
    return apiClient.post(`/ai/generate-plan`, data)
  },
  // 综合分析
  analyze: (studentId) => {
    return apiClient.get(`/ai/analyze/${studentId}`)
  },
  // 推荐
  getRecommendations: (studentId) => {
    return apiClient.get(`/ai/recommendations/${studentId}`)
  },
  getWarningSuggestions: (studentId) => {
    return apiClient.get(`/ai/warning-suggestions/${studentId}`)
  }
}

export const adminAPI = {
  register: (data) => {
    return apiClient.post(`/admin/register`, data)
  },
  // 新增用户走 /users/add，不用 /auth/register/*：register 在"用户名已存在"这类
  // 业务失败时**返回错误字符串但仍包成 code:200 的成功响应**，前端会提示"添加成功"。
  // /users/add 重名直接抛异常，前端能正确显示失败。
  createUser: (data) => {
    return apiClient.post('/users/add', data)
  },
  // ---- 管理员本人的账号级自助设置 ----
  // 与 student/teacher/counselor 用的是同两条路径（users 表是账号属性的唯一权威源）：
  //   改姓名/邮箱/电话 → PUT /users/update/{自己的id}
  //   改密码（要验旧密码）→ POST /auth/change-password
  // 2026-09-30 补：管理员账号以前**两条都没有** —— `AdminSettings.vue` 的
  // 「保存」和「修改密码」只在 localStorage 里改、然后直接弹成功，一个请求都不发。
  // 用户名/姓名/邮箱在 loadAdminInfo 里优先读 localStorage，所以页面看着"改成功了"，
  // 库里那行一个字都没动，刷新后（或换台机器）就变回去。
  changePassword: ({ userId, oldPassword, newPassword }) => {
    return apiClient.post('/auth/change-password', { userId, oldPassword, newPassword })
  },
  updateProfile: (userId, data) => {
    return apiClient.put(`/users/update/${userId}`, data)
  },
  getDashboard: () => {
    return apiClient.get(`/admin/dashboard`)
  },
  getColleges: () => {
    return apiClient.get(`/admin/colleges`)
  },
  createCollege: (data) => {
    return apiClient.post(`/admin/colleges`, data)
  },
  updateCollege: (collegeId, data) => {
    return apiClient.put(`/admin/colleges/${collegeId}`, data)
  },
  deleteCollege: (collegeId) => {
    return apiClient.delete(`/admin/colleges/${collegeId}`)
  },
  getMajorsByCollege: (collegeId) => {
    return apiClient.get(`/admin/colleges/${collegeId}/majors`)
  },
  getMajors: () => {
    return apiClient.get(`/admin/majors`)
  },
  createMajor: (data) => {
    return apiClient.post(`/admin/majors`, data)
  },
  updateMajor: (majorId, data) => {
    return apiClient.put(`/admin/majors/${majorId}`, data)
  },
  deleteMajor: (majorId) => {
    return apiClient.delete(`/admin/majors/${majorId}`)
  },
  getUsers: (page = 1, size = 10, collegeId = null, role = null) => {
    const params = { page, size }
    if (collegeId !== null) params.collegeId = collegeId
    if (role !== null) params.role = role
    console.log('API请求参数:', params)
    return apiClient.get(`/admin/users/page`, { params })
  },
  disableUser: (userId) => {
    return apiClient.post(`/admin/users/${userId}/toggle-status`)
  },
  enableUser: (userId) => {
    return apiClient.post(`/admin/users/${userId}/toggle-status`)
  },
  getRules: () => {
    return apiClient.get(`/admin/rules`)
  },
  createRule: (data) => {
    return apiClient.post(`/admin/rules`, data)
  },
  updateRule: (ruleId, data) => {
    return apiClient.put(`/admin/rules/${ruleId}`, data)
  },
  deleteRule: (ruleId) => {
    return apiClient.delete(`/admin/rules/${ruleId}`)
  },
  getStatistics: () => {
    return apiClient.get(`/admin/statistics`)
  },
  exportStatistics: () => {
    return apiClient.get(`/admin/export/statistics`, { responseType: 'blob' })
  },
  getPendingRequests: () => {
    return apiClient.get(`/admin/pending-requests`)
  },
  getCourses: () => {
    return apiClient.get(`/admin/courses`)
  },
  createCourse: (data) => {
    return apiClient.post(`/admin/courses`, data)
  },
  updateCourse: (courseId, data) => {
    return apiClient.put(`/admin/courses/${courseId}`, data)
  },
  deleteCourse: (courseId) => {
    return apiClient.delete(`/admin/courses/${courseId}`)
  },
  getCourseRequirements: () => {
    // 临时返回空数组，后端需实现该端点
    return Promise.resolve([])
  },
  exportInsufficientStudents: () => {
    return apiClient.get(`/admin/export/insufficient-students`, { responseType: 'blob' })
  },
  exportColleges: () => {
    return apiClient.get(`/admin/export/colleges`, { responseType: 'blob' })
  },
  exportTeachers: () => {
    return apiClient.get(`/admin/export/teachers`, { responseType: 'blob' })
  },
  exportUsers: () => {
    return apiClient.get(`/admin/export/users`, { responseType: 'blob' })
  },
  updateUser: (userId, data) => {
    return apiClient.put(`/admin/users/${userId}`, data)
  },
  deleteUser: (userId) => {
    return apiClient.delete(`/admin/users/${userId}`)
  },
  resetPassword: (userId) => {
    return apiClient.post(`/admin/users/${userId}/reset-password`)
  },
  // viewPassword 已删（2026-09-30）：`GET /admin/users/{id}/password` 后端**没有这个端点**，
  // 实测 404（admin-service 只有 POST /users/{id}/reset-password）。密码存的是 BCrypt 哈希，
  // "查看"本身也没有意义，管理员的正当操作是重置。
  // ============= 权限管理API =============
  getRoles: () => {
    return apiClient.get(`/admin/permissions/roles`)
  },
  getPermissions: () => {
    return apiClient.get(`/admin/permissions/list`)
  },
  getUserPermissions: (userId) => {
    return apiClient.get(`/admin/permissions/user/${userId}`)
  },
  assignRole: (data) => {
    return apiClient.post(`/admin/permissions/assign-role`, data)
  },
  toggleUserStatus: (userId) => {
    return apiClient.post(`/admin/users/${userId}/toggle-status`)
  },
  approveUser: (userId) => {
    return apiClient.put(`/admin/users/${userId}/approve`)
  },
  getLoginLogs: (userId) => {
    return apiClient.get(`/admin/login-logs/${userId}`)
  },
  getUserById: (userId) => {
    return apiClient.get(`/admin/users/${userId}`)
  },
  // ============= 消息管理API =============
  broadcastMessage: (data) => {
    return apiClient.post(`/admin/messages/broadcast`, data)
  },
  sendTargetedMessage: (data) => {
    return apiClient.post(`/admin/messages/targeted`, data)
  },
  getMessages: (page = 1, size = 20) => {
    return apiClient.get(`/admin/messages/list`, { params: { page, size } })
  },
  deleteMessage: (messageId) => {
    return apiClient.delete(`/admin/messages/${messageId}`)
  },
  // ============= 任务管理API =============
  getTasks: (page = 1, size = 20, status) => {
    return apiClient.get(`/admin/tasks/list`, { params: { page, size, status } })
  },
  createTask: (data) => {
    return apiClient.post(`/admin/tasks/create`, data)
  },
  updateTaskStatus: (taskId, status) => {
    return apiClient.post(`/admin/tasks/${taskId}/status`, { status })
  },
  deleteTask: (taskId) => {
    return apiClient.delete(`/admin/tasks/${taskId}`)
  },
  // ============= 数据导出与API =============
  exportStudents: () => {
    return apiClient.get(`/admin/export/students`, { responseType: 'blob' })
  },
  exportScores: (courseId) => {
    return apiClient.get(`/admin/export/scores`, { params: { course_id: courseId }, responseType: 'blob' })
  },
  // ============= 预警管理API =============
  getAllWarnings: () => {
    return apiClient.get(`/admin/warnings`)
  },
  processWarning: (warningId, data) => {
    return apiClient.put(`/admin/warnings/${warningId}/handle`, data)
  },
  exportWarnings: () => {
    return apiClient.get(`/admin/export/warnings`, { responseType: 'blob' })
  },
  exportUsers: (role) => {
    return apiClient.get(`/admin/export/users`, { params: { role }, responseType: 'blob' })
  },
  importStudents: (data) => {
    return apiClient.post(`/admin/import/students`, data)
  },
  importScores: (data) => {
    return apiClient.post(`/admin/import/scores`, data)
  },
  downloadTemplate: (type) => {
    return apiClient.get(`/admin/import/template/${type}`, { responseType: 'blob' })
  },
  // ============= 报表API =============
  getReportTemplates: () => {
    return apiClient.get(`/admin/reports/templates`)
  },
  generateReport: (data) => {
    return apiClient.post(`/admin/reports/generate`, data)
  },
  // ============= 自定义模板API =============
  getCustomReportTemplates: () => {
    return apiClient.get(`/admin/reports/templates/list`)
  },
  deleteCustomTemplate: (templateId) => {
    return apiClient.delete(`/admin/reports/templates/${templateId}`)
  },
  // ============= 数据备份API =============
  backupData: () => {
    return apiClient.post(`/admin/backup`)
  },
  getBackupList: () => {
    return apiClient.get(`/admin/backups/list`)
  },
  restoreBackup: (backupId) => {
    return apiClient.post(`/admin/backup/${backupId}/restore`)
  },
  getExportHistory: (page = 1, pageSize = 10) => {
    return apiClient.get(`/admin/export/history`, { params: { page, size: pageSize } })
  },
  deleteExport: (exportId) => {
    return apiClient.delete(`/admin/export/${exportId}`)
  },
  getActivities: () => {
    return apiClient.get(`/admin/activities`)
  },
  // ============= 班级管理申请API =============
  getAllClassManagementRequests: () => {
    return apiClient.get(`/admin/class-management/applications?size=999`)
  },
  getPendingClassManagementRequests: () => {
    return apiClient.get(`/admin/class-management/pending-requests`)
  },
  approveClassManagementRequest: (requestId, type = 'teacher') => {
    return apiClient.post(`/admin/class-management/approve/${requestId}?type=${type}`)
  },
  rejectClassManagementRequest: (requestId, reason, type = 'teacher') => {
    return apiClient.post(`/admin/class-management/reject/${requestId}?type=${type}`, { reason })
  },
  removeTeacherFromClass: (classId) => {
    return apiClient.post(`/admin/class-management/remove-teacher/${classId}`)
  },
  getAllClasses: () => {
    return apiClient.get(`/admin/classes`)
  }
}

export const collegeAdminAPI = {
  getProfile: (userId) => apiClient.get(`/college-admin/profile/${userId}`),
  getDashboard: (userId) => apiClient.get(`/college-admin/dashboard/${userId}`),
  getClasses: (userId) => apiClient.get(`/college-admin/classes/${userId}`),
  getStudents: (userId, keyword) => {
    const qs = keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''
    return apiClient.get(`/college-admin/students/${userId}${qs}`)
  },
  getStudentDetail: (userId, profileId) => apiClient.get(`/college-admin/student-detail/${userId}/${profileId}`),
  getTeachers: (userId) => apiClient.get(`/college-admin/teachers/${userId}`),
  getWarnings: (userId) => apiClient.get(`/college-admin/warnings/${userId}`),
  getScoreStats: (userId) => apiClient.get(`/college-admin/scores-stats/${userId}`),
  getScoreAnalysis: (userId) => apiClient.get(`/college-admin/score-analysis/${userId}`),
  getClassComparison: (userId) => apiClient.get(`/college-admin/class-comparison/${userId}`),
  getCounselors: (userId) => apiClient.get(`/college-admin/counselors/${userId}`),
  getAssistance: (userId) => apiClient.get(`/college-admin/assistance/${userId}`),
  getTeacherDetail: (userId, teacherId) => apiClient.get(`/college-admin/teacher-detail/${userId}/${teacherId}`),
  processWarning: (data) => apiClient.post('/college-admin/process-warning', data),
  deleteWarning: (warningId) => apiClient.del(`/college-admin/delete-warning/${warningId}`),
  getClassStudents: (userId, classId) => apiClient.get(`/college-admin/class-students/${userId}/${classId}`),
  exportData: (userId, type) => apiClient.get(`/college-admin/export/${userId}?type=${type||'scores'}`),
  scoreQuery: (userId, params) => apiClient.get(`/college-admin/score-query/${userId}`, { params }),
}
