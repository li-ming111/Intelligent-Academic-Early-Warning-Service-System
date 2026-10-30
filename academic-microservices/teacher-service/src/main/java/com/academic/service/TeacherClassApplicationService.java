package com.academic.service;

import com.academic.common.entity.TeacherProfile;
import com.academic.common.entity.User;
import com.academic.entity.TeacherClassApplication;
import com.academic.entity.ClassInfo;
import com.academic.mapper.TeacherClassApplicationMapper;
import com.academic.mapper.TeacherProfileMapper;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class TeacherClassApplicationService {
    @Autowired
    private TeacherClassApplicationMapper applicationMapper;
    
    @Autowired
    private TeacherProfileMapper teacherProfileMapper;
    
    @Autowired
    private ClassInfoMapper classInfoMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 提交班级管理申请
     * @return null表示成功，否则返回错误消息
     */
    public String submitApplication(Long teacherId, Long classId, String reason, String className) {
        // 默认是申请管理（类型0）
        return submitApplication(teacherId, classId, reason, className, 0);
    }
    
    /**
     * 提交班级管理申请（带申请类型）
     * @param applicationType 申请类型：0-申请管理，1-解除管理
     * @return null表示成功，否则返回错误消息
     */
    public String submitApplication(Long teacherId, Long classId, String reason, String className, Integer applicationType) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        
        if (applicationType == 0) {
            // 申请管理
            // 1. 检查班级是否已经有教师管理
            if (classInfo != null && classInfo.getTeacherId() != null && classInfo.getTeacherId() > 0) {
                return "该班级已有教师管理，无法重复申请";
            }
            
            // 2. 检查是否已有待审核的管理申请
            // 能走到这里说明班级当前无管理者，历史“已通过”的管理申请只代表曾经管理过、
            // 后来又被解除，不能作为拒绝重新申请的依据，只有待审核（status=0）才拦截
            List<TeacherClassApplication> existingApplications = applicationMapper.getApplicationsByTeacherAndClass(teacherId, classId);
            if (existingApplications != null) {
                for (TeacherClassApplication app : existingApplications) {
                    boolean isManageApplication = app.getApplicationType() == null || app.getApplicationType() == 0;
                    if (isManageApplication && app.getStatus() != null && app.getStatus() == 0) {
                        return "您已经申请过该班级";
                    }
                }
            }
        } else {
            // 解除管理
            // 1. 检查班级是否存在且该教师是当前管理者
            if (classInfo == null || classInfo.getTeacherId() == null || !classInfo.getTeacherId().equals(teacherId)) {
                return "您不是该班级的管理者，无法提交解除申请";
            }
            
            // 2. 检查是否已经有解除申请在处理中
            List<TeacherClassApplication> existingApplications = applicationMapper.getApplicationsByTeacherAndClass(teacherId, classId);
            if (existingApplications != null) {
                for (TeacherClassApplication app : existingApplications) {
                    if (app.getApplicationType() != null && app.getApplicationType() == 1 && app.getStatus() == 0) {
                        return "您已经提交了解除申请，正在审核中";
                    }
                }
            }
        }
        
        TeacherClassApplication application = new TeacherClassApplication();
        application.setTeacherId(teacherId);
        application.setClassId(classId);
        application.setReason(reason);
        application.setClassName(className);
        application.setApplicationType(applicationType);
        
        // 查询教师信息（优先从 TeacherProfile 获取）
        TeacherProfile profile = teacherProfileMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TeacherProfile>()
                .eq("user_id", teacherId)
        );
        
        String teacherName = null;
        String teacherUsername = null;
        
        if (profile != null && profile.getName() != null && !profile.getName().isEmpty()) {
            teacherName = profile.getName();
            // 使用教师ID作为工号（TeacherProfile没有staffId字段）
            teacherUsername = "T" + teacherId;
        } else {
            // 如果 TeacherProfile 中没有信息，从 User 表获取
            User user = userMapper.selectById(teacherId);
            if (user != null) {
                teacherName = user.getName() != null ? user.getName() : ("教师" + teacherId);
                teacherUsername = user.getUsername() != null ? user.getUsername() : ("T" + teacherId);
            } else {
                // 如果都找不到，使用默认值
                teacherName = "教师" + teacherId;
                teacherUsername = "T" + teacherId;
            }
        }
        
        application.setTeacherName(teacherName);
        application.setTeacherUsername(teacherUsername);
        
        applicationMapper.insertApplication(application);
        return null; // 成功
    }
    
    public List<TeacherClassApplication> getApplicationsByTeacherId(Long teacherId) {
        return applicationMapper.getApplicationsByTeacherId(teacherId);
    }
    
    public List<TeacherClassApplication> getAllApplications() {
        return applicationMapper.getAllApplications();
    }
    
    public TeacherClassApplication getApplicationById(Long id) {
        return applicationMapper.getApplicationById(id);
    }
    
    /**
     * 解除班级管理权限
     *
     * <p>两个写操作：先把 {@code classes.teacher_id} 置空，再把相关申请单置为"已取消"。
     * 两步之间失败会留下"班级没人管、申请单还挂着已通过"，所以整个方法必须是一个事务。
     * 这里不加 try/catch —— 方法内吞掉异常会让 @Transactional 以为一切正常照常提交
     * （那就是个假事务）；DB 异常直接抛出，调用方 TeacherController 的 catch 在事务
     * 边界之外，回滚不受影响。
     *
     * @param teacherId 用户ID（User.id）
     * @param classId 班级ID
     * @return null表示成功，否则返回错误消息
     */
    @Transactional(rollbackFor = Exception.class)
    public String cancelClassManagement(Long teacherId, Long classId) {
        // 1. 检查班级是否存在
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            return "班级不存在";
        }
        
        // 2. 根据用户ID查询教师Profile
        // 注意：classes.teacher_id 存储的是 User.id，不是 TeacherProfile.id
        // 所以这里不需要转换为 TeacherProfile.id，直接使用 teacherId 即可
        TeacherProfile profile = teacherProfileMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TeacherProfile>()
                .eq("user_id", teacherId)
        );
        
        // 无论是否找到 TeacherProfile，都使用传入的 teacherId（User.id）进行验证
        // 因为 classes.teacher_id 存储的就是 User.id
        Long realTeacherId = teacherId;
        
        // 3. 检查教师是否管理该班级
        // 如果班级没有教师（teacher_id为null），说明已经没有管理者，直接返回成功
        if (classInfo.getTeacherId() == null) {
            log.info("用户ID {} 尝试解除班级 {} 的管理权限，班级teacherId已为null，无需解除", teacherId, classId);
            return null; // 直接返回成功，因为已经没有管理权限了
        }
        
        // 如果教师ID不匹配，检查申请记录是否有该用户的已批准申请
        if (!classInfo.getTeacherId().equals(realTeacherId)) {
            // 检查是否有该用户的已批准申请（状态=1）
            List<TeacherClassApplication> approvedApplications = applicationMapper.getApplicationsByTeacherAndClass(realTeacherId, classId);
            boolean hasApprovedApplication = false;
            if (approvedApplications != null) {
                for (TeacherClassApplication app : approvedApplications) {
                    if (app.getStatus() == 1) { // 1-已通过
                        hasApprovedApplication = true;
                        break;
                    }
                }
            }
            
            // 如果没有已批准的申请，拒绝解除
            if (!hasApprovedApplication) {
                log.warn("用户ID {} 尝试解除班级 {} 的管理权限，但班级的teacherId是 {}，realTeacherId是 {}，无已批准申请", 
                    teacherId, classId, classInfo.getTeacherId(), realTeacherId);
                return "您不是该班级的管理者";
            }
            
            // 如果有已批准的申请但班级teacherId不匹配，可能是数据不一致，仍允许解除
            log.info("用户ID {} 解除班级 {} 的管理权限（检测到已批准申请但teacherId不匹配，可能是数据不一致）", 
                teacherId, classId);
        }
        
        // 4. 解除班级与教师的关联（使用 UpdateWrapper 确保 teacher_id 被设置为 null）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ClassInfo> updateWrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("id", classId).set("teacher_id", null);
        classInfoMapper.update(null, updateWrapper);
        
        // 5. 更新相关申请记录状态为"已取消"
        List<TeacherClassApplication> applications = applicationMapper.getApplicationsByTeacherAndClass(realTeacherId, classId);
        if (applications != null && !applications.isEmpty()) {
            for (TeacherClassApplication application : applications) {
                // 将状态更新为已取消（状态值：0-待审核，1-已通过，2-已拒绝，3-已取消）
                application.setStatus(3);
                applicationMapper.updateById(application);
            }
        }
        
        return null; // 成功
    }
}
