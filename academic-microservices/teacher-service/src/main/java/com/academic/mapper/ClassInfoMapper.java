package com.academic.mapper;

import com.academic.entity.ClassInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 班级Mapper接口
 */
@Mapper
public interface ClassInfoMapper extends BaseMapper<ClassInfo> {

    /**
     * 根据教师ID获取管理的班级
     */
    @Select("SELECT c.id, c.name, c.college_id as collegeId, c.major_id as majorId, COUNT(s.id) as studentCount FROM classes c LEFT JOIN student_profile s ON c.id = s.class_id WHERE c.teacher_id = #{teacherId} GROUP BY c.id")
    List<Map<String, Object>> getClassesByTeacherIdMap(@Param("teacherId") Long teacherId);

    /**
     * 搜索班级（按名称）
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(s.id) as studentCount, CASE WHEN c.teacher_id IS NOT NULL THEN 1 ELSE 0 END as hasTeacher FROM classes c LEFT JOIN student_profile s ON c.id = s.class_id WHERE c.name LIKE CONCAT('%', #{keyword}, '%') GROUP BY c.id")
    List<Map<String, Object>> searchClassesMap(@Param("keyword") String keyword);

    /**
     * 获取所有班级
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(s.id) as studentCount, CASE WHEN c.teacher_id IS NOT NULL THEN 1 ELSE 0 END as hasTeacher FROM classes c LEFT JOIN student_profile s ON c.id = s.class_id GROUP BY c.id")
    List<Map<String, Object>> getAllClassesMap();

    /**
     * 根据班级ID获取学生列表
     */
    @Select("SELECT sp.student_id as studentId, u.name as studentName FROM student_profile sp JOIN users u ON sp.user_id = u.id WHERE sp.class_id = #{classId}")
    List<Map<String, Object>> getStudentsByClassIdMap(@Param("classId") Long classId);

    /**
     * 按学号找学生档案 id（{@code student_profile.id}，不是 users.id、也不是学号）。
     *
     * <p>分两个单列查询而不是一个 {@code Map} 结果：`mapUnderscoreToCamelCase` 开着的时候
     * Map 结果的 key 究竟是 {@code class_id} 还是 {@code classId} 取决于版本，
     * 取错了会静默当成"该生目前没有班级"。单列标量没有这个歧义。
     */
    @Select("SELECT id FROM student_profile WHERE student_id = #{studentId}")
    Long findProfileIdByStudentId(@Param("studentId") String studentId);

    /** 该学生档案当前的班级（null = 尚未归入任何班级）。 */
    @Select("SELECT class_id FROM student_profile WHERE id = #{profileId}")
    Long findClassIdOfProfile(@Param("profileId") Long profileId);

    /**
     * 把学生归入班级。
     *
     * <p>只改 {@code student_profile.class_id} 与 {@code class_name} —— 班级名单、班级人数、
     * 按班统计的预警都读这两列（见本类上面几条 SQL），没有别的"成员表"。
     * 一行一个 UPDATE，本身就是原子的。
     */
    @Update("UPDATE student_profile SET class_id = #{classId}, class_name = #{className}, updated_at = NOW() WHERE id = #{profileId}")
    int assignProfileToClass(@Param("profileId") Long profileId,
                             @Param("classId") Long classId,
                             @Param("className") String className);

    /**
     * 根据班级名称获取辅导员信息
     */
    @Select("SELECT c.counselor_id as counselorId, cp.name as counselorName, cp.phone as counselorPhone FROM classes c LEFT JOIN counselor_profiles cp ON c.counselor_id = cp.id WHERE c.name = #{className}")
    Map<String, Object> getCounselorByClassName(@Param("className") String className);
}
