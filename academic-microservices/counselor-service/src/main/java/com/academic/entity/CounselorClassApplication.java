package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 辅导员班级管理申请实体类
 */
@Data
@TableName("counselor_class_application")
public class CounselorClassApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long counselorId;
    private Long classId;
    private String reason;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String className;
    private String counselorName;
    
    /**
     * 申请类型：0-申请管理，1-解除管理
     */
    private Integer applicationType;

    /**
     * 拒绝原因。管理员拒绝时填写（`status=2` 才有值）。
     *
     * <p>2026-10-02（W1.14）新增 —— 此前两张申请表**都没有**这一列，
     * 而管理员端两个前端（PC `AdminClassManagement.vue` 的拒绝对话框、小程序
     * `pages/admin/class-requests.vue`）**都收了「拒绝理由」并真的发了出去**，
     * 后端收下就丢。申请表本身是**申请人自己看的那张表**（`/class-management/requests`
     * 返回的就是它），所以这一列不加，申请人永远不知道自己为什么被拒。
     */
    private String rejectReason;
}
