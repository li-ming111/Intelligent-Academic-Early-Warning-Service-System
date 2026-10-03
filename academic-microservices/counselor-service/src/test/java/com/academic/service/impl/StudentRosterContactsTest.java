package com.academic.service.impl;

import com.academic.common.entity.Student;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.ScoreMapper;
import com.academic.mapper.StudentMapper;
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
 * 辅导员花名册的「电话 / 邮箱 / 账号状态」三列（W4.11，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：PC 的辅导员「学生管理」页有三列「联系电话 / 邮箱 / 状态」，
 * 而 {@code getByCounselorId} 只从 {@code student_profile} 取数 —— 那三样在
 * **{@code users} 表**上（{@code student_profile} 实测 13 列，没有 phone/email/status）。
 * 于是前两列恒 {@code '--'}，第三列是页面上写死的「正常」。导出的 CSV 读的也是这三个字段，
 * 所以导出里同样是空的。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① 三个值真的被填上（phone/email/userStatus）；
 * <br>② 是**一次批量**查询，不是每人一次（N 个学生只打一次库 —— 用 {@code times(1)} 钉住）；
 * <br>③ 查不到 users 行的学生保持 {@code null}，**不编值**（页面上按 '--' 显示）；
 * <br>④ 一个 userId 都没有时**根本不发那条查询** —— 那段 SQL 是
 *     {@code WHERE u.id IN <foreach>}，传空集合会拼出 {@code IN ()}，是语法错误。
 *     守卫写的是 {@code if (!userIds.isEmpty())}，这条用例就是钉住这个守卫。
 */
@ExtendWith(MockitoExtension.class)
class StudentRosterContactsTest {

    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private ScoreMapper scoreMapper;
    @Mock private StudentMapper baseMapper;

    @InjectMocks private StudentServiceImpl service;

    private static final long COUNSELOR = 41L;
    private static final long CLASS_A = 7L;

    /**
     * 显式把 {@code baseMapper} 钉成 {@code StudentMapper} 这个 mock。
     *
     * <p>不能指望 {@code @InjectMocks}：{@code ServiceImpl} 里那个字段声明成
     * {@code protected M baseMapper}，**泛型擦除后类型是 Object** ⇒ 按类型注入时
     * 三个 mock 都"可赋值"，谁先被遍历到就注入谁（可能是 classInfoMapper），
     * 于是测试会在一个与生产不同的世界里跑，绿得毫无意义。
     */
    @org.junit.jupiter.api.BeforeEach
    void wireBaseMapper() throws Exception {
        java.lang.reflect.Field f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        f.setAccessible(true);
        f.set(service, baseMapper);
    }

    private void oneClass() {
        List<Map<String, Object>> classes = new ArrayList<>();
        Map<String, Object> c = new HashMap<>();
        c.put("id", CLASS_A);
        c.put("name", "计科2306班");
        classes.add(c);
        when(classInfoMapper.getClassesByCounselorIdMap(COUNSELOR)).thenReturn(classes);
        when(scoreMapper.getStudentCreditStatsByCounselorId(COUNSELOR)).thenReturn(new ArrayList<>());
    }

    private static Student student(long profileId, Long userId) {
        Student s = new Student();
        s.setId(profileId);
        s.setUserId(userId);
        s.setStudentId("2023" + profileId);
        s.setName("学生" + profileId);
        s.setClassId(CLASS_A);
        return s;
    }

    private static Map<String, Object> usersRow(long userId, String phone, String email, Integer status) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", userId);
        row.put("phone", phone);
        row.put("email", email);
        row.put("status", status);
        return row;
    }

    @Test
    @DisplayName("电话/邮箱/账号状态真的填上，且只打一次库")
    void fillsContacts_inOneBatchQuery() {
        oneClass();
        when(baseMapper.selectList(any())).thenReturn(List.of(student(1L, 101L), student(2L, 102L)));
        when(baseMapper.selectUserContacts(anyList())).thenReturn(List.of(
                usersRow(101L, "13800000001", "a@x.edu", 1),
                usersRow(102L, "13800000002", "b@x.edu", 0)));

        List<Student> list = service.getByCounselorId(COUNSELOR);

        assertEquals(2, list.size());
        Student s1 = list.get(0);
        assertEquals("13800000001", s1.getPhone());
        assertEquals("a@x.edu", s1.getEmail());
        assertEquals(1, s1.getUserStatus());
        assertEquals("13800000002", list.get(1).getPhone());
        assertEquals(0, list.get(1).getUserStatus(), "status=0（待审批）不能被当成没取到");
        // ★ 批量：两个学生只查一次 users
        verify(baseMapper, times(1)).selectUserContacts(anyList());
    }

    @Test
    @DisplayName("查不到 users 行的学生保持 null（不编值）")
    void missingUsersRow_staysNull() {
        oneClass();
        when(baseMapper.selectList(any())).thenReturn(List.of(student(1L, 101L), student(2L, 102L)));
        // 只回了 101 那一行
        when(baseMapper.selectUserContacts(anyList())).thenReturn(List.of(
                usersRow(101L, "13800000001", "a@x.edu", 1)));

        List<Student> list = service.getByCounselorId(COUNSELOR);

        assertNull(list.get(1).getPhone(), "取不到就是取不到，别用名字/学号拼一个假电话");
        assertNull(list.get(1).getEmail());
        assertNull(list.get(1).getUserStatus(), "状态取不到时是 null，页面才会显示'未知'而不是'正常'");
    }

    @Test
    @DisplayName("users 行里字段是 NULL：不写空串，保持 null")
    void nullColumns_doNotBecomeEmptyString() {
        oneClass();
        when(baseMapper.selectList(any())).thenReturn(List.of(student(1L, 101L)));
        when(baseMapper.selectUserContacts(anyList())).thenReturn(List.of(
                usersRow(101L, null, null, 1)));

        Student s = service.getByCounselorId(COUNSELOR).get(0);

        assertNull(s.getPhone(), "库里的 NULL 不该在页面上变成空串（'--' 与 '' 是两回事）");
        assertNull(s.getEmail());
        assertEquals(1, s.getUserStatus());
    }

    @Test
    @DisplayName("学生都没绑 userId：一次 users 查询都不发（空集合会拼出 IN () 语法错误）")
    void noUserIds_neverQueriesUsers() {
        oneClass();
        when(baseMapper.selectList(any())).thenReturn(List.of(student(1L, null), student(2L, null)));

        List<Student> list = service.getByCounselorId(COUNSELOR);

        assertEquals(2, list.size());
        verify(baseMapper, never()).selectUserContacts(anyList());
        assertNull(list.get(0).getPhone());
    }

    @Test
    @DisplayName("一个班都没有：直接回空列表，连学生表都不查")
    void noClass_shortCircuits() {
        when(classInfoMapper.getClassesByCounselorIdMap(COUNSELOR)).thenReturn(new ArrayList<>());

        assertTrue(service.getByCounselorId(COUNSELOR).isEmpty());
        verify(baseMapper, never()).selectList(any());
        verify(baseMapper, never()).selectUserContacts(anyList());
    }
}
