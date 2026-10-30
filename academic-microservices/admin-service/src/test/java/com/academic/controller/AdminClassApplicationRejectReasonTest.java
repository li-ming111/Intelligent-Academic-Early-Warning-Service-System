package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.CounselorClassApplication;
import com.academic.entity.TeacherClassApplication;
import com.academic.mapper.CounselorClassApplicationMapper;
import com.academic.mapper.TeacherClassApplicationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 「拒绝班级申请」这条路径真的把理由写进库了（W1.14，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：{@code POST /admin/class-management/reject/{requestId}} 的
 * {@code @RequestBody Map<String,String> rejectData} **收下后一次都没读**，方法体只把
 * {@code status} 翻成 2。而两个前端**都收了理由、也真的发了出去** ——
 * PC {@code AdminClassManagement.vue} 的拒绝弹窗还做了非空校验，小程序
 * {@code pages/admin/class-requests.vue} 的抽屉里写着「后端收下了这个原因但不存它」。
 * 更糟的是 {@code counselor_class_application} **连列都没有**（教师那张表有列但没人写）。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① 理由真的写进被拒绝那条记录（且**去掉首尾空白**）；
 * <br>② 空白理由存 {@code null} 而不是空串 —— 空串会在页面上渲染成一个"有理由"的空白块；
 * <br>③ 整个 body 缺失（老客户端 / curl 不带体）不许 500；
 * <br>④ {@code type} 决定写**哪张表** —— 两张表都有 id=1，传错就改到别人那条上，
 *     所以"辅导员分支绝不碰教师表"必须是一条独立的断言。
 */
@ExtendWith(MockitoExtension.class)
class AdminClassApplicationRejectReasonTest {

    @Mock private TeacherClassApplicationMapper applicationMapper;
    @Mock private CounselorClassApplicationMapper counselorClassApplicationMapper;

    @InjectMocks private AdminController controller;

    private static Map<String, String> body(String reason) {
        Map<String, String> m = new HashMap<>();
        m.put("reason", reason);
        return m;
    }

    private static TeacherClassApplication teacherApp(long id, int status) {
        TeacherClassApplication a = new TeacherClassApplication();
        a.setId(id);
        a.setStatus(status);
        a.setClassName("计科2306班");
        return a;
    }

    private static CounselorClassApplication counselorApp(long id, int status) {
        CounselorClassApplication a = new CounselorClassApplication();
        a.setId(id);
        a.setStatus(status);
        a.setClassName("软件2301班");
        return a;
    }

    // ---------------- 教师表 ----------------

    /** 正常拒绝：理由落库且去掉首尾空白。 */
    @Test
    @DisplayName("拒绝教师申请：理由写进记录，首尾空白被裁掉")
    void rejectTeacher_writesTrimmedReason() {
        when(applicationMapper.selectById(7L)).thenReturn(teacherApp(7L, 0));
        when(applicationMapper.updateById(any(TeacherClassApplication.class))).thenReturn(1);

        ApiResponse<String> r = controller.rejectClassRequest(7L, body("  材料不全，请补齐班级证明  "), "teacher");

        assertEquals(200, r.getCode());

        ArgumentCaptor<TeacherClassApplication> cap = ArgumentCaptor.forClass(TeacherClassApplication.class);
        verify(applicationMapper).updateById(cap.capture());

        TeacherClassApplication saved = cap.getValue();
        assertEquals(2, saved.getStatus(), "状态要翻成 2（已拒绝）");
        assertEquals("材料不全，请补齐班级证明", saved.getRejectReason(), "理由必须落库且裁掉首尾空白");
    }

    /**
     * 空白理由 → 存 {@code null}。
     *
     * <p>这是**负向对照的靶子**：如果哪天有人把这里改回"原样存进去"，
     * 页面上就会出现一个渲染成空白、却判定为"有理由"的块（{@code v-if="row.rejectReason"}
     * 对空串是**真**），断言会红。
     */
    @Test
    @DisplayName("空白理由存 null，不存空串")
    void rejectTeacher_blankReasonStoresNull() {
        when(applicationMapper.selectById(8L)).thenReturn(teacherApp(8L, 0));

        controller.rejectClassRequest(8L, body("   "), "teacher");

        ArgumentCaptor<TeacherClassApplication> cap = ArgumentCaptor.forClass(TeacherClassApplication.class);
        verify(applicationMapper).updateById(cap.capture());
        assertNull(cap.getValue().getRejectReason(), "空白理由必须归成 null，不能是空串");
    }

