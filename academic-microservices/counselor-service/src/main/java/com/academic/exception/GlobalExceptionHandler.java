package com.academic.exception;

import com.academic.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理所有异常，返回统一的ApiResponse格式
 *
 * <p>2026-10-03（W7）：补齐「路径不存在 / 缺必填参数 / 方法不支持 / 媒体类型不支持 /
 * 数据库异常 / 客户端断开」六类标准映射。此前这几类都落到最下面的
 * {@code Exception} 兜底 ⇒ 一律回 <b>HTTP 500</b>，看起来像服务挂了：
 * <ul>
 *   <li>请求一个不存在的路径（拼错、路由没配）→ 500，其实是 404；</li>
 *   <li>{@code @RequestParam} 必填参数没带 → 500，其实是 400。</li>
 * </ul>
 * 范本取自 admin-service 的同名类（它一直是对的），逐条对齐。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常
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
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("参数类型错误: %s 应为 %s 类型",
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn("参数类型不匹配: {}", errorMessage);
        return ResponseEntity.badRequest().body(ApiResponse.badRequest(errorMessage));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
    }

    /**
     * 缺少必填请求参数 → 400（不是 500）。
     *
     * <p>例：{@code GET /counselors/scores/class/{classId}} 不带 {@code counselor_id}。
     * 此前落到 {@code Exception} 兜底回 500 —— 客户端少传一个参数，却报成服务端错误。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return ResponseEntity.badRequest().body(ApiResponse.badRequest("缺少必需参数: " + e.getParameterName()));
    }

    /**
     * 请求方法不支持 → 405（例：该 GET 的端点用了 POST）。
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
     * 请求体缺失 / JSON 格式不对 → 400。
     *
     * <p>2026-10-03（W8）：原先没有这条分支，{@code @RequestBody} 缺 body 或 JSON 语法错
     * 抛的 {@code HttpMessageNotReadableException} 会落到最下面的 {@code Exception} 兜底
     * ⇒ 回 <b>HTTP 500 + code 500</b>，而且 {@code message} 里**带着完整的方法签名**
     * （实测："Required request body is missing: public com.academic.common.dto.ApiResponse
     * &lt;java.lang.String&gt; com.academic.controller.XXXController.method(...)"）——
     * 既是「把客户端错报成服务端错」，又泄漏了内部结构。
     *
     * <p>⚠️ 故意**不回显** {@code e.getMessage()}：它包含方法签名与类名。
     * 客户端只需要知道"请求体不对"，排查细节留在服务端日志里。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体缺失或格式错误: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.badRequest("请求体缺失或格式错误（需要合法的 JSON 请求体）"));
    }

    /**
     * 路径不存在 → 404（不是 500）。
     *
     * <p>⚠️ 别改回 {@code NoHandlerFoundException}：那个异常只在
     * {@code spring.mvc.throw-exception-if-no-handler-found=true} 时才抛，本工程没有配；
     * 且 Spring 6.1 起「找不到 handler」改成由静态资源兜底处理，抛的是
     * {@code NoResourceFoundException}（{@code ServletException} 的子类，与
     * {@code NoHandlerFoundException} 没有继承关系），于是会落到最下面的
     * {@code Exception} 分支 —— 表现就是**请求一个不存在的路径得到 HTTP 500**，
     * 看起来像后端崩了，实际只是路径拼错。补上这个分支，路径错误才是 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("请求路径不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "请求路径不存在"));
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<String>> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.serverError("数据不存在或未初始化"));
    }

    /**
     * 数据库访问异常 → 500（保留：这是真实故障，不能吞成 4xx）。
     *
     * <p>{@code DataAccessException} 是 {@code RuntimeException} 的子类，这里显式接住，
     * 既避免把 SQL 细节随 {@code e.getMessage()} 泄漏给客户端，也保证仍然是 500。
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

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.serverError(e.getMessage()));
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGenericException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.serverError("系统错误: " + e.getMessage()));
    }
}
