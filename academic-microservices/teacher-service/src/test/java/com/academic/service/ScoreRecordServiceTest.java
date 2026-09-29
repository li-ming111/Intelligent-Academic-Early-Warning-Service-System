package com.academic.service;

import com.academic.common.entity.Score;
import com.academic.common.entity.ScoreRecord;
import com.academic.mapper.ScoreMapper;
import com.academic.mapper.ScoreRecordMapper;
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

@ExtendWith(MockitoExtension.class)
class ScoreRecordServiceTest {

    @Mock
    private ScoreRecordMapper scoreRecordMapper;

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private ScoreAuditLogService auditLogService;

    @InjectMocks
    private ScoreRecordService scoreRecordService;

    // ------------------------------------------------------------------
    // 读路径（原有用例）
    // ------------------------------------------------------------------

    @Test
    void testGetScoresByClassName() {
        ScoreRecord record = new ScoreRecord();
        record.setId(1L);
        record.setStudentName("张三");

        when(scoreRecordMapper.getScoresByClassName("软件工程2101")).thenReturn(List.of(record));

        List<ScoreRecord> results = scoreRecordService.getScoresByClassName("软件工程2101");

        assertEquals(1, results.size());
        assertEquals("张三", results.get(0).getStudentName());
        verify(scoreRecordMapper).getScoresByClassName("软件工程2101");
    }

    @Test
    void testGetScoresByClassName_EmptyClass() {
        when(scoreRecordMapper.getScoresByClassName("不存在的班级")).thenReturn(Collections.emptyList());

        assertTrue(scoreRecordService.getScoresByClassName("不存在的班级").isEmpty());
    }

    @Test
    void testGetScoresByTeacherId() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentName("李四");
        when(scoreRecordMapper.getScoresByTeacherId(10L)).thenReturn(List.of(record));

