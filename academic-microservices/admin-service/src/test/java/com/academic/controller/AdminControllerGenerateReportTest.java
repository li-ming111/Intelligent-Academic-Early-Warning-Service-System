package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.College;
import com.academic.entity.ReportTemplate;
import com.academic.mapper.CounselorProfileMapper;
import com.academic.mapper.StudentProfileMapper;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.service.CollegeService;
import com.academic.service.ReportTemplateService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 管理员端「报表生成」的判据（W23，2026-10-04）。
 *
 * <p><b>为什么有这个测试</b>：{@code AdminDataExport.vue} 的「上传自定义模板」卡片上写着
 * 「自定义模板可以在生成报表时使用」，但下拉里**只列系统模板**，而且 {@code /admin/reports/generate}
 * 把 {@code template_id} 硬白名单化成 {@code {"1".."4"}} —— 自定义模板根本进不去。
 * 真正能吃自定义模板的 {@code POST /admin/reports/generate-with-template} **零调用方**。
 * 本轮把这条线接上，这里钉住接线之后的行为：
 * <ul>
 *   <li>两个生成端点都回 {@code rows}（真的写进去的数据行数），前端据此如实提示；</li>
 *   <li>自定义模板路径额外回 {@code matched}，把"表头没认出来"与"认出来了但没数据"分开；</li>
 *   <li>系统模板路径**不放** {@code matched} 键（不是 false，是不适用）。</li>
 * </ul>
 *
 * <p>用模板 4（学院对比报表）做正向例子：它的数据源只要 {@code collegeService.list()}，
 * 一张 {@code colleges} 行就能产出 1 行数据，不必把四张内置报表的依赖全 mock 一遍。
 * "表头匹配上内置报表"那几条的**真实数据**由端到端探针证（这里证的是分支语义）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController 报表生成（W23：rows / matched）")
class AdminControllerGenerateReportTest {

    @Mock private ReportTemplateService reportTemplateService;
    @Mock private CollegeService collegeService;
    @Mock private StudentProfileMapper studentProfileMapper;
    @Mock private TeacherProfileMapper teacherProfileMapper;
    @Mock private CounselorProfileMapper counselorProfileMapper;

    @InjectMocks private AdminController controller;

    /** 模板 4「学院对比报表」的表头（必须与 getReportHeaders("4") 逐字一致）。 */
    private static final String[] COLLEGE_HEADERS = {"学院名称", "学生人数", "教师人数", "辅导员人数"};
    /** 一张表头谁都匹配不上的模板。 */
    private static final String[] NOBODY_HEADERS = {"我自己想的列", "另一个列"};

    @TempDir Path tempDir;

    @BeforeEach
    void pointExportDirAtTemp() {
        // exportDir 是 @Value 注入的；单测里不经 Spring，不设的话会变成字面量 "null"
        // 目录，把文件写到工作目录下（而且会在仓库里留一个叫 null 的文件夹）。
        ReflectionTestUtils.setField(controller, "exportDir", tempDir.toString());
    }

    // ---------- 工具 ----------

