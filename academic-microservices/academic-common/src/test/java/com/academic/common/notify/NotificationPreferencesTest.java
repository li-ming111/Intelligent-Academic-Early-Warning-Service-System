package com.academic.common.notify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知偏好判定测试。
 *
 * <p>这里钉死的是<b>优先级</b>与<b>逐类别回落</b>两条规则 —— 它们是"开关真的生效"
 * 这件事的全部内容，一旦漂移，学生在设置页关掉的开关又会变成摆设。
 *
 * <p>另有一组"读不懂的值"的用例：{@code tinyint(1)} 经 JDBC 出来可能是
 * {@code Boolean}、{@code Integer} 或 {@code String}，认不出来时必须当"没配过"
 * （默认接收），不能当"关闭" —— 后者会让通知静默消失，比多发一条难查得多。
 */
class NotificationPreferencesTest {

    private static final NotificationPreferences.Category WARNING = NotificationPreferences.Category.WARNING;

    // ------------------------------------------------------------------
    // 默认值
    // ------------------------------------------------------------------

    @Test
    @DisplayName("没有任何偏好记录（查不到行）时默认接收")
    void nullPrefsAllows() {
        assertTrue(NotificationPreferences.allows(null, WARNING, 2));
        assertTrue(NotificationPreferences.allows(new HashMap<>(), WARNING, 2));
    }

