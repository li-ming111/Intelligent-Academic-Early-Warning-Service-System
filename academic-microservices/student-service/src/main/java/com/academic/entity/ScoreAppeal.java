package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成绩申诉实体类。
 *
 * <p>⚠️ 这是**活的**那个（{@code ScoreAppealMapper} 与 {@code StudentServiceImpl} 用的都是它）。
 * 同一张表在 academic-common 里还有一个同名的死实体，改列时两个都要改 —— 见那个类的注释。
 */
@Data
@TableName("score_appeals")
public class ScoreAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long scoreId;
    @TableField("warning_id")
    private Long warningId;
    @TableField("course_id")
    private Long courseId;
    /** 原因**代码**（'entry_error' 之类）；前端 reasonLabel 按它查中文标签、reason-statistics 按它 GROUP BY */
    private String reason;
    /**
     * 学生自己填写的申诉详细说明（自由文本）。2026-09-30 新增的列。
     *
     * <p>加这一列之前，PC 端两个页面的"请详细描述申诉理由"都是**必填**、
     * 提交时也确实发了 {@code description}，但 {@code submitAppeal} 只读五个固定 key，
     * 多出来的 key 被静默丢掉 —— 学生写了正文，库里只剩一个原因代码。
     * 见 db_backups/appeal_description_20260930_apply.sql。
     *
     * <p>加列之前提交的申诉（当前只有 id=6）该列为 NULL，**刻意不补**：补就是替学生编话。
     */
    private String description;
    private String attachments;
    private Integer status;
    private String reply;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
