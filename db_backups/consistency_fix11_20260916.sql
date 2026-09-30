-- ============================================================================
-- 批次 11：修掉"文本/身份/冗余引用"类不一致（不动任何成绩数字）
--
--   A2  账号姓名与档案表不符，且含乱码：
--         users 16=司明（admin_profile 明确写的是 夏志浩）—— 16 的邮箱
--         'admin@test.com的发生' 更是乱码；18='张老师'（teacher_profile=刘教授）；
--         28='发生的官方'（teacher_profile=赵振江）；55='发送'（teacher_profile=陈志强）；
--         56='1'、57='2'（college_admin_profile=李明远/张建国）。
--         修法：一律以档案表为准回填 users.name（档案表是各角色的权威身份源）。
--
--   A3  25 个账号共用同一个邮箱 2318986782@qq.com 和同一个手机号 15245508389
--        （司明 + 23 名同班同学 + 辅导员 53）。另有 users 55 的手机号
--        24524224545 不是合法号段、users 55 的邮箱写着 'adm001'（用户名当邮箱）。
--         修法：学生邮箱 = 学号@stu.academic.edu（天然唯一、可追问）;
--         学生手机号 = 152 + 8 位流水（沿用原 152 号段，避免与既有
--         138000000000x/1390000000x/16744343245/18845894565 冲突）;
--         55/56/57/53 用档案表里已有的邮箱手机，56/57 见 college_admin_profile。
--
--   A6  辅导员班级申请：1/2 两行 status=2（已拒绝），但 classes 表里
--        计算机2101/2102 班的 counselor_id 就是 53 —— "申请被拒但班已经归他"。
--        （前端状态码：0 待审核 / 1 已通过 / 2 已拒绝 / 3 已取消，见
--          CounselorClassManagement.vue getStatusText。）改为 1 已通过。
--        第 5 行 reason='范德萨'（乱码）、counselor_name='辅导员'（占位）改为实值。
--
--   A7  教师班级申请 14 行 reason 全是键盘乱敲（法撒旦/放大/是否都/fds a…），
--        且第 13 行 status=1（已通过管理软件工程2101班）与 classes.teacher_id=18
--        （刘教授）矛盾。修法：删掉 8 行调试残留（同一天几分钟内反复提交又取消），
--        保留 6 行可讲通的台账并重写理由：
--          班 5 软工2306班：1 已取消（信息有误撤销）→ 2 已拒绝（该班已排班主任）
--                          → 9 已通过（管理）→ 10 已通过（解除）  ⇒ 该班现无班主任
--          班 30 计算机2101班：11 已通过（管理）                    ⇒ 教师=29 于凤海
--          班 32 软件工程2101班：13 已拒绝（该班已由刘教授负责）     ⇒ 教师=18 刘教授
--        这样每个班"最后一条已通过申请"推出的教师都等于 classes.teacher_id。
--
--   A8  communication_record.student_id 用了 users.id（39/46/52），而同一张表第 4 行
--        用的是 student_profile.id（13=韩坤），第 5 行干脆是 0。全库其余地方
--        （communication_logs.student_id、assistance_plans.student_id）都是 profile.id，
--        代码 CommunicationRecordMapper 也是按该列查学生历史，故统一到 profile.id：
--        39→13(韩坤) 46→20(李爽) 52→26(张健) 0→4(邱琳紫)。第 5 行 content='范德萨' 改写成
--        与邱琳紫实际学业状态相符的沟通文本（她各科最新成绩均 ≥80，不能写"出勤作业欠佳"）。
--
--   A9  feedback 第 2 行"张浩：数据结构的课后作业太难了"—— 全库没有叫张浩的学生。
--        改为韩坤（数据结构 59 分挂科，与"作业太难"完全吻合），
--        与他的预警、沟通记录、帮扶计划串成同一条线。
--
--   A11 teaching_resources 有 5 行标题还是上一代课程目录（线性代数/Java程序设计/
--        计算机组成原理），而 course_id 指向的是计算机基础(3)/计算机网络(5)/操作系统(6)；
--        10 行的 created_by 又全是"于凤海"，而课程表里 2/3/5/6 课的教师分别是
--        王老师(19)/李老师(22)/刘教授(18)。修法：标题按现课程改写，created_by 取该课教师。
--        support_resource 第 2/4 行（Java编程入门教程、线性代数精讲）同样改写。
--
--   A16 communication_logs 第 2001 行"就本学期操作系统(40分)、计算机基础(47分)两门挂科课程
--        约谈司明"，而权威口径下司明是 3 门挂科（高等数学 56.5 也在内），
--        且必修课成绩跨 4/5 两个学期（操作系统 40 分在第 4 学期），"本学期"不成立。
--        改写为不带学期断言的三门课程表述（与他的预警/帮扶计划措辞一致）。
--
--   A17 counselor_profiles.college_name='计算机学院'，而 college_id=1 在 colleges 表里
--        是"软件学院"（没有"计算机学院"这个学院）。按 college_id 归位。
--
--   A18 counselor_class_management 表只有 1 行（辅导员 53 ↔ 计科2306班），
--        而 classes.counselor_id=53 的班有 8 个。该表无任何 Java 代码读写（死表），
--        但库里躺着"1 个班"与主表"8 个班"对不上，故补齐到 8 行（与 classes 一致）。
--
--   本批一个字节都不动 scores / score_record：行数、挂科基线（17 行 / 4 人 / 司明 9 行）、
--   scores 指纹 185227906f1a09fcfb029c1458139cfd 必须原样保持。
--   ⚠️ DDL 必须在 START TRANSACTION 之前（DDL 隐式提交，否则 ROLLBACK 空转）。
-- ============================================================================

