package com.academic.service.impl;

import com.academic.common.entity.College;
import com.academic.common.entity.Student;
import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.User;
import com.academic.mapper.CollegeMapper;
import com.academic.mapper.StudentMapper;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.mapper.UserMapper;
import com.academic.service.CollegeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CollegeServiceImpl extends ServiceImpl<CollegeMapper, College> implements CollegeService {

    private static final Logger log = LoggerFactory.getLogger(CollegeServiceImpl.class);

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private TeacherProfileMapper teacherProfileMapper;

    @Autowired
    private UserMapper userMapper;

    private static final Integer ROLE_TEACHER = 2;
    private static final Integer ROLE_COUNSELOR = 3;

    @Override
    @Cacheable(value = "colleges", key = "'all'")
    public List<College> getAllColleges() {
        try {
            List<College> colleges = this.list();
            for (College college : colleges) {
                LambdaQueryWrapper<Student> studentWrapper = new LambdaQueryWrapper<>();
                studentWrapper.eq(Student::getCollegeId, college.getId());
                long studentCountLong = studentMapper.selectCount(studentWrapper);
                int studentCount = (int) studentCountLong;
                college.setStudentCount(studentCount);

                int teacherCount = countUsersByCollegeAndRole(college.getId(), ROLE_TEACHER);
                college.setTeacherCount(teacherCount);

                int counselorCount = countUsersByCollegeAndRole(college.getId(), ROLE_COUNSELOR);
                college.setCounselorCount(counselorCount);
            }
            return colleges;
        } catch (Exception e) {
            log.error("操作异常", e);
            return new ArrayList<>();
        }
    }

    @Override
    @Cacheable(value = "colleges", key = "'id:' + #id")
    public College getCollegeById(Long id) {
        College college = this.getById(id);
        if (college != null) {
            LambdaQueryWrapper<Student> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.eq(Student::getCollegeId, id);
            college.setStudentCount((int)(long) studentMapper.selectCount(studentWrapper));
            college.setTeacherCount(countUsersByCollegeAndRole(id, ROLE_TEACHER));
            college.setCounselorCount(countUsersByCollegeAndRole(id, ROLE_COUNSELOR));
        }
        return college;
    }

    private int countUsersByCollegeAndRole(Long collegeId, Integer role) {
        LambdaQueryWrapper<TeacherProfile> profileWrapper = new LambdaQueryWrapper<>();
        profileWrapper.eq(TeacherProfile::getCollegeId, collegeId);
        List<TeacherProfile> profiles = teacherProfileMapper.selectList(profileWrapper);

        if (profiles.isEmpty()) return 0;

        List<Long> userIds = new ArrayList<>();
        for (TeacherProfile profile : profiles) {
            if (profile.getUserId() != null) userIds.add(profile.getUserId());
        }

        if (userIds.isEmpty()) return 0;

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds);
        userWrapper.eq(User::getRole, role);
        userWrapper.eq(User::getStatus, 1);

        long count = userMapper.selectCount(userWrapper);
        return (int) count;
    }

    @Override
    @CacheEvict(value = "colleges", allEntries = true)
    public boolean createCollege(College college) {
        try { return this.save(college); } catch (Exception e) { log.error("操作异常", e); return false; }
    }

    @Override
    @CacheEvict(value = "colleges", allEntries = true)
    public boolean updateCollege(College college) {
        try { return this.updateById(college); } catch (Exception e) { log.error("操作异常", e); return false; }
    }

    @Override
    @CacheEvict(value = "colleges", allEntries = true)
    public boolean deleteCollege(Long id) {
        try { return this.removeById(id); } catch (Exception e) { log.error("操作异常", e); return false; }
    }
}
