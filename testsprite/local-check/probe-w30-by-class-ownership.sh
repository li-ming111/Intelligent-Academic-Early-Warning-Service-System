#!/usr/bin/env bash
# W30 —— `GET /teachers/scores/by-class` 不传 `teacher_id` 那条分支的归属闸 (2026-10-08)
#
# 病根: 那条分支按班级名查**全班成绩**(含别位教师录的行), 此前一道校验都没有 ——
#   `teacher_id` 只在**传了**的时候才过 isSelf。W11 有意保留, 注释里给的理由是
#   「前端 useTeacherRoster 就是靠它拿姓名+分数的, 保持放行」。该理由**已被证伪**:
#     * useTeacherRoster 的消费方(小程序 services/api.js)**是带 teacher_id 的**;
#     * PC 端唯一不传 teacher_id 的 TeacherAnalysis.vue, 班级下拉来自 my-classes
#       (按 classes.teacher_id 限死) ⇒ 能选到的必然是自己带的班;
#     * teacher-service SecurityConfig 是 anyRequest().authenticated() —— **控制器只认
#       X-User-Id 头、不认角色**; 网关上 /api/teachers/** 有 role != 2 => false
#       (AuthenticationFilter:365), 管理员更上面已 return true。
#   ⇒ **经网关可达的严重性 = 任意教师(role 2)可读任意班的整份成绩单**。
#     ⚠️ 本探针**直连**服务端口(未过网关), 所以下面那条"学生"行证明的是
#     "控制器不认身份" —— 经网关时学生/辅导员**会被网关挡下**, 别把它读成
#     "任意已登录用户都能读"。直连之所以算数: 本仓服务均本机可直连, 网关不是网络边界。
#
# 判据(成对, 缺一条都不算过):
#   own   : 班主任(29 于凤海)拿自己的班   -> 必须 200 且**行数 > 0**(证明命中的是真数据)
#   cross : 非班主任(18 刘教授)拿同一个班 -> 必须被拒（★这条就是本轮的题眼）
#   cross : 学生(3, X-Role 1)拿同一个班   -> 控制器层也要拒(直连时的形状)
#   admin : X-Role 4 旁路                -> 200
#   ⚠️ 只测 cross 会把"接口整个坏了/班级名没对上"读成"修好了" —— 所以 own 那条
#      必须断言**行数 > 0**, 否则 0 行的 403 和"闸生效"在输出里长得一样。
#   ⚠️ 被拒的形状是 **HTTP 200 + body `code:403`**(控制器业务拒绝), 不是 HTTP 403 ——
#      判据必须读 body, 只读 HTTP 状态码会把这一条读成"通过"。
#   ⚠️ 中文班名**必须显式 UTF-8 百分号编码**: MSYS bash 里直接写中文会被按 GBK 发出去,
#      库里对不上 ⇒ 全班变 0 行, 看上去像"闸把所有人都拦了"(本轮亲历一次)。
#
# 用法: bash probe-w30-by-class-ownership.sh [PORT]   (默认 8190)
#   修前对照组 = 用 testsprite/local-check/_w30_mutate.py m1 把闸拆掉、重编、重起同一个端口。
set -u

PORT="${PORT:-8190}"
DB="${DB:-academic_warning_system}"
BASE="http://localhost:$PORT"

# 活库真值: classes.id=1 `计科2306班`, teacher_id=29(于凤海), 246 条 score_record。
# 教师 18(刘教授) 的班是 31/32, 与这个班不相交。
CLS='%E8%AE%A1%E7%A7%912306%E7%8F%AD'   # 计科2306班

say() { printf '%s\n' "$*"; }

row() { # $1=说明 $2=X-User-Id $3=X-Role
  local out http body code rows msg
  out=$(curl -s -m 15 -w '\n@@%{http_code}' "$BASE/teachers/scores/by-class?class_name=$CLS" \
        -H "X-User-Id: $2" -H "X-Role: $3")
  http=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//')
  code=$(printf '%s' "$body" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')
  rows=$(printf '%s' "$body" | grep -o '"studentId"' | wc -l | tr -d ' ')
  msg=$(printf '%s' "$body" | sed -n 's/.*"message":"\([^"]*\)".*/\1/p' | head -c 40)
  printf '  %-34s HTTP=%-4s body.code=%-4s rows=%-4s %s\n' "$1" "${http:-?}" "${code:-?}" "$rows" "$msg"
}

before=$(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e \
  "SELECT CONCAT((SELECT COUNT(*) FROM scores),'/',(SELECT COUNT(*) FROM score_record),'/',(SELECT COUNT(*) FROM notifications));" 2>/dev/null | tr -d '\r')

say "=== W30: /teachers/scores/by-class (不传 teacher_id) — port=$PORT ==="
say "  班级 = 计科2306班 (classes.id=1, 班主任 users.id=29), 库里 246 条 score_record"
say ""
row "own   班主任 29           [期望 200+行数>0]" 29 2
row "cross 非班主任 18        [期望 被拒]"        18 2
row "cross 学生 3 直连(X-Role 1)[期望 被拒]"      3 1
row "admin X-Role 4           [期望 200]"         1 4
say ""

out=$(curl -s -m 15 -w '\n@@%{http_code}' "$BASE/teachers/scores/by-class?class_name=$CLS")
say "  无身份头:  HTTP=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//' | head -c 60)"
say ""
say "  过滤分支(带别人的 teacher_id): "
out=$(curl -s -m 15 -w '\n@@%{http_code}' "$BASE/teachers/scores/by-class?class_name=$CLS&teacher_id=29" -H "X-User-Id: 18" -H "X-Role: 2")
say "    18 冒充 29: HTTP=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p') body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//' | head -c 70)"
say ""

after=$(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e \
  "SELECT CONCAT((SELECT COUNT(*) FROM scores),'/',(SELECT COUNT(*) FROM score_record),'/',(SELECT COUNT(*) FROM notifications));" 2>/dev/null | tr -d '\r')
if [ "$before" = "$after" ]; then
  say "=== 零写入自证: [OK] 行数不变 before=$after (scores/score_record/notifications) ==="
else
  say "=== 零写入自证: [!!] 行数变了! before=$before after=$after -- 立刻查库 ==="
fi
