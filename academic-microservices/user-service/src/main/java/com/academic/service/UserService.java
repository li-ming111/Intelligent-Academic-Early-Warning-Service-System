package com.academic.service;

import com.academic.common.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 根据ID获取用户
     */
    User getById(Long id);

    /**
     * 根据用户名获取用户
     */
    User getByUsername(String username);

    /**
     * 保存用户
     */
    boolean save(User user);

    /**
     * 更新用户
     */
    boolean updateById(User user);

    /**
     * 删除用户
     */
    boolean removeById(Long id);

    /**
     * 分页查询用户
     */
    Page<User> page(Page<User> page, QueryWrapper<User> queryWrapper);

    /**
     * 查询用户列表
     */
    List<User> list(QueryWrapper<User> queryWrapper);

    /**
     * 统计用户数量
     */
    long count(QueryWrapper<User> queryWrapper);

    /**
     * 统计用户总数
     */
    long count();

    /**
     * 用户登录
     */
    Object login(Map<String, Object> request);

    /**
     * 用户登录，并记一条 {@code login_logs}。
     *
     * <p>与 {@link #login(Map)} 的唯一区别是多带一个请求上下文（用于取客户端 IP 与 UA）。
     * {@code httpRequest} 为 {@code null} 时只是不记日志，登录结果完全一致。
     */
    Object login(Map<String, Object> request, jakarta.servlet.http.HttpServletRequest httpRequest);

    /**
     * 用户注册
     * <p>
     * ⚠️ <b>角色不来自请求体</b>：由调用方（{@code AuthController} 的各个 register 端点）
     * 显式传入，每个端点写死自己能创建的角色。请求体里若带 {@code role} 一律忽略并记 WARN。
     * </p>
     * <p>
     * 从前这里读 body 的 {@code role}，于是匿名者
     * {@code POST /api/auth/register/student} 带 {@code role:4} 就能造出系统管理员
     * （2026-09-30 实测确认：建出 users.id=80、role=4，已删除）。改成参数传入即堵死。
     * </p>
     * <p>
     * ⚠️ <b>失败一律抛异常</b>，不再用返回值表示失败 —— 从前返回
     * {@code "用户名已存在"} 这类字符串，而 controller 无条件包成
     * {@code ApiResponse.success(...)}，于是业务失败在响应里是 {@code success:true}。
     * </p>
     *
     * @param request 注册请求（只读 username/password/name）
     * @param role    由调用方指定的角色：1=学生 2=教师 3=辅导员 4=管理员；不合法则抛异常
     * @return 成功时的提示串
     */
    String register(Object request, Integer role);

    /**
     * 用户登出
     */
    Object logout(Long userId, String role);

    /**
     * 刷新令牌
     */
    Object refreshToken(String refreshToken);
    
    void changePassword(Long userId, String oldPassword, String newPassword);
}
