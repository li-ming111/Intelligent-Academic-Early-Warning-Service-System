package com.academic.common.notify;

import java.util.Map;

/**
 * 通知偏好判定（全项目唯一口径）。
 *
 * <p><b>背景</b>：库里有两张"通知开关"表，都能存、但此前<b>全仓没有一个读取方</b> ——
 * 学生在设置页把开关关掉，系统照发不误。本类把这两张表的语义定死在一处，
 * 所有"写 {@code notifications} 之前该不该发"的判断都必须走这里。
 *
 * <h3>两张表的分工</h3>
 * <ul>
 *   <li>{@code subscription_preferences}（键 {@code student_profile.id}，只有学生有）
 *       —— 订阅维度的细分开关：{@code subscribe_warnings} 是总闸，
 *       {@code subscribe_low/medium/high} 按预警等级细分（1 低 / 2 中 / 3 高，
 *       与 {@code warning_rules.level}、{@code academic_warning.warning_level} 同一套）。</li>
 *   <li>{@code user_settings}（键 {@code users.id}，任何角色都有）
 *       —— 账号维度的粗粒度开关：{@code warning_notification / system_message /
 *       class_notification / student_feedback}。</li>
 * </ul>
 *
 * <h3>优先级（必须记住，改这里之前先读）</h3>
 * <ol>
 *   <li>该类别在 {@code subscription_preferences} 里有值 ⇒ <b>以它为准</b>；</li>
 *   <li>否则看 {@code user_settings}；</li>
 *   <li>两处都没有记录（用户从没设置过）⇒ <b>默认接收</b>。</li>
 * </ol>
 * 逐类别回落，不是整行回落：例如某学生有订阅行但没有"班级通知"这一列，
 * 班级通知仍然会去读 {@code user_settings.class_notification}。
 *
 * <p>⚠️ 判定是"是否<b>允许</b>"，因此 {@code 0} 表示关闭。DB 里这些列是
 * {@code tinyint(1)}，JDBC 取出来可能是 {@code Boolean} 也可能是 {@code Integer}，
 * 所以一律走 {@link #intOf(Object)} 而不是直接强转。
 */
public final class NotificationPreferences {

    /** 通知类别。决定读哪一列。 */
    public enum Category {
        /** 预警类（生成/处理/解除）。读 subscribe_warnings(+等级细分) / warning_notification */
        WARNING,
        /** 帮扶类（计划创建、进度、评价）。读 subscribe_assistance；user_settings 无对应列 */
        ASSISTANCE,
        /** 系统类（申诉审核结果、账号变动、公告）。读 subscribe_system / system_message */
        SYSTEM,
        /** 班级类（班级通知）。subscription_preferences 无对应列，读 class_notification */
        CLASS,
        /** 反馈类（教师回复学生反馈）。subscription_preferences 无对应列，读 student_feedback */
        FEEDBACK,
        /**
         * 回执类（成绩申诉的审核结果）—— <b>不受任何开关影响，永远发送</b>。
         *
         * <p>它是学生<b>自己发起</b>的事件的回执，不是系统广播。学生把「系统消息」
         * 关掉的意思通常是"别给我推公告"，而不是"别告诉我我自己提交的申诉结果"。
         * 用 {@code SYSTEM} 去闸它，会让申诉通过/驳回的结果被静默吞掉 —— 那比不发还糟。
         * 见 {@link #allows}。
         */
        APPEAL
    }

