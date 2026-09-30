package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成绩申诉实体类 —— ⚠️ **这个类是死的，别用它**。
 *
 * <p>同一张 {@code score_appeals} 表还有第二个实体：
 * {@code student-service/src/main/java/com/academic/entity/ScoreAppeal.java}，
 * 那一个才是活的（{@code ScoreAppealMapper extends BaseMapper<那个>}，
 * {@code StudentServiceImpl} 用的也是它）。本类全仓**零引用** ——
 * grep "ScoreAppeal" 除定义行外没有一处 import 它，它只是被打进了每个服务的 fat jar
 * （因为 academic-common 是公共依赖）。
 *
 * <p>留着不删是为了不制造意外；但**改 score_appeals 的列时两个都要改**，
 * 否则将来有人图省事改用公共包这个，就会在"某列明明在库里、实体里没有"上栽一次
 * （同族的坑：{@code student_settings} 那张零读零写的表、两个 StudentSettings 实体）。
 */
@Data
@TableName("score_appeals")
public class ScoreAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warningId;
    private Long studentId;
    private Long scoreId;
    private Long courseId;
    /** 原因**代码**（'entry_error' 之类），前端按它查中文标签、统计接口按它 GROUP BY —— 别往里塞自由文本 */
    private String reason;
    /** 学生自己填写的申诉详细说明（自由文本）。2026-09-30 新增列，见 db_backups/appeal_description_20260930_apply.sql */
    private String description;
    private String attachments;
    private Integer status;
    private String reply;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}