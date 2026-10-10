package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.TeacherProfile;
import com.academic.entity.StudentProfile;
import com.academic.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 学院管理员控制器的两道闸（W1.13，2026-10-02）。
 *
 * <p><b>修之前是什么样</b>：本控制器所有接口的第一参数都是 {@code users.id}，学院归属由
 * {@code getCollegeId(那个 id)} 推出来。于是两件事同时成立：
 * <br>① 路径里的 uid 是客户端给的 —— 甲院管理员把 uid 换成乙院管理员的，拿到的就是乙院；
 * <br>② {@code /class-students} 只查了 {@code class_id = 传入的 classId}，不看这个班属不属于
 * 本院；{@code /process-warning} 与 {@code /delete-warning} 更彻底，**连操作人都不收**。
 * <br>实测：电子工程学院管理员（uid=56）请求 {@code /class-students/56/1} 拿到软件学院
 * 计科2306班 24 个人的姓名与学号。
 *
 * <p><b>这里钉四件事</b>：
 * <br>① 路径 uid ≠ 登录身份 ⇒ 拒绝，且**一个字节都不读**；
 * <br>② 班级 / 预警不属于本院 ⇒ 拒绝，且**不落任何写**（尤其是 {@code deleteById}）；
 * <br>③ 归属校验必须发生在**取数之前** —— 用 {@code verify(never())} 钉住，
 *     否则"先查出来再丢掉"照样是一次越权读；
 * <br>④ 管理员（role=4）不受 uid 绑定限制（网关对 role=4 是全部放行）。
 */
@ExtendWith(MockitoExtension.class)
class CollegeAdminControllerGuardTest {

    @Mock private CollegeAdminProfileMapper collegeAdminProfileMapper;
    @Mock private ClassesMapper classesMapper;
    @Mock private StudentProfileMapper studentProfileMapper;
    @Mock private TeacherProfileMapper teacherProfileMapper;
    @Mock private CounselorProfileMapper counselorProfileMapper;
    @Mock private AcademicWarningMapper academicWarningMapper;
    @Mock private MajorMapper majorMapper;
    @Mock private ScoreStatMapper scoreStatMapper;

    @InjectMocks private CollegeAdminController controller;

    /** 软件学院管理员：users.id=57，college_admin_profile.college_id=1。 */
    private static final long ADMIN_A = 57L;
    private static final long COLLEGE_A = 1L;

    /** 电子工程学院管理员：users.id=56，college_admin_profile.college_id=2。 */
    private static final long ADMIN_B = 56L;
    private static final long COLLEGE_B = 2L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 把 SecurityContext 摆成"某某已登录"。与 GatewayAuthFilter 的做法一致：principal 是 userId 的字符串。 */
    private void login(long userId, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId), null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    /**
     * 让 getCollegeId(uid) 返回某个学院。
     *
     * <p>用 {@code lenient()} 是刻意的：有若干用例的**用意就是**"这道闸在，所以这个查询
     * 根本不该发生"，于是它必然不会被调用 —— 严格模式会把这种桩判成
     * UnnecessaryStubbing 而让测试变红，红的原因却跟被测行为无关。
     */
    private void profileOf(long userId, Long collegeId) {
        Map<String, Object> p = new HashMap<>();
        p.put("user_id", userId);
        p.put("college_id", collegeId);
        lenient().when(collegeAdminProfileMapper.selectByUserId(userId)).thenReturn(p);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    // ==================== ① 身份绑定：uid 必须是登录人本人 ====================

    /**
     * 甲院管理员把 uid 换成乙院管理员的 —— 学院归属校验会被整个绕开，
     * 所以这一条必须在取任何数之前就被拒。
     */
    @Test
    @DisplayName("花名册：路径 uid 是别人 ⇒ 拒绝，且不查档案、不查学生")
    void roster_rejectsForeignUserId() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        // 关键：把「乙院管理员的档案」**喂饱**。不喂的话，去掉闸之后 selectByUserId 返回
        // null，接口照样不是 200（回 500「无权限」），测试会因为错误的原因变绿。
        profileOf(ADMIN_B, COLLEGE_B);

        ApiResponse<List<Map<String, Object>>> r = controller.getClassStudents(ADMIN_B, 34L);

        assertEquals(403, r.getCode());
        verify(collegeAdminProfileMapper, never()).selectByUserId(any());
        verify(studentProfileMapper, never()).selectList(any());
    }

