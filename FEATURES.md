# 智学预警云服务平台 — 功能文档

> 基于 SpringBoot + Spring Cloud Alibaba 微服务架构的高校智能学业预警管理系统  
> 版本：1.0.0 | 更新日期：2026-06-04

---

## 一、系统概述

智学预警云服务平台是一套面向高校的智能化学生学业管理系统，覆盖**学生、教师（班主任）、辅导员、管理员**四类角色。系统以学业预警为核心，打通"成绩管理 → 智能预警 → 帮扶计划 → 数据分析"的完整闭环。

### 技术架构

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5 + Spring Cloud 2023.0.3 |
| 服务治理 | Nacos（注册中心 + 配置中心） |
| 网关 | Spring Cloud Gateway（限流 + 熔断 + CORS） |
| 服务调用 | OpenFeign + LoadBalancer |
| 数据库 | MySQL 8.0 + MyBatis-Plus 3.5.6 |
| 缓存/Session | Redis + Spring Session |
| 消息队列 | RabbitMQ（预警消息异步推送） |
| 限流降级 | Sentinel + Resilience4j |
| 认证鉴权 | JWT (jjwt 0.12.5) |
| AI 引擎 | 通义千问 (DashScope API, qwen-plus) |
| 前端 | Vue 3 + Element Plus + Axios |
| 小程序 | UniApp (Vue 3) |
| 实时通信 | WebSocket + STOMP |

### 微服务架构

```
Gateway (8076)
  ├── Auth Service (8072)        登录认证、JWT签发、注册
  ├── User Service (8092)        用户CRUD
  ├── Student Service (8079)     学生端全功能
  ├── Teacher Service (8090)     教师端全功能
  ├── Counselor Service (8074)   辅导员端全功能
  ├── Admin Service (8070)       管理员端全功能
  ├── College Service (8073)     学院管理
  ├── Course Service (8075)      课程管理
  ├── Warning Service (8093)     预警规则 + 定时扫描
  ├── Message Service (8077)     消息推送 + WebSocket
  ├── Resource Service (8078)    学习资源管理
  └── AI Service (8071)          通义千问智能分析
```

---

## 二、角色功能矩阵

| 功能模块 | 学生 | 教师 | 辅导员 | 管理员 |
|:---|:---:|:---:|:---:|:---:|
| 登录/注册 | ✅ | ✅ | ✅ | ✅ |
| 个人仪表盘 | ✅ | ✅ | ✅ | ✅ |
| 成绩查看 | ✅ | ✅ 录入/管理 | ✅ 查看 | ✅ |
| 学业预警 | ✅ 查看 | ✅ 查看 | ✅ 处理 | ✅ 规则配置 |
| 帮扶计划 | ✅ 查看 | — | ✅ 创建/管理 | ✅ |
| 成绩申诉 | ✅ 提交 | — | — | ✅ 审核 |
| 班级管理 | — | ✅ | ✅ | ✅ |
| 课程管理 | — | ✅ 查看 | ✅ | ✅ CRUD |
| 用户管理 | — | — | — | ✅ |
| 数据分析 | ✅ | ✅ | ✅ | ✅ |
| AI 智能分析 | ✅ | ✅ | ✅ | ✅ |
| 消息通知 | ✅ | ✅ | ✅ | ✅ 广播 |
| 系统设置 | ✅ 个人 | — | — | ✅ 全局 |

---

## 三、核心功能模块详解

### 3.1 认证鉴权模块 (Auth Service)

