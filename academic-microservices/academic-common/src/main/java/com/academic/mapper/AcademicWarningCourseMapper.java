package com.academic.mapper;

import com.academic.common.entity.AcademicWarningCourse;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 预警的「挂科课程明细」共享 Mapper（放在 academic-common，供所有读预警的服务共用）。
 *
 * <p><b>为什么放在这里而不是各服务各写一份</b>：本包（{@code com.academic.mapper}）已被
 * 12 个服务的 {@code @MapperScan("com.academic.mapper")} 覆盖，接口放在公共 jar 里
 * 各服务无需任何配置就能注入（同包先例：{@link NotificationPreferenceMapper}）。
 * 而"这条预警涉不涉及我教的课"这个判据只要有两份实现就一定会漂移。
 *
 * <p>读写都在这个接口上：写是扫描生成预警时逐门落一行（{@code insert}，由
 * {@code WarningWriter} 的事务包住），读是教师/管理员/学生三端的列表补课程字段。
 *
 * <p>⚠️ 写侧用 {@code insert} 会在重扫时撞 {@code uk_awc_warning_course} 唯一键。
 * 续用 {@link #upsert}（{@code ON DUPLICATE KEY UPDATE}），让重扫幂等。
 */
@Mapper
public interface AcademicWarningCourseMapper extends BaseMapper<AcademicWarningCourse> {

    /**
     * 取一批预警的课程明细，按分数升序（最低分在前，与正文罗列顺序、
     * 与父行 {@code trigger_score} 的口径一致）。
     *
     * <p>课程名取"课程表实况优先、课程被删则回退生成时快照"：
     * {@code COALESCE(c.name, awc.course_name)} —— 课程改名后读端看到新名，
     * 课程被删后也读得出名字，不会出现一行空白。
     *
     * <p>{@code courseTeacherId} 是任课教师的 {@code users.id}（不是
     * {@code teacher_profile.id}）—— 与 {@code courses.teacher_id} 同一套 id 空间，
     * 调用方拿它跟自己的 {@code X-User-Id} 直接比即可判"是不是我的课"。
     *
     * <p>入参为空集合时由调用方拦住，不要传空进来（{@code IN ()} 是坏 SQL）。
     */
    @Select("<script>"
          + "SELECT awc.warning_id AS warningId, awc.course_id AS courseId, "
          + "       COALESCE(c.name, awc.course_name) AS courseName, "
          + "       awc.score_total AS scoreTotal, awc.semester AS semester, "
          + "       c.teacher_id AS courseTeacherId "
          + "  FROM academic_warning_course awc "
          + "  LEFT JOIN courses c ON c.id = awc.course_id "
          + " WHERE awc.warning_id IN "
          + " <foreach collection='warningIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
          + " ORDER BY awc.warning_id, awc.score_total ASC, awc.course_id"
          + "</script>")
    List<Map<String, Object>> selectByWarningIds(@Param("warningIds") Collection<Long> warningIds);

    /**
     * 这些预警里，哪些涉及 {@code teacherId} 教的课 —— 返回预警 id 集合。
     *
     * <p>{@code teacherId} 是 {@code users.id}。判据是
     * {@code courses.teacher_id}（该列全库都是 {@code users.id}，
     * <b>不是</b> {@code teacher_profile.id}，见 init_schema.sql 里
     * teacher_course 那段注释：两套 id 空间并存，这里用的是 courses 那套）。
     *
     * <p>只用一层 JOIN 而不是 {@code IN}：调用方拿它去过滤自己已经查出来的预警集合，
     * 少一次往返。
     */
    @Select("<script>"
          + "SELECT DISTINCT awc.warning_id "
          + "  FROM academic_warning_course awc "
          + "  JOIN courses c ON c.id = awc.course_id "
          + " WHERE c.teacher_id = #{teacherId} AND awc.warning_id IN "
          + " <foreach collection='warningIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
          + "</script>")
    List<Long> selectWarningIdsInvolvingTeacher(@Param("teacherId") Long teacherId,
                                                @Param("warningIds") Collection<Long> warningIds);

    /**
     * 落一行课程明细；已存在（同预警同课程）则更新快照。
     *
     * <p>用 upsert 而不是裸 insert：重扫会把同一条预警重新算出来，唯一键
     * {@code uk_awc_warning_course} 会挡住第二次插入。这里让重写变成幂等更新，
     * 分数/学期按本次算出来的值刷新。
     */
    @Insert("INSERT INTO academic_warning_course "
          + "  (warning_id, course_id, course_name, score_total, semester) "
          + "VALUES (#{warningId}, #{courseId}, #{courseName}, #{scoreTotal}, #{semester}) "
          + "ON DUPLICATE KEY UPDATE course_name = VALUES(course_name), "
          + "                        score_total = VALUES(score_total), "
          + "                        semester    = VALUES(semester)")
    int upsert(AcademicWarningCourse row);
}
