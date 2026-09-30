-- ============================================================================
-- 批次 8：重写 4 门选修课 92 行的评语
--
--   现状：这 92 行是旧课程目录留下的"一门课一句、23 人全同"的评语：
--           课7 人工智能导论 -> 「心理健康」×23
--           课8 机器学习     -> 「职业规划」×23
--           课9 深度学习     -> 「艺术鉴赏」×23
--           课10 中国传统文化 -> 「传统文化」×23
--         前三门是旧目录里根本不存在的课名，第四门是 4 字残句。
--         而课程 1~6 的评语是每人一句、与该生分数相呼应
--         （90 分以上「微积分掌握扎实」，不及格「多处知识漏洞」）。
--
--   本次按同样的风格逐人重写 92 句：用课程本身的术语，措辞与该生的
--   score_total 档位呼应，同一门课内 92 句互不重复。
--   司明的 291/294 两行评语保持 NULL 不动（他那两行的学期尚有争议，
--   见 verify_fix7.txt 的 (g) 段）。
--
--   只改 score_record.remark 一列，score_record 其他列与 scores 全表都不动。
--   回滚：score_record_bak_prefix7_20260916 里保存着这 92 行的原始评语。
--
--   ⚠️ 事务位置：DDL 必须放在 START TRANSACTION 之前（DDL 会隐式提交，
--      否则最后的 ROLLBACK 是空操作）。
-- ============================================================================

