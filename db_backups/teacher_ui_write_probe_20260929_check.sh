#!/bin/bash
# 教师端界面写路径验证 —— 回滚后的**机器复核**（只读）。
#
# 做两件事：
#   1) 把 score_record / scores / score_audit_log 重新 dump 一遍，
#      去掉 AUTO_INCREMENT= 行之后与验证前的快照**逐字节 diff**；
#   2) 跑 verify.sql 里的逐字段断言。
#
# 为什么要有第 1 步：verify.sql 是我自己写的断言，写漏一个字段就查不出来；
# 逐字节 diff 是"跟快照一模一样"这种更难糊弄的说法。
#
# 用法：bash db_backups/teacher_ui_write_probe_20260929_check.sh
set -u

MYSQL_BIN="/d/JAVA/mysql/mysql-8.0.44-winx64/mysql-8.0.44-winx64/bin"
DB="academic_warning_system"
DIR="$(cd "$(dirname "$0")" && pwd)"
BEFORE="$DIR/teacher_ui_write_probe_20260929_before.sql"
AFTER="$(mktemp)"

if [ ! -f "$BEFORE" ]; then
  echo "FAIL 找不到验证前的快照：$BEFORE"
  exit 1
fi

"$MYSQL_BIN/mysqldump.exe" -uroot -proot --default-character-set=utf8mb4 --skip-comments \
  "$DB" score_record scores score_audit_log > "$AFTER" 2>/dev/null

# AUTO_INCREMENT 是唯一允许不同的地方（口径：行内容逐字段相等，计数器除外）
grep -v 'AUTO_INCREMENT=' "$BEFORE" > "$AFTER.before"
grep -v 'AUTO_INCREMENT=' "$AFTER"  > "$AFTER.now"

echo "=== 一、与验证前的快照逐字节比对（忽略 AUTO_INCREMENT= 行）==="
if diff -q "$AFTER.before" "$AFTER.now" > /dev/null; then
  echo "PASS  三张表与验证前逐字节相同（行内容完全复原）"
else
  echo "FAIL  有差异："
  diff "$AFTER.before" "$AFTER.now" | head -40
fi

echo
echo "=== 二、逐字段断言 ==="
"$MYSQL_BIN/mysql.exe" -uroot -proot --default-character-set=utf8mb4 -t "$DB" \
  < "$DIR/teacher_ui_write_probe_20260929_verify.sql" 2>/dev/null

rm -f "$AFTER" "$AFTER.before" "$AFTER.now"
