#!/bin/bash
# W48 辅助脚本：给每次 xcx-page-smoke 跑一份**全新的** H5 dev server。
#
# 为什么要这样包一层：本机 Node v24 + Windows 上，uni/vite dev server 会在
# 无头 Chrome 突然断开时抛 `ECONNRESET`（socket 'read'）并**整个进程退出**。
# 表现是：第一次跑绿，第二次直接 `landed=false`（因为 5180 已经没人监听），
# 很容易被读成"登录坏了"。这不是产品缺陷，是 dev server 自己的稳定性问题
# （同 [[abs-build-and-harness-false-negatives]] 那一族）。
#
# 用法: _w48_run_smoke.sh <role>
set -u
ROLE="${1:?用法: _w48_run_smoke.sh <role>}"
XCX=/d/ABS/academic-warning-xcx
LOG="/tmp/w48_srv_${ROLE}.log"

cd "$XCX" || exit 1
npx uni --port 5180 > "$LOG" 2>&1 &
SRV=$!
# 起完就回到脚本自己所在目录：xcx-page-smoke.mjs 把产物写成**相对路径**
# `xcx-pages-<role>.txt`，留在 xcx 目录里跑会把产物扔到那儿去（W48 踩到：
# 三份产物散落在 academic-warning-xcx/，让人误以为"又验了一遍"）。
cd /d/ABS/testsprite/local-check || exit 1

# 等端口（最多 90s）；用 bash 的 /dev/tcp 探，不依赖 curl
for i in $(seq 1 90); do
  if (exec 3<>/dev/tcp/127.0.0.1/5180) 2>/dev/null; then exec 3<&-; break; fi
  sleep 1
done
if ! (exec 3<>/dev/tcp/127.0.0.1/5180) 2>/dev/null; then
  echo "❌ dev server 90s 内没起来，日志尾部："; tail -20 "$LOG"; kill $SRV 2>/dev/null; exit 1
fi
exec 3<&-

sleep 4   # 让 vite 完成首次预构建
node /d/ABS/testsprite/local-check/xcx-page-smoke.mjs "$ROLE"
RC=$?

kill $SRV 2>/dev/null
wait $SRV 2>/dev/null
exit $RC
