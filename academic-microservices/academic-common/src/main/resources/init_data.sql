-- ========================================
-- 智能学业预警服务系统 - 种子数据
-- 所有 INSERT 使用 INSERT IGNORE，安全幂等
-- 密码均为 123456 的 BCrypt 编码
-- ========================================

-- 学院
INSERT IGNORE INTO colleges (id, name, code, description, status) VALUES
(1, '信息工程学院', 'IE', '信息工程学院', 1),
(2, '人工智能学院', 'AI', '人工智能学院', 1),
(3, '计算机科学学院', 'CS', '计算机科学学院', 1);

-- 专业
INSERT IGNORE INTO majors (id, name, code, college_id, description, status) VALUES
(1, '计算机科学与技术', 'CS001', 3, '计算机科学与技术专业', 1),
(2, '软件工程', 'SE001', 3, '软件工程专业', 1),
(3, '人工智能', 'AI001', 2, '人工智能专业', 1);

-- 班级
INSERT IGNORE INTO classes (id, name, college_id, major_id) VALUES
(1, '计科2305班', 3, 1),
(2, '计科2306班', 3, 1);

-- 用户（密码：123456 → BCrypt）
INSERT IGNORE INTO users (id, username, password, role, name, email, phone, status) VALUES
(1, 'admin', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 4, '管理员', 'admin@academic.com', '13800000001', 1),
(2, 'teacher001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 2, '张老师', 'teacher001@academic.com', '13800000002', 1),
(3, 'counselor001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 3, '李辅导员', 'counselor001@academic.com', '13800000003', 1),
(4, 'student001', '$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija', 1, '王同学', 'student001@academic.com', '13800000004', 1);

-- 学生档案
INSERT IGNORE INTO student_profile (id, user_id, student_id, name, college_id, major_id, class_id, enrollment_year) VALUES
(1, 4, '2023020616', '王同学', 3, 1, 1, 2023);

-- 教师档案
INSERT IGNORE INTO teacher_profile (id, user_id, teacher_id, name, college_id, title) VALUES
(1, 2, 'T001', '张老师', 3, '教授');

-- 辅导员档案
INSERT IGNORE INTO counselor_profile (id, user_id, counselor_id, name, college_id, department) VALUES
(1, 3, 'C001', '李辅导员', 3, '学生工作办公室');

-- 课程
INSERT IGNORE INTO courses (id, code, name, credits, type) VALUES
(1, 'CS101', '数据结构', 4.00, 'required'),
(2, 'CS102', '算法设计', 3.50, 'required'),
(3, 'CS201', 'Java程序设计', 4.00, 'required'),
(4, 'CS301', '数据库系统', 3.50, 'required'),
(5, 'CS401', '软件工程', 3.00, 'required');

-- 预警规则
INSERT IGNORE INTO warning_rules (id, rule_name, rule_code, warning_level, expression, description, status) VALUES
(1, '单科不及格', 'SCORE_BELOW_60', 2, 'score < 60', '单门课程成绩低于60分触发预警', 1),
(2, 'GPA低于3.0', 'GPA_BELOW_3', 1, 'gpa < 3.0', 'GPA低于3.0触发轻度预警', 1),
(3, '学分不足', 'CREDIT_INSUFFICIENT', 2, 'total_credits < required_credits', '获得学分低于毕业要求', 1);

-- 成绩（学生1的各科成绩）
INSERT IGNORE INTO scores (id, student_id, course_id, score, grade, semester) VALUES
(1, 4, 1, 85.0, 'B', '2024-2025-1'),
(2, 4, 2, 78.0, 'C', '2024-2025-1'),
(3, 4, 3, 92.0, 'A', '2024-2025-1'),
(4, 4, 4, 88.0, 'B', '2024-2025-1'),
(5, 4, 5, 73.0, 'C', '2024-2025-1');

-- 订阅偏好
INSERT IGNORE INTO subscription_preferences (id, user_id, warning_alert, score_update, system_notice) VALUES
(1, 4, 1, 1, 1);
