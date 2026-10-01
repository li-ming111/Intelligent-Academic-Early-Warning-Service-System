package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生实体类
 */
@Data
@TableName("student_profile")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("student_id")
    private String studentId;
    private String name;
    @TableField("college_id")
    private Long collegeId;
    @TableField("major_id")
    private Long majorId;
    @TableField("class_id")
    private Long classId;
    @TableField("privacy_level")
    private Integer privacyLevel;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    
    @TableField(exist = false)
    private String className;
    @TableField(exist = false)
    private String email;
    @TableField(exist = false)
    private String phone;

    // ------------------------------------------------------------------
    // 2026-10-01 第二十一轮：辅导员端「我的学生」详情弹窗要显示
    // 「获取学分 X / Y」与「GPA」，读的是 credits / requiredCredits / gpa ——
    // 这三个字段 student_profile 表上**没有**（学分与绩点都从 scores 现算），
    // 所以标 exist = false，由 counselor-service 的 StudentServiceImpl 在
    // 查列表时批量填充。不填就是 null，前端按 '--' 处理，不会伪造数值。
    // ------------------------------------------------------------------

    /** 已获得学分（只累加通过课程的学分）。 */
    @TableField(exist = false)
    private Double credits;

    /** 已修学分（全部已修课程，用作"获取学分 X / Y"的分母）。 */
    @TableField(exist = false)
    private Integer requiredCredits;

    /** 学分加权绩点，口径见 {@code ScoreCaliber.STUDENT_GPA}。 */
    @TableField(exist = false)
    private Double gpa;
}