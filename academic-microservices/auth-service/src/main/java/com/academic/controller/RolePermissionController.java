package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.common.util.ExceptionMessages;
import com.academic.entity.Permission;
import com.academic.entity.Role;
import com.academic.entity.RolePermission;
import com.academic.mapper.PermissionMapper;
import com.academic.mapper.RoleMapper;
import com.academic.service.RolePermissionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色权限关联控制器
 */
@Slf4j
@RestController
@RequestMapping("/role-permissions")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    public RolePermissionController(RolePermissionService rolePermissionService,
                                    RoleMapper roleMapper,
                                    PermissionMapper permissionMapper) {
        this.rolePermissionService = rolePermissionService;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    /**
     * 获取角色权限关联列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRolePermissions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long permissionId) {
        try {
            log.info("Get role permissions request: page={}, size={}, roleId={}, permissionId={}", page, size, roleId, permissionId);
            QueryWrapper<RolePermission> queryWrapper = new QueryWrapper<>();
            if (roleId != null) {
                queryWrapper.eq("role_id", roleId);
            }
            if (permissionId != null) {
                queryWrapper.eq("permission_id", permissionId);
            }
            queryWrapper.orderByDesc("created_at");
            
            Page<RolePermission> rolePermissionPage = new Page<>(page, size);
            rolePermissionPage = rolePermissionService.page(rolePermissionPage, queryWrapper);
            
            Map<String, Object> responseData = Map.of(
                    "data", rolePermissionPage.getRecords(),
                    "total", rolePermissionPage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", rolePermissionPage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get role permissions failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取角色权限关联详情
     */
    @GetMapping("/info/{id}")
    public ApiResponse<RolePermission> getRolePermissionInfo(@PathVariable Long id) {
        try {
            log.info("Get role permission info request: {}", id);
            RolePermission rolePermission = rolePermissionService.getById(id);
            if (rolePermission == null) {
                return ApiResponse.error(404, "角色权限关联不存在");
            }
            return ApiResponse.success(rolePermission);
        } catch (Exception e) {
            log.error("Get role permission info failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 创建角色权限关联
     */
    @PostMapping("/add")
    public ApiResponse<Long> addRolePermission(@RequestBody RolePermission rolePermission) {
        try {
            log.info("Add role permission request: roleId={}, permissionId={}", rolePermission.getRoleId(), rolePermission.getPermissionId());
            // 检查是否已存在关联
            RolePermission existing = rolePermissionService.getByRoleIdAndPermissionId(rolePermission.getRoleId(), rolePermission.getPermissionId());
            if (existing != null) {
                return ApiResponse.error("角色权限关联已存在");
            }
            boolean saved = rolePermissionService.save(rolePermission);
            if (saved) {
                return ApiResponse.success(rolePermission.getId());
            } else {
                return ApiResponse.error("创建角色权限关联失败");
            }
        } catch (Exception e) {
            log.error("Add role permission failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 更新角色权限关联
     */
    @PutMapping("/update/{id}")
    public ApiResponse<String> updateRolePermission(@PathVariable Long id, @RequestBody RolePermission rolePermission) {
        try {
            log.info("Update role permission request: {}", id);
            rolePermission.setId(id);
            // W8：空请求体 ⇒ 无 SET 子句 ⇒ SQL 语法错；这是客户端的错，回 400。
            if (!UpdateBodyGuard.hasUpdatableField(rolePermission)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            boolean updated = rolePermissionService.updateById(rolePermission);
            if (updated) {
                return ApiResponse.success("角色权限关联已更新");
            } else {
                // W8：0 行 = 这条角色-权限关联不存在（原来回 code 500）。
                return ApiResponse.notFound("角色权限关联不存在");
            }
        } catch (Exception e) {
            log.error("Update role permission failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 删除角色权限关联
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteRolePermission(@PathVariable Long id) {
        try {
            log.info("Delete role permission request: {}", id);
            boolean deleted = rolePermissionService.removeById(id);
            if (deleted) {
                return ApiResponse.success("角色权限关联已删除");
            } else {
                return ApiResponse.error("删除角色权限关联失败");
            }
        } catch (Exception e) {
            log.error("Delete role permission failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据角色ID获取权限列表
     */
    @GetMapping("/by-role/{roleId}")
    public ApiResponse<List<RolePermission>> getRolePermissionsByRoleId(@PathVariable Long roleId) {
        try {
            log.info("Get role permissions by role id request: {}", roleId);
            List<RolePermission> rolePermissions = rolePermissionService.getByRoleId(roleId);
            return ApiResponse.success(rolePermissions);
        } catch (Exception e) {
            log.error("Get role permissions by role id failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据权限ID获取角色列表
     */
    @GetMapping("/by-permission/{permissionId}")
    public ApiResponse<List<RolePermission>> getRolePermissionsByPermissionId(@PathVariable Long permissionId) {
        try {
            log.info("Get role permissions by permission id request: {}", permissionId);
            List<RolePermission> rolePermissions = rolePermissionService.getByPermissionId(permissionId);
            return ApiResponse.success(rolePermissions);
        } catch (Exception e) {
            log.error("Get role permissions by permission id failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据角色ID和权限ID获取关联
     */
    @GetMapping("/by-role-permission/{roleId}/{permissionId}")
    public ApiResponse<RolePermission> getRolePermissionByRoleIdAndPermissionId(@PathVariable Long roleId, @PathVariable Long permissionId) {
        try {
            log.info("Get role permission by role id and permission id request: roleId={}, permissionId={}", roleId, permissionId);
            RolePermission rolePermission = rolePermissionService.getByRoleIdAndPermissionId(roleId, permissionId);
            if (rolePermission == null) {
                return ApiResponse.error(404, "角色权限关联不存在");
            }
            return ApiResponse.success(rolePermission);
        } catch (Exception e) {
            log.error("Get role permission by role id and permission id failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 批量保存角色权限关联
     */
    @PostMapping("/batch-add")
    public ApiResponse<String> batchAddRolePermissions(@RequestBody List<RolePermission> rolePermissions) {
        try {
            log.info("Batch add role permissions request: size={}", rolePermissions.size());
            boolean saved = rolePermissionService.saveBatch(rolePermissions);
            if (saved) {
                return ApiResponse.success("批量创建角色权限关联成功");
            } else {
                return ApiResponse.error("批量创建角色权限关联失败");
            }
        } catch (Exception e) {
            log.error("Batch add role permissions failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据角色ID删除所有关联
     */
    @DeleteMapping("/delete-by-role/{roleId}")
    public ApiResponse<String> deleteRolePermissionsByRoleId(@PathVariable Long roleId) {
        try {
            log.info("Delete role permissions by role id request: {}", roleId);
            boolean deleted = rolePermissionService.removeByRoleId(roleId);
            if (deleted) {
                return ApiResponse.success("角色权限关联已全部删除");
            } else {
                return ApiResponse.error("删除角色权限关联失败");
            }
        } catch (Exception e) {
            log.error("Delete role permissions by role id failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取所有角色权限关联列表（不分页）
     */
    @GetMapping("/all")
    public ApiResponse<List<RolePermission>> getAllRolePermissions() {
        try {
            log.info("Get all role permissions request");
            List<RolePermission> rolePermissions = rolePermissionService.list(new QueryWrapper<>());
            return ApiResponse.success(rolePermissions);
        } catch (Exception e) {
            log.error("Get all role permissions failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * Gateway 专用：返回角色权限映射，供网关 RBAC 鉴权使用。
     *
     * <p>响应体：
     * <pre>
     * {
     *   "roles": { "1": "ROLE_STUDENT", "2": "ROLE_TEACHER", ..., "6": "ROLE_ASSISTANT" },
     *   "map":   { "ROLE_STUDENT": ["PERM_STUDENT_VIEW", ...], ... }
     * }
     * </pre>
     *
     * <p><b>为什么要有 {@code roles} 这一段</b>（2026-10-02 W3.3）：网关拿到的角色是
     * {@code users.role} 的**整数 id**，而权限表的键是 {@code roles.code}（字符串）。
     * 原来网关是用一个写死的 {@code switch(1..5)} 做这层翻译、{@code default} 回
     * {@code "unknown"} —— 也就是说**管理员在权限管理页新建的角色（id ≥ 6）永远翻译不出来**，
     * 无论给它勾多少权限都等于没勾。现在把 {@code roles} 全表原样带出去，
     * 网关按 id 查，任何角色（含新建的）都能表达。
     *
     * <p>注意 {@code roles} 是**全表**（含一条权限都没绑的角色），而 {@code map} 只含有绑定的
     * 角色 —— 没绑定的角色查出来是"空权限集"，语义是"什么都做不了"，与"角色不存在"要能区分开。
     *
     * <p>⚠️ 这个端点在 auth-service 的 {@code SecurityConfig} 里是 <b>{@code permitAll}</b>，
     * 见那边的注释：它是网关在**启动时**（还没有任何用户身份）就要读的只读字典，
     * 内容是功能模块清单、不含任何账号或凭据。
     */
    @GetMapping("/rbac-map")
    public ApiResponse<Map<String, Object>> getRbacMap() {
        try {
            List<Role> roles = roleMapper.selectList(new QueryWrapper<>());
            List<Permission> permissions = permissionMapper.selectList(new QueryWrapper<>());
            List<RolePermission> rps = rolePermissionService.list(new QueryWrapper<>());

            Map<Long, String> roleIdToCode = roles.stream()
                    .filter(r -> r.getCode() != null)
                    .collect(Collectors.toMap(Role::getId, Role::getCode, (a, b) -> a));
            Map<Long, String> permIdToCode = permissions.stream()
                    .filter(p -> p.getCode() != null)
                    .collect(Collectors.toMap(Permission::getId, Permission::getCode, (a, b) -> a));

            Map<String, List<String>> rbacMap = new HashMap<>();
            for (RolePermission rp : rps) {
                String roleCode = roleIdToCode.get(rp.getRoleId());
                String permCode = permIdToCode.get(rp.getPermissionId());
                if (roleCode != null && permCode != null) {
                    rbacMap.computeIfAbsent(roleCode, k -> new ArrayList<>()).add(permCode);
                }
            }

            // 角色 id → 角色码：**全表**，这样网关能把任意 users.role 翻译成角色码
            Map<String, String> rolesById = new LinkedHashMap<>();
            roles.stream()
                    .filter(r -> r.getId() != null && r.getCode() != null)
                    .sorted(Comparator.comparing(Role::getId))
                    .forEach(r -> rolesById.put(String.valueOf(r.getId()), r.getCode()));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("roles", rolesById);
            body.put("map", rbacMap);
            return ApiResponse.success(body);
        } catch (Exception e) {
            log.error("构建RBAC映射失败: {}", e.getMessage());
            return ApiResponse.error("RBAC数据加载失败");
        }
    }

    @GetMapping("/count")
    public ApiResponse<Long> getRolePermissionCount(@RequestParam(required = false) Long roleId, @RequestParam(required = false) Long permissionId) {
        try {
            log.info("Get role permission count request: roleId={}, permissionId={}", roleId, permissionId);
            QueryWrapper<RolePermission> queryWrapper = new QueryWrapper<>();
            if (roleId != null) {
                queryWrapper.eq("role_id", roleId);
            }
            if (permissionId != null) {
                queryWrapper.eq("permission_id", permissionId);
            }
            long count = rolePermissionService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get role permission count failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }
}
