package com.academic.exception;

import com.academic.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 教师端 multipart 少发部件 → 400（W19，2026-10-04）。
 *
 * <p>本服务只有两个 multipart 端点（{@code /teachers/scores/import}、
 * {@code /teachers/students/import}），教师没选文件就点导入是个很容易发生、
 * 且**完全属于客户端**的错误。修之前它落到 {@code /error}：
 *
 * <pre>HTTP 500  {"code":500,"message":"服务器内部错误", ...}</pre>
 *
 * <p>成因和 W8 修的那个「缺 body」是同一族：{@code MissingServletRequestPartException}
 * 与 {@code MissingServletRequestParameterException} 是**两个不同的类型**
 * （Spring 给 multipart 部件单独起了一个），所以只处理后者的 advice 对它无效。
 * 这里钉住的是"客户端的错不要报成服务端的错"。
 */
class GlobalExceptionHandlerMultipartTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("★ 没带 file 部件 → HTTP 400 + body code 400（不是 500「服务器内部错误」）")
    void missingMultipartPart_returns400() {
        ResponseEntity<ApiResponse<String>> resp =
                handler.handleMissingPartException(new MissingServletRequestPartException("file"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertTrue(String.valueOf(resp.getBody().getMessage()).contains("file"),
                "要告诉客户端缺的是哪个部件，实际：" + resp.getBody().getMessage());
    }
}