DROP PROCEDURE IF EXISTS fix8_remarks;
DELIMITER $$
CREATE PROCEDURE fix8_remarks()
BEGIN
    DECLARE v_expected INT DEFAULT 92;
    DECLARE v_todo     INT DEFAULT 0;
    DECLARE v_updated  INT DEFAULT 0;
    DECLARE v_left_old INT DEFAULT 0;
    DECLARE v_null     INT DEFAULT 0;
    DECLARE v_dupin    INT DEFAULT 0;
    DECLARE v_sc       INT DEFAULT 0;

    -- ===== 阶段一：建映射表（DDL，事务之外）=====
    DROP TABLE IF EXISTS tmp_remark8;
    CREATE TABLE tmp_remark8 (
        student_id BIGINT NOT NULL,
        course_id  BIGINT NOT NULL,
        new_remark VARCHAR(500) NOT NULL,
        PRIMARY KEY (student_id, course_id)
    );

    INSERT INTO tmp_remark8 (student_id, course_id, new_remark) VALUES
    -- 课7 人工智能导论
    (25,7,'智能体建模思路清晰'),(14,7,'搜索算法掌握扎实'),(7,7,'启发式搜索理解透彻'),
    (19,7,'知识表示章节表现优秀'),(4,7,'博弈树分析准确'),(17,7,'谓词逻辑推导熟练'),
    (5,7,'课堂讨论积极，概念清楚'),(12,7,'专家系统案例完成好'),(21,7,'状态空间建模规范'),
    (10,7,'遗传算法理解到位'),(6,7,'模糊推理掌握较好'),(16,7,'知识图谱作业完成认真'),
    (23,7,'基本概念清晰，实践偏少'),(8,7,'智能体设计基本掌握'),(9,7,'搜索策略选择需练习'),
    (18,7,'逻辑推理部分有进步'),(11,7,'理论学习尚可，实验偏弱'),(22,7,'状态空间建模需加强'),
    (24,7,'算法伪代码书写待规范'),(13,7,'基础概念模糊，需巩固'),(15,7,'上课注意力不集中'),
    (20,7,'搜索算法理解吃力'),(26,7,'基础薄弱，多处知识漏洞'),
    -- 课8 机器学习
    (25,8,'模型调优能力突出'),(19,8,'特征工程做得细致'),(14,8,'集成学习掌握扎实'),
    (5,8,'交叉验证运用熟练'),(4,8,'决策树与剪枝理解好'),(17,8,'支持向量机推导清晰'),
    (7,8,'聚类算法实验完成好'),(12,8,'模型评估指标理解到位'),(8,8,'线性回归实现规范'),
    (21,8,'降维方法掌握较好'),(10,8,'朴素贝叶斯应用熟练'),(6,8,'梯度下降理解正确'),
    (16,8,'KNN分类实验认真'),(23,8,'基本算法能独立实现'),(18,8,'过拟合问题理解一般'),
    (9,8,'调参经验不足需多练'),(11,8,'损失函数推导需加强'),(22,8,'数学推导基础较弱'),
    (24,8,'特征选择方法需复习'),(13,8,'实验报告较粗糙'),(15,8,'模型原理理解不透'),
    (20,8,'公式推导常出错'),(26,8,'基础不牢，需系统复习'),
    -- 课9 深度学习
    (19,9,'注意力机制理解深入'),(25,9,'网络结构设计有想法'),(17,9,'反向传播推导熟练'),
    (7,9,'卷积网络实验优秀'),(14,9,'循环网络掌握扎实'),(10,9,'迁移学习应用得当'),
    (5,9,'优化器选择有依据'),(12,9,'批归一化理解到位'),(4,9,'张量运算熟练'),
    (6,9,'激活函数对比分析好'),(21,9,'模型搭建基本规范'),(16,9,'池化操作理解正确'),
    (8,9,'生成模型概念尚可'),(23,9,'正则化方法需多练'),(18,9,'网络训练调参较弱'),
    (9,9,'链式求导需巩固'),(11,9,'代码实现有困难'),(22,9,'理论理解较浅'),
    (24,9,'实验复现不完整'),(13,9,'梯度问题理解模糊'),(15,9,'基础概念掌握不牢'),
    (20,9,'推导过程错误较多'),(26,9,'基础薄弱，需从头复习'),
    -- 课10 中国传统文化
    (25,10,'诸子百家思想理解深'),(4,10,'经典诵读积累丰富'),(14,10,'史学脉络梳理清晰'),
    (10,10,'礼乐制度理解到位'),(19,10,'诗词赏析功底扎实'),(17,10,'书法史论掌握较好'),
    (7,10,'传统节日民俗熟悉'),(12,10,'戏曲艺术理解深入'),(5,10,'课堂分享积极，见解独到'),
    (21,10,'陶瓷工艺知识丰富'),(6,10,'古典建筑理解较好'),(16,10,'绘画流派辨识准确'),
    (8,10,'中医文化了解较多'),(23,10,'文化自信主题完成认真'),(18,10,'史料阅读需加强'),
    (9,10,'论文选题思路一般'),(11,10,'经典原文理解吃力'),(22,10,'背诵积累不足'),
    (24,10,'观点表达待组织'),(13,10,'上课参与度不高'),(15,10,'基础知识掌握不牢'),
    (20,10,'作业完成质量偏低'),(26,10,'多处知识漏洞');

    SELECT COUNT(*) INTO v_todo FROM tmp_remark8;

    -- ===== 阶段二：DML =====
    START TRANSACTION;

    SELECT COUNT(*) INTO v_sc FROM scores;

    UPDATE score_record sr
      JOIN tmp_remark8 r ON r.student_id = sr.student_id AND r.course_id = sr.course_id
       SET sr.remark = r.new_remark
     WHERE sr.course_id IN (7,8,9,10) AND sr.semester = 6;
    SET v_updated = ROW_COUNT();

    -- ===== 校验 =====
    -- 4 门课里不能再有旧课程名
    SELECT COUNT(*) INTO v_left_old FROM score_record
     WHERE course_id IN (7,8,9,10) AND remark IN ('心理健康','职业规划','艺术鉴赏','传统文化');

    SELECT COUNT(*) INTO v_null FROM score_record
     WHERE course_id IN (7,8,9,10) AND student_id <> 3 AND (remark IS NULL OR remark = '');

    -- 同一门课内评语不能重复
    SELECT COUNT(*) INTO v_dupin FROM (
        SELECT 1 FROM score_record WHERE course_id IN (7,8,9,10) AND student_id <> 3
         GROUP BY course_id, remark HAVING COUNT(*) > 1) g;

    IF v_todo <> v_expected OR v_updated <> v_expected OR v_left_old <> 0
       OR v_null <> 0 OR v_dupin <> 0 THEN
        ROLLBACK;
        SELECT 'ROLLED BACK' AS result, v_todo AS map_rows, v_updated AS updated,
               v_left_old AS old_remark_left, v_null AS null_remark, v_dupin AS dup_in_course;
    ELSE
        COMMIT;
        SELECT 'COMMITTED' AS result, v_todo AS map_rows, v_updated AS updated,
               v_left_old AS old_remark_left, v_null AS null_remark, v_dupin AS dup_in_course,
               v_sc AS scores_rows;
    END IF;
END$$
DELIMITER ;

CALL fix8_remarks();
DROP PROCEDURE fix8_remarks;
DROP TABLE IF EXISTS tmp_remark8;
