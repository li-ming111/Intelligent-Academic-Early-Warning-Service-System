package com.academic.mapper;

import com.academic.entity.ClassInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 班级Mapper接口
 */
@Mapper
public interface ClassInfoMapper extends BaseMapper<ClassInfo> {

    /**
     * 根据教师ID获取管理的班级
     */
    @Select("SELECT c.id, c.name, c.college_id as collegeId, c.major_id as majorId, COUNT(s.id) as studentCount FROM classes c LEFT JOIN student_profile s ON c.id = s.class_id WHERE c.teacher_id = #{teacherId} GROUP BY c.id")
    List<Map<String, Object>> getClassesByTeacherIdMap(@Param("teacherId") Long teacherId);

    /**
     * 搜索班级（按名称）
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(s.id) as studentCount, CASE WHEN c.teacher_id IS NOT NULL THEN 1 ELSE 0 END as hasTeacher FROM classes c LEFT JOIN student_profile s ON c.id = s.class_id WHERE c.name LIKE CONCAT('%', #{keyword}, '%') GROUP BY c.id")
    List<Map<String, Object>> searchClassesMap(@Param("keyword") String keyword);

    /**
     * 获取所有班级
     */
    @Select("SELECT c.id, c.name, c.major_id as majorId, COUNT(s.id) as studentCount, CASE WHEN c.teacher_id IS NOT NULL THEN 1 ELSE 0 END as hasTeacher FROM classes c LEFT JOIN student_profile s ON c.id = s.class_id GROUP BY c.id")
    List<Map<String, Object>> getAllClassesMap();

    /**
     * 根据班级ID获取学生列表
     *
     * <p>2026-10-02（W4.11）：补 {@code profileId} 与 {@code userStatus}。
     * 前者（{@code student_profile.id}）是 {@code academic_warning.student_id} /
     * {@code scores.student_id} 的取值空间，与能给人看的学号 {@code studentId} 是两回事；
     * 后者是 {@code users.status}，供「学生详情」显示真实账号状态 ——
     * 那一格此前写死 <code>正常</code>，而 {@code users.status} 是可以被管理员改的。
     */
    @Select("SELECT sp.student_id as studentId, sp.id as profileId, u.name as studentName, "
          + "       u.status as userStatus "
          + "  FROM student_profile sp JOIN users u ON sp.user_id = u.id "
          + " WHERE sp.class_id = #{classId}")
    List<Map<String, Object>> getStudentsByClassIdMap(@Param("classId") Long classId);

    /**
     * 按学号找学生档案 id（{@code student_profile.id}，不是 users.id、也不是学号）。
     *
     * <p>分两个单列查询而不是一个 {@code Map} 结果：`mapUnderscoreToCamelCase` 开着的时候
     * Map 结果的 key 究竟是 {@code class_id} 还是 {@code classId} 取决于版本，
     * 取错了会静默当成"该生目前没有班级"。单列标量没有这个歧义。
     */
    @Select("SELECT id FROM student_profile WHERE student_id = #{studentId}")
    Long findProfileIdByStudentId(@Param("studentId") String studentId);

    /**
     * 学生的姓名（{@code student_profile → users.name}）。2026-10-04（W19）补，给教师端
     * 成绩导入用：Excel 里「姓名」列是可选的，没填时用它回填——否则导入的行在成绩列表里
     * 没有姓名，变成一串"光有分数没有名字"的记录。
     *
     * <p>W36 起回填结果只是路径内使用（教师端成绩不再落 {@code score_record}）；
     * 成绩行的姓名一律由 {@code student_profile} 联表取得，不存冗余列。
     *
     * <p>同样**刻意拆成单列标量**而不是返回 Map：{@code mapUnderscoreToCamelCase} 开着时
     * Map 结果的 key 是 {@code name} 还是别的取决于版本，取错就静默当成"查不到学生"。
     */
    @Select("SELECT u.name FROM student_profile sp JOIN users u ON sp.user_id = u.id "
          + "WHERE sp.student_id = #{studentId}")
    String findStudentNameByStudentId(@Param("studentId") String studentId);

    /** 该学生档案当前的班级（null = 尚未归入任何班级）。 */
    @Select("SELECT class_id FROM student_profile WHERE id = #{profileId}")
    Long findClassIdOfProfile(@Param("profileId") Long profileId);

