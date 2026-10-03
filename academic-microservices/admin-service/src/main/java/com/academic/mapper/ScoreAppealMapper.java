package com.academic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

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

    /**
     * 申诉列表（联学生姓名/学号与课程名）。
     *
     * <p>2026-10-02 第二十五轮新增：此前全仓对 {@code score_appeals} **只有一条 UPDATE**
     * —— 学生自己撤回（{@code SET status=3}）。**没有任何代码写 status 1/2**，
     * 管理员既看不到待审申诉、也没有审核入口，库里那条 status=0 永远处理不掉。
     *
     * <p>{@code status} 传 null 表示不过滤。排序把待处理排在最前，其余按时间倒序。
     * 列名用别名给成驼峰，前端直接吃。课程名在 {@code courses.name}（**不是 course_name**）。
     */
    @Select("<script>"
          + "SELECT a.id, a.student_id AS studentId, sp.name AS studentName, sp.student_id AS studentNo, "
          + "       a.course_id AS courseId, c.name AS courseName, c.code AS courseCode, "
          + "       a.score_id AS scoreId, "
          + "       s.semester AS scoreSemester, s.year AS scoreYear, "
          + "       s.score_total AS scoreTotal, s.grade AS scoreGrade, "
          + "       a.reason, a.description, a.status, a.reply, "
          + "       a.created_at AS createdAt, a.updated_at AS updatedAt "
          + "FROM score_appeals a "
          + "LEFT JOIN student_profile sp ON sp.id = a.student_id "
          + "LEFT JOIN courses c ON c.id = a.course_id "
          + "LEFT JOIN scores s ON s.id = a.score_id "
          + "<where><if test='status != null'>a.status = #{status}</if></where> "
          + "ORDER BY (a.status = 0) DESC, a.created_at DESC"
          + "</script>")
    List<Map<String, Object>> selectAppeals(@Param("status") Integer status);

    /**
     * 单条申诉（审核时取学生 profile id 与课程名，用于回写通知）。
     *
     * <p>2026-10-03（第四十三轮 W9）：同样带出 {@code score_id} 与那次成绩的学期/总分/等级 ——
     * 审核端是最需要看清"申诉的是哪一次成绩"的地方（一门课有多个学期）。
     * {@code score_id} 为空的老行这三列自然为 null，前端不显示即可。
     */
    @Select("SELECT a.id, a.student_id AS studentId, a.course_id AS courseId, c.name AS courseName, a.status, "
          + "       a.score_id AS scoreId, s.semester AS scoreSemester, s.year AS scoreYear, "
          + "       s.score_total AS scoreTotal, s.grade AS scoreGrade "
          + "FROM score_appeals a LEFT JOIN courses c ON c.id = a.course_id "
          + "LEFT JOIN scores s ON s.id = a.score_id WHERE a.id = #{id}")
    Map<String, Object> selectAppealById(@Param("id") Long id);

    /**
     * 审核申诉：写 status（1 通过 / 2 驳回）与 reply（审核意见）。
     *
     * <p>WHERE 带 {@code status = 0}：只有待处理的行能被审，重复审核/并发审核影响行数为 0，
     * 由调用方如实报错。{@code updated_at} 由 DDL 的 ON UPDATE 自动推进。
     */
    @Update("UPDATE score_appeals SET status = #{status}, reply = #{reply} "
          + "WHERE id = #{id} AND status = 0")
    int reviewAppeal(@Param("id") Long id, @Param("status") Integer status, @Param("reply") String reply);
}
