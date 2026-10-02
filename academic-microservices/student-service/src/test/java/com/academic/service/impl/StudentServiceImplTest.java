package com.academic.service.impl;

import com.academic.entity.StudentProfile;
import com.academic.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
