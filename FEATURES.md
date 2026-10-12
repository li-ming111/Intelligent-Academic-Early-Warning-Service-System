# 智学预警云服务平台 — 功能文档

> 基于 SpringBoot + Spring Cloud Alibaba 微服务架构的高校智能学业预警管理系统  
> 版本：1.0.0 | 更新日期：2026-10-05

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

> ⚠️ **2026-10-08（W31）关于上表「课程管理」一行的口径**：`/api/courses/**`（**课程管理面**：
> 分页课程表 + 增/改/删，`CourseController`）在**后端**只放行 **管理员（role 4）与自定义角色（role ≥ 6）**，
> 学生/教师/辅导员/学院管理员（1/2/3/5）**一律拒**（网关 + course-service 两层闸）。
> 表里教师「✅ 查看」、辅导员「✅」指的是**他们各自的前端入口**，走的是
> `/api/teachers/courses/**` 等**另外的端点**，**不是** `/api/courses/**`。
> （W31 之前这条管理面**两层都没闸**，任何已登录用户含学生都能 CRUD —— 见 §8.8。）

---

## 三、核心功能模块详解

### 3.1 认证鉴权模块 (Auth Service)

| 功能 | 端点 | 说明 |
|------|------|------|
| 登录验证码 | `GET /auth/captcha` | 服务端签发 4 位码，返回 `{captchaId, captchaText, expiresInSeconds}`；匿名可达（网关白名单 + 本服务 permitAll 两处都放行） |
| 用户登录 | `POST /auth/login` | 账号+密码+验证码，返回JWT Token。**必须带 `captchaId` 与 `captcha`**，校验无论成败都消费掉该码 |
| 用户登出 | `POST /auth/logout` | 清除Token |
| 学生注册 | `POST /auth/register/student` | 创建 users 记录 + student_profile 记录（`status=1`，直接可用） |
| 教师注册 | `POST /auth/register/teacher` | 创建 users 记录 + teacher_profile 记录（`status=0`，待审批） |
| 辅导员注册 | `POST /auth/register/counselor` | 创建 users 记录 + counselor_profiles 记录（`status=0`，待审批） |
| 学院列表 | `GET /auth/colleges` | 注册时选择学院 |
| 专业列表 | `GET /auth/majors?collegeId=` | 根据学院获取专业（`collegeId` 生效） |
| 班级列表 | `GET /auth/classes?majorId=` | 注册时选择班级 |

**权限模型：** RBAC (Role-Based Access Control)，角色分为 1-学生 / 2-教师 / 3-辅导员 / 4-管理员 / 5-学院管理员。

**安全机制：**
- JWT Token 认证，过期自动刷新
- Gateway 统一鉴权，按角色限制路径前缀
- 密码 **BCrypt** 加密存储（`SecurityConfig.passwordEncoder()` 返回 `BCryptPasswordEncoder`）
- 登录验证码**服务端**校验，一次性消费（`CaptchaService`，Caffeine 进程内 TTL 5 分钟）
- 前端路由守卫 + 后端双层拦截

> **已删除的端点（不要再引用）**：
> - ⚠️ 2026-10-09（W33）**改角色那条路也补上了**：管理端「权限管理 → 用户权限」的「当前角色」下拉（`POST /admin/permissions/assign-role`）此前**同样只写 `users.role`、不碰 `college_admin_profile`** —— 是 W32 那个坑的**第二个入口**。现在改成学院管理员(5)**必须选学院**（`collegeId` 随 body 传），并 upsert 档案行；把角色从 5 改成别的会**删掉**残留档案行（直连绕过网关时那行仍会让 `CollegeAdminController` 解析出学院）。顺带收掉一个越权面：网关对 role 5 原本放行整个 `/api/admin/**`（而针对 `/api/permissions/**` 的那道闸前缀对不上真实路径 `/api/admin/permissions/...`），admin-service 又只要求"已认证"且**不验签** ⇒ **学院管理员能改全库任意账号的角色**（建号 `POST /admin/users`、删号 `DELETE /admin/users/{id}` 同病）。现在网关与控制器**两层**都只放行系统管理员(4)。
> - `POST /auth/register/admin` —— 2026-10-01 删（零调用方且与 `/users/add` 重复）。
> - ⚠️ 2026-10-09（W32）订正：这里原写「管理员建号只剩 `POST /users/add` 一条路径」，**与事实不符**。管理员建号有**两条活路径**：PC `AdminUsers.vue` → `POST /api/users/add`（user-service），小程序 `admin/users.vue` → `POST /api/admin/users`（admin-service）。两条此前都**不给 role 5（学院管理员）写 `college_admin_profile`** ⇒ 新建的学院管理员 `CollegeAdminController.getCollegeId()` 恒 null、18 个接口一律 403。W32 已给两条都补上「建号时带 `collegeId` 就写档案行」；`collegeId` 走 `@RequestParam(required=false)`（`users` 表无该列，不塞进 `User` 实体）。
> - `GET /auth/courses` —— **从来没有实现过**，是一条"白名单里有名、`AuthController` 里没有映射"的幽灵地址。2026-10-02（W1.15）把引用它的封装**一并清干净**：PC `api/index.js` 的 `authAPI.getAllCourses`、小程序 `services/api.js` 的 `getAllCourses`（两处全仓零调用方），以及网关 `WHITE_LIST` 与 `auth-service/SecurityConfig` 里那两条对应的放行 —— **四处是同一次改动的四个半**，只删一部分会留下"永远不成立的放行"。教师端课程列表走的是 `/teachers/courses`，是另一条路，未受影响。
> - 同轮删掉两个 `authAPI.registerAdmin`（PC，指向上面那个已删端点）与四个 **0 字节**占位 `.java` 文件（`auth-service/controller/AuthController.java`、三个 `util/RedisUtil.java`）—— 空文件不定义任何类、全仓零引用，留着只会让人以为"auth-service 管登录"。

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
| `POST /admin/users/{id}/toggle-status` | 启用/禁用用户（**切换**语义：翻转当前状态，不是"设为某个值"）。⚠️ 后端有、PC 封装有，但**没有任何页面调用它** —— 见 §八 |

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
| `POST /admin/import/students` | 导入学生（multipart + 服务端 POI 解析） |
| `POST /admin/import/scores` | 导入成绩（同上） |
| `GET /admin/import/template/{type}` | 下载导入模板（真 xlsx） |
| `GET /admin/export/{statistics,users,students,teachers,colleges,scores,warnings,insufficient-students,history}` | 各类导出 |
| `GET /admin/export/reports/{fileName}` | 下载已生成的报表文件 |

> ⚠️ 此前这里写的是 `POST /admin/data/import` 与 `GET /admin/data/export/{type}` ——
> **这两个路径全仓任何服务里都不存在**，是纯文档错误（2026-10-05 核对）。真实路径见上。
> 其中 `/admin/export/insufficient-students` 后端与 PC 封装都在，但**没有页面调用**（见 §八）。
| `POST /admin/backup` | **真**调 `mysqldump` 生成一份 `.sql` 到 `exports/backups/`；工具路径取不到时如实报错 |
| `GET /admin/backups/list` | 扫该目录列出**磁盘上真实存在**的备份文件（`implemented:true` + 真实 `dir`） |
| `POST /admin/backup/{backupId}/restore` | 恢复**演练**：新建临时库灌一遍、数表数、再删掉临时库；**绝不覆盖演示库** |
| `DELETE /admin/backup/{backupId}` | 删除一个备份文件（名字带路径穿越校验） |

#### 3.5.8 任务管理

对应 PC `AdminMessages.vue` 的「任务管理」tab（创建表单 / 四个统计卡 / 三个状态筛选 /
行内改状态 / 单条与批量删除 / 分页）。落在 `admin_tasks` 表上。

| 端点 | 说明 |
|------|------|
| `GET /admin/tasks/list` | 分页列表，回 `{total, items}`；`status` 可筛 `pending`/`in_progress`/`completed`（`all` 等同不过滤） |
| `POST /admin/tasks/create` | 建任务。`title` 必填且截断到 200；`priority` 只收 `low`/`medium`/`high`（缺省 `medium`）；状态恒为 `pending` |
| `POST /admin/tasks/{taskId}/status` | 只改状态。按**受影响行数**判成败 —— 改不存在的 id 回「任务不存在」 |
| `DELETE /admin/tasks/{taskId}` | 删任务。同样按受影响行数判，删不存在的 id 回「任务不存在」 |

> 2026-10-02（W1.11）之前这四个端点是**整组桩**：列表写死空 `ArrayList`，
> 创建/改状态/删除只 `log.info` 一行就回 `"…successfully"`。前端早就接好了，
> 对着永远空的列表渲染 ⇒ 页面看着"能用"，点什么都"成功"、什么都不发生。
> `created_by` 有指向 `users.id` 的真外键，解析不出操作人时写 `NULL` 而非 0。

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
6. 写入 `academic_warning` 表（**单数**），**并逐门落 `academic_warning_course` 明细**（W39，见 §3.6.4）

#### 3.6.3 预警接口

| 端点 | 说明 |
|------|------|
| `POST /warnings/scan` | **手动触发一次全量重扫**（`triggerNowDetailed()`），返回 `{generatedCount, releasedCount, skipped}`（`skipped=true` 表示已有一轮在跑、本次未重复执行）。**只放行角色 3/4/5**（读 `X-Role`，缺头或未知角色默认拒绝，回 body `code:403`）—— 该闸是 **2026-10-05 下午**补的；此前**任何已登录用户都能触发**（含学生），见手册 §6.59 |
| `POST /warnings/generate` | 手工生成**一条**预警（body `studentId` 必填，可选 `ruleId`/`warningLevel`/`warningMessage`） |
| `POST /warnings/handle` | 处理预警（记录处理意见） |
| `PUT /warnings/status/{warningId}` | 改预警状态 |
| `GET /warnings/list` | 预警列表 |
| `GET /warnings/student` | 按学生查询预警 |
| `GET /warnings/level` | 按等级查询 |
| `GET /warnings/detail/{warningId}` | 预警详情 |

