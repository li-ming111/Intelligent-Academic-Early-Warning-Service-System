package com.academic.service;

import com.academic.common.entity.TeacherProfile;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 教师服务接口
 */
public interface TeacherService extends IService<TeacherProfile> {
    /**
     * 根据用户ID获取教师信息
     * @param userId 用户ID
     * @return 教师信息
     */
    TeacherProfile getByUserId(Long userId);

    // register 已删（2026-10-08 W27）：它只插 teacher_profile 档案行，不建账号、不设口令，
    // 且零调用方。注册在 user-service 的 `/auth/register/teacher`。
    /**
     * 更新教师信息
     * @param teacherProfile 教师信息
     * @return 是否更新成功
     */
    boolean updateProfile(TeacherProfile teacherProfile);

    /**
     * 删除教师信息
     * @param id 教师ID
     * @return 是否删除成功
     */
    boolean deleteProfile(Long id);
    
    /**
     * 按学院统计教师数量
     * @param collegeId 学院ID
     * @return 教师数量
     */
    long countByCollege(Long collegeId);
}
