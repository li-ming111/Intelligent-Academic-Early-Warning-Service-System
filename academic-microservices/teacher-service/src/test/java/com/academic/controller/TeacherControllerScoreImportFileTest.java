package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Course;
import com.academic.service.CourseService;
import com.academic.service.ScoreRecordService;
import jakarta.servlet.http.HttpServletRequest;
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
 * 教师端成绩导入（{@code POST /teachers/scores/import}）的**文件解析**判据（W19，2026-10-04）。
 *
 * <p>这个端点此前收 JSON、前端发 {@code FormData}，两边对不上，**一次都没成功过**；
 * 换成 multipart 后真正需要钉住的是"Excel 里的东西有没有被正确理解"：
 *
 * <ul>
 *   <li><b>学号 → {@code student_profile.id}</b>：{@code saveScores} 要的是档案 id，
 *       不是学号。这一处最容易写成直接把学号传下去 —— 用一个和档案 id **明显不同**的学号
 *       来断言（学号 2023020616 → 档案 id 3），传错必红。</li>
 *   <li><b>「总评成绩」不能被「平时成绩」抢走</b>：三列都含「成绩」，
 *       {@code findColumn(header,"成绩")} 会回第一个匹配的列（平时），
 *       于是总评分数静默落空。</li>
 *   <li><b>姓名缺失时回填</b>：否则导进去的行在成绩列表里只有分数没有姓名。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherController 成绩导入文件解析（W19）")
class TeacherControllerScoreImportFileTest {

    @Mock private CourseService courseService;
    @Mock private ScoreRecordService scoreRecordService;
    @Mock private com.academic.mapper.ClassInfoMapper classInfoMapper;

    @InjectMocks private TeacherController controller;

    private static final long ME = 18L;
    private static final long MY_COURSE = 6L;
    /** 学号（varchar）—— 与档案 id 3 明显不同，用来证明传下去的是档案 id 而不是学号。 */
    private static final String STUDENT_NO = "2023020616";
    private static final long PROFILE_ID = 3L;