    /** 在临时目录里写一份只有表头的 xlsx，返回它的绝对路径。 */
    private String writeTemplateFile(String fileName, String... headers) throws Exception {
        Path file = tempDir.resolve(fileName);
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file.toFile())) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            wb.write(out);
        }
        return file.toAbsolutePath().toString();
    }

    /** 让 getTemplateById(7L) 返回一个 filePath 指向 tempDir 里这份模板的实体。 */
    private void stubTemplate(long id, String templatePath) {
        ReportTemplate t = new ReportTemplate();
        t.setId(id);
        t.setName("我的模板");
        t.setFilePath(templatePath);
        when(reportTemplateService.getTemplateById(id)).thenReturn(t);
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ---------- 自定义模板 ----------

    @Test
    @DisplayName("模板 id 不存在 ⇒ 报错，不生成文件")
    void customTemplateThatDoesNotExistIsRejected() {
        when(reportTemplateService.getTemplateById(99L)).thenReturn(null);

        ApiResponse<Map<String, Object>> res =
                controller.generateReportWithTemplate(body("custom_template_id", "99"));

        assertFalse(res.isSuccess(), "模板不存在不能算生成成功");
        assertTrue(String.valueOf(res.getMessage()).contains("模板不存在"), "实际：" + res.getMessage());
        assertEquals(0, tempDir.toFile().listFiles().length, "失败时不该在导出目录留下文件");
    }

    @Test
    @DisplayName("表头与模板 4 逐字一致 ⇒ matched=true，且填入的是真实数据行")
    void headersMatchingABuiltinReportFillRealRows() throws Exception {
        stubTemplate(7L, writeTemplateFile("m4.xlsx", COLLEGE_HEADERS));
        when(collegeService.list()).thenReturn(List.of(college(1L, "计算机与人工智能学院")));

        ApiResponse<Map<String, Object>> res =
                controller.generateReportWithTemplate(body("custom_template_id", "7"));

        assertTrue(res.isSuccess(), "实际：" + res.getMessage());
        assertEquals(Boolean.TRUE, res.getData().get("matched"), "表头认出了模板 4");
        assertEquals(1, ((Number) res.getData().get("rows")).intValue(),
                "colleges 里有 1 行 ⇒ 报表应当写进去 1 行数据。实际：" + res.getData().get("rows"));
    }

    @Test
    @DisplayName("★ 表头认出了报表、但那张报表当前没有数据 ⇒ matched=true 而 rows=0（两种事分得开）")
    void matchedButEmptyIsDifferentFromNotMatched() throws Exception {
        stubTemplate(7L, writeTemplateFile("m4-empty.xlsx", COLLEGE_HEADERS));
        when(collegeService.list()).thenReturn(List.of());   // colleges 表为空

        ApiResponse<Map<String, Object>> res =
                controller.generateReportWithTemplate(body("custom_template_id", "7"));

        assertTrue(res.isSuccess());
        assertEquals(Boolean.TRUE, res.getData().get("matched"),
                "表头是认得出来的 —— 这跟「认不出来」是两回事，前端要说的话也不同");
        assertEquals(0, ((Number) res.getData().get("rows")).intValue());
    }

    @Test
    @DisplayName("表头谁都匹配不上 ⇒ matched=false、rows=0，且**不去查任何报表数据**")
    void unknownHeadersAreReportedAsNotMatched() throws Exception {
        stubTemplate(7L, writeTemplateFile("unknown.xlsx", NOBODY_HEADERS));

        ApiResponse<Map<String, Object>> res =
                controller.generateReportWithTemplate(body("custom_template_id", "7"));

        assertTrue(res.isSuccess(), "认不出表头不是错误，是一份只有表头的报表");
        assertEquals(Boolean.FALSE, res.getData().get("matched"));
        assertEquals(0, ((Number) res.getData().get("rows")).intValue());
        verifyNoInteractions(collegeService);
    }

    // ---------- 系统模板 ----------

    @Test
    @DisplayName("系统模板路径（/reports/generate）也回 rows，且**不放** matched 键")
    void systemTemplatePathReportsRowsWithoutMatchedKey() throws Exception {
        when(collegeService.list()).thenReturn(List.of(college(1L, "计算机与人工智能学院")));

        ApiResponse<Map<String, Object>> res = controller.generateReport(body("template_id", "4"));

        assertTrue(res.isSuccess(), "实际：" + res.getMessage());
        assertEquals(1, ((Number) res.getData().get("rows")).intValue());
        assertFalse(res.getData().containsKey("matched"),
                "matched 只对自定义模板有意义；系统模板路径不该放这个键（不是 false，是不适用）");
    }

    @Test
    @DisplayName("系统模板 id 不在白名单 ⇒ 明确报错，而不是产出一份全是占位列的文件")
    void unsupportedSystemTemplateIsRejected() {
        ApiResponse<Map<String, Object>> res = controller.generateReport(body("template_id", "99"));

        assertFalse(res.isSuccess());
        assertTrue(String.valueOf(res.getMessage()).contains("不支持的报表模板"),
                "实际：" + res.getMessage());
        assertEquals(0, tempDir.toFile().listFiles().length);
    }

    private static College college(Long id, String name) {
        College c = new College();
        c.setId(id);
        c.setName(name);
        return c;
    }
}
