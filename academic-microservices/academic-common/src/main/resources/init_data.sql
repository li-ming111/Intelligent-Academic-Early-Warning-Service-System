-- ========================================
-- 智能学业预警服务系统 - 种子数据
-- 所有 INSERT 使用 INSERT IGNORE，可重复执行，安全幂等。
-- 密码均为 123456 的 BCrypt 编码。
--
-- 列名以线上库为准。此前本文件存在两类错误，会导致全新库"看起来建好了"其实半残：
--   1. 用了不存在的列（warning_rules.rule_name/warning_level/expression、
--      scores.score、teacher_profile.teacher_id、subscription_preferences.user_id）；
--   2. 用了不存在的表名（counselor_profile，真实表名是复数 counselor_profiles）。
-- 这些都会以 1054/1146 失败，而 DataInitializer 现在会把失败汇总进启动日志。
--
-- 字段口径约定（与 ScoreCaliber / ScoreSyncSupport 一致）：
--   * scores.student_id 存的是 student_profile.id，不是 users.id（两个 id 空间不重合）。
--   * scores.semester 是全局学期序号（int），不是"学年内的第几学期"，也不是日期字符串。
--     入学年份取学号前四位：idx → (入学年 + (idx-1)/2)-(+1)-( (idx-1)%2+1 )。
--   * scores.score_total = ROUND((regular_score + final_score) / 2)。
--   * scores.grade / grade_point 由 score_total 经 ScoreSyncSupport 折算（A/A-/B/C/D）。
--   * final_score 必须落值：为空会被"期末未录入"口径整体排除，该行等于没有成绩。
-- ========================================

-- 学院
INSERT IGNORE INTO colleges (id, name, code, description, status) VALUES
(1, '信息工程学院', 'IE', '信息工程学院', 1),
(2, '计算机科学学院', 'CS', '计算机科学学院', 1),
(3, '人工智能学院', 'AI', '人工智能学院', 1);

-- 专业
INSERT IGNORE INTO majors (id, name, code, college_id, description, short_name, status) VALUES
(1, '计算机科学与技术', 'CS001', 2, '计算机科学与技术专业', '计科', 1),
(2, '软件工程', 'SE001', 2, '软件工程专业', '软工', 1),
(3, '人工智能', 'AI001', 3, '人工智能专业', 'AI', 1);

-- 班级
INSERT IGNORE INTO classes (id, name, college_id, major_id, teacher_id, counselor_id) VALUES
(1, '计科2306班', 2, 1, 1, 1);

