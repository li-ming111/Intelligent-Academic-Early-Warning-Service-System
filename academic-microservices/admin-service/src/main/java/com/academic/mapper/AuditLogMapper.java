package com.academic.mapper;

import com.academic.entity.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 审计日志。
 *
 * <p>2026-10-02 之前 {@code audit_logs} 是**全仓零写入方**的空表（0 行），只有
 * {@code UserMapper} 里一句 {@code COUNT(*) FROM audit_logs} 在读它 —— 于是管理端
 * 「用户详情 → 操作日志」那一格永远是 0。写入方见 {@link com.academic.service.AuditLogService}，
 * 读取口见下面两条 {@code @Select}（管理端「操作日志」视图）。
 *
 * <p>只挂 {@code @Select}、不建实体映射的写法沿用 {@code ScoreAppealMapper} 的先例：
 * 这两个查询要 JOIN {@code users} 取操作人用户名，而 {@code AuditLog} 实体里没有这个字段，
 * 为它加一个 {@code @TableField(exist=false)} 反而会让"实体 == 表"这句话不成立。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 分页查审计日志，带上操作人用户名。
     *
     * <p>{@code LEFT JOIN}：{@code user_id} 可空（网关注入的身份解析不出来时写 NULL），
     * 那种行也要显示出来 —— 用 INNER JOIN 会把"身份不明的操作"整条藏掉，
     * 而恰恰是这类行最需要被看到。
     *
     * @param entityType 可空，按被操作对象类型过滤
     * @param actionType 可空，按动作过滤
     */
    @Select("<script>"
            + "SELECT al.id AS id, al.user_id AS userId, u.username AS username, "
            + "       al.action_type AS actionType, al.entity_type AS entityType, "
            + "       al.entity_id AS entityId, al.details AS details, al.created_at AS createdAt "
            + "FROM audit_logs al LEFT JOIN users u ON u.id = al.user_id "
            + "<where>"
            + "  <if test='entityType != null'> AND al.entity_type = #{entityType} </if>"
            + "  <if test='actionType != null'> AND al.action_type = #{actionType} </if>"
            + "</where> "
            + "ORDER BY al.created_at DESC, al.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Map<String, Object>> selectPageWithActor(@Param("entityType") String entityType,
                                                  @Param("actionType") String actionType,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    /** 与上一条同一套过滤条件，只数总数（分页控件要用）。 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM audit_logs al "
            + "<where>"
            + "  <if test='entityType != null'> AND al.entity_type = #{entityType} </if>"
            + "  <if test='actionType != null'> AND al.action_type = #{actionType} </if>"
            + "</where>"
            + "</script>")
    long countWithFilter(@Param("entityType") String entityType,
                         @Param("actionType") String actionType);
}
