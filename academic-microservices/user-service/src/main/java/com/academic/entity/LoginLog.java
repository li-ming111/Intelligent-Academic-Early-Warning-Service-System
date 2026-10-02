package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志。
 *
 * <p>表 {@code login_logs} 此前**全仓没有任何写入方** —— 41 行全是 6 月的种子数据。
 * 后果是三个页面永远空：学生端「设置 → 安全日志 → 登录历史」、管理员端
 * 「用户详情 → 登录日志」、小程序 admin 的「登录日志」tab。现在每次登录都真写一条。
 *
 * <p>⚠️ {@code user_id} 是 {@code NOT NULL}：用户名不存在时<b>没有 id 可写</b>，
 * 那种失败登录只记 {@code log.warn}，不落库（写 0 会造出一个指向不存在用户的悬空外键）。
 */
@Data
@TableName("login_logs")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** {@code users.id}。 */
    private Long userId;

    private String username;

    private LocalDateTime loginTime;

    /** 客户端 IP：优先取 {@code X-Forwarded-For} 的第一段（请求经网关转发）。 */
    private String ipAddress;

    /** User-Agent，截断到 200（列宽）。 */
    private String device;

    /** 归属地。没有 IP 库，一律 NULL —— 不编。 */
    private String location;

    /** {@code success} / {@code failed}。列默认值是 {@code success}。 */
    private String status;
}
