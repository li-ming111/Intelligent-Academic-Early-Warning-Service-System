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

    // ------------------------------------------------------------------
    // 2026-10-02（W4.11）：辅导员端「学生管理」页的「状态」列原先写死 "正常"，
    // 「联系电话 / 邮箱」两列读的 phone/email 也永远是 null（见上）。
    // 这三样都在 `users` 表上，由 counselor-service 的 StudentServiceImpl
    // 按 user_id 批量取回后填进来。取不到就是 null —— 前端照实显示，不编。
    // ------------------------------------------------------------------

    /**
     * 账号状态：{@code 1} 正常 / {@code 0} 待审批。
     *
     * <p>口径是 {@code users.status}，**不是** {@code student_profile} 上的任何列
     * （该表没有状态列）。按本工程的注册口径，学生账号一律 {@code 1}（直接可用），
     * 只有教师/辅导员会停在 {@code 0}（待审批）；但管理员可以在用户管理里停用账号，
     * 所以这一列**是会变的**，不能当成常量写死在页面上。
     */
    @TableField(exist = false)
    private Integer userStatus;
}