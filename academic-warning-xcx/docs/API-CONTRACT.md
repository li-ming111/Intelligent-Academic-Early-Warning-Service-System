# 小程序端接口契约（实测）

> 全部结论来自 2026-09-29 对**运行中的后端**实测（网关 8076 + 13 个微服务 + MySQL `academic_warning_system`），
> 并与控制器注解、`information_schema` 交叉核对。凡标 ⚠️ 的都是踩过的坑。
>
> **写页面前先查本文档，不要照抄旧计划里的接口清单** —— 旧清单把 `/students` 前缀丢了、
> 把不可达的顶层路由写了进去，且若干 id 空间记反了。

---

## 1. 网关路由表是可达面的**唯一**边界

`gateway-service/application.yml` 只配了下面这些前缀。**不在表里的路径一律 404**，
不管对应服务里有没有这个 Controller。

| 前缀 | 目标服务 | StripPrefix | 备注 |
|---|---|---|---|
| `/api/auth/**` | user-service | 无 | user-service 是唯一配了 `context-path: /api` 的服务 |
| `/api/users/**` | user-service | 无 | 同上 |
| `/api/students/**` | student-service | 1 | |
| `/api/teachers/**` | teacher-service | 1 | |
| `/api/counselors/**` | counselor-service | 1 | |
| `/api/admin/**` | admin-service | 1 | `AdminController` base `/admin` |
| `/api/college-admin/**` | admin-service | 1 | `CollegeAdminController` base `/college-admin` |
| `/api/courses/**` | course-service | 1 | 只有 `CourseController` |
| `/api/colleges/**` | college-service | 1 | |
| `/api/messages/**` | message-service | 1 | ⚠️ 见 §5 |
| `/api/warnings/**` | warning-service | 1 | |
| `/api/resources/**` | resource-service | 1 | |
| `/api/roles/**`、`/api/permissions/**`、`/api/role-permissions/**` | auth-service | 1 | |
| `/api/ai/**` | ai-service | 1 | |
| `/api/auth/colleges`、`/api/auth/majors` | college-service | 2 + Rewrite | 特例 |

### ⚠️ 因此这些**不可达**（服务里存在，但没有路由）

| 看似该有的路径 | 实际情况 |
|---|---|
| `/api/appeals/**` | student-service 里是 `/students/appeals/**`，**必须带 `/students`** |
| `/api/benchmark/**` | 实为 `/students/benchmark/**` |
| `/api/class-ranking/**`、`/api/classes/**` | 实为 `/students/class-ranking/**`、`/students/class-members/**`；`/classes/{id}/**` 在 teacher-service 下 |
| `/api/notification-center/**` | 实为 `/students/notification-center/**` |
| `/api/evaluations/**`、`/api/subscription/**`、`/api/assistance-plans/**` | 均实为 `/students/**` 之下 |
| `/api/course-plans/**`、`/api/teacher-courses/**`、`/api/teaching-resources/**` | **真的不可达**，网关没有对应路由（course-service 的这两个 Controller 够不着） |
| `/api/student-by-user/**` | 实为 `/students/student-by-user/**` |

---

## 2. 三套 ID 空间 —— 本项目最容易写错的地方

| 名字 | 含义 | 出现在哪 |
|---|---|---|
| `users.id` | 用户账号主键（司明 = **27**） | **所有控制器路径参数**（见下）；`student_profile.user_id`；`classes.teacher_id` |
| `student_profile.id` | 学生档案主键（司明 = **3**） | `scores.student_id`；`academic_warning.student_id`；**响应体里回显的 `studentId`** |
| 学号字符串 | `"2023020616"` | 极少数响应里也叫 `studentId` ⚠️ |

### 铁律

1. **`/api/students/**` 的所有路径参数都是 `users.id`。**
   实测：27 全部有数据；传 `3`（profile id）**HTTP 仍是 200、返回空数组**。
   → 传错 id **不报错**，页面显示"暂无数据"，是最难查的一类 bug。
2. **`classes.teacher_id` 是 `users.id`**（于凤海 profile id=1 / users.id=29，班级记的是 29）。
3. **但 `teacher_course.teacher_id` 是 `teacher_profile.id`**（取值 1/2/3/5）。
   **同一个列名 `teacher_id` 在两张表里是两个 id 空间**。教师首页"授课课程 0"就是这个引起的候选原因之一。