> ⚠️ 此前这里写的 `GET /warnings/{studentId}`、`PATCH /warnings/{id}/process`、`PATCH /warnings/{id}/release`、
> `GET /warnings/statistics/{counselorId}` **四条都不存在**（2026-10-05 核对）。
> 且上面这 **8 条前端一条都没调** —— 预警实际由每日 02:00 的定时任务产生，
> 各端读取走自己的端点（`/counselors/warnings/**`、`/students/warnings/**`、`/admin/warnings`）。见 §八。

#### 3.6.4 预警 ↔ 课程：`academic_warning_course`（W39，2026-10-12）

**现象（用户报的）**：教师端和管理员端的"预警信息"与"课程信息"对应不上。

**根因不是前端，是预警表根本没有课程这一维**：`academic_warning` 只有 `student_id`
（= `student_profile.id`），预警按**学生聚合**生成，"挂了哪几门课"只以**散文**形式写在
`description` 里（如"第5学期累计 3 门课程不及格：操作系统(40分)、计算机基础(47分)、高等数学(56.5分)"）。
由此派生三处对不上：

| # | 症状 | 机制 |
|---|------|------|
| 1 | 教师看到自己班上学生的预警，正文点名的课大部分**不是他教的** | 预警按 `classes.teacher_id`（班主任）过滤；老师的"我的课程"来自 `courses.teacher_id`。两条路不相交 |
| 2 | 管理员端"班级"一列**恒为 `--`** | `GET /admin/warnings` 直接回裸实体，没有 `className`；`AdminWarnings.vue` 写的是 `w.className \|\| '--'` |
| 3 | PC 学生端的"关联课程"**是前端编造的** | `Warnings.vue` 的 `extractCourseName()` 用正则从 `description` 里抠课程名 |

**改法**：新增子表 `academic_warning_course`（一条预警 × 多门挂科课）。
`academic_warning` 的**语义不变**，现有"累计挂科 1-2 / 3-5 / >5 门"规则引擎一行没改 ——
所以这是"把已经存在的关系落到库里"，不是"改预警口径"。

| 项 | 内容 |
|---|---|
| 写入方 | **只有** `WarningScheduledTask.createWarning`，经新 bean `WarningWriter.insertWarningWithCourses`（`@Transactional`，两表原子）。⚠️ 抽成独立 bean 是因为同类私有方法自调用不过代理 ⇒ 假事务 |
| 不写的 | `IntelligentWarningServiceImpl.generateWarning`（`POST /warnings/generate`，前端零调用方）：它的契约只收 `studentId/ruleId/warningLevel/warningMessage`，**没有课程入参**，不落也不读子表 |
| 读端 | `GET /teachers/warnings/{id}`、`GET /admin/warnings`、`GET /admin/warnings/{id}`、`GET /students/warnings/{id}` 各补 `courses`（数组）+ `courseNames`（拼串）；教师端另有 `involvesMyCourse` 布尔与 `onlyMine` 筛选 |
| 改表处（**已不含 dump**） | `init_schema.sql` + 活库 + `db_backups/academic_warning_course_20261012_{apply,rollback}.sql` + **`DataInitializer.EXPECTED_COLUMNS` 刻意不加**（理由见下）。⚠️ 原"第四处" `db_backups/schema_dump_*.sql` **已随重建路径一并删除**（2026-10-12 W39，见 **§8.14**）—— 它早就不是"活库快照"了 |
| 回填 | `apply` 脚本按权威口径（`ScoreCaliber.LATEST_VALID_SCORE`，`score_total < 60`）对**所有** pending 预警重算。实测活库 **4 条预警 / 11 行明细**，逐条 `COUNT(*) == academic_warning.fail_count`（司明3、李爽3、张健3、韩坤2） |

**四条读端实测**（直连服务，2026-10-12；活库 **4 条预警**）：

- `GET /teachers/warnings/29`（于凤海，uid 29）→ 4 条。司明那条的 `courses` = 操作系统40(任课18) / 计算机基础47(任课22) / **高等数学56.5(任课29)**，`involvesMyCourse=true`。
- `GET /teachers/warnings/29?onlyMine=true` → 仍 **4** 条（4 条都涉他的课）。
- ⚠️ **`rows=0` 不等于闸生效**：`GET /teachers/warnings/18?onlyMine=true` 也回 **0** 条，但那是**正确的** —— 刘教授（uid 18）带的是 31/32 班，司明等在 1 班（班主任 29）。**演示数据里每位"有预警的班主任"恰好也教那些课**，所以 `onlyMine` 这个分支在活库上打不出反例。该分支由单测钉住（`TeacherControllerReadOwnershipTest.onlyMineFiltersToWarningsTouchingMyCourses`，构造"班是我的、课是别人的"形状），并做了三轮反向打靶（M1 删筛选 ⇒ `1→2`；M2 恒拒 ⇒ `1→0`；M3 关掉归属查询 ⇒ `involvesMyCourse` 翻假）。
- `GET /admin/warnings` → 4 条，`className` 均为 `计科2306班`（此前恒 `null` ⇒ 界面恒 `--`），`courses` 与教师端一致。`GET /admin/warnings/999999` → body `code:404`（HTTP 仍 200，是本仓控制器业务拒绝的常规形状）。
- `GET /students/warnings/27`（司明）→ 1 条，`courses` 三门带 `semester`。

**顺带修掉**：PC 学生端删掉 `extractCourseName` 正则（那是全仓唯一一处前端编造课程名）；
小程序 `utils/warning.js` 的 `normalizeWarning` 统一补 `className/courses/courseNames/involvesMyCourse` 四个键，
三个页面（student/teacher/admin）共用，不再各自兜 null。

**刻意不动（免得被当成漏做）**：

- 所有**计数/聚合** SQL（`ScoreStatMapper`、`WarningMapper`、dashboard…）数的是行数，与课程维无关。
- `GET /ai/analyze-warning/{warningId}`：只把 id 塞进 prompt，**从不读预警行**。
- 教师端**仍按班级**（`classes.teacher_id`），`isSelf` 归属闸不动；`onlyMine` 是"在班级范围内再筛"。
- `warning_rules` / `RuleConditionParser`：规则仍是学生级累计口径。

**已知边界**：

- 子表是**派生数据**：`scores` 变了（补考通过、改分）子表会过期。本轮只在**生成预警时**写，不做"成绩回滚时重算子表"。
- `description` 正文最多罗列 `MAX_COURSE_DETAIL=5` 门（超出以"等"省略），子表存**全部**挂科 ⇒ 子表比正文更完整。
- 子表**不加** `EXPECTED_COLUMNS` 项：common jar 被 5 个服务加载，若某个库还没这张表，自检会把它报成"(表不存在)"并制造新的假漂移噪音（先例：`student_settings`）。代价 = 这张新表不在启动自检覆盖内，写进表注释与 `apply` 脚本头部。
- **不加外键**到 `courses`：给 `courses.id` 加 FK 会让"删课"在历史预警上失败，而这是可重算的派生记录，悬空 `course_id` 无害（读端 `COALESCE(courses.name, awc.course_name)` 已兜住）。`uk_awc_warning_course` 保证重扫重写幂等。

**同轮订正一处既有账**：`DataInitializer.EXPECTED_COLUMNS` 有 3 条基线早已过期（`colleges` 9→7、`assistance_plans` 8→9、`academic_warning` 21→22）。
⚠️ 但订正到一半发现**这个自检从来没跑过**，所以"已经报警、没人听"那句话是**错的** —— 它连报警都没有。
真相与改法见 **§8.12**（同一轮顺手核出的最重一条）。

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
| `GET /ai/student/{studentId}/suggestions` | 学习建议（按学生） | ✅ |
| `GET /ai/predict-warnings/{studentId}` | 预警预测（按学生） | ✅ |

**AI 回答规范：** 每次 API 调用自动注入 `ai-prompt-spec.md` 作为 System Prompt，确保：
- 语气专业温和，禁止贬低学生
- 分析基于数据，不凭空编造
- 输出按标准模板：总评 → 分析 → 建议
- 字数限制：个体 300 字、班级 400 字、建议 200 字

**容灾机制：** ⚠️ 这句话**只对"规则引擎 + AI 评语"那一类端点成立**（`analyze-score`、`predict-risk`、
`generate-suggestion` 等由 `AIServiceImpl` 先算规则、再叠一句 AI 评语）。
**纯 AI 端点没有规则兜底**（`analyze-warning-trend` / `analyze-warning` / `analyze-class-scores` /
`analyze` 的评语 / `chat` / `qa`）—— 模型不答时它们没有"降级结果"可给，只能如实说没拿到，见下。

> ⚠️ **2026-10-05（W26）核对：上面这 18 个端点里，当时只有 `POST /ai/chat` 有界面入口。**
> 同一天晚些已接线：`AiInsight.vue` 把 14 个此前零调用的 `aiAPI` 封装接到了
> 8 个页面（辅导员/教师/管理端），小程序端仍是 **0 个 AI 调用**。

> ⚠️ **2026-10-08（W28）修正一条"说法与事实不符"的老账。**
> `analyze-warning-trend` / `analyze-warning` / `analyze-class-scores` 三条**纯 AI** 端点，
> 在 `tongYiService.generalChat()` 返回 null（没配 `DASHSCOPE_KEY`，或调用失败）时，
> 原来各自把 `aiAnalysis` 填成一句 **`"…分析中，请稍后重试"` 并包成 `success:true`**。
> 那是一句**永远等不到**的进度话术：没配 key 时重试一万次也不会有结果，用户只会以为"再等等就有了"。
> 与 W26 已修的 `/ai/chat`、`/ai/qa` 是同一族，当时漏了这三条。
> **现在**：模型没答就回 `ApiResponse.error(...)`，并按**原因**分开说 ——
> "未配置（配置问题，重试无用）" 与 "调用失败（瞬时故障，重试有意义）"。
> `GET /ai/analyze/{studentId}` 是**特例**：它的 `scoreAnalysis`/`riskPrediction` 是规则算出的**真数据**，
> 所以不整个回 error，只把 `aiComment` 换成带原因的 `aiCommentUnavailable` 字段。
> 前端 `utils/ai.js` 的诚实性闸**保留**（防的是"换代码前启动的旧 jar"与"以后又有人塞进度话术"），
> 判据见 `testsprite/local-check/_w26_ai_guard.mjs`（W28 补了两条负对照：新的"原因"句必须**放行**，
> 否则 `/ai/analyze/{studentId}` 里的真数据会被连坐丢掉）。单测：`AIControllerAiUnavailableTest`（8 条）。

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
教师录入/导入成绩 (teacher-service)
    ↓
