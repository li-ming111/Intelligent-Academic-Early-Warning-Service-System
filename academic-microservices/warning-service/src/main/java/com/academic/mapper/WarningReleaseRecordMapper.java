package com.academic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 预警解除记录 Mapper。
 *
 * <p>{@code warning_release_records} 这张表建库时就有，但<b>全仓零个 Java 引用</b> ——
 * 文档承诺"满足恢复条件的预警自动标记为已解除"，代码里却只插不更。
 * 这里补上唯一的写入方（自动解除）。
 *
 * <p>不建实体类：本表只写不读（读取方是后续的报表需求），
 * 与 {@code admin-service.ScoreAppealMapper} 同样的取舍 —— 少一个实体就少一处
 * 与库结构漂移的机会。
 *
 * <p>注意 {@code release_by} 留 NULL 表示"系统自动"：手动解除才会填操作人。
 */
@Mapper
public interface WarningReleaseRecordMapper {

    @Insert("INSERT INTO warning_release_records "
          + "  (warning_id, student_id, release_reason, release_note, release_by, release_time) "
          + "VALUES (#{warningId}, #{studentId}, #{releaseReason}, #{releaseNote}, #{releaseBy}, NOW())")
    int insertRelease(@Param("warningId") Long warningId,
                      @Param("studentId") Long studentId,
                      @Param("releaseReason") String releaseReason,
                      @Param("releaseNote") String releaseNote,
                      @Param("releaseBy") Long releaseBy);
}
