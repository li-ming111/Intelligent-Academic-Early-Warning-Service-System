package com.academic.service.impl;

import com.academic.common.constant.ScoreCaliber;
import com.academic.service.AIService;
import com.academic.service.TongYiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 智能分析服务实现
 * 基于通义千问大模型的智能分析 + 规则引擎兜底
 */
@Service
public class AIServiceImpl implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);

    private final TongYiService tongYiService;

    public AIServiceImpl(TongYiService tongYiService) {
        this.tongYiService = tongYiService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object analyzeScore(Map<String, Object> request) {
        log.info("分析学生成绩: studentId={}", request.get("studentId"));

        Map<String, Object> result = new HashMap<>();
        result.put("analyzedAt", System.currentTimeMillis());

        // 从请求中提取成绩数据
        List<Map<String, Object>> scores = (List<Map<String, Object>>) request.get("scores");
        if (scores == null || scores.isEmpty()) {
            result.put("totalCourses", 0);
            result.put("averageScore", 0.0);
            result.put("suggestion", "暂无成绩数据，请先录入成绩");
            return result;
        }

        // 统计口径统一走 ScoreCaliber：按课程去重、取最新一次有效成绩、排除期末未录入。
        // 旧实现直接遍历原始列表、并用 toDouble(..., 0) 兜底缺值，于是同一门课的重修记录
        // 被重复计数、期末未录入的课被按 0 分算成挂科 —— "挂科 9 门"这类虚高数字就来自这里。
        List<Map<String, Object>> latestScores = ScoreCaliber.latestPerCourse(scores);
        if (latestScores.isEmpty()) {
            result.put("totalCourses", 0);
            result.put("averageScore", 0.0);
            result.put("suggestion", "暂无可统计的成绩（期末成绩尚未录入），请在期末成绩录入后重试");
            return result;
        }

        // 计算统计指标
        double totalScore = 0;
        double totalCredits = 0;
        double totalGradePoints = 0;
        int passCount = 0;
        int failCount = 0;
        List<Map<String, Object>> weakSubjects = new ArrayList<>();
        List<Map<String, Object>> strongSubjects = new ArrayList<>();

        for (Map<String, Object> score : latestScores) {
            double courseScore = toDouble(score.get("scoreTotal"), 0);
            double credits = toDouble(score.get("credits"), 0);
            double gradePoint = toDouble(score.get("gradePoint"), 0);

            totalScore += courseScore;
            totalCredits += credits;
            totalGradePoints += gradePoint * credits;

            Map<String, Object> subjectInfo = new HashMap<>();
            subjectInfo.put("courseName", score.getOrDefault("courseName", "未知课程"));
            subjectInfo.put("score", courseScore);
            subjectInfo.put("credits", credits);

            if (courseScore < 60) {
                failCount++;
                weakSubjects.add(subjectInfo);
            } else if (courseScore >= 85) {
                strongSubjects.add(subjectInfo);
            } else if (courseScore < 70) {
                weakSubjects.add(subjectInfo);
            }
            if (courseScore >= 60) {
                passCount++;
            }
        }

        int totalCourses = latestScores.size();
        double avgScore = totalCourses > 0 ? totalScore / totalCourses : 0;
        // GPA = 总绩点学分 / 总学分
        double gpa = totalCredits > 0 ? totalGradePoints / totalCredits : 0;

        result.put("totalCourses", totalCourses);
        result.put("passCount", passCount);
        result.put("failCount", failCount);
        result.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
        result.put("gpa", Math.round(gpa * 100.0) / 100.0);
        result.put("weakSubjects", weakSubjects);
        result.put("strongSubjects", strongSubjects);

        // 生成分析结论
        StringBuilder analysis = new StringBuilder();
        if (gpa >= 3.5) {
            analysis.append("学术表现优秀，GPA处于较高水平。");
        } else if (gpa >= 2.0) {
            analysis.append("学术表现良好，GPA处于正常水平。");
        } else if (gpa >= 1.0) {
            analysis.append("学术表现需关注，GPA偏低。");
        } else {
            analysis.append("学术表现堪忧，GPA严重偏低，需立即采取帮扶措施。");
        }

        if (failCount > 0) {
            analysis.append(" 有").append(failCount).append("门课程不及格，需要重点关注。");
        }
        result.put("analysis", analysis.toString());

        // 调用通义千问获取 AI 智能分析
        try {
            String aiAnalysis = tongYiService.analyzeStudentScores(
                    (String) request.getOrDefault("studentName", "学生"), result);
            if (aiAnalysis != null && !aiAnalysis.isEmpty()) {
                result.put("aiAnalysis", aiAnalysis);
            }
        } catch (Exception e) {
            log.warn("AI 分析调用失败，使用规则引擎结果: {}", e.getMessage());
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object generateSuggestion(Map<String, Object> request) {
        log.info("生成学习建议: studentId={}", request.get("studentId"));

        List<Map<String, Object>> weakSubjects =
                (List<Map<String, Object>>) request.get("weakSubjects");
        Double gpa = toDoubleOrNull(request.get("gpa"));

        Map<String, Object> result = new HashMap<>();
        List<String> suggestions = new ArrayList<>();
        List<Map<String, Object>> actionItems = new ArrayList<>();

        // 基于薄弱科目生成建议
        if (weakSubjects != null && !weakSubjects.isEmpty()) {
            for (Map<String, Object> subject : weakSubjects) {
                String courseName = (String) subject.getOrDefault("courseName", "该课程");
                double score = toDouble(subject.get("score"), 0);

                Map<String, Object> action = new HashMap<>();
                action.put("course", courseName);
                action.put("type", "study");
                if (score < 60) {
                    action.put("priority", "high");
                    action.put("suggestion", courseName + "需要重修，建议联系任课教师制定补课计划");
                    suggestions.add("重点攻克 " + courseName + "：当前成绩" + score + "分，建议每周额外投入4-6小时");
                } else if (score < 70) {
                    action.put("priority", "medium");
                    action.put("suggestion", courseName + "需要加强，建议多做练习并与同学讨论");
                    suggestions.add("加强 " + courseName + "：当前成绩" + score + "分，建议每周额外投入2-3小时");
                }
                actionItems.add(action);
            }
        }

        // 基于 GPA 的建议
        if (gpa != null) {
            if (gpa < 2.0) {
                suggestions.add("GPA(" + gpa + ")偏低，建议减少课外活动，专注学业提升");
                Map<String, Object> action = new HashMap<>();
                action.put("course", "综合");
                action.put("type", "planning");
                action.put("priority", "high");
                action.put("suggestion", "制定学业提升计划，与辅导员沟通寻求帮扶");
                actionItems.add(action);
            } else if (gpa < 3.0) {
                suggestions.add("GPA(" + gpa + ")有提升空间，建议合理安排时间，提高学习效率");
            }
        }

        // 通用建议（规则引擎兜底）
        suggestions.add("建议使用番茄工作法管理学习时间，每学习45分钟休息5分钟");
        suggestions.add("每周与学习伙伴进行一次知识复盘，互相考查对方");

        // 调用通义千问获取个性化建议
        try {
            String aiSuggestion = tongYiService.generalChat(
                    "请根据以下学生数据生成个性化学习建议，200字以内：" +
                    "薄弱科目：" + (weakSubjects != null ? weakSubjects.toString() : "无") +
                    "，GPA：" + (gpa != null ? gpa : "未知"));
            if (aiSuggestion != null && !aiSuggestion.isEmpty()) {
                Map<String, Object> aiAction = new HashMap<>();
                aiAction.put("type", "ai_suggestion");
                aiAction.put("priority", "high");
                aiAction.put("suggestion", aiSuggestion);
                actionItems.add(0, aiAction);
            }
        } catch (Exception e) {
            log.warn("AI建议生成失败，使用规则引擎: {}", e.getMessage());
        }

        result.put("suggestions", suggestions);
        result.put("actionItems", actionItems);
        result.put("generatedAt", System.currentTimeMillis());

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object analyzeBehavior(Map<String, Object> request) {
        log.info("分析成绩趋势: studentId={}", request.get("studentId"));

        List<Map<String, Object>> semesterScores =
                (List<Map<String, Object>>) request.get("semesterScores");

        Map<String, Object> result = new HashMap<>();

        if (semesterScores == null || semesterScores.isEmpty()) {
            result.put("trend", "stable");
            result.put("analysis", "暂无足够学期数据进行趋势分析");
            return result;
        }

        // 按学期计算平均分
        List<Map<String, Object>> trends = new ArrayList<>();
        for (Map<String, Object> semester : semesterScores) {
            Map<String, Object> point = new HashMap<>();
            point.put("semester", semester.getOrDefault("semester", "未知"));
            point.put("averageScore", semester.getOrDefault("averageScore", 0));
            point.put("gpa", semester.getOrDefault("gpa", 0));
            point.put("courseCount", semester.getOrDefault("courseCount", 0));
            trends.add(point);
        }

        // 判断趋势
        String trend = "stable";
        String trendDetail = "成绩保持稳定";
        if (trends.size() >= 2) {
            double first = toDouble(trends.get(0).get("averageScore"), 0);
            double last = toDouble(trends.get(trends.size() - 1).get("averageScore"), 0);
            double diff = last - first;
            if (diff > 5) {
                trend = "improving";
                trendDetail = "成绩呈上升趋势(+{diff})，继续保持".replace("{diff}", String.format("%.1f", diff));
            } else if (diff < -5) {
                trend = "declining";
                trendDetail = "成绩呈下降趋势({diff})，需引起重视".replace("{diff}", String.format("%.1f", diff));
            }
        }

        result.put("trend", trend);
        result.put("trendDetail", trendDetail);
        result.put("semesterTrends", trends);
        result.put("analysis", trendDetail);

        // 调用通义千问分析趋势
        try {
            String aiTrend = tongYiService.generalChat(
                    "请分析以下学生学期成绩趋势数据，给出趋势评价和改善建议，150字以内：" +
                    trends.toString() + "，当前趋势：" + trend);
            if (aiTrend != null && !aiTrend.isEmpty()) {
                result.put("aiTrendAnalysis", aiTrend);
            }
        } catch (Exception e) {
            log.warn("AI趋势分析失败，使用规则引擎: {}", e.getMessage());
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object predictRisk(Map<String, Object> request) {
        log.info("预测学业风险: studentId={}", request.get("studentId"));

        Double gpa = toDoubleOrNull(request.get("gpa"));
        Double failCount = toDoubleOrNull(request.get("failCount"));
        Double totalCourses = toDoubleOrNull(request.get("totalCourses"));
        List<Map<String, Object>> weakSubjects =
                (List<Map<String, Object>>) request.get("weakSubjects");

        Map<String, Object> result = new HashMap<>();

        // 风险评估算法
        int riskScore = 0;
        List<String> riskFactors = new ArrayList<>();

        if (gpa != null) {
            if (gpa < 1.0) {
                riskScore += 40;
                riskFactors.add("GPA严重偏低(" + gpa + ")，有退学风险");
            } else if (gpa < 1.5) {
                riskScore += 25;
                riskFactors.add("GPA偏低(" + gpa + ")，学业警示级别");
            } else if (gpa < 2.0) {
                riskScore += 15;
                riskFactors.add("GPA略低于标准(" + gpa + ")，需关注");
            }
        }

        if (failCount != null && failCount > 0) {
            if (failCount >= 3) {
                riskScore += 30;
                riskFactors.add("不及格科目达" + failCount.intValue() + "门，严重影响学业");
            } else {
                riskScore += 15 * failCount.intValue();
                riskFactors.add("有" + failCount.intValue() + "门课程不及格");
            }
        }

        if (weakSubjects != null && weakSubjects.size() >= 4) {
            riskScore += 10;
            riskFactors.add("薄弱科目较多(" + weakSubjects.size() + "门)，学习压力大");
        }

        // 风险等级判定
        String riskLevel;
        String riskDescription;
        if (riskScore >= 50) {
            riskLevel = "高风险";
            riskDescription = "学业状况严峻，建议立即启动预警和帮扶计划";
        } else if (riskScore >= 25) {
            riskLevel = "中风险";
            riskDescription = "存在一定学业风险，建议辅导员关注并制定预防措施";
        } else if (riskScore >= 10) {
            riskLevel = "低风险";
            riskDescription = "学业状况基本正常，建议保持关注";
        } else {
            riskLevel = "正常";
            riskDescription = "学业状况良好，建议继续保持";
        }

        result.put("riskLevel", riskLevel);
        result.put("riskScore", riskScore);
        result.put("description", riskDescription);
        result.put("riskFactors", riskFactors);
        result.put("predictedAt", System.currentTimeMillis());

        // 调用通义千问获取 AI 风险分析
        try {
            String aiRisk = tongYiService.predictAcademicRisk(
                    (String) request.getOrDefault("studentName", "学生"), result);
            if (aiRisk != null && !aiRisk.isEmpty()) {
                result.put("aiRiskAnalysis", aiRisk);
            }
        } catch (Exception e) {
            log.warn("AI 风险预测调用失败，使用规则引擎结果: {}", e.getMessage());
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object generateWarning(Map<String, Object> request) {
        log.info("生成预警建议: studentId={}", request.get("studentId"));

        String riskLevel = (String) request.getOrDefault("riskLevel", "正常");
        List<String> riskFactors = (List<String>) request.get("riskFactors");
        String studentName = (String) request.getOrDefault("studentName", "该学生");

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // 风险等级映射
        switch (riskLevel) {
            case "高风险":
                result.put("warningLevel", 3);
                result.put("warningLevelName", "严重预警");
                break;
            case "中风险":
                result.put("warningLevel", 2);
                result.put("warningLevelName", "中度预警");
                break;
            case "低风险":
                result.put("warningLevel", 1);
                result.put("warningLevelName", "轻度预警");
                break;
            default:
                result.put("warningLevel", 0);
                result.put("warningLevelName", "正常");
                break;
        }

        if (riskFactors != null) {
            result.put("riskFactors", riskFactors);
        }

        // 调用通义千问生成预警建议（取代硬编码的 if-else）
        try {
            String aiWarning = tongYiService.generalChat(
                    "你是学业预警专家。学生" + studentName +
                    "，风险等级：" + riskLevel +
                    "，风险因素：" + (riskFactors != null ? riskFactors : "无") +
                    "。请生成3-5条具体的预警干预建议，每条建议写明优先级(high/medium/low)和类型(communication/intervention/notification/support)。" +
                    "以JSON数组格式返回：[{\"suggestion\":\"...\",\"priority\":\"...\",\"type\":\"...\"}]");
            if (aiWarning != null && !aiWarning.isEmpty()) {
                result.put("aiRecommendations", aiWarning);
            }
        } catch (Exception e) {
            log.warn("AI预警建议生成失败: {}", e.getMessage());
        }

        // 规则引擎兜底：保留基本建议
        result.put("recommendations", generateFallbackRecommendations(riskLevel, studentName));
        result.put("generatedAt", System.currentTimeMillis());
        return result;
    }

    private List<Map<String, Object>> generateFallbackRecommendations(String riskLevel, String studentName) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        switch (riskLevel) {
            case "高风险":
                addRecommendation(recommendations, "立即与" + studentName + "进行一对一谈话", "high", "communication");
                addRecommendation(recommendations, "制定详细帮扶计划并安排辅导", "high", "intervention");
                break;
            case "中风险":
                addRecommendation(recommendations, "与" + studentName + "谈话了解学习困难", "high", "communication");
                addRecommendation(recommendations, "联系任课教师关注课堂表现", "medium", "notification");
                break;
            case "低风险":
                addRecommendation(recommendations, "发送学习提醒通知", "medium", "notification");
                break;
            default:
                addRecommendation(recommendations, "保持当前学习状态", "low", "encourage");
                break;
        }
        return recommendations;
    }

    // ==================== 工具方法 ====================

    private double toDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Double toDoubleOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addRecommendation(List<Map<String, Object>> list,
                                    String suggestion, String priority, String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("suggestion", suggestion);
        item.put("priority", priority);
        item.put("type", type);
        list.add(item);
    }
}