DROP PROCEDURE IF EXISTS fix11_texts;
DELIMITER $$
CREATE PROCEDURE fix11_texts()
BEGIN
    DECLARE v_sc_rows   INT DEFAULT 0;
    DECLARE v_sr_rows   INT DEFAULT 0;
    DECLARE v_fail      INT DEFAULT 0;
    DECLARE v_fail_sm   INT DEFAULT 0;
    DECLARE v_fp        VARCHAR(64) DEFAULT '';
    DECLARE v_name_upd  INT DEFAULT 0;
    DECLARE v_ctc_upd   INT DEFAULT 0;
    DECLARE v_capp      INT DEFAULT 0;
    DECLARE v_tapp_del  INT DEFAULT 0;
    DECLARE v_tapp_upd  INT DEFAULT 0;
    DECLARE v_comm_upd  INT DEFAULT 0;
    DECLARE v_fb_upd    INT DEFAULT 0;
    DECLARE v_tr_upd    INT DEFAULT 0;
    DECLARE v_sr_upd    INT DEFAULT 0;
    DECLARE v_log_upd   INT DEFAULT 0;
    DECLARE v_cp_upd    INT DEFAULT 0;
    DECLARE v_ccm_ins   INT DEFAULT 0;
    DECLARE v_bad       INT DEFAULT 0;

    -- ===== 阶段一：备份表与映射表（全部 DDL，事务之外）=====
    DROP TABLE IF EXISTS bak_fix11_users;
    CREATE TABLE bak_fix11_users AS SELECT * FROM users;
    DROP TABLE IF EXISTS bak_fix11_teacher_class_application;
    CREATE TABLE bak_fix11_teacher_class_application AS SELECT * FROM teacher_class_application;
    DROP TABLE IF EXISTS bak_fix11_counselor_class_application;
    CREATE TABLE bak_fix11_counselor_class_application AS SELECT * FROM counselor_class_application;
    DROP TABLE IF EXISTS bak_fix11_communication_record;
    CREATE TABLE bak_fix11_communication_record AS SELECT * FROM communication_record;
    DROP TABLE IF EXISTS bak_fix11_feedback;
    CREATE TABLE bak_fix11_feedback AS SELECT * FROM feedback;
    DROP TABLE IF EXISTS bak_fix11_teaching_resources;
    CREATE TABLE bak_fix11_teaching_resources AS SELECT * FROM teaching_resources;
    DROP TABLE IF EXISTS bak_fix11_support_resource;
    CREATE TABLE bak_fix11_support_resource AS SELECT * FROM support_resource;
    DROP TABLE IF EXISTS bak_fix11_communication_logs;
    CREATE TABLE bak_fix11_communication_logs AS SELECT * FROM communication_logs;
    DROP TABLE IF EXISTS bak_fix11_counselor_profiles;
    CREATE TABLE bak_fix11_counselor_profiles AS SELECT * FROM counselor_profiles;
    DROP TABLE IF EXISTS bak_fix11_counselor_class_management;
    CREATE TABLE bak_fix11_counselor_class_management AS SELECT * FROM counselor_class_management;

    -- 账号联系方式目标值（学生由档案表自动生成，教职工用档案表里的既有值）
    DROP TABLE IF EXISTS tmp_f11_user_contact;
    -- ⚠️ 显式指定排序规则：新建表默认取库默认（utf8mb4_0900_ai_ci），
    --    而 users.email/phone 是 utf8mb4_unicode_ci，直接 <=> 会撞 ERROR 1267 非法混用。
    CREATE TABLE tmp_f11_user_contact (
        user_id   BIGINT PRIMARY KEY,
        new_email VARCHAR(100) COLLATE utf8mb4_unicode_ci,
        new_phone VARCHAR(20)  COLLATE utf8mb4_unicode_ci
    );
    INSERT INTO tmp_f11_user_contact (user_id, new_email, new_phone) VALUES
        (16, 'admin@test.com',        '13800000003'),
        (53, 'counselor001@test.com', '13800000014'),
        (55, 'teacher006@test.com',   '13800000013'),
        (56, 'limy@academic.edu',     '13900000002'),
        (57, 'zhangjg@academic.edu',  '13900000001');
    INSERT INTO tmp_f11_user_contact (user_id, new_email, new_phone)
    SELECT u.id, CONCAT(sp.student_id, '@stu.academic.edu'),
           CONCAT('152', LPAD(ROW_NUMBER() OVER (ORDER BY u.id), 8, '0'))
      FROM users u JOIN student_profile sp ON sp.user_id = u.id
     WHERE u.role = 1;

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

    -- A2 账号姓名以档案表为准
    UPDATE users SET name = '夏志浩' WHERE id = 16 AND NOT (name <=> '夏志浩');
    SET v_name_upd = v_name_upd + ROW_COUNT();
    UPDATE users SET name = '刘教授' WHERE id = 18 AND NOT (name <=> '刘教授');
    SET v_name_upd = v_name_upd + ROW_COUNT();
    UPDATE users SET name = '赵振江' WHERE id = 28 AND NOT (name <=> '赵振江');
    SET v_name_upd = v_name_upd + ROW_COUNT();
    UPDATE users SET name = '陈志强' WHERE id = 55 AND NOT (name <=> '陈志强');
    SET v_name_upd = v_name_upd + ROW_COUNT();
    UPDATE users SET name = '李明远' WHERE id = 56 AND NOT (name <=> '李明远');
    SET v_name_upd = v_name_upd + ROW_COUNT();
    UPDATE users SET name = '张建国' WHERE id = 57 AND NOT (name <=> '张建国');
    SET v_name_upd = v_name_upd + ROW_COUNT();

    -- A3 联系方式去重/合法化
    UPDATE users u JOIN tmp_f11_user_contact t ON t.user_id = u.id
       SET u.email = t.new_email, u.phone = t.new_phone
     WHERE NOT ((BINARY u.email) <=> (BINARY t.new_email))
        OR NOT ((BINARY u.phone) <=> (BINARY t.new_phone));
    SET v_ctc_upd = ROW_COUNT();

    -- A6 辅导员班级申请：状态与班级事实对齐 + 文本落地
    UPDATE counselor_class_application a JOIN classes c ON c.id = a.class_id
       SET a.status = 1
     WHERE a.id IN (1, 2) AND a.counselor_id = c.counselor_id AND a.status <> 1;
    SET v_capp = v_capp + ROW_COUNT();
    UPDATE counselor_class_application
       SET reason = '申请负责计科2306班的日常管理与学业帮扶工作', counselor_name = '司明'
     WHERE id = 5 AND NOT (reason <=> '申请负责计科2306班的日常管理与学业帮扶工作');
    SET v_capp = v_capp + ROW_COUNT();

    -- A7 教师班级申请：删调试残留，保留可讲通的台账
    DELETE FROM teacher_class_application WHERE id IN (3, 4, 5, 6, 7, 8, 12, 14);
    SET v_tapp_del = ROW_COUNT();

    UPDATE teacher_class_application SET reason = '申请信息填写有误，撤销后重新提交'
     WHERE id = 1 AND NOT (reason <=> '申请信息填写有误，撤销后重新提交');
    SET v_tapp_upd = v_tapp_upd + ROW_COUNT();
    UPDATE teacher_class_application
       SET reason = '申请管理软工2306班', reject_reason = '该班本学期已安排班主任，暂不受理'
     WHERE id = 2 AND NOT (reason <=> '申请管理软工2306班');
    SET v_tapp_upd = v_tapp_upd + ROW_COUNT();
    UPDATE teacher_class_application
       SET reason = '本学期教学任务调整，再次申请管理软工2306班'
     WHERE id = 9 AND NOT (reason <=> '本学期教学任务调整，再次申请管理软工2306班');
    SET v_tapp_upd = v_tapp_upd + ROW_COUNT();
    UPDATE teacher_class_application
       SET reason = '因教学与科研任务繁重，申请解除软工2306班的管理工作'
     WHERE id = 10 AND NOT (reason <=> '因教学与科研任务繁重，申请解除软工2306班的管理工作');
    SET v_tapp_upd = v_tapp_upd + ROW_COUNT();
    UPDATE teacher_class_application
       SET reason = '申请管理计算机2101班'
     WHERE id = 11 AND NOT (reason <=> '申请管理计算机2101班');
    SET v_tapp_upd = v_tapp_upd + ROW_COUNT();
    -- 13：本来"已通过管理软件工程2101班"，与 classes.teacher_id=18（刘教授）矛盾，改为被拒
    UPDATE teacher_class_application
       SET status = 2, reason = '申请管理软件工程2101班', reject_reason = '该班已由刘教授担任班主任，暂不调整'
     WHERE id = 13 AND status = 1;
    SET v_tapp_upd = v_tapp_upd + ROW_COUNT();

    -- A8 沟通记录统一到 student_profile.id
    UPDATE communication_record SET student_id = 13 WHERE id = 1 AND student_name = '韩坤';
    SET v_comm_upd = v_comm_upd + ROW_COUNT();
    UPDATE communication_record SET student_id = 20 WHERE id = 2 AND student_name = '李爽';
    SET v_comm_upd = v_comm_upd + ROW_COUNT();
    UPDATE communication_record SET student_id = 26 WHERE id = 3 AND student_name = '张健';
    SET v_comm_upd = v_comm_upd + ROW_COUNT();
    UPDATE communication_record
       SET student_id = 4,
           content = '向辅导员同步邱琳紫同学的学习状态：本学期各科成绩均在 80 分以上，建议纳入学业优秀名单，请辅导员协同关注其后续发展'
     WHERE id = 5 AND student_name = '邱琳紫';
    SET v_comm_upd = v_comm_upd + ROW_COUNT();

    -- A9 学生反馈里的"张浩"不是本班学生，换成真实学生（韩坤：数据结构 59 分挂科）
    UPDATE feedback
       SET content = '韩坤：数据结构的课后作业太难了，能否提供一些参考示例'
     WHERE id = 2 AND content LIKE '张浩：%';
    SET v_fb_upd = ROW_COUNT();

    -- A11 教学资源标题按现课程目录改写（course 3=计算机基础 / 5=计算机网络 / 6=操作系统）
    UPDATE teaching_resources SET title = '计算机基础-核心知识点梳理',
           description = '计算机基础课程配套课件，涵盖计算机组成、数据表示与进制转换、操作系统与网络基础',
           url = 'https://example.com/computer-basics'
     WHERE id = 4 AND course_id = 3;
    SET v_tr_upd = v_tr_upd + ROW_COUNT();
    UPDATE teaching_resources SET title = '计算机网络-实验指导书',
           description = '计算机网络课程实验指导书，涵盖组网配置、抓包分析、子网划分与路由实验',
           url = 'https://example.com/network-lab'
     WHERE id = 6 AND course_id = 5;
    SET v_tr_upd = v_tr_upd + ROW_COUNT();
    UPDATE teaching_resources SET title = '网络编程实战项目源码',
           description = '基于 Socket 的网络通信程序完整源码，适合课程设计参考',
           url = 'https://example.com/network-project'
     WHERE id = 7 AND course_id = 5;
    SET v_tr_upd = v_tr_upd + ROW_COUNT();
    UPDATE teaching_resources SET title = '操作系统-实验教程',
           description = '操作系统实验教程，包括进程管理、内存管理、文件系统与并发控制实验',
           url = 'https://example.com/os-lab'
     WHERE id = 8 AND course_id = 6;
    SET v_tr_upd = v_tr_upd + ROW_COUNT();
    UPDATE teaching_resources SET title = '操作系统期末复习大纲',
           description = '操作系统期末考试复习提纲，含重点知识梳理与模拟题',
           url = 'https://example.com/os-review'
     WHERE id = 10 AND course_id = 6;
    SET v_tr_upd = v_tr_upd + ROW_COUNT();

    -- A11 created_by 应等于该课授课教师（课程 2/3/5/6 的教师分别是王老师/李老师/刘教授）
    UPDATE teaching_resources r JOIN courses c ON c.id = r.course_id JOIN users u ON u.id = c.teacher_id
       SET r.created_by = u.name
     WHERE NOT ((BINARY r.created_by) <=> (BINARY u.name));

    UPDATE support_resource SET title = '计算机网络学习指南', category = '计算机',
           description = '针对计算机网络挂科学生的补习资料，涵盖 OSI 模型、TCP/IP 协议栈、子网划分与路由配置',
           content = 'OSI 七层模型、TCP/IP 协议栈、IP 地址与子网划分、常用网络命令与故障排查...',
           url = 'https://example.com/network-guide'
     WHERE id = 2;
    SET v_sr_upd = v_sr_upd + ROW_COUNT();
    UPDATE support_resource SET title = '计算机基础精讲', category = '计算机',
           description = '计算机基础重点难点解析，含计算机组成、进制转换与操作系统基础',
           content = '计算机组成与工作原理、数据表示与进制转换、操作系统基础、计算机网络基础...',
           url = 'https://example.com/computer-basics'
     WHERE id = 4;
    SET v_sr_upd = v_sr_upd + ROW_COUNT();

    -- A16 辅导员的约谈记录按权威口径补全到 3 门（原写"两门"，少了高等数学）
    UPDATE communication_logs
       SET content = '就操作系统(40分)、计算机基础(47分)、高等数学(56.5分)三门挂科课程约谈司明, 了解其学习困难集中在程序设计基础薄弱与上机练习不足, 已建议参加操作系统学习小组并预约每周两次辅导答疑。'
     WHERE id = 2001;
    SET v_log_upd = ROW_COUNT();

    -- A17 辅导员的学院名按 college_id 归位（colleges.id=1 是软件学院，没有"计算机学院"）
    UPDATE counselor_profiles cp JOIN colleges c ON c.id = cp.college_id
       SET cp.college_name = c.name
     WHERE NOT ((BINARY cp.college_name) <=> (BINARY c.name));
    SET v_cp_upd = ROW_COUNT();

    -- A18 辅导员带班表补齐到 classes 的实际归属（该表无代码引用，纯账实相符）
    INSERT INTO counselor_class_management (counselor_id, class_id, class_name, status, created_at, updated_at)
    SELECT c.counselor_id, c.id, c.name, 1, NOW(), NOW()
      FROM classes c
     WHERE c.counselor_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM counselor_class_management m
                        WHERE m.class_id = c.id AND m.counselor_id = c.counselor_id);
    SET v_ccm_ins = ROW_COUNT();

    -- ===== 校验 =====
    -- A2：账号姓名必须与档案表一致（0 处不符）
    SELECT COUNT(*) INTO v_bad FROM users u
     LEFT JOIN student_profile sp ON sp.user_id = u.id
     LEFT JOIN teacher_profile tp ON tp.user_id = u.id
     LEFT JOIN admin_profile ap ON ap.user_id = u.id
     LEFT JOIN college_admin_profile cap ON cap.user_id = u.id
     LEFT JOIN counselor_profiles cp ON cp.user_id = u.id
     WHERE BINARY u.name <> COALESCE(BINARY sp.name, BINARY tp.name, BINARY ap.name,
                                    BINARY cap.name, BINARY cp.name, BINARY u.name);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A2 账号姓名仍与档案不符'; END IF;
    SELECT COUNT(*) INTO v_bad FROM users WHERE name IN ('发送','发生的官方','张老师','1','2');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A2 仍有占位/乱码姓名'; END IF;

    -- A3：邮箱、手机号不得重复，手机号必须是合法号段
    SELECT COUNT(*) INTO v_bad FROM (SELECT email FROM users WHERE email IS NOT NULL AND email <> ''
                                      GROUP BY email HAVING COUNT(*) > 1) z;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A3 邮箱仍有重复'; END IF;
    SELECT COUNT(*) INTO v_bad FROM (SELECT phone FROM users WHERE phone IS NOT NULL AND phone <> ''
                                      GROUP BY phone HAVING COUNT(*) > 1) z;
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A3 手机号仍有重复'; END IF;
    SELECT COUNT(*) INTO v_bad FROM users WHERE email IN ('adm001','admin@test.com的发生');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A3 仍有非法邮箱'; END IF;
    SELECT COUNT(*) INTO v_bad FROM users WHERE phone IS NOT NULL AND phone <> '' AND phone NOT REGEXP '^1[3-9][0-9]{9}$';
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A3 仍有非法手机号'; END IF;
    -- 24 名学生邮箱必须是 学号@stu.academic.edu
    SELECT COUNT(*) INTO v_bad FROM users u JOIN student_profile sp ON sp.user_id = u.id
     WHERE NOT ((BINARY u.email) <=> (BINARY CONCAT(sp.student_id, '@stu.academic.edu')));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A3 学生邮箱不是学号邮箱'; END IF;

    -- A6：被通过的申请必须真的对应班级事实；已通过的行不能指向别的辅导员
    SELECT COUNT(*) INTO v_bad FROM counselor_class_application a JOIN classes c ON c.id = a.class_id
     WHERE a.status = 1 AND NOT (a.counselor_id <=> c.counselor_id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A6 已通过申请与班级辅导员不符'; END IF;
    SELECT COUNT(*) INTO v_bad FROM counselor_class_application
     WHERE id = 5 AND (NOT (counselor_name <=> '司明') OR reason NOT REGEXP '[一-龥]');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A6 第 5 行文本仍不合法'; END IF;

    -- A7：台账必须能被逐行讲通；"最后一条已通过申请"推出的教师 == classes.teacher_id
    SELECT COUNT(*) INTO v_bad FROM (
        SELECT a.class_id, IF(a.application_type = 1, NULL, a.teacher_id) AS eff
          FROM teacher_class_application a
         WHERE a.status = 1
           AND NOT EXISTS (SELECT 1 FROM teacher_class_application b
                            WHERE b.status = 1 AND b.class_id = a.class_id
                              AND (b.create_time > a.create_time
                                   OR (b.create_time = a.create_time AND b.id > a.id)))
    ) e JOIN classes c ON c.id = e.class_id
     WHERE NOT (e.eff <=> c.teacher_id);
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A7 申请台账与班级教师不符'; END IF;
    SELECT COUNT(*) INTO v_bad FROM teacher_class_application
     WHERE reason NOT REGEXP '[一-龥]' OR LENGTH(reason) < 6
        OR (status = 2 AND (reject_reason IS NULL OR LENGTH(reject_reason) < 6));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A7 理由仍是乱码'; END IF;

    -- A8：student_id 必须落在 student_profile，且与 student_name 一致
    SELECT COUNT(*) INTO v_bad FROM communication_record r
     LEFT JOIN student_profile sp ON sp.id = r.student_id
     WHERE sp.id IS NULL OR NOT ((BINARY sp.name) <=> (BINARY r.student_name));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A8 沟通记录的学生 ID 不合法'; END IF;

    -- A9：反馈文本里的学生必须是真学生
    SELECT COUNT(*) INTO v_bad FROM feedback f
     WHERE NOT EXISTS (SELECT 1 FROM student_profile sp
                        WHERE (BINARY f.content) LIKE (BINARY CONCAT(sp.name, '：%')));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A9 反馈里的学生不存在'; END IF;

    -- A11：教学资源标题必须与课程名同源；created_by 必须是该课教师
    SELECT COUNT(*) INTO v_bad FROM teaching_resources r
     WHERE (r.course_id = 3 AND r.title NOT LIKE '%计算机基础%')
        OR (r.course_id = 5 AND r.title NOT LIKE '%网络%')
        OR (r.course_id = 6 AND r.title NOT LIKE '%操作系统%');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A11 教学资源标题与课程不符'; END IF;
    SELECT COUNT(*) INTO v_bad FROM teaching_resources r JOIN courses c ON c.id = r.course_id
     JOIN users u ON u.id = c.teacher_id WHERE NOT ((BINARY r.created_by) <=> (BINARY u.name));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A11 教学资源作者不是该课教师'; END IF;
    -- 旧目录（线性代数/Java程序设计/计算机组成原理）不得再出现在资源标题里
    SELECT COUNT(*) INTO v_bad FROM (
        SELECT CONCAT(IFNULL(title,''),' ',IFNULL(description,'')) x FROM teaching_resources
        UNION ALL SELECT CONCAT(IFNULL(title,''),' ',IFNULL(description,''),' ',IFNULL(content,'')) FROM support_resource) z
     WHERE z.x LIKE '%线性代数%' OR z.x LIKE '%计组%' OR z.x LIKE '%Java程序设计%'
        OR z.x LIKE '%计算机组成原理%' OR z.x LIKE '%Java编程%';
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A11 仍有上一代课程目录'; END IF;

    -- A16：约谈记录里的分数必须与权威口径一致，且"门数"表述不能与挂科门数矛盾
    SELECT COUNT(*) INTO v_bad FROM communication_logs
     WHERE id = 2001 AND (content NOT LIKE '%操作系统(40分)%'
                       OR content NOT LIKE '%计算机基础(47分)%'
                       OR content NOT LIKE '%高等数学(56.5分)%'
                       OR content LIKE '%两门%');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A16 约谈记录与挂科口径不符'; END IF;

    -- A17：辅导员的学院名必须等于其 college_id 的学院名
    SELECT COUNT(*) INTO v_bad FROM counselor_profiles cp JOIN colleges c ON c.id = cp.college_id
     WHERE NOT ((BINARY cp.college_name) <=> (BINARY c.name));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A17 辅导员学院名不符'; END IF;

    -- A18：带班表必须与 classes 的辅导员归属一致
    SELECT COUNT(*) INTO v_bad FROM classes c
     WHERE c.counselor_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM counselor_class_management m
                        WHERE m.class_id = c.id AND m.counselor_id = c.counselor_id
                          AND (BINARY m.class_name) = (BINARY c.name));
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A18 带班表与班级归属不符'; END IF;

    -- 全库扫描：本批涉及的文本列里不得再出现乱码/占位/上一代目录
    SELECT COUNT(*) INTO v_bad FROM (
        SELECT CONCAT(IFNULL(reason,''),' ',IFNULL(reject_reason,'')) x FROM teacher_class_application
        UNION ALL SELECT CONCAT(IFNULL(reason,''),' ',IFNULL(counselor_name,'')) FROM counselor_class_application
        UNION ALL SELECT IFNULL(content,'') FROM communication_record
        UNION ALL SELECT IFNULL(content,'') FROM feedback
        UNION ALL SELECT IFNULL(content,'') FROM communication_logs
        UNION ALL SELECT CONCAT(IFNULL(title,''),' ',IFNULL(description,'')) FROM support_resource
    ) z
     WHERE z.x LIKE '%范德萨%' OR z.x LIKE '%fds a%' OR z.x LIKE '%fdas%' OR z.x LIKE '%法撒旦%'
        OR z.x LIKE '%大师傅%' OR z.x LIKE '%飞洒%' OR z.x LIKE '%安抚的%' OR z.x LIKE '%张浩%'
        OR z.x LIKE '%张明%';
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '全库扫描仍有乱码/占位文本'; END IF;
    -- 申请表的姓名列不能是角色占位（'辅导员' 这类词在正文里是合法的，故只查姓名列）
    SELECT COUNT(*) INTO v_bad FROM (
        SELECT counselor_name AS x FROM counselor_class_application
        UNION ALL SELECT teacher_name FROM teacher_class_application) z
     WHERE z.x IS NULL OR z.x IN ('辅导员','老师','教师','学生','1','2','');
    IF v_bad <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申请表姓名列仍是占位'; END IF;

    -- 底线：本批不碰成绩
    IF (SELECT COUNT(*) FROM scores) <> v_sc_rows
       OR (SELECT COUNT(*) FROM score_record) <> v_sr_rows
       OR (SELECT COUNT(*) FROM scores WHERE score_total < 60) <> v_fail
       OR (SELECT COUNT(*) FROM scores WHERE score_total < 60 AND student_id = 3) <> v_fail_sm
       OR (SELECT MD5(GROUP_CONCAT(x ORDER BY x SEPARATOR '|')) FROM (
             SELECT CONCAT_WS(',', id, student_id, course_id, semester, IFNULL(year,'~'), regular_score,
                    final_score, score_total, IFNULL(grade,'~'), IFNULL(grade_point,'~')) x FROM scores) b) <> v_fp THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_name_upd AS users_name, v_ctc_upd AS users_contact,
               v_capp AS counselor_app, v_tapp_del AS tapp_deleted, v_tapp_upd AS tapp_updated,
               v_comm_upd AS comm_upd, v_fb_upd AS feedback_upd, v_tr_upd AS teaching_res_upd,
               v_sr_upd AS support_res_upd, v_log_upd AS comm_log_upd, v_cp_upd AS counselor_upd,
               v_ccm_ins AS ccm_inserted;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_name_upd AS users_name, v_ctc_upd AS users_contact,
               v_capp AS counselor_app, v_tapp_del AS tapp_deleted, v_tapp_upd AS tapp_updated,
               v_comm_upd AS comm_upd, v_fb_upd AS feedback_upd, v_tr_upd AS teaching_res_upd,
               v_sr_upd AS support_res_upd, v_log_upd AS comm_log_upd, v_cp_upd AS counselor_upd,
               v_ccm_ins AS ccm_inserted,
               (SELECT COUNT(*) FROM users) AS users_rows,
               (SELECT COUNT(*) FROM teacher_class_application) AS tapp_rows,
               (SELECT COUNT(*) FROM communication_record) AS comm_rows,
               (SELECT COUNT(*) FROM counselor_class_management) AS ccm_rows;
    END IF;
END$$
DELIMITER ;

CALL fix11_texts();
DROP PROCEDURE fix11_texts;
