#!/usr/bin/env bash
# W31 —— 课程管理面（`/api/courses/**`）的角色闸 + 「0 行」的说法 (2026-10-08)
#
# 病根（两层都没闸，缺一不可地叠加成"任何已登录用户可增/改/删课程"）：
#   1) 网关 `AuthenticationFilter.hasPermission`：`PREFIX_COURSE` **只出现在** `customRoleAllows`
#      （role ≥ 6）里，内置角色 1/2/3 一路落到函数末尾的 `return true` ⇒ 放行。而那条常量的
#      注释写着「内置角色…不经这两条」——**这句话是错的**（同族：注释给出的理由本身是错的）。
#      ⚠️ 被放行的恰恰是 1/2/3；学院管理员(5)在本层**本来就已被拒**（role==5 段末尾 return false）。
#      闸的方向当年是反的。
#   2) course-service `SecurityConfig`：只有 `anyRequest().authenticated()`，而 `GatewayAuthFilter`
#      是**按请求头建认证、不验签**的 ⇒ 带上 `X-Role` 头就等于"是那个角色"。
#   ⇒ 2026-10-08 直连 8075（复刻网关 StripPrefix=1 + 注入头之后的请求）实测：
#        X-Role 1（学生） POST   /courses/add            → 200 且库里多一行
#        X-Role 1（学生） DELETE /courses/delete/{新id}   → 200 且那行没了
#        X-Role 1（学生） PUT    /courses/update/1        → 200 且真的改到了 courses.id=1 ★红线：这是真实演示行
#
# ★★ 红线（本轮亲历一次）：**任何写探针都不许指向真实演示行**。
#    本探针先把写目标限定成**自己新建的 ZZ-W31PROBE 行**（先建好、拿到它的 id），
#    再由带错误角色的请求去删/改**那一行**；若闸失效，被改的也只是这行自建数据。
#    F3 那两条只打在**不存在的 id (99999)** 上。全程不碰任何既有行的一句字段。
#
# 判据（成对，缺一条都不算过）：
#   拒绝面 : 角色 1/2/3 对 /courses/add、/courses/delete/{探针id}、/courses/update/{探针id}
#            → 必须 HTTP 403 + **空体**，且**探针行仍在**（证明"没落库"）
#   放行面 : 角色 4 与角色 6 的读接口 → 必须 200 **且 credits 计数 > 0**（★证明命中的是真数据）
#   ⚠️ 只测拒绝面会把"接口整个坏了/路径写错"读成"修好了" —— 放行面那条**必须 200 且行数 > 0**，
#      否则 0 行的 403 与"闸生效"在输出里长得一样（W30 亲历）。
#   ⚠️ 被拒的形状是 **Spring Security 的 HTTP 403 + 空体**（`access(...)` 返回 AuthorizationDecision(false)
#      ⇒ 拦在**映射之前**），与控制器业务拒绝那种「HTTP 200 + body.code 403」不是同一种。
#      判据读 HTTP 状态码，别读 body。
#   ⚠️ **两个头都必须发**：闸读的是 `X-Role`（`isCourseManager(getHeader("X-Role"))`）。
#      第一版只发了 `X-User-Id` ⇒ X-Role=null ⇒ 连管理员(4)都 403（本轮亲历一次）。
#
# 零写入自证：courses / teacher_course / course_plans 三张表行数 before == after，
#            且探针行 code='ZZ-W31PROBE' 打前打后都为 0。
#
# 用法: bash probe-w31-course-write-gate.sh [PORT]   (默认 8195)
#   起被测实例（本仓惯例，直连 java -cp、common classes 前置；⚠️ Windows Java 用 `;` 分隔）：
#     cd academic-microservices
#     java -cp "academic-common/target/classes;course-service/target/classes;$(cat /tmp/cscp.txt)" \
#          com.academic.CourseServiceApplication \
#          --server.port=8195 --spring.cloud.nacos.discovery.enabled=false
set -u

PORT="${PORT:-8195}"
DB="${DB:-academic_warning_system}"
BASE="http://localhost:$PORT"
PROBE_CODE="ZZ-W31PROBE"

TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT
printf '{"code":"%s","name":"W31 probe course","credits":0.5,"type":"probe"}' "$PROBE_CODE" > "$TMP"

say() { printf '%s\n' "$*"; }

