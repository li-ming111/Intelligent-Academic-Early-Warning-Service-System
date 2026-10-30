package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Classes;
import com.academic.entity.CounselorClassApplication;
import com.academic.mapper.ClassesMapper;
import com.academic.mapper.CounselorClassApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 「批准辅导员班级申请」= 无事务的双表写，已修（W46，2026-10-14）。
 *
 * <p><b>修之前是什么样</b>：{@code approveClassRequest} 要写两张表 ——
 * 先 {@code counselor_class_application.status=1}，再 {@code classes.counselor_id=…}，
 * 但**没有 {@code @Transactional}**。第二笔失败时第一笔不回滚，库里留下
 * "申请已通过、班级却没换辅导员"的半截状态，接口照旧回 {@code "已批准辅导员班级申请"}。
 * 实测复现（另开连接对 {@code classes} 行 {@code FOR UPDATE} 持锁）：持锁期间另一条连接
 * 读到申请行已是 {@code status=1}、而 {@code classes.counselor_id} 仍是 {@code NULL}。
 *
 * <p>与 {@code ClassManagementService.reviewApplication} 同族；台账原先只记了
 * counselor-service 那 4 条，这一条漏在账外。
 *
 * <p><b>为什么断言落在三处</b>（纯单测里没有真的 Spring 代理，光调方法看不出发没发事务）：
 * <br>① 方法上**真的挂了** {@code @Transactional}（反射读注解，承重）；
 * <br>② 失败时**抛出去**而不是 catch 掉返回 error —— 后者会被事务拦截器当成"正常返回"
 *     照样提交，"加事务"就成了一句空话；
 * <br>③ 正常路径两笔写都发生，别把好路径改坏。
 */
@ExtendWith(MockitoExtension.class)
class AdminClassApproveTransactionalTest {

    @Mock private CounselorClassApplicationMapper counselorClassApplicationMapper;
    @Mock private ClassesMapper classesMapper;

    @InjectMocks private AdminController controller;

    private static CounselorClassApplication app(long id, Long classId, Long counselorId) {
        CounselorClassApplication a = new CounselorClassApplication();
        a.setId(id);
        a.setStatus(0);
        a.setClassId(classId);
        a.setCounselorId(counselorId);
        a.setClassName("软件2301班");
        return a;
    }

    /** 承重：方法上必须挂 @Transactional，否则两笔写各自提交，半截状态可达。 */
    @Test
    @DisplayName("批准申请：方法上真的挂了 @Transactional")
    void approve_isTransactional() throws Exception {
        Method m = AdminController.class.getMethod("approveClassRequest", Long.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, "approveClassRequest 写 2 张表，必须带 @Transactional");
        assertTrue(tx.rollbackFor().length > 0 || tx.rollbackForClassName().length > 0
                        || !tx.rollbackFor().equals(new Class[]{Exception.class}),
                "rollbackFor 建议显式写 Exception.class");
    }

    /**
     * 承重：第二笔写炸了 ⇒ 异常必须**冒出去**（而不是被 catch 成 return error）。
     * 吞掉的话事务拦截器看到"正常返回"，第一笔照样提交。
     */
    @Test
    @DisplayName("批准申请：第二笔写失败 ⇒ 异常抛出（不能吞成 return error）")
    void approve_secondWriteFails_throws() {
        when(counselorClassApplicationMapper.selectById(7L)).thenReturn(app(7L, 1L, 990050L));
        when(classesMapper.selectById(1L)).thenReturn(new Classes());
        when(classesMapper.update(isNull(), any(Wrapper.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        assertThrows(Exception.class,
                () -> controller.approveClassRequest(7L, "counselor"),
                "第二笔写失败必须抛出去，否则 @Transactional 会把它当成功提交");
    }

    /** 正常路径：两张表都被写。 */
    @Test
    @DisplayName("批准申请（辅导员）：申请行与班级行都被写")
    void approve_counselor_writesBothTables() {
        CounselorClassApplication a = app(7L, 1L, 990050L);
        when(counselorClassApplicationMapper.selectById(7L)).thenReturn(a);
        Classes cls = new Classes();
        cls.setId(1L);
        when(classesMapper.selectById(1L)).thenReturn(cls);
        when(classesMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(counselorClassApplicationMapper.updateById(a)).thenReturn(1);

        ApiResponse<String> r = controller.approveClassRequest(7L, "counselor");

        assertEquals(200, r.getCode(), "实际: " + r.getMessage());
        assertEquals(1, a.getStatus().intValue(), "申请行应被置为已通过");
        verify(counselorClassApplicationMapper).updateById(a);

        ArgumentCaptor<Wrapper<Classes>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(classesMapper).update(isNull(), cap.capture());
        UpdateWrapper<Classes> bind = (UpdateWrapper<Classes>) cap.getValue();
        assertTrue(bind.getSqlSet().contains("counselor_id"), "实际 SET: " + bind.getSqlSet());
        assertTrue(bind.getParamNameValuePairs().containsValue(990050L),
                "绑定值必须是申请里的辅导员，实际参数: " + bind.getParamNameValuePairs());
    }

    /** 申请不存在 ⇒ 不是 200，且两笔写都不发生。 */
    @Test
    @DisplayName("批准申请：记录不存在 ⇒ 非 200 且不写库")
    void approve_missing_returnsError() {
        when(counselorClassApplicationMapper.selectById(99999L)).thenReturn(null);

        ApiResponse<String> r = controller.approveClassRequest(99999L, "counselor");

        assertNotEquals(200, r.getCode());
        verify(counselorClassApplicationMapper, never()).updateById(any());
        verify(classesMapper, never()).update(any(), any(Wrapper.class));
    }
}
