package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.service.CollegeService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学院控制器。
 *
 * <p><b>⚠️ 2026-10-01 第十八轮：这里原先对每个学院各发 3 次 Feign 调用
 * （学生/教师/辅导员计数），已整族删除 —— 那些调用<u>从来没有成功过</u>。</b>
 *
 * <p>原因是本模块<b>没有</b> {@code feign.RequestInterceptor}（全仓只有 admin-service
 * 与 ai-service 有），Feign 请求不带 {@code X-User-Id}/{@code X-Username}/{@code X-Role}；
 * 下游三个服务都只 {@code permitAll} 了 {@code /actuator/health}、其余
 * {@code .anyRequest().authenticated()}，无头就不建 Authentication ⇒ 一律 403。
 * 实测：{@code GET /colleges/all} 每次请求在日志里留下 <b>12 条</b>
 * {@code [403] during [GET] to [http://student-service/students/count-by-college?collegeId=1]}，
 * 三个计数恒为 {@code 0}（软件学院真实是 24/5/1）。
 *
 * <p><b>别照抄 admin-service 的 RequestInterceptor 来"修"它 —— 在这条路上修不好。</b>
 * 本控制器唯一的真实调用方是 <b>匿名</b>的注册页（{@code /api/auth/colleges} 在网关
 * {@code WHITE_LIST} 里，{@code AuthenticationFilter} 命中白名单直接放行、<u>不注入</u>
 * 身份头），所以拦截器根本无头可抄，加了照样 0。
 *
 * <p>这三个计数本来就<b>零读者</b>：四个消费方（PC 三个注册页 + 小程序注册页）只取
 * {@code id/name/code}。带计数的学院管理页走的是 admin-service 的 {@code /admin/colleges}，
 * 那边是直接查库（{@code AdminController} 的 {@code studentProfileMapper.selectCount}）。
 */
@Slf4j
@RestController
@RequestMapping("/colleges")
public class CollegeController {

    private final CollegeService collegeService;

    public CollegeController(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    /**
     * 获取学院列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getColleges(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            log.info("Get colleges request: page={}, size={}", page, size);
            QueryWrapper<College> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByDesc("created_at");
            
            Page<College> collegePage = new Page<>(page, size);
            collegePage = collegeService.page(collegePage, queryWrapper);
            
            Map<String, Object> responseData = Map.of(
                    "data", collegePage.getRecords(),
                    "total", collegePage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", collegePage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get colleges failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取学院详情
     */
    @GetMapping("/info/{collegeId}")
    public ApiResponse<Map<String, Object>> getCollegeInfo(@PathVariable Long collegeId) {
        try {
            log.info("Get college info request: {}", collegeId);
            College college = collegeService.getById(collegeId);
            if (college == null) {
                return ApiResponse.error(404, "学院不存在");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", college.getId());
            result.put("name", college.getName());
            result.put("code", college.getCode());
            result.put("description", college.getDescription());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Get college info failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建学院
     */
    @PostMapping("/add")
    public ApiResponse<Long> addCollege(@RequestBody College college) {
        try {
            log.info("Add college request: {}", college.getName());
            boolean saved = collegeService.save(college);
            if (saved) {
                return ApiResponse.success(college.getId());
            } else {
                return ApiResponse.error("创建学院失败");
            }
        } catch (Exception e) {
            log.error("Add college failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新学院
     */
    @PutMapping("/update/{collegeId}")
    public ApiResponse<String> updateCollege(@PathVariable Long collegeId, @RequestBody College college) {
        try {
            log.info("Update college request: {}", collegeId);
            college.setId(collegeId);
            boolean updated = collegeService.updateById(college);
            if (updated) {
                return ApiResponse.success("学院已更新");
            } else {
                return ApiResponse.error("更新学院失败");
            }
        } catch (Exception e) {
            log.error("Update college failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 删除学院
     */
    @DeleteMapping("/delete/{collegeId}")
    public ApiResponse<String> deleteCollege(@PathVariable Long collegeId) {
        try {
            log.info("Delete college request: {}", collegeId);
            boolean deleted = collegeService.removeById(collegeId);
            if (deleted) {
                return ApiResponse.success("学院已删除");
            } else {
                return ApiResponse.error("删除学院失败");
            }
        } catch (Exception e) {
            log.error("Delete college failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 根据代码获取学院信息
     */
    @GetMapping("/by-code/{code}")
    public ApiResponse<College> getCollegeByCode(@PathVariable String code) {
        try {
            log.info("Get college by code request: {}", code);
            College college = collegeService.getByCode(code);
            if (college == null) {
                return ApiResponse.error(404, "学院不存在");
            }
            return ApiResponse.success(college);
        } catch (Exception e) {
            log.error("Get college by code failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取所有学院列表（不分页，包含统计数据）
     */
    @GetMapping("/all")
    public ApiResponse<List<Map<String, Object>>> getAllColleges() {
        try {
            log.info("Get all colleges request");
            List<College> colleges = collegeService.list(new QueryWrapper<>());
            
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (College college : colleges) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", college.getId());
                item.put("name", college.getName());
                item.put("code", college.getCode());
                item.put("description", college.getDescription());
                resultList.add(item);
            }
            
            return ApiResponse.success(resultList);
        } catch (Exception e) {
            log.error("Get all colleges failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取学院总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getCollegeCount() {
        try {
            log.info("Get college count request");
            long count = collegeService.count();
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get college count failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

}