# 读响应：$1=标签 $2=方法 $3=路径 $4=X-User-Id $5=X-Role [$6=请求体文件]
# 打印 HTTP / body.code / body 长度 / credits 计数（放行面的正对照标记）
row() {
  local label="$1" method="$2" path="$3" uid="$4" role="$5" bodyfile="${6:-}"
  local out http body code clen ncred
  if [ -n "$bodyfile" ]; then
    out=$(curl -s -m 15 -X "$method" -w '\n@@%{http_code}' "$BASE$path" \
          -H "X-User-Id: $uid" -H "X-Role: $role" \
          -H 'Content-Type: application/json' --data-binary "@$bodyfile")
  else
    out=$(curl -s -m 15 -X "$method" -w '\n@@%{http_code}' "$BASE$path" \
          -H "X-User-Id: $uid" -H "X-Role: $role")
  fi
  http=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//')
  code=$(printf '%s' "$body" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')
  clen=${#body}
  ncred=$(printf '%s' "$body" | grep -o '"credits"' | wc -l | tr -d ' ')
  printf '  %-42s HTTP=%-4s body.code=%-4s len=%-5s credits=%s\n' \
         "$label" "${http:-?}" "${code:-?}" "$clen" "$ncred"
}

probe_rows() {
  MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e \
    "SELECT COUNT(*) FROM courses WHERE code='$PROBE_CODE';" 2>/dev/null | tr -d '\r'
}
zeros() {
  MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e \
    "SELECT CONCAT((SELECT COUNT(*) FROM courses),'/',(SELECT COUNT(*) FROM teacher_course),'/',(SELECT COUNT(*) FROM course_plans),'/',(SELECT COUNT(*) FROM courses WHERE code='$PROBE_CODE'));" 2>/dev/null | tr -d '\r'
}

# 打前清掉可能残存的探针行（上次中途失败留下的）
MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -e \
  "DELETE FROM courses WHERE code='$PROBE_CODE';" >/dev/null 2>&1

before=$(zeros)

say "=== W31: 课程管理面角色闸 — port=$PORT（直连，复刻网关 strip+inject 之后的请求） ==="
say "  活库 courses/teacher_course/course_plans = $before ；探针行 code=$PROBE_CODE 打前=$(probe_rows)"
say ""

# ---- 第 0 步：先用管理员建出探针行，后面的写目标全部指向它 ----
say "--- 第 0 步：管理员(4) 建自建探针行（后续所有写目标 = 这一行） ---"
addout=$(curl -s -m 15 -X POST -w '\n@@%{http_code}' "$BASE/courses/add" \
         -H "X-User-Id: 1" -H "X-Role: 4" -H 'Content-Type: application/json' --data-binary "@$TMP")
addhttp=$(printf '%s' "$addout" | sed -n 's/.*@@\([0-9]*\)$/\1/p')
PID=$(printf '%s' "$addout" | sed 's/@@[0-9]*$//' | sed -n 's/.*"data"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p')
say "  管理员(4) POST /courses/add   HTTP=$addhttp  新id=${PID:-?}  探针行数=$(probe_rows) (期望 200 / id>0 / 1)"
if [ -z "${PID:-}" ]; then
  say "  [!!] 建不出探针行（闸或路径有问题），后续写测无安全靶 —— 中止。"
  exit 1
fi
say ""

# ---- 拒绝面：错误角色去写**探针行**（若不拦，被改的也只是这行自建数据） ----
say "--- 拒绝面（角色 1/2/3，期望 403 + 空体；探针行必须**没被动过**） ---"
row "学生(1)   POST   /courses/add"            POST   "/courses/add"             3   1  "$TMP"
row "学生(1)   DELETE /courses/delete/$PID"    DELETE "/courses/delete/$PID"    3   1
row "学生(1)   PUT    /courses/update/$PID"    PUT    "/courses/update/$PID"    3   1  "$TMP"
row "教师(2)   POST   /courses/add"            POST   "/courses/add"            18  2  "$TMP"
row "教师(2)   DELETE /courses/delete/$PID"    DELETE "/courses/delete/$PID"    18  2
row "辅导员(3) DELETE /courses/delete/$PID"    DELETE "/courses/delete/$PID"    23  3
row "学院管理员(5) GET /courses/list"          GET    "/courses/list"            1   5
say "  ↳ 探针行数=$(probe_rows) [期望 1 —— 被拒 = 没删掉]；另加的 hascode 检查："
say "    探针行 code 仍为 $(MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -N -e "SELECT code FROM courses WHERE id=$PID;" 2>/dev/null | tr -d '\r') [期望 $PROBE_CODE]"
say ""

# ---- 放行面：管理员/自定义角色读真数据 ----
say "--- 放行面（角色 4/6，期望 200 且 credits>0 —— 证明闸没把好人也拦了） ---"
row "管理员(4) GET /courses/list"              GET    "/courses/list"            1   4
row "自定义(6) GET /courses/list"              GET    "/courses/list"            1   6
say ""

# ---- 放行面的写：管理员删掉探针行（这才是它该有的下场） ----
say "--- 放行面的写：管理员(4) 删掉探针行 ---"
row "管理员(4) DELETE /courses/delete/$PID"    DELETE "/courses/delete/$PID"    1   4
say "  ↳ 探针行数=$(probe_rows) [期望 0]"
say ""

# ---- F3：0 行的说法（只打不存在的 id） ----
say "--- F3: 0 行的说法（角色 4，期望 body.code=404「课程不存在」而不是 500） ---"
row "DELETE /courses/delete/99999"             DELETE "/courses/delete/99999"   1   4
row "PUT    /courses/update/99999"             PUT    "/courses/update/99999"   1   4  "$TMP"
say ""

say "--- 无身份头（第四种形状，只报告） ---"
out=$(curl -s -m 15 -w '\n@@%{http_code}' "$BASE/courses/list")
say "  GET /courses/list 无头: HTTP=$(printf '%s' "$out" | sed -n 's/.*@@\([0-9]*\)$/\1/p')  body=$(printf '%s' "$out" | sed 's/@@[0-9]*$//' | head -c 60)"
say ""

# 兜底清理
MSYS2_ARG_CONV_EXCL='*' mysql -uroot -proot -D "$DB" -e \
  "DELETE FROM courses WHERE code='$PROBE_CODE';" >/dev/null 2>&1

after=$(zeros)
mv_before=$(printf '%s' "$before" | sed 's|/[0-9]*$||')
mv_after=$(printf '%s' "$after"  | sed 's|/[0-9]*$||')
probe_after=$(printf '%s' "$after" | sed 's|.*/||')
if [ "$mv_before" = "$mv_after" ] && [ "$probe_after" = "0" ]; then
  say "=== 零写入自证: [OK] before=$mv_before after=$mv_after 探针行残留=$probe_after (courses/teacher_course/course_plans) ==="
else
  say "=== 零写入自证: [!!] before=$before after=$after -- 立刻查库！ ==="
fi
