# 智能学业预警服务系统 - API 接口文档

> Base URL: `http://localhost:8076/api`  
> 响应格式: `{ code: 200, message: "success", data: ... }` (code=200 表示成功)

---

## 一、认证服务 (Auth)

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|----------|
| POST | `/auth/login` | 登录 | `{ username, password }` |
| POST | `/auth/logout` | 登出 | `?userId&role` |
| POST | `/auth/register/student` | 学生注册 | `{ username, password, name, ... }` |
| POST | `/auth/register/teacher` | 教师注册 | `{ username, password, name, ... }` |
| POST | `/auth/register/counselor` | 辅导员注册 | `{ username, password, name, ... }` |
| GET | `/auth/colleges` | 获取学院列表 | - |
| GET | `/auth/majors` | 按学院获取专业 | `?collegeId` |

> **注册只有上面这三条 `/auth/register/{student,teacher,counselor}`**（2026-10-08 W27 核对）。
> 本文件此前还列着 `POST /auth/register/admin`（2026-10-01 按"零调用方"已删）与 `GET /auth/courses`
> （2026-10-02 W1.15 已删，`auth-service` 本就没有这个映射）—— 两条都是**文档里写着、代码里没有**，已删行。
> 教师/辅导员账号由**管理员审批**（`status=0 待审批 → 1 正常`），没有自助注册管理员的入口。

---

## 二、学生服务 (Student)

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|----------|
| GET | `/students/{id}` | 学生信息 | `@PathVariable id` |
| GET | `/students/student-by-user/{userId}` | 根据userId查学生 | `@PathVariable userId` |
| GET | `/students/{id}/gpa` | 获取GPA | `@PathVariable id` |
| GET | `/students/dashboard/{userId}` | 学生仪表盘 | `@PathVariable userId` |
| GET | `/students/scores/{userId}` | 成绩列表 | `@PathVariable userId, ?semester` |
| GET | `/students/warnings/{userId}` | 预警列表 | `@PathVariable userId` |
| GET | `/students/assistance/{userId}` | 帮扶计划 | `@PathVariable userId` |
| GET | `/students/suggestions/{userId}` | 学习建议 | `@PathVariable userId` |
| GET | `/students/{id}/class-info` | 班级信息 | `@PathVariable id` |
| GET | `/students/class-members/{userId}` | 班级成员 | `@PathVariable userId` |
| GET | `/students/class-ranking/{userId}` | 班级排名 | `@PathVariable userId` |
| GET | `/students/notification-center/{userId}/unread` | 未读通知 | `@PathVariable userId` |
| GET | `/students/notification-center/{userId}/list` | 通知列表 | `@PathVariable userId, ?page, pageSize` |
| POST | `/students/notification-center/{id}/mark-read` | 标记已读 | `@PathVariable id` |
| POST | `/students/notification-center/{userId}/mark-batch-read` | 批量已读 | `@PathVariable userId, [ids]` |
| POST | `/students/notification-center/{id}/delete` | 删除通知 | `@PathVariable id` |
| GET | `/students/notification-center/{userId}/unread-count` | 未读数 | `@PathVariable userId` |
| POST | `/students/notification-center/{userId}/clear-read` | 清除已读 | `@PathVariable userId` |
| GET | `/students/subscription/{studentId}/preferences` | 订阅偏好 | `@PathVariable studentId` |
| POST | `/students/subscription/{studentId}/update` | 更新订阅 | `@PathVariable studentId, body` |
| POST | `/students/subscription/{studentId}/subscribe-level` | 订阅等级 | `@PathVariable studentId, ?level` |
| POST | `/students/subscription/{studentId}/unsubscribe-level` | 取消订阅 | `@PathVariable studentId, ?level` |
| POST | `/students/subscription/{studentId}/set-channel` | 设置推送渠道 | `@PathVariable studentId, ?channel, enabled` |
| GET | `/students/benchmark/{studentId}/latest` | 最新对标 | `@PathVariable studentId` |
| GET | `/students/benchmark/{studentId}/semester` | 按学期对标 | `@PathVariable studentId, ?semester` |
| GET | `/students/benchmark/{studentId}/history` | 历史对标 | `@PathVariable studentId` |
| GET | `/students/benchmark/{studentId}/class-ranking` | 班级排名 | `@PathVariable studentId, ?classId, semester` |
| GET | `/students/benchmark/{studentId}/major-ranking` | 专业排名 | `@PathVariable studentId, ?majorId, semester` |
| GET | `/students/benchmark/class/{classId}/comparison` | 班级对比 | `@PathVariable classId, ?semester` |
| GET | `/students/benchmark/major/{majorId}/comparison` | 专业对比 | `@PathVariable majorId, ?semester` |
| GET | `/students/benchmark/{studentId}/progress-report` | 进度报告 | `@PathVariable studentId` |
| POST | `/students/appeals/submit` | 提交申诉 | `{ studentId, courseId, reason, ... }` |
| GET | `/students/appeals/{studentId}/list` | 申诉列表 | `@PathVariable studentId` |
| GET | `/students/appeals/{studentId}/pending` | 待处理申诉 | `@PathVariable studentId` |
| GET | `/students/appeals/{appealId}/detail` | 申诉详情 | `@PathVariable appealId` |
| POST | `/students/appeals/{appealId}/withdraw` | 撤回申诉 | `@PathVariable appealId, ?studentId` |
| POST | `/students/evaluations/submit` | 提交评价 | `{ planId, ... }` |
| GET | `/students/evaluations/{studentId}/list` | 评价列表 | `@PathVariable studentId` |
| GET | `/students/evaluations/{planId}/detail` | 评价详情 | `@PathVariable planId` |
| GET | `/students/settings/{userId}` | 用户设置 | `@PathVariable userId` |
| PUT | `/students/settings/{userId}` | 更新设置 | `@PathVariable userId, body` |
| GET | `/students/messages/{userId}` | 消息列表 | `@PathVariable userId` |
| GET | `/students/messages/{userId}/unread-count` | 未读消息数 | `@PathVariable userId` |
| POST | `/students/messages` | 发送消息 | `{ teacherId, content, studentId, status }` |
| POST | `/students/messages/{id}/mark-read` | 标记消息已读 | `@PathVariable id` |
| GET | `/students/export/scores/{userId}/excel` | 导出成绩 | `@PathVariable userId` |

