package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message Service Fallback
 * 当 message-service 不可用时的降级处理
 */
@Slf4j
@Component
public class MessageServiceFallback implements MessageServiceClient {

    @Override
    public ApiResponse<List<Message>> getMessages(Long userId, String type, Boolean unreadOnly) {
        log.warn("message-service is unavailable, returning fallback empty list");
        return ApiResponse.success(Collections.emptyList());
    }

    @Override
    public ApiResponse<Map<String, Object>> sendBroadcast(Map<String, Object> broadcastData) {
        log.warn("message-service is unavailable, cannot send broadcast");
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "消息服务不可用，无法发送广播消息");
        return ApiResponse.error("消息服务不可用，无法发送广播消息");
    }

    @Override
    public ApiResponse<String> sendMessage(Map<String, Object> messageData) {
        log.warn("message-service is unavailable, cannot send message");
        return ApiResponse.error("消息服务不可用，无法发送消息");
    }

    // deleteMessagesByUserId 的降级实现已随接口方法一起删除（2026-10-01）。

    @Override
    public ApiResponse<String> deleteMessageById(Long id) {
        log.warn("message-service is unavailable, cannot delete message: {}", id);
        return ApiResponse.error("消息服务不可用，无法删除消息");
    }
}
