# 学业预警系统

一个综合性的学业预警管理平台，帮助教育机构实时监控学生学业进展，及时预警风险，提供精准的学业指导和帮扶。

## 📋 项目概述

学业预警系统是一个基于 B/S 架构的综合管理系统，为学生、教师、辅导员和管理员提供不同的功能模块。系统能够：

- **实时监控**学生成绩变化和学业进展
- **智能预警**识别学业困难学生
- **个性化帮扶**制定针对性的学业指导计划
- **数据分析**提供详细的学业统计和对标分析
- **成绩申诉**提供透明的成绩申诉和复议机制

## 🏗️ 系统架构

### 前端技术栈
- **框架**: Vue 3
- **构建工具**: Vite
- **UI 库**: Element Plus
- **HTTP 客户端**: Axios
- **路由**: Vue Router
- **状态管理**: Pinia
- **图表**: ECharts

### 后端技术栈
- **框架**: Spring Boot 3.3.0
- **微服务框架**: Spring Cloud
- **服务注册与发现**: Nacos
- **服务调用**: OpenFeign
- **数据库**: MySQL 8.0
- **ORM**: MyBatis Plus
- **安全**: Spring Security
- **缓存**: Redis
- **消息队列**: 支持异步处理

## 👥 用户角色

### 1. 学生端
- **数据概览**: 查看个人学业总体情况和预警等级
- **成绩查询**: 按学期查看课程成绩和 GPA
- **预警信息**: 了解学业预警原因和等级
- **帮扶信息**: 查看学业帮扶计划和进度
- **成绩申诉**: 提交和跟踪成绩申诉
- **对标分析**: 与班级和专业同学对比分析
- **通知公告**: 接收系统消息和通知

### 2. 教师端
- **成绩管理**: 输入和修改课程成绩
- **学生分析**: 分析课程学生成绩分布
- **学分预测**: 预测学生学分完成情况
- **反馈管理**: 接收和回复学生反馈
- **审计日志**: 查看成绩操作历史
- **成绩预警**: 识别低分学生

### 3. 辅导员端
- **班级管理**: 管理所属班级学生信息
- **学生跟踪**: 监控班级学生学业进展
- **学分监控**: 追踪学分不足学生
- **帮扶计划**: 制定和执行帮扶方案
- **数据分析**: 班级学业数据统计分析
- **成绩跟踪**: 关注学生课程成绩

### 4. 管理员端
- **学院管理**: 维护学院信息
- **专业管理**: 管理专业设置
- **课程管理**: 维护课程库
- **用户管理**: 管理系统用户
- **权限配置**: 设置用户角色权限
- **预警规则**: 配置预警算法规则
- **数据导出**: 导出各类数据报表

## 🚀 快速启动

### 前置条件
- Node.js 16+ (前端开发)
- Java 17+ (后端运行)
- MySQL 8.0+
- Redis 6.0+

### 微服务启动顺序

#### 1. 启动 Nacos 服务注册中心

```bash
# 下载并启动 Nacos 服务器（如果尚未安装）
# 访问 https://github.com/alibaba/nacos/releases 下载最新版本
# 解压后运行：
cd nacos/bin
startup.cmd -m standalone
```

Nacos 服务将在 `http://localhost:8848` 启动

#### 2. 启动用户服务

```bash
cd academic-microservices/user-service
mvn spring-boot:run
```

用户服务将在 `http://localhost:8082/api` 启动并注册到 Eureka

#### 3. 启动学生服务

```bash
cd academic-microservices/student-service
mvn spring-boot:run
```

学生服务将在 `http://localhost:8083/api` 启动并注册到 Eureka

#### 4. 启动其他微服务

```bash
# 教师服务
cd academic-microservices/teacher-service && mvn spring-boot:run

# 辅导员服务
cd academic-microservices/counselor-service && mvn spring-boot:run

# 管理服务
cd academic-microservices/admin-service && mvn spring-boot:run
```

所有微服务统一通过 Gateway `http://localhost:8076` 访问

### 前端启动

```bash
cd academic-warning-frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build
```

前端应用将在 `http://localhost:5173` 启动

## 📊 学业预警等级

系统根据学生学业情况自动分配预警等级：

| 等级 | 标准 | 描述 |
|------|------|------|
| 🟢 正常 | GPA ≥ 2.0，无挂科 | 学业进展良好 |
| 🟡 轻度预警 | GPA 1.5-2.0 或少量挂科 | 需要关注 |
| 🟠 中度预警 | GPA 1.0-1.5 或多门课程不及格 | 需要重点帮扶 |
| 🔴 严重预警 | GPA < 1.0 或大量挂科 | 需要紧急干预 |

## 🔐 默认账号

### 学生账号
- **账号**: 2023020616
- **密码**: 123456
- **身份**: 学生

### 教师账号
- **账号**: 教001
- **密码**: 123456
- **身份**: 教师

### 辅导员账号
- **账号**: 辅001
- **密码**: 123456
- **身份**: 辅导员

