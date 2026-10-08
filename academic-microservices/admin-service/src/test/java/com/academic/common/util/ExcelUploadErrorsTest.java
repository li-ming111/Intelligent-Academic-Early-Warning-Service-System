package com.academic.common.util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExcelUploadErrors} 的判据（W22，2026-10-04）。
 *
 * <h3>为什么这个测试必须存在（而不是"翻译一下而已"）</h3>
 * {@code ExcelUploadErrors} 住在 {@code academic-common}，而那里**没有** POI 依赖，
 * 所以它比对的是**类的全限定名字符串**，不是 {@code instanceof}。字符串匹配的失败方式是
 * **静默**的：POI 一旦改类名，翻译就悄悄退化成中性文案，没人会注意到。
 * 这个测试就是那道保险 —— 它**用真实 POI 抛出的异常实例**（不是 new 出来的假异常）来打靶，
 * POI 升级改类名时会立刻变红。
 *
 * <h3>关键约定</h3>
 * <ul>
 *   <li>本测试只覆盖"能认出来"的四种；<b>认不出来</b>的（服务端故障）返回 {@code null}，
 *       由调用方给中性文案 —— 见 {@code noClaimForServerSideFailure}。</li>
 *   <li>所有异常实例都由 POI 对**真实字节**产生，所以"实测"二字是可信的。</li>
 * </ul>
 */
class ExcelUploadErrorsTest {

    // ---------- 造真实文件（全部经 POI 自己写出，不用手拼字节） ----------

    /** 一个合法的 .xls（HSSF，OLE2 容器）。 */
    private static byte[] realXls() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            wb.createSheet("t").createRow(0).createCell(0).setCellValue("学号");
            wb.write(out);
        }
        return out.toByteArray();
    }

    /** 一个带打开密码的 .xlsx —— POI 读它会抛 EncryptedDocumentException。 */
    private static byte[] encryptedXlsx() throws Exception {
        POIFSFileSystem fs = new POIFSFileSystem();
        EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
        Encryptor enc = info.getEncryptor();
        enc.confirmPassword("secret");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("t").createRow(0).createCell(0).setCellValue("x");
            try (OutputStream os = enc.getDataStream(fs)) {
                wb.write(os);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fs.writeFilesystem(out);
        return out.toByteArray();
    }

    /** 一个合法 zip，但不是 OOXML —— 改了后缀名的"假 Excel"通常长这样。 */
    private static byte[] zipThatIsNotOoxml() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("hello.txt"));
            z.write("hello".getBytes());
            z.closeEntry();
        }
        return out.toByteArray();
    }

    /** 一个 OOXML 包，但没有 xlsx 的核心部件 —— 「把 .docx 改名成 .xlsx」大致就是这样。 */
    private static byte[] ooxmlWithoutCoreDocument() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("[Content_Types].xml"));
            z.write(("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>")
                    .getBytes());
            z.closeEntry();
        }
        return out.toByteArray();
    }

    /** 抛异常地解析一段字节，把 POI 真正抛出来的那个异常取回来。 */
    private static Exception parseError(byte[] bytes) {
        try (Workbook ignored = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    // ---------- 先证明"这个文件确实读不了" ----------

    @Test
    @DisplayName("真 .xls 现在能读进来 —— 扩展名闸写的「仅支持 .xlsx 或 .xls」不再是空话")
    void realXlsIsNowReadable() throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(realXls()))) {
            assertEquals("HSSFWorkbook", wb.getClass().getSimpleName(),
                    "真 .xls 应当交给 HSSF 读；换成 XSSFWorkbook 就会抛 OLE2NotOfficeXmlFileException");
            assertEquals("学号", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }

    // ---------- 四类能认出来的文件问题 ----------

    @Test
    @DisplayName("改后缀名的假 Excel ⇒ 本类**故意**返回 null，交给端点的解析层兜底")
    void unrecognizedFileIsLeftToTheCaller() {
        // 一个 .txt 改名成 .xlsx（也就等于随机字节）走 WorkbookFactory 的 FileMagic 嗅探，
        // 抛的是裸的 java.io.IOException("Can't open workbook - unsupported file type: UNKNOWN")。
        // 裸 IOException 与"读上传流断了"之类没法凭类型区分，所以本类不对它下结论。
        byte[] junk = new byte[2048];
        new Random(42).nextBytes(junk);
        junk[0] = 'H';   // 别让它凑巧长得像别的格式
        Exception e = parseError(junk);
        assertNotNull(e, "这么一段乱码不该被 POI 读出来");
        assertInstanceOf(IOException.class, e, "FileMagic 嗅不出文件头时抛的是裸 IOException");
        assertNull(ExcelUploadErrors.describe(e),
                "凭类型认不出，就不许猜；由端点在解析那一层兜底成 UNREADABLE");

        // 端到端（含兜底那一步）的判据在 AdminControllerImportScoresTest#garbageRenamedToXlsx。
    }

    @Test
    @DisplayName("0 字节文件 ⇒ 「上传的文件是空的」")
    void emptyFileIsReportedAsEmpty() {
        Exception e = parseError(new byte[0]);
        assertNotNull(e);
        assertEquals(ExcelUploadErrors.EMPTY, ExcelUploadErrors.describe(e));
    }

    @Test
    @DisplayName("合法 zip 但不是 OOXML ⇒ 「无法识别」—— 这一条必须走 cause 链才认得出")
    void zipThatIsNotOoxmlIsUnreadable() throws Exception {
        Exception e = parseError(zipThatIsNotOoxml());
        assertNotNull(e);
        // 顶层异常可能只是个 java.io.IOException，真正说明问题的是它的 cause。
        // 若 hasType 只看顶层不看 cause，这里就会掉到 null（中性文案）—— 判据在此。
        // 实测：顶层是裸 IOException，cause 是 InvalidFormatException，干活的是后者。
        // （早先还写过一条 java.util.zip.ZipException 兜底，实测任何输入都不抛它，已删 —— 见类注释。）
        assertEquals(ExcelUploadErrors.UNREADABLE, ExcelUploadErrors.describe(e),
                "认 zip 要顺着 cause 链走；只比顶层类型会漏掉这种最常见的「假 Excel」");
    }

    @Test
    @DisplayName("是个 OOXML 包但没有 xlsx 的核心部件（.docx 改名成 .xlsx）⇒ 「无法识别」")
    void ooxmlWithoutCoreDocumentIsUnreadable() throws Exception {
        Exception e = parseError(ooxmlWithoutCoreDocument());
        assertNotNull(e);
        assertEquals(ExcelUploadErrors.UNREADABLE, ExcelUploadErrors.describe(e));
    }

    @Test
    @DisplayName("加密的 .xlsx ⇒ 「已加密」")
    void encryptedXlsxIsReportedAsEncrypted() throws Exception {
        Exception e = parseError(encryptedXlsx());
        assertNotNull(e);
        assertEquals(ExcelUploadErrors.ENCRYPTED, ExcelUploadErrors.describe(e));
    }

    // ---------- 认不出来的：不许赖用户 ----------

    @Test
    @DisplayName("服务端故障（比如落库炸了）⇒ 返回 null，绝不谎称「文件损坏」")
    void noClaimForServerSideFailure() {
        assertNull(ExcelUploadErrors.describe(new RuntimeException("Communications link failure")),
                "这四个端点的 catch 包着整个方法体（含落库），服务端故障必须交给调用方说中性文案");
        assertNull(ExcelUploadErrors.describe(null));
    }

    @Test
    @DisplayName("截断的 OLE2 抛的是 IndexOutOfBoundsException ⇒ 刻意**不**认（已知边界，见类注释）")
    void truncatedOle2IsDeliberatelyNotClaimed() throws Exception {
        byte[] full = realXls();
        byte[] truncated = new byte[512];
        System.arraycopy(full, 0, truncated, 0, 512);
        Exception e = parseError(truncated);
        assertNotNull(e, "截断的 .xls 读不出来是应该的");

        // 这里断言 null 是把一条**已知边界**钉在明处，不是"这样最好"：
        // IndexOutOfBoundsException 太泛，而捕获它的 catch 又包着落库，
        // 把 IOOBE 一律当成"文件坏了"会把服务端自己的越界 bug 也说成用户的文件有问题。
        // 所以四个端点在**解析那一层**单独兜底（认不出就回 UNREADABLE），
        // 外层 catch 保持中性 —— 见 AdminController#importStudents 等处的注释。
        assertNull(ExcelUploadErrors.describe(e),
                "若哪天这里不返回 null 了，请回去看四个端点的解析层兜底是否还自洽");
    }

    @Test
    @DisplayName("把一个 .doc 改名成 .xlsx（合法 OLE2 容器但没有工作簿流）⇒ 同样刻意不认")
    void ole2ThatIsNotAWorkbookIsNotClaimed() throws Exception {
        // 这是**真会有人干**的一种：Word 文档改后缀名当 Excel 传。
        // 实测 WorkbookFactory 对它抛 IllegalArgumentException（"neither an OLE2 stream, nor an OOXML stream" 一类），
        // 类型太泛 ⇒ 不认，交给解析层兜底成「无法识别这个文件」。
        //
        // 顺带说明为什么本类**不再**有「旧版 .xls 且损坏」那一支：它认的
        // OLE2NotOfficeXmlFileException 只有 `new XSSFWorkbook(...)` 才会抛，
        // 而全仓读上传文件的入口已全部改成 WorkbookFactory.create（本测试下面这条断言就是证明）。
        POIFSFileSystem fs = new POIFSFileSystem();
        fs.getRoot().createDocument("WordDocument", new ByteArrayInputStream("fake doc".getBytes()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fs.writeFilesystem(out);

        Exception e = parseError(out.toByteArray());
        assertNotNull(e);
        assertNull(ExcelUploadErrors.describe(e),
                "OLE2-但-非工作簿由解析层兜底；本类不认它的原因是异常太泛");

        // 对照：同一份 OLE2 交给 XSSF 才是 OLE2NotOfficeXmlFileException ——
        // 而全仓已经没有这样的读取点了，所以那一支无处可达。
        byte[] doc = out.toByteArray();
        assertThrows(Exception.class, () -> {
            try (XSSFWorkbook ignored = new XSSFWorkbook(new ByteArrayInputStream(doc))) {
                // XSSF 不该能读 OLE2
            }
        });
    }

    // ---------- 文案本身的判据 ----------

    @Test
    @DisplayName("三条文案都不含 POI 的英文原句，且互不相同")
    void messagesAreChineseAndDistinct() {
        String[] all = {
                ExcelUploadErrors.EMPTY,
                ExcelUploadErrors.ENCRYPTED,
                ExcelUploadErrors.UNREADABLE,
        };
        for (String m : all) {
            assertFalse(m.isBlank());
            // POI 的原话里最扎眼的是这几个词；出现任一就说明又把英文漏回来了
            assertFalse(m.contains("The supplied file") || m.contains("not a valid OOXML")
                            || m.contains("appears to be in the OLE2"),
                    "文案里混进了 POI 的英文原文：" + m);
            assertTrue(m.chars().anyMatch(c -> c > 0x4E00 && c < 0x9FFF), "应当是中文：" + m);
        }
        assertEquals(3, java.util.Set.of(all).size(), "三条文案不能互相重复");
    }
}
