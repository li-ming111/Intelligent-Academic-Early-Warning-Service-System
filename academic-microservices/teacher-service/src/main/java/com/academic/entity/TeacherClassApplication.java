package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("teacher_class_application")
public class TeacherClassApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long teacherId;
    
    private Long classId;
    
    private String reason;
    
    private Integer status;
    
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
    
    private String className;
    
    private String teacherName;
    
    private String teacherUsername;
    
    /**
     * 申请类型：0-申请管理，1-解除管理
     */
    private Integer applicationType;

    /**
     * 拒绝原因（`status=2` 才有值）。
     *
     * <p>2026-10-02（W1.14）：列在 `teacher_class_application` 上**早就有**
     * （admin-service 的同名实体也一直有），但**没有任何写入方**，且本实体缺这一字段 ⇒
     * 教师端 `/class-management/requests` 就算读到了也序列化不出去。三者一并补齐。
     */
    private String rejectReason;
}