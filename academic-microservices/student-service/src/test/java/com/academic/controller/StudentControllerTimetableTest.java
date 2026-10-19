package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.mapper.CourseTimetableMapper;
import com.academic.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 「我的课表」端点（W48，2026-10-17）：{@code GET /students/timetable/{userId}}。
 *
 * <p><b>修之前是什么样</b>：{@code course_plans} 表、{@code CoursePlanController}、
 * 10 行排课数据全都在，但这个能力**没有任何出口** —— 学生端看不到课表。本轮补上。
 *
 * <p>本组用例钉两件事：
 * <ol>
 *   <li><b>id 空间</b>：路径参数是 {@code users.id}，闸用 {@code denyUnlessSelf}
 *       （不是 {@code denyUnlessSelfProfile} —— 用错帮手会把本人也拦下）；</li>
 *   <li><b>归属闸真的在跑</b>：换一个 userId 必须是 403 **且 mapper 一次没被调用**
 *       —— 和 W11 那族同一条判据，"先查了再报错"同样是泄漏。</li>
 * </ol>
 *
 * <p>⚠️ 被拒的形状是 **HTTP 200 + body {@code code:403}**（控制器业务拒绝），
 * 不是 Spring Security 的 403 空体。断言要读 {@code res.getCode()}，读 HTTP 码会误判。
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerTimetableTest {

    @Mock private StudentService studentService;
    @Mock private CourseTimetableMapper courseTimetableMapper;
    @InjectMocks private StudentController controller;

    /** 司明：users.id=27。 */
    private static final long ME = 27L;
    /** 同学：users.id=30。 */
    private static final long OTHER = 30L;

    /**
     * ⚠️ 这个 mapper 在控制器上是**字段级 `@Autowired`**（不是构造器参数），
     * 所以 Mockito 的 `@InjectMocks` **注入不到它** —— 只喂 {@code @Mock} 的话它会留 null，
     * 端点走进 catch 回 500，三条放行用例全红。这里显式塞进去。
     *
     * <p>（别改成构造器注入：构造器已经在用，加参数会让本仓另外六个
     * {@code StudentController*Test} 一起需要多喂一个 mock，改动面比收益大。）
     */
    @BeforeEach
    void injectMapper() {
        ReflectionTestUtils.setField(controller, "courseTimetableMapper", courseTimetableMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private HttpServletRequest req(String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    // ==================== 放行面 ====================

    @Test
    @DisplayName("读本人的课表 ⇒ 放行，且 mapper 收到的正是 users.id 本人")
    void ownTimetableAllowed() {
        loginAs(ME);
        when(courseTimetableMapper.listForStudent(ME))
                .thenReturn(List.of(Map.of("planId", 1, "courseName", "高等数学")));

        ApiResponse<Object> res = controller.getMyTimetable(String.valueOf(ME), req("1"));

        assertEquals(200, res.getCode());
        // ★ 承重断言：传进来的必须是 users.id=27。若误用 profile id，这里会看到 3。
        verify(courseTimetableMapper).listForStudent(ME);
    }

    @Test
    @DisplayName("管理员可代看（与其余读端点的管理员旁路一致）")
    void adminBypassAllowed() {
        loginAs(1L);
        when(courseTimetableMapper.listForStudent(ME)).thenReturn(List.of());

        ApiResponse<Object> res = controller.getMyTimetable(String.valueOf(ME), req("4"));

        assertEquals(200, res.getCode());
        verify(courseTimetableMapper).listForStudent(ME);
    }

    // ==================== 拒绝面 ====================

    @Test
    @DisplayName("★ 读同学的课表 ⇒ 403，且 mapper 一次都没被调用（不是「先查了再报错」）")
    void otherTimetableRejected() {
        loginAs(ME);

        ApiResponse<Object> res = controller.getMyTimetable(String.valueOf(OTHER), req("1"));

        assertEquals(403, res.getCode());
        verify(courseTimetableMapper, never()).listForStudent(anyLong());
    }

    @Test
    @DisplayName("未登录（SecurityContext 空）⇒ 403，不是 NPE 也不是放行")
    void anonymousRejected() {
        SecurityContextHolder.clearContext();

        ApiResponse<Object> res = controller.getMyTimetable(String.valueOf(ME), req(null));

        assertEquals(403, res.getCode());
        verify(courseTimetableMapper, never()).listForStudent(anyLong());
    }

    // ==================== 空课表不是错误 ====================

    @Test
    @DisplayName("没选课 ⇒ 200 + 空列表（不是 403、也不是 500 —— 别把空当异常）")
    void emptyTimetableIsSuccess() {
        loginAs(ME);
        // 注意用 List.of() 而非 null：mapper 的 JOIN 不成立时 MyBatis 回空表，从不回 null。
        when(courseTimetableMapper.listForStudent(ME)).thenReturn(List.of());

        ApiResponse<Object> res = controller.getMyTimetable(String.valueOf(ME), req("1"));

        assertEquals(200, res.getCode());
        assertNotNull(res.getData());
    }
}
