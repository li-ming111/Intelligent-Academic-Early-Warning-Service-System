package com.academic.service;

import com.academic.common.entity.Score;
import com.academic.common.entity.ScoreRecord;
import com.academic.common.util.ScoreSyncSupport;
import com.academic.mapper.ScoreRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 教师端成绩服务。
 *
 * <p><b>2026-10-10（W36）：全面改用权威表 {@code scores}。</b>
 *
 * <p>此前本服务把 {@code score_record}（教师录入暂存表）当作教师端的成绩源，而
 * 辅导员 / 学生 / 预警读的是 {@code scores}（权威表，见 {@link com.academic.common.constant.ScoreCaliber}）。
 * 两张表会分叉 —— 实测学生司明：暂存表 16 行、权威表 29 行，挂科门数一个 4 一个 3，
 * 于是教师成绩页与预警信息在同一屏上互相打架。
 *
 * <p>现在读走 {@code ScoreRecordMapper} 里基于 {@link com.academic.common.constant.ScoreCaliber#LATEST_VALID_SCORE}
 * 的权威投影（每门课每生取最新一次有效成绩），写直落 {@code scores}。
 * {@code score_record} 不再是教师端的数据源。方法签名与返回类型（{@link ScoreRecord}）
 * 保持原样，控制器消费面因此不用改。
 */
@Slf4j
@Service
public class ScoreRecordService {
    @Autowired
    private ScoreRecordMapper scoreRecordMapper;
    @Autowired
    private com.academic.mapper.ScoreMapper scoreMapper;
    @Autowired
    private ScoreAuditLogService auditLogService;

    // ============================== 读（scores） ==============================

    public List<ScoreRecord> getScoresByCourseId(Long courseId) {
        return scoreRecordMapper.listLatestValidByCourse(courseId);
    }

    /**
     * 某门课 + 某教师的成绩。
     *
     * <p>W36：{@code scores} 没有 {@code teacher_id}，课程的授课教师就是 {@code courses.teacher_id} ——
     * 一门课的归属由课程本身决定，所以这里与 {@link #getScoresByCourseId} 同源；
     * 传入 {@code teacherId} 只为兼容既有签名与调用方（归属闸在控制器按课程把关）。
     */
    public List<ScoreRecord> getScoresByCourseIdAndTeacherId(Long courseId, Long teacherId) {
        return scoreRecordMapper.listLatestValidByCourse(courseId);
    }

    public List<ScoreRecord> getScoresByClassName(String className) {
        return scoreRecordMapper.listLatestValidByClassName(className);
    }

    /** 按班级名 + 教师取成绩（归属由调用方的班级归属闸把关，这里与按名取同源）。 */
    public List<ScoreRecord> getScoresByClassNameAndTeacherId(String className, Long teacherId) {
        return scoreRecordMapper.listLatestValidByClassName(className);
    }

    public List<Map<String, Object>> getScoresByClassId(Long classId) {
        return scoreRecordMapper.getScoresByClassId(classId);
    }

    /** 按某门课导出成绩单（每生一行，最新一次有效成绩）。 */
    public List<ScoreRecord> exportScores(Long courseId) {
        return scoreRecordMapper.listLatestValidByCourse(courseId);
    }

    /** 按 {@code scores.id} 取一行（编辑 / 删除定位用，含待录入行与 course_id）。 */
    public ScoreRecord getById(Long id) {
        return scoreRecordMapper.selectScoreById(id);
    }

    // ============================== 写（scores） ==============================

    /**
     * 保存一批成绩，**直写权威表 {@code scores}**。
     *
     * <p>W36：此前写 {@code score_record} 再 {@code syncToScores} 二跳进 {@code scores}；
     * 现在一步到位，两张表不可能再分叉。{@code POST /teachers/scores} 与
     * {@code POST /teachers/scores/import} 共用本方法（导入端在控制器里组装行）。
     *
     * <p>查重键 {@code (student, course, semester)}：显式给学期时按三元组查（重修是同一门课
     * 另一个学期，应新增一行）；没给学期则退到该生已有的最大学期序号（保证新记录不比历史更旧，
     * 否则在"每门课取最新一次"的口径下会被直接丢弃）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveScores(List<Map<String, Object>> scores, Long teacherId) {
        for (Map<String, Object> scoreData : scores) {
            Long studentId = Long.parseLong(scoreData.get("studentId").toString());
            Long courseId = Long.parseLong(scoreData.get("courseId").toString());
            double score = Double.parseDouble(scoreData.get("score").toString());

            String requested = trimSemester(scoreData.get("semester"));
            // 未指定学期时退到该生已有的最大学期序号（scores 口径）—— 保证新记录不比历史更旧，
            // 否则在"每门课取最新一次"的口径下会被直接丢弃。
            String semester = requested;
            if (semester == null) {
                Integer maxExisting = scoreMapper.selectMaxSemester(studentId);
                semester = String.valueOf(maxExisting != null ? maxExisting : 1);
            }

            // 按 (学生, 课程, 学期) 三元组查重：重修是同一门课另一个学期，应新增一行。
            Score existing = scoreMapper.selectByStudentCourseSemester(studentId, courseId, semester);
            boolean isNew = existing == null;
            Score target = isNew ? new Score() : existing;
            target.setStudentId(studentId);
            target.setCourseId(courseId);
            target.setSemester(semester);
            // 折算（总评→等级/绩点），并镜像 final_score —— 与 admin 导入共用同一份实现。
            Double regularScore = toDoubleOrNull(scoreData.get("regularScore"));
            Double finalScore = toDoubleOrNull(scoreData.get("finalScore"));
            ScoreSyncSupport.applyToAuthoritative(target, score, regularScore, finalScore);
            Object remark = scoreData.get("remark");
            if (remark != null) {
                target.setRemark(String.valueOf(remark));
            }
            target.setYear(resolveYear(scoreData.get("year"), isNew ? null : target.getYear(), studentId, semester));
            if (teacherId != null) {
                target.setModifiedBy(teacherId);
            }

            if (isNew) {
                target.setCreatedAt(LocalDateTime.now());
                scoreMapper.insert(target);
            } else {
                scoreMapper.updateById(target);
            }
        }
    }

    /**
     * 改分：更新 {@code scores} 一行，重算等级/绩点，写审计日志。
     *
     * <p>W36：{@code scoreId} 的语义由 {@code score_record.id} 改为 {@code scores.id}。
     * 归属判定（这一行是不是本人所授课程）由控制器负责 —— 它在调用前会按
     * {@code course_id → courses.teacher_id} 把关。
     *
     * <p>只改总评与派生的等级/绩点，并保留该行原有的平时分/期末分：加载整行后原地改再
     * {@code updateById}（MyBatis-Plus 默认只写非 null 字段），于是
     * "已有期末分不被抹掉"与"待录入行（final 为 NULL）改分后 final 被补齐"两件事同时成立
     * —— 后者很重要，否则改完分的行仍会被"期末未录入"口径排除。
     *
     * <p>审计 {@code score_audit_log.teacher_id} 是 NOT NULL：取该成绩所属课程的授课教师
     * （{@code courses.teacher_id}）—— 这就是"这门课是谁的"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateScore(Long scoreId, Double score, String remark, String modifiedBy) {
        Score existing = scoreMapper.selectById(scoreId);
        if (existing == null) {
            // 目标行不存在时"改分成功"是假象，不改任何东西、也不留半条审计。
            throw new IllegalArgumentException("成绩记录不存在: id=" + scoreId);
        }
        Double oldScore = existing.getScoreTotal() == null ? null : existing.getScoreTotal().doubleValue();

        // 总评 → 等级/绩点；final 缺失时用总评补齐（与录入路径同口径）。
        ScoreSyncSupport.applyToAuthoritative(existing, score, null, null);
        if (remark != null) {
            existing.setRemark(remark);
        }
        scoreMapper.updateById(existing);

        // 审计：old_score/student_id/teacher_id 都不能空。
        if (oldScore != null) {
            ScoreRecord auditNew = new ScoreRecord();
            auditNew.setStudentId(existing.getStudentId());
            auditNew.setStudentName(scoreRecordMapper.selectStudentName(existing.getStudentId()));
            auditNew.setCourseId(existing.getCourseId());
            auditNew.setTeacherId(scoreRecordMapper.selectCourseTeacherId(existing.getCourseId()));
            auditNew.setScore(score);
            auditLogService.record(buildAuditOld(existing, oldScore), auditNew, remark, modifiedBy);
        }
    }

    /** 审计的"旧行"只需旧分数（{@code ScoreAuditLogService.record} 只读 oldRecord.getScore()）。 */
    private static ScoreRecord buildAuditOld(Score existing, Double oldScore) {
        ScoreRecord old = new ScoreRecord();
        old.setStudentId(existing.getStudentId());
        old.setCourseId(existing.getCourseId());
        old.setScore(oldScore);
        return old;
    }

    /**
     * 删除 {@code scores} 一行。
     *
     * <p>W36：此前删 {@code score_record} 一行、再连带删它在 {@code scores} 里的权威行；
     * 现在只有一张表，直接删。返回影响行数，0 行 = 该 id 不存在（调用方据此回 404）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeById(Long id) {
        return scoreMapper.deleteById(id);
    }

    /** 批量删除 {@code scores}。返回影响行数。 */
    @Transactional(rollbackFor = Exception.class)
    public int removeByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (Long id : ids) {
            affected += scoreMapper.deleteById(id);
        }
        return affected;
    }

    // ============================== 内部工具 ==============================

    /** 学期取值统一成去空白的字符串；空串按未提供处理。 */
    private static String trimSemester(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Double toDoubleOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析学年：{@code 学年 = 入学年 + (学期序号-1) DIV 2}，入学年取学号前四位。
     * 推不出来（查不到学号、前缀不是数字、学期不是数字）时依次退到该行原有值、再退到 {@code null}；
     * **不编造**。
     */
    private Integer resolveYear(Object provided, Integer existing, Long studentId, String semester) {
        if (provided != null) {
            try {
                return Integer.parseInt(String.valueOf(provided).trim());
            } catch (NumberFormatException ignored) {
                // 落到下面的推导
            }
        }
        Integer entranceYear = entranceYearOf(studentId);
        Integer semesterNo = toInt(semester);
        if (entranceYear != null && semesterNo != null && semesterNo >= 1) {
            return entranceYear + (semesterNo - 1) / 2;
        }
        return existing;
    }

    /** 学号前四位即入学年；学生查不到或学号前缀不是四位数字时返回 null。 */
    private Integer entranceYearOf(Long studentProfileId) {
        if (studentProfileId == null) {
            return null;
        }
        String studentNo = scoreRecordMapper.selectStudentNo(studentProfileId);
        if (studentNo == null || studentNo.length() < 4) {
            return null;
        }
        Integer year = toInt(studentNo.substring(0, 4));
        return (year != null && year >= 1900 && year <= 2100) ? year : null;
    }

    private static Integer toInt(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
