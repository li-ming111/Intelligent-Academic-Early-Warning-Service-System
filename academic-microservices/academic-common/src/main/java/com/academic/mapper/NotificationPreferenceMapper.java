package com.academic.mapper;

import com.academic.common.notify.NotificationPreferences;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 通知偏好只读 Mapper（放在 academic-common，供所有发通知的服务共用）。
 *
 * <p><b>为什么放在这里而不是各服务各写一份</b>：这个包
 * （{@code com.academic.mapper}）已被 12 个服务的
 * {@code @MapperScan("com.academic.mapper")} 覆盖，把接口放在公共 jar 里
 * 各服务无需任何配置就能注入 —— 而偏好判定规则只要有两份实现就一定会漂移。
 *
 * <p>SQL 与判定逻辑分别是 {@link NotificationPreferences#SQL} 与
 * {@link NotificationPreferences#allows}，本接口只负责取数，不做判断。
 *
 * <p>参数：{@code profileId} 是 {@code student_profile.id}（非学生收件人传 {@code null}），
 * {@code userId} 是 {@code users.id}。两个都传 {@code null} 时返回的仍是一行全 NULL
 * （{@code present = 1}），{@link NotificationPreferences#allows} 会按"没配过 ⇒ 默认接收"处理。
 */
@Mapper
public interface NotificationPreferenceMapper {

    @Select(NotificationPreferences.SQL)
    Map<String, Object> selectPreferences(@Param("profileId") Long profileId,
                                          @Param("userId") Long userId);
}
