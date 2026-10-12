package com.academic.service.impl;

import com.academic.common.entity.Student;
import com.academic.common.notify.StudentNotifier;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.CommunicationLogMapper;
import com.academic.mapper.StudentMapper;
import com.academic.mapper.WarningMapper;
import com.academic.service.StudentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 辅导员群发的接收人 id 空间（W38，2026-10-10）。
 *
 * <p><b>修之前是什么样</b>：{@code student_ids}（语义 {@code student_profile.id}）与
 * {@code userIds}/{@code user_ids}（语义 {@code users.id}）被混进**同一个**列表，再靠
 * {@code findStudent} "先按 profile.id 查、查不到再按 user_id 查"兜底。只因活库
 * {@code student_profile.id ≤ 26 < users.id ∈ [27,52]} **恰好不重叠**才对；一旦某个学生的
 * profile.id 与另一名学生的 user.id 相等，就会**静默发给另一个学生**（名册过滤只在
 * "那人不在我名下"时才救得了）。
 *
 * <p>两个真实调用方各发一套：PC {@code CounselorStudents.vue} 发 {@code student_ids: [row.userId]}
 * （users.id）、{@code CounselorCourses.vue} 发 {@code student_ids: [student.studentId]}
 * （profile.id）。所以这次修的是**解析**，而两个前端调用点在本次修复前**本来就互相打架** ——
 * 修复后 `student_ids` 一律按 profile.id 解，两个页面的键名迟早要归位（另行登记）。
 *
 * <p><b>这里钉的事</b>：把一段 id 空间刻意**重叠**的 fixture 喂进去 ——
 * profile.id=29 的学生 A 与 users.id=29 的学生 B —— 用 {@code user_ids:[29]} 发必须命中 B，
 * 用 {@code student_ids:[29]} 发必须命中 A。旧的"兜底顺序"实现下前者会命中 A（错人）。
 *
 * <p>直接调 {@code service.send(...)} 而不是读私有 {@code resolve}：send 的响应里
 * {@code recipients} 是解析出的人数，够用来判"发给谁"。两条学生都在辅导员名册里
 * （否则会被"不在我名下"那条跳过，测出来的就不是 id 空间了）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CounselorNotificationIdSpaceTest {

    @Mock private CommunicationLogMapper communicationLogMapper;
    @Mock private StudentNotifier studentNotifier;
    @Mock private StudentService studentService;
    @Mock private StudentMapper studentMapper;
    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private WarningMapper warningMapper;

    @InjectMocks private CounselorNotificationServiceImpl service;

    private static final long COUNSELOR = 53L;

    /** 学生 A：{@code student_profile.id = 29}（故意与下面 B 的 users.id 撞号）。 */
    private static Student profileId29() {
        Student s = new Student();
        s.setId(29L);
        s.setUserId(9001L);   // 档案绑的 users.id 与 29 无关
        s.setName("档案29");
        return s;
    }

    /** 学生 B：{@code users.id = 29}，其 profile.id = 55。 */
    private static Student userId29() {
        Student s = new Student();
        s.setId(55L);
        s.setUserId(29L);
        s.setName("用户29");
        return s;
    }

    private Map<String, Object> payload(Map<String, Object> ids) {
        Map<String, Object> data = new HashMap<>();
        data.put("counselorId", COUNSELOR);
        data.put("content", "测试通知");
        data.putAll(ids);
        return data;
    }

    /** 两个学生都在辅导员名下；通知一律送达（不被"已关闭该类通知"拦下）。 */
    private void stubSideEffects() {
        when(studentService.getByCounselorId(COUNSELOR))
                .thenReturn(List.of(profileId29(), userId29()));
        when(studentNotifier.notifyUser(anyLong(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(true);
    }

    /** `user_ids:[29]` ⇒ 必须命中 users.id=29 的 B，**不是** profile.id=29 的 A。 */
    @Test
    @DisplayName("user_ids 按 users.id 解析（撞号时不许命中同号的 profile.id）")
    void userIdsResolveInUserSpace() {
        stubSideEffects();
        // A：如果错按 profile 空间查就会命中它（这是个陷阱，正确实现永远不查它）
        when(studentMapper.selectById(29L)).thenReturn(profileId29());
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userId29()));

        Map<String, Object> res = service.send(payload(Map.of("user_ids", List.of(29))));

        assertEquals(1, res.get("recipients"));
        // 真发给 B（users.id=29）：notifyUser(userId=29, profileId=55, …)
        verify(studentNotifier).notifyUser(eq(29L), eq(55L), any(), any(), any(), any(), any());
        verify(studentNotifier, never()).notifyUser(eq(9001L), anyLong(), any(), any(), any(), any(), any());
    }

    /** `student_ids:[29]` ⇒ 必须命中 profile.id=29 的 A，且**不去查 users 空间**。 */
    @Test
    @DisplayName("student_ids 按 student_profile.id 解析（不去 users 空间兜底）")
    void studentIdsResolveInProfileSpace() {
        stubSideEffects();
        when(studentMapper.selectById(29L)).thenReturn(profileId29());

        Map<String, Object> res = service.send(payload(Map.of("student_ids", List.of(29))));

        assertEquals(1, res.get("recipients"));
        // 真发给 A（profile.id=29）：notifyUser(userId=9001, profileId=29, …)
        verify(studentNotifier).notifyUser(eq(9001L), eq(29L), any(), any(), any(), any(), any());
        verify(studentMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 反证：`student_ids:[29]` 在 profile 空间查不到时**不越界**去 users 空间。
     *
     * <p>旧的兜底顺序会回退 ⇒ 命中 users.id=29 的 B，也就是"发给另一个人"。新实现下
     * 一个接收人都解析不出来，{@code send} 如实抛错并列出原因。
     */
    @Test
    @DisplayName("student_ids 在自己空间查不到 ⇒ 不越界去 users 空间，如实报错")
    void studentIdsDoNotFallBackToUserSpace() {
        stubSideEffects();
        when(studentMapper.selectById(29L)).thenReturn(null);
        // users 空间里**有** 29 这个人（B）——若实现回退就命中他
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userId29()));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.send(payload(Map.of("student_ids", List.of(29)))));

        assertTrue(e.getMessage().contains("student_profile.id"),
                "报错要说明是按哪个空间找不到的，实际：" + e.getMessage());
        verify(studentMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(studentNotifier, never()).notifyUser(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }
}