4. ⚠️ **`studentId` 这个词有三种含义，禁止跨接口传递**：
   - `/students/scores/{uid}` 行内 `studentId: 3` → profile id
   - `/students/27` 的 `studentId: "2023020616"` → **学号字符串**
   - 路径参数 `{studentId}` → 其实是 `users.id`
   安全做法：**只用 `useUser()` 里存的 `userId`，永不从响应体里取 id 回填 URL**。

---

## 3. 学生端实测载荷

统一信封：`{code, message, data, timestamp, success}`，取 `data`。
⚠️ **登录接口例外**：无论成败 HTTP 都是 200、`code` 都是 200、`success` 都是 `true`，
真实结果只看 `data.status`（`success`/`error`）与 `data.token` 是否存在。

| 路径（参数 = `users.id`） | data 形状 |
|---|---|
| `GET /students/dashboard/{uid}` | `{studentId:27, studentName, className, majorName, gpa:1.62, warningCount:1, redWarnings:null, yellowWarnings:null, assistanceCount:null, warningLevel:"2"(字符串), failedCoursesCount:3, totalCoursesCount:8, completedCredits:17, requiredCredits:120, recentWarnings:[…]}` |
| `GET /students/scores/{uid}?semester=` | 数组，行 `{id, studentId:3, courseId, semester:"5"(字符串!), year, regularScore, finalScore, scoreTotal, grade, gradePoint, courseName, credits, createdAt}` |
| `GET /students/warnings/{uid}` | 数组，`{id:395, studentId:3, studentName, ruleId, type:1, title:"中级预警", description, warningLevel:2, warningMessage, triggerScore, status}` |
| `GET /students/assistance/{uid}` | 数组，`{id:1001, title, content, description, status:1, statusText:"进行中", progress:50, counselorId, createdAt}` |
| `GET /students/assistance-plans/{uid}` | 同上（与 `/assistance` 同源） |
| `GET /students/appeals/{uid}/list` | 数组 |
| `GET /students/appeals/{uid}/statistics` | `{total:1, approved:0, rejected:0, pending:1}` ⚠️ `rejected = total-pending-approved`，已撤回(3)被并进 rejected |
| `GET /students/benchmark/{uid}/latest` | `{studentId:3, studentName, className, studentGpa:1.62, classAvgGpa:2.83, majorAvgGpa:2.83, classRank:21, classTotal:24, majorRank:21, majorTotal:24, coursesPassed:5, coursesFailed:3, creditsOnTrack:17, requiredCredits:120}` |
| `GET /students/class-ranking/{uid}` | `{studentId:3, classId:1, total:24, rank:21, semester:null, list:[{profileId, studentNo, studentName, gpa, gradedCredits, className}]}` |
| `GET /students/{uid}/class-info` | `{classId, classIdentifier:"计科2306班", name:"司明", rank:21, rankInClass:"21/24", totalStudents:24, className, studentNo:"2023020616"}` |
| `GET /students/{uid}/gpa` | 数组 `{semesterLabel:"2025-2026-2", semester:6, studentGpa:3.87}` |
| `GET /students/{uid}` | **档案** `{id:3, userId:27, studentId:"2023020616", name, gender, collegeId, majorId, classId, className, enrollmentYear, privacyLevel}` |
| `GET /students/notification-center/{uid}/list` | `{total, pageSize, page, list:[…]}` ⚠️ `total` 恒 0，见 §5 |
| `GET /students/messages/{uid}` | 数组 `{id:1001, userId:27, title, content, type:"warning"(字符串!), isRead:0, pushChannel:"app", warningId:395, createdAt}` |
| `GET /students/evaluations/{uid}/list` | 数组（**当前库为空**） |
| `GET /students/suggestions/{uid}` | 数组（实测 5 条） |
| `GET /students/settings/{uid}` | ⚠️ **假的**，见 §5 |

### 学期字段有两套写法，别统一

- `/students/scores` 行：`semester: "5"`（**数字字符串**）+ 独立 `year: 2025`
- `/students/{uid}/gpa`、`class-ranking`：`semester: 6`（**int**）+ `semesterLabel: "2025-2026-2"`（`YYYY-YYYY-N` 形态）

`?semester=` 筛选**只认数字**（`semester=5` → 5 行，`semester=99` → 0 行）；
⚠️ 传 `2025-2026-1` 这种标签**不报错也不筛选**，静默返回全量 29 行。

---

## 4. 设置：两个同名接口，只有一个真落库

