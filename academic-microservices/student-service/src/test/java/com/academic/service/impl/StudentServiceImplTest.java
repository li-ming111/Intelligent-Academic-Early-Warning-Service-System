package com.academic.service.impl;

import com.academic.common.entity.Score;
import com.academic.entity.StudentProfile;
import com.academic.mapper.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 2026-10-02 第二十五轮：学生侧「更新帮扶进度」。
 *
 * <p>守三件事：① 进度必须在 0-100；② 进度顺带同步 status（0/100/其余 → 0/2/1）；
 * ③ 归属校验走 SQL（影响行数 0 ⇒ 抛错，而不是"回成功"）。
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock private StudentProfileMapper studentProfileMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private AcademicWarningMapper academicWarningMapper;
    @Mock private ScoreAppealMapper scoreAppealMapper;
    @Mock private NotificationMapper notificationMapper;
    @Mock private SubscriptionPreferenceMapper subscriptionPreferenceMapper;
    @Mock private AssistanceEvaluationMapper assistanceEvaluationMapper;
    @Mock private AssistancePlanMapper assistancePlanMapper;
    @Mock private UserSettingsMapper userSettingsMapper;

    @InjectMocks private StudentServiceImpl service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private void stubProfile(long userId, long profileId) {
        StudentProfile sp = new StudentProfile();
        sp.setId(profileId);
        when(studentProfileMapper.selectByUserId(userId)).thenReturn(sp);
    }

    @Test
    void updatePlanProgress_syncsStatus_midProgress() {
        loginAs("27");
        stubProfile(27L, 3L);
        when(assistancePlanMapper.updateProgress(1001L, 3L, 50, 1, "完成一半")).thenReturn(1);

        Object result = service.updatePlanProgress(1001L, Map.of("progress", 50, "note", "完成一半"));

        assertTrue(((Map<?, ?>) result).get("success").equals(true));
        assertEquals(1, ((Map<?, ?>) result).get("status"));
        verify(assistancePlanMapper).updateProgress(1001L, 3L, 50, 1, "完成一半");
    }

    @Test
    void updatePlanProgress_zeroIsPending_andHundredIsDone() {
        loginAs("27");
        stubProfile(27L, 3L);
        when(assistancePlanMapper.updateProgress(1001L, 3L, 0, 0, null)).thenReturn(1);
        when(assistancePlanMapper.updateProgress(1001L, 3L, 100, 2, null)).thenReturn(1);

        assertEquals(0, ((Map<?, ?>) service.updatePlanProgress(1001L, Map.of("progress", 0))).get("status"));
        assertEquals(2, ((Map<?, ?>) service.updatePlanProgress(1001L, Map.of("progress", 100))).get("status"));
    }

    @Test
    void updatePlanProgress_blankNoteBecomesNull() {
        loginAs("27");
        stubProfile(27L, 3L);
        when(assistancePlanMapper.updateProgress(1001L, 3L, 30, 1, null)).thenReturn(1);

        service.updatePlanProgress(1001L, Map.of("progress", 30, "note", "   "));

        verify(assistancePlanMapper).updateProgress(1001L, 3L, 30, 1, null);
    }

    @Test
    void updatePlanProgress_outOfRange_isRejected_withoutTouchingDb() {
        loginAs("27");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updatePlanProgress(1001L, Map.of("progress", 101)));

        assertTrue(ex.getMessage().contains("0-100"));
        verifyNoInteractions(assistancePlanMapper);
    }

    @Test
    void updatePlanProgress_missingProgress_isRejected() {
        loginAs("27");

        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlanProgress(1001L, Collections.emptyMap()));

        verifyNoInteractions(assistancePlanMapper);
    }

    @Test
    void updatePlanProgress_notOwnedByCaller_isRejected() {
        loginAs("27");
        stubProfile(27L, 3L);
        // SQL 的 WHERE student_id = 3 没命中 ⇒ 0 行
        when(assistancePlanMapper.updateProgress(1001L, 3L, 60, 1, null)).thenReturn(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updatePlanProgress(1001L, Map.of("progress", 60)));

        assertTrue(ex.getMessage().contains("不属于当前用户"));
    }

    @Test
    void updatePlanProgress_noAuthentication_isRejected() {
        SecurityContextHolder.clearContext();

        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlanProgress(1001L, Map.of("progress", 60)));

        verifyNoInteractions(assistancePlanMapper);
    }

    // ------------------------------------------------------------------
    // 隐私等级在排行榜上真正生效（privacy_level 此前全仓无人读）
    // ------------------------------------------------------------------

    /** 一行排行榜数据。privacyLevel 用 Object 传，好覆盖 Boolean（tinyint(1) 的默认映射）。 */
    private static Map<String, Object> row(long profileId, Object privacyLevel, double gpa) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("profileId", profileId);
        r.put("privacyLevel", privacyLevel);
        r.put("gpa", gpa);
        r.put("gradedCredits", 40);
        return r;
    }

    @Test
    void privacy_publicPeerIsVisible() {
        Map<String, Object> mine = row(3L, 0, 3.5);
        Map<String, Object> peer = row(4L, 0, 3.2);
        List<Map<String, Object>> list = new java.util.ArrayList<>(List.of(mine, peer));

        StudentServiceImpl.maskPeerPrivacy(list, 3L);

        assertEquals(3.2, peer.get("gpa"), "公开的同学，绩点应当看得见");
        assertEquals(false, peer.get("gpaHidden"));
    }

    @Test
    void privacy_privatePeerIsMaskedButRowKept() {
        Map<String, Object> mine = row(3L, 0, 3.5);
        Map<String, Object> peer = row(4L, 2, 3.2);
        List<Map<String, Object>> list = new java.util.ArrayList<>(List.of(mine, peer));

        StudentServiceImpl.maskPeerPrivacy(list, 3L);

        assertNull(peer.get("gpa"));
        assertNull(peer.get("gradedCredits"));
        assertEquals(true, peer.get("gpaHidden"));
        // 关键：行不能删 —— 删了名次与总人数就对不上了
        assertEquals(2, list.size());
    }

    @Test
    void privacy_teacherOnlyLevelIsAlsoMaskedFromPeers() {
        Map<String, Object> peer = row(4L, 1, 3.2);
        List<Map<String, Object>> list = new java.util.ArrayList<>(List.of(peer));

        StudentServiceImpl.maskPeerPrivacy(list, 3L);

        assertNull(peer.get("gpa"), "「仅教师」对同学也应当是看不见的");
    }

    @Test
    void privacy_ownRowIsNeverMaskedEvenWhenPrivate() {
        Map<String, Object> mine = row(3L, 2, 3.5);
        List<Map<String, Object>> list = new java.util.ArrayList<>(List.of(mine));

        StudentServiceImpl.maskPeerPrivacy(list, 3L);

        assertEquals(3.5, mine.get("gpa"), "自己的成绩永远看得见");
        assertEquals(false, mine.get("gpaHidden"));
    }

    @Test
    void privacy_tinyint1AsBooleanIsUnderstood() {
        // MySQL JDBC 默认把 tinyint(1) 映射成 Boolean：0 → FALSE。
        // 少这一支的话，"公开"会被当成"读不懂"→ 反而被遮住。
        Map<String, Object> peer = row(4L, Boolean.FALSE, 3.2);
        List<Map<String, Object>> list = new java.util.ArrayList<>(List.of(peer));

        StudentServiceImpl.maskPeerPrivacy(list, 3L);

        assertEquals(3.2, peer.get("gpa"), "Boolean.FALSE = 等级 0 = 公开，不该被遮");
    }

    @Test
    void privacy_unreadableLevelIsMasked() {
        Map<String, Object> peer = row(4L, null, 3.2);
        List<Map<String, Object>> list = new java.util.ArrayList<>(List.of(peer));

        StudentServiceImpl.maskPeerPrivacy(list, 3L);

        assertNull(peer.get("gpa"), "等级读不出来时按'未公开'处理，宁可少露");
    }

    // ==================== 成绩单导出（W1.8） ====================

    /**
     * 导出的必须是**真 xlsx**，而且入参按 {@code users.id} 解释。
     *
     * <p>改之前：入参被当成学号查（{@code selectByStudentId(userId.toString())}，
     * 查的是学号列）⇒ 一个学生也查不到；返回值还是 JSON 摘要，前端当文件下载。
     * 负向对照：把 {@code selectByUserId} 改回 {@code selectByStudentId} 时，
     * 下面第一条断言（zip 头）就会失败。
     */
    @Test
    void exportScoresWorkbook_producesRealXlsxKeyedByUserId() throws Exception {
        StudentProfile sp = new StudentProfile();
        sp.setId(3L);
        sp.setUserId(27L);
        sp.setName("测试学生");
        sp.setStudentId("2023020701");
        sp.setClassName("计科2301班");
        sp.setEnrollmentYear(2023);
        when(studentProfileMapper.selectByUserId(27L)).thenReturn(sp);

        Score s = new Score();
        s.setSemester("4");
        s.setCourseName("高等数学");
        s.setCredits(new BigDecimal("4.0"));
        s.setScoreTotal(new BigDecimal("88.5"));
        s.setGrade("良好");
        s.setTeacherName("王老师");
        when(scoreMapper.selectByStudentId("3")).thenReturn(List.of(s));

        byte[] bytes = service.exportScoresWorkbook(27L);

        assertNotNull(bytes);
        assertEquals(0x50, bytes[0] & 0xFF, "应是 xlsx(zip) 头，不是 JSON 的 '{'");
        assertEquals(0x4B, bytes[1] & 0xFF);
        // scores.student_id 的取值空间是 student_profile.id，不是 users.id
        verify(scoreMapper).selectByStudentId("3");
        verify(scoreMapper, never()).selectByStudentId("27");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("学期", sheet.getRow(1).getCell(0).getStringCellValue());
            // 学期按入学年份换算成可读区间，与前端 Scores.vue 的 semesterLabel() 同规则
            assertEquals("2024-2025-2", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("高等数学", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals(88.5, sheet.getRow(2).getCell(5).getNumericCellValue(), 0.001);
            assertEquals("王老师", sheet.getRow(2).getCell(8).getStringCellValue());
        }
    }

    @Test
    void exportScoresWorkbook_unknownUserReturnsNull() {
        when(studentProfileMapper.selectByUserId(999L)).thenReturn(null);

        assertNull(service.exportScoresWorkbook(999L));
    }

    @Test
    void semesterLabel_matchesFrontendRule() {
        assertEquals("2023-2024-1", StudentServiceImpl.semesterLabel("1", 2023));
        assertEquals("2024-2025-2", StudentServiceImpl.semesterLabel("4", 2023));
        // 与 Scores.vue 注释里的例子一致：2023 级 + 6 → "2025-2026-2"
        assertEquals("2025-2026-2", StudentServiceImpl.semesterLabel("6", 2023));
        // 入学年份缺失 / 序号非数字 ⇒ 原样返回，不猜
        assertEquals("4", StudentServiceImpl.semesterLabel("4", null));
        assertEquals("abc", StudentServiceImpl.semesterLabel("abc", 2023));
        assertEquals("", StudentServiceImpl.semesterLabel(null, 2023));
    }
}
