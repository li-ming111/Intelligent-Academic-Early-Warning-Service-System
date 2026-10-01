package com.academic.service.impl;

import com.academic.common.entity.College;
import com.academic.mapper.CollegeMapper;
import com.academic.service.CollegeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学院服务实现类。
 *
 * <p>2026-10-01 第二十三轮：随 {@link CollegeService} 一起收敛到只剩「取全部」。
 * 原来的 8 个方法（含 try/catch + log 的 1:1 转发样板）全部删除。
 */
@Slf4j
@Service
public class CollegeServiceImpl implements CollegeService {

    private final CollegeMapper collegeMapper;

    public CollegeServiceImpl(CollegeMapper collegeMapper) {
        this.collegeMapper = collegeMapper;
    }

    @Override
    public List<College> listAll() {
        log.info("List all colleges");
        return collegeMapper.selectList(null);
    }
}
