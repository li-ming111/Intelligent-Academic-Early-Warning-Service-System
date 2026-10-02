package com.academic.service;

import com.academic.entity.ClassInfo;
import com.academic.entity.CounselorClassApplication;
import com.academic.common.entity.CounselorProfile;
import com.academic.mapper.ClassInfoMapper;
import com.academic.mapper.CounselorClassApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 班级管理申请 / 审核 / 解除的写库路径。
 *
 * <p>这三条路径原先都直接写在 {@code CounselorController} 里，各自动了**两张表**却没有事务：
 * 「审核」改 {@code counselor_class_application.status} 之后再改 {@code classes.counselor_id}，
 * 「解除」把 {@code classes.counselor_id} 置空之后再改申请状态 —— 两处第一步成功、第二步
 * 失败时，库里就停在一个中间状态：**申请已批准、班级却还没交给辅导员**，
 * 或者**班级已经没有辅导员、申请单还挂在"已通过"**。这种半截状态不会报错，
 * 只会在下一个页面以"这个人怎么还能管理这个班"的形式浮现出来。
 *
 * <p>所以把写操作下沉到这里，让一个请求 = 一个事务。**只搬写操作，不搬判断逻辑**：
 * 业务拒绝（"班级不存在""您不是该班级的管理者"…）仍然以**返回值**表示，由 controller
 * 原样包成原来的 {@code ApiResponse.error(...)}，前端口径一字不变；DB 异常则抛出去
 * 让事务拦截器回滚，controller 的 {@code catch} 在事务边界**之外**，照常把
 * "…失败: " + message 返回给前端。
 *
 * <p>这次没有用到 service 内部的 try/catch —— 若在同一个方法里把异常吞掉，
 * {@code @Transactional} 会以为一切正常照常提交，那就是个假事务。
 */
@Slf4j
@Service
public class ClassManagementService {

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private CounselorClassApplicationMapper counselorClassApplicationMapper;

    @Autowired
    private CounselorService counselorService;

    /**
     * 辅导员提交班级管理申请（申请管理 applicationType=0 / 解除管理 =1）。
     *
     * @return null 表示成功；否则是给前端看的业务提示
     */
    @Transactional(rollbackFor = Exception.class)
    public String applyForClass(Long counselorId, Long classId, String className, String reason,
                                Integer applicationType) {
        // 获取辅导员姓名（注意 getByUserId 在记录缺失时**会顺手插一行** placeholder，
        // 所以它也算这条路径写的一张表，必须在事务里）
        CounselorProfile counselor = counselorService.getByUserId(counselorId);
        String counselorName = counselor != null ? counselor.getName() : "";

        ClassInfo classInfo = classInfoMapper.selectById(classId);

        if (applicationType == 0) {
            // 1. 检查班级是否已有辅导员
            if (classInfo != null && classInfo.getCounselorId() != null && classInfo.getCounselorId() > 0) {
                return "该班级已有辅导员管理，无法重复申请";
            }

            // 2. 检查是否已有待审核的同类申请（仅阻塞 status=0）
            List<CounselorClassApplication> existingApplications = counselorClassApplicationMapper.selectList(
                    new QueryWrapper<CounselorClassApplication>()
                            .eq("counselor_id", counselorId)
                            .eq("class_id", classId)
                            .eq("status", 0));
            if (existingApplications != null && !existingApplications.isEmpty()) {
                for (CounselorClassApplication app : existingApplications) {
                    if (app.getApplicationType() == null || app.getApplicationType() == 0) {
                        return "您已有一条该班级的待审核申请，请等待管理员处理";
                    }
                }
            }
        } else {
            // 解除管理：班级必须存在且该辅导员是当前管理者
            if (classInfo == null || classInfo.getCounselorId() == null
                    || !classInfo.getCounselorId().equals(counselorId)) {
                return "您不是该班级的辅导员，无法提交解除申请";
            }
        }

        CounselorClassApplication application = new CounselorClassApplication();
        application.setCounselorId(counselorId);
        application.setClassId(classId);
        application.setClassName(className);
        application.setReason(reason);
        application.setStatus(0); // 0-待审核
        application.setCounselorName(counselorName);
        application.setApplicationType(applicationType);

        counselorClassApplicationMapper.insert(application);

        String action = applicationType == 1 ? "解除管理" : "申请管理";
        log.info("辅导员 {}({}) {}班级 {} ({})，理由: {}", counselorId, counselorName, action, classId, className, reason);
        return null;
    }

    /**
     * 解除班级与辅导员的关联。
     *
     * @return null 表示成功；否则是给前端看的业务提示
     */
    @Transactional(rollbackFor = Exception.class)
    public String cancelClassManagement(Long userId, Long classId) {
        // 根据用户ID查询辅导员Profile（同 applyForClass：缺失时会插一行 placeholder）
        counselorService.getByUserId(userId);

        // 注意：classes.counselor_id 存储的是 User.id，不是 CounselorProfile.id，
        // 所以后面一律直接用传入的 userId。
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            return "班级不存在";
        }

