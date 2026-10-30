# 智能学业预警服务系统

基于 Spring Boot 3 + Spring Cloud Alibaba 的微服务版学业预警平台，把「成绩管理 → 风险识别 → 预警推送 → 帮扶干预 → 效果跟踪」串成一条闭环链路，覆盖学生、教师、辅导员、学院管理员、校级管理员五类角色，同时提供 PC 管理端与微信小程序 / H5 两种前端。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17、Spring Boot 3、Spring Cloud 2023、Spring Cloud Alibaba 2023 |
| 服务治理 | Nacos（注册与发现）、Spring Cloud Gateway（统一入口）、OpenFeign |
| 数据 | MySQL 8（49 张业务表）、MyBatis-Plus、Redis |
| 鉴权 | JWT，网关统一校验与角色路由 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + ECharts |
| 小程序 | uni-app（Vue 3），一套代码输出微信小程序与 H5 |
| 智能分析 | 独立 ai-service，通过 OpenAI 兼容接口调用大模型（默认 DeepSeek） |

## 角色与功能

- **学生**：学业总览、成绩查询与 GPA、预警详情与申诉、帮扶计划进度、班级/专业对标、通知中心
- **教师**：授课课程与成绩录入、成绩分析、学分预测、学生反馈处理、成绩审计日志、课表
- **辅导员**：班级学生跟踪、预警与帮扶计划、学分监控、学业分析、与任课教师私信
- **学院管理员**：本学院学生/教师/辅导员/班级管理、成绩明细查询、预警处理、数据导出
- **校级管理员**：学院与专业、课程与排课、用户与角色权限、预警规则、报表模板、数据导出

## 服务与端口

| 服务 | 端口 | 服务 | 端口 |
|---|---|---|---|
| gateway-service | 8076 | warning-service | 8093 |
| admin-service | 8070 | teacher-service | 8090 |
| ai-service | 8071 | student-service | 8079 |
| auth-service | 8072 | user-service | 8082 |
| college-service | 8073 | message-service | 8077 |
| counselor-service | 8074 | course-service | 8075 |

PC 端开发服务器 5173，小程序 H5 端 5180，全部请求经网关 `8076` 转发。

## 快速开始

### 1. 基础环境
JDK 17、Maven 3.8+、MySQL 8.0、Redis 6+、Node.js 18+、Nacos 2.x。

### 2. 导入数据库

```bash
mysql -h127.0.0.1 -uroot -p < academic_warning_system.sql
```

脚本会创建 `academic_warning_system` 库（utf8mb4）并导入表结构与演示数据。

### 3. 启动 Nacos 与 Redis

```bash
D:\JAVA\nacos\bin\startup.cmd -m standalone
redis-server
```

Nacos 控制台：`http://localhost:8848/nacos`；Redis 默认 6379、无密码。

### 4. 启动后端

用 IntelliJ IDEA 打开 `academic-microservices` 目录（Maven 工程），依次启动 12 个服务的启动类，`GatewayServiceApplication` 最后启动：

```
com.academic.UserServiceApplication          8082
com.academic.AuthServiceApplication          8072
com.academic.CollegeServiceApplication       8073
com.academic.CourseServiceApplication        8075
com.academic.MessageServiceApplication       8077
com.academic.WarningServiceApplication       8093
com.academic.StudentServiceApplication       8079
com.academic.TeacherServiceApplication       8090
com.academic.CounselorServiceApplication     8074
com.academic.AdminServiceApplication         8070
com.academic.AiServiceApplication            8071
com.academic.GatewayServiceApplication       8076
```

Nacos 与 Redis 必须先就绪，否则网关与 user-service 启动失败；服务出现在 Nacos「服务列表」即注册成功。

### 5. 启动 PC 端

```bash
cd academic-warning-frontend
npm install
npm run dev        # http://localhost:5173
```

### 6. 启动小程序 / H5

```bash
cd academic-warning-xcx
npm install
npm run dev:h5           # http://localhost:5180
npm run dev:mp-weixin    # 产物在 dist/dev/mp-weixin，用微信开发者工具导入
```

真机预览时手机上的 `localhost` 指向手机自身，需要把 `src/config/env.js` 里的 `MP_HOST` 改成电脑的局域网 IP；`npm run host:auto` 会按当前网段自动写入。

## 默认账号

演示账号密码统一为 `123456`。

| 角色 | 账号 | 姓名 |
|---|---|---|
| 学生 | `2023020616` | 司明 |
| 教师 | `teacher001` | 刘教授 |
| 辅导员 | `counselor101` | 林晓 |
| 学院管理员 | `collegeadmin1` | 张建国 |
| 校级管理员 | `admin` | 系统管理员 |

## 预警机制

- 规则存于 `warning_rules` 表，按挂科门数分级：1–2 门为轻度、3–5 门为中度、5 门以上为重度。
- 定时扫描每天 02:00 执行一次，另有 30 分钟固定间隔扫描；也可在管理端手动「重新扫描预警」，重复执行不会产生重复记录。
- 挂科门数回落到阈值以下时自动解除预警；教师/管理员也可手动解除，学生可对预警提出申诉，走审核流程。
- 成绩以 60 分为及格线，「待录入」的口径是 `final_score IS NULL`。

## 目录结构

```
academic-microservices/      # 后端 Maven 聚合工程
├── academic-common/         # 公共实体、Mapper、工具
├── gateway-service/         # 网关（8076）
└── *-service/               # 12 个业务/基础服务
academic-warning-frontend/   # PC 管理端（Vue 3 + Vite）
academic-warning-xcx/        # 小程序 / H5（uni-app）
```

## 配置说明

数据库密码与 AI 密钥通过环境变量注入，源码与仓库内不含明文：

| 环境变量 | 用途 | 默认值 |
|---|---|---|
| `DB_PASSWORD` | MySQL 密码 | `root` |
| `AI_API_KEY` | 大模型 API Key | 空（未设置时 AI 功能返回「未配置」提示） |
| `AI_MODEL` | 模型名 | `deepseek-chat` |
| `AI_BASE_URL` | 接口地址 | `https://api.deepseek.com/v1` |

Windows 设置示例：`setx AI_API_KEY "你的 Key"`（新开终端后生效）。

各服务配置在 `academic-microservices/<service>/src/main/resources/application.yml`，数据库地址、Nacos 地址等可在此调整。

## 许可证

本项目基于 [MIT License](LICENSE) 开源，仅供学习与交流使用。
