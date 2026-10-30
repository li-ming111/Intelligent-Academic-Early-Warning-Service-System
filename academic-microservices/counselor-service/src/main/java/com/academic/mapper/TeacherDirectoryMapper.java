package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 辅导员选择接收教师时的通讯录。只读 users / teacher_profile / colleges，
 * 不建实体（同 AssistanceEvaluationMapper 的做法）。
 */
@Mapper
public interface TeacherDirectoryMapper {

    @Select("SELECT u.id AS userId, u.username AS username, u.name AS name, " +
            "tp.title AS title, c.name AS college " +
            "FROM users u " +
            "LEFT JOIN teacher_profile tp ON tp.user_id = u.id " +
            "LEFT JOIN colleges c ON c.id = tp.college_id " +
            "WHERE u.role = 2 AND u.status = 1 " +
            "ORDER BY u.id")
    List<Map<String, Object>> listTeachers();

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id} AND role = 2 AND status = 1")
    int countTeacher(@Param("id") Long id);
}