---

## 三、教师服务 (Teacher)

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|----------|
| GET | `/teachers/{id}` | 教师信息 | `@PathVariable id` |
| PUT | `/teachers/{id}` | 更新信息 | `@PathVariable id, body` |
| DELETE | `/teachers/{id}` | 删除教师 | `@PathVariable id` |
| GET | `/teachers/dashboard/{userId}` | 教师仪表盘 | `@PathVariable userId` |
| GET | `/teachers/students/{teacherId}` | 学生列表 | `@PathVariable teacherId` |
| GET | `/teachers/courses` | 课程列表 | `?teacher_id` |
| GET | `/teachers/courses/{id}` | 课程详情 | `@PathVariable id` |
| POST | `/teachers/courses` | 创建课程 | body |
| PUT | `/teachers/courses/{id}` | 更新课程 | `@PathVariable id, body` |
| DELETE | `/teachers/courses/{id}` | 删除课程 | `@PathVariable id` |
| POST | `/teachers/courses/recommend` | 推荐课程 | `{ teacherId, courseId, reason }` |
| GET | `/teachers/scores` | 按课程查成绩 | `?course_id` |
| GET | `/teachers/scores/by-class` | 按班级查成绩 | `?class_name` |
| POST | `/teachers/scores` | 保存成绩 | `{ scores: [...], teacherId }` |
| PUT | `/teachers/scores/{id}` | 更新成绩 | `@PathVariable id, { score, remark, modifiedBy }` |
| DELETE | `/teachers/scores/{id}` | 删除成绩 | `@PathVariable id` |
| POST | `/teachers/scores/batch-delete` | 批量删除 | `[id1, id2, ...]` |
| POST | `/teachers/scores/import` | 导入成绩 | `{ scores: [...], teacherId }` |
| POST | `/teachers/scores/warnings` | 触发预警 | `?course_id` |
| GET | `/teachers/scores/audit/{teacherId}` | 审计日志 | `@PathVariable teacherId, ?page, size` |
| GET | `/teachers/enrollments` | 选课记录 | `?teacherId, courseId` |
| GET | `/teachers/warnings/{teacherId}` | 预警列表 | `@PathVariable teacherId, ?status` |
| POST | `/teachers/warnings/{id}/process` | 处理预警 | `@PathVariable id, body` |
| GET | `/teachers/feedbacks` | 反馈列表 | `?teacherId, category` |
| GET | `/teachers/feedbacks/{teacherId}/list` | 分页反馈 | `@PathVariable teacherId, ?category, page, size` |
| POST | `/teachers/feedbacks/{id}/reply` | 回复反馈 | `@PathVariable id, body` |
| POST | `/teachers/communications` | 保存沟通记录 | `{ teacherId, studentId, content, type, studentName }` |
| GET | `/teachers/todos/{teacherId}` | 待办事项 | `@PathVariable teacherId` |
| GET | `/teachers/analysis` | 课程分析 | `?course_id` |
| GET | `/teachers/students/{studentId}/credit-prediction` | 学分预测 | `@PathVariable studentId` |
| GET | `/teachers/scores/analyze` | 成绩分析 | `?course_id` |
| GET | `/teachers/scores/anomalies` | 成绩异常 | `?course_id` |
| GET | `/teachers/scores/student/analyze` | 学生成绩分析 | `?student_id, course_id` |
| GET | `/teachers/courses/{courseId}/distribution` | 成绩分布 | `@PathVariable courseId` |
| GET | `/teachers/courses/{courseId}/anomaly` | 课程异常 | `@PathVariable courseId, ?threshold` |
| GET | `/teachers/courses/{courseId}/students` | 课程学生 | `@PathVariable courseId, ?page, size` |
| GET | `/teachers/courses/{courseId}/recommendations` | 课程建议 | `@PathVariable courseId, ?limit` |
| GET | `/teachers/class-management/my-classes` | 我的班级 | `?teacherId` |
| GET | `/teachers/class-management/search` | 搜索班级 | `?keyword` |
| GET | `/teachers/class-management/requests` | 班级申请 | `?teacherId` |
| POST | `/teachers/class-management/apply` | 提交申请 | `{ teacherId, classId, reason, className }` |
| POST | `/teachers/class-management/cancel` | 取消管理 | `{ teacherId, classId }` |
| GET | `/teachers/classes/{classId}/analysis` | 班级分析 | `@PathVariable classId` |
| GET | `/teachers/classes/{classId}/students` | 班级学生 | `@PathVariable classId` |
| GET | `/teachers/list` | 教师列表 | - |
| GET | `/teachers/messages/{userId}` | 消息 | `@PathVariable userId` |
| GET | `/teachers/messages/{userId}/unread-count` | 未读消息数 | `@PathVariable userId` |
| POST | `/teachers/messages/{id}/mark-read` | 标记已读 | `@PathVariable id` |
| GET | `/teachers/counselor/by-class` | 获取辅导员 | `?class_name` |
| POST | `/teachers/students/import` | 导入学生 | MultipartFile |

