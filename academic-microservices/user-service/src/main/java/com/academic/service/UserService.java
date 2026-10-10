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
     * 管理员建号（PC 端 {@code POST /users/add}）：写 {@code users} 一行，并在**同一事务里**
     * 为学院管理员({@code role=5})补一行 {@code college_admin_profile}。
     *
     * <p><b>2026-10-09（W32）新增。</b>此前这个端点是裸的 {@code save(user)}，一条 profile
     * 都不建。对 role 5 来说那是致命的：{@code CollegeAdminController.getCollegeId()} 靠
     * {@code college_admin_profile} 推学院，没有档案行就回 null ⇒ 该控制器 18 个接口
     * 一律 403（"能登录、能进界面、每页都没数据"）。
     *
     * <p><b>为什么是一个 Service 方法而不是在 Controller 里 save 完再 insert：</b>
     * 那是本工程记过的"假事务"—— 在控制器方法上标 {@code @Transactional} 又在方法体里
     * try/catch，异常根本到不了事务拦截器，于是"账号建好了、档案没建"这种半截状态会照常提交。
     * 这里把两步收进一个 {@code @Transactional} 方法、**方法体内不 catch**，
     * 任何一步失败整体回滚。
     *
     * <p>口径（与 admin-service {@code AdminController.createProfileIfNeeded} 逐条对齐，
     * 两条建号路径不能有两种"资料齐不齐"的标准）：
     * <ul>
     *   <li>role 5 且 {@code collegeId} 指向一个存在的学院 → 建档案行；</li>
     *   <li>role 5 但没给 {@code collegeId} → **不建**，只记一条 WARN
     *       （不造 {@code college_id=0} 的假档案：0 不是任何学院，会让按学院查人时静默漏人）；</li>
     *   <li>role 5 但 {@code collegeId} 指向不存在的学院 → 抛 {@link IllegalArgumentException}
     *       （写进去就是一条悬空引用，比不建更糟）；</li>
     *   <li>其它角色 → 本方法与从前一样只写 {@code users}
     *       （学生/辅导员的档案在这条路径上仍不建，见 {@code FEATURES.md} 的记录）。</li>
     * </ul>
     *
     * @param user      账号（username/password/name/role/phone/email）
     * @param collegeId 仅 role 5 使用；来自 query 参数，**不来自 User 实体**
     *                  （users 表没有 college_id 列，实体也没有该字段）
     * @throws IllegalArgumentException 用户名已存在，或学院不存在（调用方应翻成 400 + 人话）
     */
    boolean createUserWithCollegeAdminProfile(User user, Long collegeId);

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
