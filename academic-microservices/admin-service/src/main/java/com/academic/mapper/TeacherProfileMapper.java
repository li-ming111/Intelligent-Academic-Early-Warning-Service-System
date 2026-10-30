package com.academic.mapper;

import com.academic.common.entity.TeacherProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 教师个人资料Mapper
 */
@Mapper
public interface TeacherProfileMapper extends BaseMapper<TeacherProfile> {
    
    @Select("SELECT COUNT(*) FROM teacher_profile WHERE college_id = #{collegeId}")
    long countByCollegeId(Long collegeId);

    /**
     * 该教师（参数是 {@code users.id}）在**没有外键**的表里还被引用多少行。
     *
     * <p>{@code classes.teacher_id} / {@code courses.teacher_id} /
     * {@code teacher_class_application.teacher_id} 都没有外键约束，删账号不会报错，
     * 只会留下指向不存在教师的孤儿行 —— 所以删除前必须单独问一次。
     * 键名与 {@code describeBlockingReferences} 的标签表对齐。
     */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM classes WHERE teacher_id = #{userId}) AS classes, "
            + "(SELECT COUNT(*) FROM courses WHERE teacher_id = #{userId}) AS teacherCourses, "
            + "(SELECT COUNT(*) FROM teacher_class_application WHERE teacher_id = #{userId}) AS classApplications, "
            + "(SELECT COUNT(*) FROM class_management_request WHERE teacher_id = #{userId}) AS classManagement, "
            + "(SELECT COUNT(*) FROM feedbacks WHERE teacher_id = #{userId}) AS feedbacks")
    Map<String, Object> countReferences(@Param("userId") Long userId);
}
