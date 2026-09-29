package com.academic.common.util;

import com.academic.common.entity.Score;

import java.math.BigDecimal;

/**
 * 成绩字段换算（纯函数，无 Spring 依赖）。
 *
 * <p>凡是往 {@code scores} 表写成绩的地方都需要这三步换算：
 * 总评 → 等级、总评 → 绩点、总评 → 五级档。此前这套逻辑散落在
 * teacher-service 与 admin-service 里各写一份，两处口径已经开始漂移。
 * 集中到这里，保证教师录入与管理端导入算出来的等级/绩点完全一致。
 */
public final class ScoreSyncSupport {

    private ScoreSyncSupport() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** 总评成绩 → 等级（A/A-/B/C/D）。 */
    public static String scoreToGrade(Double score) {
        if (score == null) {
            return null;
        }
        if (score >= 90) {
            return "A";
        }
        if (score >= 80) {
            return "A-";
        }
        if (score >= 70) {
            return "B";
        }
        if (score >= 60) {
            return "C";
        }
        return "D";
    }

    /** 总评成绩 → 绩点。 */
    public static BigDecimal scoreToGpa(Double score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score >= 90) {
            return BigDecimal.valueOf(4.0);
        }
        if (score >= 85) {
            return BigDecimal.valueOf(3.7);
        }
        if (score >= 82) {
            return BigDecimal.valueOf(3.3);
        }
        if (score >= 78) {
            return BigDecimal.valueOf(3.0);
        }
        if (score >= 75) {
            return BigDecimal.valueOf(2.7);
        }
        if (score >= 72) {
            return BigDecimal.valueOf(2.3);
        }
        if (score >= 68) {
            return BigDecimal.valueOf(2.0);
        }
        if (score >= 64) {
            return BigDecimal.valueOf(1.5);
        }
        if (score >= 60) {
            return BigDecimal.valueOf(1.0);
        }
        return BigDecimal.ZERO;
    }

    /** 总评成绩 → 五级档（1 优 … 5 不及格）。 */
    public static Integer scoreToLevel(Double score) {
        if (score == null) {
            return null;
        }
        if (score >= 90) {
            return 1;
        }
        if (score >= 80) {
            return 2;
        }
        if (score >= 70) {
            return 3;
        }
        if (score >= 60) {
            return 4;
        }
        return 5;
    }

    /**
     * 把一条录入成绩折算并覆盖到权威成绩对象上（{@code scores} 行）。
     *
     * <p>只覆盖调用方明确提供的字段，{@code null} 一律不写 —— 早期实现是无条件赋值，
     * 于是"只改了分数"的那次保存会把 {@code regular_score}/{@code final_score} 一并置空；
     * 而 {@code final_score} 一空，这一行就会被"期末未录入"的口径整体排除，
     * 学生的挂科会从预警里静默消失。
     *
     * <p>{@code final_score} 缺失时用总评补齐：录入界面只提供"总评成绩"一个分数，
     * 它就是这门课的最终成绩，必须落到 {@code final_score} 才能参与挂科统计。
     *
     * <p>★ 等级/绩点每次**无条件重算**。曾经的写法是
     * {@code if (score.getGrade() == null) score.setGrade(...)}，
     * 结果更新一条已有成绩时因为旧值非空而跳过重算，
     * 总评改了、等级还停在旧值上 —— 正是"数字与文字对不上"的来源。
     */
    public static void applyToAuthoritative(Score target, Double rawScore,
                                            Double regularScore, Double finalScore) {
        if (target == null) {
            return;
        }
        if (rawScore != null) {
            target.setScoreTotal(BigDecimal.valueOf(rawScore));
            target.setGrade(scoreToGrade(rawScore));
            target.setGradePoint(scoreToGpa(rawScore));
        }
        if (regularScore != null) {
            target.setRegularScore(BigDecimal.valueOf(regularScore));
        }
        if (finalScore != null) {
            target.setFinalScore(BigDecimal.valueOf(finalScore));
        } else if (target.getFinalScore() == null && target.getScoreTotal() != null) {
            target.setFinalScore(target.getScoreTotal());
        }
    }
}
