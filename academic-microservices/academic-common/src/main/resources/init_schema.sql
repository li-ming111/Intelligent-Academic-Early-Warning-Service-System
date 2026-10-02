-- ========================================
-- 智能学业预警服务系统 - 完整建表脚本
-- 由线上库结构导出（mysqldump --no-data）后生成，是全新库的唯一权威结构。
-- 与线上库不一致时以线上库为准重新生成，不要手工改本文件。
-- 索引内联在 CREATE TABLE 中：MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，
-- 写成独立语句只会静默失败，让全新库上的表没有索引。
-- 使用 CREATE TABLE IF NOT EXISTS，多服务并行启动安全幂等。
--
-- 前置条件：本脚本只建表，不建库（不含 CREATE DATABASE / USE），各服务的
-- JDBC URL 也没有 createDatabaseIfNotExist=true。因此在一台没有该库的 MySQL 上，
-- 服务连数据库时就已失败，DataInitializer 根本没机会执行本脚本。
-- 全新安装必须手工先建库：
--     CREATE DATABASE academic_warning_system
--       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ========================================

-- ==================== 基础表（无外键依赖） ====================

CREATE TABLE IF NOT EXISTS `colleges` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `code` varchar(50) DEFAULT NULL,
    `description` varchar(500) DEFAULT NULL,
    `status` int DEFAULT '1',
    -- 这里原来有 `student_count` / `teacher_count` 两列（2026-10-01 删，与活库对齐）。
    -- 见第十一轮：两者在 `College` 实体上都是 `@TableField(exist=false)`、全仓 getter
    -- 读取点 = 0，唯一维护者是注册路径的 `+1` ⇒ 必然漂移；与其补写路径不如删列。
    -- ⚠️ 本文件此前**一直还留着这两列**，而活库 2026-10-01 已经删了 —— 属于下面说的"结构漂移"。
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_colleges_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL,
    `password` varchar(255) NOT NULL,
    `name` varchar(100) DEFAULT NULL,
    `email` varchar(100) DEFAULT NULL,
    `phone` varchar(20) DEFAULT NULL,
    `role` tinyint(1) NOT NULL COMMENT '1-学生, 2-教师, 3-辅导员, 4-管理员',
    `status` tinyint(1) DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),
    KEY `idx_users_role` (`role`),
    KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `roles` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(50) NOT NULL,
    `code` varchar(50) NOT NULL,
    `description` varchar(255) DEFAULT NULL,
    `status` int DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_roles_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permissions` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `code` varchar(100) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `status` int DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permissions_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_permissions` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `role_id` bigint NOT NULL,
    `permission_id` bigint NOT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permissions_role_permission` (`role_id`,`permission_id`),
    KEY `idx_role_permissions_role_id` (`role_id`),
    KEY `idx_role_permissions_permission_id` (`permission_id`),
    CONSTRAINT `fk_role_permissions_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`),
    CONSTRAINT `fk_role_permissions_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `elective_modules` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `module_code` varchar(20) NOT NULL,
    `module_name` varchar(100) NOT NULL,
    `description` text,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_elective_modules_module_code` (`module_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '选修课模块表';

