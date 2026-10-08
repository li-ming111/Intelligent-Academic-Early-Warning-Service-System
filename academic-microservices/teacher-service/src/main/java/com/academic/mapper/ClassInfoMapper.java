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
     *
     * <p>2026-10-02（W4.11）：补 {@code profileId} 与 {@code userStatus}。
     * 前者（{@code student_profile.id}）是 {@code academic_warning.student_id} /
     * {@code scores.student_id} 的取值空间，与能给人看的学号 {@code studentId} 是两回事；
     * 后者是 {@code users.status}，供「学生详情」显示真实账号状态 ——
     * 那一格此前写死 <code>正常</code>，而 {@code users.status} 是可以被管理员改的。
     */
    @Select("SELECT sp.student_id as studentId, sp.id as profileId, u.name as studentName, "
          + "       u.status as userStatus "
          + "  FROM student_profile sp JOIN users u ON sp.user_id = u.id "
          + " WHERE sp.class_id = #{classId}")
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

    /**
     * 学生的姓名（{@code student_profile → users.name}）。2026-10-04（W19）补，给教师端
     * 成绩导入用：Excel 里「姓名」列是可选的，没填时用它回填 {@code score_record.student_name}
     * —— 否则导入的行在成绩列表里没有姓名，变成一串"光有分数没有名字"的记录。
     *
     * <p>同样**刻意拆成单列标量**而不是返回 Map：{@code mapUnderscoreToCamelCase} 开着时
     * Map 结果的 key 是 {@code name} 还是别的取决于版本，取错就静默当成"查不到学生"。
     */
    @Select("SELECT u.name FROM student_profile sp JOIN users u ON sp.user_id = u.id "
          + "WHERE sp.student_id = #{studentId}")
    String findStudentNameByStudentId(@Param("studentId") String studentId);

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