### 管理员账号
- **账号**: admin
- **密码**: 123456
- **身份**: 管理员

## 📁 项目结构

```
ABS/
├── academic-microservices/       # 微服务架构
│   ├── eureka-server/            # 服务注册中心
│   ├── user-service/             # 用户服务
│   └── student-service/          # 学生服务
│
├── academic-microservices/       # 微服务后端
│   ├── gateway-service/          # API 网关 (8076)
│   ├── auth-service/             # 认证服务 (8072)
│   ├── admin-service/            # 管理服务 (8070)
│   ├── student-service/          # 学生服务 (8079)
│   ├── teacher-service/          # 教师服务 (8090)
│   ├── counselor-service/        # 辅导员服务 (8074)
│   ├── course-service/           # 课程服务 (8075)
│   ├── college-service/          # 学院服务 (8073)
│   ├── warning-service/          # 预警服务 (8093)
│   ├── user-service/             # 用户服务 (8092)
│   ├── message-service/          # 消息服务 (8077)
│   ├── ai-service/               # AI服务 (8071)
│   └── academic-common/          # 公共模块
│
├── academic-warning-frontend/    # 前端应用
│   ├── src/
│   │   ├── views/                # 各功能页面
│   │   ├── components/           # 可复用组件
│   │   ├── router/               # 路由配置
│   │   ├── store/                # 状态管理
│   │   ├── api/                  # API 接口调用
│   │   ├── assets/               # 静态资源
│   │   └── utils/                # 工具函数
│   ├── package.json
│   ├── vite.config.js
│   └── index.html
│
├── academic-warning-xcx/         # 小程序前端
│
├── API_DOCUMENTATION.md          # API 文档
├── SYSTEM_DOCUMENTATION.md       # 系统文档
└── README.md                      # 项目说明文档
```

## 🔧 配置说明

### 数据库配置

编辑 `学业预警系统后端/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/academic_warning?serverTimezone=Asia/Shanghai
    username: root
    password: 你的密码
```

### Redis 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
```

## 🔄 API 接口

### 认证接口
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register/student` - 学生注册

### 学生接口
- `GET /api/students/{studentId}` - 获取学生信息
- `GET /api/students/dashboard/{userId}` - 获取工作台数据
- `GET /api/students/scores/{userId}` - 获取成绩
- `GET /api/students/warnings/{userId}` - 获取预警信息
- `GET /api/students/benchmark/{studentId}/latest` - 获取对标数据

### 教师接口
- `GET /api/teachers/dashboard/{userId}` - 获取教师工作台
- `GET /api/teachers/courses` - 获取教师课程
- `POST /api/teachers/scores` - 录入成绩

### 辅导员接口
- `GET /api/counselors/dashboard/{userId}` - 获取辅导员工作台
- `GET /api/counselors/students` - 获取班级学生
- `GET /api/counselors/warnings` - 获取预警学生

### 管理员接口
- `GET /api/admin/dashboard` - 获取管理员工作台
- `GET /api/admin/users` - 用户管理
- `GET /api/admin/rules` - 规则管理

详细 API 文档请参考源代码中的注释。

## 📈 主要功能特性

### ✨ 智能预警
- 基于多维度指标的自动预警
- 支持自定义预警规则
- 实时预警推送通知

### 📊 数据分析
- 学生学业进展追踪
- 班级和专业横向对比
- 成绩分布和趋势分析
- 多维度统计报表

### 🎓 学业帮扶
- 制定个性化帮扶计划
- 跟踪帮扶执行进度
- 记录帮扶效果评价

### 📋 成绩管理
- 成绩录入和修改
- 成绩申诉和复议
- 操作日志审计

### 📬 消息通知
- 多渠道消息推送
- 通知偏好设置
- 消息查询和管理

## 🐛 常见问题

### Q: 如何重置密码？
A: 联系管理员重置，或在登录页面选择"忘记密码"。

### Q: 前端无法连接后端？
A: 检查后端是否正在 `http://localhost:8081` 运行，查看浏览器控制台错误信息。

### Q: 数据库连接失败？
A: 检查 MySQL 服务是否运行，确认用户名和密码正确。

### Q: Redis 连接问题？
A: 确保 Redis 服务在 `localhost:6379` 运行，或修改配置文件。

## 📝 开发日志

- 2025-12-25: 项目初始化和核心功能开发完成
- 前端导航栏美化和菜单功能实现
- 后端 API 接口开发和数据库设计
- 系统测试和优化

## 📄 许可证

本项目为教育类项目，仅供学习使用。

## 👨‍💻 开发者

- 前端开发: Vue 3 + Element Plus
- 后端开发: Spring Boot + MyBatis Plus
- 数据库: MySQL + Redis

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub Issue: https://github.com/li-ming111/academic-warning-system/issues
- Email: 联系项目维护者

---

**最后更新**: 2026-03-31

希望这个系统能够帮助学生更好地管理学业，帮助教育工作者更有效地进行教学管理！