| 功能 | 端点 | 说明 |
|------|------|------|
| 用户登录 | `POST /auth/login` | 账号+密码+验证码，返回JWT Token |
| 用户登出 | `POST /auth/logout` | 清除Token |
| 学生注册 | `POST /auth/register/student` | 创建 users 记录 + student_profile 记录 |
| 教师注册 | `POST /auth/register/teacher` | 创建 users 记录 + teacher_profile 记录 |
| 辅导员注册 | `POST /auth/register/counselor` | 创建 users 记录 + counselor_profiles 记录 |
| 管理员注册 | `POST /auth/register/admin` | 创建 users 记录 |
| 学院列表 | `GET /auth/colleges` | 注册时选择学院 |
| 专业列表 | `GET /auth/majors?collegeId=` | 根据学院获取专业 |
| 课程列表 | `GET /auth/courses` | 初始化课程数据 |

**权限模型：** RBAC (Role-Based Access Control)，角色分为 1-学生 / 2-教师 / 3-辅导员 / 4-管理员。

**安全机制：**
- JWT Token 认证，过期自动刷新
- Gateway 统一鉴权，按角色限制路径前缀
- 密码 MD5 加密存储
- 前端路由守卫 + 后端双层拦截

---

### 3.2 学生端功能 (Student Service)

#### 3.2.1 学习仪表盘

| 端点 | 说明 |
|------|------|
| `GET /students/dashboard/{studentId}` | 综合仪表盘：GPA、挂科数、预警数、学期排名、课程列表 |

#### 3.2.2 成绩管理

| 端点 | 说明 |
|------|------|
| `GET /students/scores/{studentId}` | 查询个人成绩（支持按学期筛选） |
| `GET /students/scores/{studentId}/gpa` | 查询GPA及历史趋势 |
| `GET /students/export/scores/{studentId}/excel` | 导出成绩单为Excel（真 xlsx；`{studentId}` 是 `users.id`，只允许导出本人） |
| `GET /students/{studentId}/class-info` | 获取班级信息 |
| `GET /students/class-members/{studentId}` | 获取班级成员列表 |
| `GET /students/class-ranking/{studentId}` | 获取班级排名 |

#### 3.2.3 学业预警

| 端点 | 说明 |
|------|------|
| `GET /students/warnings/{studentId}` | 查看个人预警列表（等级/类型/状态） |

#### 3.2.4 帮扶计划

| 端点 | 说明 |
|------|------|
| `GET /students/assistance/{studentId}` | 查看帮扶计划及进度 |
| `POST /students/assistance/{planId}/progress` | 更新个人进度 |

#### 3.2.5 成绩申诉

| 端点 | 说明 |
|------|------|
| `POST /students/appeals/submit` | 提交成绩申诉 |
| `GET /students/appeals/{studentId}/list` | 查看申诉列表 |
| `GET /students/appeals/{studentId}/pending` | 查看待处理申诉 |
| `GET /students/appeals/{appealId}/detail` | 查看申诉详情 |
| `POST /students/appeals/{appealId}/withdraw` | 撤回申诉 |
| `GET /students/appeals/{studentId}/statistics` | 申诉统计 |

#### 3.2.6 对标分析

| 端点 | 说明 |
|------|------|
| `GET /students/benchmark/{studentId}/class-ranking` | 班级内排名 |
| `GET /students/benchmark/{studentId}/major-ranking` | 专业内排名 |
| `GET /students/benchmark/class/{classId}/comparison` | 班级成绩分布对比 |
| `GET /students/benchmark/major/{majorId}/comparison` | 专业成绩分布对比 |
| `GET /students/benchmark/{studentId}/progress-report` | 学习进步报告 |

#### 3.2.7 通知与消息

| 端点 | 说明 |
|------|------|
| `GET /students/notification-center/{userId}/unread` | 未读通知 |
| `GET /students/notification-center/{userId}/list` | 通知列表（分页） |
| `POST /students/notification-center/{id}/mark-read` | 标记已读 |
| `POST /students/notification-center/{userId}/mark-batch-read` | 批量已读 |
| `POST /students/notification-center/{userId}/clear-read` | 清空已读 |

#### 3.2.8 个人设置