直写 scores 表（权威表）：course_id + student_id(=student_profile.id) + semester + score_total
    │   {@code ScoreSyncSupport.applyToAuthoritative} 顺带算出 grade / grade_point、并镜像 final_score
    ↓
学生查看成绩 (student-service) / 辅导员 (counselor-service) / 预警引擎 (warning-service)
    全部读 scores（口径 = ScoreCaliber：每门课每生取 final_score 非空的最新一次）
```

> ⚠️ **2026-10-10（W36）改正**：上文原写「教师录入 → 写入 `score_record`」。
> 这张表**已不是**教师端的数据源 —— 真实链路是直写 `scores`。见 §8.9。

### 4.2 预警闭环链路

```
成绩录入 → WarningScheduledTask 定时扫描
    ↓
生成预警记录 (academic_warning)
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
| 数据安全 | 密码 **BCrypt** 加密（`BCryptPasswordEncoder`）+ MyBatis #{} 防SQL注入 |
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
| `scores` | **唯一权威成绩表**（W36 起：教师端读写、辅导员端、学生端、预警引擎**全部**读它） | id, student_id(**profile id**), course_id, semester(int), year, score_total, regular_score, final_score, grade, grade_point, **remark**, modified_by |
| ~~`score_record`~~ | **已删（2026-10-12 W39）**。W36 迁走教师端、W38 迁走 admin 导入之后，全仓对它**零读零写**（无 SQL 引用、无 mapper XML、无 `BaseMapper<ScoreRecord>`、活库无触发器/视图/存储过程），已从活库与 `init_schema.sql` 一并移除；246 行历史数据留档在 `db_backups/score_record_drop_20261012_rollback.sql`。详见 §8.15 | 已不存在 |
| `academic_warning` | 预警记录表（**单数**） | id, student_id, warning_level, status(0待处理/1已处理/2已解除) |
| `academic_warning_course` | 预警**挂了哪几门课**的明细（W39 新增，见 §3.6.4） | id, warning_id, course_id, course_name(快照), score_total, semester |
| `warning_rules` | 预警规则表 | id, name, rule_condition, level, status |
| `assistance_plans` | 帮扶计划表 | id, student_id, counselor_id, status, progress |
| `classes` | 班级表 | id, name, teacher_id, counselor_id, college_id, major_id |
| `score_appeals` | 成绩申诉表 | id, student_id, score_id, reason, status |
| `communication_logs` | 沟通记录表 | id, teacher_id, student_id, content, type |
| `messages` | 消息表 | id, receiver_id, title, content, type, status(0未读/1已读) |

## 八、文档与代码的差距（2026-10-05 逐条核对）

这一节是**如实记**，不是待办承诺。三类：

### 8.1 后端与封装都在、但**没有任何界面入口**（能力是死的）

| 能力 | 后端 | 前端封装 | 界面入口 |
|---|---|---|---|
| AI 分析套件（18 个端点） | ✅ | ✅ 15 条 | ❌ **只有 `/ai/chat`**（2 处调用）；小程序 0 个 |
| warning-service `/warnings/**`（8 个端点） | ✅ | —— | ⚠️ **2026-10-05 下午起有入口**：管理端 `AdminWarnings.vue` 加了「重新扫描预警」按钮（`POST /warnings/scan`）+ AI 分析。此前（本节首写时）是**零前端调用**，预警只能靠 02:00 定时任务产生 —— 该结论**只对 2026-10-05 午前成立**。⚠️ 该端点当时**任何已登录用户都能打**，同轮补上角色闸（只放行 3/4/5）与并发互斥，见手册 §6.59 |
| 用户启用/禁用 | ✅ `POST /admin/users/{id}/toggle-status` | ✅ | ❌ `disableUser`/`enableUser` 零调用 |
| 导出挂科学生 | ✅ `GET /admin/export/insufficient-students` | ✅ | ❌ `exportInsufficientStudents` 零调用 |
| 注册（**第二套实现**） | ❌ **2026-10-08 已删**（W27，见 §8.5） | ❌ 3 条封装同删 | 原为 ❌ 零调用；UI 一直走 `user-service` 的 `/auth/register/{student,teacher,counselor}`。⚠️ 其中 `/students/register` 曾是**假成功**、另两条是**半截写**，三条一并删除 |

两个前端的封装方法合计 **508 条**（PC `api/index.js` **292** / 小程序 `services/api.js` **216**），
其中**零调用点 61 条**（PC 39 / 小程序 22）。再按「同一端点还有没有别的入口」分：

- **真缺口 28 条**（PC 21 / 小程序 7）—— 端点既没有被调用的封装指向、也没有页面用字面路径直连；
- **第二个入口 33 条** —— 同端点另有被调用的封装（或页面直连了字面路径），功能不缺，只是这条封装没人用。

按命名空间（零调用 / 其中真缺口）：PC `counselorAPI` 12/7、`studentAPI` 16/9、`teacherAPI` 8/5、
`authAPI` 2/0、`collegeAdminAPI` 1/0（`aiAPI` 已在 2026-10-05 W26 接线，不再是零调用）；
小程序 `admin` 5/1、`teacher` 5/3、`counselor` 5/3、`collegeAdmin` 3/0、`student` 3/0、顶层 `apiClient` 1/0。

> 📌 **数字会随轮次变动，看的时候要带日期。** 本节首写（2026-10-05）时是「513 / 零调用 82 / 真缺口 47 / 第二入口 35」，
> 之后几轮各动过一点：W26 把 14 条 `aiAPI` 死封装接到 8 个页面上、W19 给了 `predictRisk` 调用点
> ⇒ 零调用 82 → 61；W27 删了 3 条 `/register` 假封装、W29 删了 1 条恒空的 `getSecurityLogs`
> ⇒ PC 封装 297 → 292。**上面这一版是 2026-10-08（W29）实测**，命令：
> `python testsprite/local-check/_w25_dead2.py`（自带打靶，含正负对照与"字面路径直连"这第二只眼睛）。

> ⚠️ 判据边界：**"零调用"是扫出来的事实；47 条说的是"这条端点没有任何入口"，不等于 47 个功能缺失。**
> 上表各行是**逐条核过确属缺口**的。最容易误判成缺口的另一类：**同一功能有两套实现、UI 只走其中一套**
> —— `/students|teachers|counselors/register` 就是（UI 走 `/auth/register/*`），所以它只能算"端点没入口"。

> 📌 更正记录：本节 2026-10-05 首次写下时写的是「两侧合计 **61** 个」及其分布。那个数是**错的**，两个原因：
> 扫描器只会认 `const xxxAPI = {` 一种写法，把小程序（`class ApiClient` + 实例字段）**整块漏掉** ⇒ 61 其实是 PC 独有；
> 且它的剥注释函数**先找 `/*` 再找 `//`**，于是 `// … /students/settings/** …` 这类行注释里的 `/**` 被当成块注释开头，
> 从那里起把**后半个文件全吞成空行**。修好这两处后 PC 从 21 个方法还原成 297 个。**上一版数字整体作废。**

### 8.2 本文档此前写错的（已在上文就地改正）

| 此前写的 | 实际 |
|---|---|
| `PUT /admin/users/{id}/status` | `POST /admin/users/{id}/toggle-status` |
| `POST /admin/data/import`、`GET /admin/data/export/{type}` | **全仓不存在**；见 §3.5.7 真实路径 |
| §3.6.3 四条 `/warnings/{id}/process`、`/release`、`/statistics/{counselorId}`、`/warnings/{studentId}` | **全仓不存在**；见 §3.6.3 真实列表 |
| 表名 `academic_warnings` | `academic_warning`（**单数**；实体 `@TableName`、`init_schema.sql`、活库三处一致） |
| §五"密码 MD5 加密" | **BCrypt**（与本文档 §3.1 自相矛盾过） |

### 8.3 反过来：**凡是被界面调用的功能，全部实现**

把 PC + 小程序的 **510 处调用**（含 8 处 `.vue` 直连后端）与后端 **393 条映射**逐条对照过：
**断链 0、HTTP 动词不符 0**。也就是说 —— **页面上点得到的，后端都在**；
上表那些缺口都是"反方向"的：**后端在、页面没有**。

### 8.4 核对时最该记住的一条标定陷阱

`/students/**`、`/teachers/**`、`/counselors/**` 的路径参数**默认是 `users.id`**，
而 `student_profile.id` 是**另一套 id**（成绩表里的 `student_id` 用的是这一套）。
例外：`teacher_course.teacher_id`，以及 `/subscription/**`、`/evaluations/**`。
把两套 id 混用，接口会 200 但查不到人（或查到别人）。
### 8.5 一处「假成功」：`POST /students/register`（2026-10-05 查，**2026-10-08 W27 已删**）

`POST /students/register`（student-service）的 service 方法体是：

```java
public Object registerStudent(Object request) {
    log.info("学生注册");
    return "注册成功";          // 一行都没写库
}
```

**实测（2026-10-05，活库）**：用工程自己配置的密钥签一个学生 token（`userId=27, role=1`），
经网关 `POST /api/students/register` → **`HTTP 200` + `success:true` + `data:"注册成功"`**；
而 `users` **35** / `student_profile` **24** / `probe%` 账号 **0** —— **打前打后完全一致**。
也就是说它**回了成功、却没建任何账号**。这与 §6.57 修掉的 `/teachers/scores/warnings` 同一族
（说法与事实不符）。**2026-10-08（W27）处置：删端点，不给它补实现** —— 真实的注册路径是
`POST /api/auth/register/student`（PC `Register.vue`、小程序 `register.vue` 都在走），
不为一条**零调用方**的假端点再造第二套注册。修后实测 `POST /students/register` → **HTTP 405**
（该路径只剩 `GET /students/{id}`；控制器里已无任何 `/register` 处理体）；PC `api/index.js`
里那个零调用的 `studentAPI.register` 封装一并删除。

