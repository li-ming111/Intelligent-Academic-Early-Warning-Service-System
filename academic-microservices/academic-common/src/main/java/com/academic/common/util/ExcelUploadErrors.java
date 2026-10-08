package com.academic.common.util;

import java.util.Set;

/**
 * 把"上传的 Excel 解析失败"翻译成用户能看懂的中文。
 *
 * <p><b>W22 背景</b>：四个导入端点（{@code /admin/import/students}、{@code /admin/import/scores}、
 * {@code /teachers/students/import}、{@code /teachers/scores/import}）此前一律
 * {@code return ApiResponse.error("导入失败: " + e.getMessage())} —— 于是用户上传一个不是 Excel 的文件时，
 * 屏幕上出现的是 POI 的英文原文：
 * <pre>导入失败: No valid entries or contents found, this is not a valid OOXML (Office Open XML) file</pre>
 * 这是把**库的内部措辞**当成了给用户的话。本类负责翻译，原始异常与堆栈仍由调用方
 * {@code log.error(..., e)} 记在服务端日志里（排查靠日志，不靠回给前端的字符串）。
 *
 * <h3>为什么用"类名字符串"匹配，而不是 {@code instanceof}</h3>
 * 本类住在 {@code academic-common}，而 {@code academic-common} **没有** POI 依赖
 * （它是所有服务（含 gateway/auth/message）的公共基座，为一个工具类把 poi + poi-ooxml
 * （约 10MB）拖进每一个服务的 classpath 不划算）。所以这里比对**类的全限定名**。
 * 代价是：POI 若改类名，匹配会**静默失效**并退到中性文案 —— 安全方向的失败（不会谎报原因）。
 * 因此 {@code admin-service} 侧有一条**用真实 POI 异常实例**打的测试（{@code ExcelUploadErrorsTest}），
 * 它在 POI 升级后改类名时会**红**，把这个静默失效顶出来。
 *
 * <h3>下面这组类名是实测出来的，不是猜的</h3>
 * 用 POI 5.2.5 对各类坏文件各跑一次，抛的异常如下。
 * <b>注意这里量的是 {@code WorkbookFactory.create}（四个端点现在真正用的入口），
 * 不是 {@code new XSSFWorkbook}</b> —— 换入口会换异常：{@code WorkbookFactory} 先用
 * FileMagic 嗅探文件头，嗅不出来时抛的是**它自己的裸 {@code IOException}**，
 * 而不是 XSSF 那一族的异常。本类最初就是按错入口量的，已订正。
 * <ul>
 *   <li>0 字节 → {@code EmptyFileException}（"The supplied file was empty…"）</li>
 *   <li>加密的 .xlsx → {@code EncryptedDocumentException}（"…protected, but no password…"）</li>
 *   <li>合法 zip 但不是 OOXML → 顶层是 {@code java.io.IOException}，<b>cause 是</b>
 *       {@code InvalidFormatException}（"Package should contain a content type part"）
 *       —— 这条最容易猜错，所以匹配必须**走 cause 链**</li>
 *   <li>是 OOXML 但结构坏了 → 顶层 {@code POIXMLException}（"no core document found"）</li>
 *   <li>真 .xls（OLE2 头）→ <b>不再算错误</b>，{@code WorkbookFactory} 交给 HSSF 正常读出来
 *       —— 这正是换入口要买的东西</li>
 * </ul>
 * 集合里另外几个键（{@code NotOfficeXmlFileException}、{@code NotOLE2FileException} 等）是上表各项的
 * **近亲**：它们由"HSSF/XSSF 被喂错格式"那条老路抛出，改走 {@code WorkbookFactory} 之后当前取不到，
 * 但作为查表键留着不吃亏 —— POI 换个版本若把某个近亲顶到顶层，落进同一个集合仍是安全的收敛方向。
 *
 * <h3>这几种，本类<b>故意</b>不认（都交给端点的"解析层"兜底）</h3>
 * 它们的共同点是：异常类型太泛，认了就会**替服务端背锅**（见 {@link #describe} 关于 null 的说明）。
 * <ul>
 *   <li><b>改后缀名的假 Excel</b>（.txt/随机字节/空 zip）—— FileMagic 嗅探先失败，抛**裸**
 *       {@code java.io.IOException("Can't open workbook - unsupported file type: UNKNOWN")}；
 *       与"读上传流时断了"之类无法凭类型区分，凭 message 文字认又太脆</li>
 *   <li><b>合法 OLE2 容器但不是工作簿</b>（典型就是把一个 <b>.doc 改名成 .xlsx</b>）——
 *       实测抛 {@code IllegalArgumentException}，太泛</li>
 *   <li><b>截断/损坏的 .xls</b> —— 实测抛 {@code IndexOutOfBoundsException}，同样太泛</li>
 * </ul>
 * 以上三种在**解析那一层**都兜底成 {@link #UNREADABLE}（那里已经确定"就是这个文件读不了"，
 * 不存在"是不是服务端故障"的歧义），见 {@code AdminController#importStudents}。
 *
 * <h3>曾经有过、已被删掉的一支：{@code LEGACY_XLS}（"旧版 .xls 且损坏"）</h3>
 * 它认的是 {@code OLE2NotOfficeXmlFileException}，而**这个异常只有 {@code new XSSFWorkbook(...)}
 * 才会抛**。本仓所有读上传文件的入口现在都是 {@code WorkbookFactory.create}
 * （四个导入端点 + 管理员的模板读回），全仓已无 {@code new XSSFWorkbook(InputStream)} ⇒
 * 这一支**从产品代码里取不到**，纯属摆设，故连同它的常量一并删除（同批删掉的还有一条
 * 从未生效的 {@code java.util.zip.ZipException} 兜底）。要恢复它，先恢复一个 XSSF 读取点。
 */
