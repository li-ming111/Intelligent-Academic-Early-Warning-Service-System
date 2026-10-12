package com.academic.service;

import com.academic.common.entity.AcademicWarning;
import com.academic.common.entity.AcademicWarningCourse;
import com.academic.common.constant.ScoreCaliber;
import com.academic.mapper.AcademicWarningCourseMapper;
import com.academic.mapper.AcademicWarningMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 落库一条预警及其「挂科课程明细」——<b>必须是单独一个 bean</b>。
 *
 * <p><b>为什么单独抽一个类，而不是在 {@code WarningScheduledTask} 里给
 * {@code createWarning} 加 {@code @Transactional}：</b>{@code createWarning} 是该类的
 * <b>私有方法</b>，由同类方法直接调用 —— 自调用不经过 Spring 代理，注解形同虚设，
 * 那就是个<b>假事务</b>（本仓已有的教训，见"无事务的多表写路径"一节）。
 * 抽成 bean 由外部注入调用，代理才生效。
 *
 * <p><b>为什么需要事务：</b>2026-10-12（W39）之前，"生成一条预警"是<b>单语句</b>写入，
 * 谈不上原子性。加了 {@code academic_warning_course} 之后变成写两张表
 * （父行 + 每门挂科课一行），中途失败会留下"预警在、课程明细缺失"的半截数据 ——
 * 而教师端正是按课程明细判断"这条预警涉不涉及我教的课"，半截数据会让它得出错答案。
 *
 * <p>⚠️ 这个类只负责"写"，不做判重、不挑规则、不发通知 —— 那些留在
 * {@code WarningScheduledTask} 里，免得把扫描逻辑拖进事务里跑长时间持锁。
 */
@Slf4j
@Service
public class WarningWriter {

    private final AcademicWarningMapper academicWarningMapper;
    private final AcademicWarningCourseMapper academicWarningCourseMapper;

    public WarningWriter(AcademicWarningMapper academicWarningMapper,
                         AcademicWarningCourseMapper academicWarningCourseMapper) {
        this.academicWarningMapper = academicWarningMapper;
        this.academicWarningCourseMapper = academicWarningCourseMapper;
    }

    /**
     * 插父行（拿到自增 id）后，把 {@code detail} 里<b>每一门</b>挂科课各写一行明细。
     *
     * <p>{@code detail} 就是 {@code ScoreCaliber.FAIL_DETAIL_BY_STUDENT} 的返回：
     * 每行带 {@code courseId / courseName / scoreTotal / semester}。此前这个方法
     * （原来内联在 {@code createWarning} 里）只读了 {@code scoreTotal} 和
     * {@code courseName} 去拼正文，把 {@code courseId} 扔了 —— 那正是"预警与课程
     * 对应不上"的根。
     *
     * <p>空 {@code detail} 是合法输入（例如历史迁移来的预警），此时只插父行、不写明细。
     *
     * @param warning 已填好各字段、但尚未落库的预警（id 由插入后回填）
     * @param detail  该生的挂科课程明细，按分数升序
     */
    @Transactional
    public void insertWarningWithCourses(AcademicWarning warning, List<Map<String, Object>> detail) {
        academicWarningMapper.insert(warning);

        if (detail == null || detail.isEmpty()) {
            return;
        }
        for (Map<String, Object> course : detail) {
            Long courseId = toLong(course.get("courseId"));
            if (courseId == null) {
                // course_id 是 NOT NULL。宁可跳过并留痕，也不要插一行挂不上课的明细 ——
                // 那会让"这门课算谁的"凭空多出一种答案。
                log.warn("预警 {} 的挂科明细缺 courseId，已跳过该门：{}", warning.getId(), course);
                continue;
            }
            Double score = ScoreCaliber.toDouble(course.get("scoreTotal"));
            Object courseName = course.get("courseName");
            academicWarningCourseMapper.upsert(new AcademicWarningCourse()
                    .setWarningId(warning.getId())
                    .setCourseId(courseId)
                    .setCourseName(courseName == null ? null : String.valueOf(courseName))
                    .setScoreTotal(score == null ? null : BigDecimal.valueOf(score))
                    .setSemester(toInt(course.get("semester"))));
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
