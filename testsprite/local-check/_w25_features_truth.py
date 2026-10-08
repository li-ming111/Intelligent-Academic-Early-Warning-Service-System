# W25：把 FEATURES.md 改成"事实"。**只改文档，不动一行代码。**
#
# 为什么用字节级脚本而不是 Edit：FEATURES.md 是 CRLF(566/0)，文本模式改写会把
# CRLF 静默抹成 LF（memory 里 W21 栽过一次）。这里一律按字节替换，每处断言命中恰好一次，
# 收尾用 b.count(b'\r\n') / b.count(b'\n') 复核。
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

P = r"D:/ABS/FEATURES.md"
d = open(P, 'rb').read()

def b(lines):
    return '\r\n'.join(lines).encode('utf-8')

def sub(cur, old, new, tag):
    assert cur.count(old) == 1, f"[{tag}] 命中 {cur.count(old)} 次, 期望 1"
    return cur.replace(old, new), tag

EDITS = [
    # ---- 1. §3.5.2 启用/禁用用户的真实路径（且没人调） ----
    (["| `PUT /admin/users/{id}/status` | 启用/禁用用户 |"],
     ["| `POST /admin/users/{id}/toggle-status` | 启用/禁用用户（**切换**语义：翻转当前状态，不是\"设为某个值\"）。⚠️ 后端有、PC 封装有，但**没有任何页面调用它** —— 见 §八 |"],
     "§3.5.2 user status"),

    # ---- 2. §3.5.7 导入导出：这两条路径全仓不存在 ----
    (["| `POST /admin/data/import` | 导入数据（学生/成绩） |",
      "| `GET /admin/data/export/{type}` | 导出数据 |"],
     ["| `POST /admin/import/students` | 导入学生（multipart + 服务端 POI 解析） |",
      "| `POST /admin/import/scores` | 导入成绩（同上） |",
      "| `GET /admin/import/template/{type}` | 下载导入模板（真 xlsx） |",
      "| `GET /admin/export/{statistics,users,students,teachers,colleges,scores,warnings,insufficient-students,history}` | 各类导出 |",
      "| `GET /admin/export/reports/{fileName}` | 下载已生成的报表文件 |",
      "",
      "> ⚠️ 此前这里写的是 `POST /admin/data/import` 与 `GET /admin/data/export/{type}` ——",
      "> **这两个路径全仓任何服务里都不存在**，是纯文档错误（2026-10-05 核对）。真实路径见上。",
      "> 其中 `/admin/export/insufficient-students` 后端与 PC 封装都在，但**没有页面调用**（见 §八）。"],
     "§3.5.7 import/export"),

    # ---- 3. §3.6.3 预警接口：四条路径不存在 ----
    (["| `GET /warnings/{studentId}` | 查询学生预警 |",
      "| `POST /warnings/generate` | 手动触发预警生成 |",
      "| `PATCH /warnings/{id}/process` | 处理预警 |",
      "| `PATCH /warnings/{id}/release` | 解除预警 |",
      "| `GET /warnings/statistics/{counselorId}` | 预警统计 |"],
     ["| `POST /warnings/scan` | **手动触发一次全量重扫**（`WarningScheduledTask.triggerNow()`），返回本次新生成条数 |",
      "| `POST /warnings/generate` | 手工生成**一条**预警（body `studentId` 必填，可选 `ruleId`/`warningLevel`/`warningMessage`） |",
      "| `POST /warnings/handle` | 处理预警（记录处理意见） |",
      "| `PUT /warnings/status/{warningId}` | 改预警状态 |",
      "| `GET /warnings/list` | 预警列表 |",
      "| `GET /warnings/student` | 按学生查询预警 |",
      "| `GET /warnings/level` | 按等级查询 |",
      "| `GET /warnings/detail/{warningId}` | 预警详情 |",
      "",
      "> ⚠️ 此前这里写的 `GET /warnings/{studentId}`、`PATCH /warnings/{id}/process`、`PATCH /warnings/{id}/release`、",
      "> `GET /warnings/statistics/{counselorId}` **四条都不存在**（2026-10-05 核对）。",
      "> 且上面这 **8 条前端一条都没调** —— 预警实际由每日 02:00 的定时任务产生，",
      "> 各端读取走自己的端点（`/counselors/warnings/**`、`/students/warnings/**`、`/admin/warnings`）。见 §八。"],
     "§3.6.3 warning api"),

    # ---- 4. §3.7 AI：补两条漏记的端点 ----
    (["| `GET /ai/assess-risk/{studentId}` | 快速风险评估 | ✅ |"],
     ["| `GET /ai/assess-risk/{studentId}` | 快速风险评估 | ✅ |",
      "| `GET /ai/student/{studentId}/suggestions` | 学习建议（按学生） | ✅ |",
      "| `GET /ai/predict-warnings/{studentId}` | 预警预测（按学生） | ✅ |"],
     "§3.7 add 2 rows"),

    # ---- 5. §3.7 结尾：说清只有 /ai/chat 有界面 ----
    (["**容灾机制：** 通义千问 API 不可用时，自动降级为规则引擎（`AIServiceImpl`），确保核心功能不中断。"],
     ["**容灾机制：** 通义千问 API 不可用时，自动降级为规则引擎（`AIServiceImpl`），确保核心功能不中断。",
      "",
      "> ⚠️ **2026-10-05 核对：上面这 18 个端点里，只有 `POST /ai/chat` 有界面入口。**",
      "> PC 端全部 `/ai/` 调用只出现在**两处** —— `AIAssistant.vue`（悬浮助手）与 `CounselorWarnings.vue`（预警解读）；",
      "> 其余 **14 个封装方法全仓零调用点**；小程序端**一个 AI 调用都没有**。",
      "> 也就是说：\"AI 智能分析\"目前**等于\"AI 对话\"**，其余能力**接口在、界面没有**。见 §八。"],
     "§3.7 wiring note"),

    # ---- 6. §4.2 表名 ----
    (["生成预警记录 (academic_warnings)"],
     ["生成预警记录 (academic_warning)"],
     "§4.2 table name"),

    # ---- 7. §五 加密方式（同文档 §3.1 自己写着 BCrypt） ----
    (["| 数据安全 | 密码 MD5 加密 + MyBatis #{} 防SQL注入 |"],
     ["| 数据安全 | 密码 **BCrypt** 加密（`BCryptPasswordEncoder`）+ MyBatis #{} 防SQL注入 |"],
     "§五 bcrypt"),

    # ---- 8. §七 表名 + scores 的真实地位 ----
    (["| `scores` | 成绩明细表（辅导员端用） | id, student_id, course_id, score_total, regular_score, final_score, grade_point |",
      "| `academic_warnings` | 预警记录表 | id, student_id, warning_level, status(0待处理/1已处理/2已解除) |"],
     ["| `scores` | **权威成绩表**（`score_record` 经 `syncToScores` 同步而来，分析/预警读它） | id, student_id, course_id, score_total, regular_score, final_score, grade_point |",
      "| `academic_warning` | 预警记录表（**单数**） | id, student_id, warning_level, status(0待处理/1已处理/2已解除) |"],
     "§七 table names"),

    # ---- 9. 版本/日期 ----
    (["> 版本：1.0.0 | 更新日期：2026-06-04"],
     ["> 版本：1.0.0 | 更新日期：2026-10-05"],
     "header date"),
    (["> **文档维护者：** 系统自动生成  ",
      "> **最后更新：** 2026-06-04"],
     ["> **文档维护者：** 系统自动生成  ",
      "> **最后更新：** 2026-10-05"],
     "footer date"),
]

