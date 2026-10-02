package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 收件人解析 Mapper（放在 academic-common，供所有发通知的服务共用）。
 *
 * <p>唯一职责：把 {@code student_profile.id} 换成 {@code users.id}。
 * 这一步是硬性的 —— {@code notifications.user_id} 有真外键指向 {@code users.id}，
 * 少这一次转换就会在 INSERT 时炸 {@code ERROR 1452}，而且是在业务事务里炸。
 *
 * <p>不叫 {@code StudentMapper}：那个名字在 admin/auth/counselor/student/teacher
 * 五个服务里都已存在，放一个同名的到公共 jar 里会让 MyBatis 的 mapper 扫描撞 bean 名。
 */
@Mapper
public interface NotificationRecipientMapper {

    /**
     * 列：{@code profileId}（原样回显）/ {@code userId}（可能为 NULL —— 档案没绑账号）/
     * {@code name} / {@code classId}。查不到返回 {@code null}。
     */
    @Select("SELECT sp.id AS profileId, sp.user_id AS userId, sp.name AS name, "
          + "       sp.class_id AS classId "
          + "  FROM student_profile sp WHERE sp.id = #{profileId}")
    Map<String, Object> selectRecipient(@Param("profileId") Long profileId);
}
