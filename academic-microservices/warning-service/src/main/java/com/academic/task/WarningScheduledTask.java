package com.academic.task;

import com.academic.common.constant.ScoreCaliber;
import com.academic.common.entity.AcademicWarning;
import com.academic.entity.WarningRule;
import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.ScoreReadMapper;
import com.academic.mapper.WarningReleaseRecordMapper;
import com.academic.mapper.WarningRuleMapper;
import com.academic.notify.WarningNotifier;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 预警自动扫描定时任务。
 *
 * <p>每天凌晨 2 点全量扫描，另外每 30 分钟增量扫描一次。两条路径共用
 * {@link #runScan(boolean)}，逻辑只有一份。
 *
 * <p>一轮扫描做两件事，顺序固定：
 * <ol>
 *   <li><b>自动解除</b>（{@link #releaseRecoveredWarnings}）—— 待处理的预警如果按
 *       当前成绩重算已不再满足规则条件，置为「已解除」并写 {@code warning_release_records}；</li>
 *   <li><b>生成新预警</b> —— 一次聚合查出每名学生的挂科门数 → 按规则表匹配<b>唯一一条</b>规则
 *       → 每名学生只生成一条，并通知学生与其辅导员。</li>
 * </ol>
 * 先解除后生成：解除只影响 status=0 的行，生成按「学生 + 挂科门数」判重（不看 status），
 * 两者不会互相干扰。
 *
 * <p>旧实现分三段扫描（低分学生、多门挂科、按规则），且 {@code existingWarningStudents}
 * 只在开头加载一次、从不回写，同一名学生会命中多个分支被重复插入。现在每人只可能出一条。
 *
 * <p>预警等级完全来自 {@code warning_rules.level}，没有任何硬编码档位。
 */
@Slf4j
@Component
@EnableScheduling
public class WarningScheduledTask {

    /** 预警文案里最多罗列几门挂科课程，超出部分以"等"省略。 */
    private static final int MAX_COURSE_DETAIL = 5;

    /** 自动解除时写进 {@code handled_by} 的标记，与人工处理（辅导员姓名）区分开。 */
    static final String AUTO_HANDLER = "系统自动";

    /** {@code warning_release_records.release_reason}：成绩恢复导致系统自动解除。 */
    static final String RELEASE_REASON_AUTO = "auto_recovered";

    /**
     * 「正在扫描」标志。定时任务与手动触发共用 {@link #runScan(boolean)}，此前**没有任何互斥** ——
     * 同一条路径可以被 02:00 的定时任务和一次手动触发同时跑。
     *
     * <p>串行重复本来由判重键（学生 + 挂科门数）挡住，但**并发**挡不住：两次扫描会读到同一份成绩
     * 快照，各自认为"这个人还没预警"，于是各插一条。加一个按钮之后（管理端可手动触发）这个竞态从
     * "理论上会" 变成 "点两下就会"。
     */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /**
     * 一轮扫描的结果。
     *
     * @param generated 本次**新生成**的预警条数
     * @param released  本次**自动解除**的预警条数（此前只写日志，界面上看不到）
     * @param skipped   是否因为已有一轮扫描在进行而被跳过（此时 generated/released 都是 0）
     */
    public record ScanResult(int generated, int released, boolean skipped) {
    }

    private final ScoreReadMapper scoreReadMapper;
    private final AcademicWarningMapper academicWarningMapper;
    private final WarningRuleMapper warningRuleMapper;
    private final WarningReleaseRecordMapper warningReleaseRecordMapper;
    private final WarningNotifier warningNotifier;

    public WarningScheduledTask(ScoreReadMapper scoreReadMapper,
                                AcademicWarningMapper academicWarningMapper,
                                WarningRuleMapper warningRuleMapper,
                                WarningReleaseRecordMapper warningReleaseRecordMapper,
                                WarningNotifier warningNotifier) {
        this.scoreReadMapper = scoreReadMapper;
        this.academicWarningMapper = academicWarningMapper;
        this.warningRuleMapper = warningRuleMapper;
        this.warningReleaseRecordMapper = warningReleaseRecordMapper;
        this.warningNotifier = warningNotifier;
    }

    /** 每天凌晨 2:00 全量扫描。 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scanAndGenerateWarnings() {
        log.info("========== 自动预警扫描开始 ==========");
        try {
            ScanResult result = runScan(true);
            log.info("========== 自动预警扫描完成，共生成 {} 条预警，自动解除 {} 条 ==========",
                    result.generated(), result.released());
        } catch (Exception e) {
            log.error("自动预警扫描异常: {}", e.getMessage(), e);
        }
    }

    /** 每 30 分钟增量扫描，用于当天新录入的成绩尽快触发预警。 */
    @Scheduled(fixedRate = 1800000)
    public void incrementalScan() {
        try {
            ScanResult result = runScan(false);
            if (result.generated() > 0) {
                log.info("增量预警扫描发现 {} 名新预警学生", result.generated());
            }
        } catch (Exception e) {
            log.error("增量扫描异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动全量扫描一次，供管理端触发接口与演示使用（否则只能等到凌晨 2 点）。
     *
     * @return 本次新生成的预警条数（不含自动解除的条数）—— 只关心条数的调用方用它
     */
    public int triggerNow() {
        return runScan(true).generated();
    }

    /**
     * 手动全量扫描一次，并把一轮扫描的**全部**产出告诉调用方。
     *
     * <p>与 {@link #triggerNow()} 走同一条互斥路径。加这个入口是因为 {@code triggerNow()} 只回
     * 「新生成」条数，而一轮扫描其实还做**自动解除**（{@link #releaseRecoveredWarnings}）——
     * 界面若只显示"新生成 0 条"，会把"解除了 3 条"这件事藏起来（W26）。
     *
     * @return 含 generated / released / skipped 三项的结果，供接口原样回给前端
     */
    public ScanResult triggerNowDetailed() {
        return runScan(true);
    }

    /**
     * 扫描并生成预警，同时解除已恢复的预警。
     *
     * <p>**互斥入口**：定时任务与手动触发都走这里，同一时刻只允许一轮。已经在跑时本次直接跳过
     * （返回 {@link ScanResult#skipped()} = true），而不是排队 —— 排队会让连点按钮的人以为
     * "点了 5 次所以要多扫 5 遍"，跳过才是他要的语义。
     *
     * @param verbose 是否输出完整诊断日志。全量扫描传 {@code true}；
     *                每 30 分钟一次的增量扫描传 {@code false}，避免规则诊断日志刷屏
     */
    private ScanResult runScan(boolean verbose) {
        if (!scanning.compareAndSet(false, true)) {
            log.warn("已有一轮预警扫描在进行中，本次触发被跳过（不做排队）");
            return new ScanResult(0, 0, true);
        }
        try {
            return doScan(verbose);
        } finally {
            scanning.set(false);
        }
    }

    /**
     * 一轮扫描的实际逻辑。**只能经 {@link #runScan(boolean)} 调用** —— 互斥在那里。
     *
     * @return 本次新生成条数 + 自动解除条数
     */
    private ScanResult doScan(boolean verbose) {
        List<FailCountRule> rules = loadRules(verbose);
        if (rules.isEmpty()) {
            log.warn("没有可用的挂科数预警规则，本次扫描不生成预警");
            return new ScanResult(0, 0, false);
        }

        // ==================== 判重口径 ====================
        // 既有的「学生 + 挂科门数」组合 → 跳过。**不看 status**。
        // 所以"把一条预警处理掉"不再等于"下一轮又给他报一条"：
        //   · 处理后门数没变  → 键相同 → 跳过（从前这里会再插一条，页面上一人挂两条）；
        //   · 处理后门数变了  → 键不同 → 照常生成新的一条（问题变严重本来就该再报）。
        // ⚠️ 加 fail_count 列之前生成的行，如果迁移回填不到门数（正文里没有"已累计N门"），
        //    就没有可比较的键，只能按**旧口径兜底**：仍有未处理(status=0)的旧行就跳过。
        //    这一格覆盖不到"已处理 + 没门数"的历史行（旧口径本来就不拦它），
        //    属于回填失败的残余；活库的 4 行已由迁移脚本全部回填，不会走到这里。
        Map<Long, Set<Integer>> existingCounts = new HashMap<>();
        Set<Long> legacyPendingStudents = new HashSet<>();
        for (Map<String, Object> key : scoreReadMapper.findExistingWarningKeys()) {
            if (key == null) {
                continue;
            }
            Long sid = toLong(key.get("studentId"));
            if (sid == null) {
                continue;
            }
            Integer failCount = toInt(key.get("failCount"));
            Integer status = toInt(key.get("status"));
            if (failCount != null) {
                existingCounts.computeIfAbsent(sid, k -> new HashSet<>()).add(failCount);
            } else if (status != null && status == 0) {
                legacyPendingStudents.add(sid);
            }
        }
        if (verbose) {
            log.info("已有 {} 名学生存在未处理预警（旧口径兜底），{} 名学生有可比较门数的历史预警",
                    legacyPendingStudents.size(), existingCounts.size());
        }

        List<Map<String, Object>> failCountRows = scoreReadMapper.findFailCountByStudent();
        Map<Long, Integer> currentFailCounts = new HashMap<>();
        for (Map<String, Object> row : failCountRows) {
            Long sid = toLong(row.get("studentId"));
            Integer count = toInt(row.get("failCount"));
            if (sid != null && count != null) {
                currentFailCounts.put(sid, count);
            }
        }

        // ---- 第一步：解除已恢复的预警 ----
        int released = releaseRecoveredWarnings(rules, currentFailCounts, verbose);

        // ---- 第二步：生成新预警 ----
        int generated = 0;
        for (Map<String, Object> row : failCountRows) {
            Long studentId = toLong(row.get("studentId"));
            if (studentId == null) {
                log.warn("跳过一条缺少 studentId 的挂科统计记录: {}", row);
                continue;
            }
            Integer failCount = toInt(row.get("failCount"));
            if (failCount == null || failCount <= 0) {
                continue;
            }
            if (legacyPendingStudents.contains(studentId)
                    || existingCounts.getOrDefault(studentId, Set.of()).contains(failCount)) {
                continue;
            }

            FailCountRule rule = pickRule(rules, failCount);
            if (rule == null) {
                log.debug("学生 {} 挂科 {} 门，无匹配规则，跳过", studentId, failCount);
                continue;
            }

            List<Map<String, Object>> detail = scoreReadMapper.findFailDetailByStudent(studentId);
            createWarning(studentId, (String) row.get("studentName"), rule, failCount, detail);

            // 关键：立即回写，保证同一轮扫描内不会对同一名学生重复生成
            existingCounts.computeIfAbsent(studentId, k -> new HashSet<>()).add(failCount);
            generated++;
        }
        if (released > 0) {
            log.info("本轮扫描自动解除 {} 条已恢复的预警", released);
        }
        return new ScanResult(generated, released, false);
    }

    /**
     * 自动解除：把「当前成绩已不再满足其规则条件」的待处理预警置为已解除。
     *
     * <p>判定用的是同一个 {@link ScoreReadMapper#findFailCountByStudent()} 聚合结果 ——
     * 与"生成预警"完全同一套口径，不会出现"按这套算法该报、按那套算法该解"的自相矛盾。
     *
     * <p>三种情况<b>刻意不动</b>：
     * <ul>
     *   <li>规则已被停用或改成非挂科口径（如 GPA 规则）⇒ 无从判定，保持原状。
     *       宁可留一条待处理，也不要凭猜测把预警抹掉。</li>
     *   <li>当前门数仍落在规则区间内 ⇒ 问题还在，不解除。</li>
     *   <li>并发下已被别人改掉（{@code UPDATE} 影响行数为 0）⇒ 不写解除记录，
     *       避免"记录有、状态没变"的脏数据。</li>
     * </ul>
     *
     * @param currentFailCounts student_profile.id → 当前挂科门数；查不到的学生按 0 门处理
     *                          （聚合查询只返回有挂科的，不在里面就是一门都没挂）
     */
    private int releaseRecoveredWarnings(List<FailCountRule> rules,
                                         Map<Long, Integer> currentFailCounts,
                                         boolean verbose) {
        Map<Long, FailCountRule> ruleById = new HashMap<>();
        for (FailCountRule rule : rules) {
            ruleById.put(rule.rule().getId(), rule);
        }

        int released = 0;
        for (Map<String, Object> row : academicWarningMapper.selectPendingWarnings()) {
            if (row == null) {
                continue;
            }
            Long id = toLong(row.get("id"));
            Long studentId = toLong(row.get("studentId"));
            Long ruleId = toLong(row.get("ruleId"));
            if (id == null || studentId == null || ruleId == null) {
                continue;
            }
            FailCountRule rule = ruleById.get(ruleId);
            if (rule == null) {
                if (verbose) {
                    log.debug("预警 {} 的规则 {} 不在可判定集合内，自动解除跳过", id, ruleId);
                }
                continue;
            }
            int current = currentFailCounts.getOrDefault(studentId, 0);
            if (rule.range().contains(current)) {
                continue;
            }

            String note = String.format(
                    "重新统计后该生当前挂科 %d 门，已不满足规则「%s」（%s）的条件，系统自动解除。",
                    current, rule.rule().getRuleName(), rule.rule().getExpression());
            if (academicWarningMapper.releaseWarning(id, note, AUTO_HANDLER) == 0) {
                continue;
            }
            warningReleaseRecordMapper.insertRelease(
                    id, studentId, RELEASE_REASON_AUTO, note, null);

            AcademicWarning releasedWarning = new AcademicWarning()
                    .setId(id)
                    .setStudentId(studentId)
                    .setTitle(row.get("title") == null ? "学业预警" : String.valueOf(row.get("title")))
                    .setWarningLevel(toInt(row.get("warningLevel")));
            warningNotifier.notifyReleased(releasedWarning,
                    String.format("当前挂科 %d 门，已不满足预警条件", current));
            released++;
            log.info("自动解除预警 {}（学生 {}，当前挂科 {} 门，规则「{}」）",
                    id, studentId, current, rule.rule().getRuleName());
        }
        return released;
    }

    /**
     * 加载规则，只保留能判定挂科门数的那些。
     * 无法解析的规则记日志后跳过 —— 不抛异常，也不静默忽略。
     */
    private List<FailCountRule> loadRules(boolean verbose) {
        List<WarningRule> activeRules = warningRuleMapper.selectList(
                new QueryWrapper<WarningRule>().eq("status", 1)
        );

        List<FailCountRule> usable = new ArrayList<>();
        for (WarningRule rule : activeRules) {
            if (rule.getWarningLevel() == null) {
                log.warn("规则「{}」未配置预警等级，已跳过", rule.getRuleName());
                continue;
            }
            RuleConditionParser.Range range =
                    RuleConditionParser.parseFailCountRange(rule.getExpression());
            if (range == null) {
                if (RuleConditionParser.mentionsFailCount(rule.getExpression())) {
                    // 挂了 fail_count 却解析不出来 —— 规则配错了，必须让人看见
                    log.warn("规则「{}」的挂科数条件无法解析，已跳过：{}",
                            rule.getRuleName(), rule.getExpression());
                } else if (verbose) {
                    // 如 GPA 规则：本身合法，只是不属于"挂科门数"这个指标
                    log.info("规则「{}」不是挂科数规则，本次扫描不参与：{}",
                            rule.getRuleName(), rule.getExpression());
                }
                continue;
            }
            usable.add(new FailCountRule(rule, range));
        }

        usable.sort(Comparator.comparingInt(r -> r.rule().getWarningLevel()));
        if (verbose) {
            log.info("加载到 {} 条启用的预警规则，其中 {} 条可用于挂科门数判定",
                    activeRules.size(), usable.size());
        }
        return usable;
    }

    /** 区间命中时取等级最高的规则；命中多条（区间重叠）时高等级优先。 */
    private FailCountRule pickRule(List<FailCountRule> rules, int failCount) {
        FailCountRule best = null;
        for (FailCountRule candidate : rules) {
            if (!candidate.range().contains(failCount)) {
                continue;
            }
            if (best == null
                    || candidate.rule().getWarningLevel() > best.rule().getWarningLevel()) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 插入一条预警，并立刻通知学生与其辅导员。
     *
     * <p>通知放在插入之后：只有真的落库了、拿到 id 的预警才通知得出去，
     * 也保证"本轮新生成"与"本轮通知"是同一批（不会给历史预警补发通知）。
     */
    private void createWarning(Long studentId, String studentName, FailCountRule rule,
                               int failCount, List<Map<String, Object>> detail) {
        // detail 已按分数升序，首条即最低分，用作 triggerScore
        BigDecimal triggerScore = null;
        List<String> courseTexts = new ArrayList<>();
        int shown = Math.min(detail.size(), MAX_COURSE_DETAIL);
        for (int i = 0; i < shown; i++) {
            Map<String, Object> course = detail.get(i);
            Double score = ScoreCaliber.toDouble(course.get("scoreTotal"));
            if (i == 0 && score != null) {
                triggerScore = BigDecimal.valueOf(score);
            }
            Object courseName = course.get("courseName");
            courseTexts.add(String.format("%s %s分",
                    courseName != null ? courseName : "未知课程", formatScore(score)));
        }
        String courses = String.join("、", courseTexts)
                + (detail.size() > shown ? " 等" : "");

        String message = String.format(
                "%s同学已累计%d门课程不及格（%s），触发预警规则「%s」，系统自动生成预警。",
                studentName, failCount, courses, rule.rule().getRuleName());

        AcademicWarning warning = new AcademicWarning()
                .setStudentId(studentId)
                .setStudentName(studentName)
                .setRuleId(rule.rule().getId())
                .setType(1)
                .setTitle(rule.rule().getRuleName())
                // title 是短标签，description/warningMessage 存正文。
                // 两个正文列都写，避免下游只读其中一个时拿到空值。
                .setDescription(message)
                .setWarningLevel(rule.rule().getWarningLevel())
                .setWarningMessage(message)
                .setTriggerScore(triggerScore)
                // 记下生成时的门数：这是判重键的一半（见 runScan 的注释）。
                // 不写这一列的话，下一轮扫描就认不出"这条预警说的是同一个问题"。
                .setFailCount(failCount)
                .setStatus(0)
                .setCreatedAt(LocalDateTime.now());
        academicWarningMapper.insert(warning);

        log.info("生成预警：学生 {}（{}）挂科 {} 门，命中规则「{}」，等级 {}",
                studentName, studentId, failCount, rule.rule().getRuleName(),
                rule.rule().getWarningLevel());

        warningNotifier.notifyNewWarning(warning);
    }

    /** 40.0 → "40"，56.5 → "56.5"。 */
    private static String formatScore(Double score) {
        if (score == null) {
            return ScoreCaliber.PENDING_TEXT;
        }
        if (score == Math.floor(score) && !score.isInfinite()) {
            return String.valueOf(score.intValue());
        }
        return String.valueOf(score);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        Double parsed = ScoreCaliber.toDouble(value);
        return parsed == null ? null : parsed.longValue();
    }

    private static Integer toInt(Object value) {
        Double parsed = ScoreCaliber.toDouble(value);
        return parsed == null ? null : parsed.intValue();
    }

    /** 一条可用于挂科门数判定的规则：原始规则 + 解析出的区间。 */
    private record FailCountRule(WarningRule rule, RuleConditionParser.Range range) {
    }
}
