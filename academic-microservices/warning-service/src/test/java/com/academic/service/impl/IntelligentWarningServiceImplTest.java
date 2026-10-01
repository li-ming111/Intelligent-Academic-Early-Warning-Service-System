package com.academic.service.impl;

import com.academic.common.entity.AcademicWarning;
import com.academic.entity.WarningRule;
import com.academic.exception.WarningNotFoundException;
import com.academic.mapper.AcademicWarningMapper;
import com.academic.mapper.WarningRuleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 手工生成 / 处理 / 改状态这条链的回归钉子。
 *
 * <p>钉的是两类历史缺陷（2026-09-30 读码 + 活库核对确认）：
 * <ol>
 *   <li><b>写了半截的预警行。</b>{@code /warnings/generate} 只写
 *       {@code student_id / student_name / rule_id / warning_level / warning_message /
 *       status / created_at}，而 {@code description}（辅导员预警列表的「预警原因」列、
 *       {@code CollegeAdminController} 的预警列表、{@code AdminController} 的 Excel 导出
 *       都读它）、{@code title}、{@code type} 一个都没写。
 *       （⚠️ 原稿这里还列了 {@code updated_at}，2026-09-30 自查**撤回**：活库 DDL 是
 *       {@code TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP}，
 *       INSERT 时 DB 就填上了、之后每次真 UPDATE 也会自动刷新 —— 它从来没空过。
 *       半截行是三列不是四列。见手册 §6.9 A。）
 *       结果是"行在，原因空白" —— 而活库那 4 条（全由定时任务写的）这几个字段**全都有值**，
 *       同一个表里两种成色的数据。</li>
 *   <li><b>失败被包成成功。</b>"预警不存在"原来写成返回值里的
 *       {@code {status:"error"}}，controller 却无条件 {@code ApiResponse.success(...)}；
 *       改状态时 {@code status} 为空则**一个字段都不改**却照样回"更新成功"。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class IntelligentWarningServiceImplTest {

    @Mock
    private AcademicWarningMapper academicWarningMapper;

    @Mock
    private WarningRuleMapper warningRuleMapper;

    @InjectMocks
    private IntelligentWarningServiceImpl service;

    // ------------------------------------------------------------------
    // generateWarning：这条行必须写全
    // ------------------------------------------------------------------

    @Test
    @DisplayName("手工生成：description/title/type 都要落库（下游真读 description）")
    void testGenerateWarning_WritesEveryColumnDownstreamReads() {
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("studentName", "司明");

        service.generateWarning(request);

        AcademicWarning saved = captureInsert();
        assertEquals(13L, saved.getStudentId());
        assertEquals("司明", saved.getStudentName());
        // 这一列是辅导员页面「预警原因」的数据源，原来恒为 NULL
        assertNotNull(saved.getDescription(), "description 不能为空：辅导员列表的「预警原因」读它");
        assertEquals("学生学业状况需要关注", saved.getDescription());
        assertNotNull(saved.getTitle(), "title 不能为空：定时任务写规则名，手工路径也得写");
        assertNotNull(saved.getType(), "type 不能为空：定时任务路径写 1");
        assertNotNull(saved.getUpdatedAt(), "插入时应显式写上 updated_at（DB 的 DEFAULT 本来也会填）");
        assertEquals(0, saved.getStatus(), "新生成的预警应是未处理(0)");
    }

    @Test
    @DisplayName("手工生成：没有规则时 title 用等级名兜底（与活库 warning_rules.name 同一套词）")
    void testGenerateWarning_TitleFallsBackToLevelName() {
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("warningLevel", 3);

        service.generateWarning(request);

        AcademicWarning saved = captureInsert();
        assertEquals("高级预警", saved.getTitle());
        assertEquals(3, saved.getWarningLevel());
    }

    @Test
    @DisplayName("手工生成：等级没给时默认 2（warning_level 是 NOT NULL，不能留空）")
    void testGenerateWarning_DefaultLevelIsTwo() {
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);

        service.generateWarning(request);

        assertEquals(2, captureInsert().getWarningLevel());
    }

    @Test
    @DisplayName("手工生成：给了规则就用规则的等级与规则名")
    void testGenerateWarning_UsesRuleLevelAndName() {
        when(warningRuleMapper.selectById(2L))
                .thenReturn(rule(2L, "中级预警", 2, "学生本学期不及格课程门数为3-5门（含3门和5门）"));
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("ruleId", 2L);
        request.put("warningLevel", 1);   // 规则优先，这个应被忽略

        service.generateWarning(request);

        AcademicWarning saved = captureInsert();
        assertEquals(2, saved.getWarningLevel());
        assertEquals("中级预警", saved.getTitle());
        assertEquals(2L, saved.getRuleId());
    }

    @Test
    @DisplayName("手工生成：调用方给的正文优先，规则的 description 只作兜底（原来是被无条件覆盖）")
    void testGenerateWarning_CallerMessageIsNotOverwrittenByRuleDescription() {
        when(warningRuleMapper.selectById(2L))
                .thenReturn(rule(2L, "中级预警", 2, "规则自己的说明文字"));
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("ruleId", 2L);
        request.put("warningMessage", "辅导员手工记录的：连续两周缺勤且高数不及格");

        service.generateWarning(request);

        AcademicWarning saved = captureInsert();
        assertEquals("辅导员手工记录的：连续两周缺勤且高数不及格", saved.getDescription());
        assertEquals(saved.getDescription(), saved.getWarningMessage(),
                "description 与 warning_message 要一致（下游读的不是同一个）");
    }

    @Test
    @DisplayName("手工生成：调用方没给正文时，用规则的 description 兜底")
    void testGenerateWarning_FallsBackToRuleDescription() {
        when(warningRuleMapper.selectById(2L))
                .thenReturn(rule(2L, "中级预警", 2, "规则自己的说明文字"));
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("ruleId", 2L);

        service.generateWarning(request);

        assertEquals("规则自己的说明文字", captureInsert().getDescription());
    }

    @Test
    @DisplayName("手工生成：规则描述也是 NULL 时，正文必须落到兜底文案，不能是空白")
    void testGenerateWarning_NullRuleDescriptionDoesNotYieldBlankBody() {
        when(warningRuleMapper.selectById(2L)).thenReturn(rule(2L, "中级预警", 2, null));
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("ruleId", 2L);

        service.generateWarning(request);

        AcademicWarning saved = captureInsert();
        assertNotNull(saved.getDescription());
        assertFalse(saved.getDescription().trim().isEmpty(), "正文不能是空白字符串");
    }

    @Test
    @DisplayName("手工生成：fail_count 有意留空（口径来自成绩表，硬编会污染扫描判重）")
    void testGenerateWarning_LeavesFailCountNull() {
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);

        service.generateWarning(request);

        assertNull(captureInsert().getFailCount());
    }

    @Test
    @DisplayName("手工生成：缺 studentId 直接报错（student_id 是 NOT NULL，别让它冒成 SQL 500）")
    void testGenerateWarning_MissingStudentIdIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.generateWarning(new HashMap<>()));
        assertTrue(e.getMessage().contains("studentId"), "错误信息要点名缺的是哪个字段: " + e.getMessage());
        verify(academicWarningMapper, never()).insert(any());
    }

    @Test
    @DisplayName("手工生成：ruleId 指向不存在的规则要报错，不能静默按无规则生成")
    void testGenerateWarning_UnknownRuleIsRejectedNotIgnored() {
        when(warningRuleMapper.selectById(99L)).thenReturn(null);
        Map<String, Object> request = new HashMap<>();
        request.put("studentId", 13L);
        request.put("ruleId", 99L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.generateWarning(request));
        assertTrue(e.getMessage().contains("99"), e.getMessage());
        verify(academicWarningMapper, never()).insert(any());
    }

    // ------------------------------------------------------------------
    // handleWarning / getWarningDetail / updateWarningStatus：失败必须抛，不能藏在返回值里
    // ------------------------------------------------------------------

    @Test
    @DisplayName("处理预警：预警不存在要抛 WarningNotFoundException（原来是返回 status=error 被包成成功）")
    void testHandleWarning_UnknownIdThrowsNotFound() {
        assertThrows(WarningNotFoundException.class,
                () -> service.handleWarning(Map.of("warningId", 999L)));
        verify(academicWarningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("处理预警：缺 warningId 要报错")
    void testHandleWarning_MissingIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.handleWarning(new HashMap<>()));
    }

    @Test
    @DisplayName("处理预警：warningId 传字符串数字也要认（前端 int / string 两种都可能）")
    void testHandleWarning_AcceptsStringWarningId() {
        when(academicWarningMapper.selectById(9L)).thenReturn(existingWarning(9L));

        service.handleWarning(Map.of("warningId", "9", "handledBy", "李辅导员", "handleResult", "已约谈"));

        AcademicWarning saved = captureUpdate();
        assertEquals(1, saved.getStatus());
        assertEquals("李辅导员", saved.getHandledBy());
        assertEquals("已约谈", saved.getHandleResult());
    }

    @Test
    @DisplayName("处理预警：处理时间与 updated_at 都要写")
    void testHandleWarning_StampsHandledAtAndUpdatedAt() {
        when(academicWarningMapper.selectById(9L)).thenReturn(existingWarning(9L));

        service.handleWarning(Map.of("warningId", 9));

        AcademicWarning saved = captureUpdate();
        assertNotNull(saved.getHandledAt());
        assertNotNull(saved.getUpdatedAt(), "更新路径应显式写上 updated_at（不依赖 DB 的 ON UPDATE）");
    }

    @Test
    @DisplayName("处理预警：handleResult 空白视为没填，落默认值（不是空串）")
    void testHandleWarning_BlankResultFallsBackToDefault() {
        when(academicWarningMapper.selectById(9L)).thenReturn(existingWarning(9L));

        service.handleWarning(Map.of("warningId", 9, "handleResult", "   "));

        assertEquals("已处理", captureUpdate().getHandleResult());
    }

    @Test
    @DisplayName("预警详情：不存在要抛 WarningNotFoundException")
    void testGetWarningDetail_UnknownIdThrowsNotFound() {
        assertThrows(WarningNotFoundException.class, () -> service.getWarningDetail("999"));
    }

    @Test
    @DisplayName("预警详情：ID 不是数字要给人话，而不是裸的 NumberFormatException")
    void testGetWarningDetail_NonNumericIdIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.getWarningDetail("abc"));
        assertTrue(e.getMessage().contains("abc"), e.getMessage());
    }

    @Test
    @DisplayName("改状态：预警不存在要抛 WarningNotFoundException")
    void testUpdateWarningStatus_UnknownIdThrowsNotFound() {
        assertThrows(WarningNotFoundException.class,
                () -> service.updateWarningStatus("999", Map.of("status", 1)));
        verify(academicWarningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("改状态：缺 status 要报错（原来一个字段都不改却回『更新成功』——假成功）")
    void testUpdateWarningStatus_MissingStatusIsRejectedNotSilentlySkipped() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateWarningStatus("9", new HashMap<>()));
        assertTrue(e.getMessage().contains("status"), e.getMessage());
        verify(academicWarningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("改状态：置为已处理时补 handledAt 与 updated_at")
    void testUpdateWarningStatus_StampsTimestampsWhenHandled() {
        when(academicWarningMapper.selectById(9L)).thenReturn(existingWarning(9L));

        service.updateWarningStatus("9", Map.of("status", 1));

        AcademicWarning saved = captureUpdate();
        assertEquals(1, saved.getStatus());
        assertNotNull(saved.getHandledAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("改状态：置为未处理不改写已有的 handledAt")
    void testUpdateWarningStatus_DoesNotOverwriteExistingHandledAt() {
        AcademicWarning handled = existingWarning(9L);
        handled.setStatus(1).setHandledAt(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        when(academicWarningMapper.selectById(9L)).thenReturn(handled);

        service.updateWarningStatus("9", Map.of("status", 0));

        AcademicWarning saved = captureUpdate();
        assertEquals(0, saved.getStatus());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 1, 10, 0), saved.getHandledAt(),
                "重新打开一条预警不该抹掉原来的处理时间");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private AcademicWarning captureInsert() {
        ArgumentCaptor<AcademicWarning> captor = ArgumentCaptor.forClass(AcademicWarning.class);
        verify(academicWarningMapper).insert(captor.capture());
        return captor.getValue();
    }

    private AcademicWarning captureUpdate() {
        ArgumentCaptor<AcademicWarning> captor = ArgumentCaptor.forClass(AcademicWarning.class);
        verify(academicWarningMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private static AcademicWarning existingWarning(Long id) {
        return new AcademicWarning()
                .setId(id)
                .setStudentId(13L)
                .setStudentName("司明")
                .setWarningLevel(2)
                .setStatus(0);
    }

    private static WarningRule rule(Long id, String name, int level, String description) {
        return new WarningRule()
                .setId(id)
                .setRuleName(name)
                .setWarningLevel(level)
                .setDescription(description);
    }
}
