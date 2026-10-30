package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.entity.Score;
import com.academic.common.util.ExcelUploadErrors;
import com.academic.entity.StudentProfile;
import com.academic.service.CourseService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理员端成绩导入（{@code POST /admin/import/scores}）的判据。
 *
 * <p><b>W19（2026-10-04）</b>：端点原收 {@code @RequestBody List<Map>}，而前端
 * {@code AdminDataExport.vue} 发的是一个 {@code el-upload} 的 {@code UploadFile}
 * 对象（{@code {name,url,status,raw}} —— 不是文件内容）。两边从第一天起就对不上，
 * 这个按钮一次都没成功过。改成 multipart + POI 之后，端到端才露出三个真 bug。
 *
 * <p><b>W38（2026-10-10）</b>：<b>落库目标由 {@code score_record}（暂存表）改为
 * 权威表 {@code scores}</b>。W36 已把教师端、辅导员端、导出全部迁到 {@code scores}，
 * 这里是"两套成绩口径"最后一个写方。从此：
 * <ul>
 *   <li>不再有"先写暂存表、再二跳同步"的顺序问题 —— 两张表不可能再分叉；</li>
 *   <li>{@code inserted}/{@code updated} 直接由 {@code scores} 的 (学生,课程,学期)
 *       查重结果决定；结果里不再有 {@code synced}/{@code syncFailed}
 *       （前端只读 {@code success/skipped/errors}，安全）。</li>
 * </ul>
 *
 * <p>所以本类的判据从"捕获 {@code List<ScoreRecord>}喂给 {@code saveBatch}"改成
 * "<b>捕获 {@code Score} 看 {@code scoreMapper.insert/updateById}</b>"。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController 成绩导入（multipart → scores，W19/W38）")
class AdminControllerImportScoresTest {

    @Mock private CourseService courseService;
    @Mock private com.academic.mapper.ScoreMapper scoreMapper;
    @Mock private com.academic.mapper.StudentProfileMapper studentProfileMapper;

    @InjectMocks private AdminController controller;

    private static final String STUDENT_NO = "2023020616";
    private static final long PROFILE_ID = 3L;
    private static final long COURSE_ID = 10L;
    /** 课程 10 的任课教师（活库真值：users.id=29）。 */
    private static final long COURSE_TEACHER = 29L;

