package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮扶计划实体类
 */
@Data
@TableName("assistance_plans")
public class AssistancePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long counselorId;
    private Long studentId;
    private String content;
    private Integer status;
    /**
     * 完成进度百分比 0-100。
     *
     * <p>学生端"更新帮扶进度"的滑块需要一个落库字段。此前该字段不存在，
     * 前端把百分比当 status 发过来、服务端又 {@code parseInt} 进 status，
     * 于是进度被写成了一个含义不明的状态值，而页面显示的 50% 是硬编码的。
     */
    private Integer progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}