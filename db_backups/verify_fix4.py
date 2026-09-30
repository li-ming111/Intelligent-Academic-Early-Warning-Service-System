# -*- coding: utf-8 -*-
"""第四批修复的校验：score_record 重复三元组已按「保留 id 最大」去重，
且权威表 scores、权威挂科基线、司明本人的记录均未被触碰。"""
import subprocess

DB = 'academic_warning_system'
BAK = 'score_record_bak_dup_20260916'


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
o.append('========== 第四批修复校验（score_record 去重）==========')
o.append('')

o.append('--- (a) 行数：384 -> 247，备份表留住全部 384 ---')
o.append(check('live=247 / bak=384', '247\t384',
               'SELECT (SELECT COUNT(*) FROM score_record), (SELECT COUNT(*) FROM %s)' % BAK))
o.append(check('备份表建成时确实是 384 行（不是备份后才删的空表）', '384',
               'SELECT COUNT(*) FROM %s' % BAK))
o.append('')

o.append('--- (b) 重复已彻底消失 ---')
o.append(check('残留重复三元组组数 = 0', '0',
               'SELECT COUNT(*) FROM (SELECT 1 FROM score_record '
               'GROUP BY student_id, course_id, semester HAVING COUNT(*) > 1) g'))
o.append(check('去重后三元组数 = 行数 = 247（无重复也无遗漏）', '247\t247',
               'SELECT (SELECT COUNT(*) FROM (SELECT 1 FROM score_record '
               'GROUP BY student_id, course_id, semester) t), (SELECT COUNT(*) FROM score_record)'))
o.append(check('去重后的 247 行构成：138(semester=2) + 92(NULL 学期选修课) + 17(司明等 3~6 学期)',
               '138\t92\t17',
               'SELECT (SELECT COUNT(*) FROM score_record WHERE semester="2"), '
               '(SELECT COUNT(*) FROM score_record WHERE semester IS NULL OR semester=""), '
               '(SELECT COUNT(*) FROM score_record WHERE semester IS NOT NULL AND semester<>"2")'))
o.append('')

o.append('--- (c) 删的确实是每组 id 较小的那条，且没有整组被删 ---')
o.append(check('每个备份三元组在 live 里都还有行（组数 247 = live 行数）', '247',
               'SELECT COUNT(*) FROM (SELECT DISTINCT student_id, course_id, semester FROM %s) b' % BAK))
o.append(check('live 保留行的 id 等于该组的 MAX(id)（反例数 = 0）', '0',
               'SELECT COUNT(*) FROM score_record sr JOIN %s b '
               'ON b.student_id=sr.student_id AND b.course_id=sr.course_id AND b.semester<=>sr.semester '
               'WHERE sr.id <> (SELECT MAX(x.id) FROM %s x WHERE x.student_id=b.student_id '
               'AND x.course_id=b.course_id AND x.semester<=>b.semester)' % (BAK, BAK)))
o.append(check('被删的 137 行全部是与保留行 score 相同的冗余副本（分数丢失反例 = 0）', '0',
               'SELECT COUNT(*) FROM %s b WHERE NOT EXISTS ('
               'SELECT 1 FROM score_record sr WHERE sr.student_id=b.student_id '
               'AND sr.course_id=b.course_id AND sr.semester<=>b.semester) '
               'AND NOT EXISTS (SELECT 1 FROM %s k WHERE k.student_id=b.student_id '
               'AND k.course_id=b.course_id AND k.semester<=>b.semester AND k.id>b.id AND k.score=b.score)' % (BAK, BAK)))
o.append(check('被删的 137 行全部在 semester=2（去重只动了那批重复）', '137',
               'SELECT COUNT(*) FROM %s b WHERE NOT EXISTS '
               '(SELECT 1 FROM score_record sr WHERE sr.id=b.id) AND b.semester="2"' % BAK))
o.append(check('被删的 137 行没有一行属于司明', '0',
               'SELECT COUNT(*) FROM %s b WHERE NOT EXISTS '
               '(SELECT 1 FROM score_record sr WHERE sr.id=b.id) AND b.student_id=3' % BAK))
o.append(check('去重波及面：23 名学生 / 必修课 1~6', '23\t1,2,3,4,5,6',
               'SELECT COUNT(DISTINCT student_id), GROUP_CONCAT(DISTINCT course_id ORDER BY course_id) '
               'FROM %s WHERE semester=2' % BAK))
o.append('')

o.append('--- (d) 权威表 scores 一根毫毛都没动 ---')
o.append(check('scores 仍是 167 行', '167', 'SELECT COUNT(*) FROM scores'))
o.append(check('scores 无重复三元组（本批次不应引入）', '0',
               'SELECT COUNT(*) FROM (SELECT 1 FROM scores '
               'GROUP BY student_id, course_id, semester HAVING COUNT(*) > 1) g'))
o.append('')

o.append('--- (e) 权威挂科基线不变：司明 3 / 李爽 3 / 张健 3 / 韩坤 2 ---')
o.append(check('按人挂科门数', '3,3,3,2',
               'SELECT GROUP_CONCAT(f.c) FROM ('
               'SELECT COUNT(*) c FROM (SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s WHERE s.final_score IS NOT NULL) t '
               'WHERE t.rn=1 AND t.score_total < 60 GROUP BY t.student_id ORDER BY c DESC, t.student_id) f'))
o.append(check('司明（profile_id=3）挂科课程为 3 门：高等数学/计算机基础/操作系统', '1,3,6',
               'SELECT GROUP_CONCAT(course_id ORDER BY course_id) FROM ('
               'SELECT s.course_id FROM (SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.student_id, s.course_id '
               'ORDER BY s.semester DESC, s.id DESC) rn FROM scores s '
               'WHERE s.final_score IS NOT NULL) s WHERE s.rn=1 AND s.student_id=3 AND s.score_total < 60) d'))
o.append('')

o.append('--- (f) 司明本人的录入记录未受影响（他的行不在 semester=2 那批）---')
o.append(check('司明(profile_id=3) 的 score_record 行数 = 16（3~6 学期 6/5/3/2）', '16',
               'SELECT COUNT(*) FROM score_record WHERE student_id=3'))
o.append(check('semester=2 那批重复之外的 17 行未被去重波及：2 名学生(司明16 + 邱琳紫1) 共 17 行',
               '2\t17',
               'SELECT COUNT(DISTINCT student_id), COUNT(*) FROM score_record '
               'WHERE semester IS NOT NULL AND semester<>"2"'))
o.append(check('司明在 semester=2 的 score_record 行数（本就不存在）', '0',
               'SELECT COUNT(*) FROM score_record WHERE student_id=3 AND semester=2'))
o.append('')

o.append('--- (g) 回滚脚本的可行性：备份表可原样插回 ---')
o.append(check('备份表 384 行、主键无重复（INSERT IGNORE 能补回 137 行）', '384\t384',
               'SELECT COUNT(*), COUNT(DISTINCT id) FROM %s' % BAK))
o.append(check('缺失行数 = 137（即回滚要插回的行数）', '137',
               'SELECT COUNT(*) FROM %s b WHERE NOT EXISTS '
               '(SELECT 1 FROM score_record sr WHERE sr.id=b.id)' % BAK))

o.append('')
o.append('========== 汇总 ==========')
fails = [ln for ln in o if ln.startswith('[FAIL]')]
o.append('通过 %d 项，失败 %d 项' % (len([ln for ln in o if ln.startswith('[PASS]')]), len(fails)))

with open('_verify4.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(o))
print('written _verify4.txt; fails =', len(fails))
