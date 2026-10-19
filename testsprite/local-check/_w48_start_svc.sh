#!/bin/bash
# W48 辅助脚本：用「直连 java -cp」的方式起一个微服务。
#
# 为什么要这样起（而不是 mvn spring-boot:run 或 java -jar）：
#   · 本仓的 jar 可能是**旧构建**（target/*.jar 停在几天前），`java -jar` 跑的是旧代码；
#     而 target/classes 是刚刚 `mvn compile` 出来的 ⇒ 直接 `-cp target/classes` 跑的是新代码。
#   · mvn spring-boot:run 会再 fork 一层，TaskStop 只杀得到 mvn 外壳、java 子进程会活着占端口
#     （见 [[abs-testsprite-verification]] 里记的那条）。
#
# ⚠️ `academic-common/target/classes` **必须前置**在 -cp 最前面：本地仓库里那份 common jar 是旧的，
#    不加会「找不到符号 / 启动失败」（见 [[abs-build-and-harness-false-negatives]]）。
#
# 用法: _w48_start_svc.sh <模块目录名> <主类全名> <日志标签>
set -u
MOD="${1:?用法: _w48_start_svc.sh <module> <MainClass> <tag>}"
MAIN="${2:?}"
TAG="${3:?}"
MS=/d/ABS/academic-microservices
# ⚠️ -cp 里必须用 **Windows 路径**（D:/ABS/...）：java.exe 是 Windows 程序，
#    认不出 MSYS 的 `/d/ABS/...`（症状是 ClassNotFoundException: ...Application，
#    看着像"类没编译"，其实是 classpath 根本没指到那块盘）。W48 踩到。
MSW=D:/ABS/academic-microservices
LOG="/tmp/w48_${TAG}.log"

cd "$MS/$MOD" || exit 1
[ -f target/cp.txt ] || { echo "❌ $MOD/target/cp.txt 不存在，先跑 mvn dependency:build-classpath"; exit 1; }
# cp.txt 里的依赖本身已是 Windows 反斜杠路径；只需把本工程的两段也写成 Windows 形式
CP="$MSW/academic-common/target/classes;$(cat target/cp.txt);$MSW/$MOD/target/classes"

JAVA=/d/JAVA/jdk/bin/java.exe
[ -x "$JAVA" ] || JAVA=java

"$JAVA" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
  -cp "$CP" "$MAIN" > "$LOG" 2>&1 &

PID=$!
echo "$PID" > "/tmp/w48_${TAG}.pid"
echo "started $TAG pid=$PID -> $LOG"
