package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 学生端读「老师推荐选课」（W14，2026-10-04）。
 *
 * <p>与 teacher-service 的同名 Mapper 分工不同：那边写 + 教师回看，这边只读、且是
 * **学生视角的可见性**。可见性规则和 {@code target_class_id} 的语义是配套的：
 * <ul>
 *   <li>点名推荐给某个班 ⇒ 那个班的学生看得见；</li>
 *   <li>{@code target_class_id IS NULL}（"发给全班"）⇒ 只有**推荐人自己带的班**看得见，
 *       不是全校都看得见 —— 否则任何教师发一条就成了全校公告。</li>
 * </ul>
 *
 * <p>入参是 {@code users.id}（与 {@code /students/**} 其余端点同一套）。
 * 学生没有班级（{@code student_profile.class_id IS NULL}）时，两个条件都因 NULL 比较而不成立
 * ⇒ 回空列表（fail-closed，不会漏成"全校可见"）。
 */
@Mapper
public interface CourseRecommendationMapper {

    @Select("SELECT r.id, r.course_id AS courseId, c.name AS courseName, c.credits AS credits, "
          + "       r.reason, r.created_at AS createdAt, "
          + "       COALESCE(tp.name, u.name) AS teacherName "
          + "  FROM course_recommendation r "
          + "  JOIN courses c ON c.id = r.course_id "
          + "  JOIN student_profile me ON me.user_id = #{userId} "
          + "  LEFT JOIN teacher_profile tp ON tp.user_id = r.teacher_id "
          + "  LEFT JOIN users u ON u.id = r.teacher_id "
          + " WHERE r.status = 1 "
          + "   AND ( r.target_class_id = me.class_id "
          + "         OR ( r.target_class_id IS NULL "
          + "              AND EXISTS (SELECT 1 FROM classes cl "
          + "                           WHERE cl.id = me.class_id AND cl.teacher_id = r.teacher_id) ) ) "
          + " ORDER BY r.created_at DESC, r.id DESC")
    List<Map<String, Object>> listForStudent(@Param("userId") Long userId);
}
