package com.academic.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 登录验证码的**服务端**签发与校验（W1.12，2026-10-02）。
 *
 * <h3>改之前是什么样</h3>
 * 验证码是**纯前端**的：`Login.vue` 用 `Math.random()` 生成一个 4 位串放在
 * `captchaCode` 里渲染成文字，提交前拿输入和它比一比；小程序 `pages/auth/login.vue`
 * 同款。`AuthController.login` 只收 `username` / `password` —— 也就是说
 * <b>直接 POST 一次登录接口就绕过去了</b>，页面上那个验证码对脚本等于不存在。
 * （更早的版本连比都没比，只判了非空，填任何字符都能登录。）
 *
 * <h3>现在</h3>
 * 服务端签发 → 存起来 → 登录时校验并**一次性消费**：
 * <ol>
 *   <li>{@code GET /auth/captcha} 生成 4 位码，返回 {@code {captchaId, captchaText}}；</li>
 *   <li>{@code POST /auth/login} 必须带上 {@code captchaId} + {@code captcha}，对不上就拒；</li>
 *   <li>校验无论成败都把这一条**删掉** ⇒ 同一个码不能用第二次（防重放/防暴力试）。</li>
 * </ol>
 *
 * <h3>为什么用 Caffeine 而不是 Redis（与方案里写的不一样，理由如下）</h3>
 * 方案原本写"Redis 在跑，用 Redis 存 TTL"，实现时改了，两个原因：
 * <ul>
 *   <li><b>user-service 目前没有 Redis 依赖</b>（`pom.xml` 里没有
 *       `spring-boot-starter-data-redis`，`application.yml` 那段 `spring.redis` 是**死的**）。</li>
 *   <li>更关键的是<b>可用性</b>：`/api/auth/**` 是全站唯一的登录入口，把它挂在 Redis 上
 *       意味着"Redis 一挂，所有人登不进来" —— 演示当场起不来。而验证码只是进程内
 *       几 KB 的短命数据，本工程每个服务都是**单实例**，进程内缓存与 Redis 的语义完全等价。
 *       Caffeine 本来就是这个模块的既有依赖（{@code config/CacheConfig.java}）。</li>
 * </ul>
 *
 * <h3>诚实边界（别在答辩时说过头）</h3>
 * 这个码是**字符**验证码，签发接口把字符直接返回给前端（页面上就是把它显示成文字），
 * 所以它挡的是"绕过前端直接打登录接口"，<b>不是</b>机器人 —— 脚本照样能先 GET 再 POST。
 * 要真挡机器人，把 {@link #issue()} 的返回换成 Java2D 生成的 PNG，前端把展示从文字换成
 * 图片即可，其余流程一行不用改（校验逻辑与存储都在这一层）。
 */
@Slf4j
@Service
public class CaptchaService {

    /** 有效期。够填完一个登录表单，又不至于让旧码长期可用。 */
    private static final Duration TTL = Duration.ofMinutes(5);

    /** 最多同时存多少个未使用的码（防内存被刷爆）。 */
    private static final int MAX_PENDING = 10_000;

    /**
     * 字符集**刻意去掉易混的 0/O/1/I**：这个码是要人眼从屏幕读到输入框里的，
     * `0` 和 `O` 分不清会变成"我明明输对了却说错误"的假投诉。
     */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int CODE_LENGTH = 4;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * {@code captchaId -> 明文码}。用 {@code Cache} 而不是裸 {@code ConcurrentHashMap}：
     * TTL 与容量上限由 Caffeine 管，不需要自己写清理线程。
     */
    private final Cache<String, String> pending = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_PENDING)
            .build();

    /**
     * 签发一个新验证码。
     *
     * @return 至少含 {@code captchaId} 与 {@code captchaText} 两个键；
     *         前端拿前者回传、拿后者显示
     */
    public Map<String, Object> issue() {
        String id = UUID.randomUUID().toString();
        String code = randomCode();
        pending.put(id, code);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("captchaId", id);
        out.put("captchaText", code);
        out.put("expiresInSeconds", TTL.toSeconds());
        return out;
    }

    /**
     * 校验并**消费**一个验证码。
     *
     * <p>不管对没对上，这一条都会被删掉 —— 失败也删是刻意的：不删的话一个 id 可以被
     * 反复试，4 位码只有 30 万种组合，挂着不删就等于把爆破成本降到"多试几次"。
     * 删掉之后重试必须重新签发，等于给爆破加了一道换码成本。
     *
     * @param captchaId 签发时给的 id；空/不存在/已过期 → false
     * @param input     用户输入的码；空 → false（**不抛异常**，调用方按"验证失败"处理）
     */
    public boolean verifyAndConsume(String captchaId, String input) {
        if (captchaId == null || captchaId.isBlank() || input == null || input.isBlank()) {
            return false;
        }
        String expected = pending.asMap().remove(captchaId.trim());
        if (expected == null) {
            log.debug("验证码 id 不存在或已过期: {}", captchaId);
            return false;
        }
        boolean ok = expected.equalsIgnoreCase(input.trim());
        if (!ok) {
            log.debug("验证码不匹配: id={}", captchaId);
        }
        return ok;
    }

    /** 当前待消费的验证码条数（给测试与探针用，不参与业务）。 */
    public long pendingCount() {
        return pending.estimatedSize();
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
