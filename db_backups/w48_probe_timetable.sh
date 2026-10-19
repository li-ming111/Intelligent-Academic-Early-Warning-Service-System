#!/bin/bash
# W48（2026-10-18）排课面 live 探针。
#
# 覆盖四件事：
#   A. 学生自助端点 GET /api/students/timetable/{uid}  —— 本人放行 / 换 id 拒绝
#   B. 教师自助端点 GET /api/teachers/courses/timetable
#   C. 排课管理面 /api/course-plans/** 的角色闸（网关层 403 vs 控制器层 200+code403）
#   D. 管理员经网关走通 CRUD 全链（自建行），并回库核对
#
# ⚠️ 写目标一律**自建行**：course_plans 的 id 自增，新建的 id 先查出来再删，
#    绝不碰演示数据的那 10 行（W31/W44 红线）。
# ⚠️ 「被拒」有三种形状，分开断言：
#      · 网关 403 + body code 403（拦在路由前）
#      · Spring Security 403 + **空体**（course-service 的 access(...) 拒）
#      · 控制器业务拒绝 = HTTP 200 + body code 403
#    只断言 HTTP 状态码会把第三种读成"通过"。

GW=http://localhost:8076
# 完整响应的落盘位置。⚠️ 必须是 **Windows 侧看得见** 的路径：
# 本机 `python` 是 D:\Python\python.exe，读不到 MSYS 的 `/tmp`（实测 FileNotFoundError）。
LAST_BODY_FILE="D:/ABS/db_backups/_w48_last_body.json"
PASS=0; FAIL=0
ok()   { echo "  ✔ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ✘ $1"; FAIL=$((FAIL+1)); }
chk()  { if [ "$2" = "$3" ]; then ok "$1 ($2)"; else bad "$1 —— 期望 $3，实得 $2"; fi; }

# 从库里取 course_plans 最大 id（自建行的起点）
QL() { mysql -uroot -proot --default-character-set=utf8mb4 -N -B \
       -e "USE academic_warning_system; $1" 2>/dev/null; }

login() {
  local u=$1
  local cap cid ctxt
  cap=$(curl -s "$GW/api/auth/captcha")
  cid=$(echo "$cap"  | python -c "import sys,json;print(json.load(sys.stdin)['data']['captchaId'])")
  ctxt=$(echo "$cap" | python -c "import sys,json;print(json.load(sys.stdin)['data']['captchaText'])")
  curl -s -X POST "$GW/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$u\",\"password\":\"123456\",\"captchaId\":\"$cid\",\"captcha\":\"$ctxt\"}" \
    | python -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'])"
}

