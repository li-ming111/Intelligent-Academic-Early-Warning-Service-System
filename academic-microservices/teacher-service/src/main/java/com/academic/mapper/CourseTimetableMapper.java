package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 教师端读「我的课表」（W48，2026-10-17）。
 *
 * <p>数据源 {@code course_plans}（排课计划）。教师此前看不到自己的上课安排 ——
 * 这块能力后端齐备却无入口，本 Mapper 是教师视角的读取口。
 *
 * <p>关联口径（已逐张表核对）：
 * <ul>
 *   <li>入参 {@code userId} 是 <b>{@code users.id}</b>（与 {@code /teachers/**} 其余端点同套，
 *       也与 {@link #listForTeacher} 的归属闸 {@code isSelf} 一致）；</li>
 *   <li>{@code course_plans.teacher_id} = <b>{@code users.id}</b> —— ⚠️ 注意这是**第三套 id 空间**：
 *       {@code course_plans.teacher_id} 用 users.id，而 {@code teacher_course.teacher_id} 用
 *       {@code teacher_profile.id}。两者**不相通**，别互相代入；</li>
 *   <li>{@code course_plans.course_id} = {@code courses.id}。</li>
 * </ul>
 *
 * <p>「上课人数」由 {@code enrollments} 现算（{@code status=1} 为在读），不是 {@code max_students}
 * 那个容量 —— 容量是计划值，这里是实际选课数，两者含义不同，分别回给前端。
 */
@Mapper
public interface CourseTimetableMapper {

    @Select("SELECT cp.id            AS planId, "
          + "       cp.course_id     AS courseId, "
          + "       c.name           AS courseName, "
          + "       c.code           AS courseCode, "
          + "       c.credits        AS credits, "
          + "       c.type           AS courseType, "
          + "       cp.class_time    AS classTime, "
          + "       cp.location      AS location, "
          + "       cp.semester      AS semester, "
          + "       cp.year          AS year, "
          + "       cp.start_date    AS startDate, "
          + "       cp.end_date      AS endDate, "
          + "       cp.max_students  AS maxStudents, "
          + "       (SELECT COUNT(*) FROM enrollments e "
          + "         WHERE e.course_id = cp.course_id AND e.status = 1) AS enrolledCount "
          + "  FROM course_plans cp "
          + "  JOIN courses c ON c.id = cp.course_id "
          + " WHERE cp.teacher_id = #{userId} "
          + "   AND cp.status = 1 "
          + " ORDER BY cp.id")
    List<Map<String, Object>> listForTeacher(@Param("userId") Long userId);
}
