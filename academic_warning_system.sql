-- 旧表 academic_warnings 已废弃，统一使用 academic_warning 表（@TableName("academic_warning")），见 AcademicWarning 实体



create table colleges
(
    id            bigint auto_increment
        primary key,
    name          varchar(100)                        not null,
    code          varchar(50)                         null,
    description   varchar(500)                        null,
    status        int       default 1                 null,
    student_count int       default 0                 null,
    teacher_count int       default 0                 null,
    created_at    timestamp default CURRENT_TIMESTAMP null,
    updated_at    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_colleges_name
        unique (name)
);

create table classes
(
    id           bigint auto_increment
        primary key,
    name         varchar(50)                         not null,
    college_id   bigint                              not null,
    major_id     bigint                              null,
    teacher_id   bigint                              null,
    counselor_id bigint                              null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    updated_at   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_classes_college_id
        foreign key (college_id) references colleges (id)
);

create index idx_classes_college_id
    on classes (college_id);

create table communication_logs
(
    id           bigint auto_increment
        primary key,
    counselor_id bigint                              null,
    teacher_id   bigint                              null,
    student_id   bigint                              null,
    warning_id   bigint                              null,
    content      text                                null,
    category     varchar(50)                         null,
    status       int       default 0                 null,
    reply        text                                null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    updated_at   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);

create index idx_communication_logs_counselor_id
    on communication_logs (counselor_id);

create index idx_communication_logs_student_id
    on communication_logs (student_id);

create index idx_communication_logs_warning_id
    on communication_logs (warning_id);

create table communication_record
(
    id           bigint auto_increment
        primary key,
    teacher_id   bigint                             not null,
    student_id   bigint                             not null,
    content      text                               null,
    type         varchar(50)                        null,
    student_name varchar(100)                       null,
    create_time  datetime default CURRENT_TIMESTAMP null
)
    comment '沟通记录表';

create index idx_student_id
    on communication_record (student_id);

create index idx_teacher_id
    on communication_record (teacher_id);

create index idx_type
    on communication_record (type);

create table counselor_class_application
(
    id               bigint auto_increment
        primary key,
    counselor_id     bigint                             not null,
    class_id         bigint                             not null,
    reason           text                               null,
    status           int      default 0                 null comment '0-待审核, 1-通过, 2-拒绝',
    create_time      datetime default CURRENT_TIMESTAMP null,
    update_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    class_name       varchar(100)                       null,
    counselor_name   varchar(100)                       null,
    application_type int      default 0                 null comment '申请类型：0-申请管理，1-解除管理'
)
    comment '辅导员班级管理申请表';

create index idx_application_type
    on counselor_class_application (application_type);

create index idx_class_id
    on counselor_class_application (class_id);

create index idx_counselor_id
    on counselor_class_application (counselor_id);

create index idx_status
    on counselor_class_application (status);

create table counselor_class_management
(
    id           bigint auto_increment
        primary key,
    counselor_id bigint                             not null comment '辅导员ID',
    class_id     bigint                             not null comment '班级ID',
    class_name   varchar(100)                       null comment '班级名称',
    status       int      default 1                 null comment '状态: 0-解除管理, 1-管理中',
    created_at   datetime default CURRENT_TIMESTAMP null,
    updated_at   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_counselor_class
        unique (counselor_id, class_id)
)
    comment '辅导员班级管理表';

create index idx_class_id
    on counselor_class_management (class_id);

create index idx_counselor_id
    on counselor_class_management (counselor_id);

create index idx_status
    on counselor_class_management (status);

create table counselor_profiles
(
    id              bigint auto_increment
        primary key,
    user_id         bigint                             not null comment '用户ID',
    college_id      bigint                             null comment '学院ID',
    counselor_id    varchar(50)                        null comment '辅导员编号',
    name            varchar(100)                       null comment '姓名',
    gender          varchar(10)                        null comment '性别',
    phone           varchar(20)                        null comment '联系电话',
    email           varchar(100)                       null comment '邮箱',
    department      varchar(100)                       null comment '部门',
    title           varchar(50)                        null comment '职称',
    education       varchar(50)                        null comment '学历',
    introduction    text                               null comment '个人简介',
    status          int      default 1                 null comment '状态：0-禁用，1-启用',
    created_at      datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    college_name    varchar(100)                       null comment '学院名称',
    staff_id        varchar(50)                        null comment '员工编号',
    office_location varchar(200)                       null comment '办公地点',
    duty_phone      varchar(20)                        null comment '值班电话',
    class_count     int      default 0                 null comment '管理班级数',
    student_count   int      default 0                 null comment '管理学生数',
    constraint user_id
        unique (user_id)
)
    comment '辅导员信息表';

create index idx_college_id
    on counselor_profiles (college_id);

create index idx_status
    on counselor_profiles (status);

create index idx_user_id
    on counselor_profiles (user_id);

create table elective_modules
(
    id          bigint auto_increment
        primary key,
    module_code varchar(20)                         not null,
    module_name varchar(100)                        not null,
    description text                                null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_elective_modules_module_code
        unique (module_code)
)
    comment '选修课模块表';

create table courses
(
    id             bigint auto_increment
        primary key,
    code           varchar(20)                         not null,
    name           varchar(100)                        not null,
    credits        decimal(5, 2)                       not null,
    type           varchar(20)                         not null,
    description    varchar(500)                        null,
    module_id      bigint                              null,
    score_template varchar(50)                         null,
    teacher_id     bigint                              null,
    created_at     timestamp default CURRENT_TIMESTAMP null,
    updated_at     timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_courses_code
        unique (code),
    constraint fk_courses_module_id
        foreign key (module_id) references elective_modules (id)
);

create table course_plans
(
    id           bigint auto_increment
        primary key,
    course_id    bigint                              not null,
    teacher_id   bigint                              null,
    semester     varchar(20)                         null,
    year         int                                 null,
    start_date   date                                null,
    end_date     date                                null,
    class_time   varchar(100)                        null,
    location     varchar(200)                        null,
    max_students int       default 50                null,
    status       int       default 1                 null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    updated_at   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_course_plans_course_id
        foreign key (course_id) references courses (id)
);

create index idx_course_plans_course_id
    on course_plans (course_id);

create index idx_course_plans_semester
    on course_plans (semester);

create index idx_courses_code
    on courses (code);

create index idx_courses_module_id
    on courses (module_id);

create table export_history
(
    id           bigint auto_increment
        primary key,
    data_type    varchar(50)                         not null,
    file_name    varchar(255)                        not null,
    record_count int       default 0                 null,
    exported_by  bigint                              null,
    created_at   timestamp default CURRENT_TIMESTAMP null
);

create index idx_export_history_created_at
    on export_history (created_at);

create index idx_export_history_data_type
    on export_history (data_type);

create table fail_records
(
    id            bigint auto_increment
        primary key,
    student_id    bigint                             not null comment '学生ID',
    course_id     bigint                             null comment '课程ID',
    course_name   varchar(100)                       null comment '课程名称',
    exam_time     datetime                           null comment '考试时间',
    score         decimal(5, 2)                      null comment '成绩',
    fail_type     varchar(50)                        null comment '挂科类型：fail-不及格, absent-缺考, cheat-作弊',
    semester      varchar(20)                        null comment '学期',
    academic_year varchar(20)                        null comment '学年',
    status        int      default 0                 null comment '状态：0-挂科, 1-补考通过, 2-重修通过',
    created_at    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '挂科记录表';

create index idx_course_id
    on fail_records (course_id);

create index idx_status
    on fail_records (status);

create index idx_student_id
    on fail_records (student_id);

-- 旧表 feedback 已废弃，统一使用 feedbacks 表（@TableName("feedbacks")），见 Feedback 实体



create table majors
(
    id          bigint auto_increment
        primary key,
    college_id  bigint                              not null,
    name        varchar(100)                        not null,
    code        varchar(50)                         not null,
    short_name  varchar(20)                         null comment '专业简称',
    description varchar(500)                        null,
    status      int       default 1                 null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_majors_code
        unique (code),
    constraint fk_majors_college_id
        foreign key (college_id) references colleges (id)
);

create index idx_majors_college_id
    on majors (college_id);

create table permissions
(
    id          bigint auto_increment
        primary key,
    name        varchar(100)                        not null,
    code        varchar(100)                        not null,
    description varchar(500)                        null,
    status      int       default 1                 null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_permissions_code
        unique (code)
);

create table roles
(
    id          bigint auto_increment
        primary key,
    name        varchar(50)                         not null,
    code        varchar(50)                         not null,
    description varchar(255)                        null,
    status      int       default 1                 null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_roles_code
        unique (code)
);

create table role_permissions
(
    id            bigint auto_increment
        primary key,
    role_id       bigint                              not null,
    permission_id bigint                              not null,
    created_at    timestamp default CURRENT_TIMESTAMP null,
    updated_at    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_role_permissions_role_permission
        unique (role_id, permission_id),
    constraint fk_role_permissions_permission_id
        foreign key (permission_id) references permissions (id),
    constraint fk_role_permissions_role_id
        foreign key (role_id) references roles (id)
);

create index idx_role_permissions_permission_id
    on role_permissions (permission_id);

create index idx_role_permissions_role_id
    on role_permissions (role_id);

create table score_record
(
    id           bigint auto_increment
        primary key,
    course_id    bigint                             not null,
    student_id   bigint                             not null,
    teacher_id   bigint                             not null,
    student_name varchar(100)                       null,
    class_name   varchar(100)                       null,
    score        decimal(5, 2)                      null,
    score_level  int                                null,
    remark       varchar(500)                       null,
    semester     varchar(50)                        null comment '学期',
    year         int                                null comment '学年',
    regular_score decimal(5,2)                      null comment '平时成绩',
    final_score   decimal(5,2)                      null comment '期末成绩',
    score_total   decimal(5,2)                      null comment '总评成绩',
    grade         varchar(20)                       null comment '等级',
    grade_point   decimal(3,2)                      null comment '绩点',
    reason_for_change varchar(500)                  null comment '修改原因',
    modified_by   bigint                            null comment '修改人',
    created_at   datetime default CURRENT_TIMESTAMP null,
    updated_at   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '成绩记录表';

create index idx_course_id
    on score_record (course_id);

create index idx_student_id
    on score_record (student_id);

create index idx_teacher_id
    on score_record (teacher_id);

-- 旧表 student 已废弃，统一使用 student_profile 表
-- create table student (...) 已删除

create table support_resource
(
    id          bigint auto_increment
        primary key,
    title       varchar(200)                        not null,
    description text                                null,
    type        varchar(50)                         null,
    category    varchar(50)                         null,
    content     text                                null,
    url         varchar(500)                        null,
    file_path   varchar(500)                        null,
    file_size   bigint                              null,
    file_type   varchar(50)                         null,
    author      varchar(100)                        null,
    views       int       default 0                 null,
    downloads   int       default 0                 null,
    status      int       default 1                 null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);

create index idx_support_resource_category
    on support_resource (category);

create index idx_support_resource_status
    on support_resource (status);

create index idx_support_resource_type
    on support_resource (type);

create table teacher_class_application
(
    id               bigint auto_increment
        primary key,
    teacher_id       bigint                             not null,
    class_id         bigint                             not null,
    reason           text                               null,
    status           int      default 0                 null comment '0-待审核, 1-通过, 2-拒绝',
    create_time      datetime default CURRENT_TIMESTAMP null,
    update_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    class_name       varchar(100)                       null,
    teacher_name     varchar(100)                       null,
    teacher_username varchar(50)                        null,
    application_type int      default 0                 null comment '申请类型：0-申请管理，1-解除管理',
    reject_reason    varchar(500)                       null comment '拒绝原因'
)
    comment '教师班级管理申请表';

create index idx_application_type
    on teacher_class_application (application_type);

create index idx_class_id
    on teacher_class_application (class_id);

create index idx_status
    on teacher_class_application (status);

create index idx_teacher_id
    on teacher_class_application (teacher_id);

create table teaching_resources
(
    id          bigint auto_increment
        primary key,
    course_id   bigint                              null,
    title       varchar(200)                        not null,
    description text                                null,
    type        varchar(50)                         null,
    url         varchar(500)                        null,
    file_path   varchar(500)                        null,
    file_size   bigint                              null,
    file_type   varchar(50)                         null,
    downloads   int       default 0                 null,
    status      int       default 1                 null,
    created_by  bigint                              null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_by  bigint                              null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_teaching_resources_course_id
        foreign key (course_id) references courses (id)
);

create index idx_teaching_resources_course_id
    on teaching_resources (course_id);

create index idx_teaching_resources_uploader_id
    on teaching_resources (created_by);

create table users
(
    id         bigint auto_increment
        primary key,
    username   varchar(50)                          not null,
    password   varchar(255)                         not null,
    name       varchar(100)                         null,
    email      varchar(100)                         null,
    phone      varchar(20)                          null,
    role       tinyint(1)                           not null comment '1-学生, 2-教师, 3-辅导员, 4-管理员',
    status     tinyint(1) default 1                 null,
    created_at timestamp  default CURRENT_TIMESTAMP null,
    updated_at timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted_at timestamp                            null,
    constraint uk_users_username
        unique (username)
);

create table admin_profile
(
    id         bigint auto_increment
        primary key,
    user_id    bigint                              not null,
    name       varchar(100)                        null,
    phone      varchar(20)                         null,
    email      varchar(100)                        null,
    department varchar(100)                        null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_admin_profile_user_id
        unique (user_id),
    constraint fk_admin_profile_user_id
        foreign key (user_id) references users (id)
);

create table audit_logs
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                              null,
    action_type varchar(50)                         null,
    entity_type varchar(50)                         null,
    entity_id   bigint                              null,
    details     text                                null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    constraint fk_audit_logs_user_id
        foreign key (user_id) references users (id)
);

create index idx_audit_logs_action_type
    on audit_logs (action_type);

create index idx_audit_logs_user_id
    on audit_logs (user_id);

-- 旧表 class_management_request 已废弃，已被 teacher_class_application 替代，无代码引用



create table messages
(
    id          bigint auto_increment
        primary key,
    sender_id   bigint                              not null,
    receiver_id bigint                              not null,
    title       varchar(255)                        null,
    content     text                                not null,
    type        int       default 1                 not null,
    status      int       default 0                 null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    read_at     datetime                            null,
    constraint fk_messages_receiver_id
        foreign key (receiver_id) references users (id)
            on delete cascade,
    constraint fk_messages_sender_id
        foreign key (sender_id) references users (id)
            on delete cascade
);

create table notifications
(
    id           bigint auto_increment
        primary key,
    user_id      bigint                                not null,
    title        varchar(100)                          null,
    content      text                                  not null,
    type         varchar(50)                           null,
    warning_id   bigint                                null,
    push_channel varchar(50) default 'app'             null,
    is_read      int         default 0                 null,
    is_deleted   int         default 0                 null,
    created_at   timestamp   default CURRENT_TIMESTAMP null,
    updated_at   timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_notifications_user_id
        foreign key (user_id) references users (id)
);

create index idx_notifications_user_id
    on notifications (user_id);

create table security_logs
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                               not null,
    ip_address    varchar(50)                          null,
    login_time    timestamp                            null,
    is_successful tinyint(1) default 1                 null,
    action        varchar(100)                         null,
    created_at    timestamp  default CURRENT_TIMESTAMP null,
    constraint fk_security_logs_user_id
        foreign key (user_id) references users (id)
);

