package com.academic.common.util;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 「更新请求体里到底有没有可更新的字段」的判据。
 *
 * <p><b>为什么需要它（2026-10-03 W8 实测）</b>：MyBatis-Plus 的 {@code updateById(entity)}
 * 默认走 {@link FieldStrategy#NOT_NULL} —— **值为 null 的字段不出现在 SET 子句里**。
 * 所以当请求体是个空对象 {@code {}}、或者字段名与实体对不上（Jackson 默认忽略未知 key，
 * 不报错）时，实体里除主键外全是 null，生成出来的 SQL 是：
 *
 * <pre>UPDATE student_profile    WHERE id=?</pre>
 *
 * <p>—— **没有 SET 子句**，MySQL 直接报语法错（{@code SQLSyntaxErrorException}）。
 * 实测两种下游形状：不接异常的控制器回 <b>HTTP 500</b>；接了 {@code catch (Exception)}
 * 的控制器回 <b>HTTP 200 + body code 500</b>，而且把上面那行 SQL 连同 MyBatis 内部
 * 信息一起写进 {@code message} 返回给客户端（既报错报得不对，又泄漏内部结构）。
 *
 * <p>客户端发了个空 body 是**客户端的错**，应当如实回 400，而不是 500/内部错误。
 *
 * <p><b>为什么不直接用 {@code TableInfoHelper.getTableInfo(...)} 拿 MyBatis-Plus 的元数据</b>
 * （曾考虑，未采用）：那份元数据要等 Mapper 注册完才有值，拿不到时只能返回 null，
 * 于是一个**请求路径上的守卫**会退化成"静默放行"—— 而"守卫其实没在守"正是本工程
 * 反复踩过的一类假象。这里改成读注解，规则少而稳定，且在没有 Spring/MyBatis 上下文的
 * 单元测试里行为完全一致。
 *
 * <p>与 MyBatis-Plus 默认口径的对应关系（漂移风险在此明处写清）：
 * <ul>
 *   <li>跳过 {@code static} / {@code transient} / {@code serialVersionUID}；</li>
 *   <li>跳过 {@code @TableId}（主键进的是 WHERE，不是 SET）；</li>
 *   <li>跳过 {@code @TableField(exist = false)}；</li>
 *   <li>{@code @TableField(updateStrategy = IGNORED)} 的字段即使为 null 也会进 SET
 *       ⇒ 直接判为"可更新"。</li>
 * </ul>
 *
 * <p>⚠️ 依赖一个当前成立的前提：<b>本工程零 {@code MetaObjectHandler}</b>，
 * 没有任何字段会被自动填充，所以"值为 null ⇒ 不进 SET 子句"处处成立。
 * 将来若新增了自动填充（{@code @TableField(fill = ...)}），这里要跟着放宽。
 */
public final class UpdateBodyGuard {

    private UpdateBodyGuard() {
    }

    /**
     * 该实体的 {@code updateById} 是否会生成**非空**的 SET 子句。
     *
     * @return {@code true} 表示能更新（至少有一个非 null 的普通字段，或有字段声明为
     *         {@link FieldStrategy#IGNORED}）；{@code false} 表示 SET 子句会是空的，
     *         调用方应当回 400，而不是把请求递给数据库。
     */
    public static boolean hasUpdatableField(Object entity) {
        if (entity == null) {
            return false;
        }
        for (Class<?> clazz = entity.getClass(); clazz != null && clazz != Object.class;
                clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!isMappedUpdateField(field)) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && tableField.updateStrategy() == FieldStrategy.IGNORED) {
                    // 无论是不是 null 都会写进 SET，SET 子句因此不空。
                    return true;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(entity) != null) {
                        return true;
                    }
                } catch (IllegalAccessException | RuntimeException e) {
                    // 反射读不出来时**放行**：宁可让原来的逻辑去跑（可能报 SQL 错），
                    // 也不要因为判据本身失灵而把正常请求误拦成 400。
                    return true;
                }
            }
        }
        return false;
    }

    /** 这个字段会不会出现在 {@code updateById} 生成的 SET 子句里（只看结构，不看值）。 */
    private static boolean isMappedUpdateField(Field field) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
            return false;
        }
        if (field.getAnnotation(TableId.class) != null) {
            return false;
        }
        TableField tableField = field.getAnnotation(TableField.class);
        return tableField == null || tableField.exist();
    }
}
