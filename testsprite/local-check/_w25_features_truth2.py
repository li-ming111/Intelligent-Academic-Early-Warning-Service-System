# W25 第二支：改 FEATURES.md 的两处 ——
#   ① §8.1 那个 "61 个零调用封装" 是**错的**（PC-only，且扫描器还中了剥注释的坑）
#   ② 新增 §8.5：本轮顺带查到的一处「假成功」= POST /students/register
#
# 为什么仍然字节级：FEATURES.md 是 CRLF，文本模式改写会把 CRLF 静默抹成 LF（W21 栽过）。
# 每处替换断言命中恰好一次；收尾 b.count(b'\r\n') / b.count(b'\n') 复核。
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

P = r"D:/ABS/FEATURES.md"
d = open(P, 'rb').read()

def b(lines):
    return '\r\n'.join(lines).encode('utf-8')

def sub(cur, old, new, tag):
    n = cur.count(old)
    assert n == 1, f"[{tag}] 命中 {n} 次, 期望 1"
    return cur.replace(old, new)

# ---------- ① §8.1 表格加一行：注册的第二套实现 ----------
OLD_ROW = b(["| 导出挂科学生 | ✅ `GET /admin/export/insufficient-students` | ✅ | ❌ `exportInsufficientStudents` 零调用 |"])
NEW_ROW = b(["| 导出挂科学生 | ✅ `GET /admin/export/insufficient-students` | ✅ | ❌ `exportInsufficientStudents` 零调用 |",
             "| 注册（**第二套实现**） | ✅ `POST /students\\|teachers\\|counselors/register` | ✅ 3 条 | ❌ 零调用；UI 走 `user-service` 的 `/auth/register/{student,teacher,counselor}`。⚠️ 其中 `/students/register` 是**假成功**，见 §8.5 |"])
d = sub(d, OLD_ROW, NEW_ROW, "§8.1 add register row")

# ---------- ② §8.1 把 "61" 那段换成真实清点 ----------
OLD_61 = b(["PC + 小程序两侧**零调用点的封装方法合计 61 个**，分布：",
            "`adminAPI` 3 / `aiAPI` 14 / `authAPI` 2 / `collegeAdminAPI` 1 / `counselorAPI` 13 / `studentAPI` 19 / `teacherAPI` 9。",
            "",
            "> ⚠️ 判据边界：**\"零调用\"是扫出来的事实，不等于\"每一个都是缺口\"** ——",
            "> 其中一部分是**同类功能的第二个入口**（例如 `getCourseDetail`/`getStudents` 的活由别的封装承担）。",
            "> 上表四行是**逐条核过确属缺口**的；其余只保证\"没被调用\"。"])
NEW_61 = b(["两个前端的封装方法合计 **513 条**（PC `api/index.js` **297** / 小程序 `services/api.js` **216**），",
            "其中**零调用点 82 条**（PC 60 / 小程序 22）。再按「同一端点还有没有别的入口」分：",
            "",
            "- **真缺口 47 条**（PC 40 / 小程序 7）—— 端点既没有被调用的封装指向、也没有页面用字面路径直连；",
            "- **第二个入口 35 条** —— 同端点另有被调用的封装（或页面直连了字面路径），功能不缺，只是这条封装没人用。",
            "",
            "按命名空间（零调用 / 其中真缺口）：PC `aiAPI` 14/14、`counselorAPI` 13/8、`studentAPI` 18/11、",
            "`teacherAPI` 9/6、`adminAPI` 3/1、`authAPI` 2/0、`collegeAdminAPI` 1/0；",
            "小程序 `admin` 5/1、`teacher` 5/3、`counselor` 5/3、`collegeAdmin` 3/0、`student` 3/0、顶层 `apiClient` 1/0。",
            "",
            "> ⚠️ 判据边界：**\"零调用\"是扫出来的事实；47 条说的是\"这条端点没有任何入口\"，不等于 47 个功能缺失。**",
            "> 上表各行是**逐条核过确属缺口**的。最容易误判成缺口的另一类：**同一功能有两套实现、UI 只走其中一套**",
            "> —— `/students|teachers|counselors/register` 就是（UI 走 `/auth/register/*`），所以它只能算\"端点没入口\"。",
            "",
            "> 📌 更正记录：本节 2026-10-05 首次写下时写的是「两侧合计 **61** 个」及其分布。那个数是**错的**，两个原因：",
            "> 扫描器只会认 `const xxxAPI = {` 一种写法，把小程序（`class ApiClient` + 实例字段）**整块漏掉** ⇒ 61 其实是 PC 独有；",
            "> 且它的剥注释函数**先找 `/*` 再找 `//`**，于是 `// … /students/settings/** …` 这类行注释里的 `/**` 被当成块注释开头，",
            "> 从那里起把**后半个文件全吞成空行**。修好这两处后 PC 从 21 个方法还原成 297 个。**上一版数字整体作废。**"])
d = sub(d, OLD_61, NEW_61, "§8.1 real census")

# ---------- ③ 新增 §8.5 ----------
SEC85 = b([
    "### 8.5 顺带查到的一处「假成功」（**尚未修**，2026-10-05）",
    "",
    "`POST /students/register`（student-service）的 service 方法体是：",
    "",
    "```java",
    "public Object registerStudent(Object request) {",
    "    log.info(\"学生注册\");",
    "    return \"注册成功\";          // 一行都没写库",
    "}",
    "```",
    "",
    "**实测（2026-10-05，活库）**：用工程自己配置的密钥签一个学生 token（`userId=27, role=1`），",
    "经网关 `POST /api/students/register` → **`HTTP 200` + `success:true` + `data:\"注册成功\"`**；",
    "而 `users` **35** / `student_profile` **24** / `probe%` 账号 **0** —— **打前打后完全一致**。",
    "也就是说它**回了成功、却没建任何账号**。这与 §6.57 修掉的 `/teachers/scores/warnings` 同一族",
    "（说法与事实不符），只是这一条**本轮只报告、未修**。",
    "",
    "**够得着吗**（这决定严重性）：网关白名单里**没有**它（只有 `/api/auth/register/{student,teacher,counselor}`），",
    "匿名走网关实测 → **`HTTP 401` `{\"code\":401,\"message\":\"缺少认证信息\"}`**（网关的第四种拒绝形状，此前只记过 403）；",
    "网关也不认裸的 `X-User-Id`/`X-Role` 头。但**任何已登录用户**带着真 token 打过去，就是上面那个 200 + 假成功。",
    "",
    "**同族两条**：`/teachers/register`、`/counselors/register` 是**真写库**的，但只往",
    "`teacher_profile` / `counselor_profiles` 写**一行**，**不建 `users` 账号**（半截写）",
    "—— 写出来的是**永远登不进来的孤儿档案**。",
    "",
])

MARK = b(["---", "", "> **文档维护者：** 系统自动生成  "])
i = d.find(MARK)
assert i > 0, "找不到末尾的 --- + 文档维护者 块"
assert "### 8.5".encode('utf-8') not in d, "§8.5 已存在"
d = d[:i] + SEC85 + d[i:]

open(P, 'wb').write(d)
crlf = d.count(b'\r\n'); bare = d.count(b'\n') - crlf
print(f"完成: {len(d)} 字节 | CRLF {crlf} | 裸 LF {bare}（必须 0）")
for k in ["61 个", "513 条", "### 8.5", "students/register"]:
    print(f"  '{k}' 出现 {d.count(k.encode('utf-8'))} 次")
