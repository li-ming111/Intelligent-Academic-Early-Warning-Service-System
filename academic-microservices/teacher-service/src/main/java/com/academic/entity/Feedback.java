package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    /**
     * {@code feedback} 表**没有** student_id 列（只有 teacher_id / category / content /
     * reply / status / create_time / update_time）。反馈是由学生端把「姓名：内容」
     * 拼成一整段正文提交的，学生身份只存在于正文字符串里。
     *
     * <p>标 {@code exist = false} 是给后来者看的：这两个字段永远不会被查到值，
     * 别拿它们做判断（{@code studentId != null} 恒为假），也别拿去做 {@code UPDATE}
     * 的条件 —— 真接上 BaseMapper 的按实体更新时，会去写不存在的列。
     */
    @TableField(exist = false)
    private Long studentId;

    private String category;

    private String content;

    private String reply;

    private Integer status;

    private LocalDateTime createTime;

    /**
     * 同时充当"回复时间"：{@link com.academic.mapper.FeedbackMapper#replyFeedback}
     * 回复时把这一列置为 NOW()。原先这里叫 {@code replyTime}，映射的是一个
     * 不存在的列，于是回复时间永远读不到。
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String studentName;
}