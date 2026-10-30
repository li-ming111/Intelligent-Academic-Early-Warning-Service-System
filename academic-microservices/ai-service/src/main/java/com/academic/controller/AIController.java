package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.util.ExceptionMessages;
import com.academic.feign.StudentServiceFeignClient;
import com.academic.service.AIService;
import com.academic.service.TongYiService;
import com.academic.service.AnalysisContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能服务控制器
 * 提供成绩分析、风险预测、学习建议、预警生成等 AI 功能。
 */
@RestController
@RequestMapping("/ai")
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    private final AIService aiService;
    private final TongYiService tongYiService;
    private final StudentServiceFeignClient studentServiceClient;
    private final AnalysisContextService analysisContextService;

    // ⚠️ 2026-09-30：`/ai/chat` 与 `/ai/qa` 原来是**假装**答过的 ——
    //   `tongYiService.generalChat()` 在没配 key / 调不通时返回 null，
    //   而这两个接口把 null 换成了**写死的一句话**（"已收到您的问题，请提供具体成绩数据以便精确分析" /
    //   "学业预警系统可以帮助分析成绩趋势…"）并回 `success:true`。
    //   后果：`application.yml` 的默认 key 是占位串 `your-api-key-here`，也就是**开箱即骗** ——
    //   一次真实模型调用都没发生，前端却弹「处理意见生成成功」，还把这句话填进辅导员的处理意见框。
    //   现在：模型没答就如实说没答，不再拿一句话顶替。
    private static final String AI_UNAVAILABLE =
        "没有可用的 AI 回答：DeepSeek 未配置有效的 API Key（环境变量 AI_API_KEY），或本次调用失败。请配置后再使用该功能。";

    // ⚠️ 2026-10-08（W28）：下面三条**纯 AI** 端点当时没跟上上面那次改动 ——
    //   `analyze-warning-trend` / `analyze-warning` / `analyze-class-scores` 在模型没答时
    //   各自塞一句「…分析中，请稍后重试」**并包成 `success:true`**。
    //   那是一句**永远等不到**的进度话术：没配 key 时无论重试多少次都不会有结果，
    //   用户只会以为"再等等就有了"。与 AI_UNAVAILABLE 上面记的是同一族（说法与事实不符）。
    //   现在按**原因**分开说，并回 `error`（不再包 success）。
    private static final String AI_UNCONFIGURED =
        "DeepSeek API Key 未配置（环境变量 AI_API_KEY），AI 分析不可用。请配置后再使用该功能。";
    private static final String AI_CALL_FAILED =
        "调用 DeepSeek 失败（网络不通或服务端返回错误），本次拿不到分析结果。"
        + "重试若仍失败，请检查 AI_API_KEY 与出网情况。";

    /**
     * 纯 AI 端点没拿到结果时，**如实说没拿到**，并把"没配 key"与"调失败"分开说。
     *
     * <p>为什么必须分开：前者是**配置问题**（重试一万次也不会有结果），后者是**瞬时故障**
     * （重试有意义）。合并成一句"请稍后重试"等于把配置问题伪装成瞬时故障。
     */
    private ApiResponse<Object> aiUnavailable(String what) {
        String why = aiUnavailableWhy();
        log.warn("{}不可用：{}", what, why);
        return ApiResponse.error("没有可用的" + what + "：" + why);
    }

    /**
     * 上一条的"原因"部分，单独抽出来 —— 因为 {@link #analyzeStudent} 拿不到评语时
     * **不能整个响应回 error**（那边的成绩分析/风险预测是规则算出来的真数据，不该被连坐丢掉），
     * 只能把原因作为**一个字段**如实带回去。两处必须用同一句话，否则前端看到的原因会打架。
     */
    private String aiUnavailableWhy() {
        return tongYiService.isConfigured() ? AI_CALL_FAILED : AI_UNCONFIGURED;
    }

    public AIController(AIService aiService, TongYiService tongYiService,
                        StudentServiceFeignClient studentServiceClient,
                        AnalysisContextService analysisContextService) {
        this.aiService = aiService;
        this.tongYiService = tongYiService;
        this.studentServiceClient = studentServiceClient;
        this.analysisContextService = analysisContextService;
    }

    // ==================== 核心 AI 分析接口 ====================

    @PostMapping("/analyze-score")
    public ApiResponse<Object> analyzeScore(@RequestBody Map<String, Object> request) {
        try {
            log.info("成绩分析请求: studentId={}", request.get("studentId"));
            return ApiResponse.success(aiService.analyzeScore(request));
        } catch (Exception e) {
            log.error("成绩分析失败: {}", e.getMessage(), e);
            return ApiResponse.error("分析失败: " + ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/generate-suggestion")
    public ApiResponse<Object> generateSuggestion(@RequestBody Map<String, Object> request) {
        try {
            log.info("学习建议生成: studentId={}", request.get("studentId"));
            return ApiResponse.success(aiService.generateSuggestion(request));
        } catch (Exception e) {
            log.error("建议生成失败: {}", e.getMessage(), e);
            return ApiResponse.error("生成失败: " + ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/analyze-behavior")
    public ApiResponse<Object> analyzeBehavior(@RequestBody Map<String, Object> request) {
        try {
            log.info("趋势分析请求: studentId={}", request.get("studentId"));
            return ApiResponse.success(aiService.analyzeBehavior(request));
        } catch (Exception e) {
            log.error("趋势分析失败: {}", e.getMessage(), e);
            return ApiResponse.error("分析失败: " + ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/predict-risk")
    public ApiResponse<Object> predictRisk(@RequestBody Map<String, Object> request) {
        try {
            log.info("风险预测请求: studentId={}", request.get("studentId"));
            return ApiResponse.success(aiService.predictRisk(request));
        } catch (Exception e) {
            log.error("风险预测失败: {}", e.getMessage(), e);
            return ApiResponse.error("预测失败: " + ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/generate-warning")
    public ApiResponse<Object> generateWarning(@RequestBody Map<String, Object> request) {
        try {
            log.info("预警生成请求: studentId={}", request.get("studentId"));
            return ApiResponse.success(aiService.generateWarning(request));
        } catch (Exception e) {
            log.error("预警生成失败: {}", e.getMessage(), e);
            return ApiResponse.error("生成失败: " + ExceptionMessages.describe(e));
        }
    }

    // ==================== GET 便捷接口 ====================

    /**
     * 获取学生智能建议（综合调用）
     */
    @GetMapping("/student/{studentId}/suggestions")
    public ApiResponse<Object> getStudentSuggestions(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            return ApiResponse.success(aiService.generateSuggestion(request));
        } catch (Exception e) {
            log.error("获取建议失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/chat")
    public ApiResponse<Object> chat(@RequestBody Map<String, Object> data) {
        try {
            // 聊天功能：组合调用分析+建议
            String message = (String) data.getOrDefault("message", "");
            String studentId = (String) data.getOrDefault("studentId", "");

            Map<String, Object> analysisReq = new HashMap<>();
            analysisReq.put("studentId", studentId);
            analysisReq.put("query", message);

            Map<String, Object> result = new HashMap<>();
            result.put("studentId", studentId);

            // 调用 DeepSeek 获取智能回复
            String aiReply = tongYiService.generalChat(message);
            if (aiReply == null || aiReply.isEmpty()) {
                // 模型真的没答 —— 那就别答。原来这里塞的是一句写死的话，见 AI_UNAVAILABLE 上面那段。
                log.warn("chat 调用 DeepSeek 没有得到回答（key 已配置={}），如实回失败", tongYiService.isConfigured());
                return ApiResponse.error(AI_UNAVAILABLE);
            }
            result.put("reply", aiReply);

            // 如果有成绩数据，进行规则分析作为补充
            if (data.containsKey("scores")) {
                analysisReq.put("scores", data.get("scores"));
                Object analysis = aiService.analyzeScore(analysisReq);
                result.put("analysis", analysis);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("聊天失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/qa")
    public ApiResponse<Object> qa(@RequestBody Map<String, Object> data) {
        try {
            String question = (String) data.getOrDefault("question", "");
            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            // 调用 DeepSeek 智能问答
            String aiAnswer = tongYiService.generalChat(question);
            if (aiAnswer == null || aiAnswer.isEmpty()) {
                // 同 /ai/chat：没答就说没答，不拿写死的句子冒充模型输出。
                log.warn("qa 调用 DeepSeek 没有得到回答（key 已配置={}），如实回失败", tongYiService.isConfigured());
                return ApiResponse.error(AI_UNAVAILABLE);
            }
            result.put("answer", aiAnswer);

            // 如果传入了学生数据，进行实际分析
            if (data.containsKey("scores")) {
                Map<String, Object> req = new HashMap<>();
                req.put("studentId", data.getOrDefault("studentId", ""));
                req.put("scores", data.get("scores"));
                Object analysis = aiService.analyzeScore(req);
                result.put("analysis", analysis);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("问答失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/predict-warnings/{studentId}")
    public ApiResponse<Object> predictWarnings(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            return ApiResponse.success(aiService.predictRisk(request));
        } catch (Exception e) {
            log.error("预警预测失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @PostMapping("/generate-plan")
    public ApiResponse<Object> generatePlan(@RequestBody Map<String, Object> data) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", data.getOrDefault("studentId", ""));
            request.put("studentName", data.getOrDefault("studentName", ""));
            request.put("riskLevel", data.getOrDefault("riskLevel", "正常"));
            request.put("riskFactors", data.get("riskFactors"));
            return ApiResponse.success(aiService.generateWarning(request));
        } catch (Exception e) {
            log.error("计划生成失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    /**
     * 单生成绩分析。**成绩由本服务自己去 student-service 取**（前端不用传）。
     *
     * <p>2026-10-05（W26）修两处，两处都会让这个端点"看起来有结果、其实没数据"：
     *
     * <ol>
     *   <li><b>没解包</b>：Feign 声明是 {@code ApiResponse<Object>}，旧代码把**整个信封**塞进
     *       {@code request.put("scores", scores)}，而 {@code AIServiceImpl.analyzeScore} 直接
     *       {@code (List<Map>) request.get("scores")} ⇒ ClassCastException。因下面第 2 条
     *       让这行永远走不到，这个 bug 一直是**潜伏**的（一旦身份头通了就会立刻炸）。</li>
     *   <li><b>取不到被说成"没有"</b>：旧代码在 Feign 抛异常时只 log.warn 就往下走，
     *       于是 {@code analyzeScore} 看到 {@code scores == null}，回一句
     *       "暂无成绩数据，请先录入成绩" —— 而该生库里有 29 行成绩。这就是本仓最忌讳的
     *       "把'没拿到'渲染成'确实没有'"。现在改为显式传 {@code scoresUnavailable}，
     *       由 {@code AIServiceImpl} 如实说明"读不到"。实测见
     *       {@code testsprite/local-check/_w26_ai_scores_probe.mjs}。</li>
     * </ol>
     */
    @GetMapping("/analyze-scores/{studentId}")
    public ApiResponse<Object> analyzeScores(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            try {
                ApiResponse<Object> resp = studentServiceClient.getStudentScores(studentId);
                if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                    // ⭐ 必须取 getData()：塞整个信封进去会在 analyzeScore 里强转炸掉
                    request.put("scores", resp.getData());
                } else {
                    String why = resp == null
                            ? "成绩服务无响应"
                            : ("成绩服务返回 code=" + resp.getCode() + "：" + resp.getMessage());
                    log.warn("读取学生成绩失败: studentId={}, {}", studentId, why);
                    request.put("scoresUnavailable", why);
                }
            } catch (Exception ex) {
                log.warn("无法获取学生成绩数据: studentId={}, {}", studentId, ex.getMessage());
                request.put("scoresUnavailable", "调用成绩服务失败：" + ExceptionMessages.describe(ex));
            }
            return ApiResponse.success(aiService.analyzeScore(request));
        } catch (Exception e) {
            log.error("成绩分析失败: {}", e.getMessage(), e);
            return ApiResponse.error("分析失败: " + ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/analyze-warning-trend/{counselorId}")
    public ApiResponse<Object> analyzeWarningTrend(@PathVariable Long counselorId) {
        try {
            String prompt = analysisContextService.buildWarningTrendPrompt(counselorId);
            if (prompt == null) {
                return ApiResponse.notFound("未找到该辅导员管理的班级");
            }
            String aiAnalysis = tongYiService.generalChat(prompt);
            if (aiAnalysis == null || aiAnalysis.isBlank()) {
                return aiUnavailable("预警趋势分析");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("counselorId", counselorId);
            result.put("aiAnalysis", aiAnalysis);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("趋势分析失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/analyze/{studentId}")
    public ApiResponse<Object> analyzeStudent(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            request.put("studentName", "学生" + studentId);
            Object scoreAnalysis = aiService.analyzeScore(request);
            Object riskPrediction = aiService.predictRisk(request);

            Map<String, Object> result = new HashMap<>();
            result.put("scoreAnalysis", scoreAnalysis);
            result.put("riskPrediction", riskPrediction);
            // 用 DeepSeek 生成综合评语
            String comment = tongYiService.generalChat(
                    "请对学号为" + studentId + "的学生做综合学业评价，" +
                    "结合成绩和风险数据给出100字以内的总结和建议。");
            if (comment != null && !comment.isBlank()) {
                result.put("aiComment", comment);
            } else {
                // ⚠️ 这里**不能**照 analyze-warning 那三条一样 `return aiUnavailable(...)`：
                //   同一个响应里的 scoreAnalysis / riskPrediction 是**规则算出来的真数据**，
                //   整个回 error 会把它们一起丢掉（"AI 没答"连坐了"成绩分析没答"）。
                //   也不能干脆留 `aiComment: null` —— 前端只看到"没这项"，分不清
                //   "没配 key" 和 "这个学生没评语"。所以补一个**带原因**的字段，说清是哪种。
                result.put("aiCommentUnavailable", aiUnavailableWhy());
                log.warn("AI 综合评语不可用：studentId={}", studentId);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("综合分析失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/analyze-warning/{warningId}")
    public ApiResponse<Object> analyzeWarning(@PathVariable Long warningId) {
        try {
            // 只把 ID 交给模型，它会自己编等级和原因；必须连同真实预警记录一起传。
            String prompt = analysisContextService.buildWarningPrompt(warningId);
            if (prompt == null) {
                return ApiResponse.notFound("预警不存在");
            }
            String aiAnalysis = tongYiService.generalChat(prompt);
            if (aiAnalysis == null || aiAnalysis.isBlank()) {
                return aiUnavailable("预警分析");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("warningId", warningId);
            result.put("aiAnalysis", aiAnalysis);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("预警分析失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/analyze-class-scores/{classId}")
    public ApiResponse<Object> analyzeClassScores(@PathVariable Long classId) {
        try {
            String prompt = analysisContextService.buildClassScoresPrompt(classId);
            if (prompt == null) {
                return ApiResponse.notFound("班级不存在");
            }
            String aiAnalysis = tongYiService.generalChat(prompt);
            if (aiAnalysis == null || aiAnalysis.isBlank()) {
                return aiUnavailable("班级成绩分析");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("classId", classId);
            result.put("aiAnalysis", aiAnalysis);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("班级成绩分析失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/warning-suggestions/{studentId}")
    public ApiResponse<Object> getWarningSuggestions(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            return ApiResponse.success(aiService.generateSuggestion(request));
        } catch (Exception e) {
            log.error("预警建议获取失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/recommendations/{studentId}")
    public ApiResponse<Object> getRecommendations(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            return ApiResponse.success(aiService.generateSuggestion(request));
        } catch (Exception e) {
            log.error("推荐获取失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }

    @GetMapping("/assess-risk/{studentId}")
    public ApiResponse<Object> assessRisk(@PathVariable String studentId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("studentId", studentId);
            return ApiResponse.success(aiService.predictRisk(request));
        } catch (Exception e) {
            log.error("风险评估失败: {}", e.getMessage());
            return ApiResponse.error(ExceptionMessages.describe(e));
        }
    }
}
