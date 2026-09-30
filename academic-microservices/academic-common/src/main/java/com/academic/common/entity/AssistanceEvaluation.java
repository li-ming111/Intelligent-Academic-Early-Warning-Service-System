package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮扶评估实体类 —— ⚠️ **这个类是死的，别用它**。
 *
 * <p>同一张 `assistance_evaluations` 表有**两个**同名的实体类，和
 * {@code score_appeals} → {@code ScoreAppeal} 是同一类诱饵：
 *
 * <ul>
 *   <li>**活的**：{@code student-service/.../com/academic/entity/AssistanceEvaluation.java}
 *       —— 有 Mapper、有 Service，`/students/evaluations/**` 走的就是它。</li>
 *   <li>**死的**：本类。全仓 grep 只有它自己的声明，零引用、零 mapper。</li>
 * </ul>
 *
 * <p>加字段时**两处都改**，否则将来有人 import 错了就会拿到一个"少几列"的实体，
 * 写库时静默丢掉那几列（这正是本次 `effectiveness` 事故的形状）。
 */
@Data
@TableName("assistance_evaluations")
public class AssistanceEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long counselorId;
    private String content;
    private Integer score;
    /**
     * 帮扶有效性评分 1-5。与 {@link #score} 是**两个不同的问题**：
     * score = 对这个帮扶计划本身的评分，effectiveness = 这次帮扶是否真的有用。
     * 列由 db_backups/evaluation_effectiveness_20260930_apply.sql 添加，历史行为 NULL。
     */
    private Integer effectiveness;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}