package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 教师回复统一走 message-service：由它落库并向辅导员推送 WebSocket。
 * 身份头由 {@link com.academic.config.FeignConfig} 透传。
 */
@FeignClient(name = "message-service")
public interface MessageServiceClient {

    @PostMapping("/api/messages/send")
    ApiResponse<String> sendMessage(@RequestBody Map<String, Object> messageData);
}