create index idx_security_logs_user_id
    on security_logs (user_id);

create table student_profile
(
    id              bigint auto_increment
        primary key,
    user_id         bigint                               not null,
    student_id      varchar(20)                          not null comment '学号',
    name            varchar(50)                          not null,
    gender          varchar(10)                           null comment '性别',
    college_id      bigint                               null,
    major_id        bigint                               null,
    class_id        bigint                               null,
    class_name      varchar(255)                         null,
    enrollment_year int                                   null comment '入学年份',
    privacy_level   tinyint(1) default 2                 null,
    created_at      timestamp  default CURRENT_TIMESTAMP null,
    updated_at      timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_student_profile_student_id
        unique (student_id),
    constraint uk_student_profile_user_id
        unique (user_id),
    constraint fk_student_profile_class_id
        foreign key (class_id) references classes (id),
    constraint fk_student_profile_college_id
        foreign key (college_id) references colleges (id),
    constraint fk_student_profile_major_id
        foreign key (major_id) references majors (id),
    constraint fk_student_profile_user_id
        foreign key (user_id) references users (id)
);

create table assistance_plans
(
    id           bigint auto_increment
        primary key,
    counselor_id bigint                              null,
    student_id   bigint                              not null,
    content      text                                null,
    status       int       default 0                 null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    updated_at   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_assistance_plans_student_id
        foreign key (student_id) references student_profile (id)
);

create table assistance_evaluations
(
    id           bigint auto_increment
        primary key,
    plan_id      bigint                              not null,
    counselor_id bigint                              null,
    content      text                                null,
    score        int                                 null,
    created_at   timestamp default CURRENT_TIMESTAMP null,
    updated_at   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_assistance_evaluations_plan_id
        foreign key (plan_id) references assistance_plans (id)
);

create index idx_assistance_evaluations_counselor_id
    on assistance_evaluations (counselor_id);

create index idx_assistance_evaluations_plan_id
    on assistance_evaluations (plan_id);

create index idx_assistance_plans_counselor_id
    on assistance_plans (counselor_id);

create index idx_assistance_plans_student_id
    on assistance_plans (student_id);

create table benchmark_analysis
(
    id               bigint auto_increment
        primary key,
    student_id       bigint                              not null,
    class_id         bigint                              null,
    major_id         bigint                              null,
    semester         varchar(50)                         null,
    student_gpa      decimal(5, 3)                       null,
    class_avg_gpa    decimal(5, 3)                       null,
    major_avg_gpa    decimal(5, 3)                       null,
    class_rank       int                                 null,
    class_total      int                                 null,
    major_rank       int                                 null,
    major_total      int                                 null,
    courses_passed   int       default 0                 null,
    courses_failed   int       default 0                 null,
    required_credits decimal(5, 2)                       null,
    credits_on_track int       default 1                 null,
    created_at       timestamp default CURRENT_TIMESTAMP null,
    updated_at       timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_benchmark_analysis_student_semester
        unique (student_id, semester),
    constraint fk_benchmark_analysis_student_id
        foreign key (student_id) references student_profile (id)
);

create index idx_benchmark_analysis_class_id
    on benchmark_analysis (class_id);

create index idx_benchmark_analysis_major_id
    on benchmark_analysis (major_id);

create index idx_benchmark_analysis_student_id
    on benchmark_analysis (student_id);

create table enrollments
(
    id         bigint auto_increment
        primary key,
    student_id bigint                              not null,
    course_id  bigint                              not null,
    class_id   bigint                              null,
    status     int       default 0                 null,
    semester   varchar(20)                          null,
    year       int                                 null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_enrollments_class_id
        foreign key (class_id) references classes (id),
    constraint fk_enrollments_course_id
        foreign key (course_id) references courses (id),
    constraint fk_enrollments_student_id
        foreign key (student_id) references student_profile (id)
);

create index idx_enrollments_course_id
    on enrollments (course_id);

create index idx_enrollments_student_id
    on enrollments (student_id);

create table feedbacks
(
    id            bigint auto_increment
        primary key,
    teacher_id    bigint                              not null,
    student_id    bigint                              not null,
    content       text                                not null,
    category      varchar(50)                         null,
    rating        int                                 null,
    status        varchar(50)                         null,
    reply_content text                                null,
    replied_at    timestamp                           null,
    created_at    timestamp default CURRENT_TIMESTAMP null,
    updated_at    timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_feedbacks_student_id
        foreign key (student_id) references student_profile (id),
    constraint fk_feedbacks_teacher_id
        foreign key (teacher_id) references users (id)
);

create index idx_feedbacks_student_id
    on feedbacks (student_id);

create index idx_feedbacks_teacher_id
    on feedbacks (teacher_id);

create table scores
(
    id                bigint auto_increment
        primary key,
    student_id        bigint                              not null,
    course_id         bigint                              not null,
    semester          varchar(20)                         not null,
    year              int                                 null,
    regular_score     decimal(5, 2)                       null,
    final_score       decimal(5, 2)                       null,
    score_total       decimal(5, 2)                       not null,
    grade             varchar(20)                         null,
    grade_point       decimal(5, 2)                       null,
    reason_for_change varchar(500)                        null,
    modified_by       bigint                              null,
    created_at        timestamp default CURRENT_TIMESTAMP null,
    updated_at        timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_scores_course_id
        foreign key (course_id) references courses (id),
    constraint fk_scores_student_id
        foreign key (student_id) references student_profile (id)
);

create table score_appeals
(
    id          bigint auto_increment
        primary key,
    warning_id  bigint                              null,
    student_id  bigint                              not null,
    score_id    bigint                              null,
    course_id   bigint                              null,
    reason      text                                null,
    attachments varchar(500)                        null,
    status      int       default 0                 null,
    reply       text                                null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_score_appeals_score_id
        foreign key (score_id) references scores (id),
    constraint fk_score_appeals_student_id
        foreign key (student_id) references student_profile (id)
);

create index idx_score_appeals_student_id
    on score_appeals (student_id);

create index idx_scores_course_id
    on scores (course_id);

create index idx_scores_semester
    on scores (semester);

create index idx_scores_student_id
    on scores (student_id);

create index idx_scores_student_id_semester
    on scores (student_id, semester);

create index idx_student_profile_class_id
    on student_profile (class_id);

create index idx_student_profile_college_id
    on student_profile (college_id);

create index idx_student_profile_major_id
    on student_profile (major_id);

create table student_settings
(
    id                   bigint auto_increment
        primary key,
    student_id           bigint                                not null,
    notification_enabled tinyint(1)  default 1                 null,
    email_notifications  tinyint(1)  default 1                 null,
    theme                varchar(50) default 'light'           null,
    created_at           timestamp   default CURRENT_TIMESTAMP null,
    updated_at           timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_student_settings_student_id
        foreign key (student_id) references student_profile (id)
);

create index idx_student_settings_student_id
    on student_settings (student_id);

