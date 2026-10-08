### §6.58 第五十九轮 W25 —— 文档核对（把 FEATURES.md 改成事实）+ 一处新查到的「假成功」

**这一轮做两件事：先把 `FEATURES.md` 从"写出来的愿望"改成"扫出来的事实"，再继续查"零调用封装"。**

**一、`FEATURES.md` 改成事实（只改文档，不动一行代码）**

十处就地改正 + 新增 §八。改的都是**文档与代码不符**的地方，逐条都核过代码：

- §3.5.2 `PUT /admin/users/{id}/status` → 真实的 `POST /admin/users/{id}/toggle-status`（**切换**语义）；
- §3.5.7 `POST /admin/data/import`、`GET /admin/data/export/{type}` —— **这两个路径全仓不存在**，
  换成真实的 `/admin/import/students|scores`、`/admin/import/template/{type}`、`/admin/export/{...}`；
- §3.6.3 四条 `/warnings/{id}/process`、`/release`、`/statistics/{counselorId}`、`/warnings/{studentId}` ——
  **全仓不存在**，换成 `warning-service` 真实的 8 条；
- 表名 `academic_warnings` → **`academic_warning`（单数）**；§五「密码 MD5」→ **BCrypt**（该文档 §3.1 自己写着 BCrypt，前后矛盾过）。

**二、继续查「零调用封装」，先把扫描器修对**

W24 那份 `_w24_dead_api.py` 报的「全仓 61 个零调用」是**错的**，两个原因叠加：

1. 它只认 `const xxxAPI = {` 一种写法 —— 小程序 `services/api.js` 是 **`class ApiClient` + 实例字段**
   （末尾 `export const studentAPI = apiClient.student` 再导出），**整块一个命名空间都没匹配上**
   ⇒ 那个 61 其实是 **PC 独有**；
2. 新扫描器的剥注释函数**先找 `/*` 再找 `//`**，于是诸如下面这行**行注释里的 `/**`** 被当成块注释开头：

   ```
   // 下面 4 个是 `/students/settings/**`，**当前没有任何页面调用**（学生设置页走的是…
   ```

   而它**没有配对的 `*/`** ⇒ 从这一行起 `in_block` 一直为真，**后半个文件全被吞成空行**。
   PC 源文件 7 个顶层 `\n}` 只剩 1 个、297 个封装方法只剩 21 个（小程序同理：1162 行里后半被吞）。
   **注意这不是"匹配不到"，是"把源码删了"** —— 报出来的数还一本正经。

修法是**字符级单遍扫描**，按 JS 真实词法排序：字符串 > 行注释 > 块注释（块注释里的换行保留，行号不失配）。

**三、修好之后的两前端清点**

| | 数值 |
|---|---|
| 封装方法总数 | **513**（PC `api/index.js` 297 / 小程序 `services/api.js` 216） |
| 零调用点 | **82**（PC 60 / 小程序 22） |
| 其中**真缺口** | **47**（PC 40 / 小程序 7）—— 端点没有任何入口 |
| 其中**第二个入口** | **35** —— 同端点另有被调用的封装，功能不缺 |

判据用了**两只眼睛**（缺一只就会误报）：① 有没有**别的被调用的封装方法**指向同一端点；
② 页面有没有**直接以字面路径**打这个端点（绕开封装）—— W20 就查出全仓有 8 处 `.vue` 直连。
第②只眼睛实测逮到 **3 条假"真缺口"**：`/college-admin/assistance/{}`（`assistance.vue` 直连）、
`/auth/logout`（PC `App.vue:232` 直连）。补上后真缺口 50 → **47**。

打靶（扫描器报的每个数都得先证明它扫得到）：4 条封装调用点正/负对照 + 2 条字面路径正/负对照
（正对照 `/auth/logout` 必须打中，负对照编造路径必须打不中）。另手工抽查 6 条：
`studentAPI.register`/`getUserSettings`/`teacherAPI.getStudents`/`counselorAPI.getStudentDetail` 全仓 0 命中 ✓；
`getUnreadNotificationsAdvanced` 在 `NotificationCenter.vue:219` 真被调 ✓（它才是活的那个）；
小程序 `admin/users.vue` 用的是 `getUsers`，`getStudents` 确实没人用 ✓。

**四、顺带查到的一处「假成功」（本轮只报告，未修）**

`POST /students/register`（student-service）的 service 体是：

```java
public Object registerStudent(Object request) {
    log.info("学生注册");
    return "注册成功";          // 一行都没写库
}
```

**实测**：用工程自己配置的密钥签一个学生 token（`userId=27, role=1`），
经网关 `POST /api/students/register` → **`HTTP 200` + `success:true` + `data:"注册成功"`**；
而 `users` **35** / `student_profile` **24** / `probe%` 账号 **0** —— **打前打后完全一致**。
它**回了成功、却没建任何账号**，与 §6.57 修掉的 `/teachers/scores/warnings` 同一族（说法与事实不符）。

- **够得着吗**（决定严重性）：网关白名单里**没有**它（只有 `/api/auth/register/{student,teacher,counselor}`），
  匿名走网关 → **`HTTP 401` `{"code":401,"message":"缺少认证信息"}`**；网关也不认裸的
  `X-User-Id`/`X-Role` 头。但**任何已登录用户**带真 token 打过去，就是上面那个 200 + 假成功。
- ⚠️ **这是网关拒绝的第四种形状（401 + body code）** —— 此前只记过 403 那三种。
  只按 HTTP 状态码判"被拒"，会把 401 和 403 混成一类。
- **同族两条**：`/teachers/register`、`/counselors/register` 是**真写库**的，但只往
  `teacher_profile` / `counselor_profiles` 写**一行**、**不建 `users` 账号**（半截写）
  —— 写出来的是**永远登不进来的孤儿档案**。

**五、收尾**

- **未修、未提交**：W13–W25 仍未提交（最近提交停在 W12 `620cb3bf`）。上面这条假成功**还在活库里**。
- 本轮**只新增/修改文档与探针脚本**，产品代码一行未动；`FEATURES.md` 字节级改写后
  **CRLF 668 / 裸 LF 0**（文本模式改会把 CRLF 静默抹成 LF，此前栽过）。
- 探针脚本：`testsprite/local-check/_w25_dead2.py`（修好的两前端清点）、
  `_w25_features_truth.py` / `_w25_features_truth2.py`（字节级改文档，每处断言命中恰好一次）。
