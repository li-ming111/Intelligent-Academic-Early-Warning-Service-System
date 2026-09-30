# -*- coding: utf-8 -*-
"""生成第三批一致性修复 SQL：fail_records 归档清空 + 评语按现课程重写 + 幻影人名纠正。

评语部分不是手写：脚本先从库里读出 course 3/5/6 的真实去重评语，
再用下面的映射逐条翻译，若有一条没被映射到就直接报错，
避免"改了一部分、漏了一部分"这种最难查的残留。
"""
import io
import subprocess
import sys

DB = 'academic_warning_system'


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr.decode('utf-8', 'replace'))
        raise SystemExit('SQL failed: ' + sql)
    return r.stdout.decode('utf-8', 'replace')


# ---------------------------------------------------------------
# 评语映射：(旧课程题材) -> (现课程题材)
# 逐条保持**褒贬倾向不变**，这样评语与学生的分数仍然对得上
# （原评语本来就是按分数写的，翻译时保留 +/- 就不会出现"95 分却写概念模糊"）。
# ---------------------------------------------------------------

# course 3 线性代数 -> 计算机基础
MAP3 = {
    '行列式计算准确': '数制转换准确',
    '高斯消元不熟练': '快捷键使用不熟练',
    '线性变换理解深入': '存储体系理解深入',
    '向量空间理解好': '计算机体系结构理解好',
    '矩阵对角化掌握': '表格数据处理掌握',
    '几何意义理解不足': '硬件组成理解不足',
    '部分概念模糊': '部分概念模糊',
    '矩阵乘法常出错': '进制换算常出错',
    '秩与线性相关理解深': '网络基础知识理解深',
    '推导过程完整': '操作步骤完整',
    '秩判断困难': '故障判断困难',
    '二次型理解好': '计算机组成理解好',
    '矩阵运算熟练': '办公软件操作熟练',
    '线性空间概念清晰': '操作系统概念清晰',
    '基变换理解不足': '数据编码理解不足',
    '向量概念需巩固': '数制概念需巩固',
    '证明题困难': '算法流程图困难',
    '正交变换需巩固': '信息安全意识需巩固',
    '特征值计算需加强': '二进制运算需加强',
    '合同关系理解一般': '数据库基础理解一般',
    '方程组消元准确': '文件管理操作准确',
    '需要多练习': '需要多练习',
    '抽象思维能力强': '动手操作能力强',
}

# course 5 Java程序设计 -> 计算机网络
MAP5 = {
    '编程能力强，代码规范': '组网实验能力强，配置规范',
    '多线程理解深入': '多路复用理解深入',
    '面向对象设计合理': '网络拓扑设计合理',
    '网络编程需加强': 'Socket编程需加强',
    '面向对象掌握良好': '网络协议掌握良好',
    '泛型使用规范': '网线制作规范',
    '多线程有进步': '抓包分析有进步',
    '集合框架熟练': '网络命令熟练',
    'JDBC操作熟练': 'IP地址规划熟练',
    '异常处理需加强': '差错控制需加强',
    '反射机制掌握好': '交换原理掌握好',
    'GUI编程有创意': '组网方案有创意',
    'Lambda表达式生疏': '子网掩码计算生疏',
    '基础语法过关': '网络基础过关',
    '精通JVM调优': '精通网络排障',
    '设计模式理解好': '分层模型理解好',
    '面向对象理解不足': '网络分层理解不足',
    '循环逻辑需巩固': '路由算法需巩固',
    '继承多态混淆': '协议层次混淆',
    '项目开发能力强': '组网实践能力强',
    '集合使用基本正确': '子网划分基本正确',
    '接口设计规范': '接口配置规范',
    'IO流操作熟练': '数据流分析熟练',
}

# course 6 计算机组成原理 -> 操作系统
MAP6 = {
    '理论理解深入': '理论理解深入',
    '实验报告完整': '实验报告完整',
    '需加强实践': '需加强实践',
    '硬件原理理解好': '进程调度理解好',
    'CPU原理理解好': '进程管理理解好',
    '存储器理解一般': '内存管理理解一般',
    'IO设备理解好': '设备管理理解好',
    '指令系统需复习': '文件系统需复习',
    '总线设计理解好': '并发控制理解好',
    '基本概念模糊': '基本概念模糊',
    '虚拟内存理解': '虚拟内存理解',
    '缓存概念模糊': '缓冲区管理概念模糊',
    '数值表示理解一般': '进程通信理解一般',
    'DMA方式需复习': '死锁处理需复习',
    '汇编基础需加强': '系统调用需加强',
    '微程序控制器理解': '调度算法理解',
    '多核处理器理解深': '多线程调度理解深',
    '中断处理理解好': '信号量机制理解好',
    '流水线技术理解': '页面置换算法理解',
    '运算器概念模糊': '原语概念模糊',
    '指令流水掌握好': '进程同步掌握好',
    '体系结构掌握好': '内核结构掌握好',
    '存储层次理解深': '存储管理理解深',
}

