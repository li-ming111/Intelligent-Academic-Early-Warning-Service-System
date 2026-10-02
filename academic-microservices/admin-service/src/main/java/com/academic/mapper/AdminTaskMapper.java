package com.academic.mapper;

import com.academic.entity.AdminTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 管理端任务（W1.11，2026-10-02 新增）。
 *
 * <p>{@code BaseMapper} 管增删查；下面三条 {@code @Select}/{@code @Update} 是
 * 分页列表与两个"只改一列"的写操作 —— 列表要回 {@code createdAt}（页面
 * {@code <el-table-column prop="createdAt">} 读的就是这个键名），而
 * {@code updated_at} 必须**显式写**（本仓没有 {@code MetaObjectHandler}，
 * 列的 DEFAULT 只在 INSERT 生效，UPDATE 不写就永远停在插入那一刻）。
 */
@Mapper
public interface AdminTaskMapper extends BaseMapper<AdminTask> {

    /**
     * 分页查任务，按创建时间倒序（新建的排最前，符合"刚建完就能看到"的预期）。
     *
     * @param status 可空；传了就只回该状态的行（页面顶部那三个筛选按钮走的是
     *               **前端本地过滤**，这个参数是留给服务端筛选用的，两条路都通）
     */
    @Select("<script>"
            + "SELECT t.id AS id, t.title AS title, t.description AS description, "
            + "       t.priority AS priority, t.status AS status, "
            + "       t.created_by AS createdBy, u.username AS createdByUsername, "
            + "       t.created_at AS createdAt, t.updated_at AS updatedAt "
            + "FROM admin_tasks t LEFT JOIN users u ON u.id = t.created_by "
            + "<where>"
            + "  <if test='status != null and status != \"\"'> AND t.status = #{status} </if>"
            + "</where> "
            + "ORDER BY t.created_at DESC, t.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Map<String, Object>> selectPage(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /** 与 {@link #selectPage} 同一套过滤条件，只数总数（分页控件要用真 total）。 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM admin_tasks t "
            + "<where>"
            + "  <if test='status != null and status != \"\"'> AND t.status = #{status} </if>"
            + "</where>"
            + "</script>")
    long countWithFilter(@Param("status") String status);

    /**
     * 只改状态。返回受影响行数 —— **调用方必须检查它**：改一个不存在的 id 时
     * MyBatis-Plus 的 {@code updateById} 同样"成功"，只有行数能区分。
     */
    @Update("UPDATE admin_tasks SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
