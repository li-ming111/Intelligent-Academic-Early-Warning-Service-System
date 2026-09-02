-- ========================================
-- 智能学业预警服务系统 - 完整建表脚本
-- 与 academic_warning_system.sql 完全对齐
-- 使用 CREATE TABLE IF NOT EXISTS，多服务并行启动安全幂等
-- ========================================

-- ==================== 基础表（无外键依赖） ====================

CREATE TABLE IF NOT EXISTS `colleges` (
    `id`            bigint auto_increment primary key,
    `name`          varchar(100)                        not null,
    `code`          varchar(50)                         null,
    `description`   varchar(500)                        null,
    `status`        int       default 1                 null,
    `student_count` int       default 0                 null,
    `teacher_count` int       default 0                 null,
    `created_at`    timestamp default CURRENT_TIMESTAMP null,
    `updated_at`    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_colleges_name` unique (`name`)
);

CREATE TABLE IF NOT EXISTS `users` (
    `id`         bigint auto_increment primary key,
    `username`   varchar(50)                          not null,
    `password`   varchar(255)                         not null,
    `name`       varchar(100)                         null,
    `email`      varchar(100)                         null,
    `phone`      varchar(20)                          null,
    `role`       tinyint(1)                           not null comment '1-学生, 2-教师, 3-辅导员, 4-管理员',
    `status`     tinyint(1) default 1                 null,
    `created_at` timestamp  default CURRENT_TIMESTAMP null,
    `updated_at` timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    `deleted_at` timestamp                            null,
    constraint `uk_users_username` unique (`username`)
);
CREATE INDEX IF NOT EXISTS `idx_users_role` ON `users` (`role`);
CREATE INDEX IF NOT EXISTS `idx_users_status` ON `users` (`status`);

CREATE TABLE IF NOT EXISTS `roles` (
    `id`          bigint auto_increment primary key,
    `name`        varchar(50)                         not null,
    `code`        varchar(50)                         not null,
    `description` varchar(255)                        null,
    `status`      int       default 1                 null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_roles_code` unique (`code`)
);

CREATE TABLE IF NOT EXISTS `permissions` (
    `id`          bigint auto_increment primary key,
    `name`        varchar(100)                        not null,
    `code`        varchar(100)                        not null,
    `description` varchar(500)                        null,
    `status`      int       default 1                 null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_permissions_code` unique (`code`)
);

CREATE TABLE IF NOT EXISTS `role_permissions` (
    `id`            bigint auto_increment primary key,
    `role_id`       bigint                              not null,
    `permission_id` bigint                              not null,
    `created_at`    timestamp default CURRENT_TIMESTAMP null,
    `updated_at`    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_role_permissions_role_permission` unique (`role_id`, `permission_id`),
    constraint `fk_role_permissions_role_id` foreign key (`role_id`) references `roles` (`id`),
    constraint `fk_role_permissions_permission_id` foreign key (`permission_id`) references `permissions` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_role_permissions_role_id` ON `role_permissions` (`role_id`);
CREATE INDEX IF NOT EXISTS `idx_role_permissions_permission_id` ON `role_permissions` (`permission_id`);

CREATE TABLE IF NOT EXISTS `elective_modules` (
    `id`          bigint auto_increment primary key,
    `module_code` varchar(20)                         not null,
    `module_name` varchar(100)                        not null,
    `description` text                                null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_elective_modules_module_code` unique (`module_code`)
) comment '选修课模块表';

CREATE TABLE IF NOT EXISTS `warning_rule` (
    `id`            bigint auto_increment primary key,
    `rule_name`     varchar(100)                        not null,
    `rule_code`     varchar(50)                         not null,
    `description`   text                                null,
    `warning_level` int                                 not null,
    `expression`    text                                null,
    `status`        int       default 1                 null,
    `created_by`    varchar(100)                        null,
    `created_at`    timestamp default CURRENT_TIMESTAMP null,
    `updated_by`    varchar(100)                        null,
    `updated_at`    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_warning_rule_rule_code` unique (`rule_code`)
);
CREATE INDEX IF NOT EXISTS `idx_warning_rule_warning_level` ON `warning_rule` (`warning_level`);

CREATE TABLE IF NOT EXISTS `warning_rules` (
    `id`                        bigint auto_increment primary key,
    `name`                      varchar(100)                       not null comment '规则名称',
    `description`               text                               null comment '规则描述',
    `level`                     int      default 1                 null comment '预警等级：1-低, 2-中, 3-高',
    `rule_condition`            text                               null comment '触发条件表达式',
    `status`                    int      default 1                 null comment '状态：0-禁用，1-启用',
    `created_at`                datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at`                datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    `rule_code`                 varchar(50)                        null comment '规则编码',
    `created_by`                bigint                             null comment '创建人ID',
    `updated_by`                bigint                             null comment '更新人ID',
    `fail_definition`           text                               null comment '挂科定义标准',
    `statistic_cycle`           varchar(50)                        null comment '统计周期',
    `trigger_type`              varchar(50)                        null comment '触发机制',
    `release_condition`         text                               null comment '预警解除条件',
    `intervention_plan`         text                               null comment '学业干预方案',
    `notification_flow`         text                               null comment '通知流程',
    `responsible_person`        varchar(100)                       null comment '责任人',
    `tracking_mechanism`        text                               null comment '跟踪反馈机制',
    `archive_spec`              text                               null comment '归档规范',
    `data_caliber`              text                               null comment '数据统计口径',
    `department_responsibility` text                               null comment '部门职责分工',
    `appeal_process`            text                               null comment '申诉复议流程',
    `exception_handling`        text                               null comment '例外情况处理办法'
) comment '预警规则表';
CREATE INDEX IF NOT EXISTS `idx_warning_rules_status` ON `warning_rules` (`status`);
CREATE INDEX IF NOT EXISTS `idx_warning_rules_level` ON `warning_rules` (`level`);

