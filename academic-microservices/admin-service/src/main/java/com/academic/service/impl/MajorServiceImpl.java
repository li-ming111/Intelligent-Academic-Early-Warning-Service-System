package com.academic.service.impl;

import com.academic.common.entity.Major;
import com.academic.mapper.MajorMapper;
import com.academic.service.MajorService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MajorServiceImpl extends ServiceImpl<MajorMapper, Major> implements MajorService {

    @Override
    public List<Major> getMajorsByCollegeId(Long collegeId) {
        QueryWrapper<Major> wrapper = new QueryWrapper<>();
        wrapper.eq("college_id", collegeId);
        return this.list(wrapper);
    }

    @Override
    public Major getMajorById(Long id) {
        return this.getById(id);
    }

    @Override
    public boolean createMajor(Major major) {
        return this.save(major);
    }

    @Override
    public boolean updateMajor(Major major) {
        return this.updateById(major);
    }

    @Override
    public boolean deleteMajor(Long id) {
        return this.removeById(id);
    }
}
