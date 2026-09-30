package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮扶评价实体类（**活的那个**；academic-common 里同名的那份全仓零引用，别 import 错了）
 */
@Data
@TableName("assistance_evaluations")
public class AssistanceEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("plan_id")
    private Long planId;
    @TableField("counselor_id")
    private Long counselorId;
    private String content;
    private Integer score;
    /**
     * 帮扶有效性评分 1-5。与 {@link #score} 是**两个不同的问题**：
     * score = 对帮扶计划本身的评分，effectiveness = 这次帮扶是否真的有用。
     *
     * <p>⚠️ 这一列是 2026-09-30 才加的（db_backups/evaluation_effectiveness_20260930_apply.sql）。
     * 在那之前 PC 端 `AssistanceFeedback.vue` 的弹窗一直在收集「有效性评分」，
     * 但提交体里的 `effectiveness` 既没有对应列、也不在 submitEvaluation 读的 key 里 ——
     * 学生点的分无处可去，加列前提交的行这里如实为 NULL（不编数据）。
     */
    private Integer effectiveness;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}