create table subscription_preferences
(
    id                   bigint auto_increment
        primary key,
    student_id           bigint                               not null,
    subscribe_warnings   tinyint(1) default 1                 null,
    subscribe_low        tinyint(1) default 0                 null,
    subscribe_medium     tinyint(1) default 1                 null,
    subscribe_high       tinyint(1) default 1                 null,
    subscribe_assistance tinyint(1) default 1                 null,
    subscribe_system     tinyint(1) default 1                 null,
    push_email           tinyint(1) default 1                 null,
    push_sms             tinyint(1) default 0                 null,
    push_app             tinyint(1) default 1                 null,
    created_at           timestamp  default CURRENT_TIMESTAMP null,
    updated_at           timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_subscription_preferences_student_id
        unique (student_id),
    constraint fk_subscription_preferences_student_id
        foreign key (student_id) references student_profile (id)
);

create table teacher_profile
(
    id             bigint auto_increment
        primary key,
    user_id        bigint                              not null,
    name           varchar(50)                         null,
    college_id     bigint                              not null,
    title          varchar(50)                         null,
    research_areas varchar(200)                        null,
    created_at     timestamp default CURRENT_TIMESTAMP null,
    updated_at     timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_teacher_profile_user_id
        unique (user_id),
    constraint fk_teacher_profile_college_id
        foreign key (college_id) references colleges (id),
    constraint fk_teacher_profile_user_id
        foreign key (user_id) references users (id)
);

create table teacher_course
(
    id         bigint auto_increment
        primary key,
    teacher_id bigint                              not null,
    course_id  bigint                              not null,
    semester   int                                 null,
    year       int                                 null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_teacher_course
        unique (teacher_id, course_id),
    constraint fk_teacher_course_course_id
        foreign key (course_id) references courses (id),
    constraint fk_teacher_course_teacher_id
        foreign key (teacher_id) references teacher_profile (id)
);

create index idx_teacher_course_course_id
    on teacher_course (course_id);

create index idx_teacher_course_teacher_id
    on teacher_course (teacher_id);

-- ----------------------------
-- Table structure for score_audit_log (成绩修改审计日志)
-- ----------------------------
create table score_audit_log
(
    id           bigint auto_increment primary key,
    student_id   bigint       not null,
    student_name varchar(100) null,
    course_id    bigint       null,
    course_name  varchar(100) null,
    teacher_id   bigint       not null,
    old_score    decimal(5, 2) null,
    new_score    decimal(5, 2) null,
    difference   decimal(5, 2) generated always as (`new_score` - `old_score`) stored,
    reason       varchar(500) null comment '修改原因',
    modified_by  varchar(100) null comment '操作人',
    modified_at  timestamp default CURRENT_TIMESTAMP null
);

-- 审计日志示例数据
INSERT INTO score_audit_log (student_id, student_name, course_id, course_name, teacher_id, old_score, new_score, reason, modified_by) VALUES
(3, '司明', 1, '高等数学', 29, 55.0, 65.0, '试卷复核发现加错分', '于凤海'),
(1, '张浩', 4, '数据结构', 29, 72.0, 78.0, '平时分漏记', '于凤海'),
(2, '李娜', 1, '高等数学', 29, 88.0, 92.0, '期末卷面复核', '于凤海');

create index idx_teacher_profile_college_id
    on teacher_profile (college_id);

create index idx_users_role
    on users (role);

create index idx_users_status
    on users (status);

create table warning_release_records
(
    id             bigint auto_increment
        primary key,
    warning_id     bigint                             not null comment '关联预警记录ID',
    student_id     bigint                             not null comment '学生ID',
    release_reason varchar(50)                        null comment '解除原因：makeup_pass-补考通过, retake_pass-重修合格, other-其他',
    release_note   text                               null comment '解除备注',
    release_by     bigint                             null comment '解除人ID',
    release_time   datetime default CURRENT_TIMESTAMP null comment '解除时间'
)
    comment '预警解除记录表';

create index idx_student_id
    on warning_release_records (student_id);

create index idx_warning_id
    on warning_release_records (warning_id);

-- 旧表 warning_rule 已废弃，统一使用 warning_rules 表
-- create table warning_rule (...) 已删除

create table academic_warning
(
    id              bigint auto_increment
        primary key,
    student_id      bigint                                not null,
    student_name    varchar(100)                          null,
    rule_id         bigint                                null,
    type            int                                   null,
    title           varchar(100)                          null,
    description     text                                  null,
    warning_level   int                                   not null,
    warning_message text                                  null,
    trigger_score   decimal(5, 2)                         null,
    gpa_before      decimal(5, 2)                         null,
    gpa_after       decimal(5, 2)                         null,
    status          int         default 0                 null,
    appeal_status   varchar(20) default 'none'            null,
    handled_by      varchar(100)                          null,
    handle_result   text                                  null,
    handled_at      timestamp                             null,
    created_by      varchar(100)                          null,
    created_at      timestamp   default CURRENT_TIMESTAMP null,
    updated_by      varchar(100)                          null,
    updated_at      timestamp   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_academic_warning_student_id
        foreign key (student_id) references student_profile (id)
);

create index idx_academic_warning_rule_id
    on academic_warning (rule_id);

create index idx_academic_warning_student_id
    on academic_warning (student_id);

create index idx_warning_rules_warning_level
    on warning_rules (warning_level);

create table warning_rules
(
    id                        bigint auto_increment
        primary key,
    name                      varchar(100)                       not null comment '规则名称',
    description               text                               null comment '规则描述',
    level                     int      default 1                 null comment '预警等级：1-低, 2-中, 3-高',
    rule_condition            text                               null comment '触发条件表达式',
    status                    int      default 1                 null comment '状态：0-禁用，1-启用',
    created_at                datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at                datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    rule_code                 varchar(50)                        null comment '规则编码',
    created_by                bigint                             null comment '创建人ID',
    updated_by                bigint                             null comment '更新人ID',
    fail_definition           text                               null comment '挂科定义标准',
    statistic_cycle           varchar(50)                        null comment '统计周期',
    trigger_type              varchar(50)                        null comment '触发机制',
    release_condition         text                               null comment '预警解除条件',
    intervention_plan         text                               null comment '学业干预方案',
    notification_flow         text                               null comment '通知流程',
    responsible_person        varchar(100)                       null comment '责任人',
    tracking_mechanism        text                               null comment '跟踪反馈机制',
    archive_spec              text                               null comment '归档规范',
    data_caliber              text                               null comment '数据统计口径',
    department_responsibility text                               null comment '部门职责分工',
    appeal_process            text                               null comment '申诉复议流程',
    exception_handling        text                               null comment '例外情况处理办法'
)
    comment '预警规则表';

create index idx_status
    on warning_rules (status);

create index idx_warning_level
    on warning_rules (level);

create table warning_tracking_records
(
    id               bigint auto_increment
        primary key,
    warning_id       bigint                             not null comment '关联预警记录ID',
    tracking_time    datetime default CURRENT_TIMESTAMP null comment '跟踪时间',
    tracking_content text                               null comment '跟踪内容',
    tracking_by      bigint                             null comment '跟踪人ID',
    next_follow_time datetime                           null comment '下次跟进时间'
)
    comment '预警跟踪记录表';

create index idx_warning_id
    on warning_tracking_records (warning_id);

-- ========================================
-- 补充外键约束（ALTER TABLE 方式，避免表创建顺序依赖）
-- ========================================
ALTER TABLE communication_logs ADD CONSTRAINT fk_communication_logs_counselor_id FOREIGN KEY (counselor_id) REFERENCES counselor_profiles (id);
ALTER TABLE communication_logs ADD CONSTRAINT fk_communication_logs_teacher_id FOREIGN KEY (teacher_id) REFERENCES teacher_profile (id);
ALTER TABLE communication_logs ADD CONSTRAINT fk_communication_logs_student_id FOREIGN KEY (student_id) REFERENCES student_profile (id);
ALTER TABLE communication_logs ADD CONSTRAINT fk_communication_logs_warning_id FOREIGN KEY (warning_id) REFERENCES academic_warning (id);

ALTER TABLE academic_warning ADD CONSTRAINT fk_academic_warning_rule_id FOREIGN KEY (rule_id) REFERENCES warning_rules (id);

ALTER TABLE communication_record ADD CONSTRAINT fk_communication_record_teacher_id FOREIGN KEY (teacher_id) REFERENCES teacher_profile (id);
ALTER TABLE communication_record ADD CONSTRAINT fk_communication_record_student_id FOREIGN KEY (student_id) REFERENCES student_profile (id);

ALTER TABLE counselor_class_application ADD CONSTRAINT fk_counselor_class_application_counselor_id FOREIGN KEY (counselor_id) REFERENCES counselor_profiles (id);
ALTER TABLE counselor_class_application ADD CONSTRAINT fk_counselor_class_application_class_id FOREIGN KEY (class_id) REFERENCES classes (id);

ALTER TABLE counselor_class_management ADD CONSTRAINT fk_counselor_class_management_counselor_id FOREIGN KEY (counselor_id) REFERENCES counselor_profiles (id);
ALTER TABLE counselor_class_management ADD CONSTRAINT fk_counselor_class_management_class_id FOREIGN KEY (class_id) REFERENCES classes (id);

ALTER TABLE counselor_profiles ADD CONSTRAINT fk_counselor_profiles_user_id FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE counselor_profiles ADD CONSTRAINT fk_counselor_profiles_college_id FOREIGN KEY (college_id) REFERENCES colleges (id);

ALTER TABLE fail_records ADD CONSTRAINT fk_fail_records_student_id FOREIGN KEY (student_id) REFERENCES student_profile (id);
ALTER TABLE fail_records ADD CONSTRAINT fk_fail_records_course_id FOREIGN KEY (course_id) REFERENCES courses (id);

ALTER TABLE score_record ADD CONSTRAINT fk_score_record_course_id FOREIGN KEY (course_id) REFERENCES courses (id);
ALTER TABLE score_record ADD CONSTRAINT fk_score_record_student_id FOREIGN KEY (student_id) REFERENCES student_profile (id);
ALTER TABLE score_record ADD CONSTRAINT fk_score_record_teacher_id FOREIGN KEY (teacher_id) REFERENCES teacher_profile (id);

ALTER TABLE teacher_class_application ADD CONSTRAINT fk_teacher_class_application_teacher_id FOREIGN KEY (teacher_id) REFERENCES teacher_profile (id);
ALTER TABLE teacher_class_application ADD CONSTRAINT fk_teacher_class_application_class_id FOREIGN KEY (class_id) REFERENCES classes (id);

ALTER TABLE warning_release_records ADD CONSTRAINT fk_warning_release_records_warning_id FOREIGN KEY (warning_id) REFERENCES academic_warning (id);
ALTER TABLE warning_release_records ADD CONSTRAINT fk_warning_release_records_student_id FOREIGN KEY (student_id) REFERENCES student_profile (id);

ALTER TABLE warning_tracking_records ADD CONSTRAINT fk_warning_tracking_records_warning_id FOREIGN KEY (warning_id) REFERENCES academic_warning (id);

ALTER TABLE benchmark_analysis ADD CONSTRAINT fk_benchmark_analysis_class_id FOREIGN KEY (class_id) REFERENCES classes (id);
ALTER TABLE benchmark_analysis ADD CONSTRAINT fk_benchmark_analysis_major_id FOREIGN KEY (major_id) REFERENCES majors (id);

ALTER TABLE score_appeals ADD CONSTRAINT fk_score_appeals_warning_id FOREIGN KEY (warning_id) REFERENCES academic_warning (id);
ALTER TABLE score_appeals ADD CONSTRAINT fk_score_appeals_course_id FOREIGN KEY (course_id) REFERENCES courses (id);

