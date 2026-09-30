package com.academic.mapper;

import com.academic.common.entity.UserSettings;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号级自助设置 {@code user_settings}（键 {@code users.id}）。
 *
 * <p>这张表在 user-service 里有一份完整实现（{@code /users/{uid}/settings}），
 * 那边是**规范入口**。学生端在这边再开一份，是为了让
 * {@code /students/settings/{id}} 这个别名不再返回编造的值
 * —— 两份读写的是**同一张表、同一套键**，实体 {@link UserSettings} 也是
 * academic-common 里共享的那一个，所以不存在"两个设置真相"。
 *
 * <p>学生的 {@code privacy_level} 不在这张表里，它挂在 {@code student_profile}
 * （键 {@code student_profile.id}），由 {@code updatePrivacyLevel} 单独维护。
 *
 * <p>注意 {@code student_settings} 是另一张**没人读也没人写**的表
 * （种子数据 3 行、{@code com.academic.entity.StudentSettings} 实体存在但无 mapper），
 * 别看名字更贴切就去用它 —— 用了它才真的会造出第二个设置真相。
 */
@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettings> {
}
