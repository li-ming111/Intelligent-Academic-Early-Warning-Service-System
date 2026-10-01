package com.academic.config;

import com.academic.controller.ClassController;
import com.academic.controller.MajorController;
import com.academic.common.entity.Classes;
import com.academic.common.entity.Major;
import com.academic.mapper.ClassMapper;
import com.academic.service.MajorService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册页要用的「学院/专业/班级目录」接口，**匿名**必须能读到。
 *
 * <h3>为什么要有这个测试</h3>
 * 网关的 {@code AuthenticationFilter.WHITE_LIST} 里有 {@code /api/auth/colleges}、
 * {@code /api/auth/majors}（后来还有 {@code /api/auth/classes}），意思是"这三个不查 token"。
 * 但**网关放行 ≠ 下游放行**：网关照白名单放行时**不会注入** {@code X-User-Id}/{@code X-Role}
 * 请求头（它没有 token 可解析），而下游每个服务都装着 {@code GatewayAuthFilter} ——
 * 那个过滤器"没有头就不建 {@code Authentication}"，于是请求以**匿名**身份到达
 * Spring Security，撞上本服务的 {@code .anyRequest().authenticated()} → **403**。
 *
 * <p>后果是登录前的注册页：学院/专业下拉框**永远是空的**（前端 {@code catch} 里只有一句
 * "加载失败"或干脆静默置空），而教师/辅导员注册表单里学院是必填 → 那两个页面**根本提交不了**。
 * 换任何 token 也救不了：注册页在登录页之前，用户还没有 token。
 *
 * <p>所以这些目录接口必须在本服务的 {@code SecurityConfig} 里显式 {@code permitAll}。
 * 本测试就是钉住这件事的：**匿名可读**（否则注册页废掉），**其余路径仍要认证**
 * （否则是把整个服务敞开，那是另一个极端）。
 *
 * <p>跑起来不需要 Nacos/MySQL：{@code @WebMvcTest} 只装配 Web + Security 两片，
 * 服务层用 mock。故意如此 —— 这条测试必须能在任何机器上单跑。
 */
@WebMvcTest(controllers = {MajorController.class, ClassController.class})
@Import(SecurityConfig.class)
class PublicDirectoryAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MajorService majorService;

    @MockBean
    private ClassMapper classMapper;

    @Test
    @DisplayName("匿名 GET /majors/all（注册页的专业下拉）→ 200，不是 403")
    void anonymousCanReadPublicMajorDirectory() throws Exception {
        mockMvc.perform(get("/majors/all"))
                .andExpect(status().isOk());
    }

    /**
     * 匿名读班级目录（小程序注册页选班级）。顺带钉住响应形状：
     * {@code data} 必须是**数组**且元素带 {@code id/name} —— 小程序侧
     * {@code classes.value = d || []} 后按 {@code c.name} 渲染，形状一变下拉就空。
     */
    @Test
    @DisplayName("匿名 GET /classes/all → 200，且 data 是数组（含 id/name）")
    void anonymousCanReadClassDirectory() throws Exception {
        Classes one = new Classes();
        one.setId(1L);
        one.setName("计科2306班");
        one.setCollegeId(1L);
        when(classMapper.selectList(any())).thenReturn(List.of(one));

        mockMvc.perform(get("/classes/all").param("collegeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("计科2306班"));
    }

    @Test
    @DisplayName("匿名 GET /majors/all 仍然是只读的那一条：分页管理接口 /majors/list 必须 403")
    void anonymousStillCannotReachPagedManagementEndpoint() throws Exception {
        mockMvc.perform(get("/majors/list"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("带上网关注入的 X-User-Id/X-Role（已认证）→ 200，证明放行只看路径、不看有没有头")
    void authenticatedRequestStillWorks() throws Exception {
        mockMvc.perform(get("/majors/all")
                        .header("X-User-Id", "27")
                        .header("X-Username", "2023020616")
                        .header("X-Role", "1"))
                .andExpect(status().isOk());
    }

    /**
     * 小程序注册页按 {@code /auth/majors?collegeId=} 取"本学院的专业"，但 {@code /majors/all}
     * **从前完全无视这个参数**，一律返回全部 12 条 —— 选了 A 学院也能选到 B 学院的专业。
     * 这条测试直接读控制器交给 MyBatis-Plus 的 {@code QueryWrapper} 的 SQL 片段：
     * 带 collegeId 时必须含 {@code college_id} 条件，不带时**必须不含**（不传 = 全部）。
     */
    @Test
    @DisplayName("collegeId 真的进了 where：带参数按学院过滤，不带走全量")
    void majorDirectoryHonoursCollegeIdFilter() throws Exception {
        when(majorService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/majors/all").param("collegeId", "3"))
                .andExpect(status().isOk());
        String withParam = lastSqlSegment();
        assertTrue(withParam.contains("college_id"),
                "带 collegeId 时应按 college_id 过滤，实际 SQL 片段: " + withParam);

        mockMvc.perform(get("/majors/all"))
                .andExpect(status().isOk());
        String withoutParam = lastSqlSegment();
        assertFalse(withoutParam.contains("college_id"),
                "不带 collegeId 时应查全量，实际 SQL 片段: " + withoutParam);
    }

    /** 取**最近一次** {@code majorService.list(...)} 收到的 QueryWrapper 的 SQL 片段。 */
    private String lastSqlSegment() {
        ArgumentCaptor<QueryWrapper<Major>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(majorService, atLeastOnce()).list(captor.capture());
        List<QueryWrapper<Major>> all = captor.getAllValues();
        return all.get(all.size() - 1).getSqlSegment();
    }
}
