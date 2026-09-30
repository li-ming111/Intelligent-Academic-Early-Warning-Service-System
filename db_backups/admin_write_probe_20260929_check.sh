#!/bin/bash
# 管理员端写路径验证 —— 回滚后的**机器复核**（只读）。
#
# 与教师端那次同样的两件事：
#   1) 把 users / teacher_profile / messages 重新 dump 一遍，
#      去掉 AUTO_INCREMENT= 行之后与验证前的快照**逐字节 diff**；
#   2) 跑 verify.sql 里的逐字段断言。
#
# 为什么要第 1 步：verify.sql 的断言是我自己挑字段写的，写漏一个就查不出来；
# 逐字节 diff 是"跟从前一模一样"这种更难糊弄的说法。
#
# 用法：bash db_backups/admin_write_probe_20260929_check.sh
set -u

MYSQL_BIN="/d/JAVA/mysql/mysql-8.0.44-winx64/mysql-8.0.44-winx64/bin"
DB="academic_warning_system"
DIR="$(cd "$(dirname "$0")" && pwd)"
BEFORE="$DIR/admin_write_probe_20260929_before.sql"
AFTER="$(mktemp)"

if [ ! -f "$BEFORE" ]; then
  echo "FAIL 找不到验证前的快照：$BEFORE"
  exit 1
fi

"$MYSQL_BIN/mysqldump.exe" -uroot -proot --default-character-set=utf8mb4 --skip-comments \
  "$DB" users teacher_profile messages > "$AFTER" 2>/dev/null

# AUTO_INCREMENT 是唯一允许不同的地方（口径：行内容逐字段相等，计数器除外 ——
# 本次连计数器也拨回了原位，所以下面还会额外比一次"不忽略计数器"的版本）
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
echo "=== 二、连自增计数器一起比对（本次特意把计数器也拨回原位）==="
if diff -q "$BEFORE" "$AFTER" > /dev/null; then
  echo "PASS  连同计数器也逐字节相同"
else
  echo "NOTE  计数器有差异（不影响数据，仅记录）："
  diff "$BEFORE" "$AFTER" | grep -i 'AUTO_INCREMENT' | head -10
fi

echo
echo "=== 三、逐字段断言 ==="
"$MYSQL_BIN/mysql.exe" -uroot -proot --default-character-set=utf8mb4 -t "$DB" \
  < "$DIR/admin_write_probe_20260929_verify.sql" 2>/dev/null

rm -f "$AFTER" "$AFTER.before" "$AFTER.now"
