package com.academic.exception;

import com.academic.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 请求体缺失 / 格式不对 → 400（W8，2026-10-03）。
 *
 * <p>与 admin-service 那份同名测试是**同一件事的两个变体**：这里有 7 份 handler 共用同一份
 * 代码（ai/college/counselor/course/message/user/warning，md5 相同），返回类型是
 * {@code ApiResponse<String>}；admin 那份是 {@code ApiResponse<Void>}。两个变体各钉一遍，
 * 免得将来只改了范本、漏掉另一份。
 */
class GlobalExceptionHandlerBodyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static HttpMessageNotReadableException realShapedException() {
        return new HttpMessageNotReadableException(
                "Required request body is missing: public com.academic.common.dto.ApiResponse"
                        + "<java.lang.String> com.academic.controller.CounselorController"
                        + ".updateStudentInfo(java.lang.Long,com.academic.common.entity.Student)",
                (HttpInputMessage) null);
    }

    @Test
    @DisplayName("缺 body → HTTP 400 + body code 400（不是 500）")
    void missingBody_returns400() {
        ResponseEntity<ApiResponse<String>> resp = handler.handleMessageNotReadableException(realShapedException());

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
    }

    @Test
    @DisplayName("⚠️ 响应体里不许出现异常原文（它带着类名与方法签名）")
    void doesNotLeakInternalSignature() {
        String message = handler.handleMessageNotReadableException(realShapedException()).getBody().getMessage();

        assertFalse(message.contains("com.academic"), "回显异常原文会泄漏内部类名: " + message);
        assertFalse(message.contains("Required request body is missing"), "别原样回显 Spring 的英文提示");
    }
}
