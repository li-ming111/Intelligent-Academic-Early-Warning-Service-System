package com.academic.mapper;

import com.academic.entity.ClassInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 班级Mapper接口
 */
@Mapper
public interface ClassInfoMapper extends BaseMapper<ClassInfo> {

    /**
     * 根据辅导员ID获取管理的班级
     */
    @Select("SELECT c.id, c.name, c.college_id as collegeId, c.major_id as majorId, COUNT(sp.id) as studentCount FROM classes c LEFT JOIN student_profile sp ON c.id = sp.class_id WHERE c.counselor_id = #{counselorId} GROUP BY c.id")
    List<Map<String, Object>> getClassesByCounselorIdMap(@Param("counselorId") Long counselorId);

    /**
     * 搜索班级（按名称）
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(sp.id) as studentCount, CASE WHEN c.counselor_id IS NOT NULL THEN 1 ELSE 0 END as hasCounselor FROM classes c LEFT JOIN student_profile sp ON c.id = sp.class_id WHERE c.name LIKE CONCAT('%', #{keyword}, '%') GROUP BY c.id")
    List<Map<String, Object>> searchClassesMap(@Param("keyword") String keyword);

    /**
     * 获取所有班级
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(sp.id) as studentCount, CASE WHEN c.counselor_id IS NOT NULL THEN 1 ELSE 0 END as hasCounselor FROM classes c LEFT JOIN student_profile sp ON c.id = sp.class_id GROUP BY c.id")
    List<Map<String, Object>> getAllClassesMap();

    /**
     * 获取无辅导员的班级
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(sp.id) as studentCount, 0 as hasCounselor FROM classes c LEFT JOIN student_profile sp ON c.id = sp.class_id WHERE c.counselor_id IS NULL OR c.counselor_id = 0 GROUP BY c.id")
    List<Map<String, Object>> getClassesWithoutCounselor();

    /**
     * 根据班级ID获取学生列表
     *
     * <p>⚠️ 2026-10-01 第二十一轮修: 原 SQL 写的是 {@code sp.phone}, 而
     * {@code student_profile} **根本没有 phone 列**(实测 `SHOW COLUMNS FROM student_profile`
     * 只 13 列, 无 phone; `Student.phone` 在实体上也是 `@TableField(exist = false)`)。
     * ⇒ 这条查询**每次必然** `ERROR 1054 Unknown column 'sp.phone'`, 于是依赖它的三个端点
     * ({@code /counselors/classes/{id}/students}、{@code /detail}、{@code /warnings})
     * 全部返回 `ApiResponse.serverError`(HTTP 200 + body.code 500) —— 辅导员端「我的班级」
     * 的花名册恒空、「班主任/辅导员」恒「未指派/无」。手机号在 **users** 表上(35/35 有值)。
     *
     * <p>⚠️ 同时新增 {@code sp.id as profileId}: 这里**两个 id 概念必须分清** ——
     * {@code studentId}(= {@code student_profile.student_id}, 是**学号字符串** '2023020701')
     * 是给人看的;{@code profileId}(= {@code student_profile.id}) 才是
     * {@code academic_warning.student_id} / {@code scores.student_id} 的外键值。
     * 凡是要拿这个列表回去查这两张表的调用方, **必须用 profileId**。
     */
    @Select("SELECT sp.student_id as studentId, sp.id as profileId, u.name as studentName, sp.gender, u.phone, "
          // 2026-10-02（W4.11）：`users.status` —— 辅导员「学生详情」的状态格原来写死"正常"。
          + "       u.status as userStatus "
          + "  FROM student_profile sp JOIN users u ON sp.user_id = u.id "
          + " WHERE sp.class_id = #{classId}")
    List<Map<String, Object>> getStudentsByClassIdMap(@Param("classId") Long classId);
}