# 一次调用回 "HTTP码<TAB>body码<TAB>body前200字"
call() {
  local method=$1 url=$2 token=$3 body=$4
  local out
  if [ -n "$body" ]; then
    # ⚠️ body 一律写临时文件再 `--data-binary @file`，**不要** `-d "$body"`：
    #    本机是 Git Bash (MSYS)，命令行参数里含非 ASCII 时会被 MSYS 按系统代码页
    #    转码，服务端收到 `Invalid UTF-8 middle byte 0xeb` 而回 400 —— 这正是
    #    本探针第一版报的那 14 条红，其中 12 条是探针自己的假红（W48 教训，
    #    同 W47「探针自己骗了我」那一族）。写文件发字节则完全绕开参数转码。
    printf '%s' "$body" > /tmp/w48_req_body.json
    out=$(curl -s -w $'\n%{http_code}' -X "$method" "$GW$url" \
          -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
          --data-binary @/tmp/w48_req_body.json)
  else
    out=$(curl -s -w $'\n%{http_code}' -X "$method" "$GW$url" -H "Authorization: Bearer $token")
  fi
  local code bodytxt
  code=$(echo "$out" | tail -1)
  bodytxt=$(echo "$out" | head -n -1)
  # ⚠️ 完整 body 落盘给 jq_last 用。**不能**用 bash 变量传：`call` 是被
  #    `$(call ...)` 以命令替换调用的 ⇒ 跑在子 shell 里，往里赋的全局变量出了子 shell 就没了
  #    （第一版就栽在这：`LAST_BODY` 恒空 ⇒ jq_last 全空 ⇒ A2/B2/B5 假红）。
  #    也不能写到 MSYS 的 `/tmp` —— python 是 Windows 的，读不到那个路径。
  printf '%s' "$bodytxt" > "$LAST_BODY_FILE"
  local bcode
  bcode=$(printf '%s' "$bodytxt" | python -c "
import sys,json
raw=sys.stdin.read().strip()
if not raw: print('-')
else:
    try: print(json.loads(raw).get('code','-'))
    except Exception: print('nonjson')
" 2>/dev/null)
  printf '%s\t%s\t%s\n' "$code" "$bcode" "$(echo "$bodytxt" | head -c 200)"
}

# 从上次响应的**完整** body 里取一个值（用于数条数等）。表达式里 `d` 是解析后的 JSON。
jq_last() {
  python -c "
import sys,json
raw=open(r'$LAST_BODY_FILE',encoding='utf-8').read().strip()
try:
    d=json.loads(raw) if raw else None
except Exception:
    d=None
try:
    print(eval(sys.argv[1]))
except Exception:
    print('')
" "$1" 2>/dev/null
}

# 上次响应的**完整** body 原文（查字段是否存在用这个，不要拿 print 出来的 200 字截断体查）
raw_last() { cat "$LAST_BODY_FILE" 2>/dev/null; }

echo "=========== 登录三个角色 ==========="
T_STU=$(login 2023020616)   # 司明  users.id=27
T_TEA=$(login teacher001)   # 刘教授 users.id=18
T_ADM=$(login admin)        # 管理员 users.id=1
for v in T_STU T_TEA T_ADM; do
  if [ -n "${!v}" ]; then echo "  $v 取得 token (${#T_STU} char 量级)"; else echo "  $v **登录失败**"; fi
done
for v in T_STU T_TEA T_ADM; do
  [ -n "${!v}" ] || { echo "  ❌ $v 为空，后续全部结论不可信 ⇒ 中止"; exit 1; }
done

echo
echo "=========== A. 学生自助课表 GET /api/students/timetable/{uid} ==========="
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/students/timetable/27" "$T_STU")"
chk "A1 本人(uid=27) 读自己的课表 → HTTP" "$hc" "200"
echo "     载荷: $bd"
CNT=$(jq_last "len(d.get('data') or [])")
echo "     课表条数: $CNT"
# 司明选课 enrollments 覆盖 course 1-6，course_plans 对这 6 门都有排课 ⇒ 期望 6
chk "A2 课表条数（司明选了 course 1-6）" "$CNT" "6"
# 载荷必须非空且含关键字段（只断言 200 会把空表读成通过）
raw_last | grep -q "courseName" && ok "A3 载荷含 courseName（非空壳）" || bad "A3 载荷缺 courseName"

IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/students/timetable/30" "$T_STU")"
chk "A4 换 id 读同学的课表 → HTTP（控制器业务拒绝恒 200）" "$hc" "200"
chk "A4b …body.code" "$bc" "403"
echo "     文案: $(echo "$bd" | python -c "import sys,json;print(json.load(sys.stdin).get('message'))" 2>/dev/null)"

IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/students/timetable/27" "")"
chk "A5 无 token → 网关 401" "$hc" "401"

echo
echo "=========== B. 教师自助课表 GET /api/teachers/courses/timetable ==========="
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/teachers/courses/timetable" "$T_TEA")"
chk "B1 教师(uid=18) 读自己的课表 → HTTP" "$hc" "200"
echo "     载荷: $bd"
CNT=$(jq_last "len(d.get('data') or [])")
echo "     课表条数: $CNT"
# course_plans 里 teacher_id=18 的只有 id=6 一条（周三 3-4节 教学楼A304）
chk "B2 刘教授(uid=18) 的排课条数" "$CNT" "1"
raw_last | grep -q "enrolledCount" && ok "B3 载荷含 enrolledCount（实际选课人数）" || bad "B3 载荷缺 enrolledCount"

# 教师无参数可传 —— 端点不接受任何 id 入参，故不存在"传别人 id"这条攻击面。
# 用「换成另一个教师 token 就查到另一个人的课」来钉住身份确实来自网关注入。
#
# ⚠️ 第二个人**特意挑排课条数与本条不同的**：赵振江(users.id=28) 有 3 条（course_plans id 7/8/9），
#    而 B2 的刘教授(18) 只有 1 条。若挑一个也是 1 条的老师（如 19 号王老师），
#    "1 = 1" 这种断言在**端点根本不看 token、每次都回同一份数据**时同样成立 ⇒ 不承重。
T_TEA2=$(login teacher005)  # 赵振江 uid=28
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/teachers/courses/timetable" "$T_TEA2")"
chk "B4 换教师 token（赵振江 uid=28）→ HTTP" "$hc" "200"
CNT2=$(jq_last "len(d.get('data') or [])")
echo "     赵振江的排课条数: $CNT2"
chk "B5 赵振江(uid=28) 的排课条数（3 ≠ B2 的 1 ⇒ 身份真的来自 token）" "$CNT2" "3"
raw_last | grep -q "courseName" && ok "B6 赵振江的载荷非空（含 courseName）" || bad "B6 赵振江的载荷为空/无字段"
# 钉住两条载荷确实不同（同一份数据不可能同时是 1 条和 3 条）
raw_last | grep -q "艺术楼C201" && ok "B6b 载荷含赵振江独有的排课（艺术楼C201）" || bad "B6b 载荷不含赵振江的排课"

IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/teachers/courses/timetable" "")"
chk "B7 无 token → 网关 401" "$hc" "401"

echo
echo "=========== C. 排课管理面 /api/course-plans/** 的角色闸 ==========="
# C1 学生打读端点：网关层拒（拦在路由前）
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/course-plans/all" "$T_STU")"
chk "C1 学生读 /api/course-plans/all → HTTP" "$hc" "403"
chk "C1b …body.code（网关 403 带 code）" "$bc" "403"

# C2 学生打写端点
IFS=$'\t' read -r hc bc bd <<< "$(call POST "/api/course-plans/create" "$T_STU" '{"courseId":1,"teacherId":18,"semester":"2025-2026-2","year":2026,"classTime":"周六 9-10节","location":"教学楼A501","maxStudents":50,"status":1}')"
chk "C2 学生 POST /api/course-plans/create → HTTP" "$hc" "403"

# C3 教师也拒（与 /api/courses/** 同解）
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/course-plans/all" "$T_TEA")"
chk "C3 教师读 /api/course-plans/all → HTTP" "$hc" "403"

# C4 直连 course-service（绕网关）也要拒 —— 这是「网关不是网络边界」那层
DC=$(curl -s -o /tmp/w48_dc.txt -w '%{http_code}' "http://localhost:8075/course-plans/all" \
     -H "X-User-Id: 27" -H "X-Role: 1")
chk "C4 直连 8075 带 X-Role:1（学生）→ HTTP" "$DC" "403"
SZ=$(wc -c < /tmp/w48_dc.txt)
chk "C4b …Spring Security 403 是**空体**" "$SZ" "0"

# C5 直连带管理员头 ⇒ 放行（证明 C4 拒的是角色而不是路径）
DC2=$(curl -s -o /tmp/w48_dc2.txt -w '%{http_code}' "http://localhost:8075/course-plans/all" \
      -H "X-User-Id: 1" -H "X-Role: 4")
chk "C5 直连 8075 带 X-Role:4（管理员）→ HTTP" "$DC2" "200"

echo
echo "=========== D. 管理员经网关走通 CRUD 全链（自建行） ==========="
BEFORE=$(QL "SELECT COUNT(*) FROM course_plans;")
MAXID=$(QL "SELECT IFNULL(MAX(id),0) FROM course_plans;")
echo "  写前：行数=$BEFORE，最大 id=$MAXID"

IFS=$'\t' read -r hc bc bd <<< "$(call POST "/api/course-plans/create" "$T_ADM" '{"courseId":1,"teacherId":18,"semester":"2099-2100-1","year":2099,"classTime":"W48P 探针节","location":"W48PROBE","maxStudents":42,"status":1}')"
chk "D1 管理员 POST /create → HTTP" "$hc" "201"
echo "     响应: $bd"

AFTER=$(QL "SELECT COUNT(*) FROM course_plans;")
chk "D2 回库：行数 +1" "$AFTER" "$((BEFORE+1))"
NEWID=$(QL "SELECT id FROM course_plans WHERE location='W48PROBE' ORDER BY id DESC LIMIT 1;")
echo "     自建行 id=$NEWID"
ROW=$(QL "SELECT CONCAT_WS('|',course_id,teacher_id,semester,year,class_time,location,max_students,status) FROM course_plans WHERE id=$NEWID;")
chk "D3 回库：自建行字段与请求一致" "$ROW" "1|18|2099-2100-1|2099|W48P 探针节|W48PROBE|42|1"

# D4 读回
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/course-plans/$NEWID" "$T_ADM")"
chk "D4 管理员 GET /{id} → HTTP" "$hc" "200"
raw_last | grep -q "W48PROBE" && ok "D5 读回的载荷含自建行标记" || bad "D5 读回载荷不含标记"

# D6 改
IFS=$'\t' read -r hc bc bd <<< "$(call PUT "/api/course-plans/$NEWID" "$T_ADM" '{"courseId":1,"teacherId":18,"semester":"2099-2100-1","year":2099,"classTime":"W48P 探针节改","location":"W48PROBE2","maxStudents":77,"status":1}')"
chk "D6 管理员 PUT /{id} → HTTP" "$hc" "200"
ROW2=$(QL "SELECT CONCAT_WS('|',class_time,location,max_students) FROM course_plans WHERE id=$NEWID;")
chk "D7 回库：改动真的落盘" "$ROW2" "W48P 探针节改|W48PROBE2|77"

# D8 删（只删自建行）
IFS=$'\t' read -r hc bc bd <<< "$(call DELETE "/api/course-plans/$NEWID" "$T_ADM")"
chk "D8 管理员 DELETE /{id} → HTTP" "$hc" "200"
LEFT=$(QL "SELECT COUNT(*) FROM course_plans WHERE id=$NEWID;")
chk "D9 回库：自建行已消失" "$LEFT" "0"

END=$(QL "SELECT COUNT(*) FROM course_plans;")
chk "D10 行数回到写前（没碰演示数据那 10 行）" "$END" "$BEFORE"

echo
echo "=========== E. 负对照：管理员被拒时的形状 ==========="
# 不存在的 id：控制器回 404（不是 200/403）—— 钉住"没这条"与"不是你的"在这里分得开
IFS=$'\t' read -r hc bc bd <<< "$(call GET "/api/course-plans/999999" "$T_ADM")"
chk "E1 不存在的 id → HTTP 404" "$hc" "404"
# 非法路径（校验 Spring Security 的 anyRequest().authenticated() 兜底）
DC3=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8075/course-plans/all")
chk "E2 直连不带任何头 → HTTP（authenticated 兜底拒）" "$DC3" "403"

echo
echo "=========== 汇总 ==========="
echo "  通过 $PASS / 失败 $FAIL"
[ "$FAIL" -eq 0 ] && echo "  ✅ 全绿" || echo "  ❌ 有红"