**够得着吗**（这决定严重性）：网关白名单里**没有**它（只有 `/api/auth/register/{student,teacher,counselor}`），
匿名走网关实测 → **`HTTP 401` `{"code":401,"message":"缺少认证信息"}`**（网关的第四种拒绝形状，此前只记过 403）；
网关也不认裸的 `X-User-Id`/`X-Role` 头。但**任何已登录用户**带着真 token 打过去，就是上面那个 200 + 假成功。

**同族两条**（**只读代码 + schema**）：`/teachers/register`、`/counselors/register` 也**不建 `users` 账号**。
其中 `teacher_profile.user_id` **NOT NULL 且有外键 → `users.id`** ⇒ 它**插不出孤儿档案**，只会给**已存在的用户**再插一行
`teacher_profile`（同一个 user 两份档案，或插入直接失败）；`counselor_profiles.user_id` **NOT NULL 但没有外键**
⇒ 可以插进**指向不存在用户的**档案行。两条都不设口令、也不经 `userService.register`，与 UI 真正走的 `/auth/register/*` **是两套实现**。
**2026-10-08（W27）同样按「零调用方 = 删」处置**：两条端点 + 它们的 `*Service.register` + PC 封装全删；
修后实测各回 **HTTP 405**（`GET /{id}` 通配映射还在，故不是 404）。详见《答辩准备-启动与演示手册》§6.60。

### 8.6 那 28 条「真缺口」逐条核实：**接口是否诚实**（2026-10-08 W29）

§8.1 只说"这些端点没有界面入口"。**"没入口"不等于"没实现"，更不等于"实现是真的"** ——
被问到「还有未实现的功能吗」时，这两件事必须分开答。所以把 28 条逐一读了控制器/服务实现，判据只有一条：
**它到底查不查库、算不算数。**

**结论：23 条 REAL / 2 条恒空但已诚实 / 3 条 PARTIAL（参数说了谎）。没有一条是"没实现"。**

| 判定 | 条数 | 端点 |
|---|---|---|
| ✅ **REAL**（真查库/真计算/真写） | 23 | `counselors/class-management/applications`、`counselors/classes/{id}/warnings`、`counselors/students/{id}`、`counselors/notifications/weekly-count`、`counselors/class-management/applications/{id}/review`、`students/appeals/{id}/pending`、`students/evaluations/{id}/detail`、`students/settings/{id}`（GET/PUT）、`students/subscription/{id}/**`（`subscribe-level`/`unsubscribe-level`/`set-channel` 三条）、`teachers/scores/anomalies`、`teachers/courses/{id}/distribution`、`teachers/courses/{id}/students`、`teachers/students/{id}`、`admin/students`、`counselors/assistance-plans/{id}/status`(PATCH)、`counselors/students/{id}`(PUT)，及小程序侧同端点的重复封装 |
| ⚪ **恒空，但已诚实** | 2 | `counselors/classes/activities`、`counselors/notifications/templates` —— 两条**原来都是编造的假数据**（前者两条写死活动、其中一条日期是 `LocalDate.now().plusDays(3)`，每次看都"三天后有个座谈会"；后者硬编码 3 条模板），后来已改成**如实回空列表 + 注释说明库里没有这张表**。回空在这里是**结果上的事实**，不是掩饰 |
| ⚠️ **PARTIAL：参数说了谎** | 3 | `students/benchmark/class/{id}/comparison`、`students/benchmark/major/{id}/comparison` —— `semester` 参数**收下了却从不使用**，返回的其实是全课程 GPA（W15「签名说了谎」一族）；`teachers/scores/warnings` —— W24 已处置，现在只统计不写库，且 message 里明说这一点 |

**W29 处置掉的一条**：`GET /students/settings/{id}/security-logs` 原本**不属于上面任何一类** ——
它声称按 `{page,pageSize}` 分页返回本人「安全日志」，而 service 体是**写死的** `total=0, logs=[]`，
**一次查询都没有**；`security_logs` 表**是存在的**（`init_schema.sql:306` 建表 + admin 端有实体与计数），
只是**全仓至今没有写入方**（`AdminController` 里已明写「`security_logs` 至今 0 行且全仓无写入方，
接上去只会再造一个恒空」）。它比上面两条"恒空"更差一层：那两条是"库里没有这张表"，
这一条是"有表、有实体、有人计数，唯独没有写入方，于是学生侧这个读取口**永远回空**"。
而学生真正会看的「登录历史」**早就有真路径** —— `GET /users/{uid}/login-logs`（读 `login_logs`，
有真写入方），PC 设置页「安全日志」页签用的就是它。
⇒ **零调用方 + 恒空 + 能力已被另一条真路径覆盖** ⇒ 按「零调用方 = 删」**删掉端点、接口声明、
实现、Fallback 降级实现与 PC 封装**（小程序本就没有这条封装），**不给它补实现**
（"让承诺成真"要新造一个写入方，那是另立功能，不是修接口）。
判据：全仓 `grep` 后**残留命中只剩我新写的注释**；调用方**两只眼睛**都核过（封装调用点 0 / `.vue` 字面路径直连 0）；
`/students/settings/{studentId}/` 下只剩 `privacy-level` 一条映射、**无通配同族** ⇒ 修后该路径应为 **404**
（此为**静态判定**，本轮**未起服务实测** —— 与 W27 那次不同，那时路径下有 `GET /{id}` 通配故须实测 405/404 之别）。
单测：全量 reactor **584 例 / 0 失败**。详见《答辩准备-启动与演示手册》§6.62。

> 💡 另外三处 PARTIAL 的 `semester` 参数**本轮刻意不动**：它们零调用 ⇒ 目前没有任何界面会传这个参数，
> 修法（实现按学期，或删掉参数让签名不说谎）需要先定"这个能力到底要不要"，属于产品决定，不在"接口诚实性"范围内。

### 8.7 「说法 vs 事实」不只在接口返回值上：注释里的理由、台账里的状态，也是说法（2026-10-08 W30）

本节前 6 条查的都是"**接口**说的话对不对"。W30 查到的两类，说话的人换成了**注释**和**台账**：

**(1) 注释里的"理由"也可能与代码不符 —— 而它正是那条闸一直没装的原因。**
`GET /teachers/scores/by-class` 不传 `teacher_id` 的分支零校验，保留理由写着
「前端 `useTeacherRoster` 就是靠它拿姓名+分数的，保持放行」。核实后：`useTeacherRoster`
的消费方（小程序 `services/api.js:212`）**带 `teacher_id`**，走的根本不是这条无闸路径。
⇒ **一条"我以为什么"被当成了"有人需要"，代价是任意教师可读任意班的整份成绩单（246 行带姓名分数）。**
已按班级归属收口（非班主任 ⇒ 拒，班主任的全班成绩视图原样保留），详见手册 §6.63。
⚠️ 同一条还夹着一个我自己的错：第一版我把严重性写成"任意已登录用户（含学生）可读"——
读网关代码后改准为"**任意教师**"（`AuthenticationFilter:365` 对 `/api/teachers/**`
有 `role != 2 ⇒ false`，管理员更上面已放行）。**直连时才轮到"任何角色"，而那是另一回事。**

**(2) 台账里写着"未修"的，可能早就修了。**
本轮为找线索去核 §7 的未修项，3 条其实早已收口、只是没回改台账：
`W11①`（两条教师端点不限自己班级）→ **W18 已装闸**；`W12①`（辅导员端 10 条没闸）→
**W13 整族收口**（§6.45-I③ 自己就写着，台账没跟）；`W12②`（PC 导入成绩接不上）→ **W19 已修**。
3 条已就地订正（保留历史现场 + 加订正注，不删）。

> **要记住的一条**：本仓的"说法"至少有三层 —— **接口返回值、代码注释、文档/台账**。
> 前三轮修的是第一层；这一轮两层都撞上了。**修完一条候选，顺手把它在台账里的那一条也改掉，
> 否则账本会开始说谎**；写理由时也要能指出"哪一行代码保证了这个理由"。

### 8.8 同一族又一条（W31）：注释写「内置角色不经这两条」——**方向正好写反**

`/api/courses/**`（课程管理面：分页课程表 + 增/改/删）此前**两层都没有内置角色闸**：
网关 `AuthenticationFilter` 里 `PREFIX_COURSE` **只出现在自定义角色（role ≥ 6）分支**，
内置角色 1/2/3 一路落到函数末尾 `return true`；下游 course-service 只有
`anyRequest().authenticated()`，而 `GatewayAuthFilter` **不验签**（带 `X-Role` 头就等于那个角色）。
⇒ **任何已登录用户（含学生）可新建/修改/删除课程**（直连实测：学生 `POST /courses/add` 真落库、
`PUT /courses/update/1` 真改到 `courses.id=1`）。

那条常量的注释写的是「内置角色…**不经这两条**」——**这句话既是错的、方向也写反了**：
`PREFIX_COURSE` **只**给自定义角色用，内置 1/2/3 恰恰**被它漏掉**；而被"担心"的学院管理员（5）
**本来就已被拒**。⇒ **闸的方向当年是反的**（该拦的没拦、不必担心的拦着）。
已在两层各补闸，放行面 = 管理员（4）与自定义角色（≥ 6）。详见手册 §6.64。

⚠️ 这一条与 W30 是**同一族**："闸一直没装，因为注释里那句理由本身是错的"。
本仓已有**至少四处**是"说话的层"与事实不符：接口返回值（W5–W30）、代码注释（W30/W31）、
文档台账（W25/W30）。**读代码时看到一句"理由"，要能指出它对应哪一行判断**；指不出、或指得出但那行
结论相反，就把它当**候选缺陷**查。

