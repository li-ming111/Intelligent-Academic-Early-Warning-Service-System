package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 成绩申诉的只读计数（admin-service 用）。
 *
 * <p>2026-10-01 第十九轮新增：管理员首页那张「成绩申诉」待办卡原先是后端写死 0
 * （{@code AdminController.getPendingRequests} 里 {@code result.put("appeals", 0)}），
 * 而库里 {@code score_appeals} 真的有待处理行 ⇒ 页面显示 0、事实不是 0。
 *
 * <p>⚠️ 这里**故意不建实体**：{@code score_appeals} 有两个实体类，活的那个在
 * {@code student-service}（{@code com.academic.entity.ScoreAppeal}），
 * {@code academic-common} 里那个同名类已被标注为"死的、别用"。
 * 所以本接口只挂 {@code @Select} 直查，不引用任何实体 —— 与
 * {@code ClassesMapper.selectAllWithStudentCount()} 同一写法，避免让死类复活。
 */
@Mapper
public interface ScoreAppealMapper {

    /** 待处理申诉数（{@code status = 0}，与前端 Appeals.vue 的 pending 口径一致）。 */
    @Select("SELECT COUNT(*) FROM score_appeals WHERE status = 0")
    long countPending();
}
