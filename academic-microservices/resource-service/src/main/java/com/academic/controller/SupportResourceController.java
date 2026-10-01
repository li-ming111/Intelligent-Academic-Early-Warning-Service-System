package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.exception.ResourceNotFoundException;
import com.academic.service.SupportResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Support Resource Controller
 *
 * <p><b>为什么每个方法都要显式 catch（2026-09-30）：</b>本模块明明有
 * {@code GlobalExceptionHandler}，但它对这几个端点是**够不着**的 —— 方法体里这层
 * {@code catch (Exception e)} 先把一切都拦下了，异常永远冒不到 advice。
 * 而 advice 又把 {@code RuntimeException} 一律翻成 500（**没有 404 分支**），
 * 所以"资源不存在"必须在这里自己翻成 {@code code=404}。
 *
 * <p>失败的响应体 {@code code} 不再是 200 —— 前端 axios 拦截器（{@code client.js}）
 * 只认 {@code code === 200} 为成功，其余一律 reject。原先 service 返回
 * {@code {status:"error"}} 而这里无条件包 {@code ApiResponse.success(...)}，
 * 失败在两者之间被伪装成了成功。
 */
@Slf4j
@RestController
@RequestMapping("/resources")
public class SupportResourceController {

    private final SupportResourceService supportResourceService;

    public SupportResourceController(SupportResourceService supportResourceService) {
        this.supportResourceService = supportResourceService;
    }

    /**
     * 获取支持资源列表
     */
    @GetMapping("/list")
    public ApiResponse<Object> getResourceList(@RequestParam(required = false) Map<String, Object> params) {
        try {
            log.info("Get resource list request: {}", params);
            return ApiResponse.success(supportResourceService.getResourceList(params));
        } catch (ResourceNotFoundException e) {
            log.warn("Get resource list: 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Get resource list: 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Get resource list failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 获取资源详情
     */
    @GetMapping("/detail/{resourceId}")
    public ApiResponse<Object> getResourceDetail(@PathVariable String resourceId) {
        try {
            log.info("Get resource detail request: {}", resourceId);
            return ApiResponse.success(supportResourceService.getResourceDetail(resourceId));
        } catch (ResourceNotFoundException e) {
            log.warn("Get resource detail: 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Get resource detail: 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Get resource detail failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 新增支持资源
     */
    @PostMapping("/add")
    public ApiResponse<Object> addResource(@RequestBody Map<String, Object> request) {
        try {
            log.info("Add resource request: {}", request);
            return ApiResponse.success(supportResourceService.addResource(request));
        } catch (ResourceNotFoundException e) {
            log.warn("Add resource: 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Add resource: 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Add resource failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 更新支持资源
     */
    @PutMapping("/update/{resourceId}")
    public ApiResponse<Object> updateResource(@PathVariable String resourceId, @RequestBody Map<String, Object> request) {
        try {
            log.info("Update resource request: {} {}", resourceId, request);
            return ApiResponse.success(supportResourceService.updateResource(resourceId, request));
        } catch (ResourceNotFoundException e) {
            log.warn("Update resource: 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Update resource: 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Update resource failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 删除支持资源
     */
    @DeleteMapping("/delete/{resourceId}")
    public ApiResponse<Object> deleteResource(@PathVariable String resourceId) {
        try {
            log.info("Delete resource request: {}", resourceId);
            return ApiResponse.success(supportResourceService.deleteResource(resourceId));
        } catch (ResourceNotFoundException e) {
            log.warn("Delete resource: 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Delete resource: 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Delete resource failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 搜索支持资源
     */
    @PostMapping("/search")
    public ApiResponse<Object> searchResources(@RequestBody Map<String, Object> request) {
        try {
            log.info("Search resources request: {}", request);
            return ApiResponse.success(supportResourceService.searchResources(request));
        } catch (ResourceNotFoundException e) {
            log.warn("Search resources: 目标不存在: {}", e.getMessage());
            return ApiResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Search resources: 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Search resources failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
