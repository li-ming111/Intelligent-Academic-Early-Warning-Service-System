package com.academic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通义千问 AI 大模型服务
 * 通过 DashScope OpenAI 兼容接口调用通义千问
 */
@Service
public class TongYiService {

    private static final Logger log = LoggerFactory.getLogger(TongYiService.class);

    @Value("${tongyi.api-key}")
    private String apiKey;

    @Value("${tongyi.model:qwen-plus}")
    private String model;

    @Value("${tongyi.base-url}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * key 是否真的配过。
     *
     * ⚠️ `application.yml` 里写的是 `${DASHSCOPE_KEY:your-api-key-here}` —— 没设环境变量时
     * apiKey 就是字符串 "your-api-key-here"，而这个占位串**会被照常发出去**，服务端回 401、
     * chat() 返回 null。于是"有没有 key"这件事在调用方看起来和"网络不通"完全一样，
     * 调用方（AIController）只能靠猜。这里把它变成一个可以问的问题。
     */
    public boolean isConfigured() {
        if (apiKey == null) return false;
        String k = apiKey.trim();
        return !k.isEmpty()
                && !k.startsWith("${")
                && !"your-api-key-here".equalsIgnoreCase(k);
    }

    /**
     * 发送对话请求到通义千问
     */
    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            log.warn("通义千问 API Key 未配置（DASHSCOPE_KEY），本次不发起调用");
            return null;
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();

            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2000);

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                    return (String) msg.get("content");
                }
            } else {
                log.error("通义千问 API 返回错误: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("调用通义千问失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 分析学生成绩并给出智能化建议
     */
    public String analyzeStudentScores(String studentName, Map<String, Object> scoreData) {
        String systemPrompt = "你是一个专业的高校学业指导AI助手。请根据学生的成绩数据，给出具体、个性化的学习建议。" +
                "建议应包含：薄弱科目分析、学习方法建议、时间管理建议、心态调整建议。用中文回答，语气温和但专业。";

        String userMsg = String.format(
                "学生姓名：%s\n" +
                        "成绩数据：%s\n\n" +
                        "请分析该学生的学业状况，给出300字以内的学习建议。",
                studentName, scoreData.toString()
        );

        return chat(systemPrompt, userMsg);
    }

    /**
     * 预测学业风险
     */
    public String predictAcademicRisk(String studentName, Map<String, Object> riskData) {
        String systemPrompt = "你是一个高校学业预警专家。根据学生数据评估其学业风险等级（正常/低风险/中风险/高风险），" +
                "并给出预警建议。用中文回答，简洁有力。";

        String userMsg = String.format(
                "学生姓名：%s\n风险数据：%s\n\n请评估风险等级并给出具体建议。",
                studentName, riskData.toString()
        );

        return chat(systemPrompt, userMsg);
    }

    /**
     * 通用 AI 聊天
     */
    public String generalChat(String message) {
        String systemPrompt = "你是智学预警云服务平台的AI助手。你的职责是帮助师生解决学习相关问题，" +
                "包括成绩分析、学习方法指导、学业规划建议等。请用友好、专业的语气回答。";
        return chat(systemPrompt, message);
    }
}
