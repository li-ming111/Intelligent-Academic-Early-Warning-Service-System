package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.common.util.ExceptionMessages;
import com.academic.entity.Role;
import com.academic.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 角色控制器
 */
@Slf4j
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 获取角色列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRoles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status) {
        try {
            log.info("Get roles request: page={}, size={}, name={}, code={}, status={}", page, size, name, code, status);
            QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
            if (name != null && !name.isEmpty()) {
                queryWrapper.like("name", name);
            }
            if (code != null && !code.isEmpty()) {
                queryWrapper.like("code", code);
            }
            if (status != null) {
                queryWrapper.eq("status", status);
            }
            queryWrapper.orderByDesc("created_at");
            
            Page<Role> rolePage = new Page<>(page, size);
            rolePage = roleService.page(rolePage, queryWrapper);
            
            Map<String, Object> responseData = Map.of(
                    "data", rolePage.getRecords(),
                    "total", rolePage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", rolePage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get roles failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/info/{id}")
    public ApiResponse<Role> getRoleInfo(@PathVariable Long id) {
        try {
            log.info("Get role info request: {}", id);
            Role role = roleService.getById(id);
            if (role == null) {
                return ApiResponse.error(404, "角色不存在");
            }
            return ApiResponse.success(role);
        } catch (Exception e) {
            log.error("Get role info failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 创建角色
     */
    @PostMapping("/add")
    public ApiResponse<Long> addRole(@RequestBody Role role) {
        try {
            log.info("Add role request: name={}, code={}", role.getName(), role.getCode());
            // 检查角色代码是否已存在
            Role existing = roleService.getByCode(role.getCode());
            if (existing != null) {
                return ApiResponse.error("角色代码已存在");
            }
            boolean saved = roleService.save(role);
            if (saved) {
                return ApiResponse.success(role.getId());
            } else {
                return ApiResponse.error("创建角色失败");
            }
        } catch (Exception e) {
            log.error("Add role failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 更新角色
     */
    @PutMapping("/update/{id}")
    public ApiResponse<String> updateRole(@PathVariable Long id, @RequestBody Role role) {
        try {
            log.info("Update role request: {}", id);
            role.setId(id);
            // W8：空请求体 ⇒ 无 SET 子句 ⇒ SQL 语法错；这是客户端的错，回 400 而不是内部错误。
            if (!UpdateBodyGuard.hasUpdatableField(role)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            // 检查角色代码是否已存在（排除当前角色）
            Role existing = roleService.getByCode(role.getCode());
            if (existing != null && !existing.getId().equals(id)) {
                return ApiResponse.error("角色代码已存在");
            }
            boolean updated = roleService.updateById(role);
            if (updated) {
                return ApiResponse.success("角色已更新");
            } else {
                // W8：0 行 = 该角色不存在（原来回 code 500）。
                return ApiResponse.notFound("角色不存在");
            }
        } catch (Exception e) {
            log.error("Update role failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteRole(@PathVariable Long id) {
        try {
            log.info("Delete role request: {}", id);
            boolean deleted = roleService.removeById(id);
            if (deleted) {
                return ApiResponse.success("角色已删除");
            } else {
                return ApiResponse.error("删除角色失败");
            }
        } catch (Exception e) {
            log.error("Delete role failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据代码获取角色
     */
    @GetMapping("/by-code/{code}")
    public ApiResponse<Role> getRoleByCode(@PathVariable String code) {
        try {
            log.info("Get role by code request: {}", code);
            Role role = roleService.getByCode(code);
            if (role == null) {
                return ApiResponse.error(404, "角色不存在");
            }
            return ApiResponse.success(role);
        } catch (Exception e) {
            log.error("Get role by code failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据状态获取角色列表
     */
    @GetMapping("/by-status/{status}")
    public ApiResponse<java.util.List<Role>> getRolesByStatus(@PathVariable Integer status) {
        try {
            log.info("Get roles by status request: {}", status);
            java.util.List<Role> roles = roleService.getByStatus(status);
            return ApiResponse.success(roles);
        } catch (Exception e) {
            log.error("Get roles by status failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取所有角色列表（不分页）
     */
    @GetMapping("/all")
    public ApiResponse<java.util.List<Role>> getAllRoles() {
        try {
            log.info("Get all roles request");
            java.util.List<Role> roles = roleService.list(new QueryWrapper<>());
            return ApiResponse.success(roles);
        } catch (Exception e) {
            log.error("Get all roles failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取角色总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getRoleCount(@RequestParam(required = false) String name, @RequestParam(required = false) String code, @RequestParam(required = false) Integer status) {
        try {
            log.info("Get role count request: name={}, code={}, status={}", name, code, status);
            QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
            if (name != null && !name.isEmpty()) {
                queryWrapper.like("name", name);
            }
            if (code != null && !code.isEmpty()) {
                queryWrapper.like("code", code);
            }
            if (status != null) {
                queryWrapper.eq("status", status);
            }
            long count = roleService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get role count failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }
}