> ⚠️ **方法上的两条**（都栽过）：
> 1. **写探针不许碰真实演示行** —— W31 里 `PUT /courses/update/1` 曾真改到活库 `courses.id=1`
>    （已按备份种子恢复）。现在写探针的靶一律是**自建、带前缀、结束即删**的行。
> 2. **两个请求头都要发** —— 闸读 `X-Role`，探针第一版只发 `X-User-Id` ⇒ 连管理员都被拒；
>    别让被测方替你挑键名（W8/W12 一族的第四次）。

### 8.9 教师端与辅导员端「成绩对不上」的根因：两套成绩表（2026-10-10 W36）

**现象**（用户报的原话）：「预警信息，于凤海和辅导员显示的对应不上，课程信息也对应不上。」

**根因**：全项目**两张成绩表并行**，且各自被一半的角色读：

| 读的人 | 读的表 |
|---|---|
| teacher-service（**全部**成绩端点） | `score_record`（教师录入暂存表） |
| counselor / student / warning / ai-service | `scores`（**权威表**，`ScoreCaliber` 头注释自己写着的） |

实测学生**司明**（`student_profile.id=3`，计科2306班）：

| | `score_record`（教师读） | `scores`（其余人读） |
|---|---|---|
| 行数 | 16 | 29 |
| 挂科门数 | **4** | **3** |
| 缺失方向 | 缺第 2 学期历史、缺第 6 学期「期末未录入」行 | —— |

`academic_warning`（id=395）里司明那条写的是 `fail_count=3`、「操作系统 40分、计算机基础 47分、高等数学 56.5分」——
正是 `scores` 口径。教师成绩页（`score_record`，**不按课程去重**）数出 4 门 ⇒ 同一屏上两个数字打架。

**本轮修法**（用户选定：**治本**，含写路径）：教师端**读写全部改走 `scores`**。

- 读：`ScoreRecordMapper` 只保留基于 `ScoreCaliber.LATEST_VALID_SCORE` 的权威投影（每门课每生取
  `final_score` 非空的最新一次），返回类型仍是 `ScoreRecord` 以保持控制器消费面不变。
- 写：`ScoreRecordService.saveScores` **直写 `scores`**（不再"先写 `score_record` 再 `syncToScores` 二跳"）。
- 改/删：`PUT|DELETE /teachers/scores/{id}` 的 `scoreId` 语义由 `score_record.id` 改为 **`scores.id`**，
  归属判据由"录入人是不是我"改为"**这门课是不是我教的**"（`scores` 无 `teacher_id`；
  顺带与学生申诉 `score_appeals.score_id`（本就外联 `scores.id`）统一）。
- Schema：`scores` 补 `remark varchar(500)`（`init_schema.sql` + 活库 + `db_backups/w36_scores_remark_apply|rollback.sql`）——
  不补就是**又一处「提交体被静默丢弃」**（本仓明令禁止）。**未跑 `rebuild_schema.py`**。
  ⚠️ **改表结构要同步的是四处，不是两处**：还有 `academic-common/.../config/DataInitializer.java` 的
  `EXPECTED_COLUMNS`（启动时对照 `information_schema` 自检，判据是**相等**）。`scores` 那行原写 14，
  加了列就是 15 —— 不改则**每个服务启动都 ERROR 报"少列"**。这声警报本身是对的（它正好逮到这次变更），
  但漏改等于把自己的自检喂瞎。该表**没有单测钉**，只有启动日志看得见。
- 顺带修好两条**此前一次都没成功过**的路径：PC `TeacherScores.vue` 改分弹窗（弹窗绑 `totalScore`、
  提交却发 `{regularScore,finalScore,reason}`、后端读 `{score,remark,modifiedBy}` ⇒ 键名对不上，必回「缺少 score 字段」）；
  小程序 `batchDeleteScores`（发 `{ids:[...]}` 而后端 `@RequestBody List<Long>` 收裸数组 ⇒ 必 400）。
- `admin-service` `GET /admin/export/scores` 一并切 `scores`（原读 `score_record` 整表、且「备注」列**恒写空串**）；
  admin **导入**本轮不动 ⇒ `score_record` 保留为"管理端导入暂存表"，**不删不回填**（登记为后续项）。
- 删除死代码 `latestPerStudent`（`scores` 投影已是一人一行，Java 侧再去重是空转）。

**E2E 实测**（活库，直连 8090，显式 UTF-8 百分号编码中文查询参数）：

| 检查 | 结果 |
|---|---|
| `GET /teachers/scores/by-class?class_name=计科2306班&teacher_id=29` | 238 行；**司明 8 门课、挂 3 门**，与 `academic_warning` 逐条对上 |
| `GET /teachers/enrollments?teacherId=29` | **238 行**（改造前为空） |
| 改自己的课 | HTTP 200 且 `scores` 真变、`grade/grade_point` 重算、`regular_score`/`final_score` **未被抹掉**、`score_audit_log` 落一行 |
| 改别人的课 | **HTTP 200 + body `code:403`**（本仓控制器业务拒绝的惯用形状），一行未写 |
| 管理员（`X-Role:4`）改别人的课 | 放行 |
| 不带头 / 不存在的 id | 下游 Spring Security **403 空体** / **404「成绩记录不存在」** |
| 批量删除裸数组 `[id]` | 别人的课 403、自己的课删成功；旧形状 `{ids:[...]}` ⇒ **400** |

> ⚠️ **写探针的靶是自建行**（`semester=1`，被"取最新一次"的投影遮蔽 ⇒ 对任何演示视图零影响），
> 结束即删；探针产生的 2 条 `score_audit_log` 也一并删掉（否则留下指向已删成绩行的悬空审计）。
> 核完 `scores` 恢复 259 行、`score_record` 246 行未动。
> ⚠️ 中文 body 在 MSYS bash 会按 GBK 发出去 ⇒ 探针 payload 一律用 ASCII，中文只走**显式百分号编码的查询参数**。

**未修边界**：① ~~`admin-service` 的 `POST /admin/import/scores` 仍写 `score_record`（登记）~~ ⇒
**已收口（W38 把 admin 导入也迁到 `scores`，W39 随即把这张已无人用的表整表删掉，见 §8.15）**；
② `/teachers/scores/by-class` 的入参仍是 `class_name` 字符串，同名班仍会被一并命中（要根治得改接口契约收 `class_id`）；
③ 教师端「成绩册/导出」沿用**分析口径**（过滤 `final_score IS NULL`）—— 司明那 4 行「期末未录入」因此不出现在教师页。
实测全库**没有**"只有未录入行"的课，所以没有任何数据被隐藏；但那 4 行里 3 行低于 60，
若哪天改成"不过滤"的口径，司明的挂科会从 3 变成 6（错误方向）—— 这也正是不能改的原因。
选项 **2** 的语义就是这条：**统一到 `scores` 口径 = 建库以来就存在的那套**，而不是统一到某个角色过去读的那张表。

**核过并排除的疑点**（免得下一个人重查）：迁移后全仓 `score_record` 的**活读方只剩 admin 导入那一处**
（`admin-service` `AdminController` 里查重的 `scoreRecordMapper.selectOne`）；`student-service` /
`counselor-service` / `warning-service` 对 `score_record` 的提及**全在注释里**（写的是"这里**原**读
`score_record`，已改 `scores`"的沿革），SQL 一律走 `ScoreCaliber` → `scores`。
⚠️ 另有一条**口径陷阱**：`academic_warning` 里的 40/47/56.5 是 `score_total`（总评），而 `scores`
同一行的 `final_score` 是 38/45/55 —— 两组数是**同样的三门课**，看列名再下结论。

⚠️ **同一族又两条「说法与事实不符」**（已修）：
`importScores` 的 javadoc 写「刻意没有平时/期末成绩，因为 `saveScores` 不读这两个 key、`insertScore` 的 INSERT 里也没有」
（`saveScores` 现在**会**读、`insertScore` 已随 `score_record` 废掉 ⇒ 前半句不成立）；
`ClassInfoMapper.countClassesNamedAndOwned` 的注释把"同名班分不开"归因于 `score_record` 只存 `class_name`
（教师端改读 `scores` 后成绩行不再携带 class_name，**边界还在但归因换了位置**，已改成"接口契约的上限"）。
⇒ **修完一条候选要顺手改台账那一条**，"说法"有三层：接口返回值 / 代码注释 / 文档台账。

**单测**：全量 reactor **627 例 / 0 失败**（基线 624）。`ScoreRecordServiceTest` 12 例按新语义重写
（钉"落库目标是 `scores`、不再碰 `score_record`"），`TeacherControllerScoreOwnershipTest` 6 例改判据为课程归属
（旧的那条"记录没有 teacherId 就放行"已随列消失而**失效**，换成"所属课程查不到 ⇒ 拒绝"）。
⚠️ `mvn -pl teacher-service test` 会解析到 `~/.m2` 里的**旧 academic-common jar**（缺 `CourseRecommendation`）⇒
`TestEngine ... failed to discover tests`，**必须带 `-am`**；全量回归必须跑 reactor（`-pl` 只覆盖点名模块）。

---

### 8.10 `/auth/change-password` 的「直连兜底」是个真洞（2026-10-10 W37）

**洞**：`user-service` 的 `POST /auth/change-password` 归属校验是「网关注入的 `X-User-Id`
与请求体 `userId` 不一致就拒」。但**直连服务时没有 `X-User-Id`** ⇒ 原实现**回落到信任请求体里的
`userId`**（那段注释把兜底写成"给无网关场景本地脚本用"）。

后果：**绕过网关直连 `:8082` 时改密接口不判归属**；又因演示库全部账号口令统一为 `123456`，
`oldPassword` 校验也过 ⇒ **任意已认证用户可改任意账号的口令**。
（2026-10-10 W34 实测：学生 token 直连打 `body.userId=18` 时**真写库、改掉了刘教授的密码**，已恢复。）
经网关时 `X-User-Id` 在、闸生效 ⇒ 这是**"网关是唯一防线"**的又一例（本仓红线：**网关不是网络边界**）。