| 接口 | 真相 |
|---|---|
| `GET /students/settings/{uid}` | 返回 `{displaySettings, privacySettings, notificationSettings}` —— **与 `student_settings` 表的列（`notification_enabled`/`email_notifications`/`theme`）对不上，是拼出来的假对象** |
| `PUT /students/settings/{uid}` | **纯桩**：`return ApiResponse.success(Map.of("success", true))`，**一个字段都不写库** |
| `GET /api/users/{uid}/settings` | ✅ **真**。查 `user_settings`，无行时返回 `defaultSettings()`，**不落库**（`id: null`） |
| `PUT /api/users/{uid}/settings` | ✅ **真**。有行 `updateById`、无行 `insert`；**支持部分更新**（null 字段被 `FieldStrategy.NOT_NULL` 跳过） |

**结论：设置页只能走 `/api/users/{uid}/settings`。**
用 `/students/settings` 会导致"改完显示成功、退出再进全变回去"。
修密码走 `POST /api/auth/change-password`。

---

## 5. 已确认的真缺陷（页面必须绕开或按现状呈现）

| # | 现象 | 证据 | 页面应对 |
|---|---|---|---|
| 1 | **通知中心 `total` 恒 0** | `/students/notification-center/27/list` → `{total:0, …, list:[2 条]}` | **不能用 `total` 驱动 z-paging**，会永远停在第一页；改用 `list.length < pageSize` 判到底 |
| 2 | **订阅偏好接口 100% 500** | `/students/subscription/27/preferences` → FK 约束失败：`subscription_preferences.student_id` 外键指向 `student_profile.id`，而接口传的是 `users.id` | 订阅页**先不做**，或只做只读展示 |
| 3 | 后端把**原始 SQL 报错**当 message 返回 | 同上，`message` 里是完整的 `SQLIntegrityConstraintViolationException` 堆栈 | `request.js` 的 `pickMessage` 应对 500 兜底成中文提示，不透传 |
| 4 | `warningLevel` **有的接口给中文、有的给 int** | `/teachers/warnings/{id}` 给 `"严重"/"中度"/"轻度"`；`/students/warnings/{id}` 给 `2` | 一律过 `utils/warning.js` 的 `normalizeWarning()` |
| 5 | `status` 同理（教师端中文、其余 int） | `"待处理"/"已处理"` vs `0/1` | 同上 |
| 6 | `POST /teachers/warnings/{id}/process` **不落库** | 只回 `{status:"已处理"}` 不写库 | 教师端预警页**只读**，不放处理按钮 |
| 7 | `/students/dashboard` 的 `redWarnings`/`yellowWarnings`/`assistanceCount` **恒 null** | 实测 | 分级统计自己从 warnings 列表算；总数可直接用 `warningCount` |
| 8 | `messages` 与 `notification-center` 返回**同一条 id=1001** | 实测两者都是 2 条同样的记录 | 视为同一数据源，不要当成两个独立功能各做一套 UI |
| 9 | `/api/messages/**` 路由疑似不通 | `MessageController` base 为空，StripPrefix=1 后会多出一层 `/messages` | 用 `/students/messages/{uid}`，别碰 `/api/messages/**` |
| 10 | `/students/evaluations/{uid}/list` 当前**空** | 实测 `[]` | 帮扶评价页要有像样的空态，不能白屏 |

---

## 6. 客户端实现约束（由上面推导）

1. **baseURL**：H5 dev 走 `/api`（vite 代理到 8076）；小程序走 `http://localhost:8076/api`。见 `src/services/request.js`。
2. **dev server 端口 5180**，且**必须 `host: true`**。只写端口时 Node 绑 `::1`（仅 IPv6），
   Chrome 会 `ERR_CONNECTION_REFUSED` 而 curl 正常 —— 表现是"自动化时好时坏"。见 `vite.config.js`。
3. **401 防重入**：网关 401 体是 `{"code":401,"message":"缺少认证信息"}`（**没有 `data` 字段**），
   且并发请求会同时收到 401，必须用 latch 压住多次 `reLaunch`。
4. **503**：下游没起时网关回 Spring 默认错误页 `{timestamp,path,status,error:"Service Unavailable",requestId}`。
   **不能把英文 `error` 弹给用户**，`pickMessage` 对 5xx 不采信该字段。
5. **判成功不能用 HTTP 或 `code`** —— 登录失败也是 200/200，只能看 `data.status` 与 `data.token`。
6. **id 一律取 `useUser()` 的 `userId`**，不从响应体回填。
