package com.academic.notify;

import com.academic.common.entity.AcademicWarning;
import com.academic.common.mq.RabbitMQConstants;
import com.academic.common.notify.NotificationPreferences;
import com.academic.common.notify.StudentNotifier;
import com.academic.mapper.AcademicWarningMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预警通知发送器。
 *
 * <p><b>为什么有这个类</b>：文档写的是"预警生成 → 通知学生与辅导员"，
 * 而代码里预警服务从不写 {@code notifications} 表 —— 学生得自己打开系统才知道挂了科。
 * 这里把"生成预警"与"通知"接上。
 *
 * <h3>两条通道，业务只依赖第一条</h3>
 * <ol>
 *   <li><b>直写 {@code notifications} 表</b>（走 {@link StudentNotifier}）—— 保证无论中间件
 *       在不在，通知都真的产生，学生/辅导员的「通知中心」一定能看到。</li>
 *   <li><b>尽力而为地发一条 RabbitMQ 消息</b> —— 由 message-service 消费后做 WebSocket
 *       实时推送（见 {@code RabbitMQConstants.EXCHANGE_NOTIFICATION}）。
 *       broker 没起时只 {@code log.warn}，<b>绝不冒泡到业务</b>。</li>
 * </ol>
 * 换句话说：异步通道是"加速器"，不是"必经之路"。这样答辩现场即使 RabbitMQ 没启动，
 * 功能也是完整的（当前 5672 就是关的）。
 *
 * <h3>收件人</h3>
 * 学生本人 + 其班级辅导员（{@code classes.counselor_id}）。两人的通知偏好分别判定：
 * 学生在 {@code subscription_preferences} 里的开关、辅导员在 {@code user_settings} 里的开关
 * 都真的生效（见 {@link NotificationPreferences}）。
 *
 * <p>所有异常都在本类内部消化：一次通知失败不能让整轮预警扫描中断。
 */
@Slf4j
@Component
public class WarningNotifier {

    private final StudentNotifier studentNotifier;
    private final AcademicWarningMapper academicWarningMapper;
    private final RabbitTemplate rabbitTemplate;

    public WarningNotifier(StudentNotifier studentNotifier,
                           AcademicWarningMapper academicWarningMapper,
                           RabbitTemplate rabbitTemplate) {
        this.studentNotifier = studentNotifier;
        this.academicWarningMapper = academicWarningMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 一条新预警生成后通知学生与辅导员。
     *
     * @param warning 已插入、带 id 的预警
     */
    public void notifyNewWarning(AcademicWarning warning) {
        if (warning == null || warning.getId() == null) {
            return;
        }
        try {
            Map<String, Object> recipients = academicWarningMapper.selectWarningRecipients(warning.getStudentId());
            if (recipients == null) {
                log.warn("预警 {} 找不到学生档案（student_profile.id={}），跳过通知",
                        warning.getId(), warning.getStudentId());
                return;
            }
            Long profileId = toLong(recipients.get("profileId"));
            Long studentUserId = toLong(recipients.get("userId"));
            Long counselorUserId = toLong(recipients.get("counselorId"));
            String studentName = recipients.get("studentName") == null
                    ? warning.getStudentName() : String.valueOf(recipients.get("studentName"));

            String body = warning.getWarningMessage() != null
                    ? warning.getWarningMessage() : warning.getDescription();

            // 学生：profileId 传下去，订阅表里的等级细分开关才读得到
            boolean studentSent = studentNotifier.notifyUser(studentUserId, profileId,
                    NotificationPreferences.Category.WARNING, warning.getWarningLevel(),
                    warning.getTitle(),
                    body + " 请尽快查看并配合制定帮扶计划。",
                    warning.getId());
            // 辅导员：没有订阅行，只看 user_settings.warning_notification
            if (counselorUserId != null) {
                studentNotifier.notifyUser(counselorUserId, null,
                        NotificationPreferences.Category.WARNING, warning.getWarningLevel(),
                        "学生预警：" + studentName,
                        body,
                        warning.getId());
            } else {
                log.info("学生 {} 所在班级未配置辅导员，本次只通知学生", studentName);
            }

            if (studentSent) {
                publish(studentUserId, warning.getTitle(), body, warning.getId());
            }
        } catch (Exception e) {
            // 通知失败不能影响预警生成本身 —— 预警已经落库，这才是主业务
            log.error("发送预警 {} 的通知失败: {}", warning.getId(), e.getMessage(), e);
        }
    }

    /**
     * 预警被自动解除后通知学生。
     *
     * @param warning 已置为「已解除」的预警
     * @param reason  解除原因（写进通知正文，让学生知道为什么消失了）
     */
    public void notifyReleased(AcademicWarning warning, String reason) {
        if (warning == null || warning.getId() == null) {
            return;
        }
        try {
            Map<String, Object> recipients = academicWarningMapper.selectWarningRecipients(warning.getStudentId());
            if (recipients == null) {
                return;
            }
            Long profileId = toLong(recipients.get("profileId"));
            Long studentUserId = toLong(recipients.get("userId"));
            studentNotifier.notifyUser(studentUserId, profileId,
                    NotificationPreferences.Category.WARNING, warning.getWarningLevel(),
                    "预警已解除",
                    "你此前的预警「" + warning.getTitle() + "」已解除：" + reason,
                    warning.getId());
        } catch (Exception e) {
            log.error("发送预警 {} 的解除通知失败: {}", warning.getId(), e.getMessage(), e);
        }
    }

    /** 尽力而为的异步通道：broker 没起就只记一行 warn。 */
    private void publish(Long userId, String title, String content, Long warningId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "warning.notify");
            payload.put("userId", userId);
            payload.put("title", title);
            payload.put("content", content);
            payload.put("warningId", warningId);
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NOTIFICATION, "", payload);
        } catch (Exception e) {
            log.warn("预警通知的 MQ 推送失败（业务不受影响，通知已落库）: {}", e.getMessage());
        }
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
