package com.academic.mapper;

import com.academic.common.entity.ScoreAuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScoreAuditLogMapper extends BaseMapper<ScoreAuditLog> {

    @Select("SELECT l.*, sp.student_id AS studentNo FROM score_audit_log l "
          + "LEFT JOIN student_profile sp ON sp.id = l.student_id "
          + "WHERE l.teacher_id = #{teacherId} ORDER BY l.modified_at DESC")
    List<ScoreAuditLog> findByTeacherId(Long teacherId);
}