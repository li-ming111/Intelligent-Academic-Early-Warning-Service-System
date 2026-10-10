#!/usr/bin/env bash
# W34 —— `GET /api/auth/info/{userId}`：一条**无归属校验的跨用户读**（2026-10-08）
#
# 病根：网关 `AuthenticationFilter.hasPermission` 对 `/api/auth/**` **没有任何前缀检查**
#   （`/api/auth` 既不匹配 PREFIX_ADMIN/TEACHER/STUDENT/COUNSELOR/COLLEGE_ADMIN，也不在
#   `/api/roles|permissions|role-permissions`、`/api/users/**`、`/api/courses/**` 那几段里），
#   内置角色 1/2/3 一路落到函数末尾的 `return true` ⇒ 已认证即放行。
#   下游 user-service 的 `SecurityConfig` 是 `anyRequest().authenticated()` ⇒ 同样不认角色。
#   而 `AuthController.getUserInfo` **不做任何归属判断** ⇒ **任何已登录用户（含学生）
#   可读任意 `userId` 的账号信息**（name / username / role / status）—— 还能用它枚举 userId。
#
#   与 `/api/users/info/{id}` 的对照：那条**两层都判**（网关 `USER_SELF_PATTERN` 必须 id==本人、
#   控制器 `isAdminOrSelf`），并且回**更多字段**（还带 email/phone）。`/auth/info` 是它
#   **无闸的孪生**，且**全仓零调用方**（两只眼睛：PC + 小程序封装调用点，均 0；页面字面路径 0）。
#   ⇒ 按本仓「零调用方 = 删」的第 N 例删掉端点本身（含它那一条**从不生效**的 Sentinel 规则）。
#
# ⚠️ 历史：2026-09-30 那轮修 `/api/users/**` 整段越权时**自己就写着**「只有 `/api/users/**`
#   和 `/api/auth/**` 这两段例外」，修的是前者；`/api/auth/info/{id}` 当时被**点名**（网关注释
#   「也会绕过归属校验」）却只用来**拦 role=5**，1/2/3 原样放行 —— 与 W30/W31 的"说法与事实"
#   是同一族（"我看到了，但只用它挡了另一件事"）。这是「同一个洞只修了一半」的又一例。
#
# 判据（成对，缺一条都不算过）：
#   leak : 学生(27) 打**别人**(18) 的 `/auth/info/18` → 修前必须**泄漏**（200 + 别人的 name/role）
#   self : 学生(27) 打**自己**的 `/auth/info/27` → 200（证明这条路径本身是通的，不是"整个坏了"）
#   ctrl : 同为学生(27) 打 `/users/info/18`（别人的资料）→ **403**（那条闸没坏，是很好的正对照）
#   fix  : 修后 `/auth/info/{任意}` → **404**（端点已删）；`/users/info/27` 仍 200（没有误伤）
#   ⚠️ 修后 `/auth/info/*` 的 404 是「已认证但无映射」；**匿名**打它仍是 **403**
#      （Spring Security 拦在映射之前）——两者**不是同一种**，别只断言"非 200"。
#   ⚠️ 被拒形状要分开：`/users/info/18` 是**HTTP 200 + body code:403**（控制器业务拒绝，
#      `isAdminOrSelf` → `ApiResponse.forbidden`），而匿名是 **HTTP 403 空体**。判据别混。
#
# 身份：JWT 用**工程自己配置的密钥**签（与 W25 同法），因为 `/auth/login` 需要验证码。
#   secret = `academic-jwt-secret-key-for-academic-warning-system-2026`（JwtConfig 的默认值）。
#
# 零写入自证：本探针**只读**（GET），users 行数 before == after。
#
# 用法: bash probe-w32-auth-info-idor.sh [PORT]   (默认 8082, 直连 user-service)
#   起被测实例：java -cp "academic-common/target/classes;user-service/target/classes;$(cat /tmp/uscp.txt)" \
#                     com.academic.UserServiceApplication --server.port=8082 --spring.cloud.nacos.discovery.enabled=false
set -u