COURSE_NEW_NAME = {3: '计算机基础', 5: '计算机网络', 6: '操作系统'}
COURSE_OLD_NAME = {3: '线性代数', 5: 'Java程序设计', 6: '计算机组成原理'}
ALL_MAPS = {3: MAP3, 5: MAP5, 6: MAP6}


def esc(s):
    return s.replace('\\', '\\\\').replace("'", "''")


# ---------------------------------------------------------------
# 1) 从库里读真实评语，逐条翻译
# ---------------------------------------------------------------
stmts = []
total_mapped = 0
report = []

for cid in (3, 5, 6):
    rows = []
    for line in q("SELECT remark, COUNT(*) FROM score_record WHERE course_id=%d "
                  "AND remark IS NOT NULL GROUP BY remark" % cid).split('\n'):
        line = line.strip('\r\n')
        if not line.strip():
            continue
        p = [x.strip('\r') for x in line.split('\t')]
        rows.append((p[0], int(p[1])))

    m = ALL_MAPS[cid]
    pairs = []          # (new, count)
    skipped = []        # 已经是现课程题材的（第N学期xxx）
    for remark, cnt in rows:
        if remark in m:
            pairs.append((m[remark], cnt))
            total_mapped += cnt
        else:
            # 不属于旧课程题材 → 必须已经是现课程题材，否则报错
            if COURSE_NEW_NAME[cid] not in remark:
                raise SystemExit('[未映射] course %d 评语「%s」%d 行 既不在映射表里，'
                                 '也不含现课程名「%s」' % (cid, remark, cnt, COURSE_NEW_NAME[cid]))
            skipped.append((remark, cnt))

    report.append('--   course %d (%s → %s): 翻译 %d 行 / 保留 %d 行（已是现课程题材）'
                  % (cid, COURSE_OLD_NAME[cid], COURSE_NEW_NAME[cid],
                     sum(c for _, c in pairs), sum(c for _, c in skipped)))
    for old, new in sorted(m.items(), key=lambda kv: kv[0]):
        if old == new:
            continue
        stmts.append("UPDATE score_record SET remark = '%s' WHERE course_id = %d AND remark = '%s';"
                     % (esc(new), cid, esc(old)))

# ---------------------------------------------------------------
# 2) 幻影人名 张明 -> 真实辅导员 司明
#    users.id=53 / username 1000000001 / counselor_profiles.name = '司明'
# ---------------------------------------------------------------
name_stmts = [
    "-- 沟通记录：李爽(student_id=46)的三门不及格约谈记录",
    "UPDATE communication_record SET content = REPLACE(content, '张明', '司明') WHERE content LIKE '%张明%';",
    "",
    "-- 资源作者",
    "UPDATE support_resource SET author = REPLACE(author, '张明', '司明') WHERE author LIKE '%张明%';",
    "",
    "-- 辅导员班级申请：这两行不只是名字假，counselor_id 指向的是 users.id=1"
    "（admin / 系统管理员，role=4），class_id 指向 classes.id=1（计科2306班），",
    "-- 而 class_name 写的是计算机2101/2102班（classes.id=30/31，其 counselor_id 均为 53）。",
    "-- 只换名字会留下「申请人=管理员、申请班级=另一班」的新矛盾，故一并按 class_name 校正。",
    "UPDATE counselor_class_application SET counselor_name = '司明', counselor_id = 53 WHERE id = 1;",
    "UPDATE counselor_class_application SET counselor_name = '司明', counselor_id = 53 WHERE id = 2;",
    "UPDATE counselor_class_application SET class_id = 30 WHERE id = 1;",
    "UPDATE counselor_class_application SET class_id = 31 WHERE id = 2;",
]

