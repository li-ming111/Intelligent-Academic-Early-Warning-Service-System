package com.academic.mapper;

import com.academic.common.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知写入 Mapper（放在 academic-common，供所有"要给学生发通知"的服务共用）。
 *
 * <p><b>为什么不直接复用各服务已有的 {@code com.academic.mapper.NotificationMapper}</b>：
 * student-service 的那个同名接口是个**更宽的接口**（多了 7 个方法，且绑定的是
 * {@code com.academic.entity.Notification} 而不是 common 里的实体）。把 common 的版本也
 * 叫 {@code NotificationMapper} 会和它在同一个包里重名，MyBatis 的 mapper 扫描会撞
 * bean 名。所以这里取一个不会撞的名字，职责也更单一：只写。
 *
 * <p>⚠️ {@code notifications.user_id} 有真外键指向 {@code users.id}。
 * 传 {@code student_profile.id} 进去会直接 {@code ERROR 1452} —— 两者不是一套 id，
 * 转换由 {@link NotificationRecipientMapper} 负责。
 */
@Mapper
public interface NotificationWriteMapper extends BaseMapper<Notification> {
}