---

## 四、辅导员服务 (Counselor)

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|----------|
| GET | `/counselors/dashboard/{userId}` | 仪表盘 | `@PathVariable userId` |
| GET | `/counselors` | 辅导员列表 | `?collegeId` |
| GET | `/counselors/{counselorId}` | 辅导员信息 | `@PathVariable counselorId` |
| PUT | `/counselors/{id}` | 更新信息 | `@PathVariable id, body` |
| DELETE | `/counselors/{id}` | 删除辅导员 | `@PathVariable id` |
| GET | `/counselors/students` | 学生列表 | `?counselor_id` |
| GET | `/counselors/{counselorId}/students` | 学生列表(路径) | `@PathVariable counselorId` |
| GET | `/counselors/students/{studentId}` | 学生详情 | `@PathVariable studentId` |
| PUT | `/counselors/students/{studentId}` | 更新学生 | `@PathVariable studentId, body` |
| GET | `/counselors/warnings` | 预警列表 | `?counselor_id, status` |
| POST | `/counselors/warnings/{id}/process` | 处理预警 | `@PathVariable id, { handleResult }` |
| POST | `/counselors/warnings/batch-process` | 批量处理 | `[id1, id2, ...]` |
| POST | `/counselors/students/notify` | 通知学生 | body |
| GET | `/counselors/enrollments` | 选课信息 | `?counselor_id` |
| POST | `/counselors/assistance-plans` | 创建帮扶计划 | body |
| PUT | `/counselors/assistance-plans/{id}` | 更新计划 | `@PathVariable id, body` |
| DELETE | `/counselors/assistance-plans/{id}` | 删除计划 | `@PathVariable id` |
| PATCH | `/counselors/assistance-plans/{id}/status` | 更新状态 | `@PathVariable id, { status }` |
| GET | `/counselors/students/{studentId}/assistance-plans` | 学生计划 | `@PathVariable studentId` |
| GET | `/counselors/{counselorId}/assistance-plans` | 辅导员计划 | `@PathVariable counselorId` |
| POST | `/counselors/communication-logs` | 创建沟通记录 | body |
| GET | `/counselors/students/{studentId}/communication-logs` | 学生沟通记录 | `@PathVariable studentId` |
| GET | `/counselors/{counselorId}/communication-logs` | 辅导员沟通记录 | `@PathVariable counselorId` |
| PUT | `/counselors/communication-logs/{id}` | 更新沟通记录 | `@PathVariable id, body` |
| DELETE | `/counselors/communication-logs/{id}` | 删除沟通记录 | `@PathVariable id` |
| GET | `/counselors/scores/class/{classId}` | 班级成绩 | `@PathVariable classId, ?counselor_id` |
| GET | `/counselors/scores/distribution/{courseId}` | 成绩分布 | `@PathVariable courseId, ?counselor_id` |
| GET | `/counselors/scores/low-score` | 低分学生 | `?counselor_id` |
| GET | `/counselors/scores/student/{studentId}/trend` | 学生成绩趋势 | `@PathVariable studentId` |
| GET | `/counselors/credit-monitor` | 学分监控 | `?counselor_id` |
| GET | `/counselors/credit-insufficient` | 学分不足列表 | `?counselor_id, page, size` |
| GET | `/counselors/analytics/credit-insufficient` | 学分不足率 | `?counselor_id` |
| GET | `/counselors/analytics/warning-distribution` | 预警分布 | `?counselor_id` |
| GET | `/counselors/analytics/handling-efficiency` | 处理效率 | `?counselor_id` |
| GET | `/counselors/analytics/credit-achievement-ranking` | 学分达标排名 | `?counselor_id` |
| GET | `/counselors/analytics/warning-trend` | 预警趋势 | `?counselor_id` |
| GET | `/counselors/analytics/assistance-completion` | 帮扶完成率 | `?counselor_id` |
| GET | `/counselors/classes` | 班级列表 | `?counselor_id` |
| GET | `/counselors/classes/activities` | 班级活动 | `?counselor_id` |
| GET | `/counselors/classes/{classId}/detail` | 班级详情 | `@PathVariable classId` |
| GET | `/counselors/classes/{classId}/students` | 班级学生 | `@PathVariable classId` |
| GET | `/counselors/classes/{classId}/warnings` | 班级预警 | `@PathVariable classId` |
| GET | `/counselors/classes/warnings/compare` | 班级预警对比 | `?counselor_id` |
| GET | `/counselors/class-management/my-classes` | 我的班级 | `?counselorId` |
| GET | `/counselors/class-management/search` | 搜索班级 | `?keyword` |
| GET | `/counselors/class-management/requests` | 班级申请 | `?counselorId` |
| POST | `/counselors/class-management/apply` | 申请班级 | `{ counselorId, classId, className, reason }` |
| POST | `/counselors/class-management/cancel` | 解除管理 | `{ counselorId, classId }` |
| GET | `/counselors/class-management/without-counselor` | 无辅导员班级 | - |
| GET | `/counselors/class-management/applications` | 申请列表(管理端) | `?status` |
| POST | `/counselors/class-management/applications/{id}/review` | 审核申请 | `@PathVariable id, { status }` |
| GET | `/counselors/notifications/history` | 通知历史 | `?counselor_id, page, size` |
| GET | `/counselors/notifications/templates` | 通知模板 | - |
| GET | `/counselors/notifications/weekly-count` | 每周通知数 | `?counselor_id` |
| GET | `/counselors/count-by-college` | 按学院统计 | `?collegeId` |

