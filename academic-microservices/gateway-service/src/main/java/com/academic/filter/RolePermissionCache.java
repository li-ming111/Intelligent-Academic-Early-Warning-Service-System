package com.academic.filter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 加载并缓存 auth-service 中的角色-权限映射，供 {@link AuthenticationFilter} 的自定义角色准入使用。
 *
 * <h3>2026-10-01 第十六轮：查证它自上线起从未生效</h3>
 * 三处事实叠加，缺一不可：
 * <ol>
 *   <li><b>拉取必 403</b>：{@code webClient} 无凭据直连 {@code http://localhost:8072}，
 *       而 auth-service 的 {@code SecurityConfig} 当时把 {@code /role-permissions/**}
 *       整段要求 {@code ROLE_ADMIN} —— 网关启动那一刻没有任何用户身份 ⇒ 必然 403。</li>
 *   <li><b>失败后永不再试</b>：{@link #init()} 只在 {@code @PostConstruct} 跑一次，
 *       没有重试也没有刷新 ⇒ {@code hasRbacData} 永久 {@code false}。</li>
 *   <li><b>降级是静默的</b>：catch 只 {@code log.warn}。</li>
 * </ol>
 *
 * <h3>2026-10-02 第三十六轮（W3.3）：把它救活，并**去掉那层编出来的词表**</h3>
 * 上面三条各修各的：
 * <ol>
 *   <li>{@code /role-permissions/rbac-map} 在 auth-service 侧 {@code permitAll}
 *       （只这一条只读字典，写面仍要 ADMIN；理由写在那边的 {@code SecurityConfig} 里）。
 *       端点地址做成配置项 {@code spring.cloud.gateway.rbac.auth-service-uri}，
 *       默认 {@code http://localhost:8072}。</li>
 *   <li>{@link #init()} 起一个守护线程：**失败就退避重试**，成功后每 5 分钟刷新一次。
 *       这样"auth-service 比网关晚起"不再是永久性故障。</li>
 *   <li>加载成功/失败都打日志，并把 {@code roles} 表行数一并打出来。</li>
 * </ol>
 *
 * <h3>⭐ 拿掉的不是 bug，是一个**编出来的**权限词表</h3>
 * 原来这里还有第二层错：即便加载成功也会全面 403。因为它把 URL 前缀翻译成
 * {@code "student"}/{@code "student:read"} 这样的权限码，而库里 {@code permissions.code}
 * 是 {@code PERM_STUDENT_VIEW}/{@code PERM_STUDENT_MANAGE} —— <b>两处词表不匹配</b>。
 *
 * <p>但把词表对齐**并不能**修好它，因为这两个模型是**两个不同的维度**：
 * <ul>
 *   <li>库里的权限是<b>功能模块</b>维度：学生 / 课程 / 预警 / 帮扶 / 系统，每个分
 *       「查看 / 管理」；</li>
 *   <li>网关的 URL 前缀是<b>角色自己的家</b>维度：{@code /api/students/**} 是**学生本人的自助面**
 *       （我的成绩、我的申诉、我的通知），不是"学生信息管理面"。</li>
 * </ul>
 * 硬把两者对上，只能靠编：要么得出「学生角色持有『管理学生信息』权限」这种假数据，
 * 要么 403 掉真实页面。⇒ 所以这一层**不再对内置角色生效**，
 * 只服务于**管理员新建的自定义角色**（见 {@link AuthenticationFilter}）。
 *
 * <h3>现在这层做什么</h3>
 * 只回答一个问题：**某个自定义角色（{@code users.role ≥ 6}）有没有某个功能模块的权限**。
 * 内置角色 1–5 完全由路径前缀层决定，行为一字未改。
 */
@Component
public class RolePermissionCache {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionCache.class);

    /** 内置角色 1–5：它们的准入完全由路径前缀层决定，本类不参与。 */
    public static final int BUILT_IN_ROLE_MAX = 5;

    /** 加载失败后的重试间隔（毫秒），最多 {@link #MAX_LOAD_ATTEMPTS} 次。 */
    private static final long RETRY_INTERVAL_MS = 10_000L;
    private static final int MAX_LOAD_ATTEMPTS = 30;

    /**
     * 加载成功后的刷新间隔（秒），默认 5 分钟。
     *
     * <p>做成配置项是为了**能在不重启网关的情况下验证权限变更**：
     * 管理员在权限管理页改完绑定后，最多等这么久生效。
     * 调小它可以在测试里几秒钟就看到新数据（见手册 §6.33 的实测步骤）。
     */
    @Value("${spring.cloud.gateway.rbac.refresh-seconds:300}")
    private long refreshSeconds;

    /** role_code → Set<permission_code> */
    private final Map<String, Set<String>> rbacMap = new ConcurrentHashMap<>();

    /**
     * 角色 id（{@code users.role}）→ 角色码（{@code roles.code}）。
     *
     * <p><b>这是本类存在的主要理由</b>：原来这层翻译是一个写死的 {@code switch(1..5)}，
     * {@code default} 回 {@code "unknown"} ⇒ 管理员新建的角色（id ≥ 6）无论勾了什么权限
     * 都翻译不出来、等于没勾。现在整表从 auth-service 带过来，任何角色都能表达。
     */
    private final Map<Long, String> roleIdToCode = new ConcurrentHashMap<>();

    /** 是否有 RBAC 数据（未加载时自定义角色一律拒绝；内置角色不受影响） */
    private volatile boolean hasRbacData = false;

    /**
     * auth-service 地址。默认直连本机 8072（与从前一致，不需要 Nacos）；
     * 有服务发现的环境可以改成 {@code lb://auth-service}。
     */
    @Value("${spring.cloud.gateway.rbac.auth-service-uri:http://localhost:8072}")
    private String authServiceUri;

    private WebClient webClient;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder().baseUrl(authServiceUri).build();
        // 起守护线程而不是阻塞启动：auth-service 常常比网关晚起来，
        // 原来"只试一次"的写法会让这种正常的启动顺序变成永久性故障。
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rbac-loader");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(this::loadWithRetry, 0, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /** 失败就退避重试；成功之后转入周期性刷新。 */
    private void loadWithRetry() {
        for (int attempt = 1; attempt <= MAX_LOAD_ATTEMPTS; attempt++) {
            if (loadRbacMap()) {
                scheduler.scheduleWithFixedDelay(this::loadRbacMap,
                        refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
                return;
            }
            if (attempt < MAX_LOAD_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn("RBAC 映射尝试 {} 次仍未加载成功（auth-service 未起？）。"
                + "自定义角色（users.role ≥ 6）将一律被拒绝；内置角色 1–5 不受影响。",
                MAX_LOAD_ATTEMPTS);
    }

    /** @return 是否加载成功 */
    @SuppressWarnings("unchecked")
    private boolean loadRbacMap() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/role-permissions/rbac-map")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));

            if (response == null || !(response.get("data") instanceof Map)) {
                log.warn("RBAC 响应格式异常，保持上一次的映射不变");
                return false;
            }
            applyPayload((Map<String, Object>) response.get("data"));
            return true;
        } catch (Exception e) {
            log.warn("RBAC 映射加载失败（将重试）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 把 auth-service 的响应体灌进缓存。**独立成方法是为了可测** ——
     * 单测直接喂一份 {@code data} 字典即可，不需要起 HTTP。
     *
     * <p>期望形状：{@code {"roles": {"1":"ROLE_STUDENT",...}, "map": {"ROLE_STUDENT":[...]}}}
     */
    void applyPayload(Map<String, Object> data) {
        Map<Long, String> newRoleIdToCode = new ConcurrentHashMap<>();
        Object rolesObj = data.get("roles");
        if (rolesObj instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) rolesObj).entrySet()) {
                try {
                    newRoleIdToCode.put(Long.parseLong(e.getKey()), String.valueOf(e.getValue()));
                } catch (NumberFormatException ignored) {
                    // 键不是数字就跳过 —— 不因为一条脏数据把整张表丢掉
                }
            }
        }

        Map<String, Set<String>> newMap = new ConcurrentHashMap<>();
        Object mapObj = data.get("map");
        if (mapObj instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) mapObj).entrySet()) {
                if (e.getValue() instanceof List) {
                    newMap.put(e.getKey(), new HashSet<>((List<String>) e.getValue()));
                }
            }
        }

        roleIdToCode.clear();
        roleIdToCode.putAll(newRoleIdToCode);
        rbacMap.clear();
        rbacMap.putAll(newMap);

        hasRbacData = !roleIdToCode.isEmpty();
        log.info("RBAC 映射加载完成: roles 表 {} 个角色（id→code），其中 {} 个角色有权限绑定，共 {} 条权限",
                roleIdToCode.size(), rbacMap.size(),
                rbacMap.values().stream().mapToInt(Set::size).sum());
    }

    /**
     * 自定义角色（{@code users.role ≥ 6}）能否访问某个功能模块。
     *
     * <p>语义取自库里 {@code permissions.code} 的命名：
     * <ul>
     *   <li>读（GET/HEAD）：需要 {@code PERM_{FAMILY}_VIEW} <b>或</b> {@code PERM_{FAMILY}_MANAGE}
     *       —— 「管理权限包含查看权限」；</li>
     *   <li>写（其余方法）：需要 {@code PERM_{FAMILY}_MANAGE}。</li>
     * </ul>
     *
     * @param role   {@code users.role}
     * @param family 功能模块，如 {@code SYSTEM} / {@code COURSE} / {@code WARNING}
     * @param method HTTP 方法
     */
    public boolean allows(int role, String family, String method) {
        if (!hasRbacData) {
            return false;
        }
        Set<String> perms = rbacMap.get(roleToCode(role));
        if (perms == null || perms.isEmpty()) {
            return false;
        }
        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        String manage = "PERM_" + family + "_MANAGE";
        if (perms.contains(manage)) {
            return true;
        }
        return read && perms.contains("PERM_" + family + "_VIEW");
    }

    /** 是否已加载 RBAC 数据 */
    public boolean hasRbacData() {
        return hasRbacData;
    }

    /** 该角色 id 在 roles 表里有没有（用于日志区分"角色不存在"与"角色没权限"）。 */
    public boolean isKnownRole(int role) {
        return roleIdToCode.containsKey((long) role);
    }

    /** 手动重新加载 */
    public void reload() {
        loadRbacMap();
    }

    /**
     * {@code users.role}（整数 id）→ {@code roles.code}（字符串）。
     *
     * <p>⚠️ 这里原来是 {@code switch(role){case 1..5} default: "unknown"} ——
     * <b>新建角色永远翻译不出来</b>。现在查 {@code roles} 全表，任意 id 都能表达。
     * 查不到时返回带 id 的显式名字而不是 {@code "unknown"}，
     * 这样日志里能直接看出是哪个 id 没在 roles 表里。
     */
    String roleToCode(int role) {
        String code = roleIdToCode.get((long) role);
        return code != null ? code : "ROLE_UNMAPPED_" + role;
    }
}
