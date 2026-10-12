package com.academic.service;

import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.AcademicWarningCourse;
import com.academic.mapper.AcademicWarningCourseMapper;
import com.academic.mapper.AcademicWarningMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * W39：把「生成一条预警」从单语句写变成"父行 + 每门挂科课一行明细"之后，
 * {@link WarningWriter} 的落库行为。
 *
 * <p>⚠️ 本类**不覆盖** {@code @Transactional} 是否真的生效 —— 那需要 Spring 代理，
 * 而本仓的测试全是纯 Mockito（零 {@code @SpringBootTest}）。这里能钉的是：
 * <ol>
 *   <li>每门挂科课都落了一行（此前 {@code createWarning} 手里有 {@code courseId}
 *       却扔掉不写，那正是"预警与课程对应不上"的根）；</li>
 *   <li>字段映射没串位（courseId / 课程名 / 分数 / 学期）；</li>
 *   <li>缺 {@code courseId} 的行被跳过而不是插一行挂不上课的；</li>
 *   <li><b>失败传播</b>：明细写失败必须把异常抛出去，而不是吞掉——
 *       {@code WarningScheduledTask.createWarning} 是"父行先插、明细后插"，
 *       若这里吞了异常，扫描会走到 {@code generated++}、把这名学生标记成"本轮已生成"，
 *       结果留下"预警在、课程明细缺失"的半截数据，而教师端正是按明细判断
 *       "这条预警涉不涉及我教的课"。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarningWriterTest {

    @Mock
    private AcademicWarningMapper academicWarningMapper;

    @Mock
    private AcademicWarningCourseMapper academicWarningCourseMapper;

    @InjectMocks
    private WarningWriter warningWriter;

    @Test
    @DisplayName("detail 里每一门挂科课各落一行明细，字段不串位")
    void testWritesOneRowPerFailedCourse() {
        AcademicWarning warning = new AcademicWarning()
                .setStudentId(3L).setStudentName("司明").setWarningLevel(2).setStatus(0);
        // insert 后回填自增 id —— 真实 MyBatis 会做，这里手工模拟，
        // 否则明细的 warning_id 全是 null，测不出真实行为。
        doAnswer(inv -> {
            warning.setId(395L);
            return 1;
        }).when(academicWarningMapper).insert(any(AcademicWarning.class));

        warningWriter.insertWarningWithCourses(warning, List.of(
                detail(6L, "操作系统", 40.0, 4),
                detail(3L, "计算机基础", 47.0, 5),
                detail(1L, "高等数学", 56.5, 5)));

        ArgumentCaptor<AcademicWarningCourse> captor = ArgumentCaptor.forClass(AcademicWarningCourse.class);
        verify(academicWarningCourseMapper, times(3)).upsert(captor.capture());
        List<AcademicWarningCourse> rows = captor.getAllValues();

        assertEquals(395L, rows.get(0).getWarningId());
        assertEquals(6L, rows.get(0).getCourseId());
        assertEquals("操作系统", rows.get(0).getCourseName());
        assertEquals(0, new BigDecimal("40.0").compareTo(rows.get(0).getScoreTotal()));
        assertEquals(4, rows.get(0).getSemester());

        assertEquals(3L, rows.get(1).getCourseId());
        assertEquals("计算机基础", rows.get(1).getCourseName());

        // 56.5 这种带小数的分要原样落，不能被截成 56 或 57。
        assertEquals(1L, rows.get(2).getCourseId());
        assertEquals(0, new BigDecimal("56.5").compareTo(rows.get(2).getScoreTotal()));
    }

    @Test
    @DisplayName("detail 为空是合法的：只插父行，不写明细（历史迁移来的预警就是这样）")
    void testEmptyDetailWritesOnlyParentRow() {
        AcademicWarning warning = new AcademicWarning().setStudentId(3L).setWarningLevel(1);

        warningWriter.insertWarningWithCourses(warning, List.of());

        verify(academicWarningMapper, times(1)).insert(any(AcademicWarning.class));
        verifyNoInteractions(academicWarningCourseMapper);
    }

    @Test
    @DisplayName("缺 courseId 的那一门跳过，其余照写 —— 不插一行挂不上课的明细")
    void testRowWithoutCourseIdIsSkipped() {
        AcademicWarning warning = new AcademicWarning().setStudentId(3L).setWarningLevel(2);
        doAnswer(inv -> {
            warning.setId(400L);
            return 1;
        }).when(academicWarningMapper).insert(any(AcademicWarning.class));

        Map<String, Object> noCourse = new HashMap<>();
        noCourse.put("courseId", null);
        noCourse.put("courseName", "未知课程");
        noCourse.put("scoreTotal", 50.0);

        warningWriter.insertWarningWithCourses(warning, List.of(
                detail(6L, "操作系统", 40.0, 4),
                noCourse,
                detail(1L, "高等数学", 56.5, 5)));

        verify(academicWarningCourseMapper, times(2)).upsert(any(AcademicWarningCourse.class));
    }

    @Test
    @DisplayName("明细写失败必须往外抛 —— 吞掉就会留下「预警在、课程明细缺失」的半截数据")
    void testDetailFailurePropagates() {
        AcademicWarning warning = new AcademicWarning().setStudentId(3L).setWarningLevel(2);
        doAnswer(inv -> {
            warning.setId(401L);
            return 1;
        }).when(academicWarningMapper).insert(any(AcademicWarning.class));
        when(academicWarningCourseMapper.upsert(any(AcademicWarningCourse.class)))
                .thenThrow(new RuntimeException("明细表写入失败"));

        assertThrows(RuntimeException.class,
                () -> warningWriter.insertWarningWithCourses(warning, List.of(detail(6L, "操作系统", 40.0, 4))));

        // 父行的 insert 确实已经发出去了 —— 正因如此才**必须**有事务把它一起回滚。
        // 这条断言是留给"将来有人把 @Transactional 去掉"的：去掉之后这里会变成
        // 一行真实的孤儿预警，而不是测试失败，所以本类只能钉住"异常不被吞"。
        verify(academicWarningMapper, times(1)).insert(any(AcademicWarning.class));
        verify(academicWarningCourseMapper, times(1)).upsert(any(AcademicWarningCourse.class));
    }

    private static Map<String, Object> detail(long courseId, String courseName, double scoreTotal, int semester) {
        Map<String, Object> row = new HashMap<>();
        row.put("courseId", courseId);
        row.put("courseName", courseName);
        row.put("scoreTotal", scoreTotal);
        row.put("semester", semester);
        return row;
    }
}
