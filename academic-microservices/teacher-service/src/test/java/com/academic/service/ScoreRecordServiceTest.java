package com.academic.service;

import com.academic.common.entity.Score;
import com.academic.common.entity.ScoreRecord;
import com.academic.mapper.ScoreMapper;
import com.academic.mapper.ScoreRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ScoreRecordService} 的读写路径（W36，2026-10-10 改写）。
 *
 * <p>本轮的实质变化：教师端读写的数据源由 {@code score_record}（暂存表）改为
 * {@code scores}（权威表）。所以本测试钉住的正是"落库目标是 {@code scores}、不再碰
 * {@code score_record}"以及口径细节（学期/学年推导、final_score 补齐、改分不抹平时分）。
 */
@ExtendWith(MockitoExtension.class)
class ScoreRecordServiceTest {

    @Mock private ScoreRecordMapper scoreRecordMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private ScoreAuditLogService auditLogService;

    @InjectMocks private ScoreRecordService scoreRecordService;

    // ------------------------------------------------------------------ 读（委派给权威投影）

    @Test
    @DisplayName("读按班级名 → 委派权威投影，不再查 score_record")
    void readsDelegateToAuthoritativeProjection() {
        ScoreRecord r = new ScoreRecord();
        r.setStudentName("张三");
        when(scoreRecordMapper.listLatestValidByClassName("计科2306班")).thenReturn(List.of(r));

        assertEquals("张三", scoreRecordService.getScoresByClassName("计科2306班").get(0).getStudentName());
        verify(scoreRecordMapper).listLatestValidByClassName("计科2306班");
    }

    @Test
    @DisplayName("按课程 id 读 → 委派权威投影；带 teacher_id 的重载同源")
    void readsByCourseDelegate() {
        when(scoreRecordMapper.listLatestValidByCourse(1L)).thenReturn(List.of());
        assertTrue(scoreRecordService.getScoresByCourseId(1L).isEmpty());
        assertTrue(scoreRecordService.getScoresByCourseIdAndTeacherId(1L, 29L).isEmpty());
        verify(scoreRecordMapper, times(2)).listLatestValidByCourse(1L);
    }

    @Test
    @DisplayName("按 scores.id 取行 → 委派 selectScoreById")
    void getByIdDelegates() {
        ScoreRecord r = new ScoreRecord();
        r.setId(11L);
        when(scoreRecordMapper.selectScoreById(11L)).thenReturn(r);
        assertEquals(11L, scoreRecordService.getById(11L).getId());
    }

    // ------------------------------------------------------------------ 写（直落 scores）

