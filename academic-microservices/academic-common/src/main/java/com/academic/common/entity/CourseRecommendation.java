package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师「推荐选课」实体（W14，2026-10-04）。
 *
 * <p>此前 {@code POST /teachers/courses/recommend} 把入参原样回吐、**一行都不落库**，
 * 却回一句「学生将收到通知」；库里也没有任何推荐表。这张表让"推荐"第一次真的存在：
 * 谁（{@link #teacherId}）把哪门课（{@link #courseId}）以什么理由（{@link #reason}）
 * 推荐给谁（{@link #targetClassId}，{@code NULL} = 该教师名下全部班级）。
 *
 * <p>教师 id 用 <b>{@code users.id}</b> —— 与 {@code courses.teacher_id} /
 * {@code classes.teacher_id} 同一套（全仓只有 {@code teacher_course} 一张表用
 * {@code teacher_profile.id}）。
 */
@Data
@TableName("course_recommendation")
public class CourseRecommendation {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 推荐人的 {@code users.id}。 */
    @TableField("teacher_id")
    private Long teacherId;

    @TableField("course_id")
    private Long courseId;

    /** 推荐给哪个班；{@code NULL} 表示该教师名下所有班级。{@code classes.id}。 */
    @TableField("target_class_id")
    private Long targetClassId;

    private String reason;

    /** 1 = 有效，0 = 已撤回。 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
