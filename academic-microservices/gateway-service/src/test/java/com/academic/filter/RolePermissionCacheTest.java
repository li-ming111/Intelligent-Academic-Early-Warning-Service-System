package com.academic.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关 RBAC 缓存（W3.3，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：这个类自上线起从未成功加载过（拉取必 403 + 失败后无重试），
 * 而且即便加载成功也会全面 403 —— 它把 URL 前缀翻译成 {@code "student:read"} 这种
 * 权限码，而库里是 {@code PERM_STUDENT_VIEW}。见类注释。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① {@code roles} 表（id → code）**整表**带过来，任意角色 id 都能表达 ——
 *     管理员新建的角色（id ≥ 6）不再翻译成 {@code "unknown"}；
 * <br>② 读需要 VIEW **或** MANAGE（「管理包含查看」），写只认 MANAGE；
 * <br>③ 没绑任何权限的角色 = 什么都做不了（与"角色不存在"是两回事）；
 * <br>④ 没加载到数据时一律拒绝（fail-closed），且**不是**靠"查不到所以 false"的巧合。
 */
class RolePermissionCacheTest {

    /** 造一份与 auth-service `/role-permissions/rbac-map` 同形的 data 字典。 */
    private static Map<String, Object> payload(Map<String, String> roles,
                                               Map<String, List<String>> map) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roles", roles);
        data.put("map", map);
        return data;
    }

    private static Map<String, String> builtInRoles() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("1", "ROLE_STUDENT");
        roles.put("2", "ROLE_TEACHER");
        roles.put("3", "ROLE_COUNSELOR");
        roles.put("4", "ROLE_ADMIN");
        roles.put("5", "ROLE_COLLEGE_ADMIN");
        return roles;
    }

    private RolePermissionCache loaded(Map<String, String> roles, Map<String, List<String>> map) {
        RolePermissionCache cache = new RolePermissionCache();
        cache.applyPayload(payload(roles, map));
        return cache;
    }

    @Test
    @DisplayName("任意角色 id 都能翻译成角色码 —— 新建的 id=6 不再是 unknown")
    void roleToCode_resolvesAnyRoleFromTheTable() {
        Map<String, String> roles = builtInRoles();
        roles.put("6", "ROLE_ASSISTANT");   // 管理员在权限管理页新建的角色
        RolePermissionCache cache = loaded(roles, Map.of());

        assertEquals("ROLE_STUDENT", cache.roleToCode(1));
        assertEquals("ROLE_ASSISTANT", cache.roleToCode(6), "新建角色必须能表达，否则勾多少权限都等于没勾");
        assertTrue(cache.isKnownRole(6));
        // 表里没有的 id 要**显式**说明是哪个 id，而不是含糊的 "unknown"
        assertEquals("ROLE_UNMAPPED_99", cache.roleToCode(99));
        assertFalse(cache.isKnownRole(99));
    }

    @Test
    @DisplayName("读要 VIEW 或 MANAGE；写只认 MANAGE")
    void allows_readNeedsViewOrManage_writeNeedsManage() {
        Map<String, String> roles = new LinkedHashMap<>(builtInRoles());
        roles.put("6", "ROLE_ASSISTANT");
        roles.put("7", "ROLE_READONLY");
        Map<String, List<String>> map = Map.of(
                "ROLE_ASSISTANT", List.of("PERM_SYSTEM_MANAGE"),
                "ROLE_READONLY", List.of("PERM_COURSE_VIEW"));
        RolePermissionCache cache = loaded(roles, map);

        // 只给了 MANAGE：读也放行（「管理权限包含查看权限」）
        assertTrue(cache.allows(6, "SYSTEM", "GET"));
        assertTrue(cache.allows(6, "SYSTEM", "POST"));
        // 只给了 VIEW：读放行、写拒绝 —— 这才是 VIEW/MANAGE 两个码存在的意义
        assertTrue(cache.allows(7, "COURSE", "GET"));
        assertFalse(cache.allows(7, "COURSE", "POST"), "只有查看权限不能写");
        // 模块之间不串：ASSISTANT 的 SYSTEM 权限不能拿来开 COURSE 的门
        assertFalse(cache.allows(6, "COURSE", "GET"));
    }

    @Test
    @DisplayName("没绑任何权限的角色：什么都做不了（但角色本身是存在的）")
    void roleWithoutBindings_getsNothing() {
        Map<String, String> roles = new LinkedHashMap<>(builtInRoles());
        roles.put("6", "ROLE_EMPTY");
        RolePermissionCache cache = loaded(roles, Map.of());

        assertTrue(cache.isKnownRole(6), "角色在 roles 表里");
        assertFalse(cache.allows(6, "SYSTEM", "GET"));
        assertFalse(cache.allows(6, "SYSTEM", "POST"));
    }

    @Test
    @DisplayName("未加载数据 ⇒ 一律拒绝（fail-closed，不是靠查不到碰巧 false）")
    void notLoaded_deniesEverything() {
        RolePermissionCache cache = new RolePermissionCache();
        assertFalse(cache.hasRbacData());
        assertFalse(cache.allows(6, "SYSTEM", "GET"));
        assertFalse(cache.allows(1, "STUDENT", "GET"));
    }

    @Test
    @DisplayName("roles 全表带过来，但 map 只含真有绑定的角色")
    void rolesTableIsCompleteEvenWhenMapIsNot() {
        // role 5 一条绑定都没有：它仍要出现在 roles 里（否则网关分不清
        // "这个角色不存在" 与 "这个角色没有任何权限"）
        Map<String, List<String>> map = Map.of("ROLE_STUDENT", List.of("PERM_STUDENT_VIEW"));
        RolePermissionCache cache = loaded(builtInRoles(), map);

        assertTrue(cache.isKnownRole(5));
        assertFalse(cache.allows(5, "STUDENT", "GET"));
        assertTrue(cache.allows(1, "STUDENT", "GET"));
    }

    @Test
    @DisplayName("脏数据不能把整张表丢掉：键不是数字的跳过，其余照收")
    void dirtyKeysAreSkippedNotFatal() {
        Map<String, String> roles = new LinkedHashMap<>(builtInRoles());
        roles.put("not-a-number", "ROLE_JUNK");
        roles.put("6", "ROLE_ASSISTANT");
        RolePermissionCache cache = loaded(roles, Map.of());

        assertTrue(cache.hasRbacData());
        assertEquals("ROLE_ASSISTANT", cache.roleToCode(6));
    }
}
