package com.academic.service.impl;

import com.academic.common.entity.AssistancePlan;
import com.academic.mapper.AssistancePlanMapper;
import com.academic.service.AssistancePlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帮扶计划服务实现类
 */
@Service
public class AssistancePlanServiceImpl extends ServiceImpl<AssistancePlanMapper, AssistancePlan> implements AssistancePlanService {

    @Override
    public List<AssistancePlan> getPlansByStudentId(Long studentId) {
        LambdaQueryWrapper<AssistancePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssistancePlan::getStudentId, studentId);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public boolean createPlan(AssistancePlan plan) {
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setStatus(0);
        plan.setProgress(0);
        return save(plan);
    }

    @Override
    public boolean updatePlan(AssistancePlan plan) {
        plan.setUpdatedAt(LocalDateTime.now());
        return updateById(plan);
    }

    @Override
    public boolean deletePlan(Long id) {
        return removeById(id);
    }

    /** 状态取值：0 待开始 / 1 进行中 / 2 已完成。 */
    @Override
    public boolean updatePlanStatus(Long id, String status) {
        AssistancePlan plan = getById(id);
        if (plan == null) {
            return false;
        }
        Integer normalized = parseStatus(status);
        if (normalized == null) {
            throw new IllegalArgumentException("无法识别的帮扶计划状态: " + status);
        }
        plan.setStatus(normalized);
        plan.setUpdatedAt(LocalDateTime.now());
        return updateById(plan);
    }

    /**
     * 把前端的各种状态写法归一成 0/1/2。
     *
     * <p>原实现直接 {@code Integer.parseInt(status)}，而辅导员端传的是
     * {@code "in_progress"} / {@code "completed"} 这类文本，会抛
     * {@code NumberFormatException} 变成 500；学生端则把进度百分比当状态传进来，
     * 于是 {@code status} 被写成 50 这种越界值。两种输入在这里都被正确区分开。
     */
    private Integer parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        String s = status.trim();
        switch (s.toLowerCase()) {
            case "pending": case "not_started": case "待开始": case "未开始": case "待处理":
                return 0;
            case "in_progress": case "progressing": case "进行中": case "处理中":
                return 1;
            case "completed": case "done": case "finished": case "已完成": case "已处理":
                return 2;
            default:
                try {
                    int value = Integer.parseInt(s);
                    // 只接受合法状态值。百分比（0-100）不应落到 status 上
                    return value >= 0 && value <= 2 ? value : null;
                } catch (NumberFormatException e) {
                    return null;
                }
        }
    }

    @Override
    public boolean updatePlanProgress(Long id, java.math.BigDecimal progressPercentage) {
        AssistancePlan plan = getById(id);
        if (plan == null) {
            return false;
        }
        if (progressPercentage == null) {
            throw new IllegalArgumentException("缺少进度值");
        }
        int percent = progressPercentage.intValue();
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("进度必须在 0~100 之间: " + percent);
        }
        plan.setProgress(percent);
        // 进度与状态保持一致：0 未开始、100 已完成，中间为进行中。
        // 否则会出现"进度 100% 但状态仍是进行中"这种自相矛盾的组合。
        plan.setStatus(percent == 0 ? 0 : (percent >= 100 ? 2 : 1));
        plan.setUpdatedAt(LocalDateTime.now());
        return updateById(plan);
    }
}
