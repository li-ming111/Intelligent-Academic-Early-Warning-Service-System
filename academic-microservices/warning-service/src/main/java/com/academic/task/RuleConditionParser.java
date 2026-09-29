package com.academic.task;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预警规则条件解析器：把 {@code warning_rules.rule_condition} 里的自然表达式
 * 解析成可判定的挂科门数区间。
 *
 * <p>它替换掉了原先的 {@code parseThreshold()}。后者把
 * {@code "fail_count >= 1 AND fail_count <= 2"} 里的数字全部抠出来拼成 {@code 12.0}，
 * 当成"成绩低于 12 分"去查成绩表 —— 于是多门挂科规则被静默执行成了
 * "分数低于 12 分"，一条预警都匹配不到。本解析器按比较运算符逐条解析，
 * 并保持区间的开闭语义。
 *
 * <p>只处理 {@code fail_count} 指标。规则表里其它指标（如 {@code gpa < 2.0}）
 * 一律返回 {@code null} 表示"本扫描不适用"，由调用方记日志后跳过 ——
 * 不抛异常，也不静默吞掉。
 */
public final class RuleConditionParser {

    /** 唯一被本扫描支持的指标名。 */
    private static final String FAIL_COUNT = "fail_count";

    /**
     * 匹配 {@code fail_count >= 3} 这样的单条比较。
     *
     * <p>运算符的交替顺序必须是 {@code >=|<=|>|<|=}，把双字符运算符放在前面。
     * 若写成 {@code >|>=}，正则引擎按从左到右取首个匹配，遇到 {@code >=} 会先吃掉
     * {@code >}，剩下 {@code =} 无法被 {@code \d+} 匹配，整个分支失败。
     */
    private static final Pattern COMPARISON =
            Pattern.compile(FAIL_COUNT + "\\s*(>=|<=|>|<|=)\\s*(-?\\d+)");

    /** 挂科门数区间，闭区间。{@code max} 为 {@link Integer#MAX_VALUE} 表示无上界。 */
    public static final class Range {
        private final int min;
        private final int max;

        Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        /** 挂科门数是否落在本区间内。 */
        public boolean contains(int failCount) {
            return failCount >= min && failCount <= max;
        }

        /** 区间的可读描述，用于日志与预警文案。 */
        public String describe() {
            if (max == Integer.MAX_VALUE) {
                return "挂科 " + min + " 门及以上";
            }
            if (min == max) {
                return "挂科 " + min + " 门";
            }
            return "挂科 " + min + "-" + max + " 门";
        }

        @Override
        public String toString() {
            return "[" + min + ", " + (max == Integer.MAX_VALUE ? "+∞" : max) + "]";
        }
    }

    /**
     * 解析条件中的挂科门数区间。
     *
     * @return 解析成功返回区间；条件与 {@code fail_count} 无关（如 GPA 规则）、
     *         无有效比较、或区间自相矛盾（如 {@code >= 5 AND <= 2}）时返回 {@code null}
     */
    public static Range parseFailCountRange(String condition) {
        if (condition == null || condition.isBlank()) {
            return null;
        }

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        boolean matched = false;

        Matcher matcher = COMPARISON.matcher(condition);
        while (matcher.find()) {
            matched = true;
            String operator = matcher.group(1);
            int bound;
            try {
                bound = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                // 数字超出 int 范围，视为无法解析
                return null;
            }
            switch (operator) {
                case ">=" -> min = Math.max(min, bound);
                case ">"  -> min = Math.max(min, bound + 1);
                case "<=" -> max = Math.min(max, bound);
                case "<"  -> max = Math.min(max, bound - 1);
                case "="  -> {
                    min = Math.max(min, bound);
                    max = Math.min(max, bound);
                }
                default -> {
                    return null;
                }
            }
        }

        if (!matched) {
            return null;
        }
        // 条件自相矛盾（如 fail_count >= 5 AND fail_count <= 2），视为不可用
        if (min > max) {
            return null;
        }
        // 无下界的规则会匹配"挂科 0 门"的学生，语义上不成立
        if (min == Integer.MIN_VALUE) {
            min = 1;
        }
        if (max < min) {
            return null;
        }
        return new Range(min, max);
    }

    /**
     * 条件是否与挂科门数有关。
     * 用来区分"规则本身不适用本扫描"（GPA 规则）与"规则写错了"（挂科数条件无法解析），
     * 两者应记不同级别的日志。
     */
    public static boolean mentionsFailCount(String condition) {
        return condition != null && condition.contains(FAIL_COUNT);
    }

    private RuleConditionParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
