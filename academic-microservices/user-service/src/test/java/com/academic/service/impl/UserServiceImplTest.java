package com.academic.service.impl;

import com.academic.common.entity.CounselorProfile;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.User;
import com.academic.common.entity.UserSettings;
import com.academic.mapper.RegistrationMapper;
import com.academic.mapper.UserMapper;
import com.academic.service.LoginLogService;
import com.academic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 *
 * <p>2026-09-30 注册这条路径从"只写 users"扩到"users + 角色资料行 + user_settings +
 * 学院冗余计数"。下面注册相关的用例按**写了哪几张表**逐条钉：只断言返回值的话，
 * 少建一张 profile 表也照样"绿"（这正是修复前的状态）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RegistrationMapper registrationMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    // 登录会往 `login_logs` 记一条（2026-10-02 补的写入方）。
    // ⚠️ 这个 mock 不是可有可无的：漏了它，login() 里那句 record(...) 会 NPE，
    // 被方法外层那个 catch 吞成"登录失败" —— 三条登录用例一起变红。
    @Mock
    private LoginLogService loginLogService;

    @InjectMocks
    private UserServiceImpl userService;

    // ==================== 小工具 ====================

    /** 拼请求体：{@code body("username", "2023020701", "password", "123456")}。*/
    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /**
     * 让 mock 的 {@code insert} 像真 MyBatis-Plus 一样**把自增主键回填到实体上**。
     *
     * <p>不这么做的话 {@code user.getId()} 一直是 null，注册会在"账号已写入但未取回主键"
     * 处停下 —— 那是测试的假象，不是生产行为（真 Mapper 开了 useGeneratedKeys）。
     */
    private void stubUserInsertBackfillsId(long id) {
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            inv.getArgument(0, User.class).setId(id);
            return 1;
        });
    }

    /** 注册前的公共桩：用户名没被占用、密码能被 encode。*/
    private void stubFreshRegistration() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
    }

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

    // ==================== login_logs 写入方（2026-10-02 补） ====================
    //
    // 这三条钉的是"登录这件事真的被记下来了"。此前 `login_logs` 全仓零写入方，
    // 学生端「登录历史」/管理员端「用户登录日志」/小程序「登录日志」三个页面永远空。
    // 只断言登录返回值的话，把 record(...) 三行全删掉测试照样绿 —— 所以必须断言写入方。

    /** 登录成功 → 记一条 status=success。 */
    @Test
    void testLogin_successWritesLoginLog() {
        User user = new User()
                .setId(27L)
                .setUsername("2023020616")
                .setPassword("$2a$10$abcdefghijklmnopqrstuv")
                .setRole(1)
                .setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(27L, "2023020616", 1)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(27L, "2023020616")).thenReturn("refresh-token");

        userService.login(body("username", "2023020616", "password", "123456"));

        ArgumentCaptor<User> who = ArgumentCaptor.forClass(User.class);
        verify(loginLogService).record(who.capture(), eq("success"), isNull());
        assertEquals(27L, who.getValue().getId(), "记的必须是真实用户行（login_logs.user_id 有非空约束）");
    }

    /** 密码错 → 记一条 status=failed（用户名存在，所以有 id 可写）。 */
    @Test
    void testLogin_wrongPasswordWritesFailedLoginLog() {
        User user = new User()
                .setId(27L)
                .setUsername("2023020616")
                .setPassword("$2a$10$abcdefghijklmnopqrstuv")
                .setRole(1)
                .setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        userService.login(body("username", "2023020616", "password", "wrong"));

        verify(loginLogService).record(user, "failed", null);
    }

    /**
     * 用户名根本不存在 → **一条都不写**。
     *
     * <p>不是遗漏，是刻意：{@code login_logs.user_id} 是 NOT NULL 且无外键，
     * 没有 id 可填。写 0 会造出指向不存在用户的悬空引用（本项目在
     * {@code messages.sender_id} 上踩过同一个坑，恒设 0 导致每次插入都 ERROR 1452）。
     * 这种情况 {@code LoginLogService} 自己记 warn 跳过。
     */
    @Test
    void testLogin_unknownUsernameWritesNoLoginLog() {
        when(userMapper.selectOne(any())).thenReturn(null);

        userService.login(body("username", "查无此人", "password", "123456"));

        verifyNoInteractions(loginLogService);
    }

    /** 正常注册 → 落库前密码被加密成 BCrypt 格式；手机号/邮箱一并落库。 */
    @Test
    void testRegister() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(88L);
        // 2026-10-19：学生注册学院/专业必填（与 xcx/PC 表单同口径），不给就 400
        when(registrationMapper.countMajor(5L)).thenReturn(1);
        when(registrationMapper.selectMajorCollegeId(5L)).thenReturn(1L);
        when(registrationMapper.countCollege(1L)).thenReturn(1);

        Map<String, Object> request = body(
                "username", "2023020701", "password", "123456", "name", "新同学",
                "phone", "13800001111", "email", "new@example.com",
                "collegeId", 1, "majorId", 5,
                "role", 4);   // ← 恶意请求体：想自提权成系统管理员

        // 端点只允许建学生，所以传 1
        String result = userService.register(request, 1);

        // 服务的真实返回值是英文串；全项目没有任何调用方按字符串判断成败，
        // 原测试断言的"注册成功"从来就不是它返回的东西。
        assertEquals("Register successful", result);

        // 明文密码绝不能落库；**角色必须是调用方传的 1，不是请求体里的 4**。
        // 这一句就是 2026-09-30 那个提权漏洞（匿名带 role:4 造出管理员）的回归钉子。
        User saved = capturedUserInsert();
        assertEquals("$2a$10$encoded", saved.getPassword());
        assertEquals("2023020701", saved.getUsername());
        assertEquals(1, saved.getRole());
        assertEquals(1, saved.getStatus(), "学生注册后应直接可用");
        // 2026-09-30：这两格从前是**静默丢掉**的（请求体里有，服务不读）
        assertEquals("13800001111", saved.getPhone());
        assertEquals("new@example.com", saved.getEmail());

        // 学生必须有档案行，否则登录进去没有任何数据可查
        verify(registrationMapper).insertStudentProfile(
                88L, "2023020701", "新同学", null, 1L, 5L, null, null);
    }

    /** 学生只填了专业 → 学院跟着专业走（不能出现"专业属 A 院、学院写 B 院"）。 */
    @Test
    void testRegister_studentDerivesCollegeFromMajor() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(89L);
        when(registrationMapper.countMajor(5L)).thenReturn(1);
        when(registrationMapper.selectMajorCollegeId(5L)).thenReturn(1L);
        when(registrationMapper.countCollege(1L)).thenReturn(1);

        userService.register(body("username", "2023020702", "password", "123456",
                "name", "小甲", "majorId", 5, "gender", "男"), 1);

        verify(registrationMapper).insertStudentProfile(
                89L, "2023020702", "小甲", "男", 1L, 5L, null, null);
    }

    /**
     * 学生选了班级 → 班名/专业/学院**全部以班级行为准**。
     *
     * <p>这三格必须同源：{@code verify_invariants.py} 的 C 组要求
     * "学生档案的 class_name == class_id 指向的班名"且"专业/学院与他所在班的一致"。
     */
    @Test
    void testRegister_studentClassRowIsAuthoritative() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(90L);
        when(registrationMapper.selectClassRow(1L))
                .thenReturn(Map.of("id", 1L, "name", "计科2306班", "college_id", 1L, "major_id", 5L));
        when(registrationMapper.countCollege(1L)).thenReturn(1);

        userService.register(body("username", "2023020703", "password", "123456",
                "name", "小乙", "classId", 1), 1);

        ArgumentCaptor<String> className = ArgumentCaptor.forClass(String.class);
        verify(registrationMapper).insertStudentProfile(
                eq(90L), eq("2023020703"), eq("小乙"), isNull(), eq(1L), eq(5L), eq(1L), className.capture());
        assertEquals("计科2306班", className.getValue(), "class_name 必须写成班级行里的名字");
    }

    /** 请求体里的专业与所选班级矛盾 → 明确拒绝，不静默改写。 */
    @Test
    void testRegister_studentClassMajorConflictRejected() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(91L);
        when(registrationMapper.selectClassRow(1L))
                .thenReturn(Map.of("id", 1L, "name", "计科2306班", "college_id", 1L, "major_id", 5L));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(body("username", "2023020704", "password", "123456",
                        "classId", 1, "majorId", 9), 1));
        assertEquals("所选班级不属于所选专业", e.getMessage());
        verify(registrationMapper, never()).insertStudentProfile(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** 班级不存在 → 拒绝（留着会造出指向不存在班级的档案）。 */
    @Test
    void testRegister_studentClassNotFoundRejected() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(92L);
        when(registrationMapper.selectClassRow(7L)).thenReturn(null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(body("username", "2023020705", "password", "123456", "classId", 7), 1));
        assertEquals("所选班级不存在: 7", e.getMessage());
    }

    @Test
    void testRegister_studentCollegeNotFoundRejected() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(93L);
        when(registrationMapper.selectClassRow(7L))
                .thenReturn(Map.of("id", 7L, "name", "幽灵班", "college_id", 3L, "major_id", 5L));
        when(registrationMapper.countCollege(3L)).thenReturn(0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(body("username", "2023020706", "password", "123456",
                        "classId", 7), 1));
        assertEquals("所选学院不存在: 3", e.getMessage());
    }

    /** 学号（= student_profile.student_id）有唯一约束，被占用时给人话而不是 500。 */
    @Test
    void testRegister_studentIdAlreadyHasProfileRejected() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(94L);
        when(registrationMapper.countStudentProfileByStudentId("2023020707")).thenReturn(1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(body("username", "2023020707", "password", "123456"), 1));
        assertEquals("该学号已有学生档案: 2023020707", e.getMessage());
    }

    /**
     * 前端把 id 传成非数字（小程序未选时给的是空串，选中时给字符串，PC 给数字）
     * → 明确报错，**绝不静默当没填**。
     */
    @Test
    void testRegister_nonNumericIdRejected() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(95L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(body("username", "2023020708", "password", "123456",
                        "collegeId", "abc"), 1));
        assertEquals("字段 collegeId 必须是数字: abc", e.getMessage());
    }

    /**
     * 空串（前端"未选择"的形状）算没填，不该报"必须是数字"。
     * 学院/专业 2026-10-19 起必填（另行有拒绝用例），这里给足有效值、
     * 只把 classId 留成空串 —— 钉"空串 ≠ 非数字报错"。
     */
    @Test
    void testRegister_blankIdsTreatedAsAbsent() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(96L);
        when(registrationMapper.countMajor(5L)).thenReturn(1);
        when(registrationMapper.selectMajorCollegeId(5L)).thenReturn(1L);
        when(registrationMapper.countCollege(1L)).thenReturn(1);

        userService.register(body("username", "2023020709", "password", "123456",
                "name", "小丙", "collegeId", 1, "majorId", 5, "classId", ""), 1);

        verify(registrationMapper).insertStudentProfile(
                96L, "2023020709", "小丙", null, 1L, 5L, null, null);
        verify(registrationMapper, never()).selectClassRow(any());
    }

    /**
     * 教师：`teacher_profile.college_id` 是 NOT NULL → 没有学院就**拒绝注册**，
     * 而不是建一个查不到任何班级/课程的"空教师"。
     */
    @Test
    void testRegister_teacherRequiresCollege() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(97L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(body("username", "10086", "password", "123456", "name", "王老师"), 2));
        assertEquals("教师注册必须选择所属学院", e.getMessage());
        verify(registrationMapper, never()).insertTeacherProfile(any());
    }

    /** 教师：建档案，且**状态是待审批**。 */
    @Test
    void testRegister_teacherCreatesProfilePendingApproval() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(98L);
        when(registrationMapper.countCollege(1L)).thenReturn(1);

        userService.register(body("username", "10086", "password", "123456", "name", "王老师",
                "collegeId", 1, "phone", "13900002222", "email", "w@example.com"), 2);

        assertEquals(0, capturedUserInsert().getStatus(), "教师注册后应是待审批(0)");
        ArgumentCaptor<TeacherProfile> cap = ArgumentCaptor.forClass(TeacherProfile.class);
        verify(registrationMapper).insertTeacherProfile(cap.capture());
        assertEquals(98L, cap.getValue().getUserId());
        assertEquals("王老师", cap.getValue().getName());
        assertEquals(1L, cap.getValue().getCollegeId());
    }

    /**
     * 辅导员：建档案（编号用账号名兜）、办公信息进 {@code user_settings}，
     * **不写** {@code counselor_profiles.office_location/duty_phone}（那两列没有读者，
     * 办公信息的真源是 user_settings）。
     */
    @Test
    void testRegister_counselorCreatesProfileAndOfficeSettings() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(99L);
        when(registrationMapper.countCollege(2L)).thenReturn(1);

        userService.register(body("username", "C99", "password", "123456", "name", "李辅导",
                "collegeId", 2, "gender", "女", "department", "学工处", "title", "讲师",
                "education", "硕士", "introduction", "带 6 个班",
                "officeLocation", "3 号楼 201", "dutyPhone", "0451-88880000",
                "phone", "13700003333", "email", "li@example.com"), 3);

        assertEquals(0, capturedUserInsert().getStatus(), "辅导员注册后应是待审批(0)");

        ArgumentCaptor<CounselorProfile> cap = ArgumentCaptor.forClass(CounselorProfile.class);
        verify(registrationMapper).insertCounselorProfile(cap.capture());
        CounselorProfile p = cap.getValue();
        assertEquals(99L, p.getUserId());
        assertEquals("C99", p.getCounselorId(), "表单没填编号时用账号名兜");
        assertEquals("C99", p.getStaffId());
        assertEquals("李辅导", p.getName());
        assertEquals("女", p.getGender());
        assertEquals(2L, p.getCollegeId());
        assertEquals("学工处", p.getDepartment());
        assertEquals("讲师", p.getTitle());
        assertEquals("硕士", p.getEducation());
        assertEquals("带 6 个班", p.getIntroduction());
        assertEquals(1, p.getStatus());

        ArgumentCaptor<UserSettings> sCap = ArgumentCaptor.forClass(UserSettings.class);
        verify(registrationMapper).insertUserSettings(sCap.capture());
        assertEquals(99L, sCap.getValue().getUserId());
        assertEquals("3 号楼 201", sCap.getValue().getOfficeLocation());
        assertEquals("0451-88880000", sCap.getValue().getOfficePhone());
    }

    /** 辅导员没填办公信息 → 不建空的 user_settings 行。 */
    @Test
    void testRegister_counselorWithoutOfficeInfoCreatesNoSettingsRow() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(100L);
        // 2026-10-19：辅导员注册学院必填（后端与表单同口径）
        when(registrationMapper.countCollege(2L)).thenReturn(1);

        userService.register(body("username", "C100", "password", "123456", "name", "赵辅导",
                "collegeId", 2), 3);

        verify(registrationMapper).insertCounselorProfile(any());
        verify(registrationMapper, never()).insertUserSettings(any());
    }

    /** 管理员（端点已限定 role=4 才能调）不建资料行，且直接可用。 */
    @Test
    void testRegister_adminCreatesNoProfileRow() {
        stubFreshRegistration();
        stubUserInsertBackfillsId(101L);

        userService.register(body("username", "admin2", "password", "123456", "name", "新管理员"), 4);

        assertEquals(1, capturedUserInsert().getStatus(), "管理员注册后直接可用");
        verifyNoInteractions(registrationMapper);
    }

    /** 角色不合法（含刻意排除的 5=学院管理员）时必须拒绝，且一行不写。 */
    @Test
    void testRegister_roleNotAllowed() {
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020709");
        request.put("password", "123456");

        assertThrows(IllegalArgumentException.class, () -> userService.register(request, 5));
        assertThrows(IllegalArgumentException.class, () -> userService.register(request, 9));
        assertThrows(IllegalArgumentException.class, () -> userService.register(request, null));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testRegister_passwordTooShort() {
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020702");
        request.put("password", "123");

        // 失败**抛异常**，不再靠返回值表达失败 —— 返回值那条路会被 controller
        // 无条件包成 ApiResponse.success(...)，业务失败在响应里就成了 success:true。
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(request, 1));
        assertEquals("密码长度至少为6位", e.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testRegister_duplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(new User().setId(1L).setUsername("2023020703"));

        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020703");
        request.put("password", "123456");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(request, 1));
        assertEquals("用户名已存在", e.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    // ==================== 管理员建号（POST /users/add）：W32 ====================
    //
    // 这一段钉的是「学院管理员(role 5) 建号时**真的写 college_admin_profile**」。
    // 此前该表全仓只有读和删、零写入 ⇒ 新建的 role 5 账号拿不到档案行 ⇒
    // CollegeAdminController.getCollegeId() 回 null ⇒ 学院管理端 18 个接口一律 403。
    // 只断言"账号建出来了"的话，档案那一行不写测试照样绿 —— 所以下面逐条断言 insert 参数。

    /** role 5 + 学院存在 → users 与 college_admin_profile 各写一行（同一事务）。 */
    @Test
    void createUserWithCollegeAdminProfile_role5WritesProfile() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        stubUserInsertBackfillsId(200L);
        when(registrationMapper.countCollege(1L)).thenReturn(1);

        boolean ok = userService.createUserWithCollegeAdminProfile(
                new User().setUsername("collegeadmin9").setPassword("123456").setName("新院长")
                        .setRole(5).setPhone("13900009999").setEmail("dean@academic.edu"),
                1L);

        assertTrue(ok);
        assertEquals(200L, capturedUserInsert().getId(), "账号行必须落库");
        ArgumentCaptor<com.academic.common.entity.CollegeAdminProfile> cap =
                ArgumentCaptor.forClass(com.academic.common.entity.CollegeAdminProfile.class);
        verify(registrationMapper).insertCollegeAdminProfile(cap.capture());
        assertEquals(200L, cap.getValue().getUserId());
        assertEquals("新院长", cap.getValue().getName());
        assertEquals(1L, cap.getValue().getCollegeId(), "学院必须写进去 —— 学院管理端全靠它");
    }

    /**
     * ★ role 5 但**没给学院** → 账号照建、**档案不建**（不造 college_id=0 的假档案），
     * 且不抛异常（这是**允许**的状态，只是学院端取不到数，由调用方如实提示）。
     */
    @Test
    void createUserWithCollegeAdminProfile_role5WithoutCollegeCreatesNoProfile() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        stubUserInsertBackfillsId(201L);

        boolean ok = userService.createUserWithCollegeAdminProfile(
                new User().setUsername("collegeadmin10").setPassword("123456").setName("无院院长")
                        .setRole(5),
                null);

        assertTrue(ok, "没给学院不该让建号失败");
        verify(registrationMapper, never()).insertCollegeAdminProfile(any());
        // 也不能去查学院（没得查）
        verify(registrationMapper, never()).countCollege(any());
    }

    /**
     * ★ role 5 + 学院**不存在** → 抛异常（写进去就是悬空引用 ⇒ 按学院查人时静默漏人）。
     * 异常会冒到控制器翻成 400，事务整体回滚（本方法标了 @Transactional）。
     */
    @Test
    void createUserWithCollegeAdminProfile_role5UnknownCollegeRejected() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        stubUserInsertBackfillsId(202L);
        when(registrationMapper.countCollege(999L)).thenReturn(0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.createUserWithCollegeAdminProfile(
                        new User().setUsername("collegeadmin11").setPassword("123456").setName("幽灵院长")
                                .setRole(5),
                        999L));
        assertEquals("学院不存在: 999", e.getMessage());
        verify(registrationMapper, never()).insertCollegeAdminProfile(any());
    }

    /** 其它角色（这里取 role 4）不因这次改动而多写任何 profile 行。 */
    @Test
    void createUserWithCollegeAdminProfile_otherRoleWritesNoProfile() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        stubUserInsertBackfillsId(203L);

        boolean ok = userService.createUserWithCollegeAdminProfile(
                new User().setUsername("adm009").setPassword("123456").setName("另一个管理员").setRole(4),
                null);

        assertTrue(ok);
        verifyNoInteractions(registrationMapper);
    }

    /** 取回 {@code users} 那一行的入参（断言"到底写了什么"）。*/
    private User capturedUserInsert() {
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        return cap.getValue();
    }
}