    @Test
    @DisplayName("两张表都没有这个人的行（全是 NULL）时默认接收")
    void allNullColumnsAllow() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("present", 1);
        assertTrue(NotificationPreferences.allows(prefs, WARNING, 1));
        assertTrue(NotificationPreferences.allows(prefs, NotificationPreferences.Category.ASSISTANCE, null));
        assertTrue(NotificationPreferences.allows(prefs, NotificationPreferences.Category.SYSTEM, null));
    }

    // ------------------------------------------------------------------
    // 预警：订阅表优先
    // ------------------------------------------------------------------

    @Test
    @DisplayName("订阅总闸关掉时一律不发 —— 哪怕等级细分还开着")
    void subscriptionMasterSwitchWins() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", 0);
        prefs.put("subMedium", 1);      // 细分开着也没用
        prefs.put("setWarning", 1);     // user_settings 开着也没用

        assertFalse(NotificationPreferences.allows(prefs, WARNING, 2));
    }

    @Test
    @DisplayName("总闸开着、该等级细分开着 → 发")
    void subscriptionPerLevelAllows() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", 1);
        prefs.put("subMedium", 1);

        assertTrue(NotificationPreferences.allows(prefs, WARNING, 2));
    }

    @Test
    @DisplayName("总闸开着但该等级细分关掉 → 不发（其余等级不受影响）")
    void subscriptionPerLevelBlocksOnlyThatLevel() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", 1);
        prefs.put("subLow", 0);
        prefs.put("subMedium", 1);
        prefs.put("subHigh", 1);

        assertFalse(NotificationPreferences.allows(prefs, WARNING, 1));
        assertTrue(NotificationPreferences.allows(prefs, WARNING, 2));
        assertTrue(NotificationPreferences.allows(prefs, WARNING, 3));
    }

    @Test
    @DisplayName("等级为 null 或不在 1/2/3 内时按总闸放行，不套用任何细分开关")
    void unknownLevelFallsBackToMasterSwitch() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", 1);
        prefs.put("subLow", 0);
        prefs.put("subMedium", 0);
        prefs.put("subHigh", 0);

        assertTrue(NotificationPreferences.allows(prefs, WARNING, null));
        assertTrue(NotificationPreferences.allows(prefs, WARNING, 9));
    }

    @Test
    @DisplayName("订阅行没有等级细分列（NULL）时按总闸放行")
    void missingLevelColumnsFallBackToMasterSwitch() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", 1);
        // subLow/subMedium/subHigh 全部缺席

        assertTrue(NotificationPreferences.allows(prefs, WARNING, 3));
    }

    // ------------------------------------------------------------------
    // 预警：逐类别回落到 user_settings
    // ------------------------------------------------------------------

    @Test
    @DisplayName("订阅行存在但没有 subscribe_warnings 列时，回落到 user_settings.warning_notification")
    void warningFallsBackToUserSettings() {
        Map<String, Object> prefs = prefs();
        prefs.put("subAssistance", 1);   // 有订阅行，但没配预警开关
        prefs.put("setWarning", 0);

        assertFalse(NotificationPreferences.allows(prefs, WARNING, 2));
    }

    @Test
    @DisplayName("完全没有订阅行时按 user_settings.warning_notification 判定")
    void warningUsesUserSettingsWhenNoSubscriptionRow() {
        Map<String, Object> prefs = prefs();
        prefs.put("setWarning", 0);
        assertFalse(NotificationPreferences.allows(prefs, WARNING, 1));

        prefs.put("setWarning", 1);
        assertTrue(NotificationPreferences.allows(prefs, WARNING, 1));
    }

    // ------------------------------------------------------------------
    // 其余类别
    // ------------------------------------------------------------------

    @Test
    @DisplayName("帮扶通知只认 subscribe_assistance（user_settings 没有对应列）")
    void assistanceUsesSubscriptionOnly() {
        Map<String, Object> prefs = prefs();
        prefs.put("subAssistance", 0);
        assertFalse(NotificationPreferences.allows(prefs, NotificationPreferences.Category.ASSISTANCE, null));

        prefs.put("subAssistance", 1);
        assertTrue(NotificationPreferences.allows(prefs, NotificationPreferences.Category.ASSISTANCE, null));
    }

    @Test
    @DisplayName("系统通知先看 subscribe_system，没有则回落 system_message")
    void systemPrefersSubscriptionThenSettings() {
        Map<String, Object> prefs = prefs();
        prefs.put("subSystem", 0);
        prefs.put("setSystem", 1);
        assertFalse(NotificationPreferences.allows(prefs, NotificationPreferences.Category.SYSTEM, null));

        prefs.remove("subSystem");
        prefs.put("setSystem", 0);
        assertFalse(NotificationPreferences.allows(prefs, NotificationPreferences.Category.SYSTEM, null));

        prefs.put("setSystem", 1);
        assertTrue(NotificationPreferences.allows(prefs, NotificationPreferences.Category.SYSTEM, null));
    }

    @Test
    @DisplayName("班级通知与反馈回复只读 user_settings 的对应列")
    void classAndFeedbackUseUserSettingsOnly() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", 0);     // 订阅表里其它开关不该影响这两类
        prefs.put("setClass", 0);
        prefs.put("setFeedback", 1);

        assertFalse(NotificationPreferences.allows(prefs, NotificationPreferences.Category.CLASS, null));
        assertTrue(NotificationPreferences.allows(prefs, NotificationPreferences.Category.FEEDBACK, null));
    }

    // ------------------------------------------------------------------
    // 取值容错
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tinyint(1) 取成 Boolean 也要认得")
    void booleanValuesAreUnderstood() {
        Map<String, Object> prefs = prefs();
        prefs.put("subWarnings", Boolean.FALSE);
        assertFalse(NotificationPreferences.allows(prefs, WARNING, 2));

        prefs.put("subWarnings", Boolean.TRUE);
        prefs.put("subMedium", Boolean.TRUE);
        assertTrue(NotificationPreferences.allows(prefs, WARNING, 2));
    }

    @Test
    @DisplayName("取成字符串 \"0\"/\"1\" 也要认得")
    void stringValuesAreUnderstood() {
        Map<String, Object> prefs = prefs();
        prefs.put("setWarning", "0");
        assertFalse(NotificationPreferences.allows(prefs, WARNING, 1));
    }

    @Test
    @DisplayName("读不懂的值当作\"没配过\"放行，不能当作\"关闭\" —— 否则通知会静默消失")
    void unparsableValueIsTreatedAsUnset() {
        Map<String, Object> prefs = prefs();
        prefs.put("setWarning", "yes");

        assertTrue(NotificationPreferences.allows(prefs, WARNING, 1));
    }

    // ------------------------------------------------------------------
    // 回执类：不受任何开关影响
    // ------------------------------------------------------------------

    @Test
    @DisplayName("申诉审核结果不受任何开关影响：系统消息全关也照发")
    void appealReceiptIsNeverGated() {
        Map<String, Object> prefs = prefs();
        prefs.put("subSystem", 0);
        prefs.put("setSystem", 0);

        assertTrue(NotificationPreferences.allows(prefs, NotificationPreferences.Category.APPEAL, null),
                "申诉结果是学生自己发起事件的回执，被'系统消息'开关吞掉等于申诉白提了");
    }

    @Test
    @DisplayName("申诉回执在偏好行整个查不到时也放行")
    void appealReceiptAllowedWithoutPrefsRow() {
        assertTrue(NotificationPreferences.allows(null, NotificationPreferences.Category.APPEAL, null));
    }

    private static Map<String, Object> prefs() {
        return new HashMap<>();
    }
}
