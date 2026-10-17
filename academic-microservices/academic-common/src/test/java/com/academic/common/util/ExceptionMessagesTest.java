package com.academic.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W45：控制器宽 catch 回给客户端的文案要「业务异常如实、框架异常洗白」。
 * 这里把判据钉死 —— 每一次断言都对应一条真实形状。
 */
class ExceptionMessagesTest {

    @Test
    @DisplayName("业务异常 IllegalArgumentException / 裸 RuntimeException ⇒ 原文如实回（它们本来就是人话）")
    void businessMessagePassesThrough() {
        assertEquals("进度必须在 0~100 之间: 120",
                ExceptionMessages.describe(new IllegalArgumentException("进度必须在 0~100 之间: 120")));
        assertEquals("无法识别的帮扶计划状态: 9",
                ExceptionMessages.describe(new IllegalArgumentException("无法识别的帮扶计划状态: 9")));
        // 本仓用「裸 RuntimeException / IllegalStateException（精确类、无 cause）」承载业务文案
        assertEquals("模板不存在", ExceptionMessages.describe(new RuntimeException("模板不存在")));
        assertEquals("旧密码错误", ExceptionMessages.describe(new RuntimeException("旧密码错误")));
        assertEquals("用户服务暂时不可用，请稍后重试",
                ExceptionMessages.describe(new IllegalStateException("用户服务暂时不可用，请稍后重试")));
    }

    @Test
    @DisplayName("本工程自己的异常（com.academic 包）⇒ 原文如实回")
    void ownExceptionMessagesPassThrough() {
        assertEquals("模板不存在", ExceptionMessages.describe(new OwnBusinessException("模板不存在")));
        assertEquals("任务不存在", ExceptionMessages.describe(new OwnNotFound("任务不存在")));
    }

    @Test
    @DisplayName("NumberFormatException 是 IllegalArgumentException 的子类 ⇒ 也如实回（它是可操作的输入错）")
    void numberFormatIsBusiness() {
        assertEquals("For input string: \"abc\"",
                ExceptionMessages.describe(new NumberFormatException("For input string: \"abc\"")));
    }

    @Test
    @DisplayName("数据库异常 ⇒ 洗成中性文案，SQL 原文一个字都不露")
    void sqlNeverLeaks() {
        String raw = "### Error updating database.  Cause: java.sql.SQLIntegrityConstraintViolationException: "
                + "Cannot add or update a child row: a foreign key constraint fails "
                + "(`academic_warning_system`.`student_profile`, CONSTRAINT `fk_student_major` ...)";
        String out = ExceptionMessages.describe(new org.springframework.dao.DataIntegrityViolationException(raw));
        assertEquals(ExceptionMessages.GENERIC, out);
        assertFalse(out.contains("SQL"), "不能出现 SQL");
        assertFalse(out.contains("student_profile"), "不能出现表名");
        assertFalse(out.contains("fk_student_major"), "不能出现约束名");
    }

    @Test
    @DisplayName("JDK 异常 / 带 cause 的包装异常 ⇒ 洗白（NPE 的 message 常常是内部表达式）")
    void jdkAndWrappedAreScrubbed() {
        // NullPointerException 是 RuntimeException 的子类，但不是精确类 ⇒ 不算业务
        assertEquals(ExceptionMessages.GENERIC, ExceptionMessages.describe(new NullPointerException()));
        assertEquals(ExceptionMessages.GENERIC,
                ExceptionMessages.describe(new SQLException("Unknown column 'phone' in 'field list'")));
        // 带 cause 的 RuntimeException：说明异常是「被包起来的」，message 是拼出来的
        assertEquals(ExceptionMessages.GENERIC,
                ExceptionMessages.describe(new RuntimeException("db down", new SQLException("boom"))));
    }

    @Test
    @DisplayName("整条 cause 链都要干净：包着 SQL 的裸 RuntimeException ⇒ 也要洗白")
    void wrappedFrameworkExceptionIsScrubbed() {
        Throwable wrapped = new IllegalStateException(
                "删除备份失败: ### Error updating database. Cause: java.sql.SQLException: ...",
                new org.springframework.dao.DataAccessResourceFailureException("Connection refused"));
        assertEquals(ExceptionMessages.GENERIC, ExceptionMessages.describe(wrapped));
        assertFalse(ExceptionMessages.isBusiness(wrapped));

        // 本仓真实形状：BackupService 把 DB 异常包成 IllegalStateException 再抛
        Throwable backup = new IllegalStateException(
                "创建临时库 backup_scratch 失败: ### Error updating database.  Cause: java.sql.SQLException: ...",
                new org.springframework.jdbc.UncategorizedSQLException("...", "...", new SQLException("access denied")));
        assertEquals(ExceptionMessages.GENERIC, ExceptionMessages.describe(backup));
        assertFalse(ExceptionMessages.isBusiness(backup));
    }

    @Test
    @DisplayName("message 为 null / 空白 ⇒ 回中性文案（别回一个空串给页面）")
    void blankMessageFallsBackToGeneric() {
        assertEquals(ExceptionMessages.GENERIC, ExceptionMessages.describe(new IllegalArgumentException()));
        assertEquals(ExceptionMessages.GENERIC, ExceptionMessages.describe(new IllegalArgumentException("   ")));
        assertEquals(ExceptionMessages.GENERIC, ExceptionMessages.describe(null));
    }

    @Test
    @DisplayName("isBusiness 对自引用 cause 不死循环（防御）：锚点自身是本工程异常 ⇒ 终止并判为业务")
    void selfReferencingCauseTerminates() {
        // ⚠️ 这个匿名类定义在本文件里 ⇒ 运行期包名是 com.academic.common.util
        // ⇒ 按"包名以 com.academic 开头"的规则它**就是本工程异常**。
        // 没有 t.getCause()==t 那道闸，下面这行会死循环。
        RuntimeException selfRef = new RuntimeException("x") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertTrue(ExceptionMessages.isBusiness(selfRef));
        assertEquals("x", ExceptionMessages.describe(selfRef));
    }

    // —— 测试替身：本工程自己的异常 ——
    static class OwnBusinessException extends RuntimeException {
        OwnBusinessException(String m) {
            super(m);
        }
    }

    static class OwnNotFound extends RuntimeException {
        OwnNotFound(String m) {
            super(m);
        }
    }
}
