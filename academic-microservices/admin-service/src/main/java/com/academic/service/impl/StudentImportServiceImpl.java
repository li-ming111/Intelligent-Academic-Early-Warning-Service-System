package com.academic.service.impl;

import com.academic.common.entity.User;
import com.academic.entity.StudentProfile;
import com.academic.mapper.StudentProfileMapper;
import com.academic.service.StudentImportService;
import com.academic.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @see StudentImportService
 */
@Service
public class StudentImportServiceImpl implements StudentImportService {

    @Autowired
    private UserService userService;

    @Autowired
    private StudentProfileMapper studentProfileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importOne(String studentId, String name, String rawPassword,
                            Long collegeId, Long majorId, String className, Long classId) {
        // student_id 是唯一键。先查一次，把"学号重复"变成一句人话 ——
        // 否则 DB 抛的唯一键异常对管理员没有任何解释力（就是原来那句 JDBC 约束名）。
        StudentProfile existing = studentProfileMapper.selectOne(
                new QueryWrapper<StudentProfile>().eq("student_id", studentId));
        if (existing != null) {
            return "学号已存在：" + studentId + "（" + existing.getName() + "）";
        }

        // 走 createUser，而不是裸 save/saveBatch：它会 BCrypt 编码口令、置 status=1、
        // 并且查一次重名。原来这里是 userService.saveBatch(...)，绕过了 createUser，
        // 于是**明文口令直接入库** —— 与"库里只有哈希"的口径不符，而且不会报错
        // （登录对非 $2a$ 开头的口令有一条明文兼容分支），所以这个违反是静默的。
        User user = new User();
        user.setUsername(studentId);   // 本工程口径：用户名 == 学号
        user.setName(name);
        user.setPassword(rawPassword);
        user.setRole(1);
        if (!userService.createUser(user)) {
            return "用户名已存在：" + studentId;
        }

        StudentProfile profile = new StudentProfile();
        profile.setUserId(user.getId());
        profile.setStudentId(studentId);
        // ↓ 这一列 NOT NULL 且无默认值，原来的 importStudents 从不设置它，
        //   就是"必炸"的那一处；修好后每个学生的姓名也不至于凭空缺失。
        profile.setName(name);
        profile.setCollegeId(collegeId);
        profile.setMajorId(majorId);
        profile.setClassName(className);
        profile.setClassId(classId);
        studentProfileMapper.insert(profile);
        return null;
    }
}
