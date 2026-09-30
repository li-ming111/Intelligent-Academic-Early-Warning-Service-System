package com.academic.mapper;

import com.academic.common.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper。
 *
 * <p>{@code notifications} 表此前**整个仓库没有任何 Java 写入方** —— 只有
 * {@code db_backups/demo_data_enrich_20260902.sql} 手工塞进去的几行，学生的「通知中心」
 * 一直在读一张没人写的表，而辅导员的「发送通知」接口只 log 一行就回 success。
 *
 * <p>写入口放在这里：辅导员发通知时，接收学生真的能在自己的通知中心看到这条。
 * {@code user_id} 是 {@code users.id}（不是 {@code student_profile.id}），
 * 两个 ID 空间不同，写入前必须由调用方转换好。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
