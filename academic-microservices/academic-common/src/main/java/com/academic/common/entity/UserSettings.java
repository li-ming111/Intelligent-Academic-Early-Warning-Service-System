package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 账号级自助设置（通知偏好 + 办公信息），跨角色通用。
 *
 * <p>键是 {@code users.id}，不是 {@code student_profile.id} —— 与
 * {@code notifications.receiver_id} 属同一套 ID 空间。学生专用的
 * {@code student_settings} / {@code subscription_preferences} 另有其人（键 student_profile.id），
 * 教师/辅导员没有隐私等级、订阅级别、推送渠道这些字段，故不合表。
 *
 * <p>布尔字段一律用**包装类型 {@code Boolean}** 而非 {@code boolean}：
 * PUT 是部分更新（只写非空字段，与 MyBatis-Plus {@code updateById} 的
 * {@code FieldStrategy.NOT_NULL} 语义一致），用原始类型的话"没传"和"传了 false"
 * 都是 false，会把用户关掉的通知又打开。
 */
@Data
@Accessors(chain = true)
@TableName("user_settings")
public class UserSettings {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** users.id */
    private Long userId;
    private Boolean warningNotification;
    private Boolean systemMessage;
    private Boolean classNotification;
    private Boolean studentFeedback;
    private Boolean emailReminder;
    private String officeLocation;
    private String officePhone;
    private String officeHours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
