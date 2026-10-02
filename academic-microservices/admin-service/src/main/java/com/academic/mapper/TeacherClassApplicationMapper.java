package com.academic.mapper;

import com.academic.entity.TeacherClassApplication;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教师班级申请表的 mapper。
 *
 * <p>2026-10-02（W1.14）：原先这里还有四个自定义方法
 * （{@code getAllApplications} / {@code getApplicationsByStatus} /
 * {@code updateStatus} / {@code updateStatusWithReason}），
 * **唯一调用方**是同包下那个零调用方的
 * {@code service/impl/TeacherClassApplicationServiceImpl} —— 那个 impl 连同它的接口
 * 已一并删除（`AdminController` 全程只用 {@link BaseMapper} 的
 * {@code selectList/selectById/insert/updateById/deleteById/selectCount}）。
 * 留着这四条 `@Update`/`@Select` 等于在仓库里挂一份"另一套写库路径"，
 * 谁复制谁踩（本工程已有先例：[[abs-broken-endpoints-and-silent-drops]] 里的
 * `student_settings` 诱饵表就是"零读零写却看着像真源"）。
 */
@Mapper
public interface TeacherClassApplicationMapper extends BaseMapper<TeacherClassApplication> {
}
