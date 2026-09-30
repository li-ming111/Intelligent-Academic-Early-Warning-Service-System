-- ========================================
-- 数据自洽修复（第三批）：死表归档、评语归位、幻影人名纠正
-- 执行日期：2026-09-16
-- 目标库：academic_warning_system
--
-- 本批三件事，互不依赖：
--
-- 1) fail_records 归档清空。这张表全库无任何代码引用（已 grep 核实），
--    只剩 8 行 2026-05-27 的写入，而且数据本身是错的：8 条分数 65/71/66/69/68/64/62/65
--    **全部 ≥60** 却标 fail，用的还是旧课程名（线性代数/Java程序设计/计算机组成原理），
--    student_id 用的是 users.id（39/46/52）而非 profile.id。
--    它正是 notifications id=1/2/3 正文的逐字来源 —— 那三条通知里的 66/69/68
--    就是从这里抄来的。第二批已把通知正文改成权威口径，这 8 行是它的上游残留。
--    ★ 只清数据、保留表结构与实体，同 benchmark_analysis 的处理方式。
--
-- 2) score_record.remark 归位。course 3/5/6 的 138 行评语写的是**旧课程**题材：
--    3=行列式/特征值/秩/二次型（线性代数），5=泛型/IO流/多线程（Java程序设计），
--    6=CPU/存储器/总线/中断（计算机组成原理）；而这三门课现在的名字是
--    计算机基础 / 计算机网络 / 操作系统。评语与课程名对不上，一问就露。
--    翻译时**逐条保持褒贬倾向**，所以评语与分数仍然匹配
--    （原评语本就是按分数写的，保留 +/- 就不会出现「95 分却写概念模糊」）。
--    已带现课程名的「第N学期xxx」评语不动。
--
-- 3) 幻影人名「张明」纠正。它出现在 4 处，而 users 表没有这个人；
--    真实辅导员是 users.id=53 / username 1000000001 / name=司明（counselor_profiles.id=1）。
--    注意：替换后「司明」与预警学生司明同名，读起来会怪 —— 这是已知代价，
--    已在报告中说明；若要彻底消除，需要给辅导员账号改名（未在本批范围内）。
--
-- 可回滚：全程事务；执行前快照见 academic_warning_system_pre_fix3_20260916.sql。
-- ========================================

START TRANSACTION;

-- ---------------------------------------------------------------
-- 1) fail_records：先整表备份，再清空（保留结构与实体）
-- ---------------------------------------------------------------
DROP TABLE IF EXISTS fail_records_bak_20260916;
CREATE TABLE fail_records_bak_20260916 AS SELECT * FROM fail_records;

-- 清空前留个断言：只有确实是 8 行时才继续
-- （避免将来表被重新写入后，本脚本在一个更大的表上静默执行）
DELETE FROM fail_records WHERE (SELECT COUNT(*) FROM fail_records_bak_20260916) = 8;

-- ---------------------------------------------------------------
-- 2) score_record.remark：按现课程题材重写
--
--   course 3 (线性代数 → 计算机基础): 翻译 46 行 / 保留 3 行（已是现课程题材）
--   course 5 (Java程序设计 → 计算机网络): 翻译 46 行 / 保留 2 行（已是现课程题材）
--   course 6 (计算机组成原理 → 操作系统): 翻译 46 行 / 保留 2 行（已是现课程题材）
-- ---------------------------------------------------------------

