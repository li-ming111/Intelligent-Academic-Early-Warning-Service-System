package com.academic.mapper;

import com.academic.common.entity.AcademicWarning;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 预警 Mapper。
 *
 * <p>除 MyBatis-Plus 的通用 CRUD 外，这里挂三条定时任务专用的语句：
 * 取收件人（发通知用）、取待处理预警（自动解除用）、置为已解除。
 */
@Mapper
public interface AcademicWarningMapper extends BaseMapper<AcademicWarning> {

    /**
     * 一条预警的两名收件人。
     *
     * <p>列：{@code profileId}（= 入参，student_profile.id）、{@code userId}
     * （学生本人的 {@code users.id}）、{@code studentName}、{@code counselorId}
     * （其班级辅导员的 {@code users.id}，可能为 NULL —— 班级没配辅导员时）。
     *
     * <p>⚠️ 两个 id 空间必须分清：{@code academic_warning.student_id} 存的是
     * {@code student_profile.id}，而 {@code notifications.user_id} 要的是
     * {@code users.id}。这里一次把两个都取出来，调用方不必再查第二遍。
     *
     * <p>辅导员来自 {@code student_profile.class_id → classes.counselor_id}，
     * 与 {@code classes} 表的定义一致（{@code counselor_id} 本身就是 {@code users.id}）。
     */
    @Select("SELECT sp.id AS profileId, sp.user_id AS userId, sp.name AS studentName, "
          + "       cl.counselor_id AS counselorId "
          + "  FROM student_profile sp "
          + "  LEFT JOIN classes cl ON cl.id = sp.class_id "
          + " WHERE sp.id = #{profileId}")
    Map<String, Object> selectWarningRecipients(@Param("profileId") Long profileId);

    /**
     * 所有待处理预警的关键字段，供自动解除判定。
     *
     * <p>列：{@code id} / {@code studentId}（student_profile.id）/ {@code ruleId} /
     * {@code failCount}（生成时的门数，可能为 NULL）/ {@code title} / {@code warningLevel}。
     * 后两列只用于解除通知的文案，判定本身不看它们。
     */
    @Select("SELECT id, student_id AS studentId, rule_id AS ruleId, fail_count AS failCount, "
          + "       title, warning_level AS warningLevel "
          + "  FROM academic_warning WHERE status = 0")
    List<Map<String, Object>> selectPendingWarnings();

    /**
     * 把一条待处理预警置为「已解除」（{@code status = 2}）。
     *
     * <p>{@code status = 2} 不是新发明的口径：PC 辅导员页
     * {@code CounselorWarnings.vue} 早就在渲染 {@code status === 2 ? '已解除' : ...}，
     * 只是此前**没有任何代码写得出 2**。
     *
     * <p>WHERE 带 {@code status = 0}：并发下（定时任务与手动扫描同时跑）只有一个能改到，
     * 影响行数为 0 时调用方会跳过写解除记录，不会留下"记录有、状态没变"的脏数据。
     *
     * <p>{@code updated_at} 必须显式写：本工程没有 {@code MetaObjectHandler}，
     * 列上的 DB 默认值只在 INSERT 生效，UPDATE 不写就永远停在旧值。
     */
    @Update("UPDATE academic_warning SET status = 2, handle_result = #{result}, "
          + "       handled_by = #{handledBy}, handled_at = NOW(), updated_at = NOW() "
          + " WHERE id = #{id} AND status = 0")
    int releaseWarning(@Param("id") Long id,
                       @Param("result") String result,
                       @Param("handledBy") String handledBy);
}
