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
 * <p><b>三段探针实测</b>（就是在<b>本模块</b>的测试作用域里跑的，与真服务同一类路径；
 * 靶子是指向 {@code 127.0.0.1:1} 的注定失败调用，判据只有"返回 fallback 值"或"抛异常"）：
 * <pre>
 *   两个开关都不设                                        → feign.RetryableException: Connection refused
 *   feign.circuitbreaker.enabled=true                     → 同上（旧名无效）
 *   spring.cloud.openfeign.circuitbreaker.enabled=true    → 返回 fallback 值 ✅
 * </pre>
 *
 * <p><b>而本服务的调用点更是"本来就无所谓"</b>：{@code CollegeController} 的
 * {@code getStudentCount/getTeacherCount/getCounselorCount}（{@code :219-253}）
 * 自己就 {@code catch → log.warn → return 0L} —— 与 fallback 的 {@code success(0L)}
 * 结果完全一样。也就是说这里的 fallback 类是**双重多余**。
 *
 * <p><b>别为了"让降级生效"去打开那个开关 —— 会更糟。</b>
 * 好几个 fallback 返回 {@code ApiResponse.success(空列表 / 0)}，开关一打开，
 * 调用点的 {@code response.getCode() == 200} 判断会把它当成功收下
 * ⇒ 下游挂了，前端看到的是「200 + 空表格」而不是错误。
 * 这正是本仓一直在清的那一类「静默假成功」。
 *
 * <p>完整论证见《答辩准备-启动与演示手册》§6.17。
 */
package com.academic.feign;