    /**
     * 一次取齐两张表里所有相关开关。
     *
     * <p>写成"一个 SELECT + 一堆标量子查询"而不是两条查询：子查询恒返回一行（没记录时是 NULL），
     * 所以整条语句永远只出一行，调用方不必处理"没查到"的分支。
     *
     * <p>开头那个 {@code 1 AS present} 是刻意的：所有列都为 NULL 时 MyBatis 的自动映射
     * 会把整行丢掉、返回 {@code null}，加一个恒非空列就不会出现这种"没偏好"和
     * "查不到"分不清的情况。
     *
     * <p>参数：{@code #{profileId}} 是 {@code student_profile.id}（非学生传 null），
     * {@code #{userId}} 是 {@code users.id}。
     */
    public static final String SQL =
            "SELECT 1 AS present, "
          + "  (SELECT sp.subscribe_warnings   FROM subscription_preferences sp WHERE sp.student_id = #{profileId} LIMIT 1) AS subWarnings, "
          + "  (SELECT sp.subscribe_low        FROM subscription_preferences sp WHERE sp.student_id = #{profileId} LIMIT 1) AS subLow, "
          + "  (SELECT sp.subscribe_medium     FROM subscription_preferences sp WHERE sp.student_id = #{profileId} LIMIT 1) AS subMedium, "
          + "  (SELECT sp.subscribe_high       FROM subscription_preferences sp WHERE sp.student_id = #{profileId} LIMIT 1) AS subHigh, "
          + "  (SELECT sp.subscribe_assistance FROM subscription_preferences sp WHERE sp.student_id = #{profileId} LIMIT 1) AS subAssistance, "
          + "  (SELECT sp.subscribe_system     FROM subscription_preferences sp WHERE sp.student_id = #{profileId} LIMIT 1) AS subSystem, "
          + "  (SELECT us.warning_notification FROM user_settings us WHERE us.user_id = #{userId} LIMIT 1) AS setWarning, "
          + "  (SELECT us.system_message       FROM user_settings us WHERE us.user_id = #{userId} LIMIT 1) AS setSystem, "
          + "  (SELECT us.class_notification   FROM user_settings us WHERE us.user_id = #{userId} LIMIT 1) AS setClass, "
          + "  (SELECT us.student_feedback     FROM user_settings us WHERE us.user_id = #{userId} LIMIT 1) AS setFeedback";

    /**
     * 该不该给这个人发这一类通知。
     *
     * @param prefs         {@link #SQL} 的查询结果，可为 {@code null}（查不到 ⇒ 默认接收）
     * @param category      通知类别
     * @param warningLevel  预警等级（1 低 / 2 中 / 3 高）；非预警类别传 {@code null}
     * @return {@code true} 表示允许发送
     */
    public static boolean allows(Map<String, Object> prefs, Category category, Integer warningLevel) {
        // 回执类不闸：学生自己发起的事件的答复，关掉"系统消息"也不该被吞（见 Category.APPEAL）
        if (category == Category.APPEAL) {
            return true;
        }
        if (prefs == null) {
            return true;
        }
        Integer subscribed = subscriptionValue(prefs, category, warningLevel);
        if (subscribed != null) {
            return subscribed != 0;
        }
        Integer setting = settingsValue(prefs, category);
        if (setting != null) {
            return setting != 0;
        }
        return true;
    }

    /** 订阅表侧的值；该类别在订阅表里没有对应列时返回 null（交给 user_settings 判）。 */
    private static Integer subscriptionValue(Map<String, Object> prefs, Category category, Integer level) {
        if (category == Category.WARNING) {
            Integer master = intOf(prefs.get("subWarnings"));
            if (master == null) {
                return null;
            }
            // 总闸关了就是关了，不再看等级细分 —— 否则会出现"总闸关但中级还开"的自相矛盾状态
            if (master == 0) {
                return 0;
            }
            Integer byLevel = null;
            if (level != null) {
                byLevel = switch (level) {
                    case 1 -> intOf(prefs.get("subLow"));
                    case 2 -> intOf(prefs.get("subMedium"));
                    case 3 -> intOf(prefs.get("subHigh"));
                    default -> null;   // 未知等级不套用任何细分开关
                };
            }
            // 等级未细分过（列为 NULL）时按总闸放行
            return byLevel == null ? master : byLevel;
        }
        if (category == Category.ASSISTANCE) {
            return intOf(prefs.get("subAssistance"));
        }
        if (category == Category.SYSTEM) {
            return intOf(prefs.get("subSystem"));
        }
        // 订阅表没有"班级通知/反馈回复"这两类
        return null;
    }

    /** user_settings 侧的值；该类别在 user_settings 里没有对应列时返回 null。 */
    private static Integer settingsValue(Map<String, Object> prefs, Category category) {
        return switch (category) {
            case WARNING -> intOf(prefs.get("setWarning"));
            case SYSTEM -> intOf(prefs.get("setSystem"));
            case CLASS -> intOf(prefs.get("setClass"));
            case FEEDBACK -> intOf(prefs.get("setFeedback"));
            case ASSISTANCE -> null;   // user_settings 没有帮扶开关
            case APPEAL -> null;       // 回执类不读任何开关（allows 里已提前放行）
        };
    }

    /**
     * {@code tinyint(1)} 取出来可能是 Boolean、Integer 或 String。
     * 认不出来时返回 {@code null}（= 没配过），而不是 0（= 关闭）——
     * 把"读不懂"当成"关掉"会让通知静默消失。
     */
    static Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private NotificationPreferences() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
