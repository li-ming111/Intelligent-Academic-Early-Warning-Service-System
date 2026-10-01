package com.academic.config;

import com.academic.controller.ClassController;
import com.academic.controller.MajorController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 切片测试专用最小配置。
 *
 * <p>为什么不直接用 {@code CollegeServiceApplication}：主类上有
 * {@code @MapperScan("com.academic.mapper")}
 * （第十八轮之前还有 {@code @EnableFeignClients}，随 feign 包一起删了）
 * —— {@code @WebMvcTest} 只过滤"组件扫描"出来的 bean，**不过滤主类上直接写的
 * {@code @Import}/{@code @MapperScan}**。于是 {@code CollegeMapper} 会被注册、
 * 却因为切片里没有 {@code sqlSessionFactory} 而报
 * "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"，整个上下文起不来。
 *
 * <p>本类放在 {@code com.academic.config} 包下（与测试同类），{@code @SpringBootConfiguration}
 * 的向上查找会先命中它、不再去找 {@code com.academic} 包下的主类。
 */
@SpringBootConfiguration
@Import({MajorController.class, ClassController.class, SecurityConfig.class})
class PublicDirectoryTestApp {
}
