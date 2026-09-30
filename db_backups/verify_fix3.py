# -*- coding: utf-8 -*-
"""第三批修复的校验：死表已归档清空、评语归位、幻影人名消失，且权威基线未被改动。"""
import io
import subprocess

DB = 'academic_warning_system'
LATEST = ("(SELECT lat.id, lat.student_id, lat.course_id, lat.score_total FROM "
          "(SELECT s.id, s.student_id, s.course_id, s.score_total, "
          "ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id ORDER BY s.semester DESC, s.id DESC) rn "
          "FROM scores s WHERE s.final_score IS NOT NULL) lat WHERE lat.rn=1)")


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', '--default-character-set=utf8mb4',
                        '-N', '-B', DB, '-e', sql], capture_output=True)
    # Windows 版 mysql 客户端输出 \r\n，多行结果里的 \r 会让逐行比较失败
    out = r.stdout.decode('utf-8', 'replace').replace('\r\n', '\n')
    if r.returncode != 0:
        out += '[SQLERR] ' + r.stderr.decode('utf-8', 'replace')
    return out.strip()


def check(label, expect, sql):
    got = q(sql)
    ok = (got == expect)
    return '%s %s\n    期望: %s\n    实际: %s' % ('[PASS]' if ok else '[FAIL]', label, expect or '(空)', got or '(空)')


o = []
o.append('========== 第三批修复校验 ==========')
o.append('')

o.append('--- (a) fail_records 已归档清空 ---')
o.append(check('live = 0 / bak = 8', '0\t8',
               'SELECT (SELECT COUNT(*) FROM fail_records), (SELECT COUNT(*) FROM fail_records_bak_20260916)'))
o.append(check('备份表内容仍是原来那 8 行（分数全 >=60 却标 fail）',
               '65.00,71.00,66.00,69.00,68.00,64.00,62.00,65.00',
               "SELECT GROUP_CONCAT(score ORDER BY id) FROM fail_records_bak_20260916"))
o.append(check('fail_records 表结构仍在（12 列）', '12',
               "SELECT COUNT(*) FROM information_schema.COLUMNS "
               "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='fail_records'"))
o.append('')

o.append('--- (b) score_record.remark 题材与课程名一致 ---')
o.append('  旧题材残留行数（三门课各自应为 0）:')
o.append('  注：Java 关键词写精确到旧评语原文，不用裸「多线程」——')
o.append('      新评语「多线程调度理解深」是操作系统的正当内容，裸词会误报。')
o.append(q("SELECT course_id, "
           "SUM(remark REGEXP '行列式|特征值|矩阵|向量|二次型|线性空间|正交变换|高斯消元|合同关系|基变换|秩') AS '旧:线性代数', "
           "SUM(remark REGEXP '面向对象|泛型|IO流|JDBC|JVM|Lambda|集合框架|反射|继承多态|GUI编程|"
           "多线程理解|多线程有进步|设计模式理解|基础语法过关|循环逻辑|项目开发|接口设计规范|异常处理|编程能力强') AS '旧:Java', "
           "SUM(remark REGEXP 'CPU|存储器|总线|中断|流水线|微程序|运算器|DMA|汇编|指令系统') AS '旧:计组' "
           "FROM score_record WHERE course_id IN (3,5,6) GROUP BY course_id"))
o.append(check('评语总行数未变（384）', '384', 'SELECT COUNT(*) FROM score_record'))
o.append(check('course 3/5/6 评语行数未变（3 多一条「第5学期…补考仍不及格」）', '49\t48\t48',
               "SELECT SUM(course_id=3), SUM(course_id=5), SUM(course_id=6) FROM score_record"))
o.append(check('保留 / 翻译 行数（保留 3-2-2，翻译各 46）',
               '3\t3\t46\n5\t2\t46\n6\t2\t46',
               "SELECT course_id, SUM(remark LIKE '第%学期%'), SUM(remark NOT LIKE '第%学期%') "
               "FROM score_record WHERE course_id IN (3,5,6) GROUP BY course_id"))
