package com.academic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
    private Integer applicationType;

    /**
     * 拒绝原因（`status=2` 才有值）。
     *
     * <p>2026-10-02（W1.14）：`counselor_class_application` 此前**没有这一列**
     * （同批的 `teacher_class_application` 早就有了，但也没人写），
     * 而管理员拒绝时前端**真的把理由发过来了** —— `AdminController.rejectClassRequest`
     * 的 `@RequestBody Map<String,String> rejectData` 收下后一次都没读。
     * 列与写入方、读取方是同一次改动补齐的。
     */
    private String rejectReason;
}
