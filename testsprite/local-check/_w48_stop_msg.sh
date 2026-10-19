#!/bin/bash
# W48：把刚才为诊断 W48-WS 缺陷而**临时**起的 message-service(8077) 收掉。
# 该服务平时不参与任何 PC/小程序走查（走查只起 admin/ai/counselor/teacher/student/
# warning 那几个），留着会一直占 ~500MB。**诊断结论已落文档，不依赖它常驻。**
#
# 用法: _w48_stop_msg.sh
set -u
PID=$(netstat -ano 2>/dev/null | grep LISTENING | grep ":8077" | head -1 | awk '{print $NF}')
if [ -z "$PID" ]; then echo "8077 没在监听（已经收掉了）"; exit 0; fi
echo "杀掉 message-service pid=$PID"
cmd //c "taskkill /F /PID $PID" 2>&1 | head -3
sleep 3
if netstat -ano 2>/dev/null | grep -q LISTENING | grep -q ":8077"; then
  echo "⚠️ 8077 仍在监听 —— 手动确认一下"
else
  echo "✅ 8077 已释放"
fi
