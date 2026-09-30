#!/bin/bash
# 学院管理员删号分支验证 —— 复核（只读）。跑在 admin-role5-delete-probe.mjs 之后。
#
# 两件事：
#   1) 把 users / college_admin_profile 重新 dump 一遍，去掉 AUTO_INCREMENT= 行之后
#      与验证前的快照**逐字节 diff**；
#   2) 跑 admin_role5_delete_20260929_verify.sql 的逐字段断言。
#
# 为什么要第 1 步：下面的断言字段是我自己挑的，写漏一个就查不出来；
# 逐字节 diff 是"跟从前一模一样"这种更难糊弄的说法。
#
# 用法：bash db_backups/admin_role5_delete_20260929_check.sh
set -u

MYSQL_BIN="/d/JAVA/mysql/mysql-8.0.44-winx64/mysql-8.0.44-winx64/bin"
DB="academic_warning_system"
DIR="$(cd "$(dirname "$0")" && pwd)"
BEFORE="$DIR/admin_role5_delete_20260929_before.sql"
AFTER="$(mktemp)"

if [ ! -f "$BEFORE" ]; then
  echo "FAIL 找不到验证前的快照：$BEFORE"
  exit 1
fi

# 与生成快照时**完全相同的命令**（不额外加 charset 参数），否则比对的是命令差异不是数据差异
"$MYSQL_BIN/mysqldump.exe" -uroot -proot --single-transaction --skip-comments \
  "$DB" users college_admin_profile > "$AFTER" 2>/dev/null

grep -v 'AUTO_INCREMENT=' "$BEFORE" > "$AFTER.before"
grep -v 'AUTO_INCREMENT=' "$AFTER"  > "$AFTER.now"

echo "=== 一、与验证前的快照逐字节比对（忽略 AUTO_INCREMENT= 行）==="
if diff -q "$AFTER.before" "$AFTER.now" > /dev/null; then
  echo "PASS  users / college_admin_profile 与验证前逐字节相同（行内容完全复原）"
else
  echo "FAIL  有差异："
  diff "$AFTER.before" "$AFTER.now" | head -40
fi

echo
echo "=== 二、自增计数器的差异（只记录，不是数据差异）==="
if diff -q "$BEFORE" "$AFTER" > /dev/null; then
  echo "PASS  连同计数器也逐字节相同"
else
  diff "$BEFORE" "$AFTER" | grep -i 'AUTO_INCREMENT' | head -10
  echo "NOTE  上面是自增计数器前移。验证过程真的插过行（临时账号 + 手工档案行），"
  echo "      计数器不会回退；行内容已在上一步证明完全复原。"
fi

echo
echo "=== 三、逐字段断言 ==="
"$MYSQL_BIN/mysql.exe" -uroot -proot --default-character-set=utf8mb4 -t "$DB" \
  < "$DIR/admin_role5_delete_20260929_verify.sql" 2>/dev/null

rm -f "$AFTER" "$AFTER.before" "$AFTER.now"
