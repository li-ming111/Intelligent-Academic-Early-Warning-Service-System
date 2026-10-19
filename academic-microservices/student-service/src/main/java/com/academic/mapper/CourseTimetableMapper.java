package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 学生端读「我的课表」（W48，2026-10-17）。
 *
 * <p>数据源是 {@code course_plans}（排课计划：哪门课、谁教、哪个学期、上课时间、教室、容量）。
 * 此前这张表和 {@code CoursePlanController} 都齐备，但**没有网关路由、没有前端入口** ——
 * 学生/教师端看不到课表。本 Mapper 是"学生视角"的读取口。
 *
 * <p>关联口径（已逐张表核对，别凭列名猜）：
 * <ul>
 *   <li>入参 {@code userId} 是 <b>{@code users.id}</b>（与 {@code /students/**} 其余端点同套）；
 *       {@code student_profile.user_id → .id} 才是成绩/选课用的 profile id；</li>
 *   <li>{@code enrollments.student_id} = <b>{@code student_profile.id}</b>；
 *       {@code enrollments.course_id} = {@code courses.id}；</li>
 *   <li>{@code course_plans.course_id} = {@code courses.id}；</li>
 *   <li>{@code course_plans.teacher_id} = <b>{@code users.id}</b>（第三套 id 空间 ——
 *       注意它**不是** {@code teacher_profile.id}，与 {@code teacher_course.teacher_id} 相反）；
 *       故教师名用 {@code teacher_profile.user_id = cp.teacher_id} 关联，
 *       取不到再退回 {@code users.name}。</li>
 * </ul>
 *
 * <p><b>学期口径不通用的提醒</b>：{@code course_plans.semester} 是 <b>varchar</b>（如
 * {@code "2025-2026-2"}）+ {@code year=2026}，而 {@code enrollments.semester} 是
 * <b>int</b>（如 {@code 2}）+ {@code year=2025} —— 同一个学期两种编码，**不能直接等值连接**。
 * 故本查询**不按学期过滤**（会把真课表滤空），只回学生所选课程的全部有效排课，
 * 学期原样带回给前端展示。
 *
 * <p>学生没有选课记录时回空列表（JOIN 不成立），不会漏成"看到全部课表"。
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
          + "       COALESCE(tp.name, u.name) AS teacherName "
          + "  FROM course_plans cp "
          + "  JOIN courses c      ON c.id = cp.course_id "
          + "  JOIN student_profile me ON me.user_id = #{userId} "
          + "  JOIN enrollments e  ON e.course_id = cp.course_id "
          + "                     AND e.student_id = me.id "
          + "                     AND e.status = 1 "
          + "  LEFT JOIN teacher_profile tp ON tp.user_id = cp.teacher_id "
          + "  LEFT JOIN users u            ON u.id = cp.teacher_id "
          + " WHERE cp.status = 1 "
          + " ORDER BY cp.id")
    List<Map<String, Object>> listForStudent(@Param("userId") Long userId);
}
