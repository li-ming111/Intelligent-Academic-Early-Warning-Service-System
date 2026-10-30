package com.academic.mapper;

import com.academic.common.entity.CounselorProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface CounselorProfileMapper extends BaseMapper<CounselorProfile> {
    
    @Select("SELECT COUNT(*) FROM counselor_profiles WHERE college_id = #{collegeId}")
    long countByCollegeId(Long collegeId);

    /**
     * 辅导员账号在业务表里还剩多少引用 —— 删除账号前的预检。
     *
     * <p>{@code users} 那几道外键由 {@code UserMapper.countReferences} 负责；这里补的是
     * 按 {@code counselor_id}（{@code users.id}，不是档案表 id）挂着的班级与帮扶数据。
     * 这些列没有外键，删账号不会报错，但会留下指向不存在用户的孤儿行。
     */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM classes WHERE counselor_id = #{userId}) AS classes, "
            + "(SELECT COUNT(*) FROM counselor_class_application WHERE counselor_id = #{userId}) AS classApplications, "
            + "(SELECT COUNT(*) FROM counselor_class_management WHERE counselor_id = #{userId}) AS classManagement, "
            + "(SELECT COUNT(*) FROM assistance_plans WHERE counselor_id = #{userId}) AS assistancePlans, "
            + "(SELECT COUNT(*) FROM assistance_evaluations WHERE counselor_id = #{userId}) AS assistanceEvaluations, "
            + "(SELECT COUNT(*) FROM communication_logs WHERE counselor_id = #{userId}) AS communicationLogs")
    Map<String, Object> countReferences(@Param("userId") Long userId);
}