UPDATE score_record SET remark = '计算机组成理解好' WHERE course_id = 3 AND remark = '二次型理解好';
UPDATE score_record SET remark = '硬件组成理解不足' WHERE course_id = 3 AND remark = '几何意义理解不足';
UPDATE score_record SET remark = '数据库基础理解一般' WHERE course_id = 3 AND remark = '合同关系理解一般';
UPDATE score_record SET remark = '数制概念需巩固' WHERE course_id = 3 AND remark = '向量概念需巩固';
UPDATE score_record SET remark = '计算机体系结构理解好' WHERE course_id = 3 AND remark = '向量空间理解好';
UPDATE score_record SET remark = '数据编码理解不足' WHERE course_id = 3 AND remark = '基变换理解不足';
UPDATE score_record SET remark = '动手操作能力强' WHERE course_id = 3 AND remark = '抽象思维能力强';
UPDATE score_record SET remark = '操作步骤完整' WHERE course_id = 3 AND remark = '推导过程完整';
UPDATE score_record SET remark = '文件管理操作准确' WHERE course_id = 3 AND remark = '方程组消元准确';
UPDATE score_record SET remark = '信息安全意识需巩固' WHERE course_id = 3 AND remark = '正交变换需巩固';
UPDATE score_record SET remark = '二进制运算需加强' WHERE course_id = 3 AND remark = '特征值计算需加强';
UPDATE score_record SET remark = '进制换算常出错' WHERE course_id = 3 AND remark = '矩阵乘法常出错';
UPDATE score_record SET remark = '表格数据处理掌握' WHERE course_id = 3 AND remark = '矩阵对角化掌握';
UPDATE score_record SET remark = '办公软件操作熟练' WHERE course_id = 3 AND remark = '矩阵运算熟练';
UPDATE score_record SET remark = '网络基础知识理解深' WHERE course_id = 3 AND remark = '秩与线性相关理解深';
UPDATE score_record SET remark = '故障判断困难' WHERE course_id = 3 AND remark = '秩判断困难';
UPDATE score_record SET remark = '存储体系理解深入' WHERE course_id = 3 AND remark = '线性变换理解深入';
UPDATE score_record SET remark = '操作系统概念清晰' WHERE course_id = 3 AND remark = '线性空间概念清晰';
UPDATE score_record SET remark = '数制转换准确' WHERE course_id = 3 AND remark = '行列式计算准确';
UPDATE score_record SET remark = '算法流程图困难' WHERE course_id = 3 AND remark = '证明题困难';
UPDATE score_record SET remark = '快捷键使用不熟练' WHERE course_id = 3 AND remark = '高斯消元不熟练';
UPDATE score_record SET remark = '组网方案有创意' WHERE course_id = 5 AND remark = 'GUI编程有创意';
UPDATE score_record SET remark = '数据流分析熟练' WHERE course_id = 5 AND remark = 'IO流操作熟练';
UPDATE score_record SET remark = 'IP地址规划熟练' WHERE course_id = 5 AND remark = 'JDBC操作熟练';
UPDATE score_record SET remark = '子网掩码计算生疏' WHERE course_id = 5 AND remark = 'Lambda表达式生疏';
UPDATE score_record SET remark = '交换原理掌握好' WHERE course_id = 5 AND remark = '反射机制掌握好';
UPDATE score_record SET remark = '网络基础过关' WHERE course_id = 5 AND remark = '基础语法过关';
UPDATE score_record SET remark = '抓包分析有进步' WHERE course_id = 5 AND remark = '多线程有进步';
UPDATE score_record SET remark = '多路复用理解深入' WHERE course_id = 5 AND remark = '多线程理解深入';
UPDATE score_record SET remark = '差错控制需加强' WHERE course_id = 5 AND remark = '异常处理需加强';
UPDATE score_record SET remark = '路由算法需巩固' WHERE course_id = 5 AND remark = '循环逻辑需巩固';
UPDATE score_record SET remark = '接口配置规范' WHERE course_id = 5 AND remark = '接口设计规范';
UPDATE score_record SET remark = '网线制作规范' WHERE course_id = 5 AND remark = '泛型使用规范';
UPDATE score_record SET remark = '精通网络排障' WHERE course_id = 5 AND remark = '精通JVM调优';
UPDATE score_record SET remark = '协议层次混淆' WHERE course_id = 5 AND remark = '继承多态混淆';
UPDATE score_record SET remark = '组网实验能力强，配置规范' WHERE course_id = 5 AND remark = '编程能力强，代码规范';
UPDATE score_record SET remark = 'Socket编程需加强' WHERE course_id = 5 AND remark = '网络编程需加强';
UPDATE score_record SET remark = '分层模型理解好' WHERE course_id = 5 AND remark = '设计模式理解好';
UPDATE score_record SET remark = '子网划分基本正确' WHERE course_id = 5 AND remark = '集合使用基本正确';
UPDATE score_record SET remark = '网络命令熟练' WHERE course_id = 5 AND remark = '集合框架熟练';
UPDATE score_record SET remark = '网络协议掌握良好' WHERE course_id = 5 AND remark = '面向对象掌握良好';
UPDATE score_record SET remark = '网络分层理解不足' WHERE course_id = 5 AND remark = '面向对象理解不足';
UPDATE score_record SET remark = '网络拓扑设计合理' WHERE course_id = 5 AND remark = '面向对象设计合理';
UPDATE score_record SET remark = '组网实践能力强' WHERE course_id = 5 AND remark = '项目开发能力强';
UPDATE score_record SET remark = '进程管理理解好' WHERE course_id = 6 AND remark = 'CPU原理理解好';
UPDATE score_record SET remark = '死锁处理需复习' WHERE course_id = 6 AND remark = 'DMA方式需复习';
UPDATE score_record SET remark = '设备管理理解好' WHERE course_id = 6 AND remark = 'IO设备理解好';
UPDATE score_record SET remark = '信号量机制理解好' WHERE course_id = 6 AND remark = '中断处理理解好';
UPDATE score_record SET remark = '内核结构掌握好' WHERE course_id = 6 AND remark = '体系结构掌握好';
UPDATE score_record SET remark = '多线程调度理解深' WHERE course_id = 6 AND remark = '多核处理器理解深';
UPDATE score_record SET remark = '内存管理理解一般' WHERE course_id = 6 AND remark = '存储器理解一般';
UPDATE score_record SET remark = '存储管理理解深' WHERE course_id = 6 AND remark = '存储层次理解深';
UPDATE score_record SET remark = '调度算法理解' WHERE course_id = 6 AND remark = '微程序控制器理解';
UPDATE score_record SET remark = '并发控制理解好' WHERE course_id = 6 AND remark = '总线设计理解好';
UPDATE score_record SET remark = '进程同步掌握好' WHERE course_id = 6 AND remark = '指令流水掌握好';
UPDATE score_record SET remark = '文件系统需复习' WHERE course_id = 6 AND remark = '指令系统需复习';
UPDATE score_record SET remark = '进程通信理解一般' WHERE course_id = 6 AND remark = '数值表示理解一般';
UPDATE score_record SET remark = '系统调用需加强' WHERE course_id = 6 AND remark = '汇编基础需加强';
UPDATE score_record SET remark = '页面置换算法理解' WHERE course_id = 6 AND remark = '流水线技术理解';
UPDATE score_record SET remark = '进程调度理解好' WHERE course_id = 6 AND remark = '硬件原理理解好';
UPDATE score_record SET remark = '缓冲区管理概念模糊' WHERE course_id = 6 AND remark = '缓存概念模糊';
UPDATE score_record SET remark = '原语概念模糊' WHERE course_id = 6 AND remark = '运算器概念模糊';