    /**
     * 这个名字的班级里，有几个是 {@code teacherId}（{@code users.id}）所带的。
     * 2026-10-08（W30）补，给 {@code GET /teachers/scores/by-class} 不传
     * {@code teacher_id} 的那条分支做归属闸用。
     *
     * <p>为什么是 {@code COUNT} 而不是 {@code SELECT teacher_id ... LIMIT 1}：
     * {@code classes.name} **没有唯一约束**（只有一个 {@code idx_classes_college_id}），
     * 同名班级在结构上是允许的。返回"有几个是我的"⇒ 判据写成 {@code >0}，
     * 语义是「本人确实带过这个名字的班」，不会被同名行里任意一行的 {@code teacher_id}
     * 左右。返回 0 涵盖"没有这个班"与"这个班不是我的"两种情况 ——
     * 在归属闸里这两者必须同解（判不出来 ≠ 放行）。
     *
     * <p>⚠️ 已知边界（W36 更正）：此前写的理由是「{@code score_record} 只存 {@code class_name}
     * 字符串、不存 {@code class_id}，所以同名班级的成绩行本来就分不开」。前半句已不成立 ——
     * 教师端成绩改读 {@code scores} 后，班级归属走的是 {@code student_profile.class_id}
     * （成绩行本身不再携带 class_name）。但**边界本身仍在**，只是换了位置：本端点的入参就是
     * 一个 {@code class_name} 字符串，由它反查 {@code classes} 时同名班仍会被一并命中。
     * 真正要根治得先把这个端点的入参改成 {@code class_id}（属于接口契约变更，未在本轮做）。
     * 若真出现两个同名班，本闸放行的是"按名字取到的那些行"，这是接口契约的上限，不是闸的疏漏。
     */
    @Select("SELECT COUNT(*) FROM classes WHERE name = #{className} AND teacher_id = #{teacherId}")
    int countClassesNamedAndOwned(@Param("className") String className, @Param("teacherId") Long teacherId);

    /**
     * 把学生归入班级。
     *
     * <p>只改 {@code student_profile.class_id} 与 {@code class_name} —— 班级名单、班级人数、
     * 按班统计的预警都读这两列（见本类上面几条 SQL），没有别的"成员表"。
     * 一行一个 UPDATE，本身就是原子的。
     */
    @Update("UPDATE student_profile SET class_id = #{classId}, class_name = #{className}, updated_at = NOW() WHERE id = #{profileId}")
    int assignProfileToClass(@Param("profileId") Long profileId,
                             @Param("classId") Long classId,
                             @Param("className") String className);

    /**
     * 根据班级名称获取辅导员信息。
     *
     * <p><b>2026-10-08（W30）修：join 跨错了 id 空间 ⇒ {@code counselorName}/{@code counselorPhone}
     * 恒为 null。</b>原写法是 {@code ON c.counselor_id = cp.id}，但 {@code classes.counselor_id}
     * 存的是 <b>{@code users.id}</b>（活库全 53），而 {@code counselor_profiles.id} 是 1 ——
     * 两套 id 不相交，{@code 53 = 1} 永假 ⇒ LEFT JOIN 不命中，两个字段一路返回 {@code null}，
     * 而 {@code counselorId}（取自 {@code c.counselor_id}，不走 join）是好的。
     * 于是前端 {@code TeacherScores.vue}「通知辅导员」对话框里**辅导员姓名/电话是空白**，
     * 但 {@code counselorId} 有值、通知发得出去 —— 典型的"接口 200、字段恒空"。
     *
     * <p>改法不是把 join 换成 {@code cp.user_id}，而是**直接取 {@code users}**：
     * {@code counselor_profiles.phone} 活库那唯一一行本身就是 {@code NULL}
     * （该列没人维护），真值在 {@code users.phone}（53 → 13800000014）；
     * 姓名同理（{@code users.name} 才是登录接口回的那个值）。这与本仓既有口径一致
     * —— "电话/邮箱的真源在 {@code users}"（W4 在同一族上已经踩过一次）。
     * 跨空间 join 换成同空间 join，顺带把两个字段都变成真的有值。
     *
     * <p>{@code LIMIT 1}：{@code classes.name} 没有唯一约束（见
     * {@link #countClassesNamedAndOwned}），同名的两行会让本方法返回多行，
     * 而返回类型是 {@code Map} ⇒ MyBatis 的 {@code selectOne} 直接抛
     * {@code TooManyResultsException}（500）。按名字本来也分不开，取一行即可。
     */
    @Select("SELECT c.counselor_id as counselorId, u.name as counselorName, u.phone as counselorPhone "
          + "FROM classes c LEFT JOIN users u ON u.id = c.counselor_id "
          + "WHERE c.name = #{className} LIMIT 1")
    Map<String, Object> getCounselorByClassName(@Param("className") String className);
}