# ---------------------------------------------------------------
# 3) 拼装
# ---------------------------------------------------------------
L = []
L.append("-- ========================================")
L.append("-- 数据自洽修复（第三批）：死表归档、评语归位、幻影人名纠正")
L.append("-- 执行日期：2026-09-16")
L.append("-- 目标库：academic_warning_system")
L.append("--")
L.append("-- 本批三件事，互不依赖：")
L.append("--")
L.append("-- 1) fail_records 归档清空。这张表全库无任何代码引用（已 grep 核实），")
L.append("--    只剩 8 行 2026-05-27 的写入，而且数据本身是错的：8 条分数 65/71/66/69/68/64/62/65")
L.append("--    **全部 ≥60** 却标 fail，用的还是旧课程名（线性代数/Java程序设计/计算机组成原理），")
L.append("--    student_id 用的是 users.id（39/46/52）而非 profile.id。")
L.append("--    它正是 notifications id=1/2/3 正文的逐字来源 —— 那三条通知里的 66/69/68")
L.append("--    就是从这里抄来的。第二批已把通知正文改成权威口径，这 8 行是它的上游残留。")
L.append("--    ★ 只清数据、保留表结构与实体，同 benchmark_analysis 的处理方式。")
L.append("--")
L.append("-- 2) score_record.remark 归位。course 3/5/6 的 138 行评语写的是**旧课程**题材：")
L.append("--    3=行列式/特征值/秩/二次型（线性代数），5=泛型/IO流/多线程（Java程序设计），")
L.append("--    6=CPU/存储器/总线/中断（计算机组成原理）；而这三门课现在的名字是")
L.append("--    计算机基础 / 计算机网络 / 操作系统。评语与课程名对不上，一问就露。")
L.append("--    翻译时**逐条保持褒贬倾向**，所以评语与分数仍然匹配")
L.append("--    （原评语本就是按分数写的，保留 +/- 就不会出现「95 分却写概念模糊」）。")
L.append("--    已带现课程名的「第N学期xxx」评语不动。")
L.append("--")
L.append("-- 3) 幻影人名「张明」纠正。它出现在 4 处，而 users 表没有这个人；")
L.append("--    真实辅导员是 users.id=53 / username 1000000001 / name=司明（counselor_profiles.id=1）。")
L.append("--    注意：替换后「司明」与预警学生司明同名，读起来会怪 —— 这是已知代价，")
L.append("--    已在报告中说明；若要彻底消除，需要给辅导员账号改名（未在本批范围内）。")
L.append("--")
L.append("-- 可回滚：全程事务；执行前快照见 academic_warning_system_pre_fix3_20260916.sql。")
L.append("-- ========================================")
L.append("")
L.append("START TRANSACTION;")
L.append("")
L.append("-- ---------------------------------------------------------------")
L.append("-- 1) fail_records：先整表备份，再清空（保留结构与实体）")
L.append("-- ---------------------------------------------------------------")
L.append("DROP TABLE IF EXISTS fail_records_bak_20260916;")
L.append("CREATE TABLE fail_records_bak_20260916 AS SELECT * FROM fail_records;")
L.append("")
L.append("-- 清空前留个断言：只有确实是 8 行时才继续")
L.append("-- （避免将来表被重新写入后，本脚本在一个更大的表上静默执行）")
L.append("DELETE FROM fail_records WHERE (SELECT COUNT(*) FROM fail_records_bak_20260916) = 8;")
L.append("")
L.append("-- ---------------------------------------------------------------")
L.append("-- 2) score_record.remark：按现课程题材重写")
L.append("--")
for line in report:
    L.append(line)
L.append("-- ---------------------------------------------------------------")
L.append("")
L.extend(stmts)
L.append("")
L.append("-- ---------------------------------------------------------------")
L.append("-- 3) 幻影人名「张明」-> 真实辅导员「司明」")
L.append("-- ---------------------------------------------------------------")
L.append("")
L.extend(name_stmts)
L.append("")
L.append("COMMIT;")
L.append("")
L.append("-- ========================================")
L.append("-- 执行后校验（期望值写在这里，便于直接对照）")
L.append("--")
L.append("-- (a) fail_records 已空、备份表 8 行：")
L.append("--   SELECT (SELECT COUNT(*) FROM fail_records) AS live_cnt,")
L.append("--          (SELECT COUNT(*) FROM fail_records_bak_20260916) AS bak_cnt;   -- 期望 0 / 8")
L.append("--")
L.append("-- (b) 三门课的评语里不再出现旧课程题材（全部应为 0）：")
L.append("--   SELECT course_id,")
L.append("--          SUM(remark REGEXP '行列式|特征值|矩阵|向量|二次型|线性空间|正交变换|高斯消元') AS la,")
L.append("--          SUM(remark REGEXP '面向对象|泛型|IO流|多线程|JDBC|JVM|Lambda|集合框架|反射') AS java,")
L.append("--          SUM(remark REGEXP 'CPU|存储器|总线|中断|流水线|微程序|运算器|DMA|汇编|指令') AS co")
L.append("--     FROM score_record WHERE course_id IN (3,5,6) GROUP BY course_id;")
L.append("--")
L.append("-- (c) 全库再无「张明」：")
L.append("--   SELECT COUNT(*) FROM communication_record WHERE content LIKE '%张明%';")
L.append("--   SELECT COUNT(*) FROM support_resource     WHERE author  LIKE '%张明%';")
L.append("--   SELECT COUNT(*) FROM counselor_class_application WHERE counselor_name LIKE '%张明%';")
L.append("--")
L.append("-- (d) 辅导员班级申请三字段自洽（counselor_id=53，class_id 与 classes 表里的辅导员一致）：")
L.append("--   SELECT a.id, a.counselor_id, a.class_id, a.class_name, a.counselor_name, c.name, c.counselor_id")
L.append("--     FROM counselor_class_application a LEFT JOIN classes c ON c.id = a.class_id;")
L.append("--")
L.append("-- (e) 权威基线不得改变（本批不碰 scores）：司明=3、韩坤=2、李爽=3、张健=3")
L.append("-- ========================================")

io.open('D:/ABS/db_backups/consistency_fix3_20260916.sql', 'w',
        encoding='utf-8', newline='\n').write('\n'.join(L) + '\n')

print('报告:')
for line in report:
    print(line)
print('翻译总行数 =', total_mapped, '(期望 138)')
print('UPDATE 语句数 =', len(stmts))
print('SQL 已写入 consistency_fix3_20260916.sql')
