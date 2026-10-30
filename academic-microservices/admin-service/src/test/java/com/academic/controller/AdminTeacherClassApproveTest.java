package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Classes;
import com.academic.entity.TeacherClassApplication;
import com.academic.mapper.ClassesMapper;
import com.academic.mapper.TeacherClassApplicationMapper;
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
 * 批准教师班级申请必须**真的写 {@code classes.teacher_id}**（W47，2026-10-21）。
 *
 * <p><b>修之前是什么样</b>：新老两个批准端点都只把
 * {@code teacher_class_application.status} 置 1，**从不碰 {@code classes.teacher_id}**。
 * 页面上申请变成「已通过」，可班级的教师绑定从头到尾没动过：
 * 申请管理的通过者当不上班主任，解除管理的通过者仍然绑着班级。
 *
 * <p>判据落在发给数据库的 SET 子句上（与 {@code AdminRemoveTeacherTest} 同一思路）：
 * 光断言 {@code code == 200} 在真假两种实现下都一样，区分不出来。
 */
@ExtendWith(MockitoExtension.class)
class AdminTeacherClassApproveTest {

    @Mock private TeacherClassApplicationMapper applicationMapper;
    @Mock private ClassesMapper classesMapper;

    @InjectMocks private AdminController controller;

    private static TeacherClassApplication app(long id, Long classId, Long teacherId,
                                               int applicationType, Integer status) {
        TeacherClassApplication a = new TeacherClassApplication();
        a.setId(id);
        a.setClassId(classId);
        a.setTeacherId(teacherId);
        a.setApplicationType(applicationType);
        a.setStatus(status);
        return a;
    }

    private static Classes cls(long id, Long teacherId) {
        Classes c = new Classes();
        c.setId(id);
        c.setName("计科2306班");
        c.setTeacherId(teacherId);
        return c;
    }

    /** 承重：申请管理（type=0）通过后，班级必须记到申请人名下。 */
    @Test
    @DisplayName("★ 批准申请管理：UPDATE classes 必须把 teacher_id 写成申请人")
    void approveApplication_bindsApplicant() {
        TeacherClassApplication a = app(7L, 1L, 990050L, 0, 0);
        when(applicationMapper.selectById(7L)).thenReturn(a);
        when(classesMapper.selectById(1L)).thenReturn(cls(1L, null));
        // 新实现会检查两处 UPDATE 的返回行数（0 行 ⇒ 抛异常回滚），默认 0 会被当成失败。
        when(classesMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(applicationMapper.updateById(a)).thenReturn(1);

        ApiResponse<String> r = controller.approveClassRequest(7L, "teacher");

        assertEquals(200, r.getCode(), "实际: " + r.getMessage());
        assertEquals(1, a.getStatus().intValue(), "申请行应被置为已通过");
        verify(applicationMapper).updateById(a);

        ArgumentCaptor<Wrapper<Classes>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(classesMapper).update(isNull(), cap.capture());
        UpdateWrapper<Classes> bind = (UpdateWrapper<Classes>) cap.getValue();
        assertTrue(bind.getSqlSet().contains("teacher_id"),
                "SET 子句里必须出现 teacher_id，实际 SET: " + bind.getSqlSet());
        assertTrue(bind.getParamNameValuePairs().containsValue(990050L),
                "绑定值必须是申请里的教师，实际参数: " + bind.getParamNameValuePairs());
    }

    /** 解除管理（type=1）通过后，班级必须置空 —— 否则该教师仍挂在班级上。 */
    @Test
    @DisplayName("★ 批准解除管理：UPDATE classes 必须把 teacher_id 显式写成 NULL")
    void approveRelease_clearsTeacherId() {
        TeacherClassApplication a = app(8L, 1L, 990050L, 1, 0);
        when(applicationMapper.selectById(8L)).thenReturn(a);
        when(classesMapper.selectById(1L)).thenReturn(cls(1L, 990050L));
        when(classesMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(applicationMapper.updateById(a)).thenReturn(1);

        ApiResponse<String> r = controller.approveClassRequest(8L, "teacher");

        assertEquals(200, r.getCode(), "实际: " + r.getMessage());
        assertEquals(1, a.getStatus().intValue());
        verify(applicationMapper).updateById(a);

        ArgumentCaptor<Wrapper<Classes>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(classesMapper).update(isNull(), cap.capture());
        UpdateWrapper<Classes> clear = (UpdateWrapper<Classes>) cap.getValue();
        assertTrue(clear.getSqlSet().contains("teacher_id"),
                "解除管理必须显式写 teacher_id（set 成 NULL），实际 SET: " + clear.getSqlSet());
        assertTrue(clear.getParamNameValuePairs().containsValue(null),
                "SET 进去的值应为 null，实际参数: " + clear.getParamNameValuePairs());
    }

    /** 老端点 /teacher-class-applications/{id}/approve 走同一实现，不能只修新端点。 */
    @Test
    @DisplayName("旧批准端点同样绑定教师（两个端点共用同一实现）")
    void approveApplication_legacyEndpointBindsToo() {
        TeacherClassApplication a = app(9L, 1L, 990050L, 0, 0);
        when(applicationMapper.selectById(9L)).thenReturn(a);
        when(classesMapper.selectById(1L)).thenReturn(cls(1L, null));
        when(classesMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(applicationMapper.updateById(a)).thenReturn(1);

        assertEquals(200, controller.approveApplication(9L).getCode());
        assertEquals(1, a.getStatus().intValue());
        verify(classesMapper).update(isNull(), any(Wrapper.class));
    }

    /** 重复点击：已处理的申请不能再执行一遍（尤其解除管理，会把后绑的教师又踢掉）。 */
    @Test
    @DisplayName("已处理的申请 ⇒ 拒绝，且一次写都不发生")
    void approve_alreadyProcessed_rejected() {
        when(applicationMapper.selectById(7L)).thenReturn(app(7L, 1L, 990050L, 1, 1));

        ApiResponse<String> r = controller.approveClassRequest(7L, "teacher");

        assertNotEquals(200, r.getCode());
        verify(classesMapper, never()).update(any(), any(Wrapper.class));
        verify(applicationMapper, never()).updateById(any(TeacherClassApplication.class));
    }

    /** 班级已有别的教师 ⇒ 不能悄悄抢过来。 */
    @Test
    @DisplayName("班级已有其他教师 ⇒ 拒绝绑定，申请状态不动")
    void approve_classOwnedByAnother_rejected() {
        TeacherClassApplication a = app(7L, 1L, 990050L, 0, 0);
        when(applicationMapper.selectById(7L)).thenReturn(a);
        when(classesMapper.selectById(1L)).thenReturn(cls(1L, 29L));

        ApiResponse<String> r = controller.approveClassRequest(7L, "teacher");

        assertNotEquals(200, r.getCode());
        assertEquals(0, a.getStatus().intValue(), "被拒的批准不能把申请置为已通过");
        verify(classesMapper, never()).update(any(), any(Wrapper.class));
        verify(applicationMapper, never()).updateById(any(TeacherClassApplication.class));
    }

    /** 申请指向的班级不存在 ⇒ 报错，不写申请行。 */
    @Test
    @DisplayName("申请缺少班级 / 班级不存在 ⇒ 报错且不写库")
    void approve_missingClass_rejected() {
        when(applicationMapper.selectById(7L)).thenReturn(app(7L, null, 990050L, 0, 0));

        assertNotEquals(200, controller.approveClassRequest(7L, "teacher").getCode());
        verify(classesMapper, never()).update(any(), any(Wrapper.class));
        verify(applicationMapper, never()).updateById(any(TeacherClassApplication.class));
    }
}
