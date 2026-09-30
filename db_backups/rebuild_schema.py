# -*- coding: utf-8 -*-
"""用线上库的权威结构重建 init_schema.sql。

背景：init_schema.sql 与线上库长期脱节 —— 幽灵表（warning_rule/student/academic_warnings）、
缺失表（college_admin_profile/login_logs/report_templates/score_audit_log）、
缺列的 score_record/student_profile/assistance_plans，以及 MySQL 8 根本不支持的
`CREATE INDEX IF NOT EXISTS`（每条都会执行失败，全新库上等于没有索引）。

做法：以 mysqldump --no-data 的输出为唯一权威，按原文件的**分节顺序**重排；
原文件里的分节注释尽量保留，便于对照阅读。
"""
import io
import re
import sys

SRC = 'academic-microservices/academic-common/src/main/resources/init_schema.sql'
DUMP = 'db_backups/schema_dump_20260916.sql'
OUT = 'academic-microservices/academic-common/src/main/resources/init_schema.sql'

src = io.open(SRC, encoding='utf-8').read()
dump = io.open(DUMP, encoding='utf-8').read()

# ---------- 1. 解析 dump：表名 -> 规范化的 DDL ----------
TABLES = {}
for m in re.finditer(r'CREATE TABLE `([a-z_0-9]+)` \((.*?)\n\) ENGINE=InnoDB([^;]*);',
                     dump, re.S):
    t, body, tail = m.group(1), m.group(2), m.group(3)
    # 排除历史快照表与临时表。**前缀式和后缀式都要拦** ——
    # 早先只判 `'_bak_' in t`，那只覆盖 `score_record_bak_align_20260916` 这种中缀写法；
    # 后来攒下来的 `bak_fix10_classes` / `tmp_f10_class` 是前缀式，
    # 会被当成业务表写进 init_schema.sql（实测多出 21 张，已回滚重跑）。
    if '_bak_' in t or t.startswith('bak_') or t.startswith('tmp_'):
        continue
    body_lines = []
    for ln in body.split('\n'):
        ln = ln.rstrip()
        if not ln.strip():
            continue
        # 列级 COLLATE 与表默认一致，纯冗余，去掉让行更短
        ln = ln.replace(' COLLATE utf8mb4_unicode_ci', '')
        ln = ln.replace(' COLLATE utf8mb4_general_ci', '')
        # dump 里的 KEY 名与列名保留原样
        body_lines.append('    ' + ln.strip())
    table_comment = ''
    cm = re.search("COMMENT='([^']*)'", tail)
    if cm:
        table_comment = " comment '%s'" % cm.group(1)
    # dump 的每一行本身就以逗号结尾，直接用换行拼接，不能再加逗号
    TABLES[t] = ('CREATE TABLE IF NOT EXISTS `%s` (\n%s\n'
                 ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci%s;'
                 % (t, '\n'.join(body_lines), table_comment))

print('dump 表数: %d' % len(TABLES), file=sys.stderr)

# ---------- 2. 解析原文件：抽出每个表块及其前后的注释 ----------
lines = src.split('\n')
sections = []          # [(comment_text, table_name)]
pending = []
i = 0
while i < len(lines):
    ln = lines[i]
    m = re.match(r'CREATE TABLE IF NOT EXISTS `([a-z_0-9]+)`', ln)
    if m:
        tname = m.group(1)
        sections.append(('\n'.join(pending).strip('\n'), tname))
        pending = []
        # 跳过整个表块
        while not re.match(r'^\)[^;]*;\s*$', lines[i]):
            i += 1
        i += 1
        # 跳过紧随其后的 CREATE INDEX 行（索引已内联进 CREATE TABLE）
        while i < len(lines) and re.match(
                r'CREATE INDEX IF NOT EXISTS `[a-z_0-9]+` ON `%s`' % re.escape(tname),
                lines[i]):
            i += 1
        continue
    pending.append(ln)
    i += 1
