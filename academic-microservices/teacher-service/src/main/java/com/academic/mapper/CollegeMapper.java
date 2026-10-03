package com.academic.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 读 `colleges` 表 —— 本模块只用"按 id 取学院名"这一件。
 *
 * <p>2026-10-03（W8.2）：教师端「个人设置 → 工作信息 → 学院」原来是一个
 * <b>disabled 的空框、恒空</b> —— 它想拿的学院没有任何接口给：`/teachers/dashboard/{userId}`
 * 只返回 `{pendingFeedbacks,totalClasses,teacherName,pendingRequests,totalStudents,totalCourses}`。
 * 而 `teacher_profile.college_id` 是**有值的**（活库 6 行分别指向学院 1/2），
 * 按本仓一贯口径「表里真有那一列就补后端字段」（不是把格子删掉），这里把学院名带去 dashboard。
 *
 * <p>用注解式 {@code @Select} 而**不继承 {@code BaseMapper}**：本模块其余 mapper
 * （{@code FeedbackMapper}、{@code ClassInfoMapper} …）都是这个写法，保持一致，
 * 也免得为一个单列查询引入整表映射。
 */
public interface CollegeMapper {

    /** 按 id 取学院名；不存在返回 null。 */
    @Select("SELECT name FROM colleges WHERE id = #{id}")
    String getNameById(Long id);
}