CREATE TABLE IF NOT EXISTS `warning_rules` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL COMMENT '规则名称',
    `description` text COMMENT '规则描述',
    `level` int DEFAULT '1' COMMENT '预警等级：1-低, 2-中, 3-高',
    `rule_condition` text COMMENT '触发条件表达式',
    `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `rule_code` varchar(50) DEFAULT NULL COMMENT '规则编码',
    `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
    `fail_definition` text COMMENT '挂科定义标准',
    `statistic_cycle` varchar(50) DEFAULT NULL COMMENT '统计周期',
    `trigger_type` varchar(50) DEFAULT NULL COMMENT '触发机制',
    `release_condition` text COMMENT '预警解除条件',
    `intervention_plan` text COMMENT '学业干预方案',
    `notification_flow` text COMMENT '通知流程',
    `responsible_person` varchar(100) DEFAULT NULL COMMENT '责任人',
    `tracking_mechanism` text COMMENT '跟踪反馈机制',
    `archive_spec` text COMMENT '归档规范',
    `data_caliber` text COMMENT '数据统计口径',
    `department_responsibility` text COMMENT '部门职责分工',
    `appeal_process` text COMMENT '申诉复议流程',
    `exception_handling` text COMMENT '例外情况处理办法',
    PRIMARY KEY (`id`),
    KEY `idx_warning_level` (`level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '预警规则表';

-- ==================== 这里原来有 `support_resource`（2026-10-01 删） ====================
--
-- 那张表是早期"支持资源"模块的。2026-09-30 第十一轮把整个 `/api/resources/**` 模块
-- 连根删掉之后，它的 `views` 写入方也一起没了 —— 到 2026-10-01 复核时已经是
-- **整张表零读零写**：全仓 `grep -rn "support_resource"` 在 Java / XML / 前端 / 小程序 /
-- 网关路由里 **0 命中**，只出现在本文件与历史备份 SQL 里；表里剩 4 行种子数据
-- （`views`/`downloads` = 150/45、200/60、80/30、120/38），没有任何代码消费。
--
-- 所以按"零调用方 = 删"的口径连表一起删，与第十一轮删 `/api/resources/**` 同一收尾。
-- ⚠️ 与 `teaching_resources` **不是同一张表**（那张在 course-service，见本文件下面的说明）——
--    两张都孤儿，但来路不同，别混为一谈。

CREATE TABLE IF NOT EXISTS `export_history` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `data_type` varchar(50) NOT NULL,
    `file_name` varchar(255) NOT NULL,
    `record_count` int DEFAULT '0',
    `exported_by` bigint DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_export_history_data_type` (`data_type`),
    KEY `idx_export_history_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_templates` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(200) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `file_path` varchar(500) DEFAULT NULL,
    `file_name` varchar(200) DEFAULT NULL,
    `file_size` bigint DEFAULT '0',
    `file_type` varchar(50) DEFAULT NULL,
    `status` tinyint DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 依赖 colleges 的表 ====================

CREATE TABLE IF NOT EXISTS `majors` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `college_id` bigint NOT NULL,
    `name` varchar(100) NOT NULL,
    `code` varchar(50) NOT NULL,
    `short_name` varchar(20) DEFAULT NULL COMMENT '专业简称',
    `description` varchar(500) DEFAULT NULL,
    `status` int DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_majors_code` (`code`),
    KEY `idx_majors_college_id` (`college_id`),
    CONSTRAINT `fk_majors_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `classes` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(50) NOT NULL,
    `college_id` bigint NOT NULL,
    `major_id` bigint DEFAULT NULL,
    `teacher_id` bigint DEFAULT NULL,
    `counselor_id` bigint DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_classes_college_id` (`college_id`),
    CONSTRAINT `fk_classes_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `teacher_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `name` varchar(50) DEFAULT NULL,
    `college_id` bigint NOT NULL,
    `title` varchar(50) DEFAULT NULL,
    `research_areas` varchar(200) DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_profile_user_id` (`user_id`),
    KEY `idx_teacher_profile_college_id` (`college_id`),
    CONSTRAINT `fk_teacher_profile_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`),
    CONSTRAINT `fk_teacher_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 依赖 users + colleges 的表 ====================

CREATE TABLE IF NOT EXISTS `admin_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `name` varchar(100) DEFAULT NULL,
    `phone` varchar(20) DEFAULT NULL,
    `email` varchar(100) DEFAULT NULL,
    `department` varchar(100) DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_profile_user_id` (`user_id`),
    CONSTRAINT `fk_admin_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `counselor_profiles` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `college_id` bigint DEFAULT NULL COMMENT '学院ID',
    `counselor_id` varchar(50) DEFAULT NULL COMMENT '辅导员编号',
    `name` varchar(100) DEFAULT NULL COMMENT '姓名',
    `gender` varchar(10) DEFAULT NULL COMMENT '性别',
    `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
    `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    `department` varchar(100) DEFAULT NULL COMMENT '部门',
    `title` varchar(50) DEFAULT NULL COMMENT '职称',
    `education` varchar(50) DEFAULT NULL COMMENT '学历',
    `introduction` text COMMENT '个人简介',
    `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `college_name` varchar(100) DEFAULT NULL COMMENT '学院名称',
    `staff_id` varchar(50) DEFAULT NULL COMMENT '员工编号',
    `office_location` varchar(200) DEFAULT NULL COMMENT '办公地点',
    `duty_phone` varchar(20) DEFAULT NULL COMMENT '值班电话',
    `class_count` int DEFAULT '0' COMMENT '管理班级数',
    `student_count` int DEFAULT '0' COMMENT '管理学生数',
    PRIMARY KEY (`id`),
    UNIQUE KEY `user_id` (`user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_college_id` (`college_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '辅导员信息表';

CREATE TABLE IF NOT EXISTS `college_admin_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '关联 users.id',
    `name` varchar(50) NOT NULL COMMENT '姓名',
    `college_id` bigint NOT NULL COMMENT '所属学院',
    `phone` varchar(20) DEFAULT NULL,
    `email` varchar(100) DEFAULT NULL,
    `title` varchar(50) DEFAULT NULL COMMENT '职称',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_college_admin_user` (`user_id`),
    KEY `idx_college_admin_college` (`college_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '学院管理员档案';

CREATE TABLE IF NOT EXISTS `student_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `student_id` varchar(20) NOT NULL COMMENT '学号',
    `name` varchar(50) NOT NULL,
    `gender` varchar(10) DEFAULT NULL COMMENT '性别',
    `college_id` bigint DEFAULT NULL,
    `major_id` bigint DEFAULT NULL,
    `class_id` bigint DEFAULT NULL,
    `class_name` varchar(255) DEFAULT NULL,
    `enrollment_year` int DEFAULT NULL COMMENT '入学年份',
    `privacy_level` tinyint(1) DEFAULT '2',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_profile_user_id` (`user_id`),
    UNIQUE KEY `uk_student_profile_student_id` (`student_id`),
    KEY `idx_student_profile_college_id` (`college_id`),
    KEY `idx_student_profile_class_id` (`class_id`),
    KEY `idx_student_profile_major_id` (`major_id`),
    CONSTRAINT `fk_student_profile_class_id` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
    CONSTRAINT `fk_student_profile_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`),
    CONSTRAINT `fk_student_profile_major_id` FOREIGN KEY (`major_id`) REFERENCES `majors` (`id`),
    CONSTRAINT `fk_student_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 安全/审计表 ====================

CREATE TABLE IF NOT EXISTS `security_logs` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `ip_address` varchar(50) DEFAULT NULL,
    `login_time` timestamp NULL DEFAULT NULL,
    `is_successful` tinyint(1) DEFAULT '1',
    `action` varchar(100) DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_security_logs_user_id` (`user_id`),
    CONSTRAINT `fk_security_logs_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `login_logs` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `username` varchar(100) DEFAULT NULL,
    `login_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `ip_address` varchar(50) DEFAULT NULL,
    `device` varchar(200) DEFAULT NULL,
    `location` varchar(100) DEFAULT NULL,
    `status` varchar(20) DEFAULT 'success',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `action_type` varchar(50) DEFAULT NULL,
    `entity_type` varchar(50) DEFAULT NULL,
    `entity_id` bigint DEFAULT NULL,
    `details` text,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_logs_action_type` (`action_type`),
    KEY `idx_audit_logs_user_id` (`user_id`),
    CONSTRAINT `fk_audit_logs_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 课程相关 ====================

CREATE TABLE IF NOT EXISTS `courses` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(20) NOT NULL,
    `name` varchar(100) NOT NULL,
    `credits` decimal(5,2) NOT NULL,
    `type` varchar(20) NOT NULL,
    `description` varchar(500) DEFAULT NULL,
    `module_id` bigint DEFAULT NULL,
    `score_template` varchar(50) DEFAULT NULL,
    `teacher_id` bigint DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_courses_code` (`code`),
    KEY `idx_courses_code` (`code`),
    KEY `idx_courses_module_id` (`module_id`),
    CONSTRAINT `fk_courses_module_id` FOREIGN KEY (`module_id`) REFERENCES `elective_modules` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `teacher_course` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `course_id` bigint NOT NULL,
    `semester` int DEFAULT NULL,
    `year` int DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_course` (`teacher_id`,`course_id`),
    KEY `idx_teacher_course_teacher_id` (`teacher_id`),
    KEY `idx_teacher_course_course_id` (`course_id`),
    CONSTRAINT `fk_teacher_course_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
    CONSTRAINT `fk_teacher_course_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `course_plans` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `course_id` bigint NOT NULL,
    `teacher_id` bigint DEFAULT NULL,
    `semester` varchar(20) DEFAULT NULL,
    `year` int DEFAULT NULL,
    `start_date` date DEFAULT NULL,
    `end_date` date DEFAULT NULL,
    `class_time` varchar(100) DEFAULT NULL,
    `location` varchar(200) DEFAULT NULL,
    `max_students` int DEFAULT '50',
    `status` int DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_plans_course_id` (`course_id`),
    KEY `idx_course_plans_semester` (`semester`),
    CONSTRAINT `fk_course_plans_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `enrollments` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `course_id` bigint NOT NULL,
    `class_id` bigint DEFAULT NULL,
    `status` int DEFAULT '0',
    `semester` int DEFAULT NULL,
    `year` int DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_enrollments_student_id` (`student_id`),
    KEY `idx_enrollments_course_id` (`course_id`),
    KEY `fk_enrollments_class_id` (`class_id`),
    CONSTRAINT `fk_enrollments_class_id` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
    CONSTRAINT `fk_enrollments_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
    CONSTRAINT `fk_enrollments_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 这里原来有 `teaching_resources`（2026-10-01 删） ====================
--
-- 它和上面的 `support_resource` 是**两张不同的表**，来路也不同：
-- 这张是 course-service 里一整套**从没被接上过**的后端面 ——
-- `TeachingResourceController`（`@RequestMapping("/teaching-resources")`，9 个端点）
-- + 实体 + Mapper + Service + Impl。2026-10-01 复核时全仓零调用方：
--
--   * 网关**没有这条路由**（`gateway-service/application.yml` 里 course-service 只挂
--     `Path=/api/courses/**` + `StripPrefix=1`），而 course-service 自己**没有
--     `context-path`** ⇒ 连 `/api/courses/teaching-resources/...` 也映射不到 `/teaching-resources/...`；
--   * 前端 `academic-warning-frontend/src` 与小程序 `academic-warning-xcx/src` grep
--     `teaching-resource` **0 命中**；
--   * 全仓提到 `teaching-resources` 的地方，当时只有它自己的 `@RequestMapping`
--     和实体的 `@TableName` 两行。
--
-- 表里那 10 行数据同样没有任何代码消费。⇒ 代码与表一起删，口径同第十一轮删 `/api/resources/**`。

-- ==================== 成绩相关 ====================

CREATE TABLE IF NOT EXISTS `scores` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `course_id` bigint NOT NULL,
    `semester` int NOT NULL,
    `year` int DEFAULT NULL,
    `regular_score` decimal(5,2) DEFAULT NULL,
    `final_score` decimal(5,2) DEFAULT NULL,
    `score_total` decimal(5,2) NOT NULL,
    `grade` varchar(20) DEFAULT NULL,
    `grade_point` decimal(5,2) DEFAULT NULL,
    `reason_for_change` varchar(500) DEFAULT NULL,
    `modified_by` bigint DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_scores_student_id` (`student_id`),
    KEY `idx_scores_course_id` (`course_id`),
    KEY `idx_scores_semester` (`semester`),
    KEY `idx_scores_student_id_semester` (`student_id`,`semester`),
    CONSTRAINT `fk_scores_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
    CONSTRAINT `fk_scores_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `score_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `course_id` bigint NOT NULL,
    `student_id` bigint NOT NULL,
    `teacher_id` bigint NOT NULL,
    `student_name` varchar(100) DEFAULT NULL,
    `class_name` varchar(100) DEFAULT NULL,
    `score` decimal(5,2) DEFAULT NULL,
    `score_level` int DEFAULT NULL,
    `semester` varchar(20) DEFAULT NULL COMMENT '学期',
    `year` int DEFAULT NULL COMMENT '学年',
    `remark` varchar(500) DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `regular_score` decimal(5,2) DEFAULT NULL,
    `final_score` decimal(5,2) DEFAULT NULL,
    `score_total` decimal(5,2) DEFAULT NULL,
    `grade` varchar(20) DEFAULT NULL,
    `grade_point` decimal(3,2) DEFAULT NULL,
    `reason_for_change` varchar(500) DEFAULT NULL,
    `modified_by` bigint DEFAULT NULL,
    `created_at` datetime DEFAULT NULL,
    `updated_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '成绩记录表';

CREATE TABLE IF NOT EXISTS `score_appeals` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `warning_id` bigint DEFAULT NULL,
    `student_id` bigint NOT NULL,
    `score_id` bigint DEFAULT NULL,
    `course_id` bigint DEFAULT NULL,
    `reason` text,
    -- 2026-09-30 加：学生自己写的申诉正文（"请详细描述申诉理由"那一栏）。
    -- 加列前提交体里这个 key 被静默丢弃，所以历史行为 NULL。
    `description` text,
    `attachments` varchar(500) DEFAULT NULL,
    `status` int DEFAULT '0',
    `reply` text,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_score_appeals_student_id` (`student_id`),
    KEY `fk_score_appeals_score_id` (`score_id`),
    CONSTRAINT `fk_score_appeals_score_id` FOREIGN KEY (`score_id`) REFERENCES `scores` (`id`),
    CONSTRAINT `fk_score_appeals_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `score_audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `student_name` varchar(100) DEFAULT NULL,
    `course_id` bigint DEFAULT NULL,
    `course_name` varchar(100) DEFAULT NULL,
    `teacher_id` bigint NOT NULL,
    `old_score` decimal(5,2) DEFAULT NULL,
    `new_score` decimal(5,2) DEFAULT NULL,
    `difference` decimal(5,2) GENERATED ALWAYS AS ((`new_score` - `old_score`)) STORED,
    `reason` varchar(500) DEFAULT NULL,
    `modified_by` varchar(100) DEFAULT NULL,
    `modified_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `fail_records` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL COMMENT '学生ID',
    `course_id` bigint DEFAULT NULL COMMENT '课程ID',
    `course_name` varchar(100) DEFAULT NULL COMMENT '课程名称',
    `exam_time` datetime DEFAULT NULL COMMENT '考试时间',
    `score` decimal(5,2) DEFAULT NULL COMMENT '成绩',
    `fail_type` varchar(50) DEFAULT NULL COMMENT '挂科类型：fail-不及格, absent-缺考, cheat-作弊',
    `semester` varchar(20) DEFAULT NULL COMMENT '学期',
    `academic_year` varchar(20) DEFAULT NULL COMMENT '学年',
    `status` int DEFAULT '0' COMMENT '状态：0-挂科, 1-补考通过, 2-重修通过',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '挂科记录表';

-- ==================== 预警相关 ====================

CREATE TABLE IF NOT EXISTS `academic_warning` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `student_name` varchar(100) DEFAULT NULL,
    `rule_id` bigint DEFAULT NULL,
    -- 2026-09-30 加：触发本次预警时的挂科门数。是"预警判重键"的一半
    -- （判重键 = 学生 + 门数，且**不看 status**），也是档位校验的输入。
    -- 在此之前的取值方式见 §6.6。**这条注释是 2026-10-01 补的** ——
    -- 在此之前 init_schema.sql 一直缺这一列（见本节末的"结构漂移"说明）。
    `fail_count` int DEFAULT NULL,
    `type` int DEFAULT NULL,
    `title` varchar(100) DEFAULT NULL,
    `description` text,
    `warning_level` int NOT NULL,
    `warning_message` text,
    `trigger_score` decimal(5,2) DEFAULT NULL,
    `gpa_before` decimal(5,2) DEFAULT NULL,
    `gpa_after` decimal(5,2) DEFAULT NULL,
    `status` int DEFAULT '0',
    `appeal_status` varchar(20) DEFAULT 'none',
    `handled_by` varchar(100) DEFAULT NULL,
    `handle_result` text,
    `handled_at` timestamp NULL DEFAULT NULL,
    `created_by` varchar(100) DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` varchar(100) DEFAULT NULL,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_academic_warning_rule_id` (`rule_id`),
    KEY `idx_academic_warning_student_id` (`student_id`),
    CONSTRAINT `fk_academic_warning_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `warning_release_records` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `warning_id` bigint NOT NULL COMMENT '关联预警记录ID',
    `student_id` bigint NOT NULL COMMENT '学生ID',
    `release_reason` varchar(50) DEFAULT NULL COMMENT '解除原因：makeup_pass-补考通过, retake_pass-重修合格, other-其他',
    `release_note` text COMMENT '解除备注',
    `release_by` bigint DEFAULT NULL COMMENT '解除人ID',
    `release_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '解除时间',
    PRIMARY KEY (`id`),
    KEY `idx_warning_id` (`warning_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '预警解除记录表';

CREATE TABLE IF NOT EXISTS `warning_tracking_records` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `warning_id` bigint NOT NULL COMMENT '关联预警记录ID',
    `tracking_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '跟踪时间',
    `tracking_content` text COMMENT '跟踪内容',
    `tracking_by` bigint DEFAULT NULL COMMENT '跟踪人ID',
    `next_follow_time` datetime DEFAULT NULL COMMENT '下次跟进时间',
    PRIMARY KEY (`id`),
    KEY `idx_warning_id` (`warning_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '预警跟踪记录表';

-- ==================== 帮扶计划 ====================

CREATE TABLE IF NOT EXISTS `assistance_plans` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `counselor_id` bigint DEFAULT NULL,
    `student_id` bigint NOT NULL,
    `content` text,
    `status` int DEFAULT '0',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `progress` tinyint NOT NULL DEFAULT '0' COMMENT '完成进度百分比 0-100',
    `progress_note` text COMMENT '学生更新进度时填写的完成情况说明（最近一次）',
    PRIMARY KEY (`id`),
    KEY `idx_assistance_plans_counselor_id` (`counselor_id`),
    KEY `idx_assistance_plans_student_id` (`student_id`),
    CONSTRAINT `fk_assistance_plans_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `assistance_evaluations` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `plan_id` bigint NOT NULL,
    `counselor_id` bigint DEFAULT NULL,
    `content` text,
    `score` int DEFAULT NULL,
    -- 2026-09-30 加：帮扶有效性评分 1-5。加列前的历史行为 NULL
    -- （是"当时没收集"，不是 0 分，页面不要拿 `|| 0` 盖过去）。
    `effectiveness` int DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_assistance_evaluations_plan_id` (`plan_id`),
    KEY `idx_assistance_evaluations_counselor_id` (`counselor_id`),
    CONSTRAINT `fk_assistance_evaluations_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `assistance_plans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 消息与通知 ====================

CREATE TABLE IF NOT EXISTS `messages` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `sender_id` bigint NOT NULL,
    `receiver_id` bigint NOT NULL,
    `title` varchar(255) DEFAULT NULL,
    `content` text NOT NULL,
    `type` int NOT NULL DEFAULT '1',
    `status` int DEFAULT '0',
    `extra` varchar(500) DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `read_at` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_messages_sender_id` (`sender_id`),
    KEY `fk_messages_receiver_id` (`receiver_id`),
    CONSTRAINT `fk_messages_receiver_id` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_messages_sender_id` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `title` varchar(100) DEFAULT NULL,
    `content` text NOT NULL,
    `type` varchar(50) DEFAULT NULL,
    `warning_id` bigint DEFAULT NULL,
    `push_channel` varchar(50) DEFAULT 'app',
    `is_read` int DEFAULT '0',
    `is_deleted` int DEFAULT '0',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notifications_user_id` (`user_id`),
    CONSTRAINT `fk_notifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `communication_logs` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `counselor_id` bigint DEFAULT NULL,
    `teacher_id` bigint DEFAULT NULL,
    `student_id` bigint DEFAULT NULL,
    `warning_id` bigint DEFAULT NULL,
    `content` text,
    `category` varchar(50) DEFAULT NULL,
    `status` int DEFAULT '0',
    `reply` text,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_communication_logs_counselor_id` (`counselor_id`),
    KEY `idx_communication_logs_student_id` (`student_id`),
    KEY `idx_communication_logs_warning_id` (`warning_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `communication_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `student_id` bigint NOT NULL,
    `content` text,
    `type` varchar(50) DEFAULT NULL,
    `student_name` varchar(100) DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '沟通记录表';

-- ==================== 反馈 ====================

CREATE TABLE IF NOT EXISTS `feedbacks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `student_id` bigint NOT NULL,
    `content` text NOT NULL,
    `category` varchar(50) DEFAULT NULL,
    `rating` int DEFAULT NULL,
    `status` varchar(50) DEFAULT NULL,
    `reply_content` text,
    `replied_at` timestamp NULL DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_feedbacks_teacher_id` (`teacher_id`),
    KEY `idx_feedbacks_student_id` (`student_id`),
    CONSTRAINT `fk_feedbacks_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`),
    CONSTRAINT `fk_feedbacks_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `feedback` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `category` varchar(50) DEFAULT NULL,
    `content` text,
    `reply` text,
    `status` int DEFAULT '0' COMMENT '0-未回复, 1-已回复',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '反馈表';

-- ==================== 班级管理申请 ====================

CREATE TABLE IF NOT EXISTS `class_management_request` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `class_id` bigint NOT NULL,
    `counselor_id` bigint DEFAULT NULL,
    `user_type` varchar(20) DEFAULT NULL,
    `status` varchar(20) DEFAULT 'pending',
    `reason` varchar(500) DEFAULT NULL,
    `reject_reason` varchar(500) DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_management_request_teacher_class` (`teacher_id`,`class_id`),
    KEY `idx_class_management_request_class_id` (`class_id`),
    KEY `idx_class_management_request_status` (`status`),
    CONSTRAINT `fk_class_management_request_class_id` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
    CONSTRAINT `fk_class_management_request_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '班级管理申请表';

CREATE TABLE IF NOT EXISTS `teacher_class_application` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `class_id` bigint NOT NULL,
    `reason` text,
    `status` int DEFAULT '0' COMMENT '0-待审核, 1-通过, 2-拒绝',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `class_name` varchar(100) DEFAULT NULL,
    `teacher_name` varchar(100) DEFAULT NULL,
    `teacher_username` varchar(50) DEFAULT NULL,
    `application_type` int DEFAULT '0' COMMENT '申请类型：0-申请管理，1-解除管理',
    `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
    PRIMARY KEY (`id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_class_id` (`class_id`),
    KEY `idx_status` (`status`),
    KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '教师班级管理申请表';

CREATE TABLE IF NOT EXISTS `counselor_class_application` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `counselor_id` bigint NOT NULL,
    `class_id` bigint NOT NULL,
    `reason` text,
    `status` int DEFAULT '0' COMMENT '0-待审核, 1-通过, 2-拒绝',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `class_name` varchar(100) DEFAULT NULL,
    `counselor_name` varchar(100) DEFAULT NULL,
    `application_type` int DEFAULT '0' COMMENT '申请类型：0-申请管理，1-解除管理',
    `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
    PRIMARY KEY (`id`),
    KEY `idx_counselor_id` (`counselor_id`),
    KEY `idx_class_id` (`class_id`),
    KEY `idx_status` (`status`),
    KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '辅导员班级管理申请表';

CREATE TABLE IF NOT EXISTS `counselor_class_management` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
    `class_id` bigint NOT NULL COMMENT '班级ID',
    `class_name` varchar(100) DEFAULT NULL COMMENT '班级名称',
    `status` int DEFAULT '1' COMMENT '状态: 0-解除管理, 1-管理中',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_counselor_class` (`counselor_id`,`class_id`),
    KEY `idx_counselor_id` (`counselor_id`),
    KEY `idx_class_id` (`class_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci comment '辅导员班级管理表';

-- ==================== 学生设置与偏好 ====================

CREATE TABLE IF NOT EXISTS `student_settings` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `notification_enabled` tinyint(1) DEFAULT '1',
    `email_notifications` tinyint(1) DEFAULT '1',
    `theme` varchar(50) DEFAULT 'light',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_student_settings_student_id` (`student_id`),
    CONSTRAINT `fk_student_settings_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_settings` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT 'users.id（不是 student_profile.id）',
    `warning_notification` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收预警通知',
    `system_message` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收系统消息',
    `class_notification` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收班级通知',
    `student_feedback` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收学生反馈',
    `email_reminder` tinyint(1) NOT NULL DEFAULT '0' COMMENT '邮件提醒',
    `office_location` varchar(200) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '办公地点',
    `office_phone` varchar(20) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '办公电话',
    `office_hours` varchar(100) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '办公时间',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_settings_user_id` (`user_id`),
    CONSTRAINT `fk_user_settings_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `subscription_preferences` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `subscribe_warnings` tinyint(1) DEFAULT '1',
    `subscribe_low` tinyint(1) DEFAULT '0',
    `subscribe_medium` tinyint(1) DEFAULT '1',
    `subscribe_high` tinyint(1) DEFAULT '1',
    `subscribe_assistance` tinyint(1) DEFAULT '1',
    `subscribe_system` tinyint(1) DEFAULT '1',
    `push_email` tinyint(1) DEFAULT '1',
    `push_sms` tinyint(1) DEFAULT '0',
    `push_app` tinyint(1) DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subscription_preferences_student_id` (`student_id`),
    CONSTRAINT `fk_subscription_preferences_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 基准分析 ====================

CREATE TABLE IF NOT EXISTS `benchmark_analysis` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `student_id` bigint NOT NULL,
    `class_id` bigint DEFAULT NULL,
    `major_id` bigint DEFAULT NULL,
    `semester` varchar(50) DEFAULT NULL,
    `student_gpa` decimal(5,3) DEFAULT NULL,
    `class_avg_gpa` decimal(5,3) DEFAULT NULL,
    `major_avg_gpa` decimal(5,3) DEFAULT NULL,
    `class_rank` int DEFAULT NULL,
    `class_total` int DEFAULT NULL,
    `major_rank` int DEFAULT NULL,
    `major_total` int DEFAULT NULL,
    `courses_passed` int DEFAULT '0',
    `courses_failed` int DEFAULT '0',
    `required_credits` decimal(5,2) DEFAULT NULL,
    `credits_on_track` int DEFAULT '1',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_benchmark_analysis_student_semester` (`student_id`,`semester`),
    KEY `idx_benchmark_analysis_student_id` (`student_id`),
    KEY `idx_benchmark_analysis_class_id` (`class_id`),
    KEY `idx_benchmark_analysis_major_id` (`major_id`),
    CONSTRAINT `fk_benchmark_analysis_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 管理端任务（W1.11，2026-10-02） ====================
--
-- PC `AdminMessages.vue` 的「任务管理」tab（创建表单 + 四个统计卡 + 状态筛选 +
-- 行内改状态 + 单条/批量删除 + 分页）一直写好了，而 `/admin/tasks/*` 四个端点
-- 全是桩（列表写死空、其余只 log 就回 successfully）。这张表是它的真归宿。
-- 迁移脚本：`db_backups/admin_tasks_20261002_apply.sql`（+ `_rollback.sql`）。
-- ⚠️ `updated_at` 刻意不加 `ON UPDATE CURRENT_TIMESTAMP`（与本仓其余表一致），
--    由 UPDATE 语句显式写。

CREATE TABLE IF NOT EXISTS `admin_tasks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL,
    `description` text,
    `priority` varchar(20) NOT NULL DEFAULT 'medium',
    `status` varchar(20) NOT NULL DEFAULT 'pending',
    `created_by` bigint DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin_tasks_status` (`status`),
    KEY `idx_admin_tasks_created_by` (`created_by`),
    CONSTRAINT `fk_admin_tasks_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
