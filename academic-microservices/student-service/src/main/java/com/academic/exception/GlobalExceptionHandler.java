package com.academic.exception;

import com.academic.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>2026-10-03（W7）：本类原先只有 {@code IllegalArgumentException} / {@code RuntimeException} /
 * {@code Exception} 三条，参数类型不对、缺必填参数、路径不存在**全都**落到 {@code Exception}
 * 兜底 ⇒ 一律 HTTP 500。本轮补齐标准映射，与其余服务（范本见 admin-service）对齐：
 * 路径不存在 → 404、缺必填参数 / 类型不匹配 / 参数校验失败 → 400。
 *
 * <p>⚠️ **`basePackages = "com.academic.controller"` 已去掉**（2026-10-03 实测踩到）：
 * 加了包限定后，本 advice 只对**那个包里的控制器**生效；而
 * {@code NoResourceFoundException} 是**框架**抛的（handler 是
 * {@code ResourceHttpRequestHandler}，不在 {@code com.academic.controller} 里）⇒
 * 根本不进这个 advice，异常一路冒到 {@code /error} 派发，
 * 而 {@code /error} 又被 Spring Security 的 {@code anyRequest().authenticated()} 挡下
 * ⇒ 未知路径回的是 **403 空体**（看起来像越权，其实是路径不存在）。
 * 去掉包限定后才是 404。teacher-service 那份同理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验失败（{@code @Valid} + {@code @RequestBody} 上的约束）→ 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", errorMessage);
        return ResponseEntity.badRequest().body(ApiResponse.badRequest(errorMessage));
    }

    /**
     * 参数类型不匹配（例：路径变量该是数字却传了字符串）→ 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("参数类型错误: %s 应为 %s 类型",
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn("参数类型不匹配: {}", errorMessage);
        return ResponseEntity.badRequest().body(ApiResponse.badRequest(errorMessage));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return ApiResponse.error("参数错误: " + e.getMessage());
    }

    /**
     * 缺少必填请求参数 → 400（不是 500）。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必需参数: " + e.getParameterName()));
    }

    /**
     * 请求方法不支持 → 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(405, "不支持的请求方法"));
    }

    /**
     * 媒体类型不支持 → 415。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(415, "不支持的媒体类型"));
    }

    /**
     * 路径不存在 → 404（不是 500）。理由同 counselor-service 同名注释：
     * Spring 6.1 抛的是 {@code NoResourceFoundException}，不接住就会落到 {@code Exception} 兜底变 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("请求路径不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "请求路径不存在"));
    }

    /**
     * 数据库访问异常 → 500（保留：真实故障，不能吞成 4xx）。
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<String>> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "数据库操作失败"));
    }

    /**
     * 客户端断开连接 → 204，不当作错误。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
        log.debug("客户端已断开: {}", e.getMessage());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleRuntime(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return ApiResponse.error("服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleException(Exception e) {
        log.error("未知异常: {}", e.getMessage(), e);
        return ApiResponse.error("服务器内部错误");
    }
}