**改法（fail-closed，身份只认两处、永不回落请求体）**，新方法 `resolveCallerId(request)`：

1. 先读 `X-User-Id`（网关验签后注入，正常入口）；
2. 没有则读 `Authorization: Bearer <token>`，用本服务密钥 `JwtUtil.getUserIdFromToken` 自解
   —— **直连场景**（`JwtAuthenticationFilter` 也是这么验的）；
3. 两处都拿不到 ⇒ `unauthorized("无法确定请求者身份")`，**绝不回落到请求体**。

请求体里的 `userId` 从此**只当被检查对象**：非 null 且 ≠ callerId ⇒ `forbidden("只能修改本人的密码")`。

| 场景 | 改前 | 改后 |
|---|---|---|
| 网关入口，改自己（头 27 + body 27） | 200 | 200（`changePassword(27,…)`） |
| 网关入口，改别人（头 27 + body 18） | 403 | 403 |
| **直连，带 token(27)，body 指定别人(18)** | ⚠️ **200，真改掉别人密码** | **拒绝** |
| 直连，带 token(27)，body 是本人(27) | 200 | 200（用 token 里的 27） |
| 直连，无头也无 token | ⚠️ 回落 body | **401** |

**判据**：+5 单测（`AuthControllerTest`，user-service 69→74），一律**成对**
（不只断言返回码，还要 `verify(..., never())` 证明没落到改密动作上）；
其中 `directConnect_cannotTargetAnotherUserViaBody` 是这个洞的**回归钉子**。
**反向打靶两个方向**：M1（只留头、拆掉 token 分支）⇒ 直连本人那条红；
M2（改回原始"无头就信 body"逻辑）⇒ 钉子的两条红。
全量 reactor **632 例 / 0 失败**。

> ⚠️ `AuthController.java` **在盘上是 CRLF**（行尾敏感的批量替换要用编辑工具，别用 python 文本模式）。
> ⚠️ 打靶中途那次"全绿"是**上一轮遗留的 `target/surefire-reports` 陈旧报告**（编译失败时 surefire 不跑、报告还在）
> ⇒ 看结果要同时看 mvn 输出里有没有 `Tests run:`。

### 8.11 「预警 ↔ 课程」原来不在库里，只在正文的散文里（2026-10-12 W39）

**用户报的现象**："教师端和管理员端预警信息和课程信息对应不上。"

**这条不属于 §8.6 的"接口是否诚实"（每个端点单独看都如实回了它有的东西），
而是"库里的**数据模型**缺一维"** —— 所以它既不是桩、也不是假成功，是**结构上的缺失**。
三者要分开答：桩 = 有接口没实现；假成功 = 说了做了没做；**这一条 = 关系从来没被存下来**。

诊断链条（全部实测，未用前端）：

1. `academic_warning` **没有 `course_id`**，只有 `student_id`（`student_profile.id` 空间）。
2. 扫描任务 `createWarning` 手里**已经有** `detail` 列表（每行带 `courseId/courseName/scoreTotal/semester`，
   来自 `FAIL_DETAIL_BY_STUDENT`），但 `m.put("courseDaily", ...)` 那里**只读 `scoreTotal` 和 `courseName` 就把 `courseId` 扔了**。
3. 于是"挂了哪几门课"只剩下正文字符串；三个消费方各自用不同办法应对：
   教师端不看课程（按班级过滤）、管理员端班级恒 `--`、PC 学生端**用正则从正文里抠课程名**。

**判"前端编造"的判据**：`Warnings.vue:234-243` 的 `extractCourseName()` 是正则
`/([一-龥]{2,6}?)[(（]?\d+\.?\d*分?/` —— 抠不出来就空着、抠错就是编造。
这是全仓**唯一**一处前端从散文里造结构化数据的写法（W21 那两条是**后端**编造假数据，不同族）。

**收口方式与判据**见 §3.6.4。台账取法上值得记的一条：

> ⚠️ **`rows=0` 不能当"闸生效"的证据** —— 本轮 `GET /teachers/warnings/18?onlyMine=true` 回 0 条，
> 看着像筛选对了，其实是**参数与数据两头恰好都不成立**（该教师是 31/32 班班主任，根本看不到这 4 条）。
> 放行面必须断言**行数 > 0**，否则"参数没对上"与"闸生效"在输出里长得一模一样（W30 已栽过一次，这是第二次）。
> 当活库数据**构造不出反例**时，正确的做法是**用单测构造那个形状**，而不是把 0 条读成通过。

> ⚠️ **计数器类判断一律 `SELECT MAX(id)+1`**（`information_schema` 缓存旧值，本仓已有记载）。
> 本轮 `academic_warning_course` 建表后自增计数正常，未触发该坑，但会话里查询一律走 `MAX(id)+1`。

**同轮订正的既有账**（§3.6.4 末）：`DataInitializer.EXPECTED_COLUMNS` 3 条基线过期
（`colleges` 9→7、`assistance_plans` 8→9、`academic_warning` 21→22）。
订正后 14 张表的基线自检才**第一次真的等于活库** —— 此前每个服务每次启动都打 3 条假漂移 ERROR，
把"schema 变更会被自检逮住"这个机制**自己喂瞎了**。判据 = 订正后重启服务，日志里
`[DataInit] 结构自检发现 N 处漂移` 归零。

**本轮验证**：全量 reactor **658 例 / 0 失败**（academic-common 28→31，teacher-service 118→119）；
`python db_backups/verify_invariants.py` ⇒ **41 项 / 0 失败**（⚠️ 见下方订正）；
活库 4 条预警 / 11 行明细逐条 `COUNT(*) == fail_count`。

> ⚠️ **2026-10-12 收尾订正（我必须自己认这条）**：这一行最初写的是「`verify_invariants.py` **42/42**」，
> **它是错的**。42/42 是**删 `score_record` 之前**的旧数，我是从磁盘上那份陈旧的 `_verify_invariants.txt`
> 里抄来的 —— 那份文件当时还明明打印着 `[PASS] 每行 score_record 都能在 scores 里找到对应学期的那条`。
> **删表之后我从没重跑过这个脚本**，而它里面 **9 条查询还在读 `score_record`**，所以真实状态是
> **33 通过 / 9 失败**（失败项全是 `[SQLERR] Table 'score_record' doesn't exist`）。
> 这正好是 §8.7 那条的反面教材：**"台账里写着的验证结论"也是"说法"，必须自己重跑一遍才算事实**。
> 已同步：`verify_invariants.py` 里 9 条 `score_record` 查询改写/移除（A 段 4 条、D 段 2 条、F 段 1 条、
> 全局 `PAIR`），并**补了 3 条真正守着 `scores` 的新检查**（悬空引用 / 分数值域 / 三元组唯一），
> 三条各自单独打红靶通过。现为 **41 项 / 0 失败**。详见 §8.16。

### 8.12 ⭐ `DataInitializer` 的「建库 + 种子 + 结构自检」**一次都没跑过**（2026-10-12 W39 顺手核出）

**这是本轮最重的一条，而且和被报的预警问题无关 —— 是顺手核出来的。**

**现象**：在 §3.6.4 的订正里我写了"订正这 3 条基线，14 张表的自检才第一次真的等于活库"。
为**验证这句话**，我起了一个新实例去看启动日志，结果 `DataInit` **一行都没有**。

**排查链（每一步都是实测，不是读代码推的）**：

1. `grep -c DataInit` 新起实例的日志 ⇒ **0**；`logs/` 全目录 grep `结构自检` ⇒ **零命中**（历史日志也没有）。
2. 在 `@Bean` 方法体**首行**插 `System.out.println` 探针 ⇒ 起实例后**探针 0 次** —— 说明不是"跑了但没日志"，是**方法压根没被调用**。
3. 把 `@ConditionalOnBean(DataSource.class)` 删掉，**其余一行不改**，重编重起 ⇒ **探针 2 次**（bean created + runner invoked），
   且日志出现 `[DataInit] 结构自检通过：14 张关键表列数均与基线一致。`
4. `git log -S` 定位：这个条件是 **2026-07-17 的提交 `15a1e841` 加进去的**（不是原始写法）。

**根因**：`@ConditionalOnBean` 只在**用户自己声明**的 bean 上可靠。本仓没有任何一处 `@Bean DataSource`
（11 个服务全靠自动配置），所以评估条件时 DataSource **可能还没注册** ⇒ 条件判假 ⇒ bean 静默跳过。
**它不报错、不打日志，功能整体不发** —— 这正是本仓反复记的那类形状（"没实现"和"实现了但没接上"要分开答）。

**后果**（不能夸大，逐条说清）：

| DataInitializer 的两条职责 | 实际影响 |
|---|---|
| 建库（全新库执行 `init_schema.sql` + 种子数据） | **活库是手工建的**（`academic_warning_system.sql` / `db_backups/`），所以演示环境没事 —— 但**换台机器从零起会得到一个空库**，而且不报错 |
| 结构自检（对照 `EXPECTED_COLUMNS`，漂移打 ERROR） | **完全没生效过**。本仓把"改表要在**五处**同步"写进规矩（§三 表注、§8.9），而**唯一的自动兜底一直是哑的** |

**改法**：删掉 `@ConditionalOnBean(DataSource.class)`（连带 import），并在原地留一段 `⚠️` 注释写清
"加过、为什么错、别再加回来"。去掉条件是安全的：11 个依赖 academic-common 的服务**全部**配了 `spring.datasource`，
`gateway-service` 根本不依赖本模块（已核 pom）。万一将来真有不配数据源又引本模块的服务，
Spring 会**明确报错**（"required a bean of type 'DataSource'"）—— 比"静默不跑"好得多。

> ⚠️ 我误以为当时运行的旧 jar 是"改前对照" —— 它不是（那是 15a1e841 之后打包的，同样带条件）。
> 真正的对照是**同一份代码、只差那一个注解**，这也正是判据必须写成"删掉条件 ⇒ 探针 2 次"的原因。

**验证（四条，全部实测）**：

