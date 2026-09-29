package com.academic.service.impl;

import com.academic.common.entity.User;
import com.academic.mapper.UserMapper;
import com.academic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 登录/注册/取用户信息的单元测试。
 *
 * <p>原先这三个用例是空壳：只 mock 了 {@code UserMapper}（构造器注入的另外两个依赖
 * {@code PasswordEncoder}/{@code JwtUtil} 为 null），注册那条还传了个 {@code new Object()}
 * 给一个强转 {@code Map} 的方法 —— 必然抛 ClassCastException，断言"注册成功"永远不可能成立。
 * 因为整个模块的测试源当时编译不过，这些失败长期不可见。现在补齐依赖与桩数据，
 * 让断言真正检验行为。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userService;

    /** BCrypt 哈希密码 + 账号正常 → 登录成功并下发 token。 */
    @Test
    void testLogin_Success() {
        String hash = "$2a$10$abcdefghijklmnopqrstuv";
        User user = new User()
                .setId(27L)
                .setUsername("2023020616")
                .setPassword(hash)
                .setName("司明")
                .setRole(1)
                .setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", hash)).thenReturn(true);
        when(jwtUtil.generateToken(27L, "2023020616", 1)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(27L, "2023020616")).thenReturn("refresh-token");

        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020616");
        request.put("password", "123456");

        Object response = userService.login(request);

        assertInstanceOf(Map.class, response);
        Map<?, ?> map = (Map<?, ?>) response;
        assertEquals("success", map.get("status"));
        assertEquals("access-token", map.get("token"));
        assertEquals("refresh-token", map.get("refreshToken"));
        assertEquals(27L, map.get("userId"));
        assertEquals("司明", map.get("name"));
    }

    /** 密码错 → status=error 且不下发 token。 */
    @Test
    void testLogin_wrongPassword() {
        String hash = "$2a$10$abcdefghijklmnopqrstuv";
        User user = new User()
                .setId(27L)
                .setUsername("2023020616")
                .setPassword(hash)
                .setRole(1)
                .setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", hash)).thenReturn(false);

        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020616");
        request.put("password", "wrong");

        Map<?, ?> map = (Map<?, ?>) userService.login(request);

        assertEquals("error", map.get("status"));
        assertEquals("密码错误", map.get("message"));
        assertNull(map.get("token"));
    }

    /** 用户不存在 → status=error。 */
    @Test
    void testLogin_userNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("username", "不存在");
        request.put("password", "123456");

        Map<?, ?> map = (Map<?, ?>) userService.login(request);

        assertEquals("error", map.get("status"));
        assertEquals("用户不存在", map.get("message"));
    }

    /** 账号待审批（status=0）→ 拒绝登录并给出提示。 */
    @Test
    void testLogin_pendingApproval() {
        User user = new User()
                .setId(30L)
                .setUsername("2023020699")
                .setPassword("$2a$10$abcdefghijklmnopqrstuv")
                .setRole(1)
                .setStatus(0);

        when(userMapper.selectOne(any())).thenReturn(user);

        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020699");
        request.put("password", "123456");

        Map<?, ?> map = (Map<?, ?>) userService.login(request);

        assertEquals("error", map.get("status"));
        assertEquals("账号正在审批中，请等待管理员审核", map.get("message"));
    }

    /** 正常注册 → 落库前密码被加密成 BCrypt 格式。 */
    @Test
    void testRegister() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020701");
        request.put("password", "123456");
        request.put("name", "新同学");
        request.put("role", 1);

        String result = userService.register(request);

        // 服务的真实返回值是英文串；全项目没有任何调用方按字符串判断成败，
        // 原测试断言的"注册成功"从来就不是它返回的东西。
        assertEquals("Register successful", result);

        // 明文密码绝不能落库
        verify(userMapper).insert(argThat(u -> "$2a$10$encoded".equals(u.getPassword())
                && "2023020701".equals(u.getUsername())
                && Integer.valueOf(1).equals(u.getStatus())));
    }

    @Test
    void testRegister_passwordTooShort() {
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020702");
        request.put("password", "123");

        assertEquals("密码长度至少为6位", userService.register(request));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testRegister_duplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(new User().setId(1L).setUsername("2023020703"));

        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020703");
        request.put("password", "123456");

        assertEquals("用户名已存在", userService.register(request));
        verify(userMapper, never()).insert(any(User.class));
    }

    /** 取用户信息：查得到就返回脱敏后的 Map，查不到返回 null。 */
    @Test
    void testGetUserInfo() {
        User user = new User()
                .setId(1L)
                .setUsername("admin")
                .setName("系统管理员")
                .setRole(4)
                .setStatus(1)
                .setPassword("$2a$10$shouldnotleak");

        when(userMapper.selectById(1L)).thenReturn(user);

        Object info = userService.getUserInfo(1L);

        assertInstanceOf(Map.class, info);
        Map<?, ?> map = (Map<?, ?>) info;
        assertEquals(1L, map.get("id"));
        assertEquals("admin", map.get("username"));
        assertEquals("系统管理员", map.get("name"));
        // 返回体里不能带密码
        assertFalse(map.containsKey("password"));
    }

    @Test
    void testGetUserInfo_notFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertNull(userService.getUserInfo(999L));
    }
}
