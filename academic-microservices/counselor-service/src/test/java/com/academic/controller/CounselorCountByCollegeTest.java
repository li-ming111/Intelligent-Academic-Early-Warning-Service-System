package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.mapper.*;
import com.academic.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * W15（2026-10-04）：{@code GET /counselors/count-by-college} 的 catch 吞异常。
 *
 * <p>原来 {@code catch (Exception e) { return ApiResponse.success(0L); }} —— 查库炸了与
 * "这个学院没有辅导员"回**同一个 200 + 0**，调用方（如学院看板）会把一次故障显示成
 * 一个可信的 0。与 W14 修的 {@code StudentController.countByCollege} 同族，现在如实回错误。
 *
 * <p>判据：{@code isSuccess()==false} 且 {@code data==null} —— 修前必红（修前 success 且 data=0）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CounselorController.countByCollege —— 失败不再伪装成 0（W15）")
class CounselorCountByCollegeTest {

    @Mock private CounselorService counselorService;
    @Mock private AssistancePlanService assistancePlanService;
    @Mock private CommunicationLogService communicationLogService;
    @Mock private CounselorNotificationService counselorNotificationService;
    @Mock private com.academic.common.notify.StudentNotifier studentNotifier;
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

    @Test
    @DisplayName("★ 统计炸了 ⇒ 如实回错误，不再伪装成 success(0)（修前必红）")
    void countByCollegeFailureIsNotZero() {
        when(counselorService.countByCollege(2L)).thenThrow(new RuntimeException("db down"));

        ApiResponse<Long> resp = controller.countByCollege(2L);

        assertFalse(resp.isSuccess(), "查库失败不许回成功: code=" + resp.getCode());
        assertNotEquals(200, resp.getCode());
        assertNull(resp.getData(), "不许回 0 冒充「这个学院没有辅导员」");
    }

    @Test
    @DisplayName("统计成功 ⇒ 正常返回条数（不误伤）")
    void countByCollegeOk() {
        when(counselorService.countByCollege(2L)).thenReturn(3L);

        ApiResponse<Long> resp = controller.countByCollege(2L);

        assertTrue(resp.isSuccess());
        assertEquals(3L, resp.getData());
    }
}
