package com.academic.common.util;

import com.academic.common.entity.College;
import com.academic.common.entity.Student;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「空更新体」判据（2026-10-03 W8）。
 *
 * <p>这些断言看着琐碎，但每一条都对应一个**会真的发生**的取值：MyBatis-Plus 的
 * {@code updateById} 默认 NOT_NULL，null 字段不进 SET 子句，全 null 就生成
 * {@code UPDATE tbl    WHERE id=?}（无 SET 子句 ⇒ 语法错 ⇒ 500）。
 * 判据错一个方向，要么拦住了正常的更新（误报 400），要么放行了空 body（继续 500）。
 */
class UpdateBodyGuardTest {

    @Test
    @DisplayName("只设了主键（客户端发 {}）⇒ 判为不可更新")
    void onlyIdSet_isNotUpdatable() {
        College college = new College();
        college.setId(5L);
        assertFalse(UpdateBodyGuard.hasUpdatableField(college),
                "只设 id 时 SET 子句会是空的，必须判为不可更新");
    }

    @Test
    @DisplayName("全 null 的新实体 ⇒ 判为不可更新")
    void brandNewEntity_isNotUpdatable() {
        assertFalse(UpdateBodyGuard.hasUpdatableField(new College()));
        assertFalse(UpdateBodyGuard.hasUpdatableField(new Student()));
    }

    @Test
    @DisplayName("有一个真列有值 ⇒ 判为可更新（负向对照：别把正常更新拦下来）")
    void oneMappedFieldSet_isUpdatable() {
        College college = new College();
        college.setId(5L);
        college.setName("计算机学院");
        assertTrue(UpdateBodyGuard.hasUpdatableField(college));

        // status 取 0 也是**有值** —— 不能把 0 / false 当成"没传"
        College zero = new College();
        zero.setStatus(0);
        assertTrue(UpdateBodyGuard.hasUpdatableField(zero), "status=0 是真值，不是 null");
    }

    @Test
    @DisplayName("@TableField(exist=false) 的字段不算数 —— 它根本不进 SQL")
    void existFalseFields_doNotCount() {
        College college = new College();
        college.setId(5L);
        // 这三个是 @TableField(exist = false)：前端详情页会带回来，但库里没有这些列
        college.setStudentCount(120);
        college.setTeacherCount(30);
        college.setCounselorCount(4);
        assertFalse(UpdateBodyGuard.hasUpdatableField(college),
                "只有 exist=false 的字段有值时，SET 子句依然是空的");

        Student student = new Student();
        student.setClassName("计科2201");   // exist = false
        assertFalse(UpdateBodyGuard.hasUpdatableField(student));
        student.setName("司明");            // 真列
        assertTrue(UpdateBodyGuard.hasUpdatableField(student));
    }

    @Test
    @DisplayName("null 实体 ⇒ 不可更新（别 NPE）")
    void nullEntity() {
        assertFalse(UpdateBodyGuard.hasUpdatableField(null));
    }

    @Test
    @DisplayName("父类上的字段也算（实体继承时别漏）")
    void inheritedFieldsCount() {
        Child child = new Child();
        child.setFromParent("x");
        assertTrue(UpdateBodyGuard.hasUpdatableField(child));

        Child idOnly = new Child();
        idOnly.setId(1L);
        assertFalse(UpdateBodyGuard.hasUpdatableField(idOnly));
    }

    @Test
    @DisplayName("updateStrategy=IGNORED 的字段即使为 null 也会进 SET ⇒ 判为可更新")
    void ignoredStrategyAlwaysEntersSetClause() {
        assertTrue(UpdateBodyGuard.hasUpdatableField(new WithIgnored()));
    }

    @Test
    @DisplayName("static / transient 字段不算数")
    void staticAndTransientIgnored() {
        assertFalse(UpdateBodyGuard.hasUpdatableField(new WithStaticAndTransient()));
    }

    // ── 测试用的小实体（不映射任何真表，只用来钉判据的结构规则）──────────────

    static class Parent {
        @TableId
        private Long id;
        private String fromParent;

        Long getId() {
            return id;
        }

        void setId(Long id) {
            this.id = id;
        }

        void setFromParent(String fromParent) {
            this.fromParent = fromParent;
        }
    }

    static class Child extends Parent {
        private String own;
    }

    static class WithIgnored {
        @TableId
        private Long id;
        @TableField(updateStrategy = FieldStrategy.IGNORED)
        private String note;
    }

    static class WithStaticAndTransient {
        @TableId
        private Long id;
        private static String SHARED = "有值";
        private transient String cached = "有值";
    }
}
