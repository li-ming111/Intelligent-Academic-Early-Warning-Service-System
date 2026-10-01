package com.academic.config;

import com.academic.common.config.JwtSecretGuard;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${jwt.secret:${JWT_SECRET:academic-jwt-secret-key-for-academic-warning-system-2026}}")
    private String secret;

    private long expiration = 7200000L;

    private long refreshExpiration = 86400000L;

    /** 启动时提醒一次：这个密钥是不是还在用源码里的开发默认值。 */
    @PostConstruct
    void warnIfUsingDevSecret() {
        JwtSecretGuard.warnIfDefault(secret, log, "user-service JwtConfig");
    }
}