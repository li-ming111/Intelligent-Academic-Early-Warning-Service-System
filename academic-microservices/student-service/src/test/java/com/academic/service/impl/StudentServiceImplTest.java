package com.academic.service.impl;

import com.academic.common.entity.Score;
import com.academic.entity.ScoreAppeal;
import com.academic.entity.StudentProfile;
import com.academic.mapper.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    // ==========================================================================
    // W38：benchmark 的 semester 参数「收下不用」⇒ 让排名真的按学期过滤
    //
    // 四条端点过去都只把 semester 回显、排名取自**累计** GPA（参数说谎，W15 一族）。
    // 现在非空学期走 selectSemester{Class,Major}GpaRanking，为空仍走累计（向后兼容）。
    // 判据：传 1 与传 2 命中**不同**的 mapper 方法且结果不同；不传 ⇒ 累计方法。
    // ==========================================================================

    /** 排行榜行（可变 —— `maskPeerPrivacy` 会就地 put `gpaHidden`）。 */
    private static Map<String, Object> row(long profileId, double gpa) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("profileId", profileId);
        m.put("gpa", gpa);
        m.put("privacyLevel", 0);
        return m;
    }

    @Test
    void classRanking_withSemester_usesSemesterScopedQuery() {
        StudentProfile sp = new StudentProfile();
        sp.setId(3L);
        sp.setUserId(27L);
        sp.setStudentId("2023020616");
        sp.setClassId(1L);
        when(studentProfileMapper.selectByUserId(27L)).thenReturn(sp);
        when(studentProfileMapper.selectById(3L)).thenReturn(sp);

        // semester=1 与 semester=2 命中的是同一个 mapper 方法、但**不同的学期参数** ⇒ 结果不同
        // 行必须可变：rankingResult 里的 maskPeerPrivacy 会往每行 put("gpaHidden", ...)。
        List<Map<String, Object>> sem1 = List.of(row(3L, 2.0));
        List<Map<String, Object>> sem2 = List.of(row(3L, 1.5), row(4L, 3.0));
        when(scoreMapper.selectSemesterClassGpaRanking(1L, 1)).thenReturn(sem1);
        when(scoreMapper.selectSemesterClassGpaRanking(1L, 2)).thenReturn(sem2);

        Map<String, Object> r1 = (Map<String, Object>) service.getBenchmarkClassRanking(27L, 1L, "1");
        Map<String, Object> r2 = (Map<String, Object>) service.getBenchmarkClassRanking(27L, 1L, "2");

        assertEquals(1, r1.get("total"));
        assertEquals(2, r2.get("total"));
        verify(scoreMapper).selectSemesterClassGpaRanking(1L, 1);
        verify(scoreMapper).selectSemesterClassGpaRanking(1L, 2);
        verify(scoreMapper, never()).selectClassGpaRanking(anyLong());
    }

    /** 不传 semester ⇒ 保持累计口径（两个前端当前都不传，这条钉住向后兼容）。 */
    @Test
    void classRanking_withoutSemester_keepsCumulativeQuery() {
        StudentProfile sp = new StudentProfile();
        sp.setId(3L);
        sp.setUserId(27L);
        sp.setStudentId("2023020616");
        sp.setClassId(1L);
        when(studentProfileMapper.selectByUserId(27L)).thenReturn(sp);
        when(studentProfileMapper.selectById(3L)).thenReturn(sp);
        when(scoreMapper.selectClassGpaRanking(1L)).thenReturn(List.of(row(3L, 2.0)));

        service.getBenchmarkClassRanking(27L, 1L, null);

        verify(scoreMapper).selectClassGpaRanking(1L);
        verify(scoreMapper, never()).selectSemesterClassGpaRanking(anyLong(), anyInt());
    }

    @Test
    void majorRanking_withSemester_usesSemesterScopedQuery() {
        StudentProfile sp = new StudentProfile();
        sp.setId(3L);
        sp.setUserId(27L);
        sp.setStudentId("2023020616");
        sp.setMajorId(2L);
        when(studentProfileMapper.selectByUserId(27L)).thenReturn(sp);
        when(studentProfileMapper.selectById(3L)).thenReturn(sp);
        when(scoreMapper.selectSemesterMajorGpaRanking(2L, 4)).thenReturn(List.of(row(3L, 2.0)));

        service.getBenchmarkMajorRanking(27L, 2L, "4");

        verify(scoreMapper).selectSemesterMajorGpaRanking(2L, 4);
        verify(scoreMapper, never()).selectMajorGpaRanking(anyLong());
    }

    @Test
    void classComparison_withSemester_usesSemesterScopedQuery() {
        when(scoreMapper.selectSemesterClassGpaRanking(1L, 2))
                .thenReturn(List.of(Map.of("profileId", 3L, "gpa", 2.0)));

        Map<String, Object> r = (Map<String, Object>) service.getClassBenchmarkComparison(1L, "2");

        assertEquals(1, r.get("count"));
        verify(scoreMapper).selectSemesterClassGpaRanking(1L, 2);
        verify(scoreMapper, never()).selectClassGpaRanking(anyLong());
    }

    // ==========================================================================
    // 第四十三轮 W9：申诉指向**具体哪一次成绩**
    //
    // 守三件事：① `scoreId` 非空时 courseId 由成绩行**回填**（不再让调用方各传一个）；
    // ② 指向**别人的成绩**必须被拒且**一条都不写库**（外键只管"存在"、不管"是谁的"，
    //    这条校验是不变量 D 组第 7 项唯一的人工防线）；
    // ③ 不带 `scoreId` 的老入口（预警页等）行为不变。
    // ==========================================================================

    /** `insert` 真的把 id 填上 —— 否则 `submitAppeal` 末尾的"回读"取不到 id。 */
    private void stubInsertAssigningId(long newId) {
        when(scoreAppealMapper.insert(any(ScoreAppeal.class))).thenAnswer(inv -> {
            inv.getArgument(0, ScoreAppeal.class).setId(newId);
            return 1;
        });
        ScoreAppeal stored = new ScoreAppeal();
        stored.setId(newId);
        when(scoreAppealMapper.selectById(newId)).thenReturn(stored);
    }

    /** 一行成绩。`profileId` 是 `scores.student_id`，用的是 **student_profile.id**。 */
    private Score scoreRow(long id, long profileId, long courseId, String semester, String total) {
        Score s = new Score();
        s.setId(id);
        s.setStudentId(profileId);
        s.setCourseId(courseId);
        s.setSemester(semester);
        s.setScoreTotal(new BigDecimal(total));
        s.setGrade("D");
        return s;
    }

    @Test
    void submitAppeal_withOwnScoreId_backfillsCourseIdFromTheScore() {
        loginAs("27");
        stubProfile(27L, 3L);
        when(scoreMapper.selectById(157L)).thenReturn(scoreRow(157L, 3L, 1L, "5", "56.50"));
        stubInsertAssigningId(9001L);

        // 请求里**故意不带** courseId —— 选了"哪一次成绩"就等于选了课程
        Object result = service.submitAppeal(Map.of("studentId", 27L, "scoreId", 157L, "reason", "entry_error"));

        assertNull(((Map<?, ?>) result).get("error"));
        ArgumentCaptor<ScoreAppeal> cap = ArgumentCaptor.forClass(ScoreAppeal.class);
        verify(scoreAppealMapper).insert(cap.capture());
        assertEquals(Long.valueOf(157L), cap.getValue().getScoreId());
        assertEquals(Long.valueOf(1L), cap.getValue().getCourseId(), "courseId 必须由成绩行回填");
        assertEquals(Long.valueOf(3L), cap.getValue().getStudentId(), "落库的是 profile id，不是 users.id");
    }

    @Test
    void submitAppeal_withSomeoneElsesScoreId_isRejectedAndWritesNothing() {
        loginAs("27");
        stubProfile(27L, 3L);
        // 成绩 157 属于 profile 26（张健），而提交人是 profile 3（司明）。
        // ⚠️ 外键 `score_id → scores.id` 只校验"存在"，**拦不住这个**。
        when(scoreMapper.selectById(157L)).thenReturn(scoreRow(157L, 26L, 1L, "5", "56.50"));

        Object result = service.submitAppeal(Map.of("studentId", 27L, "scoreId", 157L, "reason", "entry_error"));

        String err = String.valueOf(((Map<?, ?>) result).get("error"));
        assertTrue(err.contains("不属于该学生"), "实际: " + err);
        verify(scoreAppealMapper, never()).insert(any(ScoreAppeal.class));
    }

    @Test
    void submitAppeal_unknownScoreId_isRejected() {
        loginAs("27");
        stubProfile(27L, 3L);
        when(scoreMapper.selectById(888888L)).thenReturn(null);

        Object result = service.submitAppeal(Map.of("studentId", 27L, "scoreId", 888888L, "reason", "other"));

        assertTrue(String.valueOf(((Map<?, ?>) result).get("error")).contains("成绩不存在"));
        verify(scoreAppealMapper, never()).insert(any(ScoreAppeal.class));
    }

    @Test
    void submitAppeal_courseIdContradictingTheScore_isRejected() {
        loginAs("27");
        stubProfile(27L, 3L);
        when(scoreMapper.selectById(157L)).thenReturn(scoreRow(157L, 3L, 1L, "5", "56.50"));

        // 成绩 157 是课程 1（高等数学），请求却说课程 6（操作系统）
        Object result = service.submitAppeal(
                Map.of("studentId", 27L, "scoreId", 157L, "courseId", 6L, "reason", "other"));

        assertTrue(String.valueOf(((Map<?, ?>) result).get("error")).contains("不一致"));
        verify(scoreAppealMapper, never()).insert(any(ScoreAppeal.class));
    }

    /** 负向对照：**不带** scoreId 时行为与从前一致（课程级入口：预警页、小程序 chip…）。 */
    @Test
    void submitAppeal_withoutScoreId_stillWorksAndNeverLooksUpScores() {
        loginAs("27");
        stubProfile(27L, 3L);
        stubInsertAssigningId(9002L);

        Object result = service.submitAppeal(Map.of("studentId", 27L, "courseId", 1L, "reason", "other"));

        assertNull(((Map<?, ?>) result).get("error"));
        ArgumentCaptor<ScoreAppeal> cap = ArgumentCaptor.forClass(ScoreAppeal.class);
        verify(scoreAppealMapper).insert(cap.capture());
        assertNull(cap.getValue().getScoreId());
        assertEquals(Long.valueOf(1L), cap.getValue().getCourseId());
        verify(scoreMapper, never()).selectById(any());
    }

    /** 读路径：带 scoreId 的行要带出学期/总分/等级；不带的行**不许编造**字段、也不许抛。 */
    @Test
    void getStudentAppeals_exposesScoreAndToleratesNullScoreId() {
        stubProfile(27L, 3L);
        ScoreAppeal withScore = new ScoreAppeal();
        withScore.setId(6L);
        withScore.setStudentId(3L);
        withScore.setCourseId(1L);
        withScore.setScoreId(157L);
        withScore.setReason("entry_error");
        withScore.setStatus(0);
        ScoreAppeal without = new ScoreAppeal();
        without.setId(7L);
        without.setStudentId(3L);
        without.setStatus(0);

        when(scoreAppealMapper.selectByStudentId(3L)).thenReturn(List.of(withScore, without));
        when(scoreAppealMapper.selectCourseName(1L)).thenReturn("高等数学");
        when(scoreMapper.selectById(157L)).thenReturn(scoreRow(157L, 3L, 1L, "5", "56.50"));

        List<?> list = (List<?>) service.getStudentAppeals(27L);

        Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertEquals("高等数学", first.get("courseName"));
        assertEquals("5", first.get("scoreSemester"));
        assertEquals(0, new BigDecimal("56.50").compareTo((BigDecimal) first.get("scoreTotal")));
        assertEquals("D", first.get("scoreGrade"));

        Map<?, ?> second = (Map<?, ?>) list.get(1);
        assertNull(second.get("scoreId"));
        assertFalse(second.containsKey("scoreSemester"), "score_id 为空时不许编造字段");
        // 只有真正带 scoreId 的那一行才该去查成绩（少一次无用查询）
        verify(scoreMapper, times(1)).selectById(any());
    }

    /**
     * W16：{@code collegeId == null} 必须表示「全部学生」。
     *
     * <p>改前这里无条件走 {@code selectByCollegeId(null)} ⇒ SQL 成 {@code college_id = NULL}
     * ⇒ 恒空。{@code /admin/students}（{@code AdminController.getAllStudents} 传的就是 null）
     * 于是永远回空数组，而同一个动作在辅导员侧（{@code /admin/counselors}）却正常有数据。
     */
    @Test
    void getStudentsByCollege_nullMeansAll_neverTheNullOrEqSql() {
        StudentProfile a = new StudentProfile();
        a.setId(1L);
        StudentProfile b = new StudentProfile();
        b.setId(2L);
        when(studentProfileMapper.selectList(null)).thenReturn(List.of(a, b));

        List<?> list = service.getStudentsByCollege(null);

        assertEquals(2, list.size(), "null 学院 = 全部，不许回空");
        verify(studentProfileMapper).selectList(null);
        // 负向对照：不许再走那条把 null 变成 `= NULL` 的查询
        verify(studentProfileMapper, never()).selectByCollegeId(any());
    }

    /** W16 负向对照（另一方向）：给了具体学院时**必须**走筛选，不许退化成"全表"。 */
    @Test
    void getStudentsByCollege_withId_filtersAndNeverFallsBackToAll() {
        StudentProfile a = new StudentProfile();
        a.setId(3L);
        when(studentProfileMapper.selectByCollegeId(1L)).thenReturn(List.of(a));

        List<?> list = service.getStudentsByCollege(1L);

        assertEquals(1, list.size());
        verify(studentProfileMapper).selectByCollegeId(1L);
        verify(studentProfileMapper, never()).selectList(any());
    }
}
