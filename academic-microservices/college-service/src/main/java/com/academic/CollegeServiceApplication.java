package com.academic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;

// 2026-10-01 第十八轮：原先这里有 @EnableFeignClients —— 随 feign 包三个客户端一起删了。
// 本模块现在**不发任何 Feign 调用**，保留这个注解会让 OpenFeign 自动配置继续在启动期
// 做无意义的扫描（且它扫描出来的客户端一个都不剩）。pom 里的 spring-cloud-starter-openfeign
// 同时移除。
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.academic.mapper")
public class CollegeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollegeServiceApplication.class, args);
    }
}
