package com.academic.mapper;

import com.academic.common.entity.Student;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 学生Mapper
 */
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 按 {@code users.id} 批量取学生账号侧的字段：邮箱、手机号、账号状态。
     *
     * <p>这三列都在 {@code users} 表上，**不在** {@code student_profile}（实测该表 13 列，
     * 只有 id/user_id/student_id/name/gender/college_id/major_id/class_id/class_name/
     * enrollment_year/privacy_level/created_at/updated_at）。而辅导员端「学生管理」页
     * 的电话、邮箱两列以及导出 CSV 直接读 {@code Student.phone}/{@code Student.email}
     * （实体上标了 {@code @TableField(exist = false)}）—— 于是它们**永远是空的**，
     * 页面上那两列从来没有任何一行显示出内容（2026-10-02，W4.11 修）。
     *
     * <p>返回列：{@code id}(users.id) / {@code phone} / {@code email} / {@code status}。
     * 用 {@code <script>} 拼 {@code IN}，与本仓既有写法一致（admin-service 的
     * {@code AuditLogMapper} 等同一形状）；调用方负责传非空列表，空列表不要调。
     */
    @Select("<script>"
          + "SELECT u.id AS id, u.phone AS phone, u.email AS email, u.status AS status "
          + "  FROM users u WHERE u.id IN "
          + "  <foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>"
          + "</script>")
    List<Map<String, Object>> selectUserContacts(@Param("userIds") List<Long> userIds);
}
