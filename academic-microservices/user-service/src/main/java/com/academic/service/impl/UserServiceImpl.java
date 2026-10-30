package com.academic.service.impl;

import com.academic.common.entity.CollegeAdminProfile;
import com.academic.common.entity.CounselorProfile;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.User;
import com.academic.common.entity.UserSettings;
import com.academic.mapper.RegistrationMapper;
import com.academic.mapper.UserMapper;
import com.academic.service.LoginLogService;
import com.academic.service.UserService;
import com.academic.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@Primary
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RegistrationMapper registrationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginLogService loginLogService;

    public UserServiceImpl(UserMapper userMapper, RegistrationMapper registrationMapper,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                           LoginLogService loginLogService) {
        this.userMapper = userMapper;
        this.registrationMapper = registrationMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginLogService = loginLogService;
    }

    @Override
    public User getById(Long id) {
        try {
            log.info("Get user by id: {}", id);
            return userMapper.selectById(id);
        } catch (Exception e) {
            log.error("Get user by id failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public User getByUsername(String username) {
        try {
            log.info("Get user by username: {}", username);
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            return userMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.error("Get user by username failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean save(User user) {
        try {
            log.info("Save user: {}", user.getUsername());
            user.setCreatedAt(LocalDateTime.now());
            // 自动加密密码：非BCrypt格式的明文密码统一加密
            String pwd = user.getPassword();
            if (pwd != null && !pwd.startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(pwd));
            }
            return userMapper.insert(user) > 0;
        } catch (Exception e) {
            log.error("Save user failed: {}", e.getMessage());
            throw e;
        }
    }

    private static final int ROLE_COLLEGE_ADMIN = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUserWithCollegeAdminProfile(User user, Long collegeId) {
        log.info("Create user (admin path): username={}, role={}, collegeId={}",
                user.getUsername(), user.getRole(), collegeId);

        // ★ save() 内部对用户名唯一约束冲突会抛 DuplicateKeyException，这里**不 catch**
        //   —— 让它冒到控制器翻成"用户名已存在"。在这里吞掉再 return false 的话，
        //   调用方分不清"重名"和"别的失败"。
        boolean saved = save(user);
        if (!saved) {
            return false;
        }
        // save() 走 MyBatis-Plus insert，主键会回填；没有它下面的档案无从挂起。
        if (user.getId() == null) {
            throw new IllegalStateException("账号已写入但未取回主键，无法继续建档");
        }

        if (user.getRole() != null && user.getRole() == ROLE_COLLEGE_ADMIN) {
            // 口径与 admin-service AdminController.createProfileIfNeeded 逐条对齐。
            if (collegeId == null) {
                log.warn("学院管理员账号建立但未绑定学院（学院管理端将无法取数）: userId={}, username={}",
                        user.getId(), user.getUsername());
            } else if (registrationMapper.countCollege(collegeId) == 0) {
                // 写进去就是一条指向不存在学院的悬空引用，查学院时会静默漏人 ⇒ 直接拒。
                // 抛出去让整笔回滚：宁可"建号失败"，也不要留一个取不到数的半截账号。
                throw new IllegalArgumentException("学院不存在: " + collegeId);
            } else {
                CollegeAdminProfile profile = new CollegeAdminProfile();
                profile.setUserId(user.getId());
                profile.setName(user.getName() != null ? user.getName() : user.getUsername());
                profile.setCollegeId(collegeId);
                profile.setPhone(user.getPhone());
                profile.setEmail(user.getEmail());
                registrationMapper.insertCollegeAdminProfile(profile);
            }
        }
        return true;
    }

    @Override
    public boolean updateById(User user) {
        try {
            log.info("Update user: {}", user.getId());
            // 更新时如果密码非BCrypt格式，自动加密
            String pwd = user.getPassword();
            if (pwd != null && !pwd.startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(pwd));
            }
            return userMapper.updateById(user) > 0;
        } catch (Exception e) {
            log.error("Update user failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean removeById(Long id) {
        try {
            log.info("Remove user: {}", id);
            return userMapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.error("Remove user failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public Page<User> page(Page<User> page, QueryWrapper<User> queryWrapper) {
        try {
            log.info("Page query users");
            return userMapper.selectPage(page, queryWrapper);
        } catch (Exception e) {
            log.error("Page query users failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public List<User> list(QueryWrapper<User> queryWrapper) {
        try {
            log.info("List users");
            return userMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("List users failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public long count(QueryWrapper<User> queryWrapper) {
        try {
            log.info("Count users");
            return userMapper.selectCount(queryWrapper);
        } catch (Exception e) {
            log.error("Count users failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public long count() {
        try {
            log.info("Count all users");
            return userMapper.selectCount(null);
        } catch (Exception e) {
            log.error("Count all users failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public Object login(Map<String, Object> request) {
        return login(request, null);
    }

    /**
     * 登录，并把这次登录写进 {@code login_logs}。
     *
     * <p>保留单参重载是为了不打断既有调用方（含测试）：没有请求上下文时只是不记日志，
     * 登录行为一字不差。生产路径由 {@code AuthController} 传 {@code HttpServletRequest} 进来。
     *
     * <p>记日志的时机：<b>只在"用户名存在"时记</b>。用户名不存在时 {@code user} 为 null，
     * 而 {@code login_logs.user_id} 是 NOT NULL，没有 id 可写 —— 这种情况
     * {@link LoginLogService} 会记 warn 并跳过（写 0 会造出指向不存在用户的悬空引用）。
     */
    @Override
    public Object login(Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            log.info("User login attempt: {}", request);
            
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "用户名和密码不能为空");
                return response;
            }
            
            Map<String, Object> response = new HashMap<>();
            
            User user = getByUsername(username);
            
            if (user != null) {
                // 检查账号状态
                if (user.getStatus() != null && user.getStatus() != 1) {
                    if (user.getStatus() == 0) {
                        response.put("message", "账号正在审批中，请等待管理员审核");
                    } else if (user.getStatus() == 2) {
                        response.put("message", "账号已被锁定");
                    } else {
                        response.put("message", "账号已被禁用");
                    }
                    response.put("status", "error");
                    loginLogService.record(user, "failed", httpRequest);
                    return response;
                }
                // 使用BCrypt密码比对，兼容明文密码的向后兼容
                boolean passwordMatch;
                if (user.getPassword() != null && user.getPassword().startsWith("$2a$")) {
                    passwordMatch = passwordEncoder.matches(password, user.getPassword());
                } else {
                    // 向后兼容旧明文密码（自动加密）
                    passwordMatch = password.equals(user.getPassword());
                    if (passwordMatch) {
                        user.setPassword(passwordEncoder.encode(password));
                        userMapper.updateById(user);
                    }
                }
                if (passwordMatch) {
                    String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
                    String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
                    
                    response.put("token", token);
                    response.put("refreshToken", refreshToken);
                    response.put("userId", user.getId());
                    response.put("username", user.getUsername());
                    response.put("role", user.getRole());
                    response.put("name", user.getName());
                    response.put("status", "success");
                    log.info("User login successful: {}", user.getUsername());
                    loginLogService.record(user, "success", httpRequest);
                } else {
                    response.put("status", "error");
                    response.put("message", "密码错误");
                    log.warn("Login failed: incorrect password for user {}", username);
                    loginLogService.record(user, "failed", httpRequest);
                }
            } else {
                response.put("status", "error");
                response.put("message", "用户不存在");
                log.warn("Login failed: user not found {}", username);
            }
            
            return response;
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "登录失败");
            return response;
        }
    }

    /**
     * register 允许写入的角色。
     * 刻意**不含 5（学院管理员）**：那是管理员在后台单独开通的角色，
     * 不该出现在任何自助注册路径上。
     */
    private static final Set<Integer> REGISTER_ALLOWED_ROLES = Set.of(1, 2, 3, 4);

    private static final int ROLE_STUDENT = 1;
    private static final int ROLE_TEACHER = 2;
    private static final int ROLE_COUNSELOR = 3;
    private static final int ROLE_ADMIN = 4;

    /**
     * 自助注册。
     *
     * <h3>2026-09-30：这条路径以前只写 {@code users} 一张表</h3>
     * <p>改之前这里只做三件事：读 {@code username/password/name}、插一行 {@code users}、
     * 返回成功。于是：
     * <ul>
     *   <li>表单上填的**手机号/邮箱/学院/专业/班级全部被丢掉**（请求体里有，没人读）；</li>
     *   <li>**不建任何 profile 行** —— 学生注册成功后能登录，但成绩/预警/课表/班级
     *       全部按 {@code student_profile.id} 取数，一个都查不到，登录进去是一座空城；</li>
     *   <li>教师/辅导员同理（{@code teacher_profile} / {@code counselor_profiles} 缺行）。</li>
     * </ul>
     * <p>活库的种子数据把这个洞盖住了：24 个 role=1 的账号**每个都有** profile 行
     * （都是种子直接造的），所以"自己注册一个学生试试"才会露馅。
     *
     * <h3>现在写哪几张表（全部在同一个事务里）</h3>
     * <ol>
     *   <li>{@code users} —— 账号 + 手机号 + 邮箱；</li>
     *   <li>角色资料行：{@code student_profile} / {@code teacher_profile} / {@code counselor_profiles}；</li>
     *   <li>{@code user_settings} —— **只在注册表单填了办公信息时**（辅导员有，学生没有）。</li>
     * </ol>
     * <p>⚠️ 2026-10-01 变更：这里原来还会 {@code colleges.student_count|teacher_count + 1}。
     * 那两列已从库里删掉 —— 它们全仓没有读者（{@code College} 上那两个字段本就是
     * {@code @TableField(exist = false)}，展示时现算），却只被注册这一条路径维护，
     * 管理员建号/删号/导入都不动 ⇒ 必然漂移。现在"学院有多少人"一律 {@code COUNT(*)} 现算。
     * <p>任何一步失败都整体回滚：宁可"注册失败"，也不要留一个只有账号没有资料的半截账号
     * （那正是这次要修的缺陷）。
     *
     * <h3>status 由角色决定</h3>
     * <p>学生与管理员直接 {@code status=1} 可用；**教师/辅导员 {@code status=0} 待审批**，
     * 由管理员在「用户管理」页点"审批通过"（{@code AdminUsers.vue} 的待审批 tag + 审批通过按钮）
     * 之后才能登录 —— 登录侧一直在按 status 判（{@code login} 里 {@code status=0} 会回
     * 「账号正在审批中，请等待管理员审核」），只是从前**没有任何路径产出 status=0 的账号**，
     * 那条审批链是个没有源头的摆设。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String register(Object request, Integer role) {
        log.info("User register, role={}", role);

        if (!(request instanceof Map)) {
            throw new IllegalArgumentException("注册请求格式不正确");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> reqMap = (Map<String, Object>) request;
        String username = str(reqMap, "username");
        String password = str(reqMap, "password");
        String name = str(reqMap, "name");

        // ★ 角色只认调用方传进来的那个。请求体里的 role 一概不读 ——
        //   这一行是 2026-09-30 堵掉"匿名者自提权成管理员"的关键，别改回去。
        //   读了会无声无息，所以这里显式记一条 WARN（不拒绝请求，只让它在日志里可见）。
        if (reqMap.containsKey("role")) {
            log.warn("注册请求体里的 role 被忽略（角色由端点决定，防止自提权）: body.role={}, 实际使用 role={}",
                    reqMap.get("role"), role);
        }

        if (role == null || !REGISTER_ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("不允许注册为该角色: " + role);
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为6位");
        }

        User existingUser = getByUsername(username);
        if (existingUser != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User()
                .setUsername(username)
                .setPassword(password)   // 明文交给 save()，它统一加密（非 $2a$ 前缀就 encode）
                .setName(name != null ? name : username)
                .setPhone(str(reqMap, "phone"))
                .setEmail(str(reqMap, "email"))
                .setRole(role)
                .setStatus(statusForRole(role));

        save(user);
        // save() 走 MyBatis-Plus insert，主键会回填到 user.id；没有它下面的 profile 无从挂起。
        if (user.getId() == null) {
            throw new IllegalStateException("账号已写入但未取回主键，无法继续建档");
        }

        if (role == ROLE_STUDENT) {
            createStudentProfile(user, reqMap);
        } else if (role == ROLE_TEACHER) {
            createTeacherProfile(user, reqMap);
        } else if (role == ROLE_COUNSELOR) {
            createCounselorProfile(user, reqMap);
        }
        // 管理员(4) 不建资料行：CollegeAdminProfile/admin 侧没有对应表结构上的必填来源，
        // 与 admin-service 的建号口径一致（那边也只给学生/辅导员补 profile）。

        log.info("User registered successfully: {} (role={}, status={}, profileCreated={})",
                username, role, user.getStatus(), role != ROLE_ADMIN);
        return "Register successful";
    }

    /**
     * 注册后的初始状态。
     *
     * <p>学生与管理员直接可用；教师/辅导员需要管理员审批（0=待审批，见
     * {@code AdminUsers.vue} 的状态语义：0 待审批 / 1 正常 / 2 已锁定）。
     */
    private int statusForRole(int role) {
        return (role == ROLE_STUDENT || role == ROLE_ADMIN) ? 1 : 0;
    }

    /** 学生档案。{@code student_profile.user_id} / {@code student_id} 都有唯一约束。*/
    private void createStudentProfile(User user, Map<String, Object> reqMap) {
        String studentId = user.getUsername();   // 学号即账号名（活库里 24 个学生都是这么存的）

        if (registrationMapper.countStudentProfileByStudentId(studentId) > 0) {
            throw new IllegalArgumentException("该学号已有学生档案: " + studentId);
        }

        Long classId = idOf(reqMap, "classId");
        Long majorId = idOf(reqMap, "majorId");
        Long collegeId = idOf(reqMap, "collegeId");
        String className = null;

        if (classId != null) {
            // 选了班就以**班级行**为准：班名、专业、学院全从它取。
            // 学生档案的 class_name/class_id、major_id/college_id 必须自洽
            // （verify_invariants.py C 组），从两处分别取值迟早对不上。
            Map<String, Object> cls = registrationMapper.selectClassRow(classId);
            if (cls == null) {
                throw new IllegalArgumentException("所选班级不存在: " + classId);
            }
            className = asString(cls.get("name"));
            Long clsMajorId = asLong(cls.get("major_id"));
            Long clsCollegeId = asLong(cls.get("college_id"));
            if (majorId != null && clsMajorId != null && !majorId.equals(clsMajorId)) {
                throw new IllegalArgumentException("所选班级不属于所选专业");
            }
            if (collegeId != null && clsCollegeId != null && !collegeId.equals(clsCollegeId)) {
                throw new IllegalArgumentException("所选班级不属于所选学院");
            }
            majorId = clsMajorId != null ? clsMajorId : majorId;
            collegeId = clsCollegeId != null ? clsCollegeId : collegeId;
        } else if (majorId != null) {
            // 只给了专业：学院必须跟着专业走，否则档案里的专业和学院会互相打架。
            requireExists(registrationMapper.countMajor(majorId) > 0, "所选专业不存在: " + majorId);
            Long majorCollegeId = registrationMapper.selectMajorCollegeId(majorId);
            if (majorCollegeId != null) {
                collegeId = majorCollegeId;
            }
        }

        // 2026-10-19：学院与专业改为**必填**（班级仍可选）。此前两者都能空着注册，
        // 落下的档案没有归属，"按学院/专业统计"永远查不到这个学生。两端的注册表单
        // 已同步加了必选校验，这里是接口层的兜底 —— 不信调用方。
        // 放在班级/专业推导**之后**：只选了班级的学生，学院/专业从班级行带出，一样算选了。
        if (collegeId == null) {
            throw new IllegalArgumentException("学生注册必须选择所属学院与专业");
        }
        if (majorId == null) {
            throw new IllegalArgumentException("学生注册必须选择所属专业");
        }

        if (collegeId != null) {
            requireExists(registrationMapper.countCollege(collegeId) > 0, "所选学院不存在: " + collegeId);
        }

        registrationMapper.insertStudentProfile(user.getId(), studentId,
                user.getName(), str(reqMap, "gender"), collegeId, majorId, classId, className);
        log.info("Student profile created: userId={}, studentId={}, classId={}", user.getId(), studentId, classId);
    }

    /**
     * 教师档案。
     *
     * <p>{@code teacher_profile.college_id} 是 NOT NULL —— 拿不到就**明确拒绝注册**，
     * 而不是建一个没有学院的教师（那样他在教师端看不到任何班级/课程，也查不出原因）。
     * 这与 {@code admin-service.createProfileIfNeeded} 的口径不同：那边是"管理员建号、
     * 表单里没有学院，所以不建 profile"，这边是"本人注册、表单里学院是必填"。
     */
    private void createTeacherProfile(User user, Map<String, Object> reqMap) {
        Long collegeId = idOf(reqMap, "collegeId");
        if (collegeId == null) {
            throw new IllegalArgumentException("教师注册必须选择所属学院");
        }
        requireExists(registrationMapper.countCollege(collegeId) > 0, "所选学院不存在: " + collegeId);

        TeacherProfile profile = new TeacherProfile();
        profile.setUserId(user.getId());
        profile.setName(user.getName());
        profile.setCollegeId(collegeId);
        profile.setTitle(str(reqMap, "title"));
        registrationMapper.insertTeacherProfile(profile);
        log.info("Teacher profile created: userId={}, collegeId={}", user.getId(), collegeId);
    }

    /**
     * 辅导员档案 + 办公信息。
     *
     * <p>编号列（{@code counselor_id} / {@code staff_id}）优先用表单里填的，没填就用账号名兜
     * （与 admin-service 建号时"编号用用户名兜"同一个思路；活库里 counselor_id 无唯一约束，
     * 但同一批数据里它就是工号，用账号名最不容易撞）。
     *
     * <p>办公地点/值班电话写进 {@code user_settings}（办公信息的真源），**不写**
     * {@code counselor_profiles.office_location/duty_phone} —— 那两列全仓没有 Java 读者，
     * 写了就是第二个数据源。
     */
    private void createCounselorProfile(User user, Map<String, Object> reqMap) {
        Long collegeId = idOf(reqMap, "collegeId");
        // 2026-10-19：学院改为必填（与教师同口径）。此前可以空着注册，落下的辅导员
        // 档案没有归属，按学院筛选/统计都查不到这个人。
        if (collegeId == null) {
            throw new IllegalArgumentException("辅导员注册必须选择所属学院");
        }
        requireExists(registrationMapper.countCollege(collegeId) > 0, "所选学院不存在: " + collegeId);

        String counselorId = str(reqMap, "counselorId");
        if (counselorId == null) {
            counselorId = user.getUsername();
        }
        String staffId = str(reqMap, "staffId");
        if (staffId == null) {
            staffId = user.getUsername();
        }

        CounselorProfile profile = new CounselorProfile();
        profile.setUserId(user.getId());
        profile.setCounselorId(counselorId);
        profile.setStaffId(staffId);
        profile.setName(user.getName());
        profile.setGender(str(reqMap, "gender"));
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        profile.setCollegeId(collegeId);
        profile.setDepartment(str(reqMap, "department"));
        profile.setTitle(str(reqMap, "title"));
        profile.setEducation(str(reqMap, "education"));
        profile.setIntroduction(str(reqMap, "introduction"));
        profile.setStatus(1);
        registrationMapper.insertCounselorProfile(profile);

        String officeLocation = str(reqMap, "officeLocation");
        String officePhone = str(reqMap, "dutyPhone");
        if (officeLocation != null || officePhone != null) {
            UserSettings settings = new UserSettings();
            settings.setUserId(user.getId());
            settings.setOfficeLocation(officeLocation);
            settings.setOfficePhone(officePhone);
            registrationMapper.insertUserSettings(settings);
        }
        log.info("Counselor profile created: userId={}, collegeId={}, settingsRow={}",
                user.getId(), collegeId, officeLocation != null || officePhone != null);
    }

    // ==================== 请求体取值的小工具 ====================
    //
    // 为什么要专门写这几个：注册请求体是前端拼的 JSON，同一格在不同端是不同类型 ——
    // PC 端 el-select 给的是数字，小程序给的是 `''`（未选）或 `'3'`（字符串）。
    // 直接 `(Long) reqMap.get("collegeId")` 除了数字以外全炸 ClassCastException；
    // 而"炸"在这里表现为 500 + 一句看不懂的话。取值一律走下面几个方法：
    // 空白 → null（视为没填），填了但不是数字 → **明确报错**，绝不静默当没填
    // （静默丢弃正是这次要修的缺陷类型）。

    /** 取字符串：null / 空白都算没填。*/
    private static String str(Map<String, Object> reqMap, String key) {
        Object v = reqMap.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 取 id：空白算没填；非空白必须是数字，否则报错。*/
    private static Long idOf(Map<String, Object> reqMap, String key) {
        String s = str(reqMap, key);
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 必须是数字: " + s);
        }
    }

    private static String asString(Object v) {
        return v != null ? v.toString() : null;
    }

    /** 从 SQL 结果里取 Long（不同驱动可能给 Integer/BigInteger）。*/
    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void requireExists(boolean exists, String message) {
        if (!exists) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public Object logout(Long userId, String role) {
        try {
            log.info("User logout: userId={}, role={}", userId, role);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Logout successful");
            return result;
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public Object refreshToken(String refreshToken) {
        try {
            log.info("Refreshing token");
            
            if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "error");
                result.put("message", "无效的刷新令牌");
                return result;
            }
            
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String username = jwtUtil.getUsernameFromToken(refreshToken);
            
            User user = getById(userId);
            Integer role = user != null ? user.getRole() : 1;
            
            String newToken = jwtUtil.generateToken(userId, username, role);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
            
            Map<String, Object> result = new HashMap<>();
            result.put("token", newToken);
            result.put("refreshToken", newRefreshToken);
            result.put("status", "success");
            
            log.info("Token refreshed for user: {}", username);
            return result;
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", "令牌刷新失败");
            return result;
        }
    }
    
    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        
        boolean match;
        if (user.getPassword().startsWith("$2a$")) {
            match = passwordEncoder.matches(oldPassword, user.getPassword());
        } else {
            match = oldPassword.equals(user.getPassword());
        }
        if (!match) throw new RuntimeException("旧密码错误");
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }
}