        // 班级已经没有辅导员，无需解除（不写库，无所谓回滚）
        if (classInfo.getCounselorId() == null) {
            log.info("用户ID {} 尝试解除班级 {} 的管理权限，班级counselorId已为null，无需解除", userId, classId);
            return null;
        }

        if (!classInfo.getCounselorId().equals(userId)) {
            log.warn("用户ID {} 尝试解除班级 {} 的管理权限，但班级的counselorId是 {}",
                    userId, classId, classInfo.getCounselorId());
            return "您不是该班级的管理者";
        }

        // 1. 解除班级与辅导员的关联（UpdateWrapper 保证 counselor_id 被置为 null）
        UpdateWrapper<ClassInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", classId).set("counselor_id", null);
        classInfoMapper.update(null, updateWrapper);

        // 2. 相关申请记录置为"已取消"
        List<CounselorClassApplication> applications = counselorClassApplicationMapper.selectList(
                new QueryWrapper<CounselorClassApplication>()
                        .eq("counselor_id", userId)
                        .eq("class_id", classId));
        if (applications != null && !applications.isEmpty()) {
            for (CounselorClassApplication application : applications) {
                application.setStatus(3); // 3-已取消
                counselorClassApplicationMapper.updateById(application);
            }
        }

        log.info("辅导员用户ID {} 解除班级 {} 的管理权限", userId, classId);
        return null;
    }

    /**
     * 管理员审核辅导员班级申请。
     *
     * @param status 1-批准，其余-拒绝
     * @return null 表示成功；否则是给前端看的业务提示
     */
    // ⚠️ 这个重载**自己也要带 @Transactional**：它转调的是同一个 bean 里的方法，
    //    Spring 的**自调用不会过代理** ⇒ 下面那个重载上的注解在这条路径上不生效。
    //    只留下面那一份的话，从这条路径进来的调用会变成**假事务**（本工程踩过）。
    //    两个都是 REQUIRED 传播，嵌套时内层加入外层，不会开两个事务。
    @Transactional(rollbackFor = Exception.class)
    public String reviewApplication(Long id, Integer status) {
        return reviewApplication(id, status, null);
    }

    /**
     * 审核辅导员班级申请。
     *
     * <p>2026-10-02（W1.14）：加了 {@code rejectReason} —— 这条路径**与管理端的
     * `POST /admin/class-management/reject/{id}?type=counselor` 是同一个业务动作的两条实现**，
     * 拒绝理由必须两边都存，否则"从哪个入口拒的"决定了理由留不留得住。
     *
     * <p>理由只在**拒绝**（{@code status=2}）时写入；批准时**显式置 null**，
     * 免得一条曾经被拒、后来又被批的申请还挂着上次的理由（前端是按 status 显示它的，
     * 但库里留着一个与 status 矛盾的值迟早会被别处读到）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String reviewApplication(Long id, Integer status, String rejectReason) {
        CounselorClassApplication application = counselorClassApplicationMapper.selectById(id);
        if (application == null) {
            return "申请不存在";
        }

        // 1. 更新申请状态（顺带把拒绝理由一并落库）
        //
        // ⚠️ 这里**必须**用 UpdateWrapper 显式 `.set("reject_reason", …)`，不能靠
        //    `updateById(entity)`：MyBatis-Plus 默认的更新策略是 NOT_NULL，**值为 null 的
        //    字段根本不会出现在 UPDATE 语句里** ⇒ "批准时把理由清掉"这句会变成一句空话，
        //    一条曾经被拒、后来又被批的申请会一直挂着上次的拒绝理由。
        //    （本工程没有全局改过 update-strategy，各服务 application.yml 里只有
        //    `map-underscore-to-camel-case: true`，所以默认策略是生效的。）
        String normalizedReason = null;
        if (status != null && status == 2) {
            normalizedReason = (rejectReason == null || rejectReason.trim().isEmpty())
                    ? null : rejectReason.trim();
        }
        UpdateWrapper<CounselorClassApplication> statusUpdate = new UpdateWrapper<>();
        statusUpdate.eq("id", id)
                .set("status", status)
                .set("reject_reason", normalizedReason);
        counselorClassApplicationMapper.update(null, statusUpdate);

        // 2. 批准时，按申请类型把班级的辅导员改成/清空
        if (status == 1) {
            ClassInfo classInfo = classInfoMapper.selectById(application.getClassId());
            if (classInfo != null) {
                UpdateWrapper<ClassInfo> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", application.getClassId());

                if (application.getApplicationType() == null || application.getApplicationType() == 0) {
                    updateWrapper.set("counselor_id", application.getCounselorId());
                    classInfoMapper.update(null, updateWrapper);
                    log.info("班级 {} 已分配给辅导员 {}", application.getClassId(), application.getCounselorId());
                } else {
                    updateWrapper.set("counselor_id", null);
                    classInfoMapper.update(null, updateWrapper);
                    log.info("班级 {} 的辅导员已移除", application.getClassId());
                }
            }
        }

        log.info("管理员审核辅导员班级申请: id={}, status={}, applicationType={}",
                id, status, application.getApplicationType());
        return null;
    }
}
