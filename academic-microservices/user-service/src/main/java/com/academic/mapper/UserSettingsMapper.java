package com.academic.mapper;

import com.academic.common.entity.UserSettings;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 账号级自助设置 Mapper。
 *
 * <p>无需 {@code @Mapper}：{@code UserServiceApplication} 上有
 * {@code @MapperScan("com.academic.mapper")}，本包内的接口自动注册。
 */
public interface UserSettingsMapper extends BaseMapper<UserSettings> {
}
