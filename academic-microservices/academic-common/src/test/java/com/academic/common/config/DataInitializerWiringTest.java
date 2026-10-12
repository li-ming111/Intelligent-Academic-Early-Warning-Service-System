package com.academic.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「建库 + 种子数据 + 结构自检」这个组件**真的会被 Spring 创建**吗（W39，2026-10-12）。
 *
 * <p><b>为什么需要这一条</b>：{@link DataInitializer} 从 2026-07-17 起被加上
 * {@code @ConditionalOnBean(DataSource.class)}，而 {@code @ConditionalOnBean} 只在
 * <b>用户自己声明</b>的 bean 上可靠 —— 不写 {@code @Bean DataSource} 时它是竞态条件，
 * 评估时自动配置的数据源常常还没注册，于是条件判假、bean 被**静默跳过**。
 *
 * <p>这个故障形状本仓最痛恨：**不报错、不打日志、功能整体不发**。
 * 实测（直连 {@code java -cp} 起实例 + 在 {@code @Bean} 方法体首行插探针）：
 * 带条件时探针 0 次、去掉条件后 2 次（bean created + runner invoked），
 * 且当日志出现 {@code [DataInit] 结构自检通过：14 张关键表列数均与基线一致。}
 *
 * <p>所以这里钉的是**接线**而不是逻辑：本仓零 {@code @SpringBootTest}，起不了容器，
 * 就用注解反射断言"没有任何条件注解/条件 bean 方法会把这件事挡掉"。
 * 这是纯静态判据，但它面对的是一个**静态的、已经在日志里留下过证据的**错误。
 */
class DataInitializerWiringTest {

    private static final Class<DataInitializer> TYPE = DataInitializer.class;

    @Test
    @DisplayName("★ 类上不带任何条件注解 —— 带了就可能整个初始化+自检静默不跑")
    void classCarriesNoConditionalAnnotations() {
        for (java.lang.annotation.Annotation a : TYPE.getAnnotations()) {
            String name = a.annotationType().getName();
            assertFalse(name.startsWith("org.springframework.boot.autoconfigure.condition."),
                    "DataInitializer 类上出现了条件注解 " + name
                            + "：这正是 2026-07-17 那次事故的形状（@ConditionalOnBean(DataSource.class) "
                            + "让建库+种子+结构自检一次都没跑），别再加回来");
        }
    }

    @Test
    @DisplayName("★ initDatabase 上不带 @ConditionalOnBean —— 它读的是自动配置的 DataSource，条件不可靠")
    void initDatabaseIsUnconditional() {
        Method bean = beanMethod();
        assertNotNull(bean, "initDatabase(...) 找不到 —— 组件被改名/删除了？");
        assertNull(bean.getAnnotation(ConditionalOnBean.class),
                "initDatabase 上又有 @ConditionalOnBean 了：@ConditionalOnBean 只看**用户自己声明**的 bean，"
                        + "而这里的 DataSource 来自自动配置 ⇒ 条件判假 ⇒ 组件静默跳过（不报错、不打日志）");
    }

    @Test
    @DisplayName("方法上必须有 @Bean，且返回 CommandLineRunner、只收一个 DataSource 参数")
    void initDatabaseHasTheRightShape() {
        Method bean = beanMethod();
        assertNotNull(bean, "initDatabase(...) 找不到");
        assertTrue(Modifier.isPublic(bean.getModifiers()), "@Bean 方法必须 public");
        assertNotNull(bean.getAnnotation(Bean.class), "initDatabase 丢了 @Bean 就不会被注册");
        assertEquals(CommandLineRunner.class, bean.getReturnType(),
                "返回类型必须是 CommandLineRunner —— 这才是让它'启动时真的跑'的那一环");
        assertArrayEquals(new Class<?>[]{DataSource.class}, bean.getParameterTypes(),
                "参数只该是一个 DataSource（由 Spring 注入）");
    }

    /** 唯一那个带 {@code @Bean} 的公开方法；按类型找而不是按名字，改名了也能给出清楚的失败信息。 */
    private static Method beanMethod() {
        return Arrays.stream(TYPE.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("★ 基线里不得再有 score_record —— 表已删，留着就是每个服务每次启动一条假警报")
    void expectedColumnsHasNoScoreRecord() throws Exception {
        java.lang.reflect.Field f = TYPE.getDeclaredField("EXPECTED_COLUMNS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> map = (java.util.Map<String, Integer>) f.get(null);

        assertFalse(map.containsKey("score_record"),
                "EXPECTED_COLUMNS 里又有 score_record 了：这张表已于 2026-10-12（W39）随 "
                        + "db_backups/score_record_drop_20261012_apply.sql 删除，基线留着会让 "
                        + "verifySchema 每个服务每次启动都报 `score_record(表不存在)` 的 ERROR —— "
                        + "一声永远修不掉的假警报，会把真警报一起淹掉");
    }

    @Test
    @DisplayName("★ 基线里必须还有 scores —— 权威成绩表被误删是另一种事故，得钉住")
    void expectedColumnsStillHasScores() throws Exception {
        java.lang.reflect.Field f = TYPE.getDeclaredField("EXPECTED_COLUMNS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> map = (java.util.Map<String, Integer>) f.get(null);

        assertTrue(map.containsKey("scores"),
                "EXPECTED_COLUMNS 里没有 scores 了：它是全系统的权威成绩表，"
                        + "W36/W38 之后所有成绩读端都指向它，基线里必须有它");
    }
}
