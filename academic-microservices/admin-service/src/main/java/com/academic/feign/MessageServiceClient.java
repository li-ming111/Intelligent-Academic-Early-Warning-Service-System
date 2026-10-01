package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Message;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Message Service Feign Client
 * 调用 message-service 的接口
 */
@FeignClient(name = "message-service", fallback = MessageServiceFallback.class)
public interface MessageServiceClient {

    /**
     * 获取消息列表
     */
    @GetMapping("/api/messages")
    ApiResponse<List<Message>> getMessages(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") Boolean unreadOnly
    );

    /**
     * 发送广播消息
     */
    @PostMapping("/api/messages/broadcast")
    ApiResponse<Map<String, Object>> sendBroadcast(
            @RequestBody Map<String, Object> broadcastData
    );

    /**
     * 发送定向消息
     */
    @PostMapping("/api/messages/send")
    ApiResponse<String> sendMessage(
            @RequestBody Map<String, Object> messageData
    );

    // 这里原来有 `deleteMessagesByUserId`（`DELETE /api/messages/user/{userId}`，2026-10-01 删）。
    // 它的唯一调用方是 `AdminController.deleteUser` 里清站内信那一步，而那次调用已于 2026-09-30 删除
    // （`deleteUser` 现在先跑 `countReferences`，其中就有同一个 `messages` 谓词，
    //  有行就直接拒绝删除 ⇒ 能走到那一步时它必然是空操作，只会在 message-service
    //  没起时阻塞到 Feign 超时）。调用方没了，方法就跟着删 —— 顺带消掉 message-service
    //  那边一条不做归属校验的写端点（详见 `MessageController` 里的说明）。

    /**
     * 根据ID删除单条消息
     */
    @DeleteMapping("/api/messages/{id}")
    ApiResponse<String> deleteMessageById(
            @PathVariable Long id
    );
}