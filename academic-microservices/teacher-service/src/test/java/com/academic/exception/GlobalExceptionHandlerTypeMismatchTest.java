package com.academic.exception;

import com.academic.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路径/查询参数类型不匹配 → 400（不是 500）。
 *
 * <p>教师端读端点的 {@code {teacherId}} / {@code {userId}} 已从 {@code String} 改为
 * {@code Long}（原先在 try 外裸调 {@code Long.parseLong}，非数字 id 抛
 * {@code NumberFormatException} 一路落到 {@code RuntimeException} 兜底 ⇒ 500）。
 * 改为 {@code Long} 后由 Spring 抛本异常、走这条映射 —— 客户端传错参数得到的是 400。
 */
class GlobalExceptionHandlerTypeMismatchTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("路径 id 传了非数字 ⇒ 400，而不是 500")
    void typeMismatch_returns400() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "teacherId", null, new NumberFormatException("For input string: \"abc\""));

        ResponseEntity<ApiResponse<String>> resp = handler.handleTypeMismatchException(e);

        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("teacherId"));
    }
}
