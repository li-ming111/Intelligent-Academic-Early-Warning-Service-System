package com.academic.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 预警状态文案（W1.6 收尾，2026-10-02）。
 *
 * <p>这个类存在的全部理由是「**三个地方各写一遍，然后翻出了相反的结果**」：
 * {@code status=2}（已解除）在 teacher-service 里被翻成「已处理」、在 college-admin 里
 * 被翻成「待处理」。所以这里的断言看着很琐碎，但每一条都对应一个曾经真的错过的取值。
 */
class WarningStatusTest {

    @Test
    @DisplayName("三态各自有文案")
    void threeStates() {
        assertEquals("待处理", WarningStatus.text(0));
        assertEquals("已处理", WarningStatus.text(1));
        assertEquals("已解除", WarningStatus.text(2));
    }

    @Test
    @DisplayName("status=2 必须是「已解除」——这是本轮修掉的那个错")
    void dispelledIsNotHandledNorPending() {
        String text = WarningStatus.text(WarningStatus.DISPELLED);
        assertEquals("已解除", text);
        // 两个历史错误答案，单独钉一遍，免得以后又"顺手"简化回两态
        assertEquals(false, "已处理".equals(text), "曾把 2 翻成「已处理」");
        assertEquals(false, "待处理".equals(text), "曾把 2 翻成「待处理」");
    }

    @Test
    @DisplayName("null 与认不出的值都按「待处理」（宁可多显示，别让预警凭空消失）")
    void unknownFallsBackToPending() {
        assertEquals("待处理", WarningStatus.text(null));
        assertEquals("待处理", WarningStatus.text(99));
        assertEquals("待处理", WarningStatus.text(-1));
    }
}