1. `teacher-service` / `admin-service` 起实例 ⇒ 都打出 `结构自检通过：14 张关键表列数均与基线一致`，且**启动无异常**。
2. **反向打靶 M5**：把基线 `colleges` 从 7 改成 99 ⇒ 日志立刻
   `[DataInit] 结构自检发现 1 处漂移：colleges(实际 7 列, 期望 99 列)` ⇒ **这个闸是承重的，不是摆设**。
3. 新测试 `DataInitializerWiringTest`（academic-common 28→31）钉住**接线**：类上不得有
   `org.springframework.boot.autoconfigure.condition.*` 注解、`@Bean` 方法上不得有 `@ConditionalOnBean`、
   返回类型必须是 `CommandLineRunner`。本仓零 `@SpringBootTest`（起不了容器），所以这是静态反射判据 ——
   但它面对的正是**一个静态的、已经在日志里留下证据的**错误。
4. **反向打靶 M4**：把条件加回去 ⇒ `initDatabaseIsUnconditional` 条**准确打红**（报错文本点出这个注解），其余两条仍绿。

**顺带修掉一处"文档骗人去踩红线"**：漂移提示原本写
`请以线上库为准重建该文件（见 db_backups/rebuild_schema.py）` —— 这个闸**第一次真正生效**，
它打开就指向一个**本仓明令禁止运行**的脚本（`rebuild_schema.py` 从静态 dump 重建 `init_schema.sql`，会把新表/新列抹掉）。
已改成"三处要一起改 + 别跑 rebuild_schema.py"。

**未修边界**（如实记，不夸大）：本轮**未**在全部 11 个服务上起实例验证 —— 验了 teacher / admin 两个，
其余服务的 `datasource` 配置逐条 grep 过（都配了）。也没在**全新空库**上验证建库分支。

### 8.13 ⭐ `npx vite build` 的 BUILD SUCCESS **不能证明 `.vue` 的 `<script setup>` 是自洽的**（2026-10-12 W39 自己踩的）

**这条是我自己造的伤，也是我自己差点没发现的。**

**经过**：在 `TeacherWarnings.vue` 里做文本替换时，一次 `Edit` 把**整个 import 段**（5 行）静默吞掉了。
随后 `npx vite build` 照常报 `✓ built in 24.29s`。文件在浏览器里会**立刻**炸（`ref is not defined`），
但构建期**一行错都不报**。

**根因**：Vite/esbuild 走的是**转译**，不是类型检查 —— 它**不解析未定义标识符**。
`ref` / `computed` / `getTeacherId` 在产物里原样留着，直到运行期才抛 `ReferenceError`。
所以"前端构建通过"这类证据**只覆盖语法**，不覆盖引用完整性。这与 §8.9 的 `score_record` 一族是同一种形状：
**判据比结论弱一档**，绿灯照出来。

**发现方式**：不是构建报的，是我回头逐个数各文件 `<script setup>` 的 import 行数（`grep -c "^import"`）
——`TeacherWarnings.vue` 那一份少了 5 行。**其余 5 个改过的前端文件逐一数过，都完好**，只有这一份被吞。

**补的装置**：`tmp/check-vue-setup-refs.mjs`（`tmp/` 已 gitignore，与同目录 `check-unused-imports.mjs` 同类）。
判据：模板里用到的根标识符（`{{ }}` 插值 + `:prop`/`v-if`/`v-model`/`v-for` 绑定表达式）是否在
`<script setup>` 里 import/声明过。**两只眼睛各扫一遍**：PC `src/` 70 个 `.vue`、小程序 `src/` 65 个 `.vue`，**均为 0**。

> ⚠️ **这个检查器自己的第一版是瞎的，而且"报 0"看不出来** —— 见下一条。

**⭐ 检查器必须打靶，两个方向都要打。** 第一版直接对全树报 0，我看着像"干净"，
于是按本仓规矩**先造两个靶**再信它：

| 靶 | 期望 | 第一版实测 |
|---|---|---|
| 红靶：把 `TeacherWarnings.vue` 的 import 段**整块删掉**（复现真实损伤） | 必须报红 | **报 0！打不红** |
| 绿靶：一份正确写法的样本（嵌套对象 props / kebab 组件 / `$slots` / `in` 关键字 / `null`/`false` 字面量 / 对象字面量键 / 模板字符串 `${}`） | 必须 0 | 0 |

红靶打不红 ⇒ 这个检查器**对它存在的那件事完全瞎**。原因是我给判据加了个自认为合理的豁免：

```js
if (inCall) return   // "函数调用位置的名字多半没问题" —— 错的
```

而被删掉的那 4 个 import（`warningStatusText`/`warningStatusKey`/`warningStatusTagType`/`isPendingWarning`）
在模板里**恰好全都只出现在调用位置**（`warningStatusKey(w.status)`）。**豁免正好盖住了要抓的形态。**
删掉这条豁免后，红靶报出那 4 个名字，绿靶仍 0 ⇒ 这才叫"扫得到"。

> 这与 `abs-build-and-harness-false-negatives` 里那条一条路：
> **"报 0"的扫描器必须打靶证明它扫得到，真假两个方向各一个靶。** 本轮是第 N 次栽在同一处，
> 也再次说明"检查器输出 0"和"被检查的东西是干净的"是两件事。

**未修边界**：`tmp/check-vue-setup-refs.mjs` 是**启发式**的，不解析 Vue 的编译期语义 ——
它不认识 `defineOptions`、不认识 `<script>`（非 setup）导出的名字、不认识 `v-for` 解构重命名
（`v-for="{ id, name } in list"`）。它只保证"不误报已知正确写法、能抓到删 import"，
**不是**一个可以当门禁的静态分析器；真要门禁得上 `eslint-plugin-vue` + `no-undef`。

### 8.14 ⚠️ 「改表第四处」`schema_dump_20260916.sql` **早就不是活库快照**，而台账一直说它是（2026-10-12 W39 核出）

**起因**：写 §3.6.4 的"改表五处"表时（该行后来已改写，见 §3.6.4），我按 repo 规矩把 `db_backups/schema_dump_*.sql` 也列了进去，
还在心里记成"已改了"。实际去查 `git status` 时它**不在改动列表里** —— **我根本没改它**。
这正是本仓反复记的那类：**说法（台账）与事实（工作区）不符**，只不过这次说话的人是我。

**先量后判**（把两张文件的 `CREATE TABLE` 表名集合做差）：

| | 表数 | 只在 `init_schema.sql` | 只在 dump |
|---|---|---|---|
| `init_schema.sql` | 51 | — | — |
| `schema_dump_20260916.sql` | 47 | `academic_warning_course`、`admin_tasks`、`course_recommendation`、`user_settings` | `academic_warning_bak_20260916`（历史快照） |

**关键不在 `academic_warning_course`**（那是我这轮没加）。**关键是 `admin_tasks` / `course_recommendation` / `user_settings`
三张表早在 W39 之前就不在 dump 里**（W1.14 的 `admin_tasks_20261002_apply.sql` 在 `db_backups/` 里躺着，dump 没跟）。
即：**"两边漂移 = 0" 从 2026-10-02 起就不成立了**，而 `答辩准备-启动与演示手册.md:1135` 至今写着
「已两处都改…现在两边漂移 = **0**」。跨 10 天、两轮改动，没人回头量过。

> ⚠️ 判"哪边过期"要**去问活库**，不能只看两张文件谁新：`admin_tasks` / `course_recommendation` /
> `user_settings` / `academic_warning_course` **四张在活库里全部存在**（`information_schema` 实测）
> ⇒ **过期的是 dump，不是 init_schema.sql**。

**为什么没有自动闸**：`DataInitializer.verifySchema` 只对 `EXPECTED_COLUMNS` 里那 14 张**代码真正读写**的表做列数比对；
dump 里多一张少一张、`rebuild_schema.py` 会不会把它们抹掉，**没有任何一处自动检查**。
dump 只在"有人手动跑 `rebuild_schema.py`"时才起作用 —— 而那个脚本**本仓明令禁止运行**（§8.12 也再次提醒）。
⇒ 它是个**只有踩到才炸**的雷：平时是死的，一旦运行就把 `admin_tasks` / `user_settings` 等整表从 `init_schema.sql` 里**抹掉**。

**本轮怎么做（**没有**顺手把被删的 4 张表补进 dump）**：

- 补进去是**沿用一个错的前提**（"dump 必须与活库一致"），而且补完 `rebuild_schema.py` 照样会按自己的规则重排/重写
  `init_schema.sql`，谁也保证不了下次还对上。**正确的动作是把前提拆掉，不是继续喂它。**
- 因此只做两件**如实**的事：① 订正 §3.6.4 那张表 + 本节的措辞，**把"改表四处"降为"三处 + 一处已失效"**；
  ② 在 `db_backups/schema_dump_20260916.sql` 顶部与 `rebuild_schema.py` 的 docstring 里写明
  「本文件已过期、不被任何自动流程使用、`rebuild_schema.py` 禁止运行，理由与被抹掉的 4 张表见 FEATURES §8.14」。

**未修边界**（本节首次写下时）：`rebuild_schema.py` 与那张 dump **仍在仓库里、仍是可运行的**（当日只加警示，没删）。
真要收口，得由人决定：删掉脚本 + dump，或把 `init_schema.sql` 定为唯一权威并删掉重建路径。

> **✅ 2026-10-12 收口（人工拍板后执行）**：`db_backups/rebuild_schema.py` 与
> `db_backups/schema_dump_20260916.sql` **已 `git rm` 整块删除**（删除前 sha256 留档：
> `830ed4f8…091` / `bdd8f3d3…df5`；需要对照时从 `fe8c468d` 取回）。
> **`init_schema.sql` 由此成为唯一权威结构**，"改表"从此是**三处**：`init_schema.sql` + 活库 +
> `DataInitializer.EXPECTED_COLUMNS`（表在 `EXPECTED_COLUMNS` 里时）。
> 全仓 7 处引用已同步改口径：4 个 `db_backups/*_apply.sql` 的"不要跑"改成"已删除"、
> `DataInitializer` 的漂移提示不再指向一个不存在的脚本、`答辩准备-启动与演示手册.md` 三处。
> ⚠️ 删掉的是**过期 dump**，不是"删东西让仓库变干净" —— 判据是那份 dump 缺 4 张活库真实存在的表，
> 而脚本会以它为唯一权威反向重写 `init_schema.sql`。

