package com.academic.service;

import com.academic.entity.ClassInfo;
import com.academic.mapper.ClassInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 班级服务类
 */
@Slf4j
@Service
public class ClassInfoService {

    @Autowired
    private ClassInfoMapper classInfoMapper;

    /**
     * 根据教师ID获取管理的班级
     */
    public List<Map<String, Object>> getClassesByTeacherId(Long teacherId) {
        try {
            log.info("获取教师班级, teacherId: {}", teacherId);
            List<Map<String, Object>> classes = classInfoMapper.getClassesByTeacherIdMap(teacherId);
            log.info("查询结果数量: {}", classes != null ? classes.size() : 0);
            if (classes != null) {
                for (Map<String, Object> clazz : classes) {
                    log.info("班级: id={}, name={}", clazz.get("id"), clazz.get("name"));
                }
            }
            return classes != null ? classes : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取教师班级失败, teacherId={}", teacherId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 搜索班级
     */
    public List<Map<String, Object>> searchClasses(String keyword) {
        try {
            List<Map<String, Object>> classes;
            if (keyword == null || keyword.trim().isEmpty()) {
                classes = classInfoMapper.getAllClassesMap();
                log.info("查询所有班级, 结果数量: {}", classes != null ? classes.size() : 0);
            } else {
                classes = classInfoMapper.searchClassesMap(keyword.trim());
                log.info("搜索班级 keyword={}, 结果数量: {}", keyword, classes != null ? classes.size() : 0);
            }
            // 处理 hasTeacher 字段
            if (classes != null) {
                for (Map<String, Object> clazz : classes) {
                    String className = (String) clazz.get("name");
                    Object hasTeacherObj = clazz.get("hasTeacher");
                    log.debug("班级: {}, hasTeacher原始值: {} (类型: {})", className, hasTeacherObj, 
                        hasTeacherObj != null ? hasTeacherObj.getClass().getName() : "null");
                    
                    if (hasTeacherObj instanceof Integer) {
                        clazz.put("hasTeacher", ((Integer) hasTeacherObj) == 1);
                    } else if (hasTeacherObj instanceof Long) {
                        clazz.put("hasTeacher", ((Long) hasTeacherObj) == 1);
                    } else if (hasTeacherObj instanceof Boolean) {
                        clazz.put("hasTeacher", hasTeacherObj);
                    } else {
                        clazz.put("hasTeacher", false);
                    }
                    
                    log.debug("班级: {}, hasTeacher转换后: {}", className, clazz.get("hasTeacher"));
                }
                return classes;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("搜索班级失败, keyword={}", keyword, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取班级学生列表
     */
    public List<Map<String, Object>> getStudentsByClassId(Long classId) {
        try {
            log.info("查询班级学生, classId: {}", classId);
            List<Map<String, Object>> students = classInfoMapper.getStudentsByClassIdMap(classId);
            log.info("查询结果: {}", students);
            return students != null ? students : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取班级学生失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 根据班级ID获取班级信息
     */
    public ClassInfo getClassById(Long classId) {
        try {
            return classInfoMapper.selectById(classId);
        } catch (Exception e) {
            log.error("获取班级信息失败, classId={}", classId, e);
            return null;
        }
    }

    /**
     * 根据班级名称获取辅导员信息
     */
    public Map<String, Object> getCounselorByClassName(String className) {
        try {
            return classInfoMapper.getCounselorByClassName(className);
        } catch (Exception e) {
            log.error("获取辅导员信息失败, className={}", className, e);
            return null;
        }
    }
}