ALTER TABLE score_audit_log ADD CONSTRAINT fk_score_audit_log_student_id FOREIGN KEY (student_id) REFERENCES student_profile (id);
ALTER TABLE score_audit_log ADD CONSTRAINT fk_score_audit_log_course_id FOREIGN KEY (course_id) REFERENCES courses (id);
ALTER TABLE score_audit_log ADD CONSTRAINT fk_score_audit_log_teacher_id FOREIGN KEY (teacher_id) REFERENCES teacher_profile (id);

ALTER TABLE college_admin_profile ADD CONSTRAINT fk_college_admin_profile_user_id FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE college_admin_profile ADD CONSTRAINT fk_college_admin_profile_college_id FOREIGN KEY (college_id) REFERENCES colleges (id);

-- ========================================
-- 种子数据：INSERT 语句
-- ========================================

-- 选修课模块（真实中文模块名称）
INSERT IGNORE INTO `elective_modules` (`id`, `module_code`, `module_name`, `description`, `created_at`, `updated_at`) VALUES
(1, 'HUM', '人文素养与信息素养模块', '人文科学与信息素养教育课程，包括心理健康、职业生涯规划等', '2025-12-14 08:12:25', '2025-12-14 08:12:25'),
(2, 'ART', '艺术鉴赏与审美教育模块', '艺术欣赏与审美教育课程，包括公共艺术、音乐鉴赏等', '2025-12-14 08:12:25', '2025-12-14 08:12:25'),
(3, 'SCI', '科学技术与信息技术模块', '科学技术与信息技术课程，包括计算机前沿技术、人工智能导论等', '2025-12-14 08:12:25', '2025-12-14 08:12:25'),
(4, 'SOC', '社会科学与人文模块', '社会科学与人文课程，包括中国传统文化、社会学概论等', '2025-12-14 08:12:25', '2025-12-14 08:12:25'),
(5, 'LANG', '语言拓展与沟通模块', '外语与沟通能力拓展课程，包括大学英语拓展、商务英语等', '2025-12-14 08:12:25', '2025-12-14 08:12:25');

