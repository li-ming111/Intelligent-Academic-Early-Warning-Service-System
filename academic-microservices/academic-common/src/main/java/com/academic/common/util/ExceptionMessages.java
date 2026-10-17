package com.academic.common.util;

/**
 * 把异常的「客户可见文案」与「服务端日志」分开。
 *
 * <p><b>背景（W45）</b>：全仓控制器普遍写成
 * <pre>{@code
 * } catch (Exception e) {
 *     log.error("xx失败: {}", e.getMessage(), e);
 *     return ApiResponse.error("xx失败: " + e.getMessage());   // ← 这里
 * }}</pre>
 * 于是 {@code e} 若是数据库/框架异常，用户拿到的就是 MyBatis / JDBC 的<b>英文原文</b>，
 * 例如
 * <pre>### Error updating database.  Cause: java.sql.SQLIntegrityConstraintViolationException:
 * Cannot add or update a child row: a foreign key constraint fails
 * (`academic_warning_system`.`student_profile`, CONSTRAINT `fk_student_major` ...)</pre>
 * 前端 {@code client.js} 会把 {@code message} 直接弹给用户（辅导员/学生/教师都能看见），
 * 既是<b>信息泄露</b>，也把内部表名、约束名、列名暴露出去。
 *
 * <p>更微妙的是：好几个模块自己的 {@code GlobalExceptionHandler} 里明明有
 * {@code @ExceptionHandler(DataAccessException.class)}「统一回『数据库操作失败』」——
 * 但控制器这层 {@code catch (Exception e)} 抢在 advice 之前把异常接住了，
 * 那条分支<b>永远不会被调用</b>（死代码）。
 *
 * <p><b>判据</b>：
 * <ul>
 *   <li><b>业务异常</b>—— message 是给用户看的人话，<b>如实回</b>。判定为下列之一：
 *       <ul>
 *         <li>{@code IllegalArgumentException} 及其子类（含 {@code NumberFormatException}）；</li>
 *         <li>{@link com.academic} 包下的任何异常（本工程自定义类型）；</li>
 *         <li><b>裸</b> {@code java.lang.RuntimeException} / {@code IllegalStateException}
 *             ——即 {@code throw new RuntimeException("用户不存在")} 这种<b>精确类、无 cause</b> 的写法。
 *             本仓大量用它来抛业务文案（{@code 「旧密码错误」}、{@code 「模板不存在」}…），
 *             洗掉会让用户看到一句无用的「服务器出错」。<b>精确类</b>很关键：
 *             {@code NullPointerException} 等 JDK 子类<b>不算</b>。</li>
 *       </ul></li>
 *   <li><b>其它一律</b>（{@code DataAccessException} / {@code SQLException} /
 *       {@code NullPointerException} / 第三方异常……）——回统一的中性文案，<b>原文只进服务端日志</b>。</li>
 * </ul>
 *
 * <p>⚠️ <b>整条 cause 链都要干净</b>才算业务异常：只要链上挂着任何框架/JDK 异常，
 * 就可能是「把 {@code DataAccessException} 包一层再抛」（本仓 {@code BackupService} 就是这么写的：
 * {@code throw new IllegalStateException("删除备份失败: " + e.getMessage(), e)}），
 * 那种 message 里裹着 SQL —— 一律按服务端故障处理。
 * 判据正是「带 cause 的 {@code IllegalStateException} 不算业务、裸的才算」。
 *
 * <p>用法：
 * <pre>{@code
 * } catch (Exception e) {
 *     log.error("xx失败: {}", e.getMessage(), e);          // 排查细节留在服务端
 *     return ApiResponse.error("xx失败: " + ExceptionMessages.describe(e));  // 给用户看的话
 * }}</pre>
 * 注意 {@code describe} 只该包住 {@code e.getMessage()}，不要把前缀也塞进去 ——
 * 前缀是业务上下文，本来就该给用户看。
 */
public final class ExceptionMessages {

    /** 非业务异常统一回这句话；排查细节在服务端日志里（调用方已 log.error("...", e)）。 */
    public static final String GENERIC = "服务器处理请求时出错，请稍后重试";

    private ExceptionMessages() {
    }

    /** 供用户看的文案：业务异常如实回其 message，其余回 {@link #GENERIC}。 */
    public static String describe(Throwable e) {
        if (e == null) {
            return GENERIC;
        }
        if (!isBusiness(e)) {
            return GENERIC;
        }
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? GENERIC : m;
    }

    /**
     * 是否「本工程自己抛的业务异常」。
     *
     * <p>整条 cause 链上每一环都必须是下列之一，才算业务异常：
     * <ul>
     *   <li>{@code IllegalArgumentException} 及其子类；</li>
     *   <li>{@code com.academic} 包下的类型；</li>
     *   <li><b>精确</b> {@code java.lang.RuntimeException} / {@code IllegalStateException}
     *       且<b>没有 cause</b>（{@code throw new RuntimeException("用户不存在")} 这种业务文案写法）。</li>
     * </ul>
     */
    public static boolean isBusiness(Throwable e) {
        if (e == null) {
            return false;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (!isOwnException(t)) {
                return false;
            }
            if (t.getCause() == t) {
                break; // 防御自引用，避免死循环
            }
        }
        return true;
    }

    private static boolean isOwnException(Throwable t) {
        if (t instanceof IllegalArgumentException) {
            // 含 NumberFormatException 等子类：message 是可操作的输入错
            return true;
        }
        Class<?> c = t.getClass();
        if (c == RuntimeException.class || c == IllegalStateException.class) {
            // 精确类、且是"裸抛"（没有 cause）⇒ 本仓用它承载业务文案。
            // 带 cause 的（如 BackupService 那种把 DB 异常包一层）交给下面按包名判定。
            return t.getCause() == null;
        }
        Package p = c.getPackage();
        return p != null && p.getName().startsWith("com.academic");
    }
}
