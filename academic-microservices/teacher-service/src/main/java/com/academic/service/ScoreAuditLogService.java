package com.academic.service;

import com.academic.common.entity.Course;
import com.academic.common.entity.ScoreAuditLog;
import com.academic.common.entity.ScoreRecord;
import com.academic.mapper.CourseMapper;
import com.academic.mapper.ScoreAuditLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ScoreAuditLogService extends ServiceImpl<ScoreAuditLogMapper, ScoreAuditLog> {

    @Autowired
    private CourseMapper courseMapper;

    public List<ScoreAuditLog> getByTeacherId(Long teacherId) {
        return baseMapper.findByTeacherId(teacherId);
    }

    /**
     * 记录一次成绩修改
     */
    public void record(ScoreRecord oldRecord, ScoreRecord newRecord, String reason, String modifiedBy) {
        ScoreAuditLog log = new ScoreAuditLog();
        log.setStudentId(newRecord.getStudentId());
        log.setStudentName(newRecord.getStudentName());
        log.setCourseId(newRecord.getCourseId());
        // 课程名要单独查：score_audit_log.course_name 是冗余列，调用方传进来的
        // ScoreRecord 只带 course_id（它的 courseName 是 @TableField(exist=false) 的联表字段，
        // 这里必定为 null）。不补这一下，教师端"成绩修改记录"页的「课程」一列对新产生的
        // 记录就是空白 —— 而历史那一条（手工造的数据）有课程名，新旧不一致最容易被看出来。
        if (newRecord.getCourseId() != null) {
            Course course = courseMapper.selectById(newRecord.getCourseId());
            if (course != null) {
                log.setCourseName(course.getName());
            }
        }
        log.setTeacherId(newRecord.getTeacherId());
        log.setOldScore(BigDecimal.valueOf(oldRecord.getScore()));
        log.setNewScore(BigDecimal.valueOf(newRecord.getScore()));
        log.setReason(reason);
        log.setModifiedBy(modifiedBy);
        baseMapper.insert(log);
    }
}