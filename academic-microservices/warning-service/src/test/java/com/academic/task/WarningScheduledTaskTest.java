package com.academic.task;

import com.academic.common.entity.AcademicWarning;
import com.academic.entity.WarningRule;
import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.ScoreReadMapper;
import com.academic.mapper.WarningRuleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 预警定时任务测试。
 *
 * <p>重点回归三类历史缺陷：
 * <ul>
 *   <li>等级硬编码 —— 旧代码写死"1 门 → 2 级、≥2 门 → 3 级"，与规则表冲突，
 *       1 级预警永远不会生成；</li>
 *   <li>同一名学生重复插入 —— 旧代码的 existingWarningStudents 集合从不回写，
 *       一名学生命中多个扫描分支会插入多条；</li>
 *   <li>规则解析错误 —— 旧 parseThreshold() 把门数区间当成分数阈值。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WarningScheduledTaskTest {

    @Mock
    private ScoreReadMapper scoreReadMapper;

    @Mock
    private AcademicWarningMapper academicWarningMapper;

    @Mock
    private WarningRuleMapper warningRuleMapper;

    @InjectMocks
    private WarningScheduledTask scheduledTask;

    // ------------------------------------------------------------------
    // 等级来源
    // ------------------------------------------------------------------

    @Test
    @DisplayName("预警等级完全取自规则表 —— 旧代码在此处硬编码成 2")
    void testScan_WarningLevelComesFromRuleTable() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5), detail(3L, "计算机基础", 47.0)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        AcademicWarning warning = captureSingleInsert();
        assertEquals(1, warning.getWarningLevel());          // 规则表配的是 1 级
        assertEquals(1L, warning.getRuleId());
        assertEquals(13L, warning.getStudentId());
        assertEquals("韩坤", warning.getStudentName());
        assertEquals(0, warning.getStatus());
        assertNotNull(warning.getCreatedAt());
    }

    @Test
    @DisplayName("同一名学生命中多条重叠规则时只生成一条，且取等级最高者")
    void testScan_OverlappingRulesProduceOnlyOneWarning() {
        // 旧实现的三段扫描会让这名学生被插入多次
        stubRules(
                rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"),
                rule(2L, "中级预警", 2, "fail_count >= 1 AND fail_count <= 5"),
                rule(3L, "高级预警", 3, "fail_count > 5"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(3L, "司明", 3)));
        when(scoreReadMapper.findFailDetailByStudent(3L))
                .thenReturn(List.of(detail(6L, "操作系统", 40.0)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        verify(academicWarningMapper, times(1)).insert(any());
        assertEquals(2, captureSingleInsert().getWarningLevel());   // [1,5] 中级
    }

    // ------------------------------------------------------------------
    // 去重 / 幂等
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已有未处理预警的学生被跳过，其余学生照常生成")
    void testScan_SkipsStudentWithPendingWarning() {
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        when(scoreReadMapper.findExistingWarningStudentIds()).thenReturn(List.of(3L));
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(
                failRow(3L, "司明", 3),
                failRow(20L, "李爽", 3)));
        when(scoreReadMapper.findFailDetailByStudent(20L))
                .thenReturn(List.of(detail(3L, "计算机基础", 47.0)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        assertEquals(20L, captureSingleInsert().getStudentId());
        // 被跳过的学生不应浪费一次明细查询
        verify(scoreReadMapper, never()).findFailDetailByStudent(3L);
    }

    @Test
    @DisplayName("同一轮扫描内不会对同一名学生重复生成（pending 集合即时回写）")
    void testScan_DoesNotDuplicateWithinOneRun() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoPendingWarnings();
        // 聚合查询若因数据异常返回了重复行，也只应生成一条
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(
                failRow(13L, "韩坤", 2),
                failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        verify(academicWarningMapper, times(1)).insert(any());
    }

    // ------------------------------------------------------------------
    // 规则解析与容错
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GPA 规则被优雅跳过：不抛异常，也不影响挂科数规则生效")
    void testScan_GpaRuleSkippedGracefully() {
        // 线上第 4 条启用规则就是 gpa < 2.0
        stubRules(
                rule(4L, "GPA预警", 2, "gpa < 2.0"),
                rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        assertEquals(1L, captureSingleInsert().getRuleId());   // 命中的是初级预警
    }

    @Test
    @DisplayName("只有 GPA 规则时一条都不生成，且不报错")
    void testScan_OnlyGpaRuleConfigured() {
        stubRules(rule(4L, "GPA预警", 2, "gpa < 2.0"));
        // 规则全部不可用 → 直接返回，连待处理学生列表和聚合查询都不该发出，
        // 所以这里刻意不 stub 那两个查询
        int generated = scheduledTask.triggerNow();

        assertEquals(0, generated);
        verify(academicWarningMapper, never()).insert(any());
        verify(scoreReadMapper, never()).findFailCountByStudent();
    }

    @Test
    @DisplayName("挂科数条件自相矛盾的规则被跳过，不误伤其它规则")
    void testScan_MalformedFailCountRuleSkipped() {
        stubRules(
                rule(9L, "坏规则", 3, "fail_count >= 5 AND fail_count <= 2"),
                rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        scheduledTask.triggerNow();

        assertEquals(1L, captureSingleInsert().getRuleId());
    }

    @Test
    @DisplayName("规则未配置等级时跳过，而不是插入 warning_level 为 NULL 的行")
    void testScan_RuleWithoutLevelSkipped() {
        WarningRule noLevel = rule(8L, "无等级规则", null, "fail_count >= 1 AND fail_count <= 2");
        stubRules(noLevel);

        assertEquals(0, scheduledTask.triggerNow());
        verify(academicWarningMapper, never()).insert(any());
    }

    @Test
    @DisplayName("挂科门数落在所有规则区间之外时不生成预警")
    void testScan_FailCountMatchesNoRule() {
        stubRules(rule(3L, "高级预警", 3, "fail_count > 5"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(13L, "韩坤", 2)));

        assertEquals(0, scheduledTask.triggerNow());
        verify(academicWarningMapper, never()).insert(any());
    }

    @Test
    @DisplayName("规则加载抛异常时被兜住，不影响调度线程")
    void testScan_ExceptionIsCaught() {
        when(warningRuleMapper.selectList(any()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        assertDoesNotThrow(() -> scheduledTask.scanAndGenerateWarnings());
    }

    // ------------------------------------------------------------------
    // 预警内容
    // ------------------------------------------------------------------

    @Test
    @DisplayName("triggerScore 取最低挂科分数，预警文案包含规则名与课程明细")
    void testScan_WarningContent() {
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(3L, "司明", 3)));
        // mapper 保证按分数升序返回，首条即最低分
        when(scoreReadMapper.findFailDetailByStudent(3L)).thenReturn(List.of(
                detail(6L, "操作系统", 40.0),
                detail(3L, "计算机基础", 47.0),
                detail(1L, "高等数学", 56.5)));

        scheduledTask.triggerNow();

        AcademicWarning warning = captureSingleInsert();
        assertEquals(0, new BigDecimal("40").compareTo(warning.getTriggerScore()));
        assertEquals("中级预警", warning.getTitle());
        assertTrue(warning.getWarningMessage().contains("司明"));
        assertTrue(warning.getWarningMessage().contains("3门"));
        assertTrue(warning.getWarningMessage().contains("操作系统"));
        assertTrue(warning.getWarningMessage().contains("高等数学"));
        assertTrue(warning.getWarningMessage().contains("中级预警"));
    }

    @Test
    @DisplayName("挂科课程过多时预警文案省略为\"等\"，不会无限长")
    void testScan_WarningContentTruncatesLongCourseList() {
        stubRules(rule(3L, "高级预警", 3, "fail_count > 5"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(99L, "王五", 7)));
        when(scoreReadMapper.findFailDetailByStudent(99L)).thenReturn(List.of(
                detail(1L, "课程1", 11.0), detail(2L, "课程2", 12.0), detail(3L, "课程3", 13.0),
                detail(4L, "课程4", 14.0), detail(5L, "课程5", 15.0), detail(6L, "课程6", 16.0),
                detail(7L, "课程7", 17.0)));

        scheduledTask.triggerNow();

        String message = captureSingleInsert().getWarningMessage();
        assertTrue(message.contains("课程5"));
        assertFalse(message.contains("课程6"));
        assertTrue(message.contains("等"));
    }

    @Test
    @DisplayName("缺少 studentId 的脏数据行被跳过，不中断整轮扫描")
    void testScan_SkipsRowWithNullStudentId() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoPendingWarnings();
        Map<String, Object> dirty = new HashMap<>();
        dirty.put("studentName", "无ID");
        dirty.put("failCount", 2);
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(
                dirty, failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        assertEquals(13L, captureSingleInsert().getStudentId());
    }

    // ------------------------------------------------------------------
    // 增量扫描
    // ------------------------------------------------------------------

    @Test
    @DisplayName("增量扫描按同一口径为新挂科学生生成预警")
    void testIncrementalScan_GeneratesForNewStudent() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoPendingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        assertDoesNotThrow(() -> scheduledTask.incrementalScan());

        assertEquals(1, captureSingleInsert().getWarningLevel());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void stubRules(WarningRule... rules) {
        when(warningRuleMapper.selectList(any())).thenReturn(List.of(rules));
    }

    private void stubNoPendingWarnings() {
        when(scoreReadMapper.findExistingWarningStudentIds()).thenReturn(List.of());
    }

    private AcademicWarning captureSingleInsert() {
        ArgumentCaptor<AcademicWarning> captor = ArgumentCaptor.forClass(AcademicWarning.class);
        verify(academicWarningMapper, times(1)).insert(captor.capture());
        return captor.getValue();
    }

    private static WarningRule rule(Long id, String name, Integer level, String condition) {
        WarningRule rule = new WarningRule();
        rule.setId(id);
        rule.setRuleName(name);
        rule.setWarningLevel(level);
        rule.setExpression(condition);
        rule.setDescription(name + "的规则说明");
        return rule;
    }

    private static Map<String, Object> failRow(long studentId, String studentName, int failCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("studentId", studentId);
        row.put("studentName", studentName);
        row.put("failCount", failCount);
        return row;
    }

    private static Map<String, Object> detail(long courseId, String courseName, double scoreTotal) {
        Map<String, Object> row = new HashMap<>();
        row.put("courseId", courseId);
        row.put("courseName", courseName);
        row.put("scoreTotal", scoreTotal);
        row.put("semester", 5);
        return row;
    }
}
