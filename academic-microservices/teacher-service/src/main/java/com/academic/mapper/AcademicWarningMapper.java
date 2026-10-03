package com.academic.mapper;

import com.academic.common.entity.AcademicWarning;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AcademicWarningMapper extends BaseMapper<AcademicWarning> {

    @Select("SELECT aw.* FROM academic_warning aw INNER JOIN student_profile sp ON aw.student_id = sp.id WHERE sp.class_id = #{classId} ORDER BY aw.created_at DESC")
    List<AcademicWarning> findByClassId(Long classId);

    /**
     * 按 id 取一条预警，但**只在这个学生挂在 {@code teacherId} 所带的班里**时才返回。
     *
     * <p>2026-10-04（W12）：给 {@code POST /warnings/{warningId}/process} 做归属闸。
     * {@code academic_warning} 自己没有 teacher 列，归属只能顺着
     * {@code student_id → student_profile.class_id → classes.teacher_id} 推；
     * {@code classes.teacher_id} 与 {@code aw.student_id = sp.id} 两处口径与
     * {@link #findByClassId} 完全一致（那一处是教师端预警列表用的）。
     * 找不到（不属于本人）就返回 {@code null} —— 调用方据此拒绝。
     */
    @Select("SELECT aw.* FROM academic_warning aw "
            + "INNER JOIN student_profile sp ON aw.student_id = sp.id "
            + "INNER JOIN classes c ON sp.class_id = c.id "
            + "WHERE aw.id = #{warningId} AND c.teacher_id = #{teacherId}")
    AcademicWarning findByIdOwnedByTeacher(@Param("warningId") Long warningId,
                                           @Param("teacherId") Long teacherId);

    @Select("SELECT COUNT(*) FROM academic_warning aw INNER JOIN student_profile sp ON aw.student_id = sp.id WHERE sp.class_id = #{classId} AND aw.warning_level >= 3")
    int countHighByClassId(Long classId);
}