| 端点 | 说明 |
|------|------|
| `GET /students/settings/{studentId}` | 获取个人设置 |
| `PUT /students/settings/{studentId}` | 更新个人设置 |
| `POST /students/settings/{studentId}/privacy-level` | 设置隐私等级 |

---

### 3.3 教师端功能 (Teacher Service)

#### 3.3.1 工作仪表盘

| 端点 | 说明 |
|------|------|
| `GET /teachers/dashboard/{teacherId}` | 教师仪表盘：班级概况、待处理事项 |

#### 3.3.2 成绩管理

| 端点 | 说明 |
|------|------|
| `GET /teachers/scores` | 按课程查询成绩 |
| `POST /teachers/scores` | 录入/保存成绩 |
| `PUT /teachers/scores/{id}` | 修改单条成绩 |
| `POST /teachers/scores/import` | 批量导入Excel成绩 |
| `GET /teachers/export/scores?course_id=` | 导出该课程成绩单为真 xlsx |
| `GET /teachers/enrollments` | 查看选课情况 |

#### 3.3.3 班级管理

| 端点 | 说明 |
|------|------|
| `GET /teachers/class-management/my-classes` | 我的班级列表 |
| `POST /teachers/class-management/apply` | 申请管理班级 |
| `POST /teachers/class-management/cancel` | 取消班级管理 |

#### 3.3.4 学生管理

| 端点 | 说明 |
|------|------|
| `GET /teachers/students/{teacherId}` | 获取所教学生列表 |

#### 3.3.5 辅导员联动

| 端点 | 说明 |
|------|------|
| `GET /teachers/counselor/by-class?class_name=` | 查询班级辅导员信息 |
| `POST /teachers/communications` | 向辅导员发送沟通记录 |

#### 3.3.6 反馈管理

| 端点 | 说明 |
|------|------|
| `GET /teachers/feedbacks` | 获取学生反馈 |
| `PUT /teachers/feedbacks/{id}` | 回复反馈 |

---

### 3.4 辅导员端功能 (Counselor Service)

#### 3.4.1 工作仪表盘

| 端点 | 说明 |
|------|------|
| `GET /counselors/dashboard/{counselorId}` | 辅导员仪表盘：管理班级数、预警学生数、帮扶进度 |

#### 3.4.2 预警管理

| 端点 | 说明 |
|------|------|
| `GET /counselors/warnings/{counselorId}` | 查看管理班级的预警列表 |
| `POST /counselors/warnings/{id}/process` | 处理预警（记录处理意见） |
| `POST /counselors/warnings/{id}/release` | 解除预警 |

#### 3.4.3 帮扶计划

| 端点 | 说明 |
|------|------|
| `GET /counselors/assistance-plans/{counselorId}` | 帮扶计划列表 |
| `POST /counselors/assistance-plans` | 创建帮扶计划 |
| `PUT /counselors/assistance-plans/{id}` | 更新帮扶计划 |
| `PATCH /counselors/assistance-plans/{id}/status` | 更新计划状态/进度 |
| `POST /counselors/assistance-plans/{id}/evaluation` | 提交帮扶评估 |

#### 3.4.4 学生管理

| 端点 | 说明 |
|------|------|
| `GET /counselors/students/{counselorId}` | 管理的学生列表 |
| `GET /counselors/students/{studentId}/detail` | 学生详情（含成绩、预警历程） |

#### 3.4.5 班级管理

| 端点 | 说明 |
|------|------|
| `GET /counselors/classes/{counselorId}` | 管理班级列表 |
| `GET /counselors/classes/{classId}/scores` | 班级成绩概览 |

#### 3.4.6 数据分析

| 端点 | 说明 |
|------|------|
| `GET /counselors/analytics/{counselorId}/warning-trend` | 预警趋势分析 |
| `GET /counselors/analytics/{counselorId}/class-comparison` | 班级对比分析 |

---

### 3.5 管理员端功能 (Admin Service)

#### 3.5.1 系统仪表盘

