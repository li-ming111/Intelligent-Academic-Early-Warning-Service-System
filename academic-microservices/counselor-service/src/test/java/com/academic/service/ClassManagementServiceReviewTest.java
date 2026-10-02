package com.academic.service;

import com.academic.entity.ClassInfo;
import com.academic.entity.CounselorClassApplication;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.CounselorClassApplicationMapper;
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
 * 辅导员班级申请审核：拒绝理由真的落库，且"批准时清掉旧理由"这句不是空话（W1.14，2026-10-02）。
 *
 * <p><b>为什么这里要用 {@code UpdateWrapper} 而不是 {@code updateById(entity)}</b>：
 * MyBatis-Plus 默认的更新策略是 {@code NOT_NULL} —— **值为 null 的字段根本不会出现在
 * UPDATE 语句里**。本工程没有全局改过 update-strategy（各服务 {@code application.yml} 里
 * 只有 {@code map-underscore-to-camel-case: true}），所以：
 * <br>· 用 {@code updateById} + {@code setRejectReason(null)} 想"清掉旧理由" ⇒ 那句是空话，
 *     一条曾经被拒、后来又被批的申请会一直挂着上次的拒绝理由；
 * <br>· 用 {@code updateById} + {@code setRejectReason("")} 想"存空理由" ⇒ 空串会落库，
 *     而前端 {@code v-if="row.rejectReason"} 对空串是**真**，页面上出现一个空白却算"有理由"的块。
 *
 * <p>所以判据必须是**看 SQL 的 SET 子句**，而不是"update 被调用过"。
 * {@code verify(mapper, never()).updateById(any())} 就是钉住这一点的负向对照。
 */
@ExtendWith(MockitoExtension.class)
class ClassManagementServiceReviewTest {

    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private CounselorClassApplicationMapper counselorClassApplicationMapper;
    @Mock private CounselorService counselorService;

    @InjectMocks private ClassManagementService service;

    private static CounselorClassApplication app(long id, Integer applicationType) {
        CounselorClassApplication a = new CounselorClassApplication();
        a.setId(id);
        a.setClassId(100L + id);
        a.setCounselorId(900L + id);
        a.setApplicationType(applicationType);
        a.setStatus(0);
        return a;
    }

    /** 取出那次 {@code update(null, wrapper)} 传下来的 wrapper。 */
    @SuppressWarnings("unchecked")
    private UpdateWrapper<CounselorClassApplication> capturedStatusUpdate() {
        ArgumentCaptor<UpdateWrapper<CounselorClassApplication>> cap =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(counselorClassApplicationMapper).update(isNull(), cap.capture());
        return cap.getValue();
    }

    // ---------------- 拒绝：理由落库 ----------------

    /** 拒绝时理由进 SET 子句，且首尾空白被裁掉。 */
    @Test
    @DisplayName("拒绝：理由进 UPDATE 的 SET 子句（裁掉首尾空白）")
    void reject_putsReasonIntoSetClause() {
        when(counselorClassApplicationMapper.selectById(4L)).thenReturn(app(4L, 0));

        String err = service.reviewApplication(4L, 2, "  名额已满  ");

        assertNull(err, "审核成功时返回 null");

        UpdateWrapper<CounselorClassApplication> w = capturedStatusUpdate();
        assertTrue(w.getSqlSet().contains("status"), "SET 子句要有 status: " + w.getSqlSet());
        assertTrue(w.getSqlSet().contains("reject_reason"), "SET 子句要有 reject_reason: " + w.getSqlSet());
        assertTrue(w.getParamNameValuePairs().containsValue(2), "status 要写成 2");
        assertTrue(w.getParamNameValuePairs().containsValue("名额已满"),
                "理由要以裁掉空白后的形式写进去: " + w.getParamNameValuePairs());

        // 负向对照：一旦有人改回 updateById(entity)，null 字段会被静默跳过
        verify(counselorClassApplicationMapper, never()).updateById(any());
    }