    @Test
    @DisplayName("未指定学期、该课有历史：沿用**该课**最近一条的学期（覆盖语义），学期/学年/final_score 都补齐")
    void saveScores_noSemester_reusesLatestSemesterOfThatCourse() {
        when(scoreMapper.selectLatestSemester(3L, 1L)).thenReturn(5);
        when(scoreRecordMapper.selectStudentNo(3L)).thenReturn("2023020616");
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "5")).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        scoreRecordService.saveScores(List.of(scoreData(3L, 1L, 92.0, null)), 7L);

        ArgumentCaptor<Score> cap = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(cap.capture());
        Score saved = cap.getValue();
        assertEquals("5", saved.getSemester(), "未指定学期 → 沿用该生该课最近一条的学期");
        verify(scoreMapper, never()).selectByStudentCourseSemester(3L, 1L, "1");
        assertEquals(2025, saved.getYear(), "学年 = 学号前四位 2023 + (5-1)/2");
        assertEquals(0, new BigDecimal("92.0").compareTo(saved.getScoreTotal()));
        assertEquals("A", saved.getGrade());
        assertEquals(0, BigDecimal.valueOf(4.0).compareTo(saved.getGradePoint()));
        assertNotNull(saved.getFinalScore(), "final_score 必须补齐，否则被『期末未录入』口径整体排除");
        assertEquals(0, new BigDecimal("92.0").compareTo(saved.getFinalScore()));
        assertEquals("测试", saved.getRemark(), "备注必须落到 scores.remark");
        assertEquals(7L, saved.getModifiedBy());
        // 一次 saveScores = 一次 insert（早先是"先写 score_record 再二跳进 scores"，两次写）
        verify(scoreMapper, times(1)).insert(any(Score.class));
    }

    @Test
    @DisplayName("未指定学期、该课从无历史：从第 1 学期起（不拿该生别的课的最大学期凑数）")
    void saveScores_noSemester_noHistory_startsAtSemesterOne() {
        when(scoreMapper.selectLatestSemester(3L, 1L)).thenReturn(null);
        when(scoreRecordMapper.selectStudentNo(3L)).thenReturn("2023020616");
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "1")).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        scoreRecordService.saveScores(List.of(scoreData(3L, 1L, 92.0, null)), 7L);

        ArgumentCaptor<Score> cap = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(cap.capture());
        assertEquals("1", cap.getValue().getSemester());
        assertEquals(2023, cap.getValue().getYear(), "学期 1 → 入学学年 2023");
    }

    @Test
    @DisplayName("重修（显式指定学期）：按三元组在 scores 查重，新增一行")
    void saveScores_retake_usesTripleKey() {
        when(scoreRecordMapper.selectStudentNo(3L)).thenReturn("2023020616");
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "6")).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        scoreRecordService.saveScores(List.of(scoreData(3L, 1L, 66.0, "6")), 7L);

        verify(scoreMapper).selectByStudentCourseSemester(3L, 1L, "6");
        verify(scoreMapper, never()).selectLatestSemester(anyLong(), anyLong());
        ArgumentCaptor<Score> cap = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(cap.capture());
        assertEquals("6", cap.getValue().getSemester());
    }

    @Test
    @DisplayName("已有该三元组 → UPDATE scores（不是新增）")
    void saveScores_existing_updates() {
        Score existing = new Score();
        existing.setId(11L);
        existing.setStudentId(3L);
        existing.setCourseId(1L);
        existing.setSemester("5");
        when(scoreRecordMapper.selectStudentNo(3L)).thenReturn("2023020616");
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "5")).thenReturn(existing);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        scoreRecordService.saveScores(List.of(scoreData(3L, 1L, 80.0, "5")), 7L);

        verify(scoreMapper).updateById(any(Score.class));
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("改分：更新 scores 一行，重算等级/绩点，保留平时分与期末分，并写审计")
    void updateScore_updatesAuthoritativeRowAndAudits() {
        Score existing = new Score();
        existing.setId(11L);
        existing.setStudentId(3L);
        existing.setCourseId(1L);
        existing.setSemester("5");
        existing.setScoreTotal(new BigDecimal("55.0"));
        existing.setRegularScore(new BigDecimal("80.0"));
        existing.setFinalScore(new BigDecimal("90.0"));
        existing.setGrade("D");

        when(scoreMapper.selectById(11L)).thenReturn(existing);
        when(scoreRecordMapper.selectStudentName(3L)).thenReturn("司明");
        when(scoreRecordMapper.selectCourseTeacherId(1L)).thenReturn(29L);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        scoreRecordService.updateScore(11L, 62.0, "补考通过", "教师");

        ArgumentCaptor<Score> cap = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(cap.capture());
        Score saved = cap.getValue();
        assertEquals(0, new BigDecimal("62.0").compareTo(saved.getScoreTotal()));
        assertEquals("C", saved.getGrade(), "等级必须跟着新分数重算");
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(saved.getGradePoint()));
        assertNotNull(saved.getRegularScore(), "改分不该抹掉平时分");
        assertEquals(0, new BigDecimal("80.0").compareTo(saved.getRegularScore()));
        assertNotNull(saved.getFinalScore(), "final_score 被抹掉会让这一行从挂科统计里消失");
        assertEquals(0, new BigDecimal("90.0").compareTo(saved.getFinalScore()));
        assertEquals("补考通过", saved.getRemark());

        // 审计：旧分数取自改分前那一行，归属取自课程/档案
        ArgumentCaptor<ScoreRecord> oldCap = ArgumentCaptor.forClass(ScoreRecord.class);
        ArgumentCaptor<ScoreRecord> newCap = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(auditLogService).record(oldCap.capture(), newCap.capture(), eq("补考通过"), eq("教师"));
        assertEquals(55.0, oldCap.getValue().getScore());
        ScoreRecord audited = newCap.getValue();
        assertEquals(3L, audited.getStudentId(), "student_id 是 NOT NULL");
        assertEquals(1L, audited.getCourseId());
        assertEquals(29L, audited.getTeacherId(), "teacher_id 是 NOT NULL，取课程授课教师");
        assertEquals(62.0, audited.getScore());
    }

    @Test
    @DisplayName("改一个不存在的 scores.id → 抛异常（不假装成功、不留审计）")
    void updateScore_missingRow_throws() {
        when(scoreMapper.selectById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> scoreRecordService.updateScore(99L, 60.0, "x", "教师"));
        verify(auditLogService, never()).record(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ 删（scores）

    @Test
    @DisplayName("删单条：直接删 scores 一行，返回影响行数")
    void removeById_deletesAuthoritativeRow() {
        when(scoreMapper.deleteById(11L)).thenReturn(1);
        assertEquals(1, scoreRecordService.removeById(11L));
        verify(scoreMapper, times(1)).deleteById(11L);
    }

    @Test
    @DisplayName("删不存在的 id → 返回 0（控制器据此回 404）")
    void removeById_nonExistent_returnsZero() {
        when(scoreMapper.deleteById(99L)).thenReturn(0);
        assertEquals(0, scoreRecordService.removeById(99L));
    }

    @Test
    @DisplayName("批量删：逐个删 scores 行，累加影响行数")
    void removeByIds_deletesEach() {
        when(scoreMapper.deleteById(11L)).thenReturn(1);
        when(scoreMapper.deleteById(22L)).thenReturn(1);
        assertEquals(2, scoreRecordService.removeByIds(Arrays.asList(11L, 22L)));
        verify(scoreMapper).deleteById(11L);
        verify(scoreMapper).deleteById(22L);
    }

    @Test
    @DisplayName("批量删空列表 → 不做任何事")
    void removeByIds_emptyIsNoop() {
        assertEquals(0, scoreRecordService.removeByIds(Collections.emptyList()));
        verifyNoInteractions(scoreMapper, scoreRecordMapper);
    }

    private static Map<String, Object> scoreData(Long studentId, Long courseId, double score, String semester) {
        Map<String, Object> data = new HashMap<>();
        data.put("studentId", studentId);
        data.put("courseId", courseId);
        data.put("score", score);
        data.put("studentName", "司明");
        data.put("className", "计科2306班");
        data.put("remark", "测试");
        if (semester != null) {
            data.put("semester", semester);
        }
        return data;
    }
}
