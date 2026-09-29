package com.academic.mapper;

import com.academic.entity.StudentProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentProfileMapper extends BaseMapper<StudentProfile> {
    
    @Select("SELECT * FROM student_profile WHERE student_id = #{studentId}")
    StudentProfile selectByStudentId(String studentId);

    @Select("SELECT * FROM student_profile WHERE user_id = #{userId}")
    StudentProfile selectByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM student_profile WHERE college_id = #{collegeId}")
    long countByCollegeId(Long collegeId);

    /**
     * 专业名称。{@code student_profile} 只存 {@code major_id}，展示需要名称 ——
     * 仪表盘此前把专业名写死成"计算机科学与技术"，换专业的学生看到的专业是错的。
     */
    @Select("SELECT name FROM majors WHERE id = #{majorId}")
    String selectMajorName(Long majorId);
    
    @Select("SELECT * FROM student_profile WHERE college_id = #{collegeId}")
    java.util.List<StudentProfile> selectByCollegeId(Long collegeId);
}