    private static HttpServletRequest as(long userId) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        return r;
    }

    /** 在内存里造一个真 xlsx（POI 写、POI 读，与生产同一条路径）。 */
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

    private void stubMyCourse() {
        Course c = new Course();
        c.setId(MY_COURSE);
        c.setTeacherId(ME);
        when(courseService.getById(MY_COURSE)).thenReturn(c);
    }

    // ================================================== 表头

    @Test
    @DisplayName("表头没有「学号」列 ⇒ 明确报错，不写库")
    void missingStudentIdHeaderRejected() throws Exception {
        stubMyCourse();
        MultipartFile file = xlsx(new String[]{"姓名", "成绩"}, new Object[][]{{"司明", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        assertTrue(String.valueOf(res.getMessage()).contains("「学号」列"), "实际：" + res.getMessage());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("表头没有「成绩」列 ⇒ 明确报错，不写库")
    void missingScoreHeaderRejected() throws Exception {
        stubMyCourse();
        MultipartFile file = xlsx(new String[]{"学号", "姓名"}, new Object[][]{{STUDENT_NO, "司明"}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        assertTrue(String.valueOf(res.getMessage()).contains("「成绩」列"), "实际：" + res.getMessage());
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    // ================================================== 逐行

    @Test
    @DisplayName("学号不存在 ⇒ 计入 skipped 并给出原因，其余行不受影响")
    void unknownStudentSkipped() throws Exception {
        stubMyCourse();
        when(classInfoMapper.findProfileIdByStudentId("9999999999")).thenReturn(null);
        MultipartFile file = xlsx(new String[]{"学号", "成绩"}, new Object[][]{{"9999999999", 88}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        assertEquals(200, res.getCode());
        Map<String, Object> data = res.getData();
        assertEquals(1, data.get("skipped"));
        assertEquals(0, data.get("success"));
        assertTrue(String.valueOf(data.get("errors")).contains("学号不存在"), "实际：" + data.get("errors"));
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("成绩为空 ⇒ 计入 skipped（不是静默按 0 分导入）")
    void emptyScoreSkipped() throws Exception {
        stubMyCourse();
        MultipartFile file = xlsx(new String[]{"学号", "成绩"}, new Object[][]{{STUDENT_NO, null}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        assertEquals(1, res.getData().get("skipped"));
        assertTrue(String.valueOf(res.getData().get("errors")).contains("成绩为空"));
        verify(scoreRecordService, never()).saveScores(any(), any());
    }

    @Test
    @DisplayName("★ 合法行：学号换算成 student_profile.id、姓名从档案回填（学号列写成数字也不能变成 .0）")
    void validRowResolvesProfileIdAndBackfillsName() throws Exception {
        stubMyCourse();
        when(classInfoMapper.findProfileIdByStudentId(STUDENT_NO)).thenReturn(PROFILE_ID);
        when(classInfoMapper.findStudentNameByStudentId(STUDENT_NO)).thenReturn("司明");
        // 学号写成**数字**单元格 —— POI 读出来是 double，cellText 必须按 long 输出
        MultipartFile file = xlsx(new String[]{"学号", "总评成绩"},
                new Object[][]{{Double.parseDouble(STUDENT_NO), 88.5}});

        ApiResponse<Map<String, Object>> res =
                controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        assertEquals(200, res.getCode(), "实际：" + res.getMessage());
        assertEquals(1, res.getData().get("success"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoreRecordService).saveScores(captor.capture(), eq(ME));

        Map<String, Object> row = captor.getValue().get(0);
        Object forwardedId = row.get("studentId");
        // 先单独断言类型：真要是把学号原样传下去，这里给的是"类型不对"而不是一句 CCE 堆栈。
        assertInstanceOf(Number.class, forwardedId,
                "studentId 必须先换算成 student_profile.id，实际传下去的是：" + forwardedId);
        assertEquals(PROFILE_ID, ((Number) forwardedId).longValue(),
                "传下去的必须是 student_profile.id（" + PROFILE_ID + "），不是学号 " + STUDENT_NO);
        assertEquals(MY_COURSE, ((Number) row.get("courseId")).longValue());
        assertEquals(88.5, ((Number) row.get("score")).doubleValue(), 1e-9);
        assertEquals("司明", row.get("studentName"));
        assertEquals("计科2306班", row.get("className"));
        assertEquals("5", row.get("semester"));
    }

    @Test
    @DisplayName("★ 「平时/期末/总评」三列同时存在 ⇒ 取的是**总评**（不能被「平时成绩」抢先匹配）")
    void prefersTotalScoreColumn() throws Exception {
        stubMyCourse();
        when(classInfoMapper.findProfileIdByStudentId(STUDENT_NO)).thenReturn(PROFILE_ID);
        MultipartFile file = xlsx(new String[]{"学号", "平时成绩", "期末成绩", "总评成绩"},
                new Object[][]{{STUDENT_NO, 70, 80, 95}});

        controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoreRecordService).saveScores(captor.capture(), eq(ME));
        assertEquals(95.0, ((Number) captor.getValue().get(0).get("score")).doubleValue(), 1e-9,
                "总评成绩 95 被读成了平时/期末分");
    }

    @Test
    @DisplayName("Excel 里填了姓名 ⇒ 用它，不再回查档案")
    void explicitNameWinsOverLookup() throws Exception {
        stubMyCourse();
        when(classInfoMapper.findProfileIdByStudentId(STUDENT_NO)).thenReturn(PROFILE_ID);
        MultipartFile file = xlsx(new String[]{"学号", "姓名", "成绩"},
                new Object[][]{{STUDENT_NO, "表格里的名字", 60}});

        controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoreRecordService).saveScores(captor.capture(), eq(ME));
        assertEquals("表格里的名字", captor.getValue().get(0).get("studentName"));
        verify(classInfoMapper, never()).findStudentNameByStudentId(anyString());
    }

    @Test
    @DisplayName("整行空 ⇒ 跳过且不计入 skipped/errors（不是错误）")
    void blankRowIgnored() throws Exception {
        stubMyCourse();
        MultipartFile file = xlsx(new String[]{"学号", "成绩"}, new Object[][]{{null, null}});

        ApiResponse<Map<String, Object>> res = controller.importScores(file, MY_COURSE, "计科2306班", "5", as(ME));

        assertEquals(0, res.getData().get("skipped"));
        assertEquals(0, res.getData().get("success"));
        verify(scoreRecordService, never()).saveScores(any(), any());
    }
}
