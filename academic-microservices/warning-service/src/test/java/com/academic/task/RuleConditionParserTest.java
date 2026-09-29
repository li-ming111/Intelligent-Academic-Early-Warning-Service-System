package com.academic.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则条件解析器测试。
 *
 * <p>覆盖线上 warning_rules 表的全部真实取值，重点是旧 parseThreshold() 的三种错法：
 * 区间数字被拼成一个数、上界差一、GPA 规则被误判。
 */
class RuleConditionParserTest {

    @Test
    @DisplayName("线上规则：fail_count >= 1 AND fail_count <= 2 → [1,2]")
    void testParseRange_LowerTier() {
        // 旧实现会把它抠成 "12" 并当成"成绩低于 12 分"去查成绩表
        RuleConditionParser.Range range =
                RuleConditionParser.parseFailCountRange("fail_count >= 1 AND fail_count <= 2");

        assertNotNull(range);
        assertEquals(1, range.getMin());
        assertEquals(2, range.getMax());
        assertTrue(range.contains(1));
        assertTrue(range.contains(2));
        assertFalse(range.contains(3));
        assertFalse(range.contains(0));
    }

    @Test
    @DisplayName("线上规则：fail_count >= 3 AND fail_count <= 5 → [3,5]")
    void testParseRange_MiddleTier() {
        RuleConditionParser.Range range =
                RuleConditionParser.parseFailCountRange("fail_count >= 3 AND fail_count <= 5");

        assertNotNull(range);
        assertEquals(3, range.getMin());
        assertEquals(5, range.getMax());
        assertFalse(range.contains(2));
        assertTrue(range.contains(3));
        assertTrue(range.contains(5));
        assertFalse(range.contains(6));
    }

    @Test
    @DisplayName("线上规则：fail_count > 5 的下界是 6，不是 5")
    void testParseRange_UpperTier() {
        // 旧实现 replaceAll("[^0-9.]","") 后得到 5.0，边界差一
        RuleConditionParser.Range range =
                RuleConditionParser.parseFailCountRange("fail_count > 5");

        assertNotNull(range);
        assertEquals(6, range.getMin());
        assertEquals(Integer.MAX_VALUE, range.getMax());
        assertFalse(range.contains(5));
        assertTrue(range.contains(6));
        assertTrue(range.contains(999));
    }

    @Test
    @DisplayName("运算符交替顺序：>= 必须整体匹配 2 个字符，不能被 > 先吃掉")
    void testParseRange_OperatorAlternationOrder() {
        // 正则若写成 (>|>=)，遇 ">=" 会先匹配 ">"，剩下的 "=" 无法被 \d+ 匹配，
        // 整个比较失效 —— 区间会退化成无下界。
        RuleConditionParser.Range range =
                RuleConditionParser.parseFailCountRange("fail_count >= 10");

        assertNotNull(range);
        assertEquals(10, range.getMin());
        assertTrue(range.contains(10));
        assertFalse(range.contains(9));
    }

    @Test
    @DisplayName("线上规则：gpa < 2.0 与挂科门数无关 → 返回 null 且不报错")
    void testParseRange_GpaRuleReturnsNull() {
        // 这是 warning_rules 里第 4 条启用规则。它本身合法，只是不属于"挂科门数"指标，
        // 解析器必须优雅放行而不是抛异常。
        assertNull(RuleConditionParser.parseFailCountRange("gpa < 2.0"));
        assertFalse(RuleConditionParser.mentionsFailCount("gpa < 2.0"));
    }

    @Test
    @DisplayName("score < 60 这类单科分数条件也不归本扫描处理")
    void testParseRange_ScoreRuleReturnsNull() {
        assertNull(RuleConditionParser.parseFailCountRange("score < 60"));
        assertFalse(RuleConditionParser.mentionsFailCount("score < 60"));
    }

    @Test
    @DisplayName("区间自相矛盾的规则返回 null，但能被识别为挂科数规则")
    void testParseRange_ContradictoryRange() {
        assertNull(RuleConditionParser.parseFailCountRange("fail_count >= 5 AND fail_count <= 2"));
        // 需要靠它记 WARN 日志：配错了，而不是"不适用于本扫描"
        assertTrue(RuleConditionParser.mentionsFailCount("fail_count >= 5 AND fail_count <= 2"));
    }

    @Test
    @DisplayName("等值条件 → [3,3]")
    void testParseRange_Equality() {
        RuleConditionParser.Range range =
                RuleConditionParser.parseFailCountRange("fail_count = 3");

        assertNotNull(range);
        assertEquals(3, range.getMin());
        assertEquals(3, range.getMax());
        assertTrue(range.contains(3));
        assertFalse(range.contains(2));
        assertFalse(range.contains(4));
    }

    @Test
    @DisplayName("只有上界的条件下界兜底为 1，不会match到 0 门挂科的学生")
    void testParseRange_UpperBoundOnly() {
        RuleConditionParser.Range range =
                RuleConditionParser.parseFailCountRange("fail_count <= 2");

        assertNotNull(range);
        assertEquals(1, range.getMin());
        assertEquals(2, range.getMax());
        assertFalse(range.contains(0));
    }

    @Test
    @DisplayName("空值与没有可解析比较的条件都返回 null")
    void testParseRange_BlankAndUnparseable() {
        assertNull(RuleConditionParser.parseFailCountRange(null));
        assertNull(RuleConditionParser.parseFailCountRange(""));
        assertNull(RuleConditionParser.parseFailCountRange("   "));
        assertNull(RuleConditionParser.parseFailCountRange("fail_count 大于很多门"));
        // 写了指标名但没有有效比较
        assertNull(RuleConditionParser.parseFailCountRange("fail_count"));
    }

    @Test
    @DisplayName("数值超出 int 范围时按无法解析处理，不抛异常")
    void testParseRange_OverflowNumber() {
        assertNull(RuleConditionParser.parseFailCountRange("fail_count >= 99999999999999999999"));
    }

    @Test
    @DisplayName("区间描述文案可用于日志与预警说明")
    void testRangeDescribe() {
        assertEquals("挂科 1-2 门",
                RuleConditionParser.parseFailCountRange("fail_count >= 1 AND fail_count <= 2").describe());
        assertEquals("挂科 6 门及以上",
                RuleConditionParser.parseFailCountRange("fail_count > 5").describe());
        assertEquals("挂科 3 门",
                RuleConditionParser.parseFailCountRange("fail_count = 3").describe());
    }
}
