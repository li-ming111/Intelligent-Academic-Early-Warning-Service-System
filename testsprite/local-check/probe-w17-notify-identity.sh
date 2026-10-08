#!/usr/bin/env bash
# W17 —— POST /counselors/students/notify 的**发送人身份**取哪来 (2026-10-04)
#
# 病根: 控制器 W13 那道闸只读 body 的 `counselor_id`(下划线),
#       而**三个真实调用方**发的都是 `counselorId`(驼峰)
#       (CounselorNotifications.vue / CounselorCourses.vue / CounselorStudents.vue),
#       服务层 `firstLong(counselorId, counselor_id)` 又恰好**驼峰优先**
#       => 守卫整个被跳过, 身份照样被采纳 (冒名群发 + 记录记到别人头上)。
#
# 硬约束: **零写入**。本脚本所有请求都会被服务层在**插入之前**拒掉:
#    修后 403(闸) 或 400(解析不出收件人)。**不碰真实演示数据**。
#    刻意**不**做"真发出去"的正向用例 —— 那会往司明等人的通知中心里写行。
#    正向(本人应放行)由单测覆盖(mock, 不碰库)。
#
# 用法: bash probe-w17-notify-identity.sh
#   OLD_PORT=8074 (现役旧 jar, 未打包 => 修前的天然对照组)
#   NEW_PORT=8189 (以当前源码从 target/classes 起的备用端口)
set -u
OLD_PORT="${OLD_PORT:-8074}"
NEW_PORT="${NEW_PORT:-8189}"
BASE="/counselors/students/notify"

say() { printf '%s\n' "$*"; }

# 打一发, 只打印 "HTTP 码 + body 里的 code/message"。$2 是**原样传给 curl 的 -H 串**。
hit() { # $1=port $2=headers $3=json
  local out http body code msg
  out=$(eval curl -s -m 10 -w "'\n@@HTTP=%{http_code}'" -X POST \
        "http://localhost:$1$BASE" -H "'Content-Type: application/json'" \
        $2 --data-binary "'$3'" 2>&1)
  http=$(printf '%s' "$out" | sed -n 's/.*@@HTTP=\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@HTTP=[0-9]*$//')
  code=$(printf '%s' "$body" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')
  msg=$(printf '%s' "$body" | sed -n 's/.*"message"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  printf 'HTTP=%-4s body.code=%-5s msg=%s\n' "${http:-?}" "${code:-none}" "${msg:-<empty body>}"
}

both() { # $1=说明 $2=headers $3=json
  say "  [$1]"
  printf '    old(%s) ' "$OLD_PORT"; hit "$OLD_PORT" "$2" "$3"
  printf '    new(%s) ' "$NEW_PORT"; hit "$NEW_PORT" "$2" "$3"
}

ME='-H "X-User-Id: 53" -H "X-Role: 3"'
STRANGER='-H "X-User-Id: 9999" -H "X-Role: 3"'

say "=== W17: 发通知的发送人身份 -- old=$OLD_PORT(修前) vs new=$NEW_PORT(修后) ==="
say ""

say "A. 陌生人用 body 的 counselorId(驼峰, bogus id) 冒名   [零写入: 99999 名下没人]"
both "camelCase spoof" "$STRANGER" '{"counselorId":99999,"recipients":["all"],"content":"probe"}'
say "   ^ 修前 400 = 身份被采纳, 只是没学生可发(闸没装); 修后 403 = 拦在发之前"
say ""

say "B. 陌生人用 body 的 counselorId(驼峰, = 真正的 53) 冒名, 收件人指向不存在的学生  [零写入]"
both "camelCase spoof real id" "$STRANGER" '{"counselorId":53,"student_ids":[999999],"content":"probe"}'
say "   ^ 修前 400 找不到学生(它已经拿 53 去解析名册了); 修后 403"
say ""

say "C. 陌生人用 body 的 counselor_id(下划线, bogus)   [W13 那道闸唯一覆盖的拼写]"
both "snake_case spoof" "$STRANGER" '{"counselor_id":99999,"recipients":["all"],"content":"probe"}'
say "   ^ 实测 old 回的是 400 不是 403 => 现役 8074 是 **W13 之前的包**, 连下划线那道闸都没有"
say "     (这也解释了为什么它是最好的修前对照: 它一个身份检查都没有)"
say ""

say "D. 负向对照: 本人(53) 不带身份, 收件人指向不存在的学生   [零写入, 证明没有过度拦截]"
both "own, no body identity" "$ME" '{"student_ids":[999999],"content":"probe"}'
say "   ^ 期望 400 找不到学生(不是 403) -- 本人必须能走到解析收件人那一步"
say ""

say "F. 陌生人(9999) 不带身份, 收件人指向**真实学生**(student_profile.id=3 司明, 挂在 53 的班)"
say "   [只在修后跑: 打老 jar 会把通知真发给司明 => 违反零写入红线]"
printf '    new(%s) ' "$NEW_PORT"; hit "$NEW_PORT" "$STRANGER" '{"student_ids":[3],"content":"probe"}'
say "   ^ 必须 400 且 msg 里点名『不在该辅导员名下，已跳过』:"
say "     这句只有 roster != null 时才执行 => 直接证明『缺身份时回填了本人 id』"
say "     若没有回填, roster==null, 这句不执行, 学生被加入 => **200 且真发出去**"
say ""

say "E. 完全没有身份头"
printf '    new(%s) 只有 X-Role: ' "$NEW_PORT"; hit "$NEW_PORT" '-H "X-Role: 3"' '{"counselorId":53,"content":"probe"}'
printf '    new(%s) 没有任何头: ' "$NEW_PORT"; hit "$NEW_PORT" '' '{"counselorId":53,"content":"probe"}'
say "   ^ GatewayAuthFilter 在控制器之前就拦(403 空体), 属于『下游拒绝』形状"
say ""
say "=== 结束。任何一条 old/new 回 200 就说明真有通知发出去了 -- 不该发生, 请立即查库 ==="
