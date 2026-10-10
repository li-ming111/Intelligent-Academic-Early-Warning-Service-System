package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.common.entity.CollegeAdminProfile;
import com.academic.common.entity.User;
import com.academic.mapper.CollegeAdminProfileMapper;
import com.academic.service.AuditLogService;
import com.academic.service.CollegeService;
import com.academic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 「权限管理 → 用户权限」里把账号改成学院管理员（W33，2026-10-09）。
 *
 * <p><b>修之前是什么样</b>：{@code assignRole} 只有两句 ——
 * {@code user.setRole(role); userService.updateById(user)}。它**从不问学院、也从不碰
 * {@code college_admin_profile}**。而 {@code CollegeAdminController.getCollegeId(userId)}
 * 就是读那张表定位学院的（回 null ⇒ 该控制器 18 个接口一律 403）。于是从这里把某人改成
 * 学院管理员 = 造出一个「能登录、能进界面、每页没数据」的账号 —— 与 W32 修掉的建号那条
 * 是同一个坑，只是换了个入口。用户的原话是"学院管理员端肯定要求学院啊"。
 *
 * <p><b>这里钉三类事</b>：
 * <ol>
 *   <li>role 5 必须选学院，且**真的把档案行写对**（含 upsert：已有行要改，不能插第二行）；</li>
 *   <li>降级要**清掉**残留档案行 —— 直连绕过网关时那行仍会放行学院端，而
 *       {@code CollegeAdminController} 只校验"是不是本人"、**不核对 role**；</li>
 *   <li>越权闸：只有系统管理员(role 4)可调。此前**学院管理员也能改全库账号角色**
 *       （网关对 role 5 放行整个 {@code /api/admin/**}，而 {@code /api/permissions/**}
 *       那道闸的前缀对不上真实路径 {@code /api/admin/permissions/...}）。</li>
 * </ol>
 *
 * <p>纯 Mockito，不起 Spring 上下文 —— 本模块同族测试（{@code AdminControllerCreateUserCollegeAdminTest}）
 * 就是这个写法。⚠️ 正因为没有事务代理，"校验失败 ⇒ 一行都不写"必须靠**代码里的顺序**
 * 成立，不能指望 {@code @Transactional} 回滚，所以下面的 {@code never()} 断言是承重的。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerAssignRoleTest {

    @Mock private UserService userService;
    @Mock private CollegeAdminProfileMapper collegeAdminProfileMapper;
    @Mock private CollegeService collegeService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AdminController controller;

    // ==================== 小工具 ====================

    /** 造一个"已登录的系统管理员"请求（闸读的就是这个头）。 */
    private HttpServletRequest adminRequest() {
        return requestWithRole("4");
    }

    private HttpServletRequest requestWithRole(String role) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Role")).thenReturn(role);
        return req;
    }

    private static Map<String, Object> body(Object userId, Object role) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("role", role);
        return m;
    }

    private static Map<String, Object> body(Object userId, Object role, Object collegeId) {
        Map<String, Object> m = body(userId, role);
        m.put("collegeId", collegeId);
        return m;
    }

    private User existingUser(long id, int role) {
        User u = new User();
        u.setId(id);
        u.setUsername("w33user" + id);
        u.setName("测试用户");
        u.setRole(role);
        return u;
    }

    private College existingCollege(long id) {
        College c = new College();
        c.setId(id);
        c.setName("软件学院");
        return c;
    }

    /** 让"改角色"这一步像真服务一样成功 —— Mockito 对 boolean 默认回 false，不桩就会走异常分支。 */
    private void stubUpdateOk() {
        when(userService.updateUser(any(User.class))).thenReturn(true);
    }

    private CollegeAdminProfile capturedInsert() {
        ArgumentCaptor<CollegeAdminProfile> cap = ArgumentCaptor.forClass(CollegeAdminProfile.class);
        verify(collegeAdminProfileMapper).insert(cap.capture());
        return cap.getValue();
    }

    private CollegeAdminProfile capturedProfileUpdate() {
        ArgumentCaptor<CollegeAdminProfile> cap = ArgumentCaptor.forClass(CollegeAdminProfile.class);
        verify(collegeAdminProfileMapper).updateById(cap.capture());
        return cap.getValue();
    }

    // ==================== ① role 5：必须选学院、且真的写对档案 ====================

    @Test
    @DisplayName("★ 承重：role 5 + 学院存在 ⇒ 真的插入学院管理员档案，college_id 原样写入")
    void role5InsertsProfile() {
        stubUpdateOk();
        when(userService.getById(300L)).thenReturn(existingUser(300L, 1));
        when(collegeService.getById(1L)).thenReturn(existingCollege(1L));
        when(collegeAdminProfileMapper.selectList(any())).thenReturn(List.of());

        ApiResponse<String> resp = controller.assignRole(body(300L, 5, 1), adminRequest());

        assertTrue(resp.isSuccess());
        CollegeAdminProfile p = capturedInsert();
        assertEquals(300L, p.getUserId());
        assertEquals(1L, p.getCollegeId(), "college_id 必须写进去 —— 学院管理端就是靠它定位本学院");
        assertEquals("测试用户", p.getName());
        // 角色本身也要真的落库
        ArgumentCaptor<User> ucap = ArgumentCaptor.forClass(User.class);
        verify(userService).updateUser(ucap.capture());
        assertEquals(5, ucap.getValue().getRole());
    }

    /**
     * ⚠️ {@code ApiResponse.success(String)} 是**单参**重载 {@code success(T data)} ——
     * 它把这句话塞进 {@code data}，{@code message} 仍是 "success"。W32 在 createUser 上
     * 已经踩过一次，这里钉住"回执走 message"。
     */
    @Test
    @DisplayName("回执文案走 message 而不是 data（单参重载的坑）")
    void successMessageGoesToMessageField() {
        stubUpdateOk();
        when(userService.getById(301L)).thenReturn(existingUser(301L, 3));
        when(collegeService.getById(2L)).thenReturn(existingCollege(2L));
        when(collegeAdminProfileMapper.selectList(any())).thenReturn(List.of());

        ApiResponse<String> resp = controller.assignRole(body(301L, 5, 2), adminRequest());

        assertNull(resp.getData(), "不能把回执文案塞进 data");
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("软件学院"), "实际: " + resp.getMessage());
    }

    @Test
    @DisplayName("role 5 + 没给学院 ⇒ 报错，且 role/档案**一行都没写**")
    void role5WithoutCollegeWritesNothing() {
        when(userService.getById(302L)).thenReturn(existingUser(302L, 1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.assignRole(body(302L, 5), adminRequest()));

        assertTrue(ex.getMessage().contains("学院"), "实际: " + ex.getMessage());
        verify(userService, never()).updateUser(any(User.class));
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
        verify(collegeAdminProfileMapper, never()).delete(any());
    }

    @Test
    @DisplayName("role 5 + 学院不存在 ⇒ 报错，且 role/档案**一行都没写**（不造悬空引用）")
    void role5WithUnknownCollegeWritesNothing() {
        when(userService.getById(303L)).thenReturn(existingUser(303L, 2));
        when(collegeService.getById(999L)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.assignRole(body(303L, 5, 999L), adminRequest()));

        assertTrue(ex.getMessage().contains("学院不存在"), "实际: " + ex.getMessage());
        verify(userService, never()).updateUser(any(User.class));
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
    }

    /**
     * {@code college_admin_profile.user_id} 上**没有唯一约束**（只有普通索引），
     * 所以已经有行时必须改、不能再插 —— 插了就是同一个 user 两份档案，
     * {@code selectByUserId} 那种单行查询会随机取到一份（取到旧学院就白改了）。
     */
    @Test
    @DisplayName("role 5 + 已有档案行 ⇒ 走 update 改 college_id，绝不再插一行")
    void role5WithExistingProfileUpdatesInsteadOfInserting() {
        stubUpdateOk();
        when(userService.getById(304L)).thenReturn(existingUser(304L, 5));
        when(collegeService.getById(3L)).thenReturn(existingCollege(3L));
        CollegeAdminProfile old = new CollegeAdminProfile();
        old.setId(77L);
        old.setUserId(304L);
        old.setCollegeId(1L);
        old.setName("测试用户");
        old.setTitle("院长");
        when(collegeAdminProfileMapper.selectList(any())).thenReturn(List.of(old));

        controller.assignRole(body(304L, 5, 3L), adminRequest());

        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
        CollegeAdminProfile patch = capturedProfileUpdate();
        assertEquals(77L, patch.getId());
        assertEquals(3L, patch.getCollegeId(), "换学院要真改掉");
        // 只带 id+要改的两列：把整行回写会把旧的 updatedAt 一起写回去，
        // 而 ON UPDATE CURRENT_TIMESTAMP 只在"没显式给值"时才自动更新。
        assertNull(patch.getUpdatedAt(), "不要把查出来的旧时间戳回写");
    }

    // ==================== ② 降级：清掉残留档案行 ====================

    @Test
    @DisplayName("★ role 5 改成别的角色 ⇒ 确实删掉档案行，并在回执里说明已清除绑定")
    void downgradeFromRole5DeletesProfile() {
        stubUpdateOk();
        when(userService.getById(305L)).thenReturn(existingUser(305L, 5));
        when(collegeAdminProfileMapper.selectCount(any())).thenReturn(1L);
        when(collegeAdminProfileMapper.delete(any())).thenReturn(1);

        ApiResponse<String> resp = controller.assignRole(body(305L, 1), adminRequest());

        assertTrue(resp.isSuccess());
        verify(collegeAdminProfileMapper).delete(any());
        assertTrue(resp.getMessage().contains("清除学院绑定"), "实际: " + resp.getMessage());
    }

    /**
     * 反面：W32 之前建出来的 role 5 账号**本来就没有档案行**。那时若还照着
     * "上一个角色是 5"去说"已清除学院绑定"，就是谎报（"说法三层"规矩）。
     */
    @Test
    @DisplayName("降级但本来就没有档案行 ⇒ 不发 DELETE，且如实说'本就没有学院绑定'")
    void downgradeWithoutProfileSaysSoHonestly() {
        stubUpdateOk();
        when(userService.getById(306L)).thenReturn(existingUser(306L, 5));
        when(collegeAdminProfileMapper.selectCount(any())).thenReturn(0L);

        ApiResponse<String> resp = controller.assignRole(body(306L, 1), adminRequest());

        assertTrue(resp.isSuccess());
        verify(collegeAdminProfileMapper, never()).delete(any());
        assertTrue(resp.getMessage().contains("本就没有学院绑定"), "实际: " + resp.getMessage());
    }

    @Test
    @DisplayName("普通改角色（本来就不是学院管理员）⇒ 不做任何档案写、也不查学院")
    void plainRoleChangeTouchesNoProfile() {
        stubUpdateOk();
        when(userService.getById(307L)).thenReturn(existingUser(307L, 1));
        when(collegeAdminProfileMapper.selectCount(any())).thenReturn(0L);

        ApiResponse<String> resp = controller.assignRole(body(307L, 2), adminRequest());

        assertTrue(resp.isSuccess());
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
        verify(collegeAdminProfileMapper, never()).delete(any());
        verify(collegeService, never()).getById(any());
        ArgumentCaptor<User> ucap = ArgumentCaptor.forClass(User.class);
        verify(userService).updateUser(ucap.capture());
        assertEquals(2, ucap.getValue().getRole());
    }

    // ==================== ③ 越权闸 ====================

    @Test
    @DisplayName("★ 学院管理员(5)调该端点 ⇒ 403，且一行都不写")
    void collegeAdminCallerIsForbidden() {
        ApiResponse<String> resp = controller.assignRole(body(308L, 1), requestWithRole("5"));

        assertEquals(403, resp.getCode());
        assertEquals(403, ApiResponse.forbidden("x").getCode(), "确认 forbidden 的 code 就是 403");
        assertTrue(resp.getMessage().contains("系统管理员"), "实际: " + resp.getMessage());
        verify(userService, never()).getById(any());
        verify(userService, never()).updateUser(any(User.class));
        verify(collegeAdminProfileMapper, never()).insert(any(CollegeAdminProfile.class));
        verify(collegeAdminProfileMapper, never()).delete(any());
    }

    @Test
    @DisplayName("学生(1)/教师(2) 调该端点 ⇒ 403")
    void nonAdminCallersAreForbidden() {
        assertEquals(403, controller.assignRole(body(309L, 5, 1), requestWithRole("1")).getCode());
        assertEquals(403, controller.assignRole(body(309L, 5, 1), requestWithRole("2")).getCode());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("缺 X-Role 头（未经网关 / 直连）⇒ 拒绝，fail-closed")
    void missingRoleHeaderIsForbidden() {
        assertEquals(403, controller.assignRole(body(310L, 1), requestWithRole(null)).getCode());
        assertEquals(403, controller.assignRole(body(310L, 1), requestWithRole("")).getCode());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("★ 反向对照：管理员(4)调 ⇒ 放行（别把管理员自己也拦了）")
    void adminCallerIsAllowed() {
        stubUpdateOk();
        when(userService.getById(311L)).thenReturn(existingUser(311L, 1));
        when(collegeAdminProfileMapper.selectCount(any())).thenReturn(0L);

        ApiResponse<String> resp = controller.assignRole(body(311L, 3), adminRequest());

        assertTrue(resp.isSuccess());
        verify(userService).updateUser(any(User.class));
    }

    @Test
    @DisplayName("role 5 + collegeId 传成数字字符串（JSON 里可能是 \"1\"）也能解析")
    void collegeIdAsStringIsAccepted() {
        stubUpdateOk();
        when(userService.getById(312L)).thenReturn(existingUser(312L, 1));
        when(collegeService.getById(1L)).thenReturn(existingCollege(1L));
        when(collegeAdminProfileMapper.selectList(any())).thenReturn(List.of());

        ApiResponse<String> resp = controller.assignRole(body(312L, 5, "1"), adminRequest());

        assertTrue(resp.isSuccess());
        assertEquals(1L, capturedInsert().getCollegeId());
    }
}
