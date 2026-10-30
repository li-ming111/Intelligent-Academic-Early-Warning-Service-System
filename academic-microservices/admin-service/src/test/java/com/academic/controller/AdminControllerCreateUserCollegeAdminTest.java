package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.common.entity.CollegeAdminProfile;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.User;
import com.academic.mapper.CollegeAdminProfileMapper;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.service.AuditLogService;
import com.academic.service.CollegeService;
import com.academic.service.UserService;
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
 * 管理员建号写 {@code college_admin_profile}（W32，2026-10-09）。
 *
 * <p><b>修之前是什么样</b>：{@code createProfileIfNeeded} 只给 role 1/3 补档案，role 5
 * 的注释写着「必填列里有 {@code college_id}，建号入参里没有学院，故不建」。而
 * {@code college_admin_profile} **全仓只有读和删、零 INSERT** —— 于是**任何**新建的
 * 学院管理员账号都拿不到档案行，{@code CollegeAdminController.getCollegeId()} 回 null，
 * 该控制器 18 个接口一律 403（"能登录、能进界面、每页都没数据"）。
 *
 * <p><b>这里钉四件事</b>：
 * <ol>
 *   <li>role 5 + 学院存在 ⇒ {@code college_admin_profile} **真的插入一行**，且
 *       {@code college_id} 是传进来的那个（承重断言 —— 学院管理端全靠它）；</li>
 *   <li>role 5 + 没给学院 ⇒ 账号照建、**档案不插**，且响应 message **如实说明后果**
 *       （不能只说"创建成功"就让人拿到一个取不到数的账号）；</li>
 *   <li>role 5 + 学院不存在 ⇒ 不插档案（悬空引用比不建更糟）；</li>
 *   <li>其它角色不受影响（role 2 教师的口径见 {@link #role2WithCollegeInsertsTeacherProfile}
 *       与 {@link #role2WithoutCollegeWritesNoProfile}：2026-10-19 建号表单收口
 *       「四角色都选学院」后，教师带学院就建档、没带就不建 —— teacher_profile.college_id
 *       是 NOT NULL，悬空比缺行更糟）。</li>
 * </ol>
 *
 * <p>这不是 Spring 切片测试，是纯 Mockito —— 本模块的其它控制器测试同样是这个写法
 * （不起上下文、不打真库）。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerCreateUserCollegeAdminTest {

    @Mock private UserService userService;
    @Mock private CollegeAdminProfileMapper collegeAdminProfileMapper;
    @Mock private TeacherProfileMapper teacherProfileMapper;
    @Mock private CollegeService collegeService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AdminController controller;

    /** 让 createUser 像真服务一样回填主键。 */
    private void stubCreateOk(long id) {
        when(userService.createUser(any(User.class))).thenAnswer(inv -> {
            inv.getArgument(0, User.class).setId(id);
            return true;
        });
    }

    private College existingCollege(long id) {
        College c = new College();
        c.setId(id);
        c.setName("软件学院");
        return c;
    }

    private CollegeAdminProfile capturedProfileInsert() {
        ArgumentCaptor<CollegeAdminProfile> cap = ArgumentCaptor.forClass(CollegeAdminProfile.class);
        verify(collegeAdminProfileMapper).insert(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("role 5 + 给定学院 ⇒ 真的插入学院管理员档案，college_id 原样写入")
    void role5WithCollegeInsertsProfile() {
        stubCreateOk(300L);
        when(collegeService.getById(1L)).thenReturn(existingCollege(1L));
        when(collegeAdminProfileMapper.selectCount(any())).thenReturn(0L);

        ApiResponse<String> resp = controller.createUser(
                new User().setUsername("collegeadmin9").setPassword("123456").setName("新院长")
                        .setRole(5).setPhone("13900009999").setEmail("dean@academic.edu"),
                1L, null);

        assertTrue(resp.isSuccess());
        CollegeAdminProfile p = capturedProfileInsert();
        assertEquals(300L, p.getUserId());
        assertEquals("新院长", p.getName());
        assertEquals(1L, p.getCollegeId(), "college_id 必须写进去 —— 学院管理端就是靠它定位本学院");
        assertEquals("13900009999", p.getPhone());
        assertEquals("dean@academic.edu", p.getEmail());
    }

    @Test
    @DisplayName("role 5 + 没给学院 ⇒ 账号建出来、档案不插，且 message 如实说明后果")
    void role5WithoutCollegeSkipsProfileButWarns() {
        stubCreateOk(301L);

        ApiResponse<String> resp = controller.createUser(
                new User().setUsername("collegeadmin10").setPassword("123456").setName("无院院长").setRole(5),
                null, null);

        assertTrue(resp.isSuccess(), "没给学院不该让建号失败");
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
        assertTrue(resp.getMessage().contains("未绑定学院"),
                "必须如实说明，不能只说'创建成功'：实际消息=" + resp.getMessage());
    }

    @Test
    @DisplayName("role 5 + 学院不存在 ⇒ 不插档案（悬空引用比不建更糟）")
    void role5WithUnknownCollegeSkipsProfile() {
        stubCreateOk(302L);
        when(collegeService.getById(999L)).thenReturn(null);

        ApiResponse<String> resp = controller.createUser(
                new User().setUsername("collegeadmin11").setPassword("123456").setName("幽灵院长").setRole(5),
                999L, null);

        assertTrue(resp.isSuccess());
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
    }

    @Test
    @DisplayName("role 2 + 有效学院 ⇒ 插入教师档案，college_id 原样写入")
    void role2WithCollegeInsertsTeacherProfile() {
        stubCreateOk(303L);
        when(collegeService.getById(1L)).thenReturn(existingCollege(1L));
        when(teacherProfileMapper.selectCount(any())).thenReturn(0L);

        ApiResponse<String> resp = controller.createUser(
                new User().setUsername("10088").setPassword("123456").setName("王老师").setRole(2),
                1L, null);

        assertTrue(resp.isSuccess());
        ArgumentCaptor<TeacherProfile> cap = ArgumentCaptor.forClass(TeacherProfile.class);
        verify(teacherProfileMapper).insert(cap.capture());
        TeacherProfile p = cap.getValue();
        assertEquals(303L, p.getUserId());
        assertEquals("王老师", p.getName());
        assertEquals(1L, p.getCollegeId(), "teacher_profile.college_id 是 NOT NULL，教师端按学院取数全靠它");
    }

    @Test
    @DisplayName("role 2 + 没给学院 ⇒ 不插教师档案（college_id NOT NULL，悬空比缺行更糟），也不查学院")
    void role2WithoutCollegeWritesNoProfile() {
        stubCreateOk(305L);

        ApiResponse<String> resp = controller.createUser(
                new User().setUsername("10089").setPassword("123456").setName("李老师").setRole(2),
                null, null);

        assertTrue(resp.isSuccess());
        verify(teacherProfileMapper, never()).insert(any(TeacherProfile.class));
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
        // collegeId 为 null 时短路，不该去查学院
        verify(collegeService, never()).getById(any());
    }

    @Test
    @DisplayName("已经有档案行时不重复插（幂等）")
    void existingProfileNotReinserted() {
        stubCreateOk(304L);
        when(collegeService.getById(1L)).thenReturn(existingCollege(1L));
        when(collegeAdminProfileMapper.selectCount(any())).thenReturn(1L);

        controller.createUser(
                new User().setUsername("collegeadmin12").setPassword("123456").setName("老院长").setRole(5),
                1L, null);

        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
    }
}
