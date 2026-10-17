package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Major;
import com.academic.common.util.UpdateBodyGuard;
import com.academic.common.util.ExceptionMessages;
import com.academic.service.MajorService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 专业控制器
 */
@Slf4j
@RestController
@RequestMapping("/majors")
public class MajorController {

    private final MajorService majorService;

    public MajorController(MajorService majorService) {
        this.majorService = majorService;
    }

    /**
     * 获取专业列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getMajors(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long collegeId) {
        try {
            log.info("Get majors request: page={}, size={}, collegeId={}", page, size, collegeId);
            QueryWrapper<Major> queryWrapper = new QueryWrapper<>();
            if (collegeId != null) {
                queryWrapper.eq("college_id", collegeId);
            }
            queryWrapper.orderByDesc("created_at");
            
            Page<Major> majorPage = new Page<>(page, size);
            majorPage = majorService.page(majorPage, queryWrapper);
            
            Map<String, Object> responseData = Map.of(
                    "data", majorPage.getRecords(),
                    "total", majorPage.getTotal(),
                    "page", page,
                    "size", size,
                    "pages", majorPage.getPages()
            );
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("Get majors failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取专业详情
     */
    @GetMapping("/info/{majorId}")
    public ApiResponse<Major> getMajorInfo(@PathVariable Long majorId) {
        try {
            log.info("Get major info request: {}", majorId);
            Major major = majorService.getById(majorId);
            if (major == null) {
                return ApiResponse.error(404, "专业不存在");
            }
            return ApiResponse.success(major);
        } catch (Exception e) {
            log.error("Get major info failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 创建专业
     */
    @PostMapping("/add")
    public ApiResponse<Long> addMajor(@RequestBody Major major) {
        try {
            log.info("Add major request: {}", major.getName());
            boolean saved = majorService.save(major);
            if (saved) {
                return ApiResponse.success(major.getId());
            } else {
                return ApiResponse.error("创建专业失败");
            }
        } catch (Exception e) {
            log.error("Add major failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 更新专业
     */
    @PutMapping("/update/{majorId}")
    public ApiResponse<String> updateMajor(@PathVariable Long majorId, @RequestBody Major major) {
        try {
            log.info("Update major request: {}", majorId);
            major.setId(majorId);
            // W8：空请求体 ⇒ 无 SET 子句 ⇒ SQL 语法错；这是客户端的错，回 400（原来是 200+code500）。
            if (!UpdateBodyGuard.hasUpdatableField(major)) {
                return ApiResponse.badRequest("请求体里没有任何可更新的字段");
            }
            boolean updated = majorService.updateById(major);
            if (updated) {
                return ApiResponse.success("专业已更新");
            } else {
                // W8：0 行 = 该专业不存在（原来回 code 500）。
                return ApiResponse.notFound("专业不存在");
            }
        } catch (Exception e) {
            log.error("Update major failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 删除专业
     */
    @DeleteMapping("/delete/{majorId}")
    public ApiResponse<String> deleteMajor(@PathVariable Long majorId) {
        try {
            log.info("Delete major request: {}", majorId);
            boolean deleted = majorService.removeById(majorId);
            if (deleted) {
                return ApiResponse.success("专业已删除");
            } else {
                return ApiResponse.error("删除专业失败");
            }
        } catch (Exception e) {
            log.error("Delete major failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据代码获取专业信息
     */
    @GetMapping("/by-code/{code}")
    public ApiResponse<Major> getMajorByCode(@PathVariable String code) {
        try {
            log.info("Get major by code request: {}", code);
            Major major = majorService.getByCode(code);
            if (major == null) {
                return ApiResponse.error(404, "专业不存在");
            }
            return ApiResponse.success(major);
        } catch (Exception e) {
            log.error("Get major by code failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 根据学院ID获取专业列表
     */
    @GetMapping("/by-college/{collegeId}")
    public ApiResponse<java.util.List<Major>> getMajorsByCollegeId(@PathVariable Long collegeId) {
        try {
            log.info("Get majors by college id request: {}", collegeId);
            java.util.List<Major> majors = majorService.getByCollegeId(collegeId);
            return ApiResponse.success(majors);
        } catch (Exception e) {
            log.error("Get majors by college id failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取所有专业列表（不分页）。
     *
     * <p>{@code collegeId} 是 2026-09-30 补的：这条接口是注册页（小程序
     * {@code api.js} 的 {@code getMajorsByCollege(collegeId)} → {@code /auth/majors?collegeId=}）
     * 用来看"某学院有哪些专业"的，但从前的实现**根本不看这个参数**，一律返回全部 12 条。
     * 后果是小程序注册页选了 A 学院，专业下拉里仍能选到 B 学院的专业；学生这么选下去，
     * 档案要么被后端把学院静默改成专业所属的那个（页面显示的学院就成了一句空话），
     * 要么在班级与专业对不上时直接报错。同模块的 {@code /majors/list} 与 {@code /majors/count}
     * 一直支持这个参数，唯独 {@code /all} 漏了 —— 这里补齐，口径与那两条一致：
     * 不传 = 全部。
     */
    @GetMapping("/all")
    public ApiResponse<java.util.List<Major>> getAllMajors(@RequestParam(required = false) Long collegeId) {
        try {
            log.info("Get all majors request: collegeId={}", collegeId);
            QueryWrapper<Major> queryWrapper = new QueryWrapper<>();
            if (collegeId != null) {
                queryWrapper.eq("college_id", collegeId);
            }
            java.util.List<Major> majors = majorService.list(queryWrapper);
            return ApiResponse.success(majors);
        } catch (Exception e) {
            log.error("Get all majors failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 获取专业总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> getMajorCount(@RequestParam(required = false) Long collegeId) {
        try {
            log.info("Get major count request: collegeId={}", collegeId);
            QueryWrapper<Major> queryWrapper = new QueryWrapper<>();
            if (collegeId != null) {
                queryWrapper.eq("college_id", collegeId);
            }
            long count = majorService.count(queryWrapper);
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("Get major count failed: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }
}