| 端点 | 说明 |
|------|------|
| `GET /admin/statistics` | 系统总览：用户数、学生数、教师数、预警数、处理率 |

#### 3.5.2 用户管理

| 端点 | 说明 |
|------|------|
| `GET /admin/users` | 用户列表（分页+搜索） |
| `POST /admin/users` | 创建用户 |
| `PUT /admin/users/{id}` | 编辑用户 |
| `DELETE /admin/users/{id}` | 删除用户（级联删除关联数据） |
| `PUT /admin/users/{id}/reset-password` | 重置密码 |
| `PUT /admin/users/{id}/status` | 启用/禁用用户 |

#### 3.5.3 学生管理

| 端点 | 说明 |
|------|------|
| `GET /admin/students` | 学生列表 |
| `POST /admin/students` | 添加学生 |
| `POST /admin/students/batch-import` | 批量导入学生 |
| `PUT /admin/students/{id}` | 编辑学生信息 |

#### 3.5.4 学院/专业/班级管理

| 端点 | 说明 |
|------|------|
| `GET/POST/PUT/DELETE /admin/colleges` | 学院 CRUD |
| `GET/POST/PUT/DELETE /admin/majors` | 专业 CRUD |
| `GET/POST/PUT/DELETE /admin/classes` | 班级 CRUD，含指定班主任和辅导员 |
| `GET/POST/PUT/DELETE /admin/courses` | 课程 CRUD |

#### 3.5.5 预警规则配置

| 端点 | 说明 |
|------|------|
| `GET /admin/warning-rules` | 规则列表 |
| `POST /admin/warning-rules` | 创建规则（设置触发条件、等级、通知流程等） |
| `PUT /admin/warning-rules/{id}` | 编辑规则 |
| `DELETE /admin/warning-rules/{id}` | 删除规则 |

**规则配置项：**
- 规则名称与编码
- 预警等级（1-低/2-中/3-高）
- 触发条件（如成绩 < 60、挂科数 ≥ 2）
- 统计周期
- 通知流程与责任人
- 帮扶计划模板
- 申诉处理流程

#### 3.5.6 消息与广播

| 端点 | 说明 |
|------|------|
| `POST /admin/messages/broadcast` | 全站广播（按角色/全员） |
| `POST /admin/messages/targeted` | 定向消息 |
| `DELETE /admin/messages/{messageId}` | 删除消息 |

#### 3.5.7 数据导入导出

| 端点 | 说明 |
|------|------|
| `POST /admin/data/import` | 导入数据（学生/成绩） |
| `GET /admin/data/export/{type}` | 导出数据 |
| `POST /admin/backup` | **真**调 `mysqldump` 生成一份 `.sql` 到 `exports/backups/`；工具路径取不到时如实报错 |
| `GET /admin/backups/list` | 扫该目录列出**磁盘上真实存在**的备份文件（`implemented:true` + 真实 `dir`） |
| `POST /admin/backup/{backupId}/restore` | 恢复**演练**：新建临时库灌一遍、数表数、再删掉临时库；**绝不覆盖演示库** |
| `DELETE /admin/backup/{backupId}` | 删除一个备份文件（名字带路径穿越校验） |

---

### 3.6 学业预警引擎 (Warning Service)

#### 3.6.1 预警规则

| 规则类型 | 触发条件 | 预警等级 |
|---------|---------|---------|
| 单科不及格 | 任一课程成绩 < 60 | 1-低 |
| 多科不及格 | 挂科数 ≥ 2 门 | 2-中 |
| GPA 偏低 | GPA < 2.0 | 2-中 |
| GPA 严重偏低 | GPA < 1.0 | 3-高 |
| 成绩下降趋势 | 连续两学期 GPA 下降 > 0.5 | 2-中 |
| 自定义规则 | 管理员配置的表达式 | 可配置 |

