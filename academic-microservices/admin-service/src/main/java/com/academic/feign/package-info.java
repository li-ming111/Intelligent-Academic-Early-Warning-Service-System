/**
 * Feign 客户端 + 它们的「降级实现」。
 *
 * <p><b>⚠️ 2026-10-01 第十七轮查证：本包下所有 {@code @FeignClient(fallback = X.class)} 的
 * fallback <u>都不会执行</u>，它们是死代码。</b>
 *
 * <p><b>原因</b>：Spring Cloud OpenFeign 4.1.x（Spring Cloud 2023.0.3）要求显式打开
 * {@code spring.cloud.openfeign.circuitbreaker.enabled=true}，才会走熔断器那条路、
 * 才会去解析 {@code fallback}。本仓<b>任何服务都没有设这个属性</b>；3.x 时代的旧名
 * {@code feign.circuitbreaker.enabled} 在 4.x 上<b>已经失效</b>（设了也没用）。
 *
 * <p><b>三段探针实测</b>（跑在 college-service 测试作用域 = 与真服务同一类路径；
 * 靶子是指向 {@code 127.0.0.1:1} 的注定失败调用，判据只有"返回 fallback 值"或"抛异常"）：
 * <pre>
 *   两个开关都不设                                        → feign.RetryableException: Connection refused
 *   feign.circuitbreaker.enabled=true                     → 同上（旧名无效）
 *   spring.cloud.openfeign.circuitbreaker.enabled=true    → 返回 fallback 值 ✅
 * </pre>
 *
 * <p><b>但今天的行为差异 = 0</b>，因为<b>每一处调用点自己都包了 try/catch</b>，
 * 返回的降级形状与 fallback 基本一致（本服务的 {@code AdminController#getAllStudents}、
 * {@code #getStudentById}、{@code #sendBroadcast}、{@code #sendMessage} 等全是
 * {@code catch → ApiResponse.error(...)}）。<b>真正的"服务降级"是调用点的 try/catch 在做，
 * 不是这里的 fallback。</b>
 *
 * <p><b>所以别为了"让降级生效"去打开那个开关 —— 会更糟。</b>
 * 本包好几个 fallback 返回 {@code ApiResponse.success(空列表)}，一旦开关打开，
 * 调用点的 {@code response.getCode() == 200} 判断会把它当成功收下
 * ⇒ 下游服务挂了，前端看到的是「200 + 空表格」而不是错误。
 * 这正是本仓一直在清的那一类「静默假成功」。
 *
 * <p>本仓真正生效的降级机制是另一套：{@code @SentinelResource(fallback = "方法名")}
 * —— 见 {@code AdminController} 里那些 {@code *Fallback(Throwable)} 方法（它们会被调用）。
 * 注意 {@code @SentinelResource} 的 fallback 是"方法名"，不是这里的
 * {@code @FeignClient} 的"类"。
 *
 * <p>完整论证见《答辩准备-启动与演示手册》§6.17。
 */
package com.academic.feign;
