package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.common.entity.Student;
// ⚠️ 这里是 admin-service 自己那份 com.academic.entity.Classes（不是 academic-common 的同名类）——
//    AdminController 里 `import com.academic.entity.*;` 把它引了进来。全仓有两个 Classes 实体，
//    改这个文件的引用时先看清是哪一个（W7 在 CounselorProfile/WarningRule 上踩过同一个坑）。
import com.academic.entity.Classes;
import com.academic.entity.WarningRule;
import com.academic.mapper.ClassesMapper;
import com.academic.service.AuditLogService;
import com.academic.service.CollegeService;
import com.academic.service.StudentService;
import com.academic.service.WarningRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「更新请求体里一个可更新字段都没有」不能当成服务端错误（W8，2026-10-03）。
 *
 * <p><b>修之前是什么样</b>：客户端发 {@code {}}（或字段名与实体对不上、被 Jackson 静默忽略）时，
 * 实体里除主键外全是 null，MyBatis-Plus 的 {@code updateById} 生成
 * {@code UPDATE colleges    WHERE id=?} —— **没有 SET 子句**，MySQL 报语法错。
 * 实测两条服务上分别是 HTTP 500，以及 HTTP 200 + body code 500（还把整段 SQL 回给了客户端）。
 *
 * <p><b>现在</b>：在碰数据库**之前**判掉，回 {@code code 400}。这里钉三件事：
 * <br>① 空体 ⇒ 400（不是 404、不是 500）；
 * <br>② **压根没调 update 方法** —— 否则等于"报 400 但库其实被改了"；
 * <br>③ 负向对照：带一个真列的请求仍然走原路径（别把正常更新拦成 400）。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerEmptyUpdateBodyTest {

    @Mock private CollegeService collegeService;
    @Mock private WarningRuleService warningRuleService;
    @Mock private AuditLogService auditLogService;
    @Mock private ClassesMapper classesMapper;
    @Mock private StudentService studentService;

    @InjectMocks private AdminController controller;

    @Test
    @DisplayName("学院：空请求体 → 400，且压根没碰数据库")
    void updateCollege_emptyBody_returns400_andNeverTouchesDb() {
        ApiResponse<String> resp = controller.updateCollege(1L, new College());

        assertEquals(400, resp.getCode(), "空请求体是客户端的错，不能报成功、也不该报 404");
        verify(collegeService, never()).updateById(any(College.class));
    }

    @Test
    @DisplayName("学院：只有 @TableField(exist=false) 的字段有值 → 也是空 SET 子句 → 400")
    void updateCollege_onlyNonColumnFields_returns400() {
        College body = new College();
        body.setStudentCount(120);   // 前端详情页会带回来，但库里没有这一列
        body.setTeacherCount(30);
        body.setCounselorCount(4);

        assertEquals(400, controller.updateCollege(1L, body).getCode());
        verify(collegeService, never()).updateById(any(College.class));
    }

    @Test
    @DisplayName("负向对照：带一个真列的更新照常走到 service，不再被 400 拦下")
    void updateCollege_realField_stillReachesService() {
        when(collegeService.updateById(any(College.class))).thenReturn(true);

        College body = new College();
        body.setName("计算机学院");
        assertEquals(200, controller.updateCollege(1L, body).getCode());

        verify(collegeService).updateById(any(College.class));
    }

    @Test
    @DisplayName("预警规则：空请求体 → 400，且**不写审计**")
    void updateRule_emptyBody_returns400_andSkipsAudit() {
        assertEquals(400, controller.updateRule(1L, new WarningRule()).getCode());

        verify(warningRuleService, never()).updateById(any(WarningRule.class));
        verify(auditLogService, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("班级：空请求体 → 400，且没调 mapper")
    void updateClass_emptyBody_returns400() {
        assertEquals(400, controller.updateClass(1L, new Classes()).getCode());

        verify(classesMapper, never()).updateById(any(Classes.class));
    }

    @Test
    @DisplayName("学生：空请求体 → 400（这条原先落到 catch 里，回的是 200 + code 500）")
    void updateStudent_emptyBody_returns400() {
        assertEquals(400, controller.updateStudent(1L, new Student()).getCode());

        verify(studentService, never()).updateById(any(Student.class));
    }
}
