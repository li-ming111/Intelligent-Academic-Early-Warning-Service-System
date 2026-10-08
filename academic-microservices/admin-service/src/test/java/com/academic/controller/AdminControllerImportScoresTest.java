package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.common.entity.ScoreRecord;
import com.academic.common.util.ExcelUploadErrors;
import com.academic.entity.StudentProfile;
import com.academic.mapper.ScoreRecordMapper;
import com.academic.service.CourseService;
import com.academic.service.ScoreRecordService;
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
 * 管理员端成绩导入（{@code POST /admin/import/scores}）换成 multipart 之后的判据
 * （W19，2026-10-04）。
 *
 * <p><b>修之前是什么样</b>：端点收 {@code @RequestBody List<Map>}，而前端
 * {@code AdminDataExport.vue} 发的是一个 {@code el-upload} 的 {@code UploadFile}
 * 对象（{@code {name,url,status,raw}} —— 不是文件内容）。两边从第一天起就对不上，
 * 这个按钮一次都没成功过。同页的「导入学生」本来就是 multipart + POI，可作对照。
 *
 * <p>这里钉住的是**解析层**新增的判断：表头缺列要明确报错、学号/课程名要真的换算成
 * 库里的 id、以及「平时成绩/期末成绩/成绩」三列同时存在时**别取错列**。
 * 逐行落库的那段实现没有改动，行处理逻辑仍由既有的服务测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController 成绩导入（multipart，W19）")
class AdminControllerImportScoresTest {

    @Mock private CourseService courseService;
    @Mock private ScoreRecordService scoreRecordService;
    @Mock private ScoreRecordMapper scoreRecordMapper;
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

    // ================================================== 文件与表头

    @Test
    @DisplayName("空文件 ⇒ 明确报错，不触库")
    void emptyFileRejected() {
        MultipartFile empty = new MockMultipartFile("file", "scores.xlsx", "application/vnd.ms-excel", new byte[0]);

        ApiResponse<Map<String, Object>> res = controller.importScores(empty);

        assertTrue(String.valueOf(res.getMessage()).contains("文件为空"), "实际：" + res.getMessage());
        verifyNoInteractions(scoreRecordService, scoreRecordMapper, studentProfileMapper);
    }

    @Test
    @DisplayName("非 Excel 后缀 ⇒ 明确报错（.txt 改名成 .xlsx 的不算，后缀这关先拦）")
    void wrongExtensionRejected() {
        MultipartFile txt = new MockMultipartFile("file", "scores.txt", "text/plain", "学号,成绩".getBytes());

        ApiResponse<Map<String, Object>> res = controller.importScores(txt);

        assertTrue(String.valueOf(res.getMessage()).contains("仅支持"), "实际：" + res.getMessage());
        verifyNoInteractions(scoreRecordService, scoreRecordMapper, studentProfileMapper);
    }

