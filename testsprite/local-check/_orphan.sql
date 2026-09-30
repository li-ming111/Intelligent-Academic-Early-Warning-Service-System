SELECT 'academic_warning.rule_id' AS col, COUNT(*) AS dangling, COUNT(rule_id) AS notnull FROM `academic_warning` WHERE `rule_id` IS NOT NULL AND `rule_id` NOT IN (SELECT id FROM `warning_rules`)
UNION ALL
SELECT 'academic_warning.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `academic_warning` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'admin_profile.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `admin_profile` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'assistance_evaluations.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `assistance_evaluations` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'assistance_evaluations.plan_id' AS col, COUNT(*) AS dangling, COUNT(plan_id) AS notnull FROM `assistance_evaluations` WHERE `plan_id` IS NOT NULL AND `plan_id` NOT IN (SELECT id FROM `assistance_plans`)
UNION ALL
SELECT 'assistance_plans.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `assistance_plans` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'assistance_plans.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `assistance_plans` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'audit_logs.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `audit_logs` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'benchmark_analysis.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `benchmark_analysis` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'benchmark_analysis.major_id' AS col, COUNT(*) AS dangling, COUNT(major_id) AS notnull FROM `benchmark_analysis` WHERE `major_id` IS NOT NULL AND `major_id` NOT IN (SELECT id FROM `majors`)
UNION ALL
SELECT 'benchmark_analysis.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `benchmark_analysis` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'class_management_request.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `class_management_request` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'class_management_request.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `class_management_request` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'class_management_request.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `class_management_request` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'classes.college_id' AS col, COUNT(*) AS dangling, COUNT(college_id) AS notnull FROM `classes` WHERE `college_id` IS NOT NULL AND `college_id` NOT IN (SELECT id FROM `colleges`)
UNION ALL
SELECT 'classes.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `classes` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'classes.major_id' AS col, COUNT(*) AS dangling, COUNT(major_id) AS notnull FROM `classes` WHERE `major_id` IS NOT NULL AND `major_id` NOT IN (SELECT id FROM `majors`)
UNION ALL
SELECT 'classes.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `classes` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'college_admin_profile.college_id' AS col, COUNT(*) AS dangling, COUNT(college_id) AS notnull FROM `college_admin_profile` WHERE `college_id` IS NOT NULL AND `college_id` NOT IN (SELECT id FROM `colleges`)
UNION ALL
SELECT 'college_admin_profile.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `college_admin_profile` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'communication_logs.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `communication_logs` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'communication_logs.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `communication_logs` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'communication_logs.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `communication_logs` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'communication_logs.warning_id' AS col, COUNT(*) AS dangling, COUNT(warning_id) AS notnull FROM `communication_logs` WHERE `warning_id` IS NOT NULL AND `warning_id` NOT IN (SELECT id FROM `academic_warning`)
UNION ALL
SELECT 'communication_record.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `communication_record` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'communication_record.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `communication_record` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'counselor_class_application.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `counselor_class_application` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'counselor_class_application.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `counselor_class_application` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'counselor_class_management.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `counselor_class_management` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'counselor_class_management.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `counselor_class_management` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'counselor_profiles.college_id' AS col, COUNT(*) AS dangling, COUNT(college_id) AS notnull FROM `counselor_profiles` WHERE `college_id` IS NOT NULL AND `college_id` NOT IN (SELECT id FROM `colleges`)
UNION ALL
SELECT 'counselor_profiles.counselor_id' AS col, COUNT(*) AS dangling, COUNT(counselor_id) AS notnull FROM `counselor_profiles` WHERE `counselor_id` IS NOT NULL AND `counselor_id` NOT IN (SELECT id FROM `counselor_profiles` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'counselor_profiles.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `counselor_profiles` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'course_plans.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `course_plans` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'course_plans.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `course_plans` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'courses.module_id' AS col, COUNT(*) AS dangling, COUNT(module_id) AS notnull FROM `courses` WHERE `module_id` IS NOT NULL AND `module_id` NOT IN (SELECT id FROM `elective_modules`)
UNION ALL
SELECT 'courses.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `courses` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'enrollments.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `enrollments` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'enrollments.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `enrollments` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'enrollments.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `enrollments` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'fail_records.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `fail_records` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'fail_records.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `fail_records` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'feedback.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `feedback` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'feedbacks.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `feedbacks` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'feedbacks.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `feedbacks` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'login_logs.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `login_logs` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'majors.college_id' AS col, COUNT(*) AS dangling, COUNT(college_id) AS notnull FROM `majors` WHERE `college_id` IS NOT NULL AND `college_id` NOT IN (SELECT id FROM `colleges`)
UNION ALL
SELECT 'messages.receiver_id' AS col, COUNT(*) AS dangling, COUNT(receiver_id) AS notnull FROM `messages` WHERE `receiver_id` IS NOT NULL AND `receiver_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'messages.sender_id' AS col, COUNT(*) AS dangling, COUNT(sender_id) AS notnull FROM `messages` WHERE `sender_id` IS NOT NULL AND `sender_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'notifications.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `notifications` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'notifications.warning_id' AS col, COUNT(*) AS dangling, COUNT(warning_id) AS notnull FROM `notifications` WHERE `warning_id` IS NOT NULL AND `warning_id` NOT IN (SELECT id FROM `academic_warning`)
UNION ALL
SELECT 'role_permissions.permission_id' AS col, COUNT(*) AS dangling, COUNT(permission_id) AS notnull FROM `role_permissions` WHERE `permission_id` IS NOT NULL AND `permission_id` NOT IN (SELECT id FROM `permissions`)
UNION ALL
SELECT 'role_permissions.role_id' AS col, COUNT(*) AS dangling, COUNT(role_id) AS notnull FROM `role_permissions` WHERE `role_id` IS NOT NULL AND `role_id` NOT IN (SELECT id FROM `roles`)
UNION ALL
SELECT 'score_appeals.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `score_appeals` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'score_appeals.score_id' AS col, COUNT(*) AS dangling, COUNT(score_id) AS notnull FROM `score_appeals` WHERE `score_id` IS NOT NULL AND `score_id` NOT IN (SELECT id FROM `scores`)
UNION ALL
SELECT 'score_appeals.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `score_appeals` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'score_appeals.warning_id' AS col, COUNT(*) AS dangling, COUNT(warning_id) AS notnull FROM `score_appeals` WHERE `warning_id` IS NOT NULL AND `warning_id` NOT IN (SELECT id FROM `academic_warning`)
UNION ALL
SELECT 'score_audit_log.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `score_audit_log` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'score_audit_log.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `score_audit_log` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'score_audit_log.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `score_audit_log` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'score_record.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `score_record` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'score_record.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `score_record` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'score_record.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `score_record` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'scores.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `scores` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'scores.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `scores` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'security_logs.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `security_logs` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'student_profile.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `student_profile` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'student_profile.college_id' AS col, COUNT(*) AS dangling, COUNT(college_id) AS notnull FROM `student_profile` WHERE `college_id` IS NOT NULL AND `college_id` NOT IN (SELECT id FROM `colleges`)
UNION ALL
SELECT 'student_profile.major_id' AS col, COUNT(*) AS dangling, COUNT(major_id) AS notnull FROM `student_profile` WHERE `major_id` IS NOT NULL AND `major_id` NOT IN (SELECT id FROM `majors`)
UNION ALL
SELECT 'student_profile.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `student_profile` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'student_profile.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `student_profile` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'student_settings.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `student_settings` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'subscription_preferences.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `subscription_preferences` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'teacher_class_application.class_id' AS col, COUNT(*) AS dangling, COUNT(class_id) AS notnull FROM `teacher_class_application` WHERE `class_id` IS NOT NULL AND `class_id` NOT IN (SELECT id FROM `classes`)
UNION ALL
SELECT 'teacher_class_application.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `teacher_class_application` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'teacher_course.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `teacher_course` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'teacher_course.teacher_id' AS col, COUNT(*) AS dangling, COUNT(teacher_id) AS notnull FROM `teacher_course` WHERE `teacher_id` IS NOT NULL AND `teacher_id` NOT IN (SELECT id FROM `teacher_profile` UNION SELECT id FROM `users`)
UNION ALL
SELECT 'teacher_profile.college_id' AS col, COUNT(*) AS dangling, COUNT(college_id) AS notnull FROM `teacher_profile` WHERE `college_id` IS NOT NULL AND `college_id` NOT IN (SELECT id FROM `colleges`)
UNION ALL
SELECT 'teacher_profile.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `teacher_profile` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'teaching_resources.course_id' AS col, COUNT(*) AS dangling, COUNT(course_id) AS notnull FROM `teaching_resources` WHERE `course_id` IS NOT NULL AND `course_id` NOT IN (SELECT id FROM `courses`)
UNION ALL
SELECT 'user_settings.user_id' AS col, COUNT(*) AS dangling, COUNT(user_id) AS notnull FROM `user_settings` WHERE `user_id` IS NOT NULL AND `user_id` NOT IN (SELECT id FROM `users`)
UNION ALL
SELECT 'warning_release_records.student_id' AS col, COUNT(*) AS dangling, COUNT(student_id) AS notnull FROM `warning_release_records` WHERE `student_id` IS NOT NULL AND `student_id` NOT IN (SELECT id FROM `student_profile`)
UNION ALL
SELECT 'warning_release_records.warning_id' AS col, COUNT(*) AS dangling, COUNT(warning_id) AS notnull FROM `warning_release_records` WHERE `warning_id` IS NOT NULL AND `warning_id` NOT IN (SELECT id FROM `academic_warning`)
UNION ALL
SELECT 'warning_tracking_records.warning_id' AS col, COUNT(*) AS dangling, COUNT(warning_id) AS notnull FROM `warning_tracking_records` WHERE `warning_id` IS NOT NULL AND `warning_id` NOT IN (SELECT id FROM `academic_warning`);
