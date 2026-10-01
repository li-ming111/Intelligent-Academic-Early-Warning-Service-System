package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Classes;
import com.academic.mapper.ClassMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班级控制器 —— 只提供一份**只读目录**，给**登录前**的注册页选班级用。
 *
 * <h3>为什么这个类现在才存在</h3>
 * 网关路由表与小程序早就按 {@code GET /api/auth/classes} 在调了：
 * 小程序 {@code src/services/api.js} 的 {@code getClasses(collegeId)}、
 * 注册页 {@code pages/auth/register.vue} 的 {@code loadClasses} 都指向它；
 * 网关白名单里也曾写着 {@code /api/auth/classes}。**但全仓没有这个接口**
 * （{@code grep} 全空）—— 请求打到网关后落到 {@code /api/auth/**} 的兜底路由，
 * user-service 里没有这个映射，于是 404；小程序那边 {@code catch (e) { classes.value = [] }}
 * 一吞，表现为"班级下拉永远空、也不报错"。
 *
 * <p>与 {@code /majors/all}、{@code /colleges/all} 同一种形状：
 * 走网关的 {@code StripPrefix=2 + RewritePath}，落地路径是 {@code /classes/all}，
 * 且在 {@code SecurityConfig.PUBLIC_DIRECTORY} 里 {@code permitAll}（登录前要读）。
 *
 * <p>⚠️ 返回的是 {@link Classes} 实体，其中的 {@code teacherId} / {@code counselorId}
 * 是 **users.id**（同一列名在不同表里是两个 ID 空间）。这两个字段对注册页没用，
 * 但一起返回不构成泄露（班主任/辅导员的 user id 本身不是秘密，班级列表本来就在
 * 学生端各处出现）。真要收紧，就在 SELECT 里点名要 id/name/majorId/collegeId 四列。
 */
@Slf4j
@RestController
@RequestMapping("/classes")
public class ClassController {

    private final ClassMapper classMapper;

    public ClassController(ClassMapper classMapper) {
        this.classMapper = classMapper;
    }

    /**
     * 班级目录（不分页）。
     *
     * @param collegeId 可选。注册页先选学院再选班级，所以按学院过滤是主用法；
     *                  不传则返回全部（8 条）。
     */
    @GetMapping("/all")
    public ApiResponse<List<Classes>> getAllClasses(@RequestParam(required = false) Long collegeId) {
        try {
            QueryWrapper<Classes> queryWrapper = new QueryWrapper<>();
            if (collegeId != null) {
                queryWrapper.eq("college_id", collegeId);
            }
            queryWrapper.orderByAsc("id");
            List<Classes> classes = classMapper.selectList(queryWrapper);
            log.info("Get classes request: collegeId={}, rows={}", collegeId, classes.size());
            return ApiResponse.success(classes);
        } catch (Exception e) {
            log.error("Get classes failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
