-- ============================================================================
-- 批次 10：修掉体检扫出的"结构性/引用类"不一致（答案唯一的那一批）
--
--   A1  score_audit_log 说"司明 高等数学 55 -> 65 改过分"，但 scores 里既没有 55
--       也没有 65。更严重：真改成 65 他这科就不挂科了，挂科从 3 门变 2 门、
--       预警从"中级"掉到"低级" —— 这条更正记录直接否掉了预警结论。
--       修法：让更正记录与权威成绩表一致（55 -> 56.5），而不是反过来。
--       改后算术自洽：平时 55→58（+3，漏计），总评 55→56.5（+1.5）＝ 差 3 的一半，
--       与 scores 里那行 (58 平时, 55 期末, 56.5 总评) 逐字吻合。
--       scores 的 reason_for_change/modified_by 保持 NULL：代码从不写这两列
--       （updateScore 只写 score_record + score_audit_log），人工填反而失真。
--
--   A4  courses.teacher_id 6/7/8 指向不存在的 users.id=23（courses.teacher_id 没有外键，
--       所以一直没被发现），而 7 门有值的课与 teacher_course 表逐行吻合：
--       于凤海(29)→1,4,10；王老师(19)→2；李老师(22)→3,5；赵振江(28)→9。
--       另两处说法不一致：score_record.teacher_id 对 1~6 课是 22/23/28 混填（录入噪声），
--       course_plans.teacher_id 10 行全是 29（等于没分课）。
--       修法：6 操作系统→刘教授(18)、7 人工智能导论→赵振江(28)、8 机器学习→赵振江(28)
--       （7/8/9 三门 AI 选修归同一教师）；然后三表统一到 courses.teacher_id。
--
--   A5  classes.major_id 与班名系统性错位：计科2306班挂软件工程、软工2306班挂计科…
--       8 个班全错。且 24 名学生的 major_id 都是计算机科学与技术，唯独班里写着软件工程。
--       人工智能两个班该挂人工智能(专业8)，而人工智能属电子工程学院，故班级学院一并归位。
--
--   A12 majors 里 3/4/5 是软件学院名下的"串门"专业，且简称是旧目录的（大数据/智科/VR）：
--       3 电子信息工程 = 专业 6 的重复行；5 视觉传达设计 = 专业 14 的重复行 —— 删这两行；
--       4 工商管理归位到商学院(3)。修完软件学院只剩软工/计科，与它的 4 个班严丝合缝。
--
--   A13 colleges.student_count/teacher_count 全是 0（实际 24 学生 / 6 教师档案）。
--   A14 counselor_profiles.class_count/student_count 是 0（实际 8 个班 / 24 名学生）。
--   A10 5 门必修课被挂到选修模块上（数据结构在"艺术审美模块"、计算机基础在"人文素养模块"）。
--
--   本批一个字节都不动 scores（指纹须保持 185227906f1a09fcfb029c1458139cfd），
--   挂科基线 17 行 / 4 人 / 3,3,3,2 也不许变。
--   ⚠️ DDL 必须在 START TRANSACTION 之前（DDL 隐式提交，否则 ROLLBACK 空转）。
-- ============================================================================

