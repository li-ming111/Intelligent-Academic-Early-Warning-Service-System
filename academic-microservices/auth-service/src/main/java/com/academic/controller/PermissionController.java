package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.common.util.ExceptionMessages;
import com.academic.entity.Permission;
import com.academic.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 权限控制器
 */
@Slf4j
@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 获取权限列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getPermissions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status) {
        try {
            log.info("Get permissions request: page={}, size={}, name={}, code={}, status={}", page, size, name, code, status);
            QueryWrapper<Permission> queryWrapper = new QueryWrapper<>();
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
            
            Page<Permission> permissionPage = new Page<>(page, size);
            permissionPage = permissionService.page(permissionPage, queryWrapper);
            
            Map<String, Object> responseData = Map.of(
                    "data", permissionPage.getRecords(),
                    "total", permissionPage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", permissionPage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get permissions failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取权限详情
     */
    @GetMapping("/info/{id}")
    public ApiResponse<Permission> getPermissionInfo(@PathVariable Long id) {
        try {
            log.info("Get permission info request: {}", id);
            Permission permission = permissionService.getById(id);
            if (permission == null) {
                return ApiResponse.error(404, "权限不存在");
            }
            return ApiResponse.success(permission);
        } catch (Exception e) {
            log.error("Get permission info failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 创建权限
     */
    @PostMapping("/add")
    public ApiResponse<Long> addPermission(@RequestBody Permission permission) {
        try {
            log.info("Add permission request: name={}, code={}", permission.getName(), permission.getCode());
            // 检查权限代码是否已存在
            Permission existing = permissionService.getByCode(permission.getCode());
            if (existing != null) {
                return ApiResponse.error("权限代码已存在");
            }
            boolean saved = permissionService.save(permission);
            if (saved) {
                return ApiResponse.success(permission.getId());
            } else {
                return ApiResponse.error("创建权限失败");
            }
        } catch (Exception e) {
            log.error("Add permission failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 更新权限
     */
    @PutMapping("/update/{id}")
    public ApiResponse<String> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        try {
            log.info("Update permission request: {}", id);
            permission.setId(id);
            // W8：请求体全 null 时 MyBatis-Plus 生成无 SET 子句的 UPDATE ⇒ SQL 语法错。
            // 这是客户端的错，回 400（原来是落到 catch 里变成 200 + code 500 + 原始 SQL）。
            if (!UpdateBodyGuard.hasUpdatableField(permission)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            // 检查权限代码是否已存在（排除当前权限）
            Permission existing = permissionService.getByCode(permission.getCode());
            if (existing != null && !existing.getId().equals(id)) {
                return ApiResponse.error("权限代码已存在");
            }
            boolean updated = permissionService.updateById(permission);
            if (updated) {
                return ApiResponse.success("权限已更新");
            } else {
                // W8：0 行 = 这条权限不存在，不是服务端故障（原来是 code 500，与
                // admin-service 同场景回 404 的口径不一致）。
                return ApiResponse.notFound("权限不存在");
            }
        } catch (Exception e) {
            log.error("Update permission failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deletePermission(@PathVariable Long id) {
        try {
            log.info("Delete permission request: {}", id);
            boolean deleted = permissionService.removeById(id);
            if (deleted) {
                return ApiResponse.success("权限已删除");
            } else {
                return ApiResponse.error("删除权限失败");
            }
        } catch (Exception e) {
            log.error("Delete permission failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据代码获取权限
     */
    @GetMapping("/by-code/{code}")
    public ApiResponse<Permission> getPermissionByCode(@PathVariable String code) {
        try {
            log.info("Get permission by code request: {}", code);
            Permission permission = permissionService.getByCode(code);
            if (permission == null) {
                return ApiResponse.error(404, "权限不存在");
            }
            return ApiResponse.success(permission);
        } catch (Exception e) {
            log.error("Get permission by code failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据状态获取权限列表
     */
    @GetMapping("/by-status/{status}")
    public ApiResponse<java.util.List<Permission>> getPermissionsByStatus(@PathVariable Integer status) {
        try {
            log.info("Get permissions by status request: {}", status);
            java.util.List<Permission> permissions = permissionService.getByStatus(status);
            return ApiResponse.success(permissions);
        } catch (Exception e) {
            log.error("Get permissions by status failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取所有权限列表（不分页）
     */
    @GetMapping("/all")
    public ApiResponse<java.util.List<Permission>> getAllPermissions() {
        try {
            log.info("Get all permissions request");
            java.util.List<Permission> permissions = permissionService.list(new QueryWrapper<>());
            return ApiResponse.success(permissions);
        } catch (Exception e) {
            log.error("Get all permissions failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取权限总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getPermissionCount(@RequestParam(required = false) String name, @RequestParam(required = false) String code, @RequestParam(required = false) Integer status) {
        try {
            log.info("Get permission count request: name={}, code={}, status={}", name, code, status);
            QueryWrapper<Permission> queryWrapper = new QueryWrapper<>();
            if (name != null && !name.isEmpty()) {
                queryWrapper.like("name", name);
            }
            if (code != null && !code.isEmpty()) {
                queryWrapper.like("code", code);
            }
            if (status != null) {
                queryWrapper.eq("status", status);
            }
            long count = permissionService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get permission count failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }
}
