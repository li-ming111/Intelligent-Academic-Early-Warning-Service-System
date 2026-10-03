package com.academic.service;

import com.academic.entity.ReportTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ReportTemplateService extends IService<ReportTemplate> {
    
    List<ReportTemplate> getAllTemplates();
    
    ReportTemplate getTemplateById(Long id);
    
    ReportTemplate uploadTemplate(String name, String description, String filePath, String fileName, Long fileSize, String fileType);
    
    /**
     * 删除模板（连同磁盘上的文件）。
     *
     * <p>2026-10-03（W7）：返回值由 {@code void} 改为**是否真的删掉了** ——
     * 此前 {@code DELETE /admin/reports/templates/{id}} 删一个不存在的 id
     * 也回「模板删除成功」。调用方按 {@code false} 回 404。
     */
    boolean deleteTemplate(Long id);
}