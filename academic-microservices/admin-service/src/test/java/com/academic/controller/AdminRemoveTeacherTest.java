package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Classes;
import com.academic.mapper.ClassesMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 「移出班级教师」必须真的把 {@code teacher_id} 写成 NULL（W46，2026-10-14）。
 *
 * <p><b>修之前是什么样</b>：原实现是
 * {@code clazz.setTeacherId(null); classesMapper.updateById(clazz);}。
 * 本工程没有改过 MyBatis-Plus 的字段策略，默认 NOT_NULL ⇒ **值为 null 的字段不进 SET 子句**。
 * 实测用 MySQL general_log 抓到的语句是：
 * <pre>UPDATE classes SET name='…', college_id=1, created_at=…, updated_at=…, major_id=1 WHERE id=990001</pre>
 * —— 连 {@code teacher_id} 都不在。而接口回的是 HTTP 200 + {@code "教师已从班级移除"}、
 * 日志打的是 {@code "Removed teacher from class: …"}：**页面上教师消失了，刷新就回来**，
 * 库里那一行的绑定从头到尾没动过。典型的"报告与事实不符"。
 *
 * <p><b>为什么判据落在 UPDATE 语句本身</b>：这条端点的响应体与日志**在真假两种实现下完全一样**，
 * 所以断言 {@code code == 200} 或断言日志文本都区分不出来（那正是它藏了这么久的原因）。
 * 只有两样东西能区分：① 库里那一行；② 发给数据库的 SET 子句。
 * 纯 Mockito 单测里没有数据库，于是抓 {@link Wrapper} 参数、把 {@code getSqlSet()} 读出来，
 * 断言它**确实包含 {@code teacher_id}**。
 *
 * <p>回读校验（写完后 {@code selectById} 再确认 teacher_id 已为 null）也一并钉住：
 * 它是运行期唯一能"发现自己没改成"的手段。
 */
@ExtendWith(MockitoExtension.class)
class AdminRemoveTeacherTest {

    @Mock private ClassesMapper classesMapper;
    @Mock private com.academic.mapper.TeacherClassApplicationMapper applicationMapper;

    @InjectMocks private AdminController controller;

    private Classes cls(Long id, Long teacherId) {
        Classes c = new Classes();
        c.setId(id);
        c.setName("计科2306班");
        c.setTeacherId(teacherId);
        return c;
    }

    /** 承重断言：SET 子句里必须有 teacher_id，否则这次 UPDATE 根本清不掉绑定。 */
    @Test
    @DisplayName("移出教师：发出的 UPDATE 语句必须含 teacher_id（用 UpdateWrapper 显式 set null）")
    void removeTeacher_writesNullIntoSetClause() {
        when(classesMapper.selectById(777L)).thenReturn(cls(777L, 1L), cls(777L, null));

        ApiResponse<String> r = controller.removeTeacherFromClass(777L);

        assertEquals(200, r.getCode(), "实际: " + r.getMessage());

        ArgumentCaptor<Wrapper<Classes>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(classesMapper).update(isNull(), cap.capture());

        String set = ((UpdateWrapper<Classes>) cap.getValue()).getSqlSet();
        assertNotNull(set, "SET 子句不应为空");
        assertTrue(set.contains("teacher_id"),
                "SET 子句里必须出现 teacher_id（NOT_NULL 策略下 setTeacherId(null) 会被整条丢掉）"
                        + "，实际 SET: " + set);
    }

    /**
     * 反向：**不能**再退回 {@code updateById(entity)} —— 那条路径正是缺陷本身。
     * 判据是"没有任何一次 updateById 调用"。
     */
    @Test
    @DisplayName("移出教师：绝不走 updateById(entity)（那条路径会把 null 丢掉）")
    void removeTeacher_neverUsesUpdateById() {
        when(classesMapper.selectById(777L)).thenReturn(cls(777L, 1L), cls(777L, null));

        controller.removeTeacherFromClass(777L);

        verify(classesMapper, never()).updateById(any(Classes.class));
        verify(classesMapper).update(isNull(), any(Wrapper.class));
    }

    /**
     * 回读发现没改成 ⇒ 如实报错，不能再说"教师已从班级移除"。
     * 这一条是"报告与事实不符"的正面堵法：只要有办法知道没改成，就必须说。
     */
    @Test
    @DisplayName("移出教师：回读仍有 teacher_id ⇒ 不得回成功")
    void removeTeacher_readBackStillBound_reportsFailure() {
        // update 是"成功"的（没抛），但回读发现 teacher_id 还在
        when(classesMapper.selectById(777L)).thenReturn(cls(777L, 1L), cls(777L, 1L));

        ApiResponse<String> r = controller.removeTeacherFromClass(777L);

        assertNotEquals(200, r.getCode(), "没改成却回 200 就是这个缺陷本身");
        assertFalse(r.isSuccess());
    }

    /**
     * 移出教师后，该教师在该班的申请（待审/已通过）必须一并取消：
     * 否则 {@code getApplicationsByTeacherAndClass} 仍会把 status=1 的那条读出来，
     * 页面上「该教师仍管理中」原地复活。
     */
    @Test
    @DisplayName("移出教师：该班该教师的 status 0/1 申请一并置为已取消")
    void removeTeacher_cancelsOpenApplications() {
        when(classesMapper.selectById(777L)).thenReturn(cls(777L, 1L), cls(777L, null));

        controller.removeTeacherFromClass(777L);

        ArgumentCaptor<Wrapper> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(applicationMapper).update(isNull(), cap.capture());
        UpdateWrapper<?> cancel = (UpdateWrapper<?>) cap.getValue();

        assertTrue(cancel.getSqlSet().contains("status"), "SET 里必须显式写 status");
        assertTrue(cancel.getSqlSegment().contains("class_id"), "只取消本班的申请");
        assertTrue(cancel.getSqlSegment().contains("teacher_id"), "只取消被移出教师的申请");
        assertTrue(cancel.getParamNameValuePairs().containsValue(3), "取消后的状态值是 3");
        assertTrue(cancel.getParamNameValuePairs().containsValue(777L));
        assertTrue(cancel.getParamNameValuePairs().containsValue(1L));
    }

    /** 班级不存在 ⇒ 404，且一次 update 都不发。 */
    @Test
    @DisplayName("移出教师：班级不存在 ⇒ 404 且不写库")
    void removeTeacher_missingClass_returns404() {
        when(classesMapper.selectById(999999L)).thenReturn(null);

        ApiResponse<String> r = controller.removeTeacherFromClass(999999L);

        assertEquals(404, r.getCode());
        verify(classesMapper, never()).update(isNull(), any(Wrapper.class));
        verify(classesMapper, never()).updateById(any(Classes.class));
    }
}
