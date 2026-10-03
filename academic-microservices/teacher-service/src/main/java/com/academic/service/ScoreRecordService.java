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

@Slf4j
@Service
public class ScoreRecordService {
    @Autowired
    private ScoreRecordMapper scoreRecordMapper;
    @Autowired
    private com.academic.mapper.ScoreMapper scoreMapper;
    @Autowired
    private ScoreAuditLogService auditLogService;
    
    public List<ScoreRecord> getScoresByCourseId(Long courseId) {
        return scoreRecordMapper.getScoresByCourseId(courseId);
    }
    
    public List<ScoreRecord> getScoresByCourseIdAndTeacherId(Long courseId, Long teacherId) {
        return scoreRecordMapper.getScoresByCourseIdAndTeacherId(courseId, teacherId);
    }
    
    public ScoreRecord getById(Long id) {
        return scoreRecordMapper.selectById(id);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void saveScores(List<Map<String, Object>> scores, Long teacherId) {
        for (Map<String, Object> scoreData : scores) {
            Long studentId = Long.parseLong(scoreData.get("studentId").toString());
            Long courseId = Long.parseLong(scoreData.get("courseId").toString());
            double score = Double.parseDouble(scoreData.get("score").toString());

            // 1) 先把学期/学年定下来，再决定插还是改 —— 这两列必须跟着写入语句一起落库。
            //    早先的实现是"先 insert、后 setSemester"，而 insertScore 的 SQL 里
            //    根本没有这两列，于是应用写入的行 semester/year 恒为 NULL。
            //    后果不只是显示缺失：removeById 按 semester 定位 scores 行时
            //    会拿 NULL 去比较（NULL = NULL 永不成立），定位不到就删不掉，
            //    scores 里留下孤儿行，学生的成绩还在、录入记录却没了。
            String requested = trimSemester(scoreData.get("semester"));
            ScoreRecord latest = scoreRecordMapper.selectLatestByStudentAndCourse(studentId, courseId);
            String semester = requested;
            if (semester == null && latest != null) {
                semester = trimSemester(latest.getSemester());
            }
            if (semester == null) {
                semester = resolveSemester(null, studentId);
            }

            // 2) 查重。
            //    调用方显式给了学期 → 按 (学生, 课程, 学期) 三元组查：重修是同一门课的
            //    另一个学期，应该新增一行而不是覆盖旧学期成绩。
            //    没给学期 → 退化成"该生该门课最近的一条"，沿用它的学期。
            //    不能一律按三元组查：历史数据里有 92 行 semester 为 NULL（课程 7~10
            //    在 scores 里没有对应行，学期无法从任何数据推出来），按三元组查会
            //    一条都匹配不上，每次保存都插一条新行。
            ScoreRecord existing = requested != null
                    ? scoreRecordMapper.selectByStudentCourseSemester(studentId, courseId, semester)
                    : latest;

            ScoreRecord sr;
            if (existing != null) {
                existing.setScore(score);
                existing.setRemark((String) scoreData.get("remark"));
                existing.setStudentName((String) scoreData.get("studentName"));
                existing.setClassName((String) scoreData.get("className"));
                existing.setTeacherId(teacherId);
                existing.setSemester(semester);
                existing.setYear(resolveYear(scoreData.get("year"), existing.getYear(), studentId, semester));
                applyDerived(existing, score);
                scoreRecordMapper.updateScore(existing);
                sr = existing;
            } else {
                sr = new ScoreRecord();
                sr.setCourseId(courseId);
                sr.setStudentId(studentId);
                sr.setTeacherId(teacherId);
                sr.setStudentName((String) scoreData.get("studentName"));
                sr.setClassName((String) scoreData.get("className"));
                sr.setScore(score);
                sr.setRemark((String) scoreData.get("remark"));
                sr.setSemester(semester);
                sr.setYear(resolveYear(scoreData.get("year"), null, studentId, semester));
                applyDerived(sr, score);
                scoreRecordMapper.insertScore(sr);
            }
            // 同步到 scores 表
            syncToScores(sr);
        }
    }

    /** 学期取值统一成去空白的字符串；空串按未提供处理。 */
    private static String trimSemester(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 把录入分数折算到 {@code score_record} 行的派生列上。
     *
     * <p>折算口径与 {@code scores} 完全一致（都走 {@link ScoreSyncSupport}），
     * 这样同一份成绩在暂存表和权威表里的等级/绩点不会各说各话。
     * 早期保存只写了 {@code score_level}，{@code grade}/{@code grade_point}/
     * {@code score_total} 就停在导入时的旧值或 NULL 上。
     */
    private static void applyDerived(ScoreRecord sr, double score) {
        sr.setScoreLevel(ScoreSyncSupport.scoreToLevel(score));
        sr.setGrade(ScoreSyncSupport.scoreToGrade(score));
        sr.setGradePoint(ScoreSyncSupport.scoreToGpa(score).doubleValue());
        sr.setScoreTotal(score);
    }

    /**
     * 解析学期序号。
     *
     * <p>{@code scores.semester} 是<b>全局学期序号</b>（int，例如 2~6），不是"学年内的第几学期"。
     * 旧实现按当前月份猜成 {@code "1"}/{@code "2"}，于是新录入的成绩会被写成 semester=2，
     * 比该生已有的 semester=5/6 更旧 —— 在"每门课取最新一次成绩"的口径下会被直接丢弃，
     * 新成绩永远不生效。
     *
     * <p>未指定时兜底为该学生已有的最大学期序号，保证新记录不会比历史记录更旧。
     */
    private String resolveSemester(Object provided, Long studentId) {
        if (provided != null) {
            String text = String.valueOf(provided).trim();
            if (!text.isEmpty()) return text;
        }
        Integer max = scoreMapper.selectMaxSemester(studentId);
        return String.valueOf(max != null ? max : 1);
    }

    /**
     * 解析学年。
     *
     * <p>学年不是"当前是哪一年"，而是**学期的函数**：
     * {@code 学年 = 入学年 + (学期序号-1) DIV 2}，入学年取学号前四位。
     * 这条公式就是 {@code scores.year} 的定义（已按此对齐过），
     * 所以这里必须用同一公式，两张表才不会对同一学期给出两个学年。
     *
     * <p>早期实现按"当前日期在 9 月之前还是之后"猜，于是同一个学期
     * 在不同时间录入会得到不同学年 —— 一旦跨年就自相矛盾。
     *
     * <p>推不出来（查不到学号、学号前缀不是数字、学期不是数字）时依次退到
     * 该行原有值、再退到 {@code null}；**不编造**。
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

    /**
     * 将 ScoreRecord 同步到 scores 表。
     *
     * <p>查重键是 (student_id, course_id, semester) 三元组。同时只覆盖调用方
     * 明确提供的字段 —— 早期实现会用 null 覆盖 regular_score/final_score，
     * 而 final_score 一旦为空，该行就会被"期末未录入"口径整体排除，
     * 学生的挂科会从预警里静默消失。
     */
    private void syncToScores(ScoreRecord sr) {
        if (sr.getStudentId() == null || sr.getCourseId() == null) return;
        String semester = sr.getSemester() != null ? sr.getSemester() : resolveSemester(null, sr.getStudentId());
        Score existingScore = scoreMapper.selectByStudentCourseSemester(sr.getStudentId(), sr.getCourseId(), semester);

        boolean isNew = existingScore == null;
        Score score = isNew ? new Score() : existingScore;
        score.setStudentId(sr.getStudentId());
        score.setCourseId(sr.getCourseId());
        score.setSemester(semester);
        // 折算逻辑与 admin-service 的导入路径共用同一份实现（ScoreSyncSupport），
        // 免得两处各写一份、各自漂移。
        ScoreSyncSupport.applyToAuthoritative(score, sr.getScore(), sr.getRegularScore(), sr.getFinalScore());
        if (sr.getYear() != null) {
            score.setYear(sr.getYear());
        }
        if (sr.getModifiedBy() != null) {
            score.setModifiedBy(sr.getModifiedBy());
        }

        if (isNew) {
            score.setCreatedAt(LocalDateTime.now());
            scoreMapper.insert(score);
        } else {
            scoreMapper.updateById(score);
        }
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void updateScore(Long scoreId, Double score, String remark, String modifiedBy) {
        // 读取旧记录
        ScoreRecord oldRecord = scoreRecordMapper.selectById(scoreId);

        if (oldRecord == null) {
            // 不改任何东西，也不留半条审计：目标行不存在时"改分成功"是假象。
            throw new IllegalArgumentException("成绩记录不存在: id=" + scoreId);
        }

        // 更新成绩。
        // 这里是个只含分数和评语的裸对象，mapper 的 <if> 守空会放行
        // regular_score/final_score 不动（这正是要的），但 grade/grade_point/
        // score_total 必须跟着新分数一起重算，否则改完分暂存行还挂着旧等级，
        // 又变成"数字与文字对不上"。applyDerived 只碰这四个派生列。
        ScoreRecord scoreRecord = new ScoreRecord();
        scoreRecord.setId(scoreId);
        scoreRecord.setScore(score);
        scoreRecord.setRemark(remark);
        applyDerived(scoreRecord, score);
        scoreRecordMapper.updateScore(scoreRecord);

        // 写入审计日志。
        // 必须另起一个**带归属信息**的对象喂给 record()：上面那个裸对象只有
        // 分数和评语，而 score_audit_log 的 student_id / teacher_id 都是 NOT NULL。
        // 早先把裸对象直接传进去，插入违反非空约束 → 整个 @Transactional 回滚，
        // 于是"改分"接口表面上 500、实际上一分都没改（连 UPDATE 都白做）。
        // 归属信息从旧行复制即可，既准确又不必回查。
        if (oldRecord.getScore() != null) {
            ScoreRecord auditNew = new ScoreRecord();
            auditNew.setStudentId(oldRecord.getStudentId());
            auditNew.setStudentName(oldRecord.getStudentName());
            auditNew.setCourseId(oldRecord.getCourseId());
            auditNew.setTeacherId(oldRecord.getTeacherId());
            auditNew.setScore(score);
            auditLogService.record(oldRecord, auditNew, remark, modifiedBy);
        }

        // 同步到 scores 表。
        // 必须带上 oldRecord 的 studentId/courseId/semester：只传分数会让 syncToScores
        // 定位不到那一行，退化成按"最大学期"猜测；而学期一旦猜错，
        // 改分就会写到错误的成绩行上。
        if (oldRecord != null) {
            ScoreRecord sr = new ScoreRecord();
            sr.setId(scoreId);
            sr.setStudentId(oldRecord.getStudentId());
            sr.setCourseId(oldRecord.getCourseId());
            sr.setSemester(oldRecord.getSemester());
            sr.setYear(oldRecord.getYear());
            sr.setScore(score);
            sr.setRemark(remark);
            syncToScores(sr);
        }
    }

    public List<ScoreRecord> exportScores(Long courseId) {
        return scoreRecordMapper.getScoresByCourseId(courseId);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public int removeById(Long id) {
        deleteWithAuthoritative(scoreRecordMapper.selectById(id));
        // 2026-10-03（W7）：返回影响行数，0 行 = 该 id 不存在（调用方据此回 404）
        return scoreRecordMapper.deleteById(id);
    }

    /**
     * 删除录入记录时，连带删掉它在 {@code scores} 里的权威行。
     *
     * <p>必须带上学期：否则删掉某学期的录入会把该生这门课在 scores 里的
     * 权威成绩行一并删掉（甚至删错学期），挂科记录随之消失。
     *
     * <p>学期为空时**不删**并记一条 warn：拿 NULL 去比较永远匹配不到行，
     * 盲目按 (学生, 课程) 删则可能删掉另一个学期的权威成绩 —— 两者都会
     * 静默弄坏数据，宁可留下孤儿行并让日志说得清楚。
     */
    private void deleteWithAuthoritative(ScoreRecord sr) {
        if (sr == null || sr.getStudentId() == null || sr.getCourseId() == null) {
            return;
        }
        if (sr.getSemester() == null || sr.getSemester().trim().isEmpty()) {
            log.warn("删除录入记录 id={} 时其 semester 为空，无法定位 scores 的权威行，"
                   + "该行未删除（scores 里可能留下孤儿行）：studentId={}, courseId={}",
                    sr.getId(), sr.getStudentId(), sr.getCourseId());
            return;
        }
        Score existing = scoreMapper.selectByStudentCourseSemester(
                sr.getStudentId(), sr.getCourseId(), sr.getSemester());
        if (existing != null) {
            scoreMapper.deleteById(existing.getId());
        }
    }

    /**
     * 批量删除。必须逐条走 {@link #removeById} 那套连带删除 ——
     * 早先的实现只删 {@code score_record}，{@code scores} 里的权威行全部留下，
     * 于是"录入记录删了、学生成绩单上还在"，两边对不上。
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        for (Long id : ids) {
            deleteWithAuthoritative(scoreRecordMapper.selectById(id));
        }
        // 2026-10-03（W7）：返回影响行数，0 行 = 这些 id 一个都不存在（调用方据此回 404）
        return scoreRecordMapper.deleteByIds(ids);
    }
    
    public List<ScoreRecord> getScoresByClassName(String className) {
        return scoreRecordMapper.getScoresByClassName(className);
    }
    
    public List<ScoreRecord> getScoresByClassNameAndTeacherId(String className, Long teacherId) {
        return scoreRecordMapper.getScoresByClassNameAndTeacherId(className, teacherId);
    }
    
    public List<ScoreRecord> getScoresByTeacherId(Long teacherId) {
        return scoreRecordMapper.getScoresByTeacherId(teacherId);
    }
    
    public List<ScoreRecord> getScoresByStudentId(Long studentId) {
        return scoreRecordMapper.getScoresByStudentId(String.valueOf(studentId));
    }

    public List<Map<String, Object>> getScoresByClassId(Long classId) {
        return scoreRecordMapper.getScoresByClassId(classId);
    }
}