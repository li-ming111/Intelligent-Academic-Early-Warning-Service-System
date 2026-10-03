package com.academic.service.impl;

import com.academic.common.entity.Student;
import com.academic.mapper.StudentMapper;
import com.academic.mapper.ClassInfoMapper;
import com.academic.service.StudentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 学生服务实现类
 */
@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private com.academic.mapper.ScoreMapper scoreMapper;

    @Override
    public List<Student> getByCounselorId(Long counselorId) {
        // 通过 classes.counselor_id 查询辅导员管辖班级的学生
        List<Map<String, Object>> classes = classInfoMapper.getClassesByCounselorIdMap(counselorId);
        if (classes == null || classes.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建 classId → className 映射
        List<Long> classIds = new ArrayList<>();
        java.util.Map<Long, String> classIdToName = new java.util.HashMap<>();
        for (Map<String, Object> c : classes) {
            Object id = c.get("id");
            if (id != null) {
                Long cid = ((Number) id).longValue();
                classIds.add(cid);
                classIdToName.put(cid, (String) c.get("name"));
            }
        }
        if (classIds.isEmpty()) return new ArrayList<>();

        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Student::getClassId, classIds);
        List<Student> students = baseMapper.selectList(wrapper);
        
        // 填充 className（非数据库字段，需手动赋值）
        for (Student s : students) {
            String cn = classIdToName.get(s.getClassId());
            if (cn != null) s.setClassName(cn);
        }

        // 2026-10-01 第二十一轮: 详情弹窗要显示的「获取学分 / GPA」三个字段也不在
        // student_profile 表上（学分绩点都从 scores 现算），这里一次批量查询后填进去。
        // 查不到的学生保持 null，前端按 '--' 显示 —— 不编值。
        if (!students.isEmpty()) {
            Map<Long, Map<String, Object>> stats = new java.util.HashMap<>();
            for (Map<String, Object> row : scoreMapper.getStudentCreditStatsByCounselorId(counselorId)) {
                Object pid = row.get("profile_id");
                if (pid != null) {
                    stats.put(((Number) pid).longValue(), row);
                }
            }
            for (Student s : students) {
                Map<String, Object> row = stats.get(s.getId());
                if (row == null) continue;
                Object earned = row.get("earned_credits");
                Object attempted = row.get("attempted_credits");
                Object gpa = row.get("gpa");
                if (earned instanceof Number) s.setCredits(((Number) earned).doubleValue());
                if (attempted instanceof Number) s.setRequiredCredits(((Number) attempted).intValue());
                if (gpa instanceof Number) {
                    s.setGpa(java.math.BigDecimal.valueOf(((Number) gpa).doubleValue())
                            .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
                }
            }
        }

        // 2026-10-02（W4.11）：电话 / 邮箱 / 账号状态在 `users` 表上，
        // 而辅导员「学生管理」页的三列（含导出的 CSV 表头）读的正是 Student.phone /
        // Student.email 与一个写死的"正常"。这三个字段标了 @TableField(exist = false)，
        // selectList 不会带出来 —— 一次批量取回后填上，取不到保持 null（前端按 '--'）。
        List<Long> userIds = new ArrayList<>();
        for (Student s : students) {
            if (s.getUserId() != null) userIds.add(s.getUserId());
        }
        if (!userIds.isEmpty()) {
            Map<Long, Map<String, Object>> contacts = new java.util.HashMap<>();
            for (Map<String, Object> row : baseMapper.selectUserContacts(userIds)) {
                Object uid = row.get("id");
                if (uid != null) contacts.put(((Number) uid).longValue(), row);
            }
            for (Student s : students) {
                Map<String, Object> row = contacts.get(s.getUserId());
                if (row == null) continue;
                Object phone = row.get("phone");
                Object email = row.get("email");
                Object status = row.get("status");
                if (phone != null) s.setPhone(phone.toString());
                if (email != null) s.setEmail(email.toString());
                if (status instanceof Number) s.setUserStatus(((Number) status).intValue());
            }
        }
        return students;
    }

    @Override
    public List<Student> searchStudents(String keyword) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Student::getName, keyword)
               .or()
               .like(Student::getStudentId, keyword);
        return baseMapper.selectList(wrapper);
    }
}
