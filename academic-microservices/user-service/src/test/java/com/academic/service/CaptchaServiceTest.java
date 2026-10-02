package com.academic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CaptchaService} 的单元测试（W1.12，2026-10-02）。
 *
 * <h3>为什么这一层值得单独测</h3>
 * 改之前验证码是**纯前端内存比对**（`Login.vue` 的 `Math.random()`），直接
 * {@code POST /auth/login} 就绕过去了。现在挡在登录口上的就是这一个类，
 * 它一旦"看着像在验、其实恒真"，界面上完全看不出来（用户照样能登录）。
 * 所以下面每条都断言**具体的布尔结果**，不只看"没抛异常"。
 *
 * <h3>没有覆盖的一条（如实说明）</h3>
 * 真实的 5 分钟 TTL 过期**没有**在这里测 —— 那要么 sleep 五分钟，要么把
 * `Clock` 抽出来注入（为一个 4 行的方法加一层时钟抽象不划算）。这里覆盖的是
 * "id 取不到 → false"这条路径，而**过期后的行为与它逐字相同**（Caffeine 到点
 * 把条目删掉，`asMap().remove()` 于是返回 null）。容量上限同理未测。
 */
class CaptchaServiceTest {

    /**
     * 每个用例用一个**新实例**：验证码是进程内状态，共用实例会让
     * `pendingCount()` 之类的断言互相串味。
     */
    private final CaptchaService service = new CaptchaService();

    private static String text(Map<String, Object> issued) {
        return String.valueOf(issued.get("captchaText"));
    }

    private static String id(Map<String, Object> issued) {
        return String.valueOf(issued.get("captchaId"));
    }

    // ==================== 签发 ====================

    @Test
    @DisplayName("签发返回 id / 文本 / 有效期三个键，前端两个都要用")
    void issue_returnsAllThreeKeys() {
        Map<String, Object> issued = service.issue();

        assertNotNull(issued.get("captchaId"));
        assertFalse(id(issued).isBlank());
        assertNotNull(issued.get("captchaText"));
        assertNotNull(issued.get("expiresInSeconds"));
        assertEquals(300L, ((Number) issued.get("expiresInSeconds")).longValue());
    }

    @Test
    @DisplayName("两次签发拿到不同的 id（否则后一个会覆盖前一个）")
    void issue_idsAreUnique() {
        String first = id(service.issue());
        String second = id(service.issue());
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("码是 4 位，且不含易混的 0/O/1/I —— 人眼要照着屏幕输")
    void issue_codeIsFourCharsWithoutAmbiguousOnes() {
        // 多签几次，把随机性覆盖到（单次命中某个字符是概率问题，不是断言目标）
        for (int i = 0; i < 200; i++) {
            String code = text(service.issue());
            assertEquals(4, code.length(), "码长应为 4，实际: " + code);
            for (char c : code.toCharArray()) {
                assertTrue("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".indexOf(c) >= 0,
                        "出现了不该在字符集里的字符: " + c + "（整个码: " + code + "）");
            }
        }
    }

    // ==================== 校验 ====================

    @Test
    @DisplayName("码填对 → 通过")
    void verify_correctCodePasses() {
        Map<String, Object> issued = service.issue();
        assertTrue(service.verifyAndConsume(id(issued), text(issued)));
    }

    @Test
    @DisplayName("大小写不敏感、首尾空格无所谓 —— 用户手输的常态")
    void verify_ignoresCaseAndSurroundingWhitespace() {
        Map<String, Object> issued = service.issue();
        assertTrue(service.verifyAndConsume("  " + id(issued) + "  ", " " + text(issued).toLowerCase() + " "));
    }

    @Test
    @DisplayName("码填错 → 不通过")
    void verify_wrongCodeFails() {
        Map<String, Object> issued = service.issue();
        assertFalse(service.verifyAndConsume(id(issued), wrongCodeFor(text(issued))));
    }

    // ==================== 一次性消费（防重放/防爆破） ====================

    @Test
    @DisplayName("同一个码用第二次 → 不通过（一次性消费）")
    void verify_codeCannotBeReused() {
        Map<String, Object> issued = service.issue();
        String captchaId = id(issued);
        String code = text(issued);

        assertTrue(service.verifyAndConsume(captchaId, code), "第一次应当通过");
        assertFalse(service.verifyAndConsume(captchaId, code), "第二次必须失败 —— 否则同一个码可无限重放");
    }

    @Test
    @DisplayName("**填错也会把码消费掉** —— 否则一个 id 可以挂着反复试（4 位只有 30 万种）")
    void verify_wrongCodeStillConsumesTheEntry() {
        Map<String, Object> issued = service.issue();
        String captchaId = id(issued);
        String code = text(issued);

        assertFalse(service.verifyAndConsume(captchaId, wrongCodeFor(code)), "先填错一次");
        assertFalse(service.verifyAndConsume(captchaId, code),
                "填错之后那个 id 就该作废了；若这里返回 true，说明失败路径没删条目，等于允许爆破");
    }

    @Test
    @DisplayName("没签发过的 id → 不通过（与「已过期」走同一条路径）")
    void verify_unknownIdFails() {
        assertFalse(service.verifyAndConsume("11111111-2222-3333-4444-555555555555", "A2C4"));
    }

    // ==================== 空值（不能抛异常，调用方按"验证失败"处理） ====================

    @Test
    @DisplayName("id 或码为空/null → 返回 false，不抛异常")
    void verify_blankInputsReturnFalse() {
        Map<String, Object> issued = service.issue();
        String captchaId = id(issued);
        String code = text(issued);

        assertFalse(service.verifyAndConsume(null, code));
        assertFalse(service.verifyAndConsume(captchaId, null));
        assertFalse(service.verifyAndConsume("", code));
        assertFalse(service.verifyAndConsume(captchaId, ""));
        assertFalse(service.verifyAndConsume("   ", code));
        assertFalse(service.verifyAndConsume(captchaId, "   "));
    }

    @Test
    @DisplayName("空输入**不该**把已签发的码误删掉")
    void verify_blankInputsDoNotConsumeAValidEntry() {
        Map<String, Object> issued = service.issue();
        String captchaId = id(issued);
        String code = text(issued);

        assertFalse(service.verifyAndConsume(captchaId, "   "), "空输入应当直接拒，连查都不查");
        assertTrue(service.verifyAndConsume(captchaId, code), "真正的码必须仍然有效");
    }

    // ==================== 计数（探针与测试用） ====================

    @Test
    @DisplayName("pendingCount 随签发增长、随消费回落")
    void pendingCountTracksIssuedAndConsumed() {
        CaptchaService fresh = new CaptchaService();
        assertEquals(0, fresh.pendingCount());

        Map<String, Object> a = fresh.issue();
        Map<String, Object> b = fresh.issue();
        assertEquals(2, fresh.pendingCount());

        assertTrue(fresh.verifyAndConsume(id(a), text(a)));
        assertEquals(1, fresh.pendingCount());

        // 填错也消费 ⇒ 计数同样回落（这条同时钉住"失败也删"）
        assertFalse(fresh.verifyAndConsume(id(b), wrongCodeFor(text(b))));
        assertEquals(0, fresh.pendingCount());
    }

    /**
     * 造一个**一定与给定码不同**的 4 位串。
     *
     * <p>刻意不写 {@code code + "X"} 之类的拼接 —— 那样会变成 5 位，
     * 测出来的是"长度不符"而不是"内容不符"。这里做的是逐位替换。
     */
    private static String wrongCodeFor(String code) {
        char[] chars = code.toCharArray();
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        return new String(chars);
    }
}
