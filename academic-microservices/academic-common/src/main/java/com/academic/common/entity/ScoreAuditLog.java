package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("score_audit_log")
public class ScoreAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    /** 学号（student_profile.student_id），读日志时联表带出；表里没有这一列。 */
    @TableField(exist = false)
    private String studentNo;
    private String studentName;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private BigDecimal oldScore;
    private BigDecimal newScore;
    private BigDecimal difference;
    private String reason;
    private String modifiedBy;
    private LocalDateTime modifiedAt;
}