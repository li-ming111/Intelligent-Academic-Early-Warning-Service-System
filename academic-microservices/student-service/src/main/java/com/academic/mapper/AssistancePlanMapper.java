package com.academic.mapper;

import com.academic.common.entity.AssistancePlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帮扶计划只读访问。
 *
 * <p>学生端此前不读这张表：{@code getAssistancePlans} 拿"预警条数"当计划数，
 * 每条计划的标题由预警等级拼出来、状态写死"进行中"、进度写死 50。
 * 结果是学生看到的帮扶计划列表与实际下发的计划毫无关系。
 *
 * <p>写入仍归 counselor-service（计划由辅导员创建/维护），这里只查询。
 */
@Mapper
public interface AssistancePlanMapper extends BaseMapper<AssistancePlan> {

    /**
     * 某学生的全部帮扶计划，新的在前。
     * 参数是 {@code student_profile.id} —— {@code assistance_plans.student_id} 存的就是它。
     */
    @Select("SELECT * FROM assistance_plans WHERE student_id = #{studentId} "
          + "ORDER BY updated_at DESC, id DESC")
    List<AssistancePlan> selectByStudentId(@Param("studentId") Long profileId);
}