-- ---------------------------------------------------------------
-- 3) 幻影人名「张明」-> 真实辅导员「司明」
-- ---------------------------------------------------------------

-- 沟通记录：李爽(student_id=46)的三门不及格约谈记录
UPDATE communication_record SET content = REPLACE(content, '张明', '司明') WHERE content LIKE '%张明%';

-- 资源作者
UPDATE support_resource SET author = REPLACE(author, '张明', '司明') WHERE author LIKE '%张明%';

-- 辅导员班级申请：这两行不只是名字假，counselor_id 指向的是 users.id=1（admin / 系统管理员，role=4），class_id 指向 classes.id=1（计科2306班），
-- 而 class_name 写的是计算机2101/2102班（classes.id=30/31，其 counselor_id 均为 53）。
-- 只换名字会留下「申请人=管理员、申请班级=另一班」的新矛盾，故一并按 class_name 校正。
UPDATE counselor_class_application SET counselor_name = '司明', counselor_id = 53 WHERE id = 1;
UPDATE counselor_class_application SET counselor_name = '司明', counselor_id = 53 WHERE id = 2;
UPDATE counselor_class_application SET class_id = 30 WHERE id = 1;
UPDATE counselor_class_application SET class_id = 31 WHERE id = 2;

COMMIT;

-- ========================================
-- 执行后校验（期望值写在这里，便于直接对照）
--
-- (a) fail_records 已空、备份表 8 行：
--   SELECT (SELECT COUNT(*) FROM fail_records) AS live_cnt,
--          (SELECT COUNT(*) FROM fail_records_bak_20260916) AS bak_cnt;   -- 期望 0 / 8
--
-- (b) 三门课的评语里不再出现旧课程题材（全部应为 0）：
--   SELECT course_id,
--          SUM(remark REGEXP '行列式|特征值|矩阵|向量|二次型|线性空间|正交变换|高斯消元') AS la,
--          SUM(remark REGEXP '面向对象|泛型|IO流|多线程|JDBC|JVM|Lambda|集合框架|反射') AS java,
--          SUM(remark REGEXP 'CPU|存储器|总线|中断|流水线|微程序|运算器|DMA|汇编|指令') AS co
--     FROM score_record WHERE course_id IN (3,5,6) GROUP BY course_id;
--
-- (c) 全库再无「张明」：
--   SELECT COUNT(*) FROM communication_record WHERE content LIKE '%张明%';
--   SELECT COUNT(*) FROM support_resource     WHERE author  LIKE '%张明%';
--   SELECT COUNT(*) FROM counselor_class_application WHERE counselor_name LIKE '%张明%';
--
-- (d) 辅导员班级申请三字段自洽（counselor_id=53，class_id 与 classes 表里的辅导员一致）：
--   SELECT a.id, a.counselor_id, a.class_id, a.class_name, a.counselor_name, c.name, c.counselor_id
--     FROM counselor_class_application a LEFT JOIN classes c ON c.id = a.class_id;
--
-- (e) 权威基线不得改变（本批不碰 scores）：司明=3、韩坤=2、李爽=3、张健=3
-- ========================================
