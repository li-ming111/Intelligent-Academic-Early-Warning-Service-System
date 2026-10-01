package com.academic.mapper;

import com.academic.common.entity.CounselorProfile;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.UserSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 自助注册这条写路径上，除 {@code users} 之外的**全部** SQL 都收在这里。
 *
 * <h3>为什么单开一个 Mapper，而不是给每张表配一个实体 + {@code BaseMapper}</h3>
 * <p>注册是一次**跨 4 张表**的写（{@code users} / {@code student_profile|teacher_profile|
 * counselor_profiles} / {@code user_settings} / {@code colleges} 的冗余计数列）。
 * 之前只有第一张表被写、后三张全丢，页面上看不出来（新账号能登录，但登录进去什么都是空的）。
 * 把这几条 SQL 放在同一个文件里，是为了让"注册到底写了哪几张表"一眼可查 —— 不再有
 * "哪张表被忘了"这种事藏在别的模块。
 *
 * <p>另外两条具体原因：
 * <ol>
 *   <li><b>{@code student_profile} 不能用 common 的 {@code Student} 实体写。</b>
 *       那个实体把 {@code className} 标成了 {@code @TableField(exist = false)}
 *       （它是给"从 JOIN 里读班级名"用的），拿它 insert 的话 {@code class_name}
 *       会被**静默丢掉** —— 而 {@code db_backups/verify_invariants.py} 的 C 组要求
 *       "学生档案的 class_name 必须等于 class_id 指向的班名"，写了 class_id 却没写
 *       class_name 就是账实不符。所以这里逐列传参。</li>
 *   <li>教师/辅导员用 common 的 {@code TeacherProfile} / {@code CounselorProfile}
 *       作为参数对象（这两个实体列是齐的），但**仍写显式 INSERT**：列名写在 SQL 里，
 *       改表结构时不会因为"实体多了一个字段"而悄悄多写一列。</li>
 * </ol>
 *
 * <p>⚠️ 三张 profile 表的 NOT NULL 列（实测 information_schema）：
 * {@code student_profile}: {@code user_id, student_id, name}；
 * {@code teacher_profile}: {@code user_id, college_id}；
 * {@code counselor_profiles}: {@code user_id}。
 * {@code created_at}/{@code updated_at} 都有 DB 默认值，故不写。
 *
 * <p>⚠️ 与 {@code admin-service/AdminController.createProfileIfNeeded} 的关系：
 * 那边是**管理员建号**（表单里没有学院），口径是"必填列拿不到就不建 profile"；
 * 这里是**本人自助注册**（表单里学院是必填），口径是"教师必须带学院来"。
 * 两条路径要一起看，别只改一边 —— 否则同一个角色会出现两种"资料齐不齐"的标准。
 */
public interface RegistrationMapper {

    // ==================== 只读：落库前的查证 ====================

    /**
     * 班级行（写学生档案时要拿它的 {@code name} / {@code major_id} / {@code college_id}）。
     *
     * <p>用 {@code Map} 而不是 {@code Classes} 实体，是为了避免 user-service 依赖
     * common 的 {@code Classes}，也能少一次驼峰映射的假设。
     */
    @Select("SELECT id, name, college_id, major_id FROM classes WHERE id = #{classId}")
    Map<String, Object> selectClassRow(@Param("classId") Long classId);

    /** 学院是否存在（不校验就写 college_id 会造出悬空引用）。*/
    @Select("SELECT COUNT(*) FROM colleges WHERE id = #{id}")
    int countCollege(@Param("id") Long id);

    /** 专业是否存在。*/
    @Select("SELECT COUNT(*) FROM majors WHERE id = #{id}")
    int countMajor(@Param("id") Long id);

    /** 专业所属学院（学生只填了专业时，学院要跟着专业走）。*/
    @Select("SELECT college_id FROM majors WHERE id = #{id}")
    Long selectMajorCollegeId(@Param("id") Long id);