PORT="${PORT:-8082}"
BASE="http://localhost:$PORT"
DB="${DB:-academic_warning_system}"
SECRET="academic-jwt-secret-key-for-academic-warning-system-2026"

TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT

# 学生 27（司明）/ 教师 18（刘教授），均为**活库真实账号**；本探针只读、不改它们一个字段。
STUDENT_ID=27; STUDENT_NAME=2023020616
OTHER_ID=18

say() { printf '%s\n' "$*"; }

mint() { # $1=uid $2=username $3=role  -> 打印 HS256 JWT
  python - "$SECRET" "$1" "$2" "$3" <<'PY'
import hmac,hashlib,base64,json,time,sys
secret,uid,username,role=sys.argv[1].encode(),int(sys.argv[2]),sys.argv[3],int(sys.argv[4])
b64=lambda b: base64.urlsafe_b64encode(b).rstrip(b"=")
h=b64(json.dumps({"alg":"HS256","typ":"JWT"},separators=(",",":")).encode())
now=int(time.time())
p=b64(json.dumps({"userId":uid,"username":username,"role":role,"sub":username,"iat":now,"exp":now+7200},separators=(",",":")).encode())
s=b64(hmac.new(secret,h+b"."+p,hashlib.sha256).digest())
sys.stdout.write((h+b"."+p+b"."+s).decode())
PY
}

# 读一行响应：$1=标签 $2=路径 $3=token(可空)  —— 打印 HTTP / body.code / body / name 字段
row() {
  local label="$1" path="$2" tok="${3:-}" out http body code name
  if [ -n "$tok" ]; then
    out=$(curl -s -m 10 -w '\n@@%{http_code}' "$BASE$path" -H "Authorization: Bearer $tok")
  else
    out=$(curl -s -m 10 -w '\n@@%{http_code}' "$BASE$path")
  fi
  http=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//')
  code=$(printf '%s' "$body" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')
  name=$(printf '%s' "$body" | sed -n 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  printf '  %-46s HTTP=%-4s body.code=%-4s name=%s\n' "$label" "${http:-?}" "${code:-?}" "${name:-<none>}"
}

before=$(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e "SELECT COUNT(*) FROM users;" 2>/dev/null | tr -d '\r')

TOK_S=$(mint $STUDENT_ID $STUDENT_NAME 1)

say "=== W34: GET /api/auth/info/{userId} 跨用户读 — 直连 $BASE ==="
say "  身份=学生 $STUDENT_ID($STUDENT_NAME) role=1；别人 id=$OTHER_ID；活库 users 行数=$before"
say ""
say "--- 修前：应当看到「别人」那行泄漏 ---"
row "学生 -> 自己 /api/auth/info/$STUDENT_ID"   "/api/auth/info/$STUDENT_ID" "$TOK_S"
row "学生 -> 别人 /api/auth/info/$OTHER_ID  ★题眼" "/api/auth/info/$OTHER_ID"  "$TOK_S"
row "对照 学生 -> /api/users/info/$OTHER_ID (期望拒)" "/api/users/info/$OTHER_ID" "$TOK_S"
row "参照 学生 -> /api/users/info/$STUDENT_ID (期望 200)" "/api/users/info/$STUDENT_ID" "$TOK_S"
say ""
say "--- 匿名（第四种形状：Spring Security 拦在映射之前） ---"
row "匿名 -> /api/auth/info/1" "/api/auth/info/1"
say ""

after=$(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e "SELECT COUNT(*) FROM users;" 2>/dev/null | tr -d '\r')
if [ "$before" = "$after" ]; then
  say "=== 零写入自证: [OK] users 行数不变 $before（本探针只读） ==="
else
  say "=== 零写入自证: [!!] users 行数变了! $before -> $after ==="
fi
say ""
say "期望：修前「别人」行 = HTTP 200 + name=刘教授（泄漏）；修后同一行 = HTTP 404（端点已删）。"
