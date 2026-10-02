package com.academic.service;

import com.academic.common.entity.User;
import com.academic.entity.LoginLog;
import com.academic.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录日志写入。
 *
 * <p>{@code login_logs} 此前全仓无写入方，学生端「登录历史」、管理员端「用户登录日志」、
 * 小程序 admin「登录日志」三个页面永远空。这里把登录这件事真正记下来。
 *
 * <h3>为什么把"写日志"和"登录"分开</h3>
 * 记日志**不是**登录的一部分：写日志失败不该让用户登不进来。所以它单独成一个类、
 * 由 controller 在拿到登录结果之后调用，且内部自己吞掉异常只记 warn。
 * 这与"业务写操作要同事务"是相反的取舍 —— 这里要的恰恰是"主流程不被拖累"。
 */
@Slf4j
@Service
public class LoginLogService {

    /** {@code device} 列是 varchar(200)。 */
    private static final int MAX_DEVICE = 200;
    /** {@code ip_address} 列是 varchar(50)。 */
    private static final int MAX_IP = 50;
    /** {@code username} 列是 varchar(100)。 */
    private static final int MAX_USERNAME = 100;

    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 记一条登录日志。
     *
     * @param user   登录成功时是数据库里的用户；密码错/被锁时也可能是该用户（有 id 才记得下来）；
     *               用户名根本不存在时传 {@code null} —— 这种情况不落库（见下）
     * @param status {@code success} 或 {@code failed}
     */
    public void record(User user, String status, HttpServletRequest request) {
        if (user == null || user.getId() == null) {
            // `login_logs.user_id` 是 NOT NULL。用户名不存在时没有 id 可写；
            // 写 0 会造出一个指向不存在用户的悬空引用（本项目在 messages 表上踩过：
            // sender_id 恒设 0 导致每次插入都 ERROR 1452）。所以只记 warn，不落库。
            log.warn("登录失败但用户名在 users 里不存在，无法写 login_logs（user_id 非空）: status={}", status);
            return;
        }
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(user.getId());
            entry.setUsername(truncate(user.getUsername(), MAX_USERNAME));
            entry.setLoginTime(LocalDateTime.now());
            entry.setIpAddress(truncate(clientIp(request), MAX_IP));
            entry.setDevice(truncate(request == null ? null : request.getHeader("User-Agent"), MAX_DEVICE));
            // location 留空：工程里没有 IP 归属地库，编一个城市出来不如空着
            entry.setLocation(null);
            entry.setStatus(status);
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            // 日志写不进去不该影响登录本身
            log.warn("写 login_logs 失败（不影响登录结果）: {}", e.getMessage());
        }
    }

    /**
     * 取真实客户端 IP。
     *
     * <p>请求是经网关转发进来的，{@code getRemoteAddr()} 拿到的是网关的地址，
     * 所以先看 {@code X-Forwarded-For}（Spring Cloud Gateway 默认会加）。
     * 该头可能是 {@code "客户端, 代理1, 代理2"} 的形式，取第一段。
     */
    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