---

## 五、管理员服务 (Admin)

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|----------|
| POST | `/admin/register` | 管理员注册 | body |
| GET | `/admin/dashboard` | 仪表盘 | - |
| GET | `/admin/statistics` | 统计数据 | - |
| GET | `/admin/pending-requests` | 待处理请求 | - |
| GET | `/admin/colleges` | 学院列表 | - |
| GET | `/admin/colleges/{id}` | 学院详情 | `@PathVariable id` |
| POST | `/admin/colleges` | 创建学院 | body |
| PUT | `/admin/colleges/{id}` | 更新学院 | `@PathVariable id, body` |
| DELETE | `/admin/colleges/{id}` | 删除学院 | `@PathVariable id` |
| GET | `/admin/majors` | 专业列表 | - |
| GET | `/admin/majors/{id}` | 专业详情 | `@PathVariable id` |
| POST | `/admin/majors` | 创建专业 | body |
| PUT | `/admin/majors/{id}` | 更新专业 | `@PathVariable id, body` |
| DELETE | `/admin/majors/{id}` | 删除专业 | `@PathVariable id` |
| GET | `/admin/users` | 用户列表 | `?page, size, collegeId, role` |
| GET | `/admin/users/all` | 全部用户 | - |
| GET | `/admin/users/{id}` | 用户详情 | `@PathVariable id` |
| POST | `/admin/users` | 创建用户 | body |
| PUT | `/admin/users/{id}` | 更新用户 | `@PathVariable id, body` |
| DELETE | `/admin/users/{id}` | 删除用户 | `@PathVariable id` |
| POST | `/admin/users/{id}/toggle-status` | 禁用/启用 | `@PathVariable id` |
| PUT | `/admin/users/{id}/approve` | 审核通过 | `@PathVariable id` |
| POST | `/admin/users/{id}/reset-password` | 重置密码 | `@PathVariable id` |
| GET | `/admin/login-logs/{userId}` | 登录日志 | `@PathVariable userId` |
| GET | `/admin/courses` | 课程列表 | - |
| GET | `/admin/courses/{id}` | 课程详情 | `@PathVariable id` |
| POST | `/admin/courses` | 创建课程 | body |
| PUT | `/admin/courses/{id}` | 更新课程 | `@PathVariable id, body` |
| DELETE | `/admin/courses/{id}` | 删除课程 | `@PathVariable id` |
| GET | `/admin/rules` | 规则列表 | - |
| GET | `/admin/rules/{id}` | 规则详情 | `@PathVariable id` |
| POST | `/admin/rules` | 创建规则 | body |
| PUT | `/admin/rules/{id}` | 更新规则 | `@PathVariable id, body` |
| DELETE | `/admin/rules/{id}` | 删除规则 | `@PathVariable id` |
| GET | `/admin/warnings` | 全部预警 | - |
| PUT | `/admin/warnings/{id}/handle` | 处理预警 | `@PathVariable id, body` |
| GET | `/admin/activities` | 操作日志 | - |
| GET | `/admin/export/statistics` | 导出统计 | - |
| GET | `/admin/export/insufficient-students` | 导出学分不足 | - |
| GET | `/admin/export/colleges` | 导出学院 | - |
| GET | `/admin/export/teachers` | 导出教师 | - |
| GET | `/admin/export/users` | 导出用户 | `?role` |
| GET | `/admin/export/students` | 导出学生 | - |
| GET | `/admin/export/scores` | 导出成绩 | `?course_id` |
| GET | `/admin/export/warnings` | 导出预警 | - |
| GET | `/admin/export/history` | 导出历史 | `?page, size` |
| POST | `/admin/import/students` | 导入学生 | body |
| POST | `/admin/import/scores` | 导入成绩 | body |
| GET | `/admin/import/template/{type}` | 下载模板 | `@PathVariable type` |
| GET | `/admin/reports/templates` | 报表模板 | - |
| POST | `/admin/reports/generate` | 生成报表 | body |
| POST | `/admin/backup` | 备份数据 | - |
| GET | `/admin/backups/list` | 备份列表 | - |
| POST | `/admin/backup/{id}/restore` | 恢复备份 | `@PathVariable id` |
| GET | `/admin/permissions/roles` | 角色列表 | - |
| GET | `/admin/permissions/list` | 权限列表 | - |
| GET | `/admin/permissions/user/{userId}` | 用户权限 | `@PathVariable userId` |
| POST | `/admin/permissions/assign-role` | 分配角色（**仅系统管理员 role 4**；body `{userId, role}`，`role=5` 时**必须**再带 `collegeId`） | body |
| POST | `/admin/messages/broadcast` | 群发消息 | body |
| POST | `/admin/messages/targeted` | 定向消息 | body |
| GET | `/admin/messages/list` | 消息列表 | `?page, size` |
| DELETE | `/admin/messages/{id}` | 删除消息 | `@PathVariable id` |
| GET | `/admin/tasks/list` | 任务列表 | `?page, size, status` |
| POST | `/admin/tasks/create` | 创建任务 | body |
| POST | `/admin/tasks/{id}/status` | 更新任务状态 | `@PathVariable id, { status }` |
| DELETE | `/admin/tasks/{id}` | 删除任务 | `@PathVariable id` |
| GET | `/admin/class-management/applications` | 班级申请列表 | `?size` |
| GET | `/admin/class-management/pending-requests` | 待处理申请 | - |
| POST | `/admin/class-management/approve/{id}` | 批准申请 | `@PathVariable id, ?type` |
| POST | `/admin/class-management/reject/{id}` | 拒绝申请 | `@PathVariable id, ?type, { reason }` |
| POST | `/admin/class-management/remove-teacher/{id}` | 移除教师 | `@PathVariable id` |
| GET | `/admin/classes` | 全部班级 | - |

