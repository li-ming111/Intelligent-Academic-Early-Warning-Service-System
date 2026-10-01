package com.academic.common.config;

import org.slf4j.Logger;

/**
 * JWT 密钥"默认态"的告警工具。
 *
 * <h3>它解决什么</h3>
 * 签发/校验密钥在四处是**同一串源码字面量**：{@code auth-service/JwtUtil}（签发）、
 * {@code user-service/JwtConfig}、{@code gateway-service/AuthenticationFilter}（校验）与
 * {@code gateway-service/application.yml}。四处都包了 {@code ${JWT_SECRET:字面量}}，
 * 但**默认态没有任何东西提醒你它还在用字面量** —— 仓库若公开，拿到它就能伪造任意角色
 * （含管理员）的 token，而启动日志里一个字都不会有。
 *
 * <h3>为什么是"工具 + 三处调用"，不是一个 {@code @Component}</h3>
 * 不能写成自动生效的组件：{@code jwt.secret} 在 auth-service / user-service 里
 * **并不是一个真正的配置属性**（字面量只写在 Java 的 {@code @Value} 默认值里），
 * {@code environment.getProperty("jwt.secret")} 在那两个服务上返回 {@code null} ——
 * 一个"通用"组件会正好**跳过唯一签发 token 的服务**，看着生效、实则空转。
 * 所以把判断放在**真正解析出密钥的那个类**里：它手里就有最终值，不需要猜。
 *
 * <h3>为什么只告警、不拒绝启动</h3>
 * 本工程没有启动脚本，各服务是在 IDE 里直接 run 的（手册 §3），也没人注入过 {@code JWT_SECRET}。
 * 在这里 fail-fast 会让**演示当场起不来**；每次启动随机生成密钥更糟 —— auth-service 签的 token
 * 到 gateway 验不过，整条登录链全断。两种做法都把"修安全问题"变成了"修不好演示"。
 * ⇒ 采用唯一无损的做法：密钥保持可用，但把"你正在用开发默认值"变成启动时一条显眼 WARN，
 *    无法再被静默忽略。真要换掉，设环境变量 {@code JWT_SECRET} 即可（四处读同一属性，一起生效）。
 */
public final class JwtSecretGuard {

    /** 与四处源码字面量逐字相同。改这里没用 —— 要改就去设 {@code JWT_SECRET}。 */
    public static final String DEV_DEFAULT =
            "academic-jwt-secret-key-for-academic-warning-system-2026";

    private JwtSecretGuard() {
    }

    /**
     * 在密钥已解析出来之后调用（通常放在 {@code @PostConstruct} 里）。
     *
     * @param secret 该服务最终拿到的密钥；未配置时可为 null/空
     * @param log    调用方的 logger，让告警出现在**该服务自己的**日志流里
     * @param where  调用点标识（如 "gateway AuthenticationFilter"），便于定位
     */
    public static void warnIfDefault(String secret, Logger log, String where) {
        if (secret == null || secret.isBlank()) {
            return; // 这个服务不太可能走到这（没配密钥就该在别处报错），不在这里多嘴。
        }
        if (!DEV_DEFAULT.equals(secret)) {
            log.info("[{}] JWT 密钥已由外部配置提供（非开发默认值）。", where);
            return;
        }
        log.warn("");
        log.warn("========================================================================");
        log.warn("  ⚠️  [{}] JWT 密钥正在使用**开发默认值**（源码里的字面量）。", where);
        log.warn("      后果：任何拿到本仓库的人都能伪造任意角色（含管理员）的 token。");
        log.warn("      本地演示可忽略；但**不要**把这个默认值带到任何对外可访问的环境。");
        log.warn("      换掉它：设置环境变量 JWT_SECRET（auth/user/gateway 三处会一起生效）。");
        log.warn("========================================================================");
        log.warn("");
    }
}
