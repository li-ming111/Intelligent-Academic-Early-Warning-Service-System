package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.mapper.*;
import com.academic.common.notify.StudentNotifier;
import com.academic.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 辅导员「班级成绩」的归属闸（W4.12，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：{@code GET /counselors/scores/class/{classId}?counselor_id=…}
 * 把 {@code counselor_id} 收下来**从头到尾没用过** —— 任何一个辅导员把 classId 换成别人的班，
 * 就能读到那个班全部学生的成绩。这与学院管理员的跨院花名册（W1.13）、
 * 辅导员花名册的跨班读取是同一个形状。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① 自己的班 ⇒ 放行；
 * <br>② 不是自己的班 ⇒ 403，且 {@code scoreMapper} **一次都没被调用**
 *     （校验必须在取数**之前** —— 否则"先查出来再丢掉"照样是一次越权读）；
 * <br>③ {@code counselor_id} 缺失 ⇒ 403 而不是放行（缺参数 ≠ 有权限），同样不取数；
 * <br>④ 越权回的是 403 不是"空列表" —— 回空列表的话，页面上"越权"与"这个班没成绩"
 *     长得一模一样，会把一次拒绝讲成一条正常数据。
 *
 * <p><b>为什么这条闸是 fail-closed 的</b>：{@code ClassInfoService.getClassesByCounselorId}
 * 自己 catch 了异常并返回空列表 ⇒ 查班级这步一失败，这里就判"不是你的班" ⇒ 403。
 * 换句话说底层抖动不会变成"放行"。已有一条用例钉住这个方向（第 ③ 组的
 * "查班级抛异常" 场景由 {@code ClassInfoService} 自己的 catch 覆盖，这里用空列表等价表达）。
 */
@ExtendWith(MockitoExtension.class)
class CounselorClassScoreAccessTest {

    @Mock private CounselorService counselorService;
    @Mock private AssistancePlanService assistancePlanService;
    @Mock private CommunicationLogService communicationLogService;
    @Mock private CounselorNotificationService counselorNotificationService;
    @Mock private StudentNotifier studentNotifier;
    @Mock private AssistanceEvaluationMapper assistanceEvaluationMapper;
    @Mock private StudentService studentService;
    @Mock private CounselorClassApplicationMapper counselorClassApplicationMapper;
    @Mock private ClassManagementService classManagementService;
    @Mock private ClassInfoService classInfoService;
    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private WarningMapper warningMapper;
    @Mock private StudentMapper studentMapper;

    @InjectMocks private CounselorController controller;

    private static final long COUNSELOR = 41L;
    private static final long MY_CLASS = 7L;
    private static final long OTHER_CLASS = 8L;

    /** 造一条 {@code classes} 查询结果（{@code id} 是 MyBatis 回的 BIGINT ⇒ Long）。 */
    private static Map<String, Object> cls(long id) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", "班级" + id);
        return m;
    }

    private void ownsClasses(Long... ids) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Long id : ids) list.add(cls(id));
        when(classInfoService.getClassesByCounselorId(COUNSELOR)).thenReturn(list);
    }

    // ---------------- ① 自己的班：放行 ----------------

    @Test
    @DisplayName("自己的班：放行，并把成绩原样带回去")
    void ownClass_passesThrough() {
        ownsClasses(MY_CLASS, 9L);
        List<Map<String, Object>> scores = List.of(Map.of("studentId", 1L, "score", 88));
        when(scoreMapper.getScoresByClassId(MY_CLASS)).thenReturn(scores);

        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(MY_CLASS, COUNSELOR);

        assertEquals(200, resp.getCode(), "自己的班要放行: " + resp.getMessage());
        assertSame(scores, resp.getData(), "成绩要原样返回");
        verify(scoreMapper).getScoresByClassId(MY_CLASS);
    }

    // ---------------- ② 别人的班：403 且不取数 ----------------

    @Test
    @DisplayName("别人的班：403，且成绩查询一次都没发生")
    void otherClass_forbidden_andNeverReadsScores() {
        ownsClasses(MY_CLASS, 9L);

        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(OTHER_CLASS, COUNSELOR);

        assertEquals(403, resp.getCode(), "不是自己的班必须拒绝");
        assertNull(resp.getData(), "拒绝时不该带任何数据");
        // ★ 负向对照的核心：把校验挪到取数之后，这一句就会红。
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    // ---------------- ③ 缺参数：403 而不是放行 ----------------

    @Test
    @DisplayName("缺 counselor_id：403（缺参数 ≠ 有权限）")
    void nullCounselor_forbidden() {
        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(MY_CLASS, null);

        assertEquals(403, resp.getCode(), "不给 counselor_id 不能等价于有权限");
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
        // 连"这个辅导员有哪些班"都不该查 —— 没有辅导员就无从查起
        verify(classInfoService, never()).getClassesByCounselorId(any());
    }

    @Test
    @DisplayName("辅导员一个班都没有：403")
    void counselorWithNoClass_forbidden() {
        when(classInfoService.getClassesByCounselorId(COUNSELOR)).thenReturn(new ArrayList<>());

        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(MY_CLASS, COUNSELOR);

        assertEquals(403, resp.getCode());
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    /**
     * 查班级这步失败（{@code ClassInfoService} 内部 catch 后回空列表）不能变成放行。
     */
    @Test
    @DisplayName("查班级这一步拿不到结果：仍然 403（fail-closed，不是 fail-open）")
    void classLookupFails_closed() {
        when(classInfoService.getClassesByCounselorId(COUNSELOR)).thenReturn(null);

        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(MY_CLASS, COUNSELOR);

        assertEquals(403, resp.getCode(), "底下一层抖了就当没权限，绝不能当有权限");
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    /**
     * 班级 id 在结果里不是数字（比如被换成字符串）时算"不匹配"，而不是 ClassCastException
     * ⇒ 500。那个 {@code instanceof Number} 判断就是为这条存在的。
     */
    @Test
    @DisplayName("班级 id 不是数字：判为不匹配（403），不是抛 ClassCastException")
    void nonNumericClassId_forbidden() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(MY_CLASS));   // 故意放字符串
        m.put("name", "班级7");
        list.add(m);
        when(classInfoService.getClassesByCounselorId(COUNSELOR)).thenReturn(list);

        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(MY_CLASS, COUNSELOR);

        assertEquals(403, resp.getCode(), "类型不对要判不匹配，不能炸成 500");
        verify(scoreMapper, never()).getScoresByClassId(anyLong());
    }

    /** 同一个班 id 但类型是 Integer（不是 Long）也必须匹配上 —— 别把等值判断写成对象引用比较。 */
    @Test
    @DisplayName("班级 id 是 Integer 也能匹配上（比数值，不比类型）")
    void integerClassId_matches() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> m = new HashMap<>();
        m.put("id", (int) MY_CLASS);   // Integer
        m.put("name", "班级7");
        list.add(m);
        when(classInfoService.getClassesByCounselorId(COUNSELOR)).thenReturn(list);
        when(scoreMapper.getScoresByClassId(MY_CLASS)).thenReturn(new ArrayList<>());

        ApiResponse<List<Map<String, Object>>> resp = controller.getClassScores(MY_CLASS, COUNSELOR);

        assertEquals(200, resp.getCode(), "数值相等就该放行（Long 41 与 Integer 41 是一回事）");
        verify(scoreMapper).getScoresByClassId(MY_CLASS);
    }
}
