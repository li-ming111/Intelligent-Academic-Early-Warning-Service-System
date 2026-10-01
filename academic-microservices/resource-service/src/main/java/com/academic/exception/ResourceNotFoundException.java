package com.academic.exception;

/**
 * 资源不存在。
 *
 * <p><b>为什么值得单独一个类（2026-09-30）：</b>查详情 / 改资源 / 删资源这三条路径原先把
 * "资源不存在"写成返回值里的一段 {@code {status:"error", message:"资源不存在"}}，
 * 而 controller 无条件把返回值包进 {@code ApiResponse.success(...)} —— 于是
 * HTTP 200 + 响应体 {@code code=200}。前端 axios 拦截器（{@code client.js} 里
 * "只有 {@code code === 200} 才算成功，否则 reject"）会把这段错误**当成成功**的 data
 * 交给调用方；只看 HTTP 状态的调用方更是完全看不到失败。
 *
 * <p>与同族的 {@code WarningNotFoundException}（warning-service）是同一轮修的同一种缺陷。
 *
 * <p>⚠️ 本模块的 {@code GlobalExceptionHandler} **没有 404 分支**，它把
 * {@code RuntimeException} 一律翻成 **500**（{@code serverError}），所以这个异常
 * **不能**指望让 advice 兜——必须由 controller 显式 catch 成 404
 * （controller 里那层 {@code catch (Exception e)} 又会先于 advice 拦下一切）。
 *
 * <p>继承 {@code RuntimeException}：这几条路径本来就没有受检异常的处理者，
 * 也不想让接口签名被迫改。
 */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