#### 3.6.2 自动扫描定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| `scanAndGenerateWarnings()` | 每天 02:00 | 全量扫描所有学生成绩，生成预警 |
| `incrementalScan()` | 每 30 分钟 | 增量扫描新增成绩，补充预警 |

**扫描流程：**
1. 加载所有启用的预警规则
2. 扫描低分学生（分数 < 60）
3. 扫描多门挂科学生（≥ 2 门）
4. 按规则条件精细化扫描
5. 去重：已有未处理预警的学生不再生成
6. 写入 `academic_warnings` 表

#### 3.6.3 预警接口

| 端点 | 说明 |
|------|------|
| `GET /warnings/{studentId}` | 查询学生预警 |
| `POST /warnings/generate` | 手动触发预警生成 |
| `PATCH /warnings/{id}/process` | 处理预警 |
| `PATCH /warnings/{id}/release` | 解除预警 |
| `GET /warnings/statistics/{counselorId}` | 预警统计 |

---

### 3.7 AI 智能分析引擎 (AI Service)

所有 AI 分析均通过**通义千问大模型** (qwen-plus) + **规则引擎兜底**的双引擎架构。

| 端点 | 说明 | AI 驱动 |
|------|------|:---:|
| `POST /ai/analyze-score` | 成绩分析：统计指标 + 薄弱科目 + AI 评语 | ✅ |
| `POST /ai/predict-risk` | 风险预测：等级评估 + 因素识别 + AI 建议 | ✅ |
| `POST /ai/analyze-behavior` | 趋势分析：学期趋势 + AI 趋势评价 | ✅ |
| `POST /ai/generate-suggestion` | 学习建议：个性化策略 + AI 定制建议 | ✅ |
| `POST /ai/generate-warning` | 预警建议：干预方案 + AI 推荐 | ✅ |
| `POST /ai/generate-plan` | 帮扶计划：自动生成计划模板 | ✅ |
| `POST /ai/chat` | 智能对话：自由问答 + 成绩联动分析 | ✅ |
| `POST /ai/qa` | 快速问答 | ✅ |
| `GET /ai/analyze-scores/{studentId}` | 学生成绩自动分析（调 Feign 取数据） | ✅ |
| `GET /ai/analyze-warning-trend/{counselorId}` | 预警趋势 AI 分析 | ✅ |
| `GET /ai/analyze-warning/{warningId}` | 单条预警深度分析 | ✅ |
| `GET /ai/analyze-class-scores/{classId}` | 班级整体 AI 分析 | ✅ |
| `GET /ai/analyze/{studentId}` | 综合分析报告 | ✅ |
| `GET /ai/warning-suggestions/{studentId}` | 预警建议 | ✅ |
| `GET /ai/recommendations/{studentId}` | 智能推荐 | ✅ |
| `GET /ai/assess-risk/{studentId}` | 快速风险评估 | ✅ |

**AI 回答规范：** 每次 API 调用自动注入 `ai-prompt-spec.md` 作为 System Prompt，确保：
- 语气专业温和，禁止贬低学生
- 分析基于数据，不凭空编造
- 输出按标准模板：总评 → 分析 → 建议
- 字数限制：个体 300 字、班级 400 字、建议 200 字

**容灾机制：** 通义千问 API 不可用时，自动降级为规则引擎（`AIServiceImpl`），确保核心功能不中断。

---

### 3.8 消息通知系统 (Message Service)

| 功能 | 技术 | 说明 |
|------|------|------|
| 系统通知 | REST API | 预警生成、帮扶分配等自动创建通知 |
| 广播消息 | REST API + 数据库写入 | 管理员可向全员/指定角色发送广播 |
| 定向消息 | REST API | 向指定用户私信 |
| 实时推送 | WebSocket + STOMP | 消息实时送达客户端 |
| 消息管理 | REST API | 已读/未读、批量操作、清空 |

**广播消息写入：** 广播时自动查询所有目标用户，为每人创建一条消息记录写入数据库，确保消息可追溯。

---