-- 课程（6门必修 + 4门选修）
INSERT IGNORE INTO `courses` (`id`, `code`, `name`, `credits`, `type`, `description`, `module_id`, `teacher_id`, `created_at`, `updated_at`) VALUES
(1, 'MATH001', '高等数学', 4.00, '必修', '高等数学基础课程', NULL, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 'ENGL001', '大学英语', 3.00, '必修', '大学英语基础课程', NULL, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 'MATH002', '线性代数', 3.00, '必修', '线性代数基础课程', NULL, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, 'COMP001', '数据结构', 4.00, '必修', '数据结构与算法课程', NULL, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(5, 'COMP002', 'Java程序设计', 3.00, '必修', 'Java程序设计基础', NULL, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(6, 'COMP003', '计算机组成原理', 3.00, '必修', '计算机组成原理课程', NULL, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(7, 'PSYC001', '心理健康教育', 1.00, '选修', '大学生心理健康教育', 1, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(8, 'CARE001', '职业生涯规划', 1.00, '选修', '大学生职业生涯规划', 1, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(9, 'ART001', '公共艺术鉴赏', 1.00, '选修', '公共艺术鉴赏与审美', 2, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(10, 'CULT001', '中国传统文化', 1.00, '选修', '中国传统文化概论', 4, 29, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- 成绩记录（20学生 × 6门必修课 = 120条，用于班级分析）
INSERT IGNORE INTO `score_record` (`course_id`, `student_id`, `teacher_id`, `student_name`, `class_name`, `score`, `score_level`, `remark`, `create_time`, `update_time`) VALUES
-- 邱琳紫 (user_id=30) - 优秀
(1,30,29,'邱琳紫','计科2306班',95.0,1,'思维敏捷，解题能力强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,30,29,'邱琳紫','计科2306班',92.0,1,'口语表达流畅','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,30,29,'邱琳紫','计科2306班',88.0,2,'行列式计算准确','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,30,29,'邱琳紫','计科2306班',90.0,1,'算法掌握扎实','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,30,29,'邱琳紫','计科2306班',93.0,1,'编程能力强，代码规范','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,30,29,'邱琳紫','计科2306班',91.0,1,'理论理解深入','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 宋新鹏 (user_id=31) - 优秀
(1,31,29,'宋新鹏','计科2306班',91.0,1,'学习积极主动','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,31,29,'宋新鹏','计科2306班',89.0,2,'阅读能力较强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,31,29,'宋新鹏','计科2306班',85.0,2,'向量空间理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,31,29,'宋新鹏','计科2306班',92.0,1,'项目完成出色','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,31,29,'宋新鹏','计科2306班',88.0,2,'面向对象设计合理','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,31,29,'宋新鹏','计科2306班',90.0,1,'实验报告完整','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 赵琦 (user_id=32) - 良好
(1,32,29,'赵琦','计科2306班',85.0,2,'基础扎实，解题规范','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,32,29,'赵琦','计科2306班',82.0,2,'听力需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,32,29,'赵琦','计科2306班',78.0,3,'部分概念模糊','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,32,29,'赵琦','计科2306班',84.0,2,'递归理解较好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,32,29,'赵琦','计科2306班',86.0,2,'面向对象掌握良好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,32,29,'赵琦','计科2306班',80.0,2,'需加强实践','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 苏红阳 (user_id=33) - 良好
(1,33,29,'苏红阳','计科2306班',82.0,2,'课堂参与积极','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,33,29,'苏红阳','计科2306班',88.0,2,'写作能力突出','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,33,29,'苏红阳','计科2306班',80.0,2,'推导过程完整','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,33,29,'苏红阳','计科2306班',83.0,2,'树结构掌握较好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,33,29,'苏红阳','计科2306班',85.0,2,'多线程理解深入','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,33,29,'苏红阳','计科2306班',87.0,2,'硬件原理理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 唐浩然 (user_id=34) - 良好
(1,34,29,'唐浩然','计科2306班',88.0,2,'解题思路清晰','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,34,29,'唐浩然','计科2306班',84.0,2,'词汇量丰富','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,34,29,'唐浩然','计科2306班',86.0,2,'矩阵运算熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,34,29,'唐浩然','计科2306班',81.0,2,'图算法待加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,34,29,'唐浩然','计科2306班',82.0,2,'集合框架熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,34,29,'唐浩然','计科2306班',85.0,2,'CPU原理理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王宏民 (user_id=35) - 中等
(1,35,29,'王宏民','计科2306班',75.0,3,'计算能力一般','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,35,29,'王宏民','计科2306班',78.0,3,'语法基础尚可','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,35,29,'王宏民','计科2306班',72.0,3,'向量概念需巩固','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,35,29,'王宏民','计科2306班',76.0,3,'排序算法掌握','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,35,29,'王宏民','计科2306班',74.0,3,'异常处理需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,35,29,'王宏民','计科2306班',77.0,3,'存储器理解一般','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王玉蛟 (user_id=36) - 良好
(1,36,29,'王玉蛟','计科2306班',83.0,2,'学习态度认真','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,36,29,'王玉蛟','计科2306班',85.0,2,'翻译能力较强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,36,29,'王玉蛟','计科2306班',79.0,3,'特征值计算需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,36,29,'王玉蛟','计科2306班',87.0,2,'图论部分表现好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,36,29,'王玉蛟','计科2306班',84.0,2,'GUI编程有创意','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,36,29,'王玉蛟','计科2306班',82.0,2,'IO设备理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 吴雨航 (user_id=37) - 中等
(1,37,29,'吴雨航','计科2306班',72.0,3,'积分计算有困难','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,37,29,'吴雨航','计科2306班',76.0,3,'词汇量不足','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,37,29,'吴雨航','计科2306班',74.0,3,'需要多练习','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,37,29,'吴雨航','计科2306班',71.0,3,'链表操作不熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,37,29,'吴雨航','计科2306班',79.0,3,'基础语法过关','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,37,29,'吴雨航','计科2306班',73.0,3,'指令系统需复习','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 徐瑞瑶 (user_id=38) - 良好
(1,38,29,'徐瑞瑶','计科2306班',87.0,2,'数学思维好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,38,29,'徐瑞瑶','计科2306班',83.0,2,'听力理解优秀','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,38,29,'徐瑞瑶','计科2306班',89.0,2,'线性变换理解深入','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,38,29,'徐瑞瑶','计科2306班',85.0,2,'哈希表掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,38,29,'徐瑞瑶','计科2306班',86.0,2,'设计模式理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,38,29,'徐瑞瑶','计科2306班',84.0,2,'总线设计理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 韩坤 (user_id=39) - 中等偏下
(1,39,29,'韩坤','计科2306班',68.0,3,'基础薄弱需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,39,29,'韩坤','计科2306班',72.0,3,'语法错误较多','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,39,29,'韩坤','计科2306班',70.0,3,'矩阵乘法常出错','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,39,29,'韩坤','计科2306班',65.0,4,'栈和队列混淆','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,39,29,'韩坤','计科2306班',75.0,3,'循环逻辑需巩固','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,39,29,'韩坤','计科2306班',71.0,3,'基本概念模糊','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 张博巍 (user_id=40) - 优秀
(1,40,29,'张博巍','计科2306班',93.0,1,'微积分掌握扎实','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,40,29,'张博巍','计科2306班',90.0,1,'口语发音标准','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,40,29,'张博巍','计科2306班',88.0,2,'二次型理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,40,29,'张博巍','计科2306班',91.0,1,'动态规划掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,40,29,'张博巍','计科2306班',94.0,1,'项目开发能力强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,40,29,'张博巍','计科2306班',89.0,2,'体系结构掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 程启頔 (user_id=41) - 中等
(1,41,29,'程启頔','计科2306班',70.0,3,'上课注意力不集中','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,41,29,'程启頔','计科2306班',74.0,3,'听说能力待提高','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,41,29,'程启頔','计科2306班',68.0,3,'证明题困难','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,41,29,'程启頔','计科2306班',72.0,3,'查找算法需练习','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,41,29,'程启頔','计科2306班',76.0,3,'集合使用基本正确','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,41,29,'程启頔','计科2306班',70.0,3,'汇编基础需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 邓雨轩 (user_id=42) - 良好
(1,42,29,'邓雨轩','计科2306班',84.0,2,'数列极限理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,42,29,'邓雨轩','计科2306班',86.0,2,'作文表达流畅','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,42,29,'邓雨轩','计科2306班',80.0,2,'方程组消元准确','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,42,29,'邓雨轩','计科2306班',82.0,2,'二叉树遍历掌握','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,42,29,'邓雨轩','计科2306班',85.0,2,'接口设计规范','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,42,29,'邓雨轩','计科2306班',83.0,2,'中断处理理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 高菲 (user_id=43) - 良好
(1,43,29,'高菲','计科2306班',86.0,2,'导数应用熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,43,29,'高菲','计科2306班',88.0,2,'阅读理解准确','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,43,29,'高菲','计科2306班',82.0,2,'矩阵对角化掌握','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,43,29,'高菲','计科2306班',84.0,2,'B树B+树理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,43,29,'高菲','计科2306班',87.0,2,'IO流操作熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,43,29,'高菲','计科2306班',81.0,2,'流水线技术理解','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 胡钰 (user_id=44) - 中等
(1,44,29,'胡钰','计科2306班',78.0,3,'不定积分有进步','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,44,29,'胡钰','计科2306班',80.0,2,'语法有进步','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,44,29,'胡钰','计科2306班',72.0,3,'几何意义理解不足','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,44,29,'胡钰','计科2306班',75.0,3,'代码实现有困难','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,44,29,'胡钰','计科2306班',79.0,3,'网络编程需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,44,29,'胡钰','计科2306班',74.0,3,'缓存概念模糊','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 李佳芮 (user_id=45) - 优秀
(1,45,29,'李佳芮','计科2306班',92.0,1,'空间解析几何好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,45,29,'李佳芮','计科2306班',89.0,2,'演讲能力出色','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,45,29,'李佳芮','计科2306班',93.0,1,'秩与线性相关理解深','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,45,29,'李佳芮','计科2306班',90.0,1,'最短路径算法优秀','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,45,29,'李佳芮','计科2306班',91.0,1,'反射机制掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,45,29,'李佳芮','计科2306班',90.0,1,'存储层次理解深','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 李爽 (user_id=46) - 中等偏下
(1,46,29,'李爽','计科2306班',66.0,4,'基础不扎实','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,46,29,'李爽','计科2306班',71.0,3,'词汇积累不够','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,46,29,'李爽','计科2306班',69.0,3,'秩判断困难','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,46,29,'李爽','计科2306班',70.0,3,'遍历算法需复习','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,46,29,'李爽','计科2306班',68.0,3,'继承多态混淆','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,46,29,'李爽','计科2306班',72.0,3,'数值表示理解一般','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 梁莹静 (user_id=47) - 良好
(1,47,29,'梁莹静','计科2306班',85.0,2,'多元函数掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,47,29,'梁莹静','计科2306班',87.0,2,'跨文化交际能力好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,47,29,'梁莹静','计科2306班',81.0,2,'线性空间概念清晰','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,47,29,'梁莹静','计科2306班',83.0,2,'堆排序理解好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,47,29,'梁莹静','计科2306班',86.0,2,'泛型使用规范','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,47,29,'梁莹静','计科2306班',84.0,2,'指令流水掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 孟阳 (user_id=48) - 中等
(1,48,29,'孟阳','计科2306班',73.0,3,'函数连续性需加强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,48,29,'孟阳','计科2306班',75.0,3,'长难句理解困难','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,48,29,'孟阳','计科2306班',71.0,3,'基变换理解不足','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,48,29,'孟阳','计科2306班',77.0,3,'快速排序基本掌握','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,48,29,'孟阳','计科2306班',74.0,3,'多线程有进步','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,48,29,'孟阳','计科2306班',76.0,3,'微程序控制器理解','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王瑾泰 (user_id=49) - 良好
(1,49,29,'王瑾泰','计科2306班',82.0,2,'定积分应用熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,49,29,'王瑾泰','计科2306班',84.0,2,'完形填空准确','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,49,29,'王瑾泰','计科2306班',78.0,3,'正交变换需巩固','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,49,29,'王瑾泰','计科2306班',85.0,2,'AVL树掌握好','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,49,29,'王瑾泰','计科2306班',83.0,2,'JDBC操作熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,49,29,'王瑾泰','计科2306班',80.0,2,'虚拟内存理解','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王平洋 (user_id=50) - 中等
(1,50,29,'王平洋','计科2306班',74.0,3,'级数概念模糊','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,50,29,'王平洋','计科2306班',73.0,3,'翻译题薄弱','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,50,29,'王平洋','计科2306班',76.0,3,'合同关系理解一般','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,50,29,'王平洋','计科2306班',79.0,3,'并查集基本掌握','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,50,29,'王平洋','计科2306班',71.0,3,'Lambda表达式生疏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,50,29,'王平洋','计科2306班',75.0,3,'DMA方式需复习','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 夏志浩 (user_id=51) - 优秀
(1,51,29,'夏志浩','计科2306班',94.0,1,'数学基础雄厚','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,51,29,'夏志浩','计科2306班',91.0,1,'学术写作优秀','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,51,29,'夏志浩','计科2306班',87.0,2,'抽象思维能力强','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,51,29,'夏志浩','计科2306班',95.0,1,'算法竞赛获奖','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,51,29,'夏志浩','计科2306班',92.0,1,'精通JVM调优','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,51,29,'夏志浩','计科2306班',88.0,2,'多核处理器理解深','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 张健 (user_id=52) - 中等偏下
(1,52,29,'张健','计科2306班',64.0,4,'多处知识漏洞','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(2,52,29,'张健','计科2306班',69.0,3,'听力严重薄弱','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(3,52,29,'张健','计科2306班',62.0,4,'高斯消元不熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,52,29,'张健','计科2306班',68.0,3,'基本操作不熟练','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,52,29,'张健','计科2306班',65.0,4,'面向对象理解不足','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,52,29,'张健','计科2306班',71.0,3,'运算器概念模糊','2026-05-27 10:00:00','2026-05-27 10:00:00');

-- 选修课成绩记录（20学生 × 4门选修课 = 80条，用于学分预测）
INSERT IGNORE INTO `score_record` (`course_id`, `student_id`, `teacher_id`, `student_name`, `class_name`, `score`, `score_level`, `remark`, `create_time`, `update_time`) VALUES
-- 心理健康教育 (course_id=7, 1学分)
(7,30,29,'邱琳紫','计科2306班',90.0,1,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,31,29,'宋新鹏','计科2306班',88.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,32,29,'赵琦','计科2306班',85.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,33,29,'苏红阳','计科2306班',92.0,1,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,34,29,'唐浩然','计科2306班',80.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,35,29,'王宏民','计科2306班',78.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,36,29,'王玉蛟','计科2306班',86.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,37,29,'吴雨航','计科2306班',75.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,38,29,'徐瑞瑶','计科2306班',88.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,39,29,'韩坤','计科2306班',72.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,40,29,'张博巍','计科2306班',94.0,1,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,41,29,'程启頔','计科2306班',70.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,42,29,'邓雨轩','计科2306班',83.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,43,29,'高菲','计科2306班',89.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,44,29,'胡钰','计科2306班',76.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,45,29,'李佳芮','计科2306班',91.0,1,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,46,29,'李爽','计科2306班',68.0,4,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,47,29,'梁莹静','计科2306班',87.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,48,29,'孟阳','计科2306班',74.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,49,29,'王瑾泰','计科2306班',82.0,2,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,50,29,'王平洋','计科2306班',73.0,3,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,51,29,'夏志浩','计科2306班',95.0,1,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,52,29,'张健','计科2306班',65.0,4,'心理健康','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 职业生涯规划 (course_id=8, 1学分)
(8,30,29,'邱琳紫','计科2306班',88.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,31,29,'宋新鹏','计科2306班',90.0,1,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,32,29,'赵琦','计科2306班',82.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,33,29,'苏红阳','计科2306班',86.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,34,29,'唐浩然','计科2306班',84.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,35,29,'王宏民','计科2306班',76.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,36,29,'王玉蛟','计科2306班',83.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,37,29,'吴雨航','计科2306班',74.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,38,29,'徐瑞瑶','计科2306班',85.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,39,29,'韩坤','计科2306班',70.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,40,29,'张博巍','计科2306班',92.0,1,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,41,29,'程启頔','计科2306班',68.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,42,29,'邓雨轩','计科2306班',81.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,43,29,'高菲','计科2306班',87.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,44,29,'胡钰','计科2306班',78.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,45,29,'李佳芮','计科2306班',93.0,1,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,46,29,'李爽','计科2306班',66.0,4,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,47,29,'梁莹静','计科2306班',84.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,48,29,'孟阳','计科2306班',72.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,49,29,'王瑾泰','计科2306班',80.0,2,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,50,29,'王平洋','计科2306班',71.0,3,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,51,29,'夏志浩','计科2306班',96.0,1,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,52,29,'张健','计科2306班',63.0,4,'职业规划','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 公共艺术鉴赏 (course_id=9, 1学分)
(9,30,29,'邱琳紫','计科2306班',85.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,31,29,'宋新鹏','计科2306班',87.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,32,29,'赵琦','计科2306班',83.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,33,29,'苏红阳','计科2306班',90.0,1,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,34,29,'唐浩然','计科2306班',79.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,35,29,'王宏民','计科2306班',74.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,36,29,'王玉蛟','计科2306班',88.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,37,29,'吴雨航','计科2306班',72.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,38,29,'徐瑞瑶','计科2306班',86.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,39,29,'韩坤','计科2306班',68.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,40,29,'张博巍','计科2306班',89.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,41,29,'程启頔','计科2306班',66.0,4,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,42,29,'邓雨轩','计科2306班',80.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,43,29,'高菲','计科2306班',91.0,1,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,44,29,'胡钰','计科2306班',75.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,45,29,'李佳芮','计科2306班',94.0,1,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,46,29,'李爽','计科2306班',64.0,4,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,47,29,'梁莹静','计科2306班',82.0,2,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,48,29,'孟阳','计科2306班',70.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,49,29,'王瑾泰','计科2306班',78.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,50,29,'王平洋','计科2306班',69.0,3,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,51,29,'夏志浩','计科2306班',92.0,1,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,52,29,'张健','计科2306班',61.0,4,'艺术鉴赏','2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 中国传统文化 (course_id=10, 1学分)
(10,30,29,'邱琳紫','计科2306班',92.0,1,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,31,29,'宋新鹏','计科2306班',86.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,32,29,'赵琦','计科2306班',84.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,33,29,'苏红阳','计科2306班',88.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,34,29,'唐浩然','计科2306班',81.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,35,29,'王宏民','计科2306班',77.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,36,29,'王玉蛟','计科2306班',90.0,1,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,37,29,'吴雨航','计科2306班',73.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,38,29,'徐瑞瑶','计科2306班',87.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,39,29,'韩坤','计科2306班',69.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,40,29,'张博巍','计科2306班',91.0,1,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,41,29,'程启頔','计科2306班',67.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,42,29,'邓雨轩','计科2306班',82.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,43,29,'高菲','计科2306班',89.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,44,29,'胡钰','计科2306班',77.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,45,29,'李佳芮','计科2306班',90.0,1,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,46,29,'李爽','计科2306班',65.0,4,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,47,29,'梁莹静','计科2306班',85.0,2,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,48,29,'孟阳','计科2306班',71.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,49,29,'王瑾泰','计科2306班',79.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,50,29,'王平洋','计科2306班',70.0,3,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,51,29,'夏志浩','计科2306班',93.0,1,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,52,29,'张健','计科2306班',62.0,4,'传统文化','2026-05-27 10:00:00','2026-05-27 10:00:00');

-- ========================================
-- 学院
-- ========================================
INSERT IGNORE INTO `colleges` (`id`, `name`, `code`, `description`, `status`, `student_count`, `teacher_count`, `created_at`, `updated_at`) VALUES
(1, '软件学院', 'SOFT', '软件学院', 1, 0, 0, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(2, '电子工程学院', 'EE', '电子工程学院', 1, 0, 0, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(3, '商学院', 'BUS', '商学院', 1, 0, 0, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(4, '艺术设计学院', 'ART', '艺术设计学院', 1, 0, 0, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- 专业
-- ========================================
INSERT IGNORE INTO `majors` (`id`, `college_id`, `name`, `code`, `short_name`, `description`, `status`, `created_at`, `updated_at`) VALUES
(1, 1, '软件工程', '01', '软工', '软件工程专业', 1, '2025-12-23 01:28:15', '2026-03-27 02:35:22'),
(2, 1, '计算机科学与技术', '02', '计科', '计算机科学与技术专业', 1, '2025-12-23 01:28:15', '2026-03-27 02:35:31'),
(3, 1, '数据科学与大数据技术', '03', '大数据', '数据科学与大数据技术专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:16:21'),
(4, 1, '智能科学与技术', '04', '智科', '智能科学与技术专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:16:21'),
(5, 1, '虚拟现实技术', '05', 'VR', '虚拟现实技术专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:16:21'),
(6, 2, '电子信息工程', '06', '电信', '电子信息工程专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(7, 2, '自动化', '07', '自动化', '自动化专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(8, 2, '人工智能', '08', 'AI', '人工智能专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(9, 3, '电子商务', '09', '电商', '电子商务专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(10, 3, '物流管理', '10', '物流', '物流管理专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(11, 3, '财务管理', '11', '财管', '财务管理专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(12, 4, '网络与新媒体', '12', '网媒', '网络与新媒体专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(13, 4, '环境设计', '13', '环设', '环境设计专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38'),
(14, 4, '视觉传达设计', '14', '视传', '视觉传达设计专业', 1, '2025-12-23 01:28:15', '2025-12-25 06:17:38');

-- ========================================
-- 班级
-- ========================================
INSERT IGNORE INTO `classes` (`id`, `name`, `college_id`, `major_id`, `teacher_id`, `counselor_id`, `created_at`, `updated_at`) VALUES
(1, '计科2306班', 1, 2, 29, NULL, '2025-12-23 01:28:15', '2025-12-25 06:16:27'),
(5, '软工2306班', 1, 2, NULL, NULL, '2026-03-04 10:43:20', '2026-03-04 10:43:20');

-- ========================================
-- RBAC：角色
-- ========================================
INSERT IGNORE INTO `roles` (`id`, `name`, `code`, `description`, `status`, `created_at`, `updated_at`) VALUES
(1, '学生', 'ROLE_STUDENT', '学生角色', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(2, '教师', 'ROLE_TEACHER', '教师角色', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(3, '辅导员', 'ROLE_COUNSELOR', '辅导员角色', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(4, '管理员', 'ROLE_ADMIN', '系统管理员（全校）', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(5, '学院管理员', 'ROLE_COLLEGE_ADMIN', '学院管理员（仅本学院）', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- RBAC：权限
-- ========================================
INSERT IGNORE INTO `permissions` (`id`, `name`, `code`, `description`, `status`, `created_at`, `updated_at`) VALUES
(1, '查看学生信息', 'PERM_STUDENT_VIEW', '查看学生基本信息', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(2, '管理学生信息', 'PERM_STUDENT_MANAGE', '管理学生信息', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(3, '查看课程信息', 'PERM_COURSE_VIEW', '查看课程信息', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(4, '管理课程信息', 'PERM_COURSE_MANAGE', '管理课程信息', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(5, '查看预警信息', 'PERM_WARNING_VIEW', '查看预警信息', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(6, '管理预警规则', 'PERM_WARNING_MANAGE', '管理预警规则', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(7, '查看帮扶计划', 'PERM_ASSISTANCE_VIEW', '查看帮扶计划', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(8, '管理帮扶计划', 'PERM_ASSISTANCE_MANAGE', '管理帮扶计划', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(9, '系统管理', 'PERM_SYSTEM_MANAGE', '系统管理权限', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- RBAC：角色权限关联
-- ========================================
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`, `created_at`) VALUES
(1,1,'2025-12-23 01:28:15'),(1,3,'2025-12-23 01:28:15'),(1,5,'2025-12-23 01:28:15'),(1,7,'2025-12-23 01:28:15'),
(2,1,'2025-12-23 01:28:15'),(2,3,'2025-12-23 01:28:15'),(2,5,'2025-12-23 01:28:15'),
(3,1,'2025-12-23 01:28:15'),(3,3,'2025-12-23 01:28:15'),(3,5,'2025-12-23 01:28:15'),(3,7,'2025-12-23 01:28:15'),(3,8,'2025-12-23 01:28:15'),
(4,1,'2025-12-23 01:28:15'),(4,2,'2025-12-23 01:28:15'),(4,3,'2025-12-23 01:28:15'),(4,4,'2025-12-23 01:28:15'),(4,5,'2025-12-23 01:28:15'),(4,6,'2025-12-23 01:28:15'),(4,7,'2025-12-23 01:28:15'),(4,8,'2025-12-23 01:28:15'),(4,9,'2025-12-23 01:28:15');

-- ========================================
-- 用户账号（BCrypt密码=123456）
-- ========================================
INSERT IGNORE INTO `users` (`id`, `username`, `password`, `name`, `email`, `phone`, `role`, `status`, `created_at`, `updated_at`) VALUES
(1, 'admin', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '系统管理员', 'admin@academic.edu', '13800000000', 4, 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(16, 'adm001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '夏志浩', 'admin@test.com', '13800000003', 4, 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(18, 'teacher001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '张老师', 'teacher001@test.com', '13800000010', 2, 1, '2025-12-23 02:18:22', '2025-12-23 02:18:22'),
(19, 'teacher002', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '王老师', 'teacher002@test.com', '13800000011', 2, 1, '2025-12-30 07:09:40', '2025-12-30 07:09:40'),
(22, 'teacher003', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '李老师', 'teacher003@test.com', '13800000012', 2, 1, '2025-12-30 07:10:00', '2025-12-30 07:10:00'),
(23, 'teacher004', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '赵老师', 'teacher004@test.com', '13800000013', 2, 1, '2025-12-30 07:17:12', '2025-12-30 07:17:12'),
(27, '2023020616', '$2a$10$0vHxKjdjJZKZoKDFoiwmFuJ6mU1MIg0ViGP7gSbBavhtHxb0cwQQm', '司明', '2318986782@qq.com', '15245508389', 1, 1, '2026-03-04 10:43:20', '2026-03-04 10:43:20'),
(28, '11111', '$2a$10$Pri6I9ZjB8R.PR1/qFXYE.RaIUyy5XgtV0QMwWnAln3zkzGrgpn46', '发生的官方', '23467675@qq.com', '16744343245', 2, 1, '2026-03-04 11:56:54', '2026-03-04 11:56:54'),
(29, '10001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '于凤海', '13397583736@qq.com', '18845894565', 2, 1, '2026-03-04 11:58:09', '2026-03-04 11:58:09'),
(30, '2023020615', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '邱琳紫', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(31, '2023020617', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '宋新鹏', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(32, '2022020137', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '赵琦', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(33, '2023020619', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '苏红阳', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(34, '2023020620', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '唐浩然', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(35, '2023020621', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '王宏民', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(36, '2023020624', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '王玉蛟', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(37, '2023020625', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '吴雨航', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(38, '2023020626', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '徐瑞瑶', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(39, '2023020607', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '韩坤', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(40, '2023020629', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '张博巍', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(41, '2023020504', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '程启頔', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(42, '2023020505', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '邓雨轩', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(43, '2023020506', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '高菲', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(44, '2023020507', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '胡钰', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(45, '2023020509', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '李佳芮', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(46, '2023020510', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '李爽', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(47, '2023020512', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '梁莹静', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(48, '2023020515', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '孟阳', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(49, '2023020519', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '王瑾泰', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(50, '2023020520', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '王平洋', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(51, '2023020521', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '夏志浩', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(52, '2023020524', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '张健', NULL, NULL, 1, 1, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(53, 'counselor001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '张明', 'counselor001@test.com', '13800000020', 3, 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(54, 'counselor002', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', '李华', 'counselor002@test.com', '13800000021', 3, 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- 学生档案（全部归入计科2306班）
-- ========================================
INSERT IGNORE INTO `student_profile` (`id`, `user_id`, `student_id`, `name`, `gender`, `college_id`, `major_id`, `class_id`, `class_name`, `enrollment_year`, `privacy_level`, `created_at`, `updated_at`) VALUES
(3, 27, '2023020616', '司明', '男', 1, 2, 1, '计科2306班', 2023, 0, '2026-03-04 10:43:20', '2026-03-04 10:43:20'),
(4, 30, '2023020615', '邱琳紫', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(5, 31, '2023020617', '宋新鹏', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(6, 32, '2022020137', '赵琦', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(7, 33, '2023020619', '苏红阳', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(8, 34, '2023020620', '唐浩然', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(9, 35, '2023020621', '王宏民', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(10, 36, '2023020624', '王玉蛟', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(11, 37, '2023020625', '吴雨航', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(12, 38, '2023020626', '徐瑞瑶', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(13, 39, '2023020607', '韩坤', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(14, 40, '2023020629', '张博巍', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(15, 41, '2023020504', '程启頔', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(16, 42, '2023020505', '邓雨轩', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(17, 43, '2023020506', '高菲', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(18, 44, '2023020507', '胡钰', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(19, 45, '2023020509', '李佳芮', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(20, 46, '2023020510', '李爽', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(21, 47, '2023020512', '梁莹静', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(22, 48, '2023020515', '孟阳', '女', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(23, 49, '2023020519', '王瑾泰', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(24, 50, '2023020520', '王平洋', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(25, 51, '2023020521', '夏志浩', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29'),
(26, 52, '2023020524', '张健', '男', 1, 1, 1, '计科2306班', 2023, 2, '2026-03-05 11:00:29', '2026-03-05 11:00:29');

-- ========================================
-- 教师档案
-- ========================================
INSERT IGNORE INTO `teacher_profile` (`id`, `user_id`, `name`, `college_id`, `title`, `research_areas`, `created_at`, `updated_at`) VALUES
(1, 18, '张老师', 1, '讲师', '软件工程', '2025-12-23 02:18:22', '2025-12-23 02:18:22'),
(2, 19, '王老师', 1, '副教授', '计算机科学', '2025-12-30 07:09:40', '2025-12-30 07:09:40'),
(3, 22, '李老师', 1, '讲师', '数据科学', '2025-12-30 07:10:00', '2025-12-30 07:10:00'),
(4, 23, '赵老师', 1, '教授', '人工智能', '2025-12-30 07:17:12', '2025-12-30 07:17:12'),
(5, 28, '发生的官方', 1, '讲师', '软件工程', '2026-03-04 11:56:54', '2026-03-04 11:56:54'),
(6, 29, '于凤海', 1, '副教授', '计算机科学与技术、数据结构、Java开发', '2026-03-04 11:58:09', '2026-03-04 11:58:09');

-- ========================================
-- 管理员档案
-- ========================================
INSERT IGNORE INTO `admin_profile` (`id`, `user_id`, `name`, `phone`, `email`, `department`, `created_at`, `updated_at`) VALUES
(1, 1, '系统管理员', '13800000000', 'admin@academic.edu', '信息化管理中心', '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(2, 16, '夏志浩', '13800000003', 'admin@test.com', '信息化管理中心', '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- 学院管理员档案
-- ========================================
CREATE TABLE IF NOT EXISTS `college_admin_profile` (
  `id` bigint AUTO_INCREMENT PRIMARY KEY,
  `user_id` bigint NOT NULL COMMENT '关联 users.id',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `college_id` bigint NOT NULL COMMENT '所属学院',
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `title` varchar(50) DEFAULT NULL COMMENT '职称（院长/副院长）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_college_admin_user` (`user_id`),
  INDEX `idx_college_admin_college` (`college_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院管理员档案';

INSERT IGNORE INTO `college_admin_profile` (`id`, `user_id`, `name`, `college_id`, `phone`, `email`, `title`) VALUES
(1, 57, '张建国', 1, '13900000001', 'zhangjg@academic.edu', '软件学院院长'),
(2, 56, '李明远', 2, '13900000002', 'limy@academic.edu', '电子工程学院院长');

-- 学院管理员用户账号（密码均为 123456，BCrypt加密）
INSERT IGNORE INTO `users` (`id`, `username`, `password`, `role`, `status`, `created_at`, `updated_at`) VALUES
(57, 'collegeadmin1', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 5, 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(56, 'collegeadmin2', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 5, 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- 辅导员档案
-- ========================================
INSERT IGNORE INTO `counselor_profiles` (`id`, `user_id`, `college_id`, `counselor_id`, `name`, `gender`, `phone`, `email`, `department`, `title`, `education`, `introduction`, `status`, `college_name`, `staff_id`, `office_location`, `class_count`, `student_count`, `created_at`, `updated_at`) VALUES
(1, 53, 1, 'COU001', '张明', '男', '13800000020', 'counselor001@test.com', '学生工作办公室', '讲师', '硕士', '负责计科2306班学生日常管理与学业帮扶', 1, '软件学院', 'STAFF001', '实训楼A201', 1, 24, '2025-12-23 01:28:15', '2025-12-23 01:28:15'),
(2, 54, 1, 'COU002', '李华', '女', '13800000021', 'counselor002@test.com', '学生工作办公室', '讲师', '硕士', '负责学生思想政治教育和心理健康辅导', 1, '软件学院', 'STAFF002', '实训楼A202', 1, 0, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- 辅导员班级管理
-- ========================================
INSERT IGNORE INTO `counselor_class_management` (`id`, `counselor_id`, `class_id`, `class_name`, `status`, `created_at`, `updated_at`) VALUES
(1, 53, 1, '计科2306班', 1, '2025-12-23 01:28:15', '2025-12-23 01:28:15');

-- ========================================
-- 教师课程关系（于凤海负责全部10门课）
-- ========================================
INSERT IGNORE INTO `teacher_course` (`teacher_id`, `course_id`, `semester`, `year`, `created_at`, `updated_at`) VALUES
(29, 1, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 2, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 3, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 4, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 5, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 6, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 7, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 8, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 9, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(29, 10, 2, 2025, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 课程排课（2025-2026学年第二学期）
-- ========================================
INSERT IGNORE INTO `course_plans` (`id`, `course_id`, `teacher_id`, `semester`, `year`, `start_date`, `end_date`, `class_time`, `location`, `max_students`, `status`, `created_at`, `updated_at`) VALUES
(1, 1, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周一 1-2节', '教学楼A301', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 2, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周一 3-4节', '教学楼A302', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 3, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周二 1-2节', '教学楼A303', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, 4, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周二 3-4节', '实训楼B101', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(5, 5, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周三 1-2节', '实训楼B102', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(6, 6, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周三 3-4节', '教学楼A304', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(7, 7, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周四 5-6节', '教学楼A401', 80, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(8, 8, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周四 7-8节', '教学楼A402', 80, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(9, 9, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周五 5-6节', '艺术楼C201', 50, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(10, 10, 29, '2025-2026-2', 2026, '2026-02-24', '2026-06-30', '周五 7-8节', '教学楼A403', 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 选课记录（24名学生×6门必修课 = 144条）
-- student_profile ID 3~26
-- ========================================
INSERT IGNORE INTO `enrollments` (`student_id`, `course_id`, `class_id`, `status`, `semester`, `year`, `created_at`, `updated_at`) VALUES
(3,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(4,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(5,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(6,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(7,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(8,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(9,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(10,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(11,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(12,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(13,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(14,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(15,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(16,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(17,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(18,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(19,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(20,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(21,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(22,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(23,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(24,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(25,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
(26,1,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,2,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,3,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,4,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,5,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,6,1,1,2,2025,'2026-05-27 10:00:00','2026-05-27 10:00:00');

-- ========================================
-- 预警规则（4条）—— 驱动预警引擎
-- ========================================
INSERT IGNORE INTO `warning_rules` (`id`, `name`, `description`, `level`, `rule_condition`, `status`, `rule_code`, `created_at`, `updated_at`) VALUES
(1, '单科不及格预警', '课程成绩低于60分触发预警', 2, 'any_course_score < 60', 1, 'RULE_FAIL_SINGLE', '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, '多科挂科预警(2门)', '同一学期挂科2门及以上触发中度预警', 2, 'fail_courses_count >= 2', 1, 'RULE_FAIL_MULTI', '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, '多科挂科预警(3门)', '同一学期挂科3门及以上触发高度预警', 3, 'fail_courses_count >= 3', 1, 'RULE_FAIL_MULTI_HIGH', '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, 'GPA过低预警', '学期GPA低于2.0触发预警', 2, 'gpa < 2.0', 1, 'RULE_GPA_LOW', '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 挂科记录（score<60的学生：韩坤39、李爽46、张健52）
-- 每个学生挂2-3科
-- ========================================
INSERT IGNORE INTO `fail_records` (`id`, `student_id`, `course_id`, `course_name`, `exam_time`, `score`, `fail_type`, `semester`, `academic_year`, `status`, `created_at`, `updated_at`) VALUES
(1, 39, 4, '数据结构', '2026-06-20 09:00:00', 65.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 39, 6, '计算机组成原理', '2026-06-22 09:00:00', 71.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 46, 1, '高等数学', '2026-06-18 09:00:00', 66.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, 46, 3, '线性代数', '2026-06-20 09:00:00', 69.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(5, 46, 5, 'Java程序设计', '2026-06-23 09:00:00', 68.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(6, 52, 1, '高等数学', '2026-06-18 09:00:00', 64.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(7, 52, 3, '线性代数', '2026-06-20 09:00:00', 62.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(8, 52, 5, 'Java程序设计', '2026-06-23 09:00:00', 65.00, 'fail', '2025-2026-2', '2025-2026', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 预警记录（基于挂科记录生成）
-- ========================================
INSERT IGNORE INTO `academic_warning` (`id`, `student_id`, `student_name`, `warning_level`, `warning_type`, `description`, `status`, `rule_id`, `created_at`, `updated_at`) VALUES
(1, 39, '韩坤', 2, '多科挂科', '数据结构(65分)、计算机组成原理(71分)两门挂科，触发中度预警', 0, 2, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 46, '李爽', 3, '多科挂科', '高等数学(66分)、线性代数(69分)、Java程序设计(68分)三门挂科，触发高度预警', 1, 3, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 52, '张健', 3, '多科挂科', '高等数学(64分)、线性代数(62分)、Java程序设计(65分)三门挂科，触发高度预警', 0, 3, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 标杆分析（24名学生GPA排行）
-- ========================================
INSERT IGNORE INTO `benchmark_analysis` (`student_id`, `class_id`, `major_id`, `semester`, `student_gpa`, `class_avg_gpa`, `major_avg_gpa`, `class_rank`, `class_total`, `courses_passed`, `courses_failed`, `required_credits`, `credits_on_track`, `created_at`, `updated_at`) VALUES
(4, 1, 1, '2025-2026-2', 3.800, 3.050, 3.100, 3, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(5, 1, 1, '2025-2026-2', 3.700, 3.050, 3.100, 5, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(6, 1, 1, '2025-2026-2', 3.200, 3.050, 3.100, 10, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(7, 1, 1, '2025-2026-2', 3.350, 3.050, 3.100, 8, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(8, 1, 1, '2025-2026-2', 3.350, 3.050, 3.100, 9, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(9, 1, 1, '2025-2026-2', 2.500, 3.050, 3.100, 18, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(10, 1, 1, '2025-2026-2', 3.250, 3.050, 3.100, 11, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(11, 1, 1, '2025-2026-2', 2.450, 3.050, 3.100, 19, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(12, 1, 1, '2025-2026-2', 3.420, 3.050, 3.100, 7, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(13, 1, 1, '2025-2026-2', 2.000, 3.050, 3.100, 22, 24, 4, 2, 20.00, 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(14, 1, 1, '2025-2026-2', 3.820, 3.050, 3.100, 2, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(15, 1, 1, '2025-2026-2', 2.350, 3.050, 3.100, 20, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(16, 1, 1, '2025-2026-2', 3.250, 3.050, 3.100, 12, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(17, 1, 1, '2025-2026-2', 3.400, 3.050, 3.100, 8, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(18, 1, 1, '2025-2026-2', 2.500, 3.050, 3.100, 17, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(19, 1, 1, '2025-2026-2', 3.830, 3.050, 3.100, 1, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(20, 1, 1, '2025-2026-2', 1.850, 3.050, 3.100, 23, 24, 3, 3, 20.00, 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(21, 1, 1, '2025-2026-2', 3.350, 3.050, 3.100, 9, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(22, 1, 1, '2025-2026-2', 2.650, 3.050, 3.100, 15, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(23, 1, 1, '2025-2026-2', 3.100, 3.050, 3.100, 13, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(24, 1, 1, '2025-2026-2', 2.500, 3.050, 3.100, 16, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(25, 1, 1, '2025-2026-2', 3.680, 3.050, 3.100, 6, 24, 6, 0, 20.00, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(26, 1, 1, '2025-2026-2', 1.700, 3.050, 3.100, 24, 24, 3, 3, 20.00, 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 帮扶计划（针对挂科学生）
-- ========================================
INSERT IGNORE INTO `assistance_plans` (`id`, `counselor_id`, `student_id`, `content`, `status`, `created_at`, `updated_at`) VALUES
(1, 53, 13, '针对韩坤同学的学业帮扶计划：1.安排学习小组辅导数据结构；2.每周一次答疑；3.定期检查学习进度', 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 53, 20, '针对李爽同学的学业帮扶计划：1.重点补习高等数学和线性代数基础；2.安排学伴互助；3.每两周向辅导员汇报进度；4.已安排补考', 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 53, 26, '针对张健同学的学业帮扶计划：1.重点补习数学和编程基础；2.每周安排两次额外辅导；3.建立学习档案跟踪进度', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 沟通记录
-- ========================================
INSERT IGNORE INTO `communication_record` (`id`, `teacher_id`, `student_id`, `content`, `type`, `student_name`, `create_time`) VALUES
(1, 29, 39, '关于数据结构课程成绩不理想，与学生沟通了学习方法，建议多做练习', '成绩沟通', '韩坤', '2026-05-27 10:00:00'),
(2, 29, 46, '学生三门课程不及格，与辅导员张明联合约谈，制定补习计划', '预警沟通', '李爽', '2026-05-27 10:00:00'),
(3, 29, 52, '学生数学基础薄弱导致多科挂科，建议参加课外辅导班', '成绩沟通', '张健', '2026-05-27 10:00:00'),
(4, 29, 13, '数据结构补考准备情况跟进，目前掌握较好', '辅导记录', '韩坤', '2026-05-27 10:00:00');

-- ========================================
-- 系统通知
-- ========================================
INSERT IGNORE INTO `notifications` (`id`, `user_id`, `title`, `content`, `type`, `warning_id`, `is_read`, `created_at`, `updated_at`) VALUES
(1, 39, '学业预警通知', '韩坤同学，您本学期有2门课程成绩不达标（数据结构65分、计算机组成原理71分），已被列入学业预警名单。请尽快与辅导员联系。', 'warning', 1, 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 46, '高度学业预警通知', '李爽同学，您本学期有3门课程不及格（高等数学66分、线性代数69分、Java程序设计68分），已触发高度预警。请务必重视并尽快联系辅导员张明老师。', 'warning', 2, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 52, '高度学业预警通知', '张健同学，您本学期有3门课程不及格（高等数学64分、线性代数62分、Java程序设计65分），已触发高度预警。建议及时寻求学业帮扶。', 'warning', 3, 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, 53, '班级预警汇总', '您负责的计科2306班中，有3名学生触发了学业预警：韩坤(中度)、李爽(高度)、张健(高度)。请及时跟进帮扶。', 'system', NULL, 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 帮扶资源
-- ========================================
INSERT IGNORE INTO `support_resource` (`id`, `title`, `description`, `type`, `category`, `content`, `url`, `author`, `views`, `downloads`, `status`, `created_at`, `updated_at`) VALUES
(1, '高等数学学习指南', '高等数学核心知识点总结与习题讲解，帮助挂科学生系统复习', 'document', '数学', '高等数学包含极限、导数、积分、微分方程等核心内容...', 'https://example.com/math-guide', '于凤海', 150, 45, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 'Java编程入门教程', '针对Java程序设计挂科学生的补习资料，涵盖面向对象、集合、IO流等', 'video', '编程', 'Java基础语法、面向对象编程、异常处理、多线程...', 'https://example.com/java-tutorial', '于凤海', 200, 60, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, '学习时间管理方法', '帮助学业困难学生改善学习习惯，提高学习效率', 'article', '学习方法', '番茄工作法、费曼学习法、康奈尔笔记法等高效学习方法介绍', NULL, '张明', 80, 30, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, '线性代数精讲', '线性代数重点难点解析，含大量练习题和答案', 'document', '数学', '矩阵运算、行列式、向量空间、特征值与特征向量...', 'https://example.com/linear-algebra', '于凤海', 120, 38, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 学生设置
-- ========================================
INSERT IGNORE INTO `student_settings` (`id`, `student_id`, `notification_enabled`, `email_notifications`, `theme`, `created_at`, `updated_at`) VALUES
(3, 3, 1, 1, 'light', '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(4, 4, 1, 1, 'light', '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(5, 5, 1, 0, 'dark', '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- ========================================
-- 订阅偏好
-- ========================================
INSERT IGNORE INTO `subscription_preferences` (`id`, `student_id`, `subscribe_warnings`, `subscribe_low`, `subscribe_medium`, `subscribe_high`, `subscribe_assistance`, `subscribe_system`, `push_email`, `push_sms`, `push_app`, `created_at`, `updated_at`) VALUES
(1, 3, 1, 0, 1, 1, 1, 1, 1, 0, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(2, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
(3, 5, 1, 0, 1, 1, 1, 0, 1, 0, 1, '2026-05-27 10:00:00', '2026-05-27 10:00:00');

-- 旧表 student 已废弃，种子数据已迁移至 student_profile 表（见前文 INSERT IGNORE INTO student_profile），此处不再向不存在的 student 表插入

-- ========================================
-- 详细成绩（scores表：24学生×6门必修课 = 144条）
-- student_id对应student_profile.id（3~26）
-- 韩坤(13)挂数据结构(4)、计组(6)；李爽(20)挂高数(1)、线代(3)、Java(5)；张健(26)挂高数(1)、线代(3)、Java(5)
-- ========================================
-- 司明(3)：GPA 3.1
INSERT IGNORE INTO `scores` (`student_id`, `course_id`, `semester`, `year`, `regular_score`, `final_score`, `score_total`, `grade`, `grade_point`, `created_at`, `updated_at`) VALUES
(3,1,2,2025,78,85,82,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,2,2,2025,80,82,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,3,2,2025,75,80,78,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,4,2,2025,90,88,89,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,5,2,2025,82,85,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,6,2,2025,70,78,74,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 邱琳紫(4)：GPA 3.8
(4,1,2,2025,92,95,94,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,2,2,2025,88,90,89,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,3,2,2025,85,88,87,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,4,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,5,2,2025,90,92,91,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,6,2,2025,82,85,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 宋新鹏(5)：GPA 3.7
(5,1,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,2,2,2025,85,88,87,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,3,2,2025,82,85,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,4,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,5,2,2025,90,93,92,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,6,2,2025,80,84,82,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 赵琦(6)：GPA 3.2
(6,1,2,2025,75,82,79,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,2,2,2025,80,85,83,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,3,2,2025,70,78,74,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,4,2,2025,85,88,87,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,5,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,6,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 苏红阳(7)：GPA 3.35
(7,1,2,2025,82,86,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,2,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,3,2,2025,85,88,87,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,4,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,5,2,2025,75,80,78,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,6,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 唐浩然(8)：GPA 3.35
(8,1,2,2025,85,90,88,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,2,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,3,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,4,2,2025,82,86,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,5,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,6,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王宏民(9)：GPA 2.5
(9,1,2,2025,62,70,66,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,2,2,2025,68,75,72,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,3,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,4,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,5,2,2025,70,76,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(9,6,2,2025,75,80,78,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王玉蛟(10)：GPA 3.25
(10,1,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,2,2,2025,82,86,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,3,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,4,2,2025,88,90,89,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,5,2,2025,75,80,78,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(10,6,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 吴雨航(11)：GPA 2.45
(11,1,2,2025,60,68,64,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,2,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,3,2,2025,62,68,65,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,4,2,2025,68,75,72,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,5,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(11,6,2,2025,70,76,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 徐瑞瑶(12)：GPA 3.42
(12,1,2,2025,85,88,87,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,2,2,2025,82,86,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,3,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,4,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,5,2,2025,85,90,88,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(12,6,2,2025,78,85,82,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 韩坤(13)：GPA 2.0（挂数据结构59分→换算65、计组55分→换算71）
(13,1,2,2025,60,65,63,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,2,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,3,2,2025,68,72,70,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,4,2,2025,55,62,59,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,5,2,2025,65,70,68,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(13,6,2,2025,50,58,54,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 张博巍(14)：GPA 3.82
(14,1,2,2025,90,95,93,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,2,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,3,2,2025,85,90,88,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,4,2,2025,92,96,94,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,5,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(14,6,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 程启頔(15)：GPA 2.35
(15,1,2,2025,58,68,63,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,2,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,3,2,2025,60,68,64,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,4,2,2025,62,70,66,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,5,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(15,6,2,2025,70,76,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 邓雨轩(16)：GPA 3.25
(16,1,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,2,2,2025,82,88,85,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,3,2,2025,75,82,79,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,4,2,2025,88,90,89,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,5,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(16,6,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 高菲(17)：GPA 3.4
(17,1,2,2025,85,90,88,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,2,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,3,2,2025,82,86,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,4,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,5,2,2025,75,80,78,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(17,6,2,2025,82,88,85,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 胡钰(18)：GPA 2.5
(18,1,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,2,2,2025,70,76,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,3,2,2025,62,70,66,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,4,2,2025,75,82,79,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,5,2,2025,80,85,83,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(18,6,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 李佳芮(19)：GPA 3.83
(19,1,2,2025,92,96,94,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,2,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,3,2,2025,90,95,93,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,4,2,2025,85,90,88,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,5,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(19,6,2,2025,92,95,94,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 李爽(20)：GPA 1.85（挂高数55分、线代52分、Java 48分）
(20,1,2,2025,50,58,54,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,2,2,2025,70,75,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,3,2,2025,48,55,52,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,4,2,2025,68,72,70,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,5,2,2025,42,52,47,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(20,6,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 梁莹静(21)：GPA 3.35
(21,1,2,2025,82,86,84,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,2,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,3,2,2025,85,88,87,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,4,2,2025,80,85,83,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,5,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(21,6,2,2025,75,82,79,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 孟阳(22)：GPA 2.65
(22,1,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,2,2,2025,68,75,72,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,3,2,2025,70,76,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,4,2,2025,75,80,78,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,5,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(22,6,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王瑾泰(23)：GPA 3.1
(23,1,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,2,2,2025,75,82,79,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,3,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,4,2,2025,82,88,85,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,5,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(23,6,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 王平洋(24)：GPA 2.5
(24,1,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,2,2,2025,70,76,73,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,3,2,2025,62,70,66,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,4,2,2025,78,82,80,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,5,2,2025,72,78,75,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(24,6,2,2025,68,76,72,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 夏志浩(25)：GPA 3.68
(25,1,2,2025,88,92,90,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,2,2,2025,85,90,88,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,3,2,2025,78,84,81,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,4,2,2025,82,88,85,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,5,2,2025,88,95,92,'A',4.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(25,6,2,2025,78,86,82,'B',3.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),
-- 张健(26)：GPA 1.7（挂高数52分、线代48分、Java 50分）
(26,1,2,2025,48,55,52,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,2,2,2025,68,75,72,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,3,2,2025,45,50,48,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,4,2,2025,65,72,69,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,5,2,2025,42,55,49,'F',0.0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(26,6,2,2025,62,70,66,'C',2.0,'2026-05-27 10:00:00','2026-05-27 10:00:00');

-- ========================================
-- 教学资源
-- ========================================
INSERT IGNORE INTO `teaching_resources` (`id`, `course_id`, `title`, `description`, `type`, `url`, `downloads`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`) VALUES
(1, 1, '高等数学-第一至四章课件', '高等数学课程配套课件，包含函数极限、导数与微分、不定积分、定积分等章节', 'pdf', 'https://example.com/math-slides', 320, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(2, 1, '高等数学习题集', '配套习题集，含答案与解析，共200题覆盖全部章节', 'pdf', 'https://example.com/math-exercises', 280, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(3, 2, '大学英语综合教程', '大学英语读写译综合教程，含课文精讲与课后练习', 'pdf', 'https://example.com/english-textbook', 350, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(4, 3, '线性代数-矩阵与行列式', '线性代数重点章节精讲，包括矩阵运算、行列式、线性方程组', 'pdf', 'https://example.com/linear-algebra', 195, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(5, 4, '数据结构-算法分析视频', '数据结构算法讲解视频系列，包含排序、查找、树、图等核心算法', 'video', 'https://example.com/ds-video', 410, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(6, 5, 'Java程序设计-实验指导书', 'Java课程实验指导书，涵盖面向对象编程、异常处理、集合框架、IO流等实验', 'pdf', 'https://example.com/java-lab', 365, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(7, 5, 'Java编程实战项目源码', '学生管理系统完整项目源码，适合课程设计参考', 'zip', 'https://example.com/java-project', 250, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(8, 6, '计算机组成原理-实验教程', '计组实验教程，包括运算器、存储器、控制器等实验指导', 'pdf', 'https://example.com/co-lab', 178, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(9, 4, '数据结构习题答案', '数据结构教材课后习题参考答案，含详细解题过程', 'pdf', 'https://example.com/ds-answers', 305, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00'),
(10, 6, '计组期末复习大纲', '计算机组成原理期末考试复习提纲，含重点知识梳理与模拟题', 'doc', 'https://example.com/co-review', 420, 1, '于凤海', '2026-05-27 10:00:00', NULL, '2026-05-27 10:00:00');






