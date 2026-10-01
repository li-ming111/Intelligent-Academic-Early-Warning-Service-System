package com.academic.filter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加载并缓存 auth-service 中的角色-权限映射，
 * 供 AuthenticationFilter 的 RBAC 鉴权使用。
 *
 * <p><b>⚠️ 2026-10-01 第十六轮查证：本类自上线起从未成功加载过，整条 RBAC 层始终是 no-op。</b>
 * 三处事实叠加，缺一不可：
 * <ol>
 *   <li><b>拉取必 403</b>：下面那个 {@code webClient} 无凭据直连 {@code http://localhost:8072}
 *       （写死、绕过服务发现），而 auth-service 的 {@code SecurityConfig} 只 permitAll 了
 *       {@code /auth/login}、{@code /auth/register/*}、{@code /auth/colleges|majors|courses}
 *       与 {@code /actuator/health}，{@code /role-permissions/**} 要认证
 *       ⇒ 实测 {@code 403 Forbidden from GET .../role-permissions/rbac-map}，
 *       <b>启动日志里就能直接看到那句 WARN</b>（起 gateway 实测，2026-10-01）。</li>
 *   <li><b>失败后永不再试</b>：{@link #init()} 只在 {@code @PostConstruct} 跑一次，
 *       没有重试也没有定时刷新；{@link #reload()} 全仓零调用方。
 *       ⇒ {@link #hasRbacData()} 永久为 {@code false}。</li>
 *   <li><b>降级是静默的</b>：catch 只 {@code log.warn}，前端与调用方都察觉不到。</li>
 * </ol>
 *
 * <p><b>第二层隐患（被上面第一层掩盖）：即便加载成功，也会对 role 1/2/3 全面 403。</b>
 * {@code RolePermissionController#getRbacMap} 回填的是库里的 {@code roles.code}
 * （{@code ROLE_STUDENT}）与 {@code permissions.code}（{@code PERM_STUDENT_VIEW}），
 * 而本类 {@link #roleToCode(int)} 产出 {@code "student"}、
 * {@code AuthenticationFilter#buildPermissionCode} 产出 {@code "student:read"}
 * —— <b>两处独立不匹配</b> ⇒ 查表恒为 {@code null} ⇒ 恒拒绝。
 * 命中面是 {@code /api/students|teachers|counselors/**} 的<b>全部业务接口</b>。
 *
 * <p>⇒ <b>所以"把 /role-permissions/** 加进 permitAll"这种直觉修法会更糟</b>：
 * 它把一个静默 no-op 变成对三个角色的全面 403 雪崩。
 *
 * <p>另：即便修好，RBAC 能加的增量也只有 read/write 粒度 —— 因为
 * {@code buildPermissionCode} 只覆盖那 5 个业务前缀，而那些前缀的<b>角色排他性</b>
 * 路径前缀层（{@code AuthenticationFilter#hasPermission} 前半段）已经做到了。
 * 换句话说这一层在设计上就与路径层大面积重叠。
 *
 * <p><b>结论：刻意保持现状不动。</b>当前真正生效的是路径前缀层（2026-09-30 刚加强过），
 * 平台行为正常、无越权。要真正启用 RBAC 需三层同改并回归三个角色的全部页面，
 * 换来的只是 read/write 粒度，风险远大于收益。完整论证见《答辩准备-启动与演示手册》§6.16。
 */
@Component
public class RolePermissionCache {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionCache.class);

    /** role_code → Set<permission_code> */
    private final Map<String, Set<String>> rbacMap = new ConcurrentHashMap<>();

    /** 是否有 RBAC 数据（RBAC 未配置时回退到路径前缀模式） */
    private volatile boolean hasRbacData = false;

    /** ⚠️ 写死 8072、且不带任何凭据 —— 这是"拉取必 403"的直接原因，见类注释第 1 点。 */
    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:8072")
            .build();

    @PostConstruct
    public void init() {
        // 只跑一次、无重试、无刷新 —— 见类注释第 2 点：这次失败就是**永久**失败。
        loadRbacMap();
    }

    @SuppressWarnings("unchecked")
    private void loadRbacMap() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/role-permissions/rbac-map")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.warn("RBAC响应为空，回退到路径前缀鉴权");
                return;
            }

            // 解析 JSON: {code:200, data:{"student":["score:read",...], ...}}
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map)) {
                log.warn("RBAC data 格式异常，回退到路径前缀鉴权");
                return;
            }

            Map<String, Object> data = (Map<String, Object>) dataObj;
            rbacMap.clear();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String roleCode = entry.getKey();
                Object permList = entry.getValue();
                if (permList instanceof List) {
                    List<String> perms = (List<String>) permList;
                    rbacMap.put(roleCode, new HashSet<>(perms));
                }
            }

            hasRbacData = !rbacMap.isEmpty();
            log.info("RBAC映射加载完成: {} 个角色, 共 {} 条权限", rbacMap.size(),
                    rbacMap.values().stream().mapToInt(Set::size).sum());
        } catch (Exception e) {
            // 这就是线上实际走到的分支（403），而它只留一行 WARN —— 静默降级的源头。
            log.warn("RBAC映射加载失败，回退到路径前缀鉴权: {}", e.getMessage());
        }
    }

    /**
     * 检查指定角色是否拥有某个权限。
     * @param username 用户名（用于日志）
     * @param role 角色编码 (1-5)
     * @param permissionCode 权限编码，如 "student:read"
     * @return true=有权限 false=无权限
     */
    public boolean hasPermission(String username, int role, String permissionCode) {
        if (!hasRbacData) {
            // ⚠️ 这一行**目前不可达**：唯一调用方 AuthenticationFilter#hasPermission
            //    先用 `if (rbacCache.hasRbacData())` 把整个 RBAC 块包住了，
            //    所以 hasRbacData=false 时是"整块跳过"，而不是走到这里"拒绝"。
            //    看着像 fail-closed，实际是 fail-open（详见类注释）。
            return false; // RBAC未加载，统一拒绝
        }

        String roleCode = roleToCode(role);
        Set<String> permissions = rbacMap.get(roleCode);
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }

        return permissions.contains(permissionCode);
    }

    /** 是否已加载RBAC数据 */
    public boolean hasRbacData() {
        return hasRbacData;
    }

    /** 手动重新加载 */
    public void reload() {
        loadRbacMap();
    }

    private String roleToCode(int role) {
        switch (role) {
            case 1: return "student";
            case 2: return "teacher";
            case 3: return "counselor";
            case 4: return "admin";
            case 5: return "college_admin";
            default: return "unknown";
        }
    }
}
