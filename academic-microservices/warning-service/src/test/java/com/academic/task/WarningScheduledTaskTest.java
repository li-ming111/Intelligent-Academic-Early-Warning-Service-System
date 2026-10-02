package com.academic.task;

import com.academic.common.entity.AcademicWarning;
import com.academic.entity.WarningRule;
import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.ScoreReadMapper;
import com.academic.mapper.WarningReleaseRecordMapper;
import com.academic.mapper.WarningRuleMapper;
import com.academic.notify.WarningNotifier;
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
 *   <li>已处理的预警被重扫复活 —— 旧判重只看 status=0，处理完就等于解除屏蔽，
 *       下一轮又插一条；2026-09-30 改为按「学生 + 挂科门数」判重（不看 status），
 *       见 {@code testScan_HandledWarningIsNotRegenerated} 与
 *       {@code testScan_RegeneratesWhenFailCountChanges}。</li>
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

    @Mock
    private WarningReleaseRecordMapper warningReleaseRecordMapper;

    @Mock
    private WarningNotifier warningNotifier;

    @InjectMocks
    private WarningScheduledTask scheduledTask;

    // ------------------------------------------------------------------
    // 等级来源
    // ------------------------------------------------------------------

    @Test
    @DisplayName("预警等级完全取自规则表 —— 旧代码在此处硬编码成 2")
    void testScan_WarningLevelComesFromRuleTable() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoExistingWarnings();
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
        assertEquals(2, warning.getFailCount());   // 判重键的一半，必须落库
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
        stubNoExistingWarnings();
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
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of(existingKey(3L, 3, 0)));
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
    @DisplayName("已处理的预警不会复活：门数没变就跳过（旧口径在这里会再插一条）")
    void testScan_HandledWarningIsNotRegenerated() {
        // 旧口径是 SELECT DISTINCT student_id ... WHERE status = 0，
        // 于是这条 status=1 的行等于不存在 —— 只要该生门数仍 > 0 就又插一条，
        // 页面上同一个人同时挂着"已处理"和"待处理"，半小时复活一次。
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of(existingKey(3L, 3, 1)));
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 3)));

        int generated = scheduledTask.triggerNow();

        assertEquals(0, generated);
        verify(academicWarningMapper, never()).insert(any());
        verify(scoreReadMapper, never()).findFailDetailByStudent(3L);
    }

    @Test
    @DisplayName("挂科门数变了就照常再报一次（3 门 → 5 门，问题变严重）")
    void testScan_RegeneratesWhenFailCountChanges() {
        stubRules(
                rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"),
                rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        // 既有那条是 3 门（哪怕已处理），今天变成 5 门 → 键不同 → 应当再报
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of(existingKey(3L, 3, 1)));
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 5)));
        when(scoreReadMapper.findFailDetailByStudent(3L))
                .thenReturn(List.of(detail(6L, "操作系统", 40.0)));

        int generated = scheduledTask.triggerNow();

        assertEquals(1, generated);
        AcademicWarning warning = captureSingleInsert();
        assertEquals(3L, warning.getStudentId());
        assertEquals(2, warning.getWarningLevel());   // [3,5] 中级
        assertEquals(5, warning.getFailCount());      // 新门数必须落库，否则下一轮又会重复
    }

    @Test
    @DisplayName("加列前的历史行没有门数时按旧口径兜底：未处理就仍然跳过")
    void testScan_LegacyPendingRowWithoutFailCountStillSkips() {
        // fail_count 为 NULL 说明这是迁移回填不到的行（正文里没有"已累计N门"），
        // 比不出来就只能沿用旧口径 —— 保守，不会因此多报。
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of(existingKey(3L, null, 0)));
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 3)));

        assertEquals(0, scheduledTask.triggerNow());
        verify(academicWarningMapper, never()).insert(any());
    }

    @Test
    @DisplayName("历史行没门数且已处理：无从判定是否同一问题，不拦截（自动迁移已把 4 行全部回填）")
    void testScan_LegacyHandledRowWithoutFailCountDoesNotBlock() {
        // 这是回填失败的残留口径，写下来是为了别让人以为兜底覆盖了这一格：
        // 旧口径本来就不拦 status=1，新口径在没有门数时也只能照旧。
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of(existingKey(3L, null, 1)));
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 3)));
        when(scoreReadMapper.findFailDetailByStudent(3L))
                .thenReturn(List.of(detail(6L, "操作系统", 40.0)));

        assertEquals(1, scheduledTask.triggerNow());
        assertEquals(3, captureSingleInsert().getFailCount());
    }

    @Test
    @DisplayName("同一轮扫描内不会对同一名学生重复生成（pending 集合即时回写）")
    void testScan_DoesNotDuplicateWithinOneRun() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
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
        stubNoExistingWarnings();
        when(scoreReadMapper.findFailCountByStudent())
                .thenReturn(List.of(failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        assertDoesNotThrow(() -> scheduledTask.incrementalScan());

        assertEquals(1, captureSingleInsert().getWarningLevel());
    }

    // ------------------------------------------------------------------
    // 自动解除（W1.6）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已恢复：待处理预警的门数掉出规则区间 → 置为已解除并写解除记录、通知学生")
    void testRelease_RecoveredWarningIsReleased() {
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        stubNoExistingWarnings();
        // 该生当初挂了 3 门（预警里记的就是 3），现在只剩 1 门 —— 不再满足 [3,5]
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 1)));
        when(academicWarningMapper.selectPendingWarnings())
                .thenReturn(List.of(pendingWarning(395L, 3L, 2L, 3, "中级预警", 2)));
        when(academicWarningMapper.releaseWarning(eq(395L), anyString(), anyString())).thenReturn(1);

        int generated = scheduledTask.triggerNow();

        assertEquals(0, generated);   // 门数 1 落在所有规则区间之外，本轮也不生成新的
        verify(academicWarningMapper).releaseWarning(
                eq(395L), anyString(), eq(WarningScheduledTask.AUTO_HANDLER));
        verify(warningReleaseRecordMapper).insertRelease(
                eq(395L), eq(3L), eq(WarningScheduledTask.RELEASE_REASON_AUTO), anyString(), isNull());
        verify(warningNotifier).notifyReleased(any(), anyString());
    }

    @Test
    @DisplayName("问题仍在：门数还落在规则区间内就不解除")
    void testRelease_StillFailingIsNotReleased() {
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        stubNoExistingWarnings();
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 3)));
        when(academicWarningMapper.selectPendingWarnings())
                .thenReturn(List.of(pendingWarning(395L, 3L, 2L, 3, "中级预警", 2)));
        when(scoreReadMapper.findFailDetailByStudent(3L))
                .thenReturn(List.of(detail(6L, "操作系统", 40.0)));

        scheduledTask.triggerNow();

        verify(academicWarningMapper, never()).releaseWarning(any(), anyString(), anyString());
        verify(warningReleaseRecordMapper, never()).insertRelease(any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("规则无法判定门数（GPA 规则）时不解除 —— 宁可留着，也不凭猜测抹掉")
    void testRelease_UnevaluableRuleIsLeftAlone() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        stubNoExistingWarnings();
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 2)));
        // 该预警挂在 GPA 规则（id=4）上，解析不出门数区间
        when(academicWarningMapper.selectPendingWarnings())
                .thenReturn(List.of(pendingWarning(400L, 3L, 4L, null, "GPA预警", 2)));
        when(scoreReadMapper.findFailDetailByStudent(3L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        scheduledTask.triggerNow();

        verify(academicWarningMapper, never()).releaseWarning(any(), anyString(), anyString());
        verify(warningNotifier, never()).notifyReleased(any(), anyString());
    }

    @Test
    @DisplayName("并发下已被别人改掉（影响行数 0）时不写解除记录，避免留下脏记录")
    void testRelease_ConcurrentUpdateWritesNoRecord() {
        stubRules(rule(2L, "中级预警", 2, "fail_count >= 3 AND fail_count <= 5"));
        stubNoExistingWarnings();
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(failRow(3L, "司明", 1)));
        when(academicWarningMapper.selectPendingWarnings())
                .thenReturn(List.of(pendingWarning(395L, 3L, 2L, 3, "中级预警", 2)));
        when(academicWarningMapper.releaseWarning(eq(395L), anyString(), anyString())).thenReturn(0);

        scheduledTask.triggerNow();

        verify(warningReleaseRecordMapper, never()).insertRelease(any(), any(), anyString(), anyString(), any());
        verify(warningNotifier, never()).notifyReleased(any(), anyString());
    }

    // ------------------------------------------------------------------
    // 生成后通知（W1.2）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新生成的预警会通知学生与辅导员；被跳过的历史预警不补发通知")
    void testScan_NewWarningTriggersNotification() {
        stubRules(rule(1L, "初级预警", 1, "fail_count >= 1 AND fail_count <= 2"));
        // 学生 3 已有同门数的历史预警 → 本轮跳过、不生成、也就不通知
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of(existingKey(3L, 2, 0)));
        when(scoreReadMapper.findFailCountByStudent()).thenReturn(List.of(
                failRow(3L, "司明", 2),
                failRow(13L, "韩坤", 2)));
        when(scoreReadMapper.findFailDetailByStudent(13L))
                .thenReturn(List.of(detail(1L, "高等数学", 56.5)));

        assertEquals(1, scheduledTask.triggerNow());

        ArgumentCaptor<AcademicWarning> captor = ArgumentCaptor.forClass(AcademicWarning.class);
        verify(warningNotifier, times(1)).notifyNewWarning(captor.capture());
        assertEquals(13L, captor.getValue().getStudentId());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 一条待处理预警的判定行（{@code selectPendingWarnings} 的返回形状）。 */
    private static Map<String, Object> pendingWarning(long id, long studentId, long ruleId,
                                                     Integer failCount, String title, int level) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("studentId", studentId);
        row.put("ruleId", ruleId);
        row.put("failCount", failCount);
        row.put("title", title);
        row.put("warningLevel", level);
        return row;
    }

    private void stubRules(WarningRule... rules) {
        when(warningRuleMapper.selectList(any())).thenReturn(List.of(rules));
    }

    /** 库里一条预警都没有：既不构成判重键，也没有可兜底的历史行。 */
    private void stubNoExistingWarnings() {
        when(scoreReadMapper.findExistingWarningKeys()).thenReturn(List.of());
    }

    /** 构造一条既有预警的判重键：{@code failCount} 允许为 null（加列前的历史行）。 */
    private static Map<String, Object> existingKey(long studentId, Integer failCount, Integer status) {
        Map<String, Object> key = new HashMap<>();
        key.put("studentId", studentId);
        key.put("failCount", failCount);
        key.put("status", status);
        return key;
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