    private static MultipartFile xlsx(String[] headers, Object[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number) row.createCell(c).setCellValue(((Number) v).doubleValue());
                    else row.createCell(c).setCellValue(String.valueOf(v));
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "scores.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    /**
     * 同 {@link #xlsx}，但写成**旧版 .xls**（HSSF / OLE2 容器）。
     * W22 之前这个端点是 {@code new XSSFWorkbook(...)}，读这种文件必抛异常 ——
     * 而扩展名闸却写着"仅支持 .xlsx 或 .xls"，所以必须有一条测试盯着它。
     */
    private static MultipartFile xls(String[] headers, Object[][] rows) throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number) row.createCell(c).setCellValue(((Number) v).doubleValue());
                    else row.createCell(c).setCellValue(String.valueOf(v));
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "scores.xls", "application/vnd.ms-excel", out.toByteArray());
        }
    }

    private void oneStudentAndCourse() {
        StudentProfile profile = new StudentProfile();
        profile.setId(PROFILE_ID);
        profile.setStudentId(STUDENT_NO);
        profile.setName("司明");
        profile.setClassName("计科2306班");
        when(studentProfileMapper.selectOne(any())).thenReturn(profile);

        Course course = new Course();
        course.setId(COURSE_ID);
        course.setName("高等数学");
        course.setTeacherId(COURSE_TEACHER);
        when(courseService.getOne(any())).thenReturn(course);
    }

    // ================================================== 文件与表头

    @Test
    @DisplayName("空文件 ⇒ 明确报错，不触库")
    void emptyFileRejected() {
        MultipartFile empty = new MockMultipartFile("file", "scores.xlsx", "application/vnd.ms-excel", new byte[0]);

        ApiResponse<Map<String, Object>> res = controller.importScores(empty);

        assertTrue(String.valueOf(res.getMessage()).contains("文件为空"), "实际：" + res.getMessage());
        verifyNoInteractions(studentProfileMapper, scoreMapper);
    }

    @Test
    @DisplayName("非 Excel 后缀 ⇒ 明确报错（.txt 改名成 .xlsx 的不算，后缀这关先拦）")
    void wrongExtensionRejected() {
        MultipartFile txt = new MockMultipartFile("file", "scores.txt", "text/plain", "学号,成绩".getBytes());

        ApiResponse<Map<String, Object>> res = controller.importScores(txt);

        assertTrue(String.valueOf(res.getMessage()).contains("仅支持"), "实际：" + res.getMessage());
        verifyNoInteractions(studentProfileMapper, scoreMapper);
    }

    @Test
    @DisplayName("表头缺「学号」⇒ 明确报错并指向模板，不触库")
    void missingStudentIdHeaderRejected() throws Exception {
        MultipartFile file = xlsx(new String[]{"姓名", "课程名称", "成绩"}, new Object[][]{{"司明", "高等数学", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertTrue(String.valueOf(res.getMessage()).contains("「学号」列"), "实际：" + res.getMessage());
        verifyNoInteractions(studentProfileMapper, scoreMapper);
    }

    // ================================================== 坏文件：回中文，不回 POI 英文（W22）

    @Test
    @DisplayName("改后缀名的假 Excel ⇒ 回中文提示，不把 POI 的英文原文丢给用户（W22）")
    void garbageRenamedToXlsxGetsChinese() {
        byte[] junk = new byte[2048];
        new java.util.Random(42).nextBytes(junk);
        MultipartFile fake = new MockMultipartFile("file", "scores.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", junk);

        ApiResponse<Map<String, Object>> res = controller.importScores(fake);

        // 判据要能用两种方式打红：
        //   1) 翻译没了 ⇒ message 变回 "Can't open workbook - unsupported file type: UNKNOWN"
        //   2) 兜底没了（解析层直接放行到外层中性文案）⇒ message 变成"服务器处理这个文件时出错"
        assertEquals(ExcelUploadErrors.UNREADABLE, res.getMessage(), "实际：" + res.getMessage());
        assertFalse(String.valueOf(res.getMessage()).contains("unsupported file type"),
                "POI 的英文原文不许出现在给用户的文案里");
        verifyNoInteractions(studentProfileMapper, scoreMapper);
    }

    @Test
    @DisplayName("★ 真 .xls（HSSF）现在真的能导进来 —— 扩展名闸那句「或 .xls」不再是空话（W22）")
    void realXlsIsAcceptedNotRejected() throws Exception {
        oneStudentAndCourse();
        when(scoreMapper.selectByStudentCourseSemester(any(), any(), any())).thenReturn(null);

        MultipartFile file = xls(new String[]{"学号", "课程名称", "成绩"},
                new Object[][]{{STUDENT_NO, "高等数学", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        // 换 WorkbookFactory 之前，真 .xls 会抛 OLE2NotOfficeXmlFormatException，
        // 用户看到的是 "…appears to be in the OLE2 Format…" 和一句"导入失败"。
        assertEquals(1, res.getData().get("success"),
                "真 .xls 应当能读进来；实际：" + res.getMessage());
        verify(scoreMapper).insert(any(Score.class));
    }

    @Test
    @DisplayName("表头缺「课程名称」⇒ 明确报错（只有学号和成绩，不知道是哪门课）")
    void missingCourseNameHeaderRejected() throws Exception {
        MultipartFile file = xlsx(new String[]{"学号", "成绩"}, new Object[][]{{STUDENT_NO, 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertTrue(String.valueOf(res.getMessage()).contains("「课程名称」列"), "实际：" + res.getMessage());
        verifyNoInteractions(studentProfileMapper, scoreMapper);
    }

    // ================================================== 逐行

    @Test
    @DisplayName("学号不存在 ⇒ 计入 skipped 并给出原因，一行都不落库")
    void unknownStudentSkipped() throws Exception {
        when(studentProfileMapper.selectOne(any())).thenReturn(null);
        MultipartFile file = xlsx(new String[]{"学号", "课程名称", "成绩"},
                new Object[][]{{"9999999999", "高等数学", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertEquals(1, res.getData().get("skipped"));
        assertEquals(0, res.getData().get("success"));
        assertTrue(String.valueOf(res.getData().get("errors")).contains("学号不存在"));
        verify(scoreMapper, never()).insert(any());
        verify(scoreMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("★ 合法行 ⇒ 学号/课程名换成库里的 id 后**直写 scores**（studentId 必须是 profile id，不是学号）")
    void validRowResolvesIds() throws Exception {
        oneStudentAndCourse();
        when(scoreMapper.selectByStudentCourseSemester(any(), any(), any())).thenReturn(null);   // 没查到 → 新增

        // 学号写成**数字**单元格：POI 读出来是 double，按 long 输出才是 "2023020616" 而不是 "…616.0"
        MultipartFile file = xlsx(new String[]{"学号(必填)", "课程名称(必填)", "成绩", "学期", "备注"},
                new Object[][]{{Double.parseDouble(STUDENT_NO), "高等数学", 88.5, "5", "补录"}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertEquals(200, res.getCode(), "实际：" + res.getMessage());
        assertEquals(1, res.getData().get("success"));
        assertEquals(1, res.getData().get("inserted"));
        assertEquals(0, res.getData().get("updated"));
        // 两跳同步没了 ⇒ 结果里**不该**再有 synced/syncFailed 这两个键
        assertFalse(res.getData().containsKey("synced"), "落库已一步到位，不应再报 synced");
        assertFalse(res.getData().containsKey("syncFailed"));

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(captor.capture());
        verify(scoreMapper, never()).updateById(any());

        Score saved = captor.getValue();
        assertEquals(PROFILE_ID, saved.getStudentId().longValue(),
                "落库的必须是 student_profile.id（" + PROFILE_ID + "），不是学号 " + STUDENT_NO);
        assertEquals(COURSE_ID, saved.getCourseId().longValue());
        assertEquals("5", saved.getSemester(), "scores.semester 是 int NOT NULL，落库要归一成序号串");
        assertEquals(0, saved.getScoreTotal().compareTo(new java.math.BigDecimal("88.5")));
        assertEquals(0, saved.getFinalScore().compareTo(new java.math.BigDecimal("88.5")),
                "final_score 为空会被「期末未录入」口径整体排除，导入的成绩就永远不会参与预警");
        assertEquals("补录", saved.getRemark(), "scores.remark 有列，不写就是静默丢字段");
        // 派生列：等级/绩点必须与教师端录入算出来的一致
        assertEquals("A-", saved.getGrade());
        assertEquals(0, saved.getGradePoint().compareTo(new java.math.BigDecimal("3.7")));
        assertEquals(2025, saved.getYear().intValue(), "入学年 2023 + (学期 5 - 1)/2 = 2025");
        assertEquals(COURSE_TEACHER, saved.getModifiedBy().longValue(), "改动人记该课的任课教师");
    }

    @Test
    @DisplayName("★ 结果键里 inserted/updated 由 scores 的查重决定：已有行 ⇒ updateById，不 insert")
    void existingRowGoesThroughUpdate() throws Exception {
        oneStudentAndCourse();
        Score existing = new Score();
        existing.setId(77L);
        existing.setStudentId(PROFILE_ID);
        existing.setCourseId(COURSE_ID);
        existing.setSemester("5");
        when(scoreMapper.selectByStudentCourseSemester(PROFILE_ID, COURSE_ID, "5")).thenReturn(existing);

        MultipartFile file = xlsx(new String[]{"学号", "课程名称", "成绩", "学期"},
                new Object[][]{{STUDENT_NO, "高等数学", 91, "5"}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertEquals(1, res.getData().get("success"));
        assertEquals(0, res.getData().get("inserted"));
        assertEquals(1, res.getData().get("updated"));
        verify(scoreMapper, never()).insert(any());

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(captor.capture());
        assertEquals(77L, captor.getValue().getId().longValue(), "更新的是原来那一行");
        assertEquals("A", captor.getValue().getGrade(), "91 分 ⇒ A（旧值 88.5 的 A- 必须被重算覆盖）");
    }

    @Test
    @DisplayName("★ 不写学期 ⇒ 退到该生该课最近一条学期序号（不是硬编码 1）")
    void missingSemesterFallsBackToMax() throws Exception {
        oneStudentAndCourse();
        when(scoreMapper.selectLatestSemester(PROFILE_ID, COURSE_ID)).thenReturn(6);
        when(scoreMapper.selectByStudentCourseSemester(PROFILE_ID, COURSE_ID, "6")).thenReturn(null);

        MultipartFile file = xlsx(new String[]{"学号", "课程名称", "成绩"},
                new Object[][]{{STUDENT_NO, "高等数学", 88}});

        controller.importScores(file);

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(captor.capture());
        assertEquals("6", captor.getValue().getSemester(),
                "没给学期时不能比历史记录更旧，否则在「每门课取最新一次」口径下会被直接丢弃");
    }

    @Test
    @DisplayName("★ 学期不是数字 ⇒ 该行如实报错且不写库（不是把 'abc' 塞进 int 列撞 SQL 错）")
    void nonNumericSemesterRejectedPerRow() throws Exception {
        // 刻意**不**喂学生/课程：学期校验在查学生之前，报错理由必须是"学期"而不是"学号不存在"。
        MultipartFile file = xlsx(new String[]{"学号", "课程名称", "成绩", "学期"},
                new Object[][]{{STUDENT_NO, "高等数学", 88, "abc"}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertEquals(1, res.getData().get("skipped"));
        assertEquals(0, res.getData().get("success"));
        assertTrue(String.valueOf(res.getData().get("errors")).contains("学期必须是"),
                "实际：" + res.getData().get("errors"));
        verify(scoreMapper, never()).insert(any());
        verify(scoreMapper, never()).selectByStudentCourseSemester(any(), any(), any());
    }

    @Test
    @DisplayName("★ 「平时/期末/成绩」三列同时存在 ⇒ score 取的是「成绩」那一列，不是先出现的「平时成绩」")
    void scoreColumnNotStolenByRegularScore() throws Exception {
        oneStudentAndCourse();
        when(scoreMapper.selectByStudentCourseSemester(any(), any(), any())).thenReturn(null);

        MultipartFile file = xlsx(new String[]{"学号", "课程名称", "平时成绩", "期末成绩", "成绩"},
                new Object[][]{{STUDENT_NO, "高等数学", 70, 80, 95}});

        controller.importScores(file);

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(captor.capture());

        Score saved = captor.getValue();
        assertEquals(0, saved.getScoreTotal().compareTo(new java.math.BigDecimal("95")),
                "「成绩」列是 95，却被「平时成绩」（70）抢先匹配了");
        assertEquals(0, saved.getRegularScore().compareTo(new java.math.BigDecimal("70")));
        assertEquals(0, saved.getFinalScore().compareTo(new java.math.BigDecimal("80")));
        assertEquals("A", saved.getGrade());
    }
}
