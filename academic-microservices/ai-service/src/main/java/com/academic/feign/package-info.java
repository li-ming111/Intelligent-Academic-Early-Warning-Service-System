/**
 * Feign 客户端 + 它的「降级实现」。
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
 * <p><b>本服务的调用点也已经自己兜住了</b>：{@code AIController#analyzeScores}（{@code :221}）
 * 对内层那次 {@code studentServiceClient.getStudentScores(...)} 单独包了 try/catch ——
 * 拿不到成绩就跳过，继续走 AI 分析。<b>这比 fallback 更好</b>：fallback 会把
 * {@code ApiResponse.error("学生服务暂时不可用")} 当成 scores 塞进请求里，
 * 而 catch 是干脆不塞。
 *
 * <p><b>别为了"让降级生效"去打开那个开关 —— 会更糟。</b>关闭时拿到的是异常（可被 catch），
 * 打开后拿到的是"形状像成功"的降级对象，调用方反而可能当数据收下。
 *
 * <p>完整论证见《答辩准备-启动与演示手册》§6.17。
 */
package com.academic.feign;
