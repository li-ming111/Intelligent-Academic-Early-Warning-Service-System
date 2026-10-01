package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.User;
import com.academic.mapper.UserSettingsMapper;
import com.academic.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * `/users/**` 的角色门禁与"改不动"字段的回归钉子。
 *
 * <p>背景（2026-09-30 读码确认）：本控制器原先**一个权限判断都没有**，而网关对
 * `/api/users/**` 也只写在注释里 —— role 1/2/3 走到 `hasPermission` 末尾就 `return true`。
 * 于是**任何已登录用户**都能：
 * <ul>
 *   <li>{@code POST /users/add} —— body 是完整 User 实体、自带 role ⇒ **学生建出系统管理员**；</li>
 *   <li>{@code PUT /users/update/{自己的id}} —— body 带 {@code role:4} ⇒ **自助提权**；</li>
 *   <li>{@code GET /users/by-username/{n}} —— 回**含 BCrypt 密码哈希**的原始实体。</li>
 * </ul>
 *
 * <p>为什么控制器里还要判一次：**网关不是网络边界**。user-service 的 8082 在本机可直连，
 * 直连时网关注入的 {@code X-Role}/{@code X-User-Id} 请求头可以随便伪造 —— 所以这里只认
 * {@code JwtAuthenticationFilter} 从**已验签 JWT 声明**写进 SecurityContext 的东西。
 * 下面的桩就是照那个形状造的。
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final Long ME = 27L;

    @Mock
    private UserService userService;

    @Mock
    private UserSettingsMapper userSettingsMapper;

    @InjectMocks
    private UserController controller;

    @BeforeEach
    void setUp() {
        // 默认按"学生本人"进场；需要别的角色时用例内自己覆盖。
        authenticateAs(1, "2023020616");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 造一个"已验签 token 的 principal"，形状与 JwtAuthenticationFilter 写入的一致。 */
    private void authenticateAs(int role, String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    /** 让 {@code currentUserId()} 能解析出本人。 */
    private void stubMe() {
        when(userService.getByUsername("2023020616")).thenReturn(new User().setId(ME).setUsername("2023020616"));
    }

    // ==================== POST /users/add：提权的那扇门 ====================

    /**
     * ★ 学生 token 带 {@code role:4} 打 /users/add —— 必须被拒，且**一行都不写库**。
     * 这条就是"任何已登录用户都能造管理员"的回归钉子。
     */
    @Test
    void addUser_studentCannotCreateAdmin() {
        User body = new User().setUsername("evil").setPassword("123456").setRole(4).setStatus(1);

        ApiResponse<Long> result = controller.addUser(body);

        assertFalse(result.isSuccess());
        assertEquals(403, result.getCode());
        verify(userService, never()).save(any(User.class));
    }

    /** 管理员带 role:4 是**合法**的（管理员本来就能建账号），不能被上面那条误伤。 */
    @Test
    void addUser_adminCanCreateAdmin() {
        authenticateAs(4, "admin");
        User body = new User().setUsername("adm002").setPassword("123456").setRole(4);
        when(userService.save(body)).thenReturn(true);

        ApiResponse<Long> result = controller.addUser(body);

        assertTrue(result.isSuccess());
        verify(userService, times(1)).save(body);
    }

    // ==================== PUT /users/update/{id}：自助提权的另一扇门 ====================

    /**
     * ★ 学生改**自己**的资料时，body 里的 {@code role}/{@code status}/{@code password}
     * 必须被剥掉（置 null 走 NOT_NULL 策略 = 改不动），姓名/邮箱照常生效。
     */
    @Test
    void updateUser_selfCannotChangeRoleStatusOrPassword() {
        stubMe();
        User body = new User()
                .setName("新名字").setEmail("new@a.com")
                .setRole(4)          // ← 想把自己变成管理员
                .setStatus(0)        // ← 想自批账号
                .setPassword("hack"); // ← 想绕过"改密码要先给旧密码"直接改口令
        when(userService.updateById(any(User.class))).thenReturn(true);

        ApiResponse<String> result = controller.updateUser(ME, body);

        assertTrue(result.isSuccess());
        verify(userService).updateById(argThat(u ->
                ME.equals(u.getId())
                        && "新名字".equals(u.getName())
                        && "new@a.com".equals(u.getEmail())
                        && u.getRole() == null
                        && u.getStatus() == null
                        && u.getPassword() == null));
    }

    /** 学生改**别人**的资料 → 403，且不写库。 */
    @Test
    void updateUser_otherUserRejected() {
        stubMe();
        assertFalse(controller.updateUser(99L, new User().setName("改别人")).isSuccess());
        verify(userService, never()).updateById(any(User.class));
    }

    /** 管理员改别人 → 放行，且**不做**字段剥离。 */
    @Test
    void updateUser_adminKeepsFields() {
        authenticateAs(4, "admin");
        User body = new User().setName("改名").setRole(3);
        when(userService.updateById(any(User.class))).thenReturn(true);

        ApiResponse<String> result = controller.updateUser(99L, body);

        assertTrue(result.isSuccess());
        verify(userService).updateById(argThat(u -> Integer.valueOf(3).equals(u.getRole())));
    }

    // ==================== GET /users/info/{id}：本人 vs 别人 ====================

    @Test
    void getUserInfo_selfAllowed() {
        stubMe();
        when(userService.getById(ME)).thenReturn(new User().setId(ME).setUsername("2023020616").setName("司明"));

        ApiResponse<?> result = controller.getUserInfo(ME);

        assertTrue(result.isSuccess());
        verify(userService, times(1)).getById(ME);
    }

    @Test
    void getUserInfo_otherUserRejected() {
        stubMe();
        assertFalse(controller.getUserInfo(99L).isSuccess());
        verify(userService, never()).getById(99L);
    }

    // ==================== 管理员专用端点 ====================

    @Test
    void adminOnlyEndpoints_rejectStudent() {
        assertFalse(controller.getUsers(1, 10, null).isSuccess());
        assertFalse(controller.getUserCount(null).isSuccess());
        assertFalse(controller.deleteUser(99L).isSuccess());
        verify(userService, never()).page(any(), any());
        verify(userService, never()).removeById(any());
    }

    @Test
    void getUsers_adminAllowed() {
        authenticateAs(4, "admin");
        when(userService.page(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        ApiResponse<Map<String, Object>> result = controller.getUsers(1, 10, null);

        assertTrue(result.isSuccess());
        verify(userService, times(1)).page(any(), any());
    }

    /**
     * ★ /users/by-username 是管理员接口，且**永远不回口令哈希** ——
     * 原先它回整个 User 实体，password 就是库里那串 BCrypt。
     */
    @Test
    void getUserByUsername_adminGetsNoPasswordHash() {
        authenticateAs(4, "admin");
        when(userService.getByUsername("admin"))
                .thenReturn(new User().setId(1L).setUsername("admin").setPassword("$2a$10$shouldnotleak"));

        ApiResponse<User> result = controller.getUserByUsername("admin");

        assertTrue(result.isSuccess());
        assertNull(result.getData().getPassword(), "读接口绝不能把口令哈希发出去");
    }

    @Test
    void getUserByUsername_rejectsStudent() {
        assertFalse(controller.getUserByUsername("admin").isSuccess());
        verify(userService, never()).getByUsername("admin");
    }

    // ==================== 设置：本人 vs 别人 ====================

    @Test
    void settings_selfAllowedButOtherRejected() {
        stubMe();
        when(userSettingsMapper.selectOne(any())).thenReturn(null);

        assertTrue(controller.getSettings(ME).isSuccess());
        assertFalse(controller.getSettings(99L).isSuccess());
    }
}
