### §6.57 第五十八轮 W24 —— 一个「触发了预警」的按钮, 一行都没落库(说法与事实不符)

**这一轮查的是"宣称做了一件事、方法体里却没有任何写操作"的端点(假成功 / 名字骗人的接口)。**

- 扫法: 全仓 **controller 方法**逐个看, **两条都命中才报** ——
  A. 方法体里**没有任何写调用**(insert/update/save/remove/delete/add/create/modify/… 出现在方法名里);
  B. 方法体里有**宣称已完成的字面量**(成功 / 已生成 / 已发送 / 已保存 / 已同步 / 已删除 / 已导入 / …)。
  收敛到 **5 处**, 逐条读过代码后 **4 处是假阳性**(字符串拼装器 `getReportHeaders`/`joinHandleResult`,
  以及确实在写的 `messageService.sendMessage`、`userService.changePassword`、`messagingTemplate.convertAndSendToUser`),
  **1 处是真的**。

**真病**: `POST /teachers/scores/warnings`(教师端「成绩预警」按钮那条路)

```java
result.put("warningsTriggered", warningCount);
result.put("message", "已为" + warningCount + "名学生生成预警");   // ← 这句话是假的
```

方法体里**一行都没落库** —— 只把本课程成绩流了一遍、数了数低于及格线的人数就返回。
**谁调它, 谁就得到一句"预警已生成"的假确认**(HTTP 200 + `success:true`)。
响应里那个 `warningsTriggered` 键名也在重复这个说法, 方法名 `triggerWarnings` 同样。

**实锤(活库只读回读, 不是推理)**: 刚打完这个端点, 库里
`academic_warning` 共 **4** 行, **今天创建 0 行**, `MAX(created_at) = 2026-09-21 14:09:50`
—— 在 2026-10-05 打这个端点说"已为 3 名学生生成预警", 而库里的预警记录**一条都不是它建的**。

**改法: 把说法改成事实**(而不是"让承诺成真")

真正的预警生成**不在这个服务里**: `warning-service` 的 `POST /warnings/scan`
(`WarningScheduledTask.triggerNow()`)与每日 02:00 的定时任务才是出口, 且带
"先解除后生成 + 按「学生 + 挂科门数」判重(不看 status)"的完整规则。
**刻意不去转调它**, 两个理由:

1. 本端点是**按课程**的, 那个扫描是**全库**的 —— 从一门课的按钮触发全库重扫, 语义上就是错的;
2. 扫描会**连带发通知**(解除 + 生成各一轮), 一个"看看这门课有几个人挂科"的动作不该有这种副作用。

于是响应里:

- 删掉 `warningsTriggered`(它断言"预警被触发了"), 换成不冒充动作的 `studentsBelowPassLine`;
- 补一个 `passLine`(把 60 一并回给前端, 这个数字才可解释);
- `message` 明说 **"本课程有 N 名学生成绩低于 60 分；本接口只做统计, 不生成预警记录"**;
- 方法名 `triggerWarnings` → `inspectScoresForWarnings`。

该键可安全移除 —— PC 与小程序**两边的封装都不是调用方**(全仓零调用点, 见下)。

**判据(每条都能被打红)**

- **+5 单测** `TeacherControllerWarningPreviewTest`(542 → **547**, 52 个 surefire 文件, 全 0 失败):
  - ★ 响应里**不再有** `warningsTriggered` 键(直接 `assertFalse(data.containsKey(...))`);
  - `message` 含"只做统计""不生成预警记录", 且**不以"已为"开头**;
  - **未录入的成绩(null)不算 0 分**, 60 分是及格(严格小于)—— 三人中只 59 分那个算;
  - 别人的课程 → `code:403` **且 `verify(scoreRecordService, never()).getScoresByCourseId(...)`**
    (闸在读文件**之前**, 只看返回码会被"先读了再报错"骗过);
  - 管理员(`X-Role: 4`)旁路归属闸, 且**不查课程表**。
- **三轮打靶, 五条断言各自单独打红**(每轮只该红那一两条):
  ① 把键名改回 `warningsTriggered` + 旧文案 ⇒ 恰好键那条**红**、文案那条**红**(计数那条因键被换而 NPE, 一并红);
  ② 把 null 当 0 分 ⇒ 恰好计数那条**红**(`expected: <1> but was: <2>`), 其余 4 条绿;
  ③ 把闸挪到读成绩**之后** ⇒ 恰好"连成绩都不去查"那条**红**; 再删掉 `isAdmin` 旁路 ⇒ 恰好管理员那条**红**。
  ⇒ 五条断言**没有一条是永绿的**。
- **端到端 A/B(只读, 不写库)**: 我自己起的备用实例 **8188**(W24 新构建) vs 现役 **8090**(W24 **之前**的代码),
  同一个 `POST /teachers/scores/warnings?course_id=1` + 教师 29 身份:

  | | 响应 data |
  |---|---|
  | 旧 8090 | `{"warningsTriggered":3,"totalStudents":24,"message":"已为3名学生生成预警"}` |
  | 新 8188 | `{"passLine":60,"totalStudents":24,"studentsBelowPassLine":3,"message":"本课程有 3 名学生成绩低于 60 分；本接口只做统计, 不生成预警记录"}` |

  **同一个数字 3、同一个 `totalStudents` 24** —— 说明**计数口径一个字没动, 改的只是那句说法**。
  另三条身份同轮实测: 教师 18(别人的课) → `code:403`; 管理员(id=1/role=4) → `code:200`;
  匿名(无身份头) → **HTTP 403 空体**(网关鉴权过滤器, 与控制器业务拒绝是**两种形状**)。
- invariants **42/42**。

**顺带查到、本轮不改**: 真正的重扫端点 `POST /warnings/scan` **在任何前端里都没有入口**
(PC 与小程序全仓 grep `warnings/scan` **零命中**; 端点的 javadoc 自己写着"也方便演示")。
也就是说: **演示环境里"重新扫描预警"这件事, 只能靠每天 02:00 的定时任务或手打 curl**。
这是"能力在、入口没有", 与 W23 同类但方向相反 —— 本轮**如实报告, 不动它**
(它一调就是全库解除+重建+发通知, 该由产品决定入口放哪、要不要加二次确认)。

**收尾**

- 备用实例 8188 **只停我自己起的**(Windows PID 1014104), 日志 `/d/tmp-w24-spare.log` 已删,
  用户现役 **8070/8090 未动**(`netstat` 复核 PID 173440/114376 仍在)。
- **未重新打包、未提交**: W13–W24 仍未提交(最近提交停在 W12 `620cb3bf`)。
  ⇒ 答辩环境跑的**仍是修前代码**: 那个按钮**还会说**"已为 N 名学生生成预警"。
- 边界: 端点**路径**仍叫 `/teachers/scores/warnings`、前端封装仍挂在 `teacherAPI` 下
  —— 路径与命名空间是**兼容性面**, 只改"说法"不改"契约", 免得把零调用点也变成破坏性变更。