    /**
     * 空白理由 → 显式写 null（不是空串，也不是"不写这一列"）。
     *
     * <p>两个断言各自有靶子：{@code reject_reason} 不在 SET 里 ⇒ 旧理由清不掉；
     * 占位符是空串而不是 null ⇒ 页面上出现一个空白却判定为"有理由"的块
     * （{@code v-if="row.rejectReason"} 对空串是真）。
     */
    @Test
    @DisplayName("拒绝：空白理由显式写 null")
    void reject_blankReasonWritesNullExplicitly() {
        when(counselorClassApplicationMapper.selectById(5L)).thenReturn(app(5L, 0));

        service.reviewApplication(5L, 2, "   ");

        UpdateWrapper<CounselorClassApplication> w = capturedStatusUpdate();
        assertTrue(w.getSqlSet().contains("reject_reason"),
                "即便值是 null，这一列也要出现在 SET 里: " + w.getSqlSet());
        assertEquals(2, w.getParamNameValuePairs().size(),
                "应当恰好两个占位符（status 与 reject_reason）: " + w.getParamNameValuePairs());
        assertTrue(w.getParamNameValuePairs().containsValue(null),
                "空白理由必须写成 null，不能是空串: " + w.getParamNameValuePairs());
        assertFalse(w.getParamNameValuePairs().containsValue(""),
                "空串会落库并被前端当成「有理由」渲染出来: " + w.getParamNameValuePairs());
    }

    /**
     * 批准时**显式把理由清成 null** —— 这正是 {@code updateById} 做不到的那件事。
     */
    @Test
    @DisplayName("批准：显式清空旧的拒绝理由")
    void approve_clearsReasonExplicitly() {
        when(counselorClassApplicationMapper.selectById(6L)).thenReturn(app(6L, 0));
        ClassInfo cls = new ClassInfo();
        cls.setId(106L);
        when(classInfoMapper.selectById(106L)).thenReturn(cls);

        service.reviewApplication(6L, 1, "这条理由不该被采纳");

        UpdateWrapper<CounselorClassApplication> w = capturedStatusUpdate();
        assertTrue(w.getSqlSet().contains("reject_reason"),
                "批准时也要把 reject_reason 写进 SET（写成 null），否则旧理由会一直留着: " + w.getSqlSet());
        assertFalse(w.getParamNameValuePairs().containsValue("这条理由不该被采纳"),
                "批准时不许把拒绝理由写进去");
        verify(counselorClassApplicationMapper, never()).updateById(any());
    }

    // ---------------- 批准：班级归属联动 ----------------

    /** 申请管理（applicationType=0）批准 → 把班级的 counselor_id 设成申请人的。 */
    @Test
    @DisplayName("批准「申请管理」：班级 counselor_id 设为申请人")
    void approve_applyType_setsClassCounselor() {
        when(counselorClassApplicationMapper.selectById(7L)).thenReturn(app(7L, 0));
        ClassInfo cls = new ClassInfo();
        cls.setId(107L);
        when(classInfoMapper.selectById(107L)).thenReturn(cls);

        service.reviewApplication(7L, 1, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<ClassInfo>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(classInfoMapper).update(isNull(), cap.capture());
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue(907L),
                "应把 classes.counselor_id 写成申请人的 id 907: " + cap.getValue().getParamNameValuePairs());
    }

    /** 解除管理（applicationType=1）批准 → 把班级的 counselor_id 置空。 */
    @Test
    @DisplayName("批准「解除管理」：班级 counselor_id 置空")
    void approve_releaseType_clearsClassCounselor() {
        when(counselorClassApplicationMapper.selectById(8L)).thenReturn(app(8L, 1));
        ClassInfo cls = new ClassInfo();
        cls.setId(108L);
        cls.setCounselorId(908L);
        when(classInfoMapper.selectById(108L)).thenReturn(cls);

        service.reviewApplication(8L, 1, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<ClassInfo>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(classInfoMapper).update(isNull(), cap.capture());
        assertTrue(cap.getValue().getSqlSet().contains("counselor_id"),
                "SET 子句要有 counselor_id: " + cap.getValue().getSqlSet());
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue(null),
                "应把 classes.counselor_id 写成 null: " + cap.getValue().getParamNameValuePairs());
    }

    // ---------------- 边界 ----------------

    /** 申请不存在 → 返回业务提示，且一个字节都不写。 */
    @Test
    @DisplayName("申请不存在：返回提示且不写库")
    void review_missingApplicationWritesNothing() {
        when(counselorClassApplicationMapper.selectById(99999L)).thenReturn(null);

        assertEquals("申请不存在", service.reviewApplication(99999L, 2, "随便"));

        verify(counselorClassApplicationMapper, never()).update(any(), any());
        verify(classInfoMapper, never()).update(any(), any());
    }

    /** 拒绝**不动**班级归属（只有批准才联动 classes 表）。 */
    @Test
    @DisplayName("拒绝：不触碰 classes 表")
    void reject_neverTouchesClassesTable() {
        when(counselorClassApplicationMapper.selectById(9L)).thenReturn(app(9L, 0));

        service.reviewApplication(9L, 2, "不通过");

        verify(classInfoMapper, never()).update(any(), any());
    }
}
