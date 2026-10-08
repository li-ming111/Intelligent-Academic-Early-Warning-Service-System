package com.academic.mapper;

import com.academic.common.entity.CourseRecommendation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 教师「推荐选课」Mapper（W14，2026-10-04）。
 */
@Mapper
public interface CourseRecommendationMapper extends BaseMapper<CourseRecommendation> {

    /**
     * 这条推荐会落到多少名学生眼里：**教师名下班级**的学生数（可按班收窄）。
     *
     * <p>口径与 {@code classes.teacher_id} 一致 —— 推荐是发给"我带的班"的，
     * 不是发给"选了我课的人"。两者在活库都能取到，选前者是因为
     * {@code target_class_id} 这个可选参数本身就是班级维度。
     */
    @Select("<script>"
          + "SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id = sp.class_id "
          + " WHERE c.teacher_id = #{teacherId} "
          + "<if test='targetClassId != null'> AND c.id = #{targetClassId}</if>"
          + "</script>")
    int countRecipients(@Param("teacherId") Long teacherId,
                        @Param("targetClassId") Long targetClassId);

    /** 我发出过的推荐（教师端回看用）。 */
    @Select("SELECT r.id, r.course_id AS courseId, c.name AS courseName, c.credits AS credits, "
          + "       r.target_class_id AS targetClassId, cl.name AS targetClassName, "
          + "       r.reason, r.status, r.created_at AS createdAt "
          + "  FROM course_recommendation r "
          + "  JOIN courses c ON c.id = r.course_id "
          + "  LEFT JOIN classes cl ON cl.id = r.target_class_id "
          + " WHERE r.teacher_id = #{teacherId} "
          + " ORDER BY r.created_at DESC, r.id DESC")
    List<Map<String, Object>> listByTeacher(@Param("teacherId") Long teacherId);
}
