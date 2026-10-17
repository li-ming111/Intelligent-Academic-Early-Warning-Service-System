package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * W43（2026-10-13）：student-service 里两条**管理口径**端点的角色闸。
 *
 * <p><b>为什么它们此前没有闸</b>：`/students/**` 下其余 61 个端点都能写"归属闸"——
 * 要么比路径里的 {@code {id}} 是不是登录人（{@code denyUnlessSelf}），要么查一次库看
 * 资源主人是谁（{@code denyUnlessMyAppeal/Plan/Notification}）。这两条**没有可比的对象**：
 * 服务层返回的是 {@code student_profile} 的**整行**（姓名 / 学号 / 性别 / 班级 / 学院），
 * 是**全院名册**，不是"我班同学"（同班的公开面另有 {@code class-ranking}，那是刻意的）。
 * 于是它们当时**全靠网关**一条 `path.startsWith(PREFIX_STUDENT) && role != ROLE_STUDENT`
 * —— 那条对**任何一个学生**都是放行的。
 *
 * <p><b>修之前实测</b>（学生 邱琳紫 users.id=30，经网关）：{@code GET /students/list}
 * ⇒ HTTP 200 + **24 行**（全库学生；参数 {@code collegeId} 缺省时还是"全部"）；
 * {@code GET /students/count-by-college?collegeId=1} ⇒ 200 + {@code 24}。
 *
 * <p><b>闸的放行面<b>：系统管理员(4) 与 学院管理员(5)。前者是唯一的后端调用方
 * （admin-service 的 Feign {@code getStudentsByCollege(null)}，请求头透传 role=4）；
 * 后者是"按学院看学生"这个动作本来的使用者。**刻意不放行教师(2)** —— 教师看名单
 * 走 {@code /teachers/students/{id}}，不该从学生自助前缀拿全院花名册。
 *
 * <p><b>判据形状</b>：不只看 403，还要 {@code verify(service, never())} ——
 * 被拒时那次查询压根不该发生（否则"拒绝了但数据还是查了"）。
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerAdminFaceGuardTest {

    @Mock private StudentService studentService;

    @InjectMocks private StudentController controller;

    private HttpServletRequest req(String role) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-Role")).thenReturn(role);
        return r;
    }

    // ==================================================== /students/list

    @Test
    @DisplayName("★ 学生打 /students/list（全院花名册）⇒ 403，且服务层一次都没查库")
    void studentCannotReadWholeRoster() {
        ApiResponse<List<Object>> res = controller.getStudentsByCollege(null, req("1"));

        assertEquals(403, res.getCode(),
                "修之前这条 HTTP 200 + 24 行（实测：学生 30 拿到全库学生）");
        verify(studentService, never()).getStudentsByCollege(any());
    }

    @Test
    @DisplayName("教师打 /students/list ⇒ 403（教师看名单走 /teachers/students/{id}，不是这条）")
    void teacherCannotReadWholeRoster() {
        assertEquals(403, controller.getStudentsByCollege(null, req("2")).getCode());
        verify(studentService, never()).getStudentsByCollege(any());
    }

    @Test
    @DisplayName("辅导员打 /students/list ⇒ 403")
    void counselorCannotReadWholeRoster() {
        assertEquals(403, controller.getStudentsByCollege(null, req("3")).getCode());
        verify(studentService, never()).getStudentsByCollege(any());
    }

    @Test
    @DisplayName("缺 X-Role 头 ⇒ 403（fail-closed，不是「默认放行」）")
    void missingRoleIsDenied() {
        assertEquals(403, controller.getStudentsByCollege(null, req(null)).getCode());
        verify(studentService, never()).getStudentsByCollege(any());
    }

    @Test
    @DisplayName("管理员打 /students/list ⇒ 放行（这就是 admin-service Feign 的调用面）")
    void adminCanReadRoster() {
        when(studentService.getStudentsByCollege(null)).thenReturn(List.of("a", "b"));

        ApiResponse<List<Object>> res = controller.getStudentsByCollege(null, req("4"));

        assertEquals(200, res.getCode());
        assertEquals(2, res.getData().size());
        verify(studentService).getStudentsByCollege(null);
    }

    @Test
    @DisplayName("学院管理员(5) 打 /students/list ⇒ 放行")
    void collegeAdminCanReadRoster() {
        when(studentService.getStudentsByCollege(1L)).thenReturn(List.of("a"));

        assertEquals(200, controller.getStudentsByCollege(1L, req("5")).getCode());
        verify(studentService).getStudentsByCollege(1L);
    }

    // ==================================================== /students/count-by-college

    @Test
    @DisplayName("★ 学生打 /students/count-by-college ⇒ 403，且不查库")
    void studentCannotCountByCollege() {
        ApiResponse<Long> res = controller.countByCollege(1L, req("1"));

        assertEquals(403, res.getCode(), "修之前这条 200 + 24（实测）");
        verify(studentService, never()).countByCollege(any());
    }

    @Test
    @DisplayName("缺头 / 教师 / 辅导员 ⇒ 403")
    void otherRolesCannotCountByCollege() {
        assertEquals(403, controller.countByCollege(1L, req(null)).getCode());
        assertEquals(403, controller.countByCollege(1L, req("2")).getCode());
        assertEquals(403, controller.countByCollege(1L, req("3")).getCode());
        verify(studentService, never()).countByCollege(any());
    }

    @Test
    @DisplayName("管理员 / 学院管理员 ⇒ 放行，且返回真实计数（不是被闸压成 0）")
    void adminCanCountByCollege() {
        when(studentService.countByCollege(1L)).thenReturn(24L);

        ApiResponse<Long> a = controller.countByCollege(1L, req("4"));
        ApiResponse<Long> c = controller.countByCollege(1L, req("5"));

        assertEquals(200, a.getCode());
        assertEquals(24L, a.getData());
        assertEquals(200, c.getCode());
        assertEquals(24L, c.getData());
        verify(studentService, times(2)).countByCollege(1L);
    }
}