trailing = '\n'.join(pending).strip('\n')

# ---------- 3. 决定顺序 ----------
# 原顺序里去掉幽灵表；原文件没有的表按依赖关系补在合适位置
GHOSTS = [t for _, t in sections if t not in TABLES]
order = [t for _, t in sections if t in TABLES]
extra = [t for t in sorted(TABLES) if t not in order]
print('幽灵表(删除): %s' % ','.join(GHOSTS), file=sys.stderr)
print('新增表: %s' % ','.join(extra), file=sys.stderr)

# 新表插到所属分节之后，避免破坏可读性
PLACEMENT = {
    'college_admin_profile': 'counselor_profiles',
    'login_logs': 'security_logs',
    'report_templates': 'export_history',
    'score_audit_log': 'score_appeals',
    # 与 student_settings/subscription_preferences 同属"用户自助设置"一节，
    # 但键是 users.id（那两个键 student_profile.id），排在旁边便于对照。
    'user_settings': 'student_settings',
}

comments = dict((t, c) for c, t in sections)
for t, after in PLACEMENT.items():
    if t in extra and after in order:
        order.insert(order.index(after) + 1, t)
        extra.remove(t)
order.extend(extra)   # 兜底：仍未安置的追加到末尾

# 幽灵表所在分节的注释要保留（新表可能要挂在它下面）
ghost_sections = [c for c, t in sections if t in GHOSTS]
used_comments = set()

# ---------- 4. 输出 ----------
head = src[:src.index('CREATE TABLE IF NOT EXISTS `%s`' % sections[0][1])]
# 老标题写着"与 academic_warning_system.sql 完全对齐"，实际两处都早已脱节。整块换成事实描述；
# 只保留紧贴在第一个表前面的那段分节注释（"-- ==== 基础表 ===="）。
HONEST_HEADER = """-- ========================================
-- 智能学业预警服务系统 - 完整建表脚本
-- 由线上库结构导出（mysqldump --no-data）后生成，是全新库的唯一权威结构。
-- 与线上库不一致时以线上库为准重新生成，不要手工改本文件。
-- 索引内联在 CREATE TABLE 中：MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，
-- 写成独立语句只会静默失败，让全新库上的表没有索引。
-- 使用 CREATE TABLE IF NOT EXISTS，多服务并行启动安全幂等。
--
-- 前置条件：本脚本只建表，不建库（不含 CREATE DATABASE / USE），各服务的
-- JDBC URL 也没有 createDatabaseIfNotExist=true。因此在一台没有该库的 MySQL 上，
-- 服务连数据库时就已失败，DataInitializer 根本没机会执行本脚本。
-- 全新安装必须手工先建库：
--     CREATE DATABASE academic_warning_system
--       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ========================================
"""
hl = head.split('\n')
end = len(hl)
while end > 0 and hl[end - 1].strip() == '':
    end -= 1
start = end
while start > 0 and hl[start - 1].lstrip().startswith('--'):
    start -= 1
section = '\n'.join(hl[start:end]).strip('\n')
head = HONEST_HEADER + ('\n' + section + '\n' if section else '')
out = [head.rstrip('\n'), '']
# 第一张表之前的注释已经随 head 输出了，标记为已用，否则循环里会再输出一遍
if order:
    used_comments.add(comments.get(order[0], ''))

for t in order:
    c = comments.get(t, '')
    # 幽灵表分节的注释搬给紧随其后的新表（如 warning_rule 的注释让给别的表没有意义，
    # 所以只在原位置没有任何表时才复用）
    if c and c not in used_comments:
        out.append(c)
        out.append('')
        used_comments.add(c)
    out.append(TABLES[t])
    out.append('')

if trailing.strip():
    out.append(trailing)

io.open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(out).rstrip('\n') + '\n')
print('已写入 %s，表数 %d' % (OUT, len(order)), file=sys.stderr)
