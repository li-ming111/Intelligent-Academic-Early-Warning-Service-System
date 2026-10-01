package com.academic.exception;

/**
 * 预警不存在。
 *
 * <p><b>为什么值得单独一个类（2026-09-30）：</b>处理预警 / 查详情 / 改状态这三条路径原先把
 * "预警不存在"写成返回值里的一段 {@code {status:"error", message:"预警不存在"}}，
 * 而 controller 无条件把返回值包进 {@code ApiResponse.success(...)} —— 于是
 * HTTP 200 + 响应体 {@code code=200}。前端 axios 拦截器（{@code client.js} 里
 * "只有 {@code code === 200} 才算成功，否则 reject"）会把这段错误**当成成功**的 data
 * 交给调用方；只看 HTTP 状态的调用方更是完全看不到失败。"操作失败"伪装成"操作成功"，
 * 正是这一轮在扫的缺陷类型，所以改成抛异常，由 controller 翻译成 404。
 *
 * <p>继承 {@code RuntimeException}：这几条路径本来就没有受检异常的处理者，
 * 也不想让接口签名被迫改。
 */
public class WarningNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WarningNotFoundException(String message) {
        super(message);
    }
}
