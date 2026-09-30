package com.academic.service;

/**
 * 学生批量导入的**单行写入**。
 *
 * <p>单独抽一个服务，是为了让"一行学生"成为一个事务单元：导入一行要写
 * {@code users} 和 {@code student_profile} 两笔，两笔必须同生共死。原来的
 * {@code /admin/import/students} 用两次 {@code saveBatch} 分开写（MyBatis-Plus 的
 * {@code saveBatch} 自带 {@code @Transactional}，所以那是**两个**事务），
 * 于是 {@code student_profile.name} 一违反 NOT NULL，用户行已经提交、档案行一条没有
 * —— 接口报错、账号却建出来了，重试还会撞重名。2026-09-30 实测确认。
 *
 * <p>批级仍然是**尽力而为**：一行失败只记一行错误，不连坐其余行，口径与同文件的
 * {@code importScores} 一致（一列填错的 Excel 不该把两百行好数据一起退回去）。
 */
public interface StudentImportService {

    /**
     * 导入一个学生：写 {@code users} 一行、{@code student_profile} 一行，同一事务。
     *
     * @param studentId   学号。本工程的口径是"用户名 == 学号"（见种子数据），
     *                    所以它同时作为 {@code users.username} 与 {@code student_profile.student_id}
     * @param name        姓名。{@code student_profile.name} 是 NOT NULL 且无默认值
     * @param rawPassword 明文口令，由 {@link UserService#createUser} 内部 BCrypt 编码
     * @return 成功返回 {@code null}；可预期的业务问题（学号/用户名已存在）返回一句给管理员看的
     *         原因，此时**一行都没写**。底层写库异常不在这里吞 —— 直接抛出去让事务回滚，
     *         由调用方按行收集。
     */
    String importOne(String studentId, String name, String rawPassword,
                     Long collegeId, Long majorId, String className, Long classId);
}
