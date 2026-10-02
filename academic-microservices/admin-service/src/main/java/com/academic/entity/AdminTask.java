package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端任务（W1.11，2026-10-02 新增）。
 *
 * <p>对应 PC `AdminMessages.vue` 的「任务管理」tab。之前 {@code /admin/tasks/*}
 * 四个端点全是桩：列表写死空数组，创建/改状态/删除只 {@code log.info} 一行就回
 * {@code "…successfully"}，**一个字节都不落库**。这个实体是它的真归宿。
 *
 * <p>{@code status} / {@code priority} 的取值与页面上那四个统计卡、
 * 三个筛选按钮、三个优先级标签一一对应：
 * <ul>
 *   <li>{@code status}: {@code pending}(待处理) / {@code in_progress}(进行中) / {@code completed}(已完成)</li>
 *   <li>{@code priority}: {@code low}(低) / {@code medium}(中) / {@code high}(高)</li>
 * </ul>
 */
@Data
@TableName("admin_tasks")
public class AdminTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** low / medium / high */
    private String priority;

    /** pending / in_progress / completed */
    private String status;

    /** 创建人，users.id（这一列是 users 的取值空间，不是 student_profile.id） */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
