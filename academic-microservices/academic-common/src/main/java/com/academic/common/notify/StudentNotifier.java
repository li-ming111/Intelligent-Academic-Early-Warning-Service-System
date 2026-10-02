package com.academic.common.notify;

import com.academic.common.entity.Notification;
import com.academic.mapper.NotificationPreferenceMapper;
import com.academic.mapper.NotificationRecipientMapper;
import com.academic.mapper.NotificationWriteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 学生通知发送器（全项目唯一实现）。
 *
 * <p>把"给某个学生发一条自动通知"这件事收敛到一处，三件事一次做全：
 * <ol>
 *   <li><b>id 空间转换</b>：{@code student_profile.id} → {@code users.id}
 *       （{@code notifications.user_id} 有真外键，转错就是 {@code ERROR 1452}）；</li>
 *   <li><b>偏好闸</b>：走 {@link NotificationPreferences}，学生在订阅设置里关掉的开关真的生效；</li>
 *   <li><b>落库</b>：写 {@code notifications}，学生通知中心立刻能看到。</li>
 * </ol>
 *
 * <p><b>为什么不各服务各写一份</b>：这三步里任何一步写错都不会报错、只会静默不生效
 * （转错 id 会炸事务，但"忘了过偏好闸"完全无声）。散落成多份就一定会漂移。
 *
 * <h3>事务语义</h3>
 * 本类<b>不自己开事务、也不吞异常</b>：调用方若在 {@code @Transactional} 里调用，
 * 通知与业务写操作同生共死（这正是"帮扶计划创建 + 通知学生"想要的语义）；
 * 调用方若不在事务里，通知失败也只会让调用方看到异常，不会留下半截数据。
 * 需要"尽力而为、失败不影响主流程"的场景，由调用方显式 try/catch 决定
 * （例如预警定时任务里的 {@code WarningNotifier}）。
 */
@Slf4j
@Component
public class StudentNotifier {

    private final NotificationWriteMapper notificationWriteMapper;
    private final NotificationRecipientMapper recipientMapper;
    private final NotificationPreferenceMapper preferenceMapper;

    public StudentNotifier(NotificationWriteMapper notificationWriteMapper,
                           NotificationRecipientMapper recipientMapper,
                           NotificationPreferenceMapper preferenceMapper) {
        this.notificationWriteMapper = notificationWriteMapper;
        this.recipientMapper = recipientMapper;
        this.preferenceMapper = preferenceMapper;
    }

    /**
     * 按学生档案 id 发通知。
     *
     * @param profileId    {@code student_profile.id}
     * @param category     通知类别，决定读哪个偏好开关
     * @param warningLevel 预警等级（1 低 / 2 中 / 3 高）；非预警类别传 {@code null}
     * @param warningId    关联的预警 id，可为 {@code null}
     * @return {@code true} 表示真的写了一条通知；{@code false} 表示收件人不存在、
     *         档案没绑账号、或收件人已关闭该类通知（都会记日志）
     */
    public boolean notifyStudent(Long profileId, NotificationPreferences.Category category,
                                 Integer warningLevel, String title, String content, Long warningId) {
        if (profileId == null) {
            log.warn("发通知时学生档案 id 为空，跳过：{}", title);
            return false;
        }
        Map<String, Object> recipient = recipientMapper.selectRecipient(profileId);
        if (recipient == null) {
            log.warn("找不到学生档案 {}，通知「{}」未发送", profileId, title);
            return false;
        }
        Long userId = toLong(recipient.get("userId"));
        if (userId == null) {
            log.warn("学生档案 {}（{}）未绑定账号，通知「{}」未发送",
                    profileId, recipient.get("name"), title);
            return false;
        }
        return notifyUser(userId, profileId, category, warningLevel, title, content, warningId);
    }

    /**
     * 按 {@code users.id} 直接发通知（收件人不是学生、或调用方已经有 users.id 时用）。
     *
     * @param profileId 该收件人的 {@code student_profile.id}；非学生传 {@code null}
     *                  （传了才能读到订阅表里的等级细分开关）
     */
    public boolean notifyUser(Long userId, Long profileId, NotificationPreferences.Category category,
                              Integer warningLevel, String title, String content, Long warningId) {
        if (userId == null) {
            log.warn("发通知时收件人 users.id 为空，跳过：{}", title);
            return false;
        }
        Map<String, Object> prefs = preferenceMapper.selectPreferences(profileId, userId);
        if (!NotificationPreferences.allows(prefs, category, warningLevel)) {
            log.info("收件人 {} 已关闭「{}」类通知，跳过：{}", userId, category, title);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(truncate(title, 100));
        notification.setContent(content == null ? "" : content);
        notification.setType(typeOf(category));
        notification.setWarningId(warningId);
        notification.setPushChannel("app");
        notification.setIsRead(0);
        notification.setIsDeleted(0);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        notificationWriteMapper.insert(notification);
        return true;
    }

    /**
     * 通知类别 → {@code notifications.type} 的取值。
     *
     * <p>沿用库里已有的三个值（活库 {@code notifications.type} 只出现过
     * {@code warning} 与 {@code system}），新增的帮扶/反馈类也用能自解释的词，
     * 不新造一套和前端对不上的枚举。
     */
    private static String typeOf(NotificationPreferences.Category category) {
        return switch (category) {
            case WARNING -> "warning";
            case ASSISTANCE -> "assistance";
            case SYSTEM -> "system";
            case CLASS -> "class";
            case FEEDBACK -> "feedback";
            case APPEAL -> "appeal";
        };
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
