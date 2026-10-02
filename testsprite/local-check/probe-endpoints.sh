#!/bin/bash
# 端点实测：对每个候选路径分别用 users.id 与 student_profile.id 打一次，
# 输出 HTTP 状态 + 返回条数/顶层键，用来判定"这个端点收哪个 id 空间"。
#
# 背景：student-service 的控制器基路径是 /students，所以申诉/对标/通知/评价
# 全部在 /api/students/** 之下，不存在 /api/appeals/** 这类顶层路由（网关没配）。
# 而且同一个控制器里两种 id 混用（scores 返回的 studentId 是 profile id，
# 路径参数却是 users.id），只能实测。
BASE=http://localhost:8076/api
UID_=27      # users.id   (司明 / 2023020616)
PID=3        # student_profile.id

login() {
  # W1.12：登录要带服务端签发的验证码（先 GET /auth/captcha，再把 id 与明文一起带上），
  # 否则一律回 code:500「验证码错误或已过期」——表现成"登录失败"，其实是少带两个字段。
  local cap id code
  cap=$(curl -s "$BASE/auth/captcha" --max-time 10)
  id=$(printf '%s' "$cap" | sed -n 's/.*"captchaId":"\([^"]*\)".*/\1/p')
  code=$(printf '%s' "$cap" | sed -n 's/.*"captchaText":"\([^"]*\)".*/\1/p')
  curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"123456\",\"captchaId\":\"$id\",\"captcha\":\"$code\"}" --max-time 10 \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

S=$(login 2023020616)
if [ -z "$S" ]; then echo "登录失败，拿不到 token"; exit 1; fi
H="Authorization: Bearer $S"

# 探测单条：$1 = 方法(GET/POST 之外只做 GET)，$2 = 完整路径
probe() {
  local label="$1" path="$2"
  local out code
  out=$(curl -s -w '\n%{http_code}' -H "$H" "$BASE$path" --max-time 10)
  code=$(printf '%s' "$out" | tail -1)
  local body
  body=$(printf '%s' "$out" | sed '$d')
  local n
  n=$(printf '%s' "$body" | grep -o '"id"' | wc -l | tr -d ' ')
  local keys
  keys=$(printf '%s' "$body" | head -c 200 | grep -oE '"(data|code|message)"' | sort -u | tr '\n' ',' )
  printf '%-9s %-52s n=%-3s %s\n' "$code" "$path" "$n" "$keys"
}

echo "=================== 用 users.id=27 ==================="
for p in \
  "/students/dashboard/27" \
  "/students/scores/27" \
  "/students/warnings/27" \
  "/students/assistance/27" \
  "/students/assistance-plans/27" \
  "/students/appeals/27/list" \
  "/students/appeals/27/statistics" \
  "/students/benchmark/27/latest" \
  "/students/benchmark/27/class-ranking" \
  "/students/class-ranking/27" \
  "/students/class-members/27" \
  "/students/notification-center/27/list" \
  "/students/notification-center/27/unread-count" \
  "/students/evaluations/27/list" \
  "/students/messages/27" \
  "/students/messages/27/unread-count" \
  "/students/settings/27" \
  "/students/subscription/27/preferences" \
  "/students/student-by-user/27" \
  "/students/info/27" \
  "/students/27" \
  "/students/27/gpa" \
  "/students/27/class-info" \
  "/students/suggestions/27" \
  ; do probe x "$p"; done

echo ""
echo "=================== 用 student_profile.id=3 ==================="
for p in \
  "/students/dashboard/3" \
  "/students/scores/3" \
  "/students/warnings/3" \
  "/students/assistance/3" \
  "/students/assistance-plans/3" \
  "/students/appeals/3/list" \
  "/students/benchmark/3/latest" \
  "/students/class-ranking/3" \
  "/students/notification-center/3/list" \
  "/students/evaluations/3/list" \
  "/students/messages/3" \
  "/students/settings/3" \
  "/students/student-by-user/3" \
  "/students/info/3" \
  "/students/3" \
  "/students/3/gpa" \
  ; do probe x "$p"; done
