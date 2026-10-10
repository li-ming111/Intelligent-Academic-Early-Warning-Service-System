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
6. 写入 `academic_warning` 表（**单数**）

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
| `score_record` | 成绩记录表 | id, student_id, course_id, teacher_id, score, score_level |
| `scores` | **权威成绩表**（`score_record` 经 `syncToScores` 同步而来，分析/预警读它） | id, student_id, course_id, score_total, regular_score, final_score, grade_point |
| `academic_warning` | 预警记录表（**单数**） | id, student_id, warning_level, status(0待处理/1已处理/2已解除) |
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

---

> **文档维护者：** 系统自动生成  
> **最后更新：** 2026-10-08
