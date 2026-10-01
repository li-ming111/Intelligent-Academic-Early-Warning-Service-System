package com.academic.mapper;

import com.academic.common.entity.Classes;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 班级表 {@code classes} 的只读 Mapper。
 *
 * <p>无需 {@code @Mapper}：{@code CollegeServiceApplication} 上有
 * {@code @MapperScan("com.academic.mapper")}，本包内的接口自动注册。
 *
 * <p>本服务只做"班级目录"这一件事（给登录前的注册页选班级用），写班级是
 * admin-service / counselor-service 的事，不在这里开写入口。
 */
public interface ClassMapper extends BaseMapper<Classes> {
}
