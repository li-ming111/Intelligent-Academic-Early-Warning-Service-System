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
