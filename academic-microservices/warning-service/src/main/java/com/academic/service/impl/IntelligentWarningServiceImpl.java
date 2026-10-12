package com.academic.service.impl;

import com.academic.common.entity.AcademicWarning;
import com.academic.entity.WarningRule;
import com.academic.exception.WarningNotFoundException;
import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.WarningRuleMapper;
import com.academic.service.IntelligentWarningService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IntelligentWarningServiceImpl implements IntelligentWarningService {

    private final AcademicWarningMapper academicWarningMapper;
    private final WarningRuleMapper warningRuleMapper;

    /**
     * {@code academic_warning.type} 的值。
     *
     * <p>与定时任务路径保持一致（{@code WarningScheduledTask.createWarning} 恒写 1）。
     * 全仓没有任何按 {@code type} 分值的读者 —— 它只要求"非空且与同类数据一致"。
     */
    private static final int WARNING_TYPE_ACADEMIC = 1;

    /** 没给等级时的默认值。原来写死的 2，同时 {@code warning_level} 是 NOT NULL，不能留空。*/
    private static final int DEFAULT_WARNING_LEVEL = 2;

    /** 调用方既没给说法、规则也没有描述时的兜底正文（原来的默认值）。*/
    private static final String DEFAULT_WARNING_MESSAGE = "学生学业状况需要关注";

    /** 找不到规则名时的标题兜底。级别名与活库 {@code warning_rules.name} 一致。*/
    private static final String DEFAULT_WARNING_TITLE = "学业预警";

    /**
     * 预警等级 → 标题。
     *
     * <p>2026-10-01 第二十三轮：这里原先有一段"这三个词在
     * {@code message-service/WarningNotificationServiceImpl}（{@code LEVEL_DESCRIPTION_MAP}）
     * 里有一份一模一样的，改这里请一起看那边"。那份副本随
     * {@code WarningNotification* } 三件套一起删除了（那条路径零调用方、且一旦调用必因
     * {@code messages.sender_id} 外键失败），所以现在<b>全仓只有这一份</b>。
     * 口径未变，仍是与活库 {@code warning_rules.name} 一致的"低级/中级/高级预警"。
     */
    private static final Map<Integer, String> LEVEL_TITLES =
            Map.of(1, "低级预警", 2, "中级预警", 3, "高级预警");

    public IntelligentWarningServiceImpl(AcademicWarningMapper academicWarningMapper, WarningRuleMapper warningRuleMapper) {
        this.academicWarningMapper = academicWarningMapper;
        this.warningRuleMapper = warningRuleMapper;
    }

    @Override
    public Object generateWarning(Map<String, Object> request) {
        log.info("Generating warning: {}", request);
        
        Long studentId = longOf(request, "studentId");
        if (studentId == null) {
            // student_id 是 NOT NULL。不先拦住，插入会以 SQL 异常的形式冒成一句
            // 没人看得懂的 500，而不是"缺学生ID"。
            throw new IllegalArgumentException("缺少 studentId，无法生成预警");
        }
        String studentName = str(request, "studentName");
        Long ruleId = longOf(request, "ruleId");
        Integer warningLevel = intOf(request, "warningLevel");
        String callerMessage = str(request, "warningMessage");

        WarningRule rule = null;
        if (ruleId != null) {
            rule = warningRuleMapper.selectById(ruleId);
            if (rule == null) {
                // 原来 ruleId 查不到就**静默忽略**，悄悄按调用方给的等级/正文生成一条
                // "挂着不存在的规则ID"的预警。静默丢弃本就是这一轮在修的缺陷类型。
                throw new IllegalArgumentException("预警规则不存在: " + ruleId);
            }
            warningLevel = rule.getWarningLevel();
        }
        if (warningLevel == null) {
            warningLevel = DEFAULT_WARNING_LEVEL;
        }
        // 调用方给的说法**优先**，规则描述只作兜底 —— 原代码是无条件用 rule.getDescription()
        // 覆盖调用方传进来的 warningMessage（又一个静默丢字段），而规则描述本身可能是 NULL，
        // 于是正文一路空到底。
        String message = firstNonBlank(callerMessage,
                rule != null ? rule.getDescription() : null,
                DEFAULT_WARNING_MESSAGE);
        String title = (rule != null && isNotBlank(rule.getRuleName()))
                ? rule.getRuleName()
                : LEVEL_TITLES.getOrDefault(warningLevel, DEFAULT_WARNING_TITLE);

        AcademicWarning warning = new AcademicWarning()
                .setStudentId(studentId)
                .setStudentName(studentName)
                .setRuleId(ruleId)
                .setType(WARNING_TYPE_ACADEMIC)
                .setTitle(title)
                // description 与 warningMessage **都写**：下游读的不是同一个 ——
                // CounselorWarnings.vue 的「预警原因」列、CollegeAdminController 的预警列表、
                // AdminController 的 Excel 导出读 description，定时任务路径也是两个都写。
                // 手工生成这条路原来一个都没写，所以那三处显示为空白。
                .setDescription(message)
                .setWarningLevel(warningLevel)
                .setWarningMessage(message)
                .setStatus(0)
                // fail_count **有意留空**：它是扫描判重键的一半，口径来自成绩表
                // （ScoreCaliber.FAIL_COUNT_BY_STUDENT）。手工生成没有这个输入，
                // 硬编一个数会污染扫描的判重集合 —— 宁可留空，与"迁移回填不到的历史行"
                // 是同一种状态（见 AcademicWarning.failCount 的注释）。
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());

        // 2026-10-12（W39）：这条路**不落** academic_warning_course 明细。
        // 不是漏了 —— 它的契约只收 studentId/ruleId/warningLevel/warningMessage，
        // 没有课程入参，收到的正文是自由文本、无从解析出课程。扫描那条路
        // （WarningScheduledTask.createWarning）才是课程明细的写入方，因为只有它手里
        // 有 ScoreCaliber.FAIL_DETAIL_BY_STUDENT 的 courseId。
        // 这条路前端零调用方，且 fail_count 也是有意留空的（见上），性质相同。
        academicWarningMapper.insert(warning);

        Map<String, Object> result = new HashMap<>();
        result.put("warningId", warning.getId());
        result.put("warningLevel", warning.getWarningLevel());
        result.put("warningMessage", warning.getWarningMessage());
        result.put("studentId", warning.getStudentId());
        result.put("status", "success");
        
        return result;
    }

    @Override
    public Object handleWarning(Map<String, Object> request) {
        log.info("Handling warning: {}", request);
        
        Long warningId = longOf(request, "warningId");
        String handledBy = str(request, "handledBy");
        String handleResult = str(request, "handleResult");
        if (handleResult == null) {
            handleResult = "已处理";
        }

        if (warningId == null) {
            // 原来这里 return 一段 {status:"error", message:"预警ID不能为空"} 的 map，
            // 而 controller 无条件把返回值包成 ApiResponse.success(...) ——
            // HTTP 200 + 响应体 code=200，调用方看到的是"成功"。改成抛异常，
            // 由 controller 翻成 400（前端拦截器只认 code===200 为成功）。
            throw new IllegalArgumentException("预警ID不能为空");
        }

        AcademicWarning warning = academicWarningMapper.selectById(warningId);
        if (warning == null) {
            throw new WarningNotFoundException("预警不存在: " + warningId);
        }

        warning.setStatus(1)
               .setHandledBy(handledBy)
               .setHandleResult(handleResult)
               .setHandledAt(LocalDateTime.now())
               // ⚠️ 更正（2026-09-30）：这一列**不写也会跟着动**。活库 DDL 是
               // `updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
               // —— 全库 60 多张表的 updated_at 都带 `ON UPDATE`（information_schema 的 EXTRA
               // 逐张查过），并在一张自建临时表上打过靶：真 UPDATE 后它自己跳到当前时刻，
               // 值没变的 no-op UPDATE 则不跳（ROW_COUNT()=0）。
               // 所以这里显式写**不是**在补一个"漏掉会坏"的洞，而是让时间戳由应用决定、
               // 不依赖 DB 侧行为，并与 admin-service 处理同一条预警的写法
               // （CollegeAdminController 的 w.setUpdatedAt(...)）保持一致。
               .setUpdatedAt(LocalDateTime.now());

        academicWarningMapper.updateById(warning);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "预警处理成功");
        result.put("warningId", warningId);
        
        return result;
    }

    @Override
    public Object getWarningList(Map<String, Object> params) {
        log.info("Getting warning list: {}", params);
        
        QueryWrapper<AcademicWarning> queryWrapper = new QueryWrapper<>();
        
        if (params != null) {
            if (params.containsKey("studentId")) {
                queryWrapper.eq("student_id", params.get("studentId"));
            }
            if (params.containsKey("warningLevel")) {
                queryWrapper.eq("warning_level", params.get("warningLevel"));
            }
            if (params.containsKey("status")) {
                queryWrapper.eq("status", params.get("status"));
            }
        }

        queryWrapper.orderByDesc("created_at");
        List<AcademicWarning> warnings = academicWarningMapper.selectList(queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("warnings", warnings);
        result.put("total", warnings.size());
        
        return result;
    }

    @Override
    public Object getWarningDetail(String warningId) {
        log.info("Getting warning detail: {}", warningId);
        
        Long id = parseWarningId(warningId);
        AcademicWarning warning = academicWarningMapper.selectById(id);
        
        if (warning == null) {
            // 同 handleWarning：原来返回 {status:"error"} 的 map，controller 无条件包成
            // ApiResponse.success(...) —— 失败报成成功。抛异常 → controller 翻成 404。
            throw new WarningNotFoundException("预警不存在: " + id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", warning.getId());
        result.put("studentId", warning.getStudentId());
        result.put("studentName", warning.getStudentName());
        result.put("ruleId", warning.getRuleId());
        result.put("warningLevel", warning.getWarningLevel());
        result.put("warningMessage", warning.getWarningMessage());
        result.put("status", warning.getStatus());
        result.put("handledBy", warning.getHandledBy());
        result.put("handleResult", warning.getHandleResult());
        result.put("handledAt", warning.getHandledAt());
        result.put("createdAt", warning.getCreatedAt());

        if (warning.getRuleId() != null) {
            WarningRule rule = warningRuleMapper.selectById(warning.getRuleId());
            if (rule != null) {
                result.put("ruleName", rule.getRuleName());
                result.put("ruleCode", rule.getRuleCode());
            }
        }
        
        return result;
    }

    @Override
    public Object updateWarningStatus(String warningId, Map<String, Object> request) {
        log.info("Updating warning status: {} {}", warningId, request);
        
        Long id = parseWarningId(warningId);
        Integer status = intOf(request, "status");
        if (status == null) {
            // 原来 status 为空时**一个字段都不改**，却照样回 "预警状态更新成功" ——
            // 又一处"报告成功但没写"。缺参就是缺参。
            throw new IllegalArgumentException("缺少 status 字段");
        }

        AcademicWarning warning = academicWarningMapper.selectById(id);
        if (warning == null) {
            // 同 handleWarning：原来返回 {status:"error"} 的 map，controller 无条件包成
            // ApiResponse.success(...) —— 失败报成成功。抛异常 → controller 翻成 404。
            throw new WarningNotFoundException("预警不存在: " + id);
        }

        warning.setStatus(status);
        if (status == 1 && warning.getHandledAt() == null) {
            warning.setHandledAt(LocalDateTime.now());
        }
        // 同 handleWarning：updated_at 靠 DB 的 `ON UPDATE CURRENT_TIMESTAMP` 也会动，
        // 这里显式写只是让时间戳由应用决定、与处理路径保持一致（不是"不写就坏"）。
        warning.setUpdatedAt(LocalDateTime.now());

        academicWarningMapper.updateById(warning);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "预警状态更新成功");
        result.put("warningId", warningId);
        result.put("newStatus", warning.getStatus());
        
        return result;
    }

    @Override
    public Object getWarningsByStudentId(Long studentId) {
        log.info("Getting warnings by studentId: {}", studentId);
        
        QueryWrapper<AcademicWarning> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.orderByDesc("created_at");
        
        List<AcademicWarning> warnings = academicWarningMapper.selectList(queryWrapper);
        
        Map<String, Object> result = new HashMap<>();
        result.put("warnings", warnings);
        result.put("total", warnings.size());
        
        return result;
    }

    @Override
    public Object getWarningsByLevel(Integer level) {
        log.info("Getting warnings by level: {}", level);
        
        QueryWrapper<AcademicWarning> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("warning_level", level);
        queryWrapper.orderByDesc("created_at");
        
        List<AcademicWarning> warnings = academicWarningMapper.selectList(queryWrapper);
        
        Map<String, Object> result = new HashMap<>();
        result.put("warnings", warnings);
        result.put("total", warnings.size());
        
        return result;
    }

    // ==================== 请求体取值的小工具 ====================
    //
    // 前端拼的 JSON 在不同端类型不同（数字 / '' / '3'），直接 (Long) 强转除了数字以外
    // 全炸 ClassCastException，表现为 500 + 一句看不懂的话。统一成：
    // 空白 → null（视为没填），填了但不是数字 → **明确报错**，绝不静默当没填
    // —— 静默丢弃正是这一轮在扫的缺陷类型。（同 user-service 注册路径的口径。）

    /** 取字符串：null / 全空白都算没填。*/
    private static String str(Map<String, Object> request, String key) {
        Object v = request == null ? null : request.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 取整数：没填返回 null；填了不是数字则报错（不静默）。*/
    private static Integer intOf(Map<String, Object> request, String key) {
        String s = str(request, key);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 不是整数: " + s);
        }
    }

    /** 取长整数：同上。*/
    private static Long longOf(Map<String, Object> request, String key) {
        String s = str(request, key);
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 不是数字: " + s);
        }
    }

    /** 路径上的预警ID。写不出数字时给一句人话，而不是裸的 NumberFormatException。*/
    private static Long parseWarningId(String warningId) {
        if (warningId == null || warningId.trim().isEmpty()) {
            throw new IllegalArgumentException("预警ID不能为空");
        }
        try {
            return Long.valueOf(warningId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("预警ID不是数字: " + warningId);
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** 第一个非空白的值；全空时返回 null。*/
    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (isNotBlank(c)) {
                return c;
            }
        }
        return null;
    }
}