package com.academic.service.impl;

import com.academic.common.entity.TeacherProfile;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.service.TeacherService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 教师服务实现类
 */
@Service
public class TeacherServiceImpl extends ServiceImpl<TeacherProfileMapper, TeacherProfile> implements TeacherService {

    @Override
    public TeacherProfile getByUserId(Long userId) {
        LambdaQueryWrapper<TeacherProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeacherProfile::getUserId, userId);
        return baseMapper.selectOne(wrapper);
    }

    // register 已删（2026-10-08 W27）：原来只 `save(teacherProfile)`，不建 users 账号、
    // 不设口令 —— 对已存在的 user_id 会插出第二份档案，对陌生 user_id 撞外键直接失败。
    // 零调用方，真实注册在 user-service `/auth/register/teacher`。

    @Override
    public boolean updateProfile(TeacherProfile teacherProfile) {
        // 设置更新时间
        teacherProfile.setUpdatedAt(LocalDateTime.now());
        return updateById(teacherProfile);
    }

    @Override
    public boolean deleteProfile(Long id) {
        return removeById(id);
    }

    @Override
    public long countByCollege(Long collegeId) {
        LambdaQueryWrapper<TeacherProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeacherProfile::getCollegeId, collegeId);
        return baseMapper.selectCount(wrapper);
    }
}