o.append('')
o.append('  改后评语样本（course 3 = 计算机基础，取前 6 条）:')
o.append(q("SELECT DISTINCT remark FROM score_record WHERE course_id=3 ORDER BY remark LIMIT 6"))
o.append('  改后评语样本（course 5 = 计算机网络，取前 6 条）:')
o.append(q("SELECT DISTINCT remark FROM score_record WHERE course_id=5 ORDER BY remark LIMIT 6"))
o.append('  改后评语样本（course 6 = 操作系统，取前 6 条）:')
o.append(q("SELECT DISTINCT remark FROM score_record WHERE course_id=6 ORDER BY remark LIMIT 6"))
o.append('')

o.append('--- (c) 幻影人名「张明」全库消失 ---')
o.append(check('communication_record', '0', "SELECT COUNT(*) FROM communication_record WHERE content LIKE '%张明%'"))
o.append(check('support_resource', '0', "SELECT COUNT(*) FROM support_resource WHERE author LIKE '%张明%'"))
o.append(check('counselor_class_application', '0', "SELECT COUNT(*) FROM counselor_class_application WHERE counselor_name LIKE '%张明%'"))
o.append(check('users 里本来就没这个人（仍为 0）', '0', "SELECT COUNT(*) FROM users WHERE name = '张明'"))
o.append(check('替换后的名字确实是真实辅导员', '53\t司明\t司明',
               "SELECT u.id, u.name, cp.name FROM users u JOIN counselor_profiles cp ON cp.user_id=u.id WHERE u.id=53"))
o.append('')
o.append('  替换后的 4 处原文:')
o.append(q("SELECT CONCAT('communication_record.', id, ' -> ', content) FROM communication_record WHERE content LIKE '%司明%'"))
o.append(q("SELECT CONCAT('support_resource.', id, ' -> ', author) FROM support_resource WHERE author = '司明'"))
o.append(q("SELECT CONCAT('counselor_class_application.', id, ' -> ', counselor_name) FROM counselor_class_application WHERE counselor_name = '司明'"))
o.append('')

o.append('--- (d) 辅导员班级申请三字段自洽 ---')
o.append('  id / counselor_id / class_id / class_name / 申请人 / classes.name / classes.counselor_id')
o.append(q("SELECT a.id, a.counselor_id, a.class_id, a.class_name, a.counselor_name, c.name, c.counselor_id "
           "FROM counselor_class_application a LEFT JOIN classes c ON c.id = a.class_id ORDER BY a.id"))
o.append(check('申请班级名与 classes 表一致、且两表辅导员一致的行数（3 行全部自洽）', '3',
               "SELECT COUNT(*) FROM counselor_class_application a JOIN classes c ON c.id=a.class_id "
               "WHERE a.class_name = c.name AND a.counselor_id = c.counselor_id"))
o.append('  注：id=5 的 counselor_name 仍是占位词「辅导员」、reason 仍是「范德萨」，')
o.append('      与 id=1/2 现在写实名的做法不一致 —— 属既有垃圾行，未在本批授权范围内。')
o.append('')

o.append('--- (e) 权威基线未被改动 [期望 3:3,13:2,20:3,26:3] ---')
o.append(check('挂科门数基线', '3:3,13:2,20:3,26:3',
               'SELECT GROUP_CONCAT(CONCAT(v.student_id,":",c) ORDER BY v.student_id) FROM '
               '(SELECT v.student_id, COUNT(*) c FROM ' + LATEST +
               ' v WHERE v.score_total < 60 GROUP BY v.student_id) v'))
o.append(check('scores / score_record 行数', '167\t384',
               'SELECT (SELECT COUNT(*) FROM scores), (SELECT COUNT(*) FROM score_record)'))
o.append(check('courses.type 仍为两种取值', '必修课\t6\n选修课\t4',
               'SELECT type, COUNT(*) FROM courses GROUP BY type ORDER BY type'))

io.open('D:/ABS/db_backups/_verify3.txt', 'w', encoding='utf-8', newline='\n').write('\n'.join(o))
print('\n'.join(o))
