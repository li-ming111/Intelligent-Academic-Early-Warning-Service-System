package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Message;
import com.academic.mapper.UserMapper;
import com.academic.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W15（2026-10-04）：{@code GET /api/messages?type=} 的 {@code type} 参数。
 *
 * <p>原来 {@code type} 声明在签名上却**从没被读过** —— 调用方传 {@code ?type=2} 拿到的
 * 仍是全部类型的消息，是个会骗人的过滤器。{@code messages} 表有 {@code type} 列
 * （{@code Message.type} 是 Integer），现在按类型过滤。
 *
 * <p>判据：喂三条（type=1/2/2）过 {@code type="2"} ⇒ 只剩两条且全为 2；
 * 不传 type ⇒ 三条全回（证明过滤**只在传了 type 时**生效，对现有调用方零回归）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController.getMessages —— type 过滤真生效（W15）")
class MessageTypeFilterTest {

    @Mock private MessageService messageService;
    @Mock private UserMapper userMapper;

    @InjectMocks private MessageController controller;

    private static HttpServletRequest as(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        return r;
    }

    private static HttpServletRequest asAdmin(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        lenient().when(r.getHeader("X-Role")).thenReturn("4");
        return r;
    }

    private static Message msg(long id, int type) {
        Message m = new Message();
        m.setId(id);
        m.setType(type);
        m.setStatus(0);
        return m;
    }

    @Test
    @DisplayName("★ 管理员全量视图 + type=2 ⇒ 只剩 type=2 的（修前必红：修前回全部 3 条）")
    void typeFilterOnAllMessages() {
        when(messageService.getAllMessages())
                .thenReturn(List.of(msg(1, 1), msg(2, 2), msg(3, 2)));

        // 管理员 + 不传 userId ⇒ effectiveUserId=null ⇒ 走 getAllMessages()
        ApiResponse<List<Message>> resp = controller.getMessages(null, "2", false, asAdmin(1L));

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size(), "type=2 只该回 2 条");
        assertTrue(resp.getData().stream().allMatch(m -> m.getType() != null && m.getType() == 2));
    }

    @Test
    @DisplayName("不传 type ⇒ 行为不变，三条全回（零回归）")
    void noTypeNoFilter() {
        when(messageService.getAllMessages())
                .thenReturn(List.of(msg(1, 1), msg(2, 2), msg(3, 2)));

        ApiResponse<List<Message>> resp = controller.getMessages(null, null, false, asAdmin(1L));

        assertTrue(resp.isSuccess());
        assertEquals(3, resp.getData().size());
    }

    @Test
    @DisplayName("★ 非管理员看自己收件箱时 type 同样生效（走 getMessagesByUserId 分支）")
    void typeFilterOnOwnInbox() {
        when(messageService.getMessagesByUserId(7L))
                .thenReturn(List.of(msg(1, 1), msg(2, 2)));

        // 非管理员、不传 userId ⇒ effectiveUserId=自己的 7 ⇒ getMessagesByUserId(7)
        ApiResponse<List<Message>> resp = controller.getMessages(null, "1", false, as(7L));

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
        assertEquals(1, resp.getData().get(0).getType());
    }
}