---

## 六、AI 服务

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|----------|
| POST | `/ai/chat` | AI对话 | `{ userId, message }` |
| GET | `/ai/suggestions/{userId}` | 学习建议 | `@PathVariable userId` |
| GET | `/ai/analyze-scores/{studentId}` | 成绩分析 | `@PathVariable studentId` |
| POST | `/ai/analyze-behavior` | 行为分析 | body |
| POST | `/ai/predict-risk` | 风险预测 | body |
| GET | `/ai/assess-risk/{studentId}` | 风险评估 | `@PathVariable studentId` |
| POST | `/ai/generate-warning` | 生成预警 | body |
| GET | `/ai/predict-warnings/{studentId}` | 预测预警 | `@PathVariable studentId` |
| GET | `/ai/analyze-warning-trend/{counselorId}` | 预警趋势 | `@PathVariable counselorId` |
| GET | `/ai/analyze-warning/{warningId}` | 预警分析 | `@PathVariable warningId` |
| GET | `/ai/analyze-class-scores/{classId}` | 班级成绩分析 | `@PathVariable classId` |
| POST | `/ai/generate-suggestion` | 生成建议 | body |
| POST | `/ai/generate-plan` | 生成计划 | body |
| GET | `/ai/analyze/{studentId}` | 综合分析 | `@PathVariable studentId` |
| GET | `/ai/recommendations/{studentId}` | 推荐 | `@PathVariable studentId` |
| GET | `/ai/warning-suggestions/{studentId}` | 预警建议 | `@PathVariable studentId` |

---

## 通用说明

- **认证**: 除 `/auth/*` 外，所有请求需带 `Authorization: Bearer {token}` 头
- **角色**: 1=学生, 2=教师, 3=辅导员, 4=管理员
- **状态**: 0=待处理/待审核, 1=已处理/进行中, 2=已完成/已解除
- **预警等级**: 1=蓝色(轻度), 2=黄色(中度), 3=红色(严重)
- **分页**: 默认 page=1, size=20
