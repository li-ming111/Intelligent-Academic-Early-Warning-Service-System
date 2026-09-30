-- ========================================
-- 数据自洽修复：让所有"派生字段"等于系统自己算出来的值
-- 执行日期：2026-09-16
-- 目标库：academic_warning_system
--
-- 背景：库里的成绩是演示用的合成数据（这一点本身没问题）。问题在于
-- **派生字段不是按系统自己的公式算的**，于是"这个数怎么来的"答不上来：
--   * scores.grade_point 里存在 1.30、2.00 这类 ScoreSyncSupport.scoreToGpa()
--     永远算不出来的值，而 GPA 恰恰就是用 grade_point 算的；
--   * scores.grade 混用了 5 种词汇表；
--   * scores.year 与 scores.semester 互相矛盾；
--   * benchmark_analysis 27 行是写死常量的假快照，required_credits=20 与代码的
--     ScoreCaliber.GRADUATION_CREDITS=120 直接打架。
--
-- 原则：只做**纯函数派生**的修正 —— 每一处新值都由 score_total 或学号唯一确定，
-- 不含任何猜测。凡是推导不出来的（score_record.semester）一律不动，只记录在案。
--
-- 可回滚：全程在事务里；benchmark_analysis 的删除先备份成 _bak_ 表；
-- 执行前的完整快照见 academic_warning_system_pre_consistency_20260916.sql。
-- ========================================

-- 0) 先备份要删除的假快照。IF NOT EXISTS 保证重复执行不会覆盖第一次的备份。
CREATE TABLE IF NOT EXISTS benchmark_analysis_bak_20260916 AS SELECT * FROM benchmark_analysis;

START TRANSACTION;

-- 1) scores.grade ← ScoreSyncSupport.scoreToGrade(score_total)
--    A/A-/B/C/D 五档，与 teacher-service / admin-service 写新成绩用的是同一个函数。
--    期末未录入的行（final_score IS NULL）保持 NULL —— 没录入就没有等级，
--    这是正确的自洽状态，不能拿 score_total 里那个"平时分折半"的中间值去评等级。
UPDATE scores SET grade = CASE
        WHEN score_total >= 90 THEN 'A'
        WHEN score_total >= 80 THEN 'A-'
        WHEN score_total >= 70 THEN 'B'
        WHEN score_total >= 60 THEN 'C'
        ELSE 'D' END
WHERE final_score IS NOT NULL;

-- 2) scores.grade_point ← ScoreSyncSupport.scoreToGpa(score_total)
--    ⚠️ 会改变所有 GPA / 班级均分 / 排名：GPA = Σ(grade_point×学分)/Σ学分，
--    而改之前库里的 grade_point 不是按这个公式算的。改完之后 GPA 可用一条 SQL 复现。
UPDATE scores SET grade_point = CASE
        WHEN score_total >= 90 THEN 4.0
        WHEN score_total >= 85 THEN 3.7
        WHEN score_total >= 82 THEN 3.3
        WHEN score_total >= 78 THEN 3.0
        WHEN score_total >= 75 THEN 2.7
        WHEN score_total >= 72 THEN 2.3
        WHEN score_total >= 68 THEN 2.0
        WHEN score_total >= 64 THEN 1.5
        WHEN score_total >= 60 THEN 1.0
        ELSE 0.0 END
WHERE final_score IS NOT NULL;

-- 3) scores.year ← 入学年 + (semester-1) DIV 2
--    入学年取该生**自己的**学号前四位（库里有 2022 级 1 人、2023 级 23 人两个年级，
--    不能按全局常量算）。与 StudentServiceImpl.entranceYear/semesterLabel 同一换算：
--      idx → (入学年+(idx-1)/2)-(+1)-( (idx-1)%2+1 )
--    注意 scores.semester 是**全局学期序号**，不是"学年内的第几学期"。
UPDATE scores s
JOIN student_profile sp ON sp.id = s.student_id
SET s.year = CAST(LEFT(sp.student_id, 4) AS UNSIGNED) + (s.semester - 1) DIV 2
WHERE sp.student_id REGEXP '^[0-9]{4}';

-- 4) student_profile.enrollment_year ← 学号前四位
--    只补 NULL，不覆盖已有值（若将来有人工修正过的数据，不该被这里抹掉）。
UPDATE student_profile
SET enrollment_year = CAST(LEFT(student_id, 4) AS UNSIGNED)
WHERE enrollment_year IS NULL AND student_id REGEXP '^[0-9]{4}';

-- 5) 删除 benchmark_analysis 的 27 行假快照
--    该表没有任何代码写入，读取路径也已在 StudentServiceImpl.getLatestBenchmark /
--    getSemesterBenchmark 里去掉（改为从 scores 实时算 + 常量 GRADUATION_CREDITS）。
--    表结构与实体会保留，只清数据；原数据在 benchmark_analysis_bak_20260916 里。
DELETE FROM benchmark_analysis;

-- 6) score_record.score_level ← ScoreSyncSupport.scoreToLevel(score)
--    修正与公式不符的行（含 5 行非法的 0）。
--
--    【本脚本不处理的一项】score_record.semester：92 行 NULL + 275 行是旧
--    getDefaultSemester() 的默认值 '2'。曾尝试从 scores 回填，结论是**不可行**：
--    score_record 是 24 人 × 16 门 = 384 行，而 scores 只有 146 个 (学生,课程) 组合，
--    绝大多数 score_record 行在 scores 里没有对应记录，没有任何可靠依据可推导。
--    处置：不动。score_record 定位为教师录入暂存表，权威成绩源是 scores；
--    新数据会在 ScoreRecordMapper.insertScore 补上 semester/year 列之后自然正确。
UPDATE score_record SET score_level = CASE
        WHEN score >= 90 THEN 1
        WHEN score >= 80 THEN 2
        WHEN score >= 70 THEN 3
        WHEN score >= 60 THEN 4
        ELSE 5 END
WHERE score IS NOT NULL;

COMMIT;

-- ========================================
-- 执行后校验（应全部返回 0，或返回预期的基线）
--
-- (a) 派生字段是否已与公式一致 —— 应为 0 行：
--   SELECT COUNT(*) FROM scores WHERE final_score IS NOT NULL AND (
--        grade <> CASE WHEN score_total>=90 THEN 'A' WHEN score_total>=80 THEN 'A-'
--                      WHEN score_total>=70 THEN 'B' WHEN score_total>=60 THEN 'C' ELSE 'D' END
--     OR grade_point <> CASE WHEN score_total>=90 THEN 4.0 WHEN score_total>=85 THEN 3.7
--                      WHEN score_total>=82 THEN 3.3 WHEN score_total>=78 THEN 3.0
--                      WHEN score_total>=75 THEN 2.7 WHEN score_total>=72 THEN 2.3
--                      WHEN score_total>=68 THEN 2.0 WHEN score_total>=64 THEN 1.5
--                      WHEN score_total>=60 THEN 1.0 ELSE 0.0 END);
--
-- (b) year 是否自洽 —— 应为 0 行：
--   SELECT COUNT(*) FROM scores s JOIN student_profile sp ON sp.id=s.student_id
--    WHERE s.year <> CAST(LEFT(sp.student_id,4) AS UNSIGNED) + (s.semester-1) DIV 2;
--
-- (c) 预警口径基线**不应改变**（挂科门数只依赖 score_total < 60，与本次改动无关）：
--   司明(3)=3、韩坤(13)=2、李爽(20)=3、张健(26)=3
-- ========================================