public final class ExcelUploadErrors {

    /** 0 字节。注意多数端点在更早的 {@code file.isEmpty()} 处就会拦下，这里是兜底。 */
    public static final String EMPTY =
            "上传的文件是空的（0 字节），请重新选择文件";

    /** 有打开密码 / 加密。 */
    public static final String ENCRYPTED =
            "这个文件已加密或设了打开密码，请去掉密码后另存为 .xlsx 再上传";

    /** 认不出是什么文件。这是最常遇到的一种，文案要给出"下一步怎么办"。 */
    public static final String UNREADABLE =
            "无法识别这个文件，它可能不是 Excel、已经损坏，或者只是把别的文件改了后缀名。"
          + "请下载模板重新填写后再上传";

    private static final Set<String> ENCRYPTED_TYPES = Set.of(
            "org.apache.poi.EncryptedDocumentException");

    private static final Set<String> EMPTY_TYPES = Set.of(
            "org.apache.poi.EmptyFileException");

    private static final Set<String> UNREADABLE_TYPES = Set.of(
            "org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException",
            "org.apache.poi.UnsupportedFileFormatException",
            "org.apache.poi.ooxml.POIXMLException",
            "org.apache.poi.openxml4j.exceptions.InvalidFormatException",
            "org.apache.poi.poifs.filesystem.NotOLE2FileException");

    private ExcelUploadErrors() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 翻译成中文；**不是**"上传文件本身的问题"时返回 {@code null}。
     *
     * <p>返回 {@code null} 是刻意留的口子：这四个端点的 {@code catch (Exception e)} 包住的是
     * **整个方法体**（含逐行落库），所以落库时炸了也会进同一个 catch。那种情况下回一句
     * "文件损坏"就是**谎报原因**——调用方应改用中性文案（见四个端点的用法）。
     * 宁可说"服务器处理时出错"，也不要把服务端故障说成用户的文件有问题。
     *
     * @param e 解析上传文件时抛出的异常（可为 null）
     * @return 中文提示；不像文件问题时返回 {@code null}
     */
    public static String describe(Throwable e) {
        if (e == null) {
            return null;
        }
        if (hasType(e, ENCRYPTED_TYPES)) {
            return ENCRYPTED;
        }
        if (hasType(e, EMPTY_TYPES)) {
            return EMPTY;
        }
        if (hasType(e, UNREADABLE_TYPES)) {
            return UNREADABLE;
        }
        return null;
    }

    /**
     * 沿 cause 链找异常；链上每个异常**连同它的父类**都比对一遍全限定名。
     * 比对父类是为了覆盖 {@code OLE2NotOfficeXmlFileException extends NotOfficeXmlFileException} 这类层次，
     * 不必把每个子类都列进集合。
     */
    private static boolean hasType(Throwable e, Set<String> fqns) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                if (fqns.contains(c.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
