package com.academic.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学术预警实体类
 */
@Data
@Accessors(chain = true)
@TableName("academic_warning")
public class AcademicWarning {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;
    
    private String studentName;
    
    private Long ruleId;
    
    // collegeId 在 academic_warning 表中不存在（仅用于 service 层临时传递）
    @TableField(exist = false)
    private Long collegeId;
    
    private Integer type;
    
    private String title;
    
    private String description;
    
    @TableField("warning_level")
    private Integer warningLevel;
    
    private String warningMessage;
    
    private BigDecimal triggerScore;

    /**
     * 生成这条预警时，该学生的**挂科门数**（`ScoreCaliber.FAIL_COUNT_BY_STUDENT` 的口径）。
     *
     * <p>2026-09-30 新增，是判重口径的一部分：预警扫描按「学生 + 挂科门数」判重
     * （见 {@code WarningScheduledTask.runScan}），所以这一列必须落库，
     * 否则"处理掉一条 → 下一轮又冒一条"的重复就消不掉。
     *
     * <p>可空：加列之前生成的历史行没有这个值（迁移脚本按行内正文里的
     * "已累计N门课程不及格"回填，回填不到的保持 NULL）。
     */
    private Integer failCount;
    
    private BigDecimal gpaBefore;
    
    private BigDecimal gpaAfter;
    
    private Integer status;
    
    private String appealStatus;
    
    private String handledBy;
    
    private String handleResult;
    
    private LocalDateTime handledAt;
    
    private String createdBy;
    
    private LocalDateTime createdAt;
    
    private String updatedBy;
    
    private LocalDateTime updatedAt;
}