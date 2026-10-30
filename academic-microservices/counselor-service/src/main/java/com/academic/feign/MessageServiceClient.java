package com.academic.feign;

import com.academic.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 写入 messages 表统一走 message-service：它负责落库与 WebSocket 实时推送。
 * 调用方（counselor-service）必须透传 X-User-Id/X-Role，见 FeignConfig。
 */
@FeignClient(name = "message-service")
public interface MessageServiceClient {

    @PostMapping("/api/messages/send")
    ApiResponse<String> sendMessage(@RequestBody Map<String, Object> messageData);
}