    @Test
    @DisplayName("表头缺「学号」⇒ 明确报错并指向模板，不触库")
    void missingStudentIdHeaderRejected() throws Exception {
        MultipartFile file = xlsx(new String[]{"姓名", "课程名称", "成绩"}, new Object[][]{{"司明", "高等数学", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertTrue(String.valueOf(res.getMessage()).contains("「学号」列"), "实际：" + res.getMessage());
        verifyNoInteractions(scoreRecordService, scoreRecordMapper, studentProfileMapper);
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
        verifyNoInteractions(scoreRecordService, scoreRecordMapper, studentProfileMapper);
    }

    @Test
    @DisplayName("★ 真 .xls（HSSF）现在真的能导进来 —— 扩展名闸那句「或 .xls」不再是空话（W22）")
    void realXlsIsAcceptedNotRejected() throws Exception {
        StudentProfile profile = new StudentProfile();
        profile.setId(PROFILE_ID);
        profile.setName("司明");
        profile.setClassName("计科2306班");
        when(studentProfileMapper.selectOne(any())).thenReturn(profile);

        Course course = new Course();
        course.setId(COURSE_ID);
        course.setName("高等数学");
        course.setTeacherId(COURSE_TEACHER);
        when(courseService.getOne(any())).thenReturn(course);
        when(scoreRecordMapper.selectOne(any())).thenReturn(null);
        when(scoreMapper.selectByStudentCourseSemester(any(), any(), any())).thenReturn(null);

        MultipartFile file = xls(new String[]{"学号", "课程名称", "成绩"},
                new Object[][]{{STUDENT_NO, "高等数学", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        // 换 WorkbookFactory 之前，真 .xls 会抛 OLE2NotOfficeXmlFileException，
        // 用户看到的是 "…appears to be in the OLE2 Format…" 和一句"导入失败"。
        assertEquals(1, res.getData().get("success"),
                "真 .xls 应当能读进来；实际：" + res.getMessage());
        verify(scoreRecordService).saveBatch(any());
    }

    @Test
    @DisplayName("表头缺「课程名称」⇒ 明确报错（只有学号和成绩，不知道是哪门课）")
    void missingCourseNameHeaderRejected() throws Exception {
        MultipartFile file = xlsx(new String[]{"学号", "成绩"}, new Object[][]{{STUDENT_NO, 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertTrue(String.valueOf(res.getMessage()).contains("「课程名称」列"), "实际：" + res.getMessage());
        verifyNoInteractions(scoreRecordService, scoreRecordMapper, studentProfileMapper);
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
        verify(scoreRecordService, never()).saveBatch(any());
        verify(scoreMapper, never()).insert(any());
    }

    @Test
    @DisplayName("★ 合法行 ⇒ 学号/课程名换成库里的 id 后落库（studentId 必须是 profile id，不是学号）")
    void validRowResolvesIds() throws Exception {
        StudentProfile profile = new StudentProfile();
        profile.setId(PROFILE_ID);
        profile.setName("司明");
        profile.setClassName("计科2306班");
        when(studentProfileMapper.selectOne(any())).thenReturn(profile);

        Course course = new Course();
        course.setId(COURSE_ID);
        course.setName("高等数学");
        course.setTeacherId(COURSE_TEACHER);
        when(courseService.getOne(any())).thenReturn(course);

        when(scoreRecordMapper.selectOne(any())).thenReturn(null);          // 没查到重复 → 走新增
        when(scoreMapper.selectByStudentCourseSemester(any(), any(), any())).thenReturn(null);

        // 学号写成**数字**单元格：POI 读出来是 double，按 long 输出才是 "2023020616" 而不是 "…616.0"
        MultipartFile file = xlsx(new String[]{"学号(必填)", "课程名称(必填)", "成绩", "学期"},
                new Object[][]{{Double.parseDouble(STUDENT_NO), "高等数学", 88.5, "5"}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file);

        assertEquals(200, res.getCode(), "实际：" + res.getMessage());
        assertEquals(1, res.getData().get("success"));
        assertEquals(1, res.getData().get("inserted"));
        assertEquals(1, res.getData().get("synced"), "必须同步到 scores 权威表");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoreRecordService).saveBatch(captor.capture());

        ScoreRecord saved = captor.getValue().get(0);
        assertEquals(PROFILE_ID, saved.getStudentId().longValue(),
                "落库的必须是 student_profile.id（" + PROFILE_ID + "），不是学号 " + STUDENT_NO);
        assertEquals(COURSE_ID, saved.getCourseId().longValue());
        assertEquals(88.5, saved.getScore(), 1e-9);
        assertEquals("司明", saved.getStudentName(), "学生没给姓名时应从档案补");
        assertEquals("计科2306班", saved.getClassName());
        assertEquals("5", saved.getSemester());
        // score_record.teacher_id 是 NOT NULL 且无默认值：不补整批 INSERT 就整体失败
        // （实测 "Field 'teacher_id' doesn't have a default value"）。补的是**任课教师**。
        assertEquals(COURSE_TEACHER, saved.getTeacherId().longValue(),
                "teacher_id 必须从课程补上，否则整批 INSERT 会因 NOT NULL 失败");
        // 派生列要与权威表一致：原来只算了 score_level，grade/grade_point/score_total
        // 留在 NULL 上，而同步过去的 scores 行是 "A-" —— 同一份成绩两表各说各话。
        assertEquals("A-", saved.getGrade(), "等级必须与 scores 表算出来的一致");
        assertEquals(3.7, saved.getGradePoint(), 1e-9);
        assertEquals(88.5, saved.getScoreTotal(), 1e-9);
    }

    @Test
    @DisplayName("★ 「平时/期末/成绩」三列同时存在 ⇒ score 取的是「成绩」那一列，不是先出现的「平时成绩」")
    void scoreColumnNotStolenByRegularScore() throws Exception {
        StudentProfile profile = new StudentProfile();
        profile.setId(PROFILE_ID);
        profile.setName("司明");
        when(studentProfileMapper.selectOne(any())).thenReturn(profile);

        Course course = new Course();
        course.setId(COURSE_ID);
        course.setName("高等数学");
        when(courseService.getOne(any())).thenReturn(course);

        when(scoreRecordMapper.selectOne(any())).thenReturn(null);
        when(scoreMapper.selectByStudentCourseSemester(any(), any(), any())).thenReturn(null);

        MultipartFile file = xlsx(new String[]{"学号", "课程名称", "平时成绩", "期末成绩", "成绩"},
                new Object[][]{{STUDENT_NO, "高等数学", 70, 80, 95}});

        controller.importScores(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoreRecordService).saveBatch(captor.capture());

        ScoreRecord saved = captor.getValue().get(0);
        assertEquals(95.0, saved.getScore(), 1e-9,
                "「成绩」列是 95，却被「平时成绩」（70）抢先匹配了");
        assertEquals(70.0, saved.getRegularScore(), 1e-9);
        assertEquals(80.0, saved.getFinalScore(), 1e-9);
    }
}