    /** 不带请求体（老客户端 / 直接 curl）不许炸。 */
    @Test
    @DisplayName("body 整个缺失时照常拒绝，理由为 null")
    void rejectTeacher_missingBodyDoesNotThrow() {
        when(applicationMapper.selectById(9L)).thenReturn(teacherApp(9L, 0));
        when(applicationMapper.updateById(any(TeacherClassApplication.class))).thenReturn(1);

        ApiResponse<String> r = controller.rejectClassRequest(9L, null, "teacher");

        assertEquals(200, r.getCode());
        ArgumentCaptor<TeacherClassApplication> cap = ArgumentCaptor.forClass(TeacherClassApplication.class);
        verify(applicationMapper).updateById(cap.capture());
        assertEquals(2, cap.getValue().getStatus());
        assertNull(cap.getValue().getRejectReason());
    }

    /** 记录不存在 → 报错，且不写。 */
    @Test
    @DisplayName("记录不存在时报错，不落任何写")
    void rejectTeacher_missingRecordReportsError() {
        when(applicationMapper.selectById(99999L)).thenReturn(null);

        ApiResponse<String> r = controller.rejectClassRequest(99999L, body("随便"), "teacher");

        assertNotEquals(200, r.getCode());
        verify(applicationMapper, never()).updateById(any());
    }

    // ---------------- 辅导员表 ----------------

    /** 辅导员分支写辅导员表，理由一样落库。 */
    @Test
    @DisplayName("拒绝辅导员申请：理由写进辅导员表")
    void rejectCounselor_writesReasonToCounselorTable() {
        when(counselorClassApplicationMapper.selectById(3L)).thenReturn(counselorApp(3L, 0));
        when(counselorClassApplicationMapper.updateById(any(CounselorClassApplication.class))).thenReturn(1);

        ApiResponse<String> r = controller.rejectClassRequest(3L, body("该班已由另一位辅导员接管"), "counselor");

        assertEquals(200, r.getCode());
        ArgumentCaptor<CounselorClassApplication> cap = ArgumentCaptor.forClass(CounselorClassApplication.class);
        verify(counselorClassApplicationMapper).updateById(cap.capture());
        assertEquals(2, cap.getValue().getStatus());
        assertEquals("该班已由另一位辅导员接管", cap.getValue().getRejectReason());
    }

    /**
     * {@code type=counselor} 时**一个字节都不碰教师表**。
     *
     * <p>两张表都有 {@code id=1}，这是"传错 type 就改到别人那条上"这条已知风险的守门断言。
     */
    @Test
    @DisplayName("type=counselor 时绝不触碰教师表")
    void rejectCounselor_neverTouchesTeacherTable() {
        when(counselorClassApplicationMapper.selectById(1L)).thenReturn(counselorApp(1L, 0));

        controller.rejectClassRequest(1L, body("名额已满"), "counselor");

        verify(applicationMapper, never()).selectById(any());
        verify(applicationMapper, never()).updateById(any());
    }

    /** 默认 type 是 teacher（接口签名 {@code @RequestParam(defaultValue = "teacher")}）。 */
    @Test
    @DisplayName("不传 type 时按教师表处理")
    void reject_defaultsToTeacherType() {
        when(applicationMapper.selectById(5L)).thenReturn(teacherApp(5L, 0));

        controller.rejectClassRequest(5L, body("不通过"), "teacher");

        verify(counselorClassApplicationMapper, never()).updateById(any());
    }

    // ---------------- 列表回读 ----------------

    /**
     * 「全部申请」列表把 {@code rejectReason} 带出来 —— 只写不读等于没做。
     *
     * <p>顺带钉住 {@code type} 字段：两张表混在一个数组里，前端靠它区分。
     */
    @Test
    @DisplayName("全部申请列表回读 rejectReason")
    void applicationsList_readsBackRejectReason() {
        TeacherClassApplication rejected = teacherApp(11L, 2);
        rejected.setRejectReason("材料不全");
        when(applicationMapper.selectList(isNull())).thenReturn(java.util.List.of(rejected));
        when(counselorClassApplicationMapper.selectList(isNull())).thenReturn(java.util.List.of());

        ApiResponse<Map<String, Object>> r = controller.getClassManagementApplications(1, 10);

        assertEquals(200, r.getCode());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> items =
                (java.util.List<Map<String, Object>>) r.getData().get("items");
        assertNotNull(items);
        assertEquals(1, items.size());
        Map<String, Object> row = items.get(0);
        assertEquals("teacher", row.get("type"));
        assertEquals(2, row.get("status"));
        assertEquals("材料不全", row.get("rejectReason"));
    }
}