CREATE TABLE IF NOT EXISTS `support_resource` (
    `id`          bigint auto_increment primary key,
    `title`       varchar(200)                        not null,
    `description` text                                null,
    `type`        varchar(50)                         null,
    `category`    varchar(50)                         null,
    `content`     text                                null,
    `url`         varchar(500)                        null,
    `file_path`   varchar(500)                        null,
    `file_size`   bigint                              null,
    `file_type`   varchar(50)                         null,
    `author`      varchar(100)                        null,
    `views`       int       default 0                 null,
    `downloads`   int       default 0                 null,
    `status`      int       default 1                 null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS `idx_support_resource_type` ON `support_resource` (`type`);
CREATE INDEX IF NOT EXISTS `idx_support_resource_category` ON `support_resource` (`category`);
CREATE INDEX IF NOT EXISTS `idx_support_resource_status` ON `support_resource` (`status`);

CREATE TABLE IF NOT EXISTS `export_history` (
    `id`           bigint auto_increment primary key,
    `data_type`    varchar(50)                         not null,
    `file_name`    varchar(255)                        not null,
    `record_count` int       default 0                 null,
    `exported_by`  bigint                              null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null
);
CREATE INDEX IF NOT EXISTS `idx_export_history_data_type` ON `export_history` (`data_type`);
CREATE INDEX IF NOT EXISTS `idx_export_history_created_at` ON `export_history` (`created_at`);

-- ==================== 依赖 colleges 的表 ====================

CREATE TABLE IF NOT EXISTS `majors` (
    `id`          bigint auto_increment primary key,
    `college_id`  bigint                              not null,
    `name`        varchar(100)                        not null,
    `code`        varchar(50)                         not null,
    `description` varchar(500)                        null,
    `status`      int       default 1                 null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_majors_code` unique (`code`),
    constraint `fk_majors_college_id` foreign key (`college_id`) references `colleges` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_majors_college_id` ON `majors` (`college_id`);

CREATE TABLE IF NOT EXISTS `classes` (
    `id`           bigint auto_increment primary key,
    `name`         varchar(50)                         not null,
    `college_id`   bigint                              not null,
    `major_id`     bigint                              null,
    `teacher_id`   bigint                              null,
    `counselor_id` bigint                              null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_classes_college_id` foreign key (`college_id`) references `colleges` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_classes_college_id` ON `classes` (`college_id`);

CREATE TABLE IF NOT EXISTS `teacher_profile` (
    `id`             bigint auto_increment primary key,
    `user_id`        bigint                              not null,
    `name`           varchar(50)                         null,
    `college_id`     bigint                              not null,
    `title`          varchar(50)                         null,
    `research_areas` varchar(200)                        null,
    `created_at`     timestamp default CURRENT_TIMESTAMP null,
    `updated_at`     timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_teacher_profile_user_id` unique (`user_id`),
    constraint `fk_teacher_profile_college_id` foreign key (`college_id`) references `colleges` (`id`),
    constraint `fk_teacher_profile_user_id` foreign key (`user_id`) references `users` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_teacher_profile_college_id` ON `teacher_profile` (`college_id`);

-- ==================== 依赖 users + colleges 的表 ====================

CREATE TABLE IF NOT EXISTS `admin_profile` (
    `id`         bigint auto_increment primary key,
    `user_id`    bigint                              not null,
    `name`       varchar(100)                        null,
    `phone`      varchar(20)                         null,
    `email`      varchar(100)                        null,
    `department` varchar(100)                        null,
    `created_at` timestamp default CURRENT_TIMESTAMP null,
    `updated_at` timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_admin_profile_user_id` unique (`user_id`),
    constraint `fk_admin_profile_user_id` foreign key (`user_id`) references `users` (`id`)
);

CREATE TABLE IF NOT EXISTS `counselor_profiles` (
    `id`              bigint auto_increment primary key,
    `user_id`         bigint                             not null comment '用户ID',
    `college_id`      bigint                             null comment '学院ID',
    `counselor_id`    varchar(50)                        null comment '辅导员编号',
    `name`            varchar(100)                       null comment '姓名',
    `gender`          varchar(10)                        null comment '性别',
    `phone`           varchar(20)                        null comment '联系电话',
    `email`           varchar(100)                       null comment '邮箱',
    `department`      varchar(100)                       null comment '部门',
    `title`           varchar(50)                        null comment '职称',
    `education`       varchar(50)                        null comment '学历',
    `introduction`    text                               null comment '个人简介',
    `status`          int      default 1                 null comment '状态：0-禁用，1-启用',
    `created_at`      datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at`      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    `college_name`    varchar(100)                       null comment '学院名称',
    `staff_id`        varchar(50)                        null comment '员工编号',
    `office_location` varchar(200)                       null comment '办公地点',
    `duty_phone`      varchar(20)                        null comment '值班电话',
    `class_count`     int      default 0                 null comment '管理班级数',
    `student_count`   int      default 0                 null comment '管理学生数',
    constraint `uk_counselor_profiles_user_id` unique (`user_id`)
) comment '辅导员信息表';
CREATE INDEX IF NOT EXISTS `idx_counselor_profiles_user_id` ON `counselor_profiles` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_counselor_profiles_college_id` ON `counselor_profiles` (`college_id`);
CREATE INDEX IF NOT EXISTS `idx_counselor_profiles_status` ON `counselor_profiles` (`status`);

CREATE TABLE IF NOT EXISTS `student_profile` (
    `id`            bigint auto_increment primary key,
    `user_id`       bigint                               not null,
    `student_id`    varchar(20)                          not null comment '学号',
    `name`          varchar(50)                          not null,
    `college_id`    bigint                               null,
    `major_id`      bigint                               null,
    `class_id`      bigint                               null,
    `class_name`    varchar(255)                         null,
    `privacy_level` tinyint(1) default 2                 null,
    `created_at`    timestamp  default CURRENT_TIMESTAMP null,
    `updated_at`    timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_student_profile_student_id` unique (`student_id`),
    constraint `uk_student_profile_user_id` unique (`user_id`),
    constraint `fk_student_profile_user_id` foreign key (`user_id`) references `users` (`id`),
    constraint `fk_student_profile_college_id` foreign key (`college_id`) references `colleges` (`id`),
    constraint `fk_student_profile_major_id` foreign key (`major_id`) references `majors` (`id`),
    constraint `fk_student_profile_class_id` foreign key (`class_id`) references `classes` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_student_profile_college_id` ON `student_profile` (`college_id`);
CREATE INDEX IF NOT EXISTS `idx_student_profile_class_id` ON `student_profile` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_student_profile_major_id` ON `student_profile` (`major_id`);

CREATE TABLE IF NOT EXISTS `student` (
    `id`         bigint auto_increment primary key,
    `name`       varchar(100)                       not null comment '学生姓名',
    `student_id` varchar(50)                        not null comment '学号',
    `class_id`   bigint                             null comment '班级ID',
    `college_id` bigint                             null comment '学院ID',
    `major_id`   bigint                             null comment '专业ID',
    `gender`     varchar(10)                        null comment '性别',
    `phone`      varchar(20)                        null comment '联系电话',
    `email`      varchar(100)                       null comment '邮箱',
    `status`     int      default 1                 null comment '状态：0-禁用，1-启用',
    `created_at` datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at` datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint `uk_student_student_id` unique (`student_id`)
) comment '学生表';
CREATE INDEX IF NOT EXISTS `idx_student_class_id` ON `student` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_student_college_id` ON `student` (`college_id`);
CREATE INDEX IF NOT EXISTS `idx_student_major_id` ON `student` (`major_id`);

-- ==================== 安全/审计表 ====================

CREATE TABLE IF NOT EXISTS `security_logs` (
    `id`            bigint auto_increment primary key,
    `user_id`       bigint                               not null,
    `ip_address`    varchar(50)                          null,
    `login_time`    timestamp                            null,
    `is_successful` tinyint(1) default 1                 null,
    `action`        varchar(100)                         null,
    `created_at`    timestamp  default CURRENT_TIMESTAMP null,
    constraint `fk_security_logs_user_id` foreign key (`user_id`) references `users` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_security_logs_user_id` ON `security_logs` (`user_id`);

CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id`          bigint auto_increment primary key,
    `user_id`     bigint                              null,
    `action_type` varchar(50)                         null,
    `entity_type` varchar(50)                         null,
    `entity_id`   bigint                              null,
    `details`     text                                null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    constraint `fk_audit_logs_user_id` foreign key (`user_id`) references `users` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_audit_logs_user_id` ON `audit_logs` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_audit_logs_action_type` ON `audit_logs` (`action_type`);

-- ==================== 课程相关 ====================

CREATE TABLE IF NOT EXISTS `courses` (
    `id`             bigint auto_increment primary key,
    `code`           varchar(20)                         not null,
    `name`           varchar(100)                        not null,
    `credits`        decimal(5, 2)                       not null,
    `type`           varchar(20)                         not null,
    `description`    varchar(500)                        null,
    `module_id`      bigint                              null,
    `score_template` varchar(50)                         null,
    `teacher_id`     bigint                              null,
    `created_at`     timestamp default CURRENT_TIMESTAMP null,
    `updated_at`     timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_courses_code` unique (`code`),
    constraint `fk_courses_module_id` foreign key (`module_id`) references `elective_modules` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_courses_code` ON `courses` (`code`);
CREATE INDEX IF NOT EXISTS `idx_courses_module_id` ON `courses` (`module_id`);

CREATE TABLE IF NOT EXISTS `teacher_course` (
    `id`         bigint auto_increment primary key,
    `teacher_id` bigint                              not null,
    `course_id`  bigint                              not null,
    `semester`   int                                 null,
    `year`       int                                 null,
    `created_at` timestamp default CURRENT_TIMESTAMP null,
    `updated_at` timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_teacher_course` unique (`teacher_id`, `course_id`),
    constraint `fk_teacher_course_teacher_id` foreign key (`teacher_id`) references `teacher_profile` (`id`),
    constraint `fk_teacher_course_course_id` foreign key (`course_id`) references `courses` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_teacher_course_teacher_id` ON `teacher_course` (`teacher_id`);
CREATE INDEX IF NOT EXISTS `idx_teacher_course_course_id` ON `teacher_course` (`course_id`);

CREATE TABLE IF NOT EXISTS `course_plans` (
    `id`           bigint auto_increment primary key,
    `course_id`    bigint                              not null,
    `teacher_id`   bigint                              null,
    `semester`     varchar(20)                         null,
    `year`         int                                 null,
    `start_date`   date                                null,
    `end_date`     date                                null,
    `class_time`   varchar(100)                        null,
    `location`     varchar(200)                        null,
    `max_students` int       default 50                null,
    `status`       int       default 1                 null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_course_plans_course_id` foreign key (`course_id`) references `courses` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_course_plans_course_id` ON `course_plans` (`course_id`);
CREATE INDEX IF NOT EXISTS `idx_course_plans_semester` ON `course_plans` (`semester`);

CREATE TABLE IF NOT EXISTS `enrollments` (
    `id`         bigint auto_increment primary key,
    `student_id` bigint                              not null,
    `course_id`  bigint                              not null,
    `class_id`   bigint                              null,
    `status`     int       default 0                 null,
    `semester`   int                                 null,
    `year`       int                                 null,
    `created_at` timestamp default CURRENT_TIMESTAMP null,
    `updated_at` timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_enrollments_student_id` foreign key (`student_id`) references `student_profile` (`id`),
    constraint `fk_enrollments_course_id` foreign key (`course_id`) references `courses` (`id`),
    constraint `fk_enrollments_class_id` foreign key (`class_id`) references `classes` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_enrollments_student_id` ON `enrollments` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_enrollments_course_id` ON `enrollments` (`course_id`);

CREATE TABLE IF NOT EXISTS `teaching_resources` (
    `id`          bigint auto_increment primary key,
    `course_id`   bigint                              null,
    `title`       varchar(200)                        not null,
    `description` text                                null,
    `type`        varchar(50)                         null,
    `url`         varchar(500)                        null,
    `file_path`   varchar(500)                        null,
    `file_size`   bigint                              null,
    `file_type`   varchar(50)                         null,
    `downloads`   int       default 0                 null,
    `status`      int       default 1                 null,
    `created_by`  varchar(100)                        null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_by`  varchar(100)                        null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_teaching_resources_course_id` foreign key (`course_id`) references `courses` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_teaching_resources_course_id` ON `teaching_resources` (`course_id`);
CREATE INDEX IF NOT EXISTS `idx_teaching_resources_uploader_id` ON `teaching_resources` (`created_by`);

-- ==================== 成绩相关 ====================

CREATE TABLE IF NOT EXISTS `scores` (
    `id`                bigint auto_increment primary key,
    `student_id`        bigint                              not null,
    `course_id`         bigint                              not null,
    `semester`          int                                 not null,
    `year`              int                                 null,
    `regular_score`     decimal(5, 2)                       null,
    `final_score`       decimal(5, 2)                       null,
    `score_total`       decimal(5, 2)                       not null,
    `grade`             varchar(20)                         null,
    `grade_point`       decimal(5, 2)                       null,
    `reason_for_change` varchar(500)                        null,
    `modified_by`       bigint                              null,
    `created_at`        timestamp default CURRENT_TIMESTAMP null,
    `updated_at`        timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_scores_student_id` foreign key (`student_id`) references `student_profile` (`id`),
    constraint `fk_scores_course_id` foreign key (`course_id`) references `courses` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_scores_student_id` ON `scores` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_scores_course_id` ON `scores` (`course_id`);
CREATE INDEX IF NOT EXISTS `idx_scores_semester` ON `scores` (`semester`);
CREATE INDEX IF NOT EXISTS `idx_scores_student_id_semester` ON `scores` (`student_id`, `semester`);

CREATE TABLE IF NOT EXISTS `score_record` (
    `id`           bigint auto_increment primary key,
    `course_id`    bigint                             not null,
    `student_id`   bigint                             not null,
    `teacher_id`   bigint                             not null,
    `student_name` varchar(100)                       null,
    `class_name`   varchar(100)                       null,
    `score`        decimal(5, 2)                      null,
    `score_level`  int                                null,
    `remark`       varchar(500)                       null,
    `create_time`  datetime default CURRENT_TIMESTAMP null,
    `update_time`  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
) comment '成绩记录表';
CREATE INDEX IF NOT EXISTS `idx_score_record_student_id` ON `score_record` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_score_record_course_id` ON `score_record` (`course_id`);
CREATE INDEX IF NOT EXISTS `idx_score_record_teacher_id` ON `score_record` (`teacher_id`);

CREATE TABLE IF NOT EXISTS `score_appeals` (
    `id`          bigint auto_increment primary key,
    `warning_id`  bigint                              null,
    `student_id`  bigint                              not null,
    `score_id`    bigint                              null,
    `course_id`   bigint                              null,
    `reason`      text                                null,
    `attachments` varchar(500)                        null,
    `status`      int       default 0                 null,
    `reply`       text                                null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_score_appeals_student_id` foreign key (`student_id`) references `student_profile` (`id`),
    constraint `fk_score_appeals_score_id` foreign key (`score_id`) references `scores` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_score_appeals_student_id` ON `score_appeals` (`student_id`);

CREATE TABLE IF NOT EXISTS `fail_records` (
    `id`            bigint auto_increment primary key,
    `student_id`    bigint                             not null comment '学生ID',
    `course_id`     bigint                             null comment '课程ID',
    `course_name`   varchar(100)                       null comment '课程名称',
    `exam_time`     datetime                           null comment '考试时间',
    `score`         decimal(5, 2)                      null comment '成绩',
    `fail_type`     varchar(50)                        null comment '挂科类型：fail-不及格, absent-缺考, cheat-作弊',
    `semester`      varchar(20)                        null comment '学期',
    `academic_year` varchar(20)                        null comment '学年',
    `status`        int      default 0                 null comment '状态：0-挂科, 1-补考通过, 2-重修通过',
    `created_at`    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at`    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
) comment '挂科记录表';
CREATE INDEX IF NOT EXISTS `idx_fail_records_student_id` ON `fail_records` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_fail_records_course_id` ON `fail_records` (`course_id`);
CREATE INDEX IF NOT EXISTS `idx_fail_records_status` ON `fail_records` (`status`);

-- ==================== 预警相关 ====================

CREATE TABLE IF NOT EXISTS `academic_warning` (
    `id`              bigint auto_increment primary key,
    `student_id`      bigint                                not null,
    `student_name`    varchar(100)                          null,
    `rule_id`         bigint                                null,
    `type`            int                                   null,
    `title`           varchar(100)                          null,
    `description`     text                                  null,
    `warning_level`   int                                   not null,
    `warning_message` text                                  null,
    `trigger_score`   decimal(5, 2)                         null,
    `gpa_before`      decimal(5, 2)                         null,
    `gpa_after`       decimal(5, 2)                         null,
    `status`          int         default 0                 null,
    `appeal_status`   varchar(20) default 'none'            null,
    `handled_by`      varchar(100)                          null,
    `handle_result`   text                                  null,
    `handled_at`      timestamp                             null,
    `created_by`      varchar(100)                          null,
    `created_at`      timestamp   default CURRENT_TIMESTAMP null,
    `updated_by`      varchar(100)                          null,
    `updated_at`      timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_academic_warning_student_id` foreign key (`student_id`) references `student_profile` (`id`),
    constraint `fk_academic_warning_rule_id` foreign key (`rule_id`) references `warning_rule` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_academic_warning_student_id` ON `academic_warning` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_academic_warning_rule_id` ON `academic_warning` (`rule_id`);

CREATE TABLE IF NOT EXISTS `academic_warnings` (
    `id`            bigint auto_increment primary key,
    `student_id`    bigint                             not null comment '学生ID',
    `student_name`  varchar(100)                       null comment '学生姓名',
    `warning_level` int      default 1                 null comment '预警等级：1-低, 2-中, 3-高',
    `warning_type`  varchar(50)                        null comment '预警类型',
    `description`   text                               null comment '预警描述',
    `status`        int      default 0                 null comment '状态：0-待处理, 1-已处理, 2-已解除',
    `rule_id`       bigint                             null comment '关联规则ID',
    `created_at`    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at`    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    `processed_by`  bigint                             null comment '处理人ID',
    `process_time`  datetime                           null comment '处理时间',
    `process_note`  text                               null comment '处理备注'
) comment '预警记录表';
CREATE INDEX IF NOT EXISTS `idx_academic_warnings_student_id` ON `academic_warnings` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_academic_warnings_rule_id` ON `academic_warnings` (`rule_id`);
CREATE INDEX IF NOT EXISTS `idx_academic_warnings_status` ON `academic_warnings` (`status`);
CREATE INDEX IF NOT EXISTS `idx_academic_warnings_warning_level` ON `academic_warnings` (`warning_level`);

CREATE TABLE IF NOT EXISTS `warning_release_records` (
    `id`             bigint auto_increment primary key,
    `warning_id`     bigint                             not null comment '关联预警记录ID',
    `student_id`     bigint                             not null comment '学生ID',
    `release_reason` varchar(50)                        null comment '解除原因：makeup_pass-补考通过, retake_pass-重修合格, other-其他',
    `release_note`   text                               null comment '解除备注',
    `release_by`     bigint                             null comment '解除人ID',
    `release_time`   datetime default CURRENT_TIMESTAMP null comment '解除时间'
) comment '预警解除记录表';
CREATE INDEX IF NOT EXISTS `idx_warning_release_records_warning_id` ON `warning_release_records` (`warning_id`);
CREATE INDEX IF NOT EXISTS `idx_warning_release_records_student_id` ON `warning_release_records` (`student_id`);

CREATE TABLE IF NOT EXISTS `warning_tracking_records` (
    `id`               bigint auto_increment primary key,
    `warning_id`       bigint                             not null comment '关联预警记录ID',
    `tracking_time`    datetime default CURRENT_TIMESTAMP null comment '跟踪时间',
    `tracking_content` text                               null comment '跟踪内容',
    `tracking_by`      bigint                             null comment '跟踪人ID',
    `next_follow_time` datetime                           null comment '下次跟进时间'
) comment '预警跟踪记录表';
CREATE INDEX IF NOT EXISTS `idx_warning_tracking_records_warning_id` ON `warning_tracking_records` (`warning_id`);

-- ==================== 帮扶计划 ====================

CREATE TABLE IF NOT EXISTS `assistance_plans` (
    `id`           bigint auto_increment primary key,
    `counselor_id` bigint                              null,
    `student_id`   bigint                              not null,
    `content`      text                                null,
    `status`       int       default 0                 null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_assistance_plans_student_id` foreign key (`student_id`) references `student_profile` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_assistance_plans_student_id` ON `assistance_plans` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_assistance_plans_counselor_id` ON `assistance_plans` (`counselor_id`);

CREATE TABLE IF NOT EXISTS `assistance_evaluations` (
    `id`           bigint auto_increment primary key,
    `plan_id`      bigint                              not null,
    `counselor_id` bigint                              null,
    `content`      text                                null,
    `score`        int                                 null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_assistance_evaluations_plan_id` foreign key (`plan_id`) references `assistance_plans` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_assistance_evaluations_plan_id` ON `assistance_evaluations` (`plan_id`);
CREATE INDEX IF NOT EXISTS `idx_assistance_evaluations_counselor_id` ON `assistance_evaluations` (`counselor_id`);

-- ==================== 消息与通知 ====================

CREATE TABLE IF NOT EXISTS `messages` (
    `id`          bigint auto_increment primary key,
    `sender_id`   bigint                              not null,
    `receiver_id` bigint                              not null,
    `title`       varchar(255)                        null,
    `content`     text                                not null,
    `type`        int       default 1                 not null,
    `status`      int       default 0                 null,
    `created_at`  timestamp default CURRENT_TIMESTAMP null,
    `updated_at`  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    `read_at`     datetime                            null,
    constraint `fk_messages_sender_id` foreign key (`sender_id`) references `users` (`id`),
    constraint `fk_messages_receiver_id` foreign key (`receiver_id`) references `users` (`id`)
);

CREATE TABLE IF NOT EXISTS `notifications` (
    `id`           bigint auto_increment primary key,
    `user_id`      bigint                                not null,
    `title`        varchar(100)                          null,
    `content`      text                                  not null,
    `type`         varchar(50)                           null,
    `warning_id`   bigint                                null,
    `push_channel` varchar(50) default 'app'             null,
    `is_read`      int         default 0                 null,
    `is_deleted`   int         default 0                 null,
    `created_at`   timestamp   default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_notifications_user_id` foreign key (`user_id`) references `users` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_notifications_user_id` ON `notifications` (`user_id`);

CREATE TABLE IF NOT EXISTS `communication_logs` (
    `id`           bigint auto_increment primary key,
    `counselor_id` bigint                              null,
    `teacher_id`   bigint                              null,
    `student_id`   bigint                              null,
    `warning_id`   bigint                              null,
    `content`      text                                null,
    `category`     varchar(50)                         null,
    `status`       int       default 0                 null,
    `reply`        text                                null,
    `created_at`   timestamp default CURRENT_TIMESTAMP null,
    `updated_at`   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS `idx_communication_logs_counselor_id` ON `communication_logs` (`counselor_id`);
CREATE INDEX IF NOT EXISTS `idx_communication_logs_student_id` ON `communication_logs` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_communication_logs_warning_id` ON `communication_logs` (`warning_id`);

CREATE TABLE IF NOT EXISTS `communication_record` (
    `id`           bigint auto_increment primary key,
    `teacher_id`   bigint                             not null,
    `student_id`   bigint                             not null,
    `content`      text                               null,
    `type`         varchar(50)                        null,
    `student_name` varchar(100)                       null,
    `create_time`  datetime default CURRENT_TIMESTAMP null
) comment '沟通记录表';
CREATE INDEX IF NOT EXISTS `idx_communication_record_student_id` ON `communication_record` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_communication_record_teacher_id` ON `communication_record` (`teacher_id`);
CREATE INDEX IF NOT EXISTS `idx_communication_record_type` ON `communication_record` (`type`);

-- ==================== 反馈 ====================

CREATE TABLE IF NOT EXISTS `feedbacks` (
    `id`            bigint auto_increment primary key,
    `teacher_id`    bigint                              not null,
    `student_id`    bigint                              not null,
    `content`       text                                not null,
    `category`      varchar(50)                         null,
    `rating`        int                                 null,
    `status`        varchar(50)                         null,
    `reply_content` text                                null,
    `replied_at`    timestamp                           null,
    `created_at`    timestamp default CURRENT_TIMESTAMP null,
    `updated_at`    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_feedbacks_teacher_id` foreign key (`teacher_id`) references `users` (`id`),
    constraint `fk_feedbacks_student_id` foreign key (`student_id`) references `student_profile` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_feedbacks_teacher_id` ON `feedbacks` (`teacher_id`);
CREATE INDEX IF NOT EXISTS `idx_feedbacks_student_id` ON `feedbacks` (`student_id`);

CREATE TABLE IF NOT EXISTS `feedback` (
    `id`          bigint auto_increment primary key,
    `teacher_id`  bigint                             not null,
    `category`    varchar(50)                        null,
    `content`     text                               null,
    `reply`       text                               null,
    `status`      int      default 0                 null comment '0-未回复, 1-已回复',
    `create_time` datetime default CURRENT_TIMESTAMP null,
    `update_time` datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
) comment '反馈表';
CREATE INDEX IF NOT EXISTS `idx_feedback_teacher_id` ON `feedback` (`teacher_id`);
CREATE INDEX IF NOT EXISTS `idx_feedback_category` ON `feedback` (`category`);
CREATE INDEX IF NOT EXISTS `idx_feedback_status` ON `feedback` (`status`);

-- ==================== 班级管理申请 ====================

CREATE TABLE IF NOT EXISTS `class_management_request` (
    `id`            bigint auto_increment primary key,
    `teacher_id`    bigint                                not null,
    `class_id`      bigint                                not null,
    `counselor_id`  bigint                                null,
    `user_type`     varchar(20)                           null,
    `status`        varchar(20) default 'pending'         null,
    `reason`        varchar(500)                          null,
    `reject_reason` varchar(500)                          null,
    `created_at`    timestamp   default CURRENT_TIMESTAMP null,
    `updated_at`    timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_class_management_request_teacher_class` unique (`teacher_id`, `class_id`),
    constraint `fk_class_management_request_teacher_id` foreign key (`teacher_id`) references `users` (`id`),
    constraint `fk_class_management_request_class_id` foreign key (`class_id`) references `classes` (`id`)
) comment '班级管理申请表';
CREATE INDEX IF NOT EXISTS `idx_class_management_request_class_id` ON `class_management_request` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_class_management_request_status` ON `class_management_request` (`status`);

CREATE TABLE IF NOT EXISTS `teacher_class_application` (
    `id`               bigint auto_increment primary key,
    `teacher_id`       bigint                             not null,
    `class_id`         bigint                             not null,
    `reason`           text                               null,
    `status`           int      default 0                 null comment '0-待审核, 1-通过, 2-拒绝',
    `create_time`      datetime default CURRENT_TIMESTAMP null,
    `update_time`      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    `class_name`       varchar(100)                       null,
    `teacher_name`     varchar(100)                       null,
    `teacher_username` varchar(50)                        null,
    `application_type` int      default 0                 null comment '申请类型：0-申请管理，1-解除管理',
    `reject_reason`    varchar(500)                       null comment '拒绝原因'
) comment '教师班级管理申请表';
CREATE INDEX IF NOT EXISTS `idx_teacher_class_application_teacher_id` ON `teacher_class_application` (`teacher_id`);
CREATE INDEX IF NOT EXISTS `idx_teacher_class_application_class_id` ON `teacher_class_application` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_teacher_class_application_status` ON `teacher_class_application` (`status`);
CREATE INDEX IF NOT EXISTS `idx_teacher_class_application_type` ON `teacher_class_application` (`application_type`);

CREATE TABLE IF NOT EXISTS `counselor_class_application` (
    `id`               bigint auto_increment primary key,
    `counselor_id`     bigint                             not null,
    `class_id`         bigint                             not null,
    `reason`           text                               null,
    `status`           int      default 0                 null comment '0-待审核, 1-通过, 2-拒绝',
    `create_time`      datetime default CURRENT_TIMESTAMP null,
    `update_time`      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    `class_name`       varchar(100)                       null,
    `counselor_name`   varchar(100)                       null,
    `application_type` int      default 0                 null comment '申请类型：0-申请管理，1-解除管理'
) comment '辅导员班级管理申请表';
CREATE INDEX IF NOT EXISTS `idx_counselor_class_application_counselor_id` ON `counselor_class_application` (`counselor_id`);
CREATE INDEX IF NOT EXISTS `idx_counselor_class_application_class_id` ON `counselor_class_application` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_counselor_class_application_status` ON `counselor_class_application` (`status`);
CREATE INDEX IF NOT EXISTS `idx_counselor_class_application_type` ON `counselor_class_application` (`application_type`);

CREATE TABLE IF NOT EXISTS `counselor_class_management` (
    `id`           bigint auto_increment primary key,
    `counselor_id` bigint                             not null comment '辅导员ID',
    `class_id`     bigint                             not null comment '班级ID',
    `class_name`   varchar(100)                       null comment '班级名称',
    `status`       int      default 1                 null comment '状态: 0-解除管理, 1-管理中',
    `created_at`   datetime default CURRENT_TIMESTAMP null,
    `updated_at`   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_counselor_class` unique (`counselor_id`, `class_id`)
) comment '辅导员班级管理表';
CREATE INDEX IF NOT EXISTS `idx_counselor_class_management_counselor_id` ON `counselor_class_management` (`counselor_id`);
CREATE INDEX IF NOT EXISTS `idx_counselor_class_management_class_id` ON `counselor_class_management` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_counselor_class_management_status` ON `counselor_class_management` (`status`);

-- ==================== 学生设置与偏好 ====================

CREATE TABLE IF NOT EXISTS `student_settings` (
    `id`                   bigint auto_increment primary key,
    `student_id`           bigint                                not null,
    `notification_enabled` tinyint(1)  default 1                 null,
    `email_notifications`  tinyint(1)  default 1                 null,
    `theme`                varchar(50) default 'light'           null,
    `created_at`           timestamp   default CURRENT_TIMESTAMP null,
    `updated_at`           timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `fk_student_settings_student_id` foreign key (`student_id`) references `student_profile` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_student_settings_student_id` ON `student_settings` (`student_id`);

CREATE TABLE IF NOT EXISTS `subscription_preferences` (
    `id`                   bigint auto_increment primary key,
    `student_id`           bigint                               not null,
    `subscribe_warnings`   tinyint(1) default 1                 null,
    `subscribe_low`        tinyint(1) default 0                 null,
    `subscribe_medium`     tinyint(1) default 1                 null,
    `subscribe_high`       tinyint(1) default 1                 null,
    `subscribe_assistance` tinyint(1) default 1                 null,
    `subscribe_system`     tinyint(1) default 1                 null,
    `push_email`           tinyint(1) default 1                 null,
    `push_sms`             tinyint(1) default 0                 null,
    `push_app`             tinyint(1) default 1                 null,
    `created_at`           timestamp  default CURRENT_TIMESTAMP null,
    `updated_at`           timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_subscription_preferences_student_id` unique (`student_id`),
    constraint `fk_subscription_preferences_student_id` foreign key (`student_id`) references `student_profile` (`id`)
);

-- ==================== 基准分析 ====================

CREATE TABLE IF NOT EXISTS `benchmark_analysis` (
    `id`               bigint auto_increment primary key,
    `student_id`       bigint                              not null,
    `class_id`         bigint                              null,
    `major_id`         bigint                              null,
    `semester`         varchar(50)                         null,
    `student_gpa`      decimal(5, 3)                       null,
    `class_avg_gpa`    decimal(5, 3)                       null,
    `major_avg_gpa`    decimal(5, 3)                       null,
    `class_rank`       int                                 null,
    `class_total`      int                                 null,
    `major_rank`       int                                 null,
    `major_total`      int                                 null,
    `courses_passed`   int       default 0                 null,
    `courses_failed`   int       default 0                 null,
    `required_credits` decimal(5, 2)                       null,
    `credits_on_track` int       default 1                 null,
    `created_at`       timestamp default CURRENT_TIMESTAMP null,
    `updated_at`       timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint `uk_benchmark_analysis_student_semester` unique (`student_id`, `semester`),
    constraint `fk_benchmark_analysis_student_id` foreign key (`student_id`) references `student_profile` (`id`)
);
CREATE INDEX IF NOT EXISTS `idx_benchmark_analysis_student_id` ON `benchmark_analysis` (`student_id`);
CREATE INDEX IF NOT EXISTS `idx_benchmark_analysis_class_id` ON `benchmark_analysis` (`class_id`);
CREATE INDEX IF NOT EXISTS `idx_benchmark_analysis_major_id` ON `benchmark_analysis` (`major_id`);
