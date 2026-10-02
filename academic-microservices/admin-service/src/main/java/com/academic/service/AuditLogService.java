package com.academic.service;

import com.academic.common.entity.User;
import com.academic.entity.AuditLog;
import com.academic.mapper.AuditLogMapper;
import com.academic.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志写入（管理端的关键写操作）。
 *
 * <p>2026-10-02 之前 {@code audit_logs} 是**全仓零写入方**的空表，而管理端「用户详情」
 * 页的「操作日志」计数一直读它 ⇒ 恒 0。这里把"谁在什么时候改了什么东西"真正记下来。
 *
 * <h3>调用方身份从哪来</h3>
 * 本服务不做认证（网关才是边界），所以身份只能取自网关注入的 {@code X-User-Id} /
 * {@code X-Username}。**这两条头在直连 8070 时可以随便伪造** —— 对"审计"来说这是
 * 一个真实的局限，所以这里做两件事：<br>
 * ① 拿到的 id 先回查 {@code users}，查无此人就退回按用户名查，再查不到就写 {@code NULL}
 * （{@code audit_logs.user_id} 有外键指向 {@code users.id}，塞一个不存在的 id 会
 * {@code ERROR 1452} 把整条业务事务拖垮 —— 本项目在 {@code messages.sender_id} 上踩过）；<br>
 * ② 详情里同时记下用户名，即便 id 落空也还认得出人。
 *
 * <h3>为什么吞异常</h3>
 * 记审计**不是**业务的一部分：审计写失败不该让"删用户"回滚。与 {@code LoginLogService}
 * 同一取舍，与"业务写操作要同事务"相反。
 */
@Slf4j
@Service
public class AuditLogService {

    /** {@code action_type} / {@code entity_type} 都是 varchar(50)。 */
    private static final int MAX_TYPE = 50;

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    public AuditLogService(AuditLogMapper auditLogMapper, UserMapper userMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
    }

    /**
     * 记一条审计日志。
     *
     * @param actionType 动作，如 {@code CREATE} / {@code UPDATE} / {@code DELETE} / {@code REVIEW}
     * @param entityType 被操作的对象类型，如 {@code USER} / {@code WARNING_RULE} / {@code APPEAL}
     * @param entityId   被操作对象的 id（可空）
     * @param details    人话描述，例如 {@code "删除用户 adm002(id=16)"}
     */
    public void record(String actionType, String entityType, Long entityId, String details) {
        try {
            HttpServletRequest request = currentRequest();
            AuditLog entry = new AuditLog();
            entry.setUserId(resolveActorId(request));
            entry.setActionType(truncate(actionType, MAX_TYPE));
            entry.setEntityType(truncate(entityType, MAX_TYPE));
            entry.setEntityId(entityId);
            entry.setDetails(details);
            entry.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("写 audit_logs 失败（不影响本次操作）: action={}, entity={}#{}, err={}",
                    actionType, entityType, entityId, e.getMessage());
        }
    }

    /**
     * 当前操作者的 {@code users.id}，解析不出来返回 {@code null}。
     *
     * <p>W1.11（2026-10-02）起对外暴露：{@code admin_tasks.created_by} 上有指向
     * {@code users.id} 的**真外键**，写一个不存在的 id 会 {@code ERROR 1452}。
     * 与其在 controller 里再抄一遍"回查 users 挡住伪造/已删用户"，不如就用这里
     * 这一份 —— 两处各写一套必然漂移。
     */
    public Long currentActorId() {
        return resolveActorId(currentRequest());
    }

    /**
     * 解析操作者 {@code users.id}；解析不出来返回 {@code null}（该列可空）。
     *
     * <p>先按 {@code X-User-Id} 回查（挡住伪造/已删用户导致的 1452），再退回按
     * {@code X-Username} 查。两者都没有就写 NULL —— **不编一个 0**。
     */
    private Long resolveActorId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String rawId = request.getHeader("X-User-Id");
        if (rawId != null && !rawId.isBlank()) {
            try {
                Long id = Long.valueOf(rawId.trim());
                User byId = userMapper.selectById(id);
                if (byId != null) {
                    return byId.getId();
                }
                log.warn("X-User-Id={} 在 users 里不存在，审计日志按用户名重试", rawId);
            } catch (NumberFormatException e) {
                log.warn("X-User-Id 不是数字: {}", rawId);
            }
        }
        String username = request.getHeader("X-Username");
        if (username != null && !username.isBlank()) {
            User byName = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                            .eq("username", username.trim()).last("LIMIT 1"));
            if (byName != null) {
                return byName.getId();
            }
        }
        return null;
    }

    /** 取当前请求；不在请求线程里（如定时任务）返回 null。 */
    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