DROP PROCEDURE IF EXISTS fix10_structural;
DELIMITER $$
CREATE PROCEDURE fix10_structural()
BEGIN
    DECLARE v_sc_rows    INT DEFAULT 0;
    DECLARE v_sr_rows    INT DEFAULT 0;
    DECLARE v_fail       INT DEFAULT 0;
    DECLARE v_fail_sm    INT DEFAULT 0;
    DECLARE v_fp         VARCHAR(64) DEFAULT '';
    DECLARE v_audit_fix  INT DEFAULT 0;
    DECLARE v_crs_upd    INT DEFAULT 0;
    DECLARE v_sr_upd     INT DEFAULT 0;
    DECLARE v_plan_upd   INT DEFAULT 0;
    DECLARE v_cls_upd    INT DEFAULT 0;
    DECLARE v_maj_move   INT DEFAULT 0;
    DECLARE v_maj_del    INT DEFAULT 0;
    DECLARE v_mod_upd    INT DEFAULT 0;
    DECLARE v_col_upd    INT DEFAULT 0;
    DECLARE v_cp_upd     INT DEFAULT 0;
    DECLARE v_bad        INT DEFAULT 0;

    -- ===== 阶段一：备份表与映射表（全部 DDL，事务之外）=====
    DROP TABLE IF EXISTS bak_fix10_score_audit_log;
    CREATE TABLE bak_fix10_score_audit_log AS SELECT * FROM score_audit_log;
    DROP TABLE IF EXISTS bak_fix10_courses;
    CREATE TABLE bak_fix10_courses AS SELECT * FROM courses;
    DROP TABLE IF EXISTS bak_fix10_score_record;
    CREATE TABLE bak_fix10_score_record AS SELECT * FROM score_record;
    DROP TABLE IF EXISTS bak_fix10_course_plans;
    CREATE TABLE bak_fix10_course_plans AS SELECT * FROM course_plans;
    DROP TABLE IF EXISTS bak_fix10_classes;
    CREATE TABLE bak_fix10_classes AS SELECT * FROM classes;
    DROP TABLE IF EXISTS bak_fix10_majors;
    CREATE TABLE bak_fix10_majors AS SELECT * FROM majors;
    DROP TABLE IF EXISTS bak_fix10_colleges;
    CREATE TABLE bak_fix10_colleges AS SELECT * FROM colleges;
    DROP TABLE IF EXISTS bak_fix10_counselor_profiles;
    CREATE TABLE bak_fix10_counselor_profiles AS SELECT * FROM counselor_profiles;

    -- 课程 -> 授课教师（users.id），1~5/9/10 与原值相同，只有 6/7/8 是补的
    DROP TABLE IF EXISTS tmp_f10_course_teacher;
    CREATE TABLE tmp_f10_course_teacher (course_id BIGINT PRIMARY KEY, new_teacher BIGINT NOT NULL);
    INSERT INTO tmp_f10_course_teacher (course_id, new_teacher) VALUES
        (1,29),(2,19),(3,22),(4,29),(5,22),(6,18),(7,28),(8,28),(9,28),(10,29);

    -- 班级 -> 专业/学院（按班名里的专业）
    DROP TABLE IF EXISTS tmp_f10_class;
    CREATE TABLE tmp_f10_class (class_id BIGINT PRIMARY KEY, new_major BIGINT NOT NULL, new_college BIGINT NOT NULL);
    INSERT INTO tmp_f10_class (class_id, new_major, new_college) VALUES
        (1,2,1),(5,1,1),(30,2,1),(31,2,1),(32,1,1),(33,1,1),(34,8,2),(35,8,2);

    -- 基线
    SELECT COUNT(*) INTO v_sc_rows FROM scores;
    SELECT COUNT(*) INTO v_sr_rows FROM score_record;
    SELECT COUNT(*) INTO v_fail FROM scores WHERE score_total < 60;
    SELECT COUNT(*) INTO v_fail_sm FROM scores WHERE score_total < 60 AND student_id = 3;
    SET SESSION group_concat_max_len = 100000000;
    SELECT MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) INTO v_fp FROM (
        SELECT CONCAT_WS(',', id, student_id, course_id, semester, IFNULL(year,'~'), regular_score,
               final_score, score_total, IFNULL(grade,'~'), IFNULL(grade_point,'~')) x FROM scores) b;

    -- ===== 阶段二：DML =====
    START TRANSACTION;

    -- A1 更正记录与权威成绩对齐（取该课最新一次成绩，即口径用的那一行）
    -- ⚠️ difference 是 STORED GENERATED (new_score - old_score)，不能直接赋值（ERROR 3105），
    --    改 new_score 后它会自动变成 1.50。
    UPDATE score_audit_log
       SET new_score = (SELECT s.score_total FROM scores s
                         WHERE s.student_id = score_audit_log.student_id
                           AND s.course_id  = score_audit_log.course_id
                         ORDER BY s.semester DESC, s.id DESC LIMIT 1),
           reason    = '试卷复核发现平时分漏计，据实更正'
     WHERE id = 1 AND student_id = 3 AND course_id = 1;
    SET v_audit_fix = ROW_COUNT();

    -- A4 课程授课教师：给 6/7/8 一个真实教师
    UPDATE courses c JOIN tmp_f10_course_teacher t ON t.course_id = c.id
       SET c.teacher_id = t.new_teacher
     WHERE NOT (c.teacher_id <=> t.new_teacher);
    SET v_crs_upd = ROW_COUNT();

    -- A4 录入表、开课计划统一到课程教师
    UPDATE score_record sr JOIN tmp_f10_course_teacher t ON t.course_id = sr.course_id
       SET sr.teacher_id = t.new_teacher
     WHERE NOT (sr.teacher_id <=> t.new_teacher);
    SET v_sr_upd = ROW_COUNT();

    UPDATE course_plans p JOIN tmp_f10_course_teacher t ON t.course_id = p.course_id
       SET p.teacher_id = t.new_teacher
     WHERE NOT (p.teacher_id <=> t.new_teacher);
    SET v_plan_upd = ROW_COUNT();

    -- A5 班级的专业与学院按班名归位
    UPDATE classes c JOIN tmp_f10_class t ON t.class_id = c.id
       SET c.major_id = t.new_major, c.college_id = t.new_college
     WHERE NOT (c.major_id <=> t.new_major) OR NOT (c.college_id <=> t.new_college);
    SET v_cls_upd = ROW_COUNT();

    -- A12 工商管理归位到商学院（先改简称再搬，避免出现"商学院简称是智科"的中间态）
    UPDATE majors SET college_id = 3, short_name = '工商' WHERE id = 4 AND name = '工商管理';
    SET v_maj_move = ROW_COUNT();

    -- A12 删掉与专业 6 / 14 重名的遗留行（已无任何表引用；student_profile.major_id 有外键，故先确认）
    DELETE m FROM majors m
      LEFT JOIN student_profile sp ON sp.major_id = m.id
     WHERE m.id IN (3,5) AND m.name IN ('电子信息工程','视觉传达设计') AND sp.id IS NULL;
    SET v_maj_del = ROW_COUNT();

    -- A10 必修课不该挂选修模块
    UPDATE courses SET module_id = NULL WHERE type = '必修课' AND module_id IS NOT NULL;
    SET v_mod_upd = ROW_COUNT();

    -- A13 学院人数冗余列
    UPDATE colleges c
       SET c.student_count = (SELECT COUNT(*) FROM student_profile sp WHERE sp.college_id = c.id),
           c.teacher_count = (SELECT COUNT(*) FROM teacher_profile t WHERE t.college_id = c.id)
     WHERE NOT (c.student_count <=> (SELECT COUNT(*) FROM student_profile sp WHERE sp.college_id = c.id))
        OR NOT (c.teacher_count <=> (SELECT COUNT(*) FROM teacher_profile t WHERE t.college_id = c.id));
    SET v_col_upd = ROW_COUNT();

    -- A14 辅导员带班数/学生数（代码用 counselor_profiles.user_id 匹配 classes.counselor_id）
    UPDATE counselor_profiles cp
       SET cp.class_count = (SELECT COUNT(*) FROM classes c WHERE c.counselor_id = cp.user_id),
           cp.student_count = (SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id = sp.class_id
                                WHERE c.counselor_id = cp.user_id)
     WHERE NOT (cp.class_count <=> (SELECT COUNT(*) FROM classes c WHERE c.counselor_id = cp.user_id))
        OR NOT (cp.student_count <=> (SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id = sp.class_id
                                WHERE c.counselor_id = cp.user_id));
    SET v_cp_upd = ROW_COUNT();

    -- ===== 校验 =====
    -- A1：更正记录必须与权威成绩（该课最新一次）对得上
    SELECT COUNT(*) INTO v_bad FROM score_audit_log l
     WHERE l.id = 1 AND (NOT (l.new_score <=> (SELECT s.score_total FROM scores s
              WHERE s.student_id = l.student_id AND s.course_id = l.course_id
              ORDER BY s.semester DESC, s.id DESC LIMIT 1))
        OR NOT (l.difference <=> l.new_score - l.old_score));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A1 更正记录仍与成绩不符'; END IF;

    -- A4：三张表的教师引用都不能悬空，且必须彼此一致
    SELECT COUNT(*) INTO v_bad FROM courses c LEFT JOIN users u ON u.id = c.teacher_id
     WHERE c.teacher_id IS NOT NULL AND u.id IS NULL;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A4 courses 仍有悬空教师'; END IF;
    SELECT COUNT(*) INTO v_bad FROM score_record sr LEFT JOIN users u ON u.id = sr.teacher_id
     WHERE sr.teacher_id IS NOT NULL AND u.id IS NULL;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A4 score_record 仍有悬空教师'; END IF;
    SELECT COUNT(*) INTO v_bad FROM score_record sr JOIN courses c ON c.id = sr.course_id
     WHERE NOT (sr.teacher_id <=> c.teacher_id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A4 录入表与课程表的教师不一致'; END IF;
    SELECT COUNT(*) INTO v_bad FROM course_plans p JOIN courses c ON c.id = p.course_id
     WHERE NOT (p.teacher_id <=> c.teacher_id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A4 开课计划与课程表的教师不一致'; END IF;

    -- A5：班名、专业、学院三者自洽
    SELECT COUNT(*) INTO v_bad FROM classes c JOIN majors m ON m.id = c.major_id
     WHERE m.college_id <> c.college_id;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A5 班级专业不属于该学院'; END IF;
    SELECT COUNT(*) INTO v_bad FROM classes c JOIN majors m ON m.id = c.major_id
     WHERE (c.name LIKE '计科%'     AND m.name <> '计算机科学与技术')
        OR (c.name LIKE '计算机%'   AND m.name <> '计算机科学与技术')
        OR (c.name LIKE '软工%'     AND m.name <> '软件工程')
        OR (c.name LIKE '软件工程%' AND m.name <> '软件工程')
        OR (c.name LIKE '人工智能%' AND m.name <> '人工智能');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A5 班级专业与班名不符'; END IF;
    SELECT COUNT(*) INTO v_bad FROM classes c JOIN tmp_f10_class t ON t.class_id = c.id
     WHERE c.major_id <> t.new_major OR c.college_id <> t.new_college;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A5 班级未按班名归位'; END IF;

    -- A12：专业表不能有重复行、不能有串门专业
    SELECT COUNT(*) INTO v_bad FROM majors m
     WHERE EXISTS (SELECT 1 FROM majors o WHERE o.name = m.name AND o.college_id = m.college_id AND o.id <> m.id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A12 同学院仍有同名专业'; END IF;
    SELECT COUNT(*) INTO v_bad FROM majors WHERE college_id = 1 AND name NOT IN ('软件工程','计算机科学与技术');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A12 软件学院仍挂着非软件类专业'; END IF;
    SELECT COUNT(*) INTO v_bad FROM majors WHERE id IN (3,5);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A12 重复专业行没删掉'; END IF;
    SELECT COUNT(*) INTO v_bad FROM classes WHERE major_id NOT IN (SELECT id FROM majors);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A12 有班级挂在已删专业上'; END IF;
    SELECT COUNT(*) INTO v_bad FROM student_profile WHERE major_id NOT IN (SELECT id FROM majors);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A12 有学生挂在已删专业上'; END IF;
    SELECT COUNT(*) INTO v_bad FROM benchmark_analysis WHERE major_id IN (3,5);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A12 benchmark 仍引用已删专业'; END IF;

    -- A10 / A13 / A14
    SELECT COUNT(*) INTO v_bad FROM courses WHERE type = '必修课' AND module_id IS NOT NULL;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A10 必修课仍挂着选修模块'; END IF;
    SELECT COUNT(*) INTO v_bad FROM colleges c
     WHERE c.student_count <> (SELECT COUNT(*) FROM student_profile sp WHERE sp.college_id = c.id)
        OR c.teacher_count <> (SELECT COUNT(*) FROM teacher_profile t WHERE t.college_id = c.id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A13 学院人数仍不符'; END IF;
    SELECT COUNT(*) INTO v_bad FROM counselor_profiles cp
     WHERE cp.class_count <> (SELECT COUNT(*) FROM classes c WHERE c.counselor_id = cp.user_id)
        OR cp.student_count <> (SELECT COUNT(*) FROM student_profile sp JOIN classes c ON c.id = sp.class_id
                                WHERE c.counselor_id = cp.user_id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A14 辅导员人数仍不符'; END IF;

    -- 底线：行数、挂科基线、scores 指纹都不能变
    IF (SELECT COUNT(*) FROM scores) <> v_sc_rows
       OR (SELECT COUNT(*) FROM score_record) <> v_sr_rows
       OR (SELECT COUNT(*) FROM scores WHERE score_total < 60) <> v_fail
       OR (SELECT COUNT(*) FROM scores WHERE score_total < 60 AND student_id = 3) <> v_fail_sm
       OR (SELECT MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
             SELECT CONCAT_WS(',', id, student_id, course_id, semester, IFNULL(year,'~'), regular_score,
                    final_score, score_total, IFNULL(grade,'~'), IFNULL(grade_point,'~')) x FROM scores) b) <> v_fp THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_audit_fix AS audit_fix, v_crs_upd AS courses_upd,
               v_sr_upd AS score_record_upd, v_plan_upd AS course_plans_upd, v_cls_upd AS classes_upd,
               v_maj_move AS major_moved, v_maj_del AS major_deleted, v_mod_upd AS module_cleared,
               v_col_upd AS colleges_upd, v_cp_upd AS counselor_upd;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_audit_fix AS audit_fix, v_crs_upd AS courses_upd,
               v_sr_upd AS score_record_upd, v_plan_upd AS course_plans_upd, v_cls_upd AS classes_upd,
               v_maj_move AS major_moved, v_maj_del AS major_deleted, v_mod_upd AS module_cleared,
               v_col_upd AS colleges_upd, v_cp_upd AS counselor_upd,
               (SELECT COUNT(*) FROM scores) AS scores_rows,
               (SELECT COUNT(*) FROM score_record) AS score_record_rows,
               (SELECT COUNT(*) FROM scores WHERE score_total < 60) AS fail_rows,
               (SELECT COUNT(*) FROM majors) AS majors_rows,
               (SELECT COUNT(*) FROM classes) AS classes_rows;
    END IF;
END$$
DELIMITER ;

CALL fix10_structural();
DROP PROCEDURE fix10_structural;
