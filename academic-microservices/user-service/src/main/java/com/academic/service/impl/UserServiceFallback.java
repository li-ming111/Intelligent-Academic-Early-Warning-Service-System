package com.academic.service.impl;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.User;
import com.academic.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户服务降级处理类
 *
 * <p><b>⚠️ 2026-10-01 第十七轮查证：本类是个死 bean，永远不会被调用。</b>
 * 它只是个普通的 {@code @Component}，本仓<b>没有任何</b> {@code fallbackClass} /
 * {@code fallback =} 指向它（全仓 grep {@code fallbackClass|blockHandlerClass} 零命中），
 * 而 {@link UserServiceImpl} 标了 {@code @Primary} —— 容器里每个 {@code UserService}
 * 注入点拿到的都是 impl。
 *
 * <p>它<b>唯一的作用</b>是"接口多一个方法就得跟着改一遍"：{@code UserController} 的
 * 构造函数注释里就记着这件事逼出来的一个绕行写法（那里干脆直接注入 Mapper 来躲开它）。
 *
 * <p>本仓真正生效的降级是<b>另一套</b>：{@code @SentinelResource(fallback = "方法名")}
 * （注意是<b>方法名</b>，不是类），以及各调用点自己的 try/catch。
 * 详见《答辩准备-启动与演示手册》§6.17。
 */
@Component
public class UserServiceFallback implements UserService {

    @Override
    public User getById(Long id) {
        return null;
    }

    @Override
    public User getByUsername(String username) {
        return null;
    }

    @Override
    public boolean save(User user) {
        return false;
    }

    @Override
    public boolean updateById(User user) {
        return false;
    }

    @Override
    public boolean removeById(Long id) {
        return false;
    }

    @Override
    public Page<User> page(Page<User> page, QueryWrapper<User> queryWrapper) {
        return new Page<>();
    }

    @Override
    public List<User> list(QueryWrapper<User> queryWrapper) {
        return Collections.emptyList();
    }

    @Override
    public long count(QueryWrapper<User> queryWrapper) {
        return 0;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public Object login(Map<String, Object> request) {
        return ApiResponse.error("登录服务暂时不可用，请稍后重试");
    }

    @Override
    public Object login(Map<String, Object> request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        // 降级路径下没有"登录成功"这回事，自然也没有 login_logs 可写
        return ApiResponse.error("登录服务暂时不可用，请稍后重试");
    }

    @Override
    public String register(Object request, Integer role) {
        // 降级时**抛异常**，不返回字符串 —— 返回的字符串会被 controller 包成
        // ApiResponse.success(...)，于是"服务不可用"在响应里变成 success:true（修复前的形状）。
        throw new IllegalStateException("注册服务暂时不可用，请稍后重试");
    }

    @Override
    public Object getUserInfo(Long userId) {
        return ApiResponse.error("获取用户信息服务暂时不可用，请稍后重试");
    }

    @Override
    public Object logout(Long userId, String role) {
        return ApiResponse.error("登出服务暂时不可用，请稍后重试");
    }

    @Override
    public Object refreshToken(String refreshToken) {
        return ApiResponse.error("令牌刷新服务暂时不可用，请稍后重试");
    }
    
    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        throw new RuntimeException("密码修改服务暂时不可用");
    }
}
