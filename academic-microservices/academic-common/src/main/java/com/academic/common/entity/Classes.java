package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级实体类（表 {@code classes}）。
 *
 * <p>与 {@code admin-service/com/academic/entity/Classes.java} 是同一个表的两个副本
 * —— 那个是本服务内的私有副本（先有），这份放在 common 里给 college-service 与
 * user-service 共用。字段与列一一对应，没有 {@code @TableField(exist = false)} 的字段
 * （common 里的 {@link Student} 就吃了这个亏：{@code className} 标了 exist=false，
 * 用它写 {@code student_profile} 时 {@code class_name} 会被**静默丢弃**）。
 *
 * <p>⚠️ {@code teacher_id} / {@code counselor_id} 用的是 **users.id**，不是
 * teacher_profile.id（同一列名在不同表里是两个 ID 空间，见
 * {@code db_backups/verify_invariants.py} D 组）。班级本身没有 {@code student_count} 列，
 * 学生数靠 {@code COUNT(student_profile.class_id)} 算。
 */
@Data
@TableName("classes")
public class Classes {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long collegeId;
    /** users.id */
    private Long teacherId;
    /** users.id */
    private Long counselorId;
    private Long majorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
