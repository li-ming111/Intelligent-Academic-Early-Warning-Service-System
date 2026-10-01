package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.service.CollegeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 *
 * <p><b>2026-10-01 第二十三轮：又把 7 个零调用方端点删掉了，本控制器现在只剩
 * {@link #getAllColleges()} 一个。</b>删掉的是 {@code /list}（分页）、{@code /info/{id}}、
 * {@code /add}、{@code /update/{id}}、{@code /delete/{id}}、{@code /by-code/{code}}、
 * {@code /count} —— 网关 {@code Path=/api/colleges/**} + {@code StripPrefix=1} 确实能把它们
 * 送到这里（实测带 token 打 {@code /api/colleges/list} 是 200，**不是**不可达，是没人调），
 * 但全仓零调用方：PC/小程序打的都是 {@code /admin/colleges/**}，而那条走 admin-service
 * 直接查库；学院的增删改查因此是本模块的**重复实现**。真正活的只有 {@code /colleges/all}，
 * 由白名单那两条路（{@code /api/auth/colleges} 经 RewritePath、{@code /api/colleges/all}）
 * 给四个注册页供学院下拉。
 *
 * <p>为什么保留 {@code /all} 而不整个删掉本控制器：{@code /colleges/all} 在网关
 * {@code WHITE_LIST} 和本服务 {@code SecurityConfig.PUBLIC_DIRECTORY} 两处都挂了名，
 * 注册页（登录之前）依赖它。删了会让教师/辅导员注册页的学院下拉变空、表单提交不了
 * —— 这正是 2026-09-30 那次 403 事故的形态。
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
     * 获取所有学院列表（不分页）。
     *
     * <p>只回 {@code id/name/code/description} 四个字段 —— 正好是四个注册页真正在用的东西。
     */
    @GetMapping("/all")
    public ApiResponse<List<Map<String, Object>>> getAllColleges() {
        try {
            log.info("Get all colleges request");
            List<College> colleges = collegeService.listAll();

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

}
