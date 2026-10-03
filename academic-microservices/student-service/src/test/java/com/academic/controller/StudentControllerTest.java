package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.dto.StudentDashboardResponse;
import com.academic.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    @Mock
    private StudentService studentService;

    @InjectMocks
    private StudentController studentController;

    @Test
    void testGetStudentDashboard() {
        loginAs(100L);
        StudentDashboardResponse response = new StudentDashboardResponse();
        when(studentService.getStudentDashboard(100L)).thenReturn(response);

        ApiResponse<StudentDashboardResponse> result = studentController.getStudentDashboard("100", req());

        assertNotNull(result);
        verify(studentService, times(1)).getStudentDashboard(100L);
    }

    @Test
    void testGetStudentDashboard_InvalidId() {
        loginAs(999L);
        when(studentService.getStudentDashboard(anyLong())).thenThrow(new RuntimeException("学生不存在"));

        ApiResponse<StudentDashboardResponse> result = studentController.getStudentDashboard("999", req());

        assertNotNull(result);
        verify(studentService, times(1)).getStudentDashboard(anyLong());
    }

    @Test
    void testGetStudentScores() {
        loginAs(100L);
        when(studentService.getStudentScores(100L, null)).thenReturn(Collections.emptyList());

        ApiResponse<Object> result = studentController.getStudentScores("100", null, req());

        assertNotNull(result);
        verify(studentService, times(1)).getStudentScores(100L, null);
    }

    @Test
    void testGetStudentScores_WithSemester() {
        loginAs(100L);
        when(studentService.getStudentScores(100L, "2025-1")).thenReturn(Collections.emptyList());

        ApiResponse<Object> result = studentController.getStudentScores("100", "2025-1", req());

        assertNotNull(result);
        verify(studentService, times(1)).getStudentScores(100L, "2025-1");
    }

    // ==================== 成绩导出（W1.8） ====================

    /**
     * 网关注入的身份头。
     *
     * <p>W11 起读端点也有归属闸：身份取自 {@code SecurityContext}（见 {@link #loginAs}），
     * 而 {@code X-Role} 头只用来判"是不是管理员"。这里的 mock 没有任何头 ⇒ 非管理员。
     */
    private static jakarta.servlet.http.HttpServletRequest req() {
        return mock(jakarta.servlet.http.HttpServletRequest.class);
    }

    /** 把当前登录用户塞进 SecurityContext —— 导出端点靠它做归属校验。 */
    private static void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, Collections.emptyList()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testExportScoresToExcel_streamsWorkbook() throws Exception {
        loginAs(100L);
        byte[] xlsx = {0x50, 0x4B, 0x03, 0x04};   // xlsx 的 zip 头
        when(studentService.exportScoresWorkbook(100L)).thenReturn(xlsx);

        MockHttpServletResponse response = new MockHttpServletResponse();
        studentController.exportScoresToExcel("100", response);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                "内容类型应为 xlsx，实际: " + response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("scores_100.xlsx"));
        assertArrayEquals(xlsx, response.getContentAsByteArray());
    }

    @Test
    void testExportScoresToExcel_rejectsOtherStudent() throws Exception {
        loginAs(100L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        studentController.exportScoresToExcel("101", response);

        assertEquals(403, response.getStatus());
        assertTrue(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).contains("本人"));
        verify(studentService, never()).exportScoresWorkbook(anyLong());
    }

    @Test
    void testExportScoresToExcel_studentNotFound() throws Exception {
        loginAs(100L);
        when(studentService.exportScoresWorkbook(100L)).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        studentController.exportScoresToExcel("100", response);

        assertEquals(404, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
    }

    @Test
    void testExportScoresToExcel_withoutIdentity() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        studentController.exportScoresToExcel("100", response);

        assertEquals(401, response.getStatus());
        verify(studentService, never()).exportScoresWorkbook(anyLong());
    }

    @Test
    void testGetStudentWarnings() {
        loginAs(100L);
        when(studentService.getStudentWarnings(100L)).thenReturn(Collections.emptyList());

        ApiResponse<Object> result = studentController.getStudentWarnings("100", req());

        assertNotNull(result);
        verify(studentService, times(1)).getStudentWarnings(100L);
    }
}
