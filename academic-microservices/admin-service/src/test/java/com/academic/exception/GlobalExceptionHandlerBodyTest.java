package com.academic.exception;

import com.academic.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求体缺失 / 格式不对 → 400（W8，2026-10-03）。
 *
 * <p>此前这份 handler 没有 {@code HttpMessageNotReadableException} 分支，异常落到最下面的
 * catch-all ⇒ 回 <b>HTTP 500 + code 500</b>。实测的响应体长这样：
 *
 * <pre>{"code":500,"message":"Required request body is missing: public com.academic.common.dto.ApiResponse&lt;java.lang.String&gt; com.academic.controller.CounselorController.updateStudentInfo(java.lang.Long,com.academic.common.entity.Student)", ...}</pre>
 *
 * <p>两件事同时错：**把客户端的错报成服务端的错**，以及**把内部类名/方法签名回给了客户端**。
 * 下面第二条断言就是钉住"不许回显原文"这一条 —— 它是这类修复里最容易在改写时丢掉的。
 */
class GlobalExceptionHandlerBodyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** 造一个与运行时同形的异常（message 里带完整方法签名）。 */
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
        ResponseEntity<ApiResponse<Void>> resp = handler.handleMessageNotReadableException(realShapedException());

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
        assertFalse(message.contains("updateStudentInfo"), "别把被调用的方法名回给客户端");
    }

    // ================================================== multipart 少发部件（W19）

    /**
     * {@code MissingServletRequestPartException} 与 {@code MissingServletRequestParameterException}
     * 是**两个不同的类型** —— 只处理后者，漏发文件就会冒到 {@code /error} 变成 HTTP 500。
     */
    @Test
    @DisplayName("★ 导入接口没带 file 部件 → HTTP 400 + body code 400（不是 500「服务器内部错误」）")
    void missingMultipartPart_returns400() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleMissingPartException(new MissingServletRequestPartException("file"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertTrue(String.valueOf(resp.getBody().getMessage()).contains("file"),
                "要告诉客户端缺的是哪个部件，实际：" + resp.getBody().getMessage());
    }
}