    /**
     * 是否已有档案占用了这个学号。
     *
     * <p>{@code student_profile.student_id} 有唯一约束。注册时学号取 {@code users.username}，
     * 正常情况下 {@code users.username} 的唯一性已经拦住了；但历史数据里可能存在
     * "账号名 ≠ 学号"的档案，那时唯一约束会以 {@code DuplicateKeyException} 的形式
     * 冒到最后（一条没人看得懂的 500）。先查一次，把它变成一句人话。
     */
    @Select("SELECT COUNT(*) FROM student_profile WHERE student_id = #{studentId}")
    int countStudentProfileByStudentId(@Param("studentId") String studentId);

    // ==================== 写：三张角色资料表 ====================

    /**
     * 学生档案。{@code class_name} 与 {@code class_id} 要么一起给要么一起不给
     * （见类注释第 1 条与 verify_invariants.py 的 C 组）。
     */
    @Insert("INSERT INTO student_profile "
            + "(user_id, student_id, name, gender, college_id, major_id, class_id, class_name) "
            + "VALUES (#{userId}, #{studentId}, #{name}, #{gender}, #{collegeId}, "
            + "#{majorId}, #{classId}, #{className})")
    int insertStudentProfile(@Param("userId") Long userId,
                             @Param("studentId") String studentId,
                             @Param("name") String name,
                             @Param("gender") String gender,
                             @Param("collegeId") Long collegeId,
                             @Param("majorId") Long majorId,
                             @Param("classId") Long classId,
                             @Param("className") String className);

    /** 教师档案。{@code college_id} 是 NOT NULL —— 调用方必须先保证它非空。*/
    @Insert("INSERT INTO teacher_profile (user_id, name, college_id, title) "
            + "VALUES (#{userId}, #{name}, #{collegeId}, #{title})")
    int insertTeacherProfile(TeacherProfile profile);

    /**
     * 辅导员档案。{@code office_location} / {@code duty_phone} **刻意不写** ——
     * 办公信息在库里的真源是 {@code user_settings}（见 {@link #insertUserSettings}），
     * 这两列全仓没有 Java 读者，再往里写就又成了双数据源。
     * 注册表单上填的"办公地点/值班电话"进 {@code user_settings}。
     */
    @Insert("INSERT INTO counselor_profiles "
            + "(user_id, counselor_id, staff_id, name, gender, phone, email, "
            + " college_id, department, title, education, introduction, status) "
            + "VALUES (#{userId}, #{counselorId}, #{staffId}, #{name}, #{gender}, #{phone}, #{email}, "
            + "#{collegeId}, #{department}, #{title}, #{education}, #{introduction}, #{status})")
    int insertCounselorProfile(CounselorProfile profile);

    // ==================== 写：账号级设置（办公信息） ====================

    /**
     * 账号级设置行。目前只有辅导员的注册表单会填办公信息，学生的注册表单不填 ——
     * 所以**只在有值可写时才建这一行**，避免给每个新账号都留一条空设置。
     */
    @Insert("INSERT INTO user_settings (user_id, office_location, office_phone) "
            + "VALUES (#{userId}, #{officeLocation}, #{officePhone})")
    int insertUserSettings(UserSettings settings);

    // ==================== 学院冗余计数列：已删（2026-10-01） ====================
    //
    // 这里原有 `increaseCollegeStudentCount` / `increaseCollegeTeacherCount` 两条
    // `UPDATE colleges SET xxx_count = xxx_count + 1`。删掉的依据：
    //
    //   - `College.studentCount/teacherCount` **本来就是 `@TableField(exist = false)`**
    //     （只算不存），`counselor_count` 连列都不存在 —— 也就是说 Java 侧从来没人读这两列；
    //   - 唯一维护它们的就是这两条 `+1`。于是它们必然漂移：管理员建号、删号、
    //     批量导入、改学生的学院……**没有任何一条路径会动它们**，
    //     删一个学生这两列就永久偏大；
    //   - `verify_invariants.py` 的 B 组原本拿这两列做校验（40 项里的 2 项），
    //     等于在守一个只被注册路径碰过的列。
    //
    // 处理：**列本身也从库里删掉**（`db_backups/colleges_drop_redundant_counts_20261001_*.sql`），
    // B 组那两项随之移除（40 → 38 项）。展示用的"学生数/教师数"一律走现算
    // （`AdminController.getStudentCount(id)` 这类 `COUNT(*)`），不落库。
}