-- 用户（密码：123456 → BCrypt；role：1 学生 / 2 教师 / 3 辅导员 / 4 管理员）
INSERT IGNORE INTO users (id, username, password, role, name, email, phone, status) VALUES
(1, 'admin', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 4, '管理员', 'admin@academic.com', '13800000001', 1),
(2, 'teacher001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 2, '张老师', 'teacher001@academic.com', '13800000002', 1),
(3, 'counselor001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 3, '李辅导员', 'counselor001@academic.com', '13800000003', 1),
(4, 'student001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 1, '王同学', 'student001@academic.com', '13800000004', 1);

-- 学生档案（id=1 是下面 scores.student_id 要用的那个值）
INSERT IGNORE INTO student_profile (id, user_id, student_id, name, gender, college_id, major_id, class_id, enrollment_year) VALUES
(1, 4, '2023020616', '王同学', '男', 2, 1, 1, 2023);

-- 教师档案（无 teacher_id 列，工号不是独立列）
INSERT IGNORE INTO teacher_profile (id, user_id, name, college_id, title) VALUES
(1, 2, '张老师', 2, '教授');

-- 辅导员档案（表名是复数 counselor_profiles）
INSERT IGNORE INTO counselor_profiles (id, user_id, counselor_id, name, college_id, department, title, status) VALUES
(1, 3, 'C001', '李辅导员', 2, '学生工作办公室', '讲师', 1);

-- 课程（type 用中文「必修课/选修课」，与 AdminCourses/TeacherCourses 的取值一致）
INSERT IGNORE INTO courses (id, code, name, credits, type, teacher_id) VALUES
(1, 'CS101', '数据结构', 4.00, '必修课', 1),
(2, 'CS102', '算法设计', 3.50, '必修课', 1),
(3, 'CS201', 'Java程序设计', 4.00, '必修课', 1),
(4, 'CS301', '数据库系统', 3.50, '必修课', 1),
(5, 'CS401', '软件工程', 3.00, '选修课', 1);

-- 预警规则：与线上库逐字一致。全新库没有规则时预警引擎无规则可匹配，扫描结果恒为空。
INSERT IGNORE INTO `warning_rules` (`id`, `name`, `description`, `level`, `rule_condition`, `status`, `created_at`, `updated_at`, `rule_code`, `created_by`, `updated_by`, `fail_definition`, `statistic_cycle`, `trigger_type`, `release_condition`, `intervention_plan`, `notification_flow`, `responsible_person`, `tracking_mechanism`, `archive_spec`, `data_caliber`, `department_responsibility`, `appeal_process`, `exception_handling`) VALUES (1,'低级预警','学生在所有学期内累计挂科次数为1-2科（含2科）',1,'fail_count >= 1 AND fail_count <= 2',1,'2026-05-20 11:14:57','2026-05-20 11:14:57','RULE001',NULL,NULL,'课程考核成绩低于60分（不含60分）视为不及格；未参加考试（缺考）视为挂科；考试作弊取消成绩视为挂科','full_program','periodic','补考通过或重修合格后自动解除预警','1. 教师谈话提醒；2. 学习计划调整建议；3. 每周学习进度跟踪','通知对象：教师、学生。流程：系统自动生成预警，发送通知给教师和学生，教师在3个工作日内约谈学生','教师','教师每周跟踪一次，记录学生学习状态变化，每月汇总上报学院','预警记录保存至学生毕业离校后5年，电子档案与纸质档案同步归档','挂科次数统计范围：学生入学以来所有学期的不及格课程（不含已通过补考/重修的课程）','1. 教务处：负责数据统计和规则维护；2. 学院：负责预警通知和学业干预；3. 教师：负责跟踪学生；4. 学生：配合干预措施','学生对预警结果有异议的，可在收到通知后5个工作日内向学院提交申诉材料，学院在10个工作日内给出复议结果','因不可抗力导致的挂科可申请特殊处理，需提供相关证明材料，经学院审核批准后可不计入挂科统计');
INSERT IGNORE INTO `warning_rules` (`id`, `name`, `description`, `level`, `rule_condition`, `status`, `created_at`, `updated_at`, `rule_code`, `created_by`, `updated_by`, `fail_definition`, `statistic_cycle`, `trigger_type`, `release_condition`, `intervention_plan`, `notification_flow`, `responsible_person`, `tracking_mechanism`, `archive_spec`, `data_caliber`, `department_responsibility`, `appeal_process`, `exception_handling`) VALUES (2,'中级预警','学生在所有学期内累计挂科次数为3-5科（含3科和5科）',2,'fail_count >= 3 AND fail_count <= 5',1,'2026-05-20 11:14:57','2026-05-20 11:14:57','RULE002',NULL,NULL,'课程考核成绩低于60分（不含60分）视为不及格；未参加考试（缺考）视为挂科；考试作弊取消成绩视为挂科','full_program','periodic','补考通过或重修合格后自动解除预警','1. 辅导员约谈；2. 制定个性化学习方案；3. 家长沟通；4. 每周两次学习辅导','通知对象：教师、学生、辅导员。流程：系统自动生成预警，发送通知给教师、学生和辅导员，辅导员在3个工作日内约谈学生并与家长沟通','辅导员','辅导员每3天跟踪一次，学院每周召开预警学生专题会议，每月向教务处上报进展','预警记录保存至学生毕业离校后5年，电子档案与纸质档案同步归档','挂科次数统计范围：学生入学以来所有学期的不及格课程（不含已通过补考/重修的课程）','1. 教务处：负责数据统计和规则维护；2. 学院：负责预警通知和学业干预；3. 辅导员：负责跟踪学生；4. 学生：配合干预措施','学生对预警结果有异议的，可在收到通知后5个工作日内向学院提交申诉材料，学院在10个工作日内给出复议结果','因不可抗力导致的挂科可申请特殊处理，需提供相关证明材料，经学院审核批准后可不计入挂科统计');
INSERT IGNORE INTO `warning_rules` (`id`, `name`, `description`, `level`, `rule_condition`, `status`, `created_at`, `updated_at`, `rule_code`, `created_by`, `updated_by`, `fail_definition`, `statistic_cycle`, `trigger_type`, `release_condition`, `intervention_plan`, `notification_flow`, `responsible_person`, `tracking_mechanism`, `archive_spec`, `data_caliber`, `department_responsibility`, `appeal_process`, `exception_handling`) VALUES (3,'高级预警','学生在所有学期内累计挂科次数超过5科（不含5科）',3,'fail_count > 5',1,'2026-05-20 11:14:57','2026-05-20 11:14:57','RULE003',NULL,NULL,'课程考核成绩低于60分（不含60分）视为不及格；未参加考试（缺考）视为挂科；考试作弊取消成绩视为挂科','full_program','real_time','补考通过或重修合格后自动解除预警；累计挂科超过8科建议留级处理','1. 学院院长约谈；2. 制定严格的学业改进计划；3. 家长到校沟通；4. 每日学习打卡；5. 可能建议留级','通知对象：教师、学生、辅导员、管理员。流程：系统实时生成预警，立即通知教师、学生、辅导员和管理员，学院在3个工作日内组织专项会议','学院院长','辅导员每日跟踪，学院成立专项帮扶小组，每周向教务处上报进展','预警记录保存至学生毕业离校后5年，电子档案与纸质档案同步归档','挂科次数统计范围：学生入学以来所有学期的不及格课程（不含已通过补考/重修的课程）','1. 教务处：负责数据统计和规则维护；2. 学院：负责预警通知和学业干预；3. 辅导员：负责跟踪学生；4. 学生：配合干预措施','学生对预警结果有异议的，可在收到通知后5个工作日内向学院提交申诉材料，学院在10个工作日内给出复议结果','因不可抗力导致的挂科可申请特殊处理，需提供相关证明材料，经学院审核批准后可不计入挂科统计');
INSERT IGNORE INTO `warning_rules` (`id`, `name`, `description`, `level`, `rule_condition`, `status`, `created_at`, `updated_at`, `rule_code`, `created_by`, `updated_by`, `fail_definition`, `statistic_cycle`, `trigger_type`, `release_condition`, `intervention_plan`, `notification_flow`, `responsible_person`, `tracking_mechanism`, `archive_spec`, `data_caliber`, `department_responsibility`, `appeal_process`, `exception_handling`) VALUES (4,'GPA过低预警','学期GPA低于2.0触发预警',2,'gpa < 2.0',1,'2026-05-27 10:00:00','2026-05-27 10:00:00','RULE_GPA_LOW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);

-- 成绩（student_id=1 是 student_profile.id；学期 3/4 = 2024-2025-1 / 2024-2025-2）
-- 全部为及格成绩，seed 库不预置挂科，避免与预警引擎的预期结果混淆。
INSERT IGNORE INTO scores (id, student_id, course_id, semester, year, regular_score, final_score, score_total, grade, grade_point) VALUES
(1, 1, 1, 3, 2024, 86.00, 84.00, 85.00, 'A-', 3.70),
(2, 1, 2, 3, 2024, 80.00, 78.00, 79.00, 'B', 3.00),
(3, 1, 3, 3, 2024, 95.00, 91.00, 93.00, 'A', 4.00),
(4, 1, 4, 4, 2024, 88.00, 88.00, 88.00, 'A-', 3.70),
(5, 1, 5, 4, 2024, 76.00, 72.00, 74.00, 'B', 2.30);

-- 订阅偏好（学生 id 列名是 student_id，不是 user_id；列是一组开关而非 warning_alert/score_update）
INSERT IGNORE INTO subscription_preferences (id, student_id, subscribe_warnings, subscribe_low, subscribe_medium, subscribe_high, subscribe_assistance, subscribe_system, push_email, push_sms, push_app) VALUES
(1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1);
