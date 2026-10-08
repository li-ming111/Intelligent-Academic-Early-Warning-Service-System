package com.academic.service.impl;

import com.academic.common.entity.CounselorProfile;
import com.academic.mapper.CounselorProfileMapper;
import com.academic.service.CounselorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 辅导员服务实现类
 */
@Service
public class CounselorServiceImpl extends ServiceImpl<CounselorProfileMapper, CounselorProfile> implements CounselorService {

    @Override
    public CounselorProfile getByUserId(Long userId) {
        LambdaQueryWrapper<CounselorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CounselorProfile::getUserId, userId);
        CounselorProfile counselor = baseMapper.selectOne(wrapper);
        
        // 如果找不到辅导员记录，自动创建一个默认记录
        if (counselor == null) {
            counselor = new CounselorProfile();
            counselor.setCounselorId("C" + userId);
            counselor.setUserId(userId);
            counselor.setName("辅导员");
            counselor.setCollegeName("计算机学院");
            counselor.setDepartment("学生工作办公室");
            counselor.setStatus(1);
            counselor.setCreatedAt(LocalDateTime.now());
            counselor.setUpdatedAt(LocalDateTime.now());
            baseMapper.insert(counselor);
        }
        
        return counselor;
    }

    @Override
    public CounselorProfile getByCounselorId(String counselorId) {
        LambdaQueryWrapper<CounselorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CounselorProfile::getCounselorId, counselorId);
        return baseMapper.selectOne(wrapper);
    }

    // register 已删（2026-10-08 W27）：原来只 `save(counselorProfile)`，不建 users 账号、
    // 不设口令；user_id 无外键 ⇒ 能插出指向不存在用户的孤儿档案。零调用方，
    // 真实注册在 user-service `/auth/register/counselor`。

    @Override
    public boolean updateProfile(CounselorProfile counselorProfile) {
        // 设置更新时间
        counselorProfile.setUpdatedAt(LocalDateTime.now());
        return updateById(counselorProfile);
    }

    @Override
    public boolean deleteProfile(Long id) {
        return removeById(id);
    }

    @Override
    public long countByCollege(Long collegeId) {
        LambdaQueryWrapper<CounselorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CounselorProfile::getCollegeId, collegeId);
        return baseMapper.selectCount(wrapper);
    }

    @Override
    public java.util.List<CounselorProfile> getAllCounselors(Long collegeId) {
        LambdaQueryWrapper<CounselorProfile> wrapper = new LambdaQueryWrapper<>();
        if (collegeId != null) {
            wrapper.eq(CounselorProfile::getCollegeId, collegeId);
        }
        wrapper.orderByDesc(CounselorProfile::getCreatedAt);
        return baseMapper.selectList(wrapper);
    }
}