        assertEquals("李四", scoreRecordService.getScoresByTeacherId(10L).get(0).getStudentName());
    }

    @Test
    void testGetScoresByStudentId() {
        ScoreRecord record = new ScoreRecord();
        record.setScore(78.0);
        when(scoreRecordMapper.getScoresByStudentId("100")).thenReturn(List.of(record));

        assertEquals(78.0, scoreRecordService.getScoresByStudentId(100L).get(0).getScore());
    }

    @Test
    void testExportScores() {
        when(scoreRecordMapper.getScoresByCourseId(10L)).thenReturn(Collections.emptyList());

        assertTrue(scoreRecordService.exportScores(10L).isEmpty());
    }

    // ------------------------------------------------------------------
    // 写入路径：录 / 改 / 删
    // ------------------------------------------------------------------

    /** 录一门新课（调用方未指定学期）→ semester/year 必须真正落库，且 scores 行补齐 final_score。 */
    @Test
    void testSaveScores_newRecord_persistsSemesterYearAndFinalScore() {
        when(scoreRecordMapper.selectLatestByStudentAndCourse(3L, 1L)).thenReturn(null);
        when(scoreMapper.selectMaxSemester(3L)).thenReturn(5);
        when(scoreRecordMapper.selectStudentNo(3L)).thenReturn("2023020616");
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "5")).thenReturn(null);
        when(scoreRecordMapper.insertScore(any(ScoreRecord.class))).thenReturn(1);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        scoreRecordService.saveScores(List.of(scoreData(3L, 1L, 92.0, null)), 7L);

        // 暂存行：学期/学年必须写进去（旧实现是 insert 之后才 set，SQL 里又没有这两列 → 恒为 NULL）
        ArgumentCaptor<ScoreRecord> recCap = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(scoreRecordMapper).insertScore(recCap.capture());
        assertEquals("5", recCap.getValue().getSemester());
        // 学年 = 学号前四位 + (学期-1) DIV 2 = 2023 + 2 = 2025
        assertEquals(2025, recCap.getValue().getYear());
        // 派生列按权威口径（ScoreSyncSupport）折算
        assertEquals("A", recCap.getValue().getGrade());
        assertEquals(4.0, recCap.getValue().getGradePoint());
        assertEquals(1, recCap.getValue().getScoreLevel());

        // 权威行：final_score 必须补齐，否则该行被"期末未录入"口径整体排除，永远不参与预警
        ArgumentCaptor<Score> scoreCap = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(scoreCap.capture());
        Score saved = scoreCap.getValue();
        assertEquals("5", saved.getSemester());
        assertEquals(2025, saved.getYear());
        assertNotNull(saved.getFinalScore(), "final_score 为空会让这一行被挂科口径整体排除");
        assertEquals(0, new BigDecimal("92.0").compareTo(saved.getFinalScore()));
        assertEquals("A", saved.getGrade());
        assertEquals(0, BigDecimal.valueOf(4.0).compareTo(saved.getGradePoint()));
    }

    /** 重修：调用方显式指定学期时必须按三元组查重，新增一行而不是覆盖旧学期。 */
    @Test
    void testSaveScores_retake_usesTripleKeyAndKeepsOldSemesterRow() {
        ScoreRecord oldSemesterRow = new ScoreRecord();
        oldSemesterRow.setId(50L);
        oldSemesterRow.setSemester("3");

        when(scoreRecordMapper.selectLatestByStudentAndCourse(3L, 1L)).thenReturn(oldSemesterRow);
        when(scoreRecordMapper.selectByStudentCourseSemester(3L, 1L, "6")).thenReturn(null);
        when(scoreRecordMapper.selectStudentNo(3L)).thenReturn("2023020616");
        when(scoreRecordMapper.insertScore(any(ScoreRecord.class))).thenReturn(1);
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "6")).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        scoreRecordService.saveScores(List.of(scoreData(3L, 1L, 66.0, "6")), 7L);

        // 必须按三元组查，而不是沿用学期 3 的那一行
        verify(scoreRecordMapper).selectByStudentCourseSemester(3L, 1L, "6");
        verify(scoreRecordMapper, never()).updateScore(any(ScoreRecord.class));

        ArgumentCaptor<ScoreRecord> recCap = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(scoreRecordMapper).insertScore(recCap.capture());
        assertEquals("6", recCap.getValue().getSemester());
    }

    /** 改分：regular_score / final_score 不能被抹掉，等级必须跟着新分数重算。 */
    @Test
    void testUpdateScore_keepsRegularAndFinalAndRecomputesGrade() {
        ScoreRecord oldRecord = new ScoreRecord();
        oldRecord.setId(1L);
        oldRecord.setStudentId(3L);
        oldRecord.setCourseId(1L);
        oldRecord.setSemester("5");
        oldRecord.setScore(55.0);
        oldRecord.setRegularScore(80.0);
        oldRecord.setFinalScore(90.0);

        Score existing = new Score();
        existing.setId(11L);
        existing.setStudentId(3L);
        existing.setCourseId(1L);
        existing.setSemester("5");
        existing.setScoreTotal(new BigDecimal("55.0"));
        existing.setGrade("D");
        existing.setGradePoint(BigDecimal.ZERO);
        existing.setRegularScore(new BigDecimal("80.0"));
        existing.setFinalScore(new BigDecimal("90.0"));

        when(scoreRecordMapper.selectById(1L)).thenReturn(oldRecord);
        when(scoreRecordMapper.updateScore(any(ScoreRecord.class))).thenReturn(1);
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "5")).thenReturn(existing);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        scoreRecordService.updateScore(1L, 62.0, "补考通过", "教师");

        // 暂存行：派生列跟着新分数重算，且不携带 regular/final（由 mapper 的 <if> 守空放行）
        ArgumentCaptor<ScoreRecord> recCap = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(scoreRecordMapper).updateScore(recCap.capture());
        ScoreRecord sent = recCap.getValue();
        assertEquals("C", sent.getGrade());
        assertEquals(1.0, sent.getGradePoint());
        assertEquals(62.0, sent.getScoreTotal());
        assertNull(sent.getRegularScore());
        assertNull(sent.getFinalScore());

        // 权威行：总评和等级变了，平时分/期末分原封不动
        ArgumentCaptor<Score> scoreCap = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(scoreCap.capture());
        Score saved = scoreCap.getValue();
        assertEquals(0, new BigDecimal("62.0").compareTo(saved.getScoreTotal()));
        assertEquals("C", saved.getGrade());
        assertNotNull(saved.getRegularScore(), "改分不该抹掉平时分");
        assertEquals(0, new BigDecimal("80.0").compareTo(saved.getRegularScore()));
        assertNotNull(saved.getFinalScore(), "final_score 被抹掉会让这一行从挂科统计里消失");
        assertEquals(0, new BigDecimal("90.0").compareTo(saved.getFinalScore()));

        verify(auditLogService).record(oldRecord, sent, "补考通过", "教师");
    }

    /** 删单条：连带删掉对应学期的权威行。 */
    @Test
    void testRemoveById_deletesMatchingAuthoritativeRow() {
        ScoreRecord sr = new ScoreRecord();
        sr.setId(1L);
        sr.setStudentId(3L);
        sr.setCourseId(1L);
        sr.setSemester("5");

        Score authoritative = new Score();
        authoritative.setId(11L);

        when(scoreRecordMapper.selectById(1L)).thenReturn(sr);
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "5")).thenReturn(authoritative);
        when(scoreMapper.deleteById(11L)).thenReturn(1);
        when(scoreRecordMapper.deleteById(1L)).thenReturn(1);

        scoreRecordService.removeById(1L);

        verify(scoreMapper).deleteById(11L);
        verify(scoreRecordMapper).deleteById(1L);
    }

    /** 删单条但学期为空：不猜、不乱删，只删录入记录并留下 warn。 */
    @Test
    void testRemoveById_nullSemester_doesNotGuessWhichAuthoritativeRow() {
        ScoreRecord sr = new ScoreRecord();
        sr.setId(3L);
        sr.setStudentId(4L);
        sr.setCourseId(7L);
        sr.setSemester(null);

        when(scoreRecordMapper.selectById(3L)).thenReturn(sr);
        when(scoreRecordMapper.deleteById(3L)).thenReturn(1);

        scoreRecordService.removeById(3L);

        verify(scoreMapper, never()).deleteById(anyLong());
        verify(scoreMapper, never()).selectByStudentCourseSemester(anyLong(), anyLong(), any());
        verify(scoreRecordMapper).deleteById(3L);
    }

    /** 批量删除必须和单条一样连带删权威行 —— 旧实现只删暂存表，scores 里留下孤儿行。 */
    @Test
    void testRemoveByIds_alsoDeletesAuthoritativeRows() {
        ScoreRecord a = new ScoreRecord();
        a.setId(1L);
        a.setStudentId(3L);
        a.setCourseId(1L);
        a.setSemester("5");
        ScoreRecord b = new ScoreRecord();
        b.setId(2L);
        b.setStudentId(4L);
        b.setCourseId(2L);
        b.setSemester("6");

        Score scoreA = new Score();
        scoreA.setId(11L);
        Score scoreB = new Score();
        scoreB.setId(22L);

        when(scoreRecordMapper.selectById(1L)).thenReturn(a);
        when(scoreRecordMapper.selectById(2L)).thenReturn(b);
        when(scoreMapper.selectByStudentCourseSemester(3L, 1L, "5")).thenReturn(scoreA);
        when(scoreMapper.selectByStudentCourseSemester(4L, 2L, "6")).thenReturn(scoreB);
        when(scoreMapper.deleteById(11L)).thenReturn(1);
        when(scoreMapper.deleteById(22L)).thenReturn(1);
        when(scoreRecordMapper.deleteByIds(List.of(1L, 2L))).thenReturn(2);

        scoreRecordService.removeByIds(Arrays.asList(1L, 2L));

        verify(scoreMapper).deleteById(11L);
        verify(scoreMapper).deleteById(22L);
        verify(scoreRecordMapper).deleteByIds(List.of(1L, 2L));
    }

    @Test
    void testRemoveByIds_emptyListIsNoop() {
        scoreRecordService.removeByIds(Collections.emptyList());
        verifyNoInteractions(scoreRecordMapper, scoreMapper);
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