### 8.15 收尾：死表 `score_record` 已整表删除（2026-10-12 W39）

**这是 §8.9 那条「两套成绩表」的最后一笔。** W36 把教师端、W38 把管理员导入都迁到权威表
`scores` 之后，`score_record` 就只剩历史数据了 —— 本轮把它删掉。

**删除前的判据（四条，全是实测，不是读代码推的）**：

| 查什么 | 怎么查 | 结果 |
|---|---|---|
| SQL 层读写 | `grep -rn --include=*.java --include=*.xml -E "(FROM\|INTO\|UPDATE\|JOIN\|DELETE FROM)[[:space:]]+score_record"` | **零命中** |
| mapper XML | `find -name "*.xml" -path "*mapper*"` | **全仓一个都没有**（SQL 全在注解里） |
| MyBatis-Plus 泛型 | `grep -rn "BaseMapper<ScoreRecord>\|< *ScoreRecord *>"` | **零命中** ⇒ 没有任何按 `@TableName` 自动生成 SQL 的入口 |
| 库内依赖 | `information_schema` 的 `TRIGGERS` / `VIEWS` / `ROUTINES` 正文 LIKE `%score_record%` | **零行** |

> ⚠️ **判"表死没死"不能只 grep Java** —— 前三轮都在这一步差点放过 MyBatis-Plus：
> `BaseMapper<T>` 和 `QueryWrapper<T>` 会在**运行期**按 `@TableName` 拼 SQL，源码里根本搜不到
> `score_record` 这个字符串。所以第三条判据（"有没有 `BaseMapper<ScoreRecord>`"）是**必须单独查**的，
> 它比第一条更接近真相。这四条里少任何一条，"删得安全"都是猜的。

**特别说明：实体 `ScoreRecord` 类留下了，表没了 —— 这不是没删干净。**
`academic-common/entity/ScoreRecord.java` 上仍写着 `@TableName("score_record")`，但它**只被当只读投影类型**用：
teacher-service 的 `ScoreRecordMapper`（名字是历史残留）里每一条 SQL 都是 `FROM scores` 或
`FROM ScoreCaliber.LATEST_VALID_SCORE`，返回类型是这个类，方便控制器消费面不变。
把类一起删掉要动 7 个文件的签名，收益是把"名字骗人"换成"没有名字"—— 本轮**没做**，登记在未修边界里。

**改表四处，本轮同步了四处**：

| 处 | 动作 |
|---|---|
| `init_schema.sql` | 删掉 `CREATE TABLE score_record`，原地留注释说明"为什么没有它了" |
| 活库 | 跑 `db_backups/score_record_drop_20261012_apply.sql`：删前打 246 行 / 22 列留档，再 `DROP TABLE IF EXISTS`。实测净表数 **81 → 80**，`scores` 259 行未动 |
| `DataInitializer.EXPECTED_COLUMNS` | **删掉 `score_record` 那条基线**（14 → 13 项）。不删就是每个服务每次启动一条 `score_record(表不存在)` 的 ERROR —— 一声永远修不掉的假警报 |
| `db_backups/*_{apply,rollback}.sql` | apply 是幂等 `DROP TABLE IF EXISTS`；rollback 是 **`mysqldump` 原样导出**（46476 字节，含 CREATE TABLE + 全部 246 行），逐字节可还原 |
| ~~原"第五处" `schema_dump_20260916.sql`~~ | **已随之删除**（2026-10-12，见 §8.14）—— 它早就不是活库快照，且"以它为权威重建 `init_schema.sql`"这条路径整块移除后，"处数"从五降为四 |

**钉住回归**：`DataInitializerWiringTest` 新增两例 ——
`expectedColumnsHasNoScoreRecord`（基线里不得再有 `score_record`）与
`expectedColumnsStillHasScores`（权威表 `scores` 的基线必须在，防止"顺手删表"删过头）。
**两条是配对的**：只有"不该有的没有"，没有"该有的还在"，这类改动照样能全绿。

**顺带订正三份文档里过期的说法**（`score_record` / `scores` 双写、表名复数 `academic_warnings`）：
`系统功能模块介绍.md`（6 处 + 顶部订正说明）、`FEATURES.md` §7 表注与 §8.9 的未修边界①、
`答辩准备-启动与演示手册.md`。**这些文档写的都是 2026-10-10 之前的实现**，
而代码 10-10 就改了 —— 又一次印证 §8.7 那条："说法至少有三层，改完代码要顺手改台账"。

**未修边界**：① `ScoreRecord` 实体与 `ScoreRecordMapper`（teacher-service + academic-common）**名字仍在骗人**
（叫 Record，读的是 `scores`）—— 本轮只加注释，未改名；② ~~`schema_dump_20260916.sql` 与 `rebuild_schema.py`
仍在仓库里且仍可运行~~ → **已于同日删除收口，见 §8.14 末尾**；③ 本轮**未**在全新空库上验证"不再建 `score_record`"这条路径。

### 8.16 ⭐ 删表之后**没重跑**不变量脚本，于是台账里多了一条我自己的假验证（2026-10-12 W39 收尾自查）

**形状**：删掉一张表 ⇒ 某个"常驻体检"脚本里还有查询读它 ⇒ 脚本红了，而我**没跑**，
把它删表前的绿数（42/42）当成本轮的验证结论写进了 §8.11、`db_backups/_verify_invariants.txt` 与提交信息 `dbdabb69`。

**怎么发现的**：收尾时按惯例跑一次 `python db_backups/verify_invariants.py`，
得到 **33 通过 / 9 失败**，失败项 9 条**全是** `[SQLERR] Table 'score_record' doesn't exist`。
去翻磁盘上的 `_verify_invariants.txt`（未跟踪、停留在 2026-10-03），它第 4 行赫然是
`[PASS] 每行 score_record 都能在 scores 里找到对应学期的那条` ⇒ **42/42 是删表前最后一次运行的产物**。

> ⚠️ 我写这句时**没有跑它** —— 这正是本仓反复记的那类失衡：**"说法"（台账 + 提交信息）与"事实"（工作区）不符**。
> 而且这次说话的人是我自己，栽的也是本仓已经记过至少三次的同一个坑：
> **结果文件可能是上一轮的 —— 必须自己重跑，且要看清它是哪个入口/哪一刻写出来的。**

**修法（`db_backups/verify_invariants.py`，9 条查询）**：

| 原检查 | 处理 |
|---|---|
| A 段 4 条：`score_record` ↔ `scores` 逐列对齐、`score_record.score_level` 反查 | **移除**（没有了第二张表；`scores` 里从来没有 `score_level`，它是 `ScoreMapper` 用 `ScoreCaliber.SCORE_LEVEL_CASE` 现算的） |
| A 段 2 条（`scores` 自身的 grade/grade_point 换算） | **保留**（本来就是对 `scores` 单向校验，不依赖 `score_record`） |
| A 段"学年 = 学号前四位…"、"不许 NULL 学期/学年" | **改写成只查 `scores`**（去掉了 `+ (SELECT … FROM score_record)` 那一半） |
| 全局 `PAIR` / `L` 两个常量 | `PAIR` 无引用**删除**；`L` 无引用**删除**（本仓"零引用=删"） |
| D 段"courses / score_record / course_plans 的 teacher_id 都不悬空" | 收窄为 **courses / course_plans** |
| D 段"成绩记录里的教师 == 该门课的授课教师（三张表同一个口径）" | 收窄为 **course_plans 单侧**（`score_record.teacher_id` 那半消失；`scores` 无 `teacher_id` 列，归属走 `courses.teacher_id`） |
| F 段"两表行数 259 / 246" | 改为 **单表 259** |

**不增不减的诚实做法：同时补 3 条真承重的新检查**（`scores` 一跃成为**唯一**权威成绩表，
而 D 段此前**从没有一条**守着它自己的引用完整性）：

1. `scores.student_id` / `course_id` 都**不悬空**；
2. 三个分数列（`score_total` / `regular_score` / `final_score`）都在 **0..100** 内；
3. `(student_id, course_id, semester)` **不许重复**（重复即"同一门课同一学期两条成绩"）。

**判据（全部实测）**：改完 `python db_backups/verify_invariants.py` ⇒ **41 项 / 0 失败**（退出码 0）。
三条新检查**各自单独打红靶**（不是"一次全红"）：

| 靶 | 变异 | 结果 |
|---|---|---|
| M1 悬空 | `WHERE sp.id IS NULL` → `IS NOT NULL` | FAIL，实际 **259**，恰 1 条红 |
| M2 值域 | `score_total < 0` → `< 60` | FAIL，实际 **17**，恰 1 条红 |
| M3 重复 | `HAVING COUNT(*) > 1` → `> 0` | FAIL，实际 **259**，恰 1 条红 |

> ⚠️ M3 的第一版变异（`GROUP BY` 去掉 `semester`）返回的是 `[SQLERR]` —— **那叫"脚本坏了"，不叫"抓到了违例"**。
> 判据绿色/红色都要能分清"被守的东西出问题"和"守它的东西自己出问题"。换成一个合法 SQL 变异才真正打红。

收尾 `cp` 从备份还原，`diff` 确认字节一致。

**未修边界**：① 磁盘上那份未跟踪的 `db_backups/_verify_invariants.txt`（2026-10-03 的旧产物）
**没删**，它继续留着会误导下一个人；根治办法是让脚本把它写进一个明确标日期的路径，属独立改动，未做。
② 分支从未推送（无 upstream），所以历史提交 `dbdabb69` 的信息里那句"42/42"**改不掉**（改它要重写
`fe8c468d` 的哈希，而 §8.14 正以 `fe8c468d` 作为"删文件取回点"）⇒ 只能在本节如实订正，未 amend。

---

> **文档维护者：** 系统自动生成  
> **最后更新：** 2026-10-12（W39）
