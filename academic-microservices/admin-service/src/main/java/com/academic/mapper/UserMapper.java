package com.academic.mapper;

import com.academic.common.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 数这个账号在别处被引用了多少行 —— "删除账号"的前置检查。
     *
     * 为什么必须有这一步：`users` 被 6 张表引用（user_settings / notifications /
     * messages / security_logs / audit_logs / admin_profile），`student_profile` 被 9 张
     * （scores / academic_warning / enrollments / score_appeals / assistance_plans /
     * student_settings / subscription_preferences / benchmark_analysis / feedbacks），
     * `teacher_profile` 被 teacher_course 引用。**这些外键全是 NO ACTION**，所以只要
     * 还剩一行，硬删就必然失败；而 deleteUser 只清理四张 profile 表，其余一律不管。
     *
     * 2026-09-30 实测：35 个演示账号里 34 个过不了这一关（学生卡在 student_profile
     * 那一步、管理员/辅导员/学院管理员卡在账号级那一步）。以前的表现是管理员吃一个
     * HTTP 500「系统内部错误」，既不知道原因也不知道能不能修。
     *
     * 返回按类别分列（而不是一个总数），这样可以把"被什么挡住"原样讲给管理员听。
     */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM scores WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS scores, "
            + "(SELECT COUNT(*) FROM academic_warning WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS warnings, "
            + "(SELECT COUNT(*) FROM enrollments WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS enrollments, "
            + "(SELECT COUNT(*) FROM score_appeals WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS appeals, "
            + "(SELECT COUNT(*) FROM assistance_plans WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS assistancePlans, "
            + "(SELECT COUNT(*) FROM student_settings WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS studentSettings, "
            + "(SELECT COUNT(*) FROM subscription_preferences WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS subscriptions, "
            + "(SELECT COUNT(*) FROM benchmark_analysis WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS benchmarks, "
            + "(SELECT COUNT(*) FROM feedbacks WHERE student_id IN (SELECT id FROM student_profile WHERE user_id = #{userId})) AS feedbacks, "
            + "(SELECT COUNT(*) FROM teacher_course WHERE teacher_id IN (SELECT id FROM teacher_profile WHERE user_id = #{userId})) AS teacherCourses, "
            + "(SELECT COUNT(*) FROM user_settings WHERE user_id = #{userId}) AS userSettings, "
            + "(SELECT COUNT(*) FROM notifications WHERE user_id = #{userId}) AS notifications, "
            + "(SELECT COUNT(*) FROM messages WHERE sender_id = #{userId} OR receiver_id = #{userId}) AS messages, "
            + "(SELECT COUNT(*) FROM security_logs WHERE user_id = #{userId}) AS securityLogs, "
            + "(SELECT COUNT(*) FROM audit_logs WHERE user_id = #{userId}) AS auditLogs, "
            + "(SELECT COUNT(*) FROM admin_profile WHERE user_id = #{userId}) AS adminProfile")
    Map<String, Object> countReferences(@Param("userId") Long userId);
}
