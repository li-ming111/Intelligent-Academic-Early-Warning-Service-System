package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警的「挂科课程明细」——一条 {@link AcademicWarning} 由哪几门课的挂科构成。
 *
 * <p><b>2026-10-12（W39）新增。</b>在此之前这个关系只以散文形式躺在
 * {@code academic_warning.description} 里（"…（操作系统 40分、计算机基础 47分、
 * 高等数学 56.5分）…"），于是教师端只能按<b>班级</b>过滤预警，看不出"这条预警涉不涉及
 * 我教的课"；PC 学生端 {@code Warnings.vue} 只好用正则从正文里抠课程名。
 *
 * <p><b>为什么是子表而不是 {@code academic_warning} 上加一列 {@code course_id}：</b>
 * 预警是学生级的聚合（{@code warning_rules} 的规则全是"入学以来累计挂科 N 门"），
 * 一条预警就由多门课构成（司明 id=395 由 3 门课构成）。单列存不下，只留"最低分那门"
 * 等于把另外两门继续丢掉。
 *
 * <p>写入方只有 {@code WarningScheduledTask.createWarning}（扫描生成预警那一刻），
 * 与父行同属一次写入。读取方见
 * {@link com.academic.mapper.AcademicWarningCourseMapper}。
 *
 * <p>⚠️ 这是<b>派生数据</b>，只在生成预警时写一次；成绩后来变了本表不跟着改
 * （那种情况由 {@code releaseRecoveredWarnings} 把整条预警标成已解除）。
 */
@Data
@Accessors(chain = true)
@TableName("academic_warning_course")
public class AcademicWarningCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 {@code academic_warning.id}。 */
    private Long warningId;

    /** 关联 {@code courses.id}。刻意无外键：删课不该被历史预警挡住。 */
    private Long courseId;

    /**
     * 生成预警那一刻的课程名快照。
     *
     * <p>课程还在时读端以 {@code courses.name} 为准，这一列只在课程被删后兜底
     * （读端 SQL 里是 {@code COALESCE(c.name, awc.course_name)}）。
     */
    private String courseName;

    /** 生成预警那一刻的 {@code score_total}，与父行 {@code trigger_score} 同口径。 */
    private BigDecimal scoreTotal;

    /** 该门挂科成绩所属学期。 */
    private Integer semester;

    private LocalDateTime createdAt;
}