    /** 同一个口子对**只读**接口同样成立：换成别人的 uid 就换成别人的学院。 */
    @Test
    @DisplayName("概览：路径 uid 是别人 ⇒ 拒绝，不查任何统计")
    void dashboard_rejectsForeignUserId() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_B, COLLEGE_B);
        lenient().when(classesMapper.getClassesByCollegeId(COLLEGE_B)).thenReturn(List.of());

        ApiResponse<Map<String, Object>> r = controller.dashboard(ADMIN_B);

        assertEquals(403, r.getCode());
        verify(collegeAdminProfileMapper, never()).selectByUserId(any());
        verify(classesMapper, never()).getClassesByCollegeId(any());
    }

    /** 档案接口原先连权限判断都没有，任何登录人都能读别人的姓名/手机号/邮箱。 */
    @Test
    @DisplayName("档案：读别人的 ⇒ 拒绝")
    void profile_rejectsForeignUserId() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_B, COLLEGE_B);

        assertEquals(403, controller.getProfile(ADMIN_B).getCode());
        verify(collegeAdminProfileMapper, never()).selectByUserId(any());
    }

    /** 管理员（role=4）走网关是全部放行，这一层也跟着放行 —— 别把管理员一起挡了。 */
    @Test
    @DisplayName("管理员不受 uid 绑定限制")
    void adminBypassesIdentityBinding() {
        login(999L, "ROLE_ADMIN");
        profileOf(ADMIN_B, COLLEGE_B);
        lenient().when(classesMapper.getClassesByCollegeId(COLLEGE_B)).thenReturn(List.of());

        assertEquals(200, controller.dashboard(ADMIN_B).getCode());
    }

    // ==================== ② 班级归属 ====================

    /**
     * 核心回归：乙院的班 id 拿给甲院管理员 ⇒ 拒绝，且**在查学生之前就拒**。
     * {@code verify(studentProfileMapper, never())} 是关键 —— 只断言返回码的话，
     * "先全查出来再返回空"这种写法照样能过，而那次读已经发生了。
     */
    @Test
    @DisplayName("花名册：班级不属于本院 ⇒ 拒绝，且不读学生表")
    void roster_rejectsForeignClass() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(classesMapper.countClassInCollege(34L, COLLEGE_A)).thenReturn(0);

        ApiResponse<List<Map<String, Object>>> r = controller.getClassStudents(ADMIN_A, 34L);

        assertEquals(403, r.getCode());
        assertTrue(r.getMessage().contains("不属于本院"), "实际: " + r.getMessage());
        verify(studentProfileMapper, never()).selectList(any());
    }

    /** 本院的班照常返回 —— 修完不能把正常路径一起挡了。 */
    @Test
    @DisplayName("花名册：本院的班 ⇒ 正常返回")
    void roster_allowsOwnClass() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(classesMapper.countClassInCollege(1L, COLLEGE_A)).thenReturn(1);

        StudentProfile sp = new StudentProfile();
        sp.setId(3L); sp.setStudentId("2023020616"); sp.setName("司明"); sp.setClassName("计科2306班");
        when(studentProfileMapper.selectList(any())).thenReturn(List.of(sp));
        when(scoreStatMapper.selectStudentScoreStats(COLLEGE_A)).thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> r = controller.getClassStudents(ADMIN_A, 1L);

        assertEquals(200, r.getCode());
        assertEquals(1, r.getData().size());
        assertEquals("2023020616", r.getData().get(0).get("studentId"));
    }

    // ==================== ③ 写操作的预警归属 ====================

    /** 少传操作人就拒 —— 不能让"不知道是谁"的请求改到数据。 */
    @Test
    @DisplayName("处理预警：不传 userId ⇒ 400，且不写库")
    void process_missingUserId() {
        ApiResponse<String> r = controller.processWarning(body("id", 1L, "status", 1));

        assertEquals(400, r.getCode());
        assertTrue(r.getMessage().contains("userId"), "实际: " + r.getMessage());
        verify(academicWarningMapper, never()).updateById(any());
    }

    /** 乙院的预警拿给甲院管理员处理 ⇒ 拒绝，不写库。 */
    @Test
    @DisplayName("处理预警：预警不属于本院 ⇒ 403，且不写库")
    void process_rejectsForeignWarning() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(academicWarningMapper.countWarningInCollege(9L, COLLEGE_A)).thenReturn(0);

        ApiResponse<String> r = controller.processWarning(body("userId", ADMIN_A, "id", 9L, "status", 1));

        assertEquals(403, r.getCode());
        verify(academicWarningMapper, never()).updateById(any());
        verify(academicWarningMapper, never()).selectById(anyLong());
    }

    /** 本院的预警：状态真的写进去，且带上 updated_at（本项目没有自动填充，UPDATE 必须显式写）。 */
    @Test
    @DisplayName("处理预警：本院的预警 ⇒ 写 status 与 updated_at")
    void process_writesStatusAndUpdatedAt() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(academicWarningMapper.countWarningInCollege(9L, COLLEGE_A)).thenReturn(1);
        AcademicWarning w = new AcademicWarning();
        w.setId(9L); w.setStatus(0);
        when(academicWarningMapper.selectById(9L)).thenReturn(w);

        ApiResponse<String> r = controller.processWarning(body("userId", ADMIN_A, "id", 9L, "status", 1));

        assertEquals(200, r.getCode());
        assertEquals(1, w.getStatus().intValue());
        assertNotNull(w.getUpdatedAt(), "updated_at 必须显式写 —— 本项目没有 MetaObjectHandler");
        verify(academicWarningMapper).updateById(w);
    }

    /**
     * 最要紧的一条：删除是**真删行**，跨院删预警的代价是数据没了。
     * 判据是 {@code deleteById} 一次都没被调用，不是返回码。
     */
    @Test
    @DisplayName("删除预警：预警不属于本院 ⇒ 403，且绝不 deleteById")
    void delete_rejectsForeignWarning() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(academicWarningMapper.countWarningInCollege(9L, COLLEGE_A)).thenReturn(0);

        ApiResponse<String> r = controller.deleteWarning(ADMIN_A, 9L);

        assertEquals(403, r.getCode());
        verify(academicWarningMapper, never()).deleteById(anyLong());
        verify(academicWarningMapper, never()).selectById(any());
    }

    /** 本院的预警照常能删（这条不改数据 —— selectById 是 mock 出来的）。 */
    @Test
    @DisplayName("删除预警：本院的预警 ⇒ 真删")
    void delete_allowsOwnWarning() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(academicWarningMapper.countWarningInCollege(9L, COLLEGE_A)).thenReturn(1);
        when(academicWarningMapper.selectById(9L)).thenReturn(new AcademicWarning());

        assertEquals(200, controller.deleteWarning(ADMIN_A, 9L).getCode());
        verify(academicWarningMapper).deleteById(9L);
    }

    /** 删别人的（uid 换成乙院管理员）⇒ 身份绑定先拒，连学院都查不出来。 */
    @Test
    @DisplayName("删除预警：uid 是别人 ⇒ 拒绝，且不 deleteById")
    void delete_rejectsForeignUserId() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");

        assertNotEquals(200, controller.deleteWarning(ADMIN_B, 9L).getCode());
        verify(academicWarningMapper, never()).deleteById(anyLong());
    }

    // ==================== ④ 详情接口的归属口径（W35，2026-10-10） ====================
    //
    // 上面 ② 钉住了 class-students / process-warning / delete-warning 的归属拒绝都是 403，
    // 但**详情两条漏了**：student-detail 与 teacher-detail 的"不属于本院"改前用的是
    // `error(String)`（code 500）—— 与本文件"越权一律 forbidden(403)"的头注、
    // 以及同类拒绝的实际口径都不一致，客户端会把"越权"读成"服务器错误"。
    // 因为此前没有任何用例钉这两条，口径漂到 500 也没人发现。下面两个方向各一条承重断言。

    /** 乙院的学生档案拿给甲院管理员 ⇒ 403（不是 500），且**在返回前不读成绩明细**。 */
    @Test
    @DisplayName("学生详情：学生不属于本院 ⇒ 403，且不读成绩表")
    void studentDetail_rejectsForeignStudent() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        StudentProfile sp = new StudentProfile();
        sp.setId(34L); sp.setClassId(30L); sp.setStudentId("2023029999"); sp.setName("外院生");
        when(studentProfileMapper.selectById(34L)).thenReturn(sp);
        // 本院名下没有 classId=30 这个班 ⇒ 学生不属于本院
        when(classesMapper.getClassesByCollegeId(COLLEGE_A)).thenReturn(List.of());

        ApiResponse<Map<String, Object>> r = controller.getStudentDetail(ADMIN_A, 34L);

        assertEquals(403, r.getCode());
        verify(scoreStatMapper, never()).selectStudentScoreRows(any());
    }

    /** 本院的学生照常返回（别把正常路径一起挡了）。 */
    @Test
    @DisplayName("学生详情：本院的学生 ⇒ 正常返回")
    void studentDetail_allowsOwnStudent() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        StudentProfile sp = new StudentProfile();
        sp.setId(3L); sp.setClassId(1L); sp.setStudentId("2023020616"); sp.setName("司明");
        when(studentProfileMapper.selectById(3L)).thenReturn(sp);
        Map<String, Object> cls = new HashMap<>(); cls.put("id", 1L);
        when(classesMapper.getClassesByCollegeId(COLLEGE_A)).thenReturn(List.of(cls));
        when(scoreStatMapper.selectStudentScoreRows(3L)).thenReturn(List.of());
        when(scoreStatMapper.selectOneStudentScoreStats(3L)).thenReturn(null);
        when(academicWarningMapper.selectList(any())).thenReturn(List.of());

        ApiResponse<Map<String, Object>> r = controller.getStudentDetail(ADMIN_A, 3L);

        assertEquals(200, r.getCode());
        assertEquals("司明", r.getData().get("name"));
    }

    /** 乙院的教师档案拿给甲院管理员 ⇒ 403（不是 500），且不读该教师的成绩。 */
    @Test
    @DisplayName("教师详情：教师不属于本院 ⇒ 403，且不读成绩表")
    void teacherDetail_rejectsForeignTeacher() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        TeacherProfile tp = new TeacherProfile();
        tp.setId(7L); tp.setUserId(55L); tp.setCollegeId(COLLEGE_B); tp.setName("外院师");
        when(teacherProfileMapper.selectById(7L)).thenReturn(tp);

        ApiResponse<Map<String, Object>> r = controller.getTeacherDetail(ADMIN_A, 7L);

        assertEquals(403, r.getCode());
        verify(scoreStatMapper, never()).selectTeacherScoreRows(any(), any());
    }

    /** 本院的教师照常返回。 */
    @Test
    @DisplayName("教师详情：本院的教师 ⇒ 正常返回")
    void teacherDetail_allowsOwnTeacher() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        TeacherProfile tp = new TeacherProfile();
        tp.setId(1L); tp.setUserId(29L); tp.setCollegeId(COLLEGE_A); tp.setName("李明");
        when(teacherProfileMapper.selectById(1L)).thenReturn(tp);
        when(scoreStatMapper.selectTeacherScoreRows(COLLEGE_A, 29L)).thenReturn(List.of());

        ApiResponse<Map<String, Object>> r = controller.getTeacherDetail(ADMIN_A, 1L);

        assertEquals(200, r.getCode());
        assertEquals("李明", r.getData().get("name"));
    }

    /**
     * "教师不存在"与"不属于本院"是两回事 —— 前者是 500（沿用本文件服务器侧失败的既有口径），
     * 后者是 403。合并成一个码会让前端分不清"查不到人"和"没权限"。
     */
    @Test
    @DisplayName("教师详情：教师不存在 ⇒ 500「教师不存在」，且不读成绩表")
    void teacherDetail_missingTeacher() {
        login(ADMIN_A, "ROLE_COLLEGE_ADMIN");
        profileOf(ADMIN_A, COLLEGE_A);
        when(teacherProfileMapper.selectById(9999L)).thenReturn(null);

        ApiResponse<Map<String, Object>> r = controller.getTeacherDetail(ADMIN_A, 9999L);

        assertEquals(500, r.getCode());
        assertTrue(r.getMessage().contains("不存在"), "实际: " + r.getMessage());
        verify(scoreStatMapper, never()).selectTeacherScoreRows(any(), any());
    }
}