for old, new, tag in EDITS:
    d, _ = sub(d, b(old), b(new), tag)
    print(f"  ✓ {tag}")

# ---- 10. 新增 §八（插在末尾"--- + 文档维护者"块之前）----
SEC8 = b([
    "## 八、文档与代码的差距（2026-10-05 逐条核对）",
    "",
    "这一节是**如实记**，不是待办承诺。三类：",
    "",
    "### 8.1 后端与封装都在、但**没有任何界面入口**（能力是死的）",
    "",
    "| 能力 | 后端 | 前端封装 | 界面入口 |",
    "|---|---|---|---|",
    "| AI 分析套件（18 个端点） | ✅ | ✅ 15 条 | ❌ **只有 `/ai/chat`**（2 处调用）；小程序 0 个 |",
    "| warning-service `/warnings/**`（8 个端点） | ✅ | —— | ❌ **零前端调用**；预警只能靠 02:00 定时任务产生 |",
    "| 用户启用/禁用 | ✅ `POST /admin/users/{id}/toggle-status` | ✅ | ❌ `disableUser`/`enableUser` 零调用 |",
    "| 导出挂科学生 | ✅ `GET /admin/export/insufficient-students` | ✅ | ❌ `exportInsufficientStudents` 零调用 |",
    "",
    "PC + 小程序两侧**零调用点的封装方法合计 61 个**，分布：",
    "`adminAPI` 3 / `aiAPI` 14 / `authAPI` 2 / `collegeAdminAPI` 1 / `counselorAPI` 13 / `studentAPI` 19 / `teacherAPI` 9。",
    "",
    "> ⚠️ 判据边界：**\"零调用\"是扫出来的事实，不等于\"每一个都是缺口\"** ——",
    "> 其中一部分是**同类功能的第二个入口**（例如 `getCourseDetail`/`getStudents` 的活由别的封装承担）。",
    "> 上表四行是**逐条核过确属缺口**的；其余只保证\"没被调用\"。",
    "",
    "### 8.2 本文档此前写错的（已在上文就地改正）",
    "",
    "| 此前写的 | 实际 |",
    "|---|---|",
    "| `PUT /admin/users/{id}/status` | `POST /admin/users/{id}/toggle-status` |",
    "| `POST /admin/data/import`、`GET /admin/data/export/{type}` | **全仓不存在**；见 §3.5.7 真实路径 |",
    "| §3.6.3 四条 `/warnings/{id}/process`、`/release`、`/statistics/{counselorId}`、`/warnings/{studentId}` | **全仓不存在**；见 §3.6.3 真实列表 |",
    "| 表名 `academic_warnings` | `academic_warning`（**单数**；实体 `@TableName`、`init_schema.sql`、活库三处一致） |",
    "| §五\"密码 MD5 加密\" | **BCrypt**（与本文档 §3.1 自相矛盾过） |",
    "",
    "### 8.3 反过来：**凡是被界面调用的功能，全部实现**",
    "",
    "把 PC + 小程序的 **510 处调用**（含 8 处 `.vue` 直连后端）与后端 **393 条映射**逐条对照过：",
    "**断链 0、HTTP 动词不符 0**。也就是说 —— **页面上点得到的，后端都在**；",
    "上表那些缺口都是\"反方向\"的：**后端在、页面没有**。",
    "",
    "### 8.4 核对时最该记住的一条标定陷阱",
    "",
    "`/students/**`、`/teachers/**`、`/counselors/**` 的路径参数**默认是 `users.id`**，",
    "而 `student_profile.id` 是**另一套 id**（成绩表里的 `student_id` 用的是这一套）。",
    "例外：`teacher_course.teacher_id`，以及 `/subscription/**`、`/evaluations/**`。",
    "把两套 id 混用，接口会 200 但查不到人（或查到别人）。",
    "",
])

MARK = b([ "---", "", "> **文档维护者：** 系统自动生成  " ])
i = d.find(MARK)
assert i > 0, "找不到末尾的 --- + 文档维护者 块"
assert "## 八、".encode('utf-8') not in d, "§八 已存在"
d = d[:i] + SEC8 + d[i:]

open(P, 'wb').write(d)
crlf = d.count(b'\r\n'); bare = d.count(b'\n') - crlf
print(f"\n完成: {len(d)} 字节 | CRLF {crlf} | 裸 LF {bare}（必须 0）")