## 四、跨模块数据流

### 4.1 成绩数据链路

```
教师录入成绩 (TeacherService)
    ↓
写入 score_record 表 (course_id + student_id + score)
    ↓
学生查看成绩 (StudentService)
    ├── 联表 courses 获取课程名
    ├── 联表 student_profile 获取学号
    └── Feign 调用 student-service
```

### 4.2 预警闭环链路

```
成绩录入 → WarningScheduledTask 定时扫描
    ↓
生成预警记录 (academic_warnings)
    ↓
WebSocket 实时推送 → 学生/辅导员收到通知
    ↓
辅导员处理预警 + 创建帮扶计划 (assistance_plans)
    ↓
学生执行帮扶 → AI分析进度 → 辅导员评估效果
    ↓
预警解除 或 升级
```

### 4.3 服务间 Feign 调用

| 调用方 | 目标服务 | 用途 |
|--------|---------|------|
| admin-service | student-service | 获取学生GPA数据 |
| admin-service | counselor-service | 获取辅导员信息 |
| admin-service | warning-service | 获取预警列表 |
| admin-service | message-service | 发送广播/定向消息 |
| ai-service | student-service | 获取学生成绩用于分析 |
| college-service | student-service | 获取学生列表 |

---

## 五、数据安全

| 层面 | 措施 |
|------|------|
| 传输安全 | HTTPS + JWT Token 认证 |
| 网关安全 | 角色路径前缀鉴权 + RequestRateLimiter (50 QPS补充/100突发) |
| 服务安全 | Sentinel 限流降级 + CircuitBreaker 熔断 |
| 数据安全 | 密码 MD5 加密 + MyBatis #{} 防SQL注入 |
| 会话安全 | Redis Session + JWT 过期自动刷新 |

---

## 六、部署说明

### 6.1 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| RabbitMQ | 3.9+ |
| Nacos | 2.3+ |

### 6.2 启动顺序

```
1. Nacos (8848) + MySQL + Redis + RabbitMQ
2. Gateway (8076)
3. Auth Service (8072)
4. 其他业务服务（任意顺序）
5. 前端 (npm run dev)
```

### 6.3 核心配置

| 配置 | 默认值 |
|------|--------|
| 数据库 | jdbc:mysql://localhost:3306/academic_warning_system |
| Nacos | localhost:8848 |
| Redis | localhost:6379 |
| RabbitMQ | localhost:5672 |
| 通义千问 | qwen-plus（DashScope API） |

---

## 七、核心数据表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `users` | 用户账号表 | id, username, password, role(1学生/2教师/3辅导员/4管理员) |
| `student_profile` | 学生档案表 | id, student_id(学号), user_id, class_id, college_id, major_id |
| `teacher_profile` | 教师档案表 | id, user_id, college_id, title(职称) |
| `counselor_profiles` | 辅导员档案表 | id, user_id, college_id |
| `courses` | 课程表 | id, name, code, credits |
| `score_record` | 成绩记录表 | id, student_id, course_id, teacher_id, score, score_level |
| `scores` | 成绩明细表（辅导员端用） | id, student_id, course_id, score_total, regular_score, final_score, grade_point |
| `academic_warnings` | 预警记录表 | id, student_id, warning_level, status(0待处理/1已处理/2已解除) |
| `warning_rules` | 预警规则表 | id, name, rule_condition, level, status |
| `assistance_plans` | 帮扶计划表 | id, student_id, counselor_id, status, progress |
| `classes` | 班级表 | id, name, teacher_id, counselor_id, college_id, major_id |
| `score_appeals` | 成绩申诉表 | id, student_id, score_id, reason, status |
| `communication_logs` | 沟通记录表 | id, teacher_id, student_id, content, type |
| `messages` | 消息表 | id, receiver_id, title, content, type, status(0未读/1已读) |

---

> **文档维护者：** 系统自动生成  
> **最后更新：** 2026-06-04
