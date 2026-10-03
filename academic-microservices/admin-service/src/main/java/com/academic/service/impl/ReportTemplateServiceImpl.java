package com.academic.service.impl;

import com.academic.entity.ReportTemplate;
import com.academic.mapper.ReportTemplateMapper;
import com.academic.service.ReportTemplateService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ReportTemplateServiceImpl extends ServiceImpl<ReportTemplateMapper, ReportTemplate> implements ReportTemplateService {

    @Override
    public List<ReportTemplate> getAllTemplates() {
        return list();
    }

    @Override
    public ReportTemplate getTemplateById(Long id) {
        return getById(id);
    }

    @Override
    public ReportTemplate uploadTemplate(String name, String description, String filePath, String fileName, Long fileSize, String fileType) {
        ReportTemplate template = new ReportTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setFilePath(filePath);
        template.setFileName(fileName);
        template.setFileSize(fileSize);
        template.setFileType(fileType);
        template.setStatus(1);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        
        save(template);
        log.info("Report template uploaded: {}", name);
        return template;
    }

    @Override
    public boolean deleteTemplate(Long id) {
        ReportTemplate template = getById(id);
        if (template == null) {
            // 2026-10-03（W7）：行不存在 = 没删成，如实回 false（调用方据此回 404）。
            // 原实现在这里整个空转，调用方却照回"模板删除成功"。
            return false;
        }
        // 删除文件
        try {
            File file = new File(template.getFilePath());
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            log.warn("Failed to delete template file: {}", e.getMessage());
        }

        boolean removed = removeById(id);
        log.info("Report template deleted: {} (removed={})", template.getName(), removed);
        return removed;
    }
}