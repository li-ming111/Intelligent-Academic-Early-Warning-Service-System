package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩实体类
 */
@Data
@TableName("scores")
public class Score {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("student_id")
    private Long studentId;
    
    @TableField("course_id")
    private Long courseId;
    
    private String semester;
    
    private Integer year;
    
    @TableField("regular_score")
    private BigDecimal regularScore;
    
    @TableField("final_score")
    private BigDecimal finalScore;
    
    @TableField("score_total")
    private BigDecimal scoreTotal;
    
    private String grade;
    
    @TableField("grade_point")
    private BigDecimal gradePoint;

    /**
     * 教师录入/导入的「备注」。
     *
     * <p>2026-10-10（W36）加：此前备注只存在 {@code score_record}（教师录入暂存表），
     * 教师成绩册/导出/模板都带「备注」，而权威表 {@code scores} 没有这一列。
     * 教师端改用 {@code scores} 后，备注必须落到这里，否则会被静默丢弃。
     */
    private String remark;

    @TableField("reason_for_change")
    private String reasonForChange;
    
    /** 课程名称（连表查询，非数据库字段） */
    @TableField(exist = false)
    private String courseName;

    /** 学分（连表查询，非数据库字段） */
    @TableField(exist = false)
    private java.math.BigDecimal credits;

    /**
     * 授课教师姓名（连表查询，非数据库字段）。
     *
     * <p>2026-10-01 第二十一轮加：小程序学生端成绩卡上有一处「课程名 · 学分 · 教师 · 学期」，
     * 其中的 {@code teacherName} 一直没有来源 ⇒ 那个分隔点与名字恒不显示。
     * 来源是 {@code courses.teacher_id → users.id}（实测 courses.teacher_id 与
     * {@code teacher_profile.user_id} 10/10 一致、与 {@code teacher_profile.id} 0/10，
     * 即它存的是 users.id，不是教师档案主键）。
     */
    @TableField(exist = false)
    private String teacherName;
    
    @TableField("modified_by")
    private Long modifiedBy